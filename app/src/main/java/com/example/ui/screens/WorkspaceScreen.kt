package com.example.ui.screens

import android.appwidget.AppWidgetHost
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.domain.model.DeviceTelemetrySnapshot
import com.example.domain.model.InstalledAppItem
import com.example.domain.model.LauncherEngineConfig
import com.example.domain.model.MiuiPageTransitionEffect
import com.example.domain.model.MtzThemeMetadata
import com.example.domain.model.WorkspaceFolder
import com.example.domain.model.WorkspaceGridAllocator
import com.example.domain.model.WorkspaceWidgetItem
import com.example.ui.components.AppIconTile
import com.example.ui.components.HyperFolderCard
import com.example.ui.components.HyperWorkspaceWidgetCard
import com.example.ui.components.MiuiAppFloatingActionPill
import com.example.ui.components.MiuiFolderFloatingActionPill
import com.example.ui.components.MiuiWidgetFloatingActionPill
import com.example.ui.components.miuiIconTapLongPressAndDrag
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class ResolvedPage2DLayout(
    val widgets: List<WorkspaceWidgetItem>,
    val folders: List<WorkspaceFolder>,
    val apps: List<InstalledAppItem>
)

/**
 * Self-healing 2D grid coordinate resolver for a single workspace page (`cols x rows`, default `4 x 6`).
 * Guarantees that every widget, folder, and app icon has valid non-overlapping `(cellX, cellY)` coordinates
 * so they share rows naturally without taking full rows.
 */
