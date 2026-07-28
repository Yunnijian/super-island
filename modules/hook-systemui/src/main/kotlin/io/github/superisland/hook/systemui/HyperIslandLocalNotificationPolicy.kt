package io.github.superisland.hook.systemui

import java.util.HashMap

internal enum class LocalNotificationSkipReason {
    EXISTING_FOCUS,
    NO_CONFIG,
    RULE_MISMATCH,
    MEDIA,
    BUBBLE,
    FULL_SCREEN,
    GROUP_SUMMARY,
    PROGRESS,
    CUSTOM_FOCUS,
    INVALID_KEY,
    CAPACITY,
}

internal data class PublicNotificationText(
    val title: String? = null,
    val bigTitle: String? = null,
    val conversationTitle: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val infoText: String? = null,
    val ticker: String? = null,
    val channelId: String? = null,
    val appLabel: String? = null,
    val packageName: String,
)

internal data class ResolvedNotificationText(
    val title: String,
    val content: String,
)

internal data class NotificationStructure(
    val hasExistingFocus: Boolean = false,
    val hasCustomFocus: Boolean = false,
    val isMedia: Boolean = false,
    val isBubble: Boolean = false,
    val isFullScreen: Boolean = false,
    val isGroupSummary: Boolean = false,
    val hasProgress: Boolean = false,
)

/** Pure policy shared by the runtime mapper and host-side unit tests. */
internal object HyperIslandLocalNotificationPolicy {
    private val focusOwnershipKeys =
        setOf(
            "miui.focus.param.media",
            "miui.focus.rv",
            "miui.focus.rvNight",
            "miui.focus.rv.night",
            "miui.focus.rvAod",
            "miui.focus.rv.fullAod",
            "miui.focus.pics",
            "miui.focus.isFocus",
        )

    fun hasFocusOwnershipMarker(keys: Set<String>): Boolean = keys.any(focusOwnershipKeys::contains)

    fun hasDeterminateProgress(
        progressMax: Int,
        indeterminate: Boolean,
    ): Boolean = progressMax > 0 && !indeterminate

    fun hasProgressStructure(
        hasProgressSegments: Boolean,
        progressMax: Int,
        indeterminate: Boolean,
    ): Boolean = hasProgressSegments || hasDeterminateProgress(progressMax, indeterminate)

    fun structuralSkipReason(structure: NotificationStructure): LocalNotificationSkipReason? =
        when {
            structure.hasExistingFocus -> LocalNotificationSkipReason.EXISTING_FOCUS
            structure.hasCustomFocus -> LocalNotificationSkipReason.CUSTOM_FOCUS
            structure.isMedia -> LocalNotificationSkipReason.MEDIA
            structure.isBubble -> LocalNotificationSkipReason.BUBBLE
            structure.isFullScreen -> LocalNotificationSkipReason.FULL_SCREEN
            structure.isGroupSummary -> LocalNotificationSkipReason.GROUP_SUMMARY
            structure.hasProgress -> LocalNotificationSkipReason.PROGRESS
            else -> null
        }

    fun resolveText(fields: PublicNotificationText): ResolvedNotificationText {
        val commonFallback =
            firstText(
                fields.channelId,
                fields.appLabel,
                fields.packageName,
            ) ?: fields.packageName
        return ResolvedNotificationText(
            title =
                firstText(
                    fields.title,
                    fields.bigTitle,
                    fields.conversationTitle,
                ) ?: commonFallback,
            content =
                firstText(
                    fields.text,
                    fields.bigText,
                    fields.subText,
                    fields.infoText,
                    fields.ticker,
                ) ?: commonFallback,
        )
    }

    private fun firstText(vararg candidates: String?): String? =
        candidates.firstNotNullOfOrNull { candidate -> candidate?.trim()?.takeIf(String::isNotEmpty) }
}

/**
 * Bounds work in flight without dropping a newer update for the same SBN key.
 *
 * Callers sharing a key serialize on one slot. Distinct keys do not block each other. A new key is
 * rejected when [capacity] distinct keys are already active; existing keys can still drain.
 */
internal class BoundedSbnKeyGate(
    private val capacity: Int,
) {
    private class Slot {
        val monitor = Any()
        var references: Int = 0
    }

    private val lock = Any()
    private val slots = HashMap<String, Slot>()

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    fun <T> withKey(
        key: String,
        block: () -> T,
    ): GateResult<T> {
        if (key.isBlank()) return GateResult.Rejected(LocalNotificationSkipReason.INVALID_KEY)
        val slot =
            synchronized(lock) {
                val existing = slots[key]
                if (existing != null) {
                    existing.references += 1
                    existing
                } else {
                    if (slots.size >= capacity) return GateResult.Rejected(LocalNotificationSkipReason.CAPACITY)
                    Slot().also { created ->
                        created.references = 1
                        slots[key] = created
                    }
                }
            }
        return try {
            GateResult.Completed(synchronized(slot.monitor) { block() })
        } finally {
            synchronized(lock) {
                slot.references -= 1
                if (slot.references == 0 && slots[key] === slot) slots.remove(key)
            }
        }
    }

    fun activeKeyCount(): Int = synchronized(lock) { slots.size }

    sealed interface GateResult<out T> {
        data class Completed<T>(val value: T) : GateResult<T>

        data class Rejected(val reason: LocalNotificationSkipReason) : GateResult<Nothing>
    }
}
