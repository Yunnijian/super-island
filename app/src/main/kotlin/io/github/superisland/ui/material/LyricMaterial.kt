package io.github.superisland.ui.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import java.util.Locale
import io.github.superisland.source.lyric.IslandContentMode
import io.github.superisland.source.lyric.LyricIslandConfig
import io.github.superisland.source.lyric.LyricIslandWidthPolicy
import io.github.superisland.source.lyric.LyricAlbumCoverStyle
import io.github.superisland.source.lyric.LyricMusicInfoLayout
import io.github.superisland.source.lyric.LyricPlaceholder
import io.github.superisland.source.lyric.LyricSourceMode
import io.github.superisland.design.displaySummary
import io.github.superisland.design.LyricConfigSection
import io.github.superisland.design.rememberLyricSupportSnapshot
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors

private val sources = listOf(
    LyricSourceMode.LYRICON,
    LyricSourceMode.SUPER_LYRIC,
    LyricSourceMode.LYRIC_INFO,
)
private val sourceLabels = listOf("Lyricon", "SuperLyric", "LyricInfo")
private val lyricModeLabels = listOf("逐字歌词", "分离歌词")
private val separators = listOf("加号（+）", "空格", "逗号（,）", "顿号（、）", "斜杠（/）", "横杠（-）", "不使用连接符")
private val separatorKeys = listOf("plus", "space", "comma", "ideographic_comma", "slash", "hyphen", "none")
private val animationIds = listOf("none", "default", "fade_out_fade_in", "fade_out_up_fade_in_up", "fade_out_down_fade_in_down", "fade_out_left_fade_in_right", "fade_out_left_fade_in_up", "fade_out_left_zoom_in", "fade_out_left_landing", "fade_out_right_fade_in_left", "fade_out_right_fade_in_up", "fade_out_right_zoom_in", "fade_out_right_landing", "fade_out_left_zoom_in_right", "fade_out_right_zoom_in_left", "slide_out_left_slide_in_right", "slide_out_left_fade_in_up", "slide_out_left_zoom_in", "slide_out_left_landing", "slide_out_right_slide_in_left", "slide_out_right_fade_in_up", "slide_out_right_zoom_in", "slide_out_right_landing", "flip_out_x_flip_in_x", "flip_out_y_flip_in_y", "rotate_out_rotate_in", "zoom_out_zoom_in")
private val animationLabels = listOf("无动画", "默认", "渐隐渐现", "向上渐隐＆向上渐现", "向下渐隐＆向下渐现", "向左渐隐＆右侧渐现", "向左渐隐＆向上渐现", "向左渐隐＆缩放渐现", "向左渐隐＆柔缓着陆", "向右渐隐＆左侧渐现", "向右渐隐＆向上渐现", "向右渐隐＆缩放渐现", "向右渐隐＆聚焦着陆", "向左渐隐＆右侧缩放渐现", "向右渐隐＆左侧缩放渐现", "左侧滑出＆右侧滑入", "左侧滑出＆向上渐现", "左侧滑出＆缩放渐现", "左侧滑出＆柔缓着陆", "右侧滑出＆左侧滑入", "右侧滑出＆向上渐现", "右侧滑出＆缩放渐现", "右侧滑出＆柔缓着陆", "X轴翻转", "Y轴翻转", "旋转", "缩放")

private fun List<Any>.selected(value: Any): Int = indexOf(value).takeIf { it >= 0 } ?: 0

private val contentModes = listOf(
    IslandContentMode.NONE,
    IslandContentMode.MUSIC_INFO,
    IslandContentMode.LYRIC,
)

private val LocalLyricControlsEnabled = compositionLocalOf { true }

