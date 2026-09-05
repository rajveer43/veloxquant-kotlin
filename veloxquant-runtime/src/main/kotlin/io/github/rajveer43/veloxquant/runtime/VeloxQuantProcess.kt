/**
 * `veloxquant serve` process lifecycle (plan §3.12, build prompt Phase 4) — the highest-risk,
 * most platform-fiddly phase in this SDK. JVM-desktop only: there is no local process/shell
 * access on Android.
 */
package io.github.rajveer43.veloxquant.runtime

import io.github.rajveer43.veloxquant.core.ChatRequest
import io.github.rajveer43.veloxquant.core.Message
import io.github.rajveer43.veloxquant.core.VeloxQuantClient
import io.github.rajveer43.veloxquant.core.VeloxQuantException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val READY_PREFIX = "VELOXQUANT_READY "
private const val PORT_POLL_INTERVAL_MS = 100L
private const val SOCKET_CONNECT_TIMEOUT_MS = 200

private val json = Json { ignoreUnknownKeys = true }

/** Every live [VeloxQuantProcess], tracked so the JVM shutdown hook can force-stop orphans. */
private val liveProcesses = ConcurrentHashMap.newKeySet<VeloxQuantProcess>()

private val shutdownHookMutex = Mutex()
private var shutdownHookRegistered = false

private suspend fun ensureShutdownHookRegistered() {
    shutdownHookMutex.withLock {
        if (shutdownHookRegistered) return
        Runtime.getRuntime().addShutdownHook(
            Thread {
                liveProcesses.toList().forEach { it.forceStopOnShutdownHook() }
            },
        )
        shutdownHookRegistered = true
    }
}

private sealed interface ReadinessOutcome {
    data class Ready(val handshake: ReadyHandshake) : ReadinessOutcome

    data class ProcessExited(val exitCode: Int) : ReadinessOutcome
}

/**
 * A running `veloxquant serve` process. Construct via [VeloxQuantProcess.start]; always call
 * [stop] (or let the JVM shutdown hook catch it) to avoid an orphaned server holding a port
 * (plan §3.12 item 4 — mirrors the Python control panel's own orphan-cleanup design).
 */
