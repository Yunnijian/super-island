package io.github.superisland.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.superisland.source.lyric.IslandContentMode
import io.github.superisland.source.lyric.LyricIslandConfig
import io.github.superisland.source.lyric.LyricIslandWidthPolicy
import io.github.superisland.source.lyric.LyricAlbumCoverStyle
import io.github.superisland.source.lyric.LyricMusicInfoLayout
import io.github.superisland.source.lyric.LyricPlaceholder
import io.github.superisland.source.lyric.LyricSourceMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.RangeSlider
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

private val sourceLabels = listOf("Lyricon", "SuperLyric", "LyricInfo")
private val sourceValues = listOf(LyricSourceMode.LYRICON, LyricSourceMode.SUPER_LYRIC, LyricSourceMode.LYRIC_INFO)
private val animationLabels = listOf(
    "无动画", "默认", "渐隐渐现", "向上渐隐＆向上渐现", "向下渐隐＆向下渐现",
    "向左渐隐＆右侧渐现", "向左渐隐＆向上渐现", "向左渐隐＆缩放渐现", "向左渐隐＆柔缓着陆",
    "向右渐隐＆左侧渐现", "向右渐隐＆向上渐现", "向右渐隐＆缩放渐现", "向右渐隐＆聚焦着陆",
    "向左渐隐＆右侧缩放渐现", "向右渐隐＆左侧缩放渐现", "左侧滑出＆右侧滑入",
    "左侧滑出＆向上渐现", "左侧滑出＆缩放渐现", "左侧滑出＆柔缓着陆", "右侧滑出＆左侧滑入",
    "右侧滑出＆向上渐现", "右侧滑出＆缩放渐现", "右侧滑出＆柔缓着陆", "X轴翻转", "Y轴翻转",
    "旋转", "缩放",
)
private val animationIds = listOf(
    "none", "default", "fade_out_fade_in", "fade_out_up_fade_in_up", "fade_out_down_fade_in_down",
    "fade_out_left_fade_in_right", "fade_out_left_fade_in_up", "fade_out_left_zoom_in", "fade_out_left_landing",
    "fade_out_right_fade_in_left", "fade_out_right_fade_in_up", "fade_out_right_zoom_in", "fade_out_right_landing",
    "fade_out_left_zoom_in_right", "fade_out_right_zoom_in_left", "slide_out_left_slide_in_right",
    "slide_out_left_fade_in_up", "slide_out_left_zoom_in", "slide_out_left_landing", "slide_out_right_slide_in_left",
    "slide_out_right_fade_in_up", "slide_out_right_zoom_in", "slide_out_right_landing", "flip_out_x_flip_in_x",
    "flip_out_y_flip_in_y", "rotate_out_rotate_in", "zoom_out_zoom_in",
)

private data class IntEditorSpec(
    val title: String,
    val label: String,
    val initialValue: Int,
    val min: Int,
    val max: Int,
    val onConfirm: (Int) -> Unit,
)

private data class FloatEditorSpec(
    val title: String,
    val label: String,
    val initialValue: Float,
    val min: Float,
    val max: Float,
    val onConfirm: (Float) -> Unit,
)

private fun <T> List<T>.safeIndex(value: T): Int = indexOf(value).takeIf { it >= 0 } ?: 0

