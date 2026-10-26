package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.InstalledAppItem
import com.example.ui.components.AppIconTile
import com.example.ui.components.MiuiAppFloatingActionPill
import com.example.ui.components.miuiIconTapLongPressAndDrag
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Stock MIUI App Drawer (`Screenshot_20261024_193605_9773862d.jpg`):
 * - Translucent dark backdrop over the home wallpaper
 * - Rounded pill "Search apps" bar at the top + 3-dot (`⋮`) menu button on the right
 * - Clean 4-column scrolling grid of `.mtz`-styled app icons with white labels
 * - Pull-down at top of grid or Back gesture closes the drawer back to the workspace
 */
@Composable
fun AppDrawerScreen(
    allApps: List<InstalledAppItem>,
    searchQuery: String,
    activeSourceFilter: IconResolutionSource?,
    onSearchQueryChange: (String) -> Unit,
    onSourceFilterSelect: (IconResolutionSource?) -> Unit,
    onLaunchApp: (InstalledAppItem) -> Unit,
    onInspectApp: (InstalledAppItem) -> Unit,
    onOpenAppOptions: (InstalledAppItem) -> Unit,
    onCloseDrawer: () -> Unit = {},
    onOpenMtzStudio: () -> Unit = {},
    onOpenApiPipeline: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onPickCustomWallpaper: () -> Unit = {},
    onResetToThemeWallpaper: () -> Unit = {},
    onDeleteShortcutFromWorkspace: (InstalledAppItem) -> Unit = {},
    onUninstallApp: (InstalledAppItem) -> Unit = {},
    onShareApp: (InstalledAppItem) -> Unit = {},
    onOpenAppInfo: (InstalledAppItem) -> Unit = {},
    onStartDragToWorkspace: (InstalledAppItem, Offset) -> Unit = { _, _ -> },
    onUpdateDragToWorkspace: (Offset) -> Unit = {},
    onDropDragToWorkspace: (InstalledAppItem, Offset) -> Unit = { _, _ -> },
    onCancelDragToWorkspace: () -> Unit = {},
    overlayShadeAlpha: Float = 0.60f
) {
    var longPressedDrawerApp by remember { mutableStateOf<InstalledAppItem?>(null) }
    var isDraggingToWorkspace by remember { mutableStateOf(false) }

    BackHandler(onBack = {
        if (longPressedDrawerApp != null) {
            longPressedDrawerApp = null
        } else {
            onCloseDrawer()
        }
    })

    var showOverflowMenu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    var overscrollPullDownPx by remember { mutableFloatStateOf(0f) }

    val pullDownCloseConnection = remember(gridState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f) {
                    overscrollPullDownPx = 0f
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0
                ) {
                    overscrollPullDownPx += available.y
                    if (overscrollPullDownPx > 140f) {
                        overscrollPullDownPx = 0f
                        onCloseDrawer()
                    }
                }
                return Offset.Zero
            }
        }
    }

    val filteredApps = remember(allApps, searchQuery, activeSourceFilter) {
        allApps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesSource = activeSourceFilter == null || app.resolutionSource == activeSourceFilter
            matchesQuery && matchesSource
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(35f)
            .alpha(if (isDraggingToWorkspace) 0f else 1f)
            .background(Color.Black.copy(alpha = overlayShadeAlpha.coerceIn(0f, 0.95f)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (longPressedDrawerApp != null) {
                        longPressedDrawerApp = null
                    }
                }
            )
            .nestedScroll(pullDownCloseConnection)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("app_drawer_screen")
    ) {
        // Top Pill Search Bar + 3-Dot (`⋮`) Overflow Menu Button matching Screenshot_20261024_193605_9773862d.jpg
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
                    .testTag("drawer_search_input")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search apps",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search apps",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                color = Color.White.copy(alpha = 0.62f)
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("drawer_more_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Launcher & MTZ Menu",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    modifier = Modifier
                        .background(SlateSurface)
                        .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("MTZ Theme Studio", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = EmeraldBright
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onOpenMtzStudio()
                        },
                        modifier = Modifier.testTag("drawer_menu_mtz_studio")
                    )
                    DropdownMenuItem(
                        text = { Text("Pick Custom Wallpaper", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = EmeraldBright
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onPickCustomWallpaper()
                        },
                        modifier = Modifier.testTag("drawer_menu_pick_wallpaper")
                    )
                    DropdownMenuItem(
                        text = { Text("Restore MTZ Wallpaper", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onResetToThemeWallpaper()
                        },
                        modifier = Modifier.testTag("drawer_menu_restore_wallpaper")
                    )
                    HorizontalDivider(color = SlateBorder)
                    DropdownMenuItem(
                        text = { Text("Icon Pipeline Inspector", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = EmeraldBright
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onOpenApiPipeline()
                        },
                        modifier = Modifier.testTag("drawer_menu_api_pipeline")
                    )
                    DropdownMenuItem(
                        text = { Text("Launcher Settings", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onOpenSettings()
                        },
                        modifier = Modifier.testTag("drawer_menu_settings")
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filteredApps.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "No apps found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            } else {
                // Stock MIUI 4-Column App Drawer Icon Grid with Long-Press Drag-to-Workspace
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(4),
                    userScrollEnabled = !isDraggingToWorkspace && longPressedDrawerApp == null,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("drawer_app_list")
                ) {
                    items(
                        items = filteredApps,
                        key = { "${it.packageName}_${it.renderKey}" }
                    ) { app ->
                        var isPickedUp by remember { mutableStateOf(false) }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .miuiIconTapLongPressAndDrag(
                                    key1 = app.packageName,
                                    dragThreshold = 12.dp,
                                    onTap = {
                                        if (longPressedDrawerApp != null) {
                                            longPressedDrawerApp = null
                                        } else {
                                            onLaunchApp(app)
                                        }
                                    },
                                    onLongPressReached = {
                                        isPickedUp = true
                                        longPressedDrawerApp = app
                                    },
                                    onReleaseWithoutDrag = {
                                        isPickedUp = false
                                    },
                                    onDragStart = { fingerRootPos ->
                                        isPickedUp = false
                                        longPressedDrawerApp = null
                                        isDraggingToWorkspace = true
                                        onStartDragToWorkspace(app, fingerRootPos)
                                    },
                                    onDragMove = { fingerRootPos, _ ->
                                        if (isDraggingToWorkspace) {
                                            onUpdateDragToWorkspace(fingerRootPos)
                                        }
                                    },
                                    onDragDrop = { fingerRootPos ->
                                        if (isDraggingToWorkspace) {
                                            isDraggingToWorkspace = false
                                            onDropDragToWorkspace(app, fingerRootPos)
                                            onCloseDrawer()
                                        }
                                    },
                                    onDragCancel = {
                                        isPickedUp = false
                                        if (isDraggingToWorkspace) {
                                            isDraggingToWorkspace = false
                                            onCancelDragToWorkspace()
                                        }
                                    }
                                )
                        ) {
                            AppIconTile(
                                app = app,
                                iconSize = 58.dp,
                                showLabel = true,
                                showApiBadge = false,
                                enableClickHandling = false,
                                isPickedUp = isPickedUp,
                                onClick = {},
                                onLongClick = {},
                                modifier = Modifier.testTag("drawer_item_${app.packageName}")
                            )
                        }
                    }
                }
            }

            // Floating MIUI App Action Pill overlay (does not shift drawer grid items during long-press)
            val activePillApp = longPressedDrawerApp
            if (activePillApp != null && !isDraggingToWorkspace) {
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .zIndex(48f)
                ) {
                    MiuiAppFloatingActionPill(
                        app = activePillApp,
                        showDeleteShortcut = activePillApp.isPinnedToWorkspace || activePillApp.isPinnedToDock,
                        onDeleteShortcut = {
                            longPressedDrawerApp = null
                            onDeleteShortcutFromWorkspace(activePillApp)
                        },
                        onUninstallApp = {
                            longPressedDrawerApp = null
                            onUninstallApp(activePillApp)
                        },
                        onShareApp = {
                            longPressedDrawerApp = null
                            onShareApp(activePillApp)
                        },
                        onAppInfo = {
                            longPressedDrawerApp = null
                            onOpenAppInfo(activePillApp)
                        },
                        onEditIcon = {
                            longPressedDrawerApp = null
                            onOpenAppOptions(activePillApp)
                        }
                    )
                }
            }
        }
    }
}
