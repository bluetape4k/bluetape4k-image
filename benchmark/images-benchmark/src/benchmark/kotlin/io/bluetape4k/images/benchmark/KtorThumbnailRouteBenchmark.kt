package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.ktor.ImageThumbnailKtorRoutesConfig
import io.bluetape4k.images.ktor.bluetape4kImageThumbnailRoutes
import io.bluetape4k.images.toByteArray
import io.bluetape4k.ktor.core.Bluetape4kKtorCoreConfig
import io.bluetape4k.ktor.core.installBluetape4kKtorCore
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplication
import io.ktor.utils.io.readRemaining
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import java.util.concurrent.TimeUnit

/**
 * multipart parsing, image processing, full production thumbnail route에 대한 Ktor test-host benchmark입니다.
 * application은 JMH trial마다 한 번 시작합니다.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class KtorThumbnailRouteBenchmark {

    @Param("avatar", "medium", "photo4k")
    var fixture: String = "avatar"

    private lateinit var payload: ByteArray
    private lateinit var application: TestApplication
    private lateinit var client: HttpClient

    @Setup
    fun setup() {
        val fixtureData = KtorThumbnailFixtures.create(fixture)
        payload = fixtureData.bytes
        println(
            "Ktor route fixture: name=$fixture dimensions=" +
                "${fixtureData.width}x${fixtureData.height} encodedBytes=${payload.size}"
        )
        application = thumbnailTestApplication(maxInputBytes = MAX_INPUT_BYTES)
        runBlocking { application.start() }
        client = application.client
        runBlocking {
            val response = client.post("/benchmark/parse") {
                setBody(imageMultipart(payload))
            }
            check(response.status == HttpStatusCode.OK)
            check(response.bodyAsBytes().decodeToString().toLong() == payload.size.toLong())
        }
    }

    @TearDown
    fun tearDown() {
        runBlocking { application.stop() }
    }

    @Benchmark
    fun multipart_parseOnly(bh: Blackhole) = runBlocking {
        val response = client.post("/benchmark/parse") {
            setBody(imageMultipart(payload))
        }
        check(response.status == HttpStatusCode.OK)
        bh.consume(response.bodyAsBytes())
    }

    @Benchmark
    fun image_decodeThumbnail(bh: Blackhole) {
        val output = immutableImageOf(payload)
            .max(THUMBNAIL_SIDE, THUMBNAIL_SIDE)
            .forWriter(PngWriter.MaxCompression)
            .toByteArray()
        bh.consume(output)
    }

    @Benchmark
    fun route_fullThumbnailResponse(bh: Blackhole) = runBlocking {
        val response = client.post("/images/thumbnail?maxSide=$THUMBNAIL_SIDE") {
            setBody(imageMultipart(payload))
        }
        check(response.status == HttpStatusCode.OK)
        bh.consume(response.bodyAsBytes())
    }

    private companion object {
        private const val MAX_INPUT_BYTES = 16L * 1024 * 1024
        private const val THUMBNAIL_SIDE = 320
    }
}

/** configured limit보다 1 byte 큰 multipart upload에 대한 fail-fast route benchmark입니다. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class KtorThumbnailRejectedRouteBenchmark {

    private lateinit var oversizedPayload: ByteArray
    private lateinit var application: TestApplication
    private lateinit var client: HttpClient

    @Setup
    fun setup() {
        oversizedPayload = ByteArray(MAX_INPUT_BYTES + 1)
        application = thumbnailTestApplication(maxInputBytes = MAX_INPUT_BYTES.toLong())
        runBlocking { application.start() }
        client = application.client
    }

    @TearDown
    fun tearDown() {
        runBlocking { application.stop() }
    }

    @Benchmark
    fun route_rejectOversize(bh: Blackhole) = runBlocking {
        val response = client.post("/images/thumbnail?maxSide=320") {
            setBody(imageMultipart(oversizedPayload))
        }
        check(response.status == HttpStatusCode.BadRequest)
        bh.consume(response.bodyAsBytes())
    }

    private companion object {
        private const val MAX_INPUT_BYTES = 1024 * 1024
    }
}

/**
 * concurrent accepted-request benchmark입니다. 각 sample은 같은 coroutine gate에서 release된 request batch 하나의
 * completion time입니다.
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class KtorThumbnailConcurrentRouteBenchmark {

    @Param("medium", "photo4k")
    var fixture: String = "medium"

    @Param("1", "5", "10", "30")
    var concurrency: Int = 1

    private lateinit var payload: ByteArray
    private lateinit var application: TestApplication
    private lateinit var client: HttpClient

    @Setup
    fun setup() {
        val fixtureData = KtorThumbnailFixtures.create(fixture)
        payload = fixtureData.bytes
        println(
            "Ktor concurrent fixture: name=$fixture concurrency=$concurrency dimensions=" +
                "${fixtureData.width}x${fixtureData.height} encodedBytes=${payload.size}"
        )
        application = thumbnailTestApplication(maxInputBytes = MAX_INPUT_BYTES)
        runBlocking { application.start() }
        client = application.client
    }

    @TearDown
    fun tearDown() {
        runBlocking { application.stop() }
    }

    @Benchmark
    fun route_concurrentAcceptedBatch(bh: Blackhole) = runBlocking {
        val responses = runConcurrentRequests(concurrency) {
            val response = client.post("/images/thumbnail?maxSide=$THUMBNAIL_SIDE") {
                setBody(imageMultipart(payload))
            }
            KtorBenchmarkResponse(response.status, response.bodyAsBytes().size)
        }
        check(responses.all { it.status == HttpStatusCode.OK })
        bh.consume(responses.sumOf { it.bodySize })
    }

    private companion object {
        private const val MAX_INPUT_BYTES = 16L * 1024 * 1024
        private const val THUMBNAIL_SIDE = 320
    }
}

/** configured limit보다 1 byte 큰 upload에 대한 concurrent fail-fast benchmark입니다. */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class KtorThumbnailConcurrentRejectedBenchmark {

    @Param("1", "10", "30")
    var concurrency: Int = 1

    private lateinit var oversizedPayload: ByteArray
    private lateinit var application: TestApplication
    private lateinit var client: HttpClient

    @Setup
    fun setup() {
        oversizedPayload = ByteArray(MAX_INPUT_BYTES + 1)
        application = thumbnailTestApplication(maxInputBytes = MAX_INPUT_BYTES.toLong())
        runBlocking { application.start() }
        client = application.client
    }

    @TearDown
    fun tearDown() {
        runBlocking { application.stop() }
    }

    @Benchmark
    fun route_concurrentRejectedBatch(bh: Blackhole) = runBlocking {
        val responses = runConcurrentRequests(concurrency) {
            val response = client.post("/images/thumbnail?maxSide=320") {
                setBody(imageMultipart(oversizedPayload))
            }
            KtorBenchmarkResponse(response.status, response.bodyAsBytes().size)
        }
        check(responses.all { it.status == HttpStatusCode.BadRequest })
        bh.consume(responses.sumOf { it.bodySize })
    }

    private companion object {
        private const val MAX_INPUT_BYTES = 1024 * 1024
    }
}

