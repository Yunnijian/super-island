package io.github.superisland.testsource

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

class TestSourceActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var stressSequence: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == TestSourceActions.STRESS_200) {
            startStressSequence()
        } else {
            handle(intent)
            finish()
        }
    }

    override fun onDestroy() {
        stressSequence?.let(handler::removeCallbacks)
        super.onDestroy()
    }

    private fun handle(intent: Intent) {
        val manager = getSystemService(NotificationManager::class.java)
        when (intent.action) {
            TestSourceActions.POST_10 -> manager.notify(
                TestSourceActions.NOTIFICATION_ID,
                TestSourceNotifications.build(this, 10),
            )
            TestSourceActions.UPDATE_50 -> manager.notify(
                TestSourceActions.NOTIFICATION_ID,
                TestSourceNotifications.build(this, 50),
            )
            TestSourceActions.POST_AVATAR_BURST -> listOf(10, 30, 50).forEach { progress ->
                manager.notify(
                    TestSourceActions.NOTIFICATION_ID,
                    TestSourceNotifications.build(this, progress),
                )
            }
            TestSourceActions.CANCEL -> manager.cancel(TestSourceActions.NOTIFICATION_ID)
            TestSourceActions.POST_ORDINARY -> manager.notify(
                TestSourceActions.ORDINARY_NOTIFICATION_ID,
                TestSourceNotifications.buildOrdinary(this, updated = false),
            )
            TestSourceActions.UPDATE_ORDINARY -> manager.notify(
                TestSourceActions.ORDINARY_NOTIFICATION_ID,
                TestSourceNotifications.buildOrdinary(this, updated = true),
            )
            TestSourceActions.CANCEL_ORDINARY ->
                manager.cancel(TestSourceActions.ORDINARY_NOTIFICATION_ID)
            TestSourceActions.POST_FOUR -> postFour(manager)
            TestSourceActions.UPDATE_FOUR -> updateFour(manager)
            TestSourceActions.CANCEL_MULTI_INDEX ->
                intent.multiEventIndexOrNull()?.let { manager.cancel(TestSourceActions.multiNotificationId(it)) }
            TestSourceActions.CANCEL_FOUR -> cancelFour(manager)
            TestSourceActions.DISPATCH_NOTIFICATION_ACTION_UPDATE_50 ->
                dispatchNotificationAction(TestSourceActions.NOTIFICATION_ACTION_UPDATE_50)
            TestSourceActions.DISPATCH_NOTIFICATION_ACTION_CANCEL ->
                dispatchNotificationAction(TestSourceActions.NOTIFICATION_ACTION_CANCEL)
            TestSourceActions.START_MEDIA,
            TestSourceActions.UPDATE_MEDIA,
            TestSourceActions.PAUSE_MEDIA,
            TestSourceActions.STOP_MEDIA,
            ->
                startService(
                    Intent(this, TestMediaSessionService::class.java).setAction(intent.action),
                )
        }
    }

    private fun dispatchNotificationAction(action: String) {
        sendBroadcast(
            Intent(this, TestSourceReceiver::class.java).setAction(action),
        )
    }

    private fun postFour(manager: NotificationManager) {
        TestSourceActions.multiEventIndices.forEach { eventIndex ->
            manager.notify(
                TestSourceActions.multiNotificationId(eventIndex),
                TestSourceNotifications.build(this, progress = eventIndex * 20, eventIndex = eventIndex),
            )
        }
        Log.i(TAG, "multi-event posted count=${TestSourceActions.MULTI_EVENT_COUNT}")
    }

    private fun updateFour(manager: NotificationManager) {
        TestSourceActions.multiEventIndices.forEach { eventIndex ->
            manager.notify(
                TestSourceActions.multiNotificationId(eventIndex),
                TestSourceNotifications.build(this, progress = eventIndex * 20 + 10, eventIndex = eventIndex),
            )
        }
        Log.i(TAG, "multi-event updated count=${TestSourceActions.MULTI_EVENT_COUNT}")
    }

    private fun cancelFour(manager: NotificationManager) {
        TestSourceActions.multiEventIndices.reversed().forEach { eventIndex ->
            manager.cancel(TestSourceActions.multiNotificationId(eventIndex))
        }
        Log.i(TAG, "multi-event canceled count=${TestSourceActions.MULTI_EVENT_COUNT}")
    }

    private fun Intent.multiEventIndexOrNull(): Int? =
        getIntExtra(TestSourceActions.EXTRA_EVENT_INDEX, Int.MIN_VALUE)
            .takeIf { it in TestSourceActions.multiEventIndices }

    private fun startStressSequence() {
        val manager = getSystemService(NotificationManager::class.java)
        Log.i(TAG, "stress-200 started cycles=$STRESS_CYCLES")
        stressSequence =
            object : Runnable {
                private var cycle = 0
                private var phase = PHASE_POST_10

                override fun run() {
                    when (phase) {
                        PHASE_POST_10 -> {
                            manager.notify(
                                TestSourceActions.NOTIFICATION_ID,
                                TestSourceNotifications.build(this@TestSourceActivity, 10),
                            )
                            phase = PHASE_UPDATE_50
                            handler.postDelayed(this, EVENT_DELAY_MS)
                        }
                        PHASE_UPDATE_50 -> {
                            manager.notify(
                                TestSourceActions.NOTIFICATION_ID,
                                TestSourceNotifications.build(this@TestSourceActivity, 50),
                            )
                            phase = PHASE_CANCEL
                            handler.postDelayed(this, EVENT_DELAY_MS)
                        }
                        else -> {
                            manager.cancel(TestSourceActions.NOTIFICATION_ID)
                            cycle += 1
                            if (cycle == STRESS_CYCLES) {
                                Log.i(TAG, "stress-200 completed cycles=$cycle")
                                stressSequence = null
                                finish()
                            } else {
                                if (cycle % LOG_INTERVAL_CYCLES == 0) {
                                    Log.i(TAG, "stress-200 progress cycles=$cycle")
                                }
                                phase = PHASE_POST_10
                                handler.postDelayed(this, CANCEL_SETTLE_DELAY_MS)
                            }
                        }
                    }
                }
            }
        handler.post(checkNotNull(stressSequence))
    }

    private companion object {
        const val TAG = "SuperIslandTest"
        const val STRESS_CYCLES = 200
        const val EVENT_DELAY_MS = 120L
        const val CANCEL_SETTLE_DELAY_MS = 120L
        const val LOG_INTERVAL_CYCLES = 25
        const val PHASE_POST_10 = 0
        const val PHASE_UPDATE_50 = 1
        const val PHASE_CANCEL = 2
    }
}
