package io.github.superisland

import androidx.compose.runtime.saveable.SaverScope
import io.github.superisland.ui.navigation.AppDestination
import io.github.superisland.ui.navigation.AppNavigator
import io.github.superisland.ui.navigation.SmartCapsuleChannelDetailDestination
import io.github.superisland.ui.navigation.SmartCapsuleChannelsDestination
import io.github.superisland.ui.navigation.backDestination
import io.github.superisland.ui.navigation.isDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorExpandedContentTest {
    @Test
    fun capsuleAppearanceUsesTheSuperIslandParentAndTypedBackStack() {
        assertEquals(
            AppDestination.SUPER_ISLAND,
            AppDestination.CAPSULE_APPEARANCE.backDestination,
        )
        assertTrue(AppDestination.CAPSULE_APPEARANCE.isDetail)

        val navigator = AppNavigator(AppDestination.MAIN)
        navigator.navigate(AppDestination.CAPSULE_APPEARANCE)

        assertEquals(
            listOf(AppDestination.MAIN, AppDestination.CAPSULE_APPEARANCE),
            navigator.backStack.toList(),
        )
        navigator.navigate(AppDestination.SUPER_ISLAND)
        assertEquals(listOf(AppDestination.MAIN), navigator.backStack.toList())
    }

    @Test
    fun expandedContentReturnsToResidentConfigurationWithoutDuplicatingTheStack() {
        assertEquals(
            AppDestination.BATTERY_CONFIGURATION,
            AppDestination.BATTERY_EXPANDED_CONTENT.backDestination,
        )
        assertTrue(AppDestination.BATTERY_EXPANDED_CONTENT.isDetail)

        val navigator = AppNavigator(AppDestination.MAIN)
        navigator.navigate(AppDestination.BATTERY_CONFIGURATION)
        navigator.navigate(AppDestination.BATTERY_EXPANDED_CONTENT)
        assertEquals(
            listOf(
                AppDestination.MAIN,
                AppDestination.BATTERY_CONFIGURATION,
                AppDestination.BATTERY_EXPANDED_CONTENT,
            ),
            navigator.backStack.toList(),
        )

        navigator.navigate(AppDestination.BATTERY_CONFIGURATION)
        assertEquals(
            listOf(AppDestination.MAIN, AppDestination.BATTERY_CONFIGURATION),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun smartCapsuleSelectionTravelsAtomicallyWithTheDetailRoute() {
        assertEquals(
            AppDestination.SUPER_ISLAND,
            AppDestination.SMART_CAPSULE_APPS.backDestination,
        )
        val navigator = AppNavigator(AppDestination.MAIN)
        navigator.navigate(AppDestination.SMART_CAPSULE_APPS)

        val appRoute = SmartCapsuleChannelsDestination("com.example.chat")
        navigator.navigate(appRoute)

        assertEquals(appRoute, navigator.current)
        assertEquals("com.example.chat", (navigator.current as SmartCapsuleChannelsDestination).packageName)

        val channelRoute =
            SmartCapsuleChannelDetailDestination(
                packageName = "com.example.chat",
                channelId = "messages",
                channelName = "Messages",
                channelImportance = 4,
            )
        navigator.navigate(channelRoute)
        assertEquals(channelRoute, navigator.current)

        navigator.navigate(appRoute)
        assertEquals(appRoute, navigator.current)
        assertEquals(
            listOf(
                AppDestination.MAIN,
                AppDestination.SMART_CAPSULE_APPS,
                appRoute,
            ),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun parameterizedSmartCapsuleRoutesSurviveSavedStateRoundTrip() {
        val navigator = AppNavigator(AppDestination.MAIN)
        navigator.navigate(AppDestination.SMART_CAPSULE_APPS)
        navigator.navigate(SmartCapsuleChannelsDestination("com.example.chat"))

        val saved =
            with(AppNavigator.Saver) {
                SaverScope { value -> value is java.io.Serializable }.save(navigator)
            }
        val restored = saved?.let(AppNavigator.Saver::restore)

        assertEquals(navigator.backStack.toList(), restored?.backStack?.toList())
    }
}
