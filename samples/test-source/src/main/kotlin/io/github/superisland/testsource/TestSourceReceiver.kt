package io.github.superisland.testsource

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.util.Log

class TestSourceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val targetNotificationId =
            intent.getIntExtra(TestSourceActions.EXTRA_NOTIFICATION_ID, TestSourceActions.NOTIFICATION_ID)
        val eventIndex =
            intent.getIntExtra(TestSourceActions.EXTRA_EVENT_INDEX, Int.MIN_VALUE)
                .takeIf { it in TestSourceActions.multiEventIndices }
        when (intent.action) {
            TestSourceActions.POST_10 -> manager.notify(
                TestSourceActions.NOTIFICATION_ID,
                TestSourceNotifications.build(context, 10),
            )
            TestSourceActions.UPDATE_50 -> manager.notify(
                TestSourceActions.NOTIFICATION_ID,
                TestSourceNotifications.build(context, 50),
            )
            TestSourceActions.POST_AVATAR_BURST -> listOf(10, 30, 50).forEach { progress ->
                manager.notify(
                    TestSourceActions.NOTIFICATION_ID,
                    TestSourceNotifications.build(context, progress),
                )
            }
            TestSourceActions.CANCEL -> manager.cancel(TestSourceActions.NOTIFICATION_ID)
            TestSourceActions.NOTIFICATION_ACTION_UPDATE_ORDINARY -> {
                Log.i(TAG, "notification-action id=update_ordinary")
                manager.notify(
                    TestSourceActions.ORDINARY_NOTIFICATION_ID,
                    TestSourceNotifications.buildOrdinary(context, updated = true),
                )
            }
            TestSourceActions.NOTIFICATION_ACTION_CANCEL_ORDINARY -> {
                Log.i(TAG, "notification-action id=cancel_ordinary")
                manager.cancel(TestSourceActions.ORDINARY_NOTIFICATION_ID)
            }
            TestSourceActions.NOTIFICATION_ACTION_UPDATE_50 -> {
                Log.i(TAG, "notification-action id=update_50")
                manager.notify(
                    targetNotificationId,
                    TestSourceNotifications.build(context, 50, eventIndex),
                )
            }
            TestSourceActions.NOTIFICATION_ACTION_CANCEL -> {
                Log.i(TAG, "notification-action id=cancel")
                manager.cancel(targetNotificationId)
            }
        }
    }

    private companion object {
        const val TAG = "SuperIslandTest"
    }
}

object TestSourceActions {
    const val POST_10 = "io.github.superisland.testsource.POST_10"
    const val UPDATE_50 = "io.github.superisland.testsource.UPDATE_50"
    const val POST_AVATAR_BURST = "io.github.superisland.testsource.POST_AVATAR_BURST"
    const val CANCEL = "io.github.superisland.testsource.CANCEL"
    const val POST_ORDINARY = "io.github.superisland.testsource.POST_ORDINARY"
    const val UPDATE_ORDINARY = "io.github.superisland.testsource.UPDATE_ORDINARY"
    const val CANCEL_ORDINARY = "io.github.superisland.testsource.CANCEL_ORDINARY"
    const val STRESS_200 = "io.github.superisland.testsource.STRESS_200"
    const val POST_FOUR = "io.github.superisland.testsource.POST_FOUR"
    const val UPDATE_FOUR = "io.github.superisland.testsource.UPDATE_FOUR"
    const val CANCEL_MULTI_INDEX = "io.github.superisland.testsource.CANCEL_MULTI_INDEX"
    const val CANCEL_FOUR = "io.github.superisland.testsource.CANCEL_FOUR"
    const val NOTIFICATION_ACTION_UPDATE_50 =
        "io.github.superisland.testsource.NOTIFICATION_ACTION_UPDATE_50"
    const val NOTIFICATION_ACTION_CANCEL =
        "io.github.superisland.testsource.NOTIFICATION_ACTION_CANCEL"
    const val NOTIFICATION_ACTION_UPDATE_ORDINARY =
        "io.github.superisland.testsource.NOTIFICATION_ACTION_UPDATE_ORDINARY"
    const val NOTIFICATION_ACTION_CANCEL_ORDINARY =
        "io.github.superisland.testsource.NOTIFICATION_ACTION_CANCEL_ORDINARY"
    const val DISPATCH_NOTIFICATION_ACTION_UPDATE_50 =
        "io.github.superisland.testsource.DISPATCH_NOTIFICATION_ACTION_UPDATE_50"
    const val DISPATCH_NOTIFICATION_ACTION_CANCEL =
        "io.github.superisland.testsource.DISPATCH_NOTIFICATION_ACTION_CANCEL"
    const val START_MEDIA = "io.github.superisland.testsource.START_MEDIA"
    const val UPDATE_MEDIA = "io.github.superisland.testsource.UPDATE_MEDIA"
    const val PAUSE_MEDIA = "io.github.superisland.testsource.PAUSE_MEDIA"
    const val STOP_MEDIA = "io.github.superisland.testsource.STOP_MEDIA"
    const val EXTRA_NOTIFICATION_ID = "io.github.superisland.testsource.extra.NOTIFICATION_ID"
    const val EXTRA_EVENT_INDEX = "io.github.superisland.testsource.extra.EVENT_INDEX"
    const val NOTIFICATION_ID = 0x4d32
    const val ORDINARY_NOTIFICATION_ID = 0x4d33
    const val MULTI_EVENT_COUNT = 4

