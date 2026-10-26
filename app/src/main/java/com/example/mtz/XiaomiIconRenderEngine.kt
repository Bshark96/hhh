package com.example.mtz

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.Xfermode
import com.example.domain.model.ColorSplashMode
import com.example.domain.model.IconPipelineBreakdown
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.LauncherEngineConfig
import com.example.domain.model.MamlFancyIconSpec
import com.example.domain.model.MamlVarAnimation
import com.example.domain.model.MtzIconFilterStep
import com.example.domain.model.MtzThemeMetadata
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Implements the Xiaomi MIUI / HyperOS Icon Theme Rendering API (`<IconTransform>`).
 *
 * Resolution Pipeline Order:
 * 1. Manual Per-App User Override (`icon_overrides` table)
 * 2. Xiaomi MAML `fancy_icons/<package>/manifest.xml` Dynamic Renderer
 * 3. `icon_rule.xml` Intent / Component Rule Match
 * 4. Direct Package / Component / MIUI Alias PNG in `res/drawable-xxhdpi/`
 * 5. Xiaomi `<IconTransform>` Dynamic Pipeline:
 *    - `icon_background.png` + `<Filter name="GrayScale"><Param name="BlackColor" value="#..."/></Filter>`
 *    - `<PointsMapping>` (`(0,0)-(90,90) -> (10,10)-(80,80)`)
 *    - `<IconFilters>` (`GrayScale` `BlackColor`..`WhiteColor` + `Levels` `InputMin..InputMax` + `ColorMatrix` + `Lighting` + `HSL`)
 *    - `icon_mask.png` (`PorterDuff.Mode.DST_IN`)
 *    - `icon_border.png` (`PorterDuff.Mode.SRC_OVER`)
 * 6. Fallback `sym_def_app_icon.png` or Unmodified `PackageManager` Drawable
 */
class XiaomiIconRenderEngine {

    companion object {
        const val TARGET_ICON_PX = 192
        private const val SHADER_WORK_PX = 192

        private val XIAOMI_PACKAGE_ALIASES: Map<String, List<String>> = mapOf(
            "com.google.android.dialer" to listOf(
                "com.android.contacts.activities.TwelveKeyDialer",
                "com.android.contacts.TwelveKeyDialer",
                "com.android.dialer",
                "com.android.phone",
                "com.android.incallui",
                "com.android.providers.telephony"
            ),
            "com.android.dialer" to listOf(
                "com.android.contacts.activities.TwelveKeyDialer",
                "com.android.contacts.TwelveKeyDialer",
                "com.android.phone",
                "com.google.android.dialer",
                "com.android.providers.telephony"
            ),
            "cn.nubia.contacts" to listOf(
                "com.android.contacts",
                "com.android.contacts.TwelveKeyDialer",
                "com.android.dialer"
            ),
            "com.google.android.contacts" to listOf(
                "com.android.contacts",
                "com.android.contacts.activities.PeopleActivity",
                "com.android.providers.contacts"
            ),
            "com.google.android.apps.messaging" to listOf(
                "com.android.mms",
                "com.android.mms.ui.MmsTabActivity",
                "com.android.providers.telephony"
            ),
            "com.android.messaging" to listOf(
                "com.android.mms",
                "com.android.mms.ui.MmsTabActivity",
                "com.android.providers.telephony"
            ),
            "cn.nubia.mms" to listOf(
                "com.android.mms",
                "com.android.mms.ui.MmsTabActivity"
            ),
            "com.zte.mms" to listOf(
                "com.android.mms",
                "com.android.mms.ui.MmsTabActivity"
            ),
            "com.samsung.android.messaging" to listOf(
                "com.android.mms",
                "com.android.mms.ui.MmsTabActivity"
            ),
            "com.google.android.deskclock" to listOf(
                "com.android.deskclock",
                "com.android.deskclock.DeskClock",
                "com.android.deskclock.DeskClockTabActivity"
            ),
            "com.android.deskclock" to listOf(
                "com.android.deskclock.DeskClock",
                "com.google.android.deskclock",
                "com.android.deskclock.DeskClockTabActivity"
            ),
            "cn.nubia.deskclock.preset" to listOf(
                "com.android.deskclock",
                "com.android.deskclock.DeskClock"
            ),
            "cn.nubia.deskclock" to listOf(
                "com.android.deskclock",
                "com.android.deskclock.DeskClock"
            ),
            "com.zte.deskclock" to listOf(
                "com.android.deskclock",
                "com.android.deskclock.DeskClock"
            ),
            "com.sec.android.app.clockpackage" to listOf(
                "com.android.deskclock",
                "com.android.deskclock.DeskClock"
            ),
            "com.google.android.calendar" to listOf(
                "com.android.calendar",
                "com.xiaomi.calendar",
                "com.miui.calendar",
                "com.android.providers.calendar"
            ),
            "com.android.calendar" to listOf(
                "com.xiaomi.calendar",
                "com.google.android.calendar",
                "com.miui.calendar",
                "com.android.providers.calendar"
            ),
            "com.google.android.calculator" to listOf(
                "com.android.calculator2",
                "com.miui.calculator"
            ),
            "com.android.chrome" to listOf(
                "com.android.browser",
                "com.android.browser.BrowserActivity",
                "com.mi.globalbrowser",
                "com.google.android.apps.chrome"
            ),
            "cn.nubia.browser" to listOf(
                "com.android.browser",
                "com.android.chrome"
            ),
            "app.revanced.android.youtube" to listOf(
                "com.google.android.youtube"
            ),
            "app.rvx.android.youtube" to listOf(
                "com.google.android.youtube"
            ),
            "com.vanced.android.youtube" to listOf(
                "com.google.android.youtube"
            ),
            "com.google.android.documentsui" to listOf(
                "com.android.fileexplorer",
                "com.android.documentsui",
                "com.mi.android.globalFileexplorer",
                "com.android.providers.downloads.ui"
            ),
            "com.android.documentsui" to listOf(
                "com.android.fileexplorer",
                "com.google.android.documentsui",
                "com.android.providers.downloads.ui"
            ),
            "com.android.camera2" to listOf(
                "com.android.camera",
                "com.miui.camera"
            ),
            "cn.nubia.camera" to listOf(
                "com.android.camera",
                "com.miui.camera"
            ),
            "com.zte.camera" to listOf(
                "com.android.camera",
                "com.miui.camera"
            ),
            "com.google.android.GoogleCamera" to listOf(
                "com.android.camera",
                "com.miui.camera"
            ),
            "com.sec.android.app.camera" to listOf(
                "com.android.camera",
                "com.miui.camera"
            ),
            "com.oneplus.camera" to listOf(
                "com.android.camera",
                "com.miui.camera"
            ),
            "com.oppo.camera" to listOf(
                "com.android.camera",
                "com.miui.camera"
            ),
            "com.google.android.apps.photos" to listOf(
                "com.android.gallery3d",
                "com.miui.gallery",
                "com.android.gallery"
            ),
            "cn.nubia.gallery" to listOf(
                "com.android.gallery3d",
                "com.miui.gallery",
                "com.android.gallery"
            ),
            "com.zte.gallery" to listOf(
                "com.android.gallery3d",
                "com.miui.gallery",
                "com.android.gallery"
            ),
            "com.sec.android.gallery3d" to listOf(
                "com.android.gallery3d",
                "com.miui.gallery",
                "com.android.gallery"
            ),
            "com.android.vending" to listOf(
                "com.android.vending.AssetBrowserActivity",
                "com.xiaomi.market",
                "com.miui.supermarket"
            )
        )
    }

    private val bitmapCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val resolvedIconCache = java.util.concurrent.ConcurrentHashMap<String, ResolvedIconResult>()

    fun clearBitmapCache() {
        bitmapCache.clear()
        resolvedIconCache.clear()
    }

    data class ResolvedIconResult(
        val bitmap: Bitmap,
        val source: IconResolutionSource,
        val matchedAssetName: String,
        val renderKey: String,
        val mamlFancySpec: MamlFancyIconSpec? = null
    )

    fun resolveAppIcon(
        packageName: String,
        activityName: String,
        rawAppBitmap: Bitmap,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig,
        customOverrideAssetKey: String?,
        calendar: Calendar = Calendar.getInstance()
    ): ResolvedIconResult {
        val effectiveScale = if (config.useCustomScaleOverride) config.customScaleValue else theme.iconScale
        val scaleRounded = (effectiveScale * 100).toInt()
        val isMamlCandidate = config.enableFancyMamlIcons && findFancyIconSpec(packageName, activityName, theme) != null
        val mamlMinuteBucket = if (isMamlCandidate) calendar.get(Calendar.MINUTE) else 0

        val cacheKey = buildString(160) {
            append("v11_seq_splash|")
            append(theme.id).append('|')
            append(if (theme.hasTransformConfig) 'T' else 'N').append('|')
            append(if (theme.isCustomFilterPipeline) 'C' else 'M').append('|')
            append(if (theme.transformApplyToAllIcons) 'G' else 'F').append('|')
            append(theme.filterSteps.size).append(':').append(theme.rawTransformConfigXml.hashCode()).append('|')
            append(theme.filterBlackColorHex).append('|')
            append(theme.filterWhiteColorHex).append('|')
            append(theme.filterSpreadRadius).append('|')
            append(theme.filterBlurRadius).append('|')
            append(packageName).append('|')
            append(scaleRounded).append('|')
            append(if (config.enableDynamicMasking) '1' else '0')
            append(if (config.enableThirdPartyColorFilter) '1' else '0')
            append(if (config.enableFancyMamlIcons) '1' else '0')
            append(if (config.enableIconBorderOverlay) '1' else '0')
            append(if (config.enableIconPatternOverlay) '1' else '0')
            append(if (config.enableShortcutBadge) '1' else '0')
            append(if (config.enableColorMatrixFilter) '1' else '0')
            append(if (config.enableLightingFilter) '1' else '0')
            append(if (config.enableMultiBgHashSelection) '1' else '0')
            append('|').append(customOverrideAssetKey.orEmpty())
            append('|').append(mamlMinuteBucket)
        }

        resolvedIconCache[cacheKey]?.let { return it }

        val computed = computeAppIconInternal(
            packageName = packageName,
            activityName = activityName,
            rawAppBitmap = rawAppBitmap,
            theme = theme,
            config = config,
            customOverrideAssetKey = customOverrideAssetKey,
            calendar = calendar,
            renderKey = cacheKey
        )

        resolvedIconCache[cacheKey] = computed
        return computed
    }

    private fun computeAppIconInternal(
        packageName: String,
        activityName: String,
        rawAppBitmap: Bitmap,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig,
        customOverrideAssetKey: String?,
        calendar: Calendar,
        renderKey: String
    ): ResolvedIconResult {
        // Stage 1: Check Per-App Manual Override
        if (!customOverrideAssetKey.isNullOrBlank()) {
            val overridePath = theme.directIconsMap[customOverrideAssetKey]
            val overrideBmp = overridePath?.let { decodeBitmapCached(it) }
            if (overrideBmp != null) {
                return ResolvedIconResult(
                    bitmap = maybeApplyGlobalTransform(scaleToTarget(overrideBmp), theme, config),
                    source = IconResolutionSource.MANUAL_OVERRIDE,
                    matchedAssetName = "$customOverrideAssetKey.png",
                    renderKey = renderKey
                )
            }
        }

        // Stage 2: Check Xiaomi MAML fancy_icons/<package>/manifest.xml
        if (config.enableFancyMamlIcons) {
            val fancySpec = findFancyIconSpec(packageName, activityName, theme)
            if (fancySpec != null) {
                val renderedFancy = renderMamlFancyIcon(fancySpec, calendar)
                if (renderedFancy != null) {
                    return ResolvedIconResult(
                        bitmap = maybeApplyGlobalTransform(renderedFancy, theme, config),
                        source = IconResolutionSource.MTZ_FANCY_MAML,
                        matchedAssetName = "fancy_icons/${fancySpec.targetPackage}/manifest.xml",
                        renderKey = renderKey,
                        mamlFancySpec = fancySpec
                    )
                }
            }
        }

        // Stage 3: Check icon_rule.xml Intent / Component Rule Match
        val ruleMatch = findRuleIconPath(packageName, activityName, theme)
        if (ruleMatch != null) {
            val ruleBmp = decodeBitmapCached(ruleMatch.second)
            if (ruleBmp != null) {
                return ResolvedIconResult(
                    bitmap = maybeApplyGlobalTransform(scaleToTarget(ruleBmp), theme, config),
                    source = IconResolutionSource.MTZ_RULE_MAP,
                    matchedAssetName = "icon_rule.xml (${ruleMatch.first})",
                    renderKey = renderKey
                )
            }
        }

        // Stage 4: Direct Package / Component / MIUI Alias PNG Match in res/drawable-xxhdpi/
        val directMatch = findDirectIconPath(packageName, activityName, theme)
        if (directMatch != null) {
            val directBmp = decodeBitmapCached(directMatch.second)
            if (directBmp != null) {
                return ResolvedIconResult(
                    bitmap = maybeApplyGlobalTransform(scaleToTarget(directBmp), theme, config),
                    source = IconResolutionSource.MTZ_DIRECT_MAP,
                    matchedAssetName = "res/drawable-xxhdpi/${directMatch.first}.png",
                    renderKey = renderKey
                )
            }
        }

        // Stage 5: Xiaomi <IconTransform> Dynamic Pipeline (icon_background + PointsMapping + IconFilters + icon_mask + icon_border)
        if (config.enableDynamicMasking) {
            val composite = compositeDynamicMaskedIconFast(
                packageName = packageName,
                rawAppBitmap = rawAppBitmap,
                theme = theme,
                config = config
            )
            val filterLabel = if (config.enableThirdPartyColorFilter) {
                "IconTransform(${theme.filterBlackColorHex}) + "
            } else {
                ""
            }
            return ResolvedIconResult(
                bitmap = composite,
                source = IconResolutionSource.MTZ_DYNAMIC_MASK,
                matchedAssetName = "${filterLabel}icon_mask.png + icon_border.png",
                renderKey = renderKey
            )
        }

        // Stage 6: Fallback sym_def_app_icon.png or Raw Unmodified System Icon
        val fallbackBmp = theme.symDefAppIconPath?.let { decodeBitmapCached(it) }
        val rawOut = scaleToTarget(fallbackBmp ?: rawAppBitmap)
        return ResolvedIconResult(
            bitmap = rawOut,
            source = IconResolutionSource.SYSTEM_RAW,
            matchedAssetName = if (fallbackBmp != null) "sym_def_app_icon.png" else "PackageManager.getActivityIcon()",
            renderKey = renderKey
        )
    }

    /**
     * Renders the theme's background plate at `TARGET_ICON_PX x TARGET_ICON_PX`.
     * Supports both `icon_background.png` / `icon_bg.png` (e.g. `Midnight_Blue.mtz`) and
     * `icon_pattern.png` circular plates when `icon_bg.png` is omitted (e.g. `Trickster hejo V10.mtz`).
     */
    private fun renderThemedBackgroundPlate(
        packageName: String,
        theme: MtzThemeMetadata,
        enableMultiBgHash: Boolean,
        maskBitmap: Bitmap?,
        patternBitmap: Bitmap? = null
    ): Bitmap {
        val plate = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(plate)
        val smoothPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val blackColorInt = parseColorSafe(theme.filterBlackColorHex, Color.parseColor("#465967"))
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = blackColorInt
            style = Paint.Style.FILL
        }
        val bgPath = selectBgPath(packageName, theme, enableMultiBgHash)
        val rawBgBitmap = bgPath?.let { decodeBitmapCached(it) }

        if (rawBgBitmap == null && patternBitmap != null && patternBitmap.width > 4) {
            val patMatrix = Matrix().apply {
                postScale(
                    TARGET_ICON_PX.toFloat() / patternBitmap.width.coerceAtLeast(1),
                    TARGET_ICON_PX.toFloat() / patternBitmap.height.coerceAtLeast(1)
                )
            }
            canvas.drawBitmap(patternBitmap, patMatrix, smoothPaint)
            return plate
        }

        val clipMask = maskBitmap ?: rawBgBitmap
        if (clipMask != null) {
            // Fill the entire masked area with BlackColor (#465967 in Midnight Blue) so any
            // transparent inner region in icon_background.png is solidly backed by BlackColor
            canvas.drawRect(0f, 0f, TARGET_ICON_PX.toFloat(), TARGET_ICON_PX.toFloat(), fillPaint)
            if (rawBgBitmap != null && !isNeutralGrayPlate(rawBgBitmap)) {
                val bgMatrix = Matrix().apply {
                    postScale(
                        TARGET_ICON_PX.toFloat() / rawBgBitmap.width.coerceAtLeast(1),
                        TARGET_ICON_PX.toFloat() / rawBgBitmap.height.coerceAtLeast(1)
                    )
                }
                canvas.drawBitmap(rawBgBitmap, bgMatrix, smoothPaint)
            }
            applyAlphaMaskInPlace(plate, clipMask)
        } else {
            val r = TARGET_ICON_PX * (84f / 192f)
            canvas.drawCircle(TARGET_ICON_PX / 2f, TARGET_ICON_PX / 2f, r, fillPaint)
        }

