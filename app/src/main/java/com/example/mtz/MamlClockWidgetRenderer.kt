package com.example.mtz

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Xml
import com.example.domain.model.DeviceTelemetrySnapshot
import com.example.domain.model.MamlAniFrame
import com.example.domain.model.MamlClockWidgetSpec
import com.example.domain.model.MamlStringVarArray
import com.example.domain.model.MamlTriggerCommand
import com.example.domain.model.MamlVarAnimation
import com.example.domain.model.MamlVarDef
import com.example.domain.model.MamlWidgetNode
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Full scene-graph parser and renderer for Xiaomi MIUI / HyperOS MAML `clock_2x4` and `clock_3x4`
 * desktop widgets (`<Clock>` / `<clock>`).
 *
 * Supports:
 * - `<VarArray type="string">` indexed string lookup tables (`@hours`, `@minutes`, `@siangmalam`)
 * - `<Var>` numeric (`#var`) and string (`@var`) expressions with N-ary `ifelse(...)`, `eq(...)`,
 *   `not(...)`, `digit(n, pos)`, `int(...)`, `{` (`<`), `==`, `+`, `-`, `*`, `/`, `%`
 * - Nested `<Group>` hierarchies including `layered="true" clip="true"` offscreen compositing
 * - `<Image>` with `src`, `srcExp`, `srcid` (`m/m.png` -> `m/m_0.png`), `pivotX`/`pivotY`/`centerX`/`centerY`,
 *   `rotation`, `scale`, and `xfermode="dst_in"` masking
 * - `<ImageNumber>` / `<ImageChars>` multi-digit sprite rendering (`tt_0.png`..`tt_9.png`, `tem_-.png`)
 *   with `space` inter-digit spacing and dynamic `#<name>.bmp_width` / `#<name>.bmp_height` measurement
 * - `<DateTime>` and `<Text>` with `format`, `textExp`, `align`, `alignV`, `color`, `size`, and `bold`
 * - `<Button>` `<Trigger>` `<VariableCommand>` interactive variable updates
 */
class MamlClockWidgetRenderer {

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()
    private val triggerStateVars = ConcurrentHashMap<String, Double>()

    fun clearCache() {
        bitmapCache.clear()
        typefaceCache.clear()
    }

    fun executeTrigger(spec: MamlClockWidgetSpec, action: String = "double") {
        val matching = spec.triggers.filter { it.action.equals(action, ignoreCase = true) }
        if (matching.isEmpty()) return
        val ctx = EvalContext(
            numVars = HashMap<String, Double>().apply {
                for ((k, v) in triggerStateVars) {
                    if (k.startsWith("${spec.directoryPath}:")) {
                        put(k.substringAfterLast(':'), v)
                    }
                }
            },
            strVars = HashMap()
        )
        for (cmd in matching) {
            val nextVal = evalNumeric(cmd.expression, ctx)
            ctx.numVars[cmd.variableName] = nextVal
            triggerStateVars["${spec.directoryPath}:${cmd.variableName}"] = nextVal
        }
    }

    fun renderWidgetBitmap(
        spec: MamlClockWidgetSpec,
        targetWidthPx: Int,
        targetHeightPx: Int,
        nowMillis: Long,
        telemetry: DeviceTelemetrySnapshot,
        customFontPath: String? = null,
        is24HourFormat: Boolean = true
    ): Bitmap {
        val outW = targetWidthPx.coerceIn(120, 2048)
        val outH = targetHeightPx.coerceIn(80, 2048)
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val year = cal.get(Calendar.YEAR)
        val month0 = cal.get(Calendar.MONTH) // 0..11 in MAML
        val date = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1..7 (Sun..Sat)
        val hour12 = cal.get(Calendar.HOUR) // 0..11
        val hour24 = cal.get(Calendar.HOUR_OF_DAY) // 0..23
        val minute = cal.get(Calendar.MINUTE) // 0..59
        val second = cal.get(Calendar.SECOND) // 0..59
        val ampm = cal.get(Calendar.AM_PM) // 0=AM, 1=PM

        val weatherId = mapWeatherConditionToMiuiId(telemetry.weatherCondition)
        val numVars = HashMap<String, Double>()
        val strVars = HashMap<String, String>()

        // Populate system numeric variables
        numVars["time_sys"] = nowMillis.toDouble()
        numVars["year"] = year.toDouble()
        numVars["month"] = month0.toDouble()
        numVars["date"] = date.toDouble()
        numVars["day_of_week"] = dayOfWeek.toDouble()
        numVars["hour12"] = hour12.toDouble()
        numVars["hour24"] = hour24.toDouble()
        numVars["minute"] = minute.toDouble()
        numVars["second"] = second.toDouble()
        numVars["ampm"] = ampm.toDouble()
        numVars["time_format"] = if (is24HourFormat) 1.0 else 0.0
        numVars["weather_id"] = weatherId.toDouble()
        numVars["weather_temperature"] = telemetry.weatherTempCelsius.toDouble()
        numVars["weather_humidity"] = telemetry.weatherHumidity.toDouble()
        numVars["hasweather"] = 1.0
        numVars["battery_level"] = telemetry.batteryPercent.toDouble()
        numVars["applied_light_wallpaper"] = 0.0
        numVars["screen_width"] = spec.screenWidth.toDouble()
        numVars["screen_height"] = 1920.0

        // Determine natural view dimensions if referenced by #view_width / #view_height
        val is3x4 = spec.moduleName.contains("3x4")
        val defaultViewW = 460.0
        val defaultViewH = if (is3x4) 560.0 else 210.0
        numVars["view_width"] = defaultViewW
        numVars["view_height"] = defaultViewH
        numVars["s"] = triggerStateVars["${spec.directoryPath}:s"] ?: 0.0

        for ((k, v) in triggerStateVars) {
            if (k.startsWith("${spec.directoryPath}:")) {
                numVars[k.substringAfterLast(':')] = v
            }
        }

        // Populate localized strings from strings/strings.xml and system string variables
        strVars.putAll(spec.localizedStrings)
        strVars["weather_location"] = telemetry.weatherCity
        strVars["weather_city"] = telemetry.weatherCity
        strVars["weather_description"] = telemetry.weatherCondition
        strVars["weather_temperature_str"] = telemetry.weatherTempCelsius.toString()

        // Evaluate keyframe animations if any
        for ((vName, anim) in spec.varAnimations) {
            numVars[vName] = evaluateVarAnimation(anim, nowMillis)
        }

        val ctx = EvalContext(numVars, strVars)

        // 1. Evaluate <VarArray type="string">
        for (arr in spec.stringVarArrays) {
            val idx = evalNumeric(arr.indexExpression, ctx).toInt()
            val resolved = if (arr.items.isNotEmpty()) {
                arr.items[idx.coerceIn(0, arr.items.lastIndex)]
            } else {
                ""
            }
            strVars[arr.varName] = resolved
        }

        // 2. Evaluate <Var> definitions in declaration order
        for (vDef in spec.variables) {
            if (vDef.isString) {
                strVars[vDef.name] = evalString(vDef.expression, ctx)
            } else {
                numVars[vDef.name] = evalNumeric(vDef.expression, ctx)
            }
        }

        // 3. Pre-pass: measure all named <Image> and <ImageNumber> bitmaps so #<name>.bmp_width and
        //    #<name>.bmp_height are ready before parent <Group w="..."> expressions evaluate.
        for (node in spec.nodes) {
            preMeasureNamedBitmaps(node, spec.directoryPath, ctx)
        }

        val typeface = resolveTypeface(customFontPath, spec.directoryPath)

        // 4. Determine content bounding box in MAML coordinates so we can uniformly scale & center
        //    both sized-group widgets (e.g. theme2.mtz 436x198 / 436x550) and screen-space widgets
        //    (e.g. I.C.H.O.L.S.mtz centered at #screen_width/2-118).
        val contentBounds = computeSceneContentBounds(spec, ctx, nowMillis, typeface)
        val contentW = (contentBounds.width()).coerceAtLeast(64f)
        val contentH = (contentBounds.height()).coerceAtLeast(48f)

        val padFraction = 0.94f
        val fitScale = min(
            (outW * padFraction) / contentW,
            (outH * padFraction) / contentH
        ).coerceIn(0.15f, 4.0f)

        val contentCenterX = contentBounds.centerX()
        val contentCenterY = contentBounds.centerY()

        val saveRoot = canvas.save()
        canvas.translate(outW / 2f, outH / 2f)
        canvas.scale(fitScale, fitScale)
        canvas.translate(-contentCenterX, -contentCenterY)

        for (node in spec.nodes) {
            renderNode(
                canvas = canvas,
                node = node,
                baseDir = spec.directoryPath,
                ctx = ctx,
                nowMillis = nowMillis,
                typeface = typeface,
                inheritedAlpha = 255
            )
        }

        canvas.restoreToCount(saveRoot)
        return output
    }

