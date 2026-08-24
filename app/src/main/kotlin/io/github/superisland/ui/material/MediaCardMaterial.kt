package io.github.superisland.ui.material

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.superisland.ui.material.preview.MaterialMediaPreviewCard
import io.github.superisland.source.lyric.AlwaysOnDisplayMediaCardConfig
import io.github.superisland.source.lyric.IslandExpandedMediaCardConfig
import io.github.superisland.source.lyric.LyricIslandConfig
import io.github.superisland.source.lyric.MediaCardConstants
import io.github.superisland.source.lyric.NotificationMediaCardConfig
import kotlin.math.roundToInt
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem

private enum class MediaCardMaterialGroup { NOTIFICATION, ISLAND_EXPANDED, AOD }

private val mediaLayoutLabels = listOf(
    "系统默认",
    "iOS 风格",
    "ColorOS 风格",
    "One UI 风格",
    "MIUI 风格",
    "PixelOS 风格",
)
private val mediaLayoutValues = listOf(
    MediaCardConstants.LAYOUT_SYSTEM,
    MediaCardConstants.LAYOUT_IOS,
    MediaCardConstants.LAYOUT_COLOROS,
    MediaCardConstants.LAYOUT_ONE_UI,
    MediaCardConstants.LAYOUT_MIUI,
    MediaCardConstants.LAYOUT_PIXEL,
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
private val mediaThumbLabels = listOf("默认", "竖线", "隐藏滑块")
private val mediaThumbValues = listOf(
    MediaCardConstants.THUMB_DEFAULT,
    MediaCardConstants.THUMB_VERTICAL,
    MediaCardConstants.THUMB_HIDDEN,
)
private val mediaActionOrderLabels = listOf("默认", "自定义按钮在最右侧", "播放按钮在最左侧")
private val mediaActionOrderValues = listOf(
    MediaCardConstants.ACTION_ORDER_DEFAULT,
    MediaCardConstants.ACTION_ORDER_CUSTOM_RIGHT,
    MediaCardConstants.ACTION_ORDER_PLAY_LEFT,
)

@Composable
internal fun MediaCardConfigurationMaterial(
    config: LyricIslandConfig,
    enabled: Boolean,
    onConfigChange: (LyricIslandConfig) -> Unit,
) {
    var group by remember { mutableStateOf<MediaCardMaterialGroup?>(null) }
    BackHandler(enabled = group != null) { group = null }
    val set: (LyricIslandConfig) -> Unit = { if (enabled) onConfigChange(it.normalized()) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (group) {
            null -> MediaCardDirectoryMaterial(config, enabled, set, onOpenGroup = { group = it })
            MediaCardMaterialGroup.NOTIFICATION -> {
                MediaNotificationPreview(config.mediaCard.notification)
                MediaNotificationMaterial(
                    value = config.mediaCard.notification,
                    enabled = enabled,
                    onChange = { value ->
                        set(config.copy(mediaCard = config.mediaCard.copy(notification = value)))
                    },
                )
            }
            MediaCardMaterialGroup.ISLAND_EXPANDED -> {
                MediaIslandPreview(config.mediaCard.islandExpanded)
                MediaIslandMaterial(
                    value = config.mediaCard.islandExpanded,
                    enabled = enabled,
                    onChange = { value ->
                        set(config.copy(mediaCard = config.mediaCard.copy(islandExpanded = value)))
                    },
                )
            }
            MediaCardMaterialGroup.AOD -> SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                content = listOf(
                    {
                        MediaCardSwitch(
                            title = "禁用媒体卡片折叠",
                            checked = config.mediaCard.alwaysOnDisplay.disableMediaCardCollapsing,
                            enabled = enabled,
                        ) { value ->
                            set(
                                config.copy(
                                    mediaCard = config.mediaCard.copy(
                                        alwaysOnDisplay = AlwaysOnDisplayMediaCardConfig(value),
                                    ),
                                ),
                            )
                        }
                    },
                ),
            )
        }
    }
}

