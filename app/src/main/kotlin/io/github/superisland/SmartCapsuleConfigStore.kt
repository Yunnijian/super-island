package io.github.superisland

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import io.github.superisland.model.AppRule
import io.github.superisland.model.ChannelSelection
import io.github.superisland.model.FocusDisplayMode
import io.github.superisland.model.IslandPriority
import io.github.superisland.model.SmartCapsuleConfigCodec
import io.github.superisland.model.SmartCapsuleConfigSnapshot
import io.github.superisland.model.SmartCapsuleRemoteSnapshot

/**
 * App-private owner of smart-capsule rules and their monotonic configuration revision.
 *
 * Runtime observations and old NotificationListener candidates intentionally do not enter this
 * store. The SystemUI mirror is updated only after the complete local snapshot is durable.
 */
class SmartCapsuleConfigStore(
    context: Context,
    private val userIdProvider: () -> Int = ::currentProcessUserId,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): SmartCapsuleConfigSnapshot =
        synchronized(STORE_LOCK) {
            loadLocked()
        }

    fun save(config: SmartCapsuleConfigSnapshot): Result<SmartCapsuleConfigSnapshot> =
        mutate { config }

    fun update(
        transform: (SmartCapsuleConfigSnapshot) -> SmartCapsuleConfigSnapshot,
    ): Result<SmartCapsuleConfigSnapshot> = mutate(transform)

    fun reset(): Result<SmartCapsuleConfigSnapshot> =
        save(SmartCapsuleConfigSnapshot.disabled(userIdProvider()))

    fun observe(onChanged: (SmartCapsuleConfigSnapshot) -> Unit): () -> Unit {
        val unregister = observeChanges { onChanged(load()) }
        onChanged(load())
        return unregister
    }

    /** Observes only invalidation so callers can coalesce and decode on their own dispatcher. */
    fun observeChanges(onChanged: () -> Unit): () -> Unit {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key in OBSERVED_KEYS) onChanged()
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun mutate(
        transform: (SmartCapsuleConfigSnapshot) -> SmartCapsuleConfigSnapshot,
    ): Result<SmartCapsuleConfigSnapshot> =
        runCatching {
            val persisted =
                synchronized(STORE_LOCK) {
                    val current = loadLocked()
                    val next =
                        SmartCapsuleRevisionPolicy.resolve(
                            current = current,
                            requested = transform(current),
                            currentUserId = userIdProvider(),
                            revisionFloor = preferences.getLong(KEY_LAST_REVISION, current.revision),
                        )
                    if (next != current) persistLocked(next)
                    next
                }
            // Remote sync re-reads the newest local snapshot under its own lock. A delayed caller
            // can therefore never publish an older revision after a newer local update.
            SmartCapsuleHostConfigSync.sync(persisted).getOrThrow()
            persisted
        }

    private fun loadLocked(): SmartCapsuleConfigSnapshot {
        val currentUserId = userIdProvider()
        require(currentUserId >= 0) { "Current Android userId is invalid" }

        val stored =
            decodeStoredLocked()
                ?.takeIf { decoded -> decoded.snapshot.userId == currentUserId }
        if (stored != null) {
            val durable =
                SmartCapsuleStoredPriorityMigration.resolve(
                    snapshot = stored.snapshot,
                    revisionFloor = preferences.getLong(KEY_LAST_REVISION, stored.snapshot.revision),
                    needsPriorityMigration = stored.needsPriorityMigration,
                    needsDisplayModeMigration = stored.needsDisplayModeMigration,
                )
            if (
                stored.needsPriorityMigration ||
                stored.needsDisplayModeMigration ||
                durable != stored.snapshot ||
                !preferences.contains(KEY_SNAPSHOT_JSON) ||
                !preferences.getBoolean(KEY_LEGACY_MIGRATION_COMPLETED, false)
            ) {
                persistLocked(durable)
            }
            return durable
        }

        val initialRevision = nextRevision(preferences.getLong(KEY_LAST_REVISION, 0L))
        val migrated =
            if (!preferences.getBoolean(KEY_LEGACY_MIGRATION_COMPLETED, false)) {
                migrateLegacyRules(currentUserId, initialRevision)
            } else {
                null
            }
        val initial =
            migrated
                ?: SmartCapsuleConfigSnapshot.disabled(currentUserId).withRevision(initialRevision)
        persistLocked(initial)
        return initial
    }

    private fun decodeStoredLocked(): DecodedStoredConfig? {
        val snapshotJson = preferences.getString(KEY_SNAPSHOT_JSON, null)
        if (snapshotJson != null) {
            SmartCapsuleRemoteSnapshot.decodeOrNull(snapshotJson)?.let { current ->
                return DecodedStoredConfig(
                    snapshot = current.toConfigSnapshot(),
                    needsPriorityMigration = false,
                    needsDisplayModeMigration = false,
                )
            }
            SmartCapsuleRemoteSnapshot.decodeLegacyV3ConfigOrNull(snapshotJson)?.let { legacy ->
                return DecodedStoredConfig(
                    snapshot = legacy,
                    needsPriorityMigration = false,
                    needsDisplayModeMigration = true,
                )
            }
            return SmartCapsuleRemoteSnapshot.decodeLegacyV2ConfigOrNull(snapshotJson)?.let { legacy ->
                DecodedStoredConfig(
                    snapshot = legacy,
                    needsPriorityMigration = true,
                    needsDisplayModeMigration = true,
                )
            }
        }
        if (preferences.getInt(KEY_SCHEMA_VERSION, 0) != LEGACY_LOCAL_SCHEMA_VERSION) return null
        return SmartCapsuleConfigCodec
            .decodeOrNull(
                preferences.getString(KEY_PAYLOAD, null),
                preferences.getString(KEY_DIGEST, null),
            )
            ?.let { legacy ->
                DecodedStoredConfig(
                    snapshot = legacy,
                    needsPriorityMigration = true,
                    needsDisplayModeMigration = true,
                )
            }
    }

    private fun persistLocked(snapshot: SmartCapsuleConfigSnapshot) {
        val normalized = snapshot.normalized()
        require(normalized.revision > 0L) { "Persisted smart-capsule revision must be positive" }
        val snapshotJson = SmartCapsuleRemoteSnapshot.encode(normalized)
        check(
            preferences.edit()
                .putString(KEY_SNAPSHOT_JSON, snapshotJson)
                .putLong(KEY_LAST_REVISION, normalized.revision)
                .putBoolean(KEY_LEGACY_MIGRATION_COMPLETED, true)
                .remove(KEY_SCHEMA_VERSION)
                .remove(KEY_PAYLOAD)
                .remove(KEY_DIGEST)
                .commit(),
        ) { "Could not persist smart-capsule configuration" }
    }

    private fun migrateLegacyRules(
        userId: Int,
        revision: Long,
    ): SmartCapsuleConfigSnapshot? {
        val legacy = appContext.getSharedPreferences(LEGACY_FILE_NAME, Context.MODE_PRIVATE)
        return LegacyNotificationRuleMigration.migrate(
            userId = userId,
            revision = revision,
            featureEnabled =
                runCatching { legacy.getBoolean(LEGACY_KEY_FEATURE_ENABLED, true) }
                    .getOrDefault(false),
            encodedRules =
                runCatching { legacy.getStringSet(LEGACY_KEY_RULES, null).orEmpty() }
                    .getOrDefault(emptySet()),
            singleRuleEnabled =
                runCatching { legacy.getBoolean(LEGACY_KEY_RULE_ENABLED, false) }
                    .getOrDefault(false),
            singlePackage = runCatching { legacy.getString(LEGACY_KEY_RULE_PACKAGE, null) }.getOrNull(),
            singleChannel = runCatching { legacy.getString(LEGACY_KEY_RULE_CHANNEL, null) }.getOrNull(),
        )
    }

    companion object {
        private val STORE_LOCK = Any()

        private const val FILE_NAME = "smart-capsule-config"
        private const val LEGACY_LOCAL_SCHEMA_VERSION = 1
        private const val KEY_SNAPSHOT_JSON = "snapshot-json"
        private const val KEY_SCHEMA_VERSION = "schema-version"
        private const val KEY_PAYLOAD = "payload"
        private const val KEY_DIGEST = "digest"
        private const val KEY_LAST_REVISION = "last-revision"
        private const val KEY_LEGACY_MIGRATION_COMPLETED = "legacy-notification-rules-migrated-v1"

        private const val LEGACY_FILE_NAME = "notification-proxy"
        private const val LEGACY_KEY_FEATURE_ENABLED = "notification-feature-enabled"
        private const val LEGACY_KEY_RULES = "rules"
        private const val LEGACY_KEY_RULE_ENABLED = "rule-enabled"
        private const val LEGACY_KEY_RULE_PACKAGE = "rule-package"
        private const val LEGACY_KEY_RULE_CHANNEL = "rule-channel"

        private val OBSERVED_KEYS =
            setOf(
                KEY_SNAPSHOT_JSON,
                KEY_SCHEMA_VERSION,
                KEY_PAYLOAD,
                KEY_DIGEST,
                KEY_LAST_REVISION,
            )

        @JvmStatic
        fun currentProcessUserId(): Int =
            Process.myUid() / ANDROID_UIDS_PER_USER

        // Android encodes the userId in the high decimal range of every application UID.
        private const val ANDROID_UIDS_PER_USER = 100_000

        private fun nextRevision(current: Long): Long =
            Math.addExact(maxOf(current, 0L), 1L)
    }
}

