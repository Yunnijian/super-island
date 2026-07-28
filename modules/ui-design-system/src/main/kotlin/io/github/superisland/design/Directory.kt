package io.github.superisland.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop

/** The only five destinations permitted in the root navigation bar. */
enum class AppPrimaryTab(
    val label: String,
) {
    HOME("首页"),
    SUPER_ISLAND("超级岛"),
    EXTENSIONS("拓展"),
    SETTINGS("设置"),
    PROFILE("我的"),
}

/** Icons are kept inside the design system so feature modules never import Miuix directly. */
enum class DirectoryIcon {
    HOME,
    SUPER_ISLAND,
    EXTENSIONS,
    SETTINGS,
    PROFILE,
    NOTIFICATION,
    MEDIA,
    MONITOR,
    LAB,
    APPEARANCE,
    PRIVACY,
    DEVICE,
    INFO,
}

data class DirectoryEntryUi(
    val id: String,
    val title: String,
    val summary: String,
    val icon: DirectoryIcon,
    val status: String? = null,
    val enabled: Boolean = true,
    val showChevron: Boolean = true,
)

data class DirectoryGroupUi(
    val title: String,
    val entries: List<DirectoryEntryUi>,
)

/** A fact-based summary card used only on the root overview page. */
data class DirectoryStatusHeroUi(
    val title: String,
    val summary: String,
    val icon: DirectoryIcon,
    val status: String? = null,
)

/**
 * Root shell for the AstraFlow-style five-tab information architecture.
 *
 * Pages only receive semantic entries. The Miuix navigation, cards, icons and clickable rows stay
 * in this module to preserve the Miuix-only policy.
 */
@Composable
fun AppFiveTabDirectoryScreen(
    selectedTab: AppPrimaryTab,
    title: String,
    subtitle: String,
    groups: List<DirectoryGroupUi>,
    header: (@Composable () -> Unit)? = null,
    hero: DirectoryStatusHeroUi? = null,
    overlay: (@Composable () -> Unit)? = null,
    showBottomBar: Boolean = true,
    bottomInnerPadding: Dp = 0.dp,
    onTabSelected: (AppPrimaryTab) -> Unit,
    onEntrySelected: (String) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberBlurBackdrop(LocalDesignEnableBlur.current)
    val barColor = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(blurBackdrop) {
                TopAppBar(
                    color = barColor,
                    title = title,
                    largeTitle = title,
                    subtitle = subtitle,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                AppPrimaryNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        Box(modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .padding(horizontal = 12.dp)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                overscrollEffect = null,
            ) {
                header?.let { headerContent ->
                    item(key = "directory-header") {
                        headerContent()
                    }
                }
                hero?.let { statusHero ->
                    item(key = "status-hero") {
                        DirectoryStatusHero(hero = statusHero)
                    }
                }
                itemsIndexed(groups, key = { _, group -> group.title }) { _, group ->
                    DirectoryGroup(
                        group = group,
                        onEntrySelected = onEntrySelected,
                    )
                }
                item(key = "directory-bottom-inset") {
                    Spacer(modifier = Modifier.height(bottomInnerPadding + 12.dp))
                }
            }
            overlay?.invoke()
        }
    }
}

/**
 * The single persistent bottom bar used by the KernelSU-style root pager.
 *
 * Root pages can also render it themselves when displayed outside that pager, which keeps the
 * design previews and isolated feature screens usable.
 */
@Composable
fun AppPrimaryNavigationBar(
    selectedTab: AppPrimaryTab,
    onTabSelected: (AppPrimaryTab) -> Unit,
) {
    // Miuix owns the 64.dp item row and dynamic gesture inset. Keep this standalone adapter on
    // the same contract as the root pager instead of adding a second bottom padding layer.
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        mode = NavigationBarDisplayMode.IconAndText,
        defaultWindowInsetsPadding = true,
    ) {
        AppPrimaryTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = tab.icon(),
                label = tab.label,
            )
        }
    }
}

