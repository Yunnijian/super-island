package io.github.superisland.hook.systemui

import io.github.superisland.model.AppRule
import io.github.superisland.model.ChannelSelection
import io.github.superisland.model.FocusDisplayMode
import io.github.superisland.model.IslandPriority
import io.github.superisland.model.SmartCapsuleConfigSnapshot
import io.github.superisland.model.SmartCapsuleRemoteSnapshot
import io.github.superisland.model.SystemUiSmartCapsuleContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSmartCapsuleRuleSnapshotTest {
    @Test
    fun `complete active slot becomes immutable matching snapshot`() {
        val config =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 10,
                revision = 7,
                rules =
                    listOf(
                        AppRule(
                            packageName = "example.app",
                            channels =
                                ChannelSelection(
                                    channelIds = listOf("messages", "urgent"),
                                    islandPriorityOverrides =
                                        mapOf("urgent" to IslandPriority.HIGH),
                                    focusDisplayModeOverrides =
                                        mapOf("messages" to FocusDisplayMode.FOCUS_ONLY),
                                ),
                            islandPriority = IslandPriority.MEDIUM,
                        ),
                    ),
            ).normalized()
        val values = FakePreferenceValues().apply { publish(slot = 1, config = config) }
        val snapshot = RemoteSmartCapsuleRuleSnapshot(values, expectedUserId = 10)

        assertEquals(config, snapshot.reload())
        assertTrue(snapshot.current()!!.matches("example.app", 10, "messages"))
        assertEquals(
            IslandPriority.MEDIUM,
            snapshot.current()!!.resolveIslandPriority("example.app", 10, "messages"),
        )
        assertEquals(
            IslandPriority.HIGH,
            snapshot.current()!!.resolveIslandPriority("example.app", 10, "urgent"),
        )
        assertEquals(
            FocusDisplayMode.FOCUS_ONLY,
            snapshot.current()!!.resolveFocusDisplayMode("example.app", 10, "messages"),
        )
        assertEquals(
            FocusDisplayMode.ISLAND_AND_FOCUS,
            snapshot.current()!!.resolveFocusDisplayMode("example.app", 10, "urgent"),
        )
    }

    @Test
    fun `digest revision and user mismatch fail closed`() {
        val config =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 4,
                rules = listOf(AppRule("example.app")),
            ).normalized()
        val values = FakePreferenceValues().apply { publish(slot = 0, config = config) }
        val snapshot = RemoteSmartCapsuleRuleSnapshot(values, expectedUserId = 0)

        values.strings[SystemUiSmartCapsuleContract.slotSnapshotJsonKey(0)] =
            values.strings.getValue(SystemUiSmartCapsuleContract.slotSnapshotJsonKey(0))
                .replace("\"enabled\":true", "\"enabled\":false")
        assertNull(snapshot.reload())

        values.publish(slot = 0, config = config)
        values.longs[SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION] = 5
        assertNull(snapshot.reload())

        values.publish(slot = 0, config = config)
        assertNull(RemoteSmartCapsuleRuleSnapshot(values, expectedUserId = 11).reload())
    }

    @Test
    fun `corrupt active document falls back only to older published slot`() {
        val older =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 4,
                rules = listOf(AppRule("older.app")),
            ).normalized()
        val newer =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 5,
                rules = listOf(AppRule("newer.app")),
            ).normalized()
        val values =
            FakePreferenceValues().apply {
                publish(slot = 0, config = older)
                publish(slot = 1, config = newer)
                strings[SystemUiSmartCapsuleContract.slotSnapshotJsonKey(1)] = "corrupt"
            }

        val selected = RemoteSmartCapsuleRuleSnapshot(values, expectedUserId = 0).reload()

        assertEquals(older, selected)
    }

    @Test
    fun `transient corrupt publication retains the last accepted snapshot`() {
        val accepted =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 9,
                rules = listOf(AppRule("accepted.app")),
            ).normalized()
        val values = FakePreferenceValues().apply { publish(slot = 0, config = accepted) }
        val reader = RemoteSmartCapsuleRuleSnapshot(values, expectedUserId = 0)
        assertEquals(accepted, reader.reload())
        assertTrue(!reader.isStale(publishedRevision = 9))

        values.strings[SystemUiSmartCapsuleContract.slotSnapshotJsonKey(0)] = "corrupt"
        values.longs[SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION] = 10

        assertTrue(reader.isStale(publishedRevision = 10))
        assertEquals(accepted, reader.reload())
        assertEquals(9L, reader.current()?.revision)
    }

    @Test
    fun `same revision with different rules cannot replace the accepted snapshot`() {
        val accepted =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 12,
                rules = listOf(AppRule("accepted.app")),
            ).normalized()
        val collision =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 12,
                rules = listOf(AppRule("collision.app")),
            ).normalized()
        val values = FakePreferenceValues().apply { publish(slot = 0, config = accepted) }
        val reader = RemoteSmartCapsuleRuleSnapshot(values, expectedUserId = 0)
        assertEquals(accepted, reader.reload())

        values.publish(slot = 1, config = collision)

        assertEquals(accepted, reader.reload())
        assertEquals(accepted, reader.current())
    }

    @Test
    fun `same revision priority change cannot replace the accepted snapshot`() {
        val accepted =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 13,
                rules =
                    listOf(
                        AppRule("accepted.app", islandPriority = IslandPriority.LOW),
                    ),
            ).normalized()
        val collision =
            accepted.copy(
                rules = listOf(AppRule("accepted.app", islandPriority = IslandPriority.HIGH)),
            ).normalized()
        val values = FakePreferenceValues().apply { publish(slot = 0, config = accepted) }
        val reader = RemoteSmartCapsuleRuleSnapshot(values, expectedUserId = 0)
        assertEquals(accepted, reader.reload())

        values.publish(slot = 1, config = collision)

        assertEquals(accepted, reader.reload())
        assertEquals(IslandPriority.LOW, reader.current()?.rules?.single()?.islandPriority)
    }

    private class FakePreferenceValues : SmartCapsulePreferenceValues {
        val ints = mutableMapOf<String, Int>()
        val longs = mutableMapOf<String, Long>()
        val strings = mutableMapOf<String, String>()

        fun publish(
            slot: Int,
            config: SmartCapsuleConfigSnapshot,
        ) {
            ints[SystemUiSmartCapsuleContract.KEY_ACTIVE_SLOT] = slot
            longs[SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION] = config.revision
            strings[SystemUiSmartCapsuleContract.slotSnapshotJsonKey(slot)] =
                SmartCapsuleRemoteSnapshot.encode(config)
        }

        override fun getInt(key: String, fallback: Int): Int = ints[key] ?: fallback

        override fun getLong(key: String, fallback: Long): Long = longs[key] ?: fallback

        override fun getString(key: String, fallback: String?): String? = strings[key] ?: fallback
    }
}
