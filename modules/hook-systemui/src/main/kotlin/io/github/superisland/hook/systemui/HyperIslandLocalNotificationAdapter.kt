package io.github.superisland.hook.systemui

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.template.core.TemplateRegistry
import io.github.hyperisland.xposed.template.core.models.NotifData
import io.github.hyperisland.xposed.templates.NotificationIslandNotification
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.superisland.model.FocusDisplayMode
import io.github.superisland.model.SmartCapsuleRemoteSnapshot
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap

data class LocalNotificationAdapterInstallResult(
    val installed: Boolean,
    val adapterName: String,
    val failure: String? = null,
)

data class LocalNotificationRuleAcceptance(
    val revision: Long,
    val digest: String,
)

/**
 * Converts an allowlisted source SBN in SystemUI immediately before MIUI snapshots its extras.
 *
 * The source notification remains the only notification record. This adapter never posts, clones,
 * cancels, or suppresses a notification and never reads or applies ordinary RemoteViews.
 */
object HyperIslandLocalNotificationAdapter {
    const val ADAPTER_NAME = "hyperisland-local-sbn-v1"
    const val LOCAL_MARKER = "io.github.superisland.focus.local_adapter_v1"
    const val FOCUS_ONLY_MARKER = "io.github.superisland.focus.focus_only_v1"

    private const val TAG = "SuperIslandLocalSbn"
    private const val TARGET_CLASS = "com.miui.systemui.notification.MiuiBaseNotifUtil"
    private const val TARGET_METHOD = "generateInnerNotifBean"
    private const val EXTRA_FOCUS_PARAM = "miui.focus.param"
    private const val EXTRA_CUSTOM_FOCUS_PARAM = "miui.focus.param.custom"
    private const val EXTRA_PROGRESS_SEGMENTS = "android.progressSegments"
    private const val MAX_ACTIVE_KEYS = 64
    private const val MAX_ACTIONS = 2

    private val installLock = Any()
    private val runtimes = IdentityHashMap<ClassLoader, Runtime>()

    @Volatile
    private var lastFailure: String? = "not_installed"

    @JvmStatic
    fun install(
        module: XposedModule,
        classLoader: ClassLoader,
        preferences: SharedPreferences,
    ): LocalNotificationAdapterInstallResult =
        synchronized(installLock) {
            runtimes[classLoader]?.let {
                return@synchronized LocalNotificationAdapterInstallResult(
                    installed = true,
                    adapterName = ADAPTER_NAME,
                )
            }
            var pendingRuntime: Runtime? = null
            try {
                val target = resolveTarget(classLoader)
                val runtime =
                    Runtime(
                        module = module,
                        classLoader = classLoader,
                        rules = SharedPreferencesRuleSnapshot(preferences, Process.myUid() / 100_000),
                    )
                pendingRuntime = runtime
                ConfigManager.init(module)
                val handle =
                    module.hook(target)
                        .setExceptionMode(
                            io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE,
                        ).intercept { chain ->
                            val sbn = chain.args.firstOrNull() as? StatusBarNotification
                            if (sbn != null) runtime.beforeGenerateInnerNotifBean(sbn)
                            chain.proceed()
                        }
                runtime.hookHandle = handle
                runtimes[classLoader] = runtime
                pendingRuntime = null
                lastFailure = null
                module.log(Log.INFO, TAG, "Installed $ADAPTER_NAME")
                LocalNotificationAdapterInstallResult(
                    installed = true,
                    adapterName = ADAPTER_NAME,
                )
            } catch (error: Throwable) {
                val failure = "${error.javaClass.simpleName}: ${error.message ?: "unknown"}"
                pendingRuntime?.close()
                lastFailure = failure
                module.log(Log.ERROR, TAG, "Could not install $ADAPTER_NAME", error)
                LocalNotificationAdapterInstallResult(
                    installed = false,
                    adapterName = ADAPTER_NAME,
                    failure = failure,
                )
            }
        }

    @JvmStatic
    fun isInstalled(): Boolean = synchronized(installLock) { runtimes.isNotEmpty() }

    /** Number of distinct SBN keys currently inside the mapper, not a shadow notification count. */
    @JvmStatic
    fun getActiveKeyCount(): Int =
        synchronized(installLock) { runtimes.values.sumOf { runtime -> runtime.activeKeyCount() } }

    @JvmStatic
    fun getLastFailure(): String? = lastFailure

    /** Explicitly refreshes every SystemUI runtime after the App publishes a new A/B snapshot. */
    @JvmStatic
    fun reloadRules(): LocalNotificationRuleAcceptance? =
        synchronized(installLock) {
            runtimes.values.mapNotNull(Runtime::reloadRules).maxByOrNull { it.revision }
        }

