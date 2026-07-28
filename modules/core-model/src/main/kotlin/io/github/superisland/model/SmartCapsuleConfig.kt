package io.github.superisland.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/** HyperOS island scheduling priority. Protocol values are intentionally not ordinal ranks. */
enum class IslandPriority(
    val wireValue: Int,
) {
    HIGH(0),
    MEDIUM(1),
    LOW(2),
    ;

    companion object {
        @JvmStatic
        fun fromWireValueOrNull(value: Int): IslandPriority? =
            entries.firstOrNull { priority -> priority.wireValue == value }
    }
}

/** Whether a mapped source notification also exposes a Dynamic Island surface. */
enum class FocusDisplayMode(
    val wireValue: Int,
) {
    ISLAND_AND_FOCUS(0),
    FOCUS_ONLY(1),
    ;

    companion object {
        @JvmStatic
        fun fromWireValueOrNull(value: Int): FocusDisplayMode? =
            entries.firstOrNull { mode -> mode.wireValue == value }
    }
}

/** A package rule either accepts every Channel or an explicit, non-empty Channel allowlist. */
data class ChannelSelection(
    val allChannels: Boolean = false,
    val channelIds: List<String> = emptyList(),
    val islandPriorityOverrides: Map<String, IslandPriority> = emptyMap(),
    val focusDisplayModeOverrides: Map<String, FocusDisplayMode> = emptyMap(),
) {
    fun normalized(
        appDefaultPriority: IslandPriority = IslandPriority.LOW,
        appDefaultDisplayMode: FocusDisplayMode = FocusDisplayMode.ISLAND_AND_FOCUS,
    ): ChannelSelection {
        val normalizedChannelIds =
            if (allChannels) {
                emptyList()
            } else {
                channelIds
                    .asSequence()
                    .filter(::isValidChannelId)
                    .distinct()
                    .sorted()
                    .take(MAX_CHANNELS_PER_APP)
                    .toList()
            }
        val selectedChannelIds = normalizedChannelIds.toHashSet()
        val normalizedPriorityOverrides =
            islandPriorityOverrides.entries
                .asSequence()
                .filter { (channelId, priority) ->
                    isValidChannelId(channelId) &&
                        (allChannels || channelId in selectedChannelIds) &&
                        priority != appDefaultPriority
                }.sortedBy(Map.Entry<String, IslandPriority>::key)
                .associateTo(linkedMapOf()) { entry -> entry.key to entry.value }
        val normalizedDisplayModeOverrides =
            focusDisplayModeOverrides.entries
                .asSequence()
                .filter { (channelId, displayMode) ->
                    isValidChannelId(channelId) &&
                        (allChannels || channelId in selectedChannelIds) &&
                        displayMode != appDefaultDisplayMode
                }.sortedBy(Map.Entry<String, FocusDisplayMode>::key)
                .associateTo(linkedMapOf()) { entry -> entry.key to entry.value }
        val boundedOverrideIds =
            (normalizedPriorityOverrides.keys + normalizedDisplayModeOverrides.keys)
                .sorted()
                .take(MAX_CHANNELS_PER_APP)
                .toHashSet()
        return ChannelSelection(
            allChannels = allChannels,
            channelIds = normalizedChannelIds,
            islandPriorityOverrides = normalizedPriorityOverrides.filterKeys(boundedOverrideIds::contains),
            focusDisplayModeOverrides =
                normalizedDisplayModeOverrides.filterKeys(boundedOverrideIds::contains),
        )
    }

    fun matches(channelId: String): Boolean =
        allChannels || channelIds.binarySearch(channelId) >= 0

    fun effectiveIslandPriority(
        channelId: String,
        appDefaultPriority: IslandPriority,
    ): IslandPriority = islandPriorityOverrides[channelId] ?: appDefaultPriority

    fun effectiveFocusDisplayMode(
        channelId: String,
        appDefaultDisplayMode: FocusDisplayMode,
    ): FocusDisplayMode = focusDisplayModeOverrides[channelId] ?: appDefaultDisplayMode

    companion object {
        const val MAX_CHANNEL_ID_LENGTH = 1_024
        const val MAX_CHANNELS_PER_APP = 128

        @JvmField
        val ALL = ChannelSelection(allChannels = true)

        @JvmStatic
        fun exact(channelId: String): ChannelSelection =
            ChannelSelection(channelIds = listOf(channelId)).normalized()

        @JvmStatic
        fun isValidChannelId(channelId: String): Boolean =
            channelId.isNotBlank() &&
                channelId.length <= MAX_CHANNEL_ID_LENGTH &&
                channelId.none(Char::isISOControl)
    }
}

