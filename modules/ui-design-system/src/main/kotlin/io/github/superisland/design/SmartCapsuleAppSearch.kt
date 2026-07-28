package io.github.superisland.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Debounced app-directory search that never filters the installed package list on the UI thread. */
@Composable
fun rememberSmartCapsuleAppSearchResults(
    apps: List<NotificationSourceOptionUi>,
    query: String,
): List<NotificationSourceOptionUi> {
    var results by remember { mutableStateOf(emptyList<NotificationSourceOptionUi>()) }

    LaunchedEffect(apps, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MILLIS)
        results =
            withContext(Dispatchers.Default) {
                apps.filter { app ->
                    app.title.contains(normalizedQuery, ignoreCase = true) ||
                        app.id.contains(normalizedQuery, ignoreCase = true)
                }
            }
    }

    return results
}

/** Launchable-app filtering shared by both resident target-picker skins. */
@Composable
fun rememberResidentExpandedAppSearchResults(
    apps: List<ResidentExpandedLaunchAppUi>,
    showSystemApps: Boolean,
    query: String,
): List<ResidentExpandedLaunchAppUi> {
    var results by remember { mutableStateOf(emptyList<ResidentExpandedLaunchAppUi>()) }

    LaunchedEffect(apps, showSystemApps, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
        results =
            withContext(Dispatchers.Default) {
                apps.filter { app ->
                    (showSystemApps || !app.isSystem) &&
                        (
                            normalizedQuery.isEmpty() ||
                                app.title.contains(normalizedQuery, ignoreCase = true) ||
                                app.packageName.contains(normalizedQuery, ignoreCase = true)
                        )
                }
            }
    }

    return results
}

/** Shortcut filtering uses the same debounce and background dispatch as the app directory. */
@Composable
fun rememberResidentExpandedShortcutSearchResults(
    shortcuts: List<ResidentExpandedShortcutUi>,
    query: String,
): List<ResidentExpandedShortcutUi> {
    var results by remember { mutableStateOf(emptyList<ResidentExpandedShortcutUi>()) }

    LaunchedEffect(shortcuts, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
        results =
            withContext(Dispatchers.Default) {
                shortcuts.filter { shortcut ->
                    normalizedQuery.isEmpty() ||
                        shortcut.title.contains(normalizedQuery, ignoreCase = true) ||
                        shortcut.summary.contains(normalizedQuery, ignoreCase = true)
                }
            }
    }

    return results
}

private const val SEARCH_DEBOUNCE_MILLIS = 120L