private fun resolveSinglePage2DLayout(
    cols: Int,
    rows: Int,
    rawWidgets: List<WorkspaceWidgetItem>,
    rawFolders: List<WorkspaceFolder>,
    rawApps: List<InstalledAppItem>,
    isBottomAligned: Boolean
): ResolvedPage2DLayout {
    val grid = BooleanArray(cols * rows)

    val resolvedWidgets = rawWidgets.sortedBy { it.sortOrder }.map { w ->
        val sx = w.spanX.coerceIn(1, cols)
        val sy = w.spanY.coerceIn(1, rows)
        val cx = w.cellX
        val cy = w.cellY
        if (cx >= 0 && cy >= 0 && WorkspaceGridAllocator.canFit(grid, cols, rows, cx, cy, sx, sy)) {
            WorkspaceGridAllocator.markOccupied(grid, cols, rows, cx, cy, sx, sy)
            w.copy(spanX = sx, spanY = sy)
        } else {
            val free = WorkspaceGridAllocator.findFirstFreeCell(grid, cols, rows, sx, sy, alignBottom = isBottomAligned)
            if (free != null) {
                WorkspaceGridAllocator.markOccupied(grid, cols, rows, free.first, free.second, sx, sy)
                w.copy(cellX = free.first, cellY = free.second, spanX = sx, spanY = sy)
            } else {
                w.copy(cellX = cx.coerceIn(0, cols - 1), cellY = cy.coerceIn(0, rows - 1), spanX = sx, spanY = sy)
            }
        }
    }

    val resolvedFolders = rawFolders.sortedBy { it.sortOrder }.map { f ->
        val sx = f.spanX.coerceIn(1, cols)
        val sy = f.spanY.coerceIn(1, rows)
        val cx = f.cellX
        val cy = f.cellY
        if (cx >= 0 && cy >= 0 && WorkspaceGridAllocator.canFit(grid, cols, rows, cx, cy, sx, sy)) {
            WorkspaceGridAllocator.markOccupied(grid, cols, rows, cx, cy, sx, sy)
            f
        } else {
            val free = WorkspaceGridAllocator.findFirstFreeCell(grid, cols, rows, sx, sy, alignBottom = isBottomAligned)
            if (free != null) {
                WorkspaceGridAllocator.markOccupied(grid, cols, rows, free.first, free.second, sx, sy)
                f.copy(cellX = free.first, cellY = free.second)
            } else {
                f.copy(cellX = cx.coerceIn(0, cols - 1), cellY = cy.coerceIn(0, rows - 1))
            }
        }
    }

    val resolvedApps = rawApps.map { app ->
        val cx = app.cellX
        val cy = app.cellY
        if (cx >= 0 && cy >= 0 && WorkspaceGridAllocator.canFit(grid, cols, rows, cx, cy, 1, 1)) {
            WorkspaceGridAllocator.markOccupied(grid, cols, rows, cx, cy, 1, 1)
            app
        } else {
            val free = WorkspaceGridAllocator.findFirstFreeCell(grid, cols, rows, 1, 1, alignBottom = isBottomAligned)
            if (free != null) {
                WorkspaceGridAllocator.markOccupied(grid, cols, rows, free.first, free.second, 1, 1)
                app.copy(cellX = free.first, cellY = free.second)
            } else {
                app.copy(cellX = cx.coerceIn(0, cols - 1), cellY = cy.coerceIn(0, rows - 1))
            }
        }
    }

    return ResolvedPage2DLayout(
        widgets = resolvedWidgets,
        folders = resolvedFolders,
        apps = resolvedApps
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkspaceScreen(
    activeTheme: MtzThemeMetadata?,
    wallpaperBitmap: Bitmap?,
    engineConfig: LauncherEngineConfig,
    allApps: List<InstalledAppItem>,
    workspaceApps: List<InstalledAppItem>,
    dockApps: List<InstalledAppItem>,
    workspaceFolders: List<WorkspaceFolder>,
    workspaceWidgets: List<WorkspaceWidgetItem>,
    deviceTelemetry: DeviceTelemetrySnapshot,
    appWidgetHost: AppWidgetHost?,
    onAppClick: (InstalledAppItem) -> Unit,
    onAppLongClick: (InstalledAppItem) -> Unit,
    onDeleteAppShortcut: (InstalledAppItem) -> Unit = {},
    onUninstallApp: (InstalledAppItem) -> Unit = {},
    onShareApp: (InstalledAppItem) -> Unit = {},
    onOpenAppInfo: (InstalledAppItem) -> Unit = {},
    onExpandFolder: (WorkspaceFolder) -> Unit,
    onCycleFolderMode: (WorkspaceFolder) -> Unit,
    onResizeFolderSpan: (String, Int, Int) -> Unit = { _, _, _ -> },
    onCycleFolderBackgroundColor: (String) -> Unit = {},
    onRenameFolder: (String, String) -> Unit = { _, _ -> },
    onDeleteFolder: (String) -> Unit = {},
    onMoveFolderOnWorkspace: (String, Int, Int, Int, Int) -> Unit = { _, _, _, _, _ -> },
    onMoveWidgetOnWorkspace: (String, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onResizeWidgetSpan: (String, Int, Int) -> Unit = { _, _, _ -> },
    onCycleWidgetSize: (String) -> Unit = {},
    onMoveAppOnWorkspace: (InstalledAppItem, Int, Int, Boolean, Int, Int) -> Unit = { _, _, _, _, _, _ -> },
    onExtractAppFromFolder: (String, InstalledAppItem, Int, Int, Boolean, Int, Int) -> Unit = { _, _, _, _, _, _, _ -> },
    onSwapWorkspaceApps: (String, String) -> Unit = { _, _ -> },
    onMergeAppsIntoNewFolder: (InstalledAppItem, InstalledAppItem, Int, String?) -> Unit = { _, _, _, _ -> },
    onMoveAppIntoFolder: (String, InstalledAppItem, String?) -> Unit = { _, _, _ -> },
    onBatchMoveAppsToPage: (List<String>, Int) -> Unit = { _, _ -> },
    onAlignWorkspacePage: (Int, Boolean) -> Unit = { _, _ -> },
    onSetDefaultHomePage: (Int) -> Unit = {},
    onUpdatePageTransitionEffect: (MiuiPageTransitionEffect) -> Unit = {},
    onOpenWidgetsAndFoldersPicker: () -> Unit,
    onActivePageChanged: (Int) -> Unit = {},
    onRefreshTelemetry: () -> Unit,
    onRemoveWidget: (String) -> Unit,
    onOpenAppDrawer: () -> Unit,
    onOpenMtzStudio: () -> Unit,
    onOpenApiPipeline: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onUpdateGridColumns: (Int) -> Unit = {},
    onUpdateGridRows: (Int) -> Unit = {},
    onUpdateIconSizeDp: (Int) -> Unit = {},
    onToggleShowLabels: (Boolean) -> Unit = {},
    onSetDefaultHomeLauncher: () -> Unit = {},
    onPickCustomWallpaper: () -> Unit = {},
    onResetToThemeWallpaper: () -> Unit = {},
    externalDraggedApp: InstalledAppItem? = null,
    externalDragSourceFolderId: String? = null,
    externalDragOffsetInRoot: Offset = Offset.Zero,
    onRegisterExternalDropHandler: ((InstalledAppItem, String?, Offset) -> Unit) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDesktopOptionsSheet by remember { mutableStateOf(false) }
    var showTransitionEffectSheet by remember { mutableStateOf(false) }
    var showWallpaperQuickSheet by remember { mutableStateOf(false) }

    // MIUI Long-Tap Workspace Edit Mode State
    var isWorkspaceEditMode by remember { mutableStateOf(false) }
    val selectedEditPackages = remember { mutableStateListOf<String>() }

    // MIUI Floating App Long-Press Pill State
    var longPressedApp by remember { mutableStateOf<InstalledAppItem?>(null) }
    var longPressedAppBounds by remember { mutableStateOf(Rect.Zero) }

    var editingFolderId by remember { mutableStateOf<String?>(null) }
    var editingWidgetId by remember { mutableStateOf<String?>(null) }
    var folderPendingRename by remember { mutableStateOf<WorkspaceFolder?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Drag-and-Drop State for Icons, Folders & Widgets
    var isAnyIconHoldOrDragActive by remember { mutableStateOf(false) }
    var internalDraggedApp by remember { mutableStateOf<InstalledAppItem?>(null) }
    var internalDragSourceFolderId by remember { mutableStateOf<String?>(null) }
    var draggedFolder by remember { mutableStateOf<WorkspaceFolder?>(null) }
    var draggedWidget by remember { mutableStateOf<WorkspaceWidgetItem?>(null) }
    var internalDragFingerOffsetInRoot by remember { mutableStateOf(Offset.Zero) }

    val activeDraggedApp = externalDraggedApp ?: internalDraggedApp
    val activeDragSourceFolderId = if (externalDraggedApp != null) externalDragSourceFolderId else internalDragSourceFolderId
    val activeDragFingerOffsetInRoot = if (externalDraggedApp != null) externalDragOffsetInRoot else internalDragFingerOffsetInRoot

    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }

    val appBoundsMap = remember { mutableStateMapOf<String, Rect>() }
    val folderBoundsMap = remember { mutableStateMapOf<String, Rect>() }
    val widgetBoundsMap = remember { mutableStateMapOf<String, Rect>() }
    val pageGridBoundsMap = remember { mutableStateMapOf<Int, Rect>() }
    val pageThumbBoundsMap = remember { mutableStateMapOf<Int, Rect>() }
    var addPageThumbBounds by remember { mutableStateOf(Rect.Zero) }

    val gridCols = engineConfig.gridColumns.coerceIn(4, 6)
    val gridRows = engineConfig.gridRows.coerceIn(6, 8)

    // Dynamic Workspace Page Count (Auto-Add & Auto-Remove when empty, including Widgets)
    val baseMaxPageFromItems = maxOf(
        workspaceApps.maxOfOrNull { it.workspacePage } ?: 0,
        workspaceFolders.maxOfOrNull { it.pageIndex } ?: 0,
        workspaceWidgets.maxOfOrNull { it.pageIndex } ?: 0,
        0
    )
    val basePageCount = (baseMaxPageFromItems + 1).coerceAtLeast(1)
    var extraDynamicPages by remember { mutableIntStateOf(0) }

    // Automatically compact/remove empty extra pages when not dragging
    LaunchedEffect(basePageCount, activeDraggedApp, draggedFolder, draggedWidget, isWorkspaceEditMode) {
        if (activeDraggedApp == null && draggedFolder == null && draggedWidget == null && !isWorkspaceEditMode) {
            extraDynamicPages = 0
        }
    }

    val pageCount = (basePageCount + extraDynamicPages).coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = engineConfig.defaultHomePageIndex.coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )

    // Notify MainActivity whenever the active workspace page changes
    LaunchedEffect(pagerState.currentPage) {
        onActivePageChanged(pagerState.currentPage)
    }

    // Clamp current page if trailing pages were dynamically removed
    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) {
            pagerState.scrollToPage((pageCount - 1).coerceAtLeast(0))
        }
    }

    // BackHandler for dismissing Floating Pill, Folder Edit Mode, Widget Edit Mode, or Workspace Edit Mode
    BackHandler(
        enabled = longPressedApp != null || editingFolderId != null || editingWidgetId != null || isWorkspaceEditMode
    ) {
        when {
            longPressedApp != null -> longPressedApp = null
            editingFolderId != null -> editingFolderId = null
            editingWidgetId != null -> editingWidgetId = null
            isWorkspaceEditMode -> {
                isWorkspaceEditMode = false
                selectedEditPackages.clear()
                extraDynamicPages = 0
            }
        }
    }

    // Edge-hover auto page turn & dynamic new page creation while dragging an icon, folder, or widget
    LaunchedEffect(activeDraggedApp, draggedFolder, draggedWidget, activeDragFingerOffsetInRoot) {
        val isDragging = activeDraggedApp != null || draggedFolder != null || draggedWidget != null
        if (!isDragging || rootBounds.width <= 0f) return@LaunchedEffect
        val edgeMarginPx = with(density) { 46.dp.toPx() }
        val fingerX = activeDragFingerOffsetInRoot.x - rootBounds.left
        if (fingerX < edgeMarginPx && pagerState.currentPage > 0) {
            delay(420L)
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        } else if (fingerX > rootBounds.width - edgeMarginPx) {
            delay(420L)
            if (pagerState.currentPage < pageCount - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else if (extraDynamicPages < 2) {
                // Dynamically create a new page when dragging to the right edge of the last page!
                extraDynamicPages += 1
                delay(60L)
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    val timeString = remember(currentTimeMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentTimeMillis))
    }
    val dateString = remember(currentTimeMillis) {
        SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(currentTimeMillis)).uppercase(Locale.US)
    }

    // Helper to convert a root drop Offset into (cellX, cellY) on the given page's 2D grid
    val computeTargetGridCell: (Int, Offset, Int, Int) -> Pair<Int, Int> = { pageIdx, dropPos, spanX, spanY ->
        val gridRect = pageGridBoundsMap[pageIdx] ?: rootBounds
        if (gridRect.width > 0f && gridRect.height > 0f) {
            val cellW = gridRect.width / gridCols
            val cellH = gridRect.height / gridRows
            val rawCol = (dropPos.x - gridRect.left) / cellW
            val rawRow = (dropPos.y - gridRect.top) / cellH
            val clampedSpanX = spanX.coerceIn(1, gridCols)
            val clampedSpanY = spanY.coerceIn(1, gridRows)
            val targetX = if (clampedSpanX == 1) {
                rawCol.toInt().coerceIn(0, gridCols - 1)
            } else {
                (rawCol - clampedSpanX * 0.5f).roundToInt().coerceIn(0, (gridCols - clampedSpanX).coerceAtLeast(0))
            }
            val targetY = if (clampedSpanY == 1) {
                rawRow.toInt().coerceIn(0, gridRows - 1)
            } else {
                (rawRow - clampedSpanY * 0.5f).roundToInt().coerceIn(0, (gridRows - clampedSpanY).coerceAtLeast(0))
            }
            targetX to targetY
        } else {
            0 to 0
        }
    }

    val handleDropApp: (InstalledAppItem, String?, Offset) -> Unit = { droppedApp, sourceFolderId, dropPos ->
        val currentPage = pagerState.currentPage

        // 0. If in Edit Mode and dropped onto a Page Thumbnail or "+" Add Page Card in the bottom strip
        val hitThumbPage = if (isWorkspaceEditMode) {
            pageThumbBoundsMap.entries.firstOrNull { (_, rect) -> rect.contains(dropPos) }?.key
        } else null
        val hitAddPageThumb = isWorkspaceEditMode && addPageThumbBounds.contains(dropPos)

        if (hitAddPageThumb) {
            val newPageIdx = basePageCount
            val batchPkgs = (selectedEditPackages + droppedApp.packageName).distinct()
            onBatchMoveAppsToPage(batchPkgs, newPageIdx)
            selectedEditPackages.clear()
            coroutineScope.launch {
                delay(80L)
                pagerState.animateScrollToPage(newPageIdx.coerceAtMost(pageCount - 1))
            }
        } else if (hitThumbPage != null) {
            val batchPkgs = (selectedEditPackages + droppedApp.packageName).distinct()
            onBatchMoveAppsToPage(batchPkgs, hitThumbPage)
            selectedEditPackages.clear()
            coroutineScope.launch {
                pagerState.animateScrollToPage(hitThumbPage.coerceAtMost(pageCount - 1))
            }
        } else if (dockBounds.contains(dropPos) && !isWorkspaceEditMode) {
            // 1. Dropped onto the Dock bar:
            //    - While the dock has space (< gridCols) or when reordering an icon already in the dock,
            //      insert at the horizontal drop position (0..otherDockApps.size).
            //    - Only swap with the targeted dock icon when the dock is already full (>= gridCols)
            //      and the dragged icon is coming from outside the dock.
            val maxDockCount = gridCols
            val currentDockApps = dockApps.take(maxDockCount)
            val otherDockApps = currentDockApps.filter { it.packageName != droppedApp.packageName }
            val targetDockIndex = otherDockApps.count { dockApp ->
                val bounds = appBoundsMap[dockApp.packageName]
                bounds != null && bounds != Rect.Zero && dropPos.x > bounds.center.x
            }

            if (otherDockApps.size < maxDockCount) {
                if (sourceFolderId != null) {
                    onExtractAppFromFolder(sourceFolderId, droppedApp, 0, targetDockIndex, true, -1, -1)
                } else {
                    onMoveAppOnWorkspace(droppedApp, 0, targetDockIndex, true, -1, -1)
                }
            } else {
                val targetDockApp = currentDockApps.firstOrNull { dockApp ->
                    dockApp.packageName != droppedApp.packageName &&
                        appBoundsMap[dockApp.packageName]?.contains(dropPos) == true
                } ?: currentDockApps.minByOrNull { dockApp ->
                    val bounds = appBoundsMap[dockApp.packageName]
                    if (bounds != null && bounds != Rect.Zero) abs(dropPos.x - bounds.center.x) else Float.MAX_VALUE
                }
                if (targetDockApp != null && sourceFolderId == null) {
                    onSwapWorkspaceApps(droppedApp.packageName, targetDockApp.packageName)
                } else if (targetDockApp != null && sourceFolderId != null) {
                    val replaceIndex = currentDockApps.indexOfFirst { it.packageName == targetDockApp.packageName }
                        .coerceAtLeast(0)
                    val appsOnPage = workspaceApps.count { it.workspacePage == currentPage }
                    onMoveAppOnWorkspace(targetDockApp, currentPage, appsOnPage + 1, false, -1, -1)
                    onExtractAppFromFolder(sourceFolderId, droppedApp, 0, replaceIndex, true, -1, -1)
                }
            }
        } else {
            val (targetCellX, targetCellY) = computeTargetGridCell(currentPage, dropPos, 1, 1)

            // 2. Check if dropped onto an existing folder on currentPage (by visual bounds or 2D cell footprint)
            val hitFolder = workspaceFolders.firstOrNull { f ->
                f.pageIndex == currentPage &&
                    f.id != sourceFolderId &&
                    (folderBoundsMap[f.id]?.contains(dropPos) == true ||
                        (targetCellX in f.cellX until (f.cellX + f.spanX) &&
                            targetCellY in f.cellY until (f.cellY + f.spanY)))
            }

            if (hitFolder != null) {
                onMoveAppIntoFolder(hitFolder.id, droppedApp, sourceFolderId)
            } else {
                // 3. Check if dropped directly onto another app icon on currentPage at the target cell
                val targetApp = workspaceApps.firstOrNull { app ->
                    app.workspacePage == currentPage &&
                        app.packageName != droppedApp.packageName &&
                        app.cellX == targetCellX &&
                        app.cellY == targetCellY &&
                        appBoundsMap[app.packageName]?.contains(dropPos) == true
                }

                if (targetApp != null) {
                    if (droppedApp.isPinnedToDock && sourceFolderId == null) {
                        onSwapWorkspaceApps(droppedApp.packageName, targetApp.packageName)
                    } else {
                        // Dropping an icon onto another workspace icon creates a new folder!
                        onMergeAppsIntoNewFolder(droppedApp, targetApp, currentPage, sourceFolderId)
                    }
                } else {
                    // 4. Dropped onto a 2D cell (including shared-row cells beside a folder/widget) on currentPage
                    val appsOnPage = workspaceApps.count { it.workspacePage == currentPage }
                    if (sourceFolderId != null) {
                        onExtractAppFromFolder(
                            sourceFolderId,
                            droppedApp,
                            currentPage,
                            appsOnPage + 1,
                            false,
                            targetCellX,
                            targetCellY
                        )
                    } else {
                        onMoveAppOnWorkspace(
                            droppedApp,
                            currentPage,
                            appsOnPage + 1,
                            false,
                            targetCellX,
                            targetCellY
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(handleDropApp) {
        onRegisterExternalDropHandler(handleDropApp)
    }

    val handleDropFolder = { droppedFolder: WorkspaceFolder, dropPos: Offset ->
        val currentPage = pagerState.currentPage
        val (targetCellX, targetCellY) = computeTargetGridCell(
            currentPage,
            dropPos,
            droppedFolder.spanX,
            droppedFolder.spanY
        )
        val foldersOnPage = workspaceFolders.count { it.pageIndex == currentPage }
        onMoveFolderOnWorkspace(droppedFolder.id, currentPage, foldersOnPage + 1, targetCellX, targetCellY)
    }

    val handleDropWidget = { droppedWidget: WorkspaceWidgetItem, dropPos: Offset ->
        val currentPage = pagerState.currentPage
        val (targetCellX, targetCellY) = computeTargetGridCell(
            currentPage,
            dropPos,
            droppedWidget.spanX,
            droppedWidget.spanY
        )
        onMoveWidgetOnWorkspace(droppedWidget.id, currentPage, targetCellX, targetCellY)
    }

    val currentActiveDraggedApp by rememberUpdatedState(activeDraggedApp)
    val currentDraggedFolder by rememberUpdatedState(draggedFolder)
    val currentDraggedWidget by rememberUpdatedState(draggedWidget)
    val currentEditingFolderId by rememberUpdatedState(editingFolderId)
    val currentEditingWidgetId by rememberUpdatedState(editingWidgetId)
    val currentIsWorkspaceEditMode by rememberUpdatedState(isWorkspaceEditMode)
    val currentLongPressedApp by rememberUpdatedState(longPressedApp)
    val currentIsHoldOrDragActive by rememberUpdatedState(isAnyIconHoldOrDragActive)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                rootBounds = coords.boundsInRoot()
            }
            // Stock MIUI Swipe-Up Gesture anywhere on Workspace to open App Drawer
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startMs = System.currentTimeMillis()
                    val startPos = down.position
                    var lastPos = startPos
                    var blockedFromDrawerSwipe =
                        currentIsHoldOrDragActive ||
                            currentActiveDraggedApp != null ||
                            currentDraggedFolder != null ||
                            currentDraggedWidget != null ||
                            currentEditingFolderId != null ||
                            currentEditingWidgetId != null ||
                            currentIsWorkspaceEditMode ||
                            currentLongPressedApp != null

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null) {
                            lastPos = change.position
                        }
                        if (currentIsHoldOrDragActive ||
                            currentActiveDraggedApp != null ||
                            currentDraggedFolder != null ||
                            currentDraggedWidget != null ||
                            currentEditingFolderId != null ||
                            currentEditingWidgetId != null ||
                            currentIsWorkspaceEditMode ||
                            currentLongPressedApp != null
                        ) {
                            blockedFromDrawerSwipe = true
                        }
                    } while (event.changes.any { it.pressed })

                    val elapsedMs = System.currentTimeMillis() - startMs
                    val dy = lastPos.y - startPos.y
                    val dx = lastPos.x - startPos.x
                    if (!blockedFromDrawerSwipe &&
                        elapsedMs < 350L &&
                        dy < -110f &&
                        abs(dy) > abs(dx) * 1.35f
                    ) {
                        onOpenAppDrawer()
                    }
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!isAnyIconHoldOrDragActive &&
                        activeDraggedApp == null &&
                        draggedFolder == null &&
                        draggedWidget == null
                    ) {
                        when {
                            longPressedApp != null -> longPressedApp = null
                            editingFolderId != null -> editingFolderId = null
                            editingWidgetId != null -> editingWidgetId = null
                            isWorkspaceEditMode && selectedEditPackages.isEmpty() -> {
                                isWorkspaceEditMode = false
                                extraDynamicPages = 0
                            }
                        }
                    }
                },
                onLongClick = {
                    if (!isAnyIconHoldOrDragActive &&
                        activeDraggedApp == null &&
                        draggedFolder == null &&
                        draggedWidget == null &&
                        longPressedApp == null &&
                        editingFolderId == null &&
                        editingWidgetId == null
                    ) {
                        isWorkspaceEditMode = !isWorkspaceEditMode
                        if (!isWorkspaceEditMode) {
                            selectedEditPackages.clear()
                            extraDynamicPages = 0
                        }
                    }
                }
            )
            .testTag("workspace_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isWorkspaceEditMode) 18.dp else 10.dp, vertical = 6.dp)
        ) {
            // MIUI Workspace Edit Mode Top Action Bar (`Align with top` | `Home` | `Align with bottom`)
            AnimatedVisibility(
                visible = isWorkspaceEditMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MiuiWorkspaceEditTopBar(
                    isDefaultHomePage = pagerState.currentPage == engineConfig.defaultHomePageIndex,
                    onAlignTop = {
                        onAlignWorkspacePage(pagerState.currentPage, false)
                    },
                    onSetDefaultHome = {
                        onSetDefaultHomePage(pagerState.currentPage)
                    },
                    onAlignBottom = {
                        onAlignWorkspacePage(pagerState.currentPage, true)
                    }
                )
            }

            // Multi-Screen Workspace HorizontalPager with MIUI 3D/Fade Transition Effects
            // Strictly Non-Scrolling Fixed 2D Grid (`gridColumns x gridRows`, default 4x6) per Screen
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 3,
                userScrollEnabled = !isAnyIconHoldOrDragActive &&
                    activeDraggedApp == null &&
                    draggedFolder == null &&
                    draggedWidget == null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isWorkspaceEditMode) 12.dp else 0.dp,
                        vertical = if (isWorkspaceEditMode) 6.dp else 0.dp
                    )
                    .testTag("workspace_pager")
            ) { pageIndex ->
                val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                val isBottomAligned = pageIndex in engineConfig.bottomAlignedPages

                val rawPageWidgets = remember(workspaceWidgets, pageIndex) {
                    workspaceWidgets.filter { it.pageIndex == pageIndex }
                }
                val rawPageFolders = remember(workspaceFolders, pageIndex) {
                    workspaceFolders.filter { it.pageIndex == pageIndex }
                }
                val rawPageApps = remember(workspaceApps, pageIndex) {
                    workspaceApps.filter { it.workspacePage == pageIndex }
                }

                val resolvedPage = remember(
                    rawPageWidgets,
                    rawPageFolders,
                    rawPageApps,
                    gridCols,
                    gridRows,
                    isBottomAligned
                ) {
                    resolveSinglePage2DLayout(
                        cols = gridCols,
                        rows = gridRows,
                        rawWidgets = rawPageWidgets,
                        rawFolders = rawPageFolders,
                        rawApps = rawPageApps,
                        isBottomAligned = isBottomAligned
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (activeDraggedApp == null && draggedFolder == null && draggedWidget == null) {
                                applyMiuiPageTransition(engineConfig.pageTransitionEffect, pageOffset)
                            }
                        }
                        .then(
                            if (isWorkspaceEditMode) {
                                Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(
                                        width = 1.2.dp,
                                        color = Color.White.copy(alpha = 0.30f),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            } else {
                                Modifier
                            }
                        )
                        .onGloballyPositioned { coords ->
                            pageGridBoundsMap[pageIndex] = coords.boundsInRoot()
                        }
                        .testTag(if (pageIndex == 0) "workspace_scroll_canvas" else "workspace_page_$pageIndex")
                ) {
                    val cellWidthDp = maxWidth / gridCols
                    val cellHeightDp = maxHeight / gridRows
                    val labelReserveDp = if (engineConfig.showAppLabels) 26.dp else 10.dp
                    val maxSafeCellIconDp = minOf(
                        (cellWidthDp - 8.dp).coerceAtLeast(32.dp),
                        (cellHeightDp - labelReserveDp).coerceAtLeast(32.dp)
                    )
                    val requestedIconDp = if (isWorkspaceEditMode) {
                        (engineConfig.iconSizeDp - 4).coerceAtLeast(32).dp
                    } else {
                        engineConfig.iconSizeDp.dp
                    }
                    val effectiveCellIconSize = requestedIconDp.coerceIn(32.dp, maxSafeCellIconDp.coerceAtLeast(32.dp))

                    // Semantic testTag anchor for workspace icon grid
                    if (pageIndex == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("workspace_icon_grid")
                        )
                    }

                    // 0. Highlight Target 2D Grid Footprint during active Drag-and-Drop on the current page
                    val isDraggingAnythingOnThisPage =
                        pagerState.currentPage == pageIndex &&
                            (activeDraggedApp != null || draggedFolder != null || draggedWidget != null)
                    if (isDraggingAnythingOnThisPage) {
                        val dragSpanX = when {
                            draggedWidget != null -> draggedWidget!!.spanX.coerceIn(1, gridCols)
                            draggedFolder != null -> draggedFolder!!.spanX.coerceIn(1, gridCols)
                            else -> 1
                        }
                        val dragSpanY = when {
                            draggedWidget != null -> draggedWidget!!.spanY.coerceIn(1, gridRows)
                            draggedFolder != null -> draggedFolder!!.spanY.coerceIn(1, gridRows)
                            else -> 1
                        }
                        val (hoverCellX, hoverCellY) = computeTargetGridCell(
                            pageIndex,
                            activeDragFingerOffsetInRoot,
                            dragSpanX,
                            dragSpanY
                        )
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = cellWidthDp * hoverCellX,
                                    y = cellHeightDp * hoverCellY
                                )
                                .size(
                                    width = cellWidthDp * dragSpanX,
                                    height = cellHeightDp * dragSpanY
                                )
                                .padding(4.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(EmeraldBright.copy(alpha = 0.16f))
                                .border(1.5.dp, EmeraldBright.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
                        )
                    }

                    // 1. Render Widgets on this Page at their exact 2D (cellX, cellY, spanX, spanY) footprint
                    resolvedPage.widgets.forEach { widget ->
                        val isEditingThisWidget = editingWidgetId == widget.id
                        val clampedSpanX = widget.spanX.coerceIn(1, gridCols)
                        val clampedSpanY = widget.spanY.coerceIn(1, gridRows)
                        val clampedCellX = widget.cellX.coerceIn(0, (gridCols - clampedSpanX).coerceAtLeast(0))
                        val clampedCellY = widget.cellY.coerceIn(0, (gridRows - clampedSpanY).coerceAtLeast(0))

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = cellWidthDp * clampedCellX,
                                    y = cellHeightDp * clampedCellY
                                )
                                .size(
                                    width = cellWidthDp * clampedSpanX,
                                    height = cellHeightDp * clampedSpanY
                                )
                                .zIndex(if (isEditingThisWidget) 22f else 2f)
                                .alpha(if (draggedWidget?.id == widget.id) 0.35f else 1f)
                                .onGloballyPositioned { coords ->
                                    widgetBoundsMap[widget.id] = coords.boundsInRoot()
                                }
                        ) {
                            HyperWorkspaceWidgetCard(
                                widget = widget,
                                timeString = timeString,
                                dateString = dateString,
                                activeTheme = activeTheme,
                                engineConfig = engineConfig,
                                telemetry = deviceTelemetry,
                                allApps = allApps,
                                appWidgetHost = appWidgetHost,
                                onOpenMtzStudio = onOpenMtzStudio,
                                onOpenApiPipeline = onOpenApiPipeline,
                                onOpenWidgetsPicker = onOpenWidgetsAndFoldersPicker,
                                onRefreshTelemetry = onRefreshTelemetry,
                                onRemoveWidget = { id ->
                                    if (editingWidgetId == id) editingWidgetId = null
                                    onRemoveWidget(id)
                                },
                                onLaunchApp = onAppClick,
                                isEditingWidget = isEditingThisWidget,
                                isWorkspaceEditMode = isWorkspaceEditMode,
                                onEnterEditMode = { targetWidget ->
                                    isAnyIconHoldOrDragActive = true
                                    longPressedApp = null
                                    editingFolderId = null
                                    editingWidgetId = targetWidget.id
                                },
                                onDismissEditMode = {
                                    isAnyIconHoldOrDragActive = false
                                    editingWidgetId = null
                                },
                                onStartDragWidget = { targetWidget, posInRoot ->
                                    isAnyIconHoldOrDragActive = true
                                    longPressedApp = null
                                    editingFolderId = null
                                    editingWidgetId = null
                                    draggedWidget = targetWidget
                                    internalDragFingerOffsetInRoot = posInRoot
                                },
                                onDragWidget = { posInRoot ->
                                    internalDragFingerOffsetInRoot = posInRoot
                                },
                                onEndDragWidget = { targetWidget, dropPosInRoot ->
                                    isAnyIconHoldOrDragActive = false
                                    draggedWidget = null
                                    handleDropWidget(targetWidget, dropPosInRoot)
                                },
                                onCancelDragWidget = {
                                    isAnyIconHoldOrDragActive = false
                                    draggedWidget = null
                                },
                                onResizeWidgetSpan = { spanX, spanY ->
                                    onResizeWidgetSpan(widget.id, spanX, spanY)
                                }
                            )
                        }
                    }

                    // 2. Render Folders on this Page at their exact 2D (cellX, cellY, spanX, spanY) footprint
                    // Remaining cells in the same row(s) stay open for 1x1 app icons or other items!
                    resolvedPage.folders.forEach { folder ->
                        val isEditingThisFolder = editingFolderId == folder.id
                        val clampedSpanX = folder.spanX.coerceIn(1, gridCols)
                        val clampedSpanY = folder.spanY.coerceIn(1, gridRows)
                        val clampedCellX = folder.cellX.coerceIn(0, (gridCols - clampedSpanX).coerceAtLeast(0))
                        val clampedCellY = folder.cellY.coerceIn(0, (gridRows - clampedSpanY).coerceAtLeast(0))

                        Box(
                            contentAlignment = Alignment.TopCenter,
                            modifier = Modifier
                                .offset(
                                    x = cellWidthDp * clampedCellX,
                                    y = cellHeightDp * clampedCellY
                                )
                                .size(
                                    width = cellWidthDp * clampedSpanX,
                                    height = cellHeightDp * clampedSpanY
                                )
                                .zIndex(if (isEditingThisFolder) 20f else 3f)
                                .alpha(if (draggedFolder?.id == folder.id) 0.35f else 1f)
                                .onGloballyPositioned { coords ->
                                    folderBoundsMap[folder.id] = coords.boundsInRoot()
                                }
                        ) {
                            HyperFolderCard(
                                folder = folder,
                                onDirectLaunchApp = { app ->
                                    if (isWorkspaceEditMode) {
                                        if (app.packageName in selectedEditPackages) {
                                            selectedEditPackages.remove(app.packageName)
                                        } else {
                                            selectedEditPackages.add(app.packageName)
                                        }
                                    } else {
                                        onAppClick(app)
                                    }
                                },
                                onAppLongClick = {
                                    editingWidgetId = null
                                    editingFolderId = folder.id
                                },
                                onExpandFolder = {
                                    if (editingFolderId != null) {
                                        editingFolderId = null
                                    } else {
                                        onExpandFolder(folder)
                                    }
                                },
                                onCycleLayoutMode = onCycleFolderMode,
                                isEditingFolder = isEditingThisFolder,
                                isWorkspaceEditMode = isWorkspaceEditMode,
                                onEnterEditMode = {
                                    isAnyIconHoldOrDragActive = true
                                    longPressedApp = null
                                    editingWidgetId = null
                                    editingFolderId = folder.id
                                },
                                onDismissEditMode = {
                                    isAnyIconHoldOrDragActive = false
                                    editingFolderId = null
                                },
                                onResizeFolderSpan = { spanX, spanY ->
                                    onResizeFolderSpan(folder.id, spanX, spanY)
                                },
                                onRenameFolderRequest = { target ->
                                    renameText = target.title
                                    folderPendingRename = target
                                },
                                onCycleBackgroundColor = { target ->
                                    onCycleFolderBackgroundColor(target.id)
                                },
                                onDeleteFolderRequest = { target ->
                                    editingFolderId = null
                                    onDeleteFolder(target.id)
                                },
                                onStartDragAppFromFolder = { slotApp, posInRoot ->
                                    isAnyIconHoldOrDragActive = true
                                    editingFolderId = null
                                    editingWidgetId = null
                                    longPressedApp = null
                                    internalDraggedApp = slotApp
                                    internalDragSourceFolderId = folder.id
                                    internalDragFingerOffsetInRoot = posInRoot
                                },
                                onDragAppFromFolder = { posInRoot ->
                                    internalDragFingerOffsetInRoot = posInRoot
                                },
                                onEndDragAppFromFolder = { slotApp, dropPosInRoot ->
                                    isAnyIconHoldOrDragActive = false
                                    internalDraggedApp = null
                                    internalDragSourceFolderId = null
                                    handleDropApp(slotApp, folder.id, dropPosInRoot)
                                },
                                onCancelDragAppFromFolder = {
                                    isAnyIconHoldOrDragActive = false
                                    internalDraggedApp = null
                                    internalDragSourceFolderId = null
                                },
                                onStartDragFolder = { targetFolder, posInRoot ->
                                    isAnyIconHoldOrDragActive = true
                                    longPressedApp = null
                                    editingFolderId = null
                                    editingWidgetId = null
                                    draggedFolder = targetFolder
                                    internalDragFingerOffsetInRoot = posInRoot
                                },
                                onDragFolder = { posInRoot ->
                                    internalDragFingerOffsetInRoot = posInRoot
                                },
                                onEndDragFolder = { targetFolder, dropPosInRoot ->
                                    isAnyIconHoldOrDragActive = false
                                    draggedFolder = null
                                    handleDropFolder(targetFolder, dropPosInRoot)
                                },
                                onCancelDragFolder = {
                                    isAnyIconHoldOrDragActive = false
                                    draggedFolder = null
                                },
                                iconSize = effectiveCellIconSize,
                                showLabel = engineConfig.showAppLabels
                            )
                        }
                    }

                    // 3. Render 1x1 App Icons on this Page at their exact (cellX, cellY) coordinates
                    resolvedPage.apps.forEach { app ->
                        val clampedCellX = app.cellX.coerceIn(0, gridCols - 1)
                        val clampedCellY = app.cellY.coerceIn(0, gridRows - 1)
                        var itemBoundsInRoot by remember(app.packageName) { mutableStateOf(Rect.Zero) }
                        var isPickedUp by remember(app.packageName) { mutableStateOf(false) }
                        val isSelectedInEdit = app.packageName in selectedEditPackages

                        Box(
                            contentAlignment = Alignment.TopCenter,
                            modifier = Modifier
                                .offset(
                                    x = cellWidthDp * clampedCellX,
                                    y = cellHeightDp * clampedCellY
                                )
                                .size(
                                    width = cellWidthDp,
                                    height = cellHeightDp
                                )
                                .zIndex(4f)
                                .alpha(if (activeDraggedApp?.packageName == app.packageName) 0.3f else 1f)
                                .onGloballyPositioned { coords ->
                                    val rect = coords.boundsInRoot()
                                    itemBoundsInRoot = rect
                                    appBoundsMap[app.packageName] = rect
                                }
                                .miuiIconTapLongPressAndDrag(
                                    key1 = app.packageName,
                                    key2 = pageIndex,
                                    dragThreshold = 12.dp,
                                    enableImmediateDrag = isWorkspaceEditMode,
                                    onTap = {
                                        when {
                                            longPressedApp != null -> longPressedApp = null
                                            editingFolderId != null -> editingFolderId = null
                                            editingWidgetId != null -> editingWidgetId = null
                                            isWorkspaceEditMode -> {
                                                if (app.packageName in selectedEditPackages) {
                                                    selectedEditPackages.remove(app.packageName)
                                                } else {
                                                    selectedEditPackages.add(app.packageName)
                                                }
                                            }
                                            else -> onAppClick(app)
                                        }
                                    },
                                    onLongPressReached = {
                                        isPickedUp = true
                                        isAnyIconHoldOrDragActive = true
                                        if (isWorkspaceEditMode) {
                                            if (app.packageName !in selectedEditPackages) {
                                                selectedEditPackages.add(app.packageName)
                                            }
                                        } else {
                                            editingFolderId = null
                                            editingWidgetId = null
                                            longPressedAppBounds = itemBoundsInRoot
                                            longPressedApp = app
                                        }
                                    },
                                    onReleaseWithoutDrag = {
                                        isPickedUp = false
                                        isAnyIconHoldOrDragActive = false
                                    },
                                    onDragStart = { fingerRootPos ->
                                        isPickedUp = false
                                        isAnyIconHoldOrDragActive = true
                                        longPressedApp = null
                                        editingFolderId = null
                                        editingWidgetId = null
                                        internalDraggedApp = app
                                        internalDragSourceFolderId = null
                                        internalDragFingerOffsetInRoot = fingerRootPos
                                    },
                                    onDragMove = { fingerRootPos, _ ->
                                        internalDragFingerOffsetInRoot = fingerRootPos
                                    },
                                    onDragDrop = { fingerRootPos ->
                                        isPickedUp = false
                                        isAnyIconHoldOrDragActive = false
                                        val currentDragged = internalDraggedApp ?: app
                                        internalDraggedApp = null
                                        internalDragSourceFolderId = null
                                        handleDropApp(currentDragged, null, fingerRootPos)
                                    },
                                    onDragCancel = {
                                        isPickedUp = false
                                        isAnyIconHoldOrDragActive = false
                                        internalDraggedApp = null
                                        internalDragSourceFolderId = null
                                    }
                                )
                        ) {
                            AppIconTile(
                                app = app,
                                iconSize = effectiveCellIconSize,
                                showLabel = engineConfig.showAppLabels,
                                showApiBadge = engineConfig.showApiSourceBadges,
                                showEditSelectionBadge = isWorkspaceEditMode,
                                isSelectedForEdit = isSelectedInEdit,
                                enableClickHandling = false,
                                isPickedUp = isPickedUp || longPressedApp?.packageName == app.packageName,
                                onClick = {},
                                onLongClick = {}
                            )
                        }
                    }
                }
            }

            // Stock MIUI Page Indicator Dots (`• • • • •` + optional `+` in Edit Mode)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("workspace_page_indicators")
            ) {
                for (i in 0 until pageCount) {
                    val isCurrent = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isCurrent) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCurrent) Color.White
                                else Color.White.copy(alpha = 0.36f)
                            )
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(i)
                                }
                            }
                            .testTag("page_indicator_dot_$i")
                    )
                }
                if (isWorkspaceEditMode) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Workspace Page",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                            .clickable {
                                if (selectedEditPackages.isNotEmpty()) {
                                    val newPageIdx = basePageCount
                                    onBatchMoveAppsToPage(selectedEditPackages.toList(), newPageIdx)
                                    selectedEditPackages.clear()
                                    coroutineScope.launch {
                                        delay(80L)
                                        pagerState.animateScrollToPage(newPageIdx.coerceAtMost(pageCount - 1))
                                    }
                                } else {
                                    extraDynamicPages = (extraDynamicPages + 1).coerceAtMost(3)
                                    coroutineScope.launch {
                                        delay(60L)
                                        pagerState.animateScrollToPage((pageCount - 1).coerceAtLeast(0))
                                    }
                                }
                            }
                            .testTag("page_indicator_add_button")
                    )
                }
            }

            // Bottom Area:
            // - Normal Mode: Stock MIUI 4-Icon Bottom Dock
            // - Edit Mode (0 icons selected): 4 Circular MIUI Edit Action Buttons (`Wallpaper`, `Widget`, `Effect`, `Home screen settings`)
            // - Edit Mode (1+ icons selected): Horizontal Workspace Page Thumbnail Strip + `+` Card
            if (isWorkspaceEditMode) {
                if (selectedEditPackages.isNotEmpty()) {
                    MiuiWorkspacePageThumbnailStrip(
                        pageCount = pageCount,
                        currentPage = pagerState.currentPage,
                        workspaceApps = workspaceApps,
                        workspaceFolders = workspaceFolders,
                        selectedCount = selectedEditPackages.size,
                        onPageThumbBoundsChanged = { pageIdx, rect ->
                            pageThumbBoundsMap[pageIdx] = rect
                        },
                        onAddPageThumbBoundsChanged = { rect ->
                            addPageThumbBounds = rect
                        },
                        onSelectTargetPage = { targetPage ->
                            onBatchMoveAppsToPage(selectedEditPackages.toList(), targetPage)
                            selectedEditPackages.clear()
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetPage.coerceIn(0, pageCount - 1))
                            }
                        },
                        onSelectAddNewPage = {
                            val newPageIdx = basePageCount
                            onBatchMoveAppsToPage(selectedEditPackages.toList(), newPageIdx)
                            selectedEditPackages.clear()
                            coroutineScope.launch {
                                delay(80L)
                                pagerState.animateScrollToPage(newPageIdx.coerceIn(0, pageCount - 1))
                            }
                        }
                    )
                } else {
                    MiuiWorkspaceEditBottomBar(
                        onOpenWallpaper = { showWallpaperQuickSheet = true },
                        onOpenWidgets = onOpenWidgetsAndFoldersPicker,
                        onOpenEffects = { showTransitionEffectSheet = true },
                        onOpenHomeSettings = { showDesktopOptionsSheet = true }
                    )
                }
            } else {
                // Stock MIUI Bottom Pin Bar (Dock): up to `gridCols` (4, 5, or 6) icons, centered when fewer
                val visibleDockApps = dockApps.take(gridCols)
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 64.dp)
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .onGloballyPositioned { coords ->
                            dockBounds = coords.boundsInRoot()
                        }
                        .testTag("workspace_dock")
                ) {
                    val slotWidthDp = maxWidth / gridCols
                    val maxSafeDockIconDp = (slotWidthDp - 8.dp).coerceAtLeast(32.dp)
                    val effectiveDockIconSize = engineConfig.iconSizeDp.dp.coerceIn(
                        32.dp,
                        maxSafeDockIconDp.coerceAtMost(72.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        visibleDockApps.forEach { app ->
                            var dockItemBounds by remember(app.packageName) { mutableStateOf(Rect.Zero) }
                            var isPickedUp by remember(app.packageName) { mutableStateOf(false) }
                            val showUnreadBadge = app.packageName.contains("mms", ignoreCase = true) ||
                                app.label.equals("Messaging", ignoreCase = true)

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.width(slotWidthDp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.TopEnd,
                                    modifier = Modifier
                                        .alpha(if (activeDraggedApp?.packageName == app.packageName) 0.3f else 1f)
                                        .onGloballyPositioned { coords ->
                                            val rect = coords.boundsInRoot()
                                            dockItemBounds = rect
                                            appBoundsMap[app.packageName] = rect
                                        }
                                        .miuiIconTapLongPressAndDrag(
                                            key1 = app.packageName,
                                            key2 = "dock",
                                            dragThreshold = 12.dp,
                                            onTap = {
                                                if (longPressedApp != null) {
                                                    longPressedApp = null
                                                } else {
                                                    onAppClick(app)
                                                }
                                            },
                                            onLongPressReached = {
                                                isPickedUp = true
                                                isAnyIconHoldOrDragActive = true
                                                editingFolderId = null
                                                editingWidgetId = null
                                                longPressedAppBounds = dockItemBounds
                                                longPressedApp = app
                                            },
                                            onReleaseWithoutDrag = {
                                                isPickedUp = false
                                                isAnyIconHoldOrDragActive = false
                                            },
                                            onDragStart = { fingerRootPos ->
                                                isPickedUp = false
                                                isAnyIconHoldOrDragActive = true
                                                longPressedApp = null
                                                editingFolderId = null
                                                editingWidgetId = null
                                                internalDraggedApp = app
                                                internalDragSourceFolderId = null
                                                internalDragFingerOffsetInRoot = fingerRootPos
                                            },
                                            onDragMove = { fingerRootPos, _ ->
                                                internalDragFingerOffsetInRoot = fingerRootPos
                                            },
                                            onDragDrop = { fingerRootPos ->
                                                isPickedUp = false
                                                isAnyIconHoldOrDragActive = false
                                                val currentDragged = internalDraggedApp ?: app
                                                internalDraggedApp = null
                                                internalDragSourceFolderId = null
                                                handleDropApp(currentDragged, null, fingerRootPos)
                                            },
                                            onDragCancel = {
                                                isPickedUp = false
                                                isAnyIconHoldOrDragActive = false
                                                internalDraggedApp = null
                                                internalDragSourceFolderId = null
                                            }
                                        )
                                ) {
                                    AppIconTile(
                                        app = app,
                                        iconSize = effectiveDockIconSize,
                                        showLabel = false,
                                        showApiBadge = engineConfig.showApiSourceBadges,
                                        enableClickHandling = false,
                                        isPickedUp = isPickedUp || longPressedApp?.packageName == app.packageName,
                                        onClick = {},
                                        onLongClick = {}
                                    )

                                    if (showUnreadBadge) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .offset(x = (-2).dp, y = 2.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF3B30))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "20",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // MIUI Floating App Long-Press Action Pill
        val pillTargetApp = longPressedApp
        if (pillTargetApp != null && activeDraggedApp == null) {
            val pillWidthPx = with(density) { 300.dp.toPx() }
            val pillHeightPx = with(density) { 84.dp.toPx() }
            val iconCenterX = longPressedAppBounds.center.x - rootBounds.left
            val rawX = (iconCenterX - pillWidthPx / 2f)
                .coerceIn(16f, (rootBounds.width - pillWidthPx - 16f).coerceAtLeast(16f))
            val aboveY = longPressedAppBounds.top - rootBounds.top - pillHeightPx - 12f
            val belowY = longPressedAppBounds.bottom - rootBounds.top + 12f
            val finalY = if (aboveY > 24f) aboveY else belowY.coerceAtLeast(24f)

            MiuiAppFloatingActionPill(
                app = pillTargetApp,
                showDeleteShortcut = true,
                onDeleteShortcut = {
                    val target = pillTargetApp
                    longPressedApp = null
                    onDeleteAppShortcut(target)
                },
                onUninstallApp = {
                    val target = pillTargetApp
                    longPressedApp = null
                    onUninstallApp(target)
                },
                onShareApp = {
                    val target = pillTargetApp
                    longPressedApp = null
                    onShareApp(target)
                },
                onAppInfo = {
                    val target = pillTargetApp
                    longPressedApp = null
                    onOpenAppInfo(target)
                },
                onEditIcon = {
                    val target = pillTargetApp
                    longPressedApp = null
                    onAppLongClick(target)
                },
                modifier = Modifier
                    .offset {
                        IntOffset(rawX.roundToInt(), finalY.roundToInt())
                    }
                    .zIndex(48f)
            )
        }

        // MIUI Floating Folder Long-Press Action Pill (Delete | Rename | Icon size | Background color)
        val pillTargetFolder = editingFolderId?.let { fId ->
            workspaceFolders.firstOrNull { it.id == fId }
        }
        if (pillTargetFolder != null && draggedFolder == null) {
            val folderRect = folderBoundsMap[pillTargetFolder.id] ?: Rect.Zero
            val pillWidthPx = with(density) { 308.dp.toPx() }
            val pillHeightPx = with(density) { 80.dp.toPx() }
            val centerX = folderRect.center.x - rootBounds.left
            val rawX = (centerX - pillWidthPx / 2f)
                .coerceIn(12f, (rootBounds.width - pillWidthPx - 12f).coerceAtLeast(12f))
            val aboveY = folderRect.top - rootBounds.top - pillHeightPx - 8f
            val belowY = folderRect.bottom - rootBounds.top + 8f
            val finalY = if (aboveY > 18f) aboveY else belowY.coerceAtLeast(18f)

            MiuiFolderFloatingActionPill(
                folder = pillTargetFolder,
                onDelete = {
                    editingFolderId = null
                    onDeleteFolder(pillTargetFolder.id)
                },
                onRename = {
                    renameText = pillTargetFolder.title
                    folderPendingRename = pillTargetFolder
                },
                onCycleSize = {
                    onCycleFolderMode(pillTargetFolder)
                },
                onCycleBgColor = {
                    onCycleFolderBackgroundColor(pillTargetFolder.id)
                },
                modifier = Modifier
                    .offset {
                        IntOffset(rawX.roundToInt(), finalY.roundToInt())
                    }
                    .zIndex(49f)
            )
        }

        // MIUI Floating Widget Long-Press Action Pill (`Remove` | `Resize`)
        val pillTargetWidget = editingWidgetId?.let { wId ->
            workspaceWidgets.firstOrNull { it.id == wId }
        }
        if (pillTargetWidget != null && draggedWidget == null) {
            val widgetRect = widgetBoundsMap[pillTargetWidget.id] ?: Rect.Zero
            val pillWidthPx = with(density) { 176.dp.toPx() }
            val pillHeightPx = with(density) { 78.dp.toPx() }
            val centerX = widgetRect.center.x - rootBounds.left
            val rawX = (centerX - pillWidthPx / 2f)
                .coerceIn(16f, (rootBounds.width - pillWidthPx - 16f).coerceAtLeast(16f))
            val aboveY = widgetRect.top - rootBounds.top - pillHeightPx - 8f
            val belowY = widgetRect.bottom - rootBounds.top + 8f
            val finalY = if (aboveY > 18f) aboveY else belowY.coerceAtLeast(18f)

            MiuiWidgetFloatingActionPill(
                widget = pillTargetWidget,
                onRemove = {
                    editingWidgetId = null
                    isAnyIconHoldOrDragActive = false
                    onRemoveWidget(pillTargetWidget.id)
                },
                onResize = {
                    onCycleWidgetSize(pillTargetWidget.id)
                },
                modifier = Modifier
                    .offset {
                        IntOffset(rawX.roundToInt(), finalY.roundToInt())
                    }
                    .zIndex(49f)
            )
        }

        // Floating Drag Preview Shadow for Icon Drag-and-Drop (Workspace, Drawer, or Folder Drag-Out)
        if (activeDraggedApp != null) {
            val localOffset = activeDragFingerOffsetInRoot - rootBounds.topLeft
            val halfIconPx = with(density) { (engineConfig.iconSizeDp.dp / 2).toPx() }
            Image(
                bitmap = activeDraggedApp.themedIconBitmap.asImageBitmap(),
                contentDescription = activeDraggedApp.label,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (localOffset.x - halfIconPx).roundToInt(),
                            (localOffset.y - halfIconPx).roundToInt()
                        )
                    }
                    .size((engineConfig.iconSizeDp + 10).dp)
                    .alpha(0.92f)
                    .zIndex(60f)
            )
        }

        // Floating Drag Preview Shadow for Folder Drag-and-Drop across Workspace Pages
        val activeDraggedFolder = draggedFolder
        if (activeDraggedFolder != null) {
            val localOffset = activeDragFingerOffsetInRoot - rootBounds.topLeft
            val halfCardPx = with(density) { 42.dp.toPx() }
            Surface(
                color = Color(0xFF2B2B2E).copy(alpha = 0.88f),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (localOffset.x - halfCardPx).roundToInt(),
                            (localOffset.y - halfCardPx).roundToInt()
                        )
                    }
                    .size(84.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(22.dp))
                    .zIndex(60f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = activeDraggedFolder.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }

        // Floating Drag Preview Shadow for Widget Drag-and-Drop across Workspace Pages & Cells
        val activeDraggedWidget = draggedWidget
        if (activeDraggedWidget != null) {
            val localOffset = activeDragFingerOffsetInRoot - rootBounds.topLeft
            val previewWidthDp = (activeDraggedWidget.spanX * 64).coerceIn(110, 240).dp
            val previewHeightDp = (activeDraggedWidget.spanY * 48).coerceIn(64, 128).dp
            val halfW = with(density) { (previewWidthDp / 2).toPx() }
            val halfH = with(density) { (previewHeightDp / 2).toPx() }
            Surface(
                color = SlateSurface.copy(alpha = 0.90f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (localOffset.x - halfW).roundToInt(),
                            (localOffset.y - halfH).roundToInt()
                        )
                    }
                    .size(width = previewWidthDp, height = previewHeightDp)
                    .border(1.8.dp, EmeraldBright, RoundedCornerShape(20.dp))
                    .zIndex(60f)
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    Text(
                        text = activeDraggedWidget.customTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${activeDraggedWidget.spanX}x${activeDraggedWidget.spanY} Widget",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldBright
                    )
                }
            }
        }
    }

    // Inline Folder Rename Dialog triggered from Floating Popup Pill
    val renameTarget = folderPendingRename
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { folderPendingRename = null },
            containerColor = SlateSurface,
            title = { Text("Rename folder", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldBright,
                        unfocusedBorderColor = SlateBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameFolder(renameTarget.id, renameText)
                        folderPendingRename = null
                    }
                ) {
                    Text("OK", color = EmeraldBright)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderPendingRename = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showWallpaperQuickSheet) {
        MiuiWallpaperPickerModal(
            onDismiss = { showWallpaperQuickSheet = false },
            onPickCustomWallpaper = {
                showWallpaperQuickSheet = false
                onPickCustomWallpaper()
            },
            onResetToThemeWallpaper = {
                showWallpaperQuickSheet = false
                onResetToThemeWallpaper()
            },
            onOpenMtzStudio = {
                showWallpaperQuickSheet = false
                onOpenMtzStudio()
            }
        )
    }

    if (showTransitionEffectSheet) {
        MiuiTransitionEffectModal(
            currentEffect = engineConfig.pageTransitionEffect,
            onSelectEffect = { effect ->
                onUpdatePageTransitionEffect(effect)
                showTransitionEffectSheet = false
            },
            onDismiss = { showTransitionEffectSheet = false }
        )
    }

    if (showDesktopOptionsSheet) {
        DesktopOptionsBottomSheet(
            engineConfig = engineConfig,
            onDismiss = { showDesktopOptionsSheet = false },
            onUpdateGridColumns = onUpdateGridColumns,
            onUpdateGridRows = onUpdateGridRows,
            onUpdateIconSizeDp = onUpdateIconSizeDp,
            onToggleShowLabels = onToggleShowLabels,
            onOpenWidgetsAndFoldersPicker = {
                showDesktopOptionsSheet = false
                onOpenWidgetsAndFoldersPicker()
            },
            onOpenMtzStudio = {
                showDesktopOptionsSheet = false
                onOpenMtzStudio()
            },
            onOpenApiPipeline = {
                showDesktopOptionsSheet = false
                onOpenApiPipeline()
            },
            onOpenSettings = {
                showDesktopOptionsSheet = false
                onOpenSettings()
            },
            onSetDefaultHomeLauncher = {
                showDesktopOptionsSheet = false
                onSetDefaultHomeLauncher()
            },
            onPickCustomWallpaper = {
                showDesktopOptionsSheet = false
                onPickCustomWallpaper()
            },
            onResetToThemeWallpaper = {
                showDesktopOptionsSheet = false
                onResetToThemeWallpaper()
            }
        )
    }
}