private data class DecodedStoredConfig(
    val snapshot: SmartCapsuleConfigSnapshot,
    val needsPriorityMigration: Boolean,
    val needsDisplayModeMigration: Boolean,
)

internal object SmartCapsuleStoredPriorityMigration {
    fun resolve(
        snapshot: SmartCapsuleConfigSnapshot,
        revisionFloor: Long,
        needsPriorityMigration: Boolean,
        needsDisplayModeMigration: Boolean = false,
    ): SmartCapsuleConfigSnapshot {
        if (!needsPriorityMigration && !needsDisplayModeMigration) {
            val durableRevision = maxOf(snapshot.revision, 1L)
            if (durableRevision >= revisionFloor) return snapshot.withRevision(durableRevision)
            return snapshot.withRevision(Math.addExact(revisionFloor, 1L))
        }
        val migratedRules =
            snapshot.rules.map { rule ->
                rule.copy(
                    channels =
                        rule.channels.copy(
                            islandPriorityOverrides =
                                if (needsPriorityMigration) {
                                    emptyMap()
                                } else {
                                    rule.channels.islandPriorityOverrides
                                },
                            focusDisplayModeOverrides = emptyMap(),
                        ),
                    islandPriority =
                        if (needsPriorityMigration) {
                            IslandPriority.LOW
                        } else {
                            rule.islandPriority
                        },
                    focusDisplayMode = FocusDisplayMode.ISLAND_AND_FOCUS,
                )
            }
        val nextRevision = Math.addExact(maxOf(snapshot.revision, revisionFloor, 0L), 1L)
        return snapshot.copy(rules = migratedRules).withRevision(nextRevision)
    }
}

