package io.github.superisland.ui.lyric

import androidx.compose.runtime.Composable
import io.github.superisland.design.AppScaffold
import io.github.superisland.design.AppFeatureMasterSwitch

@Composable
fun LyricMiuix(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    AppScaffold(title = "超级岛歌词", largeTitle = "超级岛歌词", onBack = onBack) { padding ->
        AppFeatureMasterSwitch(
            title = "启用超级岛歌词",
            summary = "通过 SuperLyric 显示逐字歌词",
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
}
