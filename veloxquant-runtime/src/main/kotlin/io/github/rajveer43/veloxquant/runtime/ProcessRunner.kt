/**
 * Thin, mockable boundary around process launching (plan §5.1). Production code goes through
 * [RealProcessRunner]; tests substitute a fake so CLI shell-out parsing logic (JSON shapes,
 * flag spelling, exit-code handling) is verifiable without a real `veloxquant` binary on PATH.
 */
package io.github.rajveer43.veloxquant.runtime

/** The subset of [Process]'s surface a one-shot CLI shell-out actually needs. */
public interface RunningProcess {
    public val stdout: String
    public val stderr: String
    public val exitCode: Int
}

/** Launches a command and waits for it to exit, returning its captured output. */
public fun interface ProcessRunner {
    /** Runs [command] to completion and returns its captured stdout/stderr/exit code. */
    public fun run(command: List<String>): RunningProcess
}

/** [ProcessRunner] backed by a real [ProcessBuilder]. */
public object RealProcessRunner : ProcessRunner {
    override fun run(command: List<String>): RunningProcess {
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return CapturedProcess(stdout, stderr, exitCode)
    }
}

private data class CapturedProcess(
    override val stdout: String,
    override val stderr: String,
    override val exitCode: Int,
) : RunningProcess

/**
 * Resolves whether a binary named [name] exists on `PATH`, without launching it — used to
 * fail fast with [io.github.rajveer43.veloxquant.core.VeloxQuantException.CliNotInstalled]
 * rather than letting [ProcessBuilder] throw a generic `IOException` (plan Phase 3 item 1).
 */
public fun interface PathResolver {
    /** Returns whether [name] resolves to an executable file on `PATH`. */
    public fun resolve(name: String): Boolean
}

/** [PathResolver] backed by scanning the real `PATH` environment variable. */
public object RealPathResolver : PathResolver {
    override fun resolve(name: String): Boolean {
        val pathEntries = System.getenv("PATH")?.split(java.io.File.pathSeparatorChar) ?: return false
        return pathEntries.any { dir ->
            val candidate = java.io.File(dir, name)
            candidate.isFile && candidate.canExecute()
        }
    }
}