/** One source application and the Channels whose original SBNs may be enhanced in SystemUI. */
data class AppRule(
    val packageName: String,
    val channels: ChannelSelection = ChannelSelection.ALL,
    val islandPriority: IslandPriority = IslandPriority.LOW,
    val focusDisplayMode: FocusDisplayMode = FocusDisplayMode.ISLAND_AND_FOCUS,
) {
    fun normalizedOrNull(): AppRule? {
        val normalizedPackage = normalizePackageName(packageName) ?: return null
        val normalizedChannels = channels.normalized(islandPriority, focusDisplayMode)
        if (!normalizedChannels.allChannels && normalizedChannels.channelIds.isEmpty()) return null
        return AppRule(
            packageName = normalizedPackage,
            channels = normalizedChannels,
            islandPriority = islandPriority,
            focusDisplayMode = focusDisplayMode,
        )
    }

    fun matches(
        sourcePackage: String,
        channelId: String,
    ): Boolean = packageName == sourcePackage && channels.matches(channelId)

    fun effectiveIslandPriority(channelId: String): IslandPriority =
        channels.effectiveIslandPriority(channelId, islandPriority)

    fun effectiveFocusDisplayMode(channelId: String): FocusDisplayMode =
        channels.effectiveFocusDisplayMode(channelId, focusDisplayMode)

    companion object {
        const val MAX_PACKAGE_NAME_LENGTH = 255

        @JvmStatic
        fun normalizePackageName(packageName: String): String? {
            val normalized = packageName.trim()
            if (normalized.isEmpty() || normalized.length > MAX_PACKAGE_NAME_LENGTH) return null
            val segments = normalized.split('.')
            if (
                segments.any { segment ->
                    segment.isEmpty() ||
                        !segment.first().isAsciiLetter() ||
                        segment.drop(1).any { character ->
                            !character.isAsciiLetter() && !character.isDigit() && character != '_'
                        }
                }
            ) {
                return null
            }
            return normalized
        }

        private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
    }
}

/**
 * Immutable smart-capsule configuration consumed by the SystemUI hook.
 *
 * [revision] is store-owned monotonic metadata. It is deliberately excluded by
 * [sameConfigurationAs], which lets repeated saves of the same normalized user configuration
 * reuse the current revision.
 */
