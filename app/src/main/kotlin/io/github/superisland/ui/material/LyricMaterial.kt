package io.github.superisland.ui.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricMaterial(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("超级岛歌词") },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(
                    androidx.compose.foundation.layout.WindowInsetsSides.Top +
                        androidx.compose.foundation.layout.WindowInsetsSides.Horizontal,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            androidx.compose.foundation.layout.WindowInsetsSides.Top +
                androidx.compose.foundation.layout.WindowInsetsSides.Horizontal,
        ),
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("启用超级岛歌词") },
                        supportingContent = { Text("通过 SuperLyric 在超级岛显示逐字歌词") },
                        trailingContent = {
                            Switch(checked = enabled, onCheckedChange = onEnabledChange)
                        },
                        colors = ListItemDefaults.colors(),
                    )
                }
            }
        }
    }
}
