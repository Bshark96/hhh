package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DockCornerImageSpec
import com.example.domain.model.DockImageScaleMode
import com.example.domain.model.DockSideImageSpec
import com.example.domain.model.DockXmlConfig
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object DockXmlPresets {
    const val STOCK_NONE_XML = ""

    val BORDER_ONLY_XML = """
        <DockConfig version="1" enabled="true" cornerRadius="22">
            <Layout height="72" marginHorizontal="10" marginBottom="6" paddingHorizontal="8" paddingVertical="6" iconScale="1.0" itemSpacing="4" />
            <Background cornerRadius="22" alpha="0.0" />
            <Border width="1.0" color="#33FFFFFF" cornerRadius="22" />
        </DockConfig>
    """.trimIndent()

    val BORDER_AND_BACKGROUND_XML = """
        <DockConfig version="1" enabled="true" cornerRadius="22">
            <Layout height="72" marginHorizontal="10" marginBottom="6" paddingHorizontal="8" paddingVertical="6" iconScale="1.0" itemSpacing="4" />
            <Background color="#660B1118" cornerRadius="22" alpha="1.0" />
            <Border width="1.0" color="#33FFFFFF" cornerRadius="22" />
        </DockConfig>
    """.trimIndent()

    val BACKGROUND_ONLY_XML = """
        <DockConfig version="1" enabled="true" cornerRadius="22">
            <Layout height="72" marginHorizontal="10" marginBottom="6" paddingHorizontal="8" paddingVertical="6" iconScale="1.0" itemSpacing="4" />
            <Background color="#660B1118" cornerRadius="22" alpha="1.0" />
            <Border width="0" cornerRadius="22" />
        </DockConfig>
    """.trimIndent()
}

private fun parseSafeComposeColor(hex: String?, fallback: Color = Color.Transparent): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Renders the Home Screen Bottom Dock according to [dockConfig]:
 * - **Zero-Config Stock MIUI Fallback**: When `!dockConfig.isCustomDockEnabled`, renders a completely
 *   transparent, borderless container identical to the stock MIUI launcher pin bar.
 * - **Custom `<DockConfig>` Mode**: Renders customizable `<Background>` (solid/gradient/image/per-corner radius),
 *   `<Border>` (width, solid/gradient stroke, dash pattern, outer glow), and `<SideImages>` on all 4 sides
 *   (`<Top>`, `<Bottom>`, `<Left>`, `<Right>`) plus 4 corners (`<CornerTopLeft>`..`<CornerBottomRight>`).
 */
