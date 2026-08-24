package io.github.superisland.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import com.lidesheng.hyperlyric.ui.page.hooksettings.media.preview.MediaPreviewCard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import io.github.superisland.source.lyric.AlwaysOnDisplayMediaCardConfig
import io.github.superisland.source.lyric.IslandExpandedMediaCardConfig
import io.github.superisland.source.lyric.LyricIslandConfig
import io.github.superisland.source.lyric.MediaCardConstants
import io.github.superisland.source.lyric.NotificationMediaCardConfig
import kotlin.math.roundToInt

/**
 * Media-card subpages are owned by the lyric screen so its top-bar and system back navigation
 * can return to this directory before leaving the media-card feature.
 */
enum class MediaCardConfigurationPage(val title: String) {
    DIRECTORY("媒体卡片"),
    NOTIFICATION("通知中心"),
    ISLAND_EXPANDED("超级岛"),
    AOD("息屏显示"),
}

private val mediaLayoutLabels = listOf("系统默认", "iOS 风格", "ColorOS 风格", "One UI 风格", "MIUI 风格", "PixelOS 风格")
private val mediaLayoutValues = listOf(
    MediaCardConstants.LAYOUT_SYSTEM,
    MediaCardConstants.LAYOUT_IOS,
    MediaCardConstants.LAYOUT_COLOROS,
    MediaCardConstants.LAYOUT_ONE_UI,
    MediaCardConstants.LAYOUT_MIUI,
    MediaCardConstants.LAYOUT_PIXEL,
)
private val mediaThemeValues = listOf(
    MediaCardConstants.THEME_FOLLOW_SYSTEM,
    MediaCardConstants.THEME_ALWAYS_LIGHT,
    MediaCardConstants.THEME_ALWAYS_DARK,
)
private val mediaBackgroundLabels = listOf("默认", "拼贴封面", "模糊封面", "径向渐变", "线性渐变", "柔光封面")
private val mediaBackgroundValues = listOf(
    MediaCardConstants.BACKGROUND_DEFAULT,
    MediaCardConstants.BACKGROUND_COVER_ART,
    MediaCardConstants.BACKGROUND_BLURRED_COVER,
    MediaCardConstants.BACKGROUND_RADIAL_GRADIENT,
    MediaCardConstants.BACKGROUND_LINEAR_GRADIENT,
    MediaCardConstants.BACKGROUND_SOFT_COVER,
)
private val mediaCoverLabels = listOf("默认", "圆形封面", "旋转圆形封面", "隐藏封面")
private val mediaCoverValues = listOf(
    MediaCardConstants.COVER_DEFAULT,
    MediaCardConstants.COVER_CIRCLE,
    MediaCardConstants.COVER_ROTATING_CIRCLE,
    MediaCardConstants.COVER_HIDDEN,
)
private val mediaProgressLabels = listOf("默认", "波浪")
private val mediaProgressValues = listOf(MediaCardConstants.PROGRESS_DEFAULT, MediaCardConstants.PROGRESS_WAVE)
private val mediaThumbLabels = listOf("默认", "竖线", "隐藏滑块")
private val mediaThumbValues = listOf(MediaCardConstants.THUMB_DEFAULT, MediaCardConstants.THUMB_VERTICAL, MediaCardConstants.THUMB_HIDDEN)
private val mediaActionOrderLabels = listOf("默认", "自定义按钮在最右侧", "播放按钮在最左侧")
private val mediaActionOrderValues = listOf(
    MediaCardConstants.ACTION_ORDER_DEFAULT,
    MediaCardConstants.ACTION_ORDER_CUSTOM_RIGHT,
    MediaCardConstants.ACTION_ORDER_PLAY_LEFT,
)
private val mediaAmbientNotificationLabels = listOf("动态流光", "动态流光（封面色）", "封面流光", "禁用（默认）")
private val mediaAmbientNotificationValues = listOf(
    MediaCardConstants.NOTIFICATION_AMBIENT_DYNAMIC,
    MediaCardConstants.NOTIFICATION_AMBIENT_COVER_COLOR,
    MediaCardConstants.NOTIFICATION_AMBIENT_CUSTOM_FULL,
    MediaCardConstants.NOTIFICATION_AMBIENT_DISABLED,
)
private val mediaAmbientExpandedLabels = listOf("动态流光（默认）", "动态流光（封面色）", "封面流光", "禁用")
private val mediaAmbientExpandedValues = listOf(
    MediaCardConstants.ISLAND_EXPANDED_AMBIENT_DYNAMIC,
    MediaCardConstants.ISLAND_EXPANDED_AMBIENT_COVER_COLOR,
    MediaCardConstants.ISLAND_EXPANDED_AMBIENT_CUSTOM_FULL,
    MediaCardConstants.ISLAND_EXPANDED_AMBIENT_DISABLED,
)
private val mediaToneLabels = listOf("跟随系统", "浅色", "深色")
private val mediaToneValues = listOf(
    MediaCardConstants.SOFT_COVER_FOLLOW_SYSTEM,
    MediaCardConstants.SOFT_COVER_LIGHT,
    MediaCardConstants.SOFT_COVER_DARK,
)

