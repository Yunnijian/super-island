package io.github.superisland.design

import android.content.pm.PackageInfo

data class SmartCapsuleAppProfileUi(
    val packageName: String,
    val appLabel: String,
    val packageInfo: PackageInfo?,
    val appUid: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val enabled: Boolean,
    val enabledSummary: String,
    val focusOnly: Boolean,
    val priorityOptions: List<IslandPriorityOptionUi>,
    val selectedPriorityId: String,
    val allChannels: Boolean,
    val allChannelsEnabled: Boolean,
    val channels: List<SmartCapsuleAppProfileChannelUi>,
    val channelsReady: Boolean,
)

data class SmartCapsuleAppProfileChannelUi(
    val id: String,
    val title: String,
    val summary: String,
    val status: String?,
    val enabled: Boolean,
)
