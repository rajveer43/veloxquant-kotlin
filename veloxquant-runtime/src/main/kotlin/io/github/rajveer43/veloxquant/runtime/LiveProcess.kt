/**
 * Thin, mockable boundary around a long-running process (plan §3.12, build prompt Phase 4).
 * Distinct from [ProcessRunner]'s "run to completion" model — `serve` stays alive, so tests
 * need to feed stdout/stderr lines while the process is still "running" and simulate exit
 * independently, which [RunningProcess]'s all-at-once captured-output shape cannot express.
 */
package io.github.rajveer43.veloxquant.runtime

/** The subset of [Process]/[ProcessHandle]'s surface [VeloxQuantProcess] needs from a long-running child. */
public interface LiveProcess {
    /** Reads stdout line-by-line; returns `null` at end of stream (process closed stdout). */
    public fun readStdoutLine(): String?

    /** Reads stderr line-by-line; returns `null` at end of stream (process closed stderr). */
    public fun readStderrLine(): String?

    /** Whether the process is still running. */
    public val isAlive: Boolean

    /** The process's exit code — only valid once [isAlive] is false. */
    public val exitCode: Int

    /** Sends `SIGINT` (POSIX) to request graceful shutdown. */
    public fun sendInterrupt()

    /** Forcibly terminates the process (`SIGKILL`-equivalent). */
    public fun destroyForcibly()

    /** Blocks until the process exits or [timeoutMillis] elapses; returns whether it exited. */
    public fun waitFor(timeoutMillis: Long): Boolean
}

/** Launches a long-running command, returning a [LiveProcess] handle to it. */
public fun interface LiveProcessLauncher {
    /** Launches [command] and returns immediately with a handle to the running process. */
    public fun launch(command: List<String>): LiveProcess
}

/** [LiveProcessLauncher] backed by a real [ProcessBuilder]. */
public object RealLiveProcessLauncher : LiveProcessLauncher {
    override fun launch(command: List<String>): LiveProcess = RealLiveProcess(ProcessBuilder(command).start())
}

private class RealLiveProcess(private val process: Process) : LiveProcess {
    private val stdoutReader = process.inputStream.bufferedReader()
    private val stderrReader = process.errorStream.bufferedReader()

    override fun readStdoutLine(): String? = stdoutReader.readLine()

    override fun readStderrLine(): String? = stderrReader.readLine()

    override val isAlive: Boolean
        get() = process.isAlive

    override val exitCode: Int
        get() = process.exitValue()

    /**
     * Sends `SIGINT` via `kill -2 <pid>` on POSIX (the only signal guaranteed to hit `serve.py`'s
     * and `mlx_lm.server`'s `except KeyboardInterrupt` cleanup path — plan §3.12/§5.5). On
     * Windows there is no POSIX signal distinction at all, so this degrades straight to
     * [Process.destroy] — a known, documented platform gap (plan §3.12/§8 non-goal 13), not a
     * silent assumption that graceful shutdown behaves identically cross-platform.
     */
    override fun sendInterrupt() {
        val isWindows = System.getProperty("os.name")?.startsWith("Windows", ignoreCase = true) == true
        if (isWindows) {
            process.destroy()
        } else {
            ProcessBuilder("kill", "-2", process.pid().toString()).start().waitFor()
        }
    }

    override fun destroyForcibly() {
        process.destroyForcibly()
    }

    override fun waitFor(timeoutMillis: Long): Boolean =
        process.waitFor(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
}
