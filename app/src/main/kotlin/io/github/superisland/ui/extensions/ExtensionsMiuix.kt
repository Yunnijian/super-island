package io.github.superisland.ui.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.superisland.design.AppFiveTabDirectoryScreen
import io.github.superisland.design.AppPrimaryTab
import io.github.superisland.ui.extensionsDirectoryGroups

@Composable
fun ExtensionsMiuix(
    onTabSelected: (AppPrimaryTab) -> Unit,
    onOpenMiShareFolder: () -> Unit,
    onOpenScreenRecording: () -> Unit,
    showBottomBar: Boolean = true,
    bottomInnerPadding: Dp = 0.dp,
) {
    AppFiveTabDirectoryScreen(
        selectedTab = AppPrimaryTab.EXTENSIONS,
        title = "拓展",
        subtitle = "",
        groups = extensionsDirectoryGroups(),
        showBottomBar = showBottomBar,
        bottomInnerPadding = bottomInnerPadding,
        onTabSelected = onTabSelected,
        onEntrySelected = { entryId ->
            when (entryId) {
                "mishare_folder" -> onOpenMiShareFolder()
                "screen_recording" -> onOpenScreenRecording()
            }
        },
    )
}