        return plate
    }

    private fun applyAlphaMaskInPlace(target: Bitmap, mask: Bitmap) {
        val scaledMask = scaleToTarget(mask)
        val n = TARGET_ICON_PX * TARGET_ICON_PX
        val targetPixels = IntArray(n)
        val maskPixels = IntArray(n)
        target.getPixels(targetPixels, 0, TARGET_ICON_PX, 0, 0, TARGET_ICON_PX, TARGET_ICON_PX)
        scaledMask.getPixels(maskPixels, 0, TARGET_ICON_PX, 0, 0, TARGET_ICON_PX, TARGET_ICON_PX)
        for (i in 0 until n) {
            val t = targetPixels[i]
            val tA = t ushr 24
            if (tA == 0) continue
            val mA = maskPixels[i] ushr 24
            if (mA == 0) {
                targetPixels[i] = 0
            } else if (mA < 255) {
                val outA = (tA * mA) / 255
                targetPixels[i] = (t and 0x00FFFFFF) or (outA shl 24)
            }
        }
        target.setPixels(targetPixels, 0, TARGET_ICON_PX, 0, 0, TARGET_ICON_PX, TARGET_ICON_PX)
    }

    private fun isNeutralGrayPlate(bitmap: Bitmap): Boolean {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        if (cx <= 0 || cy <= 0) return false
        val c = bitmap.getPixel(cx, cy)
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val delta = max(r, max(g, b)) - min(r, min(g, b))
        return delta <= 8
    }

    /**
     * Fast-path `<IconTransform>` compositor for Workspace & App Drawer rendering.
     */
    private fun compositeDynamicMaskedIconFast(
        packageName: String,
        rawAppBitmap: Bitmap,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig
    ): Bitmap {
        val effectiveScaleX = if (config.useCustomScaleOverride) {
            config.customScaleValue.coerceIn(0.45f, 1.0f)
        } else {
            theme.pointsScaleX.coerceIn(0.45f, 1.0f)
        }
        val effectiveScaleY = if (config.useCustomScaleOverride) {
            config.customScaleValue.coerceIn(0.45f, 1.0f)
        } else {
            theme.pointsScaleY.coerceIn(0.45f, 1.0f)
        }

        val maskBitmap = theme.iconMaskPath?.let { decodeBitmapCached(it) }
        val safeBorderPath = theme.iconBorderPath?.takeIf { it != theme.iconPatternPath }
        val borderBitmap = if (config.enableIconBorderOverlay) {
            safeBorderPath?.let { decodeBitmapCached(it) }
        } else {
            null
        }
        val patternBitmap = if (config.enableIconPatternOverlay) {
            theme.iconPatternPath?.let { decodeBitmapCached(it) }?.takeIf { it.width > 4 && it.height > 4 }
        } else {
            null
        }
        val shortcutBitmap = if (config.enableShortcutBadge) {
            theme.iconShortcutPath?.let { decodeBitmapCached(it) }
        } else {
            null
        }

        val bgPlateBitmap = renderThemedBackgroundPlate(
            packageName = packageName,
            theme = theme,
            enableMultiBgHash = config.enableMultiBgHashSelection,
            maskBitmap = maskBitmap,
            patternBitmap = patternBitmap
        )

        val shouldRunTransformFilter = config.enableThirdPartyColorFilter &&
            theme.hasTransformConfig && theme.filterSteps.isNotEmpty()
        val filteredInput = if (shouldRunTransformFilter) {
            applyThemeIconTransformFilter(rawAppBitmap, theme, config)
        } else {
            ensureSoftwareBitmap(rawAppBitmap)
        }

        val smoothPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Scale / 4-point warp (`Matrix.setPolyToPoly`) and mask the foreground layer according to <PointsMapping>
        val maskedLayer = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskedLayer)
        val matrix = buildPointsMappingMatrix(
            srcW = filteredInput.width.coerceAtLeast(1),
            srcH = filteredInput.height.coerceAtLeast(1),
            dstSize = TARGET_ICON_PX,
            effectiveScaleX = effectiveScaleX,
            effectiveScaleY = effectiveScaleY,
            theme = theme,
            config = config
        )
        maskCanvas.drawBitmap(filteredInput, matrix, smoothPaint)

        if (maskBitmap != null) {
            applyAlphaMaskInPlace(maskedLayer, maskBitmap)
        }

        val output = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val outCanvas = Canvas(output)
        outCanvas.drawBitmap(bgPlateBitmap, 0f, 0f, smoothPaint)

        if (patternBitmap != null && theme.iconBgPaths.isNotEmpty()) {
            val patMatrix = Matrix().apply {
                postScale(
                    TARGET_ICON_PX.toFloat() / patternBitmap.width.coerceAtLeast(1),
                    TARGET_ICON_PX.toFloat() / patternBitmap.height.coerceAtLeast(1)
                )
            }
            outCanvas.drawBitmap(patternBitmap, patMatrix, smoothPaint)
        }

        outCanvas.drawBitmap(maskedLayer, 0f, 0f, smoothPaint)

        if (borderBitmap != null) {
            val bdrMatrix = Matrix().apply {
                postScale(
                    TARGET_ICON_PX.toFloat() / borderBitmap.width.coerceAtLeast(1),
                    TARGET_ICON_PX.toFloat() / borderBitmap.height.coerceAtLeast(1)
                )
            }
            outCanvas.drawBitmap(borderBitmap, bdrMatrix, smoothPaint)
        }

        if (shortcutBitmap != null) {
            val badgeSize = (TARGET_ICON_PX * 0.28f).toInt()
            val badgeLeft = TARGET_ICON_PX - badgeSize - 4f
            val badgeTop = TARGET_ICON_PX - badgeSize - 4f
            val badgeDst = RectF(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize)
            val badgeSrc = Rect(0, 0, shortcutBitmap.width, shortcutBitmap.height)
            outCanvas.drawBitmap(shortcutBitmap, badgeSrc, badgeDst, smoothPaint)
        }

        return output
    }

    /**
     * Generates a complete step-by-step visual and XML breakdown of how the Xiaomi Icon Theme API
     * transforms a specific app's icon for the API Pipeline Inspector tab.
     */
    fun buildPipelineBreakdown(
        appLabel: String,
        packageName: String,
        activityName: String,
        rawAppBitmap: Bitmap,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig,
        customOverrideAssetKey: String?,
        calendar: Calendar = Calendar.getInstance()
    ): IconPipelineBreakdown {
        val normalizedRaw = scaleToTarget(rawAppBitmap)
        val effectiveScale = if (config.useCustomScaleOverride) {
            config.customScaleValue.coerceIn(0.45f, 1.0f)
        } else {
            theme.iconScale.coerceIn(0.45f, 1.0f)
        }

        val maskBitmap = theme.iconMaskPath?.let { decodeBitmapCached(it)?.let(::scaleToTarget) }
        val safeBorderPath = theme.iconBorderPath?.takeIf { it != theme.iconPatternPath }
        val borderBitmap = if (config.enableIconBorderOverlay) {
            safeBorderPath?.let { decodeBitmapCached(it)?.let(::scaleToTarget) }
        } else {
            null
        }
        val patternBitmap = if (config.enableIconPatternOverlay) {
            theme.iconPatternPath?.let { decodeBitmapCached(it) }?.takeIf { it.width > 4 && it.height > 4 }?.let(::scaleToTarget)
        } else {
            null
        }
        val bgBitmap = renderThemedBackgroundPlate(
            packageName = packageName,
            theme = theme,
            enableMultiBgHash = config.enableMultiBgHashSelection,
            maskBitmap = maskBitmap,
            patternBitmap = patternBitmap
        )
        val shortcutBitmap = if (config.enableShortcutBadge) {
            theme.iconShortcutPath?.let { decodeBitmapCached(it)?.let(::scaleToTarget) }
        } else {
            null
        }

        val shouldRunTransformFilter = config.enableThirdPartyColorFilter &&
            theme.hasTransformConfig && theme.filterSteps.isNotEmpty()
        val colorFilteredRaw = if (shouldRunTransformFilter) {
            scaleToTarget(applyThemeIconTransformFilter(normalizedRaw, theme, config))
        } else {
            normalizedRaw
        }

        val scaledInputBitmap = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val scaledCanvas = Canvas(scaledInputBitmap)
        val matrix = buildPointsMappingMatrix(
            srcW = colorFilteredRaw.width.coerceAtLeast(1),
            srcH = colorFilteredRaw.height.coerceAtLeast(1),
            dstSize = TARGET_ICON_PX,
            effectiveScaleX = effectiveScale,
            effectiveScaleY = if (config.useCustomScaleOverride) effectiveScale else theme.pointsScaleY.coerceIn(0.45f, 1.0f),
            theme = theme,
            config = config
        )
        val smoothPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        scaledCanvas.drawBitmap(colorFilteredRaw, matrix, smoothPaint)

        val maskedLayerBitmap = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskedLayerBitmap)
        maskCanvas.drawBitmap(scaledInputBitmap, 0f, 0f, smoothPaint)
        if (maskBitmap != null) {
            applyAlphaMaskInPlace(maskedLayerBitmap, maskBitmap)
        }

        val dynamicComposite = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(dynamicComposite)
        finalCanvas.drawBitmap(bgBitmap, 0f, 0f, smoothPaint)
        if (patternBitmap != null && theme.iconBgPaths.isNotEmpty()) {
            finalCanvas.drawBitmap(patternBitmap, 0f, 0f, smoothPaint)
        }
        finalCanvas.drawBitmap(maskedLayerBitmap, 0f, 0f, smoothPaint)
        if (borderBitmap != null) {
            finalCanvas.drawBitmap(borderBitmap, 0f, 0f, smoothPaint)
        }
        if (shortcutBitmap != null) {
            val bSize = (TARGET_ICON_PX * 0.28f).toInt()
            val bLeft = TARGET_ICON_PX - bSize - 4f
            val bTop = TARGET_ICON_PX - bSize - 4f
            finalCanvas.drawBitmap(
                shortcutBitmap,
                Rect(0, 0, shortcutBitmap.width, shortcutBitmap.height),
                RectF(bLeft, bTop, bLeft + bSize, bTop + bSize),
                smoothPaint
            )
        }

        val ruleMatch = findRuleIconPath(packageName, activityName, theme)
        val directMatch = findDirectIconPath(packageName, activityName, theme)
        val fancySpec = if (config.enableFancyMamlIcons) findFancyIconSpec(packageName, activityName, theme) else null

        val (finalBitmap, source, matchedEntry) = when {
            !customOverrideAssetKey.isNullOrBlank() && theme.directIconsMap.containsKey(customOverrideAssetKey) -> {
                val bmp = decodeBitmapCached(theme.directIconsMap.getValue(customOverrideAssetKey))?.let(::scaleToTarget) ?: dynamicComposite
                Triple(maybeApplyGlobalTransform(bmp, theme, config), IconResolutionSource.MANUAL_OVERRIDE, "override -> $customOverrideAssetKey.png")
            }
            fancySpec != null -> {
                val bmp = renderMamlFancyIcon(fancySpec, calendar) ?: dynamicComposite
                Triple(maybeApplyGlobalTransform(bmp, theme, config), IconResolutionSource.MTZ_FANCY_MAML, "fancy_icons/${fancySpec.targetPackage}/manifest.xml")
            }
            ruleMatch != null -> {
                val bmp = decodeBitmapCached(ruleMatch.second)?.let(::scaleToTarget) ?: dynamicComposite
                Triple(maybeApplyGlobalTransform(bmp, theme, config), IconResolutionSource.MTZ_RULE_MAP, "icon_rule.xml (${ruleMatch.first})")
            }
            directMatch != null -> {
                val bmp = decodeBitmapCached(directMatch.second)?.let(::scaleToTarget) ?: dynamicComposite
                Triple(maybeApplyGlobalTransform(bmp, theme, config), IconResolutionSource.MTZ_DIRECT_MAP, "res/drawable-xxhdpi/${directMatch.first}.png")
            }
            config.enableDynamicMasking -> {
                val filterDesc = if (shouldRunTransformFilter) "IconTransform(${theme.filterBlackColorHex}) + " else ""
                Triple(dynamicComposite, IconResolutionSource.MTZ_DYNAMIC_MASK, "${filterDesc}icon_mask.png + icon_border.png")
            }
            else -> {
                val fallbackBmp = theme.symDefAppIconPath?.let { decodeBitmapCached(it)?.let(::scaleToTarget) }
                Triple(fallbackBmp ?: normalizedRaw, IconResolutionSource.SYSTEM_RAW, if (fallbackBmp != null) "sym_def_app_icon.png" else "PackageManager.getActivityIcon()")
            }
        }

        val mamlVars = mapOf(
            "#hour12" to (calendar.get(Calendar.HOUR) % 12).toString(),
            "#hour24" to calendar.get(Calendar.HOUR_OF_DAY).toString(),
            "#minute" to calendar.get(Calendar.MINUTE).toString(),
            "#second" to calendar.get(Calendar.SECOND).toString(),
            "#date" to calendar.get(Calendar.DAY_OF_MONTH).toString(),
            "#month" to (calendar.get(Calendar.MONTH) + 1).toString(),
            "#month_short" to SimpleDateFormat("MMM", Locale.US).format(calendar.time).uppercase(Locale.US),
            "#dow" to SimpleDateFormat("EEE", Locale.US).format(calendar.time).uppercase(Locale.US),
            "#year" to calendar.get(Calendar.YEAR).toString(),
            "#battery_level" to "85%",
            "#battery_state" to "Normal / Discharging"
        )

        val xmlSnippet = buildDiagnosticXmlSnippet(
            packageName = packageName,
            source = source,
            theme = theme,
            effectiveScale = effectiveScale,
            enableColorFilter = config.enableThirdPartyColorFilter,
            matchedEntry = matchedEntry,
            fancySpec = fancySpec
        )

        return IconPipelineBreakdown(
            appLabel = appLabel,
            packageName = packageName,
            activityName = activityName,
            resolutionSource = source,
            appliedScale = effectiveScale,
            matchedEntryPath = matchedEntry,
            rawAppBitmap = normalizedRaw,
            backgroundPlateBitmap = bgBitmap,
            scaledInputBitmap = scaledInputBitmap,
            maskTemplateBitmap = maskBitmap,
            maskedLayerBitmap = maskedLayerBitmap,
            borderOverlayBitmap = borderBitmap,
            patternOverlayBitmap = patternBitmap,
            shortcutBadgeBitmap = shortcutBitmap,
            finalOutputBitmap = finalBitmap,
            xmlDiagnosticSnippet = xmlSnippet,
            mamlVariables = mamlVars
        )
    }

    /**
     * Authentic MIUI `<IconTransform>` filter (`transform_config.xml`):
     * 1. Applies optional `ColorMatrix`, `Lighting`, and `HSL` filters when specified in `.mtz`.
     * 2. Traverses exterior transparent margins via BFS to locate the outer icon boundary and strips
     *    outer adaptive background plates (white plates, solid colored/dark plates, and smooth gradient plates
     *    that enclose an inner foreground symbol).
     * 3. Shades the remaining foreground glyph into the MTZ theme's `BlackColor`..`WhiteColor` ramp
     *    using `<Filter name="Edges">`, `<Filter name="GrayScale">`, and `<Filter name="Levels">`,
     *    and normalizes its bounding box.
     */
    private fun maybeApplyGlobalTransform(
        bitmap: Bitmap,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig
    ): Bitmap {
        if (!theme.transformApplyToAllIcons ||
            !config.enableThirdPartyColorFilter ||
            !theme.hasTransformConfig ||
            theme.filterSteps.isEmpty()
        ) {
            return bitmap
        }
        return scaleToTarget(executeSequentialFilterPipeline(bitmap, theme, config))
    }

    fun applyThemeIconTransformFilter(
        source: Bitmap,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig = LauncherEngineConfig()
    ): Bitmap {
        if (theme.isCustomFilterPipeline && theme.filterSteps.isNotEmpty()) {
            return executeSequentialFilterPipeline(source, theme, config)
        }
        return try {
            var prepSource = ensureSoftwareBitmap(source)
            // Execute pre-shading steps from theme.filterSteps in exact XML order (or fallback fields)
            if (theme.filterSteps.isNotEmpty()) {
                var edgesEncountered = false
                for (step in theme.filterSteps) {
                    when (step) {
                        is MtzIconFilterStep.Edges -> edgesEncountered = true
                        is MtzIconFilterStep.ColorMatrixStep -> {
                            if (config.enableColorMatrixFilter) {
                                prepSource = applyColorMatrixFilter(prepSource, step.matrix)
                            }
                        }
                        is MtzIconFilterStep.LightingStep -> {
                            if (config.enableLightingFilter && (step.mulHex != null || step.addHex != null)) {
                                prepSource = applyLightingFilter(prepSource, step.mulHex, step.addHex)
                            }
                        }
                        is MtzIconFilterStep.Hsl -> {
                            if (config.enableHslFilter && (step.hueShift != 0f || step.saturationModify != 0f || step.lightnessModify != 0f)) {
                                prepSource = applyHslFilter(prepSource, step.hueShift, step.saturationModify, step.lightnessModify)
                            }
                        }
                        is MtzIconFilterStep.GausBlur -> {
                            if (!edgesEncountered && step.radius > 0) {
                                prepSource = applyGausBlurBitmap(prepSource, step.radius)
                            }
                        }
                        is MtzIconFilterStep.Spread -> {
                            if (!edgesEncountered && step.radius != 0) {
                                prepSource = applySpreadBitmap(prepSource, step.radius)
                            }
                        }
                        else -> Unit
                    }
                }
            } else {
                if (config.enableColorMatrixFilter && theme.filterColorMatrix != null) {
                    prepSource = applyColorMatrixFilter(prepSource, theme.filterColorMatrix)
                }
                if (config.enableLightingFilter && (theme.filterLightingMulHex != null || theme.filterLightingAddHex != null)) {
                    prepSource = applyLightingFilter(prepSource, theme.filterLightingMulHex, theme.filterLightingAddHex)
                }
                if (config.enableHslFilter && (theme.filterHslHue != null || theme.filterHslSaturation != null || theme.filterHslLightness != null)) {
                    prepSource = applyHslFilter(prepSource, theme.filterHslHue, theme.filterHslSaturation, theme.filterHslLightness)
                }
            }

            val workBmp = if (prepSource.width != SHADER_WORK_PX || prepSource.height != SHADER_WORK_PX) {
                Bitmap.createScaledBitmap(prepSource, SHADER_WORK_PX, SHADER_WORK_PX, true)
                    .let(::ensureSoftwareBitmap)
            } else {
                prepSource
            }

            val w = workBmp.width
            val h = workBmp.height
            val total = w * h
            val pixels = IntArray(total)
            workBmp.getPixels(pixels, 0, w, 0, 0, w, h)

            val isWhiteLike = BooleanArray(total)
            val isRedShade = BooleanArray(total)
            var totalOpaque = 0

            for (i in 0 until total) {
                val c = pixels[i]
                val a = c ushr 24
                if (a < 20) continue
                totalOpaque++
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val delta = maxC - minC

                if (maxC >= 195 && delta <= 42) {
                    isWhiteLike[i] = true
                } else if (r >= 115 && delta >= 32 && r == maxC && (r - g) >= 35 && (r - b) >= 28 && g <= (r * 3) / 4) {
                    isRedShade[i] = true
                }
            }

            if (totalOpaque == 0) return workBmp

            // Step 1: Flood-fill exterior transparent margin (alpha < 20) from all 4 edges
            val isExteriorTransparent = BooleanArray(total)
            val queue = IntArray(total)
            var head = 0
            var tail = 0

            fun enqueueTransparentEdge(idx: Int) {
                if (!isExteriorTransparent[idx] && (pixels[idx] ushr 24) < 20) {
                    isExteriorTransparent[idx] = true
                    queue[tail++] = idx
                }
            }

            for (x in 0 until w) {
                enqueueTransparentEdge(x)
                enqueueTransparentEdge((h - 1) * w + x)
            }
            for (y in 0 until h) {
                enqueueTransparentEdge(y * w)
                enqueueTransparentEdge(y * w + (w - 1))
            }

            while (head < tail) {
                val curr = queue[head++]
                val cx = curr % w
                val cy = curr / w
                if (cx > 0) enqueueTransparentEdge(curr - 1)
                if (cx < w - 1) enqueueTransparentEdge(curr + 1)
                if (cy > 0) enqueueTransparentEdge(curr - w)
                if (cy < h - 1) enqueueTransparentEdge(curr + w)
            }

            // Step 2: Identify opaque pixels on the outer perimeter of the icon shape
            val isShapeBoundary = BooleanArray(total)
            val boundaryIndices = IntArray(total)
            var boundaryCount = 0
            var boundaryWhiteCount = 0

            for (i in 0 until total) {
                if ((pixels[i] ushr 24) < 20) continue
                val cx = i % w
                val cy = i / w
                val touchesEdge = cx == 0 || cx == w - 1 || cy == 0 || cy == h - 1 ||
                    isExteriorTransparent[i - 1] ||
                    isExteriorTransparent[i + 1] ||
                    isExteriorTransparent[i - w] ||
                    isExteriorTransparent[i + w]
                if (touchesEdge) {
                    isShapeBoundary[i] = true
                    boundaryIndices[boundaryCount++] = i
                    if (isWhiteLike[i]) boundaryWhiteCount++
                }
            }

            // Also identify the 1px-inward ring next to isShapeBoundary for un-antialiased color sampling
            val innerRingIndices = IntArray(total)
            var innerRingCount = 0
            var innerRingWhiteCount = 0
            for (i in 0 until total) {
                if ((pixels[i] ushr 24) < 150 || isShapeBoundary[i]) continue
                val cx = i % w
                val cy = i / w
                val touchesBoundary = (cx > 0 && isShapeBoundary[i - 1]) ||
                    (cx < w - 1 && isShapeBoundary[i + 1]) ||
                    (cy > 0 && isShapeBoundary[i - w]) ||
                    (cy < h - 1 && isShapeBoundary[i + w])
                if (touchesBoundary) {
                    innerRingIndices[innerRingCount++] = i
                    if (isWhiteLike[i]) innerRingWhiteCount++
                }
            }

            // Step 3: Flood-fill outer adaptive background plate (white plate, solid colored/dark plate, or smooth gradient plate)
            val isOuterPlate = BooleanArray(total)
            var outerPlatePixels = 0
            var plateIsWhite = false
            var plateRefR = 255
            var plateRefG = 255
            var plateRefB = 255

            val whitePerimeterMatch = (boundaryCount > 0 && boundaryWhiteCount * 100 >= boundaryCount * 35) ||
                (innerRingCount > 0 && innerRingWhiteCount * 100 >= innerRingCount * 40)

            if (whitePerimeterMatch) {
                // Case A: White adaptive background plate (e.g. Maps, Photos, Drive, Gmail, YouTube)
                plateIsWhite = true
                head = 0
                tail = 0
                for (bIdx in 0 until boundaryCount) {
                    val idx = boundaryIndices[bIdx]
                    if (isWhiteLike[idx] && !isOuterPlate[idx]) {
                        isOuterPlate[idx] = true
                        queue[tail++] = idx
                    }
                }
                for (rIdx in 0 until innerRingCount) {
                    val idx = innerRingIndices[rIdx]
                    if (isWhiteLike[idx] && !isOuterPlate[idx]) {
                        isOuterPlate[idx] = true
                        queue[tail++] = idx
                    }
                }
                while (head < tail) {
                    val curr = queue[head++]
                    outerPlatePixels++
                    val cx = curr % w
                    val cy = curr / w
                    if (cx > 0 && !isOuterPlate[curr - 1] && isWhiteLike[curr - 1]) {
                        isOuterPlate[curr - 1] = true
                        queue[tail++] = curr - 1
                    }
                    if (cx < w - 1 && !isOuterPlate[curr + 1] && isWhiteLike[curr + 1]) {
                        isOuterPlate[curr + 1] = true
                        queue[tail++] = curr + 1
                    }
                    if (cy > 0 && !isOuterPlate[curr - w] && isWhiteLike[curr - w]) {
                        isOuterPlate[curr - w] = true
                        queue[tail++] = curr - w
                    }
                    if (cy < h - 1 && !isOuterPlate[curr + w] && isWhiteLike[curr + w]) {
                        isOuterPlate[curr + w] = true
                        queue[tail++] = curr + w
                    }
                }
                if (outerPlatePixels > totalOpaque / 8) {
                    for (bIdx in 0 until boundaryCount) {
                        val idx = boundaryIndices[bIdx]
                        if (!isOuterPlate[idx]) {
                            isOuterPlate[idx] = true
                            outerPlatePixels++
                        }
                    }
                }
            } else if (boundaryCount > 0) {
                // Case B: Colored, dark, or smooth-gradient outer plate enclosing an inner symbol
                val sampleArray = if (innerRingCount >= 12) innerRingIndices else boundaryIndices
                val sampleLen = if (innerRingCount >= 12) innerRingCount else boundaryCount
                var sumR = 0
                var sumG = 0
                var sumB = 0
                for (sIdx in 0 until sampleLen) {
                    val c = pixels[sampleArray[sIdx]]
                    sumR += (c shr 16) and 0xFF
                    sumG += (c shr 8) and 0xFF
                    sumB += c and 0xFF
                }
                val avgR = sumR / sampleLen.coerceAtLeast(1)
                val avgG = sumG / sampleLen.coerceAtLeast(1)
                val avgB = sumB / sampleLen.coerceAtLeast(1)

                fun maxChannelDiff(c1: Int, c2: Int): Int {
                    val dr = abs(((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF))
                    val dg = abs(((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF))
                    val db = abs((c1 and 0xFF) - (c2 and 0xFF))
                    return max(dr, max(dg, db))
                }

                fun matchesPlateSeed(pxColor: Int): Boolean {
                    if ((pxColor ushr 24) < 20) return false
                    val r = (pxColor shr 16) and 0xFF
                    val g = (pxColor shr 8) and 0xFF
                    val b = pxColor and 0xFF
                    return abs(r - avgR) <= 46 && abs(g - avgG) <= 46 && abs(b - avgB) <= 46
                }

                fun canStepPlate(fromColor: Int, toColor: Int): Boolean {
                    if ((toColor ushr 24) < 20) return false
                    if (matchesPlateSeed(toColor)) return true
                    val stepDiff = maxChannelDiff(fromColor, toColor)
                    if (stepDiff > 10) return false
                    val r = (toColor shr 16) and 0xFF
                    val g = (toColor shr 8) and 0xFF
                    val b = toColor and 0xFF
                    return abs(r - avgR) <= 68 && abs(g - avgG) <= 68 && abs(b - avgB) <= 68
                }

                var matchingPerimeter = 0
                for (sIdx in 0 until sampleLen) {
                    if (matchesPlateSeed(pixels[sampleArray[sIdx]])) matchingPerimeter++
                }

                if (matchingPerimeter * 100 >= sampleLen * 48) {
                    val candidatePlate = BooleanArray(total)
                    var candidateCount = 0
                    head = 0
                    tail = 0
                    for (bIdx in 0 until boundaryCount) {
                        val idx = boundaryIndices[bIdx]
                        if (matchesPlateSeed(pixels[idx]) && !candidatePlate[idx]) {
                            candidatePlate[idx] = true
                            queue[tail++] = idx
                        }
                    }
                    for (rIdx in 0 until innerRingCount) {
                        val idx = innerRingIndices[rIdx]
                        if (matchesPlateSeed(pixels[idx]) && !candidatePlate[idx]) {
                            candidatePlate[idx] = true
                            queue[tail++] = idx
                        }
                    }
                    while (head < tail) {
                        val curr = queue[head++]
                        candidateCount++
                        val currColor = pixels[curr]
                        val cx = curr % w
                        val cy = curr / w
                        if (cx > 0 && !candidatePlate[curr - 1] && canStepPlate(currColor, pixels[curr - 1])) {
                            candidatePlate[curr - 1] = true
                            queue[tail++] = curr - 1
                        }
                        if (cx < w - 1 && !candidatePlate[curr + 1] && canStepPlate(currColor, pixels[curr + 1])) {
                            candidatePlate[curr + 1] = true
                            queue[tail++] = curr + 1
                        }
                        if (cy > 0 && !candidatePlate[curr - w] && canStepPlate(currColor, pixels[curr - w])) {
                            candidatePlate[curr - w] = true
                            queue[tail++] = curr - w
                        }
                        if (cy < h - 1 && !candidatePlate[curr + w] && canStepPlate(currColor, pixels[curr + w])) {
                            candidatePlate[curr + w] = true
                            queue[tail++] = curr + w
                        }
                    }
                    val remainingInner = totalOpaque - candidateCount
                    // Strip the colored/dark plate only if there is a distinct inner foreground symbol (2.5%..82% of icon)
                    if (candidateCount >= totalOpaque / 6 && remainingInner in (totalOpaque / 40)..(totalOpaque * 82 / 100)) {
                        System.arraycopy(candidatePlate, 0, isOuterPlate, 0, total)
                        for (bIdx in 0 until boundaryCount) {
                            val idx = boundaryIndices[bIdx]
                            if (!isOuterPlate[idx]) {
                                isOuterPlate[idx] = true
                                candidateCount++
                            }
                        }
                        outerPlatePixels = candidateCount
                        plateRefR = avgR
                        plateRefG = avgG
                        plateRefB = avgB
                    }
                }
            }

            val hadOuterAdaptivePlate = outerPlatePixels > (totalOpaque / 8)
            var innerWhitePixels = 0
            var redCount = 0
            for (i in 0 until total) {
                val a = pixels[i] ushr 24
                if (a >= 20 && !isOuterPlate[i]) {
                    if (isWhiteLike[i]) innerWhitePixels++
                    if (isRedShade[i]) redCount++
                }
            }
            val hasLargeRedCore = redCount > (totalOpaque / 14)
            val hasInnerWhiteSymbol = innerWhitePixels > (totalOpaque / 120)
            val hasEdgesFilter = theme.rawTransformConfigXml.contains("Edges", ignoreCase = true)

            val isWhiteTransparentOrDark = theme.filterWhiteColorHex.equals("#00000000", ignoreCase = true) ||
                theme.filterWhiteColorHex.equals("#000000", ignoreCase = true)

            val targetGlyphColorInt: Int
            val basePlateDimInt: Int

            if (isWhiteTransparentOrDark) {
                // Inverted / Cutout GrayScale pipeline (e.g. Valorant where BlackColor is #FFFFFF and WhiteColor is #00000000)
                targetGlyphColorInt = parseColorSafe(theme.filterBlackColorHex, Color.WHITE)
                basePlateDimInt = Color.TRANSPARENT
            } else {
                // Standard GrayScale ramp (e.g. Trickster, Midnight Blue where WhiteColor is the bright foreground glyph)
                targetGlyphColorInt = if (theme.filterWhiteColorHex.equals("#FFFFFF", ignoreCase = true)) {
                    Color.parseColor("#ECF0F1")
                } else {
                    parseColorSafe(theme.filterWhiteColorHex, Color.parseColor("#ECF0F1"))
                }
                basePlateDimInt = parseColorSafe(theme.filterBlackColorHex, Color.parseColor("#465967"))
            }

            val gR = (targetGlyphColorInt shr 16) and 0xFF
            val gG = (targetGlyphColorInt shr 8) and 0xFF
            val gB = targetGlyphColorInt and 0xFF

            val bR = if (basePlateDimInt == Color.TRANSPARENT) gR else ((basePlateDimInt shr 16) and 0xFF)
            val bG = if (basePlateDimInt == Color.TRANSPARENT) gG else ((basePlateDimInt shr 8) and 0xFF)
            val bB = if (basePlateDimInt == Color.TRANSPARENT) gB else (basePlateDimInt and 0xFF)

            fun blendTowardWhite(t: Float, alpha: Int): Int {
                val clamped = t.coerceIn(0f, 1f)
                val outAlpha = if (isWhiteTransparentOrDark) {
                    ((alpha / 255f) * clamped * 255).toInt().coerceIn(0, 255)
                } else {
                    alpha
                }
                val r = (bR + ((gR - bR) * clamped).toInt()).coerceIn(0, 255)
                val g = (bG + ((gG - bG) * clamped).toInt()).coerceIn(0, 255)
                val b = (bB + ((gB - bB) * clamped).toInt()).coerceIn(0, 255)
                return (outAlpha shl 24) or (r shl 16) or (g shl 8) or b
            }

            val inputMin = theme.filterLevelsInputMin.coerceIn(0, 235)
            val inputMax = theme.filterLevelsInputMax.coerceIn(inputMin + 10, 255)
            val outMinNorm = (theme.filterLevelsOutputMin.coerceIn(0, 255)) / 255f
            val outMaxNorm = (theme.filterLevelsOutputMax.coerceIn(0, 255)) / 255f
            val gamma = theme.filterLevelsGamma.coerceIn(0.2f, 4.0f)

            val lumaArr = IntArray(total)
            for (i in 0 until total) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                lumaArr[i] = (r * 77 + g * 150 + b * 29) shr 8
            }

            val outPixels = IntArray(total)
            var minFgX = w
            var minFgY = h
            var maxFgX = -1
            var maxFgY = -1

            // Full-bleed GrayScale + Levels theme (e.g. B22 N Theme: PointsMapping 0..90 -> 0..90, no Edges filter)
            val isFullBleedGrayScaleTheme = !hasEdgesFilter && !isWhiteTransparentOrDark && theme.pointsScaleX >= 0.90f
            if (isFullBleedGrayScaleTheme) {
                // Compute perimeter average luminance to seamlessly fill any exterior transparent corners
                val perimIndices = if (innerRingCount >= 12) innerRingIndices else boundaryIndices
                val perimCount = if (innerRingCount >= 12) innerRingCount else boundaryCount
                var perimLumaSum = 0
                for (pIdx in 0 until perimCount) {
                    perimLumaSum += lumaArr[perimIndices[pIdx]]
                }
                val fallbackEdgeLuma = if (perimCount > 0) perimLumaSum / perimCount else 220
                val effMin = inputMin.coerceIn(0, 235)
                val effMax = inputMax.coerceIn(effMin + 10, 255)

                for (i in 0 until total) {
                    val c = pixels[i]
                    val a = c ushr 24
                    val rawLuma = if (a < 250 && isExteriorTransparent[i]) {
                        fallbackEdgeLuma
                    } else if (a < 250) {
                        // Blend semi-transparent edge pixel against perimeter plate luminance
                        val aF = a / 255f
                        (lumaArr[i] * aF + fallbackEdgeLuma * (1f - aF)).toInt()
                    } else {
                        lumaArr[i]
                    }
                    val norm = ((rawLuma - effMin).toFloat() / (effMax - effMin).coerceAtLeast(1)).coerceIn(0f, 1f)
                    val gammaAdj = if (gamma != 1.0f) norm.toDouble().pow(1.0 / gamma.toDouble()).toFloat() else norm
                    val t = (outMinNorm + (outMaxNorm - outMinNorm) * gammaAdj).coerceIn(0f, 1f)
                    outPixels[i] = blendTowardWhite(t, 255)
                }
                var finalFullBleedPixels = outPixels
                for (step in theme.filterSteps) {
                    when (step) {
                        is MtzIconFilterStep.Spread -> {
                            if (step.radius != 0) {
                                finalFullBleedPixels = applySpreadPixels(finalFullBleedPixels, w, h, step.radius)
                            }
                        }
                        is MtzIconFilterStep.GausBlur -> {
                            if (step.radius > 0) {
                                finalFullBleedPixels = applyGausBlurPixels(finalFullBleedPixels, w, h, step.radius)
                            }
                        }
                        is MtzIconFilterStep.AlphaStep -> {
                            if (step.alpha in 0..254) {
                                finalFullBleedPixels = applyAlphaPixels(finalFullBleedPixels, step.alpha)
                            }
                        }
                        else -> Unit
                    }
                }
                val fullBleedBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                fullBleedBmp.setPixels(finalFullBleedPixels, 0, w, 0, 0, w, h)
                return fullBleedBmp
            }

            // Precompute foreground mask for <Filter name="Edges" /> + <Filter name="Spread" Radius="1" /> cutout themes (e.g. Valorant)
            val isCutoutEdgesTheme = isWhiteTransparentOrDark && hasEdgesFilter
            val hasPostEdgesSpread = theme.filterSpreadRadius != 0 || theme.filterSteps.any { it is MtzIconFilterStep.Spread && it.radius != 0 }
            val isFgCandidate = if (isCutoutEdgesTheme) {
                BooleanArray(total) { idx ->
                    val a = pixels[idx] ushr 24
                    if (a < 20 || isOuterPlate[idx]) {
                        false
                    } else if (hadOuterAdaptivePlate && !plateIsWhite) {
                        val r = (pixels[idx] shr 16) and 0xFF
                        val g = (pixels[idx] shr 8) and 0xFF
                        val b = pixels[idx] and 0xFF
                        !(abs(r - plateRefR) <= 32 && abs(g - plateRefG) <= 32 && abs(b - plateRefB) <= 32)
                    } else if (hadOuterAdaptivePlate && plateIsWhite) {
                        if (hasLargeRedCore && hasInnerWhiteSymbol && isRedShade[idx]) {
                            false
                        } else if (isWhiteLike[idx] && !hasLargeRedCore) {
                            false
                        } else {
                            true
                        }
                    } else {
                        !isShapeBoundary[idx]
                    }
                }
            } else {
                null
            }

            val contourRadius = (w / 48).coerceAtLeast(2)
            val contourRadiusSq = contourRadius * contourRadius

            for (i in 0 until total) {
                val c = pixels[i]
                val a = c ushr 24
                if (a < 20 || isOuterPlate[i]) {
                    outPixels[i] = 0
                    continue
                }

                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val delta = maxC - minC
                val luma = lumaArr[i]
                val px = i % w
                val py = i / w

                if (isCutoutEdgesTheme && isFgCandidate != null) {
                    if (!isFgCandidate[i]) {
                        outPixels[i] = 0
                        continue
                    }
                    var isContourOutline = false
                    if (hadOuterAdaptivePlate) {
                        for (dy in -contourRadius..contourRadius) {
                            for (dx in -contourRadius..contourRadius) {
                                if (dx * dx + dy * dy > contourRadiusSq) continue
                                val nx = px + dx
                                val ny = py + dy
                                if (nx !in 0 until w || ny !in 0 until h || !isFgCandidate[ny * w + nx]) {
                                    isContourOutline = true
                                    break
                                }
                            }
                            if (isContourOutline) break
                        }
                    }
                    // Also check Sobel internal edge (e.g. Play Store inner lines, Chrome rings, Camera lens)
                    var edgeMag = 0
                    if (px in 1 until (w - 1) && py in 1 until (h - 1)) {
                        val tl = lumaArr[(py - 1) * w + (px - 1)]
                        val tc = lumaArr[(py - 1) * w + px]
                        val tr = lumaArr[(py - 1) * w + (px + 1)]
                        val ml = lumaArr[py * w + (px - 1)]
                        val mr = lumaArr[py * w + (px + 1)]
                        val bl = lumaArr[(py + 1) * w + (px - 1)]
                        val bc = lumaArr[(py + 1) * w + px]
                        val br = lumaArr[(py + 1) * w + (px + 1)]
                        val gx = (-tl - (ml shl 1) - bl) + (tr + (mr shl 1) + br)
                        val gy = (-tl - (tc shl 1) - tr) + (bl + (bc shl 1) + br)
                        edgeMag = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt()
                    }
                    if (isContourOutline || edgeMag >= 32) {
                        val t = if (isContourOutline) 1.0f else ((edgeMag - 32).toFloat() / 120f).coerceIn(0.78f, 1.0f)
                        outPixels[i] = blendTowardWhite(t, a)
                    } else {
                        outPixels[i] = 0
                        continue
                    }
                } else if (hadOuterAdaptivePlate) {
                    if (!plateIsWhite) {
                        // Outer plate was colored/dark (Case B): cut out interior holes matching the plate color
                        if (abs(r - plateRefR) <= 32 && abs(g - plateRefG) <= 32 && abs(b - plateRefB) <= 32) {
                            outPixels[i] = 0
                            continue
                        }
                        // Render the inner symbol brightly in the theme's WhiteColor (#2B9D6A in Trickster, #ECF0F1 in Midnight Blue)
                        val t = if (isWhiteLike[i] || luma >= 190) {
                            1.0f
                        } else {
                            (0.80f + 0.20f * (luma / 255f)).coerceIn(0.80f, 1.0f)
                        }
                        outPixels[i] = blendTowardWhite(t, a)
                    } else {
                        // Outer plate was white (Case A)
                        if (hasLargeRedCore && hasInnerWhiteSymbol && isRedShade[i]) {
                            // Red badge core with white inner symbol -> crush red core to transparent so plate shows through
                            outPixels[i] = 0
                            continue
                        } else if (isWhiteLike[i]) {
                            if (!hasLargeRedCore) {
                                // Inner white cutout inside a colored/dark glyph on a white plate
                                outPixels[i] = 0
                                continue
                            } else {
                                outPixels[i] = blendTowardWhite(1.0f, a)
                            }
                        } else if (isRedShade[i] || (r >= g && r >= b && delta >= 28)) {
                            outPixels[i] = blendTowardWhite(1.0f, a)
                        } else if (g >= r && g >= b && delta >= 24) {
                            outPixels[i] = blendTowardWhite(0.90f, a)
                        } else if (b >= r && b >= g && delta >= 24) {
                            outPixels[i] = blendTowardWhite(0.82f, a)
                        } else {
                            // Dark or monochrome glyph on a white plate -> invert onto WhiteColor so it stands out on BlackColor plate
                            outPixels[i] = blendTowardWhite(0.94f, a)
                        }
                    }
                } else {
                    // Full-bleed / complex icon where no uniform outer plate was stripped
                    if (isShapeBoundary[i]) {
                        outPixels[i] = 0
                        continue
                    }
                    if (hasEdgesFilter && px in 1 until (w - 1) && py in 1 until (h - 1)) {
                        // MIUI <Filter name="Edges" /> Sobel operator
                        val tl = lumaArr[(py - 1) * w + (px - 1)]
                        val tc = lumaArr[(py - 1) * w + px]
                        val tr = lumaArr[(py - 1) * w + (px + 1)]
                        val ml = lumaArr[py * w + (px - 1)]
                        val mr = lumaArr[py * w + (px + 1)]
                        val bl = lumaArr[(py + 1) * w + (px - 1)]
                        val bc = lumaArr[(py + 1) * w + px]
                        val br = lumaArr[(py + 1) * w + (px + 1)]
                        val gx = (-tl - (ml shl 1) - bl) + (tr + (mr shl 1) + br)
                        val gy = (-tl - (tc shl 1) - tr) + (bl + (bc shl 1) + br)
                        val edgeMag = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt()
                        if (edgeMag >= 32) {
                            val t = ((edgeMag - 32).toFloat() / 140f).coerceIn(0.68f, 1.0f)
                            outPixels[i] = blendTowardWhite(t, a)
                        } else if (isWhiteLike[i]) {
                            outPixels[i] = blendTowardWhite(0.96f, a)
                        } else {
                            outPixels[i] = 0
                            continue
                        }
                    } else {
                        val effectiveMin = inputMin.coerceAtLeast(80)
                        if (luma <= effectiveMin) {
                            outPixels[i] = 0
                            continue
                        }
                        val t = ((luma - effectiveMin).toFloat() / (inputMax - effectiveMin).coerceAtLeast(1)).coerceIn(0f, 1f)
                        val gammaAdjusted = if (gamma != 1.0f) t.toDouble().pow(1.0 / gamma.toDouble()).toFloat() else t
                        val levelOut = 0.65f + 0.35f * (outMinNorm + (outMaxNorm - outMinNorm) * gammaAdjusted)
                        outPixels[i] = blendTowardWhite(levelOut, a)
                    }
                }

                if (px < minFgX) minFgX = px
                if (py < minFgY) minFgY = py
                if (px > maxFgX) maxFgX = px
                if (py > maxFgY) maxFgY = py
            }

            // Execute post-edge sequential steps from <IconFilters> (Spread, GausBlur, Alpha) in XML order
            var postPixels = outPixels
            if (theme.filterSteps.isNotEmpty()) {
                var afterEdges = !hasEdgesFilter
                for (step in theme.filterSteps) {
                    when (step) {
                        is MtzIconFilterStep.Edges -> afterEdges = true
                        is MtzIconFilterStep.Spread -> {
                            val effectiveSpreadRadius = if (isCutoutEdgesTheme && step.radius in 0..1) 0 else step.radius
                            if (afterEdges && effectiveSpreadRadius != 0) {
                                postPixels = applySpreadPixels(postPixels, w, h, effectiveSpreadRadius)
                            }
                        }
                        is MtzIconFilterStep.GausBlur -> {
                            if (afterEdges && step.radius > 0) {
                                postPixels = applyGausBlurPixels(postPixels, w, h, step.radius)
                            }
                        }
                        is MtzIconFilterStep.AlphaStep -> {
                            if (step.alpha in 0..254) {
                                postPixels = applyAlphaPixels(postPixels, step.alpha)
                            }
                        }
                        else -> Unit
                    }
                }
            } else {
                val effectiveSpreadRadius = if (isCutoutEdgesTheme && theme.filterSpreadRadius in 0..1) 0 else theme.filterSpreadRadius
                if (effectiveSpreadRadius != 0) {
                    postPixels = applySpreadPixels(postPixels, w, h, effectiveSpreadRadius)
                }
                if (theme.filterBlurRadius > 0) {
                    postPixels = applyGausBlurPixels(postPixels, w, h, theme.filterBlurRadius)
                }
            }

            // Recompute foreground bounding box if Spread/Blur expanded pixels
            if (postPixels !== outPixels) {
                minFgX = w
                minFgY = h
                maxFgX = -1
                maxFgY = -1
                for (idx in 0 until total) {
                    if ((postPixels[idx] ushr 24) >= 20) {
                        val px = idx % w
                        val py = idx / w
                        if (px < minFgX) minFgX = px
                        if (py < minFgY) minFgY = py
                        if (px > maxFgX) maxFgX = px
                        if (py > maxFgY) maxFgY = py
                    }
                }
            }

            val rawFilteredBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            rawFilteredBmp.setPixels(postPixels, 0, w, 0, 0, w, h)

            // Normalize foreground glyph bounding box when an outer adaptive plate was stripped
            val fgW = maxFgX - minFgX + 1
            val fgH = maxFgY - minFgY + 1
            val fgSpan = max(fgW, fgH)
            if (hadOuterAdaptivePlate && fgSpan in 8..(w - 4)) {
                val normalizedBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val normCanvas = Canvas(normalizedBmp)
                val targetRatio = (0.48f / theme.pointsScaleX.coerceIn(0.45f, 1.0f)).coerceIn(0.64f, 0.86f)
                val targetSpan = w * targetRatio
                val scale = targetSpan / fgSpan.toFloat()
                val drawW = fgW * scale
                val drawH = fgH * scale
                val dstLeft = (w - drawW) / 2f
                val dstTop = (h - drawH) / 2f
                val srcRect = Rect(minFgX, minFgY, maxFgX + 1, maxFgY + 1)
                val dstRect = RectF(dstLeft, dstTop, dstLeft + drawW, dstTop + drawH)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                normCanvas.drawBitmap(rawFilteredBmp, srcRect, dstRect, paint)
                return normalizedBmp
            }

            rawFilteredBmp
        } catch (_: Exception) {
            ensureSoftwareBitmap(source)
        }
    }

    /**
     * Builds the `<PointsMapping>` transformation matrix.
     * Uses Android's 4-point homography `Matrix.setPolyToPoly` when 4 `<Point>` mappings are present
     * (supporting perspective, trapezoid, skew, and non-uniform warps), and falls back to scale + translate.
     */
    private fun buildPointsMappingMatrix(
        srcW: Int,
        srcH: Int,
        dstSize: Int,
        effectiveScaleX: Float,
        effectiveScaleY: Float,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig
    ): Matrix {
        val matrix = Matrix()
        val srcNorm = theme.pointsMappingSrc
        val dstNorm = theme.pointsMappingDst
        if (!config.useCustomScaleOverride && srcNorm != null && dstNorm != null && srcNorm.size == 8 && dstNorm.size == 8) {
            val srcPts = FloatArray(8) { i ->
                if (i % 2 == 0) srcNorm[i] * srcW else srcNorm[i] * srcH
            }
            val dstPts = FloatArray(8) { i ->
                dstNorm[i] * dstSize
            }
            if (matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
                return matrix
            }
        }
        val offsetX = if (config.useCustomScaleOverride) {
            dstSize * (1f - effectiveScaleX) / 2f
        } else {
            dstSize * theme.pointsOffsetX
        }
        val offsetY = if (config.useCustomScaleOverride) {
            dstSize * (1f - effectiveScaleY) / 2f
        } else {
            dstSize * theme.pointsOffsetY
        }
        val scaleX = (dstSize.toFloat() / srcW.coerceAtLeast(1)) * effectiveScaleX
        val scaleY = (dstSize.toFloat() / srcH.coerceAtLeast(1)) * effectiveScaleY
        matrix.postScale(scaleX, scaleY)
        matrix.postTranslate(offsetX, offsetY)
        return matrix
    }

    /**
     * MIUI `<Filter name="Spread" Radius="N" />`:
     * Morphological dilation (`radius > 0`) or erosion (`radius < 0`) over a circular neighborhood
     * scaled from MIUI's 90x90 reference coordinate grid to `w x h`.
     */
    private fun applySpreadPixels(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val scaledRadius = ((abs(radius) * w) / 90).coerceIn(1, 16)
        val rSq = scaledRadius * scaledRadius
        val out = IntArray(w * h)
        val isDilate = radius > 0

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                var bestPixel = pixels[rowOffset + x]
                var bestMetric = ((bestPixel ushr 24) shl 8) +
                    ((((bestPixel shr 16) and 0xFF) * 77 + ((bestPixel shr 8) and 0xFF) * 150 + (bestPixel and 0xFF) * 29) shr 8)

                val minDy = max(-scaledRadius, -y)
                val maxDy = min(scaledRadius, h - 1 - y)
                val minDx = max(-scaledRadius, -x)
                val maxDx = min(scaledRadius, w - 1 - x)

                for (dy in minDy..maxDy) {
                    val dySq = dy * dy
                    val nRow = (y + dy) * w
                    for (dx in minDx..maxDx) {
                        if (dx * dx + dySq > rSq) continue
                        val cand = pixels[nRow + x + dx]
                        val candA = cand ushr 24
                        val candLuma = (((cand shr 16) and 0xFF) * 77 + ((cand shr 8) and 0xFF) * 150 + (cand and 0xFF) * 29) shr 8
                        val metric = (candA shl 8) + candLuma
                        if (isDilate) {
                            if (metric > bestMetric) {
                                bestMetric = metric
                                bestPixel = cand
                            }
                        } else {
                            if (metric < bestMetric) {
                                bestMetric = metric
                                bestPixel = cand
                            }
                        }
                    }
                }
                out[rowOffset + x] = bestPixel
            }
        }
        return out
    }

    private fun applySpreadBitmap(source: Bitmap, radius: Int): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPixels = applySpreadPixels(pixels, w, h, radius)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * MIUI `<Filter name="GausBlur" Radius="N" />`:
     * Separable 2-pass Gaussian blur across premultiplied ARGB channels,
     * scaled from MIUI's 90x90 reference coordinate grid to `w x h`.
     */
    private fun applyGausBlurPixels(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val r = ((radius * w) / 90).coerceIn(1, 24)
        val sigma = (r / 2f).coerceAtLeast(0.8f)
        val twoSigmaSq = 2f * sigma * sigma
        val kernelSize = r * 2 + 1
        val weights = FloatArray(kernelSize)
        var weightSum = 0f
        for (i in -r..r) {
            val wt = kotlin.math.exp(-(i * i) / twoSigmaSq)
            weights[i + r] = wt
            weightSum += wt
        }
        for (i in 0 until kernelSize) {
            weights[i] /= weightSum
        }

        val temp = IntArray(w * h)
        val out = IntArray(w * h)

        // Horizontal pass (premultiplied RGB to prevent dark halos on transparent borders)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var sumA = 0f
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                for (k in -r..r) {
                    val nx = (x + k).coerceIn(0, w - 1)
                    val c = pixels[row + nx]
                    val a = (c ushr 24) and 0xFF
                    val aNorm = a / 255f
                    val wt = weights[k + r]
                    sumA += a * wt
                    sumR += ((c shr 16) and 0xFF) * aNorm * wt
                    sumG += ((c shr 8) and 0xFF) * aNorm * wt
                    sumB += (c and 0xFF) * aNorm * wt
                }
                val outA = sumA.toInt().coerceIn(0, 255)
                if (outA == 0) {
                    temp[row + x] = 0
                } else {
                    val invA = 255f / sumA.coerceAtLeast(1f)
                    val outR = (sumR * invA).toInt().coerceIn(0, 255)
                    val outG = (sumG * invA).toInt().coerceIn(0, 255)
                    val outB = (sumB * invA).toInt().coerceIn(0, 255)
                    temp[row + x] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
                }
            }
        }

        // Vertical pass
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var sumA = 0f
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                for (k in -r..r) {
                    val ny = (y + k).coerceIn(0, h - 1)
                    val c = temp[ny * w + x]
                    val a = (c ushr 24) and 0xFF
                    val aNorm = a / 255f
                    val wt = weights[k + r]
                    sumA += a * wt
                    sumR += ((c shr 16) and 0xFF) * aNorm * wt
                    sumG += ((c shr 8) and 0xFF) * aNorm * wt
                    sumB += (c and 0xFF) * aNorm * wt
                }
                val outA = sumA.toInt().coerceIn(0, 255)
                if (outA == 0) {
                    out[row + x] = 0
                } else {
                    val invA = 255f / sumA.coerceAtLeast(1f)
                    val outR = (sumR * invA).toInt().coerceIn(0, 255)
                    val outG = (sumG * invA).toInt().coerceIn(0, 255)
                    val outB = (sumB * invA).toInt().coerceIn(0, 255)
                    out[row + x] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
                }
            }
        }
        return out
    }

    private fun applyGausBlurBitmap(source: Bitmap, radius: Int): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val outPixels = applyGausBlurPixels(pixels, w, h, radius)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun applyAlphaPixels(pixels: IntArray, filterAlpha: Int): IntArray {
        val factor = filterAlpha.coerceIn(0, 255)
        return IntArray(pixels.size) { idx ->
            val c = pixels[idx]
            val a = ((c ushr 24) * factor) / 255
            (c and 0x00FFFFFF) or (a shl 24)
        }
    }

    private fun applyHslFilter(
        source: Bitmap,
        hueShift: Float?,
        satDelta: Float?,
        lightDelta: Float?
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val hsv = FloatArray(3)
        for (i in 0 until total) {
            val c = pixels[i]
            val a = c ushr 24
            if (a < 10) continue
            Color.colorToHSV(c, hsv)
            if (hueShift != null) hsv[0] = (hsv[0] + hueShift + 360f) % 360f
            if (satDelta != null) hsv[1] = (hsv[1] + satDelta).coerceIn(0f, 1f)
            if (lightDelta != null) hsv[2] = (hsv[2] + lightDelta).coerceIn(0f, 1f)
            pixels[i] = Color.HSVToColor(a, hsv)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun parseColorSafe(hex: String, fallback: Int): Int {
        val clean = hex.split("|").firstOrNull()?.trim() ?: hex.trim()
        return try {
            Color.parseColor(clean)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (((color shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888 && !bitmap.isRecycled) {
            return bitmap
        }
        return try {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } catch (_: Exception) {
            val fallback = Bitmap.createBitmap(
                bitmap.width.coerceAtLeast(1),
                bitmap.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(fallback)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            fallback
        }
    }

    /**
     * Renders a Xiaomi MAML (`fancy_icons/<pkg>/manifest.xml`) icon at the given `animTimeMs`
     * (default `1200L` for resting state after entrance keyframes finish).
     * Supports:
     * - `<Var>` / `<VariableAnimation>` / `<AniFrame>` timelines with MIUI easing curves
     * - Arithmetic & variable expressions (`#iconAni`, `#iconAni2`, `#time_sys`, `#hour12`, `#minute`, `#second`, `#date`, `#weather_temperature`)
     * - `<Group layered="true" clip="true">` with offscreen layer compositing
     * - `<Image>` with `scale`, `scaleX`, `scaleY`, `rotation`, `alpha`, `pivotX`, `pivotY`, and `xfermode` (`dst_atop`, `dst_in`, `src_in`, etc.)
     * - `<Rectangle>` with `align`, `alignV`, `pivotX`, `pivotY`, `cornerRadius`, `fillColor`, and `rotation` (used for live sweeping Clock hands)
     * - `<DateTime format="EEEE|d|...">` and `<Text textExp="...">`
     */
    fun renderMamlFancyIcon(
        spec: MamlFancyIconSpec,
        calendar: Calendar,
        animTimeMs: Long = 1200L
    ): Bitmap? {
        if (spec.layers.isEmpty()) {
            val fallbackPath = spec.quietImagePath ?: spec.startImagePath
            return fallbackPath?.let { decodeBitmapCached(it)?.let(::scaleToTarget) }
        }

        val output = Bitmap.createBitmap(TARGET_ICON_PX, TARGET_ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val varsMap = buildMamlVariablesMap(spec, calendar, animTimeMs)

        val scaleX = TARGET_ICON_PX.toFloat() / spec.width.coerceAtLeast(1)
        val scaleY = TARGET_ICON_PX.toFloat() / spec.height.coerceAtLeast(1)
        canvas.save()
        canvas.scale(scaleX, scaleY)

        val groupSaveStack = ArrayDeque<Int>()
        var skipGroupDepth = 0
        var drewAnyVisual = false

        for (layer in spec.layers) {
            when (layer.layerType) {
                "GROUP_START" -> {
                    if (skipGroupDepth > 0 || !isMamlLayerVisible(layer.visibilityExpression, varsMap)) {
                        skipGroupDepth++
                        continue
                    }
                    val gw = evaluateMamlNumericExpression(layer.widthExpression, varsMap, spec.width.toFloat())
                    val gh = evaluateMamlNumericExpression(layer.heightExpression, varsMap, spec.height.toFloat())
                    val rawGx = evaluateMamlNumericExpression(layer.xExpression, varsMap, 0f)
                    val rawGy = evaluateMamlNumericExpression(layer.yExpression, varsMap, 0f)
                    val gx = when {
                        layer.alignAttr.equals("center", true) -> rawGx - gw / 2f
                        layer.alignAttr.equals("right", true) -> rawGx - gw
                        else -> rawGx
                    }
                    val gy = when {
                        layer.alignVAttr.equals("center", true) -> rawGy - gh / 2f
                        layer.alignVAttr.equals("bottom", true) -> rawGy - gh
                        else -> rawGy
                    }
                    val sc = if (layer.groupLayered) {
                        canvas.saveLayer(gx, gy, gx + gw, gy + gh, null)
                    } else {
                        canvas.save()
                    }
                    if (gx != 0f || gy != 0f) {
                        canvas.translate(gx, gy)
                    }
                    if (layer.groupClip) {
                        canvas.clipRect(0f, 0f, gw, gh)
                    }
                    groupSaveStack.addLast(sc)
                }

                "GROUP_END" -> {
                    if (skipGroupDepth > 0) {
                        skipGroupDepth--
                    } else if (groupSaveStack.isNotEmpty()) {
                        canvas.restoreToCount(groupSaveStack.removeLast())
                    }
                }

                "IMAGE" -> {
                    if (skipGroupDepth > 0 || !isMamlLayerVisible(layer.visibilityExpression, varsMap)) continue
                    val baseSrc = layer.imageFileName ?: layer.srcExp ?: continue
                    val resolvedSrc = if (!layer.srcidExpression.isNullOrBlank()) {
                        val idVal = evaluateMamlNumericExpression(layer.srcidExpression, varsMap, 0f).roundToInt()
                        appendMamlSpriteSuffix(baseSrc, idVal.toString())
                    } else {
                        baseSrc
                    }
                    val imgFile = File(spec.directoryPath, resolvedSrc).takeIf { it.isFile }
                        ?: File(spec.directoryPath, baseSrc)
                    val layerBmp = decodeBitmapCached(imgFile.absolutePath) ?: continue

                    val bw = layerBmp.width.toFloat()
                    val bh = layerBmp.height.toFloat()
                    if (layer.name.isNotBlank()) {
                        varsMap["#${layer.name}.bmp_width"] = bw
                        varsMap["${layer.name}.bmp_width"] = bw
                        varsMap["#${layer.name}.bmp_height"] = bh
                        varsMap["${layer.name}.bmp_height"] = bh
                    }

                    val x = evaluateMamlNumericExpression(layer.xExpression, varsMap, layer.centerX)
                    val y = evaluateMamlNumericExpression(layer.yExpression, varsMap, layer.centerY)
                    val uniformScale = evaluateMamlNumericExpression(layer.scaleExpression, varsMap, 1f)
                    val sx = evaluateMamlNumericExpression(layer.scaleXExpression, varsMap, uniformScale)
                    val sy = evaluateMamlNumericExpression(layer.scaleYExpression, varsMap, uniformScale)
                    val angle = evaluateMamlNumericExpression(layer.rotationExpression, varsMap, 0f)
                    val alphaVal = evaluateMamlNumericExpression(layer.alphaExpression, varsMap, layer.alpha.toFloat())
                        .toInt()
                        .coerceIn(0, 255)

                    val drawLeft = when {
                        layer.alignAttr.equals("center", true) -> x - bw / 2f
                        layer.alignAttr.equals("right", true) -> x - bw
                        else -> x
                    }
                    val drawTop = when {
                        layer.alignVAttr.equals("center", true) -> y - bh / 2f
                        layer.alignVAttr.equals("bottom", true) -> y - bh
                        else -> y
                    }

                    val pivX = evaluateMamlNumericExpression(layer.pivotXExpression, varsMap, bw / 2f)
                    val pivY = evaluateMamlNumericExpression(layer.pivotYExpression, varsMap, bh / 2f)
                    val absPivotX = drawLeft + pivX
                    val absPivotY = drawTop + pivY

                    canvas.save()
                    if (angle != 0f) {
                        canvas.rotate(angle, absPivotX, absPivotY)
                    }
                    if (sx != 1f || sy != 1f) {
                        canvas.scale(sx, sy, absPivotX, absPivotY)
                    }

                    val layerPaint = Paint(basePaint).apply {
                        alpha = alphaVal
                        xfermode = resolvePorterDuffXfermode(layer.xfermode)
                    }
                    canvas.drawBitmap(layerBmp, drawLeft, drawTop, layerPaint)
                    canvas.restore()
                    drewAnyVisual = true
                }

                "IMAGE_NUMBER" -> {
                    if (skipGroupDepth > 0 || !isMamlLayerVisible(layer.visibilityExpression, varsMap)) continue
                    val baseSrc = layer.imageFileName ?: layer.srcExp ?: continue
                    val numVal = evaluateMamlNumericExpression(layer.numberExpression ?: "#date", varsMap, 1f).roundToInt()
                    val numStr = numVal.toString()
                    val sprites = ArrayList<Bitmap>(numStr.length)
                    for (ch in numStr) {
                        val suffix = when (ch) {
                            '-' -> "-"
                            ':' -> "colon"
                            '.' -> "dot"
                            else -> ch.toString()
                        }
                        val spriteFile = File(spec.directoryPath, appendMamlSpriteSuffix(baseSrc, suffix))
                        decodeBitmapCached(spriteFile.absolutePath)?.let { sprites.add(it) }
                    }
                    if (sprites.isEmpty()) continue

                    val space = evaluateMamlNumericExpression(layer.spaceExpression, varsMap, 0f)
                    var totalW = 0f
                    var maxH = 0f
                    sprites.forEachIndexed { idx, bmp ->
                        totalW += bmp.width
                        if (idx < sprites.lastIndex) totalW += space
                        if (bmp.height > maxH) maxH = bmp.height.toFloat()
                    }
                    if (layer.name.isNotBlank()) {
                        varsMap["#${layer.name}.bmp_width"] = totalW
                        varsMap["${layer.name}.bmp_width"] = totalW
                        varsMap["#${layer.name}.bmp_height"] = maxH
                        varsMap["${layer.name}.bmp_height"] = maxH
                    }

                    val x = evaluateMamlNumericExpression(layer.xExpression, varsMap, layer.centerX)
                    val y = evaluateMamlNumericExpression(layer.yExpression, varsMap, layer.centerY)
                    val alphaVal = evaluateMamlNumericExpression(layer.alphaExpression, varsMap, layer.alpha.toFloat())
                        .toInt()
                        .coerceIn(0, 255)

                    var cursorX = when {
                        layer.alignAttr.equals("center", true) -> x - totalW / 2f
                        layer.alignAttr.equals("right", true) -> x - totalW
                        else -> x
                    }
                    val drawTop = when {
                        layer.alignVAttr.equals("center", true) -> y - maxH / 2f
                        layer.alignVAttr.equals("bottom", true) -> y - maxH
                        else -> y
                    }
                    val numPaint = Paint(basePaint).apply { alpha = alphaVal }
                    for (bmp in sprites) {
                        val digitTop = drawTop + (maxH - bmp.height) / 2f
                        canvas.drawBitmap(bmp, cursorX, digitTop, numPaint)
                        cursorX += bmp.width + space
                    }
                    drewAnyVisual = true
                }

                "RECTANGLE" -> {
                    if (skipGroupDepth > 0 || !isMamlLayerVisible(layer.visibilityExpression, varsMap)) continue
                    val x = evaluateMamlNumericExpression(layer.xExpression, varsMap, layer.centerX)
                    val y = evaluateMamlNumericExpression(layer.yExpression, varsMap, layer.centerY)
                    val w = evaluateMamlNumericExpression(layer.widthExpression, varsMap, 4f)
                    val h = evaluateMamlNumericExpression(layer.heightExpression, varsMap, 40f)
                    val pivX = evaluateMamlNumericExpression(layer.pivotXExpression, varsMap, w / 2f)
                    val pivY = evaluateMamlNumericExpression(layer.pivotYExpression, varsMap, h)
                    val angle = evaluateMamlNumericExpression(layer.rotationExpression, varsMap, 0f)
                    val alphaVal = evaluateMamlNumericExpression(layer.alphaExpression, varsMap, layer.alpha.toFloat())
                        .toInt()
                        .coerceIn(0, 255)

                    val drawLeft = when {
                        layer.alignAttr.equals("center", true) -> x - w / 2f
                        layer.alignAttr.equals("right", true) -> x - w
                        else -> x
                    }
                    val drawTop = when {
                        layer.alignVAttr.equals("center", true) -> y - h / 2f
                        layer.alignVAttr.equals("bottom", true) -> y - h
                        else -> y
                    }
                    val absPivotX = drawLeft + pivX
                    val absPivotY = drawTop + pivY

                    canvas.save()
                    if (angle != 0f) {
                        canvas.rotate(angle, absPivotX, absPivotY)
                    }
                    val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = parseColorSafe(layer.fillColorHex, Color.BLACK)
                        style = Paint.Style.FILL
                        alpha = alphaVal
                        xfermode = resolvePorterDuffXfermode(layer.xfermode)
                    }
                    val rect = RectF(drawLeft, drawTop, drawLeft + w, drawTop + h)
                    if (layer.cornerRadius > 0f) {
                        canvas.drawRoundRect(rect, layer.cornerRadius, layer.cornerRadius, rectPaint)
                    } else {
                        canvas.drawRect(rect, rectPaint)
                    }
                    canvas.restore()
                    drewAnyVisual = true
                }

                "DATETIME", "TEXT" -> {
                    if (skipGroupDepth > 0 || !isMamlLayerVisible(layer.visibilityExpression, varsMap)) continue
                    val expr = layer.textExpression ?: continue
                    val textValue = evaluateMamlTextExpression(
                        expr = expr,
                        isDateTimeTag = layer.layerType == "DATETIME",
                        calendar = calendar,
                        vars = varsMap
                    )
                    val x = evaluateMamlNumericExpression(layer.xExpression, varsMap, layer.centerX)
                    val y = evaluateMamlNumericExpression(layer.yExpression, varsMap, layer.centerY)
                    val alphaVal = evaluateMamlNumericExpression(layer.alphaExpression, varsMap, layer.alpha.toFloat())
                        .toInt()
                        .coerceIn(0, 255)

                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = parseColorSafe(layer.textColorHex, Color.WHITE)
                        textSize = layer.textSizePx
                        textAlign = when {
                            layer.alignAttr.equals("center", true) -> Paint.Align.CENTER
                            layer.alignAttr.equals("right", true) -> Paint.Align.RIGHT
                            else -> Paint.Align.LEFT
                        }
                        alpha = alphaVal
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    }

                    // Prevent wide weekday names (e.g. "Wednesday") from overflowing narrow calendar plates
                    val maxAllowedWidth = spec.width * 0.76f
                    val measuredW = textPaint.measureText(textValue)
                    if (measuredW > maxAllowedWidth && measuredW > 1f) {
                        textPaint.textSize = (layer.textSizePx * (maxAllowedWidth / measuredW)).coerceAtLeast(14f)
                    }

                    val baselineY = when {
                        layer.alignVAttr.equals("center", true) -> {
                            y - ((textPaint.descent() + textPaint.ascent()) / 2f)
                        }
                        layer.alignVAttr.equals("bottom", true) -> {
                            y - textPaint.descent()
                        }
                        else -> {
                            y - textPaint.ascent()
                        }
                    }
                    canvas.drawText(textValue, x, baselineY, textPaint)
                    drewAnyVisual = true
                }
            }
        }

        while (groupSaveStack.isNotEmpty()) {
            canvas.restoreToCount(groupSaveStack.removeLast())
        }
        canvas.restore()

        if (!drewAnyVisual) {
            val fallbackPath = spec.quietImagePath ?: spec.startImagePath
            return fallbackPath?.let { decodeBitmapCached(it)?.let(::scaleToTarget) } ?: output
        }
        return output
    }

    private fun isMamlLayerVisible(visibilityExpr: String?, vars: Map<String, Float>): Boolean {
        if (visibilityExpr.isNullOrBlank()) return true
        val trimmed = visibilityExpr.trim()
        if (trimmed.equals("true", ignoreCase = true)) return true
        if (trimmed.equals("false", ignoreCase = true)) return false
        return evaluateMamlNumericExpression(trimmed, vars, 1f) > 0f
    }

    private fun appendMamlSpriteSuffix(baseSrc: String, suffix: String): String {
        val dotIdx = baseSrc.lastIndexOf('.')
        return if (dotIdx > 0) {
            val prefix = baseSrc.substring(0, dotIdx)
            val ext = baseSrc.substring(dotIdx)
            "${prefix}_$suffix$ext"
        } else {
            "${baseSrc}_$suffix.png"
        }
    }

    private fun resolvePorterDuffXfermode(modeName: String?): Xfermode? {
        if (modeName.isNullOrBlank()) return null
        val mode = when (modeName.trim().lowercase()) {
            "dst_atop" -> PorterDuff.Mode.DST_ATOP
            "dst_in" -> PorterDuff.Mode.DST_IN
            "dst_out" -> PorterDuff.Mode.DST_OUT
            "dst_over" -> PorterDuff.Mode.DST_OVER
            "src_in" -> PorterDuff.Mode.SRC_IN
            "src_atop" -> PorterDuff.Mode.SRC_ATOP
            "src_out" -> PorterDuff.Mode.SRC_OUT
            "src_over" -> PorterDuff.Mode.SRC_OVER
            "clear" -> PorterDuff.Mode.CLEAR
            "multiply" -> PorterDuff.Mode.MULTIPLY
            "screen" -> PorterDuff.Mode.SCREEN
            "add", "lighten" -> PorterDuff.Mode.ADD
            "xor" -> PorterDuff.Mode.XOR
            else -> null
        }
        return mode?.let { PorterDuffXfermode(it) }
    }

    private fun buildMamlVariablesMap(
        spec: MamlFancyIconSpec,
        calendar: Calendar,
        animTimeMs: Long,
        batteryLevel: Int = 85
    ): MutableMap<String, Float> {
        val hour12 = (calendar.get(Calendar.HOUR) % 12).toFloat()
        val hour24 = calendar.get(Calendar.HOUR_OF_DAY).toFloat()
        val minute = calendar.get(Calendar.MINUTE).toFloat()
        val second = calendar.get(Calendar.SECOND).toFloat()
        val millisInDay = ((calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
            calendar.get(Calendar.MINUTE) * 60 +
            calendar.get(Calendar.SECOND)) * 1000L +
            calendar.get(Calendar.MILLISECOND)).toFloat()

        val map = HashMap<String, Float>(40)
        map["#hour12"] = hour12
        map["#hour24"] = hour24
        map["#minute"] = minute
        map["#second"] = second
        map["#time_sys"] = millisInDay
        map["#time_format"] = 1f
        map["#ampm"] = calendar.get(Calendar.AM_PM).toFloat()
        map["#date"] = calendar.get(Calendar.DAY_OF_MONTH).toFloat()
        map["#day"] = calendar.get(Calendar.DAY_OF_MONTH).toFloat()
        map["#day_of_week"] = calendar.get(Calendar.DAY_OF_WEEK).toFloat()
        map["#month"] = (calendar.get(Calendar.MONTH) + 1).toFloat()
        map["#year"] = calendar.get(Calendar.YEAR).toFloat()
        map["#battery_level"] = batteryLevel.toFloat()
        map["#weather_temperature"] = 24f
        map["#weather_id"] = 0f
        map["iconAlpha"] = 255f
        map["#iconAni"] = 1f
        map["#iconAni2"] = 1f
        map["#iconAni3"] = 1f
        map["#iconAni4"] = 0f
        map["#iconhour"] = 0f
        map["#iconminute"] = 0f
        map["#iconsecond"] = 0f

        for ((name, varAnim) in spec.variables) {
            val evaluated = evaluateMamlVarAtTime(varAnim, animTimeMs)
            map["#$name"] = evaluated
            map[name] = evaluated
        }
        for (vDef in spec.scalarVariables) {
            if (!vDef.isString) {
                val evaluated = evaluateMamlNumericExpression(vDef.expression, map, 0f)
                map["#${vDef.name}"] = evaluated
                map[vDef.name] = evaluated
            }
        }
        return map
    }

    private fun evaluateMamlVarAtTime(anim: MamlVarAnimation, elapsedMs: Long): Float {
        val frames = anim.frames
        if (frames.isEmpty()) return 1f
        if (frames.size == 1) return frames.first().value
        val t = elapsedMs.coerceAtLeast(0L).toInt()
        if (t <= frames.first().timeMs) return frames.first().value
        if (t >= frames.last().timeMs) return frames.last().value

        for (i in 0 until frames.size - 1) {
            val f0 = frames[i]
            val f1 = frames[i + 1]
            if (t in f0.timeMs..f1.timeMs) {
                val span = (f1.timeMs - f0.timeMs).coerceAtLeast(1)
                val rawProgress = ((t - f0.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                val eased = applyMamlEasing(rawProgress, f0.easeType)
                return f0.value + (f1.value - f0.value) * eased
            }
        }
        return frames.last().value
    }

    private fun applyMamlEasing(t: Float, easeType: String): Float {
        val clean = easeType.trim()
        val lower = clean.lowercase()
        return when {
            lower.startsWith("quadeaseout") -> 1f - (1f - t) * (1f - t)
            lower.startsWith("quadeasein") && !lower.contains("out") -> t * t
            lower.startsWith("quadeaseinout") -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f
            lower.startsWith("cubiceaseout") -> 1f - (1f - t).pow(3)
            lower.startsWith("backeaseout") -> {
                val paramStr = clean.substringAfter('(', "").substringBefore(')', "")
                val s = paramStr.toFloatOrNull() ?: 1.70158f
                val u = t - 1f
                u * u * ((s + 1f) * u + s) + 1f
            }
            lower.startsWith("bounceeaseout") -> bounceEaseOut(t)
            lower.startsWith("bounceeaseinout") -> {
                if (t < 0.5f) (1f - bounceEaseOut(1f - 2f * t)) * 0.5f
                else (1f + bounceEaseOut(2f * t - 1f)) * 0.5f
            }
            else -> t
        }
    }

    private fun bounceEaseOut(x: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> {
                val t = x - 1.5f / d1
                n1 * t * t + 0.75f
            }
            x < 2.5f / d1 -> {
                val t = x - 2.25f / d1
                n1 * t * t + 0.9375f
            }
            else -> {
                val t = x - 2.625f / d1
                n1 * t * t + 0.984375f
            }
        }
    }

    /**
     * Recursive-descent arithmetic evaluator for MAML expressions (`+`, `-`, `*`, `/`, `%`, parentheses, functions, and `#var` tokens).
     */
    private fun evaluateMamlNumericExpression(
        expr: String?,
        vars: Map<String, Float>,
        defaultVal: Float
    ): Float {
        if (expr.isNullOrBlank()) return defaultVal
        expr.toFloatOrNull()?.let { return it }
        vars[expr]?.let { return it }

        return try {
            val parser = MamlExprParser(expr, vars)
            val result = parser.parseExpression()
            if (result.isNaN() || result.isInfinite()) defaultVal else result
        } catch (_: Exception) {
            defaultVal
        }
    }

    private class MamlExprParser(
        private val input: String,
        private val vars: Map<String, Float>
    ) {
        private var pos = 0

        fun parseExpression(): Float {
            var v = parseTerm()
            while (true) {
                skipWhitespace()
                if (pos >= input.length) break
                val ch = input[pos]
                if (ch == '+') {
                    pos++
                    v += parseTerm()
                } else if (ch == '-') {
                    pos++
                    v -= parseTerm()
                } else {
                    break
                }
            }
            return v
        }

        private fun parseTerm(): Float {
            var v = parseFactor()
            while (true) {
                skipWhitespace()
                if (pos >= input.length) break
                val ch = input[pos]
                when (ch) {
                    '*' -> {
                        pos++
                        v *= parseFactor()
                    }
                    '/' -> {
                        pos++
                        val denom = parseFactor()
                        v = if (abs(denom) > 1e-6f) v / denom else 0f
                    }
                    '%' -> {
                        pos++
                        val mod = parseFactor()
                        v = if (abs(mod) > 1e-6f) v % mod else 0f
                    }
                    else -> break
                }
            }
            return v
        }

        private fun parseFactor(): Float {
            skipWhitespace()
            if (pos >= input.length) return 0f
            val ch = input[pos]
            if (ch == '+') {
                pos++
                return parseFactor()
            }
            if (ch == '-') {
                pos++
                return -parseFactor()
            }
            if (ch == '!') {
                pos++
                return if (parseFactor() == 0f) 1f else 0f
            }
            if (ch == '(') {
                pos++
                val v = parseExpression()
                skipWhitespace()
                if (pos < input.length && input[pos] == ')') pos++
                return v
            }
            if (ch == '#' || ch.isLetter() || ch == '_') {
                val start = pos
                pos++
                while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_' || input[pos] == '.')) {
                    pos++
                }
                val token = input.substring(start, pos)
                skipWhitespace()
                if (!token.startsWith("#") && pos < input.length && input[pos] == '(') {
                    pos++ // consume '('
                    val args = mutableListOf<Float>()
                    skipWhitespace()
                    if (pos < input.length && input[pos] != ')') {
                        while (pos < input.length) {
                            args.add(parseExpression())
                            skipWhitespace()
                            if (pos < input.length && input[pos] == ',') {
                                pos++
                            } else {
                                break
                            }
                        }
                    }
                    if (pos < input.length && input[pos] == ')') pos++
                    return evalFunc(token, args)
                }
                return vars[token] ?: vars["#$token"] ?: 0f
            }
            if (ch.isDigit() || ch == '.') {
                val start = pos
                while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                    pos++
                }
                return input.substring(start, pos).toFloatOrNull() ?: 0f
            }
            pos++
            return 0f
        }

        private fun evalFunc(name: String, args: List<Float>): Float {
            return when (name.lowercase()) {
                "ifelse" -> {
                    var i = 0
                    while (i + 1 < args.size) {
                        if (args[i] != 0f) return args[i + 1]
                        i += 2
                    }
                    if (args.size % 2 == 1) args.last() else 0f
                }
                "eq" -> if (args.size >= 2 && abs(args[0] - args[1]) < 1e-4f) 1f else 0f
                "ne" -> if (args.size >= 2 && abs(args[0] - args[1]) >= 1e-4f) 1f else 0f
                "gt" -> if (args.size >= 2 && args[0] > args[1]) 1f else 0f
                "ge" -> if (args.size >= 2 && args[0] >= args[1] - 1e-4f) 1f else 0f
                "lt" -> if (args.size >= 2 && args[0] < args[1]) 1f else 0f
                "le" -> if (args.size >= 2 && args[0] <= args[1] + 1e-4f) 1f else 0f
                "not" -> if (args.isEmpty() || args[0] == 0f) 1f else 0f
                "int", "floor" -> if (args.isNotEmpty()) args[0].toInt().toFloat() else 0f
                "round" -> if (args.isNotEmpty()) args[0].roundToInt().toFloat() else 0f
                "abs" -> if (args.isNotEmpty()) abs(args[0]) else 0f
                "min" -> if (args.size >= 2) kotlin.math.min(args[0], args[1]) else 0f
                "max" -> if (args.size >= 2) kotlin.math.max(args[0], args[1]) else 0f
                "isnull" -> 0f
                else -> 0f
            }
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }

    private fun evaluateMamlTextExpression(
        expr: String,
        isDateTimeTag: Boolean,
        calendar: Calendar,
        vars: Map<String, Float>,
        batteryLevel: Int = 85
    ): String {
        val trimmed = expr.trim()
        if (isDateTimeTag && !trimmed.contains('#')) {
            return try {
                SimpleDateFormat(trimmed, Locale.US).format(calendar.time)
            } catch (_: Exception) {
                calendar.get(Calendar.DAY_OF_MONTH).toString()
            }
        }
        if (trimmed.contains("#weather_temperature")) {
            val temp = (vars["#weather_temperature"] ?: 24f).toInt()
            return if (trimmed.contains("°")) "${temp}°" else temp.toString()
        }
        return when {
            trimmed.contains("#battery_level") -> "$batteryLevel%"
            trimmed.contains("#battery_state") -> "Charging"
            trimmed.equals("dd", ignoreCase = true) -> String.format(Locale.US, "%02d", calendar.get(Calendar.DAY_OF_MONTH))
            trimmed.equals("d", ignoreCase = true) -> calendar.get(Calendar.DAY_OF_MONTH).toString()
            trimmed.contains("EEEE") -> SimpleDateFormat("EEEE", Locale.US).format(calendar.time)
            trimmed.contains("EEE") -> SimpleDateFormat("EEE", Locale.US).format(calendar.time)
            trimmed.contains("#date") || trimmed.contains("#day") -> calendar.get(Calendar.DAY_OF_MONTH).toString()
            trimmed.contains("#month_short") -> SimpleDateFormat("MMM", Locale.US).format(calendar.time).uppercase(Locale.US)
            trimmed.contains("#month") -> (calendar.get(Calendar.MONTH) + 1).toString()
            trimmed.contains("#dow") || trimmed.contains("#day_of_week") -> SimpleDateFormat("EEE", Locale.US).format(calendar.time).uppercase(Locale.US)
            trimmed.contains("#hour24") -> String.format(Locale.US, "%02d", calendar.get(Calendar.HOUR_OF_DAY))
            trimmed.contains("#hour12") -> (calendar.get(Calendar.HOUR) % 12).toString()
            trimmed.contains("#year") -> calendar.get(Calendar.YEAR).toString()
            else -> {
                runCatching {
                    SimpleDateFormat(trimmed, Locale.US).format(calendar.time)
                }.getOrDefault(calendar.get(Calendar.DAY_OF_MONTH).toString())
            }
        }
    }

    private fun findFancyIconSpec(
        packageName: String,
        activityName: String,
        theme: MtzThemeMetadata
    ): MamlFancyIconSpec? {
        theme.fancyIconSpecs[packageName]?.let { return it }
        theme.fancyIconSpecs[activityName]?.let { return it }
        val aliases = XIAOMI_PACKAGE_ALIASES[packageName].orEmpty()
        for (alias in aliases) {
            theme.fancyIconSpecs[alias]?.let { return it }
        }
        return null
    }

    private fun findRuleIconPath(
        packageName: String,
        activityName: String,
        theme: MtzThemeMetadata
    ): Pair<String, String>? {
        if (theme.iconRulesMap.isEmpty()) return null
        val candidates = mutableListOf(
            packageName,
            activityName,
            "$packageName/$activityName",
            "$packageName.$activityName",
            "$packageName#$activityName"
        )
        if (packageName.contains("dialer") || packageName.contains("phone") || packageName.contains("contacts")) {
            candidates.add("android.intent.action.DIAL")
            candidates.add("android.intent.action.CALL")
        }
        if (packageName.contains("mms") || packageName.contains("messaging")) {
            candidates.add("android.intent.action.SENDTO")
            candidates.add("android.intent.action.MAIN")
        }
        if (packageName.contains("camera")) {
            candidates.add("android.media.action.IMAGE_CAPTURE")
        }
        if (packageName.contains("browser") || packageName.contains("chrome")) {
            candidates.add("android.intent.action.VIEW")
        }
        for (cand in candidates) {
            val iconName = theme.iconRulesMap[cand]
            if (iconName != null) {
                val clean = iconName.removeSuffix(".png")
                val path = theme.directIconsMap[clean] ?: theme.directIconsMap[iconName]
                if (path != null) {
                    return cand to path
                }
            }
        }
        return null
    }

    private fun findDirectIconPath(
        packageName: String,
        activityName: String,
        theme: MtzThemeMetadata
    ): Pair<String, String>? {
        val candidates = mutableListOf(
            packageName,
            activityName,
            "$packageName.$activityName",
            "$packageName#$activityName",
            activityName.substringAfterLast('.'),
            packageName.substringAfterLast('.')
        )
        XIAOMI_PACKAGE_ALIASES[packageName]?.let { candidates.addAll(it) }

        for (candidate in candidates) {
            val path = theme.directIconsMap[candidate]
            if (path != null) {
                return candidate to path
            }
        }
        return null
    }

    private fun applyColorMatrixFilter(source: Bitmap, matrix: FloatArray): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun applyLightingFilter(source: Bitmap, mulHex: String?, addHex: String?): Bitmap {
        val mul = mulHex?.let { parseColorSafe(it, Color.WHITE) } ?: Color.WHITE
        val add = addHex?.let { parseColorSafe(it, Color.BLACK) } ?: Color.BLACK
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = LightingColorFilter(mul, add)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun selectBgPath(
        packageName: String,
        theme: MtzThemeMetadata,
        enableMultiBgHash: Boolean
    ): String? {
        if (theme.iconBgPaths.isEmpty()) return null
        if (!enableMultiBgHash || theme.iconBgPaths.size == 1) {
            return theme.iconBgPaths.first()
        }
        val idx = abs(packageName.hashCode()) % theme.iconBgPaths.size
        return theme.iconBgPaths[idx]
    }

    /**
     * Executes every filter step in `theme.filterSteps` one by one in the exact XML order declared
     * in `transform_config.xml` when `theme.isCustomFilterPipeline` is active or `scope="all"` is enabled.
     */
    fun executeSequentialFilterPipeline(
        source: Bitmap,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig = LauncherEngineConfig()
    ): Bitmap {
        return try {
            var current = ensureSoftwareBitmap(source)
            if (current.width != SHADER_WORK_PX || current.height != SHADER_WORK_PX) {
                current = ensureSoftwareBitmap(Bitmap.createScaledBitmap(current, SHADER_WORK_PX, SHADER_WORK_PX, true))
            }
            for (step in theme.filterSteps) {
                current = when (step) {
                    is MtzIconFilterStep.StripAdaptivePlate -> {
                        applyStripAdaptivePlateStep(current, step.normalizeBounds, theme.pointsScaleX)
                    }
                    is MtzIconFilterStep.Edges -> {
                        applySequentialEdgesStep(current, step.threshold)
                    }
                    is MtzIconFilterStep.Spread -> {
                        if (step.radius != 0) applySpreadBitmap(current, step.radius) else current
                    }
                    is MtzIconFilterStep.GausBlur -> {
                        if (step.radius > 0) applyGausBlurBitmap(current, step.radius) else current
                    }
                    is MtzIconFilterStep.GrayScale -> {
                        applySequentialGrayScaleStep(current, step)
                    }
                    is MtzIconFilterStep.Levels -> {
                        applySequentialLevelsStep(current, step)
                    }
                    is MtzIconFilterStep.Hsl -> {
                        if (config.enableHslFilter) {
                            applyHslFilter(current, step.hueShift, step.saturationModify, step.lightnessModify)
                        } else {
                            current
                        }
                    }
                    is MtzIconFilterStep.ColorMatrixStep -> {
                        if (config.enableColorMatrixFilter) {
                            applyColorMatrixFilter(current, step.matrix)
                        } else {
                            current
                        }
                    }
                    is MtzIconFilterStep.LightingStep -> {
                        if (config.enableLightingFilter && (step.mulHex != null || step.addHex != null)) {
                            applyLightingFilter(current, step.mulHex, step.addHex)
                        } else {
                            current
                        }
                    }
                    is MtzIconFilterStep.AlphaStep -> {
                        if (step.alpha in 0..254) {
                            val w = current.width
                            val h = current.height
                            val px = IntArray(w * h)
                            current.getPixels(px, 0, w, 0, 0, w, h)
                            val outPx = applyAlphaPixels(px, step.alpha)
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                                setPixels(outPx, 0, w, 0, 0, w, h)
                            }
                        } else {
                            current
                        }
                    }
                    is MtzIconFilterStep.PointsMappingStep -> {
                        applySequentialPointsMappingStep(current, step)
                    }
                    is MtzIconFilterStep.ColorSplash -> {
                        applyColorSplashStep(current, step)
                    }
                    is MtzIconFilterStep.Duotone -> {
                        applyDuotoneStep(current, step)
                    }
                    is MtzIconFilterStep.ColorAdjust -> {
                        applyColorAdjustStep(current, step)
                    }
                    is MtzIconFilterStep.EdgeGlow -> {
                        applyEdgeGlowStep(current, step)
                    }
                    is MtzIconFilterStep.Posterize -> {
                        applyPosterizeStep(current, step)
                    }
                    is MtzIconFilterStep.Sepia -> {
                        applySepiaStep(current, step)
                    }
                    is MtzIconFilterStep.Invert -> {
                        applyInvertStep(current, step)
                    }
                    is MtzIconFilterStep.Monochrome -> {
                        applyMonochromeStep(current, step)
                    }
                }
            }
            current
        } catch (_: Exception) {
            ensureSoftwareBitmap(source)
        }
    }

    private fun applyStripAdaptivePlateStep(
        source: Bitmap,
        normalizeBounds: Boolean,
        pointsScaleX: Float
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val isWhiteLike = BooleanArray(total)
        var totalOpaque = 0
        for (i in 0 until total) {
            val c = pixels[i]
            val a = c ushr 24
            if (a < 20) continue
            totalOpaque++
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            if (maxC >= 195 && (maxC - minC) <= 42) {
                isWhiteLike[i] = true
            }
        }
        if (totalOpaque == 0) return source

        val isExteriorTransparent = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0
        fun enqueueTransparentEdge(idx: Int) {
            if (!isExteriorTransparent[idx] && (pixels[idx] ushr 24) < 20) {
                isExteriorTransparent[idx] = true
                queue[tail++] = idx
            }
        }
        for (x in 0 until w) {
            enqueueTransparentEdge(x)
            enqueueTransparentEdge((h - 1) * w + x)
        }
        for (y in 0 until h) {
            enqueueTransparentEdge(y * w)
            enqueueTransparentEdge(y * w + (w - 1))
        }
        while (head < tail) {
            val curr = queue[head++]
            val cx = curr % w
            val cy = curr / w
            if (cx > 0) enqueueTransparentEdge(curr - 1)
            if (cx < w - 1) enqueueTransparentEdge(curr + 1)
            if (cy > 0) enqueueTransparentEdge(curr - w)
            if (cy < h - 1) enqueueTransparentEdge(curr + w)
        }

        val isShapeBoundary = BooleanArray(total)
        val boundaryIndices = IntArray(total)
        var boundaryCount = 0
        var boundaryWhiteCount = 0
        for (i in 0 until total) {
            if ((pixels[i] ushr 24) < 20) continue
            val cx = i % w
            val cy = i / w
            val touchesEdge = cx == 0 || cx == w - 1 || cy == 0 || cy == h - 1 ||
                isExteriorTransparent[i - 1] ||
                isExteriorTransparent[i + 1] ||
                isExteriorTransparent[i - w] ||
                isExteriorTransparent[i + w]
            if (touchesEdge) {
                isShapeBoundary[i] = true
                boundaryIndices[boundaryCount++] = i
                if (isWhiteLike[i]) boundaryWhiteCount++
            }
        }

        val innerRingIndices = IntArray(total)
        var innerRingCount = 0
        var innerRingWhiteCount = 0
        for (i in 0 until total) {
            if ((pixels[i] ushr 24) < 150 || isShapeBoundary[i]) continue
            val cx = i % w
            val cy = i / w
            val touchesBoundary = (cx > 0 && isShapeBoundary[i - 1]) ||
                (cx < w - 1 && isShapeBoundary[i + 1]) ||
                (cy > 0 && isShapeBoundary[i - w]) ||
                (cy < h - 1 && isShapeBoundary[i + w])
            if (touchesBoundary) {
                innerRingIndices[innerRingCount++] = i
                if (isWhiteLike[i]) innerRingWhiteCount++
            }
        }

        val isOuterPlate = BooleanArray(total)
        var outerPlatePixels = 0
        val whitePerimeterMatch = (boundaryCount > 0 && boundaryWhiteCount * 100 >= boundaryCount * 35) ||
            (innerRingCount > 0 && innerRingWhiteCount * 100 >= innerRingCount * 40)

        if (whitePerimeterMatch) {
            head = 0
            tail = 0
            for (bIdx in 0 until boundaryCount) {
                val idx = boundaryIndices[bIdx]
                if (isWhiteLike[idx] && !isOuterPlate[idx]) {
                    isOuterPlate[idx] = true
                    queue[tail++] = idx
                }
            }
            for (rIdx in 0 until innerRingCount) {
                val idx = innerRingIndices[rIdx]
                if (isWhiteLike[idx] && !isOuterPlate[idx]) {
                    isOuterPlate[idx] = true
                    queue[tail++] = idx
                }
            }
            while (head < tail) {
                val curr = queue[head++]
                outerPlatePixels++
                val cx = curr % w
                val cy = curr / w
                if (cx > 0 && !isOuterPlate[curr - 1] && isWhiteLike[curr - 1]) {
                    isOuterPlate[curr - 1] = true
                    queue[tail++] = curr - 1
                }
                if (cx < w - 1 && !isOuterPlate[curr + 1] && isWhiteLike[curr + 1]) {
                    isOuterPlate[curr + 1] = true
                    queue[tail++] = curr + 1
                }
                if (cy > 0 && !isOuterPlate[curr - w] && isWhiteLike[curr - w]) {
                    isOuterPlate[curr - w] = true
                    queue[tail++] = curr - w
                }
                if (cy < h - 1 && !isOuterPlate[curr + w] && isWhiteLike[curr + w]) {
                    isOuterPlate[curr + w] = true
                    queue[tail++] = curr + w
                }
            }
            if (outerPlatePixels > totalOpaque / 8) {
                for (bIdx in 0 until boundaryCount) {
                    val idx = boundaryIndices[bIdx]
                    if (!isOuterPlate[idx]) {
                        isOuterPlate[idx] = true
                        outerPlatePixels++
                    }
                }
            }
        } else if (boundaryCount > 0) {
            val sampleArray = if (innerRingCount >= 12) innerRingIndices else boundaryIndices
            val sampleLen = if (innerRingCount >= 12) innerRingCount else boundaryCount
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (sIdx in 0 until sampleLen) {
                val c = pixels[sampleArray[sIdx]]
                sumR += (c shr 16) and 0xFF
                sumG += (c shr 8) and 0xFF
                sumB += c and 0xFF
            }
            val avgR = sumR / sampleLen.coerceAtLeast(1)
            val avgG = sumG / sampleLen.coerceAtLeast(1)
            val avgB = sumB / sampleLen.coerceAtLeast(1)

            fun matchesPlateSeed(pxColor: Int): Boolean {
                if ((pxColor ushr 24) < 20) return false
                val r = (pxColor shr 16) and 0xFF
                val g = (pxColor shr 8) and 0xFF
                val b = pxColor and 0xFF
                return abs(r - avgR) <= 46 && abs(g - avgG) <= 46 && abs(b - avgB) <= 46
            }

            fun canStepPlate(fromColor: Int, toColor: Int): Boolean {
                if ((toColor ushr 24) < 20) return false
                if (matchesPlateSeed(toColor)) return true
                val dr = abs(((fromColor shr 16) and 0xFF) - ((toColor shr 16) and 0xFF))
                val dg = abs(((fromColor shr 8) and 0xFF) - ((toColor shr 8) and 0xFF))
                val db = abs((fromColor and 0xFF) - (toColor and 0xFF))
                if (max(dr, max(dg, db)) > 10) return false
                val r = (toColor shr 16) and 0xFF
                val g = (toColor shr 8) and 0xFF
                val b = toColor and 0xFF
                return abs(r - avgR) <= 68 && abs(g - avgG) <= 68 && abs(b - avgB) <= 68
            }

            var matchingPerimeter = 0
            for (sIdx in 0 until sampleLen) {
                if (matchesPlateSeed(pixels[sampleArray[sIdx]])) matchingPerimeter++
            }
            if (matchingPerimeter * 100 >= sampleLen * 48) {
                val candidatePlate = BooleanArray(total)
                var candidateCount = 0
                head = 0
                tail = 0
                for (bIdx in 0 until boundaryCount) {
                    val idx = boundaryIndices[bIdx]
                    if (matchesPlateSeed(pixels[idx]) && !candidatePlate[idx]) {
                        candidatePlate[idx] = true
                        queue[tail++] = idx
                    }
                }
                for (rIdx in 0 until innerRingCount) {
                    val idx = innerRingIndices[rIdx]
                    if (matchesPlateSeed(pixels[idx]) && !candidatePlate[idx]) {
                        candidatePlate[idx] = true
                        queue[tail++] = idx
                    }
                }
                while (head < tail) {
                    val curr = queue[head++]
                    candidateCount++
                    val currColor = pixels[curr]
                    val cx = curr % w
                    val cy = curr / w
                    if (cx > 0 && !candidatePlate[curr - 1] && canStepPlate(currColor, pixels[curr - 1])) {
                        candidatePlate[curr - 1] = true
                        queue[tail++] = curr - 1
                    }
                    if (cx < w - 1 && !candidatePlate[curr + 1] && canStepPlate(currColor, pixels[curr + 1])) {
                        candidatePlate[curr + 1] = true
                        queue[tail++] = curr + 1
                    }
                    if (cy > 0 && !candidatePlate[curr - w] && canStepPlate(currColor, pixels[curr - w])) {
                        candidatePlate[curr - w] = true
                        queue[tail++] = curr - w
                    }
                    if (cy < h - 1 && !candidatePlate[curr + w] && canStepPlate(currColor, pixels[curr + w])) {
                        candidatePlate[curr + w] = true
                        queue[tail++] = curr + w
                    }
                }
                val remainingInner = totalOpaque - candidateCount
                if (candidateCount >= totalOpaque / 6 && remainingInner in (totalOpaque / 40)..(totalOpaque * 82 / 100)) {
                    System.arraycopy(candidatePlate, 0, isOuterPlate, 0, total)
                    for (bIdx in 0 until boundaryCount) {
                        val idx = boundaryIndices[bIdx]
                        if (!isOuterPlate[idx]) {
                            isOuterPlate[idx] = true
                            candidateCount++
                        }
                    }
                    outerPlatePixels = candidateCount
                }
            }
        }

        if (outerPlatePixels <= totalOpaque / 8) return source

        val outPixels = IntArray(total)
        var minFgX = w
        var minFgY = h
        var maxFgX = -1
        var maxFgY = -1
        for (i in 0 until total) {
            val c = pixels[i]
            val a = c ushr 24
            if (a < 20 || isOuterPlate[i]) {
                outPixels[i] = 0
            } else {
                outPixels[i] = c
                val px = i % w
                val py = i / w
                if (px < minFgX) minFgX = px
                if (py < minFgY) minFgY = py
                if (px > maxFgX) maxFgX = px
                if (py > maxFgY) maxFgY = py
            }
        }

        val strippedBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        strippedBmp.setPixels(outPixels, 0, w, 0, 0, w, h)

        val fgW = maxFgX - minFgX + 1
        val fgH = maxFgY - minFgY + 1
        val fgSpan = max(fgW, fgH)
        if (normalizeBounds && fgSpan in 8..(w - 4)) {
            val normalizedBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val normCanvas = Canvas(normalizedBmp)
            val targetRatio = (0.48f / pointsScaleX.coerceIn(0.45f, 1.0f)).coerceIn(0.64f, 0.86f)
            val targetSpan = w * targetRatio
            val scale = targetSpan / fgSpan.toFloat()
            val drawW = fgW * scale
            val drawH = fgH * scale
            val dstLeft = (w - drawW) / 2f
            val dstTop = (h - drawH) / 2f
            val srcRect = Rect(minFgX, minFgY, maxFgX + 1, maxFgY + 1)
            val dstRect = RectF(dstLeft, dstTop, dstLeft + drawW, dstTop + drawH)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            normCanvas.drawBitmap(strippedBmp, srcRect, dstRect, paint)
            return normalizedBmp
        }
        return strippedBmp
    }

    private fun applySequentialGrayScaleStep(
        source: Bitmap,
        step: MtzIconFilterStep.GrayScale
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val blackTransparent = step.blackColorHex.equals("#00000000", ignoreCase = true)
        val whiteTransparent = step.whiteColorHex.equals("#00000000", ignoreCase = true)
        val blackInt = if (blackTransparent) Color.TRANSPARENT else parseColorSafe(step.blackColorHex, Color.BLACK)
        val whiteInt = if (whiteTransparent) Color.TRANSPARENT else parseColorSafe(step.whiteColorHex, Color.WHITE)

        val bR = if (blackTransparent) ((whiteInt shr 16) and 0xFF) else ((blackInt shr 16) and 0xFF)
        val bG = if (blackTransparent) ((whiteInt shr 8) and 0xFF) else ((blackInt shr 8) and 0xFF)
        val bB = if (blackTransparent) (whiteInt and 0xFF) else (blackInt and 0xFF)

        val wR = if (whiteTransparent) bR else ((whiteInt shr 16) and 0xFF)
        val wG = if (whiteTransparent) bG else ((whiteInt shr 8) and 0xFF)
        val wB = if (whiteTransparent) bB else (whiteInt and 0xFF)

        val alphaScale = step.alpha.coerceIn(0, 255) / 255f

        for (i in 0 until total) {
            val c = pixels[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luma = (r * 77 + g * 150 + b * 29) shr 8
            val t = luma / 255f
            val outA = when {
                whiteTransparent -> (a * (1f - t) * alphaScale).toInt().coerceIn(0, 255)
                blackTransparent -> (a * t * alphaScale).toInt().coerceIn(0, 255)
                else -> (a * alphaScale).toInt().coerceIn(0, 255)
            }
            val outR = (bR + ((wR - bR) * t).toInt()).coerceIn(0, 255)
            val outG = (bG + ((wG - bG) * t).toInt()).coerceIn(0, 255)
            val outB = (bB + ((wB - bB) * t).toInt()).coerceIn(0, 255)
            pixels[i] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun applySequentialLevelsStep(
        source: Bitmap,
        step: MtzIconFilterStep.Levels
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val inMin = step.inputMin.coerceIn(0, 254)
        val inMax = step.inputMax.coerceIn(inMin + 1, 255)
        val span = (inMax - inMin).toFloat()
        val gamma = step.inputMiddle.coerceIn(0.1f, 5.0f)
        val outMin = step.outputMin.coerceIn(0, 255).toFloat()
        val outMax = step.outputMax.coerceIn(0, 255).toFloat()

        val lut = IntArray(256) { v ->
            val norm = ((v - inMin) / span).coerceIn(0f, 1f)
            val gAdj = if (gamma != 1.0f) norm.toDouble().pow(1.0 / gamma.toDouble()).toFloat() else norm
            (outMin + (outMax - outMin) * gAdj).toInt().coerceIn(0, 255)
        }

        for (i in 0 until total) {
            val c = pixels[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = lut[(c shr 16) and 0xFF]
            val g = lut[(c shr 8) and 0xFF]
            val b = lut[c and 0xFF]
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun applySequentialEdgesStep(
        source: Bitmap,
        threshold: Int
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val luma = IntArray(total)
        for (i in 0 until total) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = (((r * 77 + g * 150 + b * 29) shr 8) * a) / 255
        }

        val outPixels = IntArray(total)
        val effThreshold = threshold.coerceIn(1, 255)
        for (y in 1 until (h - 1)) {
            val row = y * w
            for (x in 1 until (w - 1)) {
                val idx = row + x
                val c = pixels[idx]
                val a = c ushr 24
                if (a < 15) continue
                val tl = luma[(y - 1) * w + (x - 1)]
                val tc = luma[(y - 1) * w + x]
                val tr = luma[(y - 1) * w + (x + 1)]
                val ml = luma[row + (x - 1)]
                val mr = luma[row + (x + 1)]
                val bl = luma[(y + 1) * w + (x - 1)]
                val bc = luma[(y + 1) * w + x]
                val br = luma[(y + 1) * w + (x + 1)]
                val gx = (-tl - (ml shl 1) - bl) + (tr + (mr shl 1) + br)
                val gy = (-tl - (tc shl 1) - tr) + (bl + (bc shl 1) + br)
                val mag = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt()
                if (mag >= effThreshold) {
                    val strength = ((mag - effThreshold).toFloat() / 140f).coerceIn(0.45f, 1.0f)
                    val outA = (a * strength).toInt().coerceIn(0, 255)
                    val r = ((c shr 16) and 0xFF).coerceAtLeast(180)
                    val g = ((c shr 8) and 0xFF).coerceAtLeast(180)
                    val b = (c and 0xFF).coerceAtLeast(180)
                    outPixels[idx] = (outA shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun applySequentialPointsMappingStep(
        source: Bitmap,
        step: MtzIconFilterStep.PointsMappingStep
    ): Bitmap {
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        if (step.srcPoints.size < 8 || step.dstPoints.size < 8) return source
        val srcPts = FloatArray(8) { i ->
            if (i % 2 == 0) step.srcPoints[i] * w else step.srcPoints[i] * h
        }
        val dstPts = FloatArray(8) { i ->
            if (i % 2 == 0) step.dstPoints[i] * w else step.dstPoints[i] * h
        }
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
            return source
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return out
    }

    /**
     * `<Filter name="ColorSplash">`:
     * Preserves or tints pixels whose HSV hue matches `step.targetHues` within `step.toleranceDegrees`
     * (and saturation >= `step.minSaturation`), while converting the remaining pixels to B&W grayscale
     * or tinting them with `step.restTintHex`. Supports `Invert="true"` to flip matched vs. rest pixels.
     */
    private fun applyColorSplashStep(
        source: Bitmap,
        step: MtzIconFilterStep.ColorSplash
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val splashColorInt = parseColorSafe(step.splashTintHex, Color.rgb(255, 59, 48))
        val splashHsv = FloatArray(3)
        Color.colorToHSV(splashColorInt, splashHsv)

        val restColorInt = step.restTintHex?.let { parseColorSafe(it, Color.GRAY) }
        val restR = if (restColorInt != null) (restColorInt shr 16) and 0xFF else 0
        val restG = if (restColorInt != null) (restColorInt shr 8) and 0xFF else 0
        val restB = if (restColorInt != null) restColorInt and 0xFF else 0

        val hsv = FloatArray(3)
        val outHsv = FloatArray(3)
        val tol = step.toleranceDegrees.coerceIn(1f, 180f)
        val minSat = step.minSaturation.coerceIn(0f, 1f)

        for (i in 0 until total) {
            val c = pixels[i]
            val a = c ushr 24
            if (a < 8) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luma = (r * 77 + g * 150 + b * 29) shr 8

            Color.colorToHSV(c, hsv)
            val pxHue = hsv[0]
            val pxSat = hsv[1]
            val pxVal = hsv[2]

            var hueMatched = false
            if (pxSat >= minSat) {
                for (targetHue in step.targetHues) {
                    val rawDiff = abs(pxHue - targetHue) % 360f
                    val angularDist = if (rawDiff > 180f) 360f - rawDiff else rawDiff
                    if (angularDist <= tol) {
                        hueMatched = true
                        break
                    }
                }
            }

            val isSplashed = if (step.invertSelection) !hueMatched else hueMatched

            if (isSplashed) {
                when (step.mode) {
                    ColorSplashMode.KEEP_ORIGINAL -> {
                        pixels[i] = c
                    }
                    ColorSplashMode.TINT_SPLASH -> {
                        outHsv[0] = splashHsv[0]
                        outHsv[1] = splashHsv[1].coerceAtLeast(0.65f)
                        outHsv[2] = pxVal.coerceIn(0.15f, 1.0f)
                        pixels[i] = Color.HSVToColor(a, outHsv)
                    }
                }
            } else {
                if (restColorInt == null) {
                    pixels[i] = (a shl 24) or (luma shl 16) or (luma shl 8) or luma
                } else {
                    val t = luma / 255f
                    val tr = (restR * t).toInt().coerceIn(0, 255)
                    val tg = (restG * t).toInt().coerceIn(0, 255)
                    val tb = (restB * t).toInt().coerceIn(0, 255)
                    pixels[i] = (a shl 24) or (tr shl 16) or (tg shl 8) or tb
                }
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * `<Filter name="Duotone">` / `<Filter name="GradientMap">`:
     * Maps pixel luminance onto a 2-stop (`ShadowColor -> HighlightColor`) or 3-stop
     * (`ShadowColor -> MidColor -> HighlightColor`) color ramp blended by `step.intensity`.
     */
    private fun applyDuotoneStep(
        source: Bitmap,
        step: MtzIconFilterStep.Duotone
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val shadowInt = parseColorSafe(step.shadowColorHex, Color.parseColor("#1B1464"))
        val midInt = step.midColorHex?.let { parseColorSafe(it, Color.GRAY) }
        val highlightInt = parseColorSafe(step.highlightColorHex, Color.parseColor("#00F5D4"))
        val blend = step.intensity.coerceIn(0f, 1f)

        val sR = (shadowInt shr 16) and 0xFF
        val sG = (shadowInt shr 8) and 0xFF
        val sB = shadowInt and 0xFF

        val mR = if (midInt != null) (midInt shr 16) and 0xFF else 0
        val mG = if (midInt != null) (midInt shr 8) and 0xFF else 0
        val mB = if (midInt != null) midInt and 0xFF else 0

        val hR = (highlightInt shr 16) and 0xFF
        val hG = (highlightInt shr 8) and 0xFF
        val hB = highlightInt and 0xFF

        for (i in 0 until total) {
            val c = pixels[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luma = (r * 77 + g * 150 + b * 29) shr 8
            val t = luma / 255f

            val (mapR, mapG, mapB) = if (midInt == null) {
                Triple(
                    (sR + (hR - sR) * t).toInt(),
                    (sG + (hG - sG) * t).toInt(),
                    (sB + (hB - sB) * t).toInt()
                )
            } else if (t < 0.5f) {
                val u = t * 2f
                Triple(
                    (sR + (mR - sR) * u).toInt(),
                    (sG + (mG - sG) * u).toInt(),
                    (sB + (mB - sB) * u).toInt()
                )
            } else {
                val u = (t - 0.5f) * 2f
                Triple(
                    (mR + (hR - mR) * u).toInt(),
                    (mG + (hG - mG) * u).toInt(),
                    (mB + (hB - mB) * u).toInt()
                )
            }

            val outR = (r + (mapR - r) * blend).toInt().coerceIn(0, 255)
            val outG = (g + (mapG - g) * blend).toInt().coerceIn(0, 255)
            val outB = (b + (mapB - b) * blend).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * `<Filter name="Adjust">` / `<Filter name="ColorAdjust">`:
     * Applies Hue shift (`-180..180`), Saturation multiplier (`0..3`), Contrast (`0.2..3`),
     * and Brightness offset (`-1..1`).
     */
    private fun applyColorAdjustStep(
        source: Bitmap,
        step: MtzIconFilterStep.ColorAdjust
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val hsv = FloatArray(3)
        val hasHsvChange = step.hueShift != 0f || step.saturationScale != 1.0f
        val contrast = step.contrast.coerceIn(0.2f, 3.0f)
        val brightOffset = step.brightness.coerceIn(-1f, 1f) * 255f

        for (i in 0 until total) {
            var c = pixels[i]
            val a = c ushr 24
            if (a < 8) continue
            if (hasHsvChange) {
                Color.colorToHSV(c, hsv)
                hsv[0] = ((hsv[0] + step.hueShift) % 360f + 360f) % 360f
                hsv[1] = (hsv[1] * step.saturationScale).coerceIn(0f, 1f)
                c = Color.HSVToColor(a, hsv)
            }
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val outR = (((r - 128f) * contrast) + 128f + brightOffset).toInt().coerceIn(0, 255)
            val outG = (((g - 128f) * contrast) + 128f + brightOffset).toInt().coerceIn(0, 255)
            val outB = (((b - 128f) * contrast) + 128f + brightOffset).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * `<Filter name="EdgeGlow">` / `<Filter name="NeonOutline">`:
     * Extracts 3x3 Sobel luminance edges above `step.threshold`, tints them with `step.edgeColorHex`,
     * optionally blooms them with `step.glowRadius`, and composites over `step.baseColorHex` and
     * original fill scaled by `step.fillAlpha`.
     */
    private fun applyEdgeGlowStep(
        source: Bitmap,
        step: MtzIconFilterStep.EdgeGlow
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val luma = IntArray(total)
        for (i in 0 until total) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = (((r * 77 + g * 150 + b * 29) shr 8) * a) / 255
        }

        val edgeColor = parseColorSafe(step.edgeColorHex, Color.parseColor("#00F5D4"))
        val eR = (edgeColor shr 16) and 0xFF
        val eG = (edgeColor shr 8) and 0xFF
        val eB = edgeColor and 0xFF

        val baseTransparent = step.baseColorHex.equals("#00000000", ignoreCase = true)
        val baseColor = if (baseTransparent) Color.TRANSPARENT else parseColorSafe(step.baseColorHex, Color.TRANSPARENT)
        val bR = (baseColor shr 16) and 0xFF
        val bG = (baseColor shr 8) and 0xFF
        val bB = baseColor and 0xFF

        val edgePixels = IntArray(total)
        val thresh = step.threshold.coerceIn(8, 180)
        for (y in 1 until (h - 1)) {
            val row = y * w
            for (x in 1 until (w - 1)) {
                val idx = row + x
                val a = pixels[idx] ushr 24
                if (a < 15) continue
                val tl = luma[(y - 1) * w + (x - 1)]
                val tc = luma[(y - 1) * w + x]
                val tr = luma[(y - 1) * w + (x + 1)]
                val ml = luma[row + (x - 1)]
                val mr = luma[row + (x + 1)]
                val bl = luma[(y + 1) * w + (x - 1)]
                val bc = luma[(y + 1) * w + x]
                val br = luma[(y + 1) * w + (x + 1)]
                val gx = (-tl - (ml shl 1) - bl) + (tr + (mr shl 1) + br)
                val gy = (-tl - (tc shl 1) - tr) + (bl + (bc shl 1) + br)
                val mag = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt()
                if (mag >= thresh) {
                    val edgeA = (((mag - thresh).toFloat() / 110f).coerceIn(0.55f, 1.0f) * a).toInt().coerceIn(0, 255)
                    edgePixels[idx] = (edgeA shl 24) or (eR shl 16) or (eG shl 8) or eB
                }
            }
        }

        val glowPixels = if (step.glowRadius > 0) {
            applyGausBlurPixels(edgePixels, w, h, step.glowRadius)
        } else {
            edgePixels
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(total)
        val fillAlpha = step.fillAlpha.coerceIn(0f, 1f)

        for (i in 0 until total) {
            val orig = pixels[i]
            val origA = orig ushr 24
            if (origA < 8 && (glowPixels[i] ushr 24) < 8) continue

            var accR = 0f
            var accG = 0f
            var accB = 0f
            var accA = 0f

            if (!baseTransparent && origA > 0) {
                accR = bR.toFloat()
                accG = bG.toFloat()
                accB = bB.toFloat()
                accA = origA / 255f
            }

            if (fillAlpha > 0f && origA > 0) {
                val fa = (origA / 255f) * fillAlpha
                val oR = (orig shr 16) and 0xFF
                val oG = (orig shr 8) and 0xFF
                val oB = orig and 0xFF
                accR = oR * fa + accR * (1f - fa)
                accG = oG * fa + accG * (1f - fa)
                accB = oB * fa + accB * (1f - fa)
                accA = fa + accA * (1f - fa)
            }

            val glow = glowPixels[i]
            val gA = (glow ushr 24) / 255f
            if (gA > 0f) {
                accR = eR * gA + accR * (1f - gA)
                accG = eG * gA + accG * (1f - gA)
                accB = eB * gA + accB * (1f - gA)
                accA = max(accA, gA)
            }

            val crisp = edgePixels[i]
            val cA = (crisp ushr 24) / 255f
            if (cA > 0f) {
                accR = eR * cA + accR * (1f - cA)
                accG = eG * cA + accG * (1f - cA)
                accB = eB * cA + accB * (1f - cA)
                accA = max(accA, cA)
            }

            val finalA = (accA * 255f).toInt().coerceIn(0, 255)
            outPixels[i] = (finalA shl 24) or
                (accR.toInt().coerceIn(0, 255) shl 16) or
                (accG.toInt().coerceIn(0, 255) shl 8) or
                accB.toInt().coerceIn(0, 255)
        }

        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * `<Filter name="Posterize">` / `<Filter name="Quantize">`:
     * Quantizes RGB channels into `step.levels` (`2..16`) discrete color bands.
     */
    private fun applyPosterizeStep(
        source: Bitmap,
        step: MtzIconFilterStep.Posterize
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val lvls = step.levels.coerceIn(2, 16)
        val lut = IntArray(256) { v ->
            val band = (v * lvls) / 256
            (band * 255) / (lvls - 1)
        }

        for (i in 0 until total) {
            val c = pixels[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = lut[(c shr 16) and 0xFF]
            val g = lut[(c shr 8) and 0xFF]
            val b = lut[c and 0xFF]
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * `<Filter name="Sepia">`:
     * Applies classic warm sepia toning blended by `step.intensity`.
     */
    private fun applySepiaStep(
        source: Bitmap,
        step: MtzIconFilterStep.Sepia
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val blend = step.intensity.coerceIn(0f, 1f)

        for (i in 0 until total) {
            val c = pixels[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val sr = (r * 0.393f + g * 0.769f + b * 0.189f).coerceIn(0f, 255f)
            val sg = (r * 0.349f + g * 0.686f + b * 0.168f).coerceIn(0f, 255f)
            val sb = (r * 0.272f + g * 0.534f + b * 0.131f).coerceIn(0f, 255f)
            val outR = (r + (sr - r) * blend).toInt().coerceIn(0, 255)
            val outG = (g + (sg - g) * blend).toInt().coerceIn(0, 255)
            val outB = (b + (sb - b) * blend).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * `<Filter name="Invert">`:
     * Inverts RGB channels (`255 - channel`) blended by `step.intensity` while preserving alpha.
     */
    private fun applyInvertStep(
        source: Bitmap,
        step: MtzIconFilterStep.Invert
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val blend = step.intensity.coerceIn(0f, 1f)

        for (i in 0 until total) {
            val c = pixels[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val outR = (r + ((255 - r) - r) * blend).toInt().coerceIn(0, 255)
            val outG = (g + ((255 - g) - g) * blend).toInt().coerceIn(0, 255)
            val outB = (b + ((255 - b) - b) * blend).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * `<Filter name="Monochrome">`:
     * Tints pixel luminance with `step.colorHex` blended by `step.intensity`.
     */
    private fun applyMonochromeStep(
        source: Bitmap,
        step: MtzIconFilterStep.Monochrome
    ): Bitmap {
        val w = source.width
        val h = source.height
        val total = w * h
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val tintColor = parseColorSafe(step.colorHex, Color.WHITE)
        val tR = (tintColor shr 16) and 0xFF
        val tG = (tintColor shr 8) and 0xFF
        val tB = tintColor and 0xFF
        val blend = step.intensity.coerceIn(0f, 1f)

        for (i in 0 until total) {
            val c = pixels[i]
            val a = c ushr 24
            if (a == 0) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lumaNorm = ((r * 77 + g * 150 + b * 29) shr 8) / 255f
            val mR = tR * lumaNorm
            val mG = tG * lumaNorm
            val mB = tB * lumaNorm
            val outR = (r + (mR - r) * blend).toInt().coerceIn(0, 255)
            val outG = (g + (mG - g) * blend).toInt().coerceIn(0, 255)
            val outB = (b + (mB - b) * blend).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun buildDiagnosticXmlSnippet(
        packageName: String,
        source: IconResolutionSource,
        theme: MtzThemeMetadata,
        effectiveScale: Float,
        enableColorFilter: Boolean,
        matchedEntry: String,
        fancySpec: MamlFancyIconSpec?
    ): String {
        if (source == IconResolutionSource.MTZ_FANCY_MAML && fancySpec != null && !theme.transformApplyToAllIcons) {
            return fancySpec.rawManifestXml
        }
        val inset = ((1f - effectiveScale) * 45f).toInt()
        val maxCoord = 90 - inset
        val polyAttr = if (theme.pointsMappingSrc != null && theme.pointsMappingDst != null) "setPolyToPoly=\"true\"" else "setPolyToPoly=\"false\""
        val scopeAttr = if (theme.transformApplyToAllIcons) "scope=\"all\"" else "scope=\"third_party\""
        val modeAttr = if (theme.isCustomFilterPipeline) "pipeline=\"sequential\"" else "pipeline=\"stock_miui\""
        val orderedFiltersXml = if (theme.filterSteps.isNotEmpty()) {
            theme.filterSteps.joinToString("\n                    ") { step ->
                when (step) {
                    is MtzIconFilterStep.StripAdaptivePlate -> "<Filter name=\"StripAdaptivePlate\" NormalizeBounds=\"${step.normalizeBounds}\" />"
                    is MtzIconFilterStep.Edges -> if (step.threshold == 32) "<Filter name=\"Edges\" />" else "<Filter name=\"Edges\" Threshold=\"${step.threshold}\" />"
                    is MtzIconFilterStep.Spread -> "<Filter name=\"Spread\"><Param name=\"Radius\" value=\"${step.radius}\" /></Filter>"
                    is MtzIconFilterStep.GausBlur -> "<Filter name=\"GausBlur\"><Param name=\"Radius\" value=\"${step.radius}\" /></Filter>"
                    is MtzIconFilterStep.GrayScale -> "<Filter name=\"GrayScale\"><Param name=\"BlackColor\" value=\"${step.blackColorHex}\" /><Param name=\"WhiteColor\" value=\"${step.whiteColorHex}\" /></Filter>"
                    is MtzIconFilterStep.Levels -> "<Filter name=\"Levels\"><Param name=\"InputMin\" value=\"${step.inputMin}\" /><Param name=\"InputMiddle\" value=\"${step.inputMiddle}\" /><Param name=\"InputMax\" value=\"${step.inputMax}\" /></Filter>"
                    is MtzIconFilterStep.Hsl -> "<Filter name=\"HSL\"><Param name=\"HueModify\" value=\"${step.hueShift}\" /><Param name=\"SaturationModify\" value=\"${step.saturationModify}\" /><Param name=\"LightnessModify\" value=\"${step.lightnessModify}\" /></Filter>"
                    is MtzIconFilterStep.ColorMatrixStep -> "<Filter name=\"ColorMatrix\"><Param name=\"Matrix\" value=\"20-float RGBA matrix\" /></Filter>"
                    is MtzIconFilterStep.LightingStep -> "<Filter name=\"Lighting\"><Param name=\"Mul\" value=\"${step.mulHex ?: "#FFFFFF"}\" /><Param name=\"Add\" value=\"${step.addHex ?: "#000000"}\" /></Filter>"
                    is MtzIconFilterStep.AlphaStep -> "<Filter name=\"Alpha\"><Param name=\"Alpha\" value=\"${step.alpha}\" /></Filter>"
                    is MtzIconFilterStep.PointsMappingStep -> "<Filter name=\"PointsMapping\" />"
                    is MtzIconFilterStep.ColorSplash -> {
                        val huesStr = step.targetHues.joinToString(",") { it.toInt().toString() }
                        "<Filter name=\"ColorSplash\" Colors=\"$huesStr\" Tolerance=\"${step.toleranceDegrees}\" Mode=\"${step.mode.name}\" Invert=\"${step.invertSelection}\" SplashTint=\"${step.splashTintHex}\" RestTint=\"${step.restTintHex ?: "none"}\" />"
                    }
                    is MtzIconFilterStep.Duotone -> "<Filter name=\"Duotone\" ShadowColor=\"${step.shadowColorHex}\" MidColor=\"${step.midColorHex ?: "none"}\" HighlightColor=\"${step.highlightColorHex}\" Intensity=\"${step.intensity}\" />"
                    is MtzIconFilterStep.ColorAdjust -> "<Filter name=\"Adjust\" Hue=\"${step.hueShift}\" Saturation=\"${step.saturationScale}\" Contrast=\"${step.contrast}\" Brightness=\"${step.brightness}\" />"
                    is MtzIconFilterStep.EdgeGlow -> "<Filter name=\"EdgeGlow\" EdgeColor=\"${step.edgeColorHex}\" BaseColor=\"${step.baseColorHex}\" Threshold=\"${step.threshold}\" FillAlpha=\"${step.fillAlpha}\" GlowRadius=\"${step.glowRadius}\" />"
                    is MtzIconFilterStep.Posterize -> "<Filter name=\"Posterize\" Levels=\"${step.levels}\" />"
                    is MtzIconFilterStep.Sepia -> "<Filter name=\"Sepia\" Intensity=\"${step.intensity}\" />"
                    is MtzIconFilterStep.Invert -> "<Filter name=\"Invert\" Intensity=\"${step.intensity}\" />"
                    is MtzIconFilterStep.Monochrome -> "<Filter name=\"Monochrome\" Color=\"${step.colorHex}\" Intensity=\"${step.intensity}\" />"
                }
            }
        } else {
            """<Filter name="GrayScale"><Param name="BlackColor" value="${theme.filterBlackColorHex}" /><Param name="WhiteColor" value="${theme.filterWhiteColorHex}" /></Filter>
                    <Filter name="Levels"><Param name="InputMin" value="${theme.filterLevelsInputMin}" /><Param name="InputMax" value="${theme.filterLevelsInputMax}" /></Filter>"""
        }
        return """
            <!-- Xiaomi MIUI / HyperOS Full Icon Theme API Trace ($matchedEntry) -->
            <IconTransform package="$packageName" stage="${source.code}" $scopeAttr $modeAttr>
                <PointsMapping scaleX="${theme.pointsScaleX}" scaleY="${theme.pointsScaleY}" $polyAttr>
                    <Point fromX="0.0" fromY="0.0" toX="$inset.0" toY="$inset.0" />
                    <Point fromX="90.0" fromY="90.0" toX="$maxCoord.0" toY="$maxCoord.0" />
                </PointsMapping>
                <IconFilters enabled="$enableColorFilter">
                    $orderedFiltersXml
                </IconFilters>
                <LayerComposite>
                    <Layer order="1" src="icon_background.png" fill="${theme.filterBlackColorHex}" />
                    ${if (theme.iconPatternPath != null) "<Layer order=\"2\" src=\"icon_pattern.png\" mode=\"OVERLAY\" />" else ""}
                    <Layer order="3" src="filtered_glyph_normalized" scale="$effectiveScale" />
                    <Layer order="4" src="icon_mask.png" mode="DST_IN" />
                    <Layer order="5" src="icon_border.png" mode="SRC_OVER" />
                    ${if (theme.iconShortcutPath != null) "<Layer order=\"6\" src=\"icon_shortcut.png\" mode=\"BADGE_BOTTOM_RIGHT\" />" else ""}
                </LayerComposite>
            </IconTransform>
        """.trimIndent()
    }

    private fun decodeBitmapCached(path: String): Bitmap? {
        bitmapCache[path]?.let { return it }
        val file = File(path)
        if (!file.exists()) return null
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, opts)?.let(::ensureSoftwareBitmap) ?: return null
        bitmapCache[path] = decoded
        return decoded
    }

    private fun scaleToTarget(bitmap: Bitmap): Bitmap {
        val soft = ensureSoftwareBitmap(bitmap)
        if (soft.width == TARGET_ICON_PX && soft.height == TARGET_ICON_PX) {
            return soft
        }
        return ensureSoftwareBitmap(Bitmap.createScaledBitmap(soft, TARGET_ICON_PX, TARGET_ICON_PX, true))
    }
}
