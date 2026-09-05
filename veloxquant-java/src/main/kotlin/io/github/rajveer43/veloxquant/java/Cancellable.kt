package io.github.rajveer43.veloxquant.java

import kotlinx.coroutines.Job

/** Wraps a [Job] so Java callers can cancel a stream explicitly — Java has no structured-concurrency scope. */
public class Cancellable internal constructor(private val job: Job) {
    /** Cancels the underlying stream's coroutine [Job]. */
    public fun cancel() {
        job.cancel()
    }

    /** Whether the underlying stream is still running. */
    public val isActive: Boolean
        get() = job.isActive
}
