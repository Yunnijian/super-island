package io.github.superisland.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * One complete, immutable RemotePreferences document consumed by SystemUI and XMSF.
 *
 * [digest] covers the canonical document body, including schema, Android user, revision and all
 * application rules. It detects torn or corrupted transport data; it is not an authentication
 * credential.
 */
class SmartCapsuleRemoteSnapshot private constructor(
    val schema: Int,
    val userId: Int,
    val revision: Long,
    val digest: String,
    val enabled: Boolean,
    val applications: List<HyperIslandAppConfig>,
) {
    fun toConfigSnapshot(): SmartCapsuleConfigSnapshot =
        SmartCapsuleConfigSnapshot(
            enabled = enabled,
            userId = userId,
            revision = revision,
            rules = applications.map(HyperIslandAppConfig::toAppRule),
        ).normalized()

    fun matches(
        packageName: String,
        sourceUserId: Int,
        channelId: String,
    ): Boolean {
        if (!enabled || sourceUserId != userId) return false
        val index =
            applications.binarySearchBy(
                key = packageName,
                selector = HyperIslandAppConfig::packageName,
            )
        return index >= 0 && applications[index].matches(packageName, channelId)
    }

    fun resolveIslandPriority(
        packageName: String,
        sourceUserId: Int,
        channelId: String,
    ): IslandPriority? {
        if (!enabled || sourceUserId != userId) return null
        val index =
            applications.binarySearchBy(
                key = packageName,
                selector = HyperIslandAppConfig::packageName,
            )
        if (index < 0) return null
        val application = applications[index]
        if (!application.matches(packageName, channelId)) return null
        return application.effectiveIslandPriority(channelId)
    }

    fun resolveFocusDisplayMode(
        packageName: String,
        sourceUserId: Int,
        channelId: String,
    ): FocusDisplayMode? {
        if (!enabled || sourceUserId != userId) return null
        val index =
            applications.binarySearchBy(
                key = packageName,
                selector = HyperIslandAppConfig::packageName,
            )
        if (index < 0) return null
        val application = applications[index]
        if (!application.matches(packageName, channelId)) return null
        return application.effectiveFocusDisplayMode(channelId)
    }

    /** Package/user selection for the XMSF authorization boundary, where Channel is unavailable. */
    fun matchesPackage(
        packageName: String,
        sourceUserId: Int,
    ): Boolean =
        enabled &&
            sourceUserId == userId &&
            applications.binarySearchBy(
                key = packageName,
                selector = HyperIslandAppConfig::packageName,
            ) >= 0

    fun toJson(): String = encodeDocument(schema, userId, revision, digest, enabled, applications)

    companion object {
        const val SCHEMA_VERSION = 4
        const val LEGACY_SCHEMA_V3 = 3
        const val LEGACY_SCHEMA_VERSION = 2
        private const val MAX_DOCUMENT_LENGTH = 512 * 1_024
        private val DIGEST_PATTERN = Regex("[0-9a-f]{64}")

        @JvmStatic
        fun fromConfig(snapshot: SmartCapsuleConfigSnapshot): SmartCapsuleRemoteSnapshot {
            val normalized = snapshot.normalized()
            require(normalized.revision > 0L) { "Smart-capsule revision must be positive" }
            val applications = normalized.rules.mapNotNull(HyperIslandAppConfig::from)
            require(applications.size == normalized.rules.size) {
                "Smart-capsule configuration contains an invalid application rule"
            }
            val digest =
                digest(
                    encodeBody(
                        SCHEMA_VERSION,
                        normalized.userId,
                        normalized.revision,
                        normalized.enabled,
                        applications,
                    ),
                )
            return SmartCapsuleRemoteSnapshot(
                schema = SCHEMA_VERSION,
                userId = normalized.userId,
                revision = normalized.revision,
                digest = digest,
                enabled = normalized.enabled,
                applications = applications,
            )
        }

        @JvmStatic
        fun encode(snapshot: SmartCapsuleConfigSnapshot): String = fromConfig(snapshot).toJson()

        @JvmStatic
        fun decode(json: String): SmartCapsuleRemoteSnapshot =
            decode(json, expectedUserId = null, minimumRevision = 1L)

        @JvmStatic
        fun decode(
            json: String,
            expectedUserId: Int,
            minimumRevision: Long,
        ): SmartCapsuleRemoteSnapshot =
            decode(json, expectedUserId = expectedUserId as Int?, minimumRevision = minimumRevision)

        @JvmStatic
        fun decodeOrNull(json: String?): SmartCapsuleRemoteSnapshot? {
            if (json == null) return null
            return runCatching { decode(json) }.getOrNull()
        }

        @JvmStatic
        fun decodeOrNull(
            json: String?,
            expectedUserId: Int,
            minimumRevision: Long,
        ): SmartCapsuleRemoteSnapshot? {
            if (json == null) return null
            return runCatching { decode(json, expectedUserId, minimumRevision) }.getOrNull()
        }

        /** Strict schema-v3 decoder used only by the app's durable-store migration path. */
        @JvmStatic
        fun decodeLegacyV3ConfigOrNull(json: String?): SmartCapsuleConfigSnapshot? {
            if (json == null) return null
            return runCatching {
                decode(
                    json = json,
                    expectedUserId = null,
                    minimumRevision = 1L,
                    requiredSchema = LEGACY_SCHEMA_V3,
                ).toConfigSnapshot()
            }.getOrNull()
        }

        /** Strict schema-v2 decoder used only by the app's durable-store migration path. */
        @JvmStatic
        fun decodeLegacyV2ConfigOrNull(json: String?): SmartCapsuleConfigSnapshot? {
            if (json == null) return null
            return runCatching {
                decode(
                    json = json,
                    expectedUserId = null,
                    minimumRevision = 1L,
                    requiredSchema = LEGACY_SCHEMA_VERSION,
                ).toConfigSnapshot()
            }.getOrNull()
        }

        @JvmStatic
        fun digest(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

        private fun decode(
            json: String,
            expectedUserId: Int?,
            minimumRevision: Long,
            requiredSchema: Int = SCHEMA_VERSION,
        ): SmartCapsuleRemoteSnapshot {
            require(json.isNotEmpty() && json.length <= MAX_DOCUMENT_LENGTH) {
                "Smart-capsule remote snapshot size is invalid"
            }
            require(minimumRevision >= 0L) { "Minimum smart-capsule revision is invalid" }
            val root = StrictJsonParser(json).parse().asObject("remote snapshot")
            require(
                root.keys.toList() ==
                    listOf("schema", "userId", "revision", "digest", "enabled", "applications"),
            ) { "Smart-capsule remote snapshot fields are invalid" }

            val schema = root.getValue("schema").asInt("schema")
            require(schema == requiredSchema) {
                "Unsupported smart-capsule remote schema"
            }
            val userId = root.getValue("userId").asInt("userId")
            require(userId >= 0) { "Smart-capsule remote userId is invalid" }
            require(expectedUserId == null || userId == expectedUserId) {
                "Smart-capsule remote snapshot belongs to another Android user"
            }
            val revision = root.getValue("revision").asLong("revision")
            require(revision > 0L && revision >= minimumRevision) {
                "Smart-capsule remote revision is invalid"
            }
            val recordedDigest = root.getValue("digest").asString("digest")
            require(DIGEST_PATTERN.matches(recordedDigest)) {
                "Smart-capsule remote digest format is invalid"
            }
            val enabled = root.getValue("enabled").asBoolean("enabled")
            val applicationObject = root.getValue("applications").asObject("applications")
            require(applicationObject.size <= SmartCapsuleConfigSnapshot.MAX_APP_RULES) {
                "Smart-capsule remote application count is invalid"
            }
            require(applicationObject.keys.toList() == applicationObject.keys.sorted()) {
                "Smart-capsule remote applications are not canonical"
            }

            val applications =
                applicationObject.map { (packageName, rawConfig) ->
                    val appObject = rawConfig.asObject("application config")
                    val expectedAppFields =
                        when (schema) {
                            SCHEMA_VERSION -> listOf("displayMode", "islandPriority", "channels")
                            LEGACY_SCHEMA_V3 -> listOf("islandPriority", "channels")
                            else -> listOf("channels")
                        }
                    require(appObject.keys.toList() == expectedAppFields) {
                        "Smart-capsule application fields are invalid"
                    }
                    val focusDisplayMode =
                        if (schema == SCHEMA_VERSION) {
                            appObject.getValue("displayMode")
                                .asFocusDisplayMode("application displayMode")
                        } else {
                            FocusDisplayMode.ISLAND_AND_FOCUS
                        }
                    val islandPriority =
                        if (schema == SCHEMA_VERSION || schema == LEGACY_SCHEMA_V3) {
                            appObject.getValue("islandPriority")
                                .asIslandPriority("application islandPriority")
                        } else {
                            IslandPriority.LOW
                        }
                    val channelsObject = appObject.getValue("channels").asObject("channels")
                    if (schema == SCHEMA_VERSION || schema == LEGACY_SCHEMA_V3) {
                        require(channelsObject.keys.toList() == listOf("enabled", "settings")) {
                            "Smart-capsule Channel fields are invalid"
                        }
                    } else {
                        require(channelsObject.keys.toList() == listOf("enabled")) {
                            "Smart-capsule Channel fields are invalid"
                        }
                    }
                    val enabledChannels =
                        channelsObject.getValue("enabled")
                            .asArray("channels.enabled")
                            .map { value -> value.asString("channels.enabled entry") }
                    val channelSettings =
                        if (schema == SCHEMA_VERSION || schema == LEGACY_SCHEMA_V3) {
                            val settingsObject =
                                channelsObject.getValue("settings").asObject("channels.settings")
                            require(settingsObject.size <= ChannelSelection.MAX_CHANNELS_PER_APP) {
                                "Smart-capsule Channel settings count is invalid"
                            }
                            require(settingsObject.keys.toList() == settingsObject.keys.sorted()) {
                                "Smart-capsule Channel settings are not canonical"
                            }
                            settingsObject.mapValuesTo(linkedMapOf()) { (channelId, rawSettings) ->
                                val settings = rawSettings.asObject("Channel settings for $channelId")
                                val expectedSettingFields =
                                    if (schema == SCHEMA_VERSION) {
                                        listOf("displayMode", "islandPriority")
                                    } else {
                                        listOf("islandPriority")
                                    }
                                require(settings.keys.toList() == expectedSettingFields) {
                                    "Smart-capsule Channel setting fields are invalid"
                                }
                                HyperIslandAppConfig.Channels.Settings(
                                    islandPriority =
                                        settings.getValue("islandPriority")
                                            .asIslandPriority("Channel islandPriority"),
                                    focusDisplayMode =
                                        if (schema == SCHEMA_VERSION) {
                                            settings.getValue("displayMode")
                                                .asFocusDisplayMode("Channel displayMode")
                                        } else {
                                            focusDisplayMode
                                        },
                                )
                            }
                        } else {
                            emptyMap()
                        }
                    val appConfig =
                        HyperIslandAppConfig(
                            packageName = packageName,
                            channels =
                                HyperIslandAppConfig.Channels(
                                    enabled = enabledChannels,
                                    settings = channelSettings,
                                ),
                            islandPriority = islandPriority,
                            focusDisplayMode = focusDisplayMode,
                        )
                    require(appConfig.normalizedOrNull() == appConfig) {
                        "Smart-capsule application configuration is not canonical"
                    }
                    appConfig
                }

            val exactChannelCount =
                applications.sumOf { app ->
                    if (app.channels.enabled.isEmpty()) {
                        app.channels.settings.size
                    } else {
                        app.channels.enabled.size
                    }
                }
            require(exactChannelCount <= SmartCapsuleConfigSnapshot.MAX_TOTAL_EXACT_CHANNELS) {
                "Smart-capsule remote Channel count exceeds the snapshot limit"
            }
            val expectedDigest = digest(encodeBody(schema, userId, revision, enabled, applications))
            require(
                MessageDigest.isEqual(
                    expectedDigest.toByteArray(StandardCharsets.US_ASCII),
                    recordedDigest.toByteArray(StandardCharsets.US_ASCII),
                ),
            ) { "Smart-capsule remote snapshot digest mismatch" }

            val decoded =
                SmartCapsuleRemoteSnapshot(
                    schema = schema,
                    userId = userId,
                    revision = revision,
                    digest = recordedDigest,
                    enabled = enabled,
                    applications = applications,
                )
            require(decoded.toJson() == json) { "Smart-capsule remote snapshot is not canonical" }
            return decoded
        }

        private fun encodeDocument(
            schema: Int,
            userId: Int,
            revision: Long,
            digest: String,
            enabled: Boolean,
            applications: List<HyperIslandAppConfig>,
        ): String =
            buildString {
                append("{\"schema\":").append(schema)
                append(",\"userId\":").append(userId)
                append(",\"revision\":").append(revision)
                append(",\"digest\":\"").append(digest).append('"')
                append(",\"enabled\":").append(enabled)
                appendApplications(schema, applications)
                append('}')
            }

        private fun encodeBody(
            schema: Int,
            userId: Int,
            revision: Long,
            enabled: Boolean,
            applications: List<HyperIslandAppConfig>,
        ): String =
            buildString {
                append("{\"schema\":").append(schema)
                append(",\"userId\":").append(userId)
                append(",\"revision\":").append(revision)
                append(",\"enabled\":").append(enabled)
                appendApplications(schema, applications)
                append('}')
            }

        private fun StringBuilder.appendApplications(
            schema: Int,
            applications: List<HyperIslandAppConfig>,
        ) {
            append(",\"applications\":{")
            applications.forEachIndexed { appIndex, app ->
                if (appIndex > 0) append(',')
                appendJsonString(app.packageName)
                append(":{")
                if (schema == SCHEMA_VERSION) {
                    append("\"displayMode\":").append(app.focusDisplayMode.wireValue)
                    append(",")
                }
                if (schema == SCHEMA_VERSION || schema == LEGACY_SCHEMA_V3) {
                    append("\"islandPriority\":").append(app.islandPriority.wireValue)
                    append(",")
                }
                append("\"channels\":{\"enabled\":[")
                app.channels.enabled.forEachIndexed { channelIndex, channelId ->
                    if (channelIndex > 0) append(',')
                    appendJsonString(channelId)
                }
                append(']')
                if (schema == SCHEMA_VERSION || schema == LEGACY_SCHEMA_V3) {
                    append(",\"settings\":{")
                    app.channels.settings.entries.forEachIndexed { settingIndex, (channelId, setting) ->
                        if (settingIndex > 0) append(',')
                        appendJsonString(channelId)
                        append(":{")
                        if (schema == SCHEMA_VERSION) {
                            append("\"displayMode\":")
                                .append(setting.focusDisplayMode.wireValue)
                                .append(',')
                        }
                        append("\"islandPriority\":")
                            .append(setting.islandPriority.wireValue)
                            .append('}')
                    }
                    append('}')
                }
                append("}}")
            }
            append('}')
        }

        private fun StringBuilder.appendJsonString(value: String) {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else ->
                        if (character.code < 0x20) {
                            append("\\u")
                            append(character.code.toString(16).padStart(4, '0'))
                        } else {
                            append(character)
                        }
                }
            }
            append('"')
        }
    }
}