/**
 * Miuix root scaffold with a fixed tab bar; the page content is supplied by [HorizontalPager].
 *
 * This mirrors KernelSU Manager's `MainScreen`: the root scaffold consumes its own system
 * insets and only exposes the persistent navigation bar height to pager content. Passing the
 * full [PaddingValues] here would make every child page apply the status-bar inset twice, moving
 * its large title downward.
 */
@Composable
fun AppPrimaryPagerScaffold(
    selectedTab: AppPrimaryTab,
    onTabSelected: (AppPrimaryTab) -> Unit,
    content: @Composable (bottomInnerPadding: Dp) -> Unit,
) {
    Scaffold(
        bottomBar = {
            AppPrimaryNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        },
    ) { innerPadding ->
        content(innerPadding.calculateBottomPadding())
    }
}

@Composable
private fun DirectoryStatusHero(hero: DirectoryStatusHeroUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = hero.title,
            summary = hero.summary,
            startAction = {
                Icon(
                    imageVector = hero.icon.imageVector(),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                )
            },
            endActions = {
                hero.status?.let { status -> Text(status) }
            },
        )
    }
}

@Composable
fun AppDirectoryDetailScreen(
    title: String,
    subtitle: String,
    groups: List<DirectoryGroupUi>,
    header: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onEntrySelected: (String) -> Unit,
) {
    AppScaffold(
        title = title,
        largeTitle = title,
        subtitle = subtitle,
        onBack = onBack,
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            header?.let { headerContent ->
                item(key = "detail-header") {
                    headerContent()
                }
            }
            itemsIndexed(groups, key = { _, group -> group.title }) { _, group ->
                DirectoryGroup(
                    group = group,
                    onEntrySelected = onEntrySelected,
                )
            }
        }
    }
}

@Composable
private fun DirectoryGroup(
    group: DirectoryGroupUi,
    onEntrySelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallTitle(
            text = group.title,
            insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            group.entries.forEachIndexed { index, entry ->
                BasicComponent(
                    title = entry.title,
                    summary = entry.summary,
                    enabled = entry.enabled,
                    startAction = {
                        Icon(
                            imageVector = entry.icon.imageVector(),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    },
                    endActions = {
                        entry.status?.let { Text(it) }
                        if (entry.enabled && entry.showChevron) {
                            Icon(
                                imageVector = MiuixIcons.ChevronForward,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    },
                    onClick = { onEntrySelected(entry.id) },
                )
                if (index != group.entries.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

private fun AppPrimaryTab.icon(): ImageVector =
    when (this) {
        AppPrimaryTab.HOME -> MiuixIcons.Home
        AppPrimaryTab.SUPER_ISLAND -> MiuixIcons.Promotions
        AppPrimaryTab.EXTENSIONS -> MiuixIcons.GridView
        AppPrimaryTab.SETTINGS -> MiuixIcons.Settings
        AppPrimaryTab.PROFILE -> MiuixIcons.Contacts
    }

internal fun DirectoryIcon.imageVector(): ImageVector =
    when (this) {
        DirectoryIcon.HOME -> MiuixIcons.Home
        DirectoryIcon.SUPER_ISLAND -> MiuixIcons.Promotions
        DirectoryIcon.EXTENSIONS -> MiuixIcons.GridView
        DirectoryIcon.SETTINGS -> MiuixIcons.Settings
        DirectoryIcon.PROFILE -> MiuixIcons.Contacts
        DirectoryIcon.NOTIFICATION -> MiuixIcons.Info
        DirectoryIcon.MEDIA -> MiuixIcons.Music
        DirectoryIcon.MONITOR -> MiuixIcons.Stopwatch
        DirectoryIcon.LAB -> MiuixIcons.Layers
        DirectoryIcon.APPEARANCE -> MiuixIcons.Theme
        DirectoryIcon.PRIVACY -> MiuixIcons.Lock
        DirectoryIcon.DEVICE -> MiuixIcons.ListView
        DirectoryIcon.INFO -> MiuixIcons.Info
    }