@Composable
fun MiuiCustomizableDockContainer(
    dockConfig: DockXmlConfig,
    extractedThemeDirPath: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    if (!dockConfig.isCustomDockEnabled) {
        // Stock MIUI Transparent Dock (Default when no <DockConfig> is present in XML)
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            content = content
        )
        return
    }

    val shape = RoundedCornerShape(
        topStart = (dockConfig.topLeftRadiusDp ?: dockConfig.cornerRadiusDp).dp,
        topEnd = (dockConfig.topRightRadiusDp ?: dockConfig.cornerRadiusDp).dp,
        bottomEnd = (dockConfig.bottomRightRadiusDp ?: dockConfig.cornerRadiusDp).dp,
        bottomStart = (dockConfig.bottomLeftRadiusDp ?: dockConfig.cornerRadiusDp).dp
    )

    val bgAlpha = dockConfig.backgroundAlpha.coerceIn(0f, 1f)
    val bgStart = parseSafeComposeColor(dockConfig.gradientStartHex)
    val bgCenter = parseSafeComposeColor(dockConfig.gradientCenterHex)
    val bgEnd = parseSafeComposeColor(dockConfig.gradientEndHex)
    val bgSolid = parseSafeComposeColor(dockConfig.backgroundColorHex, Color(0x66121A24))

    val hasBgGradient = dockConfig.gradientStartHex != null && dockConfig.gradientEndHex != null
    val borderStart = parseSafeComposeColor(dockConfig.borderGradientStartHex)
    val borderEnd = parseSafeComposeColor(dockConfig.borderGradientEndHex)
    val borderSolid = parseSafeComposeColor(dockConfig.borderColorHex, Color.White.copy(alpha = 0.35f))
    val hasBorderGradient = dockConfig.borderGradientStartHex != null && dockConfig.borderGradientEndHex != null
    val glowColor = parseSafeComposeColor(dockConfig.glowColorHex, Color.Transparent)

    val bgBitmap = remember(dockConfig.backgroundImage, extractedThemeDirPath) {
        loadDockAssetBitmap(dockConfig.backgroundImage, extractedThemeDirPath)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = dockConfig.marginHorizontalDp.dp,
                end = dockConfig.marginHorizontalDp.dp,
                bottom = dockConfig.marginBottomDp.dp,
                top = 4.dp
            )
            .defaultMinSize(minHeight = (dockConfig.heightDp ?: 68f).dp)
            .testTag("custom_xml_dock_container")
    ) {
        // 1. Background + Border Canvas Layer
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
        ) {
            val angleRad = Math.toRadians(dockConfig.gradientAngle.toDouble())
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()
            val halfW = size.width / 2f
            val halfH = size.height / 2f
            val startOffset = Offset(halfW - cosA * halfW, halfH - sinA * halfH)
            val endOffset = Offset(halfW + cosA * halfW, halfH + sinA * halfH)

            if (hasBgGradient) {
                val colors = if (dockConfig.gradientCenterHex != null) {
                    listOf(bgStart.copy(alpha = bgStart.alpha * bgAlpha), bgCenter.copy(alpha = bgCenter.alpha * bgAlpha), bgEnd.copy(alpha = bgEnd.alpha * bgAlpha))
                } else {
                    listOf(bgStart.copy(alpha = bgStart.alpha * bgAlpha), bgEnd.copy(alpha = bgEnd.alpha * bgAlpha))
                }
                drawRect(
                    brush = Brush.linearGradient(colors = colors, start = startOffset, end = endOffset),
                    size = size
                )
            } else if (dockConfig.backgroundColorHex != null) {
                drawRect(
                    color = bgSolid.copy(alpha = bgSolid.alpha * bgAlpha),
                    size = size
                )
            }

            if (bgBitmap != null) {
                val imgBmp = bgBitmap.asImageBitmap()
                drawImage(
                    image = imgBmp,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    alpha = bgAlpha
                )
            }
        }

        // 2. Border & Glow Canvas Layer
        if (dockConfig.borderWidthDp > 0f || dockConfig.glowRadiusDp > 0f) {
            val density = LocalDensity.current
            val strokePx = with(density) { dockConfig.borderWidthDp.dp.toPx() }
            val glowPx = with(density) { dockConfig.glowRadiusDp.dp.toPx() }
            val cornerPx = with(density) { dockConfig.cornerRadiusDp.dp.toPx() }
            val dashW = with(density) { dockConfig.dashWidthDp.dp.toPx() }
            val dashG = with(density) { dockConfig.dashGapDp.dp.toPx() }

            Canvas(modifier = Modifier.matchParentSize()) {
                if (glowPx > 0f && glowColor != Color.Transparent) {
                    drawRoundRect(
                        color = glowColor,
                        topLeft = Offset(strokePx / 2f, strokePx / 2f),
                        size = Size(
                            (size.width - strokePx).coerceAtLeast(1f),
                            (size.height - strokePx).coerceAtLeast(1f)
                        ),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = strokePx + glowPx * 0.65f)
                    )
                }

                if (strokePx > 0f) {
                    val pathEffect = if (dashW > 0f && dashG > 0f) {
                        PathEffect.dashPathEffect(floatArrayOf(dashW, dashG), 0f)
                    } else {
                        null
                    }
                    val strokeStyle = Stroke(width = strokePx, pathEffect = pathEffect)
                    val topLeft = Offset(strokePx / 2f, strokePx / 2f)
                    val rectSize = Size(
                        (size.width - strokePx).coerceAtLeast(1f),
                        (size.height - strokePx).coerceAtLeast(1f)
                    )
                    if (hasBorderGradient) {
                        drawRoundRect(
                            brush = Brush.linearGradient(listOf(borderStart, borderEnd)),
                            topLeft = topLeft,
                            size = rectSize,
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = strokeStyle
                        )
                    } else {
                        drawRoundRect(
                            color = borderSolid,
                            topLeft = topLeft,
                            size = rectSize,
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = strokeStyle
                        )
                    }
                }
            }
        }

        // 3. 4-Side Frame Images (<Top>, <Bottom>, <Left>, <Right>)
        dockConfig.topSide?.let { spec ->
            DockSideImageOverlay(
                spec = spec,
                isHorizontal = true,
                extractedThemeDirPath = extractedThemeDirPath,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = spec.offsetDp.dp)
                    .testTag("dock_side_top")
            )
        }
        dockConfig.bottomSide?.let { spec ->
            DockSideImageOverlay(
                spec = spec,
                isHorizontal = true,
                extractedThemeDirPath = extractedThemeDirPath,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-spec.offsetDp).dp)
                    .testTag("dock_side_bottom")
            )
        }
        dockConfig.leftSide?.let { spec ->
            DockSideImageOverlay(
                spec = spec,
                isHorizontal = false,
                extractedThemeDirPath = extractedThemeDirPath,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = spec.offsetDp.dp)
                    .testTag("dock_side_left")
            )
        }
        dockConfig.rightSide?.let { spec ->
            DockSideImageOverlay(
                spec = spec,
                isHorizontal = false,
                extractedThemeDirPath = extractedThemeDirPath,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-spec.offsetDp).dp)
                    .testTag("dock_side_right")
            )
        }

        // 4. 4-Corner Ornament Images (<CornerTopLeft> .. <CornerBottomRight>)
        dockConfig.cornerTopLeft?.let { cSpec ->
            DockCornerOrnamentOverlay(
                spec = cSpec,
                extractedThemeDirPath = extractedThemeDirPath,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = cSpec.offsetDp.dp, y = cSpec.offsetDp.dp)
                    .testTag("dock_corner_tl")
            )
        }
        dockConfig.cornerTopRight?.let { cSpec ->
            DockCornerOrnamentOverlay(
                spec = cSpec,
                extractedThemeDirPath = extractedThemeDirPath,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-cSpec.offsetDp).dp, y = cSpec.offsetDp.dp)
                    .testTag("dock_corner_tr")
            )
        }
        dockConfig.cornerBottomLeft?.let { cSpec ->
            DockCornerOrnamentOverlay(
                spec = cSpec,
                extractedThemeDirPath = extractedThemeDirPath,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = cSpec.offsetDp.dp, y = (-cSpec.offsetDp).dp)
                    .testTag("dock_corner_bl")
            )
        }
        dockConfig.cornerBottomRight?.let { cSpec ->
            DockCornerOrnamentOverlay(
                spec = cSpec,
                extractedThemeDirPath = extractedThemeDirPath,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-cSpec.offsetDp).dp, y = (-cSpec.offsetDp).dp)
                    .testTag("dock_corner_br")
            )
        }

        // 5. Inner Padded Dock Content
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dockConfig.paddingHorizontalDp.dp,
                    vertical = dockConfig.paddingVerticalDp.dp
                ),
            content = content
        )
    }
}