@Composable
fun LyricConfigurationMiuix(
    config: LyricIslandConfig,
    onConfigChange: (LyricIslandConfig) -> Unit,
) {
    val set: (LyricIslandConfig) -> Unit = { onConfigChange(it.normalized()) }
    val support = rememberLyricSupportSnapshot()
    val sourceIndex = sourceValues.safeIndex(config.sourceMode)
    val animationIndex = animationIds.indexOf(config.animId).takeIf { config.animEnabled && it >= 0 } ?: 0
    val textColors = listOf("默认", "封面色", "封面渐变色", "跟随状态栏颜色")
    val placeholders = LyricPlaceholder.entries.map { it.display }
    val separatorOptions = listOf("加号（+）", "空格", "逗号（,）", "顿号（、）", "斜杠（/）", "横杠（-）", "不使用连接符")
    val separatorValues = listOf("plus", "space", "comma", "ideographic_comma", "slash", "hyphen", "none")
    val sizes = (8..16).map(Int::toString)
    val ratios = (10..100 step 10).map { "$it%" }
    val fades = (0..100 step 5).map(Int::toString)
    var showFontDialog by remember { mutableStateOf(false) }
    var fontPath by remember(config.customFontPath) {
        mutableStateOf(TextFieldValue(config.customFontPath))
    }
    val showAlbum = LyricIslandWidthPolicy.isAlbumCoverVisible(config.albumCoverStyle)
    val showRhythm = LyricIslandWidthPolicy.isMusicWaveVisible(config.musicWaveStyle)
    val islandWidthMin = LyricIslandWidthPolicy.minIslandWidth(showAlbum, showRhythm)
    val islandWidthMax = LyricIslandWidthPolicy.maxIslandWidth(showRhythm, config.disableWidthLimit)
        .coerceAtLeast(islandWidthMin)
    var dynamicWidthRange by remember(
        config.dynamicMinWidth,
        config.dynamicMaxWidth,
        islandWidthMin,
        islandWidthMax,
    ) {
        mutableStateOf(
            config.dynamicMinWidth.coerceIn(islandWidthMin, islandWidthMax).toFloat()..
                config.dynamicMaxWidth.coerceIn(islandWidthMin, islandWidthMax).toFloat(),
        )
    }
    var fixedIslandWidth by remember(config.rightContentMaxWidth, islandWidthMin, islandWidthMax) {
        mutableFloatStateOf(config.rightContentMaxWidth.coerceIn(islandWidthMin, islandWidthMax).toFloat())
    }
    var editingFieldRow by remember { mutableStateOf<Int?>(null) }
    var editingPaddingSide by remember { mutableStateOf<Int?>(null) }
    var showIslandWidthDialog by remember { mutableStateOf(false) }
    var intEditor by remember { mutableStateOf<IntEditorSpec?>(null) }
    var floatEditor by remember { mutableStateOf<FloatEditorSpec?>(null) }

    val editingFields = when (editingFieldRow) {
        0 -> LyricMusicInfoLayout.parseFields(
            config.musicInfoFirstLine,
            LyricMusicInfoLayout.FIELD_TITLE,
        )
        1 -> LyricMusicInfoLayout.parseFields(
            config.musicInfoSecondLine,
            LyricMusicInfoLayout.FIELD_ARTIST,
        )
        else -> emptyList()
    }
    FieldOrderDialog(
        show = editingFieldRow != null,
        title = if (editingFieldRow == 1) "第二行" else "第一行",
        initialFields = editingFields,
        requireSelection = editingFieldRow == 0,
        onDismiss = { editingFieldRow = null },
        onConfirm = { fields ->
            when (editingFieldRow) {
                0 -> set(
                    config.copy(
                        musicInfoFirstLine = LyricMusicInfoLayout.encodeFields(
                            fields,
                            LyricMusicInfoLayout.FIELD_TITLE,
                        ),
                    ),
                )
                1 -> set(
                    config.copy(
                        musicInfoSecondLine = LyricMusicInfoLayout.encodeFields(
                            fields,
                            LyricMusicInfoLayout.FIELD_ARTIST,
                        ),
                    ),
                )
            }
            editingFieldRow = null
        },
    )
    val paddingSide = editingPaddingSide
    val paddingLeft = when (paddingSide) {
        0 -> config.leftPaddingLeft
        1 -> config.rightPaddingLeft
        else -> 0
    }
    val paddingRight = when (paddingSide) {
        0 -> config.leftPaddingRight
        1 -> config.rightPaddingRight
        else -> 0
    }
    PaddingEditorDialog(
        show = paddingSide != null,
        title = if (paddingSide == 1) "右侧内容内边距" else "左侧内容内边距",
        initialLeft = paddingLeft,
        initialRight = paddingRight,
        onDismiss = { editingPaddingSide = null },
        onConfirm = { left, right ->
            set(
                if (paddingSide == 1) {
                    config.copy(rightPaddingLeft = left, rightPaddingRight = right)
                } else {
                    config.copy(leftPaddingLeft = left, leftPaddingRight = right)
                },
            )
            editingPaddingSide = null
        },
    )
    NumberInputDialog(
        show = showIslandWidthDialog,
        title = "超级岛长度",
        label = "范围：$islandWidthMin ~ $islandWidthMax",
        initialValue = config.rightContentMaxWidth,
        min = islandWidthMin,
        max = islandWidthMax,
        onDismiss = { showIslandWidthDialog = false },
        onConfirm = { value ->
            set(config.copy(rightContentMaxWidth = value))
            showIslandWidthDialog = false
        },
    )
    intEditor?.let { editor ->
        NumberInputDialog(
            show = true,
            title = editor.title,
            label = editor.label,
            initialValue = editor.initialValue,
            min = editor.min,
            max = editor.max,
            onDismiss = { intEditor = null },
            onConfirm = { value ->
                editor.onConfirm(value)
                intEditor = null
            },
        )
    }
    floatEditor?.let { editor ->
        FloatInputDialog(
            show = true,
            title = editor.title,
            label = editor.label,
            initialValue = editor.initialValue,
            min = editor.min,
            max = editor.max,
            onDismiss = { floatEditor = null },
            onConfirm = { value ->
                editor.onConfirm(value)
                floatEditor = null
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SmallTitle(text = "小米超级岛歌词自定义配置")
        Card {
            OverlayDropdownPreference(
                title = "歌词源",
                items = sourceLabels,
                selectedIndex = sourceIndex,
                onSelectedIndexChange = { set(config.copy(sourceMode = sourceValues.getOrElse(it) { LyricSourceMode.LYRICON })) },
            )
            if (config.sourceMode == LyricSourceMode.LYRICON) {
                SmallTitle(text = "歌词提供器")
                if (support.loaded && support.lyriconProviders.isEmpty()) {
                    Text(text = "未发现 Lyricon 歌词提供器")
                }
                support.lyriconProviders.forEach { provider ->
                    val delay = config.lyriconProviderDelays[provider.packageName]
                        ?: config.lyriconProviderDelayMs
                    Text(
                        text = "${provider.label}  v${provider.versionName}" +
                            provider.providerAuthor.orEmpty().takeIf { it.isNotBlank() }?.let { "，作者 $it" }.orEmpty(),
                    )
                    OverlayDropdownPreference(
                        title = "歌词时间偏移",
                        summary = buildString {
                            append("v").append(provider.versionName)
                            provider.providerAuthor?.takeIf { it.isNotBlank() }?.let {
                                append("，作者 ").append(it)
                            }
                            provider.providerCategory?.takeIf { it.isNotBlank() }?.let {
                                append("，").append(it)
                            }
                            append("；正数延后显示，负数提前显示")
                        },
                        items = (-5000..5000 step 50).map { "${it}ms" },
                        selectedIndex = ((delay + 5000) / 50).coerceIn(0, 200),
                        onSelectedIndexChange = {
                            val next = config.lyriconProviderDelays.toMutableMap()
                            next[provider.packageName] = -5000 + it * 50
                            set(config.copy(lyriconProviderDelays = next))
                        },
                    )
                }
            } else if (config.sourceMode == LyricSourceMode.SUPER_LYRIC) {
                if (!support.superLyricInstalled) {
                    SmallTitle(text = "未安装 SuperLyric")
                    Text(text = "请安装并启用 SuperLyric 模块以使用该歌词源")
                }
                SmallTitle(text = "API 支持列表")
                if (support.superLyricApiApps.isEmpty() && support.loaded) {
                    Text(text = "未发现 SuperLyric 支持的应用")
                }
                support.superLyricApiApps.forEach { app ->
                    Text(text = "${app.label}  v${app.versionName} (${app.versionCode})")
                }
                SmallTitle(text = "Hook 支持列表")
                if (support.superLyricHookApps.isEmpty() && support.loaded) {
                    Text(text = "未发现 SuperLyric 支持的应用")
                }
                support.superLyricHookApps.forEach { app ->
                    Text(text = "${app.label}  v${app.versionName} (${app.versionCode})")
                }
            }
        }

        SmallTitle(text = "布局")
        Card {
            OverlayDropdownPreference(
                title = "超级岛长度模式",
                items = listOf("固定长度", "动态长度"),
                selectedIndex = config.widthMode.coerceIn(0, 1),
                onSelectedIndexChange = { set(config.copy(widthMode = it.coerceIn(0, 1))) },
            )
            if (config.widthMode == 1) {
                OverlayDropdownPreference(
                    title = "动态长度判断基准",
                    items = listOf("综合判断", "仅歌词"),
                    selectedIndex = config.dynamicWidthBasis.coerceIn(0, 1),
                    onSelectedIndexChange = { set(config.copy(dynamicWidthBasis = it.coerceIn(0, 1))) },
                )
            }
            ArrowPreference(
                title = "超级岛长度",
                summary = if (config.widthMode == 1) {
                    "${dynamicWidthRange.start.toInt()}~${dynamicWidthRange.endInclusive.toInt()}"
                } else {
                    config.rightContentMaxWidth.toString()
                },
                bottomAction = {
                    if (config.widthMode == 1) {
                        RangeSlider(
                            value = dynamicWidthRange,
                            onValueChange = { value ->
                                val start = value.start.toInt().coerceIn(islandWidthMin, islandWidthMax)
                                val end = value.endInclusive.toInt().coerceIn(start, islandWidthMax)
                                dynamicWidthRange = start.toFloat()..end.toFloat()
                            },
                            valueRange = islandWidthMin.toFloat()..islandWidthMax.toFloat(),
                            steps = 0,
                            showKeyPoints = true,
                            keyPoints = (islandWidthMin..islandWidthMax step 20).map(Int::toFloat),
                            magnetThreshold = 0f,
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                            onValueChangeFinished = {
                                set(
                                    config.copy(
                                        dynamicMinWidth = dynamicWidthRange.start.toInt(),
                                        dynamicMaxWidth = dynamicWidthRange.endInclusive.toInt(),
                                    ),
                                )
                            },
                        )
                    } else {
                        Slider(
                            value = fixedIslandWidth,
                            onValueChange = { value ->
                                fixedIslandWidth = value.coerceIn(islandWidthMin.toFloat(), islandWidthMax.toFloat())
                            },
                            valueRange = islandWidthMin.toFloat()..islandWidthMax.toFloat(),
                            steps = 0,
                            showKeyPoints = true,
                            keyPoints = (islandWidthMin..islandWidthMax step 20).map(Int::toFloat),
                            magnetThreshold = 0f,
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                            onValueChangeFinished = {
                                set(config.copy(rightContentMaxWidth = fixedIslandWidth.toInt()))
                            },
                        )
                    }
                },
                onClick = {
                    if (config.widthMode == 0) showIslandWidthDialog = true
                },
            )
        }
        Card {
            SwitchPreference(
                title = "解除超级岛最大长度限制",
                checked = config.disableWidthLimit,
                onCheckedChange = { set(config.copy(disableWidthLimit = it)) },
            )
        }
        Card {
            ArrowPreference(
                title = "左侧内容内边距",
                summary = "${config.leftPaddingLeft},${config.leftPaddingRight}",
                onClick = { editingPaddingSide = 0 },
            )
            ArrowPreference(
                title = "右侧内容内边距",
                summary = "${config.rightPaddingLeft},${config.rightPaddingRight}",
                onClick = { editingPaddingSide = 1 },
            )
        }

        SmallTitle(text = "内容")
        Card {
            SwitchPreference(
                title = "分离歌词",
                checked = config.lyricMode == 1,
                onCheckedChange = { set(config.copy(lyricMode = if (it) 1 else 0)) },
            )
        }
        Card {
            OverlayDropdownPreference(
                title = "音频封面",
                items = LyricAlbumCoverStyle.pickerLabels,
                selectedIndex = LyricAlbumCoverStyle.pickerIndex(config.albumCoverStyle),
                onSelectedIndexChange = {
                    set(config.copy(albumCoverStyle = LyricAlbumCoverStyle.pickerValues.getOrElse(it) {
                        LyricAlbumCoverStyle.DEFAULT
                    }))
                },
            )
            if (config.lyricMode == 0) {
                OverlayDropdownPreference(
                    title = "超级岛左侧",
                    items = listOf("无内容", "音乐信息", "歌词"),
                    selectedIndex = when (config.contentLeft) { IslandContentMode.NONE -> 0; IslandContentMode.MUSIC_INFO -> 1; IslandContentMode.LYRIC -> 2 },
                    onSelectedIndexChange = { index -> set(config.copy(contentLeft = listOf(IslandContentMode.NONE, IslandContentMode.MUSIC_INFO, IslandContentMode.LYRIC).getOrElse(index) { IslandContentMode.MUSIC_INFO })) },
                )
                OverlayDropdownPreference(
                    title = "超级岛右侧",
                    items = listOf("无内容", "音乐信息", "歌词"),
                    selectedIndex = when (config.contentRight) { IslandContentMode.NONE -> 0; IslandContentMode.MUSIC_INFO -> 1; IslandContentMode.LYRIC -> 2 },
                    onSelectedIndexChange = { index -> set(config.copy(contentRight = listOf(IslandContentMode.NONE, IslandContentMode.MUSIC_INFO, IslandContentMode.LYRIC).getOrElse(index) { IslandContentMode.LYRIC })) },
                )
            }
            OverlayDropdownPreference(
                title = "音频律动",
                items = listOf("默认", "封面色", "封面渐变色", "隐藏"),
                selectedIndex = config.musicWaveStyle.coerceIn(0, 3),
                onSelectedIndexChange = { set(config.copy(musicWaveStyle = it)) },
            )
        }

        SmallTitle(text = "内容布局")
        SmallTitle(text = "音乐信息")
        Card {
            ArrowPreference(
                title = "第一行",
                summary = summarizeFields(config.musicInfoFirstLine, LyricMusicInfoLayout.FIELD_TITLE),
                onClick = { editingFieldRow = 0 },
            )
            ArrowPreference(
                title = "第二行",
                summary = summarizeFields(config.musicInfoSecondLine, LyricMusicInfoLayout.FIELD_ARTIST),
                onClick = { editingFieldRow = 1 },
            )
            OverlayDropdownPreference(
                title = "字段连接符",
                summary = "选择多个字段之间的拼接方式",
                items = separatorOptions,
                selectedIndex = separatorValues.safeIndex(config.musicInfoSeparator),
                onSelectedIndexChange = { set(config.copy(musicInfoSeparator = separatorValues.getOrElse(it) { "hyphen" })) },
            )
            SwitchPreference(title = "居中显示", checked = config.centerMusicInfo, onCheckedChange = { set(config.copy(centerMusicInfo = it)) })
        }
        SmallTitle(text = "歌词")
        Card {
            SwitchPreference(title = "居中显示", checked = config.centerLyric, enabled = !config.rightLyric, onCheckedChange = { set(config.copy(centerLyric = it, rightLyric = if (it) false else config.rightLyric)) })
            SwitchPreference(title = "歌词右对齐", checked = config.rightLyric, enabled = !config.centerLyric, onCheckedChange = { set(config.copy(rightLyric = it, centerLyric = if (it) false else config.centerLyric)) })
            OverlayDropdownPreference(title = "占位符格式", items = placeholders, selectedIndex = config.placeholder.ordinal, onSelectedIndexChange = { set(config.copy(placeholder = LyricPlaceholder.entries.getOrElse(it) { LyricPlaceholder.COUNTDOWN })) })
        }

        SmallTitle(text = "文字样式")
        Card {
            SmallTitle(text = "基础样式")
            ArrowPreference(
                title = "大小",
                summary = config.textSizeSp.toInt().toString(),
                onClick = {
                    intEditor = IntEditorSpec("大小", "范围：8 ~ 16", config.textSizeSp.toInt(), 8, 16) {
                        set(config.copy(textSizeSp = it.toFloat()))
                    }
                },
            )
            ArrowPreference(
                title = "多行模式下文字大小比例",
                summary = "${(config.textSizeRatio * 100).toInt()}%",
                onClick = {
                    intEditor = IntEditorSpec("多行模式下文字大小比例", "范围：10 ~ 100", (config.textSizeRatio * 100).toInt(), 10, 100) {
                        set(config.copy(textSizeRatio = it / 100f))
                    }
                },
            )
            ArrowPreference(
                title = "羽化边缘长度",
                summary = config.fadingEdgeLengthDp.toString(),
                onClick = {
                    intEditor = IntEditorSpec("羽化边缘长度", "范围：0 ~ 100", config.fadingEdgeLengthDp, 0, 100) {
                        set(config.copy(fadingEdgeLengthDp = it))
                    }
                },
            )
            OverlayDropdownPreference(title = "文字颜色", items = textColors, selectedIndex = config.textColorStyle.coerceIn(0, 3), onSelectedIndexChange = { set(config.copy(textColorStyle = it)) })
        }
        Card {
            SmallTitle(text = "字体样式")
            OverlayDropdownPreference(
                title = "字体",
                items = listOf(if (config.customFontPath.isBlank()) "默认" else config.customFontPath),
                selectedIndex = 0,
                onSelectedIndexChange = { showFontDialog = true },
            )
            SwitchPreference(title = "英数窄字体", checked = config.narrowLatinFont, onCheckedChange = { set(config.copy(narrowLatinFont = it)) })
            ArrowPreference(
                title = "字重",
                summary = config.fontWeight.toString(),
                onClick = {
                    intEditor = IntEditorSpec("字重", "范围：100 ~ 900", config.fontWeight, 100, 900) {
                        set(config.copy(fontWeight = it))
                    }
                },
            )
            SwitchPreference(title = "斜体", checked = config.fontItalic, onCheckedChange = { set(config.copy(fontItalic = it)) })
        }

        SmallTitle(text = "滚动显示")
        SmallTitle(text = "歌词滚动")
        Card {
            SwitchPreference(title = "歌词滚动", summary = "针对没有时间轴的歌词", checked = config.marqueeMode, onCheckedChange = { set(config.copy(marqueeMode = it)) })
            ArrowPreference(
                title = "滚动速度",
                summary = config.marqueeSpeed.toString(),
                enabled = config.marqueeMode,
                onClick = {
                    intEditor = IntEditorSpec("滚动速度", "范围：5 ~ 100", config.marqueeSpeed, 5, 100) {
                        set(config.copy(marqueeSpeed = it))
                    }
                },
            )
            ArrowPreference(
                title = "初始滚动延迟",
                summary = "${config.marqueeDelay}ms",
                enabled = config.marqueeMode,
                onClick = {
                        intEditor = IntEditorSpec("初始滚动延迟", "ms 范围：0 ~ 10000", config.marqueeDelay, 0, 10000) {
                        set(config.copy(marqueeDelay = it))
                    }
                },
            )
            SwitchPreference(title = "无限循环", enabled = config.marqueeMode, checked = config.marqueeInfinite, onCheckedChange = { set(config.copy(marqueeInfinite = it)) })
            ArrowPreference(
                title = "循环间隔",
                summary = "${config.marqueeLoopDelay}ms",
                enabled = config.marqueeMode,
                onClick = {
                        intEditor = IntEditorSpec("循环间隔", "ms 范围：0 ~ 10000", config.marqueeLoopDelay, 0, 10000) {
                        set(config.copy(marqueeLoopDelay = it))
                    }
                },
            )
            SwitchPreference(title = "结束时在末尾停止", enabled = config.marqueeMode, checked = config.marqueeStopEnd, onCheckedChange = { set(config.copy(marqueeStopEnd = it)) })
        }
        if (config.lyricMode == 0) {
            SmallTitle(text = "歌曲信息滚动")
            Card {
                SwitchPreference(title = "歌曲信息滚动", summary = "针对歌曲信息", checked = config.metadataMarqueeMode, onCheckedChange = { set(config.copy(metadataMarqueeMode = it)) })
                ArrowPreference(
                    title = "滚动速度",
                    summary = config.metadataMarqueeSpeed.toString(),
                    enabled = config.metadataMarqueeMode,
                    onClick = {
                        intEditor = IntEditorSpec("滚动速度", "范围：5 ~ 100", config.metadataMarqueeSpeed, 5, 100) {
                            set(config.copy(metadataMarqueeSpeed = it))
                        }
                    },
                )
                ArrowPreference(
                    title = "初始滚动延迟",
                    summary = "${config.metadataMarqueeDelay}ms",
                    enabled = config.metadataMarqueeMode,
                    onClick = {
                        intEditor = IntEditorSpec("初始滚动延迟", "ms 范围：0 ~ 10000", config.metadataMarqueeDelay, 0, 10000) {
                            set(config.copy(metadataMarqueeDelay = it))
                        }
                    },
                )
                SwitchPreference(title = "无限循环", enabled = config.metadataMarqueeMode, checked = config.metadataMarqueeInfinite, onCheckedChange = { set(config.copy(metadataMarqueeInfinite = it)) })
                ArrowPreference(
                    title = "循环间隔",
                    summary = "${config.metadataMarqueeLoopDelay}ms",
                    enabled = config.metadataMarqueeMode,
                    onClick = {
                        intEditor = IntEditorSpec("循环间隔", "ms 范围：0 ~ 10000", config.metadataMarqueeLoopDelay, 0, 10000) {
                            set(config.copy(metadataMarqueeLoopDelay = it))
                        }
                    },
                )
            }
        }

        SmallTitle(text = "逐字歌词")
        Card {
            SwitchPreference(title = "模拟逐行歌词", summary = "将带有字词时间轴的歌词降级为整行时间轴显示", checked = config.syllableLineDisplay, onCheckedChange = { set(config.copy(syllableLineDisplay = it)) })
            SwitchPreference(title = "相对进度歌词", summary = "当歌词缺少字词时间轴时，将整行作为一个进度单元", checked = config.syllableRelative, onCheckedChange = { set(config.copy(syllableRelative = it)) })
            SwitchPreference(title = "模拟逐字歌词", enabled = config.syllableRelative, checked = config.syllableHighlight, onCheckedChange = { set(config.copy(syllableHighlight = it)) })
        }
        SmallTitle(text = "歌词动效")
        Card {
            SwitchPreference(title = "羽化进度样式", checked = config.gradientProgressStyle, onCheckedChange = { set(config.copy(gradientProgressStyle = it)) })
            SwitchPreference(title = "逐字字词上浮动画", checked = config.wordMotionEnabled, onCheckedChange = { set(config.copy(wordMotionEnabled = it)) })
            if (config.wordMotionEnabled) {
                SwitchPreference(title = "拉丁文字逐字母上浮", summary = "关闭时按整个单词上浮", checked = config.wordMotionLatinByCharacter, onCheckedChange = { set(config.copy(wordMotionLatinByCharacter = it)) })
                ArrowPreference(
                    title = "中日韩上浮系数",
                    summary = "%.2f".format(java.util.Locale.ROOT, config.wordMotionCjkLift),
                    onClick = {
                        floatEditor = FloatEditorSpec("中日韩上浮系数", "范围：0 ~ 0.2", config.wordMotionCjkLift, 0f, 0.2f) {
                            set(config.copy(wordMotionCjkLift = it))
                        }
                    },
                )
                ArrowPreference(
                    title = "中日韩波长系数",
                    summary = "%.2f".format(java.util.Locale.ROOT, config.wordMotionCjkWave),
                    onClick = {
                        floatEditor = FloatEditorSpec("中日韩波长系数", "范围：0 ~ 8", config.wordMotionCjkWave, 0f, 8f) {
                            set(config.copy(wordMotionCjkWave = it))
                        }
                    },
                )
                ArrowPreference(
                    title = "拉丁文字上浮系数",
                    summary = "%.2f".format(java.util.Locale.ROOT, config.wordMotionLatinLift),
                    onClick = {
                        floatEditor = FloatEditorSpec("拉丁文字上浮系数", "范围：0 ~ 0.2", config.wordMotionLatinLift, 0f, 0.2f) {
                            set(config.copy(wordMotionLatinLift = it))
                        }
                    },
                )
                ArrowPreference(
                    title = "拉丁文字波长系数",
                    summary = "%.2f".format(java.util.Locale.ROOT, config.wordMotionLatinWave),
                    onClick = {
                        floatEditor = FloatEditorSpec("拉丁文字波长系数", "范围：0 ~ 8", config.wordMotionLatinWave, 0f, 8f) {
                            set(config.copy(wordMotionLatinWave = it))
                        }
                    },
                )
            }
        }

        SmallTitle(text = "双行内容")
        val nextSupported = config.sourceMode == LyricSourceMode.LYRICON ||
            config.sourceMode == LyricSourceMode.LYRIC_INFO
        if (nextSupported) {
            SmallTitle(text = "下一句歌词")
            Card {
                SwitchPreference(title = "显示下一句歌词", summary = "占用第二行，开启后不显示翻译", checked = config.nextLyricLine, onCheckedChange = { set(config.copy(nextLyricLine = it)) })
                SwitchPreference(title = "自动切换翻译", summary = "有翻译的歌曲，显示歌词翻译", enabled = config.nextLyricLine, checked = config.autoSwitchTranslation, onCheckedChange = { set(config.copy(autoSwitchTranslation = it)) })
            }
        }
        SmallTitle(text = "翻译")
        Card {
            val translationEnabled = !nextSupported || !config.nextLyricLine || config.autoSwitchTranslation
            SwitchPreference(title = "禁用所有翻译", enabled = translationEnabled, checked = config.disableTranslation, onCheckedChange = { set(config.copy(disableTranslation = it)) })
            SwitchPreference(title = "仅显示翻译", enabled = translationEnabled && !config.swapTranslation, checked = config.translationOnly, onCheckedChange = { set(config.copy(translationOnly = it, swapTranslation = if (it) false else config.swapTranslation)) })
            SwitchPreference(title = "原词翻译对调位置", enabled = translationEnabled && !config.translationOnly, checked = config.swapTranslation, onCheckedChange = { set(config.copy(swapTranslation = it, translationOnly = if (it) false else config.translationOnly)) })
        }

        SmallTitle(text = "歌词切换动画")
        Card {
            OverlayDropdownPreference(title = "歌词切换动画", items = animationLabels, selectedIndex = animationIndex, onSelectedIndexChange = { index -> set(config.copy(animEnabled = index != 0, animId = animationIds.getOrElse(index) { "default" })) })
        }
    }

    if (showFontDialog) {
        OverlayDialog(
            title = "字体",
            summary = "输入字体文件完整路径",
            show = true,
            onDismissRequest = { showFontDialog = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = fontPath,
                    onValueChange = { fontPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "字体文件路径",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { showFontDialog = false }) { Text("取消") }
                    Button(onClick = {
                        set(config.copy(customFontPath = fontPath.text.trim()))
                        showFontDialog = false
                    }) { Text("确定") }
                }
            }
        }
    }
}

private fun summarizeFields(raw: String, defaultField: String): String =
    LyricMusicInfoLayout.parseFields(raw, defaultField)
        .map { LyricMusicInfoLayout.fieldLabels[it] ?: it }
        .joinToString(" - ")
        .ifBlank { "未选择字段" }

private fun <T> List<T>.moveItem(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Numeric fixed-width editor copied from HyperLyric's Super Island settings. */
@Composable
private fun NumberInputDialog(
    show: Boolean,
    title: String,
    label: String,
    initialValue: Int,
    min: Int,
    max: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    if (!show) return
    var inputValue by remember(initialValue) { mutableStateOf(initialValue.toString()) }
    OverlayDialog(
        title = title,
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = inputValue,
                onValueChange = { newValue ->
                    if (newValue.all(Char::isDigit)) inputValue = newValue
                },
                label = label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                maxLines = 1,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                ) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.weight(0.1f))
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        inputValue.toIntOrNull()?.let { onConfirm(it.coerceIn(min, max)) }
                    },
                ) {
                    Text("确定")
                }
            }
        }
    }
}

