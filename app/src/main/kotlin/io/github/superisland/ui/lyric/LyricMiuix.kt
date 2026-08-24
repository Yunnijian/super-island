package io.github.superisland.ui.lyric

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import io.github.superisland.design.MediaCardConfigurationPage
import io.github.superisland.source.lyric.LyricIslandConfig
import androidx.compose.foundation.verticalScroll

@Composable
fun LyricMiuix(
    config: LyricIslandConfig,
    onConfigChange: (LyricIslandConfig) -> Unit,
    onBack: () -> Unit,
) {
    var section by remember { mutableStateOf<LyricConfigSection?>(null) }
    var mediaCardPage by remember { mutableStateOf(MediaCardConfigurationPage.DIRECTORY) }
    val selected = LyricMiuixDestination(section, mediaCardPage)
    fun returnFromCurrentPage() {
        if (selected.section == LyricConfigSection.MEDIA_CARD &&
            selected.mediaCardPage != MediaCardConfigurationPage.DIRECTORY
        ) {
            mediaCardPage = MediaCardConfigurationPage.DIRECTORY
        } else {
            section = null
            mediaCardPage = MediaCardConfigurationPage.DIRECTORY
        }
    }
    BackHandler(enabled = selected.section != null) { returnFromCurrentPage() }
    AppScaffold(
        title = selected.title,
        largeTitle = selected.title,
        subtitle = "",
        onBack = { if (selected.section == null) onBack() else returnFromCurrentPage() },
    ) { paddingValues ->
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 6 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -it / 6 })
            },
            label = "歌词配置页面切换",
        ) { page ->
        if (page.section == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    onSectionSelected = {
                        section = it
                        if (it == LyricConfigSection.MEDIA_CARD) {
                            mediaCardPage = MediaCardConfigurationPage.DIRECTORY
                        }
                    },
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = if (page.section == LyricConfigSection.SOURCE) 0.dp else 18.dp, vertical = 12.dp)
                    .alpha(if (config.enabled || page.section == LyricConfigSection.MEDIA_CARD) 1f else 0.55f),
            ) {
                LyricConfigurationMiuix(
                    config = config,
                    onConfigChange = onConfigChange,
                    section = page.section,
                    enabled = config.enabled || page.section == LyricConfigSection.MEDIA_CARD,
                    mediaCardPage = page.mediaCardPage,
                    onMediaCardPageChange = { mediaCardPage = it },
                )
            }
        }
        }
    }
}

private data class LyricMiuixDestination(
    val section: LyricConfigSection?,
    val mediaCardPage: MediaCardConfigurationPage = MediaCardConfigurationPage.DIRECTORY,
) {
    val title: String
        get() =
            if (section == LyricConfigSection.MEDIA_CARD) mediaCardPage.title
            else section?.title ?: "超级岛歌词"
}

/** Compatibility overload for callers compiled before the rich config screen. */
@Composable
fun LyricMiuix(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, onBack: () -> Unit) =
    LyricMiuix(
        config = LyricIslandConfig(enabled = enabled),
        onConfigChange = { onEnabledChange(it.enabled) },
        onBack = onBack,
    )
