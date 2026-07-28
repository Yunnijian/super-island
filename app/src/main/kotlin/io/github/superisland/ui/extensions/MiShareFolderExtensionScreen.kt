package io.github.superisland.ui.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.superisland.ui.adaptive.AppDirectoryDetailScreen
import io.github.superisland.ui.adaptive.AppFeatureMasterSwitch
import me.weishu.kernelsu.ui.util.rememberContentReady

/** One shared detail route; adaptive components render the active Miuix or Material skin. */
@Composable
fun MiShareFolderExtensionScreen(onBack: () -> Unit) {
    val applicationContext = LocalContext.current.applicationContext
    val contentReady = rememberContentReady()
    val state by MiShareFolderExtensionUiStateOwner.state.collectAsStateWithLifecycle()

    LaunchedEffect(contentReady, applicationContext) {
        if (contentReady) {
            MiShareFolderExtensionUiStateOwner.prepare(applicationContext)
        }
    }

    AppDirectoryDetailScreen(
        title = "小米互传文件夹",
        subtitle = "",
        groups = emptyList(),
        header = {
            AppFeatureMasterSwitch(
                title = "使用 MT 管理器打开",
                summary =
                    when {
                        state.enabled && state.capability.ready -> "已启用，点击接收完成后的打开文件夹将跳转到 MT 管理器"
                        state.enabled && !state.capability.miShareInstalled -> "未检测到小米互传，暂不接管任何操作"
                        state.enabled -> "未检测到兼容的 MT 管理器快捷入口，保持系统原行为"
                        else -> "关闭后继续使用小米文件管理器"
                    },
                checked = state.enabled,
                onCheckedChange = { requested ->
                    MiShareFolderExtensionUiStateOwner.setEnabled(applicationContext, requested)
                },
            )
        },
        onBack = onBack,
        onEntrySelected = {},
    )
}
