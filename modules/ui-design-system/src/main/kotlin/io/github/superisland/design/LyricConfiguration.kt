package io.github.superisland.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import io.github.superisland.source.lyric.IslandContentMode
import io.github.superisland.source.lyric.LyricIslandConfig
import io.github.superisland.source.lyric.LyricIslandWidthPolicy
import io.github.superisland.source.lyric.LyricAlbumCoverStyle
import io.github.superisland.source.lyric.LyricMusicInfoLayout
import io.github.superisland.source.lyric.LyricPlaceholder
import io.github.superisland.source.lyric.LyricSourceMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

// MEDIA_FALLBACK is retained only for decoding old configurations. HyperLyric's basic source
// picker exposes the three real providers, so the compatibility value must never be selectable.
private val sourceLabels = listOf("Lyricon", "SuperLyric", "LyricInfo")
private val sourceValues = listOf(
    LyricSourceMode.LYRICON,
    LyricSourceMode.SUPER_LYRIC,
    LyricSourceMode.LYRIC_INFO,
)
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

@Composable
private fun LyricInlineSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    valueLabel: String,
    onValueChangeFinished: (Float) -> Unit,
) {
    var sliderValue by remember(title, value, valueRange.start, valueRange.endInclusive) {
        mutableFloatStateOf(value.coerceIn(valueRange.start, valueRange.endInclusive))
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title)
            Text(valueLabel)
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            valueRange = valueRange,
            steps = 0,
            enabled = enabled,
            onValueChangeFinished = { onValueChangeFinished(sliderValue) },
            showKeyPoints = true,
            keyPoints = lyricSliderKeyPoints(valueRange),
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            magnetThreshold = 0f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun lyricSliderKeyPoints(valueRange: ClosedFloatingPointRange<Float>): List<Float> {
    val start = valueRange.start
    val end = valueRange.endInclusive
    if (start == -50f && end == 100f) return listOf(-50f, 0f, 50f, 100f)
    return listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        .map { start + (end - start) * it }
}

@Composable
private fun LyricInlineDualSliderRow(
    title: String,
    first: Int,
    second: Int,
    enabled: Boolean,
    onValueChangeFinished: (Int, Int) -> Unit,
) {
    var firstValue by remember(title, first) { mutableFloatStateOf(first.coerceIn(-50, 100).toFloat()) }
    var secondValue by remember(title, second) { mutableFloatStateOf(second.coerceIn(-50, 100).toFloat()) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("左边距 ${firstValue.roundToInt()}", modifier = Modifier.padding(end = 8.dp))
            Slider(
                value = firstValue,
                onValueChange = { firstValue = it },
                onValueChangeFinished = {
                    onValueChangeFinished(firstValue.roundToInt(), secondValue.roundToInt())
                },
                valueRange = -50f..100f,
                steps = 0,
                enabled = enabled,
                showKeyPoints = true,
                keyPoints = lyricSliderKeyPoints(-50f..100f),
                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                magnetThreshold = 0f,
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("右边距 ${secondValue.roundToInt()}", modifier = Modifier.padding(end = 8.dp))
            Slider(
                value = secondValue,
                onValueChange = { secondValue = it },
                onValueChangeFinished = {
                    onValueChangeFinished(firstValue.roundToInt(), secondValue.roundToInt())
                },
                valueRange = -50f..100f,
                steps = 0,
                enabled = enabled,
                showKeyPoints = true,
                keyPoints = lyricSliderKeyPoints(-50f..100f),
                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                magnetThreshold = 0f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LyricSettingsTitle(text: String) {
    SmallTitle(
        text = text,
        insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
    )
}

@Composable
private fun LyricSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), content = content)
}

private val sourceCardModifier =
    Modifier
        .padding(horizontal = 12.dp)
        .padding(bottom = 12.dp)
        .fillMaxWidth()

@Composable
private fun LyricSourcePromptCard(source: LyricSourceMode) {
    var dismissed by remember(source) { mutableStateOf(false) }
    AnimatedVisibility(
        visible = !dismissed,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Card(
            modifier = sourceCardModifier,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.tertiaryContainer,
                contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = source.sourcePromptSummary(),
                    color = MiuixTheme.colorScheme.onTertiaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                IconButton(
                    onClick = { dismissed = true },
                    minWidth = 16.dp,
                    minHeight = 16.dp,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Demibold.Close,
                        contentDescription = "关闭",
                        tint = MiuixTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.height(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricSupportAppCard(app: LyricSupportApp) {
    var expanded by remember(app.packageName) { mutableStateOf(false) }
    Card(
        modifier = sourceCardModifier,
        onClick = { expanded = !expanded },
        showIndication = false,
    ) {
        BasicComponent(
            title = app.label,
            summary = app.displaySummary(),
            startAction = { LyricSupportAppIcon(app) },
        )
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BasicComponent(
                    summary = app.usage,
                    insideMargin = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 0.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricSupportAppIcon(app: LyricSupportApp) {
    val icon = rememberLyricSupportAppIcon(app)
    val iconModifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = iconModifier)
    } else {
        Box(
            modifier = iconModifier.background(
                MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f),
            ),
        )
    }
}

/** Entry pages mirror HyperLyric's compact settings surface. */
enum class LyricConfigSection(val title: String) {
    MEDIA_CARD("媒体卡片"),
    SOURCE("歌词源"),
    ISLAND("超级岛"),
    CONTENT_LAYOUT("内容布局"),
    TEXT_STYLE("文字样式"),
    SCROLL("滚动显示"),
    VERBATIM("逐字歌词"),
    TRANSLATION("翻译"),
    ANIMATION("歌词切换动画"),
    PROVIDER("歌词提供器"),
}

@Composable
fun LyricSectionEntryPageMiuix(
    config: LyricIslandConfig,
    enabled: Boolean,
    onSectionSelected: (LyricConfigSection) -> Unit,
) {
    LyricSettingsTitle(text = "自定义配置")
    Card(modifier = Modifier.fillMaxWidth()) {
        LyricConfigSection.entries
            .filter { it != LyricConfigSection.PROVIDER || config.sourceMode == LyricSourceMode.LYRICON }
            .forEach { section ->
            val summary = lyricSectionSummary(section, config)
            if (summary == null) {
                ArrowPreference(
                    title = section.title,
                    enabled = enabled,
                    onClick = { onSectionSelected(section) },
                )
            } else {
                ArrowPreference(
                    title = section.title,
                    summary = summary,
                    enabled = enabled,
                    onClick = { onSectionSelected(section) },
                )
            }
        }
    }
}

private fun lyricSectionSummary(section: LyricConfigSection, config: LyricIslandConfig): String? = when (section) {
    LyricConfigSection.MEDIA_CARD -> "通知中心、超级岛、息屏显示"
    LyricConfigSection.SOURCE -> when (config.sourceMode) {
        LyricSourceMode.LYRICON -> "Lyricon"
        LyricSourceMode.SUPER_LYRIC -> "SuperLyric"
        LyricSourceMode.LYRIC_INFO -> "LyricInfo"
        // Old builds persisted MEDIA_FALLBACK. Keep it out of the picker while showing the
        // nearest public provider label instead of leaking an implementation-only option.
        LyricSourceMode.MEDIA_FALLBACK -> "LyricInfo"
    }
    LyricConfigSection.ISLAND -> if (config.widthMode == 1) "动态长度" else "固定长度"
    LyricConfigSection.CONTENT_LAYOUT -> null
    LyricConfigSection.PROVIDER -> "Lyricon 歌词提供器与时间偏移"
    LyricConfigSection.TEXT_STYLE,
    LyricConfigSection.SCROLL,
    LyricConfigSection.VERBATIM,
    LyricConfigSection.ANIMATION,
    -> null
    LyricConfigSection.TRANSLATION -> "歌词翻译、下一句歌词等功能"
}

private fun <T> List<T>.safeIndex(value: T): Int = indexOf(value).takeIf { it >= 0 } ?: 0

@Composable
fun LyricConfigurationMiuix(
    config: LyricIslandConfig,
    onConfigChange: (LyricIslandConfig) -> Unit,
    section: LyricConfigSection? = null,
    enabled: Boolean = true,
) {
    // Keep the detail page readable while making every mutation obey the feature master switch.
    // The caller still owns persistence/rollback; this guard prevents disabled edits from reaching
    // that owner even for controls whose visual component has no enabled parameter in older Miuix.
    val set: (LyricIslandConfig) -> Unit = { if (enabled) onConfigChange(it.normalized()) }
    val support = rememberLyricSupportSnapshot()
    val sourceIndex = sourceValues.safeIndex(config.sourceMode.publicPickerMode())
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
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (section == LyricConfigSection.SOURCE) 0.dp else 12.dp),
    ) {
        if (section == LyricConfigSection.MEDIA_CARD) {
            MediaCardConfigurationMiuix(
                config = config,
                enabled = enabled,
                onConfigChange = set,
            )
        }
        if (section == null || section == LyricConfigSection.SOURCE) {
            Card(modifier = sourceCardModifier) {
                OverlayDropdownPreference(
                    title = "歌词源",
                    items = sourceLabels,
                    selectedIndex = sourceIndex,
                    enabled = enabled,
                    onSelectedIndexChange = { set(config.copy(sourceMode = sourceValues.getOrElse(it) { LyricSourceMode.LYRICON })) },
                )
            }
            LyricSourcePromptCard(config.sourceMode)
            if (config.sourceMode == LyricSourceMode.SUPER_LYRIC) {
                if (!support.superLyricInstalled) {
                    Card(modifier = sourceCardModifier) {
                        BasicComponent(
                            title = "未安装 SuperLyric",
                            summary = "请安装并启用 SuperLyric 模块以使用该歌词源",
                        )
                    }
                } else if (support.loaded && support.superLyricApiApps.isEmpty() && support.superLyricHookApps.isEmpty()) {
                    Card(modifier = sourceCardModifier) {
                        BasicComponent(
                            title = "未发现 SuperLyric 支持的应用",
                            summary = "请安装受支持的音乐应用，或给予本应用获取应用列表权限",
                        )
                    }
                } else {
                    if (support.superLyricApiApps.isNotEmpty()) {
                        SmallTitle(text = "API 支持列表")
                        support.superLyricApiApps.forEach { app -> LyricSupportAppCard(app) }
                    }
                    if (support.superLyricHookApps.isNotEmpty()) {
                        SmallTitle(text = "Hook 支持列表")
                        support.superLyricHookApps.forEach { app -> LyricSupportAppCard(app) }
                    }
                }
            }
        }

        if ((section == null || section == LyricConfigSection.PROVIDER) && config.sourceMode == LyricSourceMode.LYRICON) {
            if (section == null) {
                LyricSettingsTitle(text = "歌词提供器")
            }
            LyricSettingsCard {
                if (support.loaded && support.lyriconProviders.isEmpty()) {
                    BasicComponent(
                        title = "未发现 Lyricon 歌词提供器",
                        summary = "请安装 Lyricon Central 和兼容的 LyricProvider",
                    )
                }
                support.lyriconProviders.forEach { provider ->
                    val delay = config.lyriconProviderDelays[provider.packageName]
                        ?: config.lyriconProviderDelayMs
                    BasicComponent(
                        title = provider.label,
                        summary = provider.displaySummary(),
                    )
                    LyricInlineSliderRow("歌词时间偏移", delay.toFloat(), -5000f..5000f, enabled, "${delay}ms") { value ->
                        val next = config.lyriconProviderDelays.toMutableMap()
                        next[provider.packageName] = (value / 50f).roundToInt()
                            .times(50)
                            .coerceIn(-5000, 5000)
                        set(config.copy(lyriconProviderDelays = next))
                    }
                }
            }
        }

        if (section == null || section == LyricConfigSection.ISLAND) {
        LyricSettingsTitle(text = "布局")
        LyricSettingsCard {
            OverlayDropdownPreference(
                title = "超级岛长度模式",
                items = listOf("固定长度", "动态长度"),
                selectedIndex = config.widthMode.coerceIn(0, 1),
                enabled = enabled,
                onSelectedIndexChange = { set(config.copy(widthMode = it.coerceIn(0, 1))) },
            )
            if (config.widthMode == 1) {
                OverlayDropdownPreference(
                    title = "动态长度判断基准",
                    items = listOf("综合判断", "仅歌词"),
                    selectedIndex = config.dynamicWidthBasis.coerceIn(0, 1),
                    enabled = enabled,
                    onSelectedIndexChange = { set(config.copy(dynamicWidthBasis = it.coerceIn(0, 1))) },
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (config.widthMode == 1) {
                            "超级岛长度"
                        } else {
                            "超级岛长度"
                        },
                    )
                    Text(
                        if (config.widthMode == 1) {
                            "${dynamicWidthRange.start.toInt()}~${dynamicWidthRange.endInclusive.toInt()}"
                        } else {
                            config.rightContentMaxWidth.toString()
                        },
                    )
                }
                if (config.widthMode == 1) {
                    RangeSlider(
                        value = dynamicWidthRange,
                        enabled = enabled,
                        onValueChange = { value ->
                            val start = value.start.roundToInt().coerceIn(islandWidthMin, islandWidthMax)
                            val end = value.endInclusive.roundToInt().coerceIn(start, islandWidthMax)
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
                                    dynamicMinWidth = dynamicWidthRange.start.roundToInt(),
                                    dynamicMaxWidth = dynamicWidthRange.endInclusive.roundToInt(),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Slider(
                        value = fixedIslandWidth,
                        enabled = enabled,
                        onValueChange = { fixedIslandWidth = it.coerceIn(islandWidthMin.toFloat(), islandWidthMax.toFloat()) },
                        valueRange = islandWidthMin.toFloat()..islandWidthMax.toFloat(),
                        steps = 0,
                        showKeyPoints = true,
                        keyPoints = (islandWidthMin..islandWidthMax step 20).map(Int::toFloat),
                        magnetThreshold = 0f,
                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                        onValueChangeFinished = {
                            set(config.copy(rightContentMaxWidth = fixedIslandWidth.roundToInt()))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        LyricSettingsCard {
            LyricInlineDualSliderRow("左侧内容内边距", config.leftPaddingLeft, config.leftPaddingRight, enabled) { left, right -> set(config.copy(leftPaddingLeft = left, leftPaddingRight = right)) }
            LyricInlineDualSliderRow("右侧内容内边距", config.rightPaddingLeft, config.rightPaddingRight, enabled) { left, right -> set(config.copy(rightPaddingLeft = left, rightPaddingRight = right)) }
        }

        LyricSettingsTitle(text = "内容")
        LyricSettingsCard {
            SwitchPreference(
                title = "分离歌词",
                checked = config.lyricMode == 1,
                enabled = enabled,
                onCheckedChange = { set(config.copy(lyricMode = if (it) 1 else 0)) },
            )
        }
        LyricSettingsCard {
            OverlayDropdownPreference(
                title = "音频封面",
                items = LyricAlbumCoverStyle.pickerLabels,
                selectedIndex = LyricAlbumCoverStyle.pickerIndex(config.albumCoverStyle),
                enabled = enabled,
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
                    enabled = enabled,
                    onSelectedIndexChange = { index -> set(config.copy(contentLeft = listOf(IslandContentMode.NONE, IslandContentMode.MUSIC_INFO, IslandContentMode.LYRIC).getOrElse(index) { IslandContentMode.MUSIC_INFO })) },
                )
                OverlayDropdownPreference(
                    title = "超级岛右侧",
                    items = listOf("无内容", "音乐信息", "歌词"),
                    selectedIndex = when (config.contentRight) { IslandContentMode.NONE -> 0; IslandContentMode.MUSIC_INFO -> 1; IslandContentMode.LYRIC -> 2 },
                    enabled = enabled,
                    onSelectedIndexChange = { index -> set(config.copy(contentRight = listOf(IslandContentMode.NONE, IslandContentMode.MUSIC_INFO, IslandContentMode.LYRIC).getOrElse(index) { IslandContentMode.LYRIC })) },
                )
            }
            OverlayDropdownPreference(
                title = "音频律动",
                items = listOf("默认", "封面色", "封面渐变色", "隐藏"),
                selectedIndex = config.musicWaveStyle.coerceIn(0, 3),
                enabled = enabled,
                onSelectedIndexChange = { set(config.copy(musicWaveStyle = it)) },
            )
        }
        }

        if (section == null || section == LyricConfigSection.CONTENT_LAYOUT) {
        LyricSettingsTitle(text = "音乐信息")
        LyricSettingsCard {
            ArrowPreference(
                title = "第一行",
                summary = summarizeFields(config.musicInfoFirstLine, LyricMusicInfoLayout.FIELD_TITLE),
                enabled = enabled,
                onClick = { if (enabled) editingFieldRow = 0 },
            )
            ArrowPreference(
                title = "第二行",
                summary = summarizeFields(config.musicInfoSecondLine, LyricMusicInfoLayout.FIELD_ARTIST),
                enabled = enabled,
                onClick = { if (enabled) editingFieldRow = 1 },
            )
            OverlayDropdownPreference(
                title = "字段连接符",
                summary = "选择多个字段之间的拼接方式",
                items = separatorOptions,
                selectedIndex = separatorValues.safeIndex(config.musicInfoSeparator),
                enabled = enabled,
                onSelectedIndexChange = { set(config.copy(musicInfoSeparator = separatorValues.getOrElse(it) { "hyphen" })) },
            )
            SwitchPreference(title = "居中显示", checked = config.centerMusicInfo, enabled = enabled, onCheckedChange = { set(config.copy(centerMusicInfo = it)) })
        }
        LyricSettingsTitle(text = "歌词")
        LyricSettingsCard {
            SwitchPreference(title = "居中显示", checked = config.centerLyric, enabled = enabled && !config.rightLyric, onCheckedChange = { set(config.copy(centerLyric = it, rightLyric = if (it) false else config.rightLyric)) })
            SwitchPreference(title = "歌词右对齐", checked = config.rightLyric, enabled = enabled && !config.centerLyric, onCheckedChange = { set(config.copy(rightLyric = it, centerLyric = if (it) false else config.centerLyric)) })
            OverlayDropdownPreference(title = "占位符格式", items = placeholders, selectedIndex = config.placeholder.ordinal, enabled = enabled, onSelectedIndexChange = { set(config.copy(placeholder = LyricPlaceholder.entries.getOrElse(it) { LyricPlaceholder.COUNTDOWN })) })
        }
        }

        if (section == null || section == LyricConfigSection.TEXT_STYLE) {
        LyricSettingsTitle(text = "基础样式")
        LyricSettingsCard {
            LyricInlineSliderRow("大小", config.textSizeSp, 8f..16f, enabled, config.textSizeSp.toInt().toString()) { set(config.copy(textSizeSp = it.roundToInt().toFloat())) }
            LyricInlineSliderRow("多行模式下文字大小比例", config.textSizeRatio * 100f, 10f..100f, enabled, "${(config.textSizeRatio * 100).toInt()}%") { set(config.copy(textSizeRatio = it.roundToInt() / 100f)) }
            LyricInlineSliderRow("羽化边缘长度", config.fadingEdgeLengthDp.toFloat(), 0f..100f, enabled, config.fadingEdgeLengthDp.toString()) { set(config.copy(fadingEdgeLengthDp = it.roundToInt())) }
            OverlayDropdownPreference(title = "文字颜色", items = textColors, selectedIndex = config.textColorStyle.coerceIn(0, 3), enabled = enabled, onSelectedIndexChange = { set(config.copy(textColorStyle = it)) })
        }
        LyricSettingsTitle(text = "字体样式")
        LyricSettingsCard {
            OverlayDropdownPreference(
                title = "字体",
                items = listOf("默认", "自定义"),
                selectedIndex = if (config.customFontPath.isBlank()) 0 else 1,
                enabled = enabled,
                onSelectedIndexChange = {
                    if (!enabled) return@OverlayDropdownPreference
                    if (it == 0) set(config.copy(customFontPath = "")) else showFontDialog = true
                },
            )
            SwitchPreference(title = "英数窄字体", checked = config.narrowLatinFont, enabled = enabled, onCheckedChange = { set(config.copy(narrowLatinFont = it)) })
            LyricInlineSliderRow("字重", config.fontWeight.toFloat(), 100f..900f, enabled, config.fontWeight.toString()) { set(config.copy(fontWeight = it.roundToInt())) }
            SwitchPreference(title = "斜体", checked = config.fontItalic, enabled = enabled, onCheckedChange = { set(config.copy(fontItalic = it)) })
        }
        }

        if (section == null || section == LyricConfigSection.SCROLL) {
        LyricSettingsTitle(text = "歌词滚动")
        LyricSettingsCard {
            SwitchPreference(title = "歌词滚动", summary = "针对没有时间轴的歌词", checked = config.marqueeMode, enabled = enabled, onCheckedChange = { set(config.copy(marqueeMode = it)) })
            LyricInlineSliderRow("滚动速度", config.marqueeSpeed.toFloat(), 5f..100f, enabled && config.marqueeMode, config.marqueeSpeed.toString()) { set(config.copy(marqueeSpeed = it.roundToInt())) }
            LyricInlineSliderRow("初始滚动延迟", config.marqueeDelay.toFloat(), 0f..5000f, enabled && config.marqueeMode, "${config.marqueeDelay}ms") { set(config.copy(marqueeDelay = it.roundToInt())) }
            SwitchPreference(title = "无限循环", enabled = enabled && config.marqueeMode, checked = config.marqueeInfinite, onCheckedChange = { set(config.copy(marqueeInfinite = it)) })
            LyricInlineSliderRow("循环间隔", config.marqueeLoopDelay.toFloat(), 0f..5000f, enabled && config.marqueeMode, "${config.marqueeLoopDelay}ms") { set(config.copy(marqueeLoopDelay = it.roundToInt())) }
            SwitchPreference(title = "结束时在末尾停止", enabled = enabled && config.marqueeMode, checked = config.marqueeStopEnd, onCheckedChange = { set(config.copy(marqueeStopEnd = it)) })
        }
        if (config.lyricMode == 0) {
            LyricSettingsTitle(text = "歌曲信息滚动")
            LyricSettingsCard {
                SwitchPreference(
                    title = "歌曲信息滚动",
                    summary = "针对歌曲信息",
                    checked = config.metadataMarqueeMode,
                    enabled = enabled,
                    onCheckedChange = { set(config.copy(metadataMarqueeMode = it)) },
                )
                LyricInlineSliderRow("滚动速度", config.metadataMarqueeSpeed.toFloat(), 5f..100f, enabled && config.metadataMarqueeMode, config.metadataMarqueeSpeed.toString()) { set(config.copy(metadataMarqueeSpeed = it.roundToInt())) }
                LyricInlineSliderRow("初始滚动延迟", config.metadataMarqueeDelay.toFloat(), 0f..10000f, enabled && config.metadataMarqueeMode, "${config.metadataMarqueeDelay}ms") { set(config.copy(metadataMarqueeDelay = it.roundToInt())) }
                SwitchPreference(title = "无限循环", enabled = enabled && config.metadataMarqueeMode, checked = config.metadataMarqueeInfinite, onCheckedChange = { set(config.copy(metadataMarqueeInfinite = it)) })
                LyricInlineSliderRow("循环间隔", config.metadataMarqueeLoopDelay.toFloat(), 0f..10000f, enabled && config.metadataMarqueeMode, "${config.metadataMarqueeLoopDelay}ms") { set(config.copy(metadataMarqueeLoopDelay = it.roundToInt())) }
            }
        }
        }

        if (section == null || section == LyricConfigSection.VERBATIM) {
        if (section == null) {
            LyricSettingsTitle(text = "逐字歌词")
        }
        LyricSettingsCard {
            SwitchPreference(
                title = "模拟逐行歌词",
                summary = "将带有字词时间轴的歌词降级为整行时间轴显示",
                checked = config.syllableLineDisplay,
                enabled = enabled,
                onCheckedChange = {
                    set(config.copy(
                        syllableLineDisplay = it,
                        syllableRelative = if (it) false else config.syllableRelative,
                    ))
                },
            )
            SwitchPreference(
                title = "相对进度歌词",
                summary = "当歌词缺少字词时间轴时，将整行作为一个进度单元",
                checked = config.syllableRelative,
                enabled = enabled,
                onCheckedChange = { set(config.copy(syllableRelative = it, syllableLineDisplay = false)) },
            )
            SwitchPreference(title = "相对进度歌词高亮显示", enabled = enabled && config.syllableRelative, checked = config.syllableHighlight, onCheckedChange = { set(config.copy(syllableHighlight = it)) })
        }
        LyricSettingsTitle(text = "歌词动效")
        LyricSettingsCard {
            SwitchPreference(title = "羽化进度样式", checked = config.gradientProgressStyle, enabled = enabled, onCheckedChange = { set(config.copy(gradientProgressStyle = it)) })
            SwitchPreference(title = "逐字字词上浮动画", checked = config.wordMotionEnabled, enabled = enabled, onCheckedChange = { set(config.copy(wordMotionEnabled = it)) })
            if (config.wordMotionEnabled) {
                SwitchPreference(title = "拉丁文字逐字母上浮", summary = "关闭时按整个单词上浮", checked = config.wordMotionLatinByCharacter, enabled = enabled, onCheckedChange = { set(config.copy(wordMotionLatinByCharacter = it)) })
                LyricInlineSliderRow("中日韩上浮系数", config.wordMotionCjkLift, 0f..0.2f, enabled, "%.2f".format(java.util.Locale.ROOT, config.wordMotionCjkLift)) { set(config.copy(wordMotionCjkLift = it)) }
                LyricInlineSliderRow("中日韩波长系数", config.wordMotionCjkWave, 0f..10f, enabled, "%.2f".format(java.util.Locale.ROOT, config.wordMotionCjkWave)) { set(config.copy(wordMotionCjkWave = it)) }
                LyricInlineSliderRow("拉丁文字上浮系数", config.wordMotionLatinLift, 0f..0.2f, enabled, "%.2f".format(java.util.Locale.ROOT, config.wordMotionLatinLift)) { set(config.copy(wordMotionLatinLift = it)) }
                LyricInlineSliderRow("拉丁文字波长系数", config.wordMotionLatinWave, 0f..10f, enabled, "%.2f".format(java.util.Locale.ROOT, config.wordMotionLatinWave)) { set(config.copy(wordMotionLatinWave = it)) }
            }
        }
        }

        if (section == null || section == LyricConfigSection.TRANSLATION) {
        val nextSupported = config.sourceMode == LyricSourceMode.LYRICON ||
            config.sourceMode == LyricSourceMode.LYRIC_INFO
        if (nextSupported) {
            LyricSettingsTitle(text = "下一句歌词")
            LyricSettingsCard {
                SwitchPreference(title = "显示下一句歌词", summary = "占用第二行，开启后不显示翻译", checked = config.nextLyricLine, enabled = enabled, onCheckedChange = { set(config.copy(nextLyricLine = it)) })
            }
        }
        if (section == null) {
            LyricSettingsTitle(text = "翻译")
        }
        LyricSettingsCard {
            val translationEnabled = !nextSupported || !config.nextLyricLine
            SwitchPreference(title = "禁用所有翻译", enabled = enabled && translationEnabled, checked = config.disableTranslation, onCheckedChange = { set(config.copy(disableTranslation = it)) })
            SwitchPreference(title = "仅显示翻译", enabled = enabled && translationEnabled && !config.swapTranslation, checked = config.translationOnly, onCheckedChange = { set(config.copy(translationOnly = it, swapTranslation = if (it) false else config.swapTranslation)) })
            SwitchPreference(title = "原词翻译对调位置", enabled = enabled && translationEnabled && !config.translationOnly, checked = config.swapTranslation, onCheckedChange = { set(config.copy(swapTranslation = it, translationOnly = if (it) false else config.translationOnly)) })
        }
        }

        if (section == null || section == LyricConfigSection.ANIMATION) {
        if (section == null) {
            LyricSettingsTitle(text = "歌词切换动画")
        }
        LyricSettingsCard {
            OverlayDropdownPreference(title = "歌词切换动画", items = animationLabels, selectedIndex = animationIndex, enabled = enabled, onSelectedIndexChange = { index -> set(config.copy(animEnabled = index != 0, animId = animationIds.getOrElse(index) { "default" })) })
        }
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
