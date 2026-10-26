package com.example

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.MtzThemeEntity
import com.example.domain.model.HyperFolderLayoutMode
import com.example.domain.model.InstalledAppItem
import com.example.domain.model.MiuiFolderAnimationMode
import com.example.domain.model.SystemWidgetProviderItem
import com.example.domain.model.WorkspaceFolder
import com.example.ui.components.AppActionBottomSheet
import com.example.ui.components.ExpandedHyperFolderSheet
import com.example.ui.components.LauncherAppWidgetHost
import com.example.ui.components.WidgetsAndFoldersPickerSheet
import com.example.ui.screens.ApiPipelineScreen
import com.example.ui.screens.AppDrawerScreen
import com.example.ui.screens.LauncherSettingsScreen
import com.example.ui.screens.MtzStudioScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDeepBackground
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.LauncherTab
import com.example.ui.viewmodel.LauncherViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var appWidgetHost: LauncherAppWidgetHost? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        appWidgetHost = try {
            LauncherAppWidgetHost(applicationContext, 1024).also { it.startListening() }
        } catch (_: Exception) {
            null
        }

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val viewModel: LauncherViewModel = viewModel(
                    factory = LauncherViewModel.provideFactory(context)
                )
                HyperMtzLauncherRoot(
                    viewModel = viewModel,
                    appWidgetHost = appWidgetHost
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            appWidgetHost?.startListening()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            appWidgetHost?.stopListening()
        } catch (_: Exception) {
        }
    }
}

