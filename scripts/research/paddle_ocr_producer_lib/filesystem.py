from __future__ import annotations

"""Bounded filesystem and HTTPS primitives for the PaddleOCR producer."""

import hashlib
import http.client
import ipaddress
import os
import shutil
import socket
import ssl
import stat
import tarfile
import time
import unicodedata
import zipfile
from collections.abc import Callable, Iterable, Mapping
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath
from typing import Any, Protocol
from urllib.parse import urljoin, urlsplit

from .contracts import ProducerValidationError, require_sha256


@dataclass(frozen=True)
class ArchiveLimits:
    max_expanded_bytes: int
    max_files: int
    max_depth: int
    max_path_bytes: int
    max_compression_ratio: int
    deadline_seconds: int


ARCHIVE_LIMITS = ArchiveLimits(
    max_expanded_bytes=1024 * 1024 * 1024,
    max_files=10_000,
    max_depth=16,
    max_path_bytes=240,
    max_compression_ratio=100,
    deadline_seconds=600,
)

ORAS_VERSION = "1.3.4"
ORAS_LINUX_AMD64_URL = (
    "https://github.com/oras-project/oras/releases/download/v1.3.4/"
    "oras_1.3.4_linux_amd64.tar.gz"
)
ORAS_LINUX_AMD64_SHA256 = "f27adb935022d94df8dc77719c322dda592c78a0d57a6f7dcdd8d900b248c454"
ORAS_ARCHIVE_MAX_BYTES = 8 * 1024 * 1024
ORAS_EXPANDED_MAX_BYTES = 16 * 1024 * 1024
_ORAS_ARCHIVE_FILES = frozenset({"oras", "LICENSE", "README.md"})


@dataclass(frozen=True)
class ArchiveEntry:
    path: str
    size: int


@dataclass(frozen=True)
class FileReceipt:
    bytes: int
    sha256: str
    operation_id: str = "local-file"
    attempts: int = 1
    retry_delays: tuple[int, ...] = ()


@dataclass(frozen=True)
class DownloadResponse:
    status: int
    headers: Mapping[str, str]
    chunks: Iterable[bytes]
    peer_ip: str
    close: Callable[[], None] = field(default=lambda: None, repr=False, compare=False)


class DownloadTransport(Protocol):
    def request(
        self,
        url: str,
        addresses: tuple[str, ...],
        *,
        connect_timeout: float,
        read_timeout: float,
    ) -> DownloadResponse:
        ...


def verify_regular_file(
    path: Path,
    *,
    expected_bytes: int,
    expected_sha256: str,
    chunk_bytes: int = 1024 * 1024,
) -> FileReceipt:
    """Hash a regular no-follow file and compare its immutable receipt."""

    require_sha256(expected_sha256, "archive sha256")
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise ProducerValidationError("archive must be a regular non-symlink file") from exc
    digest = hashlib.sha256()
    total = 0
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode):
            raise ProducerValidationError("archive must be a regular file")
        with os.fdopen(descriptor, "rb", closefd=False) as stream:
            while True:
                chunk = stream.read(chunk_bytes)
                if not chunk:
                    break
                total += len(chunk)
                digest.update(chunk)
    finally:
        os.close(descriptor)
    if total != expected_bytes:
        raise ProducerValidationError("archive byte count differs")
    actual_sha256 = digest.hexdigest()
    if actual_sha256 != expected_sha256:
        raise ProducerValidationError("archive sha256 differs")
    return FileReceipt(bytes=total, sha256=actual_sha256)


def _safe_archive_path(name: str, limits: ArchiveLimits) -> str:
    if not isinstance(name, str) or not name or "\\" in name:
        raise ProducerValidationError("archive path is invalid")
    normalized = unicodedata.normalize("NFC", name)
    path = PurePosixPath(normalized)
    if path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise ProducerValidationError("archive path escapes extraction root")
    if len(path.parts) > limits.max_depth:
        raise ProducerValidationError("archive path depth exceeds limit")
    if len(normalized.encode("utf-8")) > limits.max_path_bytes:
        raise ProducerValidationError("archive path exceeds byte limit")
    lowered = normalized.lower()
    if lowered.endswith((".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tar.xz", ".zip")):
        raise ProducerValidationError("nested archive is not allowed")
    return normalized


