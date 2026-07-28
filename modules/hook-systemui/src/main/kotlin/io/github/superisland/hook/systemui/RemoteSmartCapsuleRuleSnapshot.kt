package io.github.superisland.hook.systemui

import android.content.SharedPreferences
import io.github.superisland.model.SmartCapsuleConfigSnapshot
import io.github.superisland.model.SmartCapsuleRemoteSnapshotSelector
import io.github.superisland.model.SystemUiSmartCapsuleContract
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal interface SmartCapsulePreferenceValues {
    fun getInt(key: String, fallback: Int): Int

    fun getLong(key: String, fallback: Long): Long

    fun getString(key: String, fallback: String?): String?
}

/** Decodes one complete A/B slot and publishes it as an immutable hot-path snapshot. */
internal class RemoteSmartCapsuleRuleSnapshot(
    private val values: SmartCapsulePreferenceValues,
    private val expectedUserId: Int,
) {
    private val current = AtomicReference<SmartCapsuleConfigSnapshot?>(null)

    @Synchronized
    fun reload(): SmartCapsuleConfigSnapshot? {
        val previous = current.get()
        val decoded = decodeActiveOrNull(previous?.revision ?: 1L)
        if (
            decoded != null &&
            (
                previous == null ||
                    decoded.revision > previous.revision ||
                    decoded == previous
            )
        ) {
            current.set(decoded)
        }
        return current.get()
    }

    fun current(): SmartCapsuleConfigSnapshot? = current.get()

    fun isStale(publishedRevision: Long): Boolean =
        publishedRevision > (current.get()?.revision ?: -1L)

    private fun decodeActiveOrNull(minimumRevision: Long): SmartCapsuleConfigSnapshot? =
        runCatching {
            val activeSlot = values.getInt(SystemUiSmartCapsuleContract.KEY_ACTIVE_SLOT, -1)
            val activeRevision =
                values.getLong(SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION, -1L)
            SmartCapsuleRemoteSnapshotSelector.selectPublishedOrNull(
                activeSlot = activeSlot,
                activeRevision = activeRevision,
                slotAJson =
                    values.getString(
                        SystemUiSmartCapsuleContract.slotSnapshotJsonKey(
                            SystemUiSmartCapsuleContract.SLOT_A,
                        ),
                        null,
                    ),
                slotBJson =
                    values.getString(
                        SystemUiSmartCapsuleContract.slotSnapshotJsonKey(
                            SystemUiSmartCapsuleContract.SLOT_B,
                        ),
                        null,
                    ),
                expectedUserId = expectedUserId,
                minimumRevision = minimumRevision,
            )?.snapshot?.toConfigSnapshot()
        }.getOrNull()
}

internal class SharedPreferencesRuleSnapshot(
    private val preferences: SharedPreferences,
    expectedUserId: Int,
) : SmartCapsulePreferenceValues {
    private val snapshot = RemoteSmartCapsuleRuleSnapshot(this, expectedUserId)
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(CONFIG_PREFIX)) {
                scheduleReload()
            }
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        snapshot.reload()
    }

    /**
     * Reads only the active revision on the notification thread. A newer publication keeps the
     * current notification ordinary while the full JSON snapshot is decoded in the background.
     */
    fun currentIfPublished(): SmartCapsuleConfigSnapshot? {
        val cached = snapshot.current()
        val activeRevision = getLong(SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION, -1L)
        if (snapshot.isStale(activeRevision)) {
            scheduleReload()
            return null
        }
        return cached
    }

    fun reload(): SmartCapsuleConfigSnapshot? = snapshot.reload()

    fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun scheduleReload() {
        LISTENER_EXECUTOR.execute { reload() }
    }

    override fun getInt(key: String, fallback: Int): Int =
        runCatching { preferences.getInt(key, fallback) }.getOrDefault(fallback)

    override fun getLong(key: String, fallback: Long): Long =
        runCatching { preferences.getLong(key, fallback) }.getOrDefault(fallback)

    override fun getString(key: String, fallback: String?): String? =
        runCatching { preferences.getString(key, fallback) }.getOrDefault(fallback)

    companion object {
        private const val CONFIG_PREFIX = "config."
        private val LISTENER_EXECUTOR =
            ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                ArrayBlockingQueue(1),
                { runnable ->
                    Thread(runnable, "SuperIslandRulePrefs").apply { isDaemon = true }
                },
                ThreadPoolExecutor.DiscardOldestPolicy(),
            )
    }
}