@Composable
private fun MediaSectionTitle(text: String) {
    SmallTitle(
        text = text,
        insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
    )
}

@Composable
internal fun MediaCardConfigurationMiuix(
    config: LyricIslandConfig,
    page: MediaCardConfigurationPage,
    onPageChange: (MediaCardConfigurationPage) -> Unit,
    onConfigChange: (LyricIslandConfig) -> Unit,
) {
    val set: (LyricIslandConfig) -> Unit = { onConfigChange(it.normalized()) }
    val mediaCardEnabled = config.mediaCard.enabled
    if (page == MediaCardConfigurationPage.DIRECTORY) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "启用媒体卡片",
                    checked = mediaCardEnabled,
                    onCheckedChange = { value ->
                        set(config.copy(mediaCard = config.mediaCard.copy(enabled = value)))
                    },
                )
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(title = "通知中心", enabled = mediaCardEnabled, onClick = { onPageChange(MediaCardConfigurationPage.NOTIFICATION) })
                ArrowPreference(title = "超级岛", enabled = mediaCardEnabled, onClick = { onPageChange(MediaCardConfigurationPage.ISLAND_EXPANDED) })
                ArrowPreference(title = "息屏显示", enabled = mediaCardEnabled, onClick = { onPageChange(MediaCardConfigurationPage.AOD) })
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "移除下拉小窗白名单",
                    summary = "超级岛媒体卡片",
                    checked = config.mediaCard.removeIslandWhitelist,
                    enabled = mediaCardEnabled,
                    onCheckedChange = { value ->
                        set(config.copy(mediaCard = config.mediaCard.copy(removeIslandWhitelist = value)))
                    },
                )
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "多媒体卡片切换功能",
                    summary = "通知中心媒体卡片",
                    checked = config.mediaCard.notification.cardSwitcherEnabled,
                    enabled = mediaCardEnabled,
                    onCheckedChange = { value ->
                        set(
                            config.copy(
                                mediaCard = config.mediaCard.copy(
                                    notification = config.mediaCard.notification.copy(cardSwitcherEnabled = value),
                                ),
                            ),
                        )
                    },
                )
                if (mediaCardEnabled && config.mediaCard.notification.cardSwitcherEnabled) {
                    val notification = config.mediaCard.notification
                    MediaDropdown(
                        title = "卡片显示模式",
                        items = listOf("单卡片视图", "多卡片视图"),
                        selected = notification.cardSwitcherMode,
                        enabled = mediaCardEnabled,
                    ) { mode ->
                        set(
                            config.copy(
                                mediaCard = config.mediaCard.copy(
                                    notification = notification.copy(cardSwitcherMode = mode),
                                ),
                            ),
                        )
                    }
                    if (notification.cardSwitcherMode == MediaCardConstants.CARD_SWITCHER_MULTI) {
                        MediaSlider(
                            title = "卡片显示数量上限",
                            value = notification.cardSwitcherMaxCount.toFloat(),
                            range = 2f..6f,
                            label = notification.cardSwitcherMaxCount.toString(),
                            enabled = mediaCardEnabled,
                        ) { maxCount ->
                            set(
                                config.copy(
                                    mediaCard = config.mediaCard.copy(
                                        notification = notification.copy(
                                            cardSwitcherMaxCount = maxCount.roundToInt(),
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
        return
    }
    when (page) {
        MediaCardConfigurationPage.NOTIFICATION -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaNotificationPreview(config.mediaCard.notification)
            MediaNotificationSettings(config.mediaCard.notification, mediaCardEnabled) { value -> set(config.copy(mediaCard = config.mediaCard.copy(notification = value))) }
        }
        MediaCardConfigurationPage.ISLAND_EXPANDED -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaIslandPreview(config.mediaCard.islandExpanded)
            MediaIslandSettings(config.mediaCard.islandExpanded, mediaCardEnabled) { value -> set(config.copy(mediaCard = config.mediaCard.copy(islandExpanded = value))) }
        }
        MediaCardConfigurationPage.AOD -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "禁用媒体卡片折叠",
                    checked = config.mediaCard.alwaysOnDisplay.disableMediaCardCollapsing,
                    enabled = mediaCardEnabled,
                    onCheckedChange = { value -> set(config.copy(mediaCard = config.mediaCard.copy(alwaysOnDisplay = AlwaysOnDisplayMediaCardConfig(value)))) },
                )
            }
        }
        MediaCardConfigurationPage.DIRECTORY -> Unit
    }
}

