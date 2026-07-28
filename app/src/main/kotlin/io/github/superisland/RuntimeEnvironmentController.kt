package io.github.superisland

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
                publish()
            },
        )?.cancel(true)
    }

    private fun publish() {
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
        )
}
