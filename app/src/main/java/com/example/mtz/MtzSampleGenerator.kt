package com.example.mtz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Provides the default MIUI `.mtz` archive (`Midnight_Blue.mtz`) from bundled assets,
 * with a minimal fallback archive builder if assets are unavailable in unit tests.
 */
class MtzSampleGenerator(private val context: Context) {

    data class BuiltInMtzSpec(
        val id: String,
        val fileName: String,
        val title: String,
        val author: String,
        val version: String,
        val uiVersion: String,
        val description: String,
        val iconScale: Float,
        val bgHex: String,
        val borderRingHex: String,
        val bundledAssetName: String
    )

    fun getBuiltInSpecs(): List<BuiltInMtzSpec> = listOf(
        BuiltInMtzSpec(
            id = "builtin_midnight_blue",
            fileName = "Midnight_Blue.mtz",
            title = "Midnight Blue",
            author = "MIUI Theme Engine",
            version = "1.0.0",
            uiVersion = "MIUI / HyperOS",
            description = "MIUI icon theme with direct package icons and <IconTransform> dynamic shading for 3rd-party apps.",
            iconScale = 0.78f,
            bgHex = "#465967",
            borderRingHex = "#43515E",
            bundledAssetName = "Midnight_Blue.mtz"
        )
    )

    fun ensureSingleMtzArchiveCreated(spec: BuiltInMtzSpec): File {
        val sampleDir = File(context.filesDir, "sample_mtz_archives")
        if (!sampleDir.exists()) sampleDir.mkdirs()
        val mtzFile = File(sampleDir, spec.fileName)
        val verFile = File(sampleDir, "${spec.id}_${spec.version}.ver")
        if (!mtzFile.exists() || mtzFile.length() == 0L || !verFile.exists()) {
            val copied = runCatching {
                context.assets.open(spec.bundledAssetName).use { input ->
                    FileOutputStream(mtzFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }.getOrDefault(false)
            if (!copied || mtzFile.length() == 0L) {
                buildFallbackMtzArchive(spec, mtzFile)
            }
            runCatching { verFile.writeText(spec.version) }
        }
        return mtzFile
    }

    fun ensureSampleMtzArchivesCreated(): Map<String, File> {
        val result = mutableMapOf<String, File>()
        for (spec in getBuiltInSpecs()) {
            result[spec.id] = ensureSingleMtzArchiveCreated(spec)
        }
        return result
    }

    private fun buildFallbackMtzArchive(spec: BuiltInMtzSpec, targetFile: File) {
        val descriptionXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <MIUI-Theme>
                <title>${spec.title}</title>
                <author>${spec.author}</author>
                <designer>${spec.author}</designer>
                <version>${spec.version}</version>
                <uiVersion>${spec.uiVersion}</uiVersion>
                <description>${spec.description}</description>
            </MIUI-Theme>
        """.trimIndent()

        val innerIconsBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                val themeValuesXml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <MIUI_Theme_Values>
                        <dimen name="icon_scale">${spec.iconScale}</dimen>
                        <color name="icon_title_color">#ECF0F1</color>
                    </MIUI_Theme_Values>
                """.trimIndent()
                val transformConfigXml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <IconTransform>
                        <PointsMapping>
                            <Point fromX="0.0" fromY="0.0" toX="10.0" toY="10.0" />
                            <Point fromX="0.0" fromY="90.0" toX="10.0" toY="80.0" />
                            <Point fromX="90.0" fromY="90.0" toX="80.0" toY="80.0" />
                            <Point fromX="90.0" fromY="0.0" toX="80.0" toY="10.0" />
                        </PointsMapping>
                        <IconFilters>
                            <Filter name="GrayScale">
                                <Param name="BlackColor" value="${spec.bgHex}" />
                            </Filter>
                            <Filter name="Levels">
                                <Param name="InputMin" value="95" />
                            </Filter>
                        </IconFilters>
                    </IconTransform>
                """.trimIndent()
                writeZipEntry(zos, "theme_values.xml", themeValuesXml.toByteArray(Charsets.UTF_8))
                writeZipEntry(zos, "transform_config.xml", transformConfigXml.toByteArray(Charsets.UTF_8))
                writeZipEntry(zos, "res/drawable-xxhdpi/icon_background.png", renderCirclePng(spec.bgHex, fill = true))
                writeZipEntry(zos, "res/drawable-xxhdpi/icon_mask.png", renderCirclePng("#000000", fill = true))
                writeZipEntry(zos, "res/drawable-xxhdpi/icon_border.png", renderCirclePng(spec.borderRingHex, fill = false))
            }
        }.toByteArray()

        FileOutputStream(targetFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                writeZipEntry(zos, "description.xml", descriptionXml.toByteArray(Charsets.UTF_8))
                writeZipEntry(zos, "icons", innerIconsBytes)
            }
        }
    }

    private fun renderCirclePng(colorHex: String, fill: Boolean): ByteArray {
        val bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colorHex)
            style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = 14f
        }
        canvas.drawCircle(96f, 96f, if (fill) 84f else 83f, paint)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
        return out.toByteArray()
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
    }
}
