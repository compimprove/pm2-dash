package org.compi.people_search

import io.ktor.server.application.*
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    createServer(host = "0.0.0.0").start(wait = true)
}

fun startServer(): AutoCloseable {
    val server = createServer()
    server.start(wait = false)
    return AutoCloseable {
        server.stop(gracePeriodMillis = 1_000, timeoutMillis = 2_000)
    }
}

fun createServer(
    host: String = "127.0.0.1",
    port: Int = SERVER_PORT,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
    return embeddedServer(Netty, port = port, host = host, module = Application::module)
}

fun Application.module() {
    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
    }
}
