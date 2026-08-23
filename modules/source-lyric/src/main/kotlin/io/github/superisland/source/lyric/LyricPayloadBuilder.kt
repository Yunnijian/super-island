package io.github.superisland.source.lyric

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * Pure Focus payload factory used by tests and non-NotificationCompat callers.
 *
 * SystemUI normally publishes through [FocusNotificationPublisher], which adds the notification
 * identity and monotonic sequence. This factory intentionally owns only the audited `param_v2`
 * shape so it can be reused without an Android `JSONObject` dependency.
 */
object LyricPayloadBuilder {
    private const val BUSINESS = "super_island_lyric"
    private const val LEFT_ICON = "io.github.superisland.focus.icon.left"
    private const val RIGHT_ICON = "io.github.superisland.focus.icon.right"
    private const val ISLAND_PROPERTY_PERSISTENT = 1
    private const val ISLAND_PRIORITY = 1
    private const val MAX_TEXT_CODE_POINTS = 64
    private const val PROGRESS_REACH = "#3482FF"
    private const val PROGRESS_UNREACH = "#333333"

    /** Compatibility entry retained for the original single-line source API. */
    fun buildFocusLyricJson(line: LyricLine): String =
        buildFocusLyricJson(
            LyricSnapshot(
                publisher = "local",
                line = line,
                playback = LyricPlayback(positionMs = line.startMs, isPlaying = true),
            ),
        )

    fun buildFocusLyricJson(
        snapshot: LyricSnapshot,
        config: LyricIslandConfig = LyricIslandConfig(enabled = true),
    ): String {
        val value = config.normalized()
        val leftMode = if (value.lyricMode == 1) IslandContentMode.LYRIC else value.contentLeft
        val rightMode = if (value.lyricMode == 1) IslandContentMode.LYRIC else value.contentRight
        val duplicateLyricSlots = value.lyricMode == 0 &&
            value.contentLeft == IslandContentMode.LYRIC &&
            value.contentRight == IslandContentMode.LYRIC
        val leftResolved = if (value.lyricMode == 1) {
            contentFor(snapshot, value, IslandContentMode.LYRIC, true)
        } else if (duplicateLyricSlots && value.slot == LyricSlot.RIGHT) {
            ""
        } else {
            contentFor(snapshot, value, value.contentLeft, true)
        }
        val rightResolved = if (value.lyricMode == 1) {
            contentFor(snapshot, value, IslandContentMode.LYRIC, false)
        } else if (duplicateLyricSlots && value.slot != LyricSlot.RIGHT) {
            ""
        } else {
            contentFor(snapshot, value, value.contentRight, false)
        }
        // Keep the legacy compact payload useful when one lyric slot is active but metadata has
        // not arrived yet. Explicit NONE remains empty, and a metadata-only layout never falls
        // back to a lyric placeholder.
        val hasLyricMode = value.lyricMode == 1 ||
            value.contentLeft == IslandContentMode.LYRIC ||
            value.contentRight == IslandContentMode.LYRIC
        val primary = if (hasLyricMode) primaryText(snapshot, value) else ""
        val secondary = if (hasLyricMode) {
            secondaryText(snapshot, value).ifBlank {
                if (usesNextLyricPreview(snapshot, value)) "" else primary
            }
        } else ""
        val left = if (duplicateLyricSlots) {
            leftResolved
        } else {
            leftResolved.ifBlank {
                if (leftMode != IslandContentMode.LYRIC || !hasLyricMode) ""
                else if (value.slot == LyricSlot.RIGHT) secondary else primary
            }
        }
        val right = if (duplicateLyricSlots) {
            rightResolved
        } else {
            rightResolved.ifBlank {
                if (rightMode != IslandContentMode.LYRIC || !hasLyricMode) ""
                else if (value.slot == LyricSlot.RIGHT) primary else secondary
            }
        }
        // A metadata-only layout is valid even when no lyric line has arrived. Decide whether
        // there is anything to publish from the resolved slots, rather than from lyric text.
        if (left.isBlank() && right.isBlank()) return ""
        val progress = LyricProgress.lineProgress(snapshot.line, snapshot.playback.positionMs)
        val showProgress = value.showProgress && progress != null

        return buildJsonObject {
            put(
                "param_v2",
                buildJsonObject {
                    put("protocol", 1)
                    put("business", BUSINESS)
                    put("enableFloat", false)
                    put("isFirstFloat", false)
                    put("updatable", true)
                    put("param_island", islandParam(left, right, progress, showProgress))
                },
            )
        }.toString()
    }

