package io.github.superisland.design

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop

@Composable
fun AppScaffold(
    title: String,
    largeTitle: String = title,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
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
                    largeTitle = largeTitle,
                    subtitle = subtitle,
                    navigationIcon = {
                        onBack?.let { navigateUp ->
                            IconButton(onClick = navigateUp) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = "返回",
                                )
                            }
                        }
                    },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        Box(
            modifier =
                (if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            content(paddingValues)
        }
    }
}

/** A factual, KernelSU-density status header. It intentionally contains no app or ROM branding. */
data class AppRuntimeStatusCardUi(
    val title: String,
    val summary: String,
    val active: Boolean,
)

@Composable
fun AppRuntimeStatusHeader(
    cards: List<AppRuntimeStatusCardUi>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val card = cards.firstOrNull() ?: return@Column
        // Directly adapted from KernelSU Manager's GPL-3.0-or-later
        // HomeMiuix.StatusCard (b6e50f9): full-width status card, large watermark icon, and
        // Tilt press feedback. Product-specific state and wording replace KernelSU fields.
        // The status card must use semantic container pairs. Fixed light colors become
        // unreadable when the user selects a dark dynamic Miuix scheme.
        val containerColor =
            if (card.active) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.errorContainer
        val contentColor =
            if (card.active) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onErrorContainer
        val statusColor = if (card.active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error
        Card(
            modifier = Modifier.fillMaxWidth().height(142.dp),
            colors = CardDefaults.defaultColors(color = containerColor, contentColor = contentColor),
            onClick = {},
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize().offset(38.dp, 28.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(
                        modifier = Modifier.size(170.dp),
                        imageVector = if (card.active) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
                        tint = statusColor,
                        contentDescription = null,
                    )
                }
                Column(modifier = Modifier.fillMaxSize().padding(all = 16.dp)) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = card.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = card.summary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor,
                    )
                }
            }
        }
    }
}

data class AppMaintenanceActionUi(
    val id: String,
    val title: String,
    val summary: String,
    val enabled: Boolean = true,
)

data class AppKernelInfoUi(
    val title: String,
    val summary: String,
)

/** Directly adapted from KernelSU Manager's GPL-3.0-or-later HomeMiuix.InfoCard (b6e50f9). */
@Composable
fun AppKernelStyleInfoCard(entries: List<AppKernelInfoUi>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                Text(
                    text = entry.title,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = entry.summary,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp, bottom = if (index == entries.lastIndex) 0.dp else 24.dp),
                )
            }
        }
    }
}

/** The two maintenance rows intentionally mirror the AstraFlow reference without fake details. */
@Composable
fun AppMaintenanceActionsCard(
    actions: List<AppMaintenanceActionUi>,
    onAction: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        actions.forEachIndexed { index, action ->
            BasicComponent(
                title = action.title,
                summary = action.summary,
                enabled = action.enabled,
                onClick = { onAction(action.id) },
            )
            if (index != actions.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
fun AppHomeOverview(
    statusCards: List<AppRuntimeStatusCardUi>,
    informationEntries: List<AppKernelInfoUi>,
    maintenanceActions: List<AppMaintenanceActionUi>,
    onMaintenanceAction: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppRuntimeStatusHeader(cards = statusCards)
        AppKernelStyleInfoCard(entries = informationEntries)
        AppMaintenanceActionsCard(
            actions = maintenanceActions,
            onAction = onMaintenanceAction,
        )
    }
}

/** Miuix-native confirmation dialog for disruptive or irreversible app actions. */
@Composable
fun AppConfirmationDialog(
    visible: Boolean,
    title: String,
    summary: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        title = title,
        summary = summary,
        show = visible,
        onDismissRequest = onDismiss,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val buttonWidth = (maxWidth - 12.dp) / 2
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.width(buttonWidth),
                    onClick = onDismiss,
                ) {
                    Text("取消")
                }
                Button(
                    modifier = Modifier.width(buttonWidth),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    onClick = onConfirm,
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

@Composable
fun AppPrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = ButtonDefaults.buttonColorsPrimary(),
        onClick = onClick,
    ) {
        Text(text)
    }
}

/** Full-width destructive action (e.g. stop screen recording). */
@Composable
fun AppDangerButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.error,
                contentColor = MiuixTheme.colorScheme.onError,
            ),
        onClick = onClick,
    ) {
        Text(text)
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(text)
    }
}

@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(
        text = text,
        colors = ButtonDefaults.textButtonColorsPrimary(),
        onClick = onClick,
    )
}

@Composable
fun AppInfoCard(
    title: String,
    summary: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(title = title, summary = summary)
    }
}

@Composable
fun AppSelectableInfoCard(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = title,
            summary = summary,
            onClick = onClick,
            endActions = {
                RadioButton(
                    selected = selected,
                    onClick = null,
                )
            },
        )
    }
}

/**
 * The required function-level switch for a Super Island detail page.
 *
 * Feature modules pass a real persisted value and a side-effecting callback; this component owns
 * only the Miuix presentation so an inactive feature cannot be mistaken for an enabled island.
 */
@Composable
fun AppFeatureMasterSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
fun AppProgressCard(
    title: String,
    summary: String,
    progress: Float,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = title,
            summary = summary,
            bottomAction = {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = progress,
                )
            },
        )
    }
}
