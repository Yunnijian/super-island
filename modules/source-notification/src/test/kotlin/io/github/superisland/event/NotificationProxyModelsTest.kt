package io.github.superisland.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationProxyModelsTest {
    @Test
    fun ruleIsDefaultDenyAndMatchesExactPackageAndChannel() {
        val candidate = candidate()

        assertEquals(
            NotificationRuleDecision.Rejected(NotificationRejectionReason.RULE_DISABLED),
            NotificationRuleEvaluator.evaluate(null, candidate, SELF_PACKAGE),
        )
        assertEquals(
            NotificationRuleDecision.Accepted,
            NotificationRuleEvaluator.evaluate(
                NotificationProxyRule(candidate.packageName, candidate.channelId),
                candidate,
                SELF_PACKAGE,
            ),
        )
    }

    @Test
    fun unsafeAndSelfNotificationsAreRejectedBeforeRuleMatching() {
        val rule = NotificationProxyRule("source.app", "progress")

        assertEquals(
            NotificationRejectionReason.REMOTE_INPUT,
            (NotificationRuleEvaluator.evaluate(rule, candidate(remoteInput = true), SELF_PACKAGE) as
                NotificationRuleDecision.Rejected).reason,
        )
        assertEquals(
            NotificationRejectionReason.SELF_NOTIFICATION,
            (
                NotificationRuleEvaluator.evaluate(
                    rule,
                    candidate(packageName = SELF_PACKAGE),
                    SELF_PACKAGE,
                ) as NotificationRuleDecision.Rejected
            ).reason,
        )
    }

    @Test
    fun multipleRulesStillRequireAnExactPackageAndChannelMatch() {
        val rules =
            listOf(
                NotificationProxyRule("source.app", "progress"),
                NotificationProxyRule("second.app", "tracking"),
            )

        assertEquals(
            NotificationRuleDecision.Accepted,
            NotificationRuleEvaluator.evaluate(
                rules,
                candidate(packageName = "second.app", channelId = "tracking"),
                SELF_PACKAGE,
            ),
        )
        assertEquals(
            NotificationRejectionReason.CHANNEL_NOT_ALLOWED,
            (
                NotificationRuleEvaluator.evaluate(
                    rules,
                    candidate(packageName = "second.app", channelId = "other"),
                    SELF_PACKAGE,
                ) as NotificationRuleDecision.Rejected
            ).reason,
        )
    }

    @Test
    fun unknownStoredPrivacyModeFallsBackToSourceVisibility() {
        assertEquals(
            NotificationProxyPrivacyMode.INHERIT_SOURCE,
            NotificationProxyPrivacyMode.fromStored("removed_mode"),
        )
    }

    @Test
    fun privacyPoliciesDoNotExposeSourceContentUnlessExplicitlyInherited() {
        assertEquals(
            NotificationProxyPrivacyPolicy(
                hideSourceContent = false,
                forceSecretVisibility = false,
            ),
            NotificationProxyPrivacyMode.INHERIT_SOURCE.policy,
        )
        assertEquals(
            NotificationProxyPrivacyPolicy(
                hideSourceContent = true,
                forceSecretVisibility = false,
            ),
            NotificationProxyPrivacyMode.HIDE_CONTENT.policy,
        )
        assertEquals(
            NotificationProxyPrivacyPolicy(
                hideSourceContent = true,
                forceSecretVisibility = true,
            ),
            NotificationProxyPrivacyMode.HIDE_ON_LOCK_SCREEN.policy,
        )
    }

    @Test
    fun lifecycleIgnoresStaleRemovalAndAdvancesGenerationForReusedKey() {
        val lifecycle = NotificationProxyLifecycle()
        val created = lifecycle.onAccepted("event", sourceInstance = 10L)
        val updated = lifecycle.onAccepted("event", sourceInstance = 10L)
        val replaced = lifecycle.onAccepted("event", sourceInstance = 20L)

        assertEquals(1L, created.session.generation)
        assertTrue(updated.update)
        assertEquals(2L, replaced.session.generation)
        assertEquals(created.session.notificationId, replaced.session.notificationId)
        assertEquals(ProxyLifecycleCommand.None, lifecycle.onRemoved("event", 10L))
        assertTrue(lifecycle.onRemoved("event", 20L) is ProxyLifecycleCommand.Cancel)
        assertEquals(0, lifecycle.activeCount())
    }

    @Test
    fun lifecycleKeepsFourIndependentEventsUntilTheirOwnRemoval() {
        val lifecycle = NotificationProxyLifecycle()
        val published =
            (1..4).map { eventIndex ->
                lifecycle.onAccepted("event-$eventIndex", sourceInstance = eventIndex.toLong())
            }

        assertEquals(4, lifecycle.activeCount())
        assertEquals(4, published.map { it.session.notificationId }.toSet().size)
        assertTrue(lifecycle.onAccepted("event-2", sourceInstance = 2L).update)

        (4 downTo 1).forEach { eventIndex ->
            assertTrue(
                lifecycle.onRemoved("event-$eventIndex", sourceInstance = eventIndex.toLong()) is
                    ProxyLifecycleCommand.Cancel,
            )
            assertEquals(eventIndex - 1, lifecycle.activeCount())
        }
    }

    @Test
    fun callbackDeduplicatorSuppressesRepeatedBindingsWithoutBlockingReplay() {
        val deduplicator = NotificationCallbackDeduplicator()

        assertTrue(deduplicator.shouldProcess("event", sourceInstance = 10L))
        assertFalse(deduplicator.shouldProcess("event", sourceInstance = 10L))
        assertTrue(deduplicator.shouldProcess("event", sourceInstance = 20L))

        deduplicator.onRemoved("event", sourceInstance = 10L)
        assertFalse(deduplicator.shouldProcess("event", sourceInstance = 20L))
        deduplicator.onRemoved("event", sourceInstance = 20L)
        assertTrue(deduplicator.shouldProcess("event", sourceInstance = 20L))

        deduplicator.clear()
        assertTrue(deduplicator.shouldProcess("event", sourceInstance = 20L))
    }

    @Test
    fun notificationIdAllocatorResolvesHashCollisions() {
        val allocator = NotificationIdAllocator(hash = { 7 })

        val first = allocator.allocate("first")
        val second = allocator.allocate("second")

        assertNotEquals(first, second)
        allocator.release("first")
        assertEquals(first, allocator.allocate("third"))
    }

    @Test
    fun notificationIdAllocatorKeepsMediaNamespaceSeparate() {
        val proxyId = NotificationIdAllocator(hash = { 7 }).allocate("same-hash")
        val mediaId =
            NotificationIdAllocator(
                hash = { 7 },
                idPrefix = NotificationIdAllocator.MEDIA_ID_PREFIX,
            ).allocate("same-hash")

        assertNotEquals(proxyId, mediaId)
        assertEquals(NotificationIdAllocator.MEDIA_ID_PREFIX, mediaId and 0xf0000000.toInt())
    }

    private fun candidate(
        packageName: String = "source.app",
        channelId: String = "progress",
        remoteInput: Boolean = false,
    ): NotificationCandidate =
        NotificationCandidate(
            eventId = "0|source.app|1",
            sourceInstance = 1L,
            packageName = packageName,
            channelId = channelId,
            title = "下载",
            text = "正在下载",
            shortStatus = "20%",
            ongoing = true,
            progress = 20,
            progressMax = 100,
            progressIndeterminate = false,
            systemApp = false,
            groupSummary = false,
            bubble = false,
            fullScreenIntent = false,
            customViews = false,
            remoteInput = remoteInput,
        )

    private companion object {
        const val SELF_PACKAGE = "io.github.superisland"
    }
}