private data class NavTabSpec(
    val tab: LauncherTab,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val NAV_TABS = listOf(
    NavTabSpec(LauncherTab.WORKSPACE, "Workspace", Icons.Default.Home, "nav_tab_workspace"),
    NavTabSpec(LauncherTab.APP_DRAWER, "Drawer", Icons.Default.Apps, "nav_tab_drawer"),
    NavTabSpec(LauncherTab.MTZ_STUDIO, "MTZ Studio", Icons.Default.Layers, "nav_tab_mtz_studio"),
    NavTabSpec(LauncherTab.API_PIPELINE, "API Pipeline", Icons.Default.Tune, "nav_tab_api_pipeline"),
    NavTabSpec(LauncherTab.SETTINGS, "System", Icons.Default.Settings, "nav_tab_settings")
)

@Composable
fun HyperMtzLauncherRoot(
    viewModel: LauncherViewModel,
    appWidgetHost: AppWidgetHost?
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedAppForSheet by remember { mutableStateOf<InstalledAppItem?>(null) }
    var expandedFolderId by remember { mutableStateOf<String?>(null) }
    var expandedFolderSourceBounds by remember { mutableStateOf<Rect?>(null) }
    var showWidgetsAndFoldersPicker by remember { mutableStateOf(false) }
    var activeWorkspacePage by remember { mutableIntStateOf(0) }
    var pendingExportTheme by remember { mutableStateOf<MtzThemeEntity?>(null) }
    var pendingBindWidget by remember { mutableStateOf<Pair<Int, SystemWidgetProviderItem>?>(null) }
    var pendingConfigureWidget by remember { mutableStateOf<Pair<Int, SystemWidgetProviderItem>?>(null) }

    LaunchedEffect(uiState.statusBannerMessage) {
        if (uiState.statusBannerMessage != null) {
            delay(3200L)
            viewModel.dismissStatusBanner()
        }
    }

    // Runtime permission launcher for MIUI Weather (Location) & Calendar widgets
    val widgetPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshTelemetry()
    }

    val requestWidgetPermissions: () -> Unit = {
        widgetPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        )
    }

    // Cross-screen drag state for dragging apps from the App Drawer or Open Folder onto the Workspace
    var externalDraggedApp by remember { mutableStateOf<InstalledAppItem?>(null) }
    var externalDragSourceFolderId by remember { mutableStateOf<String?>(null) }
    var externalDragOffsetInRoot by remember { mutableStateOf(Offset.Zero) }
    var workspaceExternalDropHandler by remember {
        mutableStateOf<((InstalledAppItem, String?, Offset) -> Unit)?>(null)
    }

    val handleUninstallApp: (InstalledAppItem) -> Unit = { app ->
        try {
            val intent = Intent(
                Intent.ACTION_DELETE,
                Uri.fromParts("package", app.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", app.packageName, null)
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
            }
        }
    }

    val handleShareApp: (InstalledAppItem) -> Unit = { app ->
        try {
            val shareText = "${app.label} (${app.packageName})\nhttps://play.google.com/store/apps/details?id=${app.packageName}"
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, app.label)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            val chooser = Intent.createChooser(sendIntent, "Share ${app.label}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
        }
    }

    val handleOpenAppInfo: (InstalledAppItem) -> Unit = { app ->
        try {
            val infoIntent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", app.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(infoIntent)
        } catch (_: Exception) {
            selectedAppForSheet = app
        }
    }

    // Return to Home Workspace when pressing hardware/gesture Back on any secondary tab
    BackHandler(enabled = uiState.currentTab != LauncherTab.WORKSPACE) {
        viewModel.selectTab(LauncherTab.WORKSPACE)
    }

    // Document Picker for importing .mtz / .mtx / .zip Xiaomi Theme Archives
    val importMtzLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = resolveFileName(context, uri) ?: "imported_theme.mtz"
            viewModel.importExternalMtzArchive(uri, fileName)
        }
    }

    // Document Creator for exporting built-in .mtz archives to user storage
    val exportMtzLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val theme = pendingExportTheme
        if (uri != null && theme != null) {
            viewModel.exportMtzThemeToUri(theme.id, uri)
        }
        pendingExportTheme = null
    }

    // Native Android AppWidget Configure Activity launcher (when providerInfo.configure != null)
    val configureAppWidgetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingConfigureWidget
        if (pending != null) {
            if (result.resultCode == Activity.RESULT_OK || result.resultCode == Activity.RESULT_CANCELED) {
                viewModel.addNativeSystemWidget(
                    appWidgetId = pending.first,
                    providerComponent = pending.second.providerComponent,
                    label = pending.second.label,
                    pageIndex = activeWorkspacePage,
                    minWidthDp = pending.second.minWidthDp,
                    minHeightDp = pending.second.minHeightDp
                )
            }
            pendingConfigureWidget = null
        }
    }

    val finalizeBoundSystemWidget: (Int, SystemWidgetProviderItem) -> Unit = { appWidgetId, provider ->
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val info = if (appWidgetId >= 0) appWidgetManager.getAppWidgetInfo(appWidgetId) else null
            val configureComponent = info?.configure
            if (configureComponent != null && appWidgetId >= 0) {
                pendingConfigureWidget = appWidgetId to provider
                val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = configureComponent
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                configureAppWidgetLauncher.launch(configIntent)
            } else {
                viewModel.addNativeSystemWidget(
                    appWidgetId = appWidgetId,
                    providerComponent = provider.providerComponent,
                    label = provider.label,
                    pageIndex = activeWorkspacePage,
                    minWidthDp = provider.minWidthDp,
                    minHeightDp = provider.minHeightDp
                )
            }
        } catch (_: Exception) {
            viewModel.addNativeSystemWidget(
                appWidgetId = appWidgetId,
                providerComponent = provider.providerComponent,
                label = provider.label,
                pageIndex = activeWorkspacePage,
                minWidthDp = provider.minWidthDp,
                minHeightDp = provider.minHeightDp
            )
        }
    }

    // Native Android AppWidget Bind permission launcher
    val bindAppWidgetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val pending = pendingBindWidget
        if (pending != null) {
            finalizeBoundSystemWidget(pending.first, pending.second)
            pendingBindWidget = null
        }
    }

    val handleBindSystemWidget: (SystemWidgetProviderItem) -> Unit = { provider ->
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val comp = ComponentName.unflattenFromString(provider.providerComponent)
            val allocatedId = appWidgetHost?.allocateAppWidgetId() ?: -1
            if (comp != null && allocatedId >= 0) {
                val bound = appWidgetManager.bindAppWidgetIdIfAllowed(allocatedId, comp)
                if (bound) {
                    finalizeBoundSystemWidget(allocatedId, provider)
                } else {
                    pendingBindWidget = allocatedId to provider
                    val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, allocatedId)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, comp)
                    }
                    bindAppWidgetLauncher.launch(bindIntent)
                }
            } else {
                viewModel.addNativeSystemWidget(
                    appWidgetId = -1,
                    providerComponent = provider.providerComponent,
                    label = provider.label,
                    pageIndex = activeWorkspacePage,
                    minWidthDp = provider.minWidthDp,
                    minHeightDp = provider.minHeightDp
                )
            }
        } catch (_: Exception) {
            viewModel.addNativeSystemWidget(
                appWidgetId = -1,
                providerComponent = provider.providerComponent,
                label = provider.label,
                pageIndex = activeWorkspacePage,
                minWidthDp = provider.minWidthDp,
                minHeightDp = provider.minHeightDp
            )
        }
    }

    // Zero-permission Photo Picker for Custom Wallpaper selection
    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomWallpaper(uri)
        }
    }

    // RoleManager launcher for setting Default Home App on Android 10+
    val homeRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    val triggerDefaultHomeSelection = {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    homeRoleLauncher.launch(intent)
                } else {
                    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } else {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDeepBackground)
    ) {
        // Full-bleed edge-to-edge wallpaper extending behind Status Bar, Workspace, and App Drawer
        val wpBitmap = uiState.activeWallpaperBitmap
        val showWallpaperBackdrop =
            uiState.currentTab == LauncherTab.WORKSPACE || uiState.currentTab == LauncherTab.APP_DRAWER
        val isDrawerOrFolderOverlayOpen =
            (uiState.currentTab == LauncherTab.APP_DRAWER || expandedFolderId != null) && externalDraggedApp == null
        val targetBlurDp by animateDpAsState(
            targetValue = if (isDrawerOrFolderOverlayOpen) {
                uiState.engineConfig.backgroundBlurMode.blurDp.dp
            } else {
                0.dp
            },
            animationSpec = tween(durationMillis = 220),
            label = "backgroundBlurDp"
        )
        val workspaceZoomBackScale by animateFloatAsState(
            targetValue = if (
                expandedFolderId != null &&
                externalDraggedApp == null &&
                uiState.engineConfig.folderAnimationMode == MiuiFolderAnimationMode.MIUI_ZOOM_EXPAND
            ) {
                0.93f
            } else {
                1f
            },
            animationSpec = tween(durationMillis = 240),
            label = "workspaceZoomBackScale"
        )

        if (showWallpaperBackdrop && wpBitmap != null) {
            Image(
                bitmap = wpBitmap.asImageBitmap(),
                contentDescription = "Active MTZ Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (targetBlurDp > 0.dp) Modifier.blur(targetBlurDp) else Modifier)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                SlateDeepBackground.copy(alpha = 0.22f),
                                SlateDeepBackground.copy(alpha = 0.10f),
                                SlateDeepBackground.copy(alpha = 0.34f)
                            )
                        )
                    )
            )
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = if (showWallpaperBackdrop) {
                Color.Transparent
            } else {
                SlateDeepBackground
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState.isLoading) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        CircularProgressIndicator(color = EmeraldBright)
                    }
                } else {
                    when (uiState.currentTab) {
                        LauncherTab.WORKSPACE, LauncherTab.APP_DRAWER -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Workspace layer sits inside safeDrawing insets and blurs/scales smoothly when Drawer or Folder opens
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .windowInsetsPadding(WindowInsets.safeDrawing)
                                        .graphicsLayer {
                                            scaleX = workspaceZoomBackScale
                                            scaleY = workspaceZoomBackScale
                                        }
                                        .then(if (targetBlurDp > 0.dp) Modifier.blur(targetBlurDp) else Modifier)
                                ) {
                                    WorkspaceScreen(
                                        activeTheme = uiState.activeThemeMetadata,
                                        activeWidgetTheme = uiState.activeWidgetThemeMetadata,
                                        wallpaperBitmap = uiState.activeWallpaperBitmap,
                                        engineConfig = uiState.engineConfig,
                                        effectiveDockConfig = uiState.effectiveDockConfig,
                                        allApps = uiState.allApps,
                                        workspaceApps = uiState.workspaceApps,
                                        dockApps = uiState.dockApps,
                                        workspaceFolders = uiState.workspaceFolders,
                                        workspaceWidgets = uiState.workspaceWidgets,
                                        deviceTelemetry = uiState.deviceTelemetry,
                                        appWidgetHost = appWidgetHost,
                                        onAppClick = { viewModel.launchInstalledApp(it) },
                                        onAppLongClick = { selectedAppForSheet = it },
                                        onDeleteAppShortcut = { app ->
                                            viewModel.removeAppFromWorkspace(app.packageName)
                                        },
                                        onUninstallApp = handleUninstallApp,
                                        onShareApp = handleShareApp,
                                        onOpenAppInfo = handleOpenAppInfo,
                                        onExpandFolder = { folder ->
                                            expandedFolderSourceBounds = null
                                            expandedFolderId = folder.id
                                        },
                                        onExpandFolderWithBounds = { folder, bounds ->
                                            expandedFolderSourceBounds = bounds
                                            expandedFolderId = folder.id
                                        },
                                        onCycleFolderMode = { folder ->
                                            val modes = listOf(
                                                HyperFolderLayoutMode.REGULAR_1X1,
                                                HyperFolderLayoutMode.CAPSULE_2X1,
                                                HyperFolderLayoutMode.TALL_1X2,
                                                HyperFolderLayoutMode.BIG_FOLDER_2X2
                                            )
                                            val nextIdx = (modes.indexOf(folder.layoutMode) + 1) % modes.size
                                            viewModel.updateFolderLayoutMode(folder.id, modes[nextIdx])
                                        },
                                        onResizeFolderSpan = viewModel::resizeFolderSpan,
                                        onCycleFolderBackgroundColor = viewModel::cycleFolderBackgroundColor,
                                        onRenameFolder = viewModel::renameFolder,
                                        onDeleteFolder = viewModel::deleteFolder,
                                        onMoveFolderOnWorkspace = viewModel::moveFolderOnWorkspace,
                                        onMoveWidgetOnWorkspace = viewModel::moveWidgetOnWorkspace,
                                        onResizeWidgetSpan = viewModel::resizeWidgetSpan,
                                        onCycleWidgetSize = viewModel::cycleWidgetSize,
                                        onMoveAppOnWorkspace = viewModel::moveAppOnWorkspace,
                                        onExtractAppFromFolder = { folderId, app, page, order, isDock, cellX, cellY ->
                                            viewModel.extractAppFromFolderToWorkspace(
                                                folderId = folderId,
                                                app = app,
                                                targetPage = page,
                                                targetSortOrder = order,
                                                isDockItem = isDock,
                                                preferredCellX = cellX,
                                                preferredCellY = cellY
                                            )
                                        },
                                        onSwapWorkspaceApps = viewModel::swapWorkspaceApps,
                                        onMergeAppsIntoNewFolder = { draggedApp, targetApp, pageIndex, sourceFolderId ->
                                            viewModel.mergeAppsIntoNewFolder(
                                                draggedApp = draggedApp,
                                                targetApp = targetApp,
                                                pageIndex = pageIndex,
                                                sourceFolderId = sourceFolderId
                                            )
                                        },
                                        onMoveAppIntoFolder = { folderId, app, sourceFolderId ->
                                            viewModel.moveAppIntoFolder(
                                                folderId = folderId,
                                                app = app,
                                                sourceFolderId = sourceFolderId
                                            )
                                        },
                                        onBatchMoveAppsToPage = viewModel::batchMoveAppsToPage,
                                        onAlignWorkspacePage = viewModel::alignWorkspacePage,
                                        onSetDefaultHomePage = viewModel::setDefaultHomePageIndex,
                                        onUpdatePageTransitionEffect = viewModel::updatePageTransitionEffect,
                                        onOpenWidgetsAndFoldersPicker = { showWidgetsAndFoldersPicker = true },
                                        onActivePageChanged = { activeWorkspacePage = it },
                                        onRefreshTelemetry = viewModel::refreshTelemetry,
                                        onRemoveWidget = viewModel::removeWidget,
                                        onOpenAppDrawer = { viewModel.selectTab(LauncherTab.APP_DRAWER) },
                                        onOpenMtzStudio = { viewModel.selectTab(LauncherTab.MTZ_STUDIO) },
                                        onOpenApiPipeline = { viewModel.selectTab(LauncherTab.API_PIPELINE) },
                                        onOpenSettings = { viewModel.selectTab(LauncherTab.SETTINGS) },
                                        onUpdateGridColumns = viewModel::updateGridColumns,
                                        onUpdateGridRows = viewModel::updateGridRows,
                                        onUpdateIconSizeDp = viewModel::updateIconSizeDp,
                                        onToggleShowLabels = viewModel::toggleShowAppLabels,
                                        onUpdateOverlayShadeAlpha = viewModel::updateOverlayShadeAlpha,
                                        onUpdateBackgroundBlurMode = viewModel::updateBackgroundBlurMode,
                                        onUpdateFolderAnimationMode = viewModel::updateFolderAnimationMode,
                                        onUpdateCustomDockXml = viewModel::updateCustomDockXml,
                                        onLaunchClockApp = viewModel::launchClockApp,
                                        onLaunchCalendarApp = viewModel::launchCalendarApp,
                                        onLaunchWeatherApp = viewModel::launchWeatherApp,
                                        onRequestWidgetPermissions = requestWidgetPermissions,
                                        onSetDefaultHomeLauncher = triggerDefaultHomeSelection,
                                        onPickCustomWallpaper = {
                                            wallpaperPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        onResetToThemeWallpaper = {
                                            viewModel.setCustomWallpaper(null)
                                        },
                                        activeNotificationPackages = uiState.activeNotificationPackages,
                                        activeNotificationCounts = uiState.activeNotificationCounts,
                                        onToggleMtzWidgetStyling = viewModel::toggleMtzWidgetStyling,
                                        onToggleWorkspaceNotificationDots = viewModel::toggleWorkspaceNotificationDots,
                                        onOpenNotificationAccessSettings = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                )
                                            }
                                        },
                                        externalDraggedApp = externalDraggedApp,
                                        externalDragSourceFolderId = externalDragSourceFolderId,
                                        externalDragOffsetInRoot = externalDragOffsetInRoot,
                                        onRegisterExternalDropHandler = { handler ->
                                            workspaceExternalDropHandler = handler
                                        }
                                    )
                                }

                                // AppDrawerScreen sits OUTSIDE safeDrawing padding so its shade covers the status bar & navigation bar edge-to-edge!
                                if (uiState.currentTab == LauncherTab.APP_DRAWER) {
                                    AppDrawerScreen(
                                        allApps = uiState.allApps,
                                        searchQuery = uiState.drawerSearchQuery,
                                        activeSourceFilter = uiState.drawerSourceFilter,
                                        onSearchQueryChange = viewModel::updateDrawerSearch,
                                        onSourceFilterSelect = viewModel::updateDrawerSourceFilter,
                                        onLaunchApp = viewModel::launchInstalledApp,
                                        onInspectApp = { app ->
                                            viewModel.inspectAppInPipeline(app, switchToTab = true)
                                        },
                                        onOpenAppOptions = { app -> selectedAppForSheet = app },
                                        onCloseDrawer = { viewModel.selectTab(LauncherTab.WORKSPACE) },
                                        onOpenMtzStudio = { viewModel.selectTab(LauncherTab.MTZ_STUDIO) },
                                        onOpenApiPipeline = { viewModel.selectTab(LauncherTab.API_PIPELINE) },
                                        onOpenSettings = { viewModel.selectTab(LauncherTab.SETTINGS) },
                                        onPickCustomWallpaper = {
                                            wallpaperPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        onResetToThemeWallpaper = {
                                            viewModel.setCustomWallpaper(null)
                                        },
                                        onDeleteShortcutFromWorkspace = { app ->
                                            viewModel.removeAppFromWorkspace(app.packageName)
                                        },
                                        onUninstallApp = handleUninstallApp,
                                        onShareApp = handleShareApp,
                                        onOpenAppInfo = handleOpenAppInfo,
                                        onStartDragToWorkspace = { app, offsetInRoot ->
                                            externalDraggedApp = app
                                            externalDragSourceFolderId = null
                                            externalDragOffsetInRoot = offsetInRoot
                                        },
                                        onUpdateDragToWorkspace = { offsetInRoot ->
                                            externalDragOffsetInRoot = offsetInRoot
                                        },
                                        onDropDragToWorkspace = { app, offsetInRoot ->
                                            workspaceExternalDropHandler?.invoke(app, null, offsetInRoot)
                                            externalDraggedApp = null
                                            externalDragSourceFolderId = null
                                            externalDragOffsetInRoot = Offset.Zero
                                            viewModel.selectTab(LauncherTab.WORKSPACE)
                                        },
                                        onCancelDragToWorkspace = {
                                            externalDraggedApp = null
                                            externalDragSourceFolderId = null
                                            externalDragOffsetInRoot = Offset.Zero
                                        },
                                        overlayShadeAlpha = uiState.engineConfig.overlayShadeAlpha
                                    )
                                }
                            }
                        }

                        LauncherTab.MTZ_STUDIO -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                            ) {
                                MtzStudioScreen(
                                    themes = uiState.installedThemes,
                                    activeThemeId = uiState.engineConfig.activeThemeId,
                                    widgetThemeId = uiState.engineConfig.widgetThemeId,
                                    activeThemeMetadata = uiState.activeThemeMetadata,
                                    activeWidgetThemeMetadata = uiState.activeWidgetThemeMetadata,
                                    activeThemeCatalog = uiState.activeThemeIconCatalog,
                                    onActivateTheme = viewModel::activateMtzTheme,
                                    onSelectWidgetTheme = viewModel::setWidgetTheme,
                                    onRequestImportMtz = {
                                        importMtzLauncher.launch(arrayOf("*/*"))
                                    },
                                    onRequestExportMtz = { theme ->
                                        pendingExportTheme = theme
                                        exportMtzLauncher.launch(theme.archiveFileName)
                                    },
                                    onDeleteCustomTheme = viewModel::deleteCustomMtzTheme,
                                    onCloseStudio = { viewModel.selectTab(LauncherTab.WORKSPACE) }
                                )
                            }
                        }

                        LauncherTab.API_PIPELINE -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                            ) {
                                ApiPipelineScreen(
                                    allApps = uiState.allApps,
                                    inspectedApp = uiState.inspectedApp,
                                    breakdown = uiState.pipelineBreakdown,
                                    engineConfig = uiState.engineConfig,
                                    iconCatalog = uiState.activeThemeIconCatalog,
                                    onSelectAppToInspect = { app ->
                                        viewModel.inspectAppInPipeline(app, switchToTab = false)
                                    },
                                    onUpdateScaleOverride = viewModel::updateScaleOverride,
                                    onToggleDynamicMasking = viewModel::toggleDynamicMasking,
                                    onToggleThirdPartyColorFilter = viewModel::toggleThirdPartyColorFilter,
                                    onToggleFancyMaml = viewModel::toggleFancyMamlIcons,
                                    onToggleIconBorder = viewModel::toggleIconBorderOverlay,
                                    onToggleIconPattern = viewModel::toggleIconPatternOverlay,
                                    onToggleShortcutBadge = viewModel::toggleShortcutBadge,
                                    onToggleColorMatrix = viewModel::toggleColorMatrixFilter,
                                    onToggleLighting = viewModel::toggleLightingFilter,
                                    onAssignOverrideAsset = viewModel::setAppIconOverride,
                                    inspectedAppActivities = uiState.inspectedAppActivities,
                                    onRemapAppIconToPackage = viewModel::remapAppIconToPackage,
                                    onSelectCustomLaunchActivity = viewModel::setAppCustomLaunchActivity,
                                    onClosePipeline = { viewModel.selectTab(LauncherTab.WORKSPACE) }
                                )
                            }
                        }

                        LauncherTab.SETTINGS -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                            ) {
                                LauncherSettingsScreen(
                                    engineConfig = uiState.engineConfig,
                                    effectiveDockConfig = uiState.effectiveDockConfig,
                                    deviceTelemetry = uiState.deviceTelemetry,
                                    onSetDefaultHomeLauncher = triggerDefaultHomeSelection,
                                    onUpdateGridColumns = viewModel::updateGridColumns,
                                    onUpdateGridRows = viewModel::updateGridRows,
                                    onUpdateIconSizeDp = viewModel::updateIconSizeDp,
                                    onToggleShowLabels = viewModel::toggleShowAppLabels,
                                    onToggleShowApiBadges = viewModel::toggleShowApiSourceBadges,
                                    onToggleMultiBgHash = viewModel::toggleMultiBgHash,
                                    onUpdateOverlayShadeAlpha = viewModel::updateOverlayShadeAlpha,
                                    onUpdateBackgroundBlurMode = viewModel::updateBackgroundBlurMode,
                                    onUpdateFolderAnimationMode = viewModel::updateFolderAnimationMode,
                                    onUpdateCustomDockXml = viewModel::updateCustomDockXml,
                                    onRequestWidgetPermissions = requestWidgetPermissions,
                                    isNotificationAccessGranted = com.example.service.LauncherNotificationListenerService.isNotificationAccessGranted(context),
                                    onToggleMtzWidgetStyling = viewModel::toggleMtzWidgetStyling,
                                    onToggleWorkspaceNotificationDots = viewModel::toggleWorkspaceNotificationDots,
                                    onOpenNotificationAccessSettings = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        }
                                    },
                                    onCloseSettings = { viewModel.selectTab(LauncherTab.WORKSPACE) }
                                )
                            }
                        }
                    }
                }

                // Floating Status Toast / Banner (e.g., "No space on this screen")
                val bannerMsg = uiState.statusBannerMessage
                if (bannerMsg != null) {
                    Surface(
                        color = SlateSurface.copy(alpha = 0.96f),
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 10.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .border(1.dp, EmeraldBright.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                            .zIndex(90f)
                            .testTag("workspace_status_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = bannerMsg,
                                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.dismissStatusBanner() }
                            )
                        }
                    }
                }
            }
        }
    }

    val sheetApp = selectedAppForSheet
    if (sheetApp != null) {
        val freshApp = uiState.allApps.firstOrNull { it.packageName == sheetApp.packageName } ?: sheetApp
        var sheetActivities by remember(freshApp.packageName, freshApp.defaultActivityName) {
            mutableStateOf<List<com.example.domain.model.AppActivityOption>>(emptyList())
        }
        LaunchedEffect(freshApp.packageName, freshApp.defaultActivityName) {
            sheetActivities = viewModel.queryAppActivities(freshApp)
        }
        AppActionBottomSheet(
            app = freshApp,
            iconCatalog = uiState.activeThemeIconCatalog,
            allApps = uiState.allApps,
            appActivities = sheetActivities,
            onDismiss = { selectedAppForSheet = null },
            onLaunch = { viewModel.launchInstalledApp(freshApp) },
            onInspectPipeline = {
                viewModel.inspectAppInPipeline(freshApp, switchToTab = true)
            },
            onToggleWorkspacePin = {
                viewModel.togglePinApp(freshApp, toDock = false)
            },
            onToggleDockPin = {
                viewModel.togglePinApp(freshApp, toDock = true)
            },
            onSelectOverrideAsset = { assetKey ->
                viewModel.setAppIconOverride(freshApp.packageName, assetKey)
            },
            onRemapIconToApp = { targetPkg ->
                viewModel.remapAppIconToPackage(freshApp.packageName, targetPkg)
            },
            onSelectCustomActivity = { activityClassName ->
                viewModel.setAppCustomLaunchActivity(freshApp.packageName, activityClassName)
            }
        )
    }

    val activeExpandedFolder: WorkspaceFolder? = expandedFolderId?.let { id ->
        uiState.workspaceFolders.firstOrNull { it.id == id }
    }
    if (activeExpandedFolder != null) {
        ExpandedHyperFolderSheet(
            folder = activeExpandedFolder,
            allApps = uiState.allApps,
            onDismiss = { expandedFolderId = null },
            onLaunchApp = viewModel::launchInstalledApp,
            onSelectLayoutMode = { mode ->
                viewModel.updateFolderLayoutMode(activeExpandedFolder.id, mode)
            },
            onRenameFolder = { newTitle ->
                viewModel.renameFolder(activeExpandedFolder.id, newTitle)
            },
            onToggleAppMembership = { pkg ->
                viewModel.toggleAppInFolder(activeExpandedFolder.id, pkg)
            },
            onDeleteFolder = {
                viewModel.deleteFolder(activeExpandedFolder.id)
            },
            onOpenAppEditSheet = { app ->
                selectedAppForSheet = app
            },
            onUninstallApp = handleUninstallApp,
            onShareApp = handleShareApp,
            onOpenAppInfo = handleOpenAppInfo,
            onStartDragOutOfFolder = { app, offsetInRoot ->
                externalDraggedApp = app
                externalDragSourceFolderId = activeExpandedFolder.id
                externalDragOffsetInRoot = offsetInRoot
            },
            onUpdateDragOutOfFolder = { offsetInRoot ->
                externalDragOffsetInRoot = offsetInRoot
            },
            onDropDragOutOfFolder = { app, offsetInRoot ->
                workspaceExternalDropHandler?.invoke(app, activeExpandedFolder.id, offsetInRoot)
                externalDraggedApp = null
                externalDragSourceFolderId = null
                externalDragOffsetInRoot = Offset.Zero
                expandedFolderId = null
            },
            onCancelDragOutOfFolder = {
                externalDraggedApp = null
                externalDragSourceFolderId = null
                externalDragOffsetInRoot = Offset.Zero
            },
            overlayShadeAlpha = uiState.engineConfig.overlayShadeAlpha,
            folderAnimationMode = uiState.engineConfig.folderAnimationMode,
            sourceFolderBounds = expandedFolderSourceBounds
        )
    }

    if (showWidgetsAndFoldersPicker) {
        WidgetsAndFoldersPickerSheet(
            allApps = uiState.allApps,
            systemWidgetProviders = uiState.systemWidgetProviders,
            onDismiss = { showWidgetsAndFoldersPicker = false },
            onCreateFolder = { title, mode, pkgs ->
                viewModel.createHyperFolder(title, mode, pkgs, pageIndex = activeWorkspacePage)
            },
            onAddBuiltInWidget = { type ->
                viewModel.addBuiltInWidget(type, pageIndex = activeWorkspacePage)
            },
            onBindSystemWidget = handleBindSystemWidget
        )
    }
}

private fun resolveFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path?.substringAfterLast('/')
    }
    return result
}
