package io.github.superisland.source.notification

import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationManagerCompat
import io.github.superisland.event.NotificationProxyPrivacyMode
import io.github.superisland.event.NotificationProxyRule
import io.github.superisland.event.NotificationRejectionReason

data class ObservedNotificationSource(
    val packageName: String,
    val channelId: String,
    val appLabel: String,
) {
    val id: String
        get() = "$packageName\t$channelId"
}

data class NotificationProxySnapshot(
    val featureEnabled: Boolean,
    val notificationAccessGranted: Boolean,
    val listenerConnected: Boolean,
    val privacyMode: NotificationProxyPrivacyMode,
    val enabledSources: List<ObservedNotificationSource>,
    val candidates: List<ObservedNotificationSource>,
    val activeProxyCount: Int,
    val lastResult: String,
    val lastDiagnostic: String?,
)

data class ObservedMediaSource(
    val packageName: String,
    val appLabel: String,
) {
    val id: String
        get() = packageName
}

data class MediaIslandSnapshot(
    val featureEnabled: Boolean,
    val notificationAccessGranted: Boolean,
    val listenerConnected: Boolean,
    val enabledSources: List<ObservedMediaSource>,
    val candidates: List<ObservedMediaSource>,
    val activeMediaCount: Int,
    val lastResult: String,
)

data class MediaRuleToggle(
    val source: ObservedMediaSource,
    val enabled: Boolean,
)

data class NotificationRuleToggle(
    val source: ObservedNotificationSource,
    val enabled: Boolean,
)

class NotificationProxyController(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = NotificationProxyPreferences(appContext)

    fun snapshot(): NotificationProxySnapshot =
        NotificationProxySnapshot(
            featureEnabled = preferences.notificationFeatureEnabled(),
            notificationAccessGranted =
                NotificationManagerCompat.getEnabledListenerPackages(appContext)
                    .contains(appContext.packageName),
            listenerConnected = NotificationProxyService.isConnected(),
            privacyMode = preferences.loadPrivacyMode(),
            enabledSources = preferences.loadEnabledSources(),
            candidates = preferences.loadCandidates(),
            activeProxyCount = preferences.activeProxyCount(),
            lastResult = preferences.lastResult(),
            lastDiagnostic = preferences.lastDiagnostic(),
        )

    fun toggleCandidate(id: String): NotificationRuleToggle? {
        val candidate = preferences.loadCandidates().firstOrNull { it.id == id } ?: return null
        val enabled = preferences.toggleRule(candidate)
        NotificationProxyService.onRuleChanged(appContext)
        return NotificationRuleToggle(candidate, enabled)
    }

    fun clearRules() {
        preferences.clearRules()
        NotificationProxyService.onRuleChanged(appContext)
    }

    /** Retained only for migrating the former listener-based Smart Capsule preference. */
    fun setNotificationFeatureEnabled(enabled: Boolean) {
        preferences.setNotificationFeatureEnabled(enabled)
        NotificationProxyService.onRuleChanged(appContext)
    }

    fun setPrivacyMode(mode: NotificationProxyPrivacyMode) {
        preferences.savePrivacyMode(mode)
        NotificationProxyService.onRuleChanged(appContext)
    }

    fun refresh() {
        NotificationProxyService.refresh(appContext)
    }

    fun observeSnapshotChanges(onChanged: () -> Unit): () -> Unit =
        preferences.observeChanges(onChanged)

    fun mediaSnapshot(): MediaIslandSnapshot =
        MediaIslandSnapshot(
            featureEnabled = preferences.mediaFeatureEnabled(),
            notificationAccessGranted =
                NotificationManagerCompat.getEnabledListenerPackages(appContext)
                    .contains(appContext.packageName),
            listenerConnected = NotificationProxyService.isConnected(),
            enabledSources = preferences.loadEnabledMediaSources(),
            candidates = preferences.loadMediaCandidates(),
            activeMediaCount = preferences.activeMediaCount(),
            lastResult = preferences.lastMediaResult(),
        )

    fun toggleMediaCandidate(id: String): MediaRuleToggle? {
        val candidate = preferences.loadMediaCandidates().firstOrNull { it.id == id } ?: return null
        val enabled = preferences.toggleMediaRule(candidate)
        NotificationProxyService.onMediaRuleChanged(appContext)
        return MediaRuleToggle(candidate, enabled)
    }

    fun clearMediaRules() {
        preferences.clearMediaRules()
        NotificationProxyService.onMediaRuleChanged(appContext)
    }

    /** Stops new music-island publishes without discarding the user's player allowlist. */
    fun setMediaFeatureEnabled(enabled: Boolean) {
        preferences.setMediaFeatureEnabled(enabled)
        NotificationProxyService.onMediaRuleChanged(appContext)
    }

    fun refreshMedia() {
        NotificationProxyService.refreshMedia(appContext)
    }

    /** Restores legacy and media preferences without revoking the media listener system grant. */
    fun resetAllSettings() {
        preferences.resetAllSettings()
        NotificationProxyService.onRuleChanged(appContext)
        NotificationProxyService.onMediaRuleChanged(appContext)
    }
}

