package io.github.superisland.model

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Safe actions supported by the resident island's expanded layout. */
enum class ResidentExpandedActionType {
    REFRESH_NOW,
    LAUNCH_APP,
    OPEN_BATTERY_SETTINGS,
    OPEN_NOTIFICATION_SETTINGS,

    /**
     * Fixed audited app shortcuts. The wire field that normally holds a package name stores a
     * [ResidentKnownShortcut.id] instead. The SystemUI host resolves an explicit exported activity
     * from a hard-coded template and fail-closes when unavailable.
     */
    OPEN_KNOWN_SHORTCUT,
}

/**
 * Catalog of allowlisted system shortcuts. Only these ids may be stored for
 * [ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT].
 */
enum class ResidentKnownShortcut(
    val id: String,
    val displayName: String,
    val packageName: String,
) {
    ALIPAY_PAY(
        id = "alipay_pay",
        displayName = "支付宝付款",
        packageName = "com.eg.android.AlipayGphone",
    ),
    ALIPAY_SCAN(
        id = "alipay_scan",
        displayName = "支付宝扫一扫",
        packageName = "com.eg.android.AlipayGphone",
    ),
    ALIPAY_COLLECT(
        id = "alipay_collect",
        displayName = "支付宝收钱",
        packageName = "com.eg.android.AlipayGphone",
    ),
    ALIPAY_SHORTCUT_SETTINGS(
        id = "alipay_shortcut_settings",
        displayName = "支付宝更多设置",
        packageName = "com.eg.android.AlipayGphone",
    ),
    WECHAT_PAY(
        id = "wechat_pay",
        displayName = "微信支付",
        packageName = "com.tencent.mm",
    ),
    WECHAT_SCAN(
        id = "wechat_scan",
        displayName = "微信扫一扫",
        packageName = "com.tencent.mm",
    ),
    WECHAT_MY_QR_CODE(
        id = "wechat_my_qr_code",
        displayName = "微信我的二维码",
        packageName = "com.tencent.mm",
    ),
    ;

    companion object {
        private val byId = entries.associateBy(ResidentKnownShortcut::id)

        fun fromId(id: String?): ResidentKnownShortcut? =
            id?.trim()?.takeIf(String::isNotEmpty)?.let(byId::get)

        fun isKnownId(id: String?): Boolean = fromId(id) != null
    }
}

/**
 * One stable, user-ordered action displayed in the resident island's expanded layout.
 *
 * [targetPackage] is either:
 * - a launchable package for [ResidentExpandedActionType.LAUNCH_APP], or
 * - a [ResidentKnownShortcut.id] for [ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT].
 *
 * The SystemUI host never accepts an arbitrary component, URI, Intent, shell command, or root
 * command from the wire.
 */