/** Decimal editor used by HyperLyric's word-motion coefficient settings. */
@Composable
private fun FloatInputDialog(
    show: Boolean,
    title: String,
    label: String,
    initialValue: Float,
    min: Float,
    max: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    if (!show) return
    var inputValue by remember(initialValue) { mutableStateOf(initialValue.toString()) }
    OverlayDialog(
        title = title,
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = inputValue,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() || it == '.' }) inputValue = newValue
                },
                label = label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                maxLines = 1,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Spacer(modifier = Modifier.weight(0.1f))
                Button(
                    onClick = {
                        inputValue.toFloatOrNull()?.let { onConfirm(it.coerceIn(min, max)) }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("确定") }
            }
        }
    }
}

/** HyperLyric's ordered checkbox editor, kept as a compact Miuix dialog. */
@Composable
private fun FieldOrderDialog(
    show: Boolean,
    title: String,
    initialFields: List<String>,
    requireSelection: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    if (!show) return
    val initialOrder = remember(initialFields) {
        initialFields + LyricMusicInfoLayout.fieldOrder.filterNot { it in initialFields }
    }
    var order by remember(initialOrder) { mutableStateOf(initialOrder) }
    var selected by remember(initialOrder) { mutableStateOf(initialFields.toSet()) }
    OverlayDialog(
        title = title,
        summary = "选择要显示的音乐信息字段，并调整顺序",
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            order.forEachIndexed { index, field ->
                SwitchPreference(
                    title = LyricMusicInfoLayout.fieldLabels[field] ?: field,
                    checked = field in selected,
                    onCheckedChange = { checked ->
                        if (checked || !requireSelection || selected.size > 1) {
                            selected = if (checked) selected + field else selected - field
                        }
                    },
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        enabled = index > 0,
                        onClick = { order = order.moveItem(index, index - 1) },
                    ) { Text("上移") }
                    Button(
                        enabled = index < order.lastIndex,
                        onClick = { order = order.moveItem(index, index + 1) },
                    ) { Text("下移") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    val fields = order.filter { it in selected }
                    if (!requireSelection || fields.isNotEmpty()) onConfirm(fields)
                }) { Text("确定") }
            }
        }
    }
}

@Composable
private fun PaddingEditorDialog(
    show: Boolean,
    title: String,
    initialLeft: Int,
    initialRight: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    if (!show) return
    var left by remember(title, initialLeft) { mutableStateOf(TextFieldValue(initialLeft.toString())) }
    var right by remember(title, initialRight) { mutableStateOf(TextFieldValue(initialRight.toString())) }
    OverlayDialog(
        title = title,
        summary = "分别设置左、右内边距（dp）",
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = left,
                onValueChange = { left = it },
                modifier = Modifier.fillMaxWidth(),
                label = "左侧",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            TextField(
                value = right,
                onValueChange = { right = it },
                modifier = Modifier.fillMaxWidth(),
                label = "右侧",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = {
                    onConfirm(
                        left.text.toIntOrNull()?.coerceIn(-50, 100) ?: initialLeft,
                        right.text.toIntOrNull()?.coerceIn(-50, 100) ?: initialRight,
                    )
                }) { Text("确定") }
            }
        }
    }
}