internal class NotificationProxyPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private fun loadLegacyRule(): ObservedNotificationSource? {
        if (!preferences.getBoolean(KEY_RULE_ENABLED, false)) return null
        val packageName = preferences.getString(KEY_RULE_PACKAGE, null) ?: return null
        val channelId = preferences.getString(KEY_RULE_CHANNEL, null) ?: return null
        val appLabel = preferences.getString(KEY_RULE_LABEL, packageName) ?: packageName
        return runCatching { ObservedNotificationSource(packageName, channelId, appLabel) }.getOrNull()
    }

    fun loadEnabledSources(): List<ObservedNotificationSource> {
        val stored =
            preferences.getStringSet(KEY_RULES, null)
                ?.mapNotNull(::decodeCandidate)
                .orEmpty()
        return (if (stored.isNotEmpty()) stored else listOfNotNull(loadLegacyRule()))
            .distinctBy(ObservedNotificationSource::id)
            .sortedWith(compareBy<ObservedNotificationSource> { it.appLabel }.thenBy { it.id })
    }

    fun loadRules(): Set<NotificationProxyRule> =
        loadEnabledSources()
            .map { NotificationProxyRule(it.packageName, it.channelId) }
            .toSet()

    fun loadPrivacyMode(): NotificationProxyPrivacyMode =
        NotificationProxyPrivacyMode.fromStored(preferences.getString(KEY_PRIVACY_MODE, null))

    fun notificationFeatureEnabled(): Boolean =
        preferences.getBoolean(KEY_NOTIFICATION_FEATURE_ENABLED, true)

    fun setNotificationFeatureEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_NOTIFICATION_FEATURE_ENABLED, enabled).apply()
    }

    fun savePrivacyMode(mode: NotificationProxyPrivacyMode) {
        preferences.edit().putString(KEY_PRIVACY_MODE, mode.name).apply()
    }

    fun toggleRule(source: ObservedNotificationSource): Boolean {
        val enabled = loadEnabledSources().associateBy(ObservedNotificationSource::id).toMutableMap()
        val wasEnabled = enabled.remove(source.id) != null
        if (!wasEnabled) enabled[source.id] = source
        saveRules(enabled.values)
        return !wasEnabled
    }

    fun clearRules() = saveRules(emptyList())

    private fun saveRules(sources: Collection<ObservedNotificationSource>) {
        preferences.edit()
            .putStringSet(KEY_RULES, sources.map(::encodeCandidate).toSet())
            .putBoolean(KEY_RULE_ENABLED, sources.isNotEmpty())
            .remove(KEY_RULE_PACKAGE)
            .remove(KEY_RULE_CHANNEL)
            .remove(KEY_RULE_LABEL)
            .apply()
    }

    fun loadCandidate(): ObservedNotificationSource? {
        val packageName = preferences.getString(KEY_CANDIDATE_PACKAGE, null) ?: return null
        val channelId = preferences.getString(KEY_CANDIDATE_CHANNEL, null) ?: return null
        val appLabel = preferences.getString(KEY_CANDIDATE_LABEL, packageName) ?: packageName
        return ObservedNotificationSource(packageName, channelId, appLabel)
    }

    fun loadCandidates(): List<ObservedNotificationSource> {
        val candidates =
            preferences.getStringSet(KEY_CANDIDATES, emptySet()).orEmpty()
                .mapNotNull(::decodeCandidate)
                .toMutableList()
        loadCandidate()?.let(candidates::add)
        return candidates
            .distinctBy(ObservedNotificationSource::id)
            .sortedWith(compareBy<ObservedNotificationSource> { it.appLabel }.thenBy { it.id })
    }

    fun recordCandidate(source: ObservedNotificationSource) {
        val stored =
            preferences.getStringSet(KEY_CANDIDATES, emptySet()).orEmpty()
                .toMutableSet()
        stored.removeAll { encoded -> decodeCandidate(encoded)?.id == source.id }
        stored.add(encodeCandidate(source))
        while (stored.size > MAX_CANDIDATES) {
            stored.remove(stored.sorted().last())
        }
        preferences.edit()
            .putStringSet(KEY_CANDIDATES, stored)
            .putString(KEY_CANDIDATE_PACKAGE, source.packageName)
            .putString(KEY_CANDIDATE_CHANNEL, source.channelId)
            .putString(KEY_CANDIDATE_LABEL, source.appLabel)
            .apply()
    }

    fun recordRuntime(result: String, activeProxyCount: Int) {
        preferences.edit()
            .putString(KEY_LAST_RESULT, result)
            .putInt(KEY_ACTIVE_COUNT, activeProxyCount)
            .apply()
    }

    fun markLegacyTransportDisabled() {
        preferences.edit()
            .putString(KEY_LAST_RESULT, LEGACY_TRANSPORT_DISABLED_RESULT)
            .putInt(KEY_ACTIVE_COUNT, 0)
            .remove(KEY_LAST_DIAGNOSTIC)
            .apply()
    }

    fun lastResult(): String = preferences.getString(KEY_LAST_RESULT, "尚无代理事件") ?: "尚无代理事件"

    fun activeProxyCount(): Int = preferences.getInt(KEY_ACTIVE_COUNT, 0)

    fun lastDiagnostic(): String? = preferences.getString(KEY_LAST_DIAGNOSTIC, null)

    fun recordDiagnostic(source: ObservedNotificationSource, reason: NotificationRejectionReason) {
        val message = "${source.appLabel} · ${source.channelId}：${reason.displayName()}"
        if (preferences.getString(KEY_LAST_DIAGNOSTIC, null) == message) return
        preferences.edit().putString(KEY_LAST_DIAGNOSTIC, message).apply()
    }

    fun clearDiagnostic(source: ObservedNotificationSource) {
        val message = preferences.getString(KEY_LAST_DIAGNOSTIC, null) ?: return
        if (message.startsWith("${source.appLabel} · ${source.channelId}：")) {
            preferences.edit().remove(KEY_LAST_DIAGNOSTIC).apply()
        }
    }

    fun observeChanges(onChanged: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChanged() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun loadEnabledMediaSources(): List<ObservedMediaSource> =
        preferences.getStringSet(KEY_MEDIA_RULES, emptySet()).orEmpty()
            .mapNotNull(::decodeMediaSource)
            .distinctBy(ObservedMediaSource::id)
            .sortedWith(compareBy<ObservedMediaSource> { it.appLabel }.thenBy { it.id })

    fun mediaFeatureEnabled(): Boolean = preferences.getBoolean(KEY_MEDIA_FEATURE_ENABLED, true)

    fun setMediaFeatureEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_MEDIA_FEATURE_ENABLED, enabled).apply()
    }

    fun loadMediaCandidates(): List<ObservedMediaSource> =
        preferences.getStringSet(KEY_MEDIA_CANDIDATES, emptySet()).orEmpty()
            .mapNotNull(::decodeMediaSource)
            .distinctBy(ObservedMediaSource::id)
            .sortedWith(compareBy<ObservedMediaSource> { it.appLabel }.thenBy { it.id })

    fun toggleMediaRule(source: ObservedMediaSource): Boolean {
        val enabled = loadEnabledMediaSources().associateBy(ObservedMediaSource::id).toMutableMap()
        val wasEnabled = enabled.remove(source.id) != null
        if (!wasEnabled) enabled[source.id] = source
        preferences.edit().putStringSet(KEY_MEDIA_RULES, enabled.values.map(::encodeMediaSource).toSet()).apply()
        return !wasEnabled
    }

    fun clearMediaRules() {
        preferences.edit().remove(KEY_MEDIA_RULES).apply()
    }

    fun resetAllSettings() {
        check(preferences.edit().clear().commit()) { "Could not reset notification proxy settings" }
    }

    fun recordMediaCandidate(source: ObservedMediaSource) {
        val stored = preferences.getStringSet(KEY_MEDIA_CANDIDATES, emptySet()).orEmpty().toMutableSet()
        stored.removeAll { encoded -> decodeMediaSource(encoded)?.id == source.id }
        stored.add(encodeMediaSource(source))
        while (stored.size > MAX_MEDIA_CANDIDATES) {
            stored.remove(stored.sorted().last())
        }
        preferences.edit().putStringSet(KEY_MEDIA_CANDIDATES, stored).apply()
    }

    fun recordMediaRuntime(result: String, activeCount: Int) {
        preferences.edit()
            .putString(KEY_MEDIA_LAST_RESULT, result)
            .putInt(KEY_MEDIA_ACTIVE_COUNT, activeCount)
            .apply()
    }

    fun lastMediaResult(): String =
        preferences.getString(KEY_MEDIA_LAST_RESULT, "尚无媒体会话") ?: "尚无媒体会话"

    fun activeMediaCount(): Int = preferences.getInt(KEY_MEDIA_ACTIVE_COUNT, 0)

    private fun encodeCandidate(source: ObservedNotificationSource): String =
        listOf(
            source.packageName,
            source.channelId,
            source.appLabel.replace(CANDIDATE_SEPARATOR, " "),
        ).joinToString(CANDIDATE_SEPARATOR)

    private fun decodeCandidate(encoded: String): ObservedNotificationSource? {
        val fields = encoded.split(CANDIDATE_SEPARATOR, limit = 3)
        if (fields.size != 3) return null
        return runCatching { ObservedNotificationSource(fields[0], fields[1], fields[2]) }.getOrNull()
    }

    private fun encodeMediaSource(source: ObservedMediaSource): String =
        listOf(source.packageName, source.appLabel.replace(CANDIDATE_SEPARATOR, " "))
            .joinToString(CANDIDATE_SEPARATOR)

    private fun decodeMediaSource(encoded: String): ObservedMediaSource? {
        val fields = encoded.split(CANDIDATE_SEPARATOR, limit = 2)
        if (fields.size != 2) return null
        return runCatching { ObservedMediaSource(fields[0], fields[1]) }.getOrNull()
    }

    private companion object {
        const val FILE_NAME = "notification-proxy"
        const val KEY_RULE_ENABLED = "rule-enabled"
        const val KEY_RULE_PACKAGE = "rule-package"
        const val KEY_RULE_CHANNEL = "rule-channel"
        const val KEY_RULE_LABEL = "rule-label"
        const val KEY_RULES = "rules"
        const val KEY_NOTIFICATION_FEATURE_ENABLED = "notification-feature-enabled"
        const val KEY_PRIVACY_MODE = "privacy-mode"
        const val KEY_CANDIDATE_PACKAGE = "candidate-package"
        const val KEY_CANDIDATE_CHANNEL = "candidate-channel"
        const val KEY_CANDIDATE_LABEL = "candidate-label"
        const val KEY_CANDIDATES = "candidates"
        const val KEY_LAST_RESULT = "last-result"
        const val KEY_LAST_DIAGNOSTIC = "last-diagnostic"
        const val KEY_ACTIVE_COUNT = "active-count"
        const val KEY_MEDIA_RULES = "media-rules"
        const val KEY_MEDIA_FEATURE_ENABLED = "media-feature-enabled"
        const val KEY_MEDIA_CANDIDATES = "media-candidates"
        const val KEY_MEDIA_LAST_RESULT = "media-last-result"
        const val KEY_MEDIA_ACTIVE_COUNT = "media-active-count"
        const val CANDIDATE_SEPARATOR = "\t"
        const val MAX_CANDIDATES = 8
        const val MAX_MEDIA_CANDIDATES = 8
        const val LEGACY_TRANSPORT_DISABLED_RESULT = "旧通知代理已停用"
    }

    private fun NotificationRejectionReason.displayName(): String =
        when (this) {
            NotificationRejectionReason.RULE_DISABLED -> "未启用代理规则"
            NotificationRejectionReason.SELF_NOTIFICATION -> "已阻止自身通知循环"
            NotificationRejectionReason.SYSTEM_APP -> "系统应用通知不代理"
            NotificationRejectionReason.PACKAGE_NOT_ALLOWED -> "应用未被允许"
            NotificationRejectionReason.CHANNEL_NOT_ALLOWED -> "Channel 未被允许"
            NotificationRejectionReason.NOT_ONGOING -> "不是进行中或进度通知"
            NotificationRejectionReason.GROUP_SUMMARY -> "组摘要通知不代理"
            NotificationRejectionReason.BUBBLE -> "气泡通知不代理"
            NotificationRejectionReason.FULL_SCREEN_INTENT -> "全屏通知不代理"
            NotificationRejectionReason.CUSTOM_VIEWS -> "自定义布局通知不代理"
            NotificationRejectionReason.REMOTE_INPUT -> "含快捷回复的通知不代理"
        }
}