/**
 * Applies MIUI 3D/Fade Page Transition Effects (`Classic`, `Cube`, `Cascade`, `Flip`, `Rotate`)
 * to each page of the Workspace `HorizontalPager`.
 */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyMiuiPageTransition(
    effect: MiuiPageTransitionEffect,
    pageOffset: Float
) {
    val clamped = pageOffset.coerceIn(-1f, 1f)
    when (effect) {
        MiuiPageTransitionEffect.CLASSIC -> {
            alpha = 1f
        }
        MiuiPageTransitionEffect.CUBE -> {
            cameraDistance = 14f * density
            TransformOrigin(if (clamped < 0f) 0f else 1f, 0.5f).also { transformOrigin = it }
            rotationY = -clamped * 65f
            alpha = (1f - abs(clamped) * 0.25f).coerceIn(0.4f, 1f)
        }
        MiuiPageTransitionEffect.CASCADE -> {
            if (clamped < 0f) {
                val scale = (1f + clamped * 0.22f).coerceIn(0.75f, 1f)
                scaleX = scale
                scaleY = scale
                alpha = (1f + clamped * 0.7f).coerceIn(0.2f, 1f)
            }
        }
        MiuiPageTransitionEffect.FLIP -> {
            cameraDistance = 16f * density
            rotationY = -clamped * 110f
            alpha = if (abs(clamped) > 0.65f) 0.2f else 1f
        }
        MiuiPageTransitionEffect.ROTATE -> {
            transformOrigin = TransformOrigin(0.5f, 1f)
            rotationZ = -clamped * 24f
            alpha = (1f - abs(clamped) * 0.3f).coerceIn(0.4f, 1f)
        }
    }
}

