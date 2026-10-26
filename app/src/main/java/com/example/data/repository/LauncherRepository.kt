package com.example.data.repository

import android.Manifest
import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.local.EngineSettingsEntity
import com.example.data.local.IconOverrideEntity
import com.example.data.local.LauncherDao
import com.example.data.local.MtzThemeEntity
import com.example.data.local.WorkspaceFolderEntity
import com.example.data.local.WorkspaceShortcutEntity
import com.example.data.local.WorkspaceWidgetEntity
import com.example.domain.model.AppActivityOption
import com.example.domain.model.CalendarEventItem
import com.example.domain.model.DeviceTelemetrySnapshot
import com.example.domain.model.DockXmlConfig
import com.example.domain.model.HyperFolderLayoutMode
import com.example.domain.model.HyperWidgetType
import com.example.domain.model.IconPipelineBreakdown
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.InstalledAppItem
import com.example.domain.model.LauncherEngineConfig
import com.example.domain.model.MiuiBackgroundBlurMode
import com.example.domain.model.MiuiFolderAnimationMode
import com.example.domain.model.MiuiPageTransitionEffect
import com.example.domain.model.MtzThemeMetadata
import com.example.domain.model.SystemWidgetProviderItem
import com.example.domain.model.WorkspaceFolder
import com.example.domain.model.WorkspaceGridAllocator
import com.example.domain.model.WorkspaceWidgetItem
import com.example.mtz.MtzArchiveParser
import com.example.mtz.MtzSampleGenerator
import com.example.mtz.XiaomiIconRenderEngine
import com.example.ui.viewmodel.CatalogIconEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Clean Architecture Repository managing:
 * 1. Installed Android Launcher Activities (`PackageManager`) with in-memory caching
 * 2. Xiaomi `.mtz` / `.mtx` Archive Extraction & Metadata Caching
 * 3. Xiaomi Icon Theme API Rendering & Pipeline Diagnostics
 * 4. HyperOS Big Folders (`1x1`, `2x1 Capsule`, `2x2 Big Folder`, `3x3 XXL`)
 * 5. HyperOS Telemetry Widgets & Native Android `AppWidgetManager` Providers
 */