data class ResidentExpandedAction(
    val id: String,
    val label: String,
    val type: ResidentExpandedActionType,
    val targetPackage: String? = null,
) {
    fun isValid(): Boolean =
        ID_PATTERN.matches(id) &&
            label.isNotBlank() &&
            label.hasWellFormedUtf16() &&
            label.codePointCount() <= MAX_LABEL_CODE_POINTS &&
            label.codePoints().noneMatch(Character::isISOControl) &&
            when (type) {
                ResidentExpandedActionType.LAUNCH_APP ->
                    targetPackage != null &&
                        targetPackage.length <= MAX_PACKAGE_NAME_LENGTH &&
                        PACKAGE_NAME_PATTERN.matches(targetPackage)

                ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT ->
                    targetPackage != null &&
                        targetPackage.length <= MAX_PACKAGE_NAME_LENGTH &&
                        SHORTCUT_ID_PATTERN.matches(targetPackage) &&
                        ResidentKnownShortcut.isKnownId(targetPackage)

                ResidentExpandedActionType.REFRESH_NOW,
                ResidentExpandedActionType.OPEN_BATTERY_SETTINGS,
                ResidentExpandedActionType.OPEN_NOTIFICATION_SETTINGS,
                -> targetPackage == null
            }

    companion object {
        const val MAX_ACTIONS = 3
        const val MAX_LABEL_CODE_POINTS = 8
        const val MAX_ID_LENGTH = 64
        const val MAX_PACKAGE_NAME_LENGTH = 255

        /** Stable ids for the three fixed UI slots (图 2 按钮 1/2/3). */
        val SLOT_IDS: List<String> = listOf("slot-0", "slot-1", "slot-2")

        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        private val PACKAGE_NAME_PATTERN =
            Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
        private val SHORTCUT_ID_PATTERN = Regex("[a-z][a-z0-9_]{0,63}")

        fun slotId(index: Int): String = SLOT_IDS.getOrElse(index) { "slot-$index" }

        /**
         * Maps stored actions onto three fixed slots by id. Unknown ids fill the first free slots
         * in list order so older configs still display.
         */
        fun toSlots(actions: List<ResidentExpandedAction>): List<ResidentExpandedAction?> {
            val normalized = normalize(actions)
            val slots = arrayOfNulls<ResidentExpandedAction>(MAX_ACTIONS)
            val remaining = normalized.toMutableList()
            SLOT_IDS.forEachIndexed { index, slotId ->
                val match = remaining.firstOrNull { it.id == slotId }
                if (match != null) {
                    slots[index] = match
                    remaining.remove(match)
                }
            }
            remaining.forEach { action ->
                val free = slots.indexOfFirst { it == null }
                if (free >= 0) {
                    slots[free] = action.copy(id = SLOT_IDS[free])
                }
            }
            return slots.toList()
        }

        fun fromSlots(slots: List<ResidentExpandedAction?>): List<ResidentExpandedAction> =
            normalize(
                slots.mapIndexedNotNull { index, action ->
                    action?.copy(id = slotId(index))?.takeIf(ResidentExpandedAction::isValid)
                },
            )

        fun normalize(actions: List<ResidentExpandedAction>): List<ResidentExpandedAction> {
            val seenIds = hashSetOf<String>()
            return actions
                .asSequence()
                .filter(ResidentExpandedAction::isValid)
                .filter { action -> seenIds.add(action.id) }
                .take(MAX_ACTIONS)
                .toList()
        }

        private fun String.codePointCount(): Int = codePointCount(0, length)

        private fun String.hasWellFormedUtf16(): Boolean {
            var index = 0
            while (index < length) {
                val character = this[index]
                when {
                    Character.isHighSurrogate(character) -> {
                        if (index + 1 >= length || !Character.isLowSurrogate(this[index + 1])) return false
                        index += 2
                    }

                    Character.isLowSurrogate(character) -> return false
                    else -> index += 1
                }
            }
            return true
        }
    }
}

/**
 * Strict, bounded wire codec for the single RemotePreferences action field.
 *
 * The format is `RA1`, followed by at most three newline-delimited records. Every record has four
 * URL-safe, unpadded Base64 fields: stable id, label, enum name, and package/shortcut id (empty
 * unless the action launches an app or known shortcut). Canonical re-encoding prevents permissive
 * decoder variants from entering the cross-process configuration boundary.
 */
object ResidentExpandedActionCodec {
    private const val WIRE_VERSION = "RA1"
    private const val RECORD_SEPARATOR = '\n'
    private const val FIELD_SEPARATOR = ':'
    private const val FIELD_COUNT = 4
    private const val MAX_WIRE_LENGTH = 2_048
    private const val MAX_LABEL_UTF8_BYTES = 64
    private const val MAX_TYPE_UTF8_BYTES = 40
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val encodedFieldPattern = Regex("[A-Za-z0-9_-]*")