    /** Resolves one native/Fallback slot using HyperLyric's content-mode contract. */
    fun contentFor(
        snapshot: LyricSnapshot,
        config: LyricIslandConfig,
        mode: IslandContentMode,
        leftSlot: Boolean,
    ): String {
        if (mode == IslandContentMode.LYRIC) {
            val primary = primaryText(snapshot, config)
            if (config.lyricMode != 1) return bounded(primary)
            val secondary = secondaryText(snapshot, config).ifBlank {
                if (usesNextLyricPreview(snapshot, config)) "" else primary
            }
            return bounded(if (leftSlot) primary else secondary)
        }
        if (mode == IslandContentMode.MUSIC_INFO) {
            val raw = if (leftSlot) config.musicInfoFirstLine else config.musicInfoSecondLine
            return bounded(resolveMusicInfo(raw, snapshot, config.musicInfoSeparator))
        }
        return ""
    }

    private fun resolveMusicInfo(raw: String, snapshot: LyricSnapshot, separator: String): String {
        val values = raw.split(',').mapNotNull { field ->
            when (field.trim()) {
                LyricMusicInfoLayout.FIELD_TITLE -> snapshot.title
                LyricMusicInfoLayout.FIELD_ARTIST -> snapshot.artist
                LyricMusicInfoLayout.FIELD_ALBUM -> snapshot.album
                LyricMusicInfoLayout.FIELD_DURATION -> snapshot.playback.durationMs
                    .takeIf { it > 0L }
                    ?.let { formatTime(it, it) }
                LyricMusicInfoLayout.FIELD_ELAPSED -> formatTime(
                    snapshot.playback.positionMs,
                    maxOf(snapshot.playback.positionMs, snapshot.playback.durationMs, 0L),
                )
                LyricMusicInfoLayout.FIELD_REMAINING ->
                    if (snapshot.playback.durationMs > 0L) formatTime(
                        (snapshot.playback.durationMs - snapshot.playback.positionMs).coerceAtLeast(0L),
                        maxOf(snapshot.playback.positionMs, snapshot.playback.durationMs, 0L),
                    ) else null
                LyricMusicInfoLayout.FIELD_PROGRESS_PERCENT -> snapshot.playback.durationMs
                    .takeIf { it > 0L }
                    ?.let {
                        val fraction =
                            (snapshot.playback.positionMs.toDouble() / it.toDouble()).coerceIn(0.0, 1.0)
                        "${(fraction * 100.0).roundToInt()}%"
                    }
                else -> null
            }?.takeIf(String::isNotBlank)
        }
        return values.joinToString(separatorValue(separator))
    }

    private fun separatorValue(value: String): String = when (value) {
        LyricMusicInfoLayout.SEPARATOR_PLUS -> " + "
        LyricMusicInfoLayout.SEPARATOR_SPACE -> " "
        LyricMusicInfoLayout.SEPARATOR_COMMA -> ", "
        LyricMusicInfoLayout.SEPARATOR_IDEOGRAPHIC_COMMA -> "、"
        LyricMusicInfoLayout.SEPARATOR_SLASH -> " / "
        LyricMusicInfoLayout.SEPARATOR_NONE -> ""
        else -> " - "
    }

    private fun formatTime(milliseconds: Long, referenceDurationMs: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
        val seconds = totalSeconds % 60L
        val showHours = referenceDurationMs >= HOUR_MS
        return if (showHours) {
            "%02d:%02d:%02d".format(totalSeconds / 3_600L, (totalSeconds / 60L) % 60L, seconds)
        } else {
            "%02d:%02d".format(totalSeconds / 60L, seconds)
        }
    }

    private const val HOUR_MS = 3_600_000L

