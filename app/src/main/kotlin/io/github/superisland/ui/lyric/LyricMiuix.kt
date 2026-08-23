package io.github.superisland.ui.lyric

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import io.github.superisland.design.AppFeatureMasterSwitch
import io.github.superisland.design.AppScaffold
import io.github.superisland.design.LyricConfigurationMiuix
import io.github.superisland.design.LyricConfigSection
import io.github.superisland.design.LyricSectionEntryPageMiuix
import io.github.superisland.source.lyric.LyricIslandConfig
import androidx.compose.foundation.verticalScroll

@Composable
fun LyricMiuix(
    config: LyricIslandConfig,
    onConfigChange: (LyricIslandConfig) -> Unit,
    onBack: () -> Unit,
) {
    var section by remember { mutableStateOf<LyricConfigSection?>(null) }
    val selected = section
    AppScaffold(
        title = selected?.title ?: "小米超级岛歌词",
        largeTitle = selected?.title ?: "小米超级岛歌词",
        subtitle = "",
        onBack = { if (selected == null) onBack() else section = null },
    ) { paddingValues ->
        if (selected == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                AppFeatureMasterSwitch(
                    title = "启用",
                    summary = if (config.enabled) "正在通过超级岛显示歌词" else "关闭后不会发布歌词超级岛",
                    checked = config.enabled,
                    onCheckedChange = { onConfigChange(config.withEnabled(it)) },
                )
                LyricSectionEntryPageMiuix(
                    config = config,
                    enabled = config.enabled,
                    onSectionSelected = { section = it },
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .alpha(if (config.enabled) 1f else 0.55f),
            ) {
                LyricConfigurationMiuix(
                    config = config,
                    onConfigChange = onConfigChange,
                    section = selected,
                    enabled = config.enabled,
                )
            }
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
