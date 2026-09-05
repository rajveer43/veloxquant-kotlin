package io.github.rajveer43.veloxquant.runtime

/** Shared [RunningProcess] test double for shell-out tests — feeds canned output without a real subprocess. */
internal data class FakeProcess(
    override val stdout: String,
    override val stderr: String = "",
    override val exitCode: Int = 0,
) : RunningProcess
