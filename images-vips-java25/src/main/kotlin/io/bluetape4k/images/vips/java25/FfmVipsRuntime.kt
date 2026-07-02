package io.bluetape4k.images.vips.java25

import io.bluetape4k.images.IncubatingImageApi
import io.bluetape4k.images.vips.VipsCodecCapability
import io.bluetape4k.images.vips.VipsCodecCapabilityReport
import io.bluetape4k.images.vips.VipsCodecDirection
import io.bluetape4k.images.vips.VipsCodecOperationCapability
import io.bluetape4k.images.vips.VipsCodecSmokeResult
import io.bluetape4k.images.vips.VipsDecodeException
import io.bluetape4k.images.vips.VipsEncodeException
import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImage
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsInitializationException
import io.bluetape4k.images.vips.VipsLimits
import io.bluetape4k.images.vips.VipsRuntime
import io.bluetape4k.images.vips.java25.internal.DefaultFfmVipsCodecProbe
import io.bluetape4k.images.vips.java25.internal.DefaultFfmVipsNativeRuntime
import io.bluetape4k.images.vips.java25.internal.FfmVipsCodecProbe
import io.bluetape4k.images.vips.java25.internal.FfmVipsNativeRuntime
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicReference

/**
 * vips-ffm(FFM API) 기반 libvips 런타임 싱글턴.
 *
 * Java 23+ FFM API를 사용합니다. JVM 시작 시 `--enable-native-access=ALL-UNNAMED` 옵션이 필요합니다.
 * 누락 시 [init] 호출에서 경고를 기록합니다.
 *
 * **종료 계약(Terminal Contract)**: [shutdown] 이후 [init]을 호출하면 [VipsInitializationException]이 발생합니다.
 * `Vips.shutdown()` 이후 `Vips.init()` 재호출을 지원하지 않으므로, 프로세스를 재시작해야 합니다.
 *
 * **스레드 안전성**: `AtomicReference<RuntimeState>` CAS로 스레드 안전성을 보장합니다.
 * `@Synchronized`를 사용하지 않습니다 (Virtual Thread 핀닝 방지).
 *
 * **Spring devtools 경고**: [shutdown]을 `@PreDestroy` 빈 메서드로 등록하지 마십시오.
 */
@OptIn(IncubatingImageApi::class)
object FfmVipsRuntime : VipsRuntime, KLogging() {

    private enum class RuntimeState { UNINITIALIZED, INITIALIZING, INITIALIZED, SHUTDOWN }

    private val state = AtomicReference(RuntimeState.UNINITIALIZED)

    @VisibleForTesting
    internal var nativeRuntime: FfmVipsNativeRuntime = DefaultFfmVipsNativeRuntime

    @VisibleForTesting
    internal var codecProbe: FfmVipsCodecProbe = DefaultFfmVipsCodecProbe

    @Volatile
    private var _maxPixels: Long = VipsLimits.DEFAULT_MAX_PIXELS

    /** 허용할 최대 픽셀 수 `width × height × bands` */
    val maxPixels: Long get() = _maxPixels

    override fun init(concurrency: Int, maxPixels: Long) {
        when (state.get()) {
            RuntimeState.INITIALIZED -> return
            RuntimeState.SHUTDOWN -> throw VipsInitializationException(
                "libvips has been shut down — restart the process to re-initialize"
            )
            else -> {}
        }

        if (!state.compareAndSet(RuntimeState.UNINITIALIZED, RuntimeState.INITIALIZING)) {
            var spinCount = 0
            while (state.get() == RuntimeState.INITIALIZING) {
                if (++spinCount > 10_000) {
                    java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L) // 1ms backoff
                    spinCount = 0
                } else {
                    Thread.onSpinWait()
                }
            }
            when (state.get()) {
                RuntimeState.INITIALIZED -> return
                RuntimeState.SHUTDOWN -> throw VipsInitializationException(
                    "libvips was shut down during concurrent initialization"
                )
                RuntimeState.UNINITIALIZED -> throw VipsInitializationException(
                    "Concurrent initialization attempt failed — retry"
                )
                else -> {}
            }
            return
        }