@Composable
private fun MediaNotificationPreview(value: NotificationMediaCardConfig) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MediaPreviewCard(
            showShadow = !value.hideCoverShadow,
            coverStyle = value.coverStyle,
            hideCoverSource = value.hideCoverSource,
            disableCoverFlip = value.disableCoverFlip,
            hideDeviceSwitch = value.hideDeviceSwitch,
            hideCustomActions = value.hideCustomActions,
            hideTime = value.hideTime,
            actionOrder = value.actionOrder,
            actionAlignLeft = value.actionAlignLeft,
            cardTheme = value.cardTheme,
            backgroundStyle = value.backgroundStyle,
            backgroundBlur = value.backgroundBlur,
            softCoverTone = value.softCoverTone,
            ambientFlowMode = value.ambientFlowMode,
            waveProgress = value.progressStyle == MediaCardConstants.PROGRESS_WAVE,
            verticalProgressThumb = value.thumbStyle == MediaCardConstants.THUMB_VERTICAL,
            hideProgressThumb = value.thumbStyle == MediaCardConstants.THUMB_HIDDEN,
        )
    }
}

@Composable
private fun MediaIslandPreview(value: IslandExpandedMediaCardConfig) {
    val previewAmbientFlowMode = when (value.ambientFlowMode) {
        MediaCardConstants.ISLAND_EXPANDED_AMBIENT_DYNAMIC -> MediaCardConstants.NOTIFICATION_AMBIENT_DYNAMIC
        MediaCardConstants.ISLAND_EXPANDED_AMBIENT_DISABLED -> MediaCardConstants.NOTIFICATION_AMBIENT_DISABLED
        MediaCardConstants.ISLAND_EXPANDED_AMBIENT_COVER_COLOR -> MediaCardConstants.NOTIFICATION_AMBIENT_COVER_COLOR
        MediaCardConstants.ISLAND_EXPANDED_AMBIENT_CUSTOM_FULL -> MediaCardConstants.NOTIFICATION_AMBIENT_CUSTOM_FULL
        else -> MediaCardConstants.NOTIFICATION_AMBIENT_DISABLED
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MediaPreviewCard(
            showShadow = false,
            coverStyle = value.coverStyle,
            hideCoverSource = value.hideCoverSource,
            disableCoverFlip = value.disableCoverFlip,
            hideDeviceSwitch = value.hideDeviceSwitch,
            hideCustomActions = value.hideCustomActions,
            hideTime = value.hideTime,
            actionOrder = value.actionOrder,
            actionAlignLeft = value.actionAlignLeft,
            cardTheme = value.cardTheme,
            backgroundStyle = value.backgroundStyle,
            backgroundBlur = value.backgroundBlur,
            softCoverTone = value.softCoverTone,
            ambientFlowMode = previewAmbientFlowMode,
            waveProgress = value.progressStyle == MediaCardConstants.PROGRESS_WAVE,
            verticalProgressThumb = value.thumbStyle == MediaCardConstants.THUMB_VERTICAL,
            hideProgressThumb = value.thumbStyle == MediaCardConstants.THUMB_HIDDEN,
        )
    }
}

