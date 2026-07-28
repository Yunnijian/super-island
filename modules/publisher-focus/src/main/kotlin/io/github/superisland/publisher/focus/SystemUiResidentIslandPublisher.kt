package io.github.superisland.publisher.focus

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import io.github.superisland.model.FocusNotificationRequest

/**
 * Narrow, signature-protected IPC from the app process to the SystemUI LSPosed host.
 *
 * The host owns the backing ongoing focus notification. This sender never includes an arbitrary
 * PendingIntent, action, or source-notification payload. The optional icon is restricted by the
 * SystemUI host to this module's own package resource.
 */
class SystemUiResidentIslandPublisher(context: Context) {
    private val appContext = context.applicationContext

    fun post(
        request: FocusNotificationRequest,
        appIcon: Icon? = null,
    ): Result<Unit> =
        send(
            Intent(SystemUiResidentIslandContract.ACTION_POST)
                .putExtra(SystemUiResidentIslandContract.EXTRA_TITLE, request.title)
                .putExtra(SystemUiResidentIslandContract.EXTRA_TEXT, request.text)
                .putExtra(SystemUiResidentIslandContract.EXTRA_SHORT_STATUS, request.shortStatus)
                .apply {
                    appIcon?.let { putExtra(SystemUiResidentIslandContract.EXTRA_APP_ICON, it) }
                },
        )

    fun cancel(): Result<Unit> = send(Intent(SystemUiResidentIslandContract.ACTION_CANCEL))

    /** Asks the SystemUI host to synchronously apply the persisted resident-island settings. */
    fun reloadSettings(): Result<Unit> = send(Intent(SystemUiResidentIslandContract.ACTION_RELOAD_SETTINGS))

    private fun send(intent: Intent): Result<Unit> =
        runCatching {
            // The receiver in the scoped SystemUI module declares PERMISSION as its required
            // sender permission; Android therefore validates this app's signature grant before
            // delivery. Passing that permission to sendBroadcast would reverse the check and
            // incorrectly require SystemUI to hold it. The explicit package is a second routing
            // restriction.
            appContext.sendBroadcast(intent.setPackage(SystemUiResidentIslandContract.SYSTEM_UI_PACKAGE))
        }
}

/** Shared constants for the app sender and the injected SystemUI receiver. */
object SystemUiResidentIslandContract {
    const val HOST_SCHEMA_VERSION = 5
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val PERMISSION = "io.github.superisland.permission.SEND_RESIDENT_ISLAND"
    const val ACTION_POST = "io.github.superisland.action.POST_RESIDENT_ISLAND"
    const val ACTION_CANCEL = "io.github.superisland.action.CANCEL_RESIDENT_ISLAND"
    const val ACTION_RELOAD_SETTINGS = "io.github.superisland.action.RELOAD_RESIDENT_ISLAND_SETTINGS"
    const val EXTRA_TITLE = "io.github.superisland.extra.RESIDENT_TITLE"
    const val EXTRA_TEXT = "io.github.superisland.extra.RESIDENT_TEXT"
    const val EXTRA_SHORT_STATUS = "io.github.superisland.extra.RESIDENT_SHORT_STATUS"
    const val EXTRA_APP_ICON = "io.github.superisland.extra.RESIDENT_APP_ICON"
    const val HOST_NOTIFICATION_ID = 0x535249

    /** libxposed RemotePreferences name, readable only by this module's scoped hook process. */
    const val REMOTE_PREFERENCES = "SuperIslandResidentHost"
    const val KEY_SCHEMA_VERSION = "schema_version"
    const val KEY_ENABLED = "enabled"
    const val KEY_LEFT_ICON = "left_icon"
    const val KEY_RIGHT_ICON = "right_icon"
    const val KEY_LEFT_TITLE_METRIC = "left_title_metric"
    const val KEY_RIGHT_TITLE_METRIC = "right_title_metric"
    const val KEY_REFRESH_INTERVAL_MILLIS = "refresh_interval_millis"
    const val KEY_EXPANDED_METRICS = "expanded_metrics"
    const val KEY_EXPANDED_CONTENT_MODE = "expanded_content_mode"
    const val KEY_EXPANDED_CONTENT_TEMPLATE = "expanded_content_template"
    const val KEY_EXPANDED_ACTIONS = "expanded_actions"
    const val KEY_APP_ICON_RES_ID = "app_icon_res_id"

    // Version 1 keys remain during one compatibility window. The app mirrors values into them so
    // a loaded pre-update SystemUI host still reacts until the required SystemUI restart occurs.
    const val KEY_TITLE_METRIC = "title_metric"
    const val KEY_LEADING_KIND = "leading_kind"
    const val KEY_LEADING_METRIC = "leading_metric"
    const val KEY_LEADING_STATIC_TEXT = "leading_static_text"
    const val KEY_TRAILING_KIND = "trailing_kind"
    const val KEY_TRAILING_METRIC = "trailing_metric"
    const val KEY_TRAILING_STATIC_TEXT = "trailing_static_text"
}