/**
 * accepted request 90%와 oversize rejection 10%를 섞은 mixed traffic benchmark입니다.
 * ratio를 정확히 유지하기 위해 concurrency 값은 10의 배수입니다.
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class KtorThumbnailMixedConcurrencyBenchmark {

    @Param("medium", "photo4k")
    var fixture: String = "medium"

    @Param("10", "30")
    var concurrency: Int = 10

    private lateinit var acceptedPayload: ByteArray
    private lateinit var rejectedPayload: ByteArray
    private lateinit var application: TestApplication
    private lateinit var client: HttpClient

    @Setup
    fun setup() {
        val fixtureData = KtorThumbnailFixtures.create(fixture)
        acceptedPayload = fixtureData.bytes
        check(acceptedPayload.size <= MIXED_MAX_INPUT_BYTES)
        rejectedPayload = ByteArray(MIXED_MAX_INPUT_BYTES + 1)
        println(
            "Ktor mixed fixture: name=$fixture concurrency=$concurrency accepted=${concurrency * 9 / 10} " +
                "rejected=${concurrency / 10} dimensions=${fixtureData.width}x${fixtureData.height} " +
                "encodedBytes=${acceptedPayload.size}"
        )
        application = thumbnailTestApplication(maxInputBytes = MIXED_MAX_INPUT_BYTES.toLong())
        runBlocking { application.start() }
        client = application.client
    }

    @TearDown
    fun tearDown() {
        runBlocking { application.stop() }
    }

    @Benchmark
    fun route_mixedAcceptedRejectedBatch(bh: Blackhole) = runBlocking {
        val acceptedCount = concurrency * 9 / 10
        val responses = runConcurrentRequests(concurrency) { index ->
            val accepted = index < acceptedCount
            val response = client.post("/images/thumbnail?maxSide=$THUMBNAIL_SIDE") {
                setBody(imageMultipart(if (accepted) acceptedPayload else rejectedPayload))
            }
            KtorBenchmarkResponse(response.status, response.bodyAsBytes().size) to accepted
        }
        check(
            responses.all { (response, accepted) ->
                response.status == if (accepted) HttpStatusCode.OK else HttpStatusCode.BadRequest
            }
        )
        bh.consume(responses.sumOf { (response) -> response.bodySize })
    }

    private companion object {
        private const val MIXED_MAX_INPUT_BYTES = 2 * 1024 * 1024
        private const val THUMBNAIL_SIDE = 320
    }
}

private suspend fun <T> runConcurrentRequests(
    concurrency: Int,
    request: suspend (Int) -> T,
): List<T> = coroutineScope {
    val startGate = CompletableDeferred<Unit>()
    val requests = List(concurrency) { index ->
        async {
            startGate.await()
            request(index)
        }
    }
    startGate.complete(Unit)
    requests.awaitAll()
}

private data class KtorBenchmarkResponse(
    val status: HttpStatusCode,
    val bodySize: Int,
)

private fun thumbnailTestApplication(maxInputBytes: Long): TestApplication =
    TestApplication {
        application {
            installBluetape4kKtorCore(Bluetape4kKtorCoreConfig(installHealthRoutes = false))
            routing {
                post("/benchmark/parse") {
                    val multipart = call.receiveMultipart()
                    var receivedBytes = 0L
                    while (true) {
                        val part = multipart.readPart() ?: break
                        try {
                            receivedBytes += when (part) {
                                is PartData.FileItem -> part.provider().readRemaining().readByteArray().size
                                is PartData.BinaryChannelItem -> part.provider().readRemaining().readByteArray().size
                                else -> 0
                            }
                        } finally {
                            part.release()
                        }
                    }
                    call.respondText(receivedBytes.toString())
                }
                bluetape4kImageThumbnailRoutes(
                    ImageThumbnailKtorRoutesConfig(
                        maxInputBytes = maxInputBytes,
                        maxInputPixels = 16_777_216,
                        maxInputSide = 8_192,
                        defaultMaxSide = 320,
                        maxAllowedSide = 320,
                    )
                )
            }
        }
    }

private fun imageMultipart(bytes: ByteArray): MultiPartFormDataContent =
    MultiPartFormDataContent(
        formData {
            append("file", "fixture.jpg", ContentType.Image.JPEG, bytes.size.toLong()) {
                write(bytes)
            }
        }
    )

private data class KtorThumbnailFixture(
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
)

private object KtorThumbnailFixtures {
    private val jpegWriter = JpegWriter(82, false)

    fun create(name: String): KtorThumbnailFixture {
        val (width, height) = when (name) {
            "avatar" -> 256 to 256
            "medium" -> 1920 to 1080
            "photo4k" -> 3840 to 2160
            else -> error("Unsupported Ktor benchmark fixture: $name")
        }
        val bytes = BenchmarkImageSets.photo4k.scaleTo(width, height).bytes(jpegWriter)
        return KtorThumbnailFixture(width, height, bytes)
    }
}
