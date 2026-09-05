package io.github.rajveer43.veloxquant.memory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemoryEstimatorTest {
    private val workload = WorkloadSpec(seqLen = 4096, nLayers = 32, nKvHeads = 8, headDim = 128)

    @Test
    fun `fp16 byte count matches closed-form seqLen x layers x heads x headDim x 2 tensors x 2 bytes`() {
        val estimate = MemoryEstimator.estimate(workload, CompressionConfig(method = "turboquant_rvq", bits = 16))

        val expectedFp16Bytes = 4096L * 32L * 8L * 128L * 2L * 2L
        assertEquals(expectedFp16Bytes, estimate.kvFp16Bytes)
    }

    @Test
    fun `lower bit width yields a smaller compressed estimate than a higher bit width`() {
        val low = MemoryEstimator.estimate(workload, CompressionConfig(method = "kivi", bits = 2))
        val high = MemoryEstimator.estimate(workload, CompressionConfig(method = "kivi", bits = 8))

        assertTrue(low.kvCompressedEstimateBytes < high.kvCompressedEstimateBytes)
    }

    @Test
    fun `zero-length sequence produces zero bytes and never divides by zero`() {
        val zeroLenWorkload = workload.copy(seqLen = 0)

        val estimate = MemoryEstimator.estimate(zeroLenWorkload, CompressionConfig(method = "gear", bits = 4))

        assertEquals(0L, estimate.kvFp16Bytes)
        assertEquals(0L, estimate.kvCompressedEstimateBytes)
        assertEquals(0L, estimate.runtimeOverheadBytes)
    }

    @Test
    fun `batch size greater than 1 scales fp16 bytes linearly`() {
        val batchOne = MemoryEstimator.estimate(workload, CompressionConfig(method = "kvquant", bits = 4))
        val batchFour =
            MemoryEstimator.estimate(workload.copy(batchSize = 4), CompressionConfig(method = "kvquant", bits = 4))

        assertEquals(batchOne.kvFp16Bytes * 4, batchFour.kvFp16Bytes)
    }

    @Test
    fun `unknown compression method name still returns a best-effort bits-based estimate rather than throwing`() {
        val estimate =
            MemoryEstimator.estimate(workload, CompressionConfig(method = "not_a_real_method", bits = 4))

        assertTrue(estimate.kvCompressedEstimateBytes > 0)
    }

    @Test
    fun `fitsInBytes reflects compressed plus overhead against the available budget`() {
        val estimate = MemoryEstimator.estimate(workload, CompressionConfig(method = "kivi", bits = 2))
        val requiredBytes = estimate.kvCompressedEstimateBytes + estimate.runtimeOverheadBytes

        assertTrue(estimate.fitsInBytes(requiredBytes))
        assertFalse(estimate.fitsInBytes(requiredBytes - 1))
    }

    @Test
    fun `accountingOnly is always true and accountingNote is never blank`() {
        val estimate = MemoryEstimator.estimate(workload, CompressionConfig(method = "turboquant_rvq", bits = 3))

        assertTrue(estimate.accountingOnly)
        assertTrue(estimate.accountingNote.isNotBlank())
        assertEquals(ACCOUNTING_WARNING_TEXT, estimate.accountingNote)
    }
}