/**
 * Top bar in MIUI Workspace Edit Mode (`Screenshot_20261025_022640_61a06b62.jpg`):
 * `Align with top` | `Home (Default Page)` | `Align with bottom`
 */
@Composable
private fun MiuiWorkspaceEditTopBar(
    isDefaultHomePage: Boolean,
    onAlignTop: () -> Unit,
    onSetDefaultHome: () -> Unit,
    onAlignBottom: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("workspace_edit_top_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        MiuiEditCircleButton(
            icon = Icons.Default.VerticalAlignTop,
            label = "Align with top",
            isHighlighted = false,
            onClick = onAlignTop,
            testTag = "edit_align_top_button"
        )
        MiuiEditCircleButton(
            icon = Icons.Default.Home,
            label = "",
            isHighlighted = isDefaultHomePage,
            onClick = onSetDefaultHome,
            testTag = "edit_default_home_button"
        )
        MiuiEditCircleButton(
            icon = Icons.Default.VerticalAlignBottom,
            label = "Align with bottom",
            isHighlighted = false,
            onClick = onAlignBottom,
            testTag = "edit_align_bottom_button"
        )
    }
}

/**
 * Bottom 4-button bar in MIUI Workspace Edit Mode when 0 icons are selected (`Screenshot_20261025_022640_61a06b62.jpg`):
 * `Wallpaper` | `Widget` | `Effect` | `Home screen settings`
 */
