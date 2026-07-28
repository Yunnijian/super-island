package io.github.superisland.source.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.drawable.IconCompat
import io.github.superisland.event.NotificationIdAllocator
import io.github.superisland.event.NotificationProxyLifecycle
import io.github.superisland.event.ProxyLifecycleCommand
import io.github.superisland.event.policy
import io.github.superisland.model.FocusNotificationRequest
import io.github.superisland.publisher.focus.FocusNotificationPublisher

/**
 * Converts user-approved public MediaSession metadata into this app's own focus notification.
 * It never modifies, hides, or persists another app's media notification or song metadata.
 */
internal class MediaIslandController(
    context: Context,
    private val notificationListenerComponent: ComponentName,
    private val preferences: NotificationProxyPreferences,
    private val publisher: FocusNotificationPublisher,
) : MediaIslandRuntime {
    private val appContext = context.applicationContext
    private val mediaSessionManager = appContext.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val lifecycle =
        NotificationProxyLifecycle(
            idAllocator = NotificationIdAllocator(idPrefix = NotificationIdAllocator.MEDIA_ID_PREFIX),
        )
    private val callbacks = mutableMapOf<MediaSession.Token, RegisteredCallback>()
    private val publishedInstances = mutableMapOf<String, Long>()
    private var started = false

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener {
            scheduleReconcile()
        }

    override fun start() {
        if (started) {
            reconcile()
            return
        }
        started = true
        runCatching {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                sessionListener,
                notificationListenerComponent,
                handler,
            )
        }.onFailure {
            preferences.recordMediaRuntime("读取媒体会话失败 ${it.javaClass.simpleName}", 0)
        }
        reconcile()
    }

    override fun refresh() {
        if (started) reconcile()
    }

    override fun stop(status: String) {
        if (started) {
            runCatching { mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener) }
        }
        started = false
        callbacks.values.forEach { registered ->
            runCatching { registered.controller.unregisterCallback(registered.callback) }
        }
        callbacks.clear()
        lifecycle.clear().forEach { publisher.cancel(it.notificationId) }
        publishedInstances.clear()
        preferences.recordMediaRuntime(status, 0)
    }

    private fun scheduleReconcile() {
        handler.removeCallbacks(reconcileRunnable)
        handler.post(reconcileRunnable)
    }

    private val reconcileRunnable = Runnable(::reconcile)

    private fun reconcile() {
        if (!started) return
        val controllers =
            runCatching { mediaSessionManager.getActiveSessions(notificationListenerComponent).orEmpty() }
                .getOrElse {
                    preferences.recordMediaRuntime("读取媒体会话失败 ${it.javaClass.simpleName}", 0)
                    return
                }
        synchronizeCallbacks(controllers)

        val selections =
            controllers
                .filter { it.packageName != appContext.packageName }
                .groupBy(MediaController::getPackageName)
                .mapNotNull { (_, packageControllers) -> packageControllers.maxByOrNull(::priority) }

        val sources = selections.map(::sourceFor)
        sources.forEach(preferences::recordMediaCandidate)
        val allowedPackages = preferences.loadEnabledMediaSources().mapTo(mutableSetOf()) { it.packageName }
        val featureEnabled = preferences.mediaFeatureEnabled()
        val publishing =
            if (featureEnabled) {
                selections.filter { it.packageName in allowedPackages && it.isPlayingOrBuffering() }
            } else {
                emptyList()
            }

        val liveEventIds = publishing.mapTo(mutableSetOf()) { it.eventId() }
        publishedInstances.toMap().forEach { (eventId, sourceInstance) ->
            if (eventId !in liveEventIds) cancel(eventId, sourceInstance)
        }
        publishing.forEach(::publish)

        val status =
            when {
                !featureEnabled -> "超级岛音乐已关闭"
                allowedPackages.isEmpty() -> "未选择允许的播放器"
                publishing.isEmpty() -> "没有允许的播放器正在播放"
                else -> "已发布 ${publishing.size} 个媒体岛"
            }
        preferences.recordMediaRuntime(status, publishing.size)
    }

    private fun synchronizeCallbacks(controllers: List<MediaController>) {
        val active = controllers.associateBy(MediaController::getSessionToken)
        callbacks.toMap().forEach { (token, registered) ->
            if (token !in active) {
                runCatching { registered.controller.unregisterCallback(registered.callback) }
                callbacks.remove(token)
            }
        }
        active.forEach { (token, controller) ->
            if (token !in callbacks) {
                val callback =
                    object : MediaController.Callback() {
                        override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleReconcile()

                        override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleReconcile()

                        override fun onSessionDestroyed() = scheduleReconcile()
                    }
                controller.registerCallback(callback, handler)
                callbacks[token] = RegisteredCallback(controller, callback)
            }
        }
    }

    private fun publish(controller: MediaController) {
        val eventId = controller.eventId()
        val sourceInstance = controller.sessionToken.hashCode().toLong()
        val command = lifecycle.onAccepted(eventId, sourceInstance)
        val source = sourceFor(controller)
        val policy = preferences.loadPrivacyMode().policy
        val privateContent = policy.hideSourceContent
        val metadata = controller.metadata
        val title = metadata.titleOrNull() ?: source.appLabel
        val artist = metadata.artistOrNull() ?: "正在播放"
        val request =
            FocusNotificationRequest(
                title = if (privateContent) source.appLabel else title,
                text = if (privateContent) "媒体内容已隐藏" else artist,
                progress = 0,
                progressIndeterminate = true,
                shortStatusText = "播放中",
            )
        val visibility =
            if (policy.forceSecretVisibility) Notification.VISIBILITY_SECRET else Notification.VISIBILITY_PRIVATE
        publisher.post(
            request = request,
            smallIconResId = R.drawable.ic_stat_proxy,
            contentIntent = controller.sessionActivity ?: ownContentIntent(),
            notificationId = command.session.notificationId,
            sourceSmallIcon = sourceIcon(controller.packageName),
            visibility = visibility,
        ).onSuccess {
            publishedInstances[eventId] = sourceInstance
        }.onFailure {
            cancel(eventId, sourceInstance)
            preferences.recordMediaRuntime("媒体岛发布失败 ${it.javaClass.simpleName}", 0)
        }
    }

    private fun cancel(eventId: String, sourceInstance: Long) {
        when (val command = lifecycle.onRemoved(eventId, sourceInstance)) {
            is ProxyLifecycleCommand.Cancel -> publisher.cancel(command.session.notificationId)
            else -> Unit
        }
        publishedInstances.remove(eventId)
    }

    private fun sourceFor(controller: MediaController): ObservedMediaSource =
        ObservedMediaSource(
            packageName = controller.packageName,
            appLabel =
                runCatching {
                    appContext.packageManager.getApplicationLabel(
                        appContext.packageManager.getApplicationInfo(
                            controller.packageName,
                            android.content.pm.PackageManager.ApplicationInfoFlags.of(0),
                        ),
                    ).toString()
                }.getOrDefault(controller.packageName),
        )

    private fun sourceIcon(packageName: String): IconCompat? =
        runCatching {
            IconCompat.createWithResource(
                appContext,
                appContext.packageManager.getApplicationInfo(
                    packageName,
                    android.content.pm.PackageManager.ApplicationInfoFlags.of(0),
                ).icon,
            )
        }.getOrNull()

    private fun ownContentIntent(): PendingIntent =
        PendingIntent.getActivity(
            appContext,
            CONTENT_INTENT_REQUEST_CODE,
            appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
                ?: Intent().setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun priority(controller: MediaController): Int =
        when (controller.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> 2
            PlaybackState.STATE_BUFFERING -> 1
            else -> 0
        }

    private fun MediaController.isPlayingOrBuffering(): Boolean =
        playbackState?.state in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING)

    private fun MediaController.eventId(): String = "media:$packageName"

    private fun MediaMetadata?.titleOrNull(): String? =
        this?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: this?.getText(MediaMetadata.METADATA_KEY_TITLE)
                ?.toString()
                ?.trim()
                ?.takeIf(String::isNotBlank)

    private fun MediaMetadata?.artistOrNull(): String? =
        this?.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: this?.getText(MediaMetadata.METADATA_KEY_ARTIST)
                ?.toString()
                ?.trim()
                ?.takeIf(String::isNotBlank)

    private data class RegisteredCallback(
        val controller: MediaController,
        val callback: MediaController.Callback,
    )

    private companion object {
        const val CONTENT_INTENT_REQUEST_CODE = 0x4d45
    }
}
