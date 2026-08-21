package io.github.superisland

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.superisland.model.RuntimeEnvironmentLabels
import io.github.superisland.model.RuntimeEnvironmentSnapshot
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

/**
 * Combines asynchronous Root probing with the official libxposed service activation signal.
 *
 * Product UI continues to show only Root and LSPosed states.
 */
object RuntimeEnvironmentController {
    private val listeners = CopyOnWriteArraySet<(RuntimeEnvironmentSnapshot) -> Unit>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "super-island-env-probe").apply { isDaemon = true }
    }
    private val rootProbeInFlight = AtomicReference<Future<*>?>(null)

    @Volatile
    private var rootAvailable: Boolean = false

    @Volatile
    private var rootSummary: String = "未授权"

    @Volatile
    private var lsposedActive: Boolean = false

    @Volatile
    private var lsposedDetail: String = RuntimeEnvironmentLabels.lsposedDetail(active = false)

    @Volatile
    private var lsposedApiVersion: Int? = null

    @Volatile
    private var lsposedVersion: String? = null

    @Volatile
    private var isLoading: Boolean = true

    @Volatile
    private var lsposedReported: Boolean = false

    @Volatile
    private var rootReported: Boolean = false

    private val loadingStartElapsed: Long = SystemClock.elapsedRealtime()
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val MIN_LOADING_MILLIS = 700L

    @Volatile
    private var current: RuntimeEnvironmentSnapshot = buildSnapshot()

    private var xposedSubscription: (() -> Unit)? = null

    @Synchronized
    fun start() {
        if (xposedSubscription != null) return
        XposedRuntimeController.start()
        xposedSubscription =
            XposedRuntimeController.observe { status ->
                lsposedActive = status.active
                lsposedDetail = status.detail
                lsposedApiVersion = status.apiVersion
                lsposedReported = true
                publish()
            }
        refreshRoot()
    }

    fun current(): RuntimeEnvironmentSnapshot = current

    fun observe(listener: (RuntimeEnvironmentSnapshot) -> Unit): () -> Unit {
        listeners += listener
        listener(current)
        return { listeners -= listener }
    }

    fun refreshRoot() {
        XposedRuntimeController.refresh()
        rootProbeInFlight.getAndSet(
            executor.submit {
                rootAvailable = RuntimeEnvironmentProbe.probeRootPermission()
                rootSummary =
                    if (rootAvailable) {
                        RuntimeEnvironmentProbe.probeRootSummary()
                    } else {
                        "未授权"
                    }
                lsposedVersion =
                    if (rootAvailable) {
                        RuntimeEnvironmentProbe.probeLsposedVersion()
                    } else {
                        null
                    }
                rootReported = true
                publish()
            },
        )?.cancel(true)
    }

    private fun publish() {
        // Keep loading placeholder until both probes have reported and the minimum
        // visible loading duration has elapsed. This avoids a sub-second flash where
        // the card would briefly show loading then immediately flip to the final state.
        if (isLoading && lsposedReported && rootReported) {
            val elapsed = SystemClock.elapsedRealtime() - loadingStartElapsed
            if (elapsed < MIN_LOADING_MILLIS) {
                val remaining = MIN_LOADING_MILLIS - elapsed
                mainHandler.removeCallbacksAndMessages(null)
                mainHandler.postDelayed({ publish() }, remaining)
                return
            }
            isLoading = false
        }
        val next = buildSnapshot()
        current = next
        listeners.forEach { it(next) }
    }

    private fun buildSnapshot(): RuntimeEnvironmentSnapshot =
        RuntimeEnvironmentSnapshot(
            rootAvailable = rootAvailable,
            rootDetail = RuntimeEnvironmentLabels.rootDetail(rootAvailable),
            lsposedActive = lsposedActive,
            lsposedDetail = lsposedDetail,
            rootSummary = rootSummary,
            lsposedVersion = lsposedVersion,
            lsposedApiVersion = lsposedApiVersion,
            isLoading = isLoading,
        )
}