    private fun preMeasureNamedBitmaps(
        node: MamlWidgetNode,
        baseDir: String,
        ctx: EvalContext
    ) {
        when (node) {
            is MamlWidgetNode.GroupNode -> {
                for (child in node.children) {
                    preMeasureNamedBitmaps(child, baseDir, ctx)
                }
            }
            is MamlWidgetNode.ImageNode -> {
                val bmp = resolveImageNodeBitmap(node, baseDir, ctx)
                val w = (node.wExpr?.let { evalNumeric(it, ctx).toFloat() } ?: bmp?.width?.toFloat() ?: 0f)
                val h = (node.hExpr?.let { evalNumeric(it, ctx).toFloat() } ?: bmp?.height?.toFloat() ?: 0f)
                if (!node.name.isNullOrBlank()) {
                    ctx.numVars["${node.name}.bmp_width"] = w.toDouble()
                    ctx.numVars["${node.name}.bmp_height"] = h.toDouble()
                }
            }
            is MamlWidgetNode.ImageNumberNode -> {
                val sprites = resolveImageNumberSprites(node, baseDir, ctx)
                val space = node.spaceExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                var totalW = 0f
                var maxH = 0f
                sprites.forEachIndexed { idx, bmp ->
                    totalW += bmp.width
                    if (idx < sprites.lastIndex) totalW += space
                    if (bmp.height > maxH) maxH = bmp.height.toFloat()
                }
                if (!node.name.isNullOrBlank()) {
                    ctx.numVars["${node.name}.bmp_width"] = totalW.toDouble()
                    ctx.numVars["${node.name}.bmp_height"] = maxH.toDouble()
                }
            }
            else -> {}
        }
    }