data class SmartCapsuleConfigSnapshot(
    val enabled: Boolean = false,
    val userId: Int = 0,
    val revision: Long = 0L,
    val rules: List<AppRule> = emptyList(),
) {
    fun normalized(): SmartCapsuleConfigSnapshot {
        require(userId >= 0) { "userId must be non-negative" }
        require(revision >= 0L) { "revision must be non-negative" }

        val merged = sortedMapOf<String, MutableMergedAppRule>()
        rules.forEach { rule ->
            val normalizedRule = rule.normalizedOrNull() ?: return@forEach
            val current =
                merged.getOrPut(normalizedRule.packageName) {
                    MutableMergedAppRule(
                        islandPriority = normalizedRule.islandPriority,
                        focusDisplayMode = normalizedRule.focusDisplayMode,
                    )
                }
            if (current.islandPriority != normalizedRule.islandPriority) {
                current.islandPriority = IslandPriority.LOW
            }
            if (current.focusDisplayMode != normalizedRule.focusDisplayMode) {
                current.focusDisplayMode = FocusDisplayMode.FOCUS_ONLY
            }
            current.allChannels = current.allChannels || normalizedRule.channels.allChannels
            current.channelIds += normalizedRule.channels.channelIds
            normalizedRule.channels.islandPriorityOverrides.forEach { (channelId, priority) ->
                if (channelId !in current.conflictingPriorityOverrides) {
                    val existing = current.islandPriorityOverrides[channelId]
                    if (existing == null || existing == priority) {
                        current.islandPriorityOverrides[channelId] = priority
                    } else {
                        current.islandPriorityOverrides.remove(channelId)
                        current.conflictingPriorityOverrides += channelId
                    }
                }
            }
            normalizedRule.channels.focusDisplayModeOverrides.forEach { (channelId, displayMode) ->
                if (channelId !in current.conflictingDisplayModeOverrides) {
                    val existing = current.focusDisplayModeOverrides[channelId]
                    if (existing == null || existing == displayMode) {
                        current.focusDisplayModeOverrides[channelId] = displayMode
                    } else {
                        current.focusDisplayModeOverrides[channelId] = FocusDisplayMode.FOCUS_ONLY
                        current.conflictingDisplayModeOverrides += channelId
                    }
                }
            }
        }

        var remainingExactChannels = MAX_TOTAL_EXACT_CHANNELS
        val normalizedRules = mutableListOf<AppRule>()
        merged.forEach { (packageName, mergedRule) ->
            if (normalizedRules.size >= MAX_APP_RULES) return@forEach
            val channels =
                ChannelSelection(
                    allChannels = mergedRule.allChannels,
                    channelIds = mergedRule.channelIds.toList(),
                    islandPriorityOverrides = mergedRule.islandPriorityOverrides,
                    focusDisplayModeOverrides = mergedRule.focusDisplayModeOverrides,
                ).normalized(mergedRule.islandPriority, mergedRule.focusDisplayMode)
            if (channels.allChannels) {
                val boundedOverrideIds =
                    (channels.islandPriorityOverrides.keys + channels.focusDisplayModeOverrides.keys)
                        .sorted()
                        .take(remainingExactChannels)
                        .toHashSet()
                normalizedRules +=
                    AppRule(
                        packageName = packageName,
                        channels =
                            ChannelSelection(
                                allChannels = true,
                                islandPriorityOverrides =
                                    channels.islandPriorityOverrides.filterKeys(boundedOverrideIds::contains),
                                focusDisplayModeOverrides =
                                    channels.focusDisplayModeOverrides.filterKeys(boundedOverrideIds::contains),
                            ),
                        islandPriority = mergedRule.islandPriority,
                        focusDisplayMode = mergedRule.focusDisplayMode,
                    )
                remainingExactChannels -= boundedOverrideIds.size
            } else if (remainingExactChannels > 0) {
                val bounded = channels.channelIds.take(remainingExactChannels)
                if (bounded.isNotEmpty()) {
                    val boundedSet = bounded.toHashSet()
                    normalizedRules +=
                        AppRule(
                            packageName = packageName,
                            channels =
                                ChannelSelection(
                                    channelIds = bounded,
                                    islandPriorityOverrides =
                                        channels.islandPriorityOverrides.filterKeys(boundedSet::contains),
                                    focusDisplayModeOverrides =
                                        channels.focusDisplayModeOverrides.filterKeys(boundedSet::contains),
                                ),
                            islandPriority = mergedRule.islandPriority,
                            focusDisplayMode = mergedRule.focusDisplayMode,
                        )
                    remainingExactChannels -= bounded.size
                }
            }
        }
        return copy(rules = normalizedRules)
    }

    fun sameConfigurationAs(other: SmartCapsuleConfigSnapshot): Boolean {
        val left = normalized()
        val right = other.normalized()
        return left.enabled == right.enabled &&
            left.userId == right.userId &&
            left.rules == right.rules
    }

    fun matches(
        sourcePackage: String,
        sourceUserId: Int,
        channelId: String,
    ): Boolean {
        if (!matchesPackage(sourcePackage, sourceUserId)) return false
        val index = rules.binarySearchBy(sourcePackage) { rule -> rule.packageName }
        return rules[index].matches(sourcePackage, channelId)
    }

    /** Package-level gate used by Xiaomi's final canShowFocus check before Channel data exists. */
    fun matchesPackage(
        sourcePackage: String,
        sourceUserId: Int,
    ): Boolean {
        if (!enabled || sourceUserId != userId) return false
        return rules.binarySearchBy(sourcePackage) { rule -> rule.packageName } >= 0
    }

    fun resolveIslandPriority(
        sourcePackage: String,
        sourceUserId: Int,
        channelId: String,
    ): IslandPriority? {
        if (!matchesPackage(sourcePackage, sourceUserId)) return null
        val index = rules.binarySearchBy(sourcePackage) { rule -> rule.packageName }
        val rule = rules[index]
        if (!rule.matches(sourcePackage, channelId)) return null
        return rule.effectiveIslandPriority(channelId)
    }

    fun resolveFocusDisplayMode(
        sourcePackage: String,
        sourceUserId: Int,
        channelId: String,
    ): FocusDisplayMode? {
        if (!matchesPackage(sourcePackage, sourceUserId)) return null
        val index = rules.binarySearchBy(sourcePackage) { rule -> rule.packageName }
        val rule = rules[index]
        if (!rule.matches(sourcePackage, channelId)) return null
        return rule.effectiveFocusDisplayMode(channelId)
    }

    fun withRevision(newRevision: Long): SmartCapsuleConfigSnapshot =
        copy(revision = newRevision).normalized()

    companion object {
        const val MAX_APP_RULES = 256
        const val MAX_TOTAL_EXACT_CHANNELS = 64

        @JvmStatic
        fun disabled(userId: Int): SmartCapsuleConfigSnapshot =
            SmartCapsuleConfigSnapshot(userId = userId).normalized()
    }
}

