package io.github.superisland.ui.superisland

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.superisland.design.AppFiveTabDirectoryScreen
import io.github.superisland.design.AppPrimaryTab
import io.github.superisland.ui.superIslandDirectoryGroups

/** Miuix-only Super Island directory skin; routing and business actions remain injected. */
@Composable
fun SuperIslandMiuix(
    onTabSelected: (AppPrimaryTab) -> Unit,
    onOpenSmartCapsule: () -> Unit,
    onOpenMediaIsland: () -> Unit,
    onOpenBatteryMonitor: () -> Unit,
    onOpenCapsuleAppearance: () -> Unit,
    showBottomBar: Boolean = true,
    bottomInnerPadding: Dp = 0.dp,
) {
    AppFiveTabDirectoryScreen(
        selectedTab = AppPrimaryTab.SUPER_ISLAND,
        title = "超级岛",
        subtitle = "",
        groups = superIslandDirectoryGroups(),
        showBottomBar = showBottomBar,
        bottomInnerPadding = bottomInnerPadding,
        onTabSelected = onTabSelected,
        onEntrySelected = { entryId ->
            when (entryId) {
                "smart_capsule" -> onOpenSmartCapsule()
                "media_island" -> onOpenMediaIsland()
                "monitor" -> onOpenBatteryMonitor()
                "capsule_appearance" -> onOpenCapsuleAppearance()
            }
        },
    )
}