    private fun computeSceneContentBounds(
        spec: MamlClockWidgetSpec,
        ctx: EvalContext,
        nowMillis: Long,
        typeface: Typeface
    ): RectF {
        // Case 1: If there is a single primary top-level GroupNode with explicit w and h (like theme2.mtz),
        // use that group's aligned bounds directly for rock-solid layout & scaling.
        val rootGroups = spec.nodes.filterIsInstance<MamlWidgetNode.GroupNode>()
            .filter { it.wExpr != null && it.hExpr != null }
        if (rootGroups.size == 1 && spec.nodes.none { it is MamlWidgetNode.ImageNode || it is MamlWidgetNode.DateTimeNode || it is MamlWidgetNode.TextNode }) {
            val g = rootGroups.first()
            val x = g.xExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
            val y = g.yExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
            val w = g.wExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 436f
            val h = g.hExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 198f
            val left = alignOffset(x, w, g.align)
            val top = alignVOffset(y, h, g.alignV)
            return RectF(left, top, left + w, top + h)
        }

        // Case 2: Compute union of visible top-level elements (e.g. I.C.H.O.L.S.mtz)
        val bounds = RectF()
        var hasAny = false
        var anchorXSum = 0f
        var anchorXCount = 0

        fun unionRect(l: Float, t: Float, r: Float, b: Float) {
            if (r <= l || b <= t) return
            if (!hasAny) {
                bounds.set(l, t, r, b)
                hasAny = true
            } else {
                bounds.union(l, t, r, b)
            }
        }

        fun visitBounds(node: MamlWidgetNode, offsetX: Float, offsetY: Float) {
            when (node) {
                is MamlWidgetNode.GroupNode -> {
                    if (!isVisible(node.visibilityExpr, ctx)) return
                    val x = node.xExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val y = node.yExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val w = node.wExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val h = node.hExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val gx = offsetX + alignOffset(x, w, node.align)
                    val gy = offsetY + alignVOffset(y, h, node.alignV)
                    if (w > 0f && h > 0f) {
                        unionRect(gx, gy, gx + w, gy + h)
                    } else {
                        for (child in node.children) {
                            visitBounds(child, gx, gy)
                        }
                    }
                }
                is MamlWidgetNode.ImageNode -> {
                    if (!isVisible(node.visibilityExpr, ctx)) return
                    val bmp = resolveImageNodeBitmap(node, spec.directoryPath, ctx) ?: return
                    val x = node.xExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val y = node.yExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val w = node.wExpr?.let { evalNumeric(it, ctx).toFloat() } ?: bmp.width.toFloat()
                    val h = node.hExpr?.let { evalNumeric(it, ctx).toFloat() } ?: bmp.height.toFloat()
                    val l = offsetX + alignOffset(x, w, node.align)
                    val t = offsetY + alignVOffset(y, h, node.alignV)
                    unionRect(l, t, l + w, t + h)
                    anchorXSum += (l + w / 2f)
                    anchorXCount++
                }
                is MamlWidgetNode.ImageNumberNode -> {
                    if (!isVisible(node.visibilityExpr, ctx)) return
                    val sprites = resolveImageNumberSprites(node, spec.directoryPath, ctx)
                    if (sprites.isEmpty()) return
                    val space = node.spaceExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val w = sprites.sumOf { it.width }.toFloat() + max(0, sprites.size - 1) * space
                    val h = (sprites.maxOfOrNull { it.height } ?: 0).toFloat()
                    val x = node.xExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val y = node.yExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val l = offsetX + alignOffset(x, w, node.align)
                    val t = offsetY + alignVOffset(y, h, node.alignV)
                    unionRect(l, t, l + w, t + h)
                    anchorXSum += (l + w / 2f)
                    anchorXCount++
                }
                is MamlWidgetNode.DateTimeNode -> {
                    if (!isVisible(node.visibilityExpr, ctx)) return
                    val rawFmt = node.formatExp?.let { evalString(it, ctx) } ?: node.format ?: "HH:mm"
                    val formatted = formatMamlDateTime(rawFmt, nowMillis)
                    val sizePx = (node.sizeExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 36f).coerceAtLeast(8f)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.typeface = if (node.bold) Typeface.create(typeface, Typeface.BOLD) else typeface
                        textSize = sizePx
                    }
                    val w = paint.measureText(formatted).coerceAtLeast(20f)
                    val h = sizePx * 1.25f
                    val x = node.xExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val y = node.yExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val l = offsetX + alignOffset(x, w, node.align)
                    val t = offsetY + alignVOffset(y, h, node.alignV)
                    unionRect(l, t, l + w, t + h)
                    anchorXSum += (l + w / 2f)
                    anchorXCount++
                }
                is MamlWidgetNode.TextNode -> {
                    if (!isVisible(node.visibilityExpr, ctx)) return
                    val content = resolveTextNodeContent(node, ctx)
                    if (content.isEmpty()) return
                    val sizePx = (node.sizeExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 30f).coerceAtLeast(8f)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.typeface = if (node.bold) Typeface.create(typeface, Typeface.BOLD) else typeface
                        textSize = sizePx
                    }
                    val w = paint.measureText(content).coerceAtLeast(20f)
                    val h = sizePx * 1.25f
                    val x = node.xExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val y = node.yExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                    val l = offsetX + alignOffset(x, w, node.align)
                    val t = offsetY + alignVOffset(y, h, node.alignV)
                    unionRect(l, t, l + w, t + h)
                    anchorXSum += (l + w / 2f)
                    anchorXCount++
                }
                is MamlWidgetNode.ButtonNode -> {}
            }
        }

        for (node in spec.nodes) {
            visitBounds(node, 0f, 0f)
        }

        if (!hasAny) {
            return RectF(0f, 0f, 436f, 198f)
        }

        // Stabilize horizontal bounds around the mean anchor X so minute-by-minute word length changes
        // do not shift the widget's horizontal center, without artificially inflating smaller view_width scenes.
        if (anchorXCount > 0) {
            val meanCenterX = anchorXSum / anchorXCount
            val halfSpanX = max(abs(bounds.left - meanCenterX), abs(bounds.right - meanCenterX))
                .coerceAtLeast(120f)
            bounds.left = meanCenterX - halfSpanX
            bounds.right = meanCenterX + halfSpanX
        }
        return bounds
    }

    private fun renderNode(
        canvas: Canvas,
        node: MamlWidgetNode,
        baseDir: String,
        ctx: EvalContext,
        nowMillis: Long,
        typeface: Typeface,
        inheritedAlpha: Int
    ) {
        when (node) {
            is MamlWidgetNode.GroupNode -> {
                if (!isVisible(node.visibilityExpr, ctx)) return
                // Pre-measure direct children so dynamic group width expressions (#m.bmp_width+...) are fresh
                for (child in node.children) {
                    preMeasureNamedBitmaps(child, baseDir, ctx)
                }

                val x = node.xExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                val y = node.yExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                val w = node.wExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                val h = node.hExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                val left = alignOffset(x, w, node.align)
                val top = alignVOffset(y, h, node.alignV)

                val nodeAlpha = node.alphaExpr?.let { evalNumeric(it, ctx).toInt().coerceIn(0, 255) } ?: 255
                val effectiveAlpha = ((inheritedAlpha * nodeAlpha) / 255).coerceIn(0, 255)
                if (effectiveAlpha <= 0) return

                val scaleUniform = node.scaleExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 1f
                val sx = (node.scaleXExpr?.let { evalNumeric(it, ctx).toFloat() } ?: scaleUniform).coerceIn(0.05f, 5f)
                val sy = (node.scaleYExpr?.let { evalNumeric(it, ctx).toFloat() } ?: scaleUniform).coerceIn(0.05f, 5f)
                val rot = node.rotationExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                val pivX = node.pivotXExpr?.let { evalNumeric(it, ctx).toFloat() } ?: (w / 2f)
                val pivY = node.pivotYExpr?.let { evalNumeric(it, ctx).toFloat() } ?: (h / 2f)

                val saveCount = if (node.layered && w > 0f && h > 0f) {
                    val layerPaint = if (effectiveAlpha < 255) {
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = effectiveAlpha }
                    } else {
                        null
                    }
                    canvas.saveLayer(left, top, left + w, top + h, layerPaint)
                } else {
                    canvas.save()
                }

                canvas.translate(left, top)
                if (sx != 1f || sy != 1f) {
                    canvas.scale(sx, sy, pivX, pivY)
                }
                if (rot != 0f) {
                    canvas.rotate(rot, pivX, pivY)
                }
                if (node.clip && w > 0f && h > 0f) {
                    canvas.clipRect(0f, 0f, w, h)
                }

                val childInheritedAlpha = if (node.layered) 255 else effectiveAlpha
                for (child in node.children) {
                    renderNode(
                        canvas = canvas,
                        node = child,
                        baseDir = baseDir,
                        ctx = ctx,
                        nowMillis = nowMillis,
                        typeface = typeface,
                        inheritedAlpha = childInheritedAlpha
                    )
                }

                canvas.restoreToCount(saveCount)
            }

            is MamlWidgetNode.ImageNode -> {
                if (!isVisible(node.visibilityExpr, ctx)) return
                val bmp = resolveImageNodeBitmap(node, baseDir, ctx) ?: return

                val nodeAlpha = node.alphaExpr?.let { evalNumeric(it, ctx).toInt().coerceIn(0, 255) } ?: 255
                val effectiveAlpha = ((inheritedAlpha * nodeAlpha) / 255).coerceIn(0, 255)
                if (effectiveAlpha <= 0) return

                val x = node.xExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                val y = node.yExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                val drawW = node.wExpr?.let { evalNumeric(it, ctx).toFloat() } ?: bmp.width.toFloat()
                val drawH = node.hExpr?.let { evalNumeric(it, ctx).toFloat() } ?: bmp.height.toFloat()

                if (!node.name.isNullOrBlank()) {
                    ctx.numVars["${node.name}.bmp_width"] = drawW.toDouble()
                    ctx.numVars["${node.name}.bmp_height"] = drawH.toDouble()
                }

                val left = alignOffset(x, drawW, node.align)
                val top = alignVOffset(y, drawH, node.alignV)

                val scaleUniform = node.scaleExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 1f
                val sx = (node.scaleXExpr?.let { evalNumeric(it, ctx).toFloat() } ?: scaleUniform).coerceIn(0.05f, 5f)
                val sy = (node.scaleYExpr?.let { evalNumeric(it, ctx).toFloat() } ?: scaleUniform).coerceIn(0.05f, 5f)
                val rot = node.rotationExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f

                // In MAML, pivotX/pivotY on an <Image> are relative to the image's own top-left corner
                val pivX = node.pivotXExpr?.let { left + evalNumeric(it, ctx).toFloat() } ?: (left + drawW / 2f)
                val pivY = node.pivotYExpr?.let { top + evalNumeric(it, ctx).toFloat() } ?: (top + drawH / 2f)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = effectiveAlpha
                    resolvePorterDuffMode(node.xfermode)?.let { mode ->
                        xfermode = PorterDuffXfermode(mode)
                    }
                }

                val save = canvas.save()
                if (sx != 1f || sy != 1f) {
                    canvas.scale(sx, sy, pivX, pivY)
                }
                if (rot != 0f) {
                    canvas.rotate(rot, pivX, pivY)
                }
                val dstRect = RectF(left, top, left + drawW, top + drawH)
                canvas.drawBitmap(bmp, null, dstRect, paint)
                canvas.restoreToCount(save)
            }

            is MamlWidgetNode.ImageNumberNode -> {
                if (!isVisible(node.visibilityExpr, ctx)) return
                val sprites = resolveImageNumberSprites(node, baseDir, ctx)
                if (sprites.isEmpty()) return

                val nodeAlpha = node.alphaExpr?.let { evalNumeric(it, ctx).toInt().coerceIn(0, 255) } ?: 255
                val effectiveAlpha = ((inheritedAlpha * nodeAlpha) / 255).coerceIn(0, 255)
                if (effectiveAlpha <= 0) return

                val space = node.spaceExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                var totalW = 0f
                var maxH = 0f
                sprites.forEachIndexed { idx, bmp ->
                    totalW += bmp.width
                    if (idx < sprites.lastIndex) totalW += space
                    if (bmp.height > maxH) maxH = bmp.height.toFloat()
                }

                if (!node.name.isNullOrBlank()) {
                    ctx.numVars["${node.name}.bmp_width"] = totalW.toDouble()
                    ctx.numVars["${node.name}.bmp_height"] = maxH.toDouble()
                }

                val x = node.xExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                val y = node.yExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
                var cursorX = alignOffset(x, totalW, node.align)
                val top = alignVOffset(y, maxH, node.alignV)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = effectiveAlpha
                }
                for (bmp in sprites) {
                    val digitTop = top + (maxH - bmp.height) / 2f
                    canvas.drawBitmap(bmp, cursorX, digitTop, paint)
                    cursorX += bmp.width + space
                }
            }

            is MamlWidgetNode.DateTimeNode -> {
                if (!isVisible(node.visibilityExpr, ctx)) return
                val rawFmt = node.formatExp?.let { evalString(it, ctx) } ?: node.format ?: "HH:mm"
                val text = formatMamlDateTime(rawFmt, nowMillis)
                if (text.isEmpty()) return

                val nodeAlpha = node.alphaExpr?.let { evalNumeric(it, ctx).toInt().coerceIn(0, 255) } ?: 255
                val effectiveAlpha = ((inheritedAlpha * nodeAlpha) / 255).coerceIn(0, 255)
                if (effectiveAlpha <= 0) return

                drawMamlText(
                    canvas = canvas,
                    text = text,
                    xExpr = node.xExpr,
                    yExpr = node.yExpr,
                    align = node.align,
                    alignV = node.alignV,
                    colorHex = node.colorHex,
                    sizeExpr = node.sizeExpr,
                    bold = node.bold,
                    alpha = effectiveAlpha,
                    typeface = typeface,
                    ctx = ctx
                )
            }

            is MamlWidgetNode.TextNode -> {
                if (!isVisible(node.visibilityExpr, ctx)) return
                val text = resolveTextNodeContent(node, ctx)
                if (text.isEmpty()) return

                val nodeAlpha = node.alphaExpr?.let { evalNumeric(it, ctx).toInt().coerceIn(0, 255) } ?: 255
                val effectiveAlpha = ((inheritedAlpha * nodeAlpha) / 255).coerceIn(0, 255)
                if (effectiveAlpha <= 0) return

                drawMamlText(
                    canvas = canvas,
                    text = text,
                    xExpr = node.xExpr,
                    yExpr = node.yExpr,
                    align = node.align,
                    alignV = node.alignV,
                    colorHex = node.colorHex,
                    sizeExpr = node.sizeExpr,
                    bold = node.bold,
                    alpha = effectiveAlpha,
                    typeface = typeface,
                    ctx = ctx
                )
            }

            is MamlWidgetNode.ButtonNode -> {}
        }
    }

    private fun resolveTextNodeContent(
        node: MamlWidgetNode.TextNode,
        ctx: EvalContext
    ): String {
        if (!node.format.isNullOrBlank()) {
            val rawFormat = if (node.format.startsWith("@")) {
                ctx.strVars[node.format.substring(1).trim()] ?: node.format
            } else {
                node.format
            }
            if (!node.paras.isNullOrBlank()) {
                val paraTokens = splitTopLevelArgs(node.paras)
                val evaluatedArgs = paraTokens.map { token ->
                    val t = token.trim()
                    when {
                        t.startsWith("@") || t.startsWith("'") || t.startsWith("ifelse(", ignoreCase = true) -> evalString(t, ctx)
                        else -> evalNumeric(t, ctx).roundToInt()
                    }
                }
                return runCatching {
                    var result = rawFormat
                    for (arg in evaluatedArgs) {
                        val match = Regex("%(\\d+\\$)?[sd]").find(result) ?: break
                        val replacement = arg.toString()
                        result = result.replaceRange(match.range, replacement)
                    }
                    result
                }.getOrDefault(rawFormat)
            }
            return rawFormat
        }
        if (!node.textExp.isNullOrBlank()) {
            return evalString(node.textExp, ctx)
        }
        val rawText = node.text ?: return ""
        return if (rawText.startsWith("@")) {
            ctx.strVars[rawText.substring(1).trim()] ?: ""
        } else {
            rawText
        }
    }

    private fun drawMamlText(
        canvas: Canvas,
        text: String,
        xExpr: String?,
        yExpr: String?,
        align: String,
        alignV: String,
        colorHex: String,
        sizeExpr: String?,
        bold: Boolean,
        alpha: Int,
        typeface: Typeface,
        ctx: EvalContext
    ) {
        val x = xExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
        val y = yExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 0f
        val sizePx = (sizeExpr?.let { evalNumeric(it, ctx).toFloat() } ?: 32f).coerceAtLeast(8f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = if (bold) Typeface.create(typeface, Typeface.BOLD) else typeface
            textSize = sizePx
            color = parseSafeColor(colorHex)
            this.alpha = alpha
            textAlign = when {
                align.equals("center", ignoreCase = true) -> Paint.Align.CENTER
                align.equals("right", ignoreCase = true) -> Paint.Align.RIGHT
                else -> Paint.Align.LEFT
            }
        }

        val fm = paint.fontMetrics
        val baselineY = when {
            alignV.equals("center", ignoreCase = true) -> y - (fm.ascent + fm.descent) / 2f
            alignV.equals("bottom", ignoreCase = true) -> y - fm.descent
            else -> y - fm.ascent
        }
        canvas.drawText(text, x, baselineY, paint)
    }

    private fun resolveImageNodeBitmap(
        node: MamlWidgetNode.ImageNode,
        baseDir: String,
        ctx: EvalContext
    ): Bitmap? {
        val baseSrc = when {
            !node.srcExp.isNullOrBlank() -> evalString(node.srcExp, ctx)
            !node.src.isNullOrBlank() -> node.src
            else -> return null
        }.trim()
        if (baseSrc.isEmpty()) return null

        val finalRelativePath = if (!node.srcidExpr.isNullOrBlank()) {
            val idVal = evalNumeric(node.srcidExpr, ctx).roundToInt()
            appendSpriteSuffix(baseSrc, idVal.toString())
        } else {
            baseSrc
        }

        return decodeCachedBitmap(baseDir, finalRelativePath)
            ?: decodeCachedBitmap(baseDir, baseSrc)
    }

    private fun resolveImageNumberSprites(
        node: MamlWidgetNode.ImageNumberNode,
        baseDir: String,
        ctx: EvalContext
    ): List<Bitmap> {
        val baseSrc = when {
            !node.srcExp.isNullOrBlank() -> evalString(node.srcExp, ctx)
            !node.src.isNullOrBlank() -> node.src
            else -> return emptyList()
        }.trim()
        if (baseSrc.isEmpty()) return emptyList()

        // Handle MAML <Time src="time.png"/> and <Time src="date.png" format="d"/>
        if (node.numberExpr.isNullOrBlank()) {
            val fmt = node.format?.trim()
            if (fmt.equals("d", ignoreCase = true) || fmt.equals("dd", ignoreCase = true)) {
                val dayVal = (ctx.numVars["date"] ?: 1.0).roundToInt().coerceIn(1, 31)
                val dayStr = if (fmt.equals("dd", ignoreCase = true)) {
                    String.format(Locale.US, "%02d", dayVal)
                } else {
                    dayVal.toString()
                }
                val sprites = ArrayList<Bitmap>(dayStr.length)
                for (ch in dayStr) {
                    val relPath = appendSpriteSuffix(baseSrc, ch.toString())
                    decodeCachedBitmap(baseDir, relPath)?.let { sprites.add(it) }
                }
                return sprites
            } else {
                val is24h = (ctx.numVars["time_format"] ?: 1.0) != 0.0
                val rawH12 = (ctx.numVars["hour12"] ?: 0.0).roundToInt()
                val hourVal = if (is24h) {
                    (ctx.numVars["hour24"] ?: 0.0).roundToInt().coerceIn(0, 23)
                } else {
                    if (rawH12 == 0) 12 else rawH12
                }
                val minVal = (ctx.numVars["minute"] ?: 0.0).roundToInt().coerceIn(0, 59)
                val hStr = String.format(Locale.US, "%02d", hourVal)
                val mStr = String.format(Locale.US, "%02d", minVal)
                val sprites = ArrayList<Bitmap>(5)
                for (ch in hStr) {
                    decodeCachedBitmap(baseDir, appendSpriteSuffix(baseSrc, ch.toString()))?.let { sprites.add(it) }
                }
                val dotBmp = decodeCachedBitmap(baseDir, appendSpriteSuffix(baseSrc, "dot"))
                    ?: decodeCachedBitmap(baseDir, appendSpriteSuffix(baseSrc, "colon"))
                dotBmp?.let { sprites.add(it) }
                for (ch in mStr) {
                    decodeCachedBitmap(baseDir, appendSpriteSuffix(baseSrc, ch.toString()))?.let { sprites.add(it) }
                }
                return sprites
            }
        }

        val numVal = evalNumeric(node.numberExpr, ctx).roundToInt()
        val numStr = numVal.toString()
        val sprites = ArrayList<Bitmap>(numStr.length)
        for (ch in numStr) {
            val suffix = when (ch) {
                '-' -> "-"
                ':' -> "colon"
                '.' -> "dot"
                else -> ch.toString()
            }
            val relPath = appendSpriteSuffix(baseSrc, suffix)
            decodeCachedBitmap(baseDir, relPath)?.let { sprites.add(it) }
        }
        return sprites
    }

    private fun appendSpriteSuffix(baseSrc: String, suffix: String): String {
        val dotIdx = baseSrc.lastIndexOf('.')
        return if (dotIdx > 0) {
            val prefix = baseSrc.substring(0, dotIdx)
            val ext = baseSrc.substring(dotIdx)
            "${prefix}_$suffix$ext"
        } else {
            "${baseSrc}_$suffix.png"
        }
    }

    private fun decodeCachedBitmap(baseDir: String, relativePath: String): Bitmap? {
        val cleanRel = relativePath.removePrefix("/")
        val file = File(baseDir, cleanRel)
        val absPath = file.absolutePath
        bitmapCache[absPath]?.let { return it }
        if (!file.isFile) return null
        return try {
            BitmapFactory.decodeFile(absPath)?.also { bmp ->
                bitmapCache[absPath] = bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveTypeface(customFontPath: String?, widgetDir: String): Typeface {
        val candidatePath = customFontPath ?: File(widgetDir).walkTopDown().firstOrNull {
            val ext = it.extension.lowercase()
            ext == "ttf" || ext == "otf"
        }?.absolutePath

        if (!candidatePath.isNullOrBlank()) {
            typefaceCache[candidatePath]?.let { return it }
            runCatching {
                val f = File(candidatePath)
                if (f.isFile && f.length() > 512L) {
                    val tf = Typeface.createFromFile(f)
                    typefaceCache[candidatePath] = tf
                    return tf
                }
            }
        }
        return Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun alignOffset(x: Float, width: Float, align: String): Float {
        return when {
            align.equals("center", ignoreCase = true) -> x - width / 2f
            align.equals("right", ignoreCase = true) -> x - width
            else -> x
        }
    }

    private fun alignVOffset(y: Float, height: Float, alignV: String): Float {
        return when {
            alignV.equals("center", ignoreCase = true) -> y - height / 2f
            alignV.equals("bottom", ignoreCase = true) -> y - height
            else -> y
        }
    }

    private fun isVisible(visibilityExpr: String?, ctx: EvalContext): Boolean {
        if (visibilityExpr.isNullOrBlank()) return true
        val trimmed = visibilityExpr.trim()
        if (trimmed.equals("true", ignoreCase = true)) return true
        if (trimmed.equals("false", ignoreCase = true)) return false
        return evalNumeric(trimmed, ctx) > 0.0
    }

    private fun resolvePorterDuffMode(raw: String?): PorterDuff.Mode? {
        if (raw.isNullOrBlank()) return null
        return when (raw.trim().lowercase()) {
            "dst_in", "dstin" -> PorterDuff.Mode.DST_IN
            "src_in", "srcin" -> PorterDuff.Mode.SRC_IN
            "dst_out", "dstout" -> PorterDuff.Mode.DST_OUT
            "src_out", "srcout" -> PorterDuff.Mode.SRC_OUT
            "src_atop", "srcatop" -> PorterDuff.Mode.SRC_ATOP
            "dst_atop", "dstatop" -> PorterDuff.Mode.DST_ATOP
            "multiply" -> PorterDuff.Mode.MULTIPLY
            "screen" -> PorterDuff.Mode.SCREEN
            "add", "lighten" -> PorterDuff.Mode.ADD
            "overlay" -> PorterDuff.Mode.OVERLAY
            "clear" -> PorterDuff.Mode.CLEAR
            else -> null
        }
    }

    private fun formatMamlDateTime(formatPattern: String, nowMillis: Long): String {
        return try {
            val normalized = formatPattern
                .replace("NNNN", "EEEE")
                .replace("NNN", "EEE")
            SimpleDateFormat(normalized, Locale.getDefault()).format(Date(nowMillis))
        } catch (_: Exception) {
            formatPattern
        }
    }

    private fun parseSafeColor(hex: String): Int {
        return try {
            Color.parseColor(hex.trim())
        } catch (_: Exception) {
            Color.WHITE
        }
    }

    private fun mapWeatherConditionToMiuiId(condition: String): Int {
        val lower = condition.lowercase()
        return when {
            lower.contains("rain") || lower.contains("shower") || lower.contains("drizzle") -> 4
            lower.contains("storm") || lower.contains("thunder") -> 7
            lower.contains("snow") || lower.contains("sleet") || lower.contains("blizzard") -> 12
            lower.contains("haze") || lower.contains("smoke") || lower.contains("dust") -> 18
            lower.contains("fog") || lower.contains("mist") -> 3
            lower.contains("overcast") -> 2
            lower.contains("cloud") || lower.contains("partly") -> 1
            else -> 0 // Sunny / Clear
        }
    }

    private fun evaluateVarAnimation(anim: MamlVarAnimation, nowMillis: Long): Double {
        val frames = anim.frames
        if (frames.isEmpty()) return 0.0
        if (frames.size == 1) return frames.first().value.toDouble()
        val totalDuration = frames.last().timeMs.coerceAtLeast(1)
        val t = (nowMillis % totalDuration).toInt()
        for (i in 0 until frames.size - 1) {
            val f0 = frames[i]
            val f1 = frames[i + 1]
            if (t in f0.timeMs..f1.timeMs) {
                val span = (f1.timeMs - f0.timeMs).coerceAtLeast(1)
                val fraction = (t - f0.timeMs).toFloat() / span.toFloat()
                return (f0.value + (f1.value - f0.value) * fraction).toDouble()
            }
        }
        return frames.last().value.toDouble()
    }

    // =========================================================================
    // MAML Expression Engine (Numeric #var, String @var, Functions, Operators)
    // =========================================================================

    internal data class EvalContext(
        val numVars: MutableMap<String, Double>,
        val strVars: MutableMap<String, String>
    )

    internal fun evalString(expr: String?, ctx: EvalContext): String {
        if (expr.isNullOrBlank()) return ""
        val s = expr.trim()

        // Handle top-level ifelse(...) returning strings
        if (s.startsWith("ifelse(", ignoreCase = true) && s.endsWith(")")) {
            val inner = s.substring(7, s.length - 1)
            val args = splitTopLevelArgs(inner)
            var i = 0
            while (i + 1 < args.size) {
                val cond = evalNumeric(args[i], ctx)
                if (cond != 0.0) {
                    return evalString(args[i + 1], ctx)
                }
                i += 2
            }
            return if (args.size % 2 == 1) evalString(args.last(), ctx) else ""
        }

        // Split top-level '+' concatenation if quotes or @vars are present
        if (s.contains("'") || s.contains("@")) {
            val parts = splitTopLevelPlus(s)
            if (parts.size > 1) {
                return buildString {
                    for (part in parts) {
                        append(evalStringAtom(part.trim(), ctx))
                    }
                }
            }
            return evalStringAtom(s, ctx)
        }

        return evalStringAtom(s, ctx)
    }

    private fun evalStringAtom(atom: String, ctx: EvalContext): String {
        val t = atom.trim()
        if (t.length >= 2 && t.startsWith("'") && t.endsWith("'")) {
            return t.substring(1, t.length - 1)
        }
        if (t.startsWith("ifelse(", ignoreCase = true) && t.endsWith(")")) {
            return evalString(t, ctx)
        }
        if (t.startsWith("@")) {
            val key = t.substring(1).trim()
            return ctx.strVars[key] ?: ""
        }
        if (t.startsWith("#")) {
            val key = t.substring(1).trim()
            val num = ctx.numVars[key] ?: 0.0
            return if (num % 1.0 == 0.0) num.roundToInt().toString() else num.toString()
        }
        val num = evalNumeric(t, ctx)
        return if (num % 1.0 == 0.0) num.roundToInt().toString() else num.toString()
    }

    internal fun evalNumeric(expr: String?, ctx: EvalContext): Double {
        if (expr.isNullOrBlank()) return 0.0
        return try {
            MamlMathParser(expr.trim(), ctx).parseExpression()
        } catch (_: Exception) {
            0.0
        }
    }

    private fun splitTopLevelArgs(inner: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        var inQuotes = false
        for (ch in inner) {
            when {
                ch == '\'' -> {
                    inQuotes = !inQuotes
                    sb.append(ch)
                }
                !inQuotes && ch == '(' -> {
                    depth++
                    sb.append(ch)
                }
                !inQuotes && ch == ')' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    sb.append(ch)
                }
                !inQuotes && depth == 0 && ch == ',' -> {
                    result.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString().trim())
        }
        return result
    }

    private fun splitTopLevelPlus(expr: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        var inQuotes = false
        for (ch in expr) {
            when {
                ch == '\'' -> {
                    inQuotes = !inQuotes
                    sb.append(ch)
                }
                !inQuotes && ch == '(' -> {
                    depth++
                    sb.append(ch)
                }
                !inQuotes && ch == ')' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    sb.append(ch)
                }
                !inQuotes && depth == 0 && ch == '+' -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            result.add(sb.toString())
        }
        return result
    }

    private inner class MamlMathParser(
        private val input: String,
        private val ctx: EvalContext
    ) {
        private var pos = 0

        fun parseExpression(): Double {
            return parseComparison()
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }

        private fun parseComparison(): Double {
            var left = parseAddSub()
            while (true) {
                skipWhitespace()
                when {
                    matchToken("==") -> {
                        val right = parseAddSub()
                        left = if (abs(left - right) < 1e-6) 1.0 else 0.0
                    }
                    matchToken("!=") -> {
                        val right = parseAddSub()
                        left = if (abs(left - right) >= 1e-6) 1.0 else 0.0
                    }
                    matchToken("<=") || matchToken("{=") -> {
                        val right = parseAddSub()
                        left = if (left <= right + 1e-6) 1.0 else 0.0
                    }
                    matchToken(">=") -> {
                        val right = parseAddSub()
                        left = if (left >= right - 1e-6) 1.0 else 0.0
                    }
                    matchToken("<") || matchToken("{") -> {
                        // In Xiaomi MAML XML attributes, '{' is the official alias for '<'
                        val right = parseAddSub()
                        left = if (left < right - 1e-6) 1.0 else 0.0
                    }
                    matchToken(">") -> {
                        val right = parseAddSub()
                        left = if (left > right + 1e-6) 1.0 else 0.0
                    }
                    else -> return left
                }
            }
        }

        private fun parseAddSub(): Double {
            var left = parseMulDivMod()
            while (true) {
                skipWhitespace()
                if (pos < input.length && input[pos] == '+') {
                    pos++
                    left += parseMulDivMod()
                } else if (pos < input.length && input[pos] == '-') {
                    pos++
                    left -= parseMulDivMod()
                } else {
                    return left
                }
            }
        }

        private fun parseMulDivMod(): Double {
            var left = parseUnary()
            while (true) {
                skipWhitespace()
                if (pos < input.length && input[pos] == '*') {
                    pos++
                    left *= parseUnary()
                } else if (pos < input.length && input[pos] == '/') {
                    pos++
                    val divisor = parseUnary()
                    left = if (abs(divisor) < 1e-9) 0.0 else left / divisor
                } else if (pos < input.length && input[pos] == '%') {
                    pos++
                    val divisor = parseUnary()
                    left = if (abs(divisor) < 1e-9) 0.0 else left % divisor
                } else {
                    return left
                }
            }
        }

        private fun parseUnary(): Double {
            skipWhitespace()
            if (pos < input.length && input[pos] == '-') {
                pos++
                return -parseUnary()
            }
            if (pos < input.length && input[pos] == '+') {
                pos++
                return parseUnary()
            }
            if (pos < input.length && input[pos] == '!') {
                pos++
                val v = parseUnary()
                return if (v == 0.0) 1.0 else 0.0
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            skipWhitespace()
            if (pos >= input.length) return 0.0

            // Parentheses
            if (input[pos] == '(') {
                pos++
                val v = parseExpression()
                skipWhitespace()
                if (pos < input.length && input[pos] == ')') pos++
                return v
            }

            // Numeric variable #name or #name.bmp_width
            if (input[pos] == '#') {
                pos++
                val start = pos
                while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_' || input[pos] == '.')) {
                    pos++
                }
                val varName = input.substring(start, pos)
                return ctx.numVars[varName] ?: 0.0
            }

            // String variable @name used in numeric context (e.g. length or numeric conversion)
            if (input[pos] == '@') {
                pos++
                val start = pos
                while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_' || input[pos] == '.')) {
                    pos++
                }
                val varName = input.substring(start, pos)
                val strVal = ctx.strVars[varName] ?: ""
                return strVal.toDoubleOrNull() ?: if (strVal.isNotEmpty()) 1.0 else 0.0
            }

            // Single-quoted literal in numeric context
            if (input[pos] == '\'') {
                pos++
                val start = pos
                while (pos < input.length && input[pos] != '\'') pos++
                val lit = input.substring(start, pos)
                if (pos < input.length && input[pos] == '\'') pos++
                return lit.toDoubleOrNull() ?: 0.0
            }

            // Function call or identifier
            if (input[pos].isLetter() || input[pos] == '_') {
                val start = pos
                while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) {
                    pos++
                }
                val ident = input.substring(start, pos)
                skipWhitespace()
                if (pos < input.length && input[pos] == '(') {
                    pos++ // consume '('
                    val argsStart = pos
                    var depth = 1
                    var inQuotes = false
                    while (pos < input.length && depth > 0) {
                        val c = input[pos]
                        if (c == '\'') inQuotes = !inQuotes
                        else if (!inQuotes && c == '(') depth++
                        else if (!inQuotes && c == ')') depth--
                        pos++
                    }
                    val innerArgs = input.substring(argsStart, (pos - 1).coerceAtLeast(argsStart))
                    val args = splitTopLevelArgs(innerArgs)
                    return evalFunction(ident, args)
                }
                if (ident.equals("true", ignoreCase = true)) return 1.0
                if (ident.equals("false", ignoreCase = true)) return 0.0
                return ctx.numVars[ident] ?: 0.0
            }

            // Number literal
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                pos++
            }
            if (pos > start) {
                return input.substring(start, pos).toDoubleOrNull() ?: 0.0
            }

            // Unrecognized character: advance to avoid infinite loop
            pos++
            return 0.0
        }

        private fun evalFunction(name: String, args: List<String>): Double {
            return when (name.lowercase()) {
                "ifelse" -> {
                    var i = 0
                    while (i + 1 < args.size) {
                        val cond = evalNumeric(args[i], ctx)
                        if (cond != 0.0) {
                            return evalNumeric(args[i + 1], ctx)
                        }
                        i += 2
                    }
                    if (args.size % 2 == 1) evalNumeric(args.last(), ctx) else 0.0
                }
                "eq" -> {
                    if (args.size < 2) 0.0
                    else if (abs(evalNumeric(args[0], ctx) - evalNumeric(args[1], ctx)) < 1e-6) 1.0 else 0.0
                }
                "ne" -> {
                    if (args.size < 2) 0.0
                    else if (abs(evalNumeric(args[0], ctx) - evalNumeric(args[1], ctx)) >= 1e-6) 1.0 else 0.0
                }
                "gt" -> {
                    if (args.size < 2) 0.0
                    else if (evalNumeric(args[0], ctx) > evalNumeric(args[1], ctx)) 1.0 else 0.0
                }
                "ge" -> {
                    if (args.size < 2) 0.0
                    else if (evalNumeric(args[0], ctx) >= evalNumeric(args[1], ctx) - 1e-6) 1.0 else 0.0
                }
                "lt" -> {
                    if (args.size < 2) 0.0
                    else if (evalNumeric(args[0], ctx) < evalNumeric(args[1], ctx)) 1.0 else 0.0
                }
                "le" -> {
                    if (args.size < 2) 0.0
                    else if (evalNumeric(args[0], ctx) <= evalNumeric(args[1], ctx) + 1e-6) 1.0 else 0.0
                }
                "not" -> {
                    if (args.isEmpty()) 1.0
                    else if (evalNumeric(args[0], ctx) == 0.0) 1.0 else 0.0
                }
                "digit" -> {
                    if (args.size < 2) 0.0
                    else {
                        val n = abs(evalNumeric(args[0], ctx).toInt())
                        val pos1Based = evalNumeric(args[1], ctx).toInt().coerceAtLeast(1)
                        val divisor = 10.0.pow((pos1Based - 1).toDouble()).toInt().coerceAtLeast(1)
                        ((n / divisor) % 10).toDouble()
                    }
                }
                "int", "floor" -> {
                    if (args.isEmpty()) 0.0 else evalNumeric(args[0], ctx).toInt().toDouble()
                }
                "round" -> {
                    if (args.isEmpty()) 0.0 else evalNumeric(args[0], ctx).roundToInt().toDouble()
                }
                "abs" -> {
                    if (args.isEmpty()) 0.0 else abs(evalNumeric(args[0], ctx))
                }
                "sin" -> {
                    if (args.isEmpty()) 0.0 else sin(evalNumeric(args[0], ctx))
                }
                "cos" -> {
                    if (args.isEmpty()) 0.0 else cos(evalNumeric(args[0], ctx))
                }
                "min" -> {
                    if (args.size < 2) 0.0 else min(evalNumeric(args[0], ctx), evalNumeric(args[1], ctx))
                }
                "max" -> {
                    if (args.size < 2) 0.0 else max(evalNumeric(args[0], ctx), evalNumeric(args[1], ctx))
                }
                "isnull" -> {
                    if (args.isEmpty()) 1.0 else 0.0
                }
                else -> 0.0
            }
        }

        private fun matchToken(token: String): Boolean {
            if (input.regionMatches(pos, token, 0, token.length)) {
                pos += token.length
                return true
            }
            return false
        }
    }

    companion object {
        private class GroupFrame(
            val name: String?,
            val xExpr: String?,
            val yExpr: String?,
            val wExpr: String?,
            val hExpr: String?,
            val align: String,
            val alignV: String,
            val pivotXExpr: String?,
            val pivotYExpr: String?,
            val rotationExpr: String?,
            val scaleExpr: String?,
            val scaleXExpr: String?,
            val scaleYExpr: String?,
            val alphaExpr: String?,
            val visibilityExpr: String?,
            val layered: Boolean,
            val clip: Boolean,
            val children: MutableList<MamlWidgetNode> = mutableListOf()
        )

        /**
         * Parses a Xiaomi MAML `<Clock>` or `<clock>` `manifest.xml` into a [MamlClockWidgetSpec].
         */
        fun parseClockWidgetManifest(
            moduleName: String,
            directoryPath: String,
            xmlContent: String
        ): MamlClockWidgetSpec {
            var screenWidth = 1080
            var frameRate = 30
            val stringVarArrays = mutableListOf<MamlStringVarArray>()
            val variables = mutableListOf<MamlVarDef>()
            val varAnimations = linkedMapOf<String, MamlVarAnimation>()
            val topLevelNodes = mutableListOf<MamlWidgetNode>()
            val allTriggers = mutableListOf<MamlTriggerCommand>()

            val groupStack = ArrayDeque<GroupFrame>()
            val buttonStack = ArrayDeque<MutableList<MamlTriggerCommand>>()

            var insideVarArray = false
            val currentVarArrayBindings = mutableListOf<Pair<String, String>>() // name to indexExpr
            val currentVarArrayItems = mutableListOf<String>()

            var currentAnimVarName: String? = null
            val currentAnimFrames = mutableListOf<MamlAniFrame>()
            var currentEaseType = "Linear"

            var currentTriggerAction: String? = null

            val safeXml = sanitizeMamlClockXml(xmlContent)

            fun addNode(node: MamlWidgetNode) {
                if (groupStack.isNotEmpty()) {
                    groupStack.last().children.add(node)
                } else {
                    topLevelNodes.add(node)
                }
            }

            try {
                val parser = Xml.newPullParser()
                parser.setInput(StringReader(safeXml))
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            val tag = parser.name ?: ""
                            when {
                                tag.equals("Clock", ignoreCase = true) -> {
                                    screenWidth = parser.getAttributeValue(null, "screenWidth")?.toIntOrNull() ?: 1080
                                    frameRate = parser.getAttributeValue(null, "frameRate")?.toIntOrNull() ?: 30
                                }
                                tag.equals("VarArray", ignoreCase = true) -> {
                                    insideVarArray = true
                                    currentVarArrayBindings.clear()
                                    currentVarArrayItems.clear()
                                }
                                insideVarArray && tag.equals("Var", ignoreCase = true) -> {
                                    val name = parser.getAttributeValue(null, "name")
                                    val indexExpr = parser.getAttributeValue(null, "index")
                                    if (!name.isNullOrBlank() && !indexExpr.isNullOrBlank()) {
                                        currentVarArrayBindings.add(name to indexExpr)
                                    }
                                }
                                insideVarArray && tag.equals("Item", ignoreCase = true) -> {
                                    val value = parser.getAttributeValue(null, "value") ?: ""
                                    currentVarArrayItems.add(value)
                                }
                                !insideVarArray && tag.equals("Var", ignoreCase = true) -> {
                                    val name = parser.getAttributeValue(null, "name")
                                    val expr = parser.getAttributeValue(null, "expression")
                                    val type = parser.getAttributeValue(null, "type") ?: "number"
                                    val isConst = parser.getAttributeValue(null, "const").equals("true", ignoreCase = true)
                                    if (!name.isNullOrBlank() && expr != null) {
                                        variables.add(
                                            MamlVarDef(
                                                name = name,
                                                expression = expr,
                                                isString = type.equals("string", ignoreCase = true),
                                                isConst = isConst
                                            )
                                        )
                                    }
                                    currentAnimVarName = name
                                    currentAnimFrames.clear()
                                    currentEaseType = "Linear"
                                }
                                tag.equals("AniFrame", ignoreCase = true) -> {
                                    if (currentAnimVarName != null) {
                                        val v = parser.getAttributeValue(null, "value")?.toFloatOrNull() ?: 0f
                                        val t = parser.getAttributeValue(null, "time")?.toIntOrNull() ?: 0
                                        val ease = parser.getAttributeValue(null, "easeType")
                                        if (!ease.isNullOrBlank()) currentEaseType = ease
                                        currentAnimFrames.add(MamlAniFrame(value = v, timeMs = t, easeType = currentEaseType))
                                    }
                                }
                                tag.equals("Group", ignoreCase = true) || tag.equals("ElementGroup", ignoreCase = true) -> {
                                    groupStack.addLast(
                                        GroupFrame(
                                            name = parser.getAttributeValue(null, "name"),
                                            xExpr = parser.getAttributeValue(null, "x"),
                                            yExpr = parser.getAttributeValue(null, "y"),
                                            wExpr = parser.getAttributeValue(null, "w") ?: parser.getAttributeValue(null, "width"),
                                            hExpr = parser.getAttributeValue(null, "h") ?: parser.getAttributeValue(null, "height"),
                                            align = parser.getAttributeValue(null, "align") ?: "left",
                                            alignV = parser.getAttributeValue(null, "alignV") ?: "top",
                                            pivotXExpr = parser.getAttributeValue(null, "pivotX") ?: parser.getAttributeValue(null, "centerX"),
                                            pivotYExpr = parser.getAttributeValue(null, "pivotY") ?: parser.getAttributeValue(null, "centerY"),
                                            rotationExpr = parser.getAttributeValue(null, "rotation") ?: parser.getAttributeValue(null, "angle"),
                                            scaleExpr = parser.getAttributeValue(null, "scale"),
                                            scaleXExpr = parser.getAttributeValue(null, "scaleX"),
                                            scaleYExpr = parser.getAttributeValue(null, "scaleY"),
                                            alphaExpr = parser.getAttributeValue(null, "alpha"),
                                            visibilityExpr = parser.getAttributeValue(null, "visibility"),
                                            layered = parser.getAttributeValue(null, "layered").equals("true", ignoreCase = true),
                                            clip = parser.getAttributeValue(null, "clip").equals("true", ignoreCase = true)
                                        )
                                    )
                                }
                                tag.equals("Image", ignoreCase = true) -> {
                                    addNode(
                                        MamlWidgetNode.ImageNode(
                                            name = parser.getAttributeValue(null, "name"),
                                            xExpr = parser.getAttributeValue(null, "x"),
                                            yExpr = parser.getAttributeValue(null, "y"),
                                            wExpr = parser.getAttributeValue(null, "w") ?: parser.getAttributeValue(null, "width"),
                                            hExpr = parser.getAttributeValue(null, "h") ?: parser.getAttributeValue(null, "height"),
                                            align = parser.getAttributeValue(null, "align") ?: "left",
                                            alignV = parser.getAttributeValue(null, "alignV") ?: "top",
                                            pivotXExpr = parser.getAttributeValue(null, "pivotX") ?: parser.getAttributeValue(null, "centerX"),
                                            pivotYExpr = parser.getAttributeValue(null, "pivotY") ?: parser.getAttributeValue(null, "centerY"),
                                            rotationExpr = parser.getAttributeValue(null, "rotation") ?: parser.getAttributeValue(null, "angle"),
                                            scaleExpr = parser.getAttributeValue(null, "scale"),
                                            scaleXExpr = parser.getAttributeValue(null, "scaleX"),
                                            scaleYExpr = parser.getAttributeValue(null, "scaleY"),
                                            alphaExpr = parser.getAttributeValue(null, "alpha"),
                                            visibilityExpr = parser.getAttributeValue(null, "visibility"),
                                            src = parser.getAttributeValue(null, "src"),
                                            srcExp = parser.getAttributeValue(null, "srcExp"),
                                            srcidExpr = parser.getAttributeValue(null, "srcid"),
                                            xfermode = parser.getAttributeValue(null, "xfermode")
                                        )
                                    )
                                }
                                tag.equals("ImageNumber", ignoreCase = true) ||
                                    tag.equals("ImageChars", ignoreCase = true) ||
                                    tag.equals("Time", ignoreCase = true) -> {
                                    val isTime = tag.equals("Time", ignoreCase = true)
                                    addNode(
                                        MamlWidgetNode.ImageNumberNode(
                                            name = parser.getAttributeValue(null, "name"),
                                            xExpr = parser.getAttributeValue(null, "x"),
                                            yExpr = parser.getAttributeValue(null, "y"),
                                            align = parser.getAttributeValue(null, "align") ?: "left",
                                            alignV = parser.getAttributeValue(null, "alignV") ?: "top",
                                            numberExpr = if (isTime) null else (parser.getAttributeValue(null, "number") ?: parser.getAttributeValue(null, "string")),
                                            format = parser.getAttributeValue(null, "format"),
                                            src = parser.getAttributeValue(null, "src"),
                                            srcExp = parser.getAttributeValue(null, "srcExp"),
                                            spaceExpr = parser.getAttributeValue(null, "space"),
                                            alphaExpr = parser.getAttributeValue(null, "alpha"),
                                            visibilityExpr = parser.getAttributeValue(null, "visibility")
                                        )
                                    )
                                }
                                tag.equals("DateTime", ignoreCase = true) -> {
                                    val styleAttr = parser.getAttributeValue(null, "textStyle") ?: ""
                                    val boldAttr = parser.getAttributeValue(null, "bold") ?: ""
                                    addNode(
                                        MamlWidgetNode.DateTimeNode(
                                            name = parser.getAttributeValue(null, "name"),
                                            xExpr = parser.getAttributeValue(null, "x"),
                                            yExpr = parser.getAttributeValue(null, "y"),
                                            align = parser.getAttributeValue(null, "align") ?: "left",
                                            alignV = parser.getAttributeValue(null, "alignV") ?: "top",
                                            format = parser.getAttributeValue(null, "format"),
                                            formatExp = parser.getAttributeValue(null, "formatExp"),
                                            colorHex = parser.getAttributeValue(null, "color") ?: "#FFFFFF",
                                            sizeExpr = parser.getAttributeValue(null, "size"),
                                            alphaExpr = parser.getAttributeValue(null, "alpha"),
                                            visibilityExpr = parser.getAttributeValue(null, "visibility"),
                                            bold = styleAttr.contains("bold", ignoreCase = true) || boldAttr.equals("true", ignoreCase = true)
                                        )
                                    )
                                }
                                tag.equals("Text", ignoreCase = true) -> {
                                    val styleAttr = parser.getAttributeValue(null, "textStyle") ?: ""
                                    val boldAttr = parser.getAttributeValue(null, "bold") ?: ""
                                    addNode(
                                        MamlWidgetNode.TextNode(
                                            name = parser.getAttributeValue(null, "name"),
                                            xExpr = parser.getAttributeValue(null, "x"),
                                            yExpr = parser.getAttributeValue(null, "y"),
                                            align = parser.getAttributeValue(null, "align") ?: "left",
                                            alignV = parser.getAttributeValue(null, "alignV") ?: "top",
                                            text = parser.getAttributeValue(null, "text"),
                                            textExp = parser.getAttributeValue(null, "textExp") ?: parser.getAttributeValue(null, "exp"),
                                            format = parser.getAttributeValue(null, "format") ?: parser.getAttributeValue(null, "formatExp"),
                                            paras = parser.getAttributeValue(null, "paras"),
                                            colorHex = parser.getAttributeValue(null, "color") ?: "#FFFFFF",
                                            sizeExpr = parser.getAttributeValue(null, "size"),
                                            alphaExpr = parser.getAttributeValue(null, "alpha"),
                                            visibilityExpr = parser.getAttributeValue(null, "visibility"),
                                            bold = styleAttr.contains("bold", ignoreCase = true) || boldAttr.equals("true", ignoreCase = true)
                                        )
                                    )
                                }
                                tag.equals("Button", ignoreCase = true) -> {
                                    buttonStack.addLast(mutableListOf())
                                }
                                tag.equals("Trigger", ignoreCase = true) -> {
                                    currentTriggerAction = parser.getAttributeValue(null, "action") ?: "up"
                                }
                                tag.equals("VariableCommand", ignoreCase = true) -> {
                                    val vName = parser.getAttributeValue(null, "name")
                                    val vExpr = parser.getAttributeValue(null, "expression")
                                    if (!vName.isNullOrBlank() && !vExpr.isNullOrBlank()) {
                                        val cmd = MamlTriggerCommand(
                                            action = currentTriggerAction ?: "up",
                                            variableName = vName,
                                            expression = vExpr
                                        )
                                        allTriggers.add(cmd)
                                        buttonStack.lastOrNull()?.add(cmd)
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            val tag = parser.name ?: ""
                            when {
                                tag.equals("VarArray", ignoreCase = true) -> {
                                    val itemsCopy = currentVarArrayItems.toList()
                                    for ((varName, idxExpr) in currentVarArrayBindings) {
                                        stringVarArrays.add(
                                            MamlStringVarArray(
                                                varName = varName,
                                                indexExpression = idxExpr,
                                                items = itemsCopy
                                            )
                                        )
                                    }
                                    insideVarArray = false
                                }
                                !insideVarArray && tag.equals("Var", ignoreCase = true) -> {
                                    val vName = currentAnimVarName
                                    if (!vName.isNullOrBlank() && currentAnimFrames.isNotEmpty()) {
                                        varAnimations[vName] = MamlVarAnimation(
                                            name = vName,
                                            frames = currentAnimFrames.toList()
                                        )
                                    }
                                    currentAnimVarName = null
                                    currentAnimFrames.clear()
                                }
                                tag.equals("Group", ignoreCase = true) || tag.equals("ElementGroup", ignoreCase = true) -> {
                                    if (groupStack.isNotEmpty()) {
                                        val frame = groupStack.removeLast()
                                        val groupNode = MamlWidgetNode.GroupNode(
                                            name = frame.name,
                                            xExpr = frame.xExpr,
                                            yExpr = frame.yExpr,
                                            wExpr = frame.wExpr,
                                            hExpr = frame.hExpr,
                                            align = frame.align,
                                            alignV = frame.alignV,
                                            pivotXExpr = frame.pivotXExpr,
                                            pivotYExpr = frame.pivotYExpr,
                                            rotationExpr = frame.rotationExpr,
                                            scaleExpr = frame.scaleExpr,
                                            scaleXExpr = frame.scaleXExpr,
                                            scaleYExpr = frame.scaleYExpr,
                                            alphaExpr = frame.alphaExpr,
                                            visibilityExpr = frame.visibilityExpr,
                                            layered = frame.layered,
                                            clip = frame.clip,
                                            children = frame.children.toList()
                                        )
                                        addNode(groupNode)
                                    }
                                }
                                tag.equals("Button", ignoreCase = true) -> {
                                    if (buttonStack.isNotEmpty()) {
                                        val cmds = buttonStack.removeLast()
                                        addNode(MamlWidgetNode.ButtonNode(triggers = cmds))
                                    }
                                }
                                tag.equals("Trigger", ignoreCase = true) -> {
                                    currentTriggerAction = null
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            } catch (_: Exception) {
            }

            val localizedStrings = parseWidgetStringsXml(directoryPath)

            return MamlClockWidgetSpec(
                moduleName = moduleName,
                directoryPath = directoryPath,
                screenWidth = screenWidth,
                frameRate = frameRate,
                stringVarArrays = stringVarArrays,
                variables = variables,
                varAnimations = varAnimations,
                nodes = topLevelNodes,
                triggers = allTriggers,
                localizedStrings = localizedStrings,
                rawManifestXml = xmlContent
            )
        }

        private fun parseWidgetStringsXml(directoryPath: String): Map<String, String> {
            val baseDir = File(directoryPath)
            val stringsFile = File(baseDir, "strings/strings.xml").takeIf { it.isFile }
                ?: File(baseDir, "strings.xml").takeIf { it.isFile }
                ?: return emptyMap()
            val map = linkedMapOf<String, String>()
            runCatching {
                val parser = Xml.newPullParser()
                parser.setInput(StringReader(stringsFile.readText()))
                var event = parser.eventType
                var currentName: String? = null
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            if (parser.name.equals("string", ignoreCase = true)) {
                                currentName = parser.getAttributeValue(null, "name")
                                val valAttr = parser.getAttributeValue(null, "value")
                                if (!currentName.isNullOrBlank() && valAttr != null) {
                                    map[currentName!!] = valAttr
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            val key = currentName
                            if (!key.isNullOrBlank() && key !in map) {
                                map[key] = parser.text ?: ""
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name.equals("string", ignoreCase = true)) {
                                currentName = null
                            }
                        }
                    }
                    event = parser.next()
                }
            }
            return map
        }

        /**
         * Sanitizes hand-authored MAML XML so Android's `XmlPullParser` never aborts on:
         * - Missing whitespace between attributes (`clip="true"align="center"`)
         * - Duplicate attributes inside a single tag
         * - Comments containing invalid double hyphens
         */
        internal fun sanitizeMamlClockXml(xml: String): String {
            val withoutComments = xml.replace(Regex("<!--[\\s\\S]*?-->"), "")
            val spacedAttrs = withoutComments.replace(
                Regex("(\"[^\"]*\"|'[^']*')([A-Za-z_][A-Za-z0-9_.:-]*\\s*=)"),
                "$1 $2"
            )
            val tagRegex = Regex("<([A-Za-z_][A-Za-z0-9_.:-]*)(\\s+(?:\"[^\"]*\"|'[^']*'|[^/>\"'])*)?(/?>)")
            val attrRegex = Regex("([A-Za-z_][A-Za-z0-9_.:-]*)\\s*=\\s*(\"[^\"]*\"|'[^']*')")
            return tagRegex.replace(spacedAttrs) { match ->
                val tagName = match.groupValues[1]
                val attrBlock = match.groupValues[2]
                val closeToken = match.groupValues[3]
                val finalCloseToken = if (closeToken == "/>" || attrBlock.trimEnd().endsWith("/")) "/>" else ">"
                if (attrBlock.isBlank()) {
                    match.value
                } else {
                    val attrs = linkedMapOf<String, String>()
                    for (aMatch in attrRegex.findAll(attrBlock)) {
                        val key = aMatch.groupValues[1]
                        val value = aMatch.groupValues[2]
                        attrs.remove(key)
                        attrs[key] = value
                    }
                    if (attrs.isEmpty()) {
                        match.value
                    } else {
                        buildString {
                            append('<').append(tagName)
                            for ((k, v) in attrs) {
                                append(' ').append(k).append('=').append(v)
                            }
                            append(finalCloseToken)
                        }
                    }
                }
            }
        }
    }
}