public class VeloxQuantProcess internal constructor(
    private val liveProcess: LiveProcess,
    public val config: ServeConfig,
) {
    /** Pre-wired to this process's actual `host:port` — never reconstruct a client by hand. */
    public val client: VeloxQuantClient = VeloxQuantClient.create(baseUrl = "http://${config.host}:${config.port}")

    @Volatile
    private var stopped = false

    /** Sends `SIGINT` (POSIX) / `destroy()` (Windows), escalating to a forced kill after [gracePeriod]. */
    public suspend fun stop(gracePeriod: Duration = ServeConfig.DEFAULT_GRACE_PERIOD) {
        if (stopped) return
        stopped = true
        liveProcesses.remove(this)
        client.close()
        if (!liveProcess.isAlive) return
        liveProcess.sendInterrupt()
        val exited = runInterruptible(Dispatchers.IO) { liveProcess.waitFor(gracePeriod.inWholeMilliseconds) }
        if (!exited) {
            liveProcess.destroyForcibly()
        }
    }

    /** Synchronous force-stop for the JVM shutdown hook, which cannot suspend. */
    internal fun forceStopOnShutdownHook() {
        if (stopped) return
        stopped = true
        liveProcesses.remove(this)
        if (liveProcess.isAlive) {
            liveProcess.sendInterrupt()
            if (!liveProcess.waitFor(ServeConfig.DEFAULT_GRACE_PERIOD.inWholeMilliseconds)) {
                liveProcess.destroyForcibly()
            }
        }
    }

    public companion object {
        /**
         * Launches `veloxquant serve` per [config] and suspends until it reports ready.
         *
         * **Readiness is a race, not a single wait** (investigation §1.7/§1.10): once the TCP
         * port opens, a real `max_tokens: 1` chat request is fired to force `mlx_lm.server`'s
         * lazy model load — the exact mechanism Go's SDK is confirmed missing, which is why
         * Go's stdout-only wait can hang for the full timeout even on a healthy server. The
         * priming request's only job is to *trigger* the load; the `VELOXQUANT_READY` stdout
         * line remains the authoritative "ready" signal — `start()` never resolves early just
         * because the priming request itself returned (its result, success or failure, is
         * deliberately ignored).
         */
        public suspend fun start(
            config: ServeConfig,
            launcher: LiveProcessLauncher = RealLiveProcessLauncher,
            pathResolver: PathResolver = RealPathResolver,
        ): VeloxQuantProcess {
            if (!pathResolver.resolve("veloxquant")) {
                throw VeloxQuantException.CliNotInstalled("veloxquant serve")
            }

            val liveProcess = launcher.launch(buildServeCommand(config))
            val process = VeloxQuantProcess(liveProcess, config)
            return awaitReady(process, liveProcess, config)
        }

        private suspend fun awaitReady(
            process: VeloxQuantProcess,
            liveProcess: LiveProcess,
            config: ServeConfig,
        ): VeloxQuantProcess {
            val stderrBuffer = StringBuilder()

            return CoroutineScope(Dispatchers.IO).run {
                val stderrJob =
                    launch {
                        while (true) {
                            val line = runInterruptible { liveProcess.readStderrLine() } ?: break
                            synchronized(stderrBuffer) { stderrBuffer.appendLine(line) }
                        }
                    }
                val handshakeDeferred = async { runInterruptible { scanForReadyHandshake(liveProcess) } }
                val exitDeferred = async { runInterruptible { watchForEarlyExit(liveProcess) } }
                val primingDeferred =
                    async {
                        waitForPortOpen(config.host, config.port)
                        runCatching {
                            process.client.chat(
                                ChatRequest(messages = listOf(Message.User(" ")), maxTokens = 1),
                            )
                        }
                    }

                try {
                    val outcome =
                        withTimeoutOrNull(config.readyTimeout) {
                            awaitFirstReadinessOutcome(handshakeDeferred, exitDeferred)
                        }
                    resolveOutcome(outcome, process, config, stderrBuffer)
                } finally {
                    primingDeferred.cancel()
                    handshakeDeferred.cancel()
                    exitDeferred.cancel()
                    stderrJob.cancel()
                }
            }
        }

        private suspend fun resolveOutcome(
            outcome: ReadinessOutcome?,
            process: VeloxQuantProcess,
            config: ServeConfig,
            stderrBuffer: StringBuilder,
        ): VeloxQuantProcess {
            if (outcome == null) {
                process.forceStopOnShutdownHook()
                throw VeloxQuantException.ServeStartupTimeout(config.model, config.port, config.readyTimeout)
            }
            if (outcome is ReadinessOutcome.ProcessExited) {
                val stderrText = synchronized(stderrBuffer) { stderrBuffer.toString() }
                throw VeloxQuantException.ServeProcessExited(outcome.exitCode, stderrText)
            }
            liveProcesses.add(process)
            ensureShutdownHookRegistered()
            return process
        }

        /**
         * Polls both deferreds for completion rather than a plain `select`, so a
         * [scanForReadyHandshake] that completes with `null` (stdout closed without a
         * handshake) can defer to [exitDeferred]'s actual exit code instead of being treated
         * as the outcome on its own — see [scanForReadyHandshake]'s KDoc.
         */
        private suspend fun awaitFirstReadinessOutcome(
            handshakeDeferred: kotlinx.coroutines.Deferred<ReadyHandshake?>,
            exitDeferred: kotlinx.coroutines.Deferred<Int>,
        ): ReadinessOutcome {
            while (true) {
                if (exitDeferred.isCompleted) {
                    return ReadinessOutcome.ProcessExited(exitDeferred.await())
                }
                if (handshakeDeferred.isCompleted) {
                    val handshake = handshakeDeferred.await()
                    if (handshake != null) return ReadinessOutcome.Ready(handshake)
                    // Stdout closed without a handshake but the process hasn't reported exiting
                    // yet — keep waiting for exitDeferred rather than treating this as ready.
                }
                delay(PORT_POLL_INTERVAL_MS.milliseconds)
            }
        }

        private fun buildServeCommand(config: ServeConfig): List<String> {
            val command =
                mutableListOf(
                    "veloxquant", "serve",
                    "--model", config.model,
                    "--host", config.host,
                    "--port", config.port.toString(),
                    "--max_tokens", config.maxTokens.toString(),
                    "--temperature", config.temperature.toString(),
                    "--top_p", config.topP.toString(),
                    "--prompt_cache_size", config.promptCacheSize.toString(),
                    "--bits", config.bits.toString(),
                )
            config.method?.let { command.addAll(listOf("--method", it)) }
            config.setOverrides.forEach { (key, value) -> command.addAll(listOf("--set", "$key=$value")) }
            return command
        }

        /**
         * Blocks on [LiveProcess.readStdoutLine] until the ready line appears, or returns
         * `null` if stdout closes first (e.g. the process exited before printing it) — that
         * case is deliberately not an exception here: [start] races this against
         * [watchForEarlyExit] independently, so a coincidentally-early stdout close is only
         * meaningful once correlated with the process's actual exit, not on its own.
         *
         * A plain `readLine()` on a blocked stream is not reliably interruptible the way
         * [LiveProcess.waitFor] is — on a timeout, the caller destroys the underlying process
         * (see [start]'s `null` branch), which closes the stream and unblocks this thread
         * shortly after, rather than coroutine cancellation unblocking it directly.
         */
        private fun scanForReadyHandshake(liveProcess: LiveProcess): ReadyHandshake? {
            while (true) {
                val line = liveProcess.readStdoutLine() ?: return null
                if (line.startsWith(READY_PREFIX)) {
                    return json.decodeFromString(ReadyHandshake.serializer(), line.removePrefix(READY_PREFIX))
                }
            }
        }

        private fun watchForEarlyExit(liveProcess: LiveProcess): Int {
            liveProcess.waitFor(Long.MAX_VALUE)
            return liveProcess.exitCode
        }

        private suspend fun waitForPortOpen(host: String, port: Int) {
            while (true) {
                val open =
                    runInterruptible(Dispatchers.IO) {
                        runCatching {
                            Socket().use { it.connect(InetSocketAddress(host, port), SOCKET_CONNECT_TIMEOUT_MS) }
                            true
                        }.getOrDefault(false)
                    }
                if (open) return
                delay(PORT_POLL_INTERVAL_MS.milliseconds)
            }
        }
    }
}