    private fun resolveTarget(classLoader: ClassLoader): Method {
        val clazz = Class.forName(TARGET_CLASS, false, classLoader)
        val method = clazz.getDeclaredMethod(TARGET_METHOD, StatusBarNotification::class.java)
        check(Modifier.isStatic(method.modifiers)) { "$TARGET_CLASS.$TARGET_METHOD is no longer static" }
        check(method.parameterCount == 1) { "$TARGET_CLASS.$TARGET_METHOD signature changed" }
        method.isAccessible = true
        return method
    }

    private class Runtime(
        private val module: XposedModule,
        classLoader: ClassLoader,
        private val rules: SharedPreferencesRuleSnapshot,
    ) {
        private val contextResolver = SystemUiContextResolver(classLoader)
        private val keyGate = BoundedSbnKeyGate(MAX_ACTIVE_KEYS)
        private val mappedKeys = LinkedHashSet<String>()
        private val diagnosedKeys = LinkedHashSet<String>()
        private val appMetadata = ConcurrentHashMap<String, AppMetadata>()

        @Volatile
        var hookHandle: HookHandle? = null

        fun activeKeyCount(): Int = keyGate.activeKeyCount()

        fun reloadRules(): LocalNotificationRuleAcceptance? {
            val refreshed = rules.reload() ?: return null
            val remote = SmartCapsuleRemoteSnapshot.fromConfig(refreshed)
            module.log(
                Log.INFO,
                TAG,
                "Reloaded rules revision=${refreshed.revision} enabled=${refreshed.enabled} " +
                    "applications=${refreshed.rules.size}",
            )
            return LocalNotificationRuleAcceptance(
                revision = refreshed.revision,
                digest = remote.digest,
            )
        }

        fun close() {
            rules.close()
        }

        fun beforeGenerateInnerNotifBean(sbn: StatusBarNotification) {
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val key = sbn.key.orEmpty()
            when (
                val result =
                    keyGate.withKey(key) {
                        try {
                            adapt(sbn, notification, extras)
                        } catch (error: Throwable) {
                            lastFailure = "${error.javaClass.simpleName}: ${error.message ?: "unknown"}"
                            module.log(Log.WARN, TAG, "Local SBN mapping failed closed for key=$key", error)
                        }
                    }
            ) {
                is BoundedSbnKeyGate.GateResult.Completed -> Unit
                is BoundedSbnKeyGate.GateResult.Rejected -> {
                    lastFailure = "key_gate_${result.reason.name.lowercase()}"
                    module.log(Log.WARN, TAG, "Local SBN mapping skipped: ${result.reason}")
                }
            }
        }

        private fun adapt(
            sbn: StatusBarNotification,
            notification: Notification,
            extras: Bundle,
        ) {
            val snapshot = rules.currentIfPublished() ?: return
            @Suppress("DEPRECATION")
            val sourceUserId = sbn.userId
            val packageName = sbn.packageName
            if (!snapshot.matchesPackage(packageName, sourceUserId)) return
            val channelId =
                notification.channelId?.takeIf(String::isNotBlank)
                    ?: return logSelectedOutcome(sbn.key.orEmpty(), packageName, "<none>", "missing_channel")
            val configuredPriority =
                snapshot.resolveIslandPriority(packageName, sourceUserId, channelId)
            if (configuredPriority == null) {
                logSelectedOutcome(sbn.key.orEmpty(), packageName, channelId, "channel_rule_mismatch")
                return
            }
            val effectivePriority =
                SmartCapsuleIslandPriorityPolicy.effective(configuredPriority, Build.FINGERPRINT)
            val displayMode =
                snapshot.resolveFocusDisplayMode(packageName, sourceUserId, channelId)
                    ?: return logSelectedOutcome(
                        sbn.key.orEmpty(),
                        packageName,
                        channelId,
                        "display_mode_rule_mismatch",
                    )
            val focusOnly = displayMode == FocusDisplayMode.FOCUS_ONLY
            if (focusOnly && !SystemUiFocusSupportBridge.isFocusOnlyIslandSuppressionAvailable()) {
                logSelectedOutcome(
                    sbn.key.orEmpty(),
                    packageName,
                    channelId,
                    "focus_only_suppressor_unavailable",
                )
                return
            }

            val skipReason =
                HyperIslandLocalNotificationPolicy.structuralSkipReason(
                    NotificationStructure(
                        hasExistingFocus =
                            hasValidFocusParam(extras) ||
                                HyperIslandLocalNotificationPolicy.hasFocusOwnershipMarker(extras.keySet()),
                        hasCustomFocus = hasUnsupportedFocusStructure(extras),
                        isMedia = isMediaStructure(extras),
                        isBubble = notification.bubbleMetadata != null,
                        isFullScreen = notification.fullScreenIntent != null,
                        isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                        hasProgress = hasProgressStructure(extras),
                    ),
                )
            if (skipReason != null) {
                logSelectedOutcome(
                    sbn.key.orEmpty(),
                    packageName,
                    channelId,
                    "structural_${skipReason.name.lowercase()}",
                )
                return
            }

            val context =
                contextResolver.current()
                    ?: return logSelectedOutcome(
                        sbn.key.orEmpty(),
                        packageName,
                        channelId,
                        "systemui_context_unavailable",
                    )
            val metadata =
                appMetadata[packageName]
                    ?: resolveAppMetadata(context, packageName).let { resolved ->
                        appMetadata.putIfAbsent(packageName, resolved) ?: resolved
                    }
            val text =
                HyperIslandLocalNotificationPolicy.resolveText(
                    PublicNotificationText(
                        title = extras.text(Notification.EXTRA_TITLE),
                        bigTitle = extras.text(Notification.EXTRA_TITLE_BIG),
                        conversationTitle = extras.text(Notification.EXTRA_CONVERSATION_TITLE),
                        text = extras.text(Notification.EXTRA_TEXT),
                        bigText = extras.text(Notification.EXTRA_BIG_TEXT),
                        subText = extras.text(Notification.EXTRA_SUB_TEXT),
                        infoText = extras.text(Notification.EXTRA_INFO_TEXT),
                        ticker = notification.tickerText?.toString(),
                        channelId = channelId,
                        appLabel = metadata.label,
                        packageName = packageName,
                    ),
                )

            val largeIcon = resolveLargeIcon(notification, extras)
            val smallIcon = notification.smallIcon
            val appIcon = metadata.icon
            val workingExtras = Bundle(extras)
            workingExtras.remove(FOCUS_ONLY_MARKER)
            if (!hasValidFocusParam(workingExtras)) {
                workingExtras.remove(EXTRA_FOCUS_PARAM)
            }
            TemplateRegistry.dispatch(
                templateId = NotificationIslandNotification.TEMPLATE_ID,
                context = context,
                extras = workingExtras,
                data =
                    NotifData(
                        pkg = packageName,
                        channelId = channelId,
                        notifId = sbn.id,
                        title = text.title,
                        subtitle = text.content,
                        progress = -1,
                        actions = resolveNotificationActions(context, packageName, notification),
                        // With iconMode=auto, this yields island large -> small -> app while the
                        // upstream focus resolver remains large -> app -> small.
                        notifIcon = smallIcon ?: appIcon,
                        largeIcon = largeIcon,
                        appIconRaw = appIcon,
                        iconMode = "auto",
                        focusNotif = "on",
                        showNotification = "on",
                        preserveStatusBarSmallIcon = "off",
                        firstFloat = "off",
                        enableFloatMode = "off",
                        islandTimeout = 5,
                        showIslandIcon = "on",
                        isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
                        contentIntent = notification.contentIntent,
                        islandPriority = effectivePriority,
                        islandEnabled = !focusOnly,
                    ),
            )
            val generated = workingExtras.getString(EXTRA_FOCUS_PARAM)?.isNotBlank() == true
            if (!generated) {
                logSelectedOutcome(sbn.key.orEmpty(), packageName, channelId, "renderer_no_focus_payload")
                return
            }

            // These upstream markers describe its optional dispatcher/status-icon bridges. This
            // adapter never creates a proxy and therefore must not leak those identities.
            workingExtras.remove("hyperisland.owner")
            workingExtras.remove("hyperisland_focus_proxy")
            workingExtras.remove("hyperisland_preserve_status_bar_small_icon")
            if (focusOnly) {
                workingExtras.putBoolean(FOCUS_ONLY_MARKER, true)
            }
            extras.putAll(workingExtras)
            extras.putString(LOCAL_MARKER, ADAPTER_NAME)
            logFirstMapping(sbn.key.orEmpty(), packageName, channelId)
        }

        private fun logFirstMapping(
            key: String,
            packageName: String,
            channelId: String,
        ) {
            val first =
                synchronized(mappedKeys) {
                    if (!mappedKeys.add(key)) {
                        false
                    } else {
                        if (mappedKeys.size > 128) {
                            mappedKeys.remove(mappedKeys.first())
                        }
                        true
                    }
                }
            if (first) {
                module.log(Log.INFO, TAG, "Mapped source Focus pkg=$packageName channel=$channelId key=$key")
            }
        }

        private fun logSelectedOutcome(
            key: String,
            packageName: String,
            channelId: String,
            outcome: String,
        ) {
            val identity = "$key|$outcome"
            val first =
                synchronized(diagnosedKeys) {
                    if (!diagnosedKeys.add(identity)) {
                        false
                    } else {
                        if (diagnosedKeys.size > MAX_DIAGNOSED_KEYS) {
                            diagnosedKeys.remove(diagnosedKeys.first())
                        }
                        true
                    }
                }
            if (first) {
                module.log(
                    Log.INFO,
                    TAG,
                    "Selected source not mapped pkg=$packageName channel=$channelId " +
                        "reason=$outcome key=$key",
                )
            }
        }
    }

