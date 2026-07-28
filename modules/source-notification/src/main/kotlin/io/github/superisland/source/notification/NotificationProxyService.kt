package io.github.superisland.source.notification

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import io.github.superisland.publisher.focus.FocusNotificationPublisher

/**
 * Notification-listener component retained under its historical class name so an existing system
 * grant keeps working. It is now used only to obtain MediaSession access for Super Island Music.
 * Third-party notification callbacks deliberately remain inherited and unhandled.
 */
class NotificationProxyService : NotificationListenerService() {
    private lateinit var mediaLifecycle: MediaIslandServiceLifecycle

    override fun onCreate() {
        super.onCreate()
        val preferences = NotificationProxyPreferences(this)
        preferences.markLegacyTransportDisabled()
        mediaLifecycle =
            MediaIslandServiceLifecycle(
                MediaIslandController(
                    context = this,
                    notificationListenerComponent = ComponentName(this, NotificationProxyService::class.java),
                    preferences = preferences,
                    publisher = FocusNotificationPublisher(this, getString(R.string.media_island_channel_name)),
                ),
            )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        current = this
        Log.d(TAG, "media listener connected")
        mediaLifecycle.onConnected()
    }

    override fun onListenerDisconnected() {
        current = null
        mediaLifecycle.onDisconnected()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (current === this) current = null
        mediaLifecycle.onDestroyed()
        super.onDestroy()
    }

    private fun refreshMedia() {
        mediaLifecycle.onRefreshRequested()
    }

    companion object {
        @Volatile
        private var current: NotificationProxyService? = null

        fun isConnected(): Boolean = current != null

        /** Historical Smart Capsule entry point. Listener transport is intentionally retired. */
        fun onRuleChanged(context: Context) {
            dispatch(context, NotificationListenerControlRequest.LEGACY_SMART_CAPSULE_RULE_CHANGED)
        }

        fun onMediaRuleChanged(context: Context) {
            dispatch(context, NotificationListenerControlRequest.MEDIA_RULE_CHANGED)
        }

        /** Historical Smart Capsule entry point. Listener transport is intentionally retired. */
        fun refresh(context: Context) {
            dispatch(context, NotificationListenerControlRequest.LEGACY_SMART_CAPSULE_REFRESH)
        }

        fun refreshMedia(context: Context) {
            dispatch(context, NotificationListenerControlRequest.MEDIA_REFRESH)
        }

        private fun dispatch(
            context: Context,
            request: NotificationListenerControlRequest,
        ) {
            when (MediaNotificationListenerContract.actionFor(request)) {
                NotificationListenerControlAction.IGNORE -> Unit
                NotificationListenerControlAction.REFRESH_MEDIA ->
                    current?.refreshMedia() ?: requestRebind(context)
            }
        }

        private fun requestRebind(context: Context) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(context, NotificationProxyService::class.java),
                )
            }
        }

        private const val TAG = "SuperIslandMedia"
    }
}
