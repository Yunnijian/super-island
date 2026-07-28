package io.github.superisland

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import io.github.superisland.model.SmartCapsuleConfigSnapshot
import io.github.superisland.model.SmartCapsuleRemoteSnapshot
import io.github.superisland.model.SmartCapsuleRemoteSnapshotSelector
import io.github.superisland.model.SystemUiSmartCapsuleContract
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Atomically mirrors app-owned smart-capsule rules into an independent RemotePreferences file. */
object SmartCapsuleHostConfigSync {
    private const val TAG = "SuperIslandCapsuleConfig"
    private val SYNC_LOCK = Any()
    private val SYNC_EXECUTOR =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
            { runnable ->
                Thread(runnable, "SuperIslandConfigSync").apply { isDaemon = true }
            },
            ThreadPoolExecutor.DiscardOldestPolicy(),
        )

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            appContext = context.applicationContext
            XposedRuntimeController.start()
            XposedRuntimeController.observe { status ->
                SYNC_EXECUTOR.execute {
                    if (status.active) {
                        syncStoredAndReload()
                    } else {
                        SmartCapsuleRuntimeStatusController.markLsposedUnavailable(
                            checkNotNull(appContext),
                        )
                    }
                }
            }
            started = true
        }
    }

    fun sync(snapshot: SmartCapsuleConfigSnapshot): Result<Unit> =
        runCatching {
            synchronized(SYNC_LOCK) {
                val context = checkNotNull(appContext) { "Smart-capsule config sync has not started" }
                val requested = snapshot.normalized()
                require(requested.revision > 0L) { "Smart-capsule revision must be positive" }
                require(requested.userId == SmartCapsuleConfigStore.currentProcessUserId()) {
                    "Smart-capsule snapshot belongs to another Android user"
                }
                val latest = SmartCapsuleConfigStore(context).load().normalized()
                require(latest.userId == requested.userId) {
                    "Newest smart-capsule snapshot belongs to another Android user"
                }
                val normalized =
                    if (latest.revision >= requested.revision) latest else requested
                val preferences =
                    checkNotNull(
                        XposedRuntimeController.remotePreferences(
                            SystemUiSmartCapsuleContract.REMOTE_PREFERENCES,
                        ),
                    ) { "LSPosed module service is not active" }
                val activeRevision =
                    preferences.getLong(SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION, -1L)
                val active = readActiveSnapshot(preferences, normalized.userId, minimumRevision = 0L)
                if (activeRevision > normalized.revision) {
                    error("Refusing to publish an older smart-capsule revision")
                }
                if (activeRevision == normalized.revision) {
                    require(
                        active != null &&
                            active.userId == normalized.userId &&
                            active.revision == normalized.revision &&
                            active == normalized,
                    ) { "Smart-capsule revision collision" }
                }
                SmartCapsuleConsumerAcceptanceStore(context).prepare(normalized)
                if (active != normalized || activeRevision != normalized.revision) {
                    publishToInactiveSlot(preferences, normalized)
                    check(
                        readActiveSnapshot(
                            preferences,
                            normalized.userId,
                            minimumRevision = normalized.revision,
                        ) == normalized,
                    ) {
                        "Could not verify active smart-capsule configuration"
                    }
                }
                requestSystemUiReload(context)
            }
        }

    fun syncStoredAndReload() {
        val context = appContext ?: return
        sync(SmartCapsuleConfigStore(context).load()).onFailure { error ->
            Log.w(TAG, "Smart-capsule settings will retry when the LSPosed service reconnects", error)
        }
    }

    internal fun publishToInactiveSlot(
        preferences: SharedPreferences,
        snapshot: SmartCapsuleConfigSnapshot,
    ) {
        val normalized = snapshot.normalized()
        require(normalized.revision > 0L) { "Smart-capsule revision must be positive" }
        val activeSlot =
            runCatching {
                preferences.getInt(SystemUiSmartCapsuleContract.KEY_ACTIVE_SLOT, -1)
            }.getOrDefault(-1)
        val activeRevision =
            runCatching {
                preferences.getLong(SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION, -1L)
            }.getOrDefault(-1L)
        require(activeRevision <= normalized.revision) {
            "Refusing to publish an older smart-capsule revision"
        }
        val published = readActiveSnapshot(preferences)
        if (activeRevision == normalized.revision) {
            require(
                published != null &&
                    published.revision == normalized.revision &&
                    published == normalized,
            ) { "Smart-capsule revision collision" }
        }

        val pointedSnapshot =
            readSlotSnapshot(preferences, activeSlot)
                ?.takeIf { remote -> remote.revision == activeRevision }
        val alternateSlot =
            if (SystemUiSmartCapsuleContract.isSlot(activeSlot)) {
                SystemUiSmartCapsuleContract.alternateSlot(activeSlot)
            } else {
                SystemUiSmartCapsuleContract.SLOT_A
            }
        val alternateSnapshot = readSlotSnapshot(preferences, alternateSlot)
        val targetSlot =
            when {
                !SystemUiSmartCapsuleContract.isSlot(activeSlot) -> SystemUiSmartCapsuleContract.SLOT_A
                pointedSnapshot != null -> alternateSlot
                alternateSnapshot != null && alternateSnapshot.revision < activeRevision -> activeSlot
                else -> alternateSlot
            }
        val snapshotJson = SmartCapsuleRemoteSnapshot.encode(normalized)

        // Each slot is one complete document. A failed activation leaves the previous slot intact.
        check(
            preferences.edit()
                .putString(SystemUiSmartCapsuleContract.slotSnapshotJsonKey(targetSlot), snapshotJson)
                .commit(),
        ) { "Could not persist inactive smart-capsule config slot" }

        // One separate commit is the atomic publication point observed by SystemUI.
        check(
            preferences.edit()
                .putInt(SystemUiSmartCapsuleContract.KEY_ACTIVE_SLOT, targetSlot)
                .putLong(SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION, normalized.revision)
                .commit(),
        ) { "Could not activate smart-capsule config slot" }
    }

    internal fun readActiveSnapshot(preferences: SharedPreferences): SmartCapsuleConfigSnapshot? =
        readPublishedRemoteSnapshot(preferences)?.toConfigSnapshot()

    internal fun readActiveSnapshot(
        preferences: SharedPreferences,
        expectedUserId: Int,
        minimumRevision: Long,
    ): SmartCapsuleConfigSnapshot? =
        readPublishedRemoteSnapshot(preferences, expectedUserId, minimumRevision)?.toConfigSnapshot()

    private fun readPublishedRemoteSnapshot(preferences: SharedPreferences): SmartCapsuleRemoteSnapshot? =
        runCatching {
            val activeSlot = preferences.getInt(SystemUiSmartCapsuleContract.KEY_ACTIVE_SLOT, -1)
            if (!SystemUiSmartCapsuleContract.isSlot(activeSlot)) return@runCatching null
            val activeRevision =
                preferences.getLong(SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION, -1L)
            if (activeRevision <= 0L) return@runCatching null
            val active = readSlotSnapshot(preferences, activeSlot)
            if (active?.revision == activeRevision) return@runCatching active

            val fallback =
                readSlotSnapshot(
                    preferences,
                    SystemUiSmartCapsuleContract.alternateSlot(activeSlot),
                )
            fallback?.takeIf { remote -> remote.revision < activeRevision }
        }.getOrNull()

    private fun readPublishedRemoteSnapshot(
        preferences: SharedPreferences,
        expectedUserId: Int,
        minimumRevision: Long,
    ): SmartCapsuleRemoteSnapshot? =
        runCatching {
            val selected =
                SmartCapsuleRemoteSnapshotSelector.selectPublishedOrNull(
                    activeSlot = preferences.getInt(SystemUiSmartCapsuleContract.KEY_ACTIVE_SLOT, -1),
                    activeRevision =
                        preferences.getLong(
                            SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION,
                            -1L,
                        ),
                    slotAJson =
                        preferences.getString(
                            SystemUiSmartCapsuleContract.slotSnapshotJsonKey(
                                SystemUiSmartCapsuleContract.SLOT_A,
                            ),
                            null,
                        ),
                    slotBJson =
                        preferences.getString(
                            SystemUiSmartCapsuleContract.slotSnapshotJsonKey(
                                SystemUiSmartCapsuleContract.SLOT_B,
                            ),
                            null,
                        ),
                    expectedUserId = expectedUserId,
                    minimumRevision = minimumRevision,
                )
            selected?.snapshot
        }.getOrNull()

    private fun readSlotSnapshot(
        preferences: SharedPreferences,
        slot: Int,
    ): SmartCapsuleRemoteSnapshot? {
        if (!SystemUiSmartCapsuleContract.isSlot(slot)) return null
        return SmartCapsuleRemoteSnapshot.decodeOrNull(
            preferences.getString(SystemUiSmartCapsuleContract.slotSnapshotJsonKey(slot), null),
        )
    }

    private fun requestSystemUiReload(context: Context) {
        SmartCapsuleRuntimeStatusController.expectReport(context)
        listOf(
            SystemUiSmartCapsuleContract.SYSTEM_UI_PACKAGE,
            SystemUiSmartCapsuleContract.XMSF_PACKAGE,
        ).forEach { targetPackage ->
            context.sendBroadcast(
                Intent(SystemUiSmartCapsuleContract.ACTION_RELOAD_CONFIG)
                    .setPackage(targetPackage),
            )
        }
    }

}
