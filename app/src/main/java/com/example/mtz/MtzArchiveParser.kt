package com.example.mtz

import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Xml
import com.example.domain.model.ColorSplashMode
import com.example.domain.model.MamlAniFrame
import com.example.domain.model.MamlFancyIconSpec
import com.example.domain.model.MamlLayerSpec
import com.example.domain.model.MamlVarAnimation
import com.example.domain.model.MtzIconFilterStep
import com.example.domain.model.MtzThemeMetadata
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Parses Xiaomi MIUI / HyperOS `.mtz` and `.mtx` theme archives.
 */
class MtzArchiveParser {

    companion object {
        private val SKIPPED_TOP_LEVEL_SYSTEM_MODULES = setOf(
            "framework-miui-res",
            "framework-res",
            "com.android.contacts",
            "com.android.mms",
            "com.android.settings",
            "com.android.systemui",
            "com.android.updater",
            "com.miui.player",
            "lockscreen",
            "clock_2x4",
            "miwallpaper"
        )
    }

    fun parseAndExtractMtzStream(
        inputStream: InputStream,
        themeId: String,
        archiveFileName: String,
        targetExtractDir: File,
        isBuiltIn: Boolean
    ): MtzThemeMetadata {
        if (targetExtractDir.exists()) {
            targetExtractDir.deleteRecursively()
        }
        targetExtractDir.mkdirs()

        extractZipStreamRecursive(inputStream, targetExtractDir, isRootArchive = true)
        return loadExtractedTheme(
            themeId = themeId,
            extractedDir = targetExtractDir,
            archiveFileName = archiveFileName,
            isBuiltIn = isBuiltIn,
            importedAtMillis = System.currentTimeMillis()
        )
    }