/** Java-friendly codec facade for hook modules that should not construct envelopes manually. */
object SmartCapsuleRemoteSnapshotCodec {
    @JvmStatic
    fun encode(snapshot: SmartCapsuleConfigSnapshot): String = SmartCapsuleRemoteSnapshot.encode(snapshot)

    @JvmStatic
    fun decode(json: String): SmartCapsuleRemoteSnapshot = SmartCapsuleRemoteSnapshot.decode(json)

    @JvmStatic
    fun decodeOrNull(json: String?): SmartCapsuleRemoteSnapshot? =
        SmartCapsuleRemoteSnapshot.decodeOrNull(json)

    @JvmStatic
    fun decodeOrNull(
        json: String?,
        expectedUserId: Int,
        minimumRevision: Long,
    ): SmartCapsuleRemoteSnapshot? =
        SmartCapsuleRemoteSnapshot.decodeOrNull(json, expectedUserId, minimumRevision)
}

data class SmartCapsuleRemoteSlot(
    val slot: Int,
    val snapshot: SmartCapsuleRemoteSnapshot,
)

/**
 * Selects only a published snapshot and never promotes a staged inactive revision.
 *
 * Callers pass their last accepted revision as `minimumRevision` and retain
 * that in-memory snapshot when this returns null.
 */
