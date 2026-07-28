package io.github.superisland.hook.systemui

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperIslandLocalNotificationPolicyTest {
    @Test
    fun `known OEM focus ownership markers are protected without matching arbitrary helpers`() {
        assertTrue(HyperIslandLocalNotificationPolicy.hasFocusOwnershipMarker(setOf("miui.focus.param.media")))
        assertTrue(HyperIslandLocalNotificationPolicy.hasFocusOwnershipMarker(setOf("miui.focus.rv")))
        assertTrue(HyperIslandLocalNotificationPolicy.hasFocusOwnershipMarker(setOf("miui.focus.pics")))
        assertTrue(HyperIslandLocalNotificationPolicy.hasFocusOwnershipMarker(setOf("miui.focus.isFocus")))
        assertFalse(HyperIslandLocalNotificationPolicy.hasFocusOwnershipMarker(setOf("miui.focus.helper")))
        assertFalse(HyperIslandLocalNotificationPolicy.hasFocusOwnershipMarker(emptySet()))
    }

    @Test
    fun `zero-valued builder placeholders are not treated as progress notifications`() {
        assertFalse(HyperIslandLocalNotificationPolicy.hasDeterminateProgress(0, false))
        assertFalse(HyperIslandLocalNotificationPolicy.hasDeterminateProgress(100, true))
        assertTrue(HyperIslandLocalNotificationPolicy.hasDeterminateProgress(100, false))
        assertFalse(HyperIslandLocalNotificationPolicy.hasProgressStructure(false, 0, false))
        assertTrue(HyperIslandLocalNotificationPolicy.hasProgressStructure(true, 0, false))
    }

    @Test
    fun `title and content use the required public field order`() {
        val resolved =
            HyperIslandLocalNotificationPolicy.resolveText(
                PublicNotificationText(
                    title = "title",
                    bigTitle = "big title",
                    conversationTitle = "conversation",
                    text = "text",
                    bigText = "big text",
                    subText = "sub text",
                    infoText = "info",
                    ticker = "ticker",
                    channelId = "channel",
                    appLabel = "App",
                    packageName = "example.app",
                ),
            )

        assertEquals("title", resolved.title)
        assertEquals("text", resolved.content)
    }

    @Test
    fun `blank fields fall through big conversation and ticker before common fallback`() {
        val resolved =
            HyperIslandLocalNotificationPolicy.resolveText(
                PublicNotificationText(
                    title = "  ",
                    bigTitle = null,
                    conversationTitle = "Conversation",
                    text = "",
                    bigText = " ",
                    subText = null,
                    infoText = null,
                    ticker = "Ticker",
                    channelId = "channel",
                    appLabel = "App",
                    packageName = "example.app",
                ),
            )

        assertEquals("Conversation", resolved.title)
        assertEquals("Ticker", resolved.content)
    }

    @Test
    fun `channel app label and package are the final fallback chain`() {
        assertEquals(
            ResolvedNotificationText("channel", "channel"),
            HyperIslandLocalNotificationPolicy.resolveText(
                PublicNotificationText(channelId = "channel", appLabel = "App", packageName = "example.app"),
            ),
        )
        assertEquals(
            ResolvedNotificationText("App", "App"),
            HyperIslandLocalNotificationPolicy.resolveText(
                PublicNotificationText(channelId = " ", appLabel = "App", packageName = "example.app"),
            ),
        )
        assertEquals(
            ResolvedNotificationText("example.app", "example.app"),
            HyperIslandLocalNotificationPolicy.resolveText(
                PublicNotificationText(channelId = null, appLabel = null, packageName = "example.app"),
            ),
        )
    }

    @Test
    fun `every unsupported structure fails safe and ordinary layout is accepted`() {
        assertEquals(
            LocalNotificationSkipReason.EXISTING_FOCUS,
            HyperIslandLocalNotificationPolicy.structuralSkipReason(
                NotificationStructure(hasExistingFocus = true),
            ),
        )
        assertEquals(
            LocalNotificationSkipReason.CUSTOM_FOCUS,
            HyperIslandLocalNotificationPolicy.structuralSkipReason(
                NotificationStructure(hasCustomFocus = true),
            ),
        )
        assertEquals(
            LocalNotificationSkipReason.MEDIA,
            HyperIslandLocalNotificationPolicy.structuralSkipReason(NotificationStructure(isMedia = true)),
        )
        assertEquals(
            LocalNotificationSkipReason.BUBBLE,
            HyperIslandLocalNotificationPolicy.structuralSkipReason(NotificationStructure(isBubble = true)),
        )
        assertEquals(
            LocalNotificationSkipReason.FULL_SCREEN,
            HyperIslandLocalNotificationPolicy.structuralSkipReason(NotificationStructure(isFullScreen = true)),
        )
        assertEquals(
            LocalNotificationSkipReason.GROUP_SUMMARY,
            HyperIslandLocalNotificationPolicy.structuralSkipReason(
                NotificationStructure(isGroupSummary = true),
            ),
        )
        assertEquals(
            LocalNotificationSkipReason.PROGRESS,
            HyperIslandLocalNotificationPolicy.structuralSkipReason(NotificationStructure(hasProgress = true)),
        )

        // Ordinary custom contentView/bigContentView/headsUpContentView is intentionally absent
        // from the policy input: its presence cannot reject or influence mapping.
        assertNull(HyperIslandLocalNotificationPolicy.structuralSkipReason(NotificationStructure()))
    }

    @Test
    fun `key gate bounds distinct active keys and releases capacity`() {
        val gate = BoundedSbnKeyGate(capacity = 1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val worker =
            thread(start = true, name = "sbn-key-gate-test") {
                gate.withKey("key-a") {
                    entered.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                }
                finished.countDown()
            }

        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertEquals(1, gate.activeKeyCount())
        val rejected = gate.withKey("key-b") { error("must not run") }
        assertTrue(rejected is BoundedSbnKeyGate.GateResult.Rejected)
        assertEquals(
            LocalNotificationSkipReason.CAPACITY,
            (rejected as BoundedSbnKeyGate.GateResult.Rejected).reason,
        )

        release.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        worker.join(5_000)
        assertFalse(worker.isAlive)
        assertEquals(0, gate.activeKeyCount())
        assertTrue(gate.withKey("key-b") { true } is BoundedSbnKeyGate.GateResult.Completed)
    }
}
