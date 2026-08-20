package io.github.superisland.ui

import io.github.superisland.design.DirectoryEntryUi
import io.github.superisland.design.DirectoryGroupUi
import io.github.superisland.design.DirectoryIcon

/** Shared root-directory contracts. Both skins render these exact entries and route ids. */
fun superIslandDirectoryGroups(): List<DirectoryGroupUi> =
    listOf(
        DirectoryGroupUi(
            title = "内容与触发",
            entries =
                listOf(
                    DirectoryEntryUi("monitor", "常驻超级岛", "通过超级岛常驻显示设备实时状态", DirectoryIcon.MONITOR, showChevron = false),
                    DirectoryEntryUi("smart_capsule", "超级岛通知", "将你允许的应用通知显示到超级岛", DirectoryIcon.NOTIFICATION, showChevron = false),
                    DirectoryEntryUi("media_island", "超级岛音乐", "管理已允许播放器的媒体会话与歌词入口", DirectoryIcon.MEDIA, showChevron = false),
                ),
        ),
        DirectoryGroupUi(
            title = "外观定制",
            entries =
                listOf(
                    DirectoryEntryUi("capsule_appearance", "胶囊定制", "调整胶囊展示、最小化与多岛切换方式", DirectoryIcon.APPEARANCE),
                    DirectoryEntryUi("card_appearance", "卡片定制", "自定义展开卡片的背景和描边样式", DirectoryIcon.APPEARANCE, enabled = false, showChevron = false),
                    DirectoryEntryUi("size_layout", "尺寸与卡片布局", "调整胶囊尺寸与展开卡片布局参数", DirectoryIcon.APPEARANCE, enabled = false, showChevron = false),
                ),
        ),
    )

fun extensionsDirectoryGroups(): List<DirectoryGroupUi> =
    listOf(
        DirectoryGroupUi(
            title = "录制与存储",
            entries =
                listOf(
                    DirectoryEntryUi(
                        id = "screen_recording",
                        title = "超级岛录屏",
                        summary = "录制屏幕并保存到本地或所选目录",
                        icon = DirectoryIcon.MEDIA,
                    ),
                ),
        ),
        DirectoryGroupUi(
            title = "文件与分享",
            entries =
                listOf(
                    DirectoryEntryUi(
                        id = "mishare_folder",
                        title = "小米互传文件夹",
                        summary = "使用 MT 管理器打开接收文件夹",
                        icon = DirectoryIcon.DEVICE,
                    ),
                ),
        ),
    )

fun profileDirectoryGroups(): List<DirectoryGroupUi> =
    listOf(
        DirectoryGroupUi(
            title = "帮助与信息",
            entries =
                listOf(
                    DirectoryEntryUi("usage_guide", "使用指南", "了解运行环境、权限和各项超级岛功能的使用范围", DirectoryIcon.INFO),
                    DirectoryEntryUi("about", "关于超级岛", "版本、隐私边界和开源组件说明", DirectoryIcon.INFO),
                ),
        ),
    )