object SmartCapsuleRemoteSnapshotSelector {
    @JvmStatic
    fun selectPublishedOrNull(
        activeSlot: Int,
        activeRevision: Long,
        slotAJson: String?,
        slotBJson: String?,
        expectedUserId: Int,
        minimumRevision: Long,
    ): SmartCapsuleRemoteSlot? {
        if (!SystemUiSmartCapsuleContract.isSlot(activeSlot) || activeRevision <= 0L) return null
        val documents = arrayOf(slotAJson, slotBJson)
        val active =
            SmartCapsuleRemoteSnapshot.decodeOrNull(
                documents[activeSlot],
                expectedUserId,
                minimumRevision,
            )
        if (active?.revision == activeRevision) return SmartCapsuleRemoteSlot(activeSlot, active)

        val fallbackSlot = SystemUiSmartCapsuleContract.alternateSlot(activeSlot)
        val fallback =
            SmartCapsuleRemoteSnapshot.decodeOrNull(
                documents[fallbackSlot],
                expectedUserId,
                minimumRevision,
            ) ?: return null
        return fallback
            .takeIf { snapshot -> snapshot.revision < activeRevision }
            ?.let { snapshot -> SmartCapsuleRemoteSlot(fallbackSlot, snapshot) }
    }
}

private sealed interface JsonValue

