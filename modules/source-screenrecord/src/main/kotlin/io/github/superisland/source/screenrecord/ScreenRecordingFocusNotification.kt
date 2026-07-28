package io.github.superisland.source.screenrecord

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import io.github.superisland.model.FocusNotificationRequest
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

    fun build(
        elapsedMillis: Long,
        stopIntent: PendingIntent,
        contentIntent: PendingIntent? = null,
    ): Notification {
        val duration = formatDuration(elapsedMillis)
        val request =
            FocusNotificationRequest(
                title = "正在录制",
                text = "超级岛录屏 · $duration",
                progressIndeterminate = true,
                shortStatusText = duration,
            )
        return publisher.buildNotification(
            request = request,
            smallIconResId = R.drawable.ic_screen_recording,
            contentIntent = contentIntent,
            actions =
                listOf(
                    FocusNotificationAction(
                        title = appContext.getString(R.string.screen_recording_notification_stop),
                        intent = stopIntent,
                    ),
                ),
            // Island-only presentation: shade heads-up / default-importance flash made the
            // status bar blink on every start even when PROJECT_MEDIA auto-granted consent.
            showInNotificationShade = false,
            showLeftIslandIcon = true,
            showRightIslandIcon = false,
            silent = true,
        )
    }

    companion object {
        const val NOTIFICATION_ID = 28_371

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

        fun elapsedSince(startedAtElapsedRealtime: Long): Long =
            (SystemClock.elapsedRealtime() - startedAtElapsedRealtime).coerceAtLeast(0L)
    }
}
