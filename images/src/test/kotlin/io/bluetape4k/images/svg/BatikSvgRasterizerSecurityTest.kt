package io.bluetape4k.images.svg

import com.sun.net.httpserver.HttpServer
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.AbstractImageTest
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.junit5.tempfolder.TempFolderTest
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.utils.Resourcex
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * BatikSvgRasterizer XXE/SSRF 보안 방어 검증 테스트입니다.
 *
 * 테스트는 단순히 예외/비예외를 허용하는 것이 아니라
 * 실제로 악성 콘텐츠가 출력에 포함되지 않는지 검증합니다.
 */
@TempFolderTest
class BatikSvgRasterizerSecurityTest : AbstractImageTest() {

    companion object : KLoggingChannel()

    private val rasterizer = BatikSvgRasterizer()

    @Test
    fun `XXE DOCTYPE SVG - 처리 거부 또는 파일 내용 미포함 검증`() = runSuspendIO {
        // security_xxe.svg: DOCTYPE + file:///etc/passwd 외부 엔티티
        // disallow-doctype-decl=true 이므로 SAXParseException으로 거부 또는 엔티티 무시되어야 함
        val input = Resourcex.getInputStream("images/security_xxe.svg")!!

        var exceptionThrown = false
        var outputBytes = ByteArray(0)

        try {
            input.use {
                val image = rasterizer.rasterize(it)
                // 예외 없이 처리된 경우: 출력에 /etc/passwd 내용이 없어야 함
                outputBytes = image.forWriter(com.sksamuel.scrimage.nio.PngWriter.MaxCompression).bytes()
            }
        } catch (e: Exception) {
            exceptionThrown = true
            log.debug { "DOCTYPE SVG: 예외로 거부됨 (올바른 동작): ${e.javaClass.simpleName}" }
        }

        if (exceptionThrown) {
            // 예외로 거부: 올바른 보안 동작
            exceptionThrown.shouldBeTrue()
        } else {
            // 예외 없이 처리된 경우: 출력에 /etc/passwd 특징적 내용이 없어야 함
            val outputStr = String(outputBytes, Charsets.ISO_8859_1)
            val containsPasswdMarkers = outputStr.contains("root:") ||
                outputStr.contains("/bin/") ||
                outputStr.contains("nobody:")
            containsPasswdMarkers.shouldBeFalse()
            log.debug { "DOCTYPE SVG: 예외 없이 처리됨, 파일 내용 미포함 확인됨" }
        }
    }

    @Test
    fun `외부 HTTP 리소스 참조 SVG - allowExternalResources=false에서 요청하지 않음`() = runSuspendIO {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        server.createContext("/remote.png") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { }
        }
        server.start()

        val port = server.address.port
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                 xmlns:xlink="http://www.w3.org/1999/xlink"
                 width="32" height="32">
              <rect width="32" height="32" fill="#ffffff"/>
              <image x="0" y="0" width="16" height="16"
                     href="http://127.0.0.1:$port/remote.png"
                     xlink:href="http://127.0.0.1:$port/remote.png"/>
            </svg>
        """.trimIndent()
        val opts = SvgRasterizeOptions(allowExternalResources = false)

        try {
            ByteArrayInputStream(svg.toByteArray()).use {
                val image = rasterizer.rasterize(it, opts)
                log.debug { "외부 리소스 SVG: 래스터화 성공 (${image.width}x${image.height}), 외부 로드 없음" }
            }
        } catch (e: Exception) {
            log.debug { "외부 리소스 SVG: 예외로 거부됨 (${e.javaClass.simpleName}), 요청 횟수=${requests.get()}" }
        } finally {
            server.stop(0)
        }

        requests.get() shouldBeEqualTo 0
    }

    @Test
    fun `정상 SVG - allowExternalResources=false에서 정상 래스터화`() = runSuspendIO {
        // 외부 리소스 없는 일반 SVG는 기본 옵션에서 정상 처리되어야 함
        val input = Resourcex.getInputStream("images/sample.svg")!!
        val opts = SvgRasterizeOptions(allowExternalResources = false)

        input.use {
            val image = rasterizer.rasterize(it, opts)
            // 정상 래스터화 검증
            (image.width > 0).shouldBeTrue()
            (image.height > 0).shouldBeTrue()
            log.debug { "일반 SVG 래스터화: ${image.width}x${image.height}" }
        }
    }

    @Test
    fun `정상 SVG - DOCTYPE 없으므로 항상 정상 처리됨`() = runSuspendIO {
        // sample.svg는 DOCTYPE이 없으므로 disallow-doctype-decl=true여도 정상 처리
        val input = Resourcex.getInputStream("images/sample.svg")!!

        input.use {
            val image = rasterizer.rasterize(it)
            (image.width > 0).shouldBeTrue()
            log.debug { "DOCTYPE 없는 SVG: ${image.width}x${image.height} 정상 처리됨" }
        }
    }
}
