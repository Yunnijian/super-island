package io.github.superisland.ui.home

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.superisland.BatteryMonitorService
import io.github.superisland.IslandAppearanceConfigSync
import io.github.superisland.IslandAppearanceStore
import io.github.superisland.MiShareFolderExtensionStore
import io.github.superisland.ResidentMonitorConfigStore
import io.github.superisland.SmartCapsuleConfigStore
import io.github.superisland.ModuleScopeController
import io.github.superisland.model.RuntimeEnvironmentSnapshot
import io.github.superisland.publisher.focus.SystemUiResidentIslandPublisher
import io.github.superisland.source.notification.NotificationProxyController
import io.github.superisland.ui.material.HomeMaterial
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode

/** Shared Home state and actions. It intentionally has no Miuix or Material dependency. */
data class HomeUiState(
    val environment: RuntimeEnvironmentSnapshot,
    val deviceModel: String,
    val fingerprint: String,
) {
    val lsposedSummary: String
        get() =
            buildString {
                append("LSPosed")
                environment.lsposedVersion?.let { version -> append(" $version") }
                environment.lsposedApiVersion?.let { apiVersion -> append(" / API $apiVersion") }
            }

    val moduleSummary: String
        get() =
            if (environment.rootAvailable) {
                lsposedSummary
            } else {
                "$lsposedSummary · 未获取 Root 权限"
            }
}

class HomeActions(
    /** Restarts every package in [ModuleScopeController.SCOPE_PACKAGES] / scope.list. */
    val restartScopes: () -> Unit,
    val resetAllSettings: () -> Unit,
)

/** KernelSU-style feature entry: state/actions stay here; the skin is selected below this layer. */
@Composable
fun HomeScreen(
    environment: RuntimeEnvironmentSnapshot,
    onTabSelected: (io.github.superisland.design.AppPrimaryTab) -> Unit,
    showBottomBar: Boolean = true,
    bottomInnerPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val notificationController = remember { NotificationProxyController(context) }
    val residentConfigStore = remember { ResidentMonitorConfigStore(context) }
    val miShareFolderExtensionStore = remember { MiShareFolderExtensionStore(context) }
    val islandAppearanceStore = remember { IslandAppearanceStore(context) }
    val state =
        remember(environment) {
            HomeUiState(
                environment = environment,
                deviceModel =
                    listOf(Build.MANUFACTURER, Build.MODEL)
                        .filter { value -> value.isNotBlank() }
                        .distinctBy { value -> value.lowercase() }
                        .joinToString(" ")
                        .ifBlank { "未知设备" },
                fingerprint = Build.FINGERPRINT.ifBlank { "未知" },
            )
        }
    val actions =
        remember(
            context,
            notificationController,
            residentConfigStore,
            miShareFolderExtensionStore,
            islandAppearanceStore,
        ) {
            HomeActions(
                restartScopes = {
                    Thread {
                        // Withdraw the SystemUI-owned resident Focus island before tearing down
                        // scopes. Simultaneous SystemUI+XMSF kill mid-auth can leave HyperOS
                        // Dynamic Island frozen (no update / no click / no dismiss) until reboot.
                        runCatching { SystemUiResidentIslandPublisher(context).cancel() }
                        try {
                            Thread.sleep(400L)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                        ModuleScopeController.restart()
                    }.start()
                },
                resetAllSettings = {
                    Thread {
                        runCatching {
                            BatteryMonitorService.stop(context)
                            residentConfigStore.reset()
                            miShareFolderExtensionStore.reset().getOrThrow()
                            islandAppearanceStore.reset().getOrThrow()
                            IslandAppearanceConfigSync.syncStoredAndReload()
                            SmartCapsuleConfigStore(context).reset().getOrThrow()
                            notificationController.resetAllSettings()
                            // One-time migration for removed FocusLab: clear stale lab prefs and orphan notification.
                            runCatching {
                                context.getSharedPreferences("super-island-features", Context.MODE_PRIVATE)
                                    .edit()
                                    .clear()
                                    .apply()
                            }
                            runCatching {
                                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                                nm?.cancel(0x464F)
                            }
                        }
                    }.start()
                },
            )
        }

    when (LocalUiMode.current) {
        UiMode.Miuix ->
            HomeMiuix(
                state = state,
                actions = actions,
                onTabSelected = onTabSelected,
                showBottomBar = showBottomBar,
                bottomInnerPadding = bottomInnerPadding,
            )
        UiMode.Material ->
            HomeMaterial(
                state = state,
                actions = actions,
                bottomInnerPadding = bottomInnerPadding,
            )
    }
}
