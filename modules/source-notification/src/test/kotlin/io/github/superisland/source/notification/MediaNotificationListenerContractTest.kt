package io.github.superisland.source.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNotificationListenerContractTest {
    @Test
    fun listenerContractCannotActAsThirdPartyNotificationTransport() {
        assertFalse(MediaNotificationListenerContract.HANDLES_SOURCE_NOTIFICATION_CALLBACKS)
        assertFalse(MediaNotificationListenerContract.REPUBLISHES_THIRD_PARTY_NOTIFICATIONS)
        assertEquals(
            NotificationListenerControlAction.IGNORE,
            MediaNotificationListenerContract.actionFor(
                NotificationListenerControlRequest.LEGACY_SMART_CAPSULE_RULE_CHANGED,
            ),
        )
        assertEquals(
            NotificationListenerControlAction.IGNORE,
            MediaNotificationListenerContract.actionFor(
                NotificationListenerControlRequest.LEGACY_SMART_CAPSULE_REFRESH,
            ),
        )
    }

    @Test
    fun serviceDoesNotOverrideSourceNotificationCallbacks() {
        val declaredMethodNames = NotificationProxyService::class.java.declaredMethods.map { it.name }.toSet()

        assertFalse("onNotificationPosted must remain inherited", "onNotificationPosted" in declaredMethodNames)
        assertFalse("onNotificationRemoved must remain inherited", "onNotificationRemoved" in declaredMethodNames)
    }

    @Test
    fun mediaControlRequestsStillRefreshTheMediaRuntime() {
        assertTrue(MediaNotificationListenerContract.CONTROLS_MEDIA_SESSIONS)
        assertEquals(
            NotificationListenerControlAction.REFRESH_MEDIA,
            MediaNotificationListenerContract.actionFor(NotificationListenerControlRequest.MEDIA_RULE_CHANGED),
        )
        assertEquals(
            NotificationListenerControlAction.REFRESH_MEDIA,
            MediaNotificationListenerContract.actionFor(NotificationListenerControlRequest.MEDIA_REFRESH),
        )
    }

    @Test
    fun listenerLifecycleStillStartsRefreshesAndStopsMediaRuntime() {
        val runtime = RecordingMediaIslandRuntime()
        val lifecycle = MediaIslandServiceLifecycle(runtime)

        lifecycle.onConnected()
        lifecycle.onRefreshRequested()
        lifecycle.onDisconnected()
        lifecycle.onDestroyed()

        assertEquals(
            listOf(
                "start",
                "refresh",
                "stop:媒体监听已断开",
                "stop:媒体监听已停止",
            ),
            runtime.calls,
        )
    }

    private class RecordingMediaIslandRuntime : MediaIslandRuntime {
        val calls = mutableListOf<String>()

        override fun start() {
            calls += "start"
        }

        override fun refresh() {
            calls += "refresh"
        }

        override fun stop(status: String) {
            calls += "stop:$status"
        }
    }
}