private data class JsonObject(val values: LinkedHashMap<String, JsonValue>) : JsonValue

private data class JsonArray(val values: List<JsonValue>) : JsonValue

private data class JsonString(val value: String) : JsonValue

private data class JsonNumber(val value: String) : JsonValue

private data class JsonBoolean(val value: Boolean) : JsonValue

private data object JsonNull : JsonValue

private fun JsonValue.asObject(field: String): LinkedHashMap<String, JsonValue> =
    (this as? JsonObject)?.values ?: throw IllegalArgumentException("$field must be an object")

private fun JsonValue.asArray(field: String): List<JsonValue> =
    (this as? JsonArray)?.values ?: throw IllegalArgumentException("$field must be an array")

private fun JsonValue.asString(field: String): String =
    (this as? JsonString)?.value ?: throw IllegalArgumentException("$field must be a string")

private fun JsonValue.asBoolean(field: String): Boolean =
    (this as? JsonBoolean)?.value ?: throw IllegalArgumentException("$field must be a boolean")

private fun JsonValue.asLong(field: String): Long =
    (this as? JsonNumber)?.value?.toLongOrNull()
        ?: throw IllegalArgumentException("$field must be an integer")

private fun JsonValue.asInt(field: String): Int {
    val value = asLong(field)
    require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "$field is outside the integer range" }
    return value.toInt()
}