@Composable
private fun DockSideImageOverlay(
    spec: DockSideImageSpec,
    isHorizontal: Boolean,
    extractedThemeDirPath: String?,
    modifier: Modifier = Modifier
) {
    val fileBmp = remember(spec.src, extractedThemeDirPath) {
        loadDockAssetBitmap(spec.src, extractedThemeDirPath)
    }
    val tintColor = remember(spec.tintHex) {
        parseSafeComposeColor(spec.tintHex, Color(0xFF38BDF8))
    }

    val sizeModifier = if (isHorizontal) {
        modifier
            .fillMaxWidth(spec.lengthFraction.coerceIn(0.1f, 1f))
            .height(spec.thicknessDp.dp)
    } else {
        modifier
            .width(spec.thicknessDp.dp)
            .fillMaxHeight(spec.lengthFraction.coerceIn(0.1f, 1f))
    }

    Canvas(modifier = sizeModifier.alpha(spec.alpha.coerceIn(0f, 1f))) {
        if (fileBmp != null) {
            val img = fileBmp.asImageBitmap()
            when (spec.mode) {
                DockImageScaleMode.REPEAT -> {
                    val stepX = img.width.coerceAtLeast(8)
                    val stepY = img.height.coerceAtLeast(8)
                    var x = 0
                    while (x < size.width.toInt()) {
                        var y = 0
                        while (y < size.height.toInt()) {
                            drawImage(
                                image = img,
                                dstOffset = IntOffset(x, y),
                                dstSize = IntSize(stepX, stepY)
                            )
                            y += stepY
                        }
                        x += stepX
                    }
                }
                else -> {
                    drawImage(
                        image = img,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                }
            }
        } else {
            // Built-in Procedural Side Image Texture (e.g. builtin:neon_rail, builtin:gold_filigree, builtin:crystal_edge, builtin:cyber_brackets)
            val key = spec.src.lowercase()
            when {
                key.contains("gold") || key.contains("filigree") -> {
                    val brush = Brush.linearGradient(
                        listOf(
                            tintColor.copy(alpha = 0.25f),
                            tintColor,
                            Color(0xFFFFFBEB),
                            tintColor,
                            tintColor.copy(alpha = 0.25f)
                        )
                    )
                    drawRoundRect(
                        brush = brush,
                        cornerRadius = CornerRadius(size.minDimension / 2f, size.minDimension / 2f)
                    )
                    val count = if (isHorizontal) (size.width / 18f).toInt().coerceAtLeast(3) else (size.height / 18f).toInt().coerceAtLeast(2)
                    for (i in 1 until count) {
                        val frac = i.toFloat() / count
                        val cx = if (isHorizontal) size.width * frac else size.width / 2f
                        val cy = if (isHorizontal) size.height / 2f else size.height * frac
                        drawCircle(
                            color = Color(0xFFFFFBEB),
                            radius = (size.minDimension * 0.28f).coerceAtLeast(1.5f),
                            center = Offset(cx, cy)
                        )
                    }
                }
                key.contains("cyber") || key.contains("bracket") -> {
                    drawRoundRect(
                        color = tintColor.copy(alpha = 0.88f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    val segs = 4
                    for (i in 0 until segs) {
                        val frac = (i + 0.5f) / segs
                        val cx = if (isHorizontal) size.width * frac else size.width / 2f
                        val cy = if (isHorizontal) size.height / 2f else size.height * frac
                        drawCircle(
                            color = Color.White,
                            radius = (size.minDimension * 0.24f).coerceAtLeast(1.5f),
                            center = Offset(cx, cy)
                        )
                    }
                }
                else -> {
                    // neon_rail / crystal_edge / frosted_bevel
                    val brush = Brush.linearGradient(
                        listOf(
                            tintColor.copy(alpha = 0.08f),
                            tintColor.copy(alpha = 0.95f),
                            Color.White,
                            tintColor.copy(alpha = 0.95f),
                            tintColor.copy(alpha = 0.08f)
                        )
                    )
                    drawRoundRect(
                        brush = brush,
                        cornerRadius = CornerRadius(size.minDimension / 2f, size.minDimension / 2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DockCornerOrnamentOverlay(
    spec: DockCornerImageSpec,
    extractedThemeDirPath: String?,
    modifier: Modifier = Modifier
) {
    val fileBmp = remember(spec.src, extractedThemeDirPath) {
        loadDockAssetBitmap(spec.src, extractedThemeDirPath)
    }
    val tintColor = remember(spec.tintHex) {
        parseSafeComposeColor(spec.tintHex, Color(0xFF38BDF8))
    }

    Canvas(
        modifier = modifier
            .size(spec.sizeDp.dp)
            .alpha(spec.alpha.coerceIn(0f, 1f))
    ) {
        if (fileBmp != null) {
            drawImage(
                image = fileBmp.asImageBitmap(),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            )
        } else {
            val r = size.minDimension / 2f
            drawCircle(
                color = tintColor.copy(alpha = 0.35f),
                radius = r
            )
            drawCircle(
                color = tintColor,
                radius = r * 0.62f
            )
            drawCircle(
                color = Color.White,
                radius = r * 0.28f
            )
        }
    }
}

private fun loadDockAssetBitmap(
    imagePath: String?,
    extractedThemeDirPath: String?
): android.graphics.Bitmap? {
    if (imagePath.isNullOrBlank() || imagePath.startsWith("builtin:", ignoreCase = true)) {
        return null
    }
    return try {
        val directFile = File(imagePath)
        val resolvedFile = when {
            directFile.isAbsolute && directFile.exists() -> directFile
            !extractedThemeDirPath.isNullOrBlank() -> {
                val candidate1 = File(extractedThemeDirPath, imagePath)
                val candidate2 = File(File(extractedThemeDirPath, "icons"), imagePath)
                when {
                    candidate1.exists() -> candidate1
                    candidate2.exists() -> candidate2
                    else -> null
                }
            }
            else -> null
        }
        resolvedFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun DockXmlPresetSelectorSection(
    customDockXml: String,
    effectiveDockConfig: DockXmlConfig,
    onUpdateCustomDockXml: (String) -> Unit,
    onOpenXmlEditorModal: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Dock XML Customization (<DockConfig>)",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Text(
                    text = if (effectiveDockConfig.isCustomDockEnabled) {
                        when {
                            effectiveDockConfig.borderWidthDp > 0f && effectiveDockConfig.backgroundAlpha > 0f && effectiveDockConfig.backgroundColorHex != null ->
                                "Active: Border + Background (Rounded)"
                            effectiveDockConfig.borderWidthDp > 0f ->
                                "Active: Border Only (Rounded)"
                            else ->
                                "Active: Background Only (Rounded)"
                        }
                    } else {
                        "Default: Stock MIUI Transparent Dock (No XML override)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (effectiveDockConfig.isCustomDockEnabled) EmeraldBright else TextSecondary
                )
            }
            Surface(
                color = EmeraldContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .border(1.dp, EmeraldBright, RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenXmlEditorModal)
                    .testTag("open_dock_xml_editor_button")
            ) {
                Text(
                    text = "Edit XML",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldBright,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        val presets = listOf(
            "Stock (None)" to DockXmlPresets.STOCK_NONE_XML,
            "Border Only" to DockXmlPresets.BORDER_ONLY_XML,
            "Border + Background" to DockXmlPresets.BORDER_AND_BACKGROUND_XML,
            "Background Only" to DockXmlPresets.BACKGROUND_ONLY_XML
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEachIndexed { idx, (label, xml) ->
                val isSelected = if (xml.isEmpty()) {
                    customDockXml.isBlank() && !effectiveDockConfig.isCustomDockEnabled
                } else {
                    customDockXml.trim() == xml.trim()
                }
                Surface(
                    color = if (isSelected) EmeraldContainer else SlateSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) EmeraldBright else SlateBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onUpdateCustomDockXml(xml) }
                        .testTag("dock_xml_preset_$idx")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) EmeraldBright else TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DockXmlLiveEditorDialog(
    initialXml: String,
    themeDefaultXml: String,
    onDismiss: () -> Unit,
    onApplyXml: (String) -> Unit
) {
    var xmlInput by remember(initialXml) {
        mutableStateOf(
            initialXml.ifBlank {
                themeDefaultXml.ifBlank { DockXmlPresets.BORDER_AND_BACKGROUND_XML }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = {
            Text(
                text = "Live <DockConfig> XML Editor",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Customize <Background>, <Border>, and <Layout> with rounded corners (or optional <SideImages>). Clear or click 'Stock Default' for unmodified stock MIUI transparent dock.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    TextButton(onClick = { xmlInput = DockXmlPresets.BORDER_ONLY_XML }) {
                        Text("Border Only", color = EmeraldBright, fontSize = 11.sp)
                    }
                    TextButton(onClick = { xmlInput = DockXmlPresets.BORDER_AND_BACKGROUND_XML }) {
                        Text("Border + Background", color = EmeraldBright, fontSize = 11.sp)
                    }
                    TextButton(onClick = { xmlInput = DockXmlPresets.BACKGROUND_ONLY_XML }) {
                        Text("Background Only", color = EmeraldBright, fontSize = 11.sp)
                    }
                }
                OutlinedTextField(
                    value = xmlInput,
                    onValueChange = { xmlInput = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    minLines = 10,
                    maxLines = 14,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldBright,
                        unfocusedBorderColor = SlateBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dock_xml_editor_input")
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = {
                        onApplyXml("")
                        onDismiss()
                    },
                    modifier = Modifier.testTag("dock_xml_reset_stock_button")
                ) {
                    Text("Stock Default (None)", color = TextSecondary)
                }
                TextButton(
                    onClick = {
                        onApplyXml(xmlInput)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("dock_xml_apply_button")
                ) {
                    Text("Apply XML", color = EmeraldBright, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
