package io.github.superisland.publisher.focus

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.superisland.model.FocusNotificationRequest
import io.github.superisland.model.IslandPriority
import org.json.JSONObject

data class FocusNotificationCapability(
    val notificationsEnabled: Boolean,
    val focusProtocolEnabled: Boolean,
    val systemPermissionGranted: Boolean,
)

data class FocusNotificationAction(
    val title: String,
    val intent: PendingIntent,
)

/**
 * Builds ordinary ongoing notifications carrying Xiaomi's focus-notification payload.
 *
 * The payload has no Android promoted-ongoing request and no Live Update style. SystemUI accepts
 * it only after the accompanying, package-scoped LSPosed bridge authorizes this app.
 */
class FocusNotificationPublisher(
    context: Context,
    private val channelName: String,
) {
    // During SystemUI's Application.attach(Context), ContextImpl may not yet expose an
    // application context. The supplied Context is nevertheless already usable for notification
    // services and preferences, so fall back to it instead of dropping the first resident update.
    private val appContext = context.applicationContext ?: context
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val sequencePreferences =
        appContext.getSharedPreferences(SEQUENCE_PREFERENCES, Context.MODE_PRIVATE)

    fun capability(): FocusNotificationCapability =
        FocusNotificationCapability(
            notificationsEnabled = notificationManager.areNotificationsEnabled(),
            focusProtocolEnabled =
                Settings.System.getInt(
                    appContext.contentResolver,
                    FOCUS_PROTOCOL_SETTING,
                    0,
                ) > 0,
            systemPermissionGranted = canShowFocusFromSystem(),
        )

    @SuppressLint("MissingPermission")
    fun post(
        request: FocusNotificationRequest,
        smallIconResId: Int,
        contentIntent: PendingIntent,
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        sourceSmallIcon: IconCompat? = null,
        actions: List<FocusNotificationAction> = emptyList(),
        visibility: Int = NotificationCompat.VISIBILITY_PRIVATE,
    ): Result<Unit> = runCatching {
        notificationManager.notify(
            notificationId,
            buildNotification(
                request = request,
                smallIconResId = smallIconResId,
                contentIntent = contentIntent,
                sourceSmallIcon = sourceSmallIcon,
                actions = actions,
                visibility = visibility,
            ),
        )
    }

    fun buildNotification(
        request: FocusNotificationRequest,
        smallIconResId: Int,
        contentIntent: PendingIntent?,
        sourceSmallIcon: IconCompat? = null,
        leftIslandIcon: IconCompat? = null,
        rightIslandIcon: IconCompat? = null,
        actions: List<FocusNotificationAction> = emptyList(),
        visibility: Int = NotificationCompat.VISIBILITY_PRIVATE,
        showInNotificationShade: Boolean = true,
        showLeftIslandIcon: Boolean = true,
        showRightIslandIcon: Boolean = false,
        customRemoteViews: FocusCustomRemoteViews? = null,
        targetPackage: String? = null,
        /** When true, suppress heads-up/sound and lower priority (recording FGS island path). */
        silent: Boolean = false,
        /** Completion events can detach from an FGS and expire without leaving an ongoing row. */
        ongoing: Boolean = true,
        timeoutAfterMillis: Long? = null,
        islandPriority: IslandPriority = IslandPriority.MEDIUM,
    ): Notification {
        require(timeoutAfterMillis == null || timeoutAfterMillis > 0L) {
            "timeoutAfterMillis must be positive"
        }
        ensureChannel()
        val smallIcon = sourceSmallIcon ?: IconCompat.createWithResource(appContext, smallIconResId)
        val effectiveLeftIslandIcon = leftIslandIcon ?: smallIcon
        val effectiveRightIslandIcon = rightIslandIcon ?: effectiveLeftIslandIcon
        val builder =
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle(request.title)
                .setContentText(request.text)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setColor(FOCUS_BLUE)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setShowWhen(false)
                .setVisibility(visibility)
                .setSmallIcon(smallIcon)
                .setSilent(silent)
                .setPriority(
                    if (silent) {
                        NotificationCompat.PRIORITY_LOW
                    } else {
                        NotificationCompat.PRIORITY_DEFAULT
                    },
                )

        timeoutAfterMillis?.let(builder::setTimeoutAfter)

        actions.take(MAX_ACTIONS).forEach { action ->
            builder.addAction(0, action.title, action.intent)
        }

        builder.extras.apply {
            putString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT, request.shortStatus)
            if (customRemoteViews == null) {
                putString(
                    EXTRA_FOCUS_PARAM,
                    focusParamJson(
                        request = request,
                        sequence = nextSequence(),
                        showInNotificationShade = showInNotificationShade,
                        showLeftIslandIcon = showLeftIslandIcon,
                        showRightIslandIcon = showRightIslandIcon,
                        islandPriority = islandPriority,
                    ),
                )
            } else {
                putString(
                    EXTRA_FOCUS_CUSTOM_PARAM,
                    customFocusParamJson(
                        request = request,
                        showInNotificationShade = showInNotificationShade,
                        showLeftIslandIcon = showLeftIslandIcon,
                        showRightIslandIcon = showRightIslandIcon,
                        islandPriority = islandPriority,
                    ),
                )
                putParcelable(EXTRA_FOCUS_REMOTE_VIEW, customRemoteViews.day)
                putParcelable(EXTRA_FOCUS_REMOTE_VIEW_NIGHT, customRemoteViews.night)
                putParcelable(EXTRA_FOCUS_ISLAND_EXPANDED_VIEW, customRemoteViews.islandExpanded)
                putString(EXTRA_FOCUS_TICKER, request.shortStatus)
            }
            targetPackage?.takeIf(String::isNotBlank)?.let {
                putString(EXTRA_TARGET_PACKAGE, it)
            }
            putBundle(
                EXTRA_FOCUS_PICTURES,
                Bundle().apply {
                    if (customRemoteViews != null) {
                        putParcelable(FOCUS_TICKER_ICON_KEY, effectiveLeftIslandIcon.toIcon(appContext))
                    }
                    if (showLeftIslandIcon) {
                        putParcelable(FOCUS_LEFT_ICON_KEY, effectiveLeftIslandIcon.toIcon(appContext))
                    }
                    if (showRightIslandIcon) {
                        putParcelable(FOCUS_RIGHT_ICON_KEY, effectiveRightIslandIcon.toIcon(appContext))
                    }
                },
            )
            // OS4 (Android 15+, HyperOS 2) uses DynamicIsland via island_param in addition to miui.focus.
            // Keep miui.focus for OS3 compatibility and add island_param for OS4.
            if (Build.VERSION.SDK_INT >= 35) {
                putString("island_param", islandParamForOS4(request))
            }
        }
        return builder.build()
    }

    /**
     * Builds the SystemUI-owned backing notification for a resident island.
     *
     * This notification is deliberately posted from the LSPosed SystemUI process rather than the
     * app process. The island therefore survives removal of the app's ordinary foreground-service
     * notification and app task cleanup. It is kept out of the notification shade; the user must
     * explicitly disable the feature in Super Island to withdraw it.
     */
    fun buildSystemUiResidentNotification(
        request: FocusNotificationRequest,
        smallIconResId: Int,
        leftModuleIcon: Icon? = null,
        rightModuleIcon: Icon? = null,
        showLeftIslandIcon: Boolean = true,
        showRightIslandIcon: Boolean = false,
        customRemoteViews: FocusCustomRemoteViews? = null,
        targetPackage: String? = null,
    ): Notification =
        buildNotification(
            request = request,
            smallIconResId = smallIconResId,
            // A resident system-status island is display-only. Supplying an activity intent here
            // lets HyperOS turn its expanded pull-down surface into a module-app shortcut.
            // Deliberately omit it so expansion never opens Super Island in a small window.
            contentIntent = null,
            sourceSmallIcon = leftModuleIcon?.let { IconCompat.createFromIcon(appContext, it) },
            leftIslandIcon = leftModuleIcon?.let { IconCompat.createFromIcon(appContext, it) },
            rightIslandIcon = rightModuleIcon?.let { IconCompat.createFromIcon(appContext, it) },
            visibility = NotificationCompat.VISIBILITY_SECRET,
            showInNotificationShade = false,
            showLeftIslandIcon = showLeftIslandIcon,
            showRightIslandIcon = showRightIslandIcon,
            customRemoteViews = customRemoteViews,
            targetPackage = targetPackage,
        )

    fun cancel(notificationId: Int = DEFAULT_NOTIFICATION_ID) {
        notificationManager.cancel(notificationId)
    }

    /**
     * HyperOS rejects a focus payload whose sequence is not greater than the last value retained
     * for the same notification key. Publisher instances are recreated with services and after
     * an APK update, so an in-memory counter would regress to 1 and leave a resident island
     * visually stale. Persist one app-wide monotonic sequence instead.
     *
     * elapsedRealtime supplies a safe first-run floor after upgrading from the old in-memory
     * publisher: it is already far beyond a short-lived counter during the current boot. The
     * stored value preserves monotonicity across subsequent service/App process restarts.
     * SharedPreferences.apply() updates the process-local value before returning and persists it
     * asynchronously; this keeps successive publisher instances monotonic without blocking the
     * SystemUI main thread on a disk fsync for every resident-island refresh.
     */
    private fun nextSequence(): Long =
        synchronized(SEQUENCE_LOCK) {
            val stored = sequencePreferences.getLong(KEY_LAST_SEQUENCE, 0L)
            val floor = SystemClock.elapsedRealtime().coerceAtLeast(0L)
            val next = maxOf(stored, floor).let { current ->
                check(current < Long.MAX_VALUE) { "Focus notification sequence exhausted" }
                current + 1
            }
            sequencePreferences.edit().putLong(KEY_LAST_SEQUENCE, next).apply()
            next
        }

    private fun focusParamJson(
        request: FocusNotificationRequest,
        sequence: Long,
        showInNotificationShade: Boolean,
        showLeftIslandIcon: Boolean,
        showRightIslandIcon: Boolean,
        islandPriority: IslandPriority,
    ): String {
        val paramIsland =
            islandParam(
                request = request,
                showLeftIslandIcon = showLeftIslandIcon,
                showRightIslandIcon = showRightIslandIcon,
                islandPriority = islandPriority,
            )
        val paramV2 =
            JSONObject()
                .put("protocol", FOCUS_PROTOCOL_VERSION)
                .put("enableFloat", false)
                .put("isFirstFloat", false)
                .put("updatable", true)
                .put("business", BUSINESS_SUPER_ISLAND)
                .put("sequence", sequence)
                .put("isShowNotification", showInNotificationShade)
                .put("showSmallIcon", showLeftIslandIcon)
                .put("iconTextInfo", iconTextInfo(request))
                .put("param_island", paramIsland)
        val payload = JSONObject().put("param_v2", paramV2).toString()
        checkFocusPayloadSize(payload)
        return payload
    }

    private fun customFocusParamJson(
        request: FocusNotificationRequest,
        showInNotificationShade: Boolean,
        showLeftIslandIcon: Boolean,
        showRightIslandIcon: Boolean,
        islandPriority: IslandPriority,
    ): String {
        val payload =
            JSONObject()
                .put("ticker", request.shortStatus)
                .put("tickerPic", FOCUS_TICKER_ICON_KEY)
                .put("tickerPicDark", FOCUS_TICKER_ICON_KEY)
                .put("enableFloat", false)
                .put("islandFirstFloat", false)
                .put("updatable", true)
                .put("reopen", FOCUS_REOPEN_CLOSE)
                .put("isShowNotification", showInNotificationShade)
                .put("timeout", CUSTOM_FOCUS_TIMEOUT_MINUTES)
                .put(
                    "param_island",
                    islandParam(
                        request = request,
                        showLeftIslandIcon = showLeftIslandIcon,
                        showRightIslandIcon = showRightIslandIcon,
                        islandPriority = islandPriority,
                    ),
                ).toString()
        checkFocusPayloadSize(payload)
        return payload
    }

    private fun islandParam(
        request: FocusNotificationRequest,
        showLeftIslandIcon: Boolean,
        showRightIslandIcon: Boolean,
        islandPriority: IslandPriority,
    ): JSONObject =
        JSONObject()
            .put("islandProperty", ISLAND_PROPERTY_PERSISTENT)
            .put("islandPriority", islandPriority.wireValue)
            .put("islandOrder", false)
            .put(
                "bigIslandArea",
                bigIslandArea(
                    title = request.title,
                    status = request.shortStatus,
                    showLeftIslandIcon = showLeftIslandIcon,
                    showRightIslandIcon = showRightIslandIcon,
                ),
            ).put(
                "smallIslandArea",
                smallIslandArea(request.shortStatus, showLeftIslandIcon),
            )

    private fun bigIslandArea(
        title: String,
        status: String,
        showLeftIslandIcon: Boolean,
        showRightIslandIcon: Boolean,
    ): JSONObject =
        JSONObject()
            .put(
                "imageTextInfoLeft",
                JSONObject()
                    .put("type", 1)
                    .apply {
                        if (showLeftIslandIcon) {
                            put("picInfo", JSONObject().put("type", 1).put("pic", FOCUS_LEFT_ICON_KEY))
                        }
                    }
                    .put(
                        "textInfo",
                        JSONObject().put("title", title).put("showHighlightColor", true),
                    ),
            )
            .put(
                "imageTextInfoRight",
                JSONObject()
                    .put("type", 2)
                    .apply {
                        if (showRightIslandIcon) {
                            put("picInfo", JSONObject().put("type", 1).put("pic", FOCUS_RIGHT_ICON_KEY))
                        }
                    }
                    .put(
                        "textInfo",
                        JSONObject().put("title", status),
                    ),
            )

    private fun smallIslandArea(status: String, showLeftIslandIcon: Boolean): JSONObject =
        JSONObject()
            .apply {
                if (showLeftIslandIcon) {
                    put("picInfo", JSONObject().put("type", 1).put("pic", FOCUS_LEFT_ICON_KEY))
                }
            }
            .put("textInfo", JSONObject().put("title", status))

    private fun iconTextInfo(request: FocusNotificationRequest): JSONObject =
        JSONObject()
            .put("title", request.title)
            .put("content", request.text)
            .put("colorTitle", "#000000")
            .put("colorContent", "#99000000")
            .put("colorTitleDark", "#ffffff")
            .put("colorContentDark", "#99ffffff")

    private fun islandParamForOS4(request: FocusNotificationRequest): String =
        JSONObject()
            .put(
                "left",
                JSONObject().put(
                    "textParams",
                    JSONObject().put("text", request.title).put("turnAnim", false),
                ),
            ).put(
                "right",
                JSONObject().put(
                    "textParams",
                    JSONObject().put("text", request.shortStatus).put("turnAnim", false),
                ),
            ).put("glowEffect", JSONObject().put("enable", false))
            .toString()

    private fun canShowFocusFromSystem(): Boolean =
        runCatching {
            val result =
                appContext.contentResolver.call(
                    Uri.parse(FOCUS_PROVIDER_URI),
                    FOCUS_PROVIDER_METHOD,
                    null,
                    Bundle().apply { putString("package", appContext.packageName) },
                )
            result?.getBoolean("canShowFocus", false) == true
        }.getOrDefault(false)

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "HyperOS 焦点通知与超级岛事件"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "focus_notification"
        const val DEFAULT_NOTIFICATION_ID = 0x464f
        const val MAX_ACTIONS = 3
        const val FOCUS_PROTOCOL_SETTING = "notification_focus_protocol"
        const val FOCUS_PROVIDER_URI = "content://miui.statusbar.notification.public"
        const val FOCUS_PROVIDER_METHOD = "canShowFocus"
        const val SEQUENCE_PREFERENCES = "focus-notification-sequences"
        const val KEY_LAST_SEQUENCE = "last-sequence"
        const val EXTRA_FOCUS_PARAM = "miui.focus.param"
        const val EXTRA_FOCUS_CUSTOM_PARAM = "miui.focus.param.custom"
        const val EXTRA_FOCUS_PICTURES = "miui.focus.pics"
        const val EXTRA_FOCUS_REMOTE_VIEW = "miui.focus.rv"
        const val EXTRA_FOCUS_REMOTE_VIEW_NIGHT = "miui.focus.rvNight"
        const val EXTRA_FOCUS_ISLAND_EXPANDED_VIEW = "miui.focus.rv.island.expand"
        const val EXTRA_FOCUS_TICKER = "miui.focus.ticker"
        const val EXTRA_TARGET_PACKAGE = "miui.targetPkg"
        const val FOCUS_LEFT_ICON_KEY = "io.github.superisland.focus.icon.left"
        const val FOCUS_RIGHT_ICON_KEY = "io.github.superisland.focus.icon.right"
        const val FOCUS_TICKER_ICON_KEY = "io.github.superisland.focus.icon.ticker"
        const val FOCUS_PROTOCOL_VERSION = 1
        const val ISLAND_PROPERTY_PERSISTENT = 1
        // Keep the resident surface above low-priority background islands while leaving high
        // priority events free to preempt it. islandOrder=false prevents periodic refreshes from
        // changing the OEM time-based order among equal-priority islands.
        const val BUSINESS_SUPER_ISLAND = "super_island_status"
        const val FOCUS_REOPEN_CLOSE = "close"
        const val CUSTOM_FOCUS_TIMEOUT_MINUTES = 720
        const val FOCUS_BLUE = 0xff3482ff.toInt()
        val SEQUENCE_LOCK = Any()
    }
}