    @JvmStatic
    fun encode(actions: List<ResidentExpandedAction>): String {
        require(actions.size <= ResidentExpandedAction.MAX_ACTIONS) {
            "At most ${ResidentExpandedAction.MAX_ACTIONS} resident actions are supported"
        }
        require(actions.map(ResidentExpandedAction::id).distinct().size == actions.size) {
            "Resident action ids must be unique"
        }
        require(actions.all(ResidentExpandedAction::isValid)) { "Resident action is invalid" }

        val encoded =
            buildString {
                append(WIRE_VERSION)
                actions.forEach { action ->
                    append(RECORD_SEPARATOR)
                    append(encodeField(action.id))
                    append(FIELD_SEPARATOR)
                    append(encodeField(action.label))
                    append(FIELD_SEPARATOR)
                    append(encodeField(action.type.name))
                    append(FIELD_SEPARATOR)
                    append(encodeField(action.targetPackage.orEmpty()))
                }
            }
        require(encoded.length <= MAX_WIRE_LENGTH) { "Encoded resident actions exceed the wire limit" }
        return encoded
    }

    /** Returns an empty list for either a valid empty value or any malformed/oversized payload. */
    @JvmStatic
    fun decodeOrEmpty(encoded: String?): List<ResidentExpandedAction> =
        runCatching { decodeStrict(encoded) }.getOrNull().orEmpty()

    private fun decodeStrict(encoded: String?): List<ResidentExpandedAction> {
        if (encoded == null || encoded.length > MAX_WIRE_LENGTH || '\r' in encoded || encoded.endsWith('\n')) {
            return emptyList()
        }
        val lines = encoded.split(RECORD_SEPARATOR)
        if (lines.firstOrNull() != WIRE_VERSION || lines.size > ResidentExpandedAction.MAX_ACTIONS + 1) {
            return emptyList()
        }
        if (lines.size == 1) return emptyList()

        val actions =
            lines.drop(1).map { line ->
                val fields = splitRecord(line) ?: return emptyList()
                val id = decodeField(fields[0], ResidentExpandedAction.MAX_ID_LENGTH)
                    ?: return emptyList()
                val label = decodeField(fields[1], MAX_LABEL_UTF8_BYTES)
                    ?: return emptyList()
                val typeName = decodeField(fields[2], MAX_TYPE_UTF8_BYTES)
                    ?: return emptyList()
                val packageName = decodeField(fields[3], ResidentExpandedAction.MAX_PACKAGE_NAME_LENGTH)
                    ?: return emptyList()
                val type = ResidentExpandedActionType.entries.firstOrNull { it.name == typeName }
                    ?: return emptyList()
                ResidentExpandedAction(
                    id = id,
                    label = label,
                    type = type,
                    targetPackage = packageName.ifEmpty { null },
                ).takeIf(ResidentExpandedAction::isValid) ?: return emptyList()
            }
        if (actions.map(ResidentExpandedAction::id).distinct().size != actions.size) return emptyList()
        return actions
    }

    private fun splitRecord(record: String): List<String>? {
        val fields = ArrayList<String>(FIELD_COUNT)
        var start = 0
        repeat(FIELD_COUNT - 1) {
            val separator = record.indexOf(FIELD_SEPARATOR, start)
            if (separator < 0) return null
            fields += record.substring(start, separator)
            start = separator + 1
        }
        if (record.indexOf(FIELD_SEPARATOR, start) >= 0) return null
        fields += record.substring(start)
        return fields
    }

    private fun encodeField(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(
        encoded: String,
        maxUtf8Bytes: Int,
    ): String? {
        if (!encodedFieldPattern.matches(encoded)) return null
        // Four Base64 characters carry at most three bytes. Bound the encoded form before decode.
        if (encoded.length > ((maxUtf8Bytes + 2) / 3) * 4) return null
        val bytes = runCatching { decoder.decode(encoded) }.getOrNull() ?: return null
        if (bytes.size > maxUtf8Bytes || encodeFieldBytes(bytes) != encoded) return null
        return runCatching {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
    }

    private fun encodeFieldBytes(bytes: ByteArray): String = encoder.encodeToString(bytes)
}