def preflight_archive(
    path: Path,
    limits: ArchiveLimits,
    *,
    clock: Callable[[], float] = time.monotonic,
) -> tuple[ArchiveEntry, ...]:
    """Inspect an archive completely before any extraction occurs."""

    started = clock()
    compressed_bytes = path.stat().st_size
    if compressed_bytes <= 0:
        raise ProducerValidationError("archive must not be empty")
    entries: list[ArchiveEntry] = []
    seen: set[str] = set()

    def add(name: str, size: int) -> None:
        if clock() - started > limits.deadline_seconds:
            raise ProducerValidationError("archive preflight deadline exceeded")
        normalized = _safe_archive_path(name, limits)
        if normalized in seen:
            raise ProducerValidationError("archive contains a duplicate normalized path")
        seen.add(normalized)
        if size < 0:
            raise ProducerValidationError("archive entry has an invalid size")
        entries.append(ArchiveEntry(normalized, size))

    try:
        if tarfile.is_tarfile(path):
            with tarfile.open(path, "r:*") as archive:
                for member in archive.getmembers():
                    if member.isdir():
                        _safe_archive_path(member.name, limits)
                        continue
                    if not member.isfile() or member.issym() or member.islnk():
                        raise ProducerValidationError("archive links and special files are not allowed")
                    if getattr(member, "sparse", None):
                        raise ProducerValidationError("sparse archive entries are not allowed")
                    add(member.name, member.size)
        elif zipfile.is_zipfile(path):
            with zipfile.ZipFile(path) as archive:
                for member in archive.infolist():
                    mode = member.external_attr >> 16
                    if member.is_dir():
                        _safe_archive_path(member.filename.rstrip("/"), limits)
                        continue
                    if mode and not stat.S_ISREG(mode):
                        raise ProducerValidationError("archive links and special files are not allowed")
                    add(member.filename, member.file_size)
        else:
            raise ProducerValidationError("unsupported archive format")
    except (tarfile.TarError, zipfile.BadZipFile, OSError) as exc:
        if isinstance(exc, ProducerValidationError):
            raise
        raise ProducerValidationError("archive could not be inspected") from exc

    total = sum(entry.size for entry in entries)
    if not entries:
        raise ProducerValidationError("archive must contain regular files")
    if len(entries) > limits.max_files:
        raise ProducerValidationError("archive file count exceeds limit")
    if total > limits.max_expanded_bytes:
        raise ProducerValidationError("archive expanded bytes exceed limit")
    if total > compressed_bytes * limits.max_compression_ratio:
        raise ProducerValidationError("archive compression ratio exceeds limit")
    return tuple(entries)


def _public_addresses(host: str, resolver: Callable[[str], tuple[str, ...]]) -> tuple[str, ...]:
    try:
        addresses = tuple(dict.fromkeys(resolver(host)))
    except OSError as exc:
        raise ProducerValidationError("host resolution failed") from exc
    if not addresses:
        raise ProducerValidationError("host must resolve to a public allowlisted address")
    for address in addresses:
        try:
            parsed = ipaddress.ip_address(address)
        except ValueError as exc:
            raise ProducerValidationError("host resolution returned an invalid address") from exc
        if not parsed.is_global:
            raise ProducerValidationError("host must resolve to a public allowlisted address")
    return addresses


def _validated_download_url(url: str, allowed_hosts: set[str]) -> tuple[str, str]:
    parsed = urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname is None:
        raise ProducerValidationError("download URL must use HTTPS with a host")
    if parsed.username is not None or parsed.password is not None:
        raise ProducerValidationError("download URL must not contain userinfo")
    try:
        port = parsed.port
    except ValueError as exc:
        raise ProducerValidationError("download URL has an invalid port") from exc
    if port not in (None, 443):
        raise ProducerValidationError("download URL must use port 443")
    host = parsed.hostname.rstrip(".").lower()
    try:
        ipaddress.ip_address(host)
    except ValueError:
        pass
    else:
        raise ProducerValidationError("download URL must not use an IP literal")
    if host not in allowed_hosts:
        raise ProducerValidationError("download URL host is not allowlisted")
    if parsed.fragment:
        raise ProducerValidationError("download URL must not contain a fragment")
    return url, host


class _PinnedHttpsConnection(http.client.HTTPSConnection):
    def __init__(self, host: str, address: str, timeout: float) -> None:
        super().__init__(host, 443, timeout=timeout, context=ssl.create_default_context())
        self._address = address

    def connect(self) -> None:
        plain = socket.create_connection((self._address, 443), self.timeout)
        self.sock = self._context.wrap_socket(plain, server_hostname=self.host)