@Composable
private fun MaterialInlineSliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    valueLabel: String,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(title, value, valueRange.start, valueRange.endInclusive) {
        mutableFloatStateOf(value.coerceIn(valueRange.start, valueRange.endInclusive))
    }
    SegmentedListItem(
        modifier = modifier,
        onClick = {},
        enabled = enabled,
        headlineContent = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title)
                    Text(valueLabel, maxLines = 1)
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onValueChangeFinished(sliderValue) },
                    valueRange = valueRange,
                    steps = steps,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun MaterialInlineDualSliderItem(
    title: String,
    first: Int,
    second: Int,
    enabled: Boolean,
    onValueChangeFinished: (Int, Int) -> Unit,
) {
    var firstValue by remember(title, first) { mutableFloatStateOf(first.coerceIn(-50, 100).toFloat()) }
    var secondValue by remember(title, second) { mutableFloatStateOf(second.coerceIn(-50, 100).toFloat()) }
    SegmentedListItem(
        onClick = {},
        enabled = enabled,
        headlineContent = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(title)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(firstValue.toInt().toString(), modifier = Modifier.width(32.dp))
                    Slider(
                        value = firstValue,
                        onValueChange = { firstValue = it },
                        valueRange = -50f..100f,
                        steps = 14,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onValueChangeFinished = { onValueChangeFinished(firstValue.toInt(), secondValue.toInt()) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(secondValue.toInt().toString(), modifier = Modifier.width(32.dp))
                    Slider(
                        value = secondValue,
                        onValueChange = { secondValue = it },
                        valueRange = -50f..100f,
                        steps = 14,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onValueChangeFinished = { onValueChangeFinished(firstValue.toInt(), secondValue.toInt()) },
                    )
                }
            }
        },
    )
}

private fun contentModeIndex(value: IslandContentMode): Int =
    contentModes.indexOf(value).takeIf { it >= 0 } ?: 0

