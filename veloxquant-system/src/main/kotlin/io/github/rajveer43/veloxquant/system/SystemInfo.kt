/**
 * Hardware detection for the local machine (JVM-desktop only — never Android-safe).
 *
 * Mirrors `veloxquant-go/system/darwin.go` and `veloxquant-sdk/src/system.ts`'s pattern of
 * shelling directly to `sysctl`/`vm_stat` rather than through the `veloxquant` CLI, since no
 * Python `system info` subcommand exists (investigation §5.5, plan §3.2).
 */
package io.github.rajveer43.veloxquant.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Detected Apple Silicon chip generation. `UNKNOWN` covers a recognized-but-unmapped brand string. */
public enum class AppleSiliconChip { M1, M2, M3, M4, UNKNOWN }

/**
 * Snapshot of the local machine's hardware, as reported by `sysctl`/`vm_stat`.
 *
 * @param chip null on non-Apple-Silicon hardware or if detection fails.
 * @param gpuCoreCount null always on this platform today — macOS has no public `sysctl` key for
 *   GPU core count (it requires IOKit/Metal APIs, out of scope for a shell-out-only detector).
 *   This is a genuine, permanent detection gap, not a transient failure — do not retry it.
 */
public data class HardwareInfo(
    val chip: AppleSiliconChip?,
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val cpuCoreCount: Int,
    val gpuCoreCount: Int?,
)

/** Entry point for local hardware detection. See [SystemInfo.detect]. */
public object SystemInfo {
    /**
     * Detects the local machine's hardware by shelling to `sysctl`/`vm_stat`.
     *
     * Returns a best-effort [HardwareInfo] on any platform; fields default to `0`/`null` when a
     * given probe fails or the platform isn't macOS, mirroring Go's `darwin.go` fallback
     * behavior (never throws on a single failed probe).
     */
    public suspend fun detect(): HardwareInfo =
        withContext(Dispatchers.IO) {
            val isMac = System.getProperty("os.name")?.contains("Mac", ignoreCase = true) == true
            if (!isMac) {
                return@withContext HardwareInfo(
                    chip = null,
                    totalMemoryBytes = 0,
                    availableMemoryBytes = 0,
                    cpuCoreCount = Runtime.getRuntime().availableProcessors(),
                    gpuCoreCount = null,
                )
            }

            val isArm64 = System.getProperty("os.arch")?.contains("aarch64", ignoreCase = true) == true
            val brandString = sysctlString("machdep.cpu.brand_string")
            val chip = if (isArm64) chipFromBrandString(brandString) else null

            val totalMemoryBytes = sysctlUInt64("hw.memsize") ?: 0L
            val availableMemoryBytes = availableMemoryFromVmStat(totalMemoryBytes)
            val cpuCoreCount =
                sysctlUInt64("hw.physicalcpu")?.toInt() ?: Runtime.getRuntime().availableProcessors()

            HardwareInfo(
                chip = chip,
                totalMemoryBytes = totalMemoryBytes,
                availableMemoryBytes = availableMemoryBytes,
                cpuCoreCount = cpuCoreCount,
                gpuCoreCount = null,
            )
        }

    internal fun chipFromBrandString(brandString: String?): AppleSiliconChip {
        if (brandString == null) return AppleSiliconChip.UNKNOWN
        return when {
            "M4" in brandString -> AppleSiliconChip.M4
            "M3" in brandString -> AppleSiliconChip.M3
            "M2" in brandString -> AppleSiliconChip.M2
            "M1" in brandString -> AppleSiliconChip.M1
            else -> AppleSiliconChip.UNKNOWN
        }
    }

    internal fun sysctlString(name: String): String? =
        runCatching {
            val process = ProcessBuilder("sysctl", "-n", name).redirectErrorStream(false).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotEmpty()) output else null
        }.getOrNull()

    internal fun sysctlUInt64(name: String): Long? = sysctlString(name)?.toLongOrNull()

    /**
     * Estimates available memory from `vm_stat`'s free/inactive/speculative page counts,
     * mirroring `darwin.go`'s `availableMemoryFromVMStat` exactly (including its
     * fall-back-to-total behavior when parsing yields zero or an out-of-range value).
     */
    internal fun availableMemoryFromVmStat(totalMemoryBytes: Long): Long {
        val output =
            runCatching {
                val process = ProcessBuilder("vm_stat").start()
                val text = process.inputStream.bufferedReader().readText()
                process.waitFor()
                text
            }.getOrNull() ?: return totalMemoryBytes

        var pageSize = 4096L
        var free = 0L
        var inactive = 0L
        var speculative = 0L

        for (line in output.lineSequence()) {
            when {
                line.startsWith("Mach Virtual Memory Statistics") -> {
                    extractPageSize(line)?.let { pageSize = it }
                }
                line.startsWith("Pages free:") -> free = extractPageCount(line)
                line.startsWith("Pages inactive:") -> inactive = extractPageCount(line)
                line.startsWith("Pages speculative:") -> speculative = extractPageCount(line)
            }
        }

        val available = (free + inactive + speculative) * pageSize
        return if (available == 0L || available > totalMemoryBytes) totalMemoryBytes else available
    }

    internal fun extractPageCount(line: String): Long {
        val parts = line.split(":")
        if (parts.size != 2) return 0L
        val value = parts[1].trim().removeSuffix(".").trim()
        return value.toLongOrNull() ?: 0L
    }

    internal fun extractPageSize(line: String): Long? {
        val marker = "page size of "
        val idx = line.indexOf(marker)
        if (idx == -1) return null
        val rest = line.substring(idx + marker.length)
        val firstField = rest.trim().substringBefore(' ')
        return firstField.toLongOrNull()
    }
}
