#!/usr/bin/env bash

set -Eeuo pipefail

if (( $# == 0 )); then
  echo "usage: gradle-retry.sh <command> [args...]" >&2
  exit 64
fi

max_attempts="${GRADLE_MAX_ATTEMPTS:-5}"
retry_delay_seconds="${GRADLE_RETRY_DELAY_SECONDS:-30}"

if [[ ! "$max_attempts" =~ ^[1-9][0-9]*$ ]]; then
  echo "GRADLE_MAX_ATTEMPTS must be a positive integer: $max_attempts" >&2
  exit 64
fi

if [[ ! "$retry_delay_seconds" =~ ^[0-9]+$ ]]; then
  echo "GRADLE_RETRY_DELAY_SECONDS must be a non-negative integer: $retry_delay_seconds" >&2
  exit 64
fi

command_label=$(printf '%q ' "$@")
attempt=0
first_attempt="not-run"
final_status=1

while (( attempt < max_attempts )); do
  attempt=$((attempt + 1))
  echo "Gradle attempt $attempt/$max_attempts: $command_label"

  if "$@"; then
    final_status=0
    if (( attempt == 1 )); then
      first_attempt="passed"
    fi
    break
  else
    final_status=$?
    if (( attempt == 1 )); then
      first_attempt="failed (exit $final_status)"
    fi
    if (( attempt < max_attempts )); then
      echo "Gradle attempt $attempt failed (exit $final_status); retrying in ${retry_delay_seconds}s"
      sleep "$retry_delay_seconds"
    fi
  fi
done

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  if {
    echo "### Gradle retry 결과"
    echo "- 명령: $command_label"
    echo "- 최초 시도: $first_attempt"
    echo "- 총 시도: $attempt/$max_attempts"
    if (( final_status == 0 )); then
      echo "- 최종 결과: 성공"
    else
      echo "- 최종 결과: 실패 (exit $final_status)"
    fi
  } >> "$GITHUB_STEP_SUMMARY"; then
    :
  else
    echo "error: unable to write GITHUB_STEP_SUMMARY" >&2
    if (( final_status == 0 )); then
      final_status=1
    fi
  fi
fi

exit "$final_status"
