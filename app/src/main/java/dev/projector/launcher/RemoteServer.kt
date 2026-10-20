package dev.projector.launcher

import android.content.Context
import android.view.KeyEvent
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Tiny HTTP server for the phone remote. Serves the remote page and turns its button
 * presses into key events through [AdbBridge]. Every API call must carry the PIN shown
 * on the projector, so other people on the Wi-Fi can't take over the screen.
 */
class RemoteServer(private val context: Context, private val pin: String) {

    private var serverSocket: ServerSocket? = null
    private var clients: ExecutorService? = null

    fun start() {
        val pool = Executors.newFixedThreadPool(4)
        clients = pool
        thread(name = "remote-http", isDaemon = true) {
            val socket = try {
                ServerSocket(PORT)
            } catch (e: IOException) {
                return@thread
            }
            serverSocket = socket
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                pool.execute { handle(client) }
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            // Closing anyway.
        }
        clients?.shutdownNow()
    }

    private fun handle(client: Socket) {
        client.use { socket -> serve(socket) }
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].substringBefore('?')
            val query = parseQuery(parts[1].substringAfter('?', ""))

            var contentLength = 0
            var requestPin = ""
            while (true) {
                val header = readLine(input) ?: return
                if (header.isEmpty()) break
                val name = header.substringBefore(':').trim().lowercase()
                val value = header.substringAfter(':', "").trim()
                if (name == "content-length") contentLength = value.toIntOrNull()?.coerceIn(0, MAX_BODY) ?: 0
                if (name == "x-pin") requestPin = value
            }
            val body = readBody(input, contentLength)
            route(socket.getOutputStream(), method, path, query, requestPin, body)
        } catch (e: IOException) {
            // Phone went away mid-request; nothing to answer.
        }
    }

    private fun route(out: OutputStream, method: String, path: String, query: Map<String, String>, requestPin: String, body: String) {
        if (method == "GET" && (path == "/" || path == "/index.html")) {
            val page = context.assets.open("remote.html").use { it.readBytes() }
            respond(out, 200, "text/html; charset=utf-8", page)
            return
        }
        if (!path.startsWith("/api/")) {
            respond(out, 404, "text/plain", "Not found".toByteArray())
            return
        }
        if (requestPin != pin) {
            respondJson(out, 403, "Enter the PIN shown on the projector")
            return
        }
        when {
            method == "POST" && path == "/api/key" -> {
                val code = KEYS[query["k"]]
                if (code == null) {
                    respondJson(out, 400, "Unknown button")
                    return
                }
                AdbBridge.key(code)
            }
            method == "POST" && path == "/api/text" -> if (body.isNotEmpty()) AdbBridge.text(body)
            path == "/api/status" -> Unit
            else -> {
                respondJson(out, 404, "Not found")
                return
            }
        }
        respondJson(out, 200, context.getString(AdbBridge.status))
    }

    private fun respondJson(out: OutputStream, code: Int, status: String) {
        val escaped = status.replace("\\", "\\\\").replace("\"", "\\\"")
        respond(out, code, "application/json", "{\"ok\":${code == 200},\"status\":\"$escaped\"}".toByteArray())
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val head = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (line.size() == 0) null else line.toString("UTF-8")
            if (b == '\n'.code) return line.toString("UTF-8").trimEnd('\r')
            if (line.size() > MAX_LINE) return null
            line.write(b)
        }
    }

    private fun readBody(input: InputStream, length: Int): String {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n == -1) break
            read += n
        }
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun parseQuery(query: String): Map<String, String> = query.split('&')
        .filter { it.contains('=') }
        .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    companion object {
        const val PORT = 8080
        private const val MAX_LINE = 8_192
        private const val MAX_BODY = 4_096

        private val KEYS = mapOf(
            "up" to KeyEvent.KEYCODE_DPAD_UP,
            "down" to KeyEvent.KEYCODE_DPAD_DOWN,
            "left" to KeyEvent.KEYCODE_DPAD_LEFT,
            "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
            "ok" to KeyEvent.KEYCODE_DPAD_CENTER,
            "back" to KeyEvent.KEYCODE_BACK,
            "home" to KeyEvent.KEYCODE_HOME,
            "menu" to KeyEvent.KEYCODE_MENU,
            "volup" to KeyEvent.KEYCODE_VOLUME_UP,
            "voldown" to KeyEvent.KEYCODE_VOLUME_DOWN,
            "mute" to KeyEvent.KEYCODE_VOLUME_MUTE,
            "playpause" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            "rewind" to KeyEvent.KEYCODE_MEDIA_REWIND,
            "forward" to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            "enter" to KeyEvent.KEYCODE_ENTER,
            "delete" to KeyEvent.KEYCODE_DEL,
        )

        /** The projector's address on the home network, e.g. 192.168.1.6. */
        fun localAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
