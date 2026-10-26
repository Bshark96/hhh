package com.example.domain.model

import android.graphics.Bitmap

/**
 * Identifies which stage of the Xiaomi MIUI Icon Theme API resolved the icon.
 */
enum class IconResolutionSource(val code: String, val label: String) {
    MANUAL_OVERRIDE("OVERRIDE", "Manual Per-App Mapping"),
    MTZ_FANCY_MAML("MAML_FANCY", "fancy_icons/manifest.xml (MAML Dynamic)"),
    MTZ_DIRECT_MAP("DIRECT_PNG", "res/drawable-xxhdpi Package / Component Match"),
    MTZ_RULE_MAP("ICON_RULE", "icon_rule.xml Intent / Component Rule"),
    MTZ_DYNAMIC_MASK("ICON_TRANSFORM", "MIUI <IconTransform> Dynamic Pipeline"),
    SYSTEM_RAW("RAW_SYSTEM", "Unmodified PackageManager Drawable")
}

/**
 * MIUI Folder Sizing Modes:
 * - REGULAR_1X1: Standard 1x1 folder; tapping opens the expanded folder overlay.
 * - CAPSULE_2X1: Horizontal 2x1 folder; first 2 apps launch directly, 3rd slot expands folder.
 * - TALL_1X2: Vertical 1x2 folder; first 2 apps launch directly, 3rd slot expands folder.
 * - BIG_FOLDER_2X2: 2x2 Big Folder; first 3 apps launch directly, 4th slot (2x2 mini cluster) expands folder.
 * - XXL_FOLDER_3X3: 3x3 Folder; first 8 apps launch directly, 9th slot expands folder.
 */
enum class HyperFolderLayoutMode(
    val code: String,
    val displayName: String,
    val directTapSlots: Int,
    val spanX: Int,
    val spanY: Int
) {
    REGULAR_1X1("1x1", "1x1 Regular", 0, 1, 1),
    CAPSULE_2X1("2x1", "2x1 Wide", 2, 2, 1),
    TALL_1X2("1x2", "1x2 Tall", 2, 1, 2),
    BIG_FOLDER_2X2("2x2", "2x2 Big Folder", 3, 2, 2),
    XXL_FOLDER_3X3("3x3", "3x3 Large", 8, 3, 3);

    companion object {
        fun fromCode(code: String): HyperFolderLayoutMode {
            return entries.firstOrNull { it.code == code } ?: REGULAR_1X1
        }

        fun fromSpan(spanX: Int, spanY: Int): HyperFolderLayoutMode {
            val sx = spanX.coerceIn(1, 3)
            val sy = spanY.coerceIn(1, 3)
            return when {
                sx >= 3 && sy >= 3 -> XXL_FOLDER_3X3
                sx >= 2 && sy >= 2 -> BIG_FOLDER_2X2
                sx >= 2 && sy == 1 -> CAPSULE_2X1
                sx == 1 && sy >= 2 -> TALL_1X2
                else -> REGULAR_1X1
            }
        }
    }
}

/**
 * Represents a Folder on the Home Workspace containing `.mtz`-themed applications.
 */
data class WorkspaceFolder(
    val id: String,
    val title: String,
    val layoutMode: HyperFolderLayoutMode,
    val packageNames: List<String>,
    val apps: List<InstalledAppItem>,
    val sortOrder: Int,
    val pageIndex: Int = 0,
    val cellX: Int = -1,
    val cellY: Int = -1,
    val backgroundColorHex: String = "#8C9AA8",
    val renderKey: String = ""
) {
    val spanX: Int get() = layoutMode.spanX
    val spanY: Int get() = layoutMode.spanY
}

/**
 * Built-in MIUI Widget Types & Native Android AppWidgetHost integration.
 */
