package io.github.rajveer43.veloxquant.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Proves the sealed [VeloxQuantException] hierarchy is exhaustive-`when`-friendly (plan §4's
 * stated Kotlin-idiom goal): this `when` has no `else` branch and must still compile. If a
 * new subtype is ever added to the hierarchy without updating this function, the module fails
 * to compile — a deliberate, cheap tripwire.
 */
class VeloxQuantExceptionExhaustivenessTest {
    private fun describe(exception: VeloxQuantException): String =
        when (exception) {
            is VeloxQuantException.RuntimeUnreachable -> "RuntimeUnreachable"
            is VeloxQuantException.GenerationFailed -> "GenerationFailed"
            is VeloxQuantException.UnexpectedRoute -> "UnexpectedRoute"
            is VeloxQuantException.MalformedErrorResponse -> "MalformedErrorResponse"
            is VeloxQuantException.AutopilotWontFit -> "AutopilotWontFit"
            is VeloxQuantException.CliNotInstalled -> "CliNotInstalled"
            is VeloxQuantException.UnsupportedPlatform -> "UnsupportedPlatform"
            is VeloxQuantException.MalformedStructuredOutput -> "MalformedStructuredOutput"
            is VeloxQuantException.ServeStartupTimeout -> "ServeStartupTimeout"
            is VeloxQuantException.ServeProcessExited -> "ServeProcessExited"
        }

    @Test
    fun `describe() exhaustively covers every subtype without an else branch`() {
        assertEquals("UnexpectedRoute", describe(VeloxQuantException.UnexpectedRoute("/nope")))
        assertEquals("GenerationFailed", describe(VeloxQuantException.GenerationFailed("boom")))
    }
}