@Composable
private fun MediaNotificationSettings(
    value: NotificationMediaCardConfig,
    enabled: Boolean,
    onChange: (NotificationMediaCardConfig) -> Unit,
) {
    MediaCardBackground(value.backgroundStyle, value.cardTheme, value.ambientFlowMode, value.backgroundColorAnimation, value.backgroundBlur, value.backgroundAutoInvert, value.softCoverTone, { onChange(value.copy(backgroundStyle = it)) }, { onChange(value.copy(cardTheme = it)) }, { onChange(value.copy(ambientFlowMode = it)) }, { onChange(value.copy(backgroundColorAnimation = it)) }, { onChange(value.copy(backgroundBlur = it)) }, { onChange(value.copy(backgroundAutoInvert = it)) }, { onChange(value.copy(softCoverTone = it)) }, enabled = enabled, expanded = false)
    MediaSectionTitle(text = "布局")
    MediaCard(modifier = Modifier.fillMaxWidth()) {
        MediaDropdown("卡片布局样式", mediaLayoutLabels, mediaLayoutValues.indexOf(value.layoutStyle), enabled) { onChange(value.copy(layoutStyle = mediaLayoutValues[it])) }
        SwitchPreference(title = "动作按钮左对齐", checked = value.actionAlignLeft, enabled = enabled, onCheckedChange = { onChange(value.copy(actionAlignLeft = it)) })
        MediaDropdown("动作按钮顺序", mediaActionOrderLabels, mediaActionOrderValues.indexOf(value.actionOrder), enabled) { onChange(value.copy(actionOrder = mediaActionOrderValues[it])) }
    }
    MediaSectionTitle(text = "元素")
    MediaCard(modifier = Modifier.fillMaxWidth()) {
        MediaDropdown("音频封面样式", mediaCoverLabels, mediaCoverValues.indexOf(value.coverStyle), enabled) { onChange(value.copy(coverStyle = mediaCoverValues[it])) }
        SwitchPreference(title = "隐藏音频封面的来源标识", checked = value.hideCoverSource, enabled = enabled, onCheckedChange = { onChange(value.copy(hideCoverSource = it)) })
        SwitchPreference(title = "隐藏音频封面阴影", checked = value.hideCoverShadow, enabled = enabled, onCheckedChange = { onChange(value.copy(hideCoverShadow = it)) })
        SwitchPreference(title = "禁用音频封面翻转动画", checked = value.disableCoverFlip, enabled = enabled, onCheckedChange = { onChange(value.copy(disableCoverFlip = it)) })
        SwitchPreference(title = "隐藏设备切换按钮", checked = value.hideDeviceSwitch, enabled = enabled, onCheckedChange = { onChange(value.copy(hideDeviceSwitch = it)) })
        SwitchPreference(title = "隐藏自定义动作按钮", checked = value.hideCustomActions, enabled = enabled, onCheckedChange = { onChange(value.copy(hideCustomActions = it)) })
        SwitchPreference(title = "隐藏进度时间", checked = value.hideTime, enabled = enabled, onCheckedChange = { onChange(value.copy(hideTime = it)) })
    }
    MediaSectionTitle(text = "进度条")
    MediaCard(modifier = Modifier.fillMaxWidth()) {
        MediaDropdown("进度条样式", mediaProgressLabels, mediaProgressValues.indexOf(value.progressStyle), enabled) { onChange(value.copy(progressStyle = mediaProgressValues[it])) }
        if (value.progressStyle == MediaCardConstants.PROGRESS_DEFAULT) {
            SwitchPreference(title = "进度条尾部光辉", summary = "使用小米超级岛媒体卡片进度条拖尾效果", checked = value.progressHeadGlow, enabled = enabled, onCheckedChange = { onChange(value.copy(progressHeadGlow = it)) })
        } else {
            MediaDropdown("滑块样式", mediaThumbLabels, mediaThumbValues.indexOf(value.thumbStyle), enabled) { onChange(value.copy(thumbStyle = mediaThumbValues[it])) }
        }
    }
}

