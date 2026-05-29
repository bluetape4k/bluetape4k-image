package io.bluetape4k.images.examples.ktor

import io.bluetape4k.images.ktor.CaptchaKtorRoutesConfig
import io.bluetape4k.images.ktor.ImageThumbnailKtorRoutesConfig
import io.bluetape4k.images.ktor.bluetape4kCaptchaRoutes
import io.bluetape4k.images.ktor.bluetape4kImageThumbnailRoutes
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Runs the local-only Ktor image API quickstart.
 */
fun main() {
    embeddedServer(
        factory = Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
        module = Application::configureKtorImageApi,
    ).start(wait = true)
}

/**
 * Installs JSON support and the bluetape4k Ktor image routes used by the quickstart.
 */
fun Application.configureKtorImageApi() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }

    routing {
        get("/ready") {
            call.respondText("OK", ContentType.Text.Plain)
        }
        bluetape4kCaptchaRoutes(
            CaptchaKtorRoutesConfig(routePath = "/api/captcha")
        )
        bluetape4kImageThumbnailRoutes(
            ImageThumbnailKtorRoutesConfig(routePath = "/api/images")
        )
    }
}
