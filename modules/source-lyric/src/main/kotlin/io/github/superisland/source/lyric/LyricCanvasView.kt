package io.github.superisland.source.lyric

import android.content.Context
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.FrameLayout
import io.github.superisland.source.lyric.hyperlyric.model.RichLyricLine
import io.github.superisland.source.lyric.hyperlyric.model.LyricWord as RichLyricWord
import io.github.superisland.source.lyric.hyperlyric.view.Highlight
import io.github.superisland.source.lyric.hyperlyric.view.Marquee
import io.github.superisland.source.lyric.hyperlyric.view.LyricViewStyle
import io.github.superisland.source.lyric.hyperlyric.view.RichLyricLineView
import io.github.superisland.source.lyric.hyperlyric.view.TextLook
import io.github.superisland.source.lyric.hyperlyric.view.WordMotion
import io.github.superisland.source.lyric.hyperlyric.model.lyricMetadataOf
import io.github.superisland.source.lyric.hyperlyric.common.lyric.RichLyricLineSplitter
import io.github.superisland.source.lyric.hyperlyric.view.METADATA_COUNTDOWN_LINE
import io.github.superisland.source.lyric.hyperlyric.view.METADATA_NEXT_LINE_PREVIEW
import io.github.superisland.source.lyric.hyperlyric.view.METADATA_TITLE_LINE
import io.github.superisland.source.lyric.hyperlyric.view.yoyo.YoYoPresets
import io.github.superisland.source.lyric.hyperlyric.view.yoyo.animateUpdate

/**
 * Thin Android host around the copied HyperLyric rich renderer.
 *
 * SystemUI can attach this view to a native island slot when the ROM exposes that slot. The Focus
 * payload path remains the fallback, so a renderer/API mismatch never blocks ordinary lyrics.
 */
class LyricCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val richView = RichLyricLineView(context)
    private var currentConfig = LyricIslandConfig()
    private var currentArtworkColors: List<Int> = emptyList()
    private var statusBarTextColor: Int = Color.WHITE
    private var metadataSnapshot: LyricSnapshot? = null
    private var lastContentSignature: Int? = null

    init {
        clipChildren = false
        clipToPadding = false
        addView(
            richView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        applyConfig(currentConfig)
    }

    fun setLyric(line: LyricLine, positionMs: Long) {
        setSnapshot(
            LyricSnapshot(
                publisher = "local",
                line = line,
                playback = LyricPlayback(positionMs = positionMs, isPlaying = true),
            ),
            currentConfig,
        )
    }

    /**
     * Applies a snapshot to this slot. [splitSlot] is 0/1 for the two native slots in HyperLyric's
     * separated mode; -1 keeps the ordinary single-slot line intact.
     */
    fun setSnapshot(
        snapshot: LyricSnapshot,
        config: LyricIslandConfig = currentConfig,
        splitSlot: Int = -1,
    ) {
        val normalizedConfig = config.normalized()
        // The native host tags a canvas after construction. Refresh geometry on every snapshot
        // so the first frame also receives the configured side padding.
        applySlotPadding(normalizedConfig)
        if (
            normalizedConfig != currentConfig ||
            snapshot.artworkColors != currentArtworkColors ||
            metadataSnapshot != null
        ) {
            currentConfig = normalizedConfig
            currentArtworkColors = snapshot.artworkColors
            applyConfig(currentConfig, currentArtworkColors, metadataMode = false)
        }
        val line = snapshot.line?.toRichLine(snapshot.translation, snapshot.secondary)
            ?: placeholderLine(snapshot, normalizedConfig)
        val renderedLine = line?.let { candidate ->
            if (splitSlot in 0..1 && normalizedConfig.lyricMode == 1) {
                splitForNativeSlot(candidate, normalizedConfig, splitSlot)
            } else {
                candidate
            }
        }
        val signature = lineSignature(renderedLine)
        val apply = {
            metadataSnapshot = null
            richView.line = renderedLine
            richView.setPlaybackActive(snapshot.playback.isPlaying)
            richView.setPosition(snapshot.playback.positionMs, snapshot.playback.speed)
        }
        val animate = currentConfig.animEnabled || currentConfig.animation == LyricAnimation.SMOOTH
        if (animate &&
            lastContentSignature != null && lastContentSignature != signature
        ) {
            runCatching {
                richView.animateUpdate(
                    YoYoPresets.getById(currentConfig.animId) ?: YoYoPresets.Default
                ) { apply() }
            }.onFailure {
                // SystemUI theme/runtime drift must not strand the slot with stale content.
                apply()
            }
        } else {
            apply()
        }
        lastContentSignature = signature
    }

    fun setPosition(positionMs: Long, playbackSpeed: Float = 1f) {
        richView.setPosition(positionMs, playbackSpeed)
    }

    fun setPlaybackActive(active: Boolean) {
        richView.setPlaybackActive(active)
    }

    /**
     * Updates the color used by HyperLyric's "follow status bar" option.  SystemUI owns the
     * appearance flag, so the native host supplies the resolved color when it binds a slot.
     */
    fun setStatusBarTextColor(color: Int) {
        if (statusBarTextColor == color) return
        statusBarTextColor = color
        applyConfig(
            currentConfig,
            currentArtworkColors,
            centerOverride = if (metadataSnapshot != null) currentConfig.centerMusicInfo else null,
            metadataMode = metadataSnapshot != null,
        )
    }

    /** Returns the configured side width for the native slot wrapper. */
    fun configuredWidthPx(config: LyricIslandConfig = currentConfig): Int {
        val density = resources.displayMetrics.density
        val minWidth = LyricIslandWidthPolicy.minIslandWidth(
            LyricIslandWidthPolicy.isAlbumCoverVisible(config.albumCoverStyle),
            LyricIslandWidthPolicy.isMusicWaveVisible(config.musicWaveStyle),
        )
        val maxWidth = LyricIslandWidthPolicy.maxIslandWidth(
            LyricIslandWidthPolicy.isMusicWaveVisible(config.musicWaveStyle),
            config.disableWidthLimit,
        ).coerceAtLeast(minWidth)
        val targetDp = if (config.widthMode == 0) {
            config.rightContentMaxWidth
        } else {
            // "仅歌词" follows the primary lyric line; translation/preview rows must not
            // widen the native island in that mode. The combined width remains HyperLyric's
            // default basis.
            val measured = if (config.dynamicWidthBasis == 1) {
                richView.main.lineWidth
            } else {
                maxOf(richView.main.lineWidth, richView.secondary.lineWidth)
            }
            ((measured / density) +
                if (tag == "SUPER_ISLAND_LYRIC_RIGHT") {
                    config.rightPaddingLeft + config.rightPaddingRight
                } else {
                    config.leftPaddingLeft + config.leftPaddingRight
                }).toInt()
                .coerceIn(config.dynamicMinWidth, config.dynamicMaxWidth)
        }
        return (targetDp.coerceIn(minWidth, maxWidth) * density).toInt().coerceAtLeast(1)
    }

    /** Renders HyperLyric's configurable two-row music metadata in a native island slot. */
    fun setMetadata(snapshot: LyricSnapshot, config: LyricIslandConfig = currentConfig) {
        val normalizedConfig = config.normalized()
        val metadataMarqueeWasEnabled = currentConfig.metadataMarqueeMode
        val hadMetadataLine = metadataSnapshot != null
        applySlotPadding(normalizedConfig)
        if (normalizedConfig != currentConfig || snapshot.artworkColors != currentArtworkColors) {
            currentConfig = normalizedConfig
            currentArtworkColors = snapshot.artworkColors
            applyConfig(
                currentConfig,
                currentArtworkColors,
                centerOverride = normalizedConfig.centerMusicInfo,
                metadataMode = true,
            )
        } else if (metadataSnapshot == null) {
            // A slot can switch from lyrics to metadata without recreating the CanvasView.
            applyConfig(
                currentConfig,
                currentArtworkColors,
                centerOverride = normalizedConfig.centerMusicInfo,
                metadataMode = true,
            )
        }
        val first = metadataFields(normalizedConfig.musicInfoFirstLine)
            .mapNotNull { metadataValue(it, snapshot) }
            .filter { it.isNotBlank() }
            .joinToString(metadataSeparator(normalizedConfig.musicInfoSeparator))
        val second = metadataFields(normalizedConfig.musicInfoSecondLine)
            .mapNotNull { metadataValue(it, snapshot) }
            .filter { it.isNotBlank() }
            .joinToString(metadataSeparator(normalizedConfig.musicInfoSeparator))
        val primary = first.ifBlank { second }
        val metadataLine = if (primary.isBlank()) {
            null
        } else {
            RichLyricLine(
                begin = 0L,
                end = Long.MAX_VALUE,
                text = primary,
                secondary = second.takeIf { first.isNotBlank() && it.isNotBlank() },
                metadata = lyricMetadataOf(METADATA_TITLE_LINE to "true"),
            )
        }
        metadataSnapshot = snapshot
        val signature = lineSignature(metadataLine)
        if (signature == lastContentSignature) {
            // Clearing the metadata override also clears RichLyricLineView's pending request.
            // Re-arm it only for a false -> true transition; position ticks must stay cheap.
            if (!metadataMarqueeWasEnabled && normalizedConfig.metadataMarqueeMode) {
                richView.post { richView.requestStartMarquee() }
            }
            return
        }
        // Position-derived metadata (elapsed/progress) can change every clock tick. Preserve the
        // current marquee/scroll state instead of rebuilding the line as a fresh lyric each time.
        if (hadMetadataLine) {
            richView.updateMetadataLine(metadataLine)
        } else {
            richView.line = metadataLine
        }
        richView.setPlaybackActive(false)
        richView.setPosition(0L, 1f)
        if (normalizedConfig.metadataMarqueeMode) richView.post { richView.requestStartMarquee() }
        lastContentSignature = signature
    }

    /** Refreshes elapsed/remaining/progress fields without disturbing an active lyric line. */
    fun updateMetadataPosition(positionMs: Long, playbackSpeed: Float = 1f) {
        val snapshot = metadataSnapshot ?: return
        setMetadata(
            snapshot.copy(
                playback = snapshot.playback.copy(
                    positionMs = positionMs.coerceAtLeast(0L),
                    speed = playbackSpeed,
                ),
            ),
            currentConfig,
        )
    }

    private fun applyConfig(
        config: LyricIslandConfig,
        artworkColors: List<Int> = currentArtworkColors,
        centerOverride: Boolean? = null,
        metadataMode: Boolean = false,
    ) {
        applySlotPadding(config)
        richView.displayTranslation = !config.disableTranslation && config.displayTranslation
        richView.displayRoma = false
        val density = resources.displayMetrics.density
        val scaledDensity = resources.displayMetrics.scaledDensity
        val primaryTypeface = runCatching {
            if (config.customFontPath.isNotBlank()) {
                Typeface.createFromFile(config.customFontPath)
            } else if (config.narrowLatinFont) {
                Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            } else {
                Typeface.DEFAULT
            }
        }.getOrDefault(Typeface.DEFAULT)
        val typeface = runCatching {
            Typeface.create(primaryTypeface, config.fontWeight, config.fontItalic)
        }.getOrDefault(primaryTypeface)
        val primarySize = config.textSizeSp * scaledDensity
        val secondarySize = primarySize * config.textSizeRatio
        val coverColors = artworkColors
            .ifEmpty { listOf(config.textColor) }
            .take(4)
        val primaryColors = when (config.textColorStyle) {
            // HyperLyric's cover-color mode is a solid swatch; the gradient mode is the only
            // mode that feeds the full extracted palette to RichLyricLineView.
            1 -> intArrayOf(coverColors.first())
            2 -> coverColors.toIntArray()
            3 -> intArrayOf(statusBarTextColor)
            else -> intArrayOf(config.textColor)
        }
        val highlightColors = when (config.textColorStyle) {
            1 -> intArrayOf(coverColors.first())
            2 -> intArrayOf(coverColors.first())
            3 -> intArrayOf(statusBarTextColor)
            else -> intArrayOf(config.highlightColor)
        }
        // HyperLyric builds styles per slot mode. Applying the metadata override to every
        // canvas made its settings overwrite (or clear) the separate lyric marquee settings.
        val marqueeEnabled = if (metadataMode) config.metadataMarqueeMode else config.marqueeMode
        val marqueeSpeed = if (metadataMode) config.metadataMarqueeSpeed else config.marqueeSpeed
        val marqueeDelay = if (metadataMode) config.metadataMarqueeDelay else config.marqueeDelay
        val marqueeLoopDelay = if (metadataMode) {
            config.metadataMarqueeLoopDelay
        } else {
            config.marqueeLoopDelay
        }
        val marqueeInfinite = if (metadataMode) {
            config.metadataMarqueeInfinite
        } else {
            config.marqueeInfinite
        }
        val marqueeStopEnd = if (metadataMode) true else config.marqueeStopEnd
        val repeatCount = if (!marqueeEnabled) 0 else if (marqueeInfinite) -1 else 1
        richView.setStyle(
            LyricViewStyle(
                primary = TextLook(
                    color = primaryColors,
                    size = primarySize,
                    typeface = typeface,
                    relativeProgress = config.syllableRelative,
                    relativeHighlight = config.syllableHighlight,
                ),
                secondary = TextLook(
                    color = primaryColors,
                    size = secondarySize,
                    typeface = typeface,
                ),
                highlight = Highlight(
                    background = primaryColors,
                    foreground = highlightColors,
                ),
                gradient = config.gradientProgressStyle,
                lineDisplay = config.syllableLineDisplay,
                fadingEdge = (config.fadingEdgeLengthDp * density).toInt(),
                marquee = Marquee(
                    speed = if (marqueeEnabled) marqueeSpeed.toFloat() else 0f,
                    spacing = 70f * density,
                    initialDelay = marqueeDelay,
                    loopDelay = marqueeLoopDelay,
                    repeatCount = repeatCount,
                    stopAtEnd = marqueeStopEnd,
                ),
                wordMotion = WordMotion(
                    enabled = config.wordMotionEnabled,
                    cjkLiftFactor = config.wordMotionCjkLift,
                    cjkWaveFactor = config.wordMotionCjkWave,
                    latinByCharacter = config.wordMotionLatinByCharacter,
                    latinLiftFactor = config.wordMotionLatinLift,
                    latinWaveFactor = config.wordMotionLatinWave,
                ),
                animation = io.github.superisland.source.lyric.hyperlyric.view.AnimParams(
                    enabled = config.animEnabled,
                    presetId = config.animId,
                ),
                transitionConfig = if (config.animEnabled) "smooth" else "none",
                centerIfPossible = centerOverride ?: config.centerLyric,
                rightIfPossible = if (centerOverride != null) false else config.rightLyric,
            ),
        )
        if (marqueeEnabled) {
            // RichLyricLineView starts scrolling only after attachment and after its line model
            // has been measured. Posting mirrors HyperLyric's requestStartMarquee lifecycle.
            richView.post { richView.requestStartMarquee() }
        }
    }

    /** Applies HyperLyric's per-slot wrapper padding in dp, with its runtime non-negative clamp. */
    private fun applySlotPadding(config: LyricIslandConfig) {
        val isRight = tag == "SUPER_ISLAND_LYRIC_RIGHT"
        val density = resources.displayMetrics.density
        val leftDp = if (isRight) config.rightPaddingLeft else config.leftPaddingLeft
        val rightDp = if (isRight) config.rightPaddingRight else config.leftPaddingRight
        val leftPx = (leftDp * density).toInt().coerceAtLeast(0)
        val rightPx = (rightDp * density).toInt().coerceAtLeast(0)
        if (paddingLeft != leftPx || paddingRight != rightPx) {
            setPadding(leftPx, paddingTop, rightPx, paddingBottom)
            requestLayout()
        }
    }

    private fun lineSignature(line: RichLyricLine?): Int = line?.let {
        listOf(it.begin, it.end, it.text, it.words, it.translation, it.translationWords,
            it.secondary, it.secondaryWords).hashCode()
    } ?: 0

    private fun splitForNativeSlot(
        line: RichLyricLine,
        config: LyricIslandConfig,
        splitSlot: Int,
    ): RichLyricLine {
        val density = resources.displayMetrics.density
        val scaledDensity = resources.displayMetrics.scaledDensity
        val typeface = runCatching {
            if (config.customFontPath.isNotBlank()) {
                Typeface.createFromFile(config.customFontPath)
            } else if (config.narrowLatinFont) {
                Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            } else {
                Typeface.DEFAULT
            }
        }.getOrDefault(Typeface.DEFAULT).let {
            runCatching { Typeface.create(it, config.fontWeight, config.fontItalic) }.getOrDefault(it)
        }
        val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = config.textSizeSp * scaledDensity
            this.typeface = typeface
        }
        val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = config.textSizeSp * config.textSizeRatio * scaledDensity
            this.typeface = typeface
        }
        fun contentWidth(widthDp: Int, left: Boolean): Float {
            val padding = if (left) {
                config.leftPaddingLeft.coerceAtLeast(0) + config.leftPaddingRight.coerceAtLeast(0)
            } else {
                config.rightPaddingLeft.coerceAtLeast(0) + config.rightPaddingRight.coerceAtLeast(0)
            }
            return (widthDp * density - padding * density).coerceAtLeast(1f)
        }
        val fixedWidth = config.rightContentMaxWidth.coerceAtLeast(22)
        val spec = if (config.widthMode == 1) {
            // In lyric-only mode the metadata/translation rows must not make the dynamic island
            // grow. The splitter still receives the same bounds, but the measured lyric text is
            // the sole basis for the target width.
            val lyricOnly = config.dynamicWidthBasis == 1
            val primaryWidth = if (lyricOnly) primaryPaint.measureText(line.text.orEmpty()) else 0f
            RichLyricLineSplitter.ContainerWidthSpec.Dynamic(
                leftMinWidthPx = contentWidth(config.dynamicMinWidth, true),
                leftMaxWidthPx = contentWidth(config.dynamicMaxWidth, true),
                rightMinWidthPx = contentWidth(config.dynamicMinWidth, false),
                rightMaxWidthPx = contentWidth(config.dynamicMaxWidth, false),
                basisWidthPx = primaryWidth.takeIf { lyricOnly && it > 0f },
            )
        } else {
            RichLyricLineSplitter.ContainerWidthSpec.Fixed(
                leftWidthPx = contentWidth(fixedWidth, true),
                rightWidthPx = contentWidth(fixedWidth, false),
            )
        }
        val split = RichLyricLineSplitter.split(
            line = line,
            primaryPaint = primaryPaint,
            secondaryPaint = secondaryPaint,
            containerWidthSpec = spec,
        )
        return (if (splitSlot == 0) split.left else split.right) as? RichLyricLine ?: line
    }

    private fun metadataFields(raw: String): List<String> = raw.split(',')
        .map(String::trim)
        .filter { it.isNotEmpty() }

    private fun metadataValue(field: String, snapshot: LyricSnapshot): String? {
        val playback = snapshot.playback
        val duration = playback.durationMs
        val position = playback.positionMs.coerceAtLeast(0L)
        return when (field) {
            LyricMusicInfoLayout.FIELD_TITLE -> snapshot.title
            LyricMusicInfoLayout.FIELD_ARTIST -> snapshot.artist
            LyricMusicInfoLayout.FIELD_ALBUM -> snapshot.album
            LyricMusicInfoLayout.FIELD_DURATION -> duration.takeIf { it > 0L }
                ?.let { formatMediaTime(it, it) }
            LyricMusicInfoLayout.FIELD_ELAPSED -> formatMediaTime(
                position,
                maxOf(position, duration, 0L),
            )
            LyricMusicInfoLayout.FIELD_REMAINING -> (duration - position).coerceAtLeast(0L)
                .takeIf { duration > 0L }
                ?.let { formatMediaTime(it, maxOf(position, duration, 0L)) }
            LyricMusicInfoLayout.FIELD_PROGRESS_PERCENT -> duration.takeIf { it > 0L }
                ?.let {
                    val fraction = (position.toDouble() / it.toDouble()).coerceIn(0.0, 1.0)
                    "${kotlin.math.round(fraction * 100.0).toInt()}%"
                }
            else -> null
        }
    }

    private fun metadataSeparator(value: String): String = when (value) {
        LyricMusicInfoLayout.SEPARATOR_PLUS -> " + "
        LyricMusicInfoLayout.SEPARATOR_SPACE -> " "
        LyricMusicInfoLayout.SEPARATOR_COMMA -> ", "
        LyricMusicInfoLayout.SEPARATOR_IDEOGRAPHIC_COMMA -> "、"
        LyricMusicInfoLayout.SEPARATOR_SLASH -> " / "
        LyricMusicInfoLayout.SEPARATOR_NONE -> ""
        else -> " - "
    }

    private fun formatMediaTime(milliseconds: Long, referenceDurationMs: Long): String {
        if (milliseconds < 0L) return ""
        val totalSeconds = milliseconds / 1_000L
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3_600L
        val showHours = referenceDurationMs >= 3_600_000L
        return if (showHours) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(totalSeconds / 60L, seconds)
        }
    }

    private fun LyricLine.toRichLine(
        translation: LyricLine?,
        secondary: LyricLine?,
    ): RichLyricLine = RichLyricLine(
        begin = startMs,
        end = endMs,
        text = text,
        words = words.map { it.toRichWord() },
        translation = translation?.text,
        translationWords = translation?.words?.map { it.toRichWord() },
        // The source snapshot's secondary line is the upcoming lyric. It is opt-in; keeping it
        // off the rich model by default prevents the renderer from showing it as translation.
        secondary = null,
        secondaryWords = null,
    ).let { rich ->
        val showTranslation = !currentConfig.disableTranslation && currentConfig.displayTranslation
        val original = rich.text
        val originalWords = rich.words
        val translated = rich.translation
        val translatedWords = rich.translationWords
        if (currentConfig.translationOnly) {
            rich.copy(
                text = if (showTranslation) translated ?: original else original,
                words = if (showTranslation) translatedWords ?: originalWords else originalWords,
                translation = null,
                translationWords = null,
            )
        } else if (currentConfig.swapTranslation && !translated.isNullOrBlank()) {
            rich.copy(
                text = if (showTranslation) translated ?: original else original,
                words = if (showTranslation) translatedWords ?: originalWords else originalWords,
                translation = if (showTranslation) original else null,
                translationWords = if (showTranslation) originalWords else null,
            )
        } else {
            val nextLineSource = currentConfig.sourceMode == LyricSourceMode.LYRICON ||
                currentConfig.sourceMode == LyricSourceMode.LYRIC_INFO
            val showNextLine = currentConfig.nextLyricLine && nextLineSource
            rich.copy(
                translation = if (showTranslation && !showNextLine) translated else null,
                translationWords = if (showTranslation && !showNextLine) translatedWords else null,
                secondary = if (showNextLine) secondary?.text else rich.secondary,
                secondaryWords = if (showNextLine) secondary?.words?.map { it.toRichWord() } else rich.secondaryWords,
            ).also { result ->
                if (showNextLine && !secondary?.text.isNullOrBlank()) {
                    result.metadata = lyricMetadataOf(METADATA_NEXT_LINE_PREVIEW to "true")
                }
            }
        }
    }

    private fun placeholderLine(
        snapshot: LyricSnapshot,
        config: LyricIslandConfig,
    ): RichLyricLine? {
        val position = snapshot.playback.positionMs
        return when (config.placeholder) {
            LyricPlaceholder.NONE -> null
            LyricPlaceholder.NAME -> snapshot.title?.takeIf { it.isNotBlank() }?.let { title ->
                RichLyricLine(begin = position, end = position + 3_000L, text = title).apply {
                    metadata = lyricMetadataOf(METADATA_TITLE_LINE to "true")
                }
            }
            LyricPlaceholder.NAME_ARTIST -> {
                val title = snapshot.title.orEmpty()
                val artist = snapshot.artist.orEmpty()
                val text = when {
                    title.isNotBlank() && artist.isNotBlank() -> "$title - $artist"
                    title.isNotBlank() -> title
                    else -> artist
                }
                text.takeIf { it.isNotBlank() }?.let {
                    RichLyricLine(begin = position, end = position + 3_000L, text = it).apply {
                        metadata = lyricMetadataOf(METADATA_TITLE_LINE to "true")
                    }
                }
            }
            LyricPlaceholder.COUNTDOWN -> RichLyricLine(
                begin = position,
                end = position + 3_000L,
            ).apply {
                metadata = lyricMetadataOf(
                    METADATA_TITLE_LINE to "true",
                    METADATA_COUNTDOWN_LINE to "true",
                )
            }
        }
    }

    private fun LyricWord.toRichWord(): RichLyricWord = RichLyricWord(
        begin = startMs,
        end = endMs,
        text = text,
    )
}
