package dev.projector.launcher

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.ConnectException
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread

/**
 * Presses keys and types text on this device by talking to its own adb daemon over
 * 127.0.0.1:5555, the same way `adb shell input ...` from a computer does. Apps can't
 * inject input into other apps directly, so this is what lets the phone remote work
 * everywhere, not just on the home screen. Needs USB debugging on and a one-time approval.
 */
object AdbBridge {

    private const val HOST = "127.0.0.1"
    private const val PORT = 5555
    private const val CONNECT_TIMEOUT_MS = 3_000
    // Long enough for someone to notice and accept the "Allow USB debugging?" prompt.
    private const val APPROVAL_TIMEOUT_MS = 120_000

    private sealed class Command {
        class Key(val code: Int) : Command()
        class Text(val text: String) : Command()
    }

    private val queue = LinkedBlockingDeque<Command>()
    private var worker: Thread? = null
    private var connection: Dadb? = null
    private lateinit var keyDir: File

    /** String resource describing the connection, shown in the launcher and on the phone. */
    @Volatile
    var status: Int = R.string.remote_status_off
        private set

    @Synchronized
    fun start(context: Context) {
        if (worker != null) return
        keyDir = File(context.filesDir, "adb")
        worker = thread(name = "adb-bridge", isDaemon = true) { loop() }
    }

    fun key(code: Int) {
        queue.add(Command.Key(code))
    }

    fun text(text: String) {
        queue.add(Command.Text(text))
    }

    private fun loop() {
        // Connect straight away so the approval prompt appears while the user is at the projector.
        try {
            connect()
        } catch (e: Exception) {
            disconnect()
        }
        while (true) {
            val first = try {
                queue.take()
            } catch (e: InterruptedException) {
                return
            }
            val command = when (first) {
                is Command.Key -> {
                    // Starting `input` takes a moment on this chip, so send queued presses together.
                    val codes = mutableListOf(first.code)
                    while (queue.peekFirst() is Command.Key) codes += (queue.pollFirst() as Command.Key).code
                    "input keyevent " + codes.joinToString(" ")
                }
                // `input text` reads %s as a space.
                is Command.Text -> "input text " + shellQuote(first.text.replace(" ", "%s"))
            }
            run(command)
        }
    }

    private fun run(command: String) {
        repeat(2) {
            try {
                connect().shell(command)
                return
            } catch (e: Exception) {
                disconnect()
            }
        }
        // Don't replay a burst of stale presses once the connection comes back.
        queue.clear()
    }

    private fun connect(): Dadb {
        connection?.let { return it }
        status = R.string.remote_status_connecting
        try {
            val dadb = Dadb.create(HOST, PORT, keyPair(), CONNECT_TIMEOUT_MS, APPROVAL_TIMEOUT_MS)
            dadb.shell("echo ok")
            connection = dadb
            status = R.string.remote_status_connected
            return dadb
        } catch (e: Exception) {
            val refused = generateSequence(e as Throwable) { it.cause }.any { it is ConnectException }
            status = if (refused) R.string.remote_status_adb_off else R.string.remote_status_failed
            throw e
        }
    }

    private fun disconnect() {
        try {
            connection?.close()
        } catch (e: Exception) {
            // Already broken; nothing to clean up.
        }
        connection = null
    }

    private fun keyPair(): AdbKeyPair {
        val privateKey = File(keyDir, "adbkey")
        val publicKey = File(keyDir, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) {
            keyDir.mkdirs()
            AdbKeyPair.generate(privateKey, publicKey)
        }
        return AdbKeyPair.read(privateKey, publicKey)
    }

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
