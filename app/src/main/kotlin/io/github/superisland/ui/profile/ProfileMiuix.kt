package io.github.superisland.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.superisland.design.AppFiveTabDirectoryScreen
import io.github.superisland.design.AppPrimaryTab
import io.github.superisland.ui.profileDirectoryGroups

@Composable
fun ProfileMiuix(
    onTabSelected: (AppPrimaryTab) -> Unit,
    onOpenUsageGuide: () -> Unit,
    onOpenAbout: () -> Unit,
    showBottomBar: Boolean = true,
    bottomInnerPadding: Dp = 0.dp,
) {
    AppFiveTabDirectoryScreen(
        selectedTab = AppPrimaryTab.PROFILE,
        title = "我的",
        subtitle = "",
        groups = profileDirectoryGroups(),
        showBottomBar = showBottomBar,
        bottomInnerPadding = bottomInnerPadding,
        onTabSelected = onTabSelected,
        onEntrySelected = { entryId ->
            when (entryId) {
                "usage_guide" -> onOpenUsageGuide()
                "about" -> onOpenAbout()
            }
        },
    )
}
