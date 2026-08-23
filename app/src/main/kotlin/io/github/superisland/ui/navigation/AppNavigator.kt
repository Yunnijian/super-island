package io.github.superisland.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * Type-safe routes rendered by Miuix Navigation3's [androidx.navigation3.ui.NavDisplay].
 *
 * This keeps the navigation model aligned with KernelSU Manager.  The visual transition itself
 * deliberately comes from Miuix Navigation3, rather than a locally invented AnimatedContent spec.
 */
sealed interface AppRoute : NavKey, java.io.Serializable

enum class AppDestination : AppRoute {
    MAIN,
    HOME,
    HOME_SERVICE_STATUS,
    HOME_USAGE_GUIDE,
    HOME_ABOUT,
    SUPER_ISLAND,
    EXTENSIONS,
    EXTENSION_MISHARE_FOLDER,
    EXTENSION_SCREEN_RECORDING,
    SETTINGS,
    SETTINGS_THEME,
    PROFILE,
    PROFILE_USAGE_GUIDE,
    PROFILE_ABOUT,
    SMART_CAPSULE_APPS,
    CAPSULE_APPEARANCE,
    BATTERY_MONITOR,
    BATTERY_REALTIME,
    BATTERY_CONTINUOUS,
    BATTERY_EVENTS,
    BATTERY_DIAGNOSTICS,
    BATTERY_CONFIGURATION,
    BATTERY_EXPANDED_CONTENT,
    LYRIC,
}

/** The selected package is part of the route so the detail page can never observe a stale null. */
data class SmartCapsuleChannelsDestination(
    val packageName: String,
) : AppRoute

data class SmartCapsuleChannelDetailDestination(
    val packageName: String,
    val channelId: String,
    val channelName: String,
    val channelImportance: Int,
) : AppRoute

val AppRoute.isDetail: Boolean
    get() = this != AppDestination.MAIN

val AppDestination.isPrimaryTab: Boolean
    get() =
        this in
            setOf(
                AppDestination.HOME,
                AppDestination.SUPER_ISLAND,
                AppDestination.EXTENSIONS,
                AppDestination.SETTINGS,
                AppDestination.PROFILE,
            )

val AppDestination.backDestination: AppDestination
    get() =
        when (this) {
            AppDestination.HOME_SERVICE_STATUS,
            AppDestination.HOME_USAGE_GUIDE,
            AppDestination.HOME_ABOUT,
            -> AppDestination.HOME
            AppDestination.PROFILE_USAGE_GUIDE,
            AppDestination.PROFILE_ABOUT,
            -> AppDestination.PROFILE
            AppDestination.EXTENSION_MISHARE_FOLDER,
            AppDestination.EXTENSION_SCREEN_RECORDING,
            -> AppDestination.EXTENSIONS
            AppDestination.SETTINGS_THEME -> AppDestination.SETTINGS
            AppDestination.SMART_CAPSULE_APPS -> AppDestination.SUPER_ISLAND
            AppDestination.CAPSULE_APPEARANCE -> AppDestination.SUPER_ISLAND
            AppDestination.BATTERY_REALTIME,
            AppDestination.BATTERY_CONTINUOUS,
            AppDestination.BATTERY_EVENTS,
            AppDestination.BATTERY_DIAGNOSTICS,
            -> AppDestination.BATTERY_MONITOR
            AppDestination.BATTERY_CONFIGURATION -> AppDestination.SUPER_ISLAND
            AppDestination.BATTERY_EXPANDED_CONTENT -> AppDestination.BATTERY_CONFIGURATION
            AppDestination.LYRIC -> AppDestination.SUPER_ISLAND
            AppDestination.BATTERY_MONITOR,
            -> AppDestination.SUPER_ISLAND
            AppDestination.HOME,
            AppDestination.SUPER_ISLAND,
            AppDestination.EXTENSIONS,
            AppDestination.SETTINGS,
            AppDestination.PROFILE,
            -> this
            AppDestination.MAIN -> this
        }

val AppRoute.backRoute: AppRoute
    get() =
        when (this) {
            is SmartCapsuleChannelsDestination -> AppDestination.SMART_CAPSULE_APPS
            is SmartCapsuleChannelDetailDestination ->
                SmartCapsuleChannelsDestination(packageName)
            is AppDestination -> backDestination
        }

/**
 * KernelSU-derived navigation owner, restricted to this app's route set.
 *
 * Assigning through the delegate preserves the existing screen callbacks while now building a
 * real back stack for Miuix NavDisplay to animate.
 */
class AppNavigator(initialDestination: AppRoute) {
    val backStack: SnapshotStateList<AppRoute> = mutableStateListOf<AppRoute>().apply {
        addAll(initialDestination.navigationPath())
    }

    val current: AppRoute
        get() = backStack.last()

    fun navigate(destination: AppRoute) {
        val current = backStack.last()
        when {
            destination == current -> Unit
            destination == current.backRoute && backStack.size > 1 -> pop()
            else -> backStack += destination
        }
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun popToRoot() {
        while (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    companion object {
        val Saver: Saver<AppNavigator, Any> =
            listSaver(
                save = { navigator -> navigator.backStack.toList() },
                restore = { savedBackStack ->
                    // M4 stored one of the old primary-tab routes as the root.  Start clean when
                    // restoring that shape so the pager/root-route split cannot render it as a
                    // Navigation3 detail entry.
                    if (savedBackStack.firstOrNull() != AppDestination.MAIN) {
                        AppNavigator(AppDestination.MAIN)
                    } else {
                        AppNavigator(AppDestination.MAIN).also { navigator ->
                            navigator.backStack.clear()
                            navigator.backStack.addAll(savedBackStack)
                        }
                    }
                },
            )
    }
}

@Composable
fun rememberAppNavigator(startDestination: AppRoute): AppNavigator =
    rememberSaveable(startDestination, saver = AppNavigator.Saver) {
        AppNavigator(startDestination)
    }

private fun AppRoute.navigationPath(): List<AppRoute> {
    val reversedPath = mutableListOf<AppRoute>()
    var destination: AppRoute =
        if (this is AppDestination && isPrimaryTab) AppDestination.MAIN else this
    do {
        reversedPath += destination
        destination = destination.backRoute
    } while (destination !in reversedPath)
    return reversedPath.asReversed()
}
