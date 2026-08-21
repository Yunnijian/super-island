package io.github.superisland.ui.lyric

import androidx.compose.runtime.Composable
import io.github.superisland.design.AppFeatureMasterSwitch
import io.github.superisland.design.AppScaffold

@Composable
fun LyricMiuix(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    AppScaffold(title = "超级岛歌词", largeTitle = "超级岛歌词", subtitle = "", onBack = onBack) {
        AppFeatureMasterSwitch(
            title = "启用超级岛歌词",
            summary = "通过 SuperLyric 在超级岛显示逐字歌词",
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}
