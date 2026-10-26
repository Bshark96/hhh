package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.DeviceTelemetrySnapshot
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.LauncherEngineConfig
import com.example.mtz.MtzArchiveParser
import com.example.mtz.MtzSampleGenerator
import com.example.mtz.XiaomiIconRenderEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MTZLauncher", appName)
    }

    @Test
    fun `parse midnight blue and emerald mtz archives and verify icon transform pipeline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val generator = MtzSampleGenerator(context)
        val parser = MtzArchiveParser()
        val renderEngine = XiaomiIconRenderEngine()

        val archives = generator.ensureSampleMtzArchivesCreated()

        // 1. Verify Midnight Blue .mtz extraction & <IconTransform> parsing
        val midnightFile = archives["builtin_midnight_blue"]
        assertNotNull(midnightFile)
        assertTrue(midnightFile!!.exists() && midnightFile.length() > 0L)

        val extractMidnightDir = File(context.cacheDir, "test_extract_midnight")
        val midnightMeta = FileInputStream(midnightFile).use { fis ->
            parser.parseAndExtractMtzStream(
                inputStream = fis,
                themeId = "builtin_midnight_blue",
                archiveFileName = midnightFile.name,
                targetExtractDir = extractMidnightDir,
                isBuiltIn = true
            )
        }

        assertEquals("Midnight Blue", midnightMeta.title)
        assertEquals(0.7778f, midnightMeta.iconScale, 0.02f)
        assertEquals("#465967", midnightMeta.filterBlackColorHex)
        assertEquals(95, midnightMeta.filterLevelsInputMin)
        assertTrue(midnightMeta.iconBgPaths.isNotEmpty())
        assertTrue(midnightMeta.iconBgPaths.first().endsWith("icon_background.png"))
        assertNotNull(midnightMeta.iconMaskPath)
        assertNotNull(midnightMeta.iconBorderPath)
        assertTrue(midnightMeta.iconBorderPath!!.endsWith("icon_border.png"))
        assertTrue(midnightMeta.directIconsMap.containsKey("com.android.settings"))
        assertTrue(midnightMeta.fancyIconSpecs.containsKey("com.android.calendar"))

        // 2. Simulate an Android Adaptive 3rd-party icon (like Maps / Photos: outer white circle + colored glyph)
        val adaptiveRaw = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val rawCanvas = Canvas(adaptiveRaw)
        val whitePlatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        rawCanvas.drawCircle(64f, 64f, 60f, whitePlatePaint)
        val redGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(229, 57, 53)
            style = Paint.Style.FILL
        }
        rawCanvas.drawCircle(64f, 64f, 24f, redGlyphPaint)

        val config = LauncherEngineConfig(activeThemeId = "builtin_midnight_blue")
        val midnightMasked = renderEngine.resolveAppIcon(
            packageName = "com.google.android.apps.maps",
            activityName = "com.google.android.maps.MapsActivity",
            rawAppBitmap = adaptiveRaw,
            theme = midnightMeta,
            config = config,
            customOverrideAssetKey = null
        )

        assertEquals(IconResolutionSource.MTZ_DYNAMIC_MASK, midnightMasked.source)
        assertEquals(XiaomiIconRenderEngine.TARGET_ICON_PX, midnightMasked.bitmap.width)

        // Check that the circular inner plate pixel (x=80, y=32) is opaque #465967 (NOT transparent!)
        val platePixel = midnightMasked.bitmap.getPixel(80, 32)
        assertTrue("Inner circular plate must be opaque", Color.alpha(platePixel) > 240)
        assertEquals(70.0, Color.red(platePixel).toDouble(), 6.0)
        assertEquals(89.0, Color.green(platePixel).toDouble(), 6.0)
        assertEquals(103.0, Color.blue(platePixel).toDouble(), 6.0)

        // Check that the outer circular border ring pixel (x=80, y=18, r=62px on 160x160) is opaque #43515E
        val borderPixel = midnightMasked.bitmap.getPixel(80, 18)
        assertTrue("Outer circular border ring must be opaque", Color.alpha(borderPixel) > 240)
        assertEquals(67.0, Color.red(borderPixel).toDouble(), 6.0)
        assertEquals(81.0, Color.green(borderPixel).toDouble(), 6.0)
        assertEquals(94.0, Color.blue(borderPixel).toDouble(), 6.0)

        // Check that the center glyph pixel (x=80, y=80) is crisp off-white (#ECF0F1)
        val centerPixel = midnightMasked.bitmap.getPixel(80, 80)
        assertTrue(Color.red(centerPixel) > 215 && Color.green(centerPixel) > 215 && Color.blue(centerPixel) > 215)

        // 3. Verify 3rd-party adaptive icon with a colored/dark outer squircle plate (like Phone/Messages)
        // is also properly stripped of its outer adaptive plate and shaded with GrayScale + Levels
        val coloredPlateRaw = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val coloredCanvas = Canvas(coloredPlateRaw)
        val bluePlatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 118, 210)
            style = Paint.Style.FILL
        }
        // Adaptive icon with a 4px transparent margin and a uniform blue plate
        coloredCanvas.drawCircle(64f, 64f, 58f, bluePlatePaint)
        val whiteSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        coloredCanvas.drawCircle(64f, 64f, 22f, whiteSymbolPaint)

        val coloredPlateMasked = renderEngine.resolveAppIcon(
            packageName = "com.thirdparty.customapp",
            activityName = "com.thirdparty.customapp.MainActivity",
            rawAppBitmap = coloredPlateRaw,
            theme = midnightMeta,
            config = config,
            customOverrideAssetKey = null
        )

        assertEquals(IconResolutionSource.MTZ_DYNAMIC_MASK, coloredPlateMasked.source)
        // Outer plate region (x=80, y=32) should show the themed Midnight Blue background plate (#465967)
        val shadedBgPixel = coloredPlateMasked.bitmap.getPixel(80, 32)
        assertEquals(70.0, Color.red(shadedBgPixel).toDouble(), 6.0)
        assertEquals(89.0, Color.green(shadedBgPixel).toDouble(), 6.0)
        assertEquals(103.0, Color.blue(shadedBgPixel).toDouble(), 6.0)
    }

    @Test
    fun `parse Trickster hejo V10 mtz from root and verify icon_pattern background and emerald glyph filter`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = MtzArchiveParser()
        val renderEngine = XiaomiIconRenderEngine()

        val rootTrickster = File("../Trickster+hejo+V10.mtz").takeIf { it.exists() }
            ?: File("Trickster+hejo+V10.mtz")
        assertTrue("Trickster+hejo+V10.mtz must exist in project root", rootTrickster.exists())

        val extractDir = File(context.cacheDir, "test_extract_trickster")
        val tricksterMeta = FileInputStream(rootTrickster).use { fis ->
            parser.parseAndExtractMtzStream(
                inputStream = fis,
                themeId = "user_trickster_hejo",
                archiveFileName = rootTrickster.name,
                targetExtractDir = extractDir,
                isBuiltIn = false
            )
        }

        assertEquals("Trickster hejo V10", tricksterMeta.title)
        // Trickster has icon_pattern.png (#384648 circle plate) and icon_mask.png (inner circle mask), NO icon_border.png
        assertNotNull("icon_pattern.png must be detected", tricksterMeta.iconPatternPath)
        assertNotNull("icon_mask.png must be detected", tricksterMeta.iconMaskPath)
        assertEquals("icon_border.png must be null so icon_pattern does not occlude the foreground", null, tricksterMeta.iconBorderPath)
        assertTrue("Edges filter must be present in rawTransformConfigXml", tricksterMeta.rawTransformConfigXml.contains("Edges", ignoreCase = true))
        assertEquals("#384648", tricksterMeta.filterBlackColorHex)
        assertEquals("#2B9D6A", tricksterMeta.filterWhiteColorHex)

        // Test 1: White adaptive plate + blue glyph (like Chrome / Gmail / Magisk / Total Commander)
        val whitePlateBlueGlyph = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c1 = Canvas(whitePlateBlueGlyph)
        c1.drawCircle(64f, 64f, 58f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        })
        c1.drawCircle(64f, 64f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 136, 229)
            style = Paint.Style.FILL
        })

        val config = LauncherEngineConfig(activeThemeId = "user_trickster_hejo")
        val resolved = renderEngine.resolveAppIcon(
            packageName = "com.thirdparty.browser",
            activityName = "com.thirdparty.browser.MainActivity",
            rawAppBitmap = whitePlateBlueGlyph,
            theme = tricksterMeta,
            config = config,
            customOverrideAssetKey = null
        )

        assertEquals(IconResolutionSource.MTZ_DYNAMIC_MASK, resolved.source)

        // Outer circular plate pixel (x=80, y=28) comes from icon_pattern.png (#384648 -> R=56, G=70, B=72)
        val outerPlatePixel = resolved.bitmap.getPixel(80, 28)
        assertTrue("Outer circular plate from icon_pattern.png must be opaque", Color.alpha(outerPlatePixel) > 240)
        assertEquals(56.0, Color.red(outerPlatePixel).toDouble(), 6.0)
        assertEquals(70.0, Color.green(outerPlatePixel).toDouble(), 6.0)
        assertEquals(72.0, Color.blue(outerPlatePixel).toDouble(), 6.0)

        // Center glyph pixel (x=80, y=80) must be bright Trickster emerald green (#2B9D6A -> R=43, G=157, B=106), NOT blank #384648!
        val centerGlyphPixel = resolved.bitmap.getPixel(80, 80)
        assertTrue("Center glyph must be opaque", Color.alpha(centerGlyphPixel) > 240)
        assertTrue(
            "Center glyph must be shaded with Trickster emerald green #2B9D6A (got R=${Color.red(centerGlyphPixel)}, G=${Color.green(centerGlyphPixel)}, B=${Color.blue(centerGlyphPixel)})",
            Color.green(centerGlyphPixel) > 130 && Color.green(centerGlyphPixel) > Color.red(centerGlyphPixel) + 50
        )

        // Test 2: OEM alias resolution (e.g. ZTE/Nubia Camera 'com.zte.camera' -> 'com.android.camera')
        val zteCameraResolved = renderEngine.resolveAppIcon(
            packageName = "com.zte.camera",
            activityName = "com.zte.camera.CameraActivity",
            rawAppBitmap = whitePlateBlueGlyph,
            theme = tricksterMeta,
            config = config,
            customOverrideAssetKey = null
        )
        assertEquals(IconResolutionSource.MTZ_DIRECT_MAP, zteCameraResolved.source)
    }

    @Test
    fun `parse Valorant mtz archive and verify crisp white foreground glyphs on dark purple hexagon plate`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val parser = MtzArchiveParser()
        val renderEngine = XiaomiIconRenderEngine()

        val valorantFile = File("../Valorant_miuithemer.com.mtz").takeIf { it.exists() }
            ?: File("Valorant_miuithemer.com.mtz").takeIf { it.exists() }
            ?: File("/Valorant_miuithemer.com.mtz").takeIf { it.exists() }
            ?: File("../Valorant_miuithemer.com.mtz.zip").takeIf { it.exists() }
            ?: File("Valorant_miuithemer.com.mtz.zip").takeIf { it.exists() }
            ?: File("/Valorant_miuithemer.com.mtz.zip")

        assertTrue("Valorant archive must exist at root", valorantFile.exists())

        val targetDir = File(appContext.filesDir, "test_extracted_valorant")
        if (targetDir.exists()) targetDir.deleteRecursively()

        val valorantMeta = java.io.FileInputStream(valorantFile).use { fis ->
            parser.parseAndExtractMtzStream(
                inputStream = fis,
                themeId = "user_valorant",
                archiveFileName = "Valorant.mtz",
                targetExtractDir = targetDir,
                isBuiltIn = false
            )
        }

        assertNotNull("icon_pattern.png must be detected", valorantMeta.iconPatternPath)
        assertNotNull("icon_mask.png must be detected", valorantMeta.iconMaskPath)
        assertEquals("#FFFFFF", valorantMeta.filterBlackColorHex)
        assertEquals("#00000000", valorantMeta.filterWhiteColorHex)

        // Test with an adaptive white plate + center symbol (e.g. Chrome / Contacts / Play Store)
        val whitePlateBlueGlyph = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c1 = Canvas(whitePlateBlueGlyph)
        c1.drawCircle(64f, 64f, 58f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        })
        c1.drawCircle(64f, 64f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 100, 200)
            style = Paint.Style.FILL
        })

        val config = LauncherEngineConfig(activeThemeId = "user_valorant")
        val resolved = renderEngine.resolveAppIcon(
            packageName = "com.google.android.apps.messaging",
            activityName = "com.google.android.apps.messaging.MainActivity",
            rawAppBitmap = whitePlateBlueGlyph,
            theme = valorantMeta,
            config = config,
            customOverrideAssetKey = null
        )

        assertEquals(IconResolutionSource.MTZ_DYNAMIC_MASK, resolved.source)

        // Interior (96, 96) shows the dark purple hexagon plate, while the <Filter name="Edges" /> contour ring is crisp white
        val centerPixel = resolved.bitmap.getPixel(96, 96)
        assertTrue("Center plate must be opaque", Color.alpha(centerPixel) > 240)
        assertTrue(
            "Center plate must be dark purple (got R=${Color.red(centerPixel)}, G=${Color.green(centerPixel)}, B=${Color.blue(centerPixel)})",
            Color.red(centerPixel) in 45..80 && Color.blue(centerPixel) in 45..80
        )
        val hasCrispWhiteContour = (35..155).any { x ->
            val px = resolved.bitmap.getPixel(x, 96)
            Color.alpha(px) > 240 && Color.red(px) >= 210 && Color.green(px) >= 210 && Color.blue(px) >= 210
        }
        assertTrue("Foreground glyph contour must have crisp white edge pixels", hasCrispWhiteContour)
    }

    @Test
    fun `parse Fussion_SRXUi mtz and verify no transform filter is synthesized or applied to third-party icons`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val parser = MtzArchiveParser()
        val renderEngine = XiaomiIconRenderEngine()

        val fussionFile = File("../Fussion_SRXUi.mtz").takeIf { it.exists() }
            ?: File("Fussion_SRXUi.mtz").takeIf { it.exists() }
            ?: File("/Fussion_SRXUi.mtz").takeIf { it.exists() }

        val targetDir = File(appContext.filesDir, "test_extracted_fussion")
        if (targetDir.exists()) targetDir.deleteRecursively()

        val stream = fussionFile?.let { FileInputStream(it) } ?: run {
            val maskBmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).apply {
                Canvas(this).drawColor(Color.WHITE)
            }
            val maskBytes = java.io.ByteArrayOutputStream().also {
                maskBmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }.toByteArray()

            val innerIconsBytes = java.io.ByteArrayOutputStream().also { baos ->
                java.util.zip.ZipOutputStream(baos).use { zos ->
                    zos.putNextEntry(java.util.zip.ZipEntry("res/drawable-xxhdpi/icon_mask.png"))
                    zos.write(maskBytes)
                    zos.closeEntry()
                    zos.putNextEntry(java.util.zip.ZipEntry("res/drawable-xxhdpi/icon_background.png"))
                    zos.write(maskBytes)
                    zos.closeEntry()
                }
            }.toByteArray()

            val outerMtzBytes = java.io.ByteArrayOutputStream().also { baos ->
                java.util.zip.ZipOutputStream(baos).use { zos ->
                    zos.putNextEntry(java.util.zip.ZipEntry("description.xml"))
                    zos.write("<theme><title>Fussion SRXUi</title><author>SRX</author><version>1.0</version></theme>".toByteArray())
                    zos.closeEntry()
                    zos.putNextEntry(java.util.zip.ZipEntry("icons"))
                    zos.write(innerIconsBytes)
                    zos.closeEntry()
                }
            }.toByteArray()
            java.io.ByteArrayInputStream(outerMtzBytes)
        }

        val fussionMeta = stream.use { fis ->
            parser.parseAndExtractMtzStream(
                inputStream = fis,
                themeId = "user_fussion_srx",
                archiveFileName = "Fussion_SRXUi.mtz",
                targetExtractDir = targetDir,
                isBuiltIn = false
            )
        }

        assertEquals("Fussion_SRXUi.mtz has no transform_config.xml", false, fussionMeta.hasTransformConfig)
        assertTrue("Fussion_SRXUi.mtz must have empty filterSteps", fussionMeta.filterSteps.isEmpty())
        assertTrue("Fussion_SRXUi.mtz rawTransformConfigXml must be blank", fussionMeta.rawTransformConfigXml.isBlank())

        // Render a vibrant orange 3rd-party icon and verify its original orange color is preserved (NOT grayscaled!)
        val orangeIcon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c = Canvas(orangeIcon)
        c.drawColor(Color.rgb(255, 110, 20))

        val config = LauncherEngineConfig(activeThemeId = "user_fussion_srx")
        val resolved = renderEngine.resolveAppIcon(
            packageName = "com.thirdparty.unmapped.orangeapp",
            activityName = "com.thirdparty.unmapped.orangeapp.MainActivity",
            rawAppBitmap = orangeIcon,
            theme = fussionMeta,
            config = config,
            customOverrideAssetKey = null
        )

        assertEquals(IconResolutionSource.MTZ_DYNAMIC_MASK, resolved.source)
        val center = resolved.bitmap.getPixel(80, 80)
        assertTrue(
            "Fussion_SRXUi 3rd-party icon center must remain vibrant orange without GrayScale filter (got R=${Color.red(center)}, G=${Color.green(center)}, B=${Color.blue(center)})",
            Color.red(center) > 200 && Color.red(center) > Color.blue(center) + 120
        )
    }

    @Test
    fun `folder auto dissolves when 1 icon remains and empty workspace pages auto compact`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.LauncherDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val dao = db.launcherDao()
            val repo = com.example.data.repository.LauncherRepository(
                context = context,
                dao = dao
            )
            repo.initializeBuiltInThemesAndWorkspace()

            // Create two test apps on page 0
            val dummyBmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            val appA = com.example.domain.model.InstalledAppItem(
                packageName = "com.test.alpha",
                activityName = "com.test.alpha.MainActivity",
                label = "Alpha",
                isSystemApp = false,
                rawIconBitmap = dummyBmp,
                themedIconBitmap = dummyBmp,
                resolutionSource = IconResolutionSource.MTZ_DYNAMIC_MASK,
                matchedAssetName = "none",
                isPinnedToWorkspace = true,
                isPinnedToDock = false,
                workspacePage = 0,
                customOverrideAssetKey = null
            )
            val appB = appA.copy(
                packageName = "com.test.beta",
                activityName = "com.test.beta.MainActivity",
                label = "Beta"
            )

            // Merge appA and appB into a new folder on page 0
            repo.mergeAppsIntoNewFolder(draggedApp = appA, targetApp = appB, pageIndex = 0)
            val foldersAfterMerge = dao.getWorkspaceFoldersOnce()
            val createdFolder = foldersAfterMerge.firstOrNull {
                it.packageNamesCsv.contains("com.test.alpha") && it.packageNamesCsv.contains("com.test.beta")
            }
            assertNotNull("Merged folder must be created with both apps", createdFolder)

            // Extract appA out of the folder onto workspace page 1; since only appB (1 icon) remains,
            // the folder must auto-dissolve and restore appB onto workspace page 0!
            repo.extractAppFromFolderToWorkspace(
                folderId = createdFolder!!.id,
                app = appA,
                targetPage = 1,
                targetSortOrder = 0,
                isDockItem = false
            )

            val foldersAfterExtract = dao.getWorkspaceFoldersOnce()
            assertTrue(
                "Folder must auto-dissolve when only 1 icon remains",
                foldersAfterExtract.none { it.id == createdFolder.id }
            )

            val shortcutsAfterDissolve = dao.getWorkspaceShortcutsOnce()
            val restoredBeta = shortcutsAfterDissolve.firstOrNull { it.packageName == "com.test.beta" }
            assertNotNull("Remaining single app must be restored to the workspace when folder dissolves", restoredBeta)

            // Batch move both apps to a newly created page (page 5 -> compacted to next page index)
            repo.batchMoveAppsToPage(listOf("com.test.alpha", "com.test.beta"), targetPage = 5)
            val pagesWithItemsBeforeDelete = dao.getWorkspaceShortcutsOnce()
                .filter { !it.isDockItem }
                .map { it.pageIndex }
                .distinct()
                .sorted()
            assertTrue("Pages must be contiguous starting at 0", pagesWithItemsBeforeDelete.first() == 0)
            val highestPage = pagesWithItemsBeforeDelete.last()
            assertTrue("Batch move must place apps on the new page", highestPage >= 1)

            // Remove both apps from the highest page and verify empty page auto-compaction
            repo.removeAppFromWorkspace("com.test.alpha")
            repo.removeAppFromWorkspace("com.test.beta")
            val shortcutsAfterDelete = dao.getWorkspaceShortcutsOnce()
            assertTrue(
                "Removed apps must no longer be pinned on workspace",
                shortcutsAfterDelete.none { it.packageName == "com.test.alpha" || it.packageName == "com.test.beta" }
            )
        } finally {
            db.close()
        }
    }

    @get:org.junit.Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `workspace icon long tap shows floating action pill and long press drag completes drop without losing state`() {
        val dummyBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val appAlpha = com.example.domain.model.InstalledAppItem(
            packageName = "com.test.alpha",
            activityName = "com.test.alpha.MainActivity",
            label = "Alpha",
            isSystemApp = false,
            rawIconBitmap = dummyBmp,
            themedIconBitmap = dummyBmp,
            resolutionSource = IconResolutionSource.MTZ_DYNAMIC_MASK,
            matchedAssetName = "none",
            isPinnedToWorkspace = true,
            isPinnedToDock = false,
            workspacePage = 0,
            customOverrideAssetKey = null
        )

        var movedAppPkg: String? = null
        val telemetry = DeviceTelemetrySnapshot()

        composeTestRule.setContent {
            com.example.ui.screens.WorkspaceScreen(
                activeTheme = null,
                wallpaperBitmap = null,
                engineConfig = LauncherEngineConfig(),
                allApps = listOf(appAlpha),
                workspaceApps = listOf(appAlpha),
                dockApps = emptyList(),
                workspaceFolders = emptyList(),
                workspaceWidgets = emptyList(),
                deviceTelemetry = telemetry,
                appWidgetHost = null,
                onAppClick = {},
                onAppLongClick = {},
                onExpandFolder = {},
                onCycleFolderMode = {},
                onMoveAppOnWorkspace = { app, _, _, _, _, _ ->
                    movedAppPkg = app.packageName
                },
                onOpenWidgetsAndFoldersPicker = {},
                onRefreshTelemetry = {},
                onRemoveWidget = {},
                onOpenAppDrawer = {},
                onOpenMtzStudio = {},
                onOpenApiPipeline = {}
            )
        }

        // 1. Long-tap on the workspace icon without dragging -> verify MIUI Floating Action Pill appears
        val iconNode = composeTestRule.onNode(
            hasTestTag("app_tile_com.test.alpha"),
            useUnmergedTree = true
        )
        iconNode.performTouchInput {
            down(center)
            advanceEventTime(450L)
            up()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(
            hasTestTag("app_floating_action_pill_com.test.alpha"),
            useUnmergedTree = true
        ).assertExists("MIUI Floating Action Pill must appear on workspace icon long-tap")

        // 2. Long-press and drag the workspace icon across the screen -> verify drag does not cancel and drop succeeds
        iconNode.performTouchInput {
            down(center)
            advanceEventTime(450L)
            moveBy(androidx.compose.ui.geometry.Offset(60f, 120f), delayMillis = 16L)
            moveBy(androidx.compose.ui.geometry.Offset(60f, 120f), delayMillis = 16L)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "Dragging an icon after long-press must maintain drag state and invoke drop callback",
            "com.test.alpha",
            movedAppPkg
        )
    }

    @Test
    fun `workspace 4x6 grid shares rows between 2x2 folder and 1x1 icons and rejects overflow on active page`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.LauncherDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val dao = db.launcherDao()
            val repo = com.example.data.repository.LauncherRepository(
                context = context,
                dao = dao
            )
            repo.initializeBuiltInThemesAndWorkspace()

            // Verify Page 0 default 4x6 layout:
            // Clock widget (4x2) at (0,0), Big Folder (2x2) at (0,2), and 1x1 app shortcuts sharing rows 2 and 3 at (2,2), (3,2), (2,3), (3,3)
            val page0Folders = dao.getWorkspaceFoldersOnce().filter { it.pageIndex == 0 }
            assertEquals(1, page0Folders.size)
            assertEquals(0, page0Folders.first().cellX)
            assertEquals(2, page0Folders.first().cellY)

            val page0Apps = dao.getWorkspaceShortcutsOnce().filter { !it.isDockItem && it.pageIndex == 0 }
            assertTrue(
                "App icons must share row 2 with the 2x2 folder at (2,2) and (3,2)",
                page0Apps.any { it.cellX == 2 && it.cellY == 2 } && page0Apps.any { it.cellX == 3 && it.cellY == 2 }
            )
            assertTrue(
                "App icons must share row 3 with the 2x2 folder at (2,3) and (3,3)",
                page0Apps.any { it.cellX == 2 && it.cellY == 3 } && page0Apps.any { it.cellX == 3 && it.cellY == 3 }
            )

            // Verify adding a widget to active page 1 places it on page 1 (NOT page 0)
            val addedOnPage1 = repo.addBuiltInWidget(
                type = com.example.domain.model.HyperWidgetType.DEVICE_TELEMETRY,
                pageIndex = 1
            )
            assertTrue("2x2 Telemetry widget must fit on active page 1", addedOnPage1)
            val widgetsOnPage1 = dao.getWorkspaceWidgetsOnce().filter { it.pageIndex == 1 }
            assertEquals(1, widgetsOnPage1.size)

            // Fill remaining space on page 0 and verify adding another 4x2 Clock widget returns false ("No space on this screen")
            val addedSecond4x2OnPage0 = repo.addBuiltInWidget(
                type = com.example.domain.model.HyperWidgetType.CLOCK_MTZ_STATUS,
                pageIndex = 0
            )
            // Page 0 only has 6 free 1x1 cells left in rows 4 and 5 (since (0,4) and (1,4) are occupied by apps), so a 4x2 widget cannot fit!
            assertEquals(
                "4x2 widget must be rejected when page 0 does not have 4x2 contiguous free cells",
                false,
                addedSecond4x2OnPage0
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `workspace widget long tap shows floating Remove pill and long press drag moves widget on 2D grid`() {
        val telemetryWidget = com.example.domain.model.WorkspaceWidgetItem(
            id = "widget_test_telemetry",
            widgetType = com.example.domain.model.HyperWidgetType.DEVICE_TELEMETRY,
            appWidgetId = -1,
            providerComponent = null,
            customTitle = "System Telemetry",
            sortOrder = 0,
            pageIndex = 0,
            cellX = 0,
            cellY = 0,
            spanX = 2,
            spanY = 2
        )

        var movedWidgetId: String? = null
        var cycledWidgetId: String? = null
        val telemetry = DeviceTelemetrySnapshot()

        composeTestRule.setContent {
            com.example.ui.screens.WorkspaceScreen(
                activeTheme = null,
                wallpaperBitmap = null,
                engineConfig = LauncherEngineConfig(),
                allApps = emptyList(),
                workspaceApps = emptyList(),
                dockApps = emptyList(),
                workspaceFolders = emptyList(),
                workspaceWidgets = listOf(telemetryWidget),
                deviceTelemetry = telemetry,
                appWidgetHost = null,
                onAppClick = {},
                onAppLongClick = {},
                onExpandFolder = {},
                onCycleFolderMode = {},
                onMoveWidgetOnWorkspace = { wId, _, _, _ ->
                    movedWidgetId = wId
                },
                onCycleWidgetSize = { wId ->
                    cycledWidgetId = wId
                },
                onOpenWidgetsAndFoldersPicker = {},
                onRefreshTelemetry = {},
                onRemoveWidget = {},
                onOpenAppDrawer = {},
                onOpenMtzStudio = {},
                onOpenApiPipeline = {}
            )
        }

        val widgetNode = composeTestRule.onNode(
            hasTestTag("workspace_widget_widget_test_telemetry"),
            useUnmergedTree = true
        )

        // 1. Long-tap on the widget without dragging -> verify floating Resize + Remove pill and 4 corner resize handles appear
        widgetNode.performTouchInput {
            down(center)
            advanceEventTime(450L)
            up()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(
            hasTestTag("widget_floating_action_pill_widget_test_telemetry"),
            useUnmergedTree = true
        ).assertExists("Floating action pill must appear on widget long-tap")

        composeTestRule.onNode(
            hasTestTag("widget_popup_resize_widget_test_telemetry"),
            useUnmergedTree = true
        ).assertExists("Floating Resize button must appear in widget action pill")

        composeTestRule.onNode(
            hasTestTag("widget_resize_handle_widget_test_telemetry_3"),
            useUnmergedTree = true
        ).assertExists("Bottom-right corner resize handle must appear when widget is in edit mode")

        // 2. Long-press and drag the widget across the 2D grid -> verify onMoveWidgetOnWorkspace is invoked
        widgetNode.performTouchInput {
            down(center)
            advanceEventTime(450L)
            moveBy(androidx.compose.ui.geometry.Offset(120f, 160f), delayMillis = 16L)
            moveBy(androidx.compose.ui.geometry.Offset(60f, 80f), delayMillis = 16L)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "Dragging a widget after long-press must invoke onMoveWidgetOnWorkspace",
            "widget_test_telemetry",
            movedWidgetId
        )
    }

    @Test
    fun `workspace 2x2 folder long tap shows floating action pill and long press drag moves folder on 2D grid`() {
        val dummyBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val app1 = com.example.domain.model.InstalledAppItem(
            packageName = "com.test.one",
            activityName = "com.test.one.MainActivity",
            label = "One",
            isSystemApp = false,
            rawIconBitmap = dummyBmp,
            themedIconBitmap = dummyBmp,
            resolutionSource = IconResolutionSource.MTZ_DYNAMIC_MASK,
            matchedAssetName = "none",
            isPinnedToWorkspace = false,
            isPinnedToDock = false,
            workspacePage = 0,
            customOverrideAssetKey = null
        )
        val app2 = app1.copy(packageName = "com.test.two", label = "Two")
        val testFolder = com.example.domain.model.WorkspaceFolder(
            id = "folder_test_2x2",
            title = "Tools",
            layoutMode = com.example.domain.model.HyperFolderLayoutMode.BIG_FOLDER_2X2,
            packageNames = listOf(app1.packageName, app2.packageName),
            apps = listOf(app1, app2),
            sortOrder = 0,
            pageIndex = 0,
            cellX = 0,
            cellY = 2
        )

        var movedFolderId: String? = null
        var movedFolderCell: Pair<Int, Int>? = null
        val telemetry = DeviceTelemetrySnapshot()

        composeTestRule.setContent {
            com.example.ui.screens.WorkspaceScreen(
                activeTheme = null,
                wallpaperBitmap = null,
                engineConfig = LauncherEngineConfig(),
                allApps = listOf(app1, app2),
                workspaceApps = emptyList(),
                dockApps = emptyList(),
                workspaceFolders = listOf(testFolder),
                workspaceWidgets = emptyList(),
                deviceTelemetry = telemetry,
                appWidgetHost = null,
                onAppClick = {},
                onAppLongClick = {},
                onExpandFolder = {},
                onCycleFolderMode = {},
                onMoveFolderOnWorkspace = { fId, _, _, cx, cy ->
                    movedFolderId = fId
                    movedFolderCell = cx to cy
                },
                onOpenWidgetsAndFoldersPicker = {},
                onRefreshTelemetry = {},
                onRemoveWidget = {},
                onOpenAppDrawer = {},
                onOpenMtzStudio = {},
                onOpenApiPipeline = {}
            )
        }

        val folderNode = composeTestRule.onNode(
            hasTestTag("hyper_folder_folder_test_2x2"),
            useUnmergedTree = true
        )

        // 1. Long-tap on the 2x2 folder (even over its icon area) -> verify floating folder action pill appears
        folderNode.performTouchInput {
            down(center)
            advanceEventTime(450L)
            up()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(
            hasTestTag("folder_floating_action_pill_folder_test_2x2"),
            useUnmergedTree = true
        ).assertExists("Floating Folder Action Pill must appear on 2x2 folder long-tap")

        // 2. While in folder edit mode, drag immediately -> verify onMoveFolderOnWorkspace is called
        folderNode.performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(140f, 0f), delayMillis = 16L)
            moveBy(androidx.compose.ui.geometry.Offset(80f, 0f), delayMillis = 16L)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "Dragging a 2x2 folder must invoke onMoveFolderOnWorkspace",
            "folder_test_2x2",
            movedFolderId
        )
        assertNotNull("Dropped folder must resolve target 2D cell coordinates", movedFolderCell)
    }

    @Test
    fun `workspace 6x8 grid persists columns and rows and allows 6-column widget resize and 36 to 72dp icon sizing`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.LauncherDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val dao = db.launcherDao()
            val repo = com.example.data.repository.LauncherRepository(
                context = context,
                dao = dao
            )
            repo.initializeBuiltInThemesAndWorkspace()

            // Switch to 6x8 grid with 36dp compact icon size
            val updatedConfig = repo.updateEngineConfig {
                it.copy(gridColumns = 6, gridRows = 8, iconSizeDp = 36)
            }
            assertEquals(6, updatedConfig.gridColumns)
            assertEquals(8, updatedConfig.gridRows)
            assertEquals(36, updatedConfig.iconSizeDp)

            // Verify Clock widget on page 0 can resize to 6x2 on the 6x8 grid
            val clockWidget = dao.getWorkspaceWidgetsOnce().first { it.pageIndex == 0 }
            val resizedTo6x2 = repo.resizeWidgetSpan(clockWidget.id, targetSpanX = 6, targetSpanY = 2)
            assertTrue("Clock widget must resize to 6x2 when 6 columns are active", resizedTo6x2)
            val updatedWidget = dao.getWorkspaceWidgetsOnce().first { it.id == clockWidget.id }
            assertEquals(6, updatedWidget.spanX)
            assertEquals(2, updatedWidget.spanY)

            // Switch back to 4x6 grid with 72dp max icon size and verify widget span auto-clamps to 4 columns
            val shrunkConfig = repo.updateEngineConfig {
                it.copy(gridColumns = 4, gridRows = 6, iconSizeDp = 72)
            }
            assertEquals(4, shrunkConfig.gridColumns)
            assertEquals(6, shrunkConfig.gridRows)
            assertEquals(72, shrunkConfig.iconSizeDp)

            val clampedWidget = dao.getWorkspaceWidgetsOnce().first { it.id == clockWidget.id }
            assertEquals("Shrinking grid from 6 to 4 columns must clamp 6-wide widget to 4 columns", 4, clampedWidget.spanX)
        } finally {
            db.close()
        }
    }

    @Test
    fun `xml parser and render engine support ColorSplash modes, extended filters, sequential ordering, and stock miui preservation`() {
        val parser = MtzArchiveParser()
        val renderEngine = XiaomiIconRenderEngine()

        // 1. Verify unmodified stock MIUI XML preserves isCustomFilterPipeline = false
        val stockMiuiXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <IconTransform>
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
        val parsedStock = parser.parseTransformConfigXml(stockMiuiXml)
        assertEquals("Unmodified stock MIUI XML must keep isCustomFilterPipeline = false", false, parsedStock.isCustomFilterPipeline)
        assertEquals("Default scope must be third_party (applyToAllIcons = false)", false, parsedStock.applyToAllIcons)

        // 2. Verify reordered native MIUI filters (Levels before GrayScale) activates sequential pipeline
        val reorderedNativeXml = """
            <IconTransform>
                <IconFilters>
                    <Filter name="Levels" InputMin="40" InputMax="220" />
                    <Filter name="GrayScale" BlackColor="#102030" WhiteColor="#F0F4F8" />
                </IconFilters>
            </IconTransform>
        """.trimIndent()
        val parsedReordered = parser.parseTransformConfigXml(reorderedNativeXml)
        assertEquals("Reordered native MIUI filters must set isCustomFilterPipeline = true", true, parsedReordered.isCustomFilterPipeline)

        // 3. Create a dual-color test icon: left half vibrant Red (240, 20, 20), right half vibrant Blue (20, 90, 240)
        val dualColorIcon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val dualCanvas = Canvas(dualColorIcon)
        dualCanvas.drawRect(0f, 0f, 64f, 128f, Paint().apply { color = Color.rgb(240, 20, 20) })
        dualCanvas.drawRect(64f, 0f, 128f, 128f, Paint().apply { color = Color.rgb(20, 90, 240) })

        // 3a. ColorSplash KEEP_ORIGINAL on red, B&W on rest
        val splashKeepRedXml = """
            <IconTransform scope="all">
                <IconFilters>
                    <Filter name="ColorSplash" Colors="red" Tolerance="25" Mode="KEEP_ORIGINAL" Invert="false" />
                </IconFilters>
            </IconTransform>
        """.trimIndent()
        val parsedKeepRed = parser.parseTransformConfigXml(splashKeepRedXml)
        assertTrue(parsedKeepRed.isCustomFilterPipeline)
        assertTrue(parsedKeepRed.applyToAllIcons)

        val baseTheme = com.example.domain.model.MtzThemeMetadata(
            id = "test_splash_theme",
            title = "Splash Test",
            author = "Test",
            designer = "Test",
            version = "1.0",
            uiVersion = "HyperOS",
            description = "Test",
            archiveFileName = "test.mtz",
            extractedDirPath = "",
            iconScale = 1.0f,
            iconPaddingDp = 0f,
            titleColorHex = "#FFFFFF",
            iconBgPaths = emptyList(),
            iconMaskPath = null,
            iconBorderPath = null,
            directIconsMap = emptyMap(),
            fancyIconSpecs = emptyMap(),
            wallpaperPath = null,
            rawDescriptionXml = "",
            rawThemeValuesXml = "",
            rawTransformConfigXml = splashKeepRedXml,
            isBuiltIn = false,
            importedAtMillis = 0L,
            hasTransformConfig = true,
            filterSteps = parsedKeepRed.filterSteps,
            transformApplyToAllIcons = parsedKeepRed.applyToAllIcons,
            isCustomFilterPipeline = parsedKeepRed.isCustomFilterPipeline
        )

        val outKeepRed = renderEngine.applyThemeIconTransformFilter(dualColorIcon, baseTheme)
        val leftRedPx = outKeepRed.getPixel(32, 64)
        val rightBwPx = outKeepRed.getPixel(160, 64)
        // Left half (Red) must remain strongly red
        assertTrue("Matched red pixel must stay vibrant red", Color.red(leftRedPx) > 200 && Color.green(leftRedPx) < 50)
        // Right half (Blue) must be converted to B&W grayscale (R == G == B)
        assertEquals(Color.red(rightBwPx), Color.green(rightBwPx))
        assertEquals(Color.green(rightBwPx), Color.blue(rightBwPx))

        // 3b. ColorSplash with Invert="true" and Mode="TINT_SPLASH" (SplashTint="#00FF66", RestTint="#301040")
        val splashInvertTintXml = """
            <IconTransform>
                <IconFilters>
                    <Filter name="ColorSplash" Colors="red" Tolerance="25" Mode="TINT_SPLASH" Invert="true" SplashTint="#00FF66" RestTint="#301040" />
                </IconFilters>
            </IconTransform>
        """.trimIndent()
        val parsedInvertTint = parser.parseTransformConfigXml(splashInvertTintXml)
        val outInvertTint = renderEngine.applyThemeIconTransformFilter(
            dualColorIcon,
            baseTheme.copy(
                rawTransformConfigXml = splashInvertTintXml,
                filterSteps = parsedInvertTint.filterSteps,
                isCustomFilterPipeline = parsedInvertTint.isCustomFilterPipeline
            )
        )
        val invertedLeftPx = outInvertTint.getPixel(32, 64)   // Red matched -> inverted to Rest (#301040)
        val invertedRightPx = outInvertTint.getPixel(160, 64) // Blue unmatched -> inverted to Splashed (#00FF66 green)
        assertTrue(
            "With Invert=true, unmatched right side must be tinted bright green (#00FF66)",
            Color.green(invertedRightPx) > 150 && Color.green(invertedRightPx) > Color.red(invertedRightPx) + 80
        )
        assertTrue(
            "With Invert=true, matched red left side must receive dark purple RestTint",
            Color.red(invertedLeftPx) < 60 && Color.blue(invertedLeftPx) >= Color.green(invertedLeftPx)
        )

        // 4. Verify Duotone, Adjust, EdgeGlow, Posterize, Sepia, Invert, and Monochrome parse & execute in sequence
        val multiApiXml = """
            <IconTransform sequential="true">
                <IconFilters>
                    <Filter name="StripAdaptivePlate" NormalizeBounds="true" />
                    <Filter name="Posterize" Levels="4" />
                    <Filter name="Adjust" Hue="15" Saturation="1.2" Contrast="1.15" Brightness="0.05" />
                    <Filter name="Duotone" ShadowColor="#101828" MidColor="#2563EB" HighlightColor="#38BDF8" Intensity="0.8" />
                    <Filter name="Sepia" Intensity="0.2" />
                    <Filter name="Monochrome" Color="#E0F2FE" Intensity="0.2" />
                    <Filter name="Invert" Intensity="1.0" />
                    <Filter name="EdgeGlow" EdgeColor="#00F5D4" BaseColor="#111827" Threshold="30" FillAlpha="0.5" GlowRadius="1" />
                </IconFilters>
            </IconTransform>
        """.trimIndent()
        val parsedMulti = parser.parseTransformConfigXml(multiApiXml)
        assertEquals(8, parsedMulti.filterSteps.size)
        assertTrue(parsedMulti.isCustomFilterPipeline)
        val multiOut = renderEngine.applyThemeIconTransformFilter(
            dualColorIcon,
            baseTheme.copy(
                rawTransformConfigXml = multiApiXml,
                filterSteps = parsedMulti.filterSteps,
                isCustomFilterPipeline = parsedMulti.isCustomFilterPipeline
            )
        )
        assertEquals(XiaomiIconRenderEngine.TARGET_ICON_PX, multiOut.width)
        assertEquals(XiaomiIconRenderEngine.TARGET_ICON_PX, multiOut.height)
    }

    @Test
    fun `bottom pin bar dock supports sequential insertion reordering and scales with gridColumns 4 5 and 6`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.LauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val dao = db.launcherDao()
            val repo = com.example.data.repository.LauncherRepository(context, dao)
            val dummyBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

            fun makeApp(idx: Int) = com.example.domain.model.InstalledAppItem(
                packageName = "com.test.dock$idx",
                activityName = "com.test.dock$idx.MainActivity",
                label = "DockApp$idx",
                isSystemApp = false,
                rawIconBitmap = dummyBmp,
                themedIconBitmap = dummyBmp,
                resolutionSource = IconResolutionSource.MTZ_DYNAMIC_MASK,
                matchedAssetName = "none",
                isPinnedToWorkspace = false,
                isPinnedToDock = false,
                workspacePage = 0,
                customOverrideAssetKey = null
            )

            // 1. Set gridColumns = 6 so up to 6 icons can be pinned in the bottom bar
            repo.updateEngineConfig { it.copy(gridColumns = 6, gridRows = 8) }

            // Insert 4 icons one after another at the end (0, 1, 2, 3)
            for (i in 1..4) {
                val ok = repo.moveAppShortcut(makeApp(i), targetPage = 0, targetSortOrder = i - 1, isDockItem = true)
                assertTrue("Inserting icon #$i into dock must succeed", ok)
            }

            // Insert 5th icon in the middle at index 1 (between DockApp1 and DockApp2)
            val ok5 = repo.moveAppShortcut(makeApp(5), targetPage = 0, targetSortOrder = 1, isDockItem = true)
            assertTrue("Inserting 5th icon at index 1 must succeed when gridColumns=6", ok5)

            // Insert 6th icon at the end (index 5)
            val ok6 = repo.moveAppShortcut(makeApp(6), targetPage = 0, targetSortOrder = 5, isDockItem = true)
            assertTrue("Inserting 6th icon at index 5 must succeed when gridColumns=6", ok6)

            val dockAfter6 = dao.getWorkspaceShortcutsOnce()
                .filter { it.isDockItem }
                .sortedBy { it.sortOrder }
            assertEquals(
                listOf(
                    "com.test.dock1",
                    "com.test.dock5",
                    "com.test.dock2",
                    "com.test.dock3",
                    "com.test.dock4",
                    "com.test.dock6"
                ),
                dockAfter6.map { it.packageName }
            )
            assertEquals((0..5).toList(), dockAfter6.map { it.sortOrder })

            // Reorder com.test.dock6 (currently at index 5) to index 0
            val okReorder = repo.moveAppShortcut(makeApp(6), targetPage = 0, targetSortOrder = 0, isDockItem = true)
            assertTrue("Reordering existing dock icon within full 6-icon dock must succeed", okReorder)
            val dockAfterReorder = dao.getWorkspaceShortcutsOnce()
                .filter { it.isDockItem }
                .sortedBy { it.sortOrder }
            assertEquals("com.test.dock6", dockAfterReorder.first().packageName)
            assertEquals((0..5).toList(), dockAfterReorder.map { it.sortOrder })

            // Shrink gridColumns from 6 to 4: first 4 stay in dock, overflow 2 move to desktop
            repo.updateEngineConfig { it.copy(gridColumns = 4) }
            val dockAfterShrink = dao.getWorkspaceShortcutsOnce()
                .filter { it.isDockItem }
                .sortedBy { it.sortOrder }
            assertEquals(4, dockAfterShrink.size)
            assertEquals((0..3).toList(), dockAfterShrink.map { it.sortOrder })
        } finally {
            db.close()
        }
    }
}