private data class MutableMergedAppRule(
    var islandPriority: IslandPriority,
    var focusDisplayMode: FocusDisplayMode,
    var allChannels: Boolean = false,
    val channelIds: MutableSet<String> = sortedSetOf(),
    val islandPriorityOverrides: MutableMap<String, IslandPriority> = sortedMapOf(),
    val conflictingPriorityOverrides: MutableSet<String> = mutableSetOf(),
    val focusDisplayModeOverrides: MutableMap<String, FocusDisplayMode> = sortedMapOf(),
    val conflictingDisplayModeOverrides: MutableSet<String> = mutableSetOf(),
)

/** Stable, strict codec shared by the app writer and the SystemUI reader. */
object SmartCapsuleConfigCodec {
    private const val BINARY_MAGIC = 0x53494331 // "SIC1"
    private const val MAX_PAYLOAD_LENGTH = 512 * 1_024

    @JvmStatic
    fun encode(snapshot: SmartCapsuleConfigSnapshot): String {
        val normalized = snapshot.normalized()
        require(
            normalized.rules.all { rule ->
                rule.islandPriority == IslandPriority.LOW &&
                    rule.channels.islandPriorityOverrides.isEmpty() &&
                    rule.focusDisplayMode == FocusDisplayMode.ISLAND_AND_FOCUS &&
                    rule.channels.focusDisplayModeOverrides.isEmpty()
            },
        ) { "Legacy smart-capsule binary codec cannot encode priority or display mode" }
        val bytes =
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeInt(BINARY_MAGIC)
                    output.writeInt(SystemUiSmartCapsuleContract.SCHEMA_VERSION)
                    output.writeBoolean(normalized.enabled)
                    output.writeInt(normalized.userId)
                    output.writeLong(normalized.revision)
                    output.writeInt(normalized.rules.size)
                    normalized.rules.forEach { rule ->
                        output.writeUTF(rule.packageName)
                        output.writeBoolean(rule.channels.allChannels)
                        output.writeInt(rule.channels.channelIds.size)
                        rule.channels.channelIds.forEach(output::writeUTF)
                    }
                }
                buffer.toByteArray()
            }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).also { payload ->
            require(payload.length <= MAX_PAYLOAD_LENGTH) {
                "Smart-capsule payload exceeds the transport limit"
            }
        }
    }

    @JvmStatic
    fun digest(payload: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    @JvmStatic
    fun configurationDigest(snapshot: SmartCapsuleConfigSnapshot): String =
        digest(SmartCapsuleRemoteSnapshot.encode(snapshot.normalized().copy(revision = 1L)))

    /** Decodes only an intact, canonical payload produced by [encode]. */
    @JvmStatic
    fun decode(
        payload: String,
        expectedDigest: String,
    ): SmartCapsuleConfigSnapshot {
        require(payload.isNotEmpty() && payload.length <= MAX_PAYLOAD_LENGTH) {
            "Smart-capsule payload size is invalid"
        }
        require(expectedDigest.matches(Regex("[0-9a-f]{64}"))) {
            "Smart-capsule digest format is invalid"
        }
        require(
            MessageDigest.isEqual(
                digest(payload).toByteArray(StandardCharsets.US_ASCII),
                expectedDigest.toByteArray(StandardCharsets.US_ASCII),
            ),
        ) { "Smart-capsule payload digest mismatch" }

        val bytes =
            runCatching { Base64.getUrlDecoder().decode(payload) }
                .getOrElse { error -> throw IllegalArgumentException("Smart-capsule payload is not Base64", error) }
        val decoded =
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == BINARY_MAGIC) { "Smart-capsule payload magic mismatch" }
                require(input.readInt() == SystemUiSmartCapsuleContract.SCHEMA_VERSION) {
                    "Unsupported smart-capsule schema"
                }
                val enabled = input.readBoolean()
                val userId = input.readInt()
                val revision = input.readLong()
                val ruleCount = input.readInt()
                require(ruleCount in 0..SmartCapsuleConfigSnapshot.MAX_APP_RULES) {
                    "Smart-capsule rule count is invalid"
                }
                val rules =
                    List(ruleCount) {
                        val packageName = input.readUTF()
                        val allChannels = input.readBoolean()
                        val channelCount = input.readInt()
                        require(channelCount in 0..ChannelSelection.MAX_CHANNELS_PER_APP) {
                            "Smart-capsule Channel count is invalid"
                        }
                        AppRule(
                            packageName = packageName,
                            channels =
                                ChannelSelection(
                                    allChannels = allChannels,
                                    channelIds = List(channelCount) { input.readUTF() },
                                ),
                        )
                    }
                require(input.read() == -1) { "Smart-capsule payload has trailing data" }
                SmartCapsuleConfigSnapshot(enabled, userId, revision, rules)
            }
        require(decoded.normalized() == decoded) { "Smart-capsule payload is not canonical" }
        return decoded
    }

    @JvmStatic
    fun decodeOrNull(
        payload: String?,
        expectedDigest: String?,
    ): SmartCapsuleConfigSnapshot? {
        if (payload == null || expectedDigest == null) return null
        return runCatching { decode(payload, expectedDigest) }.getOrNull()
    }
}

