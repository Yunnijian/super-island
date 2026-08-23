package io.github.superisland.ui.lyric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.superisland.LyricIslandHostConfigSync
import io.github.superisland.source.lyric.LyricIslandConfig
import io.github.superisland.ui.material.LyricMaterial
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode

/** Lyric-island entry; both skins render from this single dispatcher. */
@Composable
fun LyricScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Persisted rich config: survives page exit and process restart.
    LyricIslandHostConfigSync.start(context)
    var config by remember { mutableStateOf(LyricIslandHostConfigSync.loadConfig()) }
    val setConfig: (LyricIslandConfig) -> Unit = { next ->
        val previous = config
        val normalized = next.normalized()
        LyricIslandHostConfigSync.start(context)
        if (LyricIslandHostConfigSync.sync(normalized).isSuccess) {
            config = normalized
        } else {
            config = previous
        }
    }
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            LyricMiuix(
                config = config,
                onConfigChange = setConfig,
                onBack = onBack,
            )
        UiMode.Material ->
            LyricMaterial(
                config = config,
                onConfigChange = setConfig,
                onBack = onBack,
            )
    }
}