    private class SystemUiContextResolver(
        private val classLoader: ClassLoader,
    ) {
        @Volatile
        private var cached: Context? = null

        fun current(): Context? {
            cached?.let { return it }
            val resolved =
                runCatching {
                    val activityThread = Class.forName("android.app.ActivityThread", false, classLoader)
                    activityThread.getMethod("currentApplication").invoke(null) as? Context
                }.getOrNull()?.applicationContext
            if (resolved != null) cached = resolved
            return resolved
        }
    }

    private data class AppMetadata(
        val label: String?,
        val icon: Icon?,
    )

    private fun hasProgressStructure(extras: Bundle): Boolean =
        HyperIslandLocalNotificationPolicy.hasProgressStructure(
            hasProgressSegments = extras.containsKey(EXTRA_PROGRESS_SEGMENTS),
            progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
            indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
        )

    private fun hasValidFocusParam(extras: Bundle): Boolean =
        runCatching { extras.getString(EXTRA_FOCUS_PARAM) }
            .getOrNull()
            ?.isNotBlank() == true

    /** A custom Focus RemoteViews payload is already owned by the source notification. */
    private fun hasUnsupportedFocusStructure(extras: Bundle): Boolean =
        extras.containsKey(EXTRA_CUSTOM_FOCUS_PARAM)

