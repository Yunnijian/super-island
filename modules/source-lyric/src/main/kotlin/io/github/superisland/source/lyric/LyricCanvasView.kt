package io.github.superisland.source.lyric

import android.content.Context
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.FrameLayout
import io.github.superisland.source.lyric.hyperlyric.model.RichLyricLine
import io.github.superisland.source.lyric.hyperlyric.model.LyricWord as RichLyricWord
import io.github.superisland.source.lyric.hyperlyric.model.lyricMetadataOf
import io.github.superisland.source.lyric.hyperlyric.view.Highlight
import io.github.superisland.source.lyric.hyperlyric.view.Marquee
import io.github.superisland.source.lyric.hyperlyric.view.LyricViewStyle
import io.github.superisland.source.lyric.hyperlyric.view.METADATA_COUNTDOWN_LINE
import io.github.superisland.source.lyric.hyperlyric.view.METADATA_NEXT_LINE_PREVIEW
import io.github.superisland.source.lyric.hyperlyric.view.METADATA_TITLE_LINE
import io.github.superisland.source.lyric.hyperlyric.view.RichLyricLineView
import io.github.superisland.source.lyric.hyperlyric.view.TextLook
import io.github.superisland.source.lyric.hyperlyric.view.WordMotion
import io.github.superisland.source.lyric.hyperlyric.common.lyric.RichLyricLineSplitter
import io.github.superisland.source.lyric.hyperlyric.common.SuperIslandWidthPolicy
import io.github.superisland.source.lyric.hyperlyric.island.sizing.IslandLyricWidthCalculator
import io.github.superisland.source.lyric.hyperlyric.island.sizing.IslandLyricWidthSpec
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
    private var lastMetadataSourceSignature: Int = 0
    private var lastMetadataPositionSignature: Long = Long.MIN_VALUE
    private var metadataPlaybackActive: Boolean? = null
    private var measuredMainContentWidthPx = 0f
    private var measuredSecondaryContentWidthPx = 0f

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
        rememberMeasuredContentWidths(renderedLine)
        val signature = lineSignature(renderedLine)
        val apply = {
            metadataSnapshot = null
            lastMetadataSourceSignature = 0
            lastMetadataPositionSignature = Long.MIN_VALUE
            metadataPlaybackActive = null
            richView.setLineWithCallbacks(renderedLine)
            richView.setPlaybackActive(snapshot.playback.isPlaying)
            if (normalizedConfig.marqueeMode) richView.post { richView.requestStartMarquee() }
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
        val showAlbum = LyricIslandWidthPolicy.isAlbumCoverVisible(config.albumCoverStyle)
        val showRhythm = LyricIslandWidthPolicy.isMusicWaveVisible(config.musicWaveStyle)
        val isLeft = tag != "SUPER_ISLAND_LYRIC_RIGHT"
        val targetDp: Float = if (config.widthMode == 0) {
            val islandWidth = SuperIslandWidthPolicy.normalizeIslandWidth(
                islandWidth = config.rightContentMaxWidth,
                showAlbum = showAlbum,
                showRhythm = showRhythm,
                disableWidthLimit = config.disableWidthLimit,
            )
            if (isLeft) {
                SuperIslandWidthPolicy.leftContentWidth(
                    islandWidth = islandWidth,
                    showAlbum = showAlbum,
                    showRhythm = showRhythm,
                    disableWidthLimit = config.disableWidthLimit,
                ).toFloat()
            } else {
                islandWidth.toFloat()
            }
        } else {
            val baseWidthDp = dynamicTargetWidthDp(config).toFloat()
            IslandLyricWidthCalculator.targetWidthDp(
                baseWidthDp = baseWidthDp,
                spec = islandWidthSpec(config, isLeft),
            ) ?: baseWidthDp
        }
        return kotlin.math.ceil(targetDp * density).toInt().coerceAtLeast(1)
    }

    /** Returns the shared-width coordinator's base width in the right-slot coordinate system. */
    fun dynamicBaseWidthDp(config: LyricIslandConfig = currentConfig): Float {
        val density = resources.displayMetrics.density
        if (density <= 0f) return 0f
        val mode = config.contentModeForSlot(tag != "SUPER_ISLAND_LYRIC_RIGHT")
        if (config.dynamicWidthBasis == 1 && mode == IslandContentMode.MUSIC_INFO) return Float.NaN
        val measured = if (config.dynamicWidthBasis == 1) {
            maxOf(measuredMainContentWidthPx, richView.main.lineWidth)
        } else {
            maxOf(
                measuredMainContentWidthPx,
                measuredSecondaryContentWidthPx,
                richView.main.lineWidth,
                richView.secondary.lineWidth,
            )
        }
        return IslandLyricWidthCalculator.baseWidthDp(
            contentWidthPx = measured,
            spec = islandWidthSpec(config, tag != "SUPER_ISLAND_LYRIC_RIGHT"),
        ) ?: 0f
    }

    /**
     * HyperLyric calculates the coordinated target before writing it to the slot wrapper. The
     * renderer owns that wrapper, so this view exposes only the copied geometry calculation.
     */
    fun coordinatedWrapperWidthPx(
        baseWidthDp: Float,
        config: LyricIslandConfig = currentConfig,
    ): Int? {
        if (config.widthMode == 0 || !baseWidthDp.isFinite()) return null
        val sharedBaseDp = baseWidthDp.coerceIn(
            config.dynamicMinWidth.toFloat(),
            config.dynamicMaxWidth.toFloat(),
        )
        return IslandLyricWidthCalculator.targetWidthPx(
            baseWidthDp = sharedBaseDp,
            spec = islandWidthSpec(config, tag != "SUPER_ISLAND_LYRIC_RIGHT"),
        )
    }

    /** Minimal host adapter for HyperLyric's [IslandLyricWidthSpec]. */
    private fun islandWidthSpec(config: LyricIslandConfig, isLeft: Boolean): IslandLyricWidthSpec {
        val showAlbum = LyricIslandWidthPolicy.isAlbumCoverVisible(config.albumCoverStyle)
        val showRhythm = LyricIslandWidthPolicy.isMusicWaveVisible(config.musicWaveStyle)
        val offset = if (isLeft) {
            SuperIslandWidthPolicy.leftContentWidthOffsetDp(showAlbum, showRhythm)
        } else {
            0
        }
        val leftPadding = if (isLeft) config.leftPaddingLeft else config.rightPaddingLeft
        val rightPadding = if (isLeft) config.leftPaddingRight else config.rightPaddingRight
        return IslandLyricWidthSpec(
            density = resources.displayMetrics.density,
            paddingLeftPx = (leftPadding.coerceAtLeast(0) * resources.displayMetrics.density).toInt(),
            paddingRightPx = (rightPadding.coerceAtLeast(0) * resources.displayMetrics.density).toInt(),
            minWidthDp = config.dynamicMinWidth + offset,
            maxWidthDp = config.dynamicMaxWidth + offset,
            isLeft = isLeft,
            showAlbum = showAlbum,
            showRhythm = showRhythm,
        )
    }

    private fun dynamicTargetWidthDp(config: LyricIslandConfig): Int {
        val base = dynamicBaseWidthDp(config)
        if (!base.isFinite()) return config.rightContentMaxWidth
        return kotlin.math.ceil(base).toInt()
            .coerceIn(config.dynamicMinWidth, config.dynamicMaxWidth)
    }

    /** Renders HyperLyric's configurable two-row music metadata in a native island slot. */
    fun setMetadata(snapshot: LyricSnapshot, config: LyricIslandConfig = currentConfig) {
        val normalizedConfig = config.normalized()
        val hadMetadataLine = metadataSnapshot != null
        val previousMetadataSnapshot = metadataSnapshot
        // A slot is reused across lyric/metadata mode changes. Clear HyperLyric's previous
        // marquee override only on that boundary; doing it on every metadata position tick would
        // restart the renderer on the SystemUI main looper.
        if (!hadMetadataLine) richView.clearMetadataMarqueeConfig()
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
        val sourceSignature = metadataSourceSignature(snapshot, normalizedConfig)
        val positionSignature = metadataPositionSignature(snapshot.playback, normalizedConfig)
        if (metadataSnapshot != null &&
            sourceSignature == lastMetadataSourceSignature &&
            positionSignature == lastMetadataPositionSignature
        ) {
            // Position ticks often arrive faster than the displayed metadata can change. Keep the
            // cached line and marquee state intact; only a play/pause edge needs to reach the
            // renderer here.
            if (metadataPlaybackActive != snapshot.playback.isPlaying) {
                richView.setPlaybackActive(snapshot.playback.isPlaying)
                metadataPlaybackActive = snapshot.playback.isPlaying
            }
            metadataSnapshot = snapshot
            return
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
            )
        }
        rememberMeasuredContentWidths(metadataLine)
        metadataSnapshot = snapshot
        val signature = lineSignature(metadataLine)
        if (hadMetadataLine && signature == lastContentSignature) {
            lastMetadataSourceSignature = sourceSignature
            lastMetadataPositionSignature = positionSignature
            if (metadataPlaybackActive != snapshot.playback.isPlaying) {
                richView.setPlaybackActive(snapshot.playback.isPlaying)
                metadataPlaybackActive = snapshot.playback.isPlaying
            }
            return
        }
        // Position-derived metadata (elapsed/progress) can change every clock tick. Preserve the
        // current marquee/scroll state instead of rebuilding the line as a fresh lyric each time.
        val metadataContentChanged = !hadMetadataLine || previousMetadataSnapshot?.let { previous ->
            previous.title != snapshot.title || previous.artist != snapshot.artist ||
                previous.album != snapshot.album
        } ?: true
        val applyMetadata = {
            if (hadMetadataLine) {
                richView.updateMetadataLine(metadataLine)
            } else {
                richView.line = metadataLine
            }
            richView.setPlaybackActive(snapshot.playback.isPlaying)
            richView.setPosition(snapshot.playback.positionMs, snapshot.playback.speed)
            metadataPlaybackActive = snapshot.playback.isPlaying
        }
        if (metadataContentChanged && normalizedConfig.animEnabled) {
            runCatching {
                richView.animateUpdate(
                    YoYoPresets.getById(normalizedConfig.animId) ?: YoYoPresets.Default,
                ) { applyMetadata() }
            }.onFailure { applyMetadata() }
        } else {
            applyMetadata()
        }
        lastMetadataSourceSignature = sourceSignature
        lastMetadataPositionSignature = positionSignature
        lastContentSignature = signature
    }

    /** Refreshes elapsed/remaining/progress fields without disturbing an active lyric line. */
    fun updateMetadataPosition(
        positionMs: Long,
        playbackSpeed: Float = 1f,
        playing: Boolean = metadataSnapshot?.playback?.isPlaying ?: false,
    ) {
        val snapshot = metadataSnapshot ?: return
        val nextPlayback = snapshot.playback.copy(
            positionMs = positionMs.coerceAtLeast(0L),
            speed = playbackSpeed,
            isPlaying = playing,
        )
        val nextPositionSignature = metadataPositionSignature(nextPlayback, currentConfig)
        if (nextPositionSignature == lastMetadataPositionSignature) {
            if (snapshot.playback.isPlaying != nextPlayback.isPlaying) {
                richView.setPlaybackActive(nextPlayback.isPlaying)
                metadataPlaybackActive = nextPlayback.isPlaying
                metadataSnapshot = snapshot.copy(playback = nextPlayback)
            }
            return
        }
        setMetadata(snapshot.copy(playback = nextPlayback), currentConfig)
    }

    private fun applyConfig(
        config: LyricIslandConfig,
        artworkColors: List<Int> = currentArtworkColors,
        centerOverride: Boolean? = null,
        metadataMode: Boolean = false,
    ) {
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
        // HyperLyric uses a dedicated 10sp secondary style for metadata's second row. The
        // multiline lyric ratio remains independent and is applied only to lyric translations.
        val secondarySize = if (metadataMode) {
            config.secondaryTextSizeSp * scaledDensity
        } else {
            primarySize * config.textSizeRatio
        }
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
        // Song metadata is intentionally static in Super Island. The upstream metadata-marquee
        // fields remain codec-compatible but are not part of this module's feature surface.
        val marqueeEnabled = if (metadataMode) false else config.marqueeMode
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

    private fun rememberMeasuredContentWidths(line: io.github.superisland.source.lyric.hyperlyric.model.interfaces.IRichLyricLine?) {
        if (line == null) {
            measuredMainContentWidthPx = 0f
            measuredSecondaryContentWidthPx = 0f
            return
        }
        runCatching {
            val widths = richView.measureLineWidths(line)
            measuredMainContentWidthPx = widths.getOrNull(0)?.takeIf { it.isFinite() } ?: 0f
            measuredSecondaryContentWidthPx = widths.getOrNull(1)?.takeIf { it.isFinite() } ?: 0f
        }.onFailure {
            // Keep the last valid preflight values; the committed line widths remain a fallback.
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

    private fun metadataSourceSignature(
        snapshot: LyricSnapshot,
        config: LyricIslandConfig,
    ): Int {
        var result = 17
        result = 31 * result + (snapshot.title?.hashCode() ?: 0)
        result = 31 * result + (snapshot.artist?.hashCode() ?: 0)
        result = 31 * result + (snapshot.album?.hashCode() ?: 0)
        result = 31 * result + snapshot.playback.durationMs.hashCode()
        result = 31 * result + config.hashCode()
        result = 31 * result + snapshot.artworkColors.hashCode()
        return result
    }

    /** Returns a stable key for the smallest position change visible in metadata text. */
    private fun metadataPositionSignature(
        playback: LyricPlayback,
        config: LyricIslandConfig,
    ): Long {
        val first = config.musicInfoFirstLine
        val second = config.musicInfoSecondLine
        val hasTime = first.contains(LyricMusicInfoLayout.FIELD_ELAPSED) ||
            first.contains(LyricMusicInfoLayout.FIELD_REMAINING) ||
            second.contains(LyricMusicInfoLayout.FIELD_ELAPSED) ||
            second.contains(LyricMusicInfoLayout.FIELD_REMAINING)
        val hasPercent = first.contains(LyricMusicInfoLayout.FIELD_PROGRESS_PERCENT) ||
            second.contains(LyricMusicInfoLayout.FIELD_PROGRESS_PERCENT)
        var result = if (hasTime) playback.positionMs.coerceAtLeast(0L) / 1_000L else 0L
        if (hasPercent) {
            val duration = playback.durationMs
            val percent = if (duration > 0L) {
                kotlin.math.round(
                    (playback.positionMs.coerceAtLeast(0L).toDouble() / duration) * 100.0,
                ).toLong().coerceIn(0L, 100L)
            } else {
                -1L
            }
            result = result * 101L + percent + 1L
        }
        return result
    }

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
