package io.github.superisland

import android.os.Handler
import android.os.Looper
import io.github.superisland.model.SmartCapsuleConfigSnapshot
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Preserves the user's configuration gesture order across every Compose destination. */
internal object SmartCapsuleConfigMutationQueue {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(MAX_PENDING_MUTATIONS),
            { runnable ->
                Thread(runnable, "SuperIslandConfigMutations").apply { isDaemon = true }
            },
            ThreadPoolExecutor.AbortPolicy(),
        )

    fun submit(
        store: SmartCapsuleConfigStore,
        transform: (SmartCapsuleConfigSnapshot) -> SmartCapsuleConfigSnapshot,
        onComplete: (Result<SmartCapsuleConfigSnapshot>) -> Unit = {},
    ) {
        try {
            executor.execute {
                val result = store.update(transform)
                mainHandler.post { onComplete(result) }
            }
        } catch (error: RejectedExecutionException) {
            mainHandler.post {
                onComplete(
                    Result.failure(
                        IllegalStateException("Too many pending smart-capsule configuration changes", error),
                    ),
                )
            }
        }
    }

    private const val MAX_PENDING_MUTATIONS = 32
}