    private fun isMediaStructure(extras: Bundle): Boolean =
        extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
            extras.getString(Notification.EXTRA_TEMPLATE)
                ?.contains("MediaStyle", ignoreCase = true) == true

    private fun resolveLargeIcon(
        notification: Notification,
        extras: Bundle,
    ): Icon? {
        resolveLatestMessageSenderIcon(extras)?.let { return it }
        notification.getLargeIcon()?.let { return it }
        @Suppress("DEPRECATION")
        return when (val legacy = extras.get(Notification.EXTRA_LARGE_ICON)) {
            is Icon -> legacy
            is Bitmap -> Icon.createWithBitmap(legacy)
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveLatestMessageSenderIcon(extras: Bundle): Icon? =
        runCatching {
            Notification.MessagingStyle.Message
                .getMessagesFromBundleArray(extras.getParcelableArray(Notification.EXTRA_MESSAGES))
                .asReversed()
                .firstNotNullOfOrNull { message -> message.senderPerson?.icon }
        }.getOrNull()

    private fun resolveNotificationActions(
        context: Context,
        packageName: String,
        notification: Notification,
    ): List<Notification.Action> {
        val actions =
            notification.actions
                ?.filterNotNull()
                ?.take(MAX_ACTIONS)
                .orEmpty()
        if (actions.none { action -> !action.remoteInputs.isNullOrEmpty() }) return actions

        val fallbackIntent = notification.contentIntent ?: createLaunchAppIntent(context, packageName)
        return actions.map { action ->
            if (!action.remoteInputs.isNullOrEmpty() && fallbackIntent != null) {
                Notification.Action.Builder(action.getIcon(), action.title, fallbackIntent).build()
            } else {
                action
            }
        }
    }

    private fun createLaunchAppIntent(
        context: Context,
        packageName: String,
    ): PendingIntent? {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            } ?: return null
        return PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveAppMetadata(
        context: Context,
        packageName: String,
    ): AppMetadata =
        runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            AppMetadata(
                label =
                    context.packageManager
                        .getApplicationLabel(info)
                        .toString()
                        .trim()
                        .takeIf(String::isNotEmpty),
                icon =
                    info.icon.takeIf { resourceId -> resourceId != 0 }
                        ?.let { resourceId -> Icon.createWithResource(packageName, resourceId) },
            )
        }.getOrElse { AppMetadata(label = null, icon = null) }

    private fun Bundle.text(key: String): String? = getCharSequence(key)?.toString()

    private const val MAX_DIAGNOSED_KEYS = 256
}