/** RemotePreferences wire contract. Config slots and hook runtime telemetry never share keys. */
object SystemUiSmartCapsuleContract {
    /** Legacy binary payload schema retained only for local migration compatibility. */
    const val SCHEMA_VERSION = 1
    const val REMOTE_PREFERENCES = "SuperIslandSmartCapsule"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    const val MODULE_PACKAGE = "io.github.superisland"

    const val SLOT_A = 0
    const val SLOT_B = 1
    const val KEY_ACTIVE_SLOT = "config.active_slot"
    const val KEY_ACTIVE_REVISION = "config.active_revision"
    const val ACCEPTANCE_CONSUMER_SYSTEM_UI = "systemui"
    const val ACCEPTANCE_CONSUMER_XMSF = "xmsf"

    const val ACTION_RELOAD_CONFIG = "io.github.superisland.action.RELOAD_SMART_CAPSULE_CONFIG"
    const val ACTION_REPORT_XMSF_ACCEPTANCE =
        "io.github.superisland.action.REPORT_XMSF_SMART_CAPSULE_ACCEPTANCE"
    const val RELOAD_SENDER_PERMISSION = "io.github.superisland.permission.SEND_RESIDENT_ISLAND"

    const val RUNTIME_SENDER_PERMISSION = "android.permission.STATUS_BAR_SERVICE"
    const val REPORT_PROVIDER_AUTHORITY = "io.github.superisland.smartcapsule.reports"
    const val METHOD_REPORT_RUNTIME = "report_runtime_v1"
    const val METHOD_REPORT_CHANNELS = "report_channels_v1"
    const val METHOD_REPORT_CONFIG_ACCEPTANCE = "report_config_acceptance_v1"
    const val EXTRA_REPORT_ACCEPTED = "io.github.superisland.extra.SMART_CAPSULE_REPORT_ACCEPTED"
    const val EXTRA_RUNTIME_CAPABILITY = "io.github.superisland.extra.SMART_CAPSULE_CAPABILITY"
    const val EXTRA_RUNTIME_ADAPTER = "io.github.superisland.extra.SMART_CAPSULE_ADAPTER"
    const val EXTRA_RUNTIME_LAST_FAILURE = "io.github.superisland.extra.SMART_CAPSULE_LAST_FAILURE"
    const val EXTRA_RUNTIME_ACTIVE_SESSION_COUNT =
        "io.github.superisland.extra.SMART_CAPSULE_ACTIVE_SESSION_COUNT"
    const val EXTRA_RUNTIME_PLUGIN_EPOCH = "io.github.superisland.extra.SMART_CAPSULE_PLUGIN_EPOCH"
    const val EXTRA_CONFIG_REVISION = "io.github.superisland.extra.SMART_CAPSULE_CONFIG_REVISION"
    const val EXTRA_CONFIG_DIGEST = "io.github.superisland.extra.SMART_CAPSULE_CONFIG_DIGEST"

