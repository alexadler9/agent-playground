package presentation.privatechat

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import domain.privatechat.PrivateChatErrorResponse
import domain.privatechat.PrivateChatRequest
import domain.privatechat.PrivateChatService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class PrivateChatHttpServer(
    private val host: String,
    private val port: Int,
    private val expectedToken: String,
    private val json: Json,
    private val chatService: PrivateChatService,
) {

    private val rateLimiter = SimpleRateLimiter(
        maxRequests = 10,
        windowMs = 60_000,
    )

    private val server: HttpServer = HttpServer.create(
        InetSocketAddress(host, port),
        0,
    )

    fun start() {
        server.executor = Executors.newFixedThreadPool(4)

        server.createContext("/") { exchange ->
            handleStatic(exchange)
        }

        server.createContext("/health") { exchange ->
            handleHealth(exchange)
        }

        server.createContext("/chat") { exchange ->
            handleChat(exchange)
        }

        server.start()
    }

    fun stop() {
        server.stop(0)
    }

    private fun handleHealth(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            sendError(exchange, 405, "Method not allowed")
            return
        }

        sendJson(
            exchange = exchange,
            statusCode = 200,
            body = """{"status":"ok"}""",
        )
    }

    private fun handleChat(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") {
            sendError(exchange, 405, "Method not allowed")
            return
        }

        if (!isAuthorized(exchange)) {
            sendError(exchange, 401, "Unauthorized")
            return
        }

        val clientKey = exchange.remoteAddress.address.hostAddress ?: "unknown"

        if (!rateLimiter.allow(clientKey)) {
            sendError(exchange, 429, "Rate limit exceeded")
            return
        }

        try {
            val body = exchange.requestBody
                .readAllBytes()
                .toString(StandardCharsets.UTF_8)

            val request = json.decodeFromString<PrivateChatRequest>(body)

            val response = runBlocking {
                chatService.chat(request)
            }

            sendJson(
                exchange = exchange,
                statusCode = 200,
                body = json.encodeToString(response),
            )
        } catch (error: IllegalArgumentException) {
            sendError(exchange, 400, error.message ?: "Bad request")
        } catch (error: Exception) {
            sendError(exchange, 500, error.message ?: "Internal server error")
        }
    }

    private fun handleStatic(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            sendError(exchange, 405, "Method not allowed")
            return
        }

        val path = exchange.requestURI.path

        val resourcePath = when (path) {
            "/", "/index.html" -> "/web/index.html"
            "/styles.css" -> "/web/styles.css"
            "/app.js" -> "/web/app.js"
            else -> null
        }

        if (resourcePath == null) {
            sendError(exchange, 404, "Not found")
            return
        }

        val bytes = javaClass.getResourceAsStream(resourcePath)
            ?.use { it.readAllBytes() }

        if (bytes == null) {
            sendError(exchange, 404, "Resource not found")
            return
        }

        exchange.responseHeaders.add(
            "Content-Type",
            contentTypeFor(resourcePath),
        )

        exchange.sendResponseHeaders(200, bytes.size.toLong())

        exchange.responseBody.use { output ->
            output.write(bytes)
        }
    }

    private fun contentTypeFor(resourcePath: String): String {
        return when {
            resourcePath.endsWith(".html") -> "text/html; charset=utf-8"
            resourcePath.endsWith(".css") -> "text/css; charset=utf-8"
            resourcePath.endsWith(".js") -> "application/javascript; charset=utf-8"
            else -> "application/octet-stream"
        }
    }

    private fun isAuthorized(exchange: HttpExchange): Boolean {
        val authHeader = exchange.requestHeaders.getFirst("Authorization")
        return authHeader == "Bearer $expectedToken"
    }

    private fun sendError(
        exchange: HttpExchange,
        statusCode: Int,
        message: String,
    ) {
        sendJson(
            exchange = exchange,
            statusCode = statusCode,
            body = json.encodeToString(
                PrivateChatErrorResponse(error = message),
            ),
        )
    }

    private fun sendJson(
        exchange: HttpExchange,
        statusCode: Int,
        body: String,
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)

        exchange.responseHeaders.add(
            "Content-Type",
            "application/json; charset=utf-8",
        )

        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())

        exchange.responseBody.use { output ->
            output.write(bytes)
        }
    }
}