@Composable
private fun MediaNotificationPreview(value: NotificationMediaCardConfig) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MaterialMediaPreviewCard(
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
        MaterialMediaPreviewCard(
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
private fun MediaCardDirectoryMaterial(
    config: LyricIslandConfig,
    enabled: Boolean,
    onConfigChange: (LyricIslandConfig) -> Unit,
    onOpenGroup: (MediaCardMaterialGroup) -> Unit,
) {
    val notification = config.mediaCard.notification
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        content = listOf(
            { MediaCardEntry("通知中心", enabled) { onOpenGroup(MediaCardMaterialGroup.NOTIFICATION) } },
            { MediaCardEntry("超级岛", enabled) { onOpenGroup(MediaCardMaterialGroup.ISLAND_EXPANDED) } },
            { MediaCardEntry("息屏显示", enabled) { onOpenGroup(MediaCardMaterialGroup.AOD) } },
        ),
    )
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        content = listOf(
            {
                MediaCardSwitch(
                    title = "移除下拉小窗白名单",
                    summary = "超级岛媒体卡片",
                    checked = config.mediaCard.removeIslandWhitelist,
                    enabled = enabled,
                ) { value ->
                    onConfigChange(
                        config.copy(mediaCard = config.mediaCard.copy(removeIslandWhitelist = value)),
                    )
                }
            },
        ),
    )
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        content = buildList {
            add {
                MediaCardSwitch(
                    title = "多媒体卡片切换功能",
                    summary = "通知中心媒体卡片",
                    checked = notification.cardSwitcherEnabled,
                    enabled = enabled,
                ) { value ->
                    onConfigChange(
                        config.copy(
                            mediaCard = config.mediaCard.copy(
                                notification = notification.copy(cardSwitcherEnabled = value),
                            ),
                        ),
                    )
                }
            }
            if (notification.cardSwitcherEnabled) {
                add {
                    MediaCardDropdown(
                        title = "卡片显示模式",
                        items = listOf("单卡片视图", "多卡片视图"),
                        selectedIndex = notification.cardSwitcherMode,
                        enabled = enabled,
                    ) { mode ->
                        onConfigChange(
                            config.copy(
                                mediaCard = config.mediaCard.copy(
                                    notification = notification.copy(cardSwitcherMode = mode),
                                ),
                            ),
                        )
                    }
                }
                if (notification.cardSwitcherMode == MediaCardConstants.CARD_SWITCHER_MULTI) {
                    add {
                        MaterialInlineSliderItem(
                            title = "卡片显示数量上限",
                            value = notification.cardSwitcherMaxCount.toFloat(),
                            valueRange = 2f..6f,
                            enabled = enabled,
                            valueLabel = notification.cardSwitcherMaxCount.toString(),
                            onValueChangeFinished = { maxCount ->
                                onConfigChange(
                                    config.copy(
                                        mediaCard = config.mediaCard.copy(
                                            notification = notification.copy(
                                                cardSwitcherMaxCount = maxCount.roundToInt(),
                                            ),
                                        ),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun MediaNotificationMaterial(
    value: NotificationMediaCardConfig,
    enabled: Boolean,
    onChange: (NotificationMediaCardConfig) -> Unit,
) {
    MediaCardBackgroundMaterial(
        backgroundStyle = value.backgroundStyle,
        cardTheme = value.cardTheme,
        ambientFlowMode = value.ambientFlowMode,
        backgroundColorAnimation = value.backgroundColorAnimation,
        backgroundBlur = value.backgroundBlur,
        backgroundAutoInvert = value.backgroundAutoInvert,
        softCoverTone = value.softCoverTone,
        enabled = enabled,
        expanded = false,
        onBackgroundStyleChange = { onChange(value.copy(backgroundStyle = it)) },
        onCardThemeChange = { onChange(value.copy(cardTheme = it)) },
        onAmbientFlowChange = { onChange(value.copy(ambientFlowMode = it)) },
        onColorAnimationChange = { onChange(value.copy(backgroundColorAnimation = it)) },
        onBlurChange = { onChange(value.copy(backgroundBlur = it)) },
        onAutoInvertChange = { onChange(value.copy(backgroundAutoInvert = it)) },
        onToneChange = { onChange(value.copy(softCoverTone = it)) },
    )
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = "元素",
        content = buildList {
            add { MediaCoverDropdown(value.coverStyle, enabled) { onChange(value.copy(coverStyle = it)) } }
            add { MediaCardSwitch("隐藏音频封面的来源标识", checked = value.hideCoverSource, enabled = enabled) { onChange(value.copy(hideCoverSource = it)) } }
            add { MediaCardSwitch("隐藏音频封面阴影", checked = value.hideCoverShadow, enabled = enabled) { onChange(value.copy(hideCoverShadow = it)) } }
            add { MediaCardSwitch("禁用音频封面翻转动画", checked = value.disableCoverFlip, enabled = enabled) { onChange(value.copy(disableCoverFlip = it)) } }
            add { MediaCardSwitch("隐藏设备切换按钮", checked = value.hideDeviceSwitch, enabled = enabled) { onChange(value.copy(hideDeviceSwitch = it)) } }
            add { MediaCardSwitch("隐藏自定义动作按钮", checked = value.hideCustomActions, enabled = enabled) { onChange(value.copy(hideCustomActions = it)) } }
            add { MediaCardSwitch("隐藏进度时间", checked = value.hideTime, enabled = enabled) { onChange(value.copy(hideTime = it)) } }
        },
    )
    MediaProgressMaterial(
        progressStyle = value.progressStyle,
        progressHeadGlow = value.progressHeadGlow,
        thumbStyle = value.thumbStyle,
        enabled = enabled,
        onProgressStyleChange = { onChange(value.copy(progressStyle = it)) },
        onProgressHeadGlowChange = { onChange(value.copy(progressHeadGlow = it)) },
        onThumbStyleChange = { onChange(value.copy(thumbStyle = it)) },
    )
    MediaLayoutMaterial(
        layoutStyle = value.layoutStyle,
        actionAlignLeft = value.actionAlignLeft,
        actionOrder = value.actionOrder,
        enabled = enabled,
        onLayoutStyleChange = { onChange(value.copy(layoutStyle = it)) },
        onActionAlignLeftChange = { onChange(value.copy(actionAlignLeft = it)) },
        onActionOrderChange = { onChange(value.copy(actionOrder = it)) },
    )
}

@Composable
private fun MediaIslandMaterial(
    value: IslandExpandedMediaCardConfig,
    enabled: Boolean,
    onChange: (IslandExpandedMediaCardConfig) -> Unit,
) {
    MediaCardBackgroundMaterial(
        backgroundStyle = value.backgroundStyle,
        cardTheme = value.cardTheme,
        ambientFlowMode = value.ambientFlowMode,
        backgroundColorAnimation = value.backgroundColorAnimation,
        backgroundBlur = value.backgroundBlur,
        backgroundAutoInvert = value.backgroundAutoInvert,
        softCoverTone = value.softCoverTone,
        enabled = enabled,
        expanded = true,
        onBackgroundStyleChange = { onChange(value.copy(backgroundStyle = it)) },
        onCardThemeChange = { onChange(value.copy(cardTheme = it)) },
        onAmbientFlowChange = { onChange(value.copy(ambientFlowMode = it)) },
        onColorAnimationChange = { onChange(value.copy(backgroundColorAnimation = it)) },
        onBlurChange = { onChange(value.copy(backgroundBlur = it)) },
        onAutoInvertChange = { onChange(value.copy(backgroundAutoInvert = it)) },
        onToneChange = { onChange(value.copy(softCoverTone = it)) },
    )
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = "元素",
        content = buildList {
            add { MediaCoverDropdown(value.coverStyle, enabled) { onChange(value.copy(coverStyle = it)) } }
            add { MediaCardSwitch("隐藏音频封面的来源标识", checked = value.hideCoverSource, enabled = enabled) { onChange(value.copy(hideCoverSource = it)) } }
            add { MediaCardSwitch("禁用音频封面翻转动画", checked = value.disableCoverFlip, enabled = enabled) { onChange(value.copy(disableCoverFlip = it)) } }
            add { MediaCardSwitch("隐藏设备切换按钮", checked = value.hideDeviceSwitch, enabled = enabled) { onChange(value.copy(hideDeviceSwitch = it)) } }
            add { MediaCardSwitch("隐藏自定义动作按钮", checked = value.hideCustomActions, enabled = enabled) { onChange(value.copy(hideCustomActions = it)) } }
            add { MediaCardSwitch("隐藏进度时间", checked = value.hideTime, enabled = enabled) { onChange(value.copy(hideTime = it)) } }
        },
    )
    MediaProgressMaterial(
        progressStyle = value.progressStyle,
        progressHeadGlow = value.progressHeadGlow,
        thumbStyle = value.thumbStyle,
        enabled = enabled,
        onProgressStyleChange = { onChange(value.copy(progressStyle = it)) },
        onProgressHeadGlowChange = { onChange(value.copy(progressHeadGlow = it)) },
        onThumbStyleChange = { onChange(value.copy(thumbStyle = it)) },
    )
    MediaLayoutMaterial(
        layoutStyle = value.layoutStyle,
        actionAlignLeft = value.actionAlignLeft,
        actionOrder = value.actionOrder,
        enabled = enabled,
        onLayoutStyleChange = { onChange(value.copy(layoutStyle = it)) },
        onActionAlignLeftChange = { onChange(value.copy(actionAlignLeft = it)) },
        onActionOrderChange = { onChange(value.copy(actionOrder = it)) },
    )
}

@Composable
private fun MediaCardBackgroundMaterial(
    backgroundStyle: Int,
    cardTheme: Int,
    ambientFlowMode: Int,
    backgroundColorAnimation: Boolean,
    backgroundBlur: Int,
    backgroundAutoInvert: Boolean,
    softCoverTone: Int,
    enabled: Boolean,
    expanded: Boolean,
    onBackgroundStyleChange: (Int) -> Unit,
    onCardThemeChange: (Int) -> Unit,
    onAmbientFlowChange: (Int) -> Unit,
    onColorAnimationChange: (Boolean) -> Unit,
    onBlurChange: (Int) -> Unit,
    onAutoInvertChange: (Boolean) -> Unit,
    onToneChange: (Int) -> Unit,
) {
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = "背景",
        content = buildList {
            add {
                MediaCardDropdown(
                    title = "卡片背景样式",
                    items = mediaBackgroundLabels,
                    selectedIndex = mediaBackgroundValues.indexOf(backgroundStyle),
                    enabled = enabled,
                ) { onBackgroundStyleChange(mediaBackgroundValues[it]) }
            }
            if (backgroundStyle == MediaCardConstants.BACKGROUND_DEFAULT) {
                val themeLabels = if (expanded) {
                    listOf("跟随系统", "始终浅色", "始终深色（默认）")
                } else {
                    listOf("跟随系统（默认）", "始终浅色", "始终深色")
                }
                val ambientValues = if (expanded) {
                    listOf(
                        MediaCardConstants.ISLAND_EXPANDED_AMBIENT_DYNAMIC,
                        MediaCardConstants.ISLAND_EXPANDED_AMBIENT_COVER_COLOR,
                        MediaCardConstants.ISLAND_EXPANDED_AMBIENT_CUSTOM_FULL,
                        MediaCardConstants.ISLAND_EXPANDED_AMBIENT_DISABLED,
                    )
                } else {
                    listOf(
                        MediaCardConstants.NOTIFICATION_AMBIENT_DYNAMIC,
                        MediaCardConstants.NOTIFICATION_AMBIENT_COVER_COLOR,
                        MediaCardConstants.NOTIFICATION_AMBIENT_CUSTOM_FULL,
                        MediaCardConstants.NOTIFICATION_AMBIENT_DISABLED,
                    )
                }
                val ambientLabels = if (expanded) {
                    listOf("动态流光（默认）", "动态流光（封面色）", "封面流光", "禁用")
                } else {
                    listOf("动态流光", "动态流光（封面色）", "封面流光", "禁用（默认）")
                }
                add {
                    MediaCardDropdown(
                        title = "卡片背景颜色",
                        items = themeLabels,
                        selectedIndex = cardTheme,
                        enabled = enabled,
                        onItemSelected = onCardThemeChange,
                    )
                }
                add {
                    MediaCardDropdown(
                        title = "卡片动态流光",
                        items = ambientLabels,
                        selectedIndex = ambientValues.indexOf(ambientFlowMode),
                        enabled = enabled,
                    ) { onAmbientFlowChange(ambientValues[it]) }
                }
            } else {
                if (backgroundStyle == MediaCardConstants.BACKGROUND_SOFT_COVER) {
                    add {
                        val toneValues = listOf(
                            MediaCardConstants.SOFT_COVER_FOLLOW_SYSTEM,
                            MediaCardConstants.SOFT_COVER_LIGHT,
                            MediaCardConstants.SOFT_COVER_DARK,
                        )
                        MediaCardDropdown(
                            title = "柔光封面明暗",
                            items = listOf("跟随系统", "浅色", "深色"),
                            selectedIndex = toneValues.indexOf(softCoverTone),
                            enabled = enabled,
                        ) { onToneChange(toneValues[it]) }
                    }
                }
                add {
                    MediaCardSwitch(
                        title = "背景切换动画",
                        checked = backgroundColorAnimation,
                        enabled = enabled,
                        onCheckedChange = onColorAnimationChange,
                    )
                }
                if (backgroundStyle == MediaCardConstants.BACKGROUND_BLURRED_COVER) {
                    add {
                        MaterialInlineSliderItem(
                            title = "背景模糊强度",
                            value = backgroundBlur.toFloat(),
                            valueRange = 1f..20f,
                            enabled = enabled,
                            valueLabel = backgroundBlur.toString(),
                            onValueChangeFinished = { onBlurChange(it.roundToInt()) },
                        )
                    }
                }
                if (backgroundStyle == MediaCardConstants.BACKGROUND_LINEAR_GRADIENT) {
                    add {
                        MediaCardSwitch(
                            title = "亮色封面自动反色",
                            checked = backgroundAutoInvert,
                            enabled = enabled,
                            onCheckedChange = onAutoInvertChange,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun MediaProgressMaterial(
    progressStyle: Int,
    progressHeadGlow: Boolean,
    thumbStyle: Int,
    enabled: Boolean,
    onProgressStyleChange: (Int) -> Unit,
    onProgressHeadGlowChange: (Boolean) -> Unit,
    onThumbStyleChange: (Int) -> Unit,
) {
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = "进度条",
        content = buildList {
            add {
                MediaCardDropdown(
                    title = "进度条样式",
                    items = listOf("默认", "波浪"),
                    selectedIndex = progressStyle,
                    enabled = enabled,
                    onItemSelected = onProgressStyleChange,
                )
            }
            if (progressStyle == MediaCardConstants.PROGRESS_DEFAULT) {
                add {
                    MediaCardSwitch(
                        title = "进度条尾部光辉",
                        summary = "使用小米超级岛媒体卡片进度条拖尾效果",
                        checked = progressHeadGlow,
                        enabled = enabled,
                        onCheckedChange = onProgressHeadGlowChange,
                    )
                }
            } else {
                add {
                    MediaCardDropdown(
                        title = "滑块样式",
                        items = mediaThumbLabels,
                        selectedIndex = mediaThumbValues.indexOf(thumbStyle),
                        enabled = enabled,
                    ) { onThumbStyleChange(mediaThumbValues[it]) }
                }
            }
        },
    )
}

@Composable
private fun MediaLayoutMaterial(
    layoutStyle: Int,
    actionAlignLeft: Boolean,
    actionOrder: Int,
    enabled: Boolean,
    onLayoutStyleChange: (Int) -> Unit,
    onActionAlignLeftChange: (Boolean) -> Unit,
    onActionOrderChange: (Int) -> Unit,
) {
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = "布局",
        content = listOf(
            {
                MediaCardDropdown(
                    title = "卡片布局样式",
                    items = mediaLayoutLabels,
                    selectedIndex = mediaLayoutValues.indexOf(layoutStyle),
                    enabled = enabled,
                ) { onLayoutStyleChange(mediaLayoutValues[it]) }
            },
            {
                MediaCardSwitch(
                    title = "动作按钮左对齐",
                    checked = actionAlignLeft,
                    enabled = enabled,
                    onCheckedChange = onActionAlignLeftChange,
                )
            },
            {
                MediaCardDropdown(
                    title = "动作按钮顺序",
                    items = mediaActionOrderLabels,
                    selectedIndex = mediaActionOrderValues.indexOf(actionOrder),
                    enabled = enabled,
                ) { onActionOrderChange(mediaActionOrderValues[it]) }
            },
        ),
    )
}

@Composable
private fun MediaCoverDropdown(
    coverStyle: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) = MediaCardDropdown(
    title = "音频封面样式",
    items = mediaCoverLabels,
    selectedIndex = mediaCoverValues.indexOf(coverStyle),
    enabled = enabled,
) { onChange(mediaCoverValues[it]) }

@Composable
private fun MediaCardEntry(title: String, enabled: Boolean, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        headlineContent = { Text(title) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "进入",
            )
        },
    )
}

@Composable
private fun MediaCardDropdown(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onItemSelected: (Int) -> Unit,
) {
    SegmentedDropdownItem(
        title = title,
        items = items,
        selectedIndex = selectedIndex.coerceIn(0, items.lastIndex),
        enabled = enabled,
        onItemSelected = onItemSelected,
    )
}

@Composable
private fun MediaCardSwitch(
    title: String,
    summary: String = "",
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (summary.isBlank()) {
        SegmentedListItem(
            onClick = { if (enabled) onCheckedChange(!checked) },
            enabled = enabled,
            headlineContent = { Text(title) },
            trailingContent = {
                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange,
                )
            },
        )
    } else {
        SegmentedSwitchItem(
            title = title,
            summary = summary,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
