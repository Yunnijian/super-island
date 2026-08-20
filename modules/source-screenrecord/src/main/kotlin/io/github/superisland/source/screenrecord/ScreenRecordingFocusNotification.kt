package io.github.superisland.source.screenrecord

import android.app.Notification
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.graphics.drawable.IconCompat
import io.github.superisland.model.FocusNotificationRequest
import io.github.superisland.model.IslandPriority
import io.github.superisland.model.ScreenRecordingAudioSource
import io.github.superisland.publisher.focus.FocusCustomRemoteViews
import io.github.superisland.publisher.focus.FocusNotificationAction
import io.github.superisland.publisher.focus.FocusNotificationPublisher
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Scheme A: the mediaProjection FGS notification itself carries Xiaomi Focus / island payload
 * via [FocusNotificationPublisher], so recording shows on the Dynamic Island without a second notify.
 */
class ScreenRecordingFocusNotification(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val publisher =
        FocusNotificationPublisher(
            appContext,
            appContext.getString(R.string.screen_recording_notification_channel),
        )

    fun buildActive(
        elapsedMillis: Long,
        paused: Boolean,
        audioSource: ScreenRecordingAudioSource,
        pauseResumeIntent: PendingIntent,
        stopIntent: PendingIntent,
        showInNotificationShade: Boolean,
    ): Notification {
        val duration = formatDuration(elapsedMillis)
        val title = if (paused) "录屏暂停" else "正在录屏..."
        val audioSummary = audioSummary(audioSource)
        val request =
            FocusNotificationRequest(
                title = title,
                text = "$audioSummary · $duration",
                progressIndeterminate = true,
                shortStatusText = duration,
            )
        return publisher.buildNotification(
            request = request,
            smallIconResId = R.drawable.ic_screen_recording,
            contentIntent = null,
            leftIslandIcon =
                IconCompat.createWithResource(appContext, R.drawable.ic_screen_recording_dot),
            actions =
                listOf(
                    FocusNotificationAction(
                        title =
                            appContext.getString(
                                if (paused) {
                                    R.string.screen_recording_notification_resume
                                } else {
                                    R.string.screen_recording_notification_pause
                                },
                            ),
                        intent = pauseResumeIntent,
                    ),
                    FocusNotificationAction(
                        title = appContext.getString(R.string.screen_recording_notification_stop),
                        intent = stopIntent,
                    ),
                ),
            // The initial FGS stays island-only while the projection hand-off settles. The service
            // updates this same notification to a visible, silent Focus row once encoding starts.
            showInNotificationShade = showInNotificationShade,
            showLeftIslandIcon = true,
            showRightIslandIcon = false,
            customRemoteViews =
                activeRemoteViews(
                    title = title,
                    duration = duration,
                    paused = paused,
                    pauseResumeIntent = pauseResumeIntent,
                    stopIntent = stopIntent,
                ),
            silent = true,
            islandPriority = IslandPriority.HIGH,
        )
    }

    fun buildCompleted(
        outputUri: Uri,
        savedToGallery: Boolean,
    ): Notification {
        require(outputUri.scheme == ContentResolver.SCHEME_CONTENT) {
            "Recording output must use a content URI"
        }
        val title = if (savedToGallery) "已保存至“相册”" else "录屏已保存"
        val viewIntent =
            ScreenRecordingCompletionActionActivity.viewPendingIntent(appContext, outputUri)
        val shareIntent =
            ScreenRecordingCompletionActionActivity.sharePendingIntent(appContext, outputUri)
        return publisher.buildNotification(
            request =
                FocusNotificationRequest(
                    title = title,
                    text = "点击查看",
                    shortStatusText = "已保存",
                ),
            smallIconResId = R.drawable.ic_screen_recording,
            contentIntent = viewIntent,
            leftIslandIcon =
                IconCompat.createWithResource(appContext, R.drawable.ic_screen_recording_complete),
            actions =
                listOf(
                    FocusNotificationAction(
                        title = appContext.getString(R.string.screen_recording_notification_share),
                        intent = shareIntent,
                    ),
                ),
            showInNotificationShade = true,
            showLeftIslandIcon = true,
            showRightIslandIcon = false,
            customRemoteViews = completedRemoteViews(title, viewIntent, shareIntent),
            silent = true,
            ongoing = false,
            timeoutAfterMillis = COMPLETION_TIMEOUT_MILLIS,
            islandPriority = IslandPriority.HIGH,
        )
    }

    private fun activeRemoteViews(
        title: String,
        duration: String,
        paused: Boolean,
        pauseResumeIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): FocusCustomRemoteViews {
        fun notificationView(layoutId: Int): RemoteViews =
            RemoteViews(appContext.packageName, layoutId).apply {
                setTextViewText(R.id.screen_recording_focus_notification_timer, duration)
                setTextViewText(R.id.screen_recording_focus_notification_status, title)
                setTextViewText(
                    R.id.screen_recording_focus_notification_pause,
                    appContext.getString(
                        if (paused) {
                            R.string.screen_recording_notification_resume
                        } else {
                            R.string.screen_recording_notification_pause
                        },
                    ),
                )
                setTextViewText(
                    R.id.screen_recording_focus_notification_stop,
                    appContext.getString(R.string.screen_recording_notification_stop),
                )
                setOnClickPendingIntent(
                    R.id.screen_recording_focus_notification_pause,
                    pauseResumeIntent,
                )
                setOnClickPendingIntent(R.id.screen_recording_focus_notification_stop, stopIntent)
            }

        val expanded =
            RemoteViews(appContext.packageName, R.layout.screen_recording_focus_expanded).apply {
                setTextViewText(R.id.screen_recording_focus_expanded_title, title)
                setTextViewText(R.id.screen_recording_focus_expanded_timer, duration)
                setImageViewResource(
                    R.id.screen_recording_focus_pause_icon,
                    if (paused) {
                        R.drawable.ic_screen_recording_resume
                    } else {
                        R.drawable.ic_screen_recording_pause
                    },
                )
                setContentDescription(
                    R.id.screen_recording_focus_pause,
                    appContext.getString(
                        if (paused) {
                            R.string.screen_recording_focus_resume
                        } else {
                            R.string.screen_recording_focus_pause
                        },
                    ),
                )
                setOnClickPendingIntent(R.id.screen_recording_focus_pause, pauseResumeIntent)
                setOnClickPendingIntent(R.id.screen_recording_focus_stop, stopIntent)
            }
        return FocusCustomRemoteViews(
            day = notificationView(R.layout.screen_recording_focus_notification),
            night = notificationView(R.layout.screen_recording_focus_notification_night),
            islandExpanded = expanded,
        )
    }

    private fun completedRemoteViews(
        title: String,
        viewIntent: PendingIntent,
        shareIntent: PendingIntent,
    ): FocusCustomRemoteViews {
        fun notificationView(layoutId: Int): RemoteViews =
            RemoteViews(appContext.packageName, layoutId).apply {
                setTextViewText(R.id.screen_recording_focus_notification_complete_title, title)
                setTextViewText(
                    R.id.screen_recording_focus_notification_complete_subtitle,
                    appContext.getString(R.string.screen_recording_notification_tap_to_view),
                )
                setOnClickPendingIntent(
                    R.id.screen_recording_focus_notification_complete_root,
                    viewIntent,
                )
                setOnClickPendingIntent(
                    R.id.screen_recording_focus_notification_complete_share,
                    shareIntent,
                )
            }

        val islandExpanded =
            RemoteViews(appContext.packageName, R.layout.screen_recording_focus_complete).apply {
                setTextViewText(R.id.screen_recording_focus_complete_title, title)
                setTextViewText(
                    R.id.screen_recording_focus_complete_subtitle,
                    appContext.getString(R.string.screen_recording_notification_tap_to_view),
                )
                setOnClickPendingIntent(R.id.screen_recording_focus_complete_root, viewIntent)
                setOnClickPendingIntent(R.id.screen_recording_focus_complete_share, shareIntent)
            }
        return FocusCustomRemoteViews(
            day = notificationView(R.layout.screen_recording_focus_notification_complete),
            night = notificationView(R.layout.screen_recording_focus_notification_complete_night),
            islandExpanded = islandExpanded,
        )
    }

    private fun audioSummary(source: ScreenRecordingAudioSource): String =
        when (source) {
            ScreenRecordingAudioSource.NONE -> "无声音"
            ScreenRecordingAudioSource.INTERNAL -> "系统声音"
            ScreenRecordingAudioSource.MICROPHONE -> "麦克风"
            ScreenRecordingAudioSource.BOTH -> "系统声音 + 麦克风"
        }

    companion object {
        const val NOTIFICATION_ID = 28_371
        private const val COMPLETION_TIMEOUT_MILLIS = 4_000L

        fun formatDuration(elapsedMillis: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis.coerceAtLeast(0L))
            val hours = totalSeconds / 3_600
            val minutes = (totalSeconds % 3_600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

    }
}
