package io.github.superisland

import android.content.SharedPreferences
import io.github.superisland.model.AppRule
import io.github.superisland.model.ChannelSelection
import io.github.superisland.model.FocusDisplayMode
import io.github.superisland.model.IslandPriority
import io.github.superisland.model.SmartCapsuleConfigSnapshot
import io.github.superisland.model.SmartCapsuleRemoteSnapshot
import io.github.superisland.model.SystemUiSmartCapsuleContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCapsuleConfigStoreTest {
    @Test
    fun legacyPriorityMigrationBumpsOnceBeforeV4Publication() {
        val legacy =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 8,
                rules =
                    listOf(
                        AppRule(
                            packageName = "com.example.app",
                            channels =
                                ChannelSelection(
                                    allChannels = true,
                                    islandPriorityOverrides =
                                        mapOf("messages" to IslandPriority.HIGH),
                                ),
                            islandPriority = IslandPriority.HIGH,
                        ),
                    ),
            ).normalized()

        val migrated =
            SmartCapsuleStoredPriorityMigration.resolve(
                snapshot = legacy,
                revisionFloor = 11,
                needsPriorityMigration = true,
            )
        val reloaded =
            SmartCapsuleStoredPriorityMigration.resolve(
                snapshot = migrated,
                revisionFloor = migrated.revision,
                needsPriorityMigration = false,
            )

        assertEquals(12L, migrated.revision)
        assertEquals(IslandPriority.LOW, migrated.rules.single().islandPriority)
        assertTrue(migrated.rules.single().channels.islandPriorityOverrides.isEmpty())
        assertEquals(migrated, reloaded)
    }

    @Test
    fun schemaV3DisplayMigrationPreservesPriorityAndBumpsOnce() {
        val legacy =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 8,
                rules =
                    listOf(
                        AppRule(
                            packageName = "com.example.app",
                            channels =
                                ChannelSelection(
                                    allChannels = true,
                                    islandPriorityOverrides =
                                        mapOf("messages" to IslandPriority.HIGH),
                                ),
                            islandPriority = IslandPriority.MEDIUM,
                        ),
                    ),
            ).normalized()

        val migrated =
            SmartCapsuleStoredPriorityMigration.resolve(
                snapshot = legacy,
                revisionFloor = 8,
                needsPriorityMigration = false,
                needsDisplayModeMigration = true,
            )
        val reloaded =
            SmartCapsuleStoredPriorityMigration.resolve(
                snapshot = migrated,
                revisionFloor = migrated.revision,
                needsPriorityMigration = false,
                needsDisplayModeMigration = false,
            )

        assertEquals(9L, migrated.revision)
        assertEquals(IslandPriority.MEDIUM, migrated.rules.single().islandPriority)
        assertEquals(
            IslandPriority.HIGH,
            migrated.rules.single().channels.islandPriorityOverrides["messages"],
        )
        assertEquals(FocusDisplayMode.ISLAND_AND_FOCUS, migrated.rules.single().focusDisplayMode)
        assertTrue(migrated.rules.single().channels.focusDisplayModeOverrides.isEmpty())
        assertEquals(migrated, reloaded)
    }

    @Test
    fun currentV4SnapshotAdvancesPastANewerDurableRevisionFloorOnce() {
        val restored =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 5,
                rules = listOf(AppRule("com.example.app")),
            ).normalized()

        val repaired =
            SmartCapsuleStoredPriorityMigration.resolve(
                snapshot = restored,
                revisionFloor = 8,
                needsPriorityMigration = false,
            )
        val reloaded =
            SmartCapsuleStoredPriorityMigration.resolve(
                snapshot = repaired,
                revisionFloor = repaired.revision,
                needsPriorityMigration = false,
            )

        assertEquals(9L, repaired.revision)
        assertEquals(repaired, reloaded)
    }

    @Test
    fun unchangedNormalizedConfigKeepsRevision() {
        val current =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 10,
                revision = 8,
                rules = listOf(AppRule("com.example.app", ChannelSelection.exact("messages"))),
            ).normalized()
        val reorderedEquivalent =
            current.copy(
                revision = 999,
                rules = current.rules.reversed(),
            )

        val resolved =
            SmartCapsuleRevisionPolicy.resolve(
                current = current,
                requested = reorderedEquivalent,
                currentUserId = 10,
                revisionFloor = 8,
            )

        assertEquals(current, resolved)
    }

    @Test
    fun unchangedConfigStillAdvancesPastANewerRevisionFloor() {
        val current =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 4,
                rules = listOf(AppRule("com.example.app")),
            ).normalized()

        val resolved =
            SmartCapsuleRevisionPolicy.resolve(
                current = current,
                requested = current,
                currentUserId = 0,
                revisionFloor = 7,
            )

        assertEquals(8L, resolved.revision)
        assertTrue(resolved.sameConfigurationAs(current))
    }

    @Test
    fun changedConfigUsesCurrentUserAndNextRevision() {
        val current = SmartCapsuleConfigSnapshot.disabled(userId = 0).withRevision(3)
        val requested =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 99,
                revision = 99,
                rules = listOf(AppRule("com.example.app")),
            )

        val resolved =
            SmartCapsuleRevisionPolicy.resolve(
                current = current,
                requested = requested,
                currentUserId = 0,
                revisionFloor = 5,
            )

        assertEquals(0, resolved.userId)
        assertEquals(6L, resolved.revision)
        assertTrue(resolved.enabled)
    }

    @Test
    fun priorityOnlyChangeUsesTheNextRevision() {
        val current =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 4,
                rules = listOf(AppRule("com.example.app")),
            ).normalized()
        val requested =
            current.copy(
                rules = listOf(AppRule("com.example.app", islandPriority = IslandPriority.HIGH)),
            )

        val resolved =
            SmartCapsuleRevisionPolicy.resolve(
                current = current,
                requested = requested,
                currentUserId = 0,
                revisionFloor = 4,
            )

        assertEquals(5L, resolved.revision)
        assertEquals(IslandPriority.HIGH, resolved.rules.single().islandPriority)
    }

    @Test
    fun redundantChannelOverrideDoesNotConsumeARevision() {
        val current =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 6,
                rules =
                    listOf(
                        AppRule(
                            packageName = "com.example.app",
                            channels = ChannelSelection.exact("messages"),
                            islandPriority = IslandPriority.MEDIUM,
                        ),
                    ),
            ).normalized()
        val requested =
            current.copy(
                rules =
                    listOf(
                        current.rules.single().copy(
                            channels =
                                current.rules.single().channels.copy(
                                    islandPriorityOverrides =
                                        mapOf("messages" to IslandPriority.MEDIUM),
                                ),
                        ),
                    ),
            )

        val resolved =
            SmartCapsuleRevisionPolicy.resolve(
                current = current,
                requested = requested,
                currentUserId = 0,
                revisionFloor = 6,
            )

        assertEquals(current, resolved)
    }

    @Test
    fun legacyMigrationReadsSelectedRulesOnly() {
        val migrated =
            LegacyNotificationRuleMigration.migrate(
                userId = 0,
                revision = 1,
                featureEnabled = true,
                encodedRules =
                    setOf(
                        "com.example.b\tstatus\tB",
                        "com.example.a\tmessages\tA",
                        "malformed candidate without selected rule fields",
                    ),
                singleRuleEnabled = false,
                singlePackage = "ignored.runtime.candidate",
                singleChannel = "ignored",
            )

        requireNotNull(migrated)
        assertTrue(migrated.enabled)
        assertEquals(listOf("com.example.a", "com.example.b"), migrated.rules.map(AppRule::packageName))
    }

    @Test
    fun legacySingleRuleMigratesOnceRulesSetIsAbsent() {
        val migrated =
            LegacyNotificationRuleMigration.migrate(
                userId = 10,
                revision = 4,
                featureEnabled = false,
                encodedRules = emptySet(),
                singleRuleEnabled = true,
                singlePackage = "com.example.app",
                singleChannel = "messages",
            )

        requireNotNull(migrated)
        assertFalse(migrated.enabled)
        assertEquals(10, migrated.userId)
        assertEquals(4L, migrated.revision)
        assertEquals(listOf("messages"), migrated.rules.single().channels.channelIds)
        assertEquals(IslandPriority.LOW, migrated.rules.single().islandPriority)
    }

    @Test
    fun missingLegacySelectionKeepsNewDefaultPath() {
        val migrated =
            LegacyNotificationRuleMigration.migrate(
                userId = 0,
                revision = 1,
                featureEnabled = true,
                encodedRules = emptySet(),
                singleRuleEnabled = false,
                singlePackage = null,
                singleChannel = null,
            )

        assertNull(migrated)
    }

    @Test
    fun remotePublicationCommitsCompleteInactiveSlotBeforeActivePointer() {
        val preferences = RecordingSharedPreferences()
        val snapshot =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 9,
                rules = listOf(AppRule("com.example.app")),
            ).normalized()

        SmartCapsuleHostConfigSync.publishToInactiveSlot(preferences, snapshot)

        assertEquals(2, preferences.commits.size)
        val slotCommit = preferences.commits[0]
        val activationCommit = preferences.commits[1]
        assertEquals(
            setOf(
                SystemUiSmartCapsuleContract.slotSnapshotJsonKey(
                    SystemUiSmartCapsuleContract.SLOT_A,
                ),
            ),
            slotCommit.keys,
        )
        assertEquals(
            setOf(
                SystemUiSmartCapsuleContract.KEY_ACTIVE_SLOT,
                SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION,
            ),
            activationCommit.keys,
        )
        assertEquals(snapshot, SmartCapsuleHostConfigSync.readActiveSnapshot(preferences))
        val remote =
            SmartCapsuleRemoteSnapshot.decode(
                preferences.getString(
                    SystemUiSmartCapsuleContract.slotSnapshotJsonKey(
                        SystemUiSmartCapsuleContract.SLOT_A,
                    ),
                    null,
                )!!,
            )
        assertTrue(remote.matches("com.example.app", 0, "any-channel"))
    }

    @Test
    fun corruptActiveDocumentFallsBackAndNextPublishPreservesLastValidSlot() {
        val preferences = RecordingSharedPreferences()
        val first =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 1,
                rules = listOf(AppRule("com.example.first")),
            ).normalized()
        val second =
            first.copy(
                revision = 2,
                rules = listOf(AppRule("com.example.second")),
            ).normalized()
        val third =
            first.copy(
                revision = 3,
                rules = listOf(AppRule("com.example.third")),
            ).normalized()

        SmartCapsuleHostConfigSync.publishToInactiveSlot(preferences, first)
        SmartCapsuleHostConfigSync.publishToInactiveSlot(preferences, second)
        val activeSlot = preferences.getInt(SystemUiSmartCapsuleContract.KEY_ACTIVE_SLOT, -1)
        preferences.edit()
            .putString(SystemUiSmartCapsuleContract.slotSnapshotJsonKey(activeSlot), "corrupt")
            .commit()

        assertEquals(first, SmartCapsuleHostConfigSync.readActiveSnapshot(preferences))
        SmartCapsuleHostConfigSync.publishToInactiveSlot(preferences, third)
        assertEquals(third, SmartCapsuleHostConfigSync.readActiveSnapshot(preferences))

        val preservedSlot = SystemUiSmartCapsuleContract.alternateSlot(activeSlot)
        assertEquals(
            first,
            SmartCapsuleRemoteSnapshot.decode(
                preferences.getString(
                    SystemUiSmartCapsuleContract.slotSnapshotJsonKey(preservedSlot),
                    null,
                )!!,
            ).toConfigSnapshot(),
        )
    }

    @Test
    fun remotePublicationRefusesRevisionRollback() {
        val preferences = RecordingSharedPreferences()
        val current =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 7,
                rules = listOf(AppRule("com.example.current")),
            ).normalized()
        SmartCapsuleHostConfigSync.publishToInactiveSlot(preferences, current)

        val failure =
            runCatching {
                SmartCapsuleHostConfigSync.publishToInactiveSlot(
                    preferences,
                    current.copy(revision = 6),
                )
            }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(current, SmartCapsuleHostConfigSync.readActiveSnapshot(preferences))
    }

    @Test
    fun remotePublicationRefusesSameRevisionV2ToV3DigestSwap() {
        val preferences = RecordingSharedPreferences()
        val body =
            "{\"schema\":2,\"userId\":0,\"revision\":8,\"enabled\":true," +
                "\"applications\":{\"com.example.app\":{\"channels\":{\"enabled\":[]}}}}"
        val digest = SmartCapsuleRemoteSnapshot.digest(body)
        val revisionEnd = body.indexOf(",\"enabled\"")
        val v2 =
            body.substring(0, revisionEnd) +
                ",\"digest\":\"$digest\"" +
                body.substring(revisionEnd)
        preferences.edit()
            .putInt(SystemUiSmartCapsuleContract.KEY_ACTIVE_SLOT, SystemUiSmartCapsuleContract.SLOT_A)
            .putLong(SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION, 8)
            .putString(SystemUiSmartCapsuleContract.slotSnapshotJsonKey(0), v2)
            .commit()
        val v3 =
            SmartCapsuleConfigSnapshot(
                enabled = true,
                userId = 0,
                revision = 8,
                rules = listOf(AppRule("com.example.app")),
            ).normalized()

        val failure =
            runCatching {
                SmartCapsuleHostConfigSync.publishToInactiveSlot(preferences, v3)
            }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(v2, preferences.getString(SystemUiSmartCapsuleContract.slotSnapshotJsonKey(0), null))
    }
}

private class RecordingSharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any>()
    val commits = mutableListOf<Map<String, Any?>>()

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(
        key: String,
        defaultValue: String?,
    ): String? = values[key] as? String ?: defaultValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String,
        defaultValues: Set<String>?,
    ): Set<String>? = values[key] as? Set<String> ?: defaultValues

    override fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = values[key] as? Int ?: defaultValue

    override fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = values[key] as? Long ?: defaultValue

    override fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = values[key] as? Float ?: defaultValue

    override fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defaultValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = RecordingEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    private inner class RecordingEditor : SharedPreferences.Editor {
        private val changes = linkedMapOf<String, Any?>()
        private var clear = false

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor = apply { changes[key] = value }

        override fun putStringSet(
            key: String,
            values: Set<String>?,
        ): SharedPreferences.Editor = apply { changes[key] = values }

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor = apply { changes[key] = value }

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor = apply { changes[key] = value }

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor = apply { changes[key] = value }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor = apply { changes[key] = value }

        override fun remove(key: String): SharedPreferences.Editor = apply { changes[key] = null }

        override fun clear(): SharedPreferences.Editor = apply { clear = true }

        override fun commit(): Boolean {
            if (clear) values.clear()
            changes.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
            commits += changes.toMap()
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
