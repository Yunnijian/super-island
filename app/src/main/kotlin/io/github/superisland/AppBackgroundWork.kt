package io.github.superisland

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bounded process queue for startup/reconnect persistence that must never run on the UI thread. */
internal object AppBackgroundWork {
    private val executor =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(MAX_PENDING_WORK),
            { runnable ->
                Thread(runnable, "SuperIslandAppBackground").apply { isDaemon = true }
            },
            ThreadPoolExecutor.DiscardOldestPolicy(),
        )

    fun execute(work: () -> Unit) {
        executor.execute(work)
    }

    private const val MAX_PENDING_WORK = 8
}