private fun contentModeAt(index: Int, fallback: IslandContentMode): IslandContentMode =
    contentModes.getOrElse(index) { fallback }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricMaterial(config: LyricIslandConfig, onConfigChange: (LyricIslandConfig) -> Unit, onBack: () -> Unit) {
    var section by remember { mutableStateOf<LyricConfigSection?>(null) }
    val selected = section
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (fadeIn() + slideInHorizontally { it / 6 }) togetherWith
                (fadeOut() + slideOutHorizontally { -it / 6 })
        },
        label = "歌词配置页面切换",
    ) { page ->
    if (page == null) {
        LyricMaterialDirectory(
            config = config,
            enabled = config.enabled,
            onEnabledChange = { onConfigChange(config.withEnabled(it)) },
            onConfigChange = onConfigChange,
            onOpenSection = { section = it },
            onBack = onBack,
        )
    } else {
        LyricMaterialDetail(
            config = config,
            onConfigChange = onConfigChange,
            section = page,
            onBack = { section = null },
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricMaterialDetail(
    config: LyricIslandConfig,
    onConfigChange: (LyricIslandConfig) -> Unit,
    section: LyricConfigSection,
    onBack: () -> Unit,
) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val set: (LyricIslandConfig) -> Unit = { if (config.enabled) onConfigChange(it.normalized()) }
    val support = rememberLyricSupportSnapshot()
    var showFontDialog by remember { mutableStateOf(false) }
    var fontPath by remember(config.customFontPath) { mutableStateOf(config.customFontPath) }
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
        0 -> LyricMusicInfoLayout.parseFields(config.musicInfoFirstLine, LyricMusicInfoLayout.FIELD_TITLE)
        1 -> LyricMusicInfoLayout.parseFields(config.musicInfoSecondLine, LyricMusicInfoLayout.FIELD_ARTIST)
        else -> emptyList()
    }
    MaterialFieldOrderDialog(
        show = editingFieldRow != null,
        title = if (editingFieldRow == 1) "第二行" else "第一行",
        initialFields = editingFields,
        requireSelection = editingFieldRow == 0,
        onDismiss = { editingFieldRow = null },
        onConfirm = { fields ->
            when (editingFieldRow) {
                0 -> set(config.copy(musicInfoFirstLine = LyricMusicInfoLayout.encodeFields(fields, LyricMusicInfoLayout.FIELD_TITLE)))
                1 -> set(config.copy(musicInfoSecondLine = LyricMusicInfoLayout.encodeFields(fields, LyricMusicInfoLayout.FIELD_ARTIST)))
            }
            editingFieldRow = null
        },
    )
    val dropdown: @Composable (String, List<String>, Int, (Int) -> Unit) -> Unit = { title, values, index, onSelect ->
        SegmentedDropdownItem(title = title, items = values, selectedIndex = index.coerceIn(0, values.lastIndex), enabled = config.enabled, onItemSelected = onSelect)
    }
    val dropdownEnabled: @Composable (String, List<String>, Int, Boolean, (Int) -> Unit) -> Unit = { title, values, index, enabled, onSelect ->
        SegmentedDropdownItem(title = title, items = values, selectedIndex = index.coerceIn(0, values.lastIndex), enabled = config.enabled && enabled, onItemSelected = onSelect)
    }
    val switch: @Composable (String, String, Boolean, Boolean, (Boolean) -> Unit) -> Unit = { title, summary, checked, enabled, onChange ->
        val rowEnabled = config.enabled && enabled
        if (summary.isBlank()) {
            SegmentedListItem(
                onClick = { if (rowEnabled) onChange(!checked) },
                enabled = rowEnabled,
                headlineContent = { Text(title) },
                trailingContent = {
                    Switch(
                        checked = checked,
                        enabled = rowEnabled,
                        onCheckedChange = onChange,
                    )
                },
            )
        } else {
            SegmentedSwitchItem(title = title, summary = summary, checked = checked, enabled = rowEnabled, onCheckedChange = onChange)
        }
    }
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(section.title) },
                navigationIcon = {
                    TopBarBackButton(onClick = onBack, contentDescription = "返回")
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = behavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        CompositionLocalProvider(LocalLyricControlsEnabled provides config.enabled) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(behavior.nestedScrollConnection)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (section == LyricConfigSection.SOURCE) {
            item {
                SegmentedColumn(Modifier.fillMaxWidth(), title = "小米超级岛歌词自定义配置", content = listOf(
                    { dropdown("歌词源", sourceLabels, sources.selected(config.sourceMode.publicPickerMode())) { set(config.copy(sourceMode = sources.getOrElse(it) { LyricSourceMode.LYRICON })) } },
                    { if (config.sourceMode == LyricSourceMode.LYRICON && support.lyriconProviders.isEmpty() && support.loaded) Text("未发现 Lyricon 歌词提供器", style = MaterialTheme.typography.bodySmall) },
                    { if (config.sourceMode == LyricSourceMode.SUPER_LYRIC && !support.superLyricInstalled && support.loaded) Text("未安装 SuperLyric\n请安装并启用 SuperLyric 模块以使用该歌词源", style = MaterialTheme.typography.bodySmall) },
                ))
            }
            if (config.sourceMode == LyricSourceMode.SUPER_LYRIC) {
                item {
                    SegmentedColumn(
                        Modifier.fillMaxWidth(),
                        title = "API 支持列表",
                        content = support.superLyricApiApps.map { app ->
                            { Text("${app.label}  v${app.versionName} (${app.versionCode})") }
                        },
                    )
                }
                item {
                    SegmentedColumn(
                        Modifier.fillMaxWidth(),
                        title = "Hook 支持列表",
                        content = support.superLyricHookApps.map { app ->
                            { Text("${app.label}  v${app.versionName} (${app.versionCode})") }
                        },
                    )
                }
            }
            }
            if (section == LyricConfigSection.PROVIDER && config.sourceMode == LyricSourceMode.LYRICON) {
                item {
                    SegmentedColumn(
                        Modifier.fillMaxWidth(),
                        title = "歌词提供器",
                        content = support.lyriconProviders.map { provider ->
                            {
                                val delay = config.lyriconProviderDelays[provider.packageName]
                                    ?: config.lyriconProviderDelayMs
                                Column {
                                    Text(provider.label, style = MaterialTheme.typography.titleSmall)
                                    Text(provider.displaySummary(), style = MaterialTheme.typography.bodySmall)
                                    MaterialInlineSliderItem(
                                        title = "歌词时间偏移",
                                        value = delay.toFloat(),
                                        valueRange = -5000f..5000f,
                                        steps = 19,
                                        enabled = config.enabled,
                                        valueLabel = "${delay}ms",
                                        onValueChangeFinished = { value ->
                                        val next = config.lyriconProviderDelays.toMutableMap()
                                        next[provider.packageName] = value.toInt().coerceIn(-5000, 5000)
                                        set(config.copy(lyriconProviderDelays = next))
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            if (section == LyricConfigSection.ISLAND) {
            item {
                SegmentedColumn(Modifier.fillMaxWidth(), title = "布局", content = listOf(
                    { dropdown("超级岛长度模式", listOf("固定长度", "动态长度"), config.widthMode) { set(config.copy(widthMode = it)) } },
                    { if (config.widthMode == 1) dropdown("动态长度判断基准", listOf("综合判断", "仅歌词"), config.dynamicWidthBasis) { set(config.copy(dynamicWidthBasis = it)) } },
                    {
                        SegmentedListItem(
                            onClick = {},
                            enabled = config.enabled,
                            headlineContent = {
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("超级岛长度")
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
                                            enabled = config.enabled,
                                            onValueChange = { value ->
                                                val start = value.start.toInt().coerceIn(islandWidthMin, islandWidthMax)
                                                val end = value.endInclusive.toInt().coerceIn(start, islandWidthMax)
                                                dynamicWidthRange = start.toFloat()..end.toFloat()
                                            },
                                            valueRange = islandWidthMin.toFloat()..islandWidthMax.toFloat(),
                                            steps = 3,
                                            onValueChangeFinished = {
                                                set(config.copy(dynamicMinWidth = dynamicWidthRange.start.toInt(), dynamicMaxWidth = dynamicWidthRange.endInclusive.toInt()))
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        Slider(
                                            value = fixedIslandWidth,
                                            enabled = config.enabled,
                                            onValueChange = { fixedIslandWidth = it.coerceIn(islandWidthMin.toFloat(), islandWidthMax.toFloat()) },
                                            valueRange = islandWidthMin.toFloat()..islandWidthMax.toFloat(),
                                            steps = 3,
                                            onValueChangeFinished = { set(config.copy(rightContentMaxWidth = fixedIslandWidth.toInt())) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            },
                        )
                    },
                    { switch("解除超级岛最大长度限制", "", config.disableWidthLimit, true) { set(config.copy(disableWidthLimit = it)) } },
                    { MaterialInlineDualSliderItem("左侧内容内边距", config.leftPaddingLeft, config.leftPaddingRight, config.enabled) { left, right -> set(config.copy(leftPaddingLeft = left, leftPaddingRight = right)) } },
                    { MaterialInlineDualSliderItem("右侧内容内边距", config.rightPaddingLeft, config.rightPaddingRight, config.enabled) { left, right -> set(config.copy(rightPaddingLeft = left, rightPaddingRight = right)) } },
                ))
            }
            }
            if (section == LyricConfigSection.ISLAND) {
            item {
                SegmentedColumn(Modifier.fillMaxWidth(), title = "内容", content = listOf(
                    { switch("分离歌词", "", config.lyricMode == 1, true) { set(config.copy(lyricMode = if (it) 1 else 0)) } },
                    {
                        dropdown(
                            "音频封面",
                            LyricAlbumCoverStyle.pickerLabels,
                            LyricAlbumCoverStyle.pickerIndex(config.albumCoverStyle),
                        ) {
                            set(
                                config.copy(
                                    albumCoverStyle = LyricAlbumCoverStyle.pickerValues.getOrElse(it) {
                                        LyricAlbumCoverStyle.DEFAULT
                                    },
                                ),
                            )
                        }
                    },
                    { if (config.lyricMode == 0) dropdown("超级岛左侧", listOf("无内容", "音乐信息", "歌词"), contentModeIndex(config.contentLeft)) { set(config.copy(contentLeft = contentModeAt(it, IslandContentMode.MUSIC_INFO))) } },
                    { if (config.lyricMode == 0) dropdown("超级岛右侧", listOf("无内容", "音乐信息", "歌词"), contentModeIndex(config.contentRight)) { set(config.copy(contentRight = contentModeAt(it, IslandContentMode.LYRIC))) } },
                    { dropdown("音频律动", listOf("默认", "封面色", "封面渐变色", "隐藏"), config.musicWaveStyle) { set(config.copy(musicWaveStyle = it)) } },
                ))
            }
            }
            if (section == LyricConfigSection.ISLAND) {
            item {
                SegmentedColumn(Modifier.fillMaxWidth(), title = "内容布局", content = listOf(
                    { materialFieldRow("第一行", summarizeFields(config.musicInfoFirstLine, LyricMusicInfoLayout.FIELD_TITLE)) { editingFieldRow = 0 } },
                    { materialFieldRow("第二行", summarizeFields(config.musicInfoSecondLine, LyricMusicInfoLayout.FIELD_ARTIST)) { editingFieldRow = 1 } },
                    { dropdown("字段连接符", separators, separatorKeys.selected(config.musicInfoSeparator)) { set(config.copy(musicInfoSeparator = separatorKeys.getOrElse(it) { "hyphen" })) } },
                    { switch("居中显示", "", config.centerMusicInfo, true) { set(config.copy(centerMusicInfo = it)) } },
                    { switch("居中显示", "", config.centerLyric, !config.rightLyric) { set(config.copy(centerLyric = it, rightLyric = if (it) false else config.rightLyric)) } },
                    { switch("歌词右对齐", "", config.rightLyric, !config.centerLyric) { set(config.copy(rightLyric = it, centerLyric = if (it) false else config.centerLyric)) } },
                    { dropdown("占位符格式", LyricPlaceholder.entries.map { it.display }, config.placeholder.ordinal) { set(config.copy(placeholder = LyricPlaceholder.entries.getOrElse(it) { LyricPlaceholder.COUNTDOWN })) } },
                ))
            }
            }
            if (section == LyricConfigSection.TEXT_STYLE) {
            item {
                SegmentedColumn(Modifier.fillMaxWidth(), title = "文字样式", content = listOf(
                    { dropdown("文字颜色", listOf("默认", "封面色", "封面渐变色", "跟随状态栏颜色"), config.textColorStyle) { set(config.copy(textColorStyle = it)) } },
                    {
                        dropdown(
                            "字体",
                            listOf("默认", "自定义"),
                            if (config.customFontPath.isBlank()) 0 else 1,
                        ) { index ->
                            if (index == 0) set(config.copy(customFontPath = "")) else showFontDialog = true
                        }
                    },
                    { switch("英数窄字体", "", config.narrowLatinFont, true) { set(config.copy(narrowLatinFont = it)) } },
                    { MaterialInlineSliderItem("字重", config.fontWeight.toFloat(), 100f..900f, 7, config.enabled, config.fontWeight.toString(), { set(config.copy(fontWeight = it.toInt())) }) },
                    { switch("斜体", "", config.fontItalic, true) { set(config.copy(fontItalic = it)) } },
                    { MaterialInlineSliderItem("大小", config.textSizeSp, 8f..16f, 7, config.enabled, config.textSizeSp.toInt().toString(), { set(config.copy(textSizeSp = it)) }) },
                    { MaterialInlineSliderItem("多行模式下文字大小比例", config.textSizeRatio * 100f, 10f..100f, 8, config.enabled, "${(config.textSizeRatio * 100).toInt()}%", { set(config.copy(textSizeRatio = it / 100f)) }) },
                    { MaterialInlineSliderItem("羽化边缘长度", config.fadingEdgeLengthDp.toFloat(), 0f..100f, 19, config.enabled, config.fadingEdgeLengthDp.toString(), { set(config.copy(fadingEdgeLengthDp = it.toInt())) }) },
                ))
            }
            }
            if (section == LyricConfigSection.SCROLL) {
            item {
                SegmentedColumn(Modifier.fillMaxWidth(), title = "滚动显示", content = listOf(
                    { switch("歌词滚动", "针对没有时间轴的歌词", config.marqueeMode, true) { set(config.copy(marqueeMode = it)) } },
                    { MaterialInlineSliderItem("滚动速度", config.marqueeSpeed.toFloat(), 5f..100f, 18, config.enabled && config.marqueeMode, config.marqueeSpeed.toString(), { set(config.copy(marqueeSpeed = it.toInt())) }) },
                    { MaterialInlineSliderItem("初始滚动延迟", config.marqueeDelay.toFloat(), 0f..10000f, 9, config.enabled && config.marqueeMode, "${config.marqueeDelay}ms", { set(config.copy(marqueeDelay = it.toInt())) }) },
                    { switch("无限循环", "", config.marqueeInfinite, config.marqueeMode) { set(config.copy(marqueeInfinite = it)) } },
                    { MaterialInlineSliderItem("循环间隔", config.marqueeLoopDelay.toFloat(), 0f..10000f, 9, config.enabled && config.marqueeMode, "${config.marqueeLoopDelay}ms", { set(config.copy(marqueeLoopDelay = it.toInt())) }) },
                    { switch("结束时在末尾停止", "", config.marqueeStopEnd, config.marqueeMode) { set(config.copy(marqueeStopEnd = it)) } },
                ))
            }
            }
            if (section == LyricConfigSection.VERBATIM) {
            item {
                SegmentedColumn(Modifier.fillMaxWidth(), title = "逐字歌词", content = listOf(
                    { switch("相对进度歌词", "当歌词缺少字词时间轴时，将整行作为一个进度单元", config.syllableRelative, true) { set(config.copy(syllableRelative = it)) } },
                    { switch("相对进度歌词高亮显示", "", config.syllableHighlight, config.syllableRelative) { set(config.copy(syllableHighlight = it)) } },
                ))
            }
            item {
                SegmentedColumn(Modifier.fillMaxWidth(), title = "歌词动效", content = listOf(
                    { switch("羽化进度样式", "", config.gradientProgressStyle, true) { set(config.copy(gradientProgressStyle = it)) } },
                    { switch("逐字字词上浮动画", "", config.wordMotionEnabled, true) { set(config.copy(wordMotionEnabled = it)) } },
                    { if (config.wordMotionEnabled) switch("拉丁文字逐字母上浮", "关闭时按整个单词上浮", config.wordMotionLatinByCharacter, true) { set(config.copy(wordMotionLatinByCharacter = it)) } },
                    { if (config.wordMotionEnabled) MaterialInlineSliderItem("中日韩上浮系数", config.wordMotionCjkLift, 0f..0.2f, 9, config.enabled, String.format(Locale.ROOT, "%.2f", config.wordMotionCjkLift), { set(config.copy(wordMotionCjkLift = it)) }) },
                    { if (config.wordMotionEnabled) MaterialInlineSliderItem("中日韩波长系数", config.wordMotionCjkWave, 0f..8f, 7, config.enabled, String.format(Locale.ROOT, "%.2f", config.wordMotionCjkWave), { set(config.copy(wordMotionCjkWave = it)) }) },
                    { if (config.wordMotionEnabled) MaterialInlineSliderItem("拉丁文字上浮系数", config.wordMotionLatinLift, 0f..0.2f, 9, config.enabled, String.format(Locale.ROOT, "%.2f", config.wordMotionLatinLift), { set(config.copy(wordMotionLatinLift = it)) }) },
                    { if (config.wordMotionEnabled) MaterialInlineSliderItem("拉丁文字波长系数", config.wordMotionLatinWave, 0f..8f, 7, config.enabled, String.format(Locale.ROOT, "%.2f", config.wordMotionLatinWave), { set(config.copy(wordMotionLatinWave = it)) }) },
                ))
            }
            }
            if (section == LyricConfigSection.TRANSLATION) {
            item {
                val nextSupported = config.sourceMode == LyricSourceMode.LYRICON ||
                    config.sourceMode == LyricSourceMode.LYRIC_INFO
                val translationEnabled = !nextSupported || !config.nextLyricLine
                SegmentedColumn(Modifier.fillMaxWidth(), title = "双行内容", content = buildList {
                    if (nextSupported) {
                        add { switch("显示下一句歌词", "占用第二行，开启后不显示翻译", config.nextLyricLine, true) { set(config.copy(nextLyricLine = it)) } }
                    }
                    add { switch("禁用所有翻译", "", config.disableTranslation, translationEnabled) { set(config.copy(disableTranslation = it)) } }
                    add { switch("仅显示翻译", "", config.translationOnly, translationEnabled && !config.swapTranslation) { set(config.copy(translationOnly = it, swapTranslation = if (it) false else config.swapTranslation)) } }
                    add { switch("原词翻译对调位置", "", config.swapTranslation, translationEnabled && !config.translationOnly) { set(config.copy(swapTranslation = it, translationOnly = if (it) false else config.translationOnly)) } }
                })
            }
            }
            if (section == LyricConfigSection.ANIMATION) {
            item {
                val index = animationIds.indexOf(config.animId).takeIf { config.animEnabled && it >= 0 } ?: 0
                SegmentedColumn(Modifier.fillMaxWidth(), title = "歌词切换动画", content = listOf({ dropdown("歌词切换动画", animationLabels, index) { i -> set(config.copy(animEnabled = i != 0, animId = animationIds.getOrElse(i) { "default" })) } }))
            }
            }
        }
        }
    }
    if (showFontDialog) {
        AlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text("字体") },
            text = {
                OutlinedTextField(
                    value = fontPath,
                    onValueChange = { fontPath = it },
                    label = { Text("字体文件路径") },
                    singleLine = true,
                )
            },
            dismissButton = {
                TextButton(onClick = { showFontDialog = false }) { Text("取消") }
            },
            confirmButton = {
                TextButton(onClick = {
                    set(config.copy(customFontPath = fontPath.trim()))
                    showFontDialog = false
                }) { Text("确定") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricMaterialDirectory(
    config: LyricIslandConfig,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onConfigChange: (LyricIslandConfig) -> Unit,
    onOpenSection: (LyricConfigSection) -> Unit,
    onBack: () -> Unit,
) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("小米超级岛歌词") },
                navigationIcon = {
                    TopBarBackButton(onClick = onBack, contentDescription = "返回")
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = behavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(behavior.nestedScrollConnection)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                title = "小米超级岛歌词",
                content = listOf(
                    {
                        SegmentedSwitchItem(
                            title = "启用",
                            summary = if (enabled) "正在通过超级岛显示歌词" else "关闭后不会发布歌词超级岛",
                            checked = enabled,
                            enabled = true,
                            onCheckedChange = onEnabledChange,
                        )
                    },
                    {
                        SegmentedDropdownItem(
                            title = "歌词模式",
                            items = lyricModeLabels,
                            selectedIndex = config.lyricMode.coerceIn(0, lyricModeLabels.lastIndex),
                            enabled = enabled,
                            onItemSelected = { mode ->
                                onConfigChange(config.copy(lyricMode = mode).normalized())
                            },
                        )
                    },
                    {
                        SegmentedDropdownItem(
                            title = "歌词源",
                            items = sourceLabels,
                            selectedIndex = sources.selected(config.sourceMode.publicPickerMode()),
                            enabled = enabled,
                            onItemSelected = { source ->
                                onConfigChange(config.copy(sourceMode = sources.getOrElse(source) { LyricSourceMode.LYRICON }).normalized())
                            },
                        )
                    },
                ),
            )
            val sections = LyricConfigSection.entries
                .filter {
                    it != LyricConfigSection.SOURCE &&
                        (it != LyricConfigSection.PROVIDER || config.sourceMode == LyricSourceMode.LYRICON)
                }
            SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                title = "自定义配置",
                content = sections.map { section ->
                    {
                        SegmentedListItem(
                            onClick = { onOpenSection(section) },
                            enabled = enabled,
                            headlineContent = { Text(section.title) },
                            supportingContent = lyricSectionSummary(section, config)?.let { summary ->
                                { Text(summary) }
                            },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "进入",
                                )
                            },
                        )
                    }
                },
            )
        }
    }
}

private fun lyricSectionSummary(
    section: LyricConfigSection,
    config: LyricIslandConfig,
): String? = when (section) {
    LyricConfigSection.SOURCE -> when (config.sourceMode) {
        LyricSourceMode.SUPER_LYRIC -> "SuperLyric"
        LyricSourceMode.LYRIC_INFO -> "LyricInfo"
        LyricSourceMode.MEDIA_FALLBACK -> "LyricInfo"
        LyricSourceMode.LYRICON -> "Lyricon"
    }
    LyricConfigSection.ISLAND -> if (config.widthMode == 1) "动态长度" else "固定长度"
    LyricConfigSection.PROVIDER -> "Lyricon 歌词提供器与时间偏移"
    LyricConfigSection.TEXT_STYLE,
    LyricConfigSection.SCROLL,
    LyricConfigSection.VERBATIM,
    LyricConfigSection.ANIMATION,
    -> null
    LyricConfigSection.TRANSLATION -> "歌词翻译、下一句歌词等功能"
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

@Composable
private fun materialFieldRow(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = LocalLyricControlsEnabled.current && enabled,
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
    )
}

@Composable
private fun MaterialFieldOrderDialog(
    show: Boolean,
    title: String,
    initialFields: List<String>,
    requireSelection: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    if (!show) return
    val initialOrder = remember(initialFields) { initialFields + LyricMusicInfoLayout.fieldOrder.filterNot { it in initialFields } }
    var order by remember(initialOrder) { mutableStateOf(initialOrder) }
    var selected by remember(initialOrder) { mutableStateOf(initialFields.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("选择要显示的音乐信息字段，并调整顺序", style = MaterialTheme.typography.bodySmall)
                order.forEachIndexed { index, field ->
                    SegmentedListItem(
                        headlineContent = { Text(LyricMusicInfoLayout.fieldLabels[field] ?: field) },
                        leadingContent = {
                            Checkbox(
                                checked = field in selected,
                                onCheckedChange = { checked ->
                                    if (checked || !requireSelection || selected.size > 1) {
                                        selected = if (checked) selected + field else selected - field
                                    }
                                },
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(enabled = index > 0, onClick = { order = order.moveItem(index, index - 1) }) { Icon(Icons.Filled.ArrowUpward, "上移") }
                                IconButton(enabled = index < order.lastIndex, onClick = { order = order.moveItem(index, index + 1) }) { Icon(Icons.Filled.ArrowDownward, "下移") }
                            }
                        },
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            TextButton(onClick = {
                val fields = order.filter { it in selected }
                if (!requireSelection || fields.isNotEmpty()) onConfirm(fields)
            }) { Text("确定") }
        },
    )
}

@Composable
fun LyricMaterial(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, onBack: () -> Unit) =
    LyricMaterial(LyricIslandConfig(enabled = enabled), { onEnabledChange(it.enabled) }, onBack)