    val multiEventIndices: IntRange = 1..MULTI_EVENT_COUNT

    fun multiNotificationId(eventIndex: Int): Int {
        require(eventIndex in multiEventIndices) { "eventIndex must be between 1 and $MULTI_EVENT_COUNT" }
        return MULTI_NOTIFICATION_ID_BASE + eventIndex
    }

    private const val MULTI_NOTIFICATION_ID_BASE = 0x4d40
}

object TestSourceNotifications {
    fun build(
        context: Context,
        progress: Int,
        eventIndex: Int? = null,
    ): android.app.Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        val notificationId = eventIndex?.let(TestSourceActions::multiNotificationId) ?: TestSourceActions.NOTIFICATION_ID
        val title = eventIndex?.let { "测试资源同步 #$it" } ?: "测试资源同步"
        val text =
            eventIndex?.let { "事件 $it · 进度 $progress%" } ?: "进度 $progress%"
        return android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_test_source)
            .setLargeIcon(testAvatar(progress))
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(PROGRESS_MAX, progress, false)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "更新到 50%",
                    actionIntent(
                        context,
                        TestSourceActions.NOTIFICATION_ACTION_UPDATE_50,
                        notificationId,
                        eventIndex,
                        1,
                    ),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    "取消",
                    actionIntent(
                        context,
                        TestSourceActions.NOTIFICATION_ACTION_CANCEL,
                        notificationId,
                        eventIndex,
                        2,
                    ),
                ).build(),
            )
            .build()
    }

    fun buildOrdinary(
        context: Context,
        updated: Boolean,
    ): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(ORDINARY_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ORDINARY_CHANNEL_ID,
                    context.getString(R.string.ordinary_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        return Notification.Builder(context, ORDINARY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_test_source)
            .setLargeIcon(testAvatar(if (updated) 75 else 25))
            .setContentTitle(if (updated) "普通通知已更新" else "普通通知测试")
            .setContentText(if (updated) "同一通知已完成 UPDATE" else "用于验证超级岛 POST 与按钮")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "更新",
                    actionIntent(
                        context,
                        TestSourceActions.NOTIFICATION_ACTION_UPDATE_ORDINARY,
                        TestSourceActions.ORDINARY_NOTIFICATION_ID,
                        null,
                        1,
                    ),
                ).build(),
            ).addAction(
                Notification.Action.Builder(
                    null,
                    "取消",
                    actionIntent(
                        context,
                        TestSourceActions.NOTIFICATION_ACTION_CANCEL_ORDINARY,
                        TestSourceActions.ORDINARY_NOTIFICATION_ID,
                        null,
                        2,
                    ),
                ).build(),
            ).build()
    }

    private fun testAvatar(progress: Int): Icon {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(
            Color.rgb(
                40 + progress.coerceIn(0, 100),
                120,
                220 - progress.coerceIn(0, 100),
            ),
        )
        return Icon.createWithBitmap(bitmap)
    }

    private fun actionIntent(
        context: Context,
        action: String,
        notificationId: Int,
        eventIndex: Int?,
        actionOffset: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            notificationId * 2 + actionOffset,
            Intent(context, TestSourceReceiver::class.java)
                .setAction(action)
                .putExtra(TestSourceActions.EXTRA_NOTIFICATION_ID, notificationId)
                .apply {
                    eventIndex?.let { putExtra(TestSourceActions.EXTRA_EVENT_INDEX, it) }
                },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private const val CHANNEL_ID = "m2_test_progress"
    const val ORDINARY_CHANNEL_ID = "smart_capsule_test_ordinary"
    private const val PROGRESS_MAX = 100
}
