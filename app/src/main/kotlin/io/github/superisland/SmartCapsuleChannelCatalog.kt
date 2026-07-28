package io.github.superisland

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import io.github.superisland.model.AppRule
import io.github.superisland.model.ChannelSelection
import io.github.superisland.model.SystemUiSmartCapsuleContract
import org.json.JSONArray
import org.json.JSONObject

data class SmartCapsuleChannelEntry(
    val id: String,
    val name: String,
    val importance: Int,
)

internal enum class SmartCapsuleChannelCatalogStatus {
    NOT_LOADED,
    LOADING,
    LOADED,
}

internal data class SmartCapsuleChannelCatalogSnapshot(
    val status: SmartCapsuleChannelCatalogStatus,
    val entries: List<SmartCapsuleChannelEntry> = emptyList(),
) {
    companion object {
        val NOT_LOADED = SmartCapsuleChannelCatalogSnapshot(SmartCapsuleChannelCatalogStatus.NOT_LOADED)
    }
}

/** App-private cache of Channel metadata returned by NotificationManagerService through SystemUI. */
class SmartCapsuleChannelCatalogStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val cacheLock = Any()
    private val cache = mutableMapOf<String, CachedChannels>()

    fun load(packageName: String): List<SmartCapsuleChannelEntry> = loadSnapshot(packageName).entries

    internal fun loadSnapshot(packageName: String): SmartCapsuleChannelCatalogSnapshot {
        val normalizedPackage =
            AppRule.normalizePackageName(packageName) ?: return SmartCapsuleChannelCatalogSnapshot.NOT_LOADED
        val encoded =
            preferences.getString(normalizedPackage, null)
                ?: return SmartCapsuleChannelCatalogSnapshot.NOT_LOADED
        synchronized(cacheLock) {
            cache[normalizedPackage]
                ?.takeIf { cached -> cached.encoded == encoded }
                ?.let(CachedChannels::snapshot)
                ?.let { return it }
        }
        val entries = runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length().coerceAtMost(MAX_CHANNELS)) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString(JSON_ID)
                    if (!ChannelSelection.isValidChannelId(id)) continue
                    val name = item.optString(JSON_NAME).sanitized(MAX_CHANNEL_NAME_LENGTH).ifEmpty { id }
                    val importance =
                        item.optInt(JSON_IMPORTANCE, NotificationManager.IMPORTANCE_UNSPECIFIED)
                            .coerceIn(NotificationManager.IMPORTANCE_UNSPECIFIED, NotificationManager.IMPORTANCE_MAX)
                    add(SmartCapsuleChannelEntry(id, name, importance))
                }
            }.distinctBy(SmartCapsuleChannelEntry::id).sortedBy(SmartCapsuleChannelEntry::id)
        }.getOrDefault(emptyList())
        synchronized(cacheLock) {
            cache[normalizedPackage] =
                CachedChannels(
                    encoded = encoded,
                    snapshot =
                        SmartCapsuleChannelCatalogSnapshot(
                            status = SmartCapsuleChannelCatalogStatus.LOADED,
                            entries = entries,
                        ),
                )
        }
        return SmartCapsuleChannelCatalogSnapshot(
            status = SmartCapsuleChannelCatalogStatus.LOADED,
            entries = entries,
        )
    }

    /** Pure in-process lookup. It never touches SharedPreferences or parses JSON. */
    internal fun cachedSnapshot(packageName: String): SmartCapsuleChannelCatalogSnapshot? {
        val normalizedPackage = AppRule.normalizePackageName(packageName) ?: return null
        return synchronized(cacheLock) {
            cache[normalizedPackage]?.snapshot
        }
    }

    fun cached(packageName: String): List<SmartCapsuleChannelEntry>? =
        cachedSnapshot(packageName)?.entries

    fun observe(
        packageName: String? = null,
        onChanged: () -> Unit,
    ): () -> Unit {
        val observedPackage = packageName?.let(AppRule::normalizePackageName)
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, changedPackage ->
                if (observedPackage == null || changedPackage == observedPackage) onChanged()
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    internal fun updateFrom(extras: Bundle): Boolean {
        if (
            extras.getInt(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_USER_ID, -1) !=
            SmartCapsuleConfigStore.currentProcessUserId()
        ) {
            return false
        }
        val packageName =
            AppRule.normalizePackageName(
                extras.getString(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_PACKAGE).orEmpty(),
            ) ?: return false
        val ids = extras.getStringArrayList(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_IDS) ?: return false
        val names = extras.getStringArrayList(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_NAMES) ?: return false
        val importance = extras.getIntArray(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_IMPORTANCE) ?: return false
        if (ids.size != names.size || ids.size != importance.size || ids.size > MAX_CHANNELS) return false

        val channels =
            ids.indices
                .mapNotNull { index ->
                    val id = ids[index]
                    if (!ChannelSelection.isValidChannelId(id)) return@mapNotNull null
                    SmartCapsuleChannelEntry(
                        id = id,
                        name = names[index].sanitized(MAX_CHANNEL_NAME_LENGTH).ifEmpty { id },
                        importance =
                            importance[index].coerceIn(
                                NotificationManager.IMPORTANCE_UNSPECIFIED,
                                NotificationManager.IMPORTANCE_MAX,
                            ),
                    )
                }.distinctBy(SmartCapsuleChannelEntry::id)
                .sortedBy(SmartCapsuleChannelEntry::id)
        val encoded =
            JSONArray().apply {
                channels.forEach { channel ->
                    put(
                        JSONObject()
                            .put(JSON_ID, channel.id)
                            .put(JSON_NAME, channel.name)
                            .put(JSON_IMPORTANCE, channel.importance),
                    )
                }
            }.toString()
        val committed = preferences.edit().putString(packageName, encoded).commit()
        if (committed) {
            synchronized(cacheLock) {
                cache[packageName] =
                    CachedChannels(
                        encoded = encoded,
                        snapshot =
                            SmartCapsuleChannelCatalogSnapshot(
                                status = SmartCapsuleChannelCatalogStatus.LOADED,
                                entries = channels,
                            ),
                    )
            }
        }
        return committed
    }

    private fun String.sanitized(maxLength: Int): String =
        buildString {
            var offset = 0
            while (offset < this@sanitized.length) {
                val codePoint = Character.codePointAt(this@sanitized, offset)
                offset += Character.charCount(codePoint)
                if (Character.isISOControl(codePoint)) continue
                val width = Character.charCount(codePoint)
                if (length + width > maxLength) break
                appendCodePoint(codePoint)
            }
        }.trim()

    private companion object {
        const val FILE_NAME = "smart-capsule-channel-catalog"
        const val JSON_ID = "id"
        const val JSON_NAME = "name"
        const val JSON_IMPORTANCE = "importance"
        const val MAX_CHANNELS = 128
        const val MAX_CHANNEL_NAME_LENGTH = 160
    }

    private data class CachedChannels(
        val encoded: String,
        val snapshot: SmartCapsuleChannelCatalogSnapshot,
    )
}

internal object SmartCapsuleChannelCatalogReportHandler {
    fun handle(
        context: Context,
        extras: Bundle,
    ): Boolean = SmartCapsuleChannelCatalogStore(context.applicationContext).updateFrom(extras)
}

object SmartCapsuleChannelCatalogController {

    fun request(
        context: Context,
        packageName: String,
    ) {
        val normalizedPackage = AppRule.normalizePackageName(packageName) ?: return
        context.applicationContext.sendBroadcast(
            Intent(SystemUiSmartCapsuleContract.ACTION_REQUEST_CHANNELS)
                .setPackage(SystemUiSmartCapsuleContract.SYSTEM_UI_PACKAGE)
                .putExtra(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_PACKAGE, normalizedPackage)
                .putExtra(
                    SystemUiSmartCapsuleContract.EXTRA_CHANNEL_USER_ID,
                    SmartCapsuleConfigStore.currentProcessUserId(),
                ),
        )
    }
}
