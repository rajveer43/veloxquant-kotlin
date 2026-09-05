package io.github.rajveer43.veloxquant.system

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/**
 * Manual/local-only verification (plan §5.2/§9 item 10 — no self-hosted Apple Silicon CI
 * runner exists anywhere in the ecosystem). Not a hard CI gate; runs opportunistically on
 * macOS and simply asserts the real numbers look sane, printing them for eyeball confirmation.
 */
@EnabledOnOs(OS.MAC)
class ManualDetectSmokeTest {
    @Test
    fun `detect() returns real hardware data on this Mac`() =
        runTest {
            val info = SystemInfo.detect()
            println("HardwareInfo.detect() -> $info")

            assertTrue(info.totalMemoryBytes > 0, "expected nonzero total memory")
            assertTrue(info.availableMemoryBytes > 0, "expected nonzero available memory")
            assertTrue(info.availableMemoryBytes <= info.totalMemoryBytes, "available must not exceed total")
            assertTrue(info.cpuCoreCount > 0, "expected nonzero CPU core count")
        }
}