class PinnedHttpsTransport:
    """Direct HTTPS transport that does not consult proxy environment variables."""

    def request(
        self,
        url: str,
        addresses: tuple[str, ...],
        *,
        connect_timeout: float,
        read_timeout: float,
    ) -> DownloadResponse:
        parsed = urlsplit(url)
        connection = _PinnedHttpsConnection(parsed.hostname or "", addresses[0], connect_timeout)
        path = parsed.path or "/"
        if parsed.query:
            path += "?" + parsed.query
        try:
            connection.request("GET", path, headers={"Host": parsed.hostname or "", "Accept": "*/*"})
            response = connection.getresponse()
            if connection.sock is None:
                raise ProducerValidationError("HTTPS connection did not expose a peer")
            connection.sock.settimeout(read_timeout)
            peer_ip = connection.sock.getpeername()[0]
            headers = {key.lower(): value for key, value in response.getheaders()}

            def chunks() -> Iterable[bytes]:
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    yield chunk

            return DownloadResponse(
                response.status,
                headers,
                chunks(),
                peer_ip,
                connection.close,
            )
        except Exception:
            connection.close()
            raise


def _system_resolver(host: str) -> tuple[str, ...]:
    return tuple(info[4][0] for info in socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM))


def fetch_to_regular_file(
    source: Mapping[str, Any],
    destination: Path,
    *,
    allowed_hosts: set[str],
    resolver: Callable[[str], tuple[str, ...]] = _system_resolver,
    transport: DownloadTransport | None = None,
    connect_timeout: float = 30.0,
    read_timeout: float = 30.0,
    max_redirects: int = 3,
    max_attempts: int = 3,
    deadline_seconds: float = 900.0,
    clock: Callable[[], float] = time.monotonic,
    sleeper: Callable[[float], None] = time.sleep,
) -> FileReceipt:
    """Download one immutable artifact through a DNS-rebinding-safe HTTPS path."""

    if destination.exists() or destination.is_symlink():
        raise ProducerValidationError("download destination already exists")
    expected_bytes = source.get("bytes")
    if type(expected_bytes) is not int or expected_bytes <= 0:
        raise ProducerValidationError("download bytes must be a positive integer")
    expected_sha256 = require_sha256(source.get("sha256"), "download sha256")
    url = source.get("url")
    if not isinstance(url, str):
        raise ProducerValidationError("download URL must be a string")
    if type(max_attempts) is not int or not 1 <= max_attempts <= 3:
        raise ProducerValidationError("download attempts must be between one and three")
    operation_id = source.get("id", "download")
    if not isinstance(operation_id, str) or not operation_id:
        raise ProducerValidationError("download operation id must be a non-empty string")
    client = transport or PinnedHttpsTransport()
    destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = destination.parent / ("." + destination.name + ".partial")
    started = clock()
    retry_delays: list[int] = []

    class RetryableDownloadError(Exception):
        pass

    for attempt in range(1, max_attempts + 1):
        descriptor: int | None = None
        response: DownloadResponse | None = None
        try:
            current = url
            for hop in range(max_redirects + 1):
                if clock() - started > deadline_seconds:
                    raise TimeoutError("download deadline exceeded")
                current, host = _validated_download_url(current, allowed_hosts)
                addresses = _public_addresses(host, resolver)
                response = client.request(
                    current,
                    addresses,
                    connect_timeout=connect_timeout,
                    read_timeout=read_timeout,
                )
                if response.peer_ip not in addresses:
                    raise ProducerValidationError("peer is not a public allowlisted address")
                if response.status in {301, 302, 303, 307, 308}:
                    location = response.headers.get("location")
                    response.close()
                    response = None
                    if not location or hop == max_redirects:
                        raise ProducerValidationError(
                            "download redirect limit or location is invalid"
                        )
                    current = urljoin(current, location)
                    continue
                break
            if response is None:
                raise ProducerValidationError("download returned no response")
            if response.status in {408, 429} or 500 <= response.status <= 599:
                raise RetryableDownloadError("download returned a retryable HTTP status")
            if response.status != 200:
                raise ProducerValidationError("download returned a permanent HTTP status")

            digest = hashlib.sha256()
            total = 0
            descriptor = os.open(
                temporary,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_CLOEXEC", 0),
                0o600,
            )
            for chunk in response.chunks:
                if clock() - started > deadline_seconds:
                    raise TimeoutError("download deadline exceeded")
                if not isinstance(chunk, bytes) or not chunk:
                    raise ProducerValidationError("download stream returned an invalid chunk")
                total += len(chunk)
                if total > expected_bytes:
                    raise ProducerValidationError("download exceeds locked byte count")
                digest.update(chunk)
                offset = 0
                while offset < len(chunk):
                    offset += os.write(descriptor, chunk[offset:])
            os.fsync(descriptor)
            os.close(descriptor)
            descriptor = None
            if total != expected_bytes:
                raise ProducerValidationError("download byte count differs")
            if digest.hexdigest() != expected_sha256:
                raise ProducerValidationError("download sha256 differs")
            os.link(temporary, destination)
            temporary.unlink()
            return FileReceipt(
                total,
                expected_sha256,
                operation_id=operation_id,
                attempts=attempt,
                retry_delays=tuple(retry_delays),
            )
        except (TimeoutError, ConnectionResetError, RetryableDownloadError) as exc:
            if attempt == max_attempts or clock() - started > deadline_seconds:
                raise ProducerValidationError("download retry attempts exhausted") from exc
            if response is not None:
                response.close()
                response = None
            delay = 2**attempt
            retry_delays.append(delay)
            sleeper(delay)
        finally:
            if descriptor is not None:
                os.close(descriptor)
            if response is not None:
                response.close()
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass
    raise ProducerValidationError("download retry attempts exhausted")


