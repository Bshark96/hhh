package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MtzThemeEntity
import com.example.domain.model.MtzThemeMetadata
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDeepBackground
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CatalogIconEntry
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MtzStudioScreen(
    themes: List<MtzThemeEntity>,
    activeThemeId: String,
    widgetThemeId: String = "",
    activeThemeMetadata: MtzThemeMetadata?,
    activeWidgetThemeMetadata: MtzThemeMetadata? = activeThemeMetadata,
    activeThemeCatalog: List<CatalogIconEntry>,
    onActivateTheme: (String) -> Unit,
    onSelectWidgetTheme: (String) -> Unit = {},
    onRequestImportMtz: () -> Unit,
    onRequestExportMtz: (MtzThemeEntity) -> Unit,
    onDeleteCustomTheme: (String) -> Unit,
    onCloseStudio: () -> Unit = {}
) {
    BackHandler(onBack = onCloseStudio)
    var showXmlSheet by remember { mutableStateOf(false) }
    val effectiveWidgetThemeId = widgetThemeId.takeIf { it.isNotBlank() } ?: activeThemeId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeepBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("mtz_studio_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MIUI .MTZ Themes",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Import and apply MIUI .mtz icon packs, masks, and dynamic icons",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Surface(
                color = SlateSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onCloseStudio)
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

        Spacer(modifier = Modifier.height(12.dp))

        // Import & Active XML Spec Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRequestImportMtz,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_mtz_archive_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = "Import .MTZ",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import .MTZ / .MTX", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showXmlSheet = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("view_active_mtz_xml_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "Inspect XML",
                    tint = EmeraldBright,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Active XML Specs", color = EmeraldBright)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("mtz_theme_list")
        ) {
            // Global Widget Theme Selector (Mix Icons from Theme A + Widgets from Theme B)
            item {
                GlobalWidgetThemeSelectorCard(
                    themes = themes,
                    activeIconThemeId = activeThemeId,
                    widgetThemeId = widgetThemeId,
                    activeWidgetThemeMetadata = activeWidgetThemeMetadata,
                    onSelectWidgetTheme = onSelectWidgetTheme
                )
            }

            // MTZ Icon API Compatibility Matrix
            item {
                MtzApiFeatureMatrixCard(metadata = activeThemeMetadata)
            }

            // Active Theme Extracted Icon Catalog Preview
            if (activeThemeMetadata != null && activeThemeCatalog.isNotEmpty()) {
                item {
                    Surface(
                        color = SlateSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Extracted Icon Pack: ${activeThemeMetadata.title}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldBright
                                )
                                Text(
                                    text = "${activeThemeCatalog.size} PNGs + ${activeThemeMetadata.fancyIconSpecs.size} MAML",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(activeThemeCatalog, key = { it.key }) { item ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(72.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SlateSurfaceElevated)
                                            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                                            .padding(6.dp)
                                    ) {
                                        Image(
                                            bitmap = item.bitmap.asImageBitmap(),
                                            contentDescription = item.key,
                                            modifier = Modifier.size(42.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.key.substringAfterLast('.'),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Installed / Built-In .MTZ Packages
            items(themes, key = { it.id }) { theme ->
                val isActive = theme.id == activeThemeId
                val isWidgetThemeActive = theme.id == effectiveWidgetThemeId
                val isCustomWidgetOverride = widgetThemeId.isNotBlank() && widgetThemeId == theme.id
                Surface(
                    color = if (isActive || isCustomWidgetOverride) SlateSurfaceElevated else SlateSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isActive || isCustomWidgetOverride) 1.5.dp else 1.dp,
                            color = if (isActive || isCustomWidgetOverride) EmeraldBright else SlateBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .testTag("theme_card_${theme.id}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = theme.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TextPrimary
                                    )
                                    if (isActive) {
                                        Surface(
                                            color = EmeraldContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Active Icons",
                                                    tint = EmeraldBright,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "ICONS",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldBright
                                                )
                                            }
                                        }
                                    }
                                    if (isWidgetThemeActive) {
                                        Surface(
                                            color = EmeraldContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Active Widgets",
                                                    tint = EmeraldBright,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "WIDGETS",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldBright
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Archive: ${theme.archiveFileName} | Author: ${theme.author} | ${theme.uiVersion}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }

                            if (!theme.isBuiltIn) {
                                IconButton(
                                    onClick = { onDeleteCustomTheme(theme.id) },
                                    modifier = Modifier.testTag("delete_theme_${theme.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Custom MTZ",
                                        tint = ErrorCrimson
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = theme.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary.copy(alpha = 0.9f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Technical Xiaomi API Capabilities Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TechMetricPill("icon_scale", String.format(Locale.US, "%.2fx", theme.iconScale))
                            TechMetricPill("Direct PNG", "${theme.directIconCount}")
                            TechMetricPill("MAML Fancy", "${theme.fancyIconCount}")
                            TechMetricPill(
                                "Mask API",
                                if (theme.hasMask && theme.hasBg) "BG+MASK+BDR" else "PARTIAL"
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onActivateTheme(theme.id) },
                                enabled = !isActive,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldPrimary,
                                    disabledContainerColor = EmeraldContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("apply_theme_${theme.id}")
                            ) {
                                Text(
                                    text = if (isActive) "Icons Applied" else "Apply Icons",
                                    color = if (isActive) EmeraldBright else SlateDeepBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (isCustomWidgetOverride) {
                                        onSelectWidgetTheme("")
                                    } else {
                                        onSelectWidgetTheme(theme.id)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("apply_widgets_theme_${theme.id}")
                            ) {
                                Text(
                                    text = when {
                                        isCustomWidgetOverride -> "Widgets Pinned"
                                        isWidgetThemeActive -> "Following Icons"
                                        else -> "Use Widgets Only"
                                    },
                                    color = EmeraldBright,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (theme.isBuiltIn) {
                                OutlinedButton(
                                    onClick = { onRequestExportMtz(theme) },
                                    modifier = Modifier.testTag("export_theme_${theme.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = "Export .MTZ",
                                        tint = EmeraldBright,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showXmlSheet && activeThemeMetadata != null) {
        ModalBottomSheet(
            onDismissRequest = { showXmlSheet = false },
            containerColor = SlateSurface,
            contentColor = TextPrimary
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Text(
                        text = "${activeThemeMetadata.archiveFileName} — Xiaomi XML Specifications",
                        style = MaterialTheme.typography.titleLarge,
                        color = EmeraldBright
                    )
                }
                item {
                    XmlBlockCard(title = "description.xml", xml = activeThemeMetadata.rawDescriptionXml)
                }
                item {
                    XmlBlockCard(title = "icons/theme_values.xml", xml = activeThemeMetadata.rawThemeValuesXml)
                }
                item {
                    XmlBlockCard(title = "icons/transform_config.xml", xml = activeThemeMetadata.rawTransformConfigXml)
                }
                if (activeThemeMetadata.rawIconRulesXml.isNotBlank()) {
                    item {
                        XmlBlockCard(title = "icons/icon_rule.xml", xml = activeThemeMetadata.rawIconRulesXml)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalWidgetThemeSelectorCard(
    themes: List<MtzThemeEntity>,
    activeIconThemeId: String,
    widgetThemeId: String,
    activeWidgetThemeMetadata: MtzThemeMetadata?,
    onSelectWidgetTheme: (String) -> Unit
) {
    val activeIconThemeTitle = themes.firstOrNull { it.id == activeIconThemeId }?.title ?: activeIconThemeId
    val currentWidgetLabel = if (widgetThemeId.isBlank()) {
        "Following Icon Theme ($activeIconThemeTitle)"
    } else {
        themes.firstOrNull { it.id == widgetThemeId }?.title ?: widgetThemeId
    }
    val hasClockModule = activeWidgetThemeMetadata?.clock2x4WidgetSpec != null ||
        activeWidgetThemeMetadata?.clock3x4WidgetSpec != null

    Surface(
        color = SlateSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EmeraldPrimary.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .testTag("global_widget_theme_selector_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Global Widget Theme (Mix Icons & Widgets)",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldBright,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Active Widgets: $currentWidgetLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Surface(
                    color = EmeraldContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (hasClockModule) "MAML CLOCK READY" else "PALETTE + GADGETS",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldBright,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                item(key = "__follow_icons__") {
                    val isSelected = widgetThemeId.isBlank()
                    Surface(
                        color = if (isSelected) EmeraldContainer else SlateSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) EmeraldBright else SlateBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectWidgetTheme("") }
                            .testTag("widget_theme_chip_follow_icons")
                    ) {
                        Text(
                            text = "Follow Active Icon Theme",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) EmeraldBright else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                items(themes, key = { "widget_chip_${it.id}" }) { theme ->
                    val isSelected = widgetThemeId == theme.id
                    Surface(
                        color = if (isSelected) EmeraldContainer else SlateSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) EmeraldBright else SlateBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectWidgetTheme(theme.id) }
                            .testTag("widget_theme_chip_${theme.id}")
                    ) {
                        Text(
                            text = theme.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) EmeraldBright else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MtzApiFeatureMatrixCard(metadata: MtzThemeMetadata?) {
    Surface(
        color = SlateSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, EmeraldPrimary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .testTag("mtz_api_feature_matrix_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Xiaomi MTZ Icon API Specification Coverage",
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldBright,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = EmeraldContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "100% SPEC IMPLEMENTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldBright,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Full pipeline engine support for static, procedural, PorterDuff compositing, and MAML dynamic layers.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = SlateBorder)
            Spacer(modifier = Modifier.height(10.dp))

            val directCount = metadata?.directIconsMap?.size ?: 0
            val rulesCount = metadata?.iconRulesMap?.size ?: 0
            val bgCount = metadata?.iconBgPaths?.size ?: 0
            val mamlCount = metadata?.fancyIconSpecs?.size ?: 0
            val hasMask = metadata?.iconMaskPath != null
            val hasBorder = metadata?.iconBorderPath != null
            val hasPattern = metadata?.iconPatternPath != null
            val hasShortcut = metadata?.iconShortcutPath != null
            val hasMatrix = metadata?.filterColorMatrix != null
            val hasLighting = metadata?.filterLightingMulHex != null

            ApiSpecRow(
                apiTitle = "Direct PNG & Component Mapping",
                specRef = "<package>.png, <activity>.png, <pkg>#<act>.png, alias table",
                status = if (directCount > 0) "$directCount matched PNGs" else "Supported",
                isActive = directCount > 0
            )
            ApiSpecRow(
                apiTitle = "Intent & Component Rules Mapping",
                specRef = "icon_rule.xml / rules.xml (<rule action/component icon=... />)",
                status = if (rulesCount > 0) "$rulesCount active rules" else "Supported",
                isActive = rulesCount > 0
            )
            ApiSpecRow(
                apiTitle = "PorterDuff Dynamic Stencil Masking",
                specRef = "icon_bg.png plate + icon_mask.png DST_IN PorterDuff stencil",
                status = if (hasMask) "DST_IN Active" else "Supported",
                isActive = hasMask
            )
            ApiSpecRow(
                apiTitle = "Multi-Bg Package Hash Distribution",
                specRef = "icon_bg_0.png .. icon_bg_N.png deterministic package hash mapping",
                status = if (bgCount > 1) "$bgCount plates indexed" else if (bgCount == 1) "1 plate indexed" else "Supported",
                isActive = bgCount > 1
            )
            ApiSpecRow(
                apiTitle = "Texture Overlay & Shortcut Badge",
                specRef = "icon_pattern.png background texture + icon_shortcut.png badge",
                status = when {
                    hasPattern && hasShortcut -> "Pattern + Badge"
                    hasPattern -> "Pattern active"
                    hasShortcut -> "Badge active"
                    else -> "Supported"
                },
                isActive = hasPattern || hasShortcut
            )
            ApiSpecRow(
                apiTitle = "Foreground Rim & Specular Border",
                specRef = "icon_border.png (or icon_fg.png / icon_upon.png) SRC_OVER overlay",
                status = if (hasBorder) "Border active" else "Supported",
                isActive = hasBorder
            )
            ApiSpecRow(
                apiTitle = "Procedural Bilinear Coordinate Mapping",
                specRef = "transform_config.xml (<PointsMapping src=... dst=... />)",
                status = if (metadata != null) "${String.format(Locale.US, "%.2f", metadata.pointsScaleX)}x scale" else "Supported",
                isActive = metadata != null
            )
            ApiSpecRow(
                apiTitle = "Filter Shaders & Color Matrices",
                specRef = "<Filter name=ColorMatrix / Lighting / Levels / GrayScale / Hsl>",
                status = when {
                    hasMatrix && hasLighting -> "ColorMatrix + Lighting"
                    hasMatrix -> "ColorMatrix"
                    hasLighting -> "Lighting"
                    metadata != null -> "GrayScale (${metadata.filterBlackColorHex}..${metadata.filterWhiteColorHex})"
                    else -> "Supported"
                },
                isActive = metadata != null
            )
            ApiSpecRow(
                apiTitle = "Xiaomi MAML Dynamic Fancy Icons",
                specRef = "fancy_icons/<package>/manifest.xml (clock hands, calendar, battery)",
                status = if (mamlCount > 0) "$mamlCount live scripts" else "Supported",
                isActive = mamlCount > 0
            )
            ApiSpecRow(
                apiTitle = "Typography & Metrics Specification",
                specRef = "theme_values.xml (icon_scale, icon_padding, icon_title_color)",
                status = if (metadata != null) "${metadata.iconScale}x, ${metadata.titleColorHex}" else "Supported",
                isActive = metadata != null
            )
        }
    }
}

@Composable
private fun ApiSpecRow(
    apiTitle: String,
    specRef: String,
    status: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = apiTitle,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = specRef,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = if (isActive) EmeraldContainer else SlateSurfaceElevated,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.border(
                1.dp,
                if (isActive) EmeraldBright.copy(alpha = 0.6f) else SlateBorder,
                RoundedCornerShape(6.dp)
            )
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) EmeraldBright else TextSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun TechMetricPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SlateDeepBackground)
            .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = EmeraldBright,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun XmlBlockCard(title: String, xml: String) {
    Surface(
        color = SlateDeepBackground,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = EmeraldBright
            )
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = SlateBorder)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = xml,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
        }
    }
}