enum class HyperWidgetType(
    val code: String,
    val title: String,
    val badgeSize: String,
    val description: String,
    val defaultSpanX: Int,
    val defaultSpanY: Int
) {
    CLOCK_MTZ_STATUS(
        "CLOCK_MTZ_STATUS",
        "MIUI Clock & Date",
        "4x2",
        "Standard MIUI digital clock and date widget",
        4,
        2
    ),
    DEVICE_TELEMETRY(
        "DEVICE_TELEMETRY",
        "System Status",
        "2x2",
        "Battery level and memory usage",
        2,
        2
    ),
    STORAGE_AND_PACKS(
        "STORAGE_AND_PACKS",
        "Storage Status",
        "2x2",
        "Internal storage usage",
        2,
        2
    ),
    MAML_LIVE_DUO(
        "MAML_LIVE_DUO",
        "Clock & Calendar Shortcuts",
        "4x1",
        "Live MAML dynamic clock and calendar icons",
        4,
        1
    ),
    NATIVE_APPWIDGET(
        "NATIVE_APPWIDGET",
        "Android System Widget",
        "4x2",
        "Bound system AppWidgetProvider hosted via AppWidgetHostView",
        4,
        2
    );

    companion object {
        fun fromCode(code: String): HyperWidgetType {
            return entries.firstOrNull { it.code == code } ?: CLOCK_MTZ_STATUS
        }
    }
}

/**
 * Represents an active Widget pinned to the Home Workspace.
 */
data class WorkspaceWidgetItem(
    val id: String,
    val widgetType: HyperWidgetType,
    val appWidgetId: Int = -1,
    val providerComponent: String? = null,
    val customTitle: String,
    val sortOrder: Int,
    val pageIndex: Int = 0,
    val cellX: Int = -1,
    val cellY: Int = -1,
    val spanX: Int = widgetType.defaultSpanX,
    val spanY: Int = widgetType.defaultSpanY
)

/**
 * Device status snapshot.
 */
data class DeviceTelemetrySnapshot(
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 29.5f,
    val usedRamMb: Long = 2048L,
    val totalRamMb: Long = 4096L,
    val usedStorageGb: Float = 12.4f,
    val totalStorageGb: Float = 64.0f
)

/**
 * Represents a real Android AppWidgetProvider discovered via AppWidgetManager.
 */
data class SystemWidgetProviderItem(
    val providerComponent: String,
    val packageName: String,
    val label: String,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val iconBitmap: Bitmap
)

/**
 * Keyframe in a MAML `<VariableAnimation>` timeline (`<AniFrame value="..." time="..." easeType="..."/>`).
 */
data class MamlAniFrame(
    val value: Float,
    val timeMs: Int,
    val easeType: String = "Linear"
)

/**
 * MAML `<Var name="iconAni"><VariableAnimation>...</VariableAnimation></Var>` definition.
 */
data class MamlVarAnimation(
    val name: String,
    val frames: List<MamlAniFrame>
)

/**
 * Represents a single layer or group directive in a Xiaomi MAML (`fancy_icons/<package>/manifest.xml`) dynamic icon.
 */
data class MamlLayerSpec(
    val name: String,
    val layerType: String = "IMAGE",
    val imageFileName: String? = null,
    val centerX: Float = 96f,
    val centerY: Float = 96f,
    val pivotX: Float = 96f,
    val pivotY: Float = 96f,
    val xExpression: String? = null,
    val yExpression: String? = null,
    val pivotXExpression: String? = null,
    val pivotYExpression: String? = null,
    val widthExpression: String? = null,
    val heightExpression: String? = null,
    val scaleExpression: String? = null,
    val scaleXExpression: String? = null,
    val scaleYExpression: String? = null,
    val rotationExpression: String? = null,
    val alphaExpression: String? = null,
    val textExpression: String? = null,
    val textColorHex: String = "#ECF0F1",
    val textSizePx: Float = 44f,
    val alignVCenter: Boolean = false,
    val alignHCenter: Boolean = true,
    val alignAttr: String = "center",
    val alignVAttr: String = "center",
    val alpha: Int = 255,
    val cornerRadius: Float = 0f,
    val fillColorHex: String = "#000000",
    val xfermode: String? = null,
    val groupLayered: Boolean = false,
    val groupClip: Boolean = false
)

/**
 * Represents a parsed Xiaomi `fancy_icons/<package>/manifest.xml` specification.
 */
data class MamlFancyIconSpec(
    val targetPackage: String,
    val directoryPath: String,
    val width: Int = 192,
    val height: Int = 192,
    val frameRate: Int = 30,
    val layers: List<MamlLayerSpec>,
    val rawManifestXml: String,
    val variables: Map<String, MamlVarAnimation> = emptyMap(),
    val quietImagePath: String? = null,
    val startImagePath: String? = null,
    val isLiveClockOrCalendar: Boolean = false
)

