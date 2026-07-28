package io.github.superisland.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.basic.Scaffold

/** Visual preference bridge kept in the design-system module to avoid an app->design cycle. */
val LocalDesignEnableBlur = staticCompositionLocalOf { false }

/** Miuix root surface matching KernelSU MainActivity's NavDisplay host. */
@Composable
fun KernelSuMiuixRootHost(content: @Composable () -> Unit) {
    Scaffold { content() }
}
