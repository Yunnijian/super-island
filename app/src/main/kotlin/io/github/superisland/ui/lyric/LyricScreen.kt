package io.github.superisland.ui.lyric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.superisland.LyricIslandHostConfigSync
import io.github.superisland.ui.material.LyricMaterial
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode

/** Lyric-island entry; both skins render from this single dispatcher. */
@Composable
fun LyricScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Persisted echo: survives page exit and process restart.
    var enabled by remember { mutableStateOf(LyricIslandHostConfigSync.isEnabled()) }
    val setEnabled: (Boolean) -> Unit = { next ->
        enabled = next
        LyricIslandHostConfigSync.start(context)
        LyricIslandHostConfigSync.sync(next)
    }
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            LyricMiuix(
                enabled = enabled,
                onEnabledChange = setEnabled,
                onBack = onBack,
            )
        UiMode.Material ->
            LyricMaterial(
                enabled = enabled,
                onEnabledChange = setEnabled,
                onBack = onBack,
            )
    }
}
