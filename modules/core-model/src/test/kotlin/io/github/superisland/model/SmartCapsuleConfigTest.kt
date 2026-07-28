package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCapsuleConfigTest {
    @Test
    fun islandPriorityUsesExplicitHyperOsProtocolValues() {
        assertEquals(0, IslandPriority.HIGH.wireValue)
        assertEquals(1, IslandPriority.MEDIUM.wireValue)
        assertEquals(2, IslandPriority.LOW.wireValue)
        assertEquals(IslandPriority.HIGH, IslandPriority.fromWireValueOrNull(0))
        assertEquals(IslandPriority.MEDIUM, IslandPriority.fromWireValueOrNull(1))
        assertEquals(IslandPriority.LOW, IslandPriority.fromWireValueOrNull(2))
        assertNull(IslandPriority.fromWireValueOrNull(-1))
        assertNull(IslandPriority.fromWireValueOrNull(3))
    }

    @Test
    fun focusDisplayModeUsesStableProtocolValues() {
        assertEquals(0, FocusDisplayMode.ISLAND_AND_FOCUS.wireValue)
        assertEquals(1, FocusDisplayMode.FOCUS_ONLY.wireValue)
        assertEquals(
            FocusDisplayMode.ISLAND_AND_FOCUS,
            FocusDisplayMode.fromWireValueOrNull(0),
        )
        assertEquals(FocusDisplayMode.FOCUS_ONLY, FocusDisplayMode.fromWireValueOrNull(1))
        assertNull(FocusDisplayMode.fromWireValueOrNull(-1))
        assertNull(FocusDisplayMode.fromWireValueOrNull(2))
    }

    @Test
    fun channelDisplayModeOverridesAreSparseAndInheritTheAppDefault() {
        val rule =
            AppRule(
                packageName = "com.example.app",
                channels =
                    ChannelSelection(
                        channelIds = listOf("messages", "status"),
                        focusDisplayModeOverrides =
                            mapOf(
                                "messages" to FocusDisplayMode.FOCUS_ONLY,
                                "orphan" to FocusDisplayMode.FOCUS_ONLY,
                                "status" to FocusDisplayMode.ISLAND_AND_FOCUS,
                            ),
                    ),
            ).normalizedOrNull()

        requireNotNull(rule)
        assertEquals(
            mapOf("messages" to FocusDisplayMode.FOCUS_ONLY),
            rule.channels.focusDisplayModeOverrides,
        )
        assertEquals(FocusDisplayMode.FOCUS_ONLY, rule.effectiveFocusDisplayMode("messages"))
        assertEquals(
            FocusDisplayMode.ISLAND_AND_FOCUS,
            rule.effectiveFocusDisplayMode("status"),
        )
    }

    @Test
    fun channelOverridesAreSparseAndNewChannelsInheritTheAppDefault() {
        val rule =
            AppRule(
                packageName = "com.example.app",
                channels =
                    ChannelSelection(
                        channelIds = listOf("messages", "new", "urgent"),
                        islandPriorityOverrides =
                            mapOf(
                                "messages" to IslandPriority.MEDIUM,
                                "orphan" to IslandPriority.HIGH,
                                "urgent" to IslandPriority.HIGH,
                            ),
                    ),
                islandPriority = IslandPriority.MEDIUM,
            ).normalizedOrNull()

        requireNotNull(rule)
        assertEquals(
            mapOf("urgent" to IslandPriority.HIGH),
            rule.channels.islandPriorityOverrides,
        )
        assertEquals(IslandPriority.HIGH, rule.effectiveIslandPriority("urgent"))
        assertEquals(IslandPriority.MEDIUM, rule.effectiveIslandPriority("new"))
    }

    @Test
    fun allChannelRulesRetainSpecificPriorityOverrides() {
        val rule =
            AppRule(
                packageName = "com.example.app",
                channels =
                    ChannelSelection(
                        allChannels = true,
                        islandPriorityOverrides = mapOf("urgent" to IslandPriority.HIGH),
                    ),
                islandPriority = IslandPriority.LOW,
            ).normalizedOrNull()

        requireNotNull(rule)
        assertEquals(IslandPriority.HIGH, rule.effectiveIslandPriority("urgent"))
        assertEquals(IslandPriority.LOW, rule.effectiveIslandPriority("new"))
    }

    @Test
    fun conflictingDuplicatePrioritiesNormalizeFailClosed() {
        val normalized =
            SmartCapsuleConfigSnapshot(
                rules =
                    listOf(
                        AppRule(
                            "com.example.app",
                            ChannelSelection(
                                allChannels = true,
                                islandPriorityOverrides = mapOf("messages" to IslandPriority.HIGH),
                            ),
                            IslandPriority.MEDIUM,
                        ),
                        AppRule(
                            "com.example.app",
                            ChannelSelection(
                                allChannels = true,
                                islandPriorityOverrides = mapOf("messages" to IslandPriority.MEDIUM),
                            ),
                            IslandPriority.HIGH,
                        ),
                    ),
            ).normalized()

        val rule = normalized.rules.single()
        assertEquals(IslandPriority.LOW, rule.islandPriority)
        assertTrue(rule.channels.islandPriorityOverrides.isEmpty())
    }

    @Test
    fun `package-level matching requires enabled selected app in the same Android user`() {
        val snapshot =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 10,
                revision = 1,
                rules = listOf(AppRule("com.example.selected", ChannelSelection.ALL)),
            ).normalized()

        assertTrue(snapshot.matchesPackage("com.example.selected", 10))
        assertFalse(snapshot.matchesPackage("com.example.other", 10))
        assertFalse(snapshot.matchesPackage("com.example.selected", 0))
        assertFalse(snapshot.copy(enabled = false).matchesPackage("com.example.selected", 10))
    }

    @Test
    fun defaultIsDisabledAndEmpty() {
        val snapshot = SmartCapsuleConfigSnapshot.disabled(userId = 10)

        assertFalse(snapshot.enabled)
        assertEquals(10, snapshot.userId)
        assertEquals(0L, snapshot.revision)
        assertTrue(snapshot.rules.isEmpty())
    }

    @Test
    fun normalizationMergesRulesAndUsesStableOrdering() {
        val normalized =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 7,
                rules =
                    listOf(
                        AppRule(" com.example.z ", ChannelSelection.exact("messages")),
                        AppRule("com.example.a", ChannelSelection.exact("status")),
                        AppRule("com.example.z", ChannelSelection(channelIds = listOf("alerts", "messages"))),
                        AppRule("not a package", ChannelSelection.ALL),
                    ),
            ).normalized()

        assertEquals(listOf("com.example.a", "com.example.z"), normalized.rules.map(AppRule::packageName))
        assertEquals(
            listOf("alerts", "messages"),
            normalized.rules.last().channels.channelIds,
        )
    }

    @Test
    fun allChannelsDominatesExactRulesForTheSamePackage() {
        val normalized =
            SmartCapsuleConfigSnapshot(
                rules =
                    listOf(
                        AppRule("com.example.app", ChannelSelection.exact("one")),
                        AppRule("com.example.app", ChannelSelection.ALL),
                    ),
            ).normalized()

        assertEquals(listOf(AppRule("com.example.app", ChannelSelection.ALL)), normalized.rules)
    }

    @Test
    fun matchingRequiresEnabledUserPackageAndChannel() {
        val snapshot =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 10,
                rules = listOf(AppRule("com.example.app", ChannelSelection.exact("messages"))),
            ).normalized()

        assertTrue(snapshot.matches("com.example.app", 10, "messages"))
        assertFalse(snapshot.matches("com.example.app", 0, "messages"))
        assertFalse(snapshot.matches("com.example.app", 10, "other"))
        assertFalse(snapshot.copy(enabled = false).matches("com.example.app", 10, "messages"))
    }

    @Test
    fun codecIsDeterministicAndRoundTripsCanonicalSnapshot() {
        val first =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 42,
                rules =
                    listOf(
                        AppRule("com.example.b", ChannelSelection.exact("two")),
                        AppRule("com.example.a", ChannelSelection.exact("one")),
                    ),
            )
        val second = first.copy(rules = first.rules.reversed())

        val firstPayload = SmartCapsuleConfigCodec.encode(first)
        val secondPayload = SmartCapsuleConfigCodec.encode(second)
        val digest = SmartCapsuleConfigCodec.digest(firstPayload)

        assertEquals(firstPayload, secondPayload)
        assertEquals(first.normalized(), SmartCapsuleConfigCodec.decode(firstPayload, digest))
    }

    @Test
    fun codecRejectsTamperingAndWrongDigest() {
        val snapshot = SmartCapsuleConfigSnapshot(userId = 0, revision = 1)
        val payload = SmartCapsuleConfigCodec.encode(snapshot)
        val digest = SmartCapsuleConfigCodec.digest(payload)

        assertNull(SmartCapsuleConfigCodec.decodeOrNull(payload + "A", digest))
        assertNull(SmartCapsuleConfigCodec.decodeOrNull(payload, "0".repeat(64)))
        assertNull(SmartCapsuleConfigCodec.decodeOrNull(payload, "invalid"))
    }

    @Test
    fun legacyBinaryCodecRefusesToDropPriority() {
        val prioritized =
            SmartCapsuleConfigSnapshot(
                userId = 0,
                revision = 1,
                rules = listOf(AppRule("com.example.app", islandPriority = IslandPriority.HIGH)),
            )

        assertTrue(runCatching { SmartCapsuleConfigCodec.encode(prioritized) }.isFailure)

        val focusOnly =
            SmartCapsuleConfigSnapshot(
                userId = 0,
                revision = 1,
                rules =
                    listOf(
                        AppRule(
                            "com.example.app",
                            focusDisplayMode = FocusDisplayMode.FOCUS_ONLY,
                        ),
                    ),
            )
        assertTrue(runCatching { SmartCapsuleConfigCodec.encode(focusOnly) }.isFailure)
    }

    @Test
    fun configurationDigestIgnoresRevisionButIncludesUserConfiguration() {
        val first =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 1,
                rules = listOf(AppRule("com.example.app")),
            )
        val nextRevision = first.copy(revision = 2)
        val changedRules = first.copy(rules = listOf(AppRule("com.example.other")))
        val changedPriority =
            first.copy(
                rules = listOf(AppRule("com.example.app", islandPriority = IslandPriority.HIGH)),
            )
        val changedDisplayMode =
            first.copy(
                rules =
                    listOf(
                        AppRule(
                            "com.example.app",
                            focusDisplayMode = FocusDisplayMode.FOCUS_ONLY,
                        ),
                    ),
            )

        assertTrue(first.sameConfigurationAs(nextRevision))
        assertEquals(
            SmartCapsuleConfigCodec.configurationDigest(first),
            SmartCapsuleConfigCodec.configurationDigest(nextRevision),
        )
        assertNotEquals(
            SmartCapsuleConfigCodec.configurationDigest(first),
            SmartCapsuleConfigCodec.configurationDigest(changedRules),
        )
        assertNotEquals(
            SmartCapsuleConfigCodec.configurationDigest(first),
            SmartCapsuleConfigCodec.configurationDigest(changedPriority),
        )
        assertNotEquals(
            SmartCapsuleConfigCodec.configurationDigest(first),
            SmartCapsuleConfigCodec.configurationDigest(changedDisplayMode),
        )
    }

    @Test
    fun normalizationBoundsExactChannelsAcrossTheWholeSnapshot() {
        val channels =
            (0 until SmartCapsuleConfigSnapshot.MAX_TOTAL_EXACT_CHANNELS + 10)
                .map { index -> "channel-$index" }
        val normalized =
            SmartCapsuleConfigSnapshot(
                rules = listOf(AppRule("com.example.app", ChannelSelection(channelIds = channels))),
            ).normalized()

        assertEquals(
            SmartCapsuleConfigSnapshot.MAX_TOTAL_EXACT_CHANNELS,
            normalized.rules.single().channels.channelIds.size,
        )
        val payload = SmartCapsuleConfigCodec.encode(normalized)
        assertEquals(
            normalized,
            SmartCapsuleConfigCodec.decode(payload, SmartCapsuleConfigCodec.digest(payload)),
        )
    }

    @Test
    fun normalizationBoundsAllChannelPrioritySettingsAcrossTheWholeSnapshot() {
        val overrides =
            (0 until SmartCapsuleConfigSnapshot.MAX_TOTAL_EXACT_CHANNELS + 10)
                .associate { index -> "channel-$index" to IslandPriority.HIGH }
        val normalized =
            SmartCapsuleConfigSnapshot(
                rules =
                    listOf(
                        AppRule(
                            packageName = "com.example.app",
                            channels =
                                ChannelSelection(
                                    allChannels = true,
                                    islandPriorityOverrides = overrides,
                                ),
                        ),
                    ),
            ).normalized()

        assertEquals(
            SmartCapsuleConfigSnapshot.MAX_TOTAL_EXACT_CHANNELS,
            normalized.rules.single().channels.islandPriorityOverrides.size,
        )
    }

    @Test
    fun slotContractKeepsConfigAndRuntimeNamespacesSeparate() {
        assertEquals("config.slot.0.payload", SystemUiSmartCapsuleContract.slotPayloadKey(0))
        assertEquals("config.slot.1.digest", SystemUiSmartCapsuleContract.slotDigestKey(1))
        assertEquals(
            "config.slot.0.snapshot_json",
            SystemUiSmartCapsuleContract.slotSnapshotJsonKey(0),
        )
        assertTrue(SystemUiSmartCapsuleContract.KEY_RUNTIME_CAPABILITY.startsWith("runtime."))
        assertFalse(SystemUiSmartCapsuleContract.KEY_ACTIVE_SLOT.startsWith("runtime."))
        assertTrue(SystemUiSmartCapsuleContract.METHOD_REPORT_CONFIG_ACCEPTANCE.contains("acceptance"))
        assertTrue(SystemUiSmartCapsuleContract.ACTION_REPORT_XMSF_ACCEPTANCE.contains("XMSF"))
        assertTrue(SystemUiSmartCapsuleContract.ACTION_REQUEST_CHANNELS.contains("REQUEST"))
        assertTrue(SystemUiSmartCapsuleContract.METHOD_REPORT_CHANNELS.contains("report"))
    }
}
