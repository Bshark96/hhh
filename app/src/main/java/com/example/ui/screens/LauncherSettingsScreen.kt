package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.DeviceTelemetrySnapshot
import com.example.domain.model.DockXmlConfig
import com.example.domain.model.LauncherEngineConfig
import com.example.domain.model.MiuiBackgroundBlurMode
import com.example.domain.model.MiuiFolderAnimationMode
import com.example.ui.components.DockXmlLiveEditorDialog
import com.example.ui.components.DockXmlPresetSelectorSection
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDeepBackground
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun LauncherSettingsScreen(
    engineConfig: LauncherEngineConfig,
    effectiveDockConfig: DockXmlConfig = DockXmlConfig(isCustomDockEnabled = false),
    deviceTelemetry: DeviceTelemetrySnapshot = DeviceTelemetrySnapshot(),
    onSetDefaultHomeLauncher: () -> Unit,
    onUpdateGridColumns: (Int) -> Unit,
    onUpdateGridRows: (Int) -> Unit = {},
    onUpdateIconSizeDp: (Int) -> Unit,
    onToggleShowLabels: (Boolean) -> Unit,
    onToggleShowApiBadges: (Boolean) -> Unit,
    onToggleMultiBgHash: (Boolean) -> Unit,
    onUpdateOverlayShadeAlpha: (Float) -> Unit = {},
    onUpdateBackgroundBlurMode: (MiuiBackgroundBlurMode) -> Unit = {},
    onUpdateFolderAnimationMode: (MiuiFolderAnimationMode) -> Unit = {},
    onUpdateCustomDockXml: (String) -> Unit = {},
    onRequestWidgetPermissions: () -> Unit = {},
    isNotificationAccessGranted: Boolean = false,
    onToggleMtzWidgetStyling: (Boolean) -> Unit = {},
    onToggleWorkspaceNotificationDots: (Boolean) -> Unit = {},
    onOpenNotificationAccessSettings: () -> Unit = {},
    onCloseSettings: () -> Unit = {}
) {
    BackHandler(onBack = onCloseSettings)
    var showDockXmlLiveEditor by remember { mutableStateOf(false) }

    if (showDockXmlLiveEditor) {
        DockXmlLiveEditorDialog(
            initialXml = engineConfig.customDockXml,
            themeDefaultXml = "",
            onDismiss = { showDockXmlLiveEditor = false },
            onApplyXml = onUpdateCustomDockXml
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeepBackground)
            .padding(horizontal = 16.dp)
            .testTag("launcher_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Launcher Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Configure desktop grid, icons, <DockConfig> XML, and MIUI widget permissions",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Surface(
                    color = SlateSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                        .clickable(onClick = onCloseSettings)
                ) {
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Default Android Home Launcher Role Card
        item {
            Surface(
                color = SlateSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Android Home Launcher Integration",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldBright
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "MTZLauncher declares android.intent.category.HOME and DEFAULT in AndroidManifest.xml. You can bind it as your system default home screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onSetDefaultHomeLauncher,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("set_default_launcher_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Set Default Home",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Set as Default Home Launcher",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // <DockConfig> XML Customization Card
        item {
            Surface(
                color = SlateSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Dock Customization XML API (<DockConfig>)",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldBright
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (effectiveDockConfig.isCustomDockEnabled) {
                            "Active: Custom <DockConfig> enabled (Background, Border & 4-Side Frame Images). Clear to restore stock MIUI transparent dock."
                        } else {
                            "Stock MIUI Fallback Active: No <DockConfig> modification found in XML — rendering unmodified transparent MIUI pin bar."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    DockXmlPresetSelectorSection(
                        customDockXml = engineConfig.customDockXml,
                        effectiveDockConfig = effectiveDockConfig,
                        onUpdateCustomDockXml = onUpdateCustomDockXml,
                        onOpenXmlEditorModal = { showDockXmlLiveEditor = true }
                    )
                }
            }
        }

        // MIUI Theme Widgets & Native Bridge Permissions Card
        item {
            Surface(
                color = SlateSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MIUI Theme Widgets & Native App Bridges",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Location: ${if (deviceTelemetry.hasLocationPermission) "Granted (${deviceTelemetry.weatherCity})" else "Not granted"} • Calendar: ${if (deviceTelemetry.hasCalendarPermission) "Granted (${deviceTelemetry.upcomingCalendarEvents.size} events)" else "Not granted"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldBright
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestWidgetPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_grant_widget_permissions_btn")
                    ) {
                        Text(
                            text = "Grant Location & Calendar Permissions for MIUI Widgets",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(6.dp))

                    SettingsSwitchItem(
                        title = "Use .MTZ Theme Visual Styling for MIUI Widgets (Optional)",
                        subtitle = "Applies extracted .mtz clock/weather assets, custom images, custom fonts (.ttf/.otf), theme palette & borders",
                        checked = engineConfig.enableMtzWidgetStyling,
                        onCheckedChange = onToggleMtzWidgetStyling,
                        tag = "toggle_mtz_widget_styling"
                    )

                    SettingsSwitchItem(
                        title = "Workspace & Dock Notification Badges (Count & Dot, Excludes Drawer)",
                        subtitle = "Shows notification count badges (e.g. 20) & dots on Workspace & Dock icons only (App Drawer stays badge-free)",
                        checked = engineConfig.enableWorkspaceNotificationDots,
                        onCheckedChange = onToggleWorkspaceNotificationDots,
                        tag = "toggle_workspace_notification_dots"
                    )

                    if (!isNotificationAccessGranted) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onOpenNotificationAccessSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurfaceElevated),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, EmeraldBright, RoundedCornerShape(24.dp))
                                .testTag("settings_grant_notification_listener_btn")
                        ) {
                            Text(
                                text = "Grant Notification Access for Workspace & Dock Badges",
                                color = EmeraldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Workspace Grid & Density Controls
        item {
            Surface(
                color = SlateSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Workspace Grid & Icon Geometry",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Workspace Columns",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(4, 5, 6).forEach { cols ->
                            val selected = engineConfig.gridColumns == cols
                            Surface(
                                color = if (selected) EmeraldContainer else SlateSurfaceElevated,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) EmeraldBright else SlateBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUpdateGridColumns(cols) }
                                    .testTag("grid_cols_$cols")
                            ) {
                                Text(
                                    text = "$cols Cols",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) EmeraldBright else TextPrimary,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Workspace Rows",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(6, 7, 8).forEach { rows ->
                            val selected = engineConfig.gridRows == rows
                            Surface(
                                color = if (selected) EmeraldContainer else SlateSurfaceElevated,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) EmeraldBright else SlateBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUpdateGridRows(rows) }
                                    .testTag("grid_rows_$rows")
                            ) {
                                Text(
                                    text = "$rows Rows",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) EmeraldBright else TextPrimary,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Rendered Icon Size (Auto-clamped to cell)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${engineConfig.iconSizeDp} dp",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldBright
                        )
                    }
                    Slider(
                        value = engineConfig.iconSizeDp.coerceIn(36, 72).toFloat(),
                        onValueChange = { onUpdateIconSizeDp(it.toInt()) },
                        valueRange = 36f..72f,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldBright,
                            activeTrackColor = EmeraldPrimary,
                            inactiveTrackColor = SlateSurfaceElevated
                        ),
                        modifier = Modifier.testTag("icon_size_slider")
                    )

                    HorizontalDivider(color = SlateBorder)
                    Spacer(modifier = Modifier.height(6.dp))

                    SettingsSwitchItem(
                        title = "Display Application Labels on Workspace",
                        subtitle = "Show package title beneath each themed icon on the home grid",
                        checked = engineConfig.showAppLabels,
                        onCheckedChange = onToggleShowLabels,
                        tag = "toggle_show_labels"
                    )

                    SettingsSwitchItem(
                        title = "Display Xiaomi API Resolution Badges",
                        subtitle = "Overlay MAML / PNG / MASK / OVR stage tags on workspace icons",
                        checked = engineConfig.showApiSourceBadges,
                        onCheckedChange = onToggleShowApiBadges,
                        tag = "toggle_show_badges"
                    )

                    SettingsSwitchItem(
                        title = "Multi-Plate icon_bg_0..n Deterministic Hashing",
                        subtitle = "Assigns package-consistent background plates when an .mtz includes multiple icon_bg_N.png files",
                        checked = engineConfig.enableMultiBgHashSelection,
                        onCheckedChange = onToggleMultiBgHash,
                        tag = "toggle_multi_bg_hash"
                    )
                }
            }
        }

        // Drawer & Folder Shade, Background Blur, and Folder Open Animation Card
        item {
            Surface(
                color = SlateSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Drawer & Folder Overlay Shade, Blur & Animations",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Drawer & Folder Shade Opacity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${(engineConfig.overlayShadeAlpha * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldBright
                        )
                    }
                    Slider(
                        value = engineConfig.overlayShadeAlpha.coerceIn(0f, 0.95f),
                        onValueChange = onUpdateOverlayShadeAlpha,
                        valueRange = 0f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldBright,
                            activeTrackColor = EmeraldPrimary,
                            inactiveTrackColor = SlateSurfaceElevated
                        ),
                        modifier = Modifier.testTag("overlay_shade_alpha_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Background Blur (Drawer & Open Folder)",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiuiBackgroundBlurMode.entries.forEach { blurMode ->
                            val selected = engineConfig.backgroundBlurMode == blurMode
                            Surface(
                                color = if (selected) EmeraldContainer else SlateSurfaceElevated,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) EmeraldBright else SlateBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUpdateBackgroundBlurMode(blurMode) }
                                    .testTag("blur_mode_${blurMode.code}")
                            ) {
                                Text(
                                    text = blurMode.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) EmeraldBright else TextPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Folder Open Animation",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiuiFolderAnimationMode.entries.forEach { animMode ->
                            val selected = engineConfig.folderAnimationMode == animMode
                            Surface(
                                color = if (selected) EmeraldContainer else SlateSurfaceElevated,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) EmeraldBright else SlateBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUpdateFolderAnimationMode(animMode) }
                                    .testTag("folder_anim_mode_${animMode.code}")
                            ) {
                                Text(
                                    text = animMode.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) EmeraldBright else TextPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Architectural Reference Card
        item {
            Surface(
                color = SlateSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Xiaomi MIUI / HyperOS .MTZ Architecture Reference",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldBright
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Archive Structure: Outer .mtz/.mtx ZIP containing description.xml, wallpaper/, and an inner 'icons' ZIP payload.\n" +
                            "2. Direct Mapping: res/drawable-xxhdpi/<package_or_component>.png matched against PackageManager activities and Xiaomi package aliases.\n" +
                            "3. Dynamic Masking API: Unthemed apps are scaled by <dimen name=\"icon_scale\"> from theme_values.xml, clipped against icon_mask.png using PorterDuff.Mode.DST_IN, placed over icon_bg.png, and framed by icon_border.png.\n" +
                            "4. MAML fancy_icons: Evaluates fancy_icons/<pkg>/manifest.xml expressions (#hour12, #minute, #second, #date) for live dynamic icons.\n" +
                            "5. <DockConfig> XML API: Customizes dock background, borders, and 4-side/4-corner frame images when present in dock_config.xml or transform_config.xml, or falls back to the unmodified stock MIUI transparent pin bar when omitted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SlateDeepBackground,
                checkedTrackColor = EmeraldBright,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SlateSurfaceElevated
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