    private fun islandParam(
        left: String,
        right: String,
        progress: Int?,
        showProgress: Boolean,
    ): JsonObject = buildJsonObject {
        put("islandProperty", ISLAND_PROPERTY_PERSISTENT)
        put("islandPriority", ISLAND_PRIORITY)
        put("islandOrder", false)
        put(
            "bigIslandArea",
            buildJsonObject {
                put(
                    "imageTextInfoLeft",
                    buildJsonObject {
                        put("type", 1)
                        put("picInfo", iconInfo(LEFT_ICON))
                        put("textInfo", textInfo(left, true))
                    },
                )
                put(
                    "imageTextInfoRight",
                    buildJsonObject {
                        put("type", 2)
                        put("picInfo", iconInfo(RIGHT_ICON))
                        put("textInfo", textInfo(right, false))
                    },
                )
            },
        )
        put(
            "smallIslandArea",
            if (showProgress && progress != null) {
                buildJsonObject {
                    put(
                        "combinePicInfo",
                        buildJsonObject {
                            put("picInfo", iconInfo(LEFT_ICON))
                            put(
                                "progressInfo",
                                buildJsonObject {
                                    put("progress", progress)
                                    put("colorReach", PROGRESS_REACH)
                                    put("colorUnReach", PROGRESS_UNREACH)
                                    put("isCCW", true)
                                },
                            )
                        },
                    )
                }
            } else {
                buildJsonObject {
                    put("picInfo", iconInfo(LEFT_ICON))
                    put("textInfo", textInfo(right, false))
                }
            },
        )
    }

    private fun iconInfo(key: String): JsonObject = buildJsonObject {
        put("type", 1)
        put("pic", key)
    }

    private fun textInfo(text: String, highlight: Boolean): JsonObject = buildJsonObject {
        put("title", bounded(text))
        if (highlight) put("showHighlightColor", true)
    }

    private fun primaryText(snapshot: LyricSnapshot, config: LyricIslandConfig): String {
        val hasTranslation = !snapshot.translation?.text.isNullOrBlank()
        if (!config.disableTranslation && !usesNextLyricPreview(snapshot, config) &&
            (config.translationOnly || config.swapTranslation) && hasTranslation
        ) {
            return bounded(snapshot.translation?.text)
        }
        snapshot.line?.text?.takeIf { it.isNotBlank() }?.let { return bounded(it) }
        return when (config.placeholder) {
            LyricPlaceholder.NAME -> bounded(snapshot.title)
            LyricPlaceholder.NAME_ARTIST -> {
                val title = bounded(snapshot.title)
                val artist = bounded(snapshot.artist)
                when {
                    title.isNotBlank() && artist.isNotBlank() -> bounded("$title - $artist")
                    title.isNotBlank() -> title
                    else -> artist
                }
            }
            LyricPlaceholder.COUNTDOWN -> "..."
            LyricPlaceholder.NONE -> ""
        }
    }

    private fun secondaryText(snapshot: LyricSnapshot, config: LyricIslandConfig): String {
        if (usesNextLyricPreview(snapshot, config)) {
            return snapshot.secondary?.text?.let(::bounded).orEmpty()
        }
        if (config.disableTranslation || !config.displayTranslation) return ""
        val hasTranslation = !snapshot.translation?.text.isNullOrBlank()
        if (config.translationOnly && hasTranslation) {
            return ""
        }
        if (config.swapTranslation && hasTranslation) {
            return snapshot.line?.text?.let(::bounded).orEmpty()
        }
        snapshot.translation?.text?.takeIf { it.isNotBlank() }?.let { return bounded(it) }
        return ""
    }

    /** Next-line preview takes ownership of the second line for its supported sources. */
    private fun usesNextLyricPreview(snapshot: LyricSnapshot, config: LyricIslandConfig): Boolean {
        if (!config.nextLyricLine) return false
        if (config.sourceMode != LyricSourceMode.LYRICON &&
            config.sourceMode != LyricSourceMode.LYRIC_INFO
        ) return false
        return true
    }

    private fun bounded(value: String?): String =
        LyricProgress.boundedText(value, MAX_TEXT_CODE_POINTS)
}