@Composable
private fun MediaIslandSettings(
    value: IslandExpandedMediaCardConfig,
    enabled: Boolean,
    onChange: (IslandExpandedMediaCardConfig) -> Unit,
) {
    MediaCardBackground(value.backgroundStyle, value.cardTheme, value.ambientFlowMode, value.backgroundColorAnimation, value.backgroundBlur, value.backgroundAutoInvert, value.softCoverTone, { onChange(value.copy(backgroundStyle = it)) }, { onChange(value.copy(cardTheme = it)) }, { onChange(value.copy(ambientFlowMode = it)) }, { onChange(value.copy(backgroundColorAnimation = it)) }, { onChange(value.copy(backgroundBlur = it)) }, { onChange(value.copy(backgroundAutoInvert = it)) }, { onChange(value.copy(softCoverTone = it)) }, enabled = enabled, expanded = true)
    MediaSectionTitle(text = "布局")
    MediaCard(modifier = Modifier.fillMaxWidth()) {
        MediaDropdown("卡片布局样式", mediaLayoutLabels, mediaLayoutValues.indexOf(value.layoutStyle), enabled) { onChange(value.copy(layoutStyle = mediaLayoutValues[it])) }
        SwitchPreference(title = "动作按钮左对齐", checked = value.actionAlignLeft, enabled = enabled, onCheckedChange = { onChange(value.copy(actionAlignLeft = it)) })
        MediaDropdown("动作按钮顺序", mediaActionOrderLabels, mediaActionOrderValues.indexOf(value.actionOrder), enabled) { onChange(value.copy(actionOrder = mediaActionOrderValues[it])) }
    }
    MediaSectionTitle(text = "元素")
    MediaCard(modifier = Modifier.fillMaxWidth()) {
        MediaDropdown("音频封面样式", mediaCoverLabels, mediaCoverValues.indexOf(value.coverStyle), enabled) { onChange(value.copy(coverStyle = mediaCoverValues[it])) }
        SwitchPreference(title = "隐藏音频封面的来源标识", checked = value.hideCoverSource, enabled = enabled, onCheckedChange = { onChange(value.copy(hideCoverSource = it)) })
        SwitchPreference(title = "禁用音频封面翻转动画", checked = value.disableCoverFlip, enabled = enabled, onCheckedChange = { onChange(value.copy(disableCoverFlip = it)) })
        SwitchPreference(title = "隐藏设备切换按钮", checked = value.hideDeviceSwitch, enabled = enabled, onCheckedChange = { onChange(value.copy(hideDeviceSwitch = it)) })
        SwitchPreference(title = "隐藏自定义动作按钮", checked = value.hideCustomActions, enabled = enabled, onCheckedChange = { onChange(value.copy(hideCustomActions = it)) })
        SwitchPreference(title = "隐藏进度时间", checked = value.hideTime, enabled = enabled, onCheckedChange = { onChange(value.copy(hideTime = it)) })
    }
    MediaSectionTitle(text = "进度条")
    MediaCard(modifier = Modifier.fillMaxWidth()) {
        MediaDropdown("进度条样式", mediaProgressLabels, mediaProgressValues.indexOf(value.progressStyle), enabled) { onChange(value.copy(progressStyle = mediaProgressValues[it])) }
        if (value.progressStyle == MediaCardConstants.PROGRESS_DEFAULT) {
            SwitchPreference(title = "进度条尾部光辉", checked = value.progressHeadGlow, enabled = enabled, onCheckedChange = { onChange(value.copy(progressHeadGlow = it)) })
        } else {
            MediaDropdown("滑块样式", mediaThumbLabels, mediaThumbValues.indexOf(value.thumbStyle), enabled) { onChange(value.copy(thumbStyle = mediaThumbValues[it])) }
        }
    }
}