/**
 * Mode for `<Filter name="ColorSplash">` in `transform_config.xml`.
 */
enum class ColorSplashMode {
    KEEP_ORIGINAL,
    TINT_SPLASH
}

/**
 * Represents a single filter step in `<IconFilters>` inside `transform_config.xml`,
 * executed in the exact XML order declared by the theme designer.
 */
sealed class MtzIconFilterStep {
    // Native MIUI filters
    data class StripAdaptivePlate(val normalizeBounds: Boolean = true) : MtzIconFilterStep()
    data class Edges(val threshold: Int = 32) : MtzIconFilterStep()
    data class Spread(val radius: Int = 1) : MtzIconFilterStep()
    data class GausBlur(val radius: Int = 1) : MtzIconFilterStep()
    data class GrayScale(
        val blackColorHex: String = "#000000",
        val whiteColorHex: String = "#FFFFFF",
        val alpha: Int = 255
    ) : MtzIconFilterStep()
    data class Levels(
        val inputMin: Int = 0,
        val inputMiddle: Float = 1.0f,
        val inputMax: Int = 255,
        val outputMin: Int = 0,
        val outputMax: Int = 255
    ) : MtzIconFilterStep()
    data class Hsl(
        val hueShift: Float = 0f,
        val saturationModify: Float = 0f,
        val lightnessModify: Float = 0f
    ) : MtzIconFilterStep()
    data class ColorMatrixStep(val matrix: FloatArray) : MtzIconFilterStep()
    data class LightingStep(val mulHex: String?, val addHex: String?) : MtzIconFilterStep()
    data class AlphaStep(val alpha: Int = 255) : MtzIconFilterStep()
    data class PointsMappingStep(
        val srcPoints: FloatArray,
        val dstPoints: FloatArray
    ) : MtzIconFilterStep()

    // Extended Transform API filters
    data class ColorSplash(
        val targetHues: FloatArray = floatArrayOf(0f),
        val toleranceDegrees: Float = 24f,
        val minSaturation: Float = 0.15f,
        val mode: ColorSplashMode = ColorSplashMode.KEEP_ORIGINAL,
        val invertSelection: Boolean = false,
        val splashTintHex: String = "#FF3B30",
        val restTintHex: String? = null
    ) : MtzIconFilterStep()

    data class Duotone(
        val shadowColorHex: String = "#1B1464",
        val midColorHex: String? = null,
        val highlightColorHex: String = "#00F5D4",
        val intensity: Float = 1.0f
    ) : MtzIconFilterStep()

    data class ColorAdjust(
        val hueShift: Float = 0f,
        val saturationScale: Float = 1.0f,
        val contrast: Float = 1.0f,
        val brightness: Float = 0f
    ) : MtzIconFilterStep()

    data class EdgeGlow(
        val edgeColorHex: String = "#00F5D4",
        val baseColorHex: String = "#00000000",
        val threshold: Int = 36,
        val fillAlpha: Float = 0.0f,
        val glowRadius: Int = 1
    ) : MtzIconFilterStep()

    data class Posterize(
        val levels: Int = 4
    ) : MtzIconFilterStep()

    data class Sepia(
        val intensity: Float = 1.0f
    ) : MtzIconFilterStep()

    data class Invert(
        val intensity: Float = 1.0f
    ) : MtzIconFilterStep()

    data class Monochrome(
        val colorHex: String = "#ECF0F1",
        val intensity: Float = 1.0f
    ) : MtzIconFilterStep()
}

/**
 * Complete parsed structure of a Xiaomi `.mtz` or `.mtx` theme archive.
 */
