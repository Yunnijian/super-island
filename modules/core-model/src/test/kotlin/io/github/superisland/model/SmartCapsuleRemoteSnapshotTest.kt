package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCapsuleRemoteSnapshotTest {
    @Test
    fun emptyEnabledChannelsMatchesEveryChannelLikeHyperIsland() {
        val remote =
            SmartCapsuleRemoteSnapshot.fromConfig(
                SmartCapsuleConfigSnapshot(
                    enabled = true,
                    userId = 10,
                    revision = 4,
                    rules = listOf(AppRule("com.example.all", ChannelSelection.ALL)),
                ),
            )

        assertTrue(remote.matches("com.example.all", 10, "messages"))
        assertTrue(remote.matches("com.example.all", 10, "status"))
        assertTrue(remote.matchesPackage("com.example.all", 10))
        assertFalse(remote.matches("com.example.all", 0, "messages"))
        assertFalse(remote.matchesPackage("com.example.other", 10))
        assertTrue(remote.toJson().contains("\"channels\":{\"enabled\":[],\"settings\":{}}"))
    }

    @Test
    fun exactEnabledChannelsRemainAnAllowlist() {
        val remote =
            SmartCapsuleRemoteSnapshot.fromConfig(
                SmartCapsuleConfigSnapshot(
                    enabled = true,
                    userId = 0,
                    revision = 1,
                    rules =
                        listOf(
                            AppRule(
                                "com.example.app",
                                ChannelSelection(channelIds = listOf("messages", "status")),
                            ),
                        ),
                ),
            )

        assertTrue(remote.matches("com.example.app", 0, "messages"))
        assertFalse(remote.matches("com.example.app", 0, "other"))
    }

    @Test
    fun canonicalJsonIsDeterministicAndRoundTrips() {
        val first =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 42,
                rules =
                    listOf(
                        AppRule(
                            "com.example.z",
                            ChannelSelection(
                                channelIds = listOf("quoted-\"channel", "urgent"),
                                islandPriorityOverrides = mapOf("urgent" to IslandPriority.HIGH),
                            ),
                            IslandPriority.MEDIUM,
                        ),
                        AppRule("com.example.a", ChannelSelection.ALL),
                    ),
            )
        val second = first.copy(rules = first.rules.reversed())

        val firstJson = SmartCapsuleRemoteSnapshot.encode(first)
        val secondJson = SmartCapsuleRemoteSnapshot.encode(second)
        val decoded = SmartCapsuleRemoteSnapshot.decode(firstJson)

        assertEquals(firstJson, secondJson)
        assertEquals(first.normalized(), decoded.toConfigSnapshot())
        assertEquals(SmartCapsuleRemoteSnapshot.SCHEMA_VERSION, decoded.schema)
        assertEquals(64, decoded.digest.length)
        assertTrue(
            firstJson.contains(
                "\"displayMode\":0,\"islandPriority\":1," +
                    "\"channels\":{\"enabled\":[\"quoted-\\\"channel\",\"urgent\"]," +
                    "\"settings\":{\"urgent\":{\"displayMode\":0,\"islandPriority\":0}}}",
            ),
        )
    }

    @Test
    fun schemaUserRevisionAndDigestAreValidatedTogether() {
        val json =
            SmartCapsuleRemoteSnapshot.encode(
                SmartCapsuleConfigSnapshot(
                    enabled = true,
                    userId = 10,
                    revision = 8,
                    rules = listOf(AppRule("com.example.app")),
                ),
            )

        assertNull(SmartCapsuleRemoteSnapshot.decodeOrNull(json.replace("\"schema\":4", "\"schema\":5")))
        assertNull(SmartCapsuleRemoteSnapshot.decodeOrNull(json.replace("\"enabled\":true", "\"enabled\":false")))
        assertNull(SmartCapsuleRemoteSnapshot.decodeOrNull(json, expectedUserId = 0, minimumRevision = 1))
        assertNull(SmartCapsuleRemoteSnapshot.decodeOrNull(json, expectedUserId = 10, minimumRevision = 9))
        assertEquals(
            8L,
            SmartCapsuleRemoteSnapshot.decodeOrNull(
                json,
                expectedUserId = 10,
                minimumRevision = 8,
            )?.revision,
        )
    }

    @Test
    fun illegalPriorityValuesFailClosedEvenWithAValidDigest() {
        val json =
            SmartCapsuleRemoteSnapshot.encode(
                SmartCapsuleConfigSnapshot(
                    enabled = true,
                    userId = 0,
                    revision = 3,
                    rules = listOf(AppRule("com.example.app", islandPriority = IslandPriority.HIGH)),
                ),
            )

        listOf("-1", "3", "\"HIGH\"", "null").forEach { illegalValue ->
            val illegal =
                resign(json) { body ->
                    body.replace("\"islandPriority\":0", "\"islandPriority\":$illegalValue")
                }
            assertNull(SmartCapsuleRemoteSnapshot.decodeOrNull(illegal))
        }
    }

    @Test
    fun illegalDisplayModeValuesFailClosedEvenWithAValidDigest() {
        val json =
            SmartCapsuleRemoteSnapshot.encode(
                SmartCapsuleConfigSnapshot(
                    enabled = true,
                    userId = 0,
                    revision = 3,
                    rules = listOf(AppRule("com.example.app")),
                ),
            )

        listOf("-1", "2", "\"FOCUS_ONLY\"", "null").forEach { illegalValue ->
            val illegal =
                resign(json) { body ->
                    body.replaceFirst("\"displayMode\":0", "\"displayMode\":$illegalValue")
                }
            assertNull(SmartCapsuleRemoteSnapshot.decodeOrNull(illegal))
        }
    }

    @Test
    fun redundantAndOrphanChannelOverridesAreNotCanonical() {
        val json =
            SmartCapsuleRemoteSnapshot.encode(
                SmartCapsuleConfigSnapshot(
                    enabled = true,
                    userId = 0,
                    revision = 3,
                    rules =
                        listOf(
                            AppRule(
                                packageName = "com.example.app",
                                channels =
                                    ChannelSelection(
                                        channelIds = listOf("messages"),
                                        islandPriorityOverrides =
                                            mapOf("messages" to IslandPriority.HIGH),
                                    ),
                                islandPriority = IslandPriority.MEDIUM,
                            ),
                        ),
                ),
            )

        val redundant =
            resign(json) { body ->
                body.replace(
                    "\"messages\":{\"displayMode\":0,\"islandPriority\":0}",
                    "\"messages\":{\"displayMode\":0,\"islandPriority\":1}",
                )
            }
        val orphan =
            resign(json) { body ->
                body.replace("\"enabled\":[\"messages\"]", "\"enabled\":[\"other\"]")
            }

        assertNull(SmartCapsuleRemoteSnapshot.decodeOrNull(redundant))
        assertNull(SmartCapsuleRemoteSnapshot.decodeOrNull(orphan))
    }

    @Test
    fun schemaV3MigratesPrioritiesAndDefaultsToIslandAndFocus() {
        val body =
            "{\"schema\":3,\"userId\":0,\"revision\":9,\"enabled\":true," +
                "\"applications\":{\"com.example.app\":{\"islandPriority\":1," +
                "\"channels\":{\"enabled\":[\"messages\"],\"settings\":" +
                "{\"messages\":{\"islandPriority\":0}}}}}}"
        val json = addDigest(body)

        assertNull(SmartCapsuleRemoteSnapshot.decodeOrNull(json))
        val migrated = SmartCapsuleRemoteSnapshot.decodeLegacyV3ConfigOrNull(json)
        requireNotNull(migrated)
        val rule = migrated.rules.single()
        assertEquals(9L, migrated.revision)
        assertEquals(IslandPriority.MEDIUM, rule.islandPriority)
        assertEquals(IslandPriority.HIGH, rule.effectiveIslandPriority("messages"))
        assertEquals(FocusDisplayMode.ISLAND_AND_FOCUS, rule.focusDisplayMode)
        assertTrue(rule.channels.focusDisplayModeOverrides.isEmpty())
    }

    @Test
    fun schemaV2IsAvailableOnlyThroughTheMigrationDecoder() {
        val body =
            "{\"schema\":2,\"userId\":0,\"revision\":8,\"enabled\":true," +
                "\"applications\":{\"com.example.app\":{\"channels\":{\"enabled\":[\"messages\"]}}}}"
        val json = addDigest(body)

        assertNull(SmartCapsuleRemoteSnapshot.decodeOrNull(json))
        val migrated = SmartCapsuleRemoteSnapshot.decodeLegacyV2ConfigOrNull(json)
        requireNotNull(migrated)
        assertEquals(8L, migrated.revision)
        assertEquals(IslandPriority.LOW, migrated.rules.single().islandPriority)
        assertEquals(FocusDisplayMode.ISLAND_AND_FOCUS, migrated.rules.single().focusDisplayMode)
        assertTrue(migrated.rules.single().channels.islandPriorityOverrides.isEmpty())
        assertNull(
            SmartCapsuleRemoteSnapshotSelector.selectPublishedOrNull(
                activeSlot = SystemUiSmartCapsuleContract.SLOT_A,
                activeRevision = 8,
                slotAJson = json,
                slotBJson = null,
                expectedUserId = 0,
                minimumRevision = 0,
            ),
        )
    }

    @Test
    fun selectorFallsBackToLastValidButHonorsAcceptedRevisionFloor() {
        val previous =
            SmartCapsuleRemoteSnapshot.encode(
                SmartCapsuleConfigSnapshot(
                    enabled = true,
                    userId = 0,
                    revision = 5,
                    rules = listOf(AppRule("com.example.previous")),
                ),
            )

        val fallback =
            SmartCapsuleRemoteSnapshotSelector.selectPublishedOrNull(
                activeSlot = SystemUiSmartCapsuleContract.SLOT_B,
                activeRevision = 6,
                slotAJson = previous,
                slotBJson = "corrupt",
                expectedUserId = 0,
                minimumRevision = 0,
            )

        assertEquals(SystemUiSmartCapsuleContract.SLOT_A, fallback?.slot)
        assertEquals(5L, fallback?.snapshot?.revision)
        assertNull(
            SmartCapsuleRemoteSnapshotSelector.selectPublishedOrNull(
                activeSlot = SystemUiSmartCapsuleContract.SLOT_B,
                activeRevision = 6,
                slotAJson = previous,
                slotBJson = "corrupt",
                expectedUserId = 0,
                minimumRevision = 6,
            ),
        )
    }

    @Test
    fun selectorDoesNotPromoteUnpublishedNewerInactiveDocument() {
        val published = snapshotJson(revision = 3, packageName = "com.example.published")
        val staged = snapshotJson(revision = 4, packageName = "com.example.staged")

        val selected =
            SmartCapsuleRemoteSnapshotSelector.selectPublishedOrNull(
                activeSlot = SystemUiSmartCapsuleContract.SLOT_A,
                activeRevision = 3,
                slotAJson = published,
                slotBJson = staged,
                expectedUserId = 0,
                minimumRevision = 0,
            )

        assertEquals(3L, selected?.snapshot?.revision)
        assertTrue(selected?.snapshot?.matchesPackage("com.example.published", 0) == true)

        val damagedPublished =
            SmartCapsuleRemoteSnapshotSelector.selectPublishedOrNull(
                activeSlot = SystemUiSmartCapsuleContract.SLOT_A,
                activeRevision = 3,
                slotAJson = "corrupt",
                slotBJson = staged,
                expectedUserId = 0,
                minimumRevision = 0,
            )
        assertNull(damagedPublished)

        val sameRevisionStaged = snapshotJson(revision = 3, packageName = "com.example.collision")
        assertNull(
            SmartCapsuleRemoteSnapshotSelector.selectPublishedOrNull(
                activeSlot = SystemUiSmartCapsuleContract.SLOT_A,
                activeRevision = 3,
                slotAJson = "corrupt",
                slotBJson = sameRevisionStaged,
                expectedUserId = 0,
                minimumRevision = 0,
            ),
        )
    }

    private fun snapshotJson(
        revision: Long,
        packageName: String,
    ): String =
        SmartCapsuleRemoteSnapshot.encode(
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = revision,
                rules = listOf(AppRule(packageName)),
            ),
        )

    private fun resign(
        json: String,
        transform: (String) -> String,
    ): String {
        val body = transform(json.replace(DIGEST_FIELD, ""))
        return addDigest(body)
    }

    private fun addDigest(body: String): String {
        val revision = Regex("\"revision\":-?[0-9]+").find(body)
            ?: error("Snapshot body has no revision")
        val insertionIndex = revision.range.last + 1
        val digestField = ",\"digest\":\"${SmartCapsuleRemoteSnapshot.digest(body)}\""
        return body.substring(0, insertionIndex) + digestField + body.substring(insertionIndex)
    }

    private companion object {
        val DIGEST_FIELD = Regex(",\"digest\":\"[0-9a-f]{64}\"")
    }
}