@Composable
private fun MediaCardBackground(
    backgroundStyle: Int,
    cardTheme: Int,
    ambientFlowMode: Int,
    backgroundColorAnimation: Boolean,
    backgroundBlur: Int,
    backgroundAutoInvert: Boolean,
    softCoverTone: Int,
    onBackgroundStyleChange: (Int) -> Unit,
    onCardThemeChange: (Int) -> Unit,
    onAmbientFlowChange: (Int) -> Unit,
    onColorAnimationChange: (Boolean) -> Unit,
    onBlurChange: (Int) -> Unit,
    onAutoInvertChange: (Boolean) -> Unit,
    onToneChange: (Int) -> Unit,
    enabled: Boolean,
    expanded: Boolean,
) {
    MediaSectionTitle(text = "背景")
    MediaCard(modifier = Modifier.fillMaxWidth()) {
        MediaDropdown("卡片背景样式", mediaBackgroundLabels, mediaBackgroundValues.indexOf(backgroundStyle), enabled) { onBackgroundStyleChange(mediaBackgroundValues[it]) }
        if (backgroundStyle == MediaCardConstants.BACKGROUND_DEFAULT) {
            val themeLabels = if (expanded) {
                listOf("跟随系统", "始终浅色", "始终深色（默认）")
            } else {
                listOf("跟随系统（默认）", "始终浅色", "始终深色")
            }
            MediaDropdown("卡片背景颜色", themeLabels, mediaThemeValues.indexOf(cardTheme), enabled) { onCardThemeChange(mediaThemeValues[it]) }
            MediaDropdown("卡片动态流光", if (expanded) mediaAmbientExpandedLabels else mediaAmbientNotificationLabels, (if (expanded) mediaAmbientExpandedValues else mediaAmbientNotificationValues).indexOf(ambientFlowMode), enabled) { onAmbientFlowChange((if (expanded) mediaAmbientExpandedValues else mediaAmbientNotificationValues)[it]) }
        } else {
            if (backgroundStyle == MediaCardConstants.BACKGROUND_SOFT_COVER) {
                MediaDropdown("柔光封面明暗", mediaToneLabels, mediaToneValues.indexOf(softCoverTone), enabled) { onToneChange(mediaToneValues[it]) }
            }
            SwitchPreference(title = "背景切换动画", checked = backgroundColorAnimation, enabled = enabled, onCheckedChange = onColorAnimationChange)
            if (backgroundStyle == MediaCardConstants.BACKGROUND_BLURRED_COVER) {
                MediaSlider("背景模糊强度", backgroundBlur.toFloat(), 1f..20f, backgroundBlur.toString(), enabled) { onBlurChange(it.roundToInt()) }
            }
            if (backgroundStyle == MediaCardConstants.BACKGROUND_LINEAR_GRADIENT) {
                SwitchPreference(title = "亮色封面自动反色", checked = backgroundAutoInvert, enabled = enabled, onCheckedChange = onAutoInvertChange)
            }
        }
    }
}

@Composable
private fun MediaCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, content = content)
}

@Composable
private fun MediaDropdown(title: String, items: List<String>, selected: Int, enabled: Boolean, onSelected: (Int) -> Unit) {
    OverlayDropdownPreference(title = title, items = items, selectedIndex = selected.coerceIn(0, items.lastIndex), enabled = enabled, onSelectedIndexChange = onSelected)
}

@Composable
private fun MediaSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, enabled: Boolean, onFinished: (Float) -> Unit) {
    var sliderValue by remember(title, value) { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title)
            Text(label)
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onFinished(sliderValue) },
            valueRange = range,
            steps = (range.endInclusive - range.start).roundToInt().coerceAtLeast(1) - 1,
            enabled = enabled,
            showKeyPoints = true,
            keyPoints = listOf(range.start, (range.start + range.endInclusive) / 2f, range.endInclusive),
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            magnetThreshold = 0f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