@Composable
private fun MiuiWorkspaceEditBottomBar(
    onOpenWallpaper: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenEffects: () -> Unit,
    onOpenHomeSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .testTag("workspace_edit_bottom_bar"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        MiuiEditCircleButton(
            icon = Icons.Default.Wallpaper,
            label = "Wallpaper",
            isHighlighted = false,
            onClick = onOpenWallpaper,
            testTag = "edit_btn_wallpaper"
        )
        MiuiEditCircleButton(
            icon = Icons.Default.Widgets,
            label = "Widget",
            isHighlighted = false,
            onClick = onOpenWidgets,
            testTag = "edit_btn_widget"
        )
        MiuiEditCircleButton(
            icon = Icons.Default.Animation,
            label = "Effect",
            isHighlighted = false,
            onClick = onOpenEffects,
            testTag = "edit_btn_effect"
        )
        MiuiEditCircleButton(
            icon = Icons.Default.Settings,
            label = "Home screen\nsettings",
            isHighlighted = false,
            onClick = onOpenHomeSettings,
            testTag = "edit_btn_home_settings"
        )
    }
}

@Composable
private fun MiuiEditCircleButton(
    icon: ImageVector,
    label: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    if (isHighlighted) Color.White.copy(alpha = 0.34f)
                    else Color(0xFF3A3F47).copy(alpha = 0.72f)
                )
                .border(
                    width = 1.dp,
                    color = if (isHighlighted) Color.White else Color.White.copy(alpha = 0.22f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label.ifEmpty { "Default Home" },
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp),
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Horizontal Workspace Page Thumbnail Strip shown at the bottom of Edit Mode when 1+ icons are selected
 * (`Screenshot_20261025_022655_a0b875de.jpg`).
 * Tapping any page thumbnail or the trailing `+` card batch-moves all selected icons to that page.
 */
@Composable
private fun MiuiWorkspacePageThumbnailStrip(
    pageCount: Int,
    currentPage: Int,
    workspaceApps: List<InstalledAppItem>,
    workspaceFolders: List<WorkspaceFolder>,
    selectedCount: Int,
    onPageThumbBoundsChanged: (Int, Rect) -> Unit,
    onAddPageThumbBoundsChanged: (Rect) -> Unit,
    onSelectTargetPage: (Int) -> Unit,
    onSelectAddNewPage: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workspace_edit_page_strip")
    ) {
        items(pageCount, key = { "thumb_page_$it" }) { pageIdx ->
            val isCurrent = pageIdx == currentPage
            val appsOnPage = workspaceApps.filter { it.workspacePage == pageIdx }
            val foldersOnPage = workspaceFolders.filter { it.pageIndex == pageIdx }

            Surface(
                color = Color(0xFF2E343D).copy(alpha = 0.82f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .size(width = 68.dp, height = 104.dp)
                    .border(
                        width = if (isCurrent) 1.6.dp else 1.dp,
                        color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .onGloballyPositioned { coords ->
                        onPageThumbBoundsChanged(pageIdx, coords.boundsInRoot())
                    }
                    .clickable { onSelectTargetPage(pageIdx) }
                    .testTag("edit_page_thumb_$pageIdx")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                ) {
                    if (foldersOnPage.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color.White.copy(alpha = 0.28f))
                        )
                    }
                    val previewRows = appsOnPage.take(8).chunked(3)
                    previewRows.forEach { rowApps ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowApps.forEach { app ->
                                Image(
                                    bitmap = app.themedIconBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Trailing "+" Card to create a new page and move selected icons there
        item(key = "thumb_add_page") {
            Surface(
                color = Color(0xFF2E343D).copy(alpha = 0.72f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .size(width = 68.dp, height = 104.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                    .onGloballyPositioned { coords ->
                        onAddPageThumbBoundsChanged(coords.boundsInRoot())
                    }
                    .clickable(onClick = onSelectAddNewPage)
                    .testTag("edit_page_thumb_add")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Move $selectedCount apps to new page",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiuiWallpaperPickerModal(
    onDismiss: () -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onResetToThemeWallpaper: () -> Unit,
    onOpenMtzStudio: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("wallpaper_picker_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Wallpaper",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = EmeraldContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, EmeraldBright, RoundedCornerShape(12.dp))
                        .clickable(onClick = onPickCustomWallpaper)
                ) {
                    Text(
                        text = "Pick Custom Wallpaper",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldBright,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                }
                Surface(
                    color = SlateSurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onResetToThemeWallpaper)
                ) {
                    Text(
                        text = "Restore MTZ Wallpaper",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                }
            }
            Surface(
                color = SlateSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenMtzStudio)
            ) {
                Text(
                    text = "Open MTZ Theme Studio",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldBright,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiuiTransitionEffectModal(
    currentEffect: MiuiPageTransitionEffect,
    onSelectEffect: (MiuiPageTransitionEffect) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("transition_effect_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Transition effects",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiuiPageTransitionEffect.entries.forEach { effect ->
                    val isSelected = effect == currentEffect
                    Surface(
                        color = if (isSelected) EmeraldContainer else SlateSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) EmeraldBright else SlateBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectEffect(effect) }
                            .testTag("effect_option_${effect.code}")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Animation,
                                contentDescription = effect.displayName,
                                tint = if (isSelected) EmeraldBright else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = effect.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) EmeraldBright else TextPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopOptionsBottomSheet(
    engineConfig: LauncherEngineConfig,
    onDismiss: () -> Unit,
    onUpdateGridColumns: (Int) -> Unit,
    onUpdateGridRows: (Int) -> Unit,
    onUpdateIconSizeDp: (Int) -> Unit,
    onToggleShowLabels: (Boolean) -> Unit,
    onOpenWidgetsAndFoldersPicker: () -> Unit,
    onOpenMtzStudio: () -> Unit,
    onOpenApiPipeline: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetDefaultHomeLauncher: () -> Unit,
    onPickCustomWallpaper: () -> Unit,
    onResetToThemeWallpaper: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("desktop_options_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MIUI Home & MTZ Options",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Customize themes, wallpapers, folders, and icon pipeline",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = SlateSurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenApiPipeline)
                    ) {
                        Text(
                            text = "Inspector",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldBright,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        color = EmeraldContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable(onClick = onOpenSettings)
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldBright,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = SlateBorder)

            // Grid Columns Selector (4, 5, 6)
            Column {
                Text(
                    text = "Desktop Grid Columns",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                .testTag("quick_grid_cols_$cols")
                        ) {
                            Text(
                                text = "$cols Cols",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) EmeraldBright else TextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // Grid Rows Selector (6, 7, 8)
            Column {
                Text(
                    text = "Desktop Grid Rows",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                .testTag("quick_grid_rows_$rows")
                        ) {
                            Text(
                                text = "$rows Rows",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) EmeraldBright else TextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // Icon Size (36..72 dp with automatic cell-bound clamping)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Icon Size (Auto-clamped to cell)",
                        style = MaterialTheme.typography.labelLarge,
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
                    )
                )
            }

            // Wallpaper Selection Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = EmeraldContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, EmeraldBright, RoundedCornerShape(10.dp))
                        .clickable(onClick = onPickCustomWallpaper)
                ) {
                    Text(
                        text = "Pick Custom Wallpaper",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldBright,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                Surface(
                    color = SlateSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                        .clickable(onClick = onResetToThemeWallpaper)
                ) {
                    Text(
                        text = "Use MTZ Wallpaper",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }

            // Quick Actions Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = SlateSurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenWidgetsAndFoldersPicker)
                        .padding(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "Folders & Widgets",
                            tint = EmeraldBright,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Folders & Widgets",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }

                Surface(
                    color = SlateSurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenMtzStudio)
                        .padding(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "MTZ Themes",
                            tint = EmeraldBright,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "MTZ Studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }

                Surface(
                    color = SlateSurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                        .clickable(onClick = onSetDefaultHomeLauncher)
                        .padding(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Default Home",
                            tint = EmeraldBright,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Set Default",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
