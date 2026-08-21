package io.github.superisland.ui.lyric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.superisland.ui.material.LyricMaterial
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode

/** Lyric-island entry; both skins render from this single dispatcher. */
@Composable
fun LyricScreen(onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(false) }
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            LyricMiuix(
                enabled = enabled,
                onEnabledChange = { enabled = it },
                onBack = onBack,
            )
        UiMode.Material ->
            LyricMaterial(
                enabled = enabled,
                onEnabledChange = { enabled = it },
                onBack = onBack,
            )
    }
}
