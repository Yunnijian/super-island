package io.github.superisland.ui.lyric

import androidx.compose.runtime.Composable
import io.github.superisland.design.AppScaffold

@Composable
fun LyricMaterial(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    AppScaffold(title = "超级岛歌词", largeTitle = "超级岛歌词", onBack = onBack) { padding ->
        // Material version reuses same logic, different scaffold
    }
}