def _output_archive_path(path: str, root_prefix: str | None) -> str:
    parts = PurePosixPath(path).parts
    if root_prefix is not None:
        if len(parts) < 2 or parts[0] != root_prefix:
            raise ProducerValidationError("archive does not have one removable root")
        parts = parts[1:]
    return "/".join(parts)


def _write_root_pinned_file(root: Path, relative: str, chunks: Iterable[bytes], size: int) -> None:
    root_fd = os.open(
        root,
        os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0),
    )
    current_fd = root_fd
    opened: list[int] = []
    descriptor: int | None = None
    total = 0
    try:
        parts = PurePosixPath(relative).parts
        for part in parts[:-1]:
            try:
                os.mkdir(part, mode=0o700, dir_fd=current_fd)
            except FileExistsError:
                pass
            next_fd = os.open(
                part,
                os.O_RDONLY
                | getattr(os, "O_DIRECTORY", 0)
                | getattr(os, "O_NOFOLLOW", 0),
                dir_fd=current_fd,
            )
            opened.append(next_fd)
            current_fd = next_fd
        descriptor = os.open(
            parts[-1],
            os.O_WRONLY
            | os.O_CREAT
            | os.O_EXCL
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
            0o600,
            dir_fd=current_fd,
        )
        for chunk in chunks:
            if not chunk:
                continue
            total += len(chunk)
            if total > size:
                raise ProducerValidationError("archive entry exceeds declared size")
            offset = 0
            while offset < len(chunk):
                offset += os.write(descriptor, chunk[offset:])
        if total != size:
            raise ProducerValidationError("archive entry size differs")
        os.fsync(descriptor)
    finally:
        if descriptor is not None:
            os.close(descriptor)
        for directory_fd in reversed(opened):
            os.close(directory_fd)
        os.close(root_fd)


def extract_archive(
    archive_path: Path,
    destination: Path,
    limits: ArchiveLimits,
    *,
    strip_single_root: bool = False,
    clock: Callable[[], float] = time.monotonic,
) -> tuple[ArchiveEntry, ...]:
    """Preflight and extract regular files beneath a newly created root."""

    entries = preflight_archive(archive_path, limits, clock=clock)
    if destination.exists() or destination.is_symlink():
        raise ProducerValidationError("archive destination already exists")
    roots = {PurePosixPath(entry.path).parts[0] for entry in entries}
    root_prefix = next(iter(roots)) if strip_single_root and len(roots) == 1 else None
    if strip_single_root and root_prefix is None:
        raise ProducerValidationError("archive does not have one removable root")
    output_entries = tuple(
        ArchiveEntry(_output_archive_path(entry.path, root_prefix), entry.size)
        for entry in entries
    )
    destination.mkdir(parents=True, mode=0o700)
    try:
        if tarfile.is_tarfile(archive_path):
            with tarfile.open(archive_path, "r:*") as archive:
                members = {
                    unicodedata.normalize("NFC", member.name): member
                    for member in archive.getmembers()
                    if member.isfile()
                }
                for source, output in zip(entries, output_entries):
                    stream = archive.extractfile(members[source.path])
                    if stream is None:
                        raise ProducerValidationError("archive entry could not be read")
                    with stream:
                        _write_root_pinned_file(
                            destination,
                            output.path,
                            iter(lambda stream=stream: stream.read(1024 * 1024), b""),
                            output.size,
                        )
        elif zipfile.is_zipfile(archive_path):
            with zipfile.ZipFile(archive_path) as archive:
                members = {
                    unicodedata.normalize("NFC", member.filename): member
                    for member in archive.infolist()
                    if not member.is_dir()
                }
                for source, output in zip(entries, output_entries):
                    with archive.open(members[source.path], "r") as stream:
                        _write_root_pinned_file(
                            destination,
                            output.path,
                            iter(lambda: stream.read(1024 * 1024), b""),
                            output.size,
                        )
        else:
            raise ProducerValidationError("unsupported archive format")
        return output_entries
    except BaseException:
        shutil.rmtree(destination, ignore_errors=True)
        raise