data class MtzThemeMetadata(
    val id: String,
    val title: String,
    val author: String,
    val designer: String,
    val version: String,
    val uiVersion: String,
    val description: String,
    val archiveFileName: String,
    val extractedDirPath: String,
    val iconScale: Float,
    val iconPaddingDp: Float,
    val titleColorHex: String,
    val iconBgPaths: List<String>,
    val iconMaskPath: String?,
    val iconBorderPath: String?,
    val iconPatternPath: String? = null,
    val iconShortcutPath: String? = null,
    val iconFolderPath: String? = null,
    val folderCoverPath: String? = null,
    val symDefAppIconPath: String? = null,
    val directIconsMap: Map<String, String>,
    val iconRulesMap: Map<String, String> = emptyMap(),
    val fancyIconSpecs: Map<String, MamlFancyIconSpec>,
    val wallpaperPath: String?,
    val rawDescriptionXml: String,
    val rawThemeValuesXml: String,
    val rawTransformConfigXml: String,
    val rawIconRulesXml: String = "",
    val isBuiltIn: Boolean,
    val importedAtMillis: Long,
    val hasTransformConfig: Boolean = true,
    val filterBlackColorHex: String = "#465967",
    val filterWhiteColorHex: String = "#ECF0F1",
    val filterLevelsInputMin: Int = 95,
    val filterLevelsInputMax: Int = 255,
    val filterLevelsOutputMin: Int = 0,
    val filterLevelsOutputMax: Int = 255,
    val filterLevelsGamma: Float = 1.0f,
    val filterColorMatrix: FloatArray? = null,
    val filterLightingMulHex: String? = null,
    val filterLightingAddHex: String? = null,
    val filterHslHue: Float? = null,
    val filterHslSaturation: Float? = null,
    val filterHslLightness: Float? = null,
    val filterSpreadRadius: Int = 0,
    val filterBlurRadius: Int = 0,
    val filterSteps: List<MtzIconFilterStep> = emptyList(),
    val transformApplyToAllIcons: Boolean = false,
    val isCustomFilterPipeline: Boolean = false,
    val pointsScaleX: Float = 0.78f,
    val pointsScaleY: Float = 0.78f,
    val pointsOffsetX: Float = 0.11f,
    val pointsOffsetY: Float = 0.11f,
    val pointsMappingSrc: FloatArray? = null,
    val pointsMappingDst: FloatArray? = null
)

/**
 * Represents an installed launchable Android application on the user's device,
 * enriched with its resolved Xiaomi MTZ themed icon.
 */
data class InstalledAppItem(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystemApp: Boolean,
    val rawIconBitmap: Bitmap,
    val themedIconBitmap: Bitmap,
    val resolutionSource: IconResolutionSource,
    val matchedAssetName: String,
    val isPinnedToWorkspace: Boolean,
    val isPinnedToDock: Boolean,
    val workspacePage: Int,
    val customOverrideAssetKey: String?,
    val renderKey: String = "",
    val mamlFancySpec: MamlFancyIconSpec? = null,
    val cellX: Int = -1,
    val cellY: Int = -1
)

/**
 * Step-by-step visual and technical breakdown of the Xiaomi Icon Theme API pipeline
 * for a specific application.
 */
data class IconPipelineBreakdown(
    val appLabel: String,
    val packageName: String,
    val activityName: String,
    val resolutionSource: IconResolutionSource,
    val appliedScale: Float,
    val matchedEntryPath: String,
    val rawAppBitmap: Bitmap,
    val backgroundPlateBitmap: Bitmap?,
    val scaledInputBitmap: Bitmap,
    val maskTemplateBitmap: Bitmap?,
    val maskedLayerBitmap: Bitmap,
    val borderOverlayBitmap: Bitmap?,
    val patternOverlayBitmap: Bitmap? = null,
    val shortcutBadgeBitmap: Bitmap? = null,
    val finalOutputBitmap: Bitmap,
    val xmlDiagnosticSnippet: String,
    val mamlVariables: Map<String, String> = emptyMap()
)

/**
 * MIUI Workspace Page Transition Effects selectable from Edit Mode -> Effect.
 */
enum class MiuiPageTransitionEffect(val code: String, val displayName: String) {
    CLASSIC("CLASSIC", "Classic"),
    CUBE("CUBE", "Cube"),
    CASCADE("CASCADE", "Cascade"),
    FLIP("FLIP", "Flip"),
    ROTATE("ROTATE", "Rotate");

    companion object {
        fun fromCode(code: String): MiuiPageTransitionEffect {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: CLASSIC
        }
    }
}

/**
 * Global configuration for the Launcher Grid and Xiaomi Icon Theme API Engine.
 */