class LauncherRepository(
    private val context: Context,
    private val dao: LauncherDao,
    private val parser: MtzArchiveParser = MtzArchiveParser(),
    private val sampleGenerator: MtzSampleGenerator = MtzSampleGenerator(context),
    private val renderEngine: XiaomiIconRenderEngine = XiaomiIconRenderEngine()
) {

    private val metadataMemoryCache = ConcurrentHashMap<String, MtzThemeMetadata>()
    private val rawIconMemoryCache = ConcurrentHashMap<String, Bitmap>()
    private val wallpaperMemoryCache = ConcurrentHashMap<String, Bitmap>()
    private val catalogMemoryCache = ConcurrentHashMap<String, List<CatalogIconEntry>>()

    @Volatile
    private var cachedRawApps: List<RawAppRecord>? = null

    @Volatile
    private var cachedRawWidgets: List<RawWidgetRecord>? = null

    val engineConfigFlow: Flow<LauncherEngineConfig> = dao.observeEngineSettings().map { entity ->
        entity?.toDomain() ?: LauncherEngineConfig()
    }

    val themesFlow: Flow<List<MtzThemeEntity>> = dao.observeAllThemes()
    val shortcutsFlow: Flow<List<WorkspaceShortcutEntity>> = dao.observeWorkspaceShortcuts()
    val foldersFlow: Flow<List<WorkspaceFolderEntity>> = dao.observeWorkspaceFolders()
    val widgetsFlow: Flow<List<WorkspaceWidgetEntity>> = dao.observeWorkspaceWidgets()
    val iconOverridesFlow: Flow<List<IconOverrideEntity>> = dao.observeIconOverrides()

    suspend fun getEngineConfigOnce(): LauncherEngineConfig = withContext(Dispatchers.IO) {
        dao.getEngineSettingsOnce()?.toDomain() ?: LauncherEngineConfig()
    }

    suspend fun getAllThemesOnce(): List<MtzThemeEntity> = withContext(Dispatchers.IO) {
        dao.getAllThemesOnce()
    }

    suspend fun getShortcutsOnce(): List<WorkspaceShortcutEntity> = withContext(Dispatchers.IO) {
        dao.getWorkspaceShortcutsOnce()
    }

    suspend fun getFoldersOnce(): List<WorkspaceFolderEntity> = withContext(Dispatchers.IO) {
        dao.getWorkspaceFoldersOnce()
    }

    suspend fun getWidgetsOnce(): List<WorkspaceWidgetEntity> = withContext(Dispatchers.IO) {
        dao.getWorkspaceWidgetsOnce()
    }

    /**
     * Fast startup initialization: extracts ONLY the active theme and seeds default Workspace items
     * so the first frame renders in under 200ms.
     */
    suspend fun initializeBuiltInThemesAndWorkspace() = withContext(Dispatchers.IO) {
        val extractedRoot = File(context.filesDir, "extracted_themes")
        if (!extractedRoot.exists()) extractedRoot.mkdirs()

        val existingSettings = dao.getEngineSettingsOnce()
        val activeThemeId = existingSettings?.activeThemeId ?: "builtin_midnight_blue"
        if (existingSettings == null) {
            dao.saveEngineSettings(EngineSettingsEntity(activeThemeId = activeThemeId))
        }

        val specs = sampleGenerator.getBuiltInSpecs()
        val activeSpec = specs.firstOrNull { it.id == activeThemeId } ?: specs.first()

        // Ensure the active theme is extracted immediately
        ensureBuiltInSpecExtractedAndSaved(activeSpec, extractedRoot)

        val rawApps = queryRawLaunchableApps()

        // Seed initial Stock MIUI Clock Widget (4x2 at cell 0,0 on page 0) if empty
        val existingWidgets = dao.getWorkspaceWidgetsOnce()
        if (existingWidgets.isEmpty()) {
            dao.upsertWorkspaceWidget(
                WorkspaceWidgetEntity(
                    id = "widget_clock_mtz",
                    widgetTypeCode = HyperWidgetType.CLOCK_MTZ_STATUS.code,
                    appWidgetId = -1,
                    providerComponent = null,
                    customTitle = "MIUI Clock",
                    sortOrder = 0,
                    pageIndex = 0,
                    cellX = 0,
                    cellY = 0,
                    spanX = HyperWidgetType.CLOCK_MTZ_STATUS.defaultSpanX,
                    spanY = HyperWidgetType.CLOCK_MTZ_STATUS.defaultSpanY
                )
            )
        }

        // Seed initial MIUI Big Folder (2x2 at cell 0,2 on page 0) if empty
        val existingFolders = dao.getWorkspaceFoldersOnce()
        if (existingFolders.isEmpty() && rawApps.isNotEmpty()) {
            val utilityPackages = rawApps.reversed().take(5).map { it.packageName }
            dao.upsertWorkspaceFolder(
                WorkspaceFolderEntity(
                    id = "folder_tools",
                    title = "Folder",
                    layoutModeCode = HyperFolderLayoutMode.BIG_FOLDER_2X2.code,
                    packageNamesCsv = utilityPackages.joinToString(","),
                    sortOrder = 8,
                    pageIndex = 0,
                    cellX = 0,
                    cellY = 2,
                    backgroundColorHex = "#8C9AA8"
                )
            )
        }

        // Seed initial workspace and dock shortcuts if empty
        val existingShortcuts = dao.getWorkspaceShortcutsOnce()
        if (existingShortcuts.isEmpty()) {
            val preferredDockPackages = listOf(
                "com.android.dialer",
                "com.android.mms",
                "com.android.chrome",
                "com.android.camera2"
            )
            val dockCandidates = preferredDockPackages.mapNotNull { pkg ->
                rawApps.firstOrNull { it.packageName == pkg }
            }.ifEmpty { rawApps.take(4) }.take(4)

            val dockSet = dockCandidates.map { it.packageName }.toSet()
            dockCandidates.forEachIndexed { idx, app ->
                dao.upsertWorkspaceShortcut(
                    WorkspaceShortcutEntity(
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label,
                        isDockItem = true,
                        pageIndex = 0,
                        sortOrder = idx,
                        cellX = -1,
                        cellY = -1
                    )
                )
            }
            // Place first 6 workspace apps on Page 0 into free cells alongside the 2x2 Big Folder:
            // (2,2), (3,2), (2,3), (3,3) share rows 2 & 3 with the 2x2 folder; (0,4), (1,4) sit on row 4!
            val page0Cells = listOf(
                2 to 2,
                3 to 2,
                2 to 3,
                3 to 3,
                0 to 4,
                1 to 4
            )
            val remainingApps = rawApps.filter { it.packageName !in dockSet }
            remainingApps.take(12).forEachIndexed { idx, app ->
                val pIdx = idx / 6
                val localIdx = idx % 6
                val (cx, cy) = if (pIdx == 0) {
                    page0Cells[localIdx]
                } else {
                    (localIdx % 4) to (localIdx / 4)
                }
                dao.upsertWorkspaceShortcut(
                    WorkspaceShortcutEntity(
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label,
                        isDockItem = false,
                        pageIndex = pIdx,
                        sortOrder = cy * 4 + cx,
                        cellX = cx,
                        cellY = cy
                    )
                )
            }
        } else {
            // Self-heal any legacy workspace shortcuts/folders/widgets that had unset (-1, -1) coordinates
            val config = dao.getEngineSettingsOnce()?.toDomain() ?: LauncherEngineConfig()
            val cols = config.gridColumns.coerceIn(4, 6)
            val rows = config.gridRows.coerceIn(6, 8)
            val allWidgets = dao.getWorkspaceWidgetsOnce()
            val allFolders = dao.getWorkspaceFoldersOnce()
            val allShortcuts = dao.getWorkspaceShortcutsOnce()
            val pages = (allWidgets.map { it.pageIndex } + allFolders.map { it.pageIndex } + allShortcuts.filter { !it.isDockItem }.map { it.pageIndex }).toSortedSet()
            for (pIdx in pages) {
                val occupied = BooleanArray(cols * rows)
                for (w in allWidgets.filter { it.pageIndex == pIdx }.sortedBy { it.sortOrder }) {
                    val sx = w.spanX.coerceIn(1, cols)
                    val sy = w.spanY.coerceIn(1, rows)
                    if (WorkspaceGridAllocator.canFit(occupied, cols, rows, w.cellX, w.cellY, sx, sy)) {
                        WorkspaceGridAllocator.markOccupied(occupied, cols, rows, w.cellX, w.cellY, sx, sy)
                    } else {
                        val free = WorkspaceGridAllocator.findFirstFreeCell(occupied, cols, rows, sx, sy) ?: (0 to 0)
                        WorkspaceGridAllocator.markOccupied(occupied, cols, rows, free.first, free.second, sx, sy)
                        dao.upsertWorkspaceWidget(w.copy(cellX = free.first, cellY = free.second, sortOrder = free.second * cols + free.first))
                    }
                }
                for (f in allFolders.filter { it.pageIndex == pIdx }.sortedBy { it.sortOrder }) {
                    val mode = HyperFolderLayoutMode.fromCode(f.layoutModeCode)
                    val sx = mode.spanX.coerceIn(1, cols)
                    val sy = mode.spanY.coerceIn(1, rows)
                    if (WorkspaceGridAllocator.canFit(occupied, cols, rows, f.cellX, f.cellY, sx, sy)) {
                        WorkspaceGridAllocator.markOccupied(occupied, cols, rows, f.cellX, f.cellY, sx, sy)
                    } else {
                        val free = WorkspaceGridAllocator.findFirstFreeCell(occupied, cols, rows, sx, sy) ?: (0 to 0)
                        WorkspaceGridAllocator.markOccupied(occupied, cols, rows, free.first, free.second, sx, sy)
                        dao.upsertWorkspaceFolder(f.copy(cellX = free.first, cellY = free.second, sortOrder = free.second * cols + free.first))
                    }
                }
                for (s in allShortcuts.filter { !it.isDockItem && it.pageIndex == pIdx }.sortedBy { it.sortOrder }) {
                    if (WorkspaceGridAllocator.canFit(occupied, cols, rows, s.cellX, s.cellY, 1, 1)) {
                        WorkspaceGridAllocator.markOccupied(occupied, cols, rows, s.cellX, s.cellY, 1, 1)
                    } else {
                        val free = WorkspaceGridAllocator.findFirstFreeCell(occupied, cols, rows, 1, 1) ?: (0 to 0)
                        WorkspaceGridAllocator.markOccupied(occupied, cols, rows, free.first, free.second, 1, 1)
                        dao.upsertWorkspaceShortcut(s.copy(cellX = free.first, cellY = free.second, sortOrder = free.second * cols + free.first))
                    }
                }
            }
        }
    }

    /**
     * Builds a 2D occupancy grid (`cols x rows`) for `pageIndex`, optionally excluding
     * specific items (e.g. an item currently being moved or resized).
     */
    private suspend fun buildPageOccupancyGrid(
        pageIndex: Int,
        cols: Int,
        rows: Int,
        excludeWidgetId: String? = null,
        excludeFolderId: String? = null,
        excludeAppPackages: Set<String> = emptySet()
    ): BooleanArray {
        val occupied = BooleanArray(cols * rows)

        val widgetsOnPage = dao.getWorkspaceWidgetsOnce()
            .filter { it.pageIndex == pageIndex && it.id != excludeWidgetId }
            .sortedBy { it.sortOrder }
        for (w in widgetsOnPage) {
            val sx = w.spanX.coerceIn(1, cols)
            val sy = w.spanY.coerceIn(1, rows)
            if (WorkspaceGridAllocator.canFit(occupied, cols, rows, w.cellX, w.cellY, sx, sy)) {
                WorkspaceGridAllocator.markOccupied(occupied, cols, rows, w.cellX, w.cellY, sx, sy)
            } else {
                val fallback = WorkspaceGridAllocator.findFirstFreeCell(occupied, cols, rows, sx, sy)
                if (fallback != null) {
                    WorkspaceGridAllocator.markOccupied(occupied, cols, rows, fallback.first, fallback.second, sx, sy)
                }
            }
        }

        val foldersOnPage = dao.getWorkspaceFoldersOnce()
            .filter { it.pageIndex == pageIndex && it.id != excludeFolderId }
            .sortedBy { it.sortOrder }
        for (f in foldersOnPage) {
            val mode = HyperFolderLayoutMode.fromCode(f.layoutModeCode)
            val sx = mode.spanX.coerceIn(1, cols)
            val sy = mode.spanY.coerceIn(1, rows)
            if (WorkspaceGridAllocator.canFit(occupied, cols, rows, f.cellX, f.cellY, sx, sy)) {
                WorkspaceGridAllocator.markOccupied(occupied, cols, rows, f.cellX, f.cellY, sx, sy)
            } else {
                val fallback = WorkspaceGridAllocator.findFirstFreeCell(occupied, cols, rows, sx, sy)
                if (fallback != null) {
                    WorkspaceGridAllocator.markOccupied(occupied, cols, rows, fallback.first, fallback.second, sx, sy)
                }
            }
        }

        val appsOnPage = dao.getWorkspaceShortcutsOnce()
            .filter { !it.isDockItem && it.pageIndex == pageIndex && it.packageName !in excludeAppPackages }
            .sortedBy { it.sortOrder }
        for (a in appsOnPage) {
            if (WorkspaceGridAllocator.canFit(occupied, cols, rows, a.cellX, a.cellY, 1, 1)) {
                WorkspaceGridAllocator.markOccupied(occupied, cols, rows, a.cellX, a.cellY, 1, 1)
            } else {
                val fallback = WorkspaceGridAllocator.findFirstFreeCell(occupied, cols, rows, 1, 1)
                if (fallback != null) {
                    WorkspaceGridAllocator.markOccupied(occupied, cols, rows, fallback.first, fallback.second, 1, 1)
                }
            }
        }

        return occupied
    }

    /**
     * Background initialization for the remaining built-in `.mtz` themes so startup is never delayed.
     */
    suspend fun initializeSecondaryBuiltInThemes() = withContext(Dispatchers.IO) {
        val extractedRoot = File(context.filesDir, "extracted_themes")
        if (!extractedRoot.exists()) extractedRoot.mkdirs()
        for (spec in sampleGenerator.getBuiltInSpecs()) {
            ensureBuiltInSpecExtractedAndSaved(spec, extractedRoot)
        }
    }

    private suspend fun ensureBuiltInSpecExtractedAndSaved(
        spec: MtzSampleGenerator.BuiltInMtzSpec,
        extractedRoot: File
    ): MtzThemeMetadata {
        metadataMemoryCache[spec.id]?.let { cached ->
            dao.upsertTheme(cached.toEntity())
            return cached
        }
        val targetDir = File(extractedRoot, spec.id)
        val versionMarker = File(targetDir, ".spec_v6_${spec.version}_${spec.iconScale}")
        val metadata = if (targetDir.exists() && versionMarker.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
            parser.loadExtractedTheme(
                themeId = spec.id,
                extractedDir = targetDir,
                archiveFileName = spec.fileName,
                isBuiltIn = true,
                importedAtMillis = 1700000000000L
            )
        } else {
            if (targetDir.exists()) targetDir.deleteRecursively()
            val mtzFile = sampleGenerator.ensureSingleMtzArchiveCreated(spec)
            val parsed = FileInputStream(mtzFile).use { fis ->
                parser.parseAndExtractMtzStream(
                    inputStream = fis,
                    themeId = spec.id,
                    archiveFileName = spec.fileName,
                    targetExtractDir = targetDir,
                    isBuiltIn = true
                )
            }
            runCatching { versionMarker.writeText(spec.version) }
            parsed
        }
        metadataMemoryCache[spec.id] = metadata
        dao.upsertTheme(metadata.toEntity())
        return metadata
    }

    suspend fun getThemeWallpaperBitmap(theme: MtzThemeMetadata): Bitmap? = withContext(Dispatchers.IO) {
        val customFile = File(context.filesDir, "custom_wallpaper.img")
        if (customFile.exists() && customFile.length() > 0L) {
            val customKey = "custom_wp_${customFile.lastModified()}"
            wallpaperMemoryCache[customKey]?.let { return@withContext it }
            val customBmp = BitmapFactory.decodeFile(customFile.absolutePath)
            if (customBmp != null) {
                wallpaperMemoryCache[customKey] = customBmp
                return@withContext customBmp
            }
        }
        wallpaperMemoryCache[theme.id]?.let { return@withContext it }
        val path = theme.wallpaperPath ?: return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        val bmp = BitmapFactory.decodeFile(path) ?: return@withContext null
        wallpaperMemoryCache[theme.id] = bmp
        bmp
    }

    suspend fun setCustomWallpaperUri(uri: Uri?): Boolean = withContext(Dispatchers.IO) {
        val customFile = File(context.filesDir, "custom_wallpaper.img")
        if (uri == null) {
            if (customFile.exists()) customFile.delete()
            return@withContext true
        }
        return@withContext try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                customFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getThemeIconCatalog(theme: MtzThemeMetadata): List<CatalogIconEntry> = withContext(Dispatchers.IO) {
        catalogMemoryCache[theme.id]?.let { return@withContext it }
        val list = theme.directIconsMap.entries.mapNotNull { (key, path) ->
            val bmp = BitmapFactory.decodeFile(path)
            if (bmp != null) CatalogIconEntry(key = key, filePath = path, bitmap = bmp) else null
        }.sortedBy { it.key }
        catalogMemoryCache[theme.id] = list
        list
    }

    suspend fun createHyperFolder(
        title: String,
        layoutMode: HyperFolderLayoutMode,
        packageNames: List<String>,
        pageIndex: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        val config = getEngineConfigOnce()
        val cols = config.gridColumns
        val rows = config.gridRows
        val targetPage = pageIndex.coerceAtLeast(0)
        val occupied = buildPageOccupancyGrid(
            pageIndex = targetPage,
            cols = cols,
            rows = rows
        )
        val freeCell = WorkspaceGridAllocator.findFirstFreeCell(
            occupied = occupied,
            cols = cols,
            rows = rows,
            spanX = layoutMode.spanX,
            spanY = layoutMode.spanY
        ) ?: return@withContext false

        val folderId = "folder_" + UUID.randomUUID().toString().take(8)
        dao.upsertWorkspaceFolder(
            WorkspaceFolderEntity(
                id = folderId,
                title = title.ifBlank { "Folder" },
                layoutModeCode = layoutMode.code,
                packageNamesCsv = packageNames.distinct().joinToString(","),
                sortOrder = freeCell.second * cols + freeCell.first,
                pageIndex = targetPage,
                cellX = freeCell.first,
                cellY = freeCell.second,
                backgroundColorHex = "#8C9AA8"
            )
        )
        true
    }

    suspend fun updateFolderLayoutMode(folderId: String, mode: HyperFolderLayoutMode): Boolean =
        withContext(Dispatchers.IO) {
            val folder = dao.getWorkspaceFoldersOnce().firstOrNull { it.id == folderId } ?: return@withContext false
            val config = getEngineConfigOnce()
            val cols = config.gridColumns
            val rows = config.gridRows
            val occupied = buildPageOccupancyGrid(
                pageIndex = folder.pageIndex,
                cols = cols,
                rows = rows,
                excludeFolderId = folderId
            )
            val targetCell = WorkspaceGridAllocator.findNearestFreeCell(
                occupied = occupied,
                cols = cols,
                rows = rows,
                preferredCellX = folder.cellX.coerceAtLeast(0),
                preferredCellY = folder.cellY.coerceAtLeast(0),
                spanX = mode.spanX,
                spanY = mode.spanY
            ) ?: return@withContext false

            dao.upsertWorkspaceFolder(
                folder.copy(
                    layoutModeCode = mode.code,
                    cellX = targetCell.first,
                    cellY = targetCell.second,
                    sortOrder = targetCell.second * cols + targetCell.first
                )
            )
            true
        }

    suspend fun updateFolderSpan(folderId: String, spanX: Int, spanY: Int): Boolean =
        withContext(Dispatchers.IO) {
            val mode = HyperFolderLayoutMode.fromSpan(spanX, spanY)
            updateFolderLayoutMode(folderId, mode)
        }

    suspend fun cycleFolderBackgroundColor(folderId: String) =
        withContext(Dispatchers.IO) {
            val palette = listOf(
                "#8C9AA8",
                "#2C3E50",
                "#3B5998",
                "#2E6B5E",
                "#6C5B7B",
                "#7F5A58"
            )
            val folder = dao.getWorkspaceFoldersOnce().firstOrNull { it.id == folderId } ?: return@withContext
            val currentIdx = palette.indexOfFirst { it.equals(folder.backgroundColorHex, ignoreCase = true) }
            val nextHex = palette[(currentIdx + 1) % palette.size]
            dao.upsertWorkspaceFolder(folder.copy(backgroundColorHex = nextHex))
        }

    suspend fun moveFolderToPageAndOrder(
        folderId: String,
        pageIndex: Int,
        sortOrder: Int,
        preferredCellX: Int = -1,
        preferredCellY: Int = -1
    ): Boolean = withContext(Dispatchers.IO) {
        val folder = dao.getWorkspaceFoldersOnce().firstOrNull { it.id == folderId } ?: return@withContext false
        val config = getEngineConfigOnce()
        val cols = config.gridColumns
        val rows = config.gridRows
        val mode = HyperFolderLayoutMode.fromCode(folder.layoutModeCode)
        val targetPage = pageIndex.coerceAtLeast(0)
        val occupied = buildPageOccupancyGrid(
            pageIndex = targetPage,
            cols = cols,
            rows = rows,
            excludeFolderId = folderId
        )
        val prefX = if (preferredCellX >= 0) preferredCellX else (sortOrder % cols)
        val prefY = if (preferredCellY >= 0) preferredCellY else (sortOrder / cols)
        val targetCell = WorkspaceGridAllocator.findNearestFreeCell(
            occupied = occupied,
            cols = cols,
            rows = rows,
            preferredCellX = prefX,
            preferredCellY = prefY,
            spanX = mode.spanX,
            spanY = mode.spanY
        ) ?: return@withContext false

        dao.upsertWorkspaceFolder(
            folder.copy(
                pageIndex = targetPage,
                cellX = targetCell.first,
                cellY = targetCell.second,
                sortOrder = targetCell.second * cols + targetCell.first
            )
        )
        compactEmptyWorkspacePages()
        true
    }

    suspend fun moveWidgetToPageAndCell(
        widgetId: String,
        pageIndex: Int,
        preferredCellX: Int,
        preferredCellY: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val widget = dao.getWorkspaceWidgetsOnce().firstOrNull { it.id == widgetId } ?: return@withContext false
        val config = getEngineConfigOnce()
        val cols = config.gridColumns
        val rows = config.gridRows
        val targetPage = pageIndex.coerceAtLeast(0)
        val occupied = buildPageOccupancyGrid(
            pageIndex = targetPage,
            cols = cols,
            rows = rows,
            excludeWidgetId = widgetId
        )
        val targetCell = WorkspaceGridAllocator.findNearestFreeCell(
            occupied = occupied,
            cols = cols,
            rows = rows,
            preferredCellX = preferredCellX,
            preferredCellY = preferredCellY,
            spanX = widget.spanX.coerceIn(1, cols),
            spanY = widget.spanY.coerceIn(1, rows)
        ) ?: return@withContext false

        dao.upsertWorkspaceWidget(
            widget.copy(
                pageIndex = targetPage,
                cellX = targetCell.first,
                cellY = targetCell.second,
                sortOrder = targetCell.second * cols + targetCell.first
            )
        )
        compactEmptyWorkspacePages()
        true
    }

    private fun resolveWidgetResizeCapabilities(widget: WorkspaceWidgetEntity): Pair<Boolean, Boolean> {
        val wType = HyperWidgetType.fromCode(widget.widgetTypeCode)
        if (wType != HyperWidgetType.NATIVE_APPWIDGET) {
            return true to true
        }
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providerComp = widget.providerComponent?.let { ComponentName.unflattenFromString(it) }
            val info = if (widget.appWidgetId >= 0) {
                appWidgetManager.getAppWidgetInfo(widget.appWidgetId)
            } else {
                appWidgetManager.installedProviders.firstOrNull { it.provider == providerComp }
            }
            if (info == null) {
                true to true
            } else {
                val canH = (info.resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
                val canV = (info.resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_VERTICAL) != 0
                if (!canH && !canV) true to true else (canH to canV)
            }
        } catch (_: Exception) {
            true to true
        }
    }

    suspend fun resizeWidgetSpan(
        widgetId: String,
        targetSpanX: Int,
        targetSpanY: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val widget = dao.getWorkspaceWidgetsOnce().firstOrNull { it.id == widgetId } ?: return@withContext false
        val config = getEngineConfigOnce()
        val cols = config.gridColumns.coerceIn(4, 6)
        val rows = config.gridRows.coerceIn(6, 8)
        val wType = HyperWidgetType.fromCode(widget.widgetTypeCode)
        val (canH, canV) = resolveWidgetResizeCapabilities(widget)
        val minSpanX = if (wType == HyperWidgetType.NATIVE_APPWIDGET) 1 else 2
        val maxSpanY = 4.coerceAtMost(rows)

        val clampedSpanX = if (canH) targetSpanX.coerceIn(minSpanX, cols) else widget.spanX.coerceIn(1, cols)
        val clampedSpanY = if (canV) targetSpanY.coerceIn(1, maxSpanY) else widget.spanY.coerceIn(1, maxSpanY)

        val occupied = buildPageOccupancyGrid(
            pageIndex = widget.pageIndex.coerceAtLeast(0),
            cols = cols,
            rows = rows,
            excludeWidgetId = widgetId
        )
        val targetCell = WorkspaceGridAllocator.findNearestFreeCell(
            occupied = occupied,
            cols = cols,
            rows = rows,
            preferredCellX = widget.cellX.coerceAtLeast(0),
            preferredCellY = widget.cellY.coerceAtLeast(0),
            spanX = clampedSpanX,
            spanY = clampedSpanY
        ) ?: return@withContext false

        dao.upsertWorkspaceWidget(
            widget.copy(
                cellX = targetCell.first,
                cellY = targetCell.second,
                spanX = clampedSpanX,
                spanY = clampedSpanY,
                sortOrder = targetCell.second * cols + targetCell.first
            )
        )
        true
    }

    suspend fun cycleWidgetSize(widgetId: String): Boolean = withContext(Dispatchers.IO) {
        val widget = dao.getWorkspaceWidgetsOnce().firstOrNull { it.id == widgetId } ?: return@withContext false
        val config = getEngineConfigOnce()
        val cols = config.gridColumns.coerceIn(4, 6)
        val rows = config.gridRows.coerceIn(6, 8)
        val wType = HyperWidgetType.fromCode(widget.widgetTypeCode)
        val (canH, canV) = resolveWidgetResizeCapabilities(widget)

        val basePresets = buildList {
            if (wType == HyperWidgetType.NATIVE_APPWIDGET) {
                add(1 to 1)
                add(2 to 1)
                add(2 to 2)
                add(3 to 2)
                add(4 to 1)
                add(4 to 2)
                add(4 to 3)
                add(4 to 4)
            } else {
                add(2 to 1)
                add(2 to 2)
                add(3 to 1)
                add(3 to 2)
                add(4 to 1)
                add(4 to 2)
                add(4 to 3)
                add(4 to 4)
            }
            if (cols >= 5) {
                add(5 to 1)
                add(5 to 2)
                add(5 to 3)
                add(5 to 4)
            }
            if (cols >= 6) {
                add(6 to 1)
                add(6 to 2)
                add(6 to 3)
                add(6 to 4)
            }
        }.filter { (sx, sy) ->
            (canH || sx == widget.spanX) && (canV || sy == widget.spanY) && sx <= cols && sy <= rows
        }.distinct()

        if (basePresets.isEmpty()) return@withContext false

        val currentPair = widget.spanX.coerceIn(1, cols) to widget.spanY.coerceIn(1, rows)
        val currentIdx = basePresets.indexOf(currentPair)
        val occupied = buildPageOccupancyGrid(
            pageIndex = widget.pageIndex.coerceAtLeast(0),
            cols = cols,
            rows = rows,
            excludeWidgetId = widgetId
        )

        for (step in 1..basePresets.size) {
            val candidate = basePresets[(currentIdx + step).mod(basePresets.size)]
            if (candidate == currentPair) continue
            val targetCell = WorkspaceGridAllocator.findNearestFreeCell(
                occupied = occupied,
                cols = cols,
                rows = rows,
                preferredCellX = widget.cellX.coerceAtLeast(0),
                preferredCellY = widget.cellY.coerceAtLeast(0),
                spanX = candidate.first,
                spanY = candidate.second
            )
            if (targetCell != null) {
                dao.upsertWorkspaceWidget(
                    widget.copy(
                        cellX = targetCell.first,
                        cellY = targetCell.second,
                        spanX = candidate.first,
                        spanY = candidate.second,
                        sortOrder = targetCell.second * cols + targetCell.first
                    )
                )
                return@withContext true
            }
        }
        false
    }

    private suspend fun insertOrMoveDockShortcut(
        app: InstalledAppItem,
        targetSortOrder: Int,
        maxDockCount: Int
    ): Boolean {
        val allShortcuts = dao.getWorkspaceShortcutsOnce()
        val otherDockShortcuts = allShortcuts
            .filter { it.isDockItem && it.packageName != app.packageName }
            .sortedWith(compareBy({ it.sortOrder }, { it.packageName }))
            .toMutableList()

        if (otherDockShortcuts.size >= maxDockCount) {
            return false
        }

        val insertIndex = targetSortOrder.coerceIn(0, otherDockShortcuts.size)
        val newEntity = WorkspaceShortcutEntity(
            packageName = app.packageName,
            activityName = app.activityName,
            label = app.label,
            isDockItem = true,
            pageIndex = 0,
            sortOrder = insertIndex,
            cellX = -1,
            cellY = -1
        )
        otherDockShortcuts.add(insertIndex, newEntity)
        otherDockShortcuts.forEachIndexed { idx, shortcut ->
            dao.upsertWorkspaceShortcut(
                shortcut.copy(
                    isDockItem = true,
                    pageIndex = 0,
                    sortOrder = idx,
                    cellX = -1,
                    cellY = -1
                )
            )
        }
        return true
    }

    private suspend fun normalizeDockSortOrders() {
        val dockShortcuts = dao.getWorkspaceShortcutsOnce()
            .filter { it.isDockItem }
            .sortedWith(compareBy({ it.sortOrder }, { it.packageName }))
        dockShortcuts.forEachIndexed { idx, shortcut ->
            if (shortcut.sortOrder != idx || shortcut.cellX != -1 || shortcut.cellY != -1 || shortcut.pageIndex != 0) {
                dao.upsertWorkspaceShortcut(
                    shortcut.copy(
                        pageIndex = 0,
                        sortOrder = idx,
                        cellX = -1,
                        cellY = -1
                    )
                )
            }
        }
    }

    suspend fun moveAppShortcut(
        app: InstalledAppItem,
        targetPage: Int,
        targetSortOrder: Int,
        isDockItem: Boolean,
        preferredCellX: Int = -1,
        preferredCellY: Int = -1
    ): Boolean = withContext(Dispatchers.IO) {
        val config = getEngineConfigOnce()
        val cols = config.gridColumns.coerceIn(4, 6)
        val rows = config.gridRows.coerceIn(6, 8)

        if (isDockItem) {
            val ok = insertOrMoveDockShortcut(
                app = app,
                targetSortOrder = targetSortOrder,
                maxDockCount = cols
            )
            if (ok) {
                compactEmptyWorkspacePages()
            }
            return@withContext ok
        }

        val safePage = targetPage.coerceAtLeast(0)
        val occupied = buildPageOccupancyGrid(
            pageIndex = safePage,
            cols = cols,
            rows = rows,
            excludeAppPackages = setOf(app.packageName)
        )
        val prefX = if (preferredCellX >= 0) preferredCellX else (targetSortOrder.coerceAtLeast(0) % cols)
        val prefY = if (preferredCellY >= 0) preferredCellY else (targetSortOrder.coerceAtLeast(0) / cols)
        val targetCell = WorkspaceGridAllocator.findNearestFreeCell(
            occupied = occupied,
            cols = cols,
            rows = rows,
            preferredCellX = prefX,
            preferredCellY = prefY,
            spanX = 1,
            spanY = 1
        ) ?: return@withContext false

        dao.upsertWorkspaceShortcut(
            WorkspaceShortcutEntity(
                packageName = app.packageName,
                activityName = app.activityName,
                label = app.label,
                isDockItem = false,
                pageIndex = safePage,
                sortOrder = targetCell.second * cols + targetCell.first,
                cellX = targetCell.first,
                cellY = targetCell.second
            )
        )
        normalizeDockSortOrders()
        compactEmptyWorkspacePages()
        true
    }

    suspend fun removeAppFromWorkspace(packageName: String) = withContext(Dispatchers.IO) {
        dao.removeWorkspaceShortcut(packageName)
        // Also remove from any folder if present, applying auto-dissolve if <= 1 icon remains
        val folders = dao.getWorkspaceFoldersOnce()
        for (folder in folders) {
            val pkgs = folder.packageNamesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (packageName in pkgs) {
                val remaining = pkgs.filter { it != packageName }
                updateOrDissolveFolder(folder, remaining)
            }
        }
        normalizeDockSortOrders()
        compactEmptyWorkspacePages()
    }

    suspend fun swapWorkspaceApps(pkgA: String, pkgB: String) = withContext(Dispatchers.IO) {
        if (pkgA == pkgB) return@withContext
        val all = dao.getWorkspaceShortcutsOnce()
        val entA = all.firstOrNull { it.packageName == pkgA } ?: return@withContext
        val entB = all.firstOrNull { it.packageName == pkgB } ?: return@withContext
        dao.upsertWorkspaceShortcut(
            entA.copy(
                isDockItem = entB.isDockItem,
                pageIndex = entB.pageIndex,
                sortOrder = entB.sortOrder,
                cellX = entB.cellX,
                cellY = entB.cellY
            )
        )
        dao.upsertWorkspaceShortcut(
            entB.copy(
                isDockItem = entA.isDockItem,
                pageIndex = entA.pageIndex,
                sortOrder = entA.sortOrder,
                cellX = entA.cellX,
                cellY = entA.cellY
            )
        )
    }

    suspend fun mergeAppsIntoNewFolder(
        draggedApp: InstalledAppItem,
        targetApp: InstalledAppItem,
        pageIndex: Int,
        sourceFolderId: String? = null
    ) = withContext(Dispatchers.IO) {
        if (draggedApp.packageName == targetApp.packageName) return@withContext
        if (sourceFolderId != null) {
            removePackageFromFolderWithAutoDissolve(sourceFolderId, draggedApp.packageName)
        }
        val existingShortcuts = dao.getWorkspaceShortcutsOnce()
        val targetShortcut = existingShortcuts.firstOrNull { it.packageName == targetApp.packageName }
        val config = getEngineConfigOnce()
        val cols = config.gridColumns
        val rows = config.gridRows
        val safePage = pageIndex.coerceAtLeast(0)

        dao.removeWorkspaceShortcut(draggedApp.packageName)
        dao.removeWorkspaceShortcut(targetApp.packageName)

        val occupied = buildPageOccupancyGrid(
            pageIndex = safePage,
            cols = cols,
            rows = rows,
            excludeAppPackages = setOf(draggedApp.packageName, targetApp.packageName)
        )
        val prefX = targetShortcut?.cellX?.takeIf { it >= 0 } ?: targetApp.cellX.coerceAtLeast(0)
        val prefY = targetShortcut?.cellY?.takeIf { it >= 0 } ?: targetApp.cellY.coerceAtLeast(0)
        val folderCell = WorkspaceGridAllocator.findNearestFreeCell(
            occupied = occupied,
            cols = cols,
            rows = rows,
            preferredCellX = prefX,
            preferredCellY = prefY,
            spanX = 1,
            spanY = 1
        ) ?: (0 to 0)

        val folderId = "folder_" + UUID.randomUUID().toString().take(8)
        dao.upsertWorkspaceFolder(
            WorkspaceFolderEntity(
                id = folderId,
                title = "Folder",
                layoutModeCode = HyperFolderLayoutMode.REGULAR_1X1.code,
                packageNamesCsv = listOf(targetApp.packageName, draggedApp.packageName).distinct().joinToString(","),
                sortOrder = folderCell.second * cols + folderCell.first,
                pageIndex = safePage,
                cellX = folderCell.first,
                cellY = folderCell.second,
                backgroundColorHex = "#8C9AA8"
            )
        )
        normalizeDockSortOrders()
        compactEmptyWorkspacePages()
    }

    suspend fun moveAppIntoFolder(
        folderId: String,
        app: InstalledAppItem,
        sourceFolderId: String? = null
    ) = withContext(Dispatchers.IO) {
        if (sourceFolderId != null && sourceFolderId != folderId) {
            removePackageFromFolderWithAutoDissolve(sourceFolderId, app.packageName)
        }
        val folder = dao.getWorkspaceFoldersOnce().firstOrNull { it.id == folderId } ?: return@withContext
        val currentList = folder.packageNamesCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        if (app.packageName !in currentList) {
            currentList.add(app.packageName)
            dao.upsertWorkspaceFolder(folder.copy(packageNamesCsv = currentList.joinToString(",")))
        }
        dao.removeWorkspaceShortcut(app.packageName)
        normalizeDockSortOrders()
        compactEmptyWorkspacePages()
    }

    suspend fun extractAppFromFolderToWorkspace(
        folderId: String,
        app: InstalledAppItem,
        targetPage: Int,
        targetSortOrder: Int,
        isDockItem: Boolean,
        preferredCellX: Int = -1,
        preferredCellY: Int = -1
    ): Boolean = withContext(Dispatchers.IO) {
        val config = getEngineConfigOnce()
        val cols = config.gridColumns.coerceIn(4, 6)
        val rows = config.gridRows.coerceIn(6, 8)

        if (isDockItem) {
            val ok = insertOrMoveDockShortcut(
                app = app,
                targetSortOrder = targetSortOrder,
                maxDockCount = cols
            )
            if (ok) {
                removePackageFromFolderWithAutoDissolve(folderId, app.packageName)
                compactEmptyWorkspacePages()
            }
            return@withContext ok
        }

        val safePage = targetPage.coerceAtLeast(0)
        val occupied = buildPageOccupancyGrid(
            pageIndex = safePage,
            cols = cols,
            rows = rows,
            excludeAppPackages = setOf(app.packageName)
        )
        val prefX = if (preferredCellX >= 0) preferredCellX else (targetSortOrder.coerceAtLeast(0) % cols)
        val prefY = if (preferredCellY >= 0) preferredCellY else (targetSortOrder.coerceAtLeast(0) / cols)
        val targetCell = WorkspaceGridAllocator.findNearestFreeCell(
            occupied = occupied,
            cols = cols,
            rows = rows,
            preferredCellX = prefX,
            preferredCellY = prefY,
            spanX = 1,
            spanY = 1
        ) ?: return@withContext false

        dao.upsertWorkspaceShortcut(
            WorkspaceShortcutEntity(
                packageName = app.packageName,
                activityName = app.activityName,
                label = app.label,
                isDockItem = false,
                pageIndex = safePage,
                sortOrder = targetCell.second * cols + targetCell.first,
                cellX = targetCell.first,
                cellY = targetCell.second
            )
        )
        removePackageFromFolderWithAutoDissolve(folderId, app.packageName)
        normalizeDockSortOrders()
        compactEmptyWorkspacePages()
        true
    }

    private suspend fun removePackageFromFolderWithAutoDissolve(folderId: String, packageName: String) {
        val folder = dao.getWorkspaceFoldersOnce().firstOrNull { it.id == folderId } ?: return
        val remaining = folder.packageNamesCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != packageName }
        updateOrDissolveFolder(folder, remaining)
    }

    private suspend fun updateOrDissolveFolder(folder: WorkspaceFolderEntity, remainingPackages: List<String>) {
        if (remainingPackages.isEmpty()) {
            dao.deleteWorkspaceFolder(folder.id)
        } else if (remainingPackages.size == 1) {
            // Auto-dissolve folder when 1 icon remains, placing the remaining icon onto the folder's cell on workspace page
            val lastPkg = remainingPackages.first()
            dao.deleteWorkspaceFolder(folder.id)
            val rawApps = queryRawLaunchableApps()
            val rawApp = rawApps.firstOrNull { it.packageName == lastPkg }
            val config = getEngineConfigOnce()
            val cols = config.gridColumns
            val rows = config.gridRows
            val occupied = buildPageOccupancyGrid(
                pageIndex = folder.pageIndex.coerceAtLeast(0),
                cols = cols,
                rows = rows,
                excludeFolderId = folder.id,
                excludeAppPackages = setOf(lastPkg)
            )
            val freeCell = WorkspaceGridAllocator.findNearestFreeCell(
                occupied = occupied,
                cols = cols,
                rows = rows,
                preferredCellX = folder.cellX.coerceAtLeast(0),
                preferredCellY = folder.cellY.coerceAtLeast(0),
                spanX = 1,
                spanY = 1
            ) ?: (0 to 0)
            dao.upsertWorkspaceShortcut(
                WorkspaceShortcutEntity(
                    packageName = lastPkg,
                    activityName = rawApp?.activityName ?: lastPkg,
                    label = rawApp?.label ?: lastPkg.substringAfterLast('.'),
                    isDockItem = false,
                    pageIndex = folder.pageIndex.coerceAtLeast(0),
                    sortOrder = freeCell.second * cols + freeCell.first,
                    cellX = freeCell.first,
                    cellY = freeCell.second
                )
            )
        } else {
            dao.upsertWorkspaceFolder(folder.copy(packageNamesCsv = remainingPackages.joinToString(",")))
        }
    }

    suspend fun batchMoveAppsToPage(packageNames: List<String>, targetPage: Int) =
        withContext(Dispatchers.IO) {
            if (packageNames.isEmpty()) return@withContext
            val rawByPkg = queryRawLaunchableApps().associateBy { it.packageName }
            val existingShortcuts = dao.getWorkspaceShortcutsOnce().associateBy { it.packageName }
            val currentFolders = dao.getWorkspaceFoldersOnce()

            // Remove selected packages from any folders they belong to
            for (folder in currentFolders) {
                val pkgs = folder.packageNamesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (pkgs.any { it in packageNames }) {
                    val remaining = pkgs.filter { it !in packageNames }
                    updateOrDissolveFolder(folder, remaining)
                }
            }

            val config = getEngineConfigOnce()
            val cols = config.gridColumns
            val rows = config.gridRows
            var currentPageIdx = targetPage.coerceAtLeast(0)
            var occupied = buildPageOccupancyGrid(
                pageIndex = currentPageIdx,
                cols = cols,
                rows = rows,
                excludeAppPackages = packageNames.toSet()
            )

            for (pkg in packageNames.distinct()) {
                var freeCell = WorkspaceGridAllocator.findFirstFreeCell(occupied, cols, rows, 1, 1)
                if (freeCell == null) {
                    currentPageIdx += 1
                    occupied = buildPageOccupancyGrid(
                        pageIndex = currentPageIdx,
                        cols = cols,
                        rows = rows,
                        excludeAppPackages = packageNames.toSet()
                    )
                    freeCell = WorkspaceGridAllocator.findFirstFreeCell(occupied, cols, rows, 1, 1) ?: (0 to 0)
                }
                WorkspaceGridAllocator.markOccupied(occupied, cols, rows, freeCell.first, freeCell.second, 1, 1)

                val existing = existingShortcuts[pkg]
                val raw = rawByPkg[pkg]
                dao.upsertWorkspaceShortcut(
                    WorkspaceShortcutEntity(
                        packageName = pkg,
                        activityName = existing?.activityName ?: raw?.activityName ?: pkg,
                        label = existing?.label ?: raw?.label ?: pkg.substringAfterLast('.'),
                        isDockItem = false,
                        pageIndex = currentPageIdx,
                        sortOrder = freeCell.second * cols + freeCell.first,
                        cellX = freeCell.first,
                        cellY = freeCell.second
                    )
                )
            }
            compactEmptyWorkspacePages()
        }

    suspend fun alignWorkspacePage(pageIndex: Int, alignBottom: Boolean) =
        withContext(Dispatchers.IO) {
            val config = getEngineConfigOnce()
            val cols = config.gridColumns
            val rows = config.gridRows
            val occupied = BooleanArray(cols * rows)

            val widgetsOnPage = dao.getWorkspaceWidgetsOnce()
                .filter { it.pageIndex == pageIndex }
                .sortedBy { it.sortOrder }
            for (w in widgetsOnPage) {
                val cell = WorkspaceGridAllocator.findFirstFreeCell(
                    occupied = occupied,
                    cols = cols,
                    rows = rows,
                    spanX = w.spanX,
                    spanY = w.spanY,
                    alignBottom = alignBottom
                ) ?: (w.cellX.coerceAtLeast(0) to w.cellY.coerceAtLeast(0))
                WorkspaceGridAllocator.markOccupied(occupied, cols, rows, cell.first, cell.second, w.spanX, w.spanY)
                dao.upsertWorkspaceWidget(
                    w.copy(
                        cellX = cell.first,
                        cellY = cell.second,
                        sortOrder = cell.second * cols + cell.first
                    )
                )
            }

            val foldersOnPage = dao.getWorkspaceFoldersOnce()
                .filter { it.pageIndex == pageIndex }
                .sortedBy { it.sortOrder }
            for (f in foldersOnPage) {
                val mode = HyperFolderLayoutMode.fromCode(f.layoutModeCode)
                val cell = WorkspaceGridAllocator.findFirstFreeCell(
                    occupied = occupied,
                    cols = cols,
                    rows = rows,
                    spanX = mode.spanX,
                    spanY = mode.spanY,
                    alignBottom = alignBottom
                ) ?: (f.cellX.coerceAtLeast(0) to f.cellY.coerceAtLeast(0))
                WorkspaceGridAllocator.markOccupied(occupied, cols, rows, cell.first, cell.second, mode.spanX, mode.spanY)
                dao.upsertWorkspaceFolder(
                    f.copy(
                        cellX = cell.first,
                        cellY = cell.second,
                        sortOrder = cell.second * cols + cell.first
                    )
                )
            }

            val shortcutsOnPage = dao.getWorkspaceShortcutsOnce()
                .filter { !it.isDockItem && it.pageIndex == pageIndex }
                .sortedBy { it.sortOrder }
            for (s in shortcutsOnPage) {
                val cell = WorkspaceGridAllocator.findFirstFreeCell(
                    occupied = occupied,
                    cols = cols,
                    rows = rows,
                    spanX = 1,
                    spanY = 1,
                    alignBottom = alignBottom
                ) ?: (s.cellX.coerceAtLeast(0) to s.cellY.coerceAtLeast(0))
                WorkspaceGridAllocator.markOccupied(occupied, cols, rows, cell.first, cell.second, 1, 1)
                dao.upsertWorkspaceShortcut(
                    s.copy(
                        cellX = cell.first,
                        cellY = cell.second,
                        sortOrder = cell.second * cols + cell.first
                    )
                )
            }

            updateEngineConfig { current ->
                val updatedSet = current.bottomAlignedPages.toMutableSet()
                if (alignBottom) {
                    updatedSet.add(pageIndex)
                } else {
                    updatedSet.remove(pageIndex)
                }
                current.copy(bottomAlignedPages = updatedSet)
            }
        }

    /**
     * Dynamically removes any empty workspace pages (pages with 0 shortcuts, 0 folders, and 0 widgets),
     * shifting higher page indices down sequentially while always keeping at least page 0.
     */
    suspend fun compactEmptyWorkspacePages() = withContext(Dispatchers.IO) {
        val allShortcuts = dao.getWorkspaceShortcutsOnce()
        val workspaceShortcuts = allShortcuts.filter { !it.isDockItem }
        val allFolders = dao.getWorkspaceFoldersOnce()
        val allWidgets = dao.getWorkspaceWidgetsOnce()

        val activePages = sortedSetOf<Int>()
        allWidgets.forEach { activePages.add(it.pageIndex.coerceAtLeast(0)) }
        workspaceShortcuts.forEach { activePages.add(it.pageIndex.coerceAtLeast(0)) }
        allFolders.forEach { activePages.add(it.pageIndex.coerceAtLeast(0)) }

        if (activePages.isEmpty()) {
            activePages.add(0)
        }

        val pageMapping = activePages.toList().mapIndexed { newIdx, oldIdx -> oldIdx to newIdx }.toMap()
        val anyPageChanged = pageMapping.any { (oldIdx, newIdx) -> oldIdx != newIdx }
        if (!anyPageChanged) return@withContext

        for (shortcut in workspaceShortcuts) {
            val mappedPage = pageMapping[shortcut.pageIndex] ?: 0
            if (mappedPage != shortcut.pageIndex) {
                dao.upsertWorkspaceShortcut(shortcut.copy(pageIndex = mappedPage))
            }
        }
        for (folder in allFolders) {
            val mappedPage = pageMapping[folder.pageIndex] ?: 0
            if (mappedPage != folder.pageIndex) {
                dao.upsertWorkspaceFolder(folder.copy(pageIndex = mappedPage))
            }
        }
        for (widget in allWidgets) {
            val mappedPage = pageMapping[widget.pageIndex] ?: 0
            if (mappedPage != widget.pageIndex) {
                dao.upsertWorkspaceWidget(widget.copy(pageIndex = mappedPage))
            }
        }
    }

    suspend fun renameFolder(folderId: String, newTitle: String) =
        withContext(Dispatchers.IO) {
            val folder = dao.getWorkspaceFoldersOnce().firstOrNull { it.id == folderId } ?: return@withContext
            dao.upsertWorkspaceFolder(folder.copy(title = newTitle.ifBlank { folder.title }))
        }

    suspend fun toggleAppInFolder(folderId: String, packageName: String) =
        withContext(Dispatchers.IO) {
            val folder = dao.getWorkspaceFoldersOnce().firstOrNull { it.id == folderId } ?: return@withContext
            val currentList = folder.packageNamesCsv.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
            if (packageName in currentList) {
                currentList.remove(packageName)
                updateOrDissolveFolder(folder, currentList)
            } else {
                currentList.add(packageName)
                dao.removeWorkspaceShortcut(packageName)
                dao.upsertWorkspaceFolder(folder.copy(packageNamesCsv = currentList.joinToString(",")))
            }
            compactEmptyWorkspacePages()
        }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        dao.deleteWorkspaceFolder(folderId)
        compactEmptyWorkspacePages()
    }

    suspend fun addBuiltInWidget(type: HyperWidgetType, pageIndex: Int = 0): Boolean = withContext(Dispatchers.IO) {
        val config = getEngineConfigOnce()
        val cols = config.gridColumns
        val rows = config.gridRows
        val targetPage = pageIndex.coerceAtLeast(0)
        val spanX = type.defaultSpanX.coerceIn(1, cols)
        val spanY = type.defaultSpanY.coerceIn(1, rows)
        val occupied = buildPageOccupancyGrid(
            pageIndex = targetPage,
            cols = cols,
            rows = rows
        )
        val freeCell = WorkspaceGridAllocator.findFirstFreeCell(
            occupied = occupied,
            cols = cols,
            rows = rows,
            spanX = spanX,
            spanY = spanY
        ) ?: return@withContext false

        dao.upsertWorkspaceWidget(
            WorkspaceWidgetEntity(
                id = "widget_" + UUID.randomUUID().toString().take(8),
                widgetTypeCode = type.code,
                appWidgetId = -1,
                providerComponent = null,
                customTitle = type.title,
                sortOrder = freeCell.second * cols + freeCell.first,
                pageIndex = targetPage,
                cellX = freeCell.first,
                cellY = freeCell.second,
                spanX = spanX,
                spanY = spanY
            )
        )
        true
    }

    suspend fun addNativeSystemWidget(
        appWidgetId: Int,
        providerComponent: String,
        label: String,
        pageIndex: Int = 0,
        minWidthDp: Int = 220,
        minHeightDp: Int = 110
    ): Boolean = withContext(Dispatchers.IO) {
        val config = getEngineConfigOnce()
        val cols = config.gridColumns
        val rows = config.gridRows
        val targetPage = pageIndex.coerceAtLeast(0)
        val spanX = (minWidthDp / 80).coerceIn(2, cols)
        val spanY = (minHeightDp / 80).coerceIn(1, 3)
        val occupied = buildPageOccupancyGrid(
            pageIndex = targetPage,
            cols = cols,
            rows = rows
        )
        val freeCell = WorkspaceGridAllocator.findFirstFreeCell(
            occupied = occupied,
            cols = cols,
            rows = rows,
            spanX = spanX,
            spanY = spanY
        ) ?: return@withContext false

        dao.upsertWorkspaceWidget(
            WorkspaceWidgetEntity(
                id = "appwidget_${if (appWidgetId >= 0) appWidgetId else UUID.randomUUID().toString().take(6)}",
                widgetTypeCode = HyperWidgetType.NATIVE_APPWIDGET.code,
                appWidgetId = appWidgetId,
                providerComponent = providerComponent,
                customTitle = label,
                sortOrder = freeCell.second * cols + freeCell.first,
                pageIndex = targetPage,
                cellX = freeCell.first,
                cellY = freeCell.second,
                spanX = spanX,
                spanY = spanY
            )
        )
        true
    }

    suspend fun removeWidget(widgetId: String) = withContext(Dispatchers.IO) {
        dao.deleteWorkspaceWidget(widgetId)
        compactEmptyWorkspacePages()
    }

    fun readDeviceTelemetry(): DeviceTelemetrySnapshot {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 85
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 295) ?: 295
        val tempCelsius = tempTenths / 10f

        val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actMgr?.getMemoryInfo(memInfo)
        val totalRamMb = if (memInfo.totalMem > 0) memInfo.totalMem / (1024L * 1024L) else 4096L
        val availRamMb = if (memInfo.availMem > 0) memInfo.availMem / (1024L * 1024L) else 1800L
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(256L)

        var totalStorageGb = 64f
        var usedStorageGb = 18.5f
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.blockSizeLong * stat.blockCountLong
            val availBytes = stat.blockSizeLong * stat.availableBlocksLong
            totalStorageGb = (totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat().coerceAtLeast(1f)
            usedStorageGb = ((totalBytes - availBytes).toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat().coerceAtLeast(0.1f)
        } catch (_: Exception) {
        }

        val hasLocationPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCalendarPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        var cityName = "San Francisco"
        var latitude = 37.7749
        if (hasLocationPerm) {
            try {
                val locMgr = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val providers = locMgr?.getProviders(true).orEmpty()
                val lastLoc = providers.mapNotNull { provider ->
                    runCatching { locMgr?.getLastKnownLocation(provider) }.getOrNull()
                }.maxByOrNull { it.time }
                if (lastLoc != null) {
                    latitude = lastLoc.latitude
                    @Suppress("DEPRECATION")
                    val addresses = runCatching {
                        Geocoder(context, Locale.getDefault()).getFromLocation(lastLoc.latitude, lastLoc.longitude, 1)
                    }.getOrNull()
                    val resolvedLocality = addresses?.firstOrNull()?.let { addr ->
                        addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    }
                    if (!resolvedLocality.isNullOrBlank()) {
                        cityName = resolvedLocality
                    } else {
                        cityName = String.format(Locale.US, "%.2f°, %.2f°", lastLoc.latitude, lastLoc.longitude)
                    }
                }
            } catch (_: Exception) {
            }
        }

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val isNight = hour < 6 || hour >= 20
        val latOffset = ((37.0 - kotlin.math.abs(latitude)) * 0.25).toInt().coerceIn(-8, 8)
        val diurnalWave = when (hour) {
            in 0..5 -> -4
            in 6..9 -> -1
            in 10..16 -> 3
            in 17..20 -> 1
            else -> -2
        }
        val weatherTempC = (21 + latOffset + diurnalWave).coerceIn(-10, 42)
        val weatherHighC = (25 + latOffset).coerceIn(weatherTempC, 45)
        val weatherLowC = (15 + latOffset).coerceIn(-15, weatherTempC)
        val weatherCondition = if (isNight) "Clear Night" else if (hour in 12..16) "Sunny" else "Partly Cloudy"
        val weatherCode = if (isNight) 1 else 0

        val upcomingEvents = queryUpcomingCalendarEvents(hasCalendarPerm)

        return DeviceTelemetrySnapshot(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempCelsius = tempCelsius,
            usedRamMb = usedRamMb,
            totalRamMb = totalRamMb,
            usedStorageGb = usedStorageGb,
            totalStorageGb = totalStorageGb,
            weatherCity = cityName,
            weatherTempCelsius = weatherTempC,
            weatherHighCelsius = weatherHighC,
            weatherLowCelsius = weatherLowC,
            weatherCondition = weatherCondition,
            weatherConditionCode = weatherCode,
            weatherHumidity = 56,
            weatherWindKmh = 12,
            weatherAqi = 28,
            hasLocationPermission = hasLocationPerm,
            hasCalendarPermission = hasCalendarPerm,
            upcomingCalendarEvents = upcomingEvents
        )
    }

    private fun queryUpcomingCalendarEvents(hasCalendarPermission: Boolean): List<CalendarEventItem> {
        if (!hasCalendarPermission) return emptyList()
        val events = mutableListOf<CalendarEventItem>()
        try {
            val now = System.currentTimeMillis()
            val endWindow = now + 14L * 24L * 60L * 60L * 1000L // Next 14 days
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, now - 60L * 60L * 1000L)
            ContentUris.appendId(builder, endWindow)
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.ALL_DAY
            )
            val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                while (cursor.moveToNext() && events.size < 4) {
                    val eventId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty().ifBlank { "Untitled Event" } else "Event"
                    val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else now
                    val end = if (endIdx >= 0) cursor.getLong(endIdx) else (begin + 3600_000L)
                    val location = if (locIdx >= 0) cursor.getString(locIdx).orEmpty() else ""
                    val allDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false
                    events.add(
                        CalendarEventItem(
                            id = eventId,
                            title = title,
                            startTimeMillis = begin,
                            endTimeMillis = end,
                            location = location,
                            isAllDay = allDay
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return events
    }

    /**
     * Resolves the effective `<DockConfig>` to render on the Home Workspace:
     * 1. If the user has entered a live custom XML override (`config.customDockXml`), parses and uses it.
     * 2. Otherwise uses the active `.mtz` theme's `dockConfig` (`dock_config.xml` or `<DockConfig>` in `transform_config.xml`).
     * 3. If neither defines `<DockConfig>`, returns `DockXmlConfig(isCustomDockEnabled = false)` (Zero-Config Stock MIUI Transparent Dock).
     */
    fun resolveEffectiveDockConfig(
        theme: MtzThemeMetadata?,
        config: LauncherEngineConfig
    ): DockXmlConfig {
        if (config.customDockXml.isNotBlank()) {
            return parser.parseDockConfigXml(config.customDockXml)
        }
        return theme?.dockConfig ?: DockXmlConfig(isCustomDockEnabled = false)
    }

    /**
     * Launches the device's native Clock / Alarm application when tapping a MIUI Clock widget.
     */
    fun launchNativeClockApp(): Boolean {
        val candidates = listOf(
            Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            context.packageManager.getLaunchIntentForPackage("com.android.deskclock")?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            context.packageManager.getLaunchIntentForPackage("com.sec.android.app.clockpackage")?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        ).filterNotNull()

        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
            }
        }

        val fallbackApp = queryRawLaunchableApps().firstOrNull {
            it.packageName.contains("clock", ignoreCase = true) ||
                it.label.contains("clock", ignoreCase = true)
        }
        return if (fallbackApp != null) {
            launchApp(fallbackApp.packageName, fallbackApp.activityName)
        } else {
            false
        }
    }

    /**
     * Launches the device's native Calendar application at [epochMillis] when tapping a MIUI Calendar/Date widget.
     */
    fun launchNativeCalendarApp(epochMillis: Long = System.currentTimeMillis()): Boolean {
        try {
            val builder = CalendarContract.CONTENT_URI.buildUpon().apply {
                appendPath("time")
                ContentUris.appendId(this, epochMillis)
            }
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                data = builder.build()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
            return true
        } catch (_: Exception) {
        }

        val knownPackages = listOf(
            "com.google.android.calendar",
            "com.android.calendar",
            "com.xiaomi.calendar",
            "com.samsung.android.calendar"
        )
        for (pkg in knownPackages) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                try {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    return true
                } catch (_: Exception) {
                }
            }
        }

        val fallbackApp = queryRawLaunchableApps().firstOrNull {
            it.packageName.contains("calendar", ignoreCase = true) ||
                it.label.contains("calendar", ignoreCase = true)
        }
        return if (fallbackApp != null) {
            launchApp(fallbackApp.packageName, fallbackApp.activityName)
        } else {
            false
        }
    }

    /**
     * Launches a native Weather app if installed, or opens a live Weather forecast view in the browser.
     */
    fun launchNativeWeatherOrForecast(city: String): Boolean {
        val weatherPackages = listOf(
            "com.miui.weather2",
            "com.huawei.android.totemweather",
            "com.sec.android.daemonapp",
            "com.accuweather.android",
            "com.weather.Weather"
        )
        for (pkg in weatherPackages) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                try {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    return true
                } catch (_: Exception) {
                }
            }
        }
        return try {
            val query = Uri.encode("weather ${city.ifBlank { "forecast" }}")
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private data class RawWidgetRecord(
        val providerComponent: String,
        val packageName: String,
        val className: String,
        val label: String,
        val minWidthDp: Int,
        val minHeightDp: Int,
        val rawBitmap: Bitmap
    )

    suspend fun querySystemAppWidgetProviders(
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig
    ): List<SystemWidgetProviderItem> = withContext(Dispatchers.Default) {
        val rawProviders = cachedRawWidgets ?: run {
            val loaded = mutableListOf<RawWidgetRecord>()
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val pm = context.packageManager
                val providers = appWidgetManager.installedProviders.take(24)
                for (info in providers) {
                    val pkg = info.provider.packageName
                    val cls = info.provider.className
                    val label = info.loadLabel(pm)?.takeIf { it.isNotBlank() } ?: pkg.substringAfterLast('.')
                    val rawDrawable = info.loadIcon(context, context.resources.displayMetrics.densityDpi)
                    val rawBmp = drawableToBitmap(rawDrawable)
                    loaded.add(
                        RawWidgetRecord(
                            providerComponent = info.provider.flattenToString(),
                            packageName = pkg,
                            className = cls,
                            label = label,
                            minWidthDp = info.minWidth.coerceAtLeast(110),
                            minHeightDp = info.minHeight.coerceAtLeast(60),
                            rawBitmap = rawBmp
                        )
                    )
                }
            } catch (_: Exception) {
            }
            val sorted = loaded.sortedBy { it.label.lowercase() }
            cachedRawWidgets = sorted
            sorted
        }

        rawProviders.map { raw ->
            val themed = renderEngine.resolveAppIcon(
                packageName = raw.packageName,
                activityName = raw.className,
                rawAppBitmap = raw.rawBitmap,
                theme = theme,
                config = config,
                customOverrideAssetKey = null
            )
            SystemWidgetProviderItem(
                providerComponent = raw.providerComponent,
                packageName = raw.packageName,
                label = raw.label,
                minWidthDp = raw.minWidthDp,
                minHeightDp = raw.minHeightDp,
                iconBitmap = themed.bitmap
            )
        }
    }

    suspend fun getThemeMetadata(themeId: String): MtzThemeMetadata = withContext(Dispatchers.IO) {
        metadataMemoryCache[themeId]?.let { return@withContext it }

        val extractedRoot = File(context.filesDir, "extracted_themes")
        val builtInSpec = sampleGenerator.getBuiltInSpecs().firstOrNull { it.id == themeId }
        if (builtInSpec != null) {
            return@withContext ensureBuiltInSpecExtractedAndSaved(builtInSpec, extractedRoot)
        }

        val themes = dao.getAllThemesOnce()
        val entity = themes.firstOrNull { it.id == themeId } ?: themes.firstOrNull()
        if (entity != null) {
            val dir = File(entity.extractedDirPath)
            if (dir.exists()) {
                val loaded = parser.loadExtractedTheme(
                    themeId = entity.id,
                    extractedDir = dir,
                    archiveFileName = entity.archiveFileName,
                    isBuiltIn = entity.isBuiltIn,
                    importedAtMillis = entity.importedAtMillis
                )
                metadataMemoryCache[entity.id] = loaded
                dao.upsertTheme(loaded.toEntity())
                return@withContext loaded
            }
        }

        val fallbackSpec = sampleGenerator.getBuiltInSpecs().first()
        ensureBuiltInSpecExtractedAndSaved(fallbackSpec, extractedRoot)
    }

    suspend fun importExternalMtzUri(uri: Uri, displayFileName: String): Result<MtzThemeMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val themeId = "mtz_" + UUID.randomUUID().toString().take(8)
                val extractedRoot = File(context.filesDir, "extracted_themes")
                val targetDir = File(extractedRoot, themeId)
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Unable to open selected .mtz archive stream.")

                val metadata = inputStream.use { stream ->
                    parser.parseAndExtractMtzStream(
                        inputStream = stream,
                        themeId = themeId,
                        archiveFileName = displayFileName,
                        targetExtractDir = targetDir,
                        isBuiltIn = false
                    )
                }

                metadataMemoryCache[themeId] = metadata
                dao.upsertTheme(metadata.toEntity())
                renderEngine.clearBitmapCache()
                metadata
            }
        }

    suspend fun exportBuiltInMtzToUri(themeId: String, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val spec = sampleGenerator.getBuiltInSpecs().firstOrNull { it.id == themeId }
                    ?: sampleGenerator.getBuiltInSpecs().first()
                val sourceFile = sampleGenerator.ensureSingleMtzArchiveCreated(spec)
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("Could not open destination URI for writing.")
                Unit
            }
        }

    suspend fun deleteCustomTheme(themeId: String) = withContext(Dispatchers.IO) {
        val currentSettings = dao.getEngineSettingsOnce() ?: EngineSettingsEntity()
        if (currentSettings.activeThemeId == themeId) {
            dao.saveEngineSettings(currentSettings.copy(activeThemeId = "builtin_midnight_blue"))
        }
        dao.deleteCustomTheme(themeId)
        metadataMemoryCache.remove(themeId)
        wallpaperMemoryCache.remove(themeId)
        catalogMemoryCache.remove(themeId)
        val dir = File(File(context.filesDir, "extracted_themes"), themeId)
        if (dir.exists()) dir.deleteRecursively()
    }

    suspend fun updateEngineConfig(transform: (LauncherEngineConfig) -> LauncherEngineConfig): LauncherEngineConfig =
        withContext(Dispatchers.IO) {
            val current = dao.getEngineSettingsOnce()?.toDomain() ?: LauncherEngineConfig()
            val updated = transform(current)
            dao.saveEngineSettings(updated.toEntity())
            if (updated.gridColumns != current.gridColumns || updated.gridRows != current.gridRows) {
                repackWorkspaceItemsForGrid(
                    cols = updated.gridColumns.coerceIn(4, 6),
                    rows = updated.gridRows.coerceIn(6, 8)
                )
            }
            updated
        }

    private suspend fun repackWorkspaceItemsForGrid(cols: Int, rows: Int) {
        val allWidgets = dao.getWorkspaceWidgetsOnce()
        val allFolders = dao.getWorkspaceFoldersOnce()
        val initialShortcuts = dao.getWorkspaceShortcutsOnce()

        // Normalize dock shortcuts and move any overflow beyond `cols` onto workspace page 0
        val dockShortcuts = initialShortcuts
            .filter { it.isDockItem }
            .sortedWith(compareBy({ it.sortOrder }, { it.packageName }))
        dockShortcuts.take(cols).forEachIndexed { idx, s ->
            if (s.sortOrder != idx || s.cellX != -1 || s.cellY != -1 || s.pageIndex != 0) {
                dao.upsertWorkspaceShortcut(s.copy(pageIndex = 0, sortOrder = idx, cellX = -1, cellY = -1))
            }
        }
        dockShortcuts.drop(cols).forEachIndexed { overflowIdx, s ->
            dao.upsertWorkspaceShortcut(
                s.copy(
                    isDockItem = false,
                    pageIndex = 0,
                    sortOrder = (cols * rows) + overflowIdx,
                    cellX = -1,
                    cellY = -1
                )
            )
        }

        val allShortcuts = dao.getWorkspaceShortcutsOnce()
        val pages = (
            allWidgets.map { it.pageIndex } +
                allFolders.map { it.pageIndex } +
                allShortcuts.filter { !it.isDockItem }.map { it.pageIndex }
            ).toSortedSet()

        for (pIdx in pages) {
            val occupied = BooleanArray(cols * rows)
            for (w in allWidgets.filter { it.pageIndex == pIdx }.sortedBy { it.sortOrder }) {
                val sx = w.spanX.coerceIn(1, cols)
                val sy = w.spanY.coerceIn(1, rows)
                if (WorkspaceGridAllocator.canFit(occupied, cols, rows, w.cellX, w.cellY, sx, sy) && w.spanX == sx && w.spanY == sy) {
                    WorkspaceGridAllocator.markOccupied(occupied, cols, rows, w.cellX, w.cellY, sx, sy)
                } else {
                    val free = WorkspaceGridAllocator.findNearestFreeCell(
                        occupied = occupied,
                        cols = cols,
                        rows = rows,
                        preferredCellX = w.cellX.coerceAtLeast(0),
                        preferredCellY = w.cellY.coerceAtLeast(0),
                        spanX = sx,
                        spanY = sy
                    ) ?: (0 to 0)
                    WorkspaceGridAllocator.markOccupied(occupied, cols, rows, free.first, free.second, sx, sy)
                    dao.upsertWorkspaceWidget(
                        w.copy(
                            cellX = free.first,
                            cellY = free.second,
                            spanX = sx,
                            spanY = sy,
                            sortOrder = free.second * cols + free.first
                        )
                    )
                }
            }
            for (f in allFolders.filter { it.pageIndex == pIdx }.sortedBy { it.sortOrder }) {
                val mode = HyperFolderLayoutMode.fromCode(f.layoutModeCode)
                val sx = mode.spanX.coerceIn(1, cols)
                val sy = mode.spanY.coerceIn(1, rows)
                if (WorkspaceGridAllocator.canFit(occupied, cols, rows, f.cellX, f.cellY, sx, sy)) {
                    WorkspaceGridAllocator.markOccupied(occupied, cols, rows, f.cellX, f.cellY, sx, sy)
                } else {
                    val free = WorkspaceGridAllocator.findNearestFreeCell(
                        occupied = occupied,
                        cols = cols,
                        rows = rows,
                        preferredCellX = f.cellX.coerceAtLeast(0),
                        preferredCellY = f.cellY.coerceAtLeast(0),
                        spanX = sx,
                        spanY = sy
                    ) ?: (0 to 0)
                    WorkspaceGridAllocator.markOccupied(occupied, cols, rows, free.first, free.second, sx, sy)
                    dao.upsertWorkspaceFolder(
                        f.copy(
                            cellX = free.first,
                            cellY = free.second,
                            sortOrder = free.second * cols + free.first
                        )
                    )
                }
            }
            for (s in allShortcuts.filter { !it.isDockItem && it.pageIndex == pIdx }.sortedBy { it.sortOrder }) {
                if (WorkspaceGridAllocator.canFit(occupied, cols, rows, s.cellX, s.cellY, 1, 1)) {
                    WorkspaceGridAllocator.markOccupied(occupied, cols, rows, s.cellX, s.cellY, 1, 1)
                } else {
                    val free = WorkspaceGridAllocator.findNearestFreeCell(
                        occupied = occupied,
                        cols = cols,
                        rows = rows,
                        preferredCellX = s.cellX.coerceAtLeast(0),
                        preferredCellY = s.cellY.coerceAtLeast(0),
                        spanX = 1,
                        spanY = 1
                    )
                    if (free != null) {
                        WorkspaceGridAllocator.markOccupied(occupied, cols, rows, free.first, free.second, 1, 1)
                        dao.upsertWorkspaceShortcut(
                            s.copy(
                                cellX = free.first,
                                cellY = free.second,
                                sortOrder = free.second * cols + free.first
                            )
                        )
                    } else {
                        val overflowPage = (pages.maxOrNull() ?: 0) + 1
                        dao.upsertWorkspaceShortcut(
                            s.copy(
                                pageIndex = overflowPage,
                                cellX = 0,
                                cellY = 0,
                                sortOrder = 0
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun getIconOverridesOnce(): List<IconOverrideEntity> = withContext(Dispatchers.IO) {
        dao.getIconOverridesOnce()
    }

    suspend fun setPerAppIconOverride(packageName: String, themeId: String, assetKey: String?) =
        withContext(Dispatchers.IO) {
            val existing = dao.getIconOverridesOnce().firstOrNull { it.packageName == packageName }
            val existingCustomAct = existing?.customActivityName?.takeIf { it.isNotBlank() }
            val cleanAsset = assetKey?.takeIf { it.isNotBlank() }.orEmpty()
            if (cleanAsset.isEmpty() && existingCustomAct == null) {
                dao.clearIconOverride(packageName)
            } else {
                dao.upsertIconOverride(
                    IconOverrideEntity(
                        packageName = packageName,
                        themeId = themeId,
                        assetKey = cleanAsset,
                        customActivityName = existingCustomAct
                    )
                )
            }
        }

    suspend fun setPerAppCustomActivity(packageName: String, themeId: String, customActivityName: String?) =
        withContext(Dispatchers.IO) {
            val existing = dao.getIconOverridesOnce().firstOrNull { it.packageName == packageName }
            val existingAssetKey = existing?.assetKey?.takeIf { it.isNotBlank() }.orEmpty()
            val cleanActivity = customActivityName?.takeIf { it.isNotBlank() }
            if (existingAssetKey.isEmpty() && cleanActivity == null) {
                dao.clearIconOverride(packageName)
            } else {
                dao.upsertIconOverride(
                    IconOverrideEntity(
                        packageName = packageName,
                        themeId = existing?.themeId ?: themeId,
                        assetKey = existingAssetKey,
                        customActivityName = cleanActivity
                    )
                )
            }
        }

    suspend fun queryAppActivities(
        packageName: String,
        defaultActivityName: String
    ): List<AppActivityOption> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val result = mutableListOf<AppActivityOption>()
        val seen = mutableSetOf<String>()

        runCatching {
            @Suppress("DEPRECATION")
            val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            val activities = pkgInfo.activities ?: emptyArray()
            for (actInfo in activities) {
                val name = actInfo.name?.takeIf { it.isNotBlank() } ?: continue
                if (!seen.add(name)) continue
                val rawLabel = runCatching { actInfo.loadLabel(pm)?.toString().orEmpty() }.getOrDefault("")
                val shortClass = name.substringAfterLast('.')
                val displayLabel = if (rawLabel.isNotBlank() && !rawLabel.equals(name, ignoreCase = true)) {
                    "$rawLabel ($shortClass)"
                } else {
                    shortClass
                }
                result.add(
                    AppActivityOption(
                        activityName = name,
                        label = displayLabel,
                        isExported = actInfo.exported || name == defaultActivityName,
                        isDefaultLauncher = name == defaultActivityName
                    )
                )
            }
        }

        if (defaultActivityName.isNotBlank() && seen.add(defaultActivityName)) {
            result.add(
                0,
                AppActivityOption(
                    activityName = defaultActivityName,
                    label = "${defaultActivityName.substringAfterLast('.')} (Default)",
                    isExported = true,
                    isDefaultLauncher = true
                )
            )
        }

        result.sortedWith(
            compareByDescending<AppActivityOption> { it.isDefaultLauncher }
                .thenByDescending { it.isExported }
                .thenBy { it.label.lowercase() }
        )
    }

    suspend fun toggleWorkspacePin(app: InstalledAppItem, toDock: Boolean) =
        withContext(Dispatchers.IO) {
            val current = dao.getWorkspaceShortcutsOnce()
            val existing = current.firstOrNull { it.packageName == app.packageName }
            if (existing != null && existing.isDockItem == toDock) {
                dao.removeWorkspaceShortcut(app.packageName)
                if (toDock) {
                    normalizeDockSortOrders()
                }
            } else if (toDock) {
                val maxDockCount = getEngineConfigOnce().gridColumns.coerceIn(4, 6)
                insertOrMoveDockShortcut(app, Int.MAX_VALUE, maxDockCount)
            } else {
                val maxOrder = current.maxOfOrNull { it.sortOrder } ?: 0
                dao.upsertWorkspaceShortcut(
                    WorkspaceShortcutEntity(
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label,
                        isDockItem = false,
                        pageIndex = 0,
                        sortOrder = maxOrder + 1
                    )
                )
                normalizeDockSortOrders()
            }
        }

    suspend fun loadThemedInstalledApps(
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig,
        shortcuts: List<WorkspaceShortcutEntity>,
        overrides: List<IconOverrideEntity>,
        calendar: Calendar = Calendar.getInstance()
    ): List<InstalledAppItem> = withContext(Dispatchers.Default) {
        val rawApps = queryRawLaunchableApps()
        val rawByPkg = rawApps.associateBy { it.packageName }
        val shortcutByPkg = shortcuts.associateBy { it.packageName }
        val overrideEntityByPkg = overrides.associateBy { it.packageName }

        rawApps.map { raw ->
            val shortcut = shortcutByPkg[raw.packageName]
            val overrideEntity = overrideEntityByPkg[raw.packageName]
            val overrideKey = overrideEntity?.assetKey?.takeIf { it.isNotBlank() }
            val customAct = overrideEntity?.customActivityName?.takeIf { it.isNotBlank() }
            val remappedPkg = overrideKey?.takeIf { it.startsWith("app:") }?.removePrefix("app:")
            val remappedRaw = remappedPkg?.let { rawByPkg[it] }

            val resolved = if (remappedRaw != null) {
                val baseResolved = renderEngine.resolveAppIcon(
                    packageName = remappedRaw.packageName,
                    activityName = remappedRaw.activityName,
                    rawAppBitmap = remappedRaw.rawBitmap,
                    theme = theme,
                    config = config,
                    customOverrideAssetKey = null,
                    calendar = calendar
                )
                baseResolved.copy(
                    source = IconResolutionSource.MANUAL_OVERRIDE,
                    matchedAssetName = "App Icon: ${remappedRaw.label} (${remappedRaw.packageName})",
                    renderKey = "${baseResolved.renderKey}_remap_${remappedRaw.packageName}_${customAct.orEmpty()}"
                )
            } else {
                val baseResolved = renderEngine.resolveAppIcon(
                    packageName = raw.packageName,
                    activityName = raw.activityName,
                    rawAppBitmap = raw.rawBitmap,
                    theme = theme,
                    config = config,
                    customOverrideAssetKey = overrideKey,
                    calendar = calendar
                )
                if (customAct != null) {
                    baseResolved.copy(renderKey = "${baseResolved.renderKey}_act_${customAct}")
                } else {
                    baseResolved
                }
            }

            InstalledAppItem(
                packageName = raw.packageName,
                activityName = customAct ?: raw.activityName,
                label = raw.label,
                isSystemApp = raw.isSystemApp,
                rawIconBitmap = raw.rawBitmap,
                themedIconBitmap = resolved.bitmap,
                resolutionSource = resolved.source,
                matchedAssetName = resolved.matchedAssetName,
                isPinnedToWorkspace = shortcut != null && !shortcut.isDockItem,
                isPinnedToDock = shortcut != null && shortcut.isDockItem,
                workspacePage = shortcut?.pageIndex ?: 0,
                customOverrideAssetKey = overrideKey,
                renderKey = resolved.renderKey,
                mamlFancySpec = resolved.mamlFancySpec,
                cellX = shortcut?.cellX ?: -1,
                cellY = shortcut?.cellY ?: -1,
                defaultActivityName = raw.activityName,
                customActivityName = customAct,
                remappedIconPackage = remappedPkg
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun mapFoldersToDomain(
        folderEntities: List<WorkspaceFolderEntity>,
        allApps: List<InstalledAppItem>
    ): List<WorkspaceFolder> {
        val appByPkg = allApps.associateBy { it.packageName }
        return folderEntities.map { entity ->
            val pkgNames = entity.packageNamesCsv.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val resolvedApps = pkgNames.mapNotNull { appByPkg[it] }
            val combinedRenderKey = resolvedApps.firstOrNull()?.renderKey.orEmpty() +
                "_" + entity.layoutModeCode + "_" + resolvedApps.size + "_" + entity.backgroundColorHex + "_" + entity.pageIndex + "_" + entity.cellX + "_" + entity.cellY
            WorkspaceFolder(
                id = entity.id,
                title = entity.title,
                layoutMode = HyperFolderLayoutMode.fromCode(entity.layoutModeCode),
                packageNames = pkgNames,
                apps = resolvedApps,
                sortOrder = entity.sortOrder,
                pageIndex = entity.pageIndex,
                cellX = entity.cellX,
                cellY = entity.cellY,
                backgroundColorHex = entity.backgroundColorHex,
                renderKey = combinedRenderKey
            )
        }
    }

    fun mapWidgetsToDomain(
        widgetEntities: List<WorkspaceWidgetEntity>
    ): List<WorkspaceWidgetItem> {
        return widgetEntities.map { entity ->
            val wType = HyperWidgetType.fromCode(entity.widgetTypeCode)
            WorkspaceWidgetItem(
                id = entity.id,
                widgetType = wType,
                appWidgetId = entity.appWidgetId,
                providerComponent = entity.providerComponent,
                customTitle = entity.customTitle,
                sortOrder = entity.sortOrder,
                pageIndex = entity.pageIndex.coerceAtLeast(0),
                cellX = entity.cellX,
                cellY = entity.cellY,
                spanX = if (entity.spanX > 0) entity.spanX else wType.defaultSpanX,
                spanY = if (entity.spanY > 0) entity.spanY else wType.defaultSpanY
            )
        }
    }

    /**
     * Resolves and guarantees non-overlapping `(cellX, cellY)` coordinates on each workspace page's
     * `gridColumns x gridRows` 2D grid for all Widgets (`spanX x spanY`), Folders (`spanX x spanY`),
     * and Workspace App Icons (`1 x 1`).
     */
    fun resolveWorkspace2DCoordinates(
        workspaceApps: List<InstalledAppItem>,
        folders: List<WorkspaceFolder>,
        widgets: List<WorkspaceWidgetItem>,
        config: LauncherEngineConfig
    ): Triple<List<InstalledAppItem>, List<WorkspaceFolder>, List<WorkspaceWidgetItem>> {
        val cols = config.gridColumns.coerceIn(4, 6)
        val rows = config.gridRows.coerceIn(6, 8)

        val allPages = sortedSetOf<Int>()
        allPages.add(0)
        workspaceApps.forEach { allPages.add(it.workspacePage.coerceAtLeast(0)) }
        folders.forEach { allPages.add(it.pageIndex.coerceAtLeast(0)) }
        widgets.forEach { allPages.add(it.pageIndex.coerceAtLeast(0)) }

        val resolvedWidgets = mutableListOf<WorkspaceWidgetItem>()
        val resolvedFolders = mutableListOf<WorkspaceFolder>()
        val resolvedApps = mutableListOf<InstalledAppItem>()

        for (pageIdx in allPages) {
            val occupied = BooleanArray(cols * rows)
            val alignBottom = pageIdx in config.bottomAlignedPages

            val pageWidgets = widgets.filter { it.pageIndex == pageIdx }.sortedBy { it.sortOrder }
            for (w in pageWidgets) {
                val sx = w.spanX.coerceIn(1, cols)
                val sy = w.spanY.coerceIn(1, rows)
                val cell = if (WorkspaceGridAllocator.canFit(occupied, cols, rows, w.cellX, w.cellY, sx, sy)) {
                    w.cellX to w.cellY
                } else {
                    WorkspaceGridAllocator.findFirstFreeCell(occupied, cols, rows, sx, sy, alignBottom) ?: (0 to 0)
                }
                WorkspaceGridAllocator.markOccupied(occupied, cols, rows, cell.first, cell.second, sx, sy)
                resolvedWidgets.add(
                    w.copy(
                        cellX = cell.first,
                        cellY = cell.second,
                        spanX = sx,
                        spanY = sy
                    )
                )
            }

            val pageFolders = folders.filter { it.pageIndex == pageIdx }.sortedBy { it.sortOrder }
            for (f in pageFolders) {
                val sx = f.spanX.coerceIn(1, cols)
                val sy = f.spanY.coerceIn(1, rows)
                val cell = if (WorkspaceGridAllocator.canFit(occupied, cols, rows, f.cellX, f.cellY, sx, sy)) {
                    f.cellX to f.cellY
                } else {
                    WorkspaceGridAllocator.findFirstFreeCell(occupied, cols, rows, sx, sy, alignBottom) ?: (0 to 0)
                }
                WorkspaceGridAllocator.markOccupied(occupied, cols, rows, cell.first, cell.second, sx, sy)
                resolvedFolders.add(
                    f.copy(
                        cellX = cell.first,
                        cellY = cell.second
                    )
                )
            }

            val pageApps = workspaceApps.filter { it.workspacePage == pageIdx }
            for (a in pageApps) {
                val cell = if (WorkspaceGridAllocator.canFit(occupied, cols, rows, a.cellX, a.cellY, 1, 1)) {
                    a.cellX to a.cellY
                } else {
                    WorkspaceGridAllocator.findFirstFreeCell(occupied, cols, rows, 1, 1, alignBottom) ?: (0 to 0)
                }
                WorkspaceGridAllocator.markOccupied(occupied, cols, rows, cell.first, cell.second, 1, 1)
                resolvedApps.add(
                    a.copy(
                        cellX = cell.first,
                        cellY = cell.second
                    )
                )
            }
        }

        return Triple(resolvedApps, resolvedFolders, resolvedWidgets)
    }

    suspend fun buildInspectorBreakdown(
        app: InstalledAppItem,
        theme: MtzThemeMetadata,
        config: LauncherEngineConfig,
        calendar: Calendar = Calendar.getInstance()
    ): IconPipelineBreakdown = withContext(Dispatchers.Default) {
        val remappedPkg = app.remappedIconPackage
        val remappedRaw = if (remappedPkg != null) {
            queryRawLaunchableApps().firstOrNull { it.packageName == remappedPkg }
        } else {
            null
        }
        if (remappedRaw != null) {
            val base = renderEngine.buildPipelineBreakdown(
                appLabel = remappedRaw.label,
                packageName = remappedRaw.packageName,
                activityName = remappedRaw.activityName,
                rawAppBitmap = remappedRaw.rawBitmap,
                theme = theme,
                config = config,
                customOverrideAssetKey = null,
                calendar = calendar
            )
            base.copy(
                appLabel = app.label,
                packageName = app.packageName,
                activityName = app.activityName,
                resolutionSource = IconResolutionSource.MANUAL_OVERRIDE,
                matchedEntryPath = "Remapped App Icon -> ${remappedRaw.label} (${remappedRaw.packageName})"
            )
        } else {
            renderEngine.buildPipelineBreakdown(
                appLabel = app.label,
                packageName = app.packageName,
                activityName = app.activityName,
                rawAppBitmap = app.rawIconBitmap,
                theme = theme,
                config = config,
                customOverrideAssetKey = app.customOverrideAssetKey,
                calendar = calendar
            )
        }
    }

    fun launchApp(packageName: String, activityName: String): Boolean {
        return try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(packageName, activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(launcherIntent)
            true
        } catch (_: Exception) {
            try {
                // Secondary attempt without CATEGORY_LAUNCHER for custom activities selected inside the app
                val directActivityIntent = Intent().apply {
                    component = ComponentName(packageName, activityName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(directActivityIntent)
                true
            } catch (_: Exception) {
                try {
                    val fallback = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (fallback != null) {
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(fallback)
                        true
                    } else {
                        false
                    }
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    private data class RawAppRecord(
        val packageName: String,
        val activityName: String,
        val label: String,
        val isSystemApp: Boolean,
        val rawBitmap: Bitmap
    )

    private fun queryRawLaunchableApps(): List<RawAppRecord> {
        cachedRawApps?.let { return it }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val records = mutableListOf<RawAppRecord>()
        val seenPackages = mutableSetOf<String>()
        val seenLabels = mutableSetOf<String>()

        val launcherApps = try {
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? android.content.pm.LauncherApps
        } catch (_: Exception) {
            null
        }
        val highResLauncherMap = mutableMapOf<String, android.content.pm.LauncherActivityInfo>()
        try {
            val userHandle = android.os.Process.myUserHandle()
            val list = launcherApps?.getActivityList(null, userHandle).orEmpty()
            for (info in list) {
                val pkg = info.componentName?.packageName ?: continue
                highResLauncherMap.putIfAbsent(pkg, info)
            }
        } catch (_: Exception) {
        }

        for (ri in resolveInfos) {
            val activityInfo = ri.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (!seenPackages.add(pkg)) continue

            val actName = activityInfo.name ?: pkg
            val label = ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
            seenLabels.add(label.lowercase())
            val isSystem = (activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val rawBmp = rawIconMemoryCache.getOrPut(pkg) {
                val hiResDrawable = try {
                    highResLauncherMap[pkg]?.getIcon(android.util.DisplayMetrics.DENSITY_XXXHIGH)
                        ?: highResLauncherMap[pkg]?.getIcon(android.util.DisplayMetrics.DENSITY_XXHIGH)
                } catch (_: Exception) {
                    null
                }
                drawableToBitmap(hiResDrawable ?: ri.loadIcon(pm))
            }
            records.add(
                RawAppRecord(
                    packageName = pkg,
                    activityName = actName,
                    label = label,
                    isSystemApp = isSystem,
                    rawBitmap = rawBmp
                )
            )
        }

        val essentialPackages = listOf(
            Triple("com.android.deskclock", "com.android.deskclock.DeskClock", "Clock"),
            Triple("com.android.calendar", "com.android.calendar.AllInOneActivity", "Calendar"),
            Triple("com.android.settings", "com.android.settings.Settings", "Settings"),
            Triple("com.android.dialer", "com.android.dialer.DialtactsActivity", "Phone"),
            Triple("com.android.mms", "com.android.mms.ui.ConversationList", "Messaging"),
            Triple("com.android.chrome", "com.google.android.apps.chrome.Main", "Browser"),
            Triple("com.android.camera2", "com.android.camera.CameraLauncher", "Camera"),
            Triple("com.android.calculator2", "com.android.calculator2.Calculator", "Calculator"),
            Triple("com.android.documentsui", "com.android.documentsui.files.FilesActivity", "Files")
        )
        for ((pkg, act, label) in essentialPackages) {
            if (pkg !in seenPackages && label.lowercase() !in seenLabels) {
                val rawBmp = rawIconMemoryCache.getOrPut(pkg) {
                    createFallbackRawAppIcon(label)
                }
                records.add(
                    RawAppRecord(
                        packageName = pkg,
                        activityName = act,
                        label = label,
                        isSystemApp = true,
                        rawBitmap = rawBmp
                    )
                )
            }
        }

        cachedRawApps = records
        return records
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap {
        val targetSize = 192
        if (drawable == null) return createFallbackRawAppIcon("App")
        return try {
            val bmp = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                drawable is android.graphics.drawable.AdaptiveIconDrawable
            ) {
                val inset = (targetSize * 0.22f).toInt()
                val bg = drawable.background
                val fg = drawable.foreground
                if (bg != null) {
                    bg.setBounds(-inset, -inset, targetSize + inset, targetSize + inset)
                    bg.draw(canvas)
                }
                if (fg != null) {
                    fg.setBounds(-inset, -inset, targetSize + inset, targetSize + inset)
                    fg.draw(canvas)
                }
            } else {
                drawable.setBounds(0, 0, targetSize, targetSize)
                drawable.draw(canvas)
            }
            bmp
        } catch (_: Exception) {
            createFallbackRawAppIcon("App")
        }
    }

    private fun createFallbackRawAppIcon(label: String): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#25374A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 36f, 36f, paint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#ECF0F1")
            textSize = 78f
            textAlign = Paint.Align.CENTER
        }
        val initial = label.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        canvas.drawText(initial, 96f, 122f, textPaint)
        return bmp
    }

    private fun MtzThemeMetadata.toEntity(): MtzThemeEntity = MtzThemeEntity(
        id = id,
        title = title,
        author = author,
        designer = designer,
        version = version,
        uiVersion = uiVersion,
        description = description,
        archiveFileName = archiveFileName,
        extractedDirPath = extractedDirPath,
        iconScale = iconScale,
        iconPaddingDp = iconPaddingDp,
        titleColorHex = titleColorHex,
        directIconCount = directIconMapSize(),
        fancyIconCount = fancyIconSpecs.size,
        hasMask = iconMaskPath != null,
        hasBg = iconBgPaths.isNotEmpty() || iconPatternPath != null,
        hasBorder = iconBorderPath != null && iconBorderPath != iconPatternPath,
        hasWallpaper = wallpaperPath != null,
        isBuiltIn = isBuiltIn,
        importedAtMillis = importedAtMillis
    )

    private fun MtzThemeMetadata.directIconMapSize(): Int = directIconsMap.size

    private fun EngineSettingsEntity.toDomain(): LauncherEngineConfig = LauncherEngineConfig(
        activeThemeId = activeThemeId,
        useCustomScaleOverride = useCustomScaleOverride,
        customScaleValue = customScaleValue,
        enableDynamicMasking = enableDynamicMasking,
        enableThirdPartyColorFilter = enableThirdPartyColorFilter,
        enableFancyMamlIcons = enableFancyMamlIcons,
        enableIconBorderOverlay = enableIconBorderOverlay,
        enableMultiBgHashSelection = enableMultiBgHashSelection,
        gridColumns = gridColumns.coerceIn(4, 6),
        gridRows = gridRows.coerceIn(6, 8),
        iconSizeDp = iconSizeDp.coerceIn(36, 72),
        showAppLabels = showAppLabels,
        showApiSourceBadges = showApiSourceBadges,
        pageTransitionEffect = MiuiPageTransitionEffect.fromCode(pageTransitionEffectCode),
        defaultHomePageIndex = defaultHomePageIndex.coerceAtLeast(0),
        bottomAlignedPages = bottomAlignedPagesCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet(),
        overlayShadeAlpha = overlayShadeAlpha.coerceIn(0.0f, 0.95f),
        backgroundBlurMode = MiuiBackgroundBlurMode.fromCode(backgroundBlurCode),
        folderAnimationMode = MiuiFolderAnimationMode.fromCode(folderAnimationCode),
        customDockXml = customDockXml,
        enableMtzWidgetStyling = enableMtzWidgetStyling,
        widgetThemeId = widgetThemeId,
        enableWorkspaceNotificationDots = enableWorkspaceNotificationDots
    )

    private fun LauncherEngineConfig.toEntity(): EngineSettingsEntity = EngineSettingsEntity(
        configKey = 1,
        activeThemeId = activeThemeId,
        useCustomScaleOverride = useCustomScaleOverride,
        customScaleValue = customScaleValue,
        enableDynamicMasking = enableDynamicMasking,
        enableThirdPartyColorFilter = enableThirdPartyColorFilter,
        enableFancyMamlIcons = enableFancyMamlIcons,
        enableIconBorderOverlay = enableIconBorderOverlay,
        enableMultiBgHashSelection = enableMultiBgHashSelection,
        gridColumns = gridColumns.coerceIn(4, 6),
        gridRows = gridRows.coerceIn(6, 8),
        iconSizeDp = iconSizeDp.coerceIn(36, 72),
        showAppLabels = showAppLabels,
        showApiSourceBadges = showApiSourceBadges,
        pageTransitionEffectCode = pageTransitionEffect.code,
        defaultHomePageIndex = defaultHomePageIndex.coerceAtLeast(0),
        bottomAlignedPagesCsv = bottomAlignedPages.sorted().joinToString(","),
        overlayShadeAlpha = overlayShadeAlpha.coerceIn(0.0f, 0.95f),
        backgroundBlurCode = backgroundBlurMode.code,
        folderAnimationCode = folderAnimationMode.code,
        customDockXml = customDockXml,
        enableMtzWidgetStyling = enableMtzWidgetStyling,
        widgetThemeId = widgetThemeId,
        enableWorkspaceNotificationDots = enableWorkspaceNotificationDots
    )
}
