package io.github.superisland.ui.extensions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.superisland.MiShareFolderExtensionStore
import io.github.superisland.model.MiShareFolderRedirectConfig
import io.github.superisland.model.MiShareFolderRedirectContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class MiShareFolderExtensionCapability(
    val miShareInstalled: Boolean = false,
    val mtShortcutAvailable: Boolean = false,
) {
    val ready: Boolean
        get() = miShareInstalled && mtShortcutAvailable

    companion object {
        fun inspect(context: Context): MiShareFolderExtensionCapability {
            val packageManager = context.packageManager
            val miShareInstalled =
                runCatching {
                    packageManager.getPackageInfo(MiShareFolderRedirectContract.MISHARE_PACKAGE, 0)
                }.isSuccess
            val shortcutIntent =
                Intent(MiShareFolderRedirectContract.MT_MANAGER_SHORTCUT_ACTION)
                    .setComponent(
                        ComponentName(
                            MiShareFolderRedirectContract.MT_MANAGER_PACKAGE,
                            MiShareFolderRedirectContract.MT_MANAGER_SHORTCUT_ACTIVITY,
                        ),
                    )
            val activity =
                runCatching {
                    packageManager
                        .resolveActivity(shortcutIntent, PackageManager.MATCH_DEFAULT_ONLY)
                        ?.activityInfo
                }.getOrNull()
            return MiShareFolderExtensionCapability(
                miShareInstalled = miShareInstalled,
                mtShortcutAvailable =
                    activity?.exported == true &&
                        activity.packageName == MiShareFolderRedirectContract.MT_MANAGER_PACKAGE &&
                        activity.name == MiShareFolderRedirectContract.MT_MANAGER_SHORTCUT_ACTIVITY,
            )
        }
    }
}

internal data class MiShareFolderExtensionUiState(
    val enabled: Boolean = false,
    val capability: MiShareFolderExtensionCapability = MiShareFolderExtensionCapability(),
    val contentLoaded: Boolean = false,
)

/** Process-level state keeps navigation re-entry free of preference and PackageManager work. */
internal object MiShareFolderExtensionUiStateOwner {
    private val lock = Any()
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveRequests = Channel<SaveRequest>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(MiShareFolderExtensionUiState())

    val state: StateFlow<MiShareFolderExtensionUiState> = mutableState.asStateFlow()

    private var prepareInFlight = false
    private var prepared = false
    private var store: MiShareFolderExtensionStore? = null
    private var observerRegistration: (() -> Unit)? = null
    private var persistedEnabled = false
    private var latestRequestGeneration = 0L
    private var pendingWriteCount = 0

    init {
        workerScope.launch {
            for (request in saveRequests) persist(request)
        }
    }

    /** Starts exactly one deferred load/probe and retains the result for the process lifetime. */
    fun prepare(context: Context) {
        val applicationContext = context.applicationContext
        synchronized(lock) {
            if (prepared || prepareInFlight) return
            prepareInFlight = true
        }
        workerScope.launch {
            var candidateObserver: (() -> Unit)? = null
            val result =
                runCatching {
                    val candidateStore = MiShareFolderExtensionStore(applicationContext)
                    // observe() supplies the sole initial preference snapshot; do not call load() too.
                    candidateObserver = candidateStore.observe(::onConfigObserved)
                    val capability = MiShareFolderExtensionCapability.inspect(applicationContext)
                    candidateStore to capability
                }
            result.fold(
                onSuccess = { (loadedStore, capability) ->
                    synchronized(lock) {
                        store = loadedStore
                        observerRegistration = candidateObserver
                        prepared = true
                        prepareInFlight = false
                        mutableState.value =
                            mutableState.value.copy(
                                enabled =
                                    if (pendingWriteCount == 0) {
                                        persistedEnabled
                                    } else {
                                        mutableState.value.enabled
                                    },
                                capability = capability,
                                contentLoaded = true,
                            )
                    }
                },
                onFailure = {
                    candidateObserver?.invoke()
                    synchronized(lock) {
                        prepareInFlight = false
                    }
                },
            )
        }
    }

    /** Applies the visual state immediately; the FIFO writer rolls back only the newest failure. */
    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        val request =
            synchronized(lock) {
                if (mutableState.value.enabled == enabled) return
                latestRequestGeneration += 1
                pendingWriteCount += 1
                mutableState.value = mutableState.value.copy(enabled = enabled)
                SaveRequest(
                    applicationContext = context.applicationContext,
                    generation = latestRequestGeneration,
                    enabled = enabled,
                )
            }
        check(saveRequests.trySend(request).isSuccess) { "Mi Share settings writer is unavailable" }
    }

    private fun onConfigObserved(config: MiShareFolderRedirectConfig) {
        synchronized(lock) {
            persistedEnabled = config.enabled
            if (prepared && pendingWriteCount == 0) {
                mutableState.value = mutableState.value.copy(enabled = config.enabled)
            }
        }
    }

    private fun persist(request: SaveRequest) {
        val result =
            runCatching {
                val targetStore =
                    synchronized(lock) { store }
                        ?: MiShareFolderExtensionStore(request.applicationContext)
                targetStore
                    .save(MiShareFolderRedirectConfig(enabled = request.enabled))
                    .getOrThrow()
            }
        synchronized(lock) {
            pendingWriteCount = (pendingWriteCount - 1).coerceAtLeast(0)
            if (result.isSuccess) {
                persistedEnabled = request.enabled
            } else if (request.generation == latestRequestGeneration) {
                mutableState.value = mutableState.value.copy(enabled = persistedEnabled)
            }
        }
    }

    private data class SaveRequest(
        val applicationContext: Context,
        val generation: Long,
        val enabled: Boolean,
    )
}