internal object SmartCapsuleRevisionPolicy {
    fun resolve(
        current: SmartCapsuleConfigSnapshot,
        requested: SmartCapsuleConfigSnapshot,
        currentUserId: Int,
        revisionFloor: Long,
    ): SmartCapsuleConfigSnapshot {
        require(currentUserId >= 0) { "Current Android userId is invalid" }
        val normalizedCurrent = current.normalized()
        val normalizedRequested =
            requested
                .copy(userId = currentUserId, revision = normalizedCurrent.revision)
                .normalized()
        if (normalizedCurrent.sameConfigurationAs(normalizedRequested)) {
            if (normalizedCurrent.revision >= revisionFloor) return normalizedCurrent
            return normalizedCurrent.withRevision(Math.addExact(revisionFloor, 1L))
        }
        val nextRevision = Math.addExact(maxOf(normalizedCurrent.revision, revisionFloor, 0L), 1L)
        return normalizedRequested.withRevision(nextRevision)
    }
}

internal object LegacyNotificationRuleMigration {
    private const val FIELD_SEPARATOR = "\t"

    fun migrate(
        userId: Int,
        revision: Long,
        featureEnabled: Boolean,
        encodedRules: Set<String>,
        singleRuleEnabled: Boolean,
        singlePackage: String?,
        singleChannel: String?,
    ): SmartCapsuleConfigSnapshot? {
        val migratedRules =
            encodedRules
                .mapNotNull(::decodeRule)
                .ifEmpty {
                    if (singleRuleEnabled && singlePackage != null && singleChannel != null) {
                        listOf(AppRule(singlePackage, ChannelSelection.exact(singleChannel)))
                    } else {
                        emptyList()
                    }
                }
        val normalized =
            SmartCapsuleConfigSnapshot(
                enabled = featureEnabled && migratedRules.isNotEmpty(),
                userId = userId,
                revision = revision,
                rules = migratedRules,
            ).normalized()
        return normalized.takeIf { snapshot -> snapshot.rules.isNotEmpty() }
    }

    private fun decodeRule(encoded: String): AppRule? {
        val fields = encoded.split(FIELD_SEPARATOR, limit = 3)
        if (fields.size < 2) return null
        return AppRule(fields[0], ChannelSelection.exact(fields[1])).normalizedOrNull()
    }
}
