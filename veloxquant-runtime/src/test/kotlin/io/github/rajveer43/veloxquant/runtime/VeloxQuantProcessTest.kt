package io.github.rajveer43.veloxquant.runtime

import io.github.rajveer43.veloxquant.core.VeloxQuantException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val READY_LINE_PREFIX = "VELOXQUANT_READY "

private fun readyHandshakeJson(port: Int) =
    """{"schema_version":1,"model":"m","method":"turboquant_rvq","bits":4,"host":"127.0.0.1",""" +
        """"port":$port,"endpoints":{},"accounting_only":true,"accounting_note":"note"}"""

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class VeloxQuantProcessTest {
    private var openSocket: ServerSocket? = null

    @AfterEach
    fun closeSocket() {
        openSocket?.close()
    }

    private fun openEphemeralPort(): Int {
        val socket = ServerSocket(0)
        openSocket = socket
        Thread {
            runCatching { socket.accept() }
        }.apply { isDaemon = true }.start()
        return socket.localPort
    }

    @Test
    fun `start() resolves via the ready handshake even when it only arrives after the priming request would fire`() =
        runBlocking {
            val port = openEphemeralPort()
            val fakeProcess = FakeLiveProcess()
            val config = ServeConfig(model = "m", port = port, readyTimeout = 5.seconds)

            val startDeferred =
                async(Dispatchers.Default) {
                    VeloxQuantProcess.start(config, launcher = { fakeProcess }, pathResolver = { true })
                }

            // Simulate stdout staying silent for a while after the port opens (the exact bug
            // class Go's SDK has: nothing on stdout would otherwise unblock a stdout-only
            // wait) before finally emitting the ready line, mirroring the priming request
            // having already forced the lazy model load in the meantime.
            withContext(Dispatchers.IO) { delay(300.milliseconds) }
            fakeProcess.emitStdout(READY_LINE_PREFIX + readyHandshakeJson(port))

            val process = startDeferred.await()

            assertEquals(port, process.config.port)
            process.stop()
        }

    @Test
    fun `start() throws ServeStartupTimeout when the ready line never arrives`() =
        runBlocking {
            val port = openEphemeralPort()
            val fakeProcess = FakeLiveProcess()
            val config = ServeConfig(model = "m", port = port, readyTimeout = 300.milliseconds)

            val exception =
                runCatching { VeloxQuantProcess.start(config, launcher = { fakeProcess }, pathResolver = { true }) }
                    .exceptionOrNull()

            assertTrue(exception is VeloxQuantException.ServeStartupTimeout)
        }

    @Test
    fun `start() throws ServeProcessExited when the process exits before readiness`() =
        runBlocking {
            val port = openEphemeralPort()
            val fakeProcess = FakeLiveProcess()
            val config = ServeConfig(model = "m", port = port, readyTimeout = 5.seconds)

            val startDeferred =
                async(Dispatchers.Default) {
                    runCatching {
                        VeloxQuantProcess.start(config, launcher = { fakeProcess }, pathResolver = { true })
                    }
                }
            withContext(Dispatchers.IO) { delay(100.milliseconds) }
            fakeProcess.emitStderr("validate_method: unknown method 'nope'")
            fakeProcess.simulateExit(1)

            val result = startDeferred.await()
            val exception = result.exceptionOrNull()
            assertTrue(exception is VeloxQuantException.ServeProcessExited)
            exception as VeloxQuantException.ServeProcessExited
            assertEquals(1, exception.exitCode)
            assertTrue(exception.stderr.contains("unknown method"))
        }

    @Test
    fun `start() throws CliNotInstalled when the binary is not resolvable`() =
        runBlocking {
            val config = ServeConfig(model = "m")

            val exception =
                runCatching {
                    VeloxQuantProcess.start(config, launcher = { FakeLiveProcess() }, pathResolver = { false })
                }.exceptionOrNull()

            assertTrue(exception is VeloxQuantException.CliNotInstalled)
        }
}
