package io.github.rajveer43.veloxquant.runtime

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fake [LiveProcess] for [VeloxQuantProcess] readiness-race tests. Stdout/stderr lines are fed
 * from queues under test control, so a test can deterministically control the order events
 * become visible (e.g. "emit the ready line only after the priming request would have fired")
 * without a real subprocess.
 */
private val END_OF_STREAM = Any()

internal class FakeLiveProcess : LiveProcess {
    private val stdoutQueue = LinkedBlockingQueue<Any>()
    private val stderrQueue = LinkedBlockingQueue<Any>()
    private val alive = AtomicBoolean(true)
    private val exitCodeValue = AtomicInteger(0)

    fun emitStdout(line: String) = stdoutQueue.put(line)

    fun emitStderr(line: String) = stderrQueue.put(line)

    fun closeStdout() = stdoutQueue.put(END_OF_STREAM)

    fun closeStderr() = stderrQueue.put(END_OF_STREAM)

    fun simulateExit(exitCode: Int) {
        exitCodeValue.set(exitCode)
        alive.set(false)
        closeStdout()
        closeStderr()
    }

    override fun readStdoutLine(): String? = stdoutQueue.take().let { if (it === END_OF_STREAM) null else it as String }

    override fun readStderrLine(): String? = stderrQueue.take().let { if (it === END_OF_STREAM) null else it as String }

    override val isAlive: Boolean
        get() = alive.get()

    override val exitCode: Int
        get() = exitCodeValue.get()

    override fun sendInterrupt() {
        alive.set(false)
    }

    override fun destroyForcibly() {
        alive.set(false)
        closeStdout()
        closeStderr()
    }

    override fun waitFor(timeoutMillis: Long): Boolean {
        val deadline =
            if (timeoutMillis > Long.MAX_VALUE / 2) Long.MAX_VALUE else System.currentTimeMillis() + timeoutMillis
        while (alive.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5)
        }
        return !alive.get()
    }
}