private fun JsonValue.asIslandPriority(field: String): IslandPriority =
    IslandPriority.fromWireValueOrNull(asInt(field))
        ?: throw IllegalArgumentException("$field is invalid")

private fun JsonValue.asFocusDisplayMode(field: String): FocusDisplayMode =
    FocusDisplayMode.fromWireValueOrNull(asInt(field))
        ?: throw IllegalArgumentException("$field is invalid")

/** Minimal strict parser for the bounded snapshot schema; no permissive string extraction. */
private class StrictJsonParser(
    private val source: String,
) {
    private var index = 0

    fun parse(): JsonValue {
        val value = parseValue(depth = 0)
        skipWhitespace()
        require(index == source.length) { "Smart-capsule remote snapshot has trailing data" }
        return value
    }

    private fun parseValue(depth: Int): JsonValue {
        require(depth <= MAX_DEPTH) { "Smart-capsule remote snapshot nesting is invalid" }
        skipWhitespace()
        require(index < source.length) { "Unexpected end of smart-capsule remote snapshot" }
        return when (source[index]) {
            '{' -> parseObject(depth + 1)
            '[' -> parseArray(depth + 1)
            '"' -> JsonString(parseString())
            't' -> parseLiteral("true", JsonBoolean(true))
            'f' -> parseLiteral("false", JsonBoolean(false))
            'n' -> parseLiteral("null", JsonNull)
            '-', in '0'..'9' -> JsonNumber(parseInteger())
            else -> throw IllegalArgumentException("Invalid smart-capsule JSON token")
        }
    }

    private fun parseObject(depth: Int): JsonObject {
        expect('{')
        skipWhitespace()
        val values = linkedMapOf<String, JsonValue>()
        if (consume('}')) return JsonObject(values)
        while (true) {
            skipWhitespace()
            require(index < source.length && source[index] == '"') { "JSON object key is invalid" }
            val key = parseString()
            require(key !in values) { "Duplicate JSON object key" }
            skipWhitespace()
            expect(':')
            values[key] = parseValue(depth)
            skipWhitespace()
            if (consume('}')) return JsonObject(values)
            expect(',')
        }
    }

    private fun parseArray(depth: Int): JsonArray {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (consume(']')) return JsonArray(values)
        while (true) {
            require(values.size < MAX_ARRAY_ENTRIES) { "JSON array exceeds the snapshot limit" }
            values += parseValue(depth)
            skipWhitespace()
            if (consume(']')) return JsonArray(values)
            expect(',')
        }
    }

    private fun parseString(): String {
        expect('"')
        val value = StringBuilder()
        while (index < source.length) {
            val character = source[index++]
            when {
                character == '"' -> return value.toString()
                character == '\\' -> {
                    require(index < source.length) { "Invalid JSON escape" }
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> value.append(escaped)
                        'b' -> value.append('\b')
                        'f' -> value.append('\u000C')
                        'n' -> value.append('\n')
                        'r' -> value.append('\r')
                        't' -> value.append('\t')
                        'u' -> value.append(parseUnicodeEscape())
                        else -> throw IllegalArgumentException("Invalid JSON escape")
                    }
                }
                character.code < 0x20 -> throw IllegalArgumentException("Unescaped JSON control character")
                else -> value.append(character)
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    private fun parseUnicodeEscape(): Char {
        require(index + 4 <= source.length) { "Invalid JSON unicode escape" }
        val code = source.substring(index, index + 4).toIntOrNull(16)
            ?: throw IllegalArgumentException("Invalid JSON unicode escape")
        index += 4
        return code.toChar()
    }

    private fun parseInteger(): String {
        val start = index
        consume('-')
        require(index < source.length) { "Invalid JSON number" }
        if (source[index] == '0') {
            index++
            require(index >= source.length || source[index] !in '0'..'9') { "Invalid JSON number" }
        } else {
            require(source[index] in '1'..'9') { "Invalid JSON number" }
            while (index < source.length && source[index] in '0'..'9') index++
        }
        require(index >= source.length || source[index] !in listOf('.', 'e', 'E')) {
            "Floating-point JSON numbers are not supported"
        }
        return source.substring(start, index)
    }

    private fun <T : JsonValue> parseLiteral(
        literal: String,
        value: T,
    ): T {
        require(source.regionMatches(index, literal, 0, literal.length)) { "Invalid JSON literal" }
        index += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index] in JSON_WHITESPACE) index++
    }

    private fun expect(character: Char) {
        require(consume(character)) { "Expected JSON '$character'" }
    }

    private fun consume(character: Char): Boolean {
        if (index >= source.length || source[index] != character) return false
        index++
        return true
    }

    companion object {
        private const val MAX_DEPTH = 8
        private const val MAX_ARRAY_ENTRIES = 256
        private val JSON_WHITESPACE = charArrayOf(' ', '\t', '\n', '\r')
    }
}