def bootstrap_oras_archive(archive_path: Path, tool_root: Path) -> Path:
    """Verify and extract the single pinned ORAS linux/amd64 distribution."""

    try:
        archive_bytes = archive_path.lstat().st_size
    except OSError as exc:
        raise ProducerValidationError("ORAS archive is unavailable") from exc
    if archive_bytes <= 0 or archive_bytes > ORAS_ARCHIVE_MAX_BYTES:
        raise ProducerValidationError("ORAS archive bytes exceed limit")
    verify_regular_file(
        archive_path,
        expected_bytes=archive_bytes,
        expected_sha256=ORAS_LINUX_AMD64_SHA256,
    )
    limits = ArchiveLimits(
        max_expanded_bytes=ORAS_EXPANDED_MAX_BYTES,
        max_files=len(_ORAS_ARCHIVE_FILES),
        max_depth=1,
        max_path_bytes=64,
        max_compression_ratio=100,
        deadline_seconds=30,
    )
    entries = preflight_archive(archive_path, limits)
    names = {entry.path for entry in entries}
    if "oras" not in names or not names.issubset(_ORAS_ARCHIVE_FILES):
        raise ProducerValidationError("ORAS archive file allowlist differs")
    try:
        with tarfile.open(archive_path, "r:gz") as archive:
            if any(not member.isfile() for member in archive.getmembers()):
                raise ProducerValidationError("ORAS archive contains a non-file entry")
    except (tarfile.TarError, OSError) as exc:
        if isinstance(exc, ProducerValidationError):
            raise
        raise ProducerValidationError("ORAS archive must be gzip-compressed tar") from exc
    extracted = extract_archive(archive_path, tool_root, limits)
    if {entry.path for entry in extracted} != names:
        shutil.rmtree(tool_root, ignore_errors=True)
        raise ProducerValidationError("ORAS extracted file set differs")
    os.chmod(tool_root, 0o700)
    oras_bin = tool_root / "oras"
    try:
        metadata = oras_bin.lstat()
    except OSError as exc:
        shutil.rmtree(tool_root, ignore_errors=True)
        raise ProducerValidationError("ORAS binary is unavailable after extraction") from exc
    if not stat.S_ISREG(metadata.st_mode) or oras_bin.is_symlink():
        shutil.rmtree(tool_root, ignore_errors=True)
        raise ProducerValidationError("ORAS binary must be a regular file")
    os.chmod(oras_bin, 0o700)
    if oras_bin.resolve() != tool_root.resolve() / "oras":
        shutil.rmtree(tool_root, ignore_errors=True)
        raise ProducerValidationError("ORAS binary escapes the tool root")
    return oras_bin


def canonical_tree_manifest(root: Path) -> bytes:
    """Return sorted path, byte-count and SHA-256 rows for a regular-file tree."""

    if not root.is_dir() or root.is_symlink():
        raise ProducerValidationError("tree root must be a non-symlink directory")
    rows: list[str] = []
    for current, directories, files in os.walk(root, followlinks=False):
        current_path = Path(current)
        for name in directories:
            if (current_path / name).is_symlink():
                raise ProducerValidationError("tree must not contain symlink directories")
        for name in files:
            path = current_path / name
            if path.is_symlink() or not path.is_file():
                raise ProducerValidationError("tree must contain regular files only")
            relative = path.relative_to(root).as_posix()
            receipt = verify_regular_file(
                path,
                expected_bytes=path.stat().st_size,
                expected_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
            )
            rows.append(f"{relative}\t{receipt.bytes}\t{receipt.sha256}\n")
    if not rows:
        raise ProducerValidationError("tree must contain at least one regular file")
    return "".join(sorted(rows)).encode("utf-8")


def tree_sha256(root: Path) -> str:
    return hashlib.sha256(canonical_tree_manifest(root)).hexdigest()
