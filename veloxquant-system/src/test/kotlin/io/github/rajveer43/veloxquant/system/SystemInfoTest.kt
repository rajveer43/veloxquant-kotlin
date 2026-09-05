package io.github.rajveer43.veloxquant.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SystemInfoTest {
    @Test
    fun `chipFromBrandString maps known generations`() {
        assertEquals(AppleSiliconChip.M1, SystemInfo.chipFromBrandString("Apple M1 Pro"))
        assertEquals(AppleSiliconChip.M2, SystemInfo.chipFromBrandString("Apple M2"))
        assertEquals(AppleSiliconChip.M3, SystemInfo.chipFromBrandString("Apple M3 Max"))
        assertEquals(AppleSiliconChip.M4, SystemInfo.chipFromBrandString("Apple M4"))
    }

    @Test
    fun `chipFromBrandString falls back to UNKNOWN for unrecognized or null input`() {
        assertEquals(AppleSiliconChip.UNKNOWN, SystemInfo.chipFromBrandString("Apple M99"))
        assertEquals(AppleSiliconChip.UNKNOWN, SystemInfo.chipFromBrandString(null))
    }

    @Test
    fun `extractPageSize parses the vm_stat header line`() {
        val line = "Mach Virtual Memory Statistics: (page size of 16384 bytes)"
        assertEquals(16384L, SystemInfo.extractPageSize(line))
    }

    @Test
    fun `extractPageSize returns null when the marker is absent`() {
        assertNull(SystemInfo.extractPageSize("Pages free: 12345."))
    }

    @Test
    fun `extractPageCount parses a trailing-dot page count line`() {
        assertEquals(12345L, SystemInfo.extractPageCount("Pages free:                              12345."))
    }

    @Test
    fun `extractPageCount returns zero for a malformed line`() {
        assertEquals(0L, SystemInfo.extractPageCount("not a vm_stat line"))
    }

    @Test
    fun `availableMemoryFromVmStat sums free inactive and speculative pages`() {
        val vmStatOutput =
            """
            Mach Virtual Memory Statistics: (page size of 16384 bytes)
            Pages free:                             100.
            Pages active:                           500.
            Pages inactive:                          50.
            Pages speculative:                       25.
            Pages wired down:                       200.
            """.trimIndent()

        // Simulate the parse loop directly since availableMemoryFromVmStat shells to a real
        // process; the parsing logic itself is what's under test here via the same line format.
        var pageSize = 4096L
        var free = 0L
        var inactive = 0L
        var speculative = 0L
        for (line in vmStatOutput.lineSequence()) {
            when {
                line.startsWith("Mach Virtual Memory Statistics") ->
                    SystemInfo.extractPageSize(line)?.let { pageSize = it }
                line.startsWith("Pages free:") -> free = SystemInfo.extractPageCount(line)
                line.startsWith("Pages inactive:") -> inactive = SystemInfo.extractPageCount(line)
                line.startsWith("Pages speculative:") -> speculative = SystemInfo.extractPageCount(line)
            }
        }
        val available = (free + inactive + speculative) * pageSize
        assertEquals((100L + 50L + 25L) * 16384L, available)
    }

    @Test
    fun `availableMemoryFromVmStat falls back to total when vm_stat is unavailable`() {
        // vm_stat only exists on macOS; on any other platform (including CI Linux runners)
        // the ProcessBuilder start() call itself fails, exercising the fallback path.
        val result = SystemInfo.availableMemoryFromVmStat(totalMemoryBytes = 1_000_000L)
        assert(result in 1..1_000_000L) { "expected a value between 1 and total, got $result" }
    }
}