        try {
            checkNativeAccessEnabled()
            nativeRuntime.nativeInit(concurrency)
            _maxPixels = maxPixels
            state.set(RuntimeState.INITIALIZED)
            log.debug("FfmVipsRuntime initialized: concurrency=$concurrency, maxPixels=$maxPixels")
        } catch (e: Error) {
            // UnsatisfiedLinkError, NoClassDefFoundError 등 — 상태 복구 후 원본 Error 재던짐
            state.set(RuntimeState.UNINITIALIZED)
            throw e
        } catch (e: Exception) {
            state.set(RuntimeState.UNINITIALIZED)
            throw VipsInitializationException("libvips (vips-ffm) initialization failed", e)
        }
    }

    override fun shutdown() {
        // INITIALIZING 중 shutdown()이 호출되면 spin-wait 후 전이.
        // UNINITIALIZED/SHUTDOWN 상태에서는 아무것도 하지 않음.
        while (true) {
            when (state.get()) {
                RuntimeState.SHUTDOWN, RuntimeState.UNINITIALIZED -> return
                RuntimeState.INITIALIZED -> {
                    if (state.compareAndSet(RuntimeState.INITIALIZED, RuntimeState.SHUTDOWN)) {
                        nativeRuntime.nativeShutdown()
                        log.debug("FfmVipsRuntime shut down")
                        return
                    }
                }
                RuntimeState.INITIALIZING -> Thread.onSpinWait()
            }
        }
    }

    override val isInitialized: Boolean
        get() = state.get() == RuntimeState.INITIALIZED

    override val isShutdown: Boolean
        get() = state.get() == RuntimeState.SHUTDOWN

    override fun codecCapabilityReport(): VipsCodecCapabilityReport {
        val canLoadHeif = codecProbe.supportsOperation(HEIF_LOAD_OPERATION)
        val canSaveHeif = codecProbe.supportsOperation(HEIF_SAVE_OPERATION)

        return VipsCodecCapabilityReport(
            backendName = BACKEND_NAME,
            libvipsVersion = codecProbe.libvipsVersion(),
            codecs = listOf(
                heifCapability(
                    format = VipsImageFormat.AVIF,
                    canLoadHeif = canLoadHeif,
                    canSaveHeif = canSaveHeif,
                    nativeDependencies = listOf("libvips", "libheif", "libaom"),
                ),
                heifCapability(
                    format = VipsImageFormat.HEIC,
                    canLoadHeif = canLoadHeif,
                    canSaveHeif = canSaveHeif,
                    nativeDependencies = listOf("libvips", "libheif", "HEVC encoder"),
                ),
            ),
            inspectedOperations = setOf(HEIF_LOAD_OPERATION, HEIF_SAVE_OPERATION),
        )
    }

    override fun smokeTestCodec(
        sampleBytes: ByteArray,
        outputFormat: VipsImageFormat,
        options: VipsEncodeOptions,
    ): VipsCodecSmokeResult {
        require(sampleBytes.isNotEmpty()) { "sampleBytes must not be empty" }

        val image: VipsImage = try {
            ffmVipsImageOf(sampleBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VipsDecodeException) {
            return VipsCodecSmokeResult.failure(
                backendName = BACKEND_NAME,
                format = outputFormat,
                stage = VipsCodecDirection.DECODE,
                reason = "${outputFormat.name} decode failed on $BACKEND_NAME; verify native codec support.",
            )
        } catch (e: Exception) {
            return VipsCodecSmokeResult.failure(
                backendName = BACKEND_NAME,
                format = outputFormat,
                stage = VipsCodecDirection.DECODE,
                reason = "${outputFormat.name} decode failed on $BACKEND_NAME; verify native codec support.",
            )
        }

        return try {
            image.use { it.toBytes(outputFormat, options) }
            VipsCodecSmokeResult.success(BACKEND_NAME, outputFormat)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VipsEncodeException) {
            VipsCodecSmokeResult.failure(
                backendName = BACKEND_NAME,
                format = outputFormat,
                stage = VipsCodecDirection.ENCODE,
                reason = "${outputFormat.name} encode failed on $BACKEND_NAME; verify native codec support.",
            )
        } catch (e: Exception) {
            VipsCodecSmokeResult.failure(
                backendName = BACKEND_NAME,
                format = outputFormat,
                stage = VipsCodecDirection.ENCODE,
                reason = "${outputFormat.name} encode failed on $BACKEND_NAME; verify native codec support.",
            )
        }
    }

    @VisibleForTesting
    internal fun resetForTest() {
        state.set(RuntimeState.UNINITIALIZED)
        nativeRuntime = DefaultFfmVipsNativeRuntime
        codecProbe = DefaultFfmVipsCodecProbe
        _maxPixels = VipsLimits.DEFAULT_MAX_PIXELS
    }

    private fun heifCapability(
        format: VipsImageFormat,
        canLoadHeif: Boolean,
        canSaveHeif: Boolean,
        nativeDependencies: List<String>,
    ): VipsCodecCapability =
        VipsCodecCapability.heifFamily(
            format = format,
            decode = operationCapability(
                direction = VipsCodecDirection.DECODE,
                operationName = HEIF_LOAD_OPERATION,
                available = canLoadHeif,
                unavailableReason = "$HEIF_LOAD_OPERATION is unavailable; install libvips with libheif support.",
            ),
            encode = operationCapability(
                direction = VipsCodecDirection.ENCODE,
                operationName = HEIF_SAVE_OPERATION,
                available = canSaveHeif,
                unavailableReason = "$HEIF_SAVE_OPERATION is unavailable; install libvips with HEIF encoder support.",
            ),
            nativeDependencies = nativeDependencies,
        )

    private fun operationCapability(
        direction: VipsCodecDirection,
        operationName: String,
        available: Boolean,
        unavailableReason: String,
    ): VipsCodecOperationCapability =
        if (available) {
            VipsCodecOperationCapability.available(direction, operationName)
        } else {
            VipsCodecOperationCapability.unavailable(direction, operationName, unavailableReason)
        }

    private fun checkNativeAccessEnabled() {
        // ManagementFactory.inputArguments is canonical: covers -javaagent, JDK_JAVA_OPTIONS, _JAVA_OPTIONS.
        // ProcessHandle.commandLine() is fragile (truncation, env var args invisible).
        val jvmArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments
        // 두 조건 모두 같은 arg에서 확인: --add-opens=...=ALL-UNNAMED 같은 arg가 두 번째 절만 일치하는 오탐 방지
        val hasNativeAccess = jvmArgs.any { arg ->
            arg.startsWith("--enable-native-access=") && arg.contains("ALL-UNNAMED")
        }
        if (!hasNativeAccess) {
            log.warn(
                "JVM was started without --enable-native-access=ALL-UNNAMED. " +
                "vips-ffm uses FFM API which may fail without this flag. " +
                "Add --enable-native-access=ALL-UNNAMED to JVM arguments."
            )
        }
    }

    private const val BACKEND_NAME = "vips-ffm"
    private const val HEIF_LOAD_OPERATION = "heifload_buffer"
    private const val HEIF_SAVE_OPERATION = "heifsave_buffer"
}