    private fun extractZipStreamRecursive(
        inputStream: InputStream,
        destDir: File,
        isRootArchive: Boolean
    ) {
        val zis = ZipInputStream(inputStream)
        var entry: ZipEntry? = zis.nextEntry

        while (entry != null) {
            val cleanName = entry.name.removePrefix("/").replace("\\", "/")
            if (cleanName.isNotEmpty() && !cleanName.contains("..")) {
                val shouldSkip = isRootArchive && (
                    cleanName in SKIPPED_TOP_LEVEL_SYSTEM_MODULES ||
                        cleanName.startsWith("boots/") ||
                        cleanName.startsWith("preview/")
                    )
                if (!shouldSkip) {
                    val outFile = File(destDir, cleanName)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        val bytes = zis.readBytes()
                        if ((cleanName == "icons" || cleanName == "icons.zip") && isZipPayload(bytes)) {
                            val iconsDir = File(destDir, "icons_unpacked")
                            iconsDir.mkdirs()
                            extractZipStreamRecursive(ByteArrayInputStream(bytes), iconsDir, isRootArchive = false)
                        } else if (cleanName == "com.miui.home" && isZipPayload(bytes)) {
                            val homeDir = File(destDir, "com_miui_home_unpacked")
                            homeDir.mkdirs()
                            extractZipStreamRecursive(ByteArrayInputStream(bytes), homeDir, isRootArchive = false)
                        } else {
                            FileOutputStream(outFile).use { fos ->
                                fos.write(bytes)
                            }
                        }
                    }
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }

    private fun isZipPayload(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
    }

    fun loadExtractedTheme(
        themeId: String,
        extractedDir: File,
        archiveFileName: String,
        isBuiltIn: Boolean,
        importedAtMillis: Long
    ): MtzThemeMetadata {
        val rawIconsZip = File(extractedDir, "icons")
        val iconsUnpackedDir = File(extractedDir, "icons_unpacked")
        if (rawIconsZip.isFile && !iconsUnpackedDir.exists()) {
            runCatching {
                val bytes = rawIconsZip.readBytes()
                if (isZipPayload(bytes)) {
                    iconsUnpackedDir.mkdirs()
                    extractZipStreamRecursive(ByteArrayInputStream(bytes), iconsUnpackedDir, isRootArchive = false)
                }
            }
        }
        val rawHomeZip = File(extractedDir, "com.miui.home")
        val homeUnpackedDir = File(extractedDir, "com_miui_home_unpacked")
        if (rawHomeZip.isFile && !homeUnpackedDir.exists()) {
            runCatching {
                val bytes = rawHomeZip.readBytes()
                if (isZipPayload(bytes)) {
                    homeUnpackedDir.mkdirs()
                    extractZipStreamRecursive(ByteArrayInputStream(bytes), homeUnpackedDir, isRootArchive = false)
                }
            }
        }

        val allFiles = extractedDir.walkTopDown().filter { it.isFile }.toList()

        // 1. Parse description.xml
        val descriptionFile = allFiles.firstOrNull {
            it.name.equals("description.xml", ignoreCase = true)
        }
        val rawDescriptionXml = descriptionFile?.readText() ?: defaultDescriptionXml(archiveFileName)
        val descFields = parseDescriptionXml(rawDescriptionXml, archiveFileName)

        // 2. Parse theme_values.xml and transform_config.xml
        val themeValuesFile = allFiles.firstOrNull {
            it.name.equals("theme_values.xml", ignoreCase = true)
        }
        val rawThemeValuesXml = themeValuesFile?.readText().orEmpty()
        val themeValues = if (rawThemeValuesXml.isNotBlank()) {
            parseThemeValuesXml(rawThemeValuesXml)
        } else {
            ParsedThemeValues()
        }

        val transformConfigFile = allFiles.firstOrNull {
            it.name.equals("transform_config.xml", ignoreCase = true) ||
                it.name.equals("icon_config.xml", ignoreCase = true)
        }
        val hasTransformConfig = transformConfigFile != null
        val rawTransformXml = transformConfigFile?.readText().orEmpty()
        val parsedTransform = if (hasTransformConfig && rawTransformXml.isNotBlank()) {
            parseTransformConfigXml(rawTransformXml)
        } else {
            ParsedTransformConfig(
                pointsScaleX = 1.0f,
                pointsScaleY = 1.0f,
                pointsOffsetX = 0.0f,
                pointsOffsetY = 0.0f,
                filterSteps = emptyList()
            )
        }

        // 3. Parse icon_rule.xml / rules.xml
        val rulesFile = allFiles.firstOrNull {
            it.name.equals("icon_rule.xml", ignoreCase = true) ||
                it.name.equals("rules.xml", ignoreCase = true)
        }
        val rawRulesXml = rulesFile?.readText().orEmpty()
        val iconRulesMap = if (rawRulesXml.isNotBlank()) parseIconRulesXml(rawRulesXml) else emptyMap()

        // 4. Index Xiaomi Icon API Layer Templates + Direct Package Icons
        val iconBgMap = linkedMapOf<String, String>()
        var iconMaskPath: String? = null
        var iconBorderPath: String? = null
        var iconPatternPath: String? = null
        var iconShortcutPath: String? = null
        var iconFolderPath: String? = null
        var folderCoverPath: String? = null
        var symDefAppIconPath: String? = null
        val directIconsMap = mutableMapOf<String, String>()

        val imageFiles = allFiles.filter {
            val ext = it.extension.lowercase()
            (ext == "png" || ext == "webp" || ext == "jpg") &&
                !it.invariantSeparatorsPath.contains("/fancy_icons/") &&
                !it.invariantSeparatorsPath.contains("/wallpaper/") &&
                !it.invariantSeparatorsPath.contains("/preview/")
        }.sortedBy { densityPriorityOrder(it.invariantSeparatorsPath) }

        for (img in imageFiles) {
            val baseName = img.nameWithoutExtension
            val lowerName = baseName.lowercase()
            when {
                lowerName == "icon_background" ||
                    lowerName == "icon_bg" ||
                    lowerName == "icon_back" ||
                    lowerName.startsWith("icon_bg_") ||
                    lowerName.startsWith("icon_back_") -> {
                    iconBgMap[lowerName] = img.absolutePath
                }
                lowerName == "icon_mask" || lowerName == "mask" -> {
                    iconMaskPath = img.absolutePath
                }
                lowerName == "icon_border" || lowerName == "icon_fg" || lowerName == "icon_upon" -> {
                    iconBorderPath = img.absolutePath
                }
                lowerName == "icon_pattern" -> {
                    if (img.length() > 200L) {
                        iconPatternPath = img.absolutePath
                    }
                }
                lowerName == "icon_shortcut" || lowerName == "icon_shortcut_arrow" -> {
                    iconShortcutPath = img.absolutePath
                }
                lowerName == "icon_folder" ||
                    lowerName == "folder_background" ||
                    lowerName == "icon_folder_light" ||
                    lowerName == "icon_folder_dark" -> {
                    if (iconFolderPath == null || lowerName == "icon_folder" || lowerName == "icon_folder_light") {
                        iconFolderPath = img.absolutePath
                    }
                }
                lowerName == "folder_icon_cover" -> {
                    folderCoverPath = img.absolutePath
                }
                lowerName == "sym_def_app_icon" -> {
                    symDefAppIconPath = img.absolutePath
                }
                else -> {
                    directIconsMap[baseName] = img.absolutePath
                }
            }
        }
        val iconBgPaths = iconBgMap.values.sorted()

        val effectiveIconScale = themeValues.iconScale
            ?: if (hasTransformConfig) {
                parsedTransform.pointsScaleX
            } else if (iconBgPaths.isNotEmpty()) {
                0.86f
            } else {
                1.0f
            }

        // If transform_config.xml did not specify an explicit BlackColor, sample the background plate if colored
        val effectiveBlackColorHex = parsedTransform.blackColorHex
            ?: sampleBgDominantHex(iconBgPaths.firstOrNull() ?: iconPatternPath)
            ?: "#465967"

        // 5. Parse Xiaomi MAML fancy_icons/<package>/manifest.xml
        val fancyIconSpecs = mutableMapOf<String, MamlFancyIconSpec>()
        val manifestFiles = allFiles.filter {
            it.name.equals("manifest.xml", ignoreCase = true) &&
                it.invariantSeparatorsPath.contains("fancy_icons")
        }
        for (manifestFile in manifestFiles) {
            val pkgFolder = manifestFile.parentFile ?: continue
            val pkgName = pkgFolder.name
            val rawXml = manifestFile.readText()
            val spec = parseFancyIconManifest(pkgName, pkgFolder.absolutePath, rawXml)
            fancyIconSpecs[pkgName] = spec
        }

        // 6. Locate Wallpaper
        val wallpaperFiles = allFiles.filter {
            it.invariantSeparatorsPath.contains("wallpaper") &&
                (it.extension.equals("png", true) || it.extension.equals("jpg", true))
        }
        val wallpaperFile = wallpaperFiles.firstOrNull {
            it.name.contains("default_wallpaper", ignoreCase = true)
        } ?: wallpaperFiles.firstOrNull()

        return MtzThemeMetadata(
            id = themeId,
            title = descFields["title"]?.takeIf { it.isNotBlank() }
                ?: archiveFileName.removeSuffix(".mtz").removeSuffix(".mtx").replace("+", " "),
            author = descFields["author"] ?: "Xiaomi Theme Studio",
            designer = descFields["designer"] ?: descFields["author"] ?: "MIUI / HyperOS Designer",
            version = descFields["version"] ?: "1.0.0",
            uiVersion = descFields["uiVersion"] ?: "MIUI / HyperOS",
            description = descFields["description"] ?: "Xiaomi MTZ Icon Theme Package",
            archiveFileName = archiveFileName,
            extractedDirPath = extractedDir.absolutePath,
            iconScale = effectiveIconScale,
            iconPaddingDp = themeValues.iconPaddingDp,
            titleColorHex = themeValues.titleColorHex,
            iconBgPaths = iconBgPaths,
            iconMaskPath = iconMaskPath,
            iconBorderPath = iconBorderPath,
            iconPatternPath = iconPatternPath,
            iconShortcutPath = iconShortcutPath,
            iconFolderPath = iconFolderPath,
            folderCoverPath = folderCoverPath,
            symDefAppIconPath = symDefAppIconPath,
            directIconsMap = directIconsMap,
            iconRulesMap = iconRulesMap,
            fancyIconSpecs = fancyIconSpecs,
            wallpaperPath = wallpaperFile?.absolutePath,
            rawDescriptionXml = rawDescriptionXml,
            rawThemeValuesXml = rawThemeValuesXml,
            rawTransformConfigXml = rawTransformXml,
            rawIconRulesXml = rawRulesXml,
            isBuiltIn = isBuiltIn,
            importedAtMillis = importedAtMillis,
            hasTransformConfig = hasTransformConfig,
            filterBlackColorHex = effectiveBlackColorHex,
            filterWhiteColorHex = parsedTransform.whiteColorHex,
            filterLevelsInputMin = parsedTransform.levelsInputMin,
            filterLevelsInputMax = parsedTransform.levelsInputMax,
            filterLevelsOutputMin = parsedTransform.levelsOutputMin,
            filterLevelsOutputMax = parsedTransform.levelsOutputMax,
            filterLevelsGamma = parsedTransform.levelsGamma,
            filterColorMatrix = parsedTransform.colorMatrix,
            filterLightingMulHex = parsedTransform.lightingMulHex,
            filterLightingAddHex = parsedTransform.lightingAddHex,
            filterHslHue = parsedTransform.hslHue,
            filterHslSaturation = parsedTransform.hslSaturation,
            filterHslLightness = parsedTransform.hslLightness,
            filterSpreadRadius = parsedTransform.spreadRadius,
            filterBlurRadius = parsedTransform.blurRadius,
            filterSteps = parsedTransform.filterSteps,
            transformApplyToAllIcons = parsedTransform.applyToAllIcons,
            isCustomFilterPipeline = parsedTransform.isCustomFilterPipeline,
            pointsScaleX = if (hasTransformConfig) parsedTransform.pointsScaleX else effectiveIconScale,
            pointsScaleY = if (hasTransformConfig) parsedTransform.pointsScaleY else effectiveIconScale,
            pointsOffsetX = if (hasTransformConfig) parsedTransform.pointsOffsetX else (1f - effectiveIconScale) / 2f,
            pointsOffsetY = if (hasTransformConfig) parsedTransform.pointsOffsetY else (1f - effectiveIconScale) / 2f,
            pointsMappingSrc = parsedTransform.pointsMappingSrc,
            pointsMappingDst = parsedTransform.pointsMappingDst
        )
    }

    private fun densityPriorityOrder(path: String): Int {
        val p = path.lowercase()
        return when {
            p.contains("com_miui_home") -> 0
            p.contains("drawable-hdpi") -> 1
            p.contains("drawable-xhdpi") -> 2
            p.contains("drawable-xxhdpi") -> 3
            p.contains("drawable-xxxhdpi") -> 4
            else -> 2
        }
    }

    private fun sampleBgDominantHex(bgPath: String?): String? {
        if (bgPath == null) return null
        return try {
            val bmp = BitmapFactory.decodeFile(bgPath) ?: return null
            val cx = bmp.width / 2
            val cy = bmp.height / 2
            if (cx <= 0 || cy <= 0) return null
            val c = bmp.getPixel(cx, cy)
            val a = Color.alpha(c)
            if (a < 40) return null
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val delta = max(r, max(g, b)) - min(r, min(g, b))
            // Neutral gray template plates rely on #465967 fallback
            if (delta <= 8 && r in 100..160) return null
            String.format("#%02X%02X%02X", r, g, b)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseIconRulesXml(xmlContent: String): Map<String, String> {
        val rules = mutableMapOf<String, String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.equals("rule", ignoreCase = true)) {
                    val action = parser.getAttributeValue(null, "action")
                    val component = parser.getAttributeValue(null, "component")
                    val pkg = parser.getAttributeValue(null, "package")
                    val icon = parser.getAttributeValue(null, "icon")?.removeSuffix(".png")
                    if (icon != null) {
                        if (action != null) rules[action] = icon
                        if (component != null) rules[component] = icon
                        if (pkg != null) rules[pkg] = icon
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
        return rules
    }

    private fun parseDescriptionXml(xmlContent: String, fallbackTitle: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            var currentTag: String? = null
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> {
                        val tag = currentTag
                        val text = parser.text?.trim()
                        if (!tag.isNullOrEmpty() && !text.isNullOrEmpty()) {
                            result[tag] = text
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = null
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            result["title"] = fallbackTitle.removeSuffix(".mtz").replace("+", " ")
        }
        return result
    }

    private data class ParsedThemeValues(
        val iconScale: Float? = null,
        val iconPaddingDp: Float = 10f,
        val titleColorHex: String = "#ECF0F1"
    )

    private fun parseThemeValuesXml(xmlContent: String): ParsedThemeValues {
        var scale: Float? = null
        var padding = 10f
        var colorHex = "#ECF0F1"
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            var currentNameAttr: String? = null
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentNameAttr = parser.getAttributeValue(null, "name")
                    }
                    XmlPullParser.TEXT -> {
                        val value = parser.text?.trim().orEmpty()
                        if (value.isNotEmpty() && currentNameAttr != null) {
                            when (currentNameAttr) {
                                "icon_scale", "customize_icon_scale" -> {
                                    val cleanFloat = value.replace("dp", "").replace("px", "").replace("f", "").trim()
                                    scale = cleanFloat.toFloatOrNull()?.coerceIn(0.45f, 1.0f) ?: scale
                                }
                                "icon_padding" -> {
                                    val cleanFloat = value.replace("dp", "").replace("px", "").trim()
                                    padding = cleanFloat.toFloatOrNull()?.coerceIn(0f, 36f) ?: padding
                                }
                                "icon_title_color", "workspace_icon_text_color" -> {
                                    if (value.startsWith("#")) colorHex = value
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentNameAttr = null
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
        return ParsedThemeValues(scale, padding, colorHex)
    }

    internal data class ParsedTransformConfig(
        val pointsScaleX: Float = 0.78f,
        val pointsScaleY: Float = 0.78f,
        val pointsOffsetX: Float = 0.11f,
        val pointsOffsetY: Float = 0.11f,
        val pointsMappingSrc: FloatArray? = null,
        val pointsMappingDst: FloatArray? = null,
        val blackColorHex: String? = null,
        val whiteColorHex: String = "#ECF0F1",
        val levelsInputMin: Int = 95,
        val levelsInputMax: Int = 255,
        val levelsOutputMin: Int = 0,
        val levelsOutputMax: Int = 255,
        val levelsGamma: Float = 1.0f,
        val colorMatrix: FloatArray? = null,
        val lightingMulHex: String? = null,
        val lightingAddHex: String? = null,
        val hslHue: Float? = null,
        val hslSaturation: Float? = null,
        val hslLightness: Float? = null,
        val spreadRadius: Int = 0,
        val blurRadius: Int = 0,
        val filterSteps: List<MtzIconFilterStep> = emptyList(),
        val applyToAllIcons: Boolean = false,
        val isCustomFilterPipeline: Boolean = false
    )

    internal fun parseTransformConfigXml(xmlContent: String): ParsedTransformConfig {
        var minFromX = Float.MAX_VALUE
        var maxFromX = -Float.MAX_VALUE
        var minToX = Float.MAX_VALUE
        var maxToX = -Float.MAX_VALUE

        var minFromY = Float.MAX_VALUE
        var maxFromY = -Float.MAX_VALUE
        var minToY = Float.MAX_VALUE
        var maxToY = -Float.MAX_VALUE

        val rawSrcPoints = mutableListOf<Float>()
        val rawDstPoints = mutableListOf<Float>()

        var explicitScaleX: Float? = null
        var explicitScaleY: Float? = null
        var blackColorHex: String? = null
        var whiteColorHex = "#ECF0F1"
        var levelsInputMin = 95
        var levelsInputMax = 255
        var levelsOutputMin = 0
        var levelsOutputMax = 255
        var levelsGamma = 1.0f
        var colorMatrix: FloatArray? = null
        var lightingMulHex: String? = null
        var lightingAddHex: String? = null
        var hslHue: Float? = null
        var hslSaturation: Float? = null
        var hslLightness: Float? = null
        var spreadRadius = 0
        var blurRadius = 0
        var applyToAllIcons = false
        var explicitSequential = false
        val filterSteps = mutableListOf<MtzIconFilterStep>()

        var activeFilterName: String? = null
        val activeFilterParams = linkedMapOf<String, String>()

        fun consumePipelineAttributes(parser: XmlPullParser) {
            for (attrIdx in 0 until parser.attributeCount) {
                val attrName = parser.getAttributeName(attrIdx)?.lowercase() ?: continue
                val attrVal = parser.getAttributeValue(attrIdx)?.trim() ?: continue
                when (attrName) {
                    "scope", "target" -> {
                        if (attrVal.equals("all", true) || attrVal.equals("global", true)) {
                            applyToAllIcons = true
                        } else if (attrVal.equals("third_party", true) || attrVal.equals("fallback", true)) {
                            applyToAllIcons = false
                        }
                    }
                    "applytoall", "global" -> {
                        if (attrVal.equals("true", true) || attrVal == "1") {
                            applyToAllIcons = true
                        } else if (attrVal.equals("false", true) || attrVal == "0") {
                            applyToAllIcons = false
                        }
                    }
                    "sequential" -> {
                        if (attrVal.equals("true", true) || attrVal == "1") {
                            explicitSequential = true
                        }
                    }
                    "pipeline", "mode" -> {
                        if (attrVal.equals("custom", true) || attrVal.equals("sequential", true)) {
                            explicitSequential = true
                        }
                    }
                }
            }
        }

        fun consumeFilterParam(fName: String?, pName: String, pVal: String) {
            activeFilterParams[pName.lowercase()] = pVal
            when {
                pName.equals("PointsMapping", true) -> {
                    val coords = pVal.split(",").mapNotNull { it.trim().toFloatOrNull() }
                    if (coords.size >= 8) {
                        val xs = listOf(coords[0], coords[2], coords[4], coords[6])
                        val ys = listOf(coords[1], coords[3], coords[5], coords[7])
                        minToX = xs.minOrNull() ?: minToX
                        maxToX = xs.maxOrNull() ?: maxToX
                        minToY = ys.minOrNull() ?: minToY
                        maxToY = ys.maxOrNull() ?: maxToY
                        minFromX = 0f
                        maxFromX = 90f
                        minFromY = 0f
                        maxFromY = 90f
                        if (rawSrcPoints.isEmpty()) {
                            val dx1 = kotlin.math.abs(coords[2] - coords[0])
                            val dy1 = kotlin.math.abs(coords[3] - coords[1])
                            if (dx1 >= dy1) {
                                // Clockwise: Top-Left -> Top-Right -> Bottom-Right -> Bottom-Left (e.g. Valorant 17,17,73,17,73,73,17,73)
                                rawSrcPoints.addAll(listOf(0f, 0f, 90f, 0f, 90f, 90f, 0f, 90f))
                            } else {
                                // Counter-Clockwise: Top-Left -> Bottom-Left -> Bottom-Right -> Top-Right
                                rawSrcPoints.addAll(listOf(0f, 0f, 0f, 90f, 90f, 90f, 90f, 0f))
                            }
                            rawDstPoints.addAll(coords.take(8))
                        }
                    }
                }
                fName.equals("GrayScale", true) -> {
                    val cleanHex = normalizeMtzColorHex(pVal)
                    if (pName.equals("BlackColor", true) && cleanHex != null) {
                        blackColorHex = cleanHex
                    }
                    if (pName.equals("WhiteColor", true) && cleanHex != null) {
                        whiteColorHex = cleanHex
                    }
                }
                fName.equals("Levels", true) -> {
                    if (pName.equals("InputMin", true)) levelsInputMin = pVal.toIntOrNull()?.coerceIn(0, 240) ?: 95
                    if (pName.equals("InputMax", true)) levelsInputMax = pVal.toIntOrNull()?.coerceIn(10, 255) ?: 255
                    if (pName.equals("OutputMin", true)) levelsOutputMin = pVal.toIntOrNull()?.coerceIn(0, 255) ?: 0
                    if (pName.equals("OutputMax", true)) levelsOutputMax = pVal.toIntOrNull()?.coerceIn(0, 255) ?: 255
                    if (pName.equals("Gamma", true) || pName.equals("InputMiddle", true)) {
                        levelsGamma = pVal.toFloatOrNull()?.coerceIn(0.1f, 5.0f) ?: 1.0f
                    }
                }
                fName.equals("Spread", true) -> {
                    if (pName.equals("Radius", true) || pName.equals("Value", true)) {
                        spreadRadius = pVal.toFloatOrNull()?.toInt()?.coerceIn(-12, 12) ?: 1
                    }
                }
                fName.equals("GausBlur", true) || fName.equals("Blur", true) -> {
                    if (pName.equals("Radius", true) || pName.equals("Value", true)) {
                        blurRadius = pVal.toFloatOrNull()?.toInt()?.coerceIn(1, 25) ?: 1
                    }
                }
                fName.equals("ColorMatrix", true) && pName.equals("Matrix", true) -> {
                    colorMatrix = parseColorMatrixString(pVal)
                }
                fName.equals("Lighting", true) -> {
                    if (pName.equals("Mul", true)) lightingMulHex = pVal
                    if (pName.equals("Add", true)) lightingAddHex = pVal
                }
                fName.equals("Hsl", true) || fName.equals("HSB", true) -> {
                    if (pName.equals("Hue", true) || pName.equals("H", true) || pName.equals("HueModify", true)) {
                        hslHue = pVal.toFloatOrNull()
                    }
                    if (pName.equals("Saturation", true) || pName.equals("S", true) || pName.equals("SaturationModify", true)) {
                        hslSaturation = pVal.toFloatOrNull()
                    }
                    if (pName.equals("Lightness", true) || pName.equals("L", true) || pName.equals("B", true) || pName.equals("LightnessModify", true)) {
                        hslLightness = pVal.toFloatOrNull()
                    }
                }
            }
        }

        fun finalizeActiveFilter() {
            val fName = activeFilterName ?: return
            when {
                fName.equals("StripAdaptivePlate", true) || fName.equals("StripPlate", true) -> {
                    val normAttr = activeFilterParams["normalizebounds"] ?: activeFilterParams["normalize"]
                    val norm = normAttr == null || normAttr.equals("true", true) || normAttr == "1"
                    filterSteps.add(MtzIconFilterStep.StripAdaptivePlate(normalizeBounds = norm))
                }
                fName.equals("Edges", true) -> {
                    val thresh = (activeFilterParams["threshold"] ?: activeFilterParams["value"])
                        ?.toIntOrNull()?.coerceIn(1, 255) ?: 32
                    filterSteps.add(MtzIconFilterStep.Edges(threshold = thresh))
                }
                fName.equals("Spread", true) -> {
                    val r = activeFilterParams["radius"]?.toFloatOrNull()?.toInt()?.coerceIn(-12, 12)
                        ?: activeFilterParams["value"]?.toFloatOrNull()?.toInt()?.coerceIn(-12, 12)
                        ?: 1
                    filterSteps.add(MtzIconFilterStep.Spread(r))
                }
                fName.equals("GausBlur", true) || fName.equals("Blur", true) -> {
                    val r = activeFilterParams["radius"]?.toFloatOrNull()?.toInt()?.coerceIn(1, 25)
                        ?: activeFilterParams["value"]?.toFloatOrNull()?.toInt()?.coerceIn(1, 25)
                        ?: 1
                    filterSteps.add(MtzIconFilterStep.GausBlur(r))
                }
                fName.equals("GrayScale", true) -> {
                    val bHex = activeFilterParams["blackcolor"]?.let(::normalizeMtzColorHex) ?: (blackColorHex ?: "#000000")
                    val wHex = activeFilterParams["whitecolor"]?.let(::normalizeMtzColorHex) ?: whiteColorHex
                    val aVal = activeFilterParams["alpha"]?.toIntOrNull()?.coerceIn(0, 255) ?: 255
                    filterSteps.add(MtzIconFilterStep.GrayScale(bHex, wHex, aVal))
                }
                fName.equals("Levels", true) -> {
                    val inMin = activeFilterParams["inputmin"]?.toIntOrNull()?.coerceIn(0, 240) ?: 0
                    val inMid = (activeFilterParams["inputmiddle"] ?: activeFilterParams["gamma"])
                        ?.toFloatOrNull()?.coerceIn(0.1f, 5.0f) ?: 1.0f
                    val inMax = activeFilterParams["inputmax"]?.toIntOrNull()?.coerceIn(10, 255) ?: 255
                    val outMin = activeFilterParams["outputmin"]?.toIntOrNull()?.coerceIn(0, 255) ?: 0
                    val outMax = activeFilterParams["outputmax"]?.toIntOrNull()?.coerceIn(0, 255) ?: 255
                    filterSteps.add(MtzIconFilterStep.Levels(inMin, inMid, inMax, outMin, outMax))
                }
                fName.equals("Hsl", true) || fName.equals("HSB", true) -> {
                    val h = (activeFilterParams["hue"] ?: activeFilterParams["h"] ?: activeFilterParams["huemodify"])?.toFloatOrNull() ?: 0f
                    val s = (activeFilterParams["saturation"] ?: activeFilterParams["s"] ?: activeFilterParams["saturationmodify"])?.toFloatOrNull() ?: 0f
                    val l = (activeFilterParams["lightness"] ?: activeFilterParams["l"] ?: activeFilterParams["b"] ?: activeFilterParams["lightnessmodify"])?.toFloatOrNull() ?: 0f
                    filterSteps.add(MtzIconFilterStep.Hsl(h, s, l))
                }
                fName.equals("ColorMatrix", true) -> {
                    val mat = activeFilterParams["matrix"]?.let(::parseColorMatrixString)
                    if (mat != null) {
                        filterSteps.add(MtzIconFilterStep.ColorMatrixStep(mat))
                    }
                }
                fName.equals("Lighting", true) -> {
                    filterSteps.add(
                        MtzIconFilterStep.LightingStep(
                            mulHex = activeFilterParams["mul"],
                            addHex = activeFilterParams["add"]
                        )
                    )
                }
                fName.equals("Alpha", true) -> {
                    val a = (activeFilterParams["alpha"] ?: activeFilterParams["value"])?.toIntOrNull()?.coerceIn(0, 255) ?: 255
                    filterSteps.add(MtzIconFilterStep.AlphaStep(a))
                }
                fName.equals("PointsMapping", true) -> {
                    val step = parsePointsMappingFilterStep(activeFilterParams)
                    if (step != null) {
                        filterSteps.add(step)
                    }
                }
                fName.equals("ColorSplash", true) || fName.equals("SplashColor", true) -> {
                    filterSteps.add(parseColorSplashStep(activeFilterParams))
                }
                fName.equals("Duotone", true) || fName.equals("GradientMap", true) -> {
                    val shadow = (activeFilterParams["shadowcolor"] ?: activeFilterParams["darkcolor"] ?: activeFilterParams["blackcolor"])
                        ?.let(::normalizeMtzColorHex) ?: "#1B1464"
                    val mid = (activeFilterParams["midcolor"] ?: activeFilterParams["middlecolor"])
                        ?.let(::normalizeMtzColorHex)
                    val highlight = (activeFilterParams["highlightcolor"] ?: activeFilterParams["lightcolor"] ?: activeFilterParams["whitecolor"])
                        ?.let(::normalizeMtzColorHex) ?: "#00F5D4"
                    val rawIntensity = (activeFilterParams["intensity"] ?: activeFilterParams["blend"] ?: activeFilterParams["alpha"])
                        ?.toFloatOrNull() ?: 1.0f
                    val intensity = if (rawIntensity > 1.0f) (rawIntensity / 255f).coerceIn(0f, 1f) else rawIntensity.coerceIn(0f, 1f)
                    filterSteps.add(MtzIconFilterStep.Duotone(shadow, mid, highlight, intensity))
                }
                fName.equals("Adjust", true) || fName.equals("ColorAdjust", true) ||
                    fName.equals("Contrast", true) || fName.equals("Brightness", true) -> {
                    val hue = (activeFilterParams["hue"] ?: activeFilterParams["hueshift"] ?: activeFilterParams["huemodify"])
                        ?.toFloatOrNull()?.coerceIn(-180f, 180f) ?: 0f
                    val sat = (activeFilterParams["saturation"] ?: activeFilterParams["sat"] ?: activeFilterParams["saturationscale"])
                        ?.toFloatOrNull()?.coerceIn(0f, 3f) ?: 1.0f
                    val contrastRaw = activeFilterParams["contrast"]
                        ?: if (fName.equals("Contrast", true)) activeFilterParams["value"] else null
                    val contrast = contrastRaw?.toFloatOrNull()?.coerceIn(0.2f, 3.0f) ?: 1.0f
                    val brightRaw = activeFilterParams["brightness"]
                        ?: if (fName.equals("Brightness", true)) activeFilterParams["value"] else null
                    val bFloat = brightRaw?.toFloatOrNull() ?: 0f
                    val brightness = if (kotlin.math.abs(bFloat) > 1.0f) (bFloat / 255f).coerceIn(-1f, 1f) else bFloat.coerceIn(-1f, 1f)
                    filterSteps.add(MtzIconFilterStep.ColorAdjust(hue, sat, contrast, brightness))
                }
                fName.equals("EdgeGlow", true) || fName.equals("NeonOutline", true) || fName.equals("SobelEdge", true) -> {
                    val edgeHex = (activeFilterParams["edgecolor"] ?: activeFilterParams["color"] ?: activeFilterParams["outlinecolor"])
                        ?.let(::normalizeMtzColorHex) ?: "#00F5D4"
                    val baseHex = (activeFilterParams["basecolor"] ?: activeFilterParams["backgroundcolor"] ?: activeFilterParams["fillcolor"])
                        ?.let(::normalizeMtzColorHex) ?: "#00000000"
                    val threshold = (activeFilterParams["threshold"] ?: activeFilterParams["value"])
                        ?.toIntOrNull()?.coerceIn(8, 180) ?: 36
                    val fillAlphaRaw = (activeFilterParams["fillalpha"] ?: activeFilterParams["originalalpha"] ?: activeFilterParams["keeporiginal"])
                        ?.toFloatOrNull() ?: 0.0f
                    val fillAlpha = if (fillAlphaRaw > 1.0f) (fillAlphaRaw / 255f).coerceIn(0f, 1f) else fillAlphaRaw.coerceIn(0f, 1f)
                    val glowRadius = (activeFilterParams["glowradius"] ?: activeFilterParams["radius"])
                        ?.toIntOrNull()?.coerceIn(0, 12) ?: 1
                    filterSteps.add(MtzIconFilterStep.EdgeGlow(edgeHex, baseHex, threshold, fillAlpha, glowRadius))
                }
                fName.equals("Posterize", true) || fName.equals("Quantize", true) -> {
                    val lvls = (activeFilterParams["levels"] ?: activeFilterParams["value"])
                        ?.toIntOrNull()?.coerceIn(2, 16) ?: 4
                    filterSteps.add(MtzIconFilterStep.Posterize(lvls))
                }
                fName.equals("Sepia", true) -> {
                    val rawInt = (activeFilterParams["intensity"] ?: activeFilterParams["value"])?.toFloatOrNull() ?: 1.0f
                    val intensity = if (rawInt > 1.0f) (rawInt / 255f).coerceIn(0f, 1f) else rawInt.coerceIn(0f, 1f)
                    filterSteps.add(MtzIconFilterStep.Sepia(intensity))
                }
                fName.equals("Invert", true) || fName.equals("Negative", true) -> {
                    val rawInt = (activeFilterParams["intensity"] ?: activeFilterParams["value"])?.toFloatOrNull() ?: 1.0f
                    val intensity = if (rawInt > 1.0f) (rawInt / 255f).coerceIn(0f, 1f) else rawInt.coerceIn(0f, 1f)
                    filterSteps.add(MtzIconFilterStep.Invert(intensity))
                }
                fName.equals("Monochrome", true) || fName.equals("Tint", true) -> {
                    val cHex = (activeFilterParams["color"] ?: activeFilterParams["tintcolor"] ?: activeFilterParams["value"])
                        ?.let(::normalizeMtzColorHex) ?: "#ECF0F1"
                    val rawInt = (activeFilterParams["intensity"] ?: activeFilterParams["alpha"])?.toFloatOrNull() ?: 1.0f
                    val intensity = if (rawInt > 1.0f) (rawInt / 255f).coerceIn(0f, 1f) else rawInt.coerceIn(0f, 1f)
                    filterSteps.add(MtzIconFilterStep.Monochrome(cHex, intensity))
                }
            }
            activeFilterName = null
            activeFilterParams.clear()
        }

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "IconTransform", "IconFilters" -> {
                                consumePipelineAttributes(parser)
                            }
                            "Point" -> {
                                val fx = parser.getAttributeValue(null, "fromX")?.toFloatOrNull()
                                val tx = parser.getAttributeValue(null, "toX")?.toFloatOrNull()
                                val fy = parser.getAttributeValue(null, "fromY")?.toFloatOrNull()
                                val ty = parser.getAttributeValue(null, "toY")?.toFloatOrNull()
                                if (fx != null && tx != null) {
                                    minFromX = min(minFromX, fx)
                                    maxFromX = max(maxFromX, fx)
                                    minToX = min(minToX, tx)
                                    maxToX = max(maxToX, tx)
                                }
                                if (fy != null && ty != null) {
                                    minFromY = min(minFromY, fy)
                                    maxFromY = max(maxFromY, fy)
                                    minToY = min(minToY, ty)
                                    maxToY = max(maxToY, ty)
                                }
                                if (fx != null && fy != null && tx != null && ty != null && rawSrcPoints.size < 8) {
                                    rawSrcPoints.add(fx)
                                    rawSrcPoints.add(fy)
                                    rawDstPoints.add(tx)
                                    rawDstPoints.add(ty)
                                }
                            }
                            "Scale", "IconMatrix" -> {
                                val s = parser.getAttributeValue(null, "value")?.toFloatOrNull()
                                val sx = parser.getAttributeValue(null, "scaleX")?.toFloatOrNull() ?: s
                                val sy = parser.getAttributeValue(null, "scaleY")?.toFloatOrNull() ?: s
                                if (sx != null) explicitScaleX = sx.coerceIn(0.45f, 1.0f)
                                if (sy != null) explicitScaleY = sy.coerceIn(0.45f, 1.0f)
                            }
                            "Filter" -> {
                                activeFilterName = parser.getAttributeValue(null, "name")
                                activeFilterParams.clear()
                                // Also read inline attributes directly on <Filter name="..." BlackColor="..." InputMin="..." Radius="..." />
                                for (attrIdx in 0 until parser.attributeCount) {
                                    val attrName = parser.getAttributeName(attrIdx)
                                    val attrVal = parser.getAttributeValue(attrIdx)
                                    if (!attrName.equals("name", ignoreCase = true) && attrName != null && attrVal != null) {
                                        consumeFilterParam(activeFilterName, attrName, attrVal)
                                    }
                                }
                            }
                            "Param" -> {
                                val pName = parser.getAttributeValue(null, "name")
                                val pVal = parser.getAttributeValue(null, "value")
                                if (pName != null && pVal != null) {
                                    consumeFilterParam(activeFilterName, pName, pVal)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Filter") {
                            finalizeActiveFilter()
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }

        val computedScaleX = if (maxFromX > minFromX && maxToX > minToX) {
            ((maxToX - minToX) / (maxFromX - minFromX)).coerceIn(0.45f, 1.0f)
        } else {
            explicitScaleX ?: 0.78f
        }

        val computedScaleY = if (maxFromY > minFromY && maxToY > minToY) {
            ((maxToY - minToY) / (maxFromY - minFromY)).coerceIn(0.45f, 1.0f)
        } else {
            explicitScaleY ?: computedScaleX
        }

        val computedOffsetX = if (maxFromX > minFromX) (minToX / (maxFromX - minFromX)).coerceIn(0f, 0.4f) else (1f - computedScaleX) / 2f
        val computedOffsetY = if (maxFromY > minFromY) (minToY / (maxFromY - minFromY)).coerceIn(0f, 0.4f) else (1f - computedScaleY) / 2f

        val coordRefW = if (maxFromX > minFromX) (maxFromX - minFromX) else 90f
        val coordRefH = if (maxFromY > minFromY) (maxFromY - minFromY) else 90f
        val normalizedSrcPoints = if (rawSrcPoints.size == 8 && coordRefW > 0f && coordRefH > 0f) {
            FloatArray(8) { i ->
                if (i % 2 == 0) (rawSrcPoints[i] - minFromX) / coordRefW else (rawSrcPoints[i] - minFromY) / coordRefH
            }
        } else {
            null
        }
        val normalizedDstPoints = if (rawDstPoints.size == 8 && coordRefW > 0f && coordRefH > 0f) {
            FloatArray(8) { i ->
                if (i % 2 == 0) (rawDstPoints[i] - minFromX) / coordRefW else (rawDstPoints[i] - minFromY) / coordRefH
            }
        } else {
            null
        }

        val isCustomFilterPipeline = !isStandardStockMiuiPipeline(filterSteps, explicitSequential)

        return ParsedTransformConfig(
            pointsScaleX = computedScaleX,
            pointsScaleY = computedScaleY,
            pointsOffsetX = computedOffsetX,
            pointsOffsetY = computedOffsetY,
            pointsMappingSrc = normalizedSrcPoints,
            pointsMappingDst = normalizedDstPoints,
            blackColorHex = blackColorHex,
            whiteColorHex = whiteColorHex,
            levelsInputMin = levelsInputMin,
            levelsInputMax = levelsInputMax,
            levelsOutputMin = levelsOutputMin,
            levelsOutputMax = levelsOutputMax,
            levelsGamma = levelsGamma,
            colorMatrix = colorMatrix,
            lightingMulHex = lightingMulHex,
            lightingAddHex = lightingAddHex,
            hslHue = hslHue,
            hslSaturation = hslSaturation,
            hslLightness = hslLightness,
            spreadRadius = spreadRadius,
            blurRadius = blurRadius,
            filterSteps = filterSteps,
            applyToAllIcons = applyToAllIcons,
            isCustomFilterPipeline = isCustomFilterPipeline
        )
    }

    private fun isStandardStockMiuiPipeline(
        steps: List<MtzIconFilterStep>,
        explicitSequential: Boolean
    ): Boolean {
        if (explicitSequential) return false
        if (steps.isEmpty()) return true

        val grayScaleCount = steps.count { it is MtzIconFilterStep.GrayScale }
        if (grayScaleCount == 0) return false

        val levelsCount = steps.count { it is MtzIconFilterStep.Levels }
        if (levelsCount > 1) return false

        val edgesSteps = steps.filterIsInstance<MtzIconFilterStep.Edges>()
        if (edgesSteps.size > 1) return false
        if (edgesSteps.any { it.threshold != 32 }) return false

        val hasExtendedStep = steps.any { step ->
            step is MtzIconFilterStep.StripAdaptivePlate ||
                step is MtzIconFilterStep.PointsMappingStep ||
                step is MtzIconFilterStep.ColorSplash ||
                step is MtzIconFilterStep.Duotone ||
                step is MtzIconFilterStep.ColorAdjust ||
                step is MtzIconFilterStep.EdgeGlow ||
                step is MtzIconFilterStep.Posterize ||
                step is MtzIconFilterStep.Sepia ||
                step is MtzIconFilterStep.Invert ||
                step is MtzIconFilterStep.Monochrome
        }
        if (hasExtendedStep) return false

        val grayScaleIdx = steps.indexOfFirst { it is MtzIconFilterStep.GrayScale }
        val levelsIdx = steps.indexOfFirst { it is MtzIconFilterStep.Levels }
        val edgesIdx = steps.indexOfFirst { it is MtzIconFilterStep.Edges }

        if (edgesIdx != -1 && edgesIdx > grayScaleIdx) return false
        if (levelsIdx != -1 && levelsIdx < grayScaleIdx) return false

        val firstCoreIdx = if (edgesIdx != -1) min(edgesIdx, grayScaleIdx) else grayScaleIdx
        for ((idx, step) in steps.withIndex()) {
            if (step is MtzIconFilterStep.Hsl ||
                step is MtzIconFilterStep.ColorMatrixStep ||
                step is MtzIconFilterStep.LightingStep
            ) {
                if (idx > firstCoreIdx) return false
            }
        }

        return true
    }

    private fun parseColorSplashStep(params: Map<String, String>): MtzIconFilterStep.ColorSplash {
        val hues = mutableListOf<Float>()
        val rawTokens = (params["colors"] ?: params["targetcolors"] ?: params["hues"] ?: params["swatch"]).orEmpty()
        if (rawTokens.isNotBlank()) {
            val parts = rawTokens.split(",", "|", ";", " ")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            for (token in parts) {
                val hue = parseHueToken(token)
                if (hue != null) hues.add(hue)
            }
        }
        val customHue = (params["customhue"] ?: params["hue"])?.let(::parseHueToken)
        if (customHue != null) {
            hues.add(customHue)
        }
        if (hues.isEmpty()) {
            hues.add(0f)
        }
        val tolerance = (params["tolerance"] ?: params["huetolerance"])
            ?.toFloatOrNull()?.coerceIn(5f, 90f) ?: 24f
        val minSat = (params["minsaturation"] ?: params["minsat"])
            ?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.15f
        val modeRaw = (params["mode"] ?: "").trim()
        val mode = when {
            modeRaw.equals("TINT_SPLASH", true) ||
                modeRaw.equals("TINT", true) ||
                modeRaw.equals("RECOLOR", true) -> ColorSplashMode.TINT_SPLASH
            else -> ColorSplashMode.KEEP_ORIGINAL
        }
        val invRaw = (params["invert"] ?: params["invertselection"]).orEmpty().trim()
        val invert = invRaw.equals("true", true) || invRaw == "1"
        val splashTint = (params["splashtint"] ?: params["tintsplash"] ?: params["splashcolor"])
            ?.let(::normalizeMtzColorHex) ?: "#FF3B30"
        val restTintRaw = (params["resttint"] ?: params["tintrest"] ?: params["backgroundtint"] ?: params["bwtint"])?.trim()
        val restTint = if (restTintRaw.isNullOrBlank() || restTintRaw.equals("none", true) || restTintRaw.equals("bw", true)) {
            null
        } else {
            normalizeMtzColorHex(restTintRaw)
        }
        return MtzIconFilterStep.ColorSplash(
            targetHues = hues.toFloatArray(),
            toleranceDegrees = tolerance,
            minSaturation = minSat,
            mode = mode,
            invertSelection = invert,
            splashTintHex = splashTint,
            restTintHex = restTint
        )
    }

    private fun parseHueToken(token: String): Float? {
        val clean = token.trim().lowercase()
        when (clean) {
            "red", "crimson", "scarlet" -> return 0f
            "orange", "amber", "gold" -> return 30f
            "yellow", "lemon" -> return 60f
            "lime", "chartreuse" -> return 90f
            "green", "emerald" -> return 120f
            "teal", "turquoise", "mint" -> return 165f
            "cyan", "aqua", "sky" -> return 185f
            "blue", "azure", "indigo", "navy" -> return 220f
            "purple", "violet", "lavender" -> return 275f
            "magenta", "fuchsia" -> return 300f
            "pink", "rose", "coral" -> return 335f
        }
        if (clean.startsWith("#")) {
            val normHex = normalizeMtzColorHex(clean) ?: return null
            return try {
                val c = Color.parseColor(normHex)
                val hsv = FloatArray(3)
                Color.colorToHSV(c, hsv)
                ((hsv[0] % 360f) + 360f) % 360f
            } catch (_: Exception) {
                null
            }
        }
        val num = clean.removeSuffix("deg").trim().toFloatOrNull() ?: return null
        return ((num % 360f) + 360f) % 360f
    }

    private fun parsePointsMappingFilterStep(params: Map<String, String>): MtzIconFilterStep.PointsMappingStep? {
        val toRaw = params["pointsmapping"] ?: params["points"] ?: params["to"]
        if (!toRaw.isNullOrBlank()) {
            val coords = toRaw.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (coords.size >= 8) {
                val fromRaw = params["from"]
                val fromCoords = fromRaw?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
                val isNormalized = coords.all { it in -2f..2f } && coords.maxOrNull() ?: 0f <= 2f
                val refSpan = if (isNormalized) 1f else 90f
                val srcNorm = if (fromCoords != null && fromCoords.size >= 8) {
                    FloatArray(8) { i -> fromCoords[i] / refSpan }
                } else {
                    val dx1 = kotlin.math.abs(coords[2] - coords[0])
                    val dy1 = kotlin.math.abs(coords[3] - coords[1])
                    if (dx1 >= dy1) {
                        floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
                    } else {
                        floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f)
                    }
                }
                val dstNorm = FloatArray(8) { i -> coords[i] / refSpan }
                return MtzIconFilterStep.PointsMappingStep(srcNorm, dstNorm)
            }
        }
        val scale = params["scale"]?.toFloatOrNull()
        val sx = (params["scalex"]?.toFloatOrNull() ?: scale)?.coerceIn(0.25f, 1.5f)
        val sy = (params["scaley"]?.toFloatOrNull() ?: scale)?.coerceIn(0.25f, 1.5f)
        if (sx != null && sy != null) {
            val ox = (1f - sx) / 2f
            val oy = (1f - sy) / 2f
            val srcNorm = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
            val dstNorm = floatArrayOf(ox, oy, ox + sx, oy, ox + sx, oy + sy, ox, oy + sy)
            return MtzIconFilterStep.PointsMappingStep(srcNorm, dstNorm)
        }
        return null
    }

    private fun normalizeMtzColorHex(raw: String): String? {
        val single = raw.split("|").firstOrNull()?.trim() ?: raw.trim()
        if (!single.startsWith("#")) return null
        return if (single.length == 9) {
            val alpha = single.substring(1, 3)
            val rgb = single.substring(3).uppercase()
            if (alpha.equals("00", ignoreCase = true)) {
                "#00000000"
            } else {
                "#$rgb"
            }
        } else {
            single.uppercase()
        }
    }

    private fun parseColorMatrixString(raw: String): FloatArray? {
        return try {
            val tokens = raw.replace("\n", " ").replace(",", " ").trim().split("\\s+".toRegex())
            if (tokens.size >= 20) {
                FloatArray(20) { i -> tokens[i].toFloat() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFancyIconManifest(
        targetPackage: String,
        directoryPath: String,
        xmlContent: String
    ): MamlFancyIconSpec {
        var width = 192
        var height = 192
        var frameRate = 30
        val layers = mutableListOf<MamlLayerSpec>()
        val variables = linkedMapOf<String, MamlVarAnimation>()

        var currentVarName: String? = null
        val currentVarFrames = mutableListOf<MamlAniFrame>()
        var currentEaseType = "Linear"

        val safeXml = deduplicateXmlTagAttributes(xmlContent)

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(safeXml))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "Icon" -> {
                                width = parser.getAttributeValue(null, "width")?.toIntOrNull()
                                    ?: parser.getAttributeValue(null, "defaultWidth")?.toIntOrNull()
                                    ?: 192
                                height = parser.getAttributeValue(null, "height")?.toIntOrNull()
                                    ?: parser.getAttributeValue(null, "defaultHeight")?.toIntOrNull()
                                    ?: 192
                                frameRate = parser.getAttributeValue(null, "frameRate")?.toIntOrNull() ?: 30
                            }
                            "Var" -> {
                                currentVarName = parser.getAttributeValue(null, "name")
                                currentVarFrames.clear()
                                currentEaseType = "Linear"
                            }
                            "AniFrame" -> {
                                if (currentVarName != null) {
                                    val v = parser.getAttributeValue(null, "value")?.toFloatOrNull() ?: 0f
                                    val t = parser.getAttributeValue(null, "time")?.toIntOrNull() ?: 0
                                    val easeAttr = parser.getAttributeValue(null, "easeType")
                                    if (!easeAttr.isNullOrBlank()) {
                                        currentEaseType = easeAttr
                                    }
                                    currentVarFrames.add(MamlAniFrame(value = v, timeMs = t, easeType = currentEaseType))
                                }
                            }
                            "Group" -> {
                                val wExpr = parser.getAttributeValue(null, "w")
                                val hExpr = parser.getAttributeValue(null, "h")
                                val clip = parser.getAttributeValue(null, "clip").equals("true", ignoreCase = true)
                                val layered = parser.getAttributeValue(null, "layered").equals("true", ignoreCase = true)
                                val xExpr = parser.getAttributeValue(null, "x")
                                val yExpr = parser.getAttributeValue(null, "y")
                                layers.add(
                                    MamlLayerSpec(
                                        name = "group_start",
                                        layerType = "GROUP_START",
                                        xExpression = xExpr,
                                        yExpression = yExpr,
                                        widthExpression = wExpr,
                                        heightExpression = hExpr,
                                        groupClip = clip,
                                        groupLayered = layered
                                    )
                                )
                            }
                            "Image" -> {
                                val src = parser.getAttributeValue(null, "src")
                                val name = parser.getAttributeValue(null, "name") ?: src ?: "layer"
                                val xExpr = parser.getAttributeValue(null, "x")
                                val yExpr = parser.getAttributeValue(null, "y")
                                val pivotXExpr = parser.getAttributeValue(null, "pivotX")
                                val pivotYExpr = parser.getAttributeValue(null, "pivotY")
                                val scaleExpr = parser.getAttributeValue(null, "scale")
                                val scaleXExpr = parser.getAttributeValue(null, "scaleX")
                                val scaleYExpr = parser.getAttributeValue(null, "scaleY")
                                val rotation = parser.getAttributeValue(null, "rotation")
                                val alphaExpr = parser.getAttributeValue(null, "alpha")
                                val xfermode = parser.getAttributeValue(null, "xfermode")
                                val alignV = parser.getAttributeValue(null, "alignV") ?: "center"
                                val align = parser.getAttributeValue(null, "align") ?: "center"
                                val x = xExpr?.toFloatOrNull() ?: (width / 2f)
                                val y = yExpr?.toFloatOrNull() ?: (height / 2f)
                                val pivotX = pivotXExpr?.toFloatOrNull() ?: (width / 2f)
                                val pivotY = pivotYExpr?.toFloatOrNull() ?: (height / 2f)
                                val alpha = alphaExpr?.toIntOrNull()?.coerceIn(0, 255) ?: 255
                                layers.add(
                                    MamlLayerSpec(
                                        name = name,
                                        layerType = "IMAGE",
                                        imageFileName = src,
                                        centerX = x,
                                        centerY = y,
                                        pivotX = pivotX,
                                        pivotY = pivotY,
                                        xExpression = xExpr,
                                        yExpression = yExpr,
                                        pivotXExpression = pivotXExpr,
                                        pivotYExpression = pivotYExpr,
                                        scaleExpression = scaleExpr,
                                        scaleXExpression = scaleXExpr,
                                        scaleYExpression = scaleYExpr,
                                        rotationExpression = rotation,
                                        alphaExpression = alphaExpr,
                                        alignVCenter = alignV.equals("center", true),
                                        alignHCenter = align.equals("center", true),
                                        alignAttr = align,
                                        alignVAttr = alignV,
                                        alpha = alpha,
                                        xfermode = xfermode
                                    )
                                )
                            }
                            "Rectangle" -> {
                                val name = parser.getAttributeValue(null, "name") ?: "rect_layer"
                                val xExpr = parser.getAttributeValue(null, "x")
                                val yExpr = parser.getAttributeValue(null, "y")
                                val wExpr = parser.getAttributeValue(null, "w")
                                val hExpr = parser.getAttributeValue(null, "h")
                                val pivotXExpr = parser.getAttributeValue(null, "pivotX")
                                val pivotYExpr = parser.getAttributeValue(null, "pivotY")
                                val rotation = parser.getAttributeValue(null, "rotation")
                                val alphaExpr = parser.getAttributeValue(null, "alpha")
                                val cornerRadius = parser.getAttributeValue(null, "cornerRadius")?.toFloatOrNull() ?: 0f
                                val fillColor = parser.getAttributeValue(null, "fillColor") ?: "#000000"
                                val align = parser.getAttributeValue(null, "align") ?: "left"
                                val alignV = parser.getAttributeValue(null, "alignV") ?: "top"
                                val x = xExpr?.toFloatOrNull() ?: (width / 2f)
                                val y = yExpr?.toFloatOrNull() ?: (height / 2f)
                                val pivotX = pivotXExpr?.toFloatOrNull() ?: 0f
                                val pivotY = pivotYExpr?.toFloatOrNull() ?: 0f
                                layers.add(
                                    MamlLayerSpec(
                                        name = name,
                                        layerType = "RECTANGLE",
                                        centerX = x,
                                        centerY = y,
                                        pivotX = pivotX,
                                        pivotY = pivotY,
                                        xExpression = xExpr,
                                        yExpression = yExpr,
                                        pivotXExpression = pivotXExpr,
                                        pivotYExpression = pivotYExpr,
                                        widthExpression = wExpr,
                                        heightExpression = hExpr,
                                        rotationExpression = rotation,
                                        alphaExpression = alphaExpr,
                                        alignVCenter = alignV.equals("center", true),
                                        alignHCenter = align.equals("center", true),
                                        alignAttr = align,
                                        alignVAttr = alignV,
                                        cornerRadius = cornerRadius,
                                        fillColorHex = fillColor
                                    )
                                )
                            }
                            "DateTime", "Text" -> {
                                val isDateTime = parser.name.equals("DateTime", ignoreCase = true)
                                val name = parser.getAttributeValue(null, "name") ?: "text_layer"
                                val xExpr = parser.getAttributeValue(null, "x")
                                val yExpr = parser.getAttributeValue(null, "y")
                                val alphaExpr = parser.getAttributeValue(null, "alpha")
                                val x = xExpr?.toFloatOrNull() ?: (width / 2f)
                                val y = yExpr?.toFloatOrNull() ?: (height / 2f)
                                val exp = parser.getAttributeValue(null, "format")
                                    ?: parser.getAttributeValue(null, "textExp")
                                    ?: parser.getAttributeValue(null, "exp")
                                    ?: parser.getAttributeValue(null, "text")
                                    ?: "#date"
                                val color = parser.getAttributeValue(null, "color") ?: "#ECF0F1"
                                val size = parser.getAttributeValue(null, "size")?.toFloatOrNull() ?: 44f
                                val alignV = parser.getAttributeValue(null, "alignV") ?: "top"
                                val align = parser.getAttributeValue(null, "align") ?: "center"
                                layers.add(
                                    MamlLayerSpec(
                                        name = name,
                                        layerType = if (isDateTime) "DATETIME" else "TEXT",
                                        imageFileName = null,
                                        centerX = x,
                                        centerY = y,
                                        xExpression = xExpr,
                                        yExpression = yExpr,
                                        alphaExpression = alphaExpr,
                                        textExpression = exp,
                                        textColorHex = color,
                                        textSizePx = size,
                                        alignVCenter = alignV.equals("center", true),
                                        alignHCenter = align.equals("center", true),
                                        alignAttr = align,
                                        alignVAttr = alignV
                                    )
                                )
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "Var" -> {
                                val vName = currentVarName
                                if (!vName.isNullOrBlank() && currentVarFrames.isNotEmpty()) {
                                    variables[vName] = MamlVarAnimation(
                                        name = vName,
                                        frames = currentVarFrames.toList()
                                    )
                                }
                                currentVarName = null
                                currentVarFrames.clear()
                            }
                            "Group" -> {
                                layers.add(
                                    MamlLayerSpec(
                                        name = "group_end",
                                        layerType = "GROUP_END"
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }

        val dir = File(directoryPath)
        val quietFile = File(dir, "quietImage.png").takeIf { it.isFile }
        val startFile = File(dir, "startImage.png").takeIf { it.isFile }
        val lowerPkg = targetPackage.lowercase()
        val isLive = lowerPkg.contains("clock") ||
            lowerPkg.contains("calendar") ||
            lowerPkg.contains("weather") ||
            layers.any { it.layerType == "RECTANGLE" || it.layerType == "DATETIME" || it.layerType == "TEXT" }

        return MamlFancyIconSpec(
            targetPackage = targetPackage,
            directoryPath = directoryPath,
            width = width,
            height = height,
            frameRate = frameRate,
            layers = layers,
            rawManifestXml = xmlContent,
            variables = variables,
            quietImagePath = quietFile?.absolutePath,
            startImagePath = startFile?.absolutePath,
            isLiveClockOrCalendar = isLive
        )
    }

    /**
     * Removes duplicate attributes within individual XML opening tags (keeping the last occurrence)
     * so `XmlPullParser` does not abort on hand-authored MAML manifests (e.g. duplicate `alpha` or `pivotX`).
     */
    private fun deduplicateXmlTagAttributes(xml: String): String {
        val tagRegex = Regex("<([A-Za-z_][A-Za-z0-9_.:-]*)(\\s+[^>]*?)?(/?>)")
        val attrRegex = Regex("([A-Za-z_][A-Za-z0-9_.:-]*)\\s*=\\s*(\"[^\"]*\"|'[^']*')")
        return tagRegex.replace(xml) { match ->
            val tagName = match.groupValues[1]
            val attrBlock = match.groupValues[2]
            val closeToken = match.groupValues[3]
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
                        append(closeToken)
                    }
                }
            }
        }
    }

    private fun defaultDescriptionXml(fileName: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <MIUI-Theme>
            <title>${fileName.removeSuffix(".mtz").replace("+", " ")}</title>
            <author>Custom MTZ Archive</author>
            <designer>Xiaomi Theme Developer</designer>
            <version>1.0</version>
            <uiVersion>MIUI / HyperOS</uiVersion>
        </MIUI-Theme>
    """.trimIndent()

    private fun defaultThemeValuesXml() = """
        <?xml version="1.0" encoding="utf-8"?>
        <MIUI_Theme_Values>
            <dimen name="icon_scale">0.78</dimen>
            <dimen name="icon_padding">10dp</dimen>
            <color name="icon_title_color">#ECF0F1</color>
        </MIUI_Theme_Values>
    """.trimIndent()

    private fun defaultTransformConfigXml(scale: Float): String {
        val inset = ((1f - scale) * 45f).coerceIn(0f, 25f)
        val maxCoord = 90f - inset
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <IconTransform>
                <PointsMapping>
                    <Point fromX="0.0" fromY="0.0" toX="$inset" toY="$inset" />
                    <Point fromX="0.0" fromY="90.0" toX="$inset" toY="$maxCoord" />
                    <Point fromX="90.0" fromY="90.0" toX="$maxCoord" toY="$maxCoord" />
                    <Point fromX="90.0" fromY="0.0" toX="$maxCoord" toY="$inset" />
                </PointsMapping>
                <IconFilters>
                    <Filter name="GrayScale">
                        <Param name="BlackColor" value="#465967" />
                    </Filter>
                    <Filter name="Levels">
                        <Param name="InputMin" value="95" />
                    </Filter>
                </IconFilters>
            </IconTransform>
        """.trimIndent()
    }
}
