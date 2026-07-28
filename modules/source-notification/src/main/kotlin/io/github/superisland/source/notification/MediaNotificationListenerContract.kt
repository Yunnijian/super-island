package io.github.superisland.source.notification

/** Explicit boundary for the notification-listener component retained by the media feature. */
internal object MediaNotificationListenerContract {
    const val HANDLES_SOURCE_NOTIFICATION_CALLBACKS = false
    const val REPUBLISHES_THIRD_PARTY_NOTIFICATIONS = false
    const val CONTROLS_MEDIA_SESSIONS = true

    fun actionFor(request: NotificationListenerControlRequest): NotificationListenerControlAction =
        when (request) {
            NotificationListenerControlRequest.LEGACY_SMART_CAPSULE_RULE_CHANGED,
            NotificationListenerControlRequest.LEGACY_SMART_CAPSULE_REFRESH,
            -> NotificationListenerControlAction.IGNORE

            NotificationListenerControlRequest.MEDIA_RULE_CHANGED,
            NotificationListenerControlRequest.MEDIA_REFRESH,
            -> NotificationListenerControlAction.REFRESH_MEDIA
        }
}

internal enum class NotificationListenerControlRequest {
    LEGACY_SMART_CAPSULE_RULE_CHANGED,
    LEGACY_SMART_CAPSULE_REFRESH,
    MEDIA_RULE_CHANGED,
    MEDIA_REFRESH,
}

internal enum class NotificationListenerControlAction {
    IGNORE,
    REFRESH_MEDIA,
}

internal interface MediaIslandRuntime {
    fun start()

    fun refresh()

    fun stop(status: String)
}

internal class MediaIslandServiceLifecycle(
    private val mediaIsland: MediaIslandRuntime,
) {
    fun onConnected() = mediaIsland.start()

    fun onDisconnected() = mediaIsland.stop("媒体监听已断开")

    fun onDestroyed() = mediaIsland.stop("媒体监听已停止")

    fun onRefreshRequested() = mediaIsland.refresh()
}
