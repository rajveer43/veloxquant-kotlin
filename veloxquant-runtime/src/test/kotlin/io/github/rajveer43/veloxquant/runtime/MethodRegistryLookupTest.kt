package io.github.rajveer43.veloxquant.runtime

import io.github.rajveer43.veloxquant.core.TelemetryCoverage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MethodRegistryLookupTest {
    @Test
    fun `listMethods maps CLI payloads to CompressionMethod, defaulting unknown telemetry coverage to NONE`() {
        val stdout =
            """
            [{"name":"turboquant_rvq","family":"rvq","servable":true,"telemetry_coverage":"FULL"},
            {"name":"experimental","family":"x","servable":false,"telemetry_coverage":"something_new"}]
            """.trimIndent()
        val shellOut = CliShellOut(processRunner = { FakeProcess(stdout = stdout) }, pathResolver = { true })

        val methods = listMethods(shellOut)

        assertEquals(2, methods.size)
        assertEquals(TelemetryCoverage.FULL, methods[0].telemetryCoverage)
        assertEquals(TelemetryCoverage.NONE, methods[1].telemetryCoverage)
        assertEquals(false, methods[1].servable)
    }

    @Test
    fun `listLocalModels returns an empty list when the cache directory does not exist`(
        @TempDir tempDir: File,
    ) {
        val nonExistent = File(tempDir, "does-not-exist")

        val models = listLocalModels(nonExistent)

        assertTrue(models.isEmpty())
    }

    @Test
    fun `listLocalModels scans models-- prefixed directories and converts repo id separators`(
        @TempDir tempDir: File,
    ) {
        val modelDir = File(tempDir, "models--mlx-community--Llama-3-8B-4bit")
        modelDir.mkdirs()
        File(modelDir, "weights.safetensors").writeBytes(ByteArray(1024))
        File(tempDir, "not-a-model-dir").mkdirs()

        val models = listLocalModels(tempDir)

        assertEquals(1, models.size)
        assertEquals("mlx-community/Llama-3-8B-4bit", models[0].repoId)
        assertEquals(1024L, models[0].sizeBytes)
    }
}