    const val ACTION_REQUEST_CHANNELS = "io.github.superisland.action.REQUEST_SMART_CAPSULE_CHANNELS"
    const val EXTRA_CHANNEL_PACKAGE = "io.github.superisland.extra.SMART_CAPSULE_CHANNEL_PACKAGE"
    const val EXTRA_CHANNEL_USER_ID = "io.github.superisland.extra.SMART_CAPSULE_CHANNEL_USER_ID"
    const val EXTRA_CHANNEL_IDS = "io.github.superisland.extra.SMART_CAPSULE_CHANNEL_IDS"
    const val EXTRA_CHANNEL_NAMES = "io.github.superisland.extra.SMART_CAPSULE_CHANNEL_NAMES"
    const val EXTRA_CHANNEL_IMPORTANCE = "io.github.superisland.extra.SMART_CAPSULE_CHANNEL_IMPORTANCE"

    const val KEY_RUNTIME_CAPABILITY = "runtime.capability"
    const val KEY_RUNTIME_ADAPTER = "runtime.adapter"
    const val KEY_RUNTIME_LAST_FAILURE = "runtime.last_failure"
    const val KEY_RUNTIME_ACTIVE_SESSION_COUNT = "runtime.active_session_count"
    const val KEY_RUNTIME_PLUGIN_EPOCH = "runtime.plugin_epoch"

    @JvmStatic
    fun slotSchemaKey(slot: Int): String = "config.slot.${requireSlot(slot)}.schema"

    @JvmStatic
    fun slotRevisionKey(slot: Int): String = "config.slot.${requireSlot(slot)}.revision"

    @JvmStatic
    fun slotUserIdKey(slot: Int): String = "config.slot.${requireSlot(slot)}.user_id"

    @JvmStatic
    fun slotPayloadKey(slot: Int): String = "config.slot.${requireSlot(slot)}.payload"

    /** Complete JSON envelope used by the current SystemUI/XMSF A/B transport. */
    @JvmStatic
    fun slotSnapshotJsonKey(slot: Int): String = "config.slot.${requireSlot(slot)}.snapshot_json"

    @JvmStatic
    fun slotDigestKey(slot: Int): String = "config.slot.${requireSlot(slot)}.digest"

    @JvmStatic
    fun alternateSlot(activeSlot: Int): Int = if (activeSlot == SLOT_A) SLOT_B else SLOT_A

    @JvmStatic
    fun isSlot(slot: Int): Boolean = slot == SLOT_A || slot == SLOT_B

    private fun requireSlot(slot: Int): Int {
        require(isSlot(slot)) { "Unknown smart-capsule config slot: $slot" }
        return slot
    }
}
