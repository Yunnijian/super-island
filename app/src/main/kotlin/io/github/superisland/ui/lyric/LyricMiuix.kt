package io.github.superisland.ui.lyric

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.superisland.design.AppFeatureMasterSwitch
import io.github.superisland.design.AppScaffold
import io.github.superisland.design.LyricConfigurationMiuix
import io.github.superisland.source.lyric.LyricIslandConfig

@Composable
fun LyricMiuix(
    config: LyricIslandConfig,
    onConfigChange: (LyricIslandConfig) -> Unit,
    onBack: () -> Unit,
) {
    AppScaffold(title = "小米超级岛歌词", largeTitle = "小米超级岛歌词", subtitle = "", onBack = onBack) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            AppFeatureMasterSwitch(
                title = "启用",
                summary = "",
                checked = config.enabled,
                onCheckedChange = { onConfigChange(config.withEnabled(it)) },
            )
            LyricConfigurationMiuix(
                config = config,
                onConfigChange = onConfigChange,
            )
        }
    }
}

/** Compatibility overload for callers compiled before the rich config screen. */
@Composable
fun LyricMiuix(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, onBack: () -> Unit) =
    LyricMiuix(
        config = LyricIslandConfig(enabled = enabled),
        onConfigChange = { onEnabledChange(it.enabled) },
        onBack = onBack,
    )