data class LauncherEngineConfig(
    val activeThemeId: String = "builtin_midnight_blue",
    val useCustomScaleOverride: Boolean = false,
    val customScaleValue: Float = 0.78f,
    val enableDynamicMasking: Boolean = true,
    val enableThirdPartyColorFilter: Boolean = true,
    val enableFancyMamlIcons: Boolean = true,
    val enableIconBorderOverlay: Boolean = true,
    val enableIconPatternOverlay: Boolean = true,
    val enableShortcutBadge: Boolean = false,
    val enableColorMatrixFilter: Boolean = true,
    val enableLightingFilter: Boolean = true,
    val enableHslFilter: Boolean = true,
    val enableMultiBgHashSelection: Boolean = true,
    val gridColumns: Int = 4,
    val gridRows: Int = 6,
    val iconSizeDp: Int = 58,
    val showAppLabels: Boolean = true,
    val showApiSourceBadges: Boolean = false,
    val pageTransitionEffect: MiuiPageTransitionEffect = MiuiPageTransitionEffect.CLASSIC,
    val defaultHomePageIndex: Int = 0,
    val bottomAlignedPages: Set<Int> = emptySet()
)

/**
 * 2D Non-Scrolling Workspace Grid Allocator (`gridColumns x gridRows`, default `4 x 6`).
 * Packs Widgets (`spanX x spanY`), Folders (`spanX x spanY`), and App Icons (`1 x 1`)
 * side-by-side in the same rows without wasting full rows when `spanX < gridColumns`.
 */
object WorkspaceGridAllocator {

    fun canFit(
        occupied: BooleanArray,
        cols: Int,
        rows: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int
    ): Boolean {
        val clampedSpanX = spanX.coerceIn(1, cols)
        val clampedSpanY = spanY.coerceIn(1, rows)
        if (cellX < 0 || cellY < 0 || cellX + clampedSpanX > cols || cellY + clampedSpanY > rows) {
            return false
        }
        for (y in cellY until (cellY + clampedSpanY)) {
            for (x in cellX until (cellX + clampedSpanX)) {
                if (occupied[y * cols + x]) return false
            }
        }
        return true
    }

    fun markOccupied(
        occupied: BooleanArray,
        cols: Int,
        rows: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int
    ) {
        val clampedSpanX = spanX.coerceIn(1, cols)
        val clampedSpanY = spanY.coerceIn(1, rows)
        for (y in cellY until (cellY + clampedSpanY).coerceAtMost(rows)) {
            for (x in cellX until (cellX + clampedSpanX).coerceAtMost(cols)) {
                if (x >= 0 && y >= 0) {
                    occupied[y * cols + x] = true
                }
            }
        }
    }

    fun findFirstFreeCell(
        occupied: BooleanArray,
        cols: Int,
        rows: Int,
        spanX: Int,
        spanY: Int,
        alignBottom: Boolean = false
    ): Pair<Int, Int>? {
        val clampedSpanX = spanX.coerceIn(1, cols)
        val clampedSpanY = spanY.coerceIn(1, rows)
        val rowIndices = if (alignBottom) {
            ((rows - clampedSpanY) downTo 0).toList()
        } else {
            (0..(rows - clampedSpanY)).toList()
        }
        for (y in rowIndices) {
            for (x in 0..(cols - clampedSpanX)) {
                if (canFit(occupied, cols, rows, x, y, clampedSpanX, clampedSpanY)) {
                    return x to y
                }
            }
        }
        return null
    }

    fun findNearestFreeCell(
        occupied: BooleanArray,
        cols: Int,
        rows: Int,
        preferredCellX: Int,
        preferredCellY: Int,
        spanX: Int,
        spanY: Int
    ): Pair<Int, Int>? {
        val clampedSpanX = spanX.coerceIn(1, cols)
        val clampedSpanY = spanY.coerceIn(1, rows)
        val targetX = preferredCellX.coerceIn(0, (cols - clampedSpanX).coerceAtLeast(0))
        val targetY = preferredCellY.coerceIn(0, (rows - clampedSpanY).coerceAtLeast(0))

        if (canFit(occupied, cols, rows, targetX, targetY, clampedSpanX, clampedSpanY)) {
            return targetX to targetY
        }

        var bestCell: Pair<Int, Int>? = null
        var bestDistSq = Int.MAX_VALUE
        for (y in 0..(rows - clampedSpanY)) {
            for (x in 0..(cols - clampedSpanX)) {
                if (canFit(occupied, cols, rows, x, y, clampedSpanX, clampedSpanY)) {
                    val dx = x - targetX
                    val dy = y - targetY
                    val distSq = dx * dx + dy * dy
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq
                        bestCell = x to y
                    }
                }
            }
        }
        return bestCell
    }
}


