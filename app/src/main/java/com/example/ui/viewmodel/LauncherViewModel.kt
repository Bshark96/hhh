package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.LauncherDatabase
import com.example.data.local.MtzThemeEntity
import com.example.data.repository.LauncherRepository
import com.example.domain.model.AppActivityOption
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
import com.example.domain.model.WorkspaceWidgetItem
import com.example.service.LauncherNotificationListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LauncherTab {
    WORKSPACE,
    APP_DRAWER,
    MTZ_STUDIO,
    API_PIPELINE,
    SETTINGS
}

data class CatalogIconEntry(
    val key: String,
    val filePath: String,
    val bitmap: Bitmap
)

data class LauncherUiState(
    val isLoading: Boolean = true,
    val renderVersion: Long = 0L,
    val currentTab: LauncherTab = LauncherTab.WORKSPACE,
    val engineConfig: LauncherEngineConfig = LauncherEngineConfig(),
    val installedThemes: List<MtzThemeEntity> = emptyList(),
    val activeThemeMetadata: MtzThemeMetadata? = null,
    val activeWidgetThemeMetadata: MtzThemeMetadata? = null,
    val effectiveDockConfig: DockXmlConfig = DockXmlConfig(),
    val activeWallpaperBitmap: Bitmap? = null,
    val allApps: List<InstalledAppItem> = emptyList(),
    val workspaceApps: List<InstalledAppItem> = emptyList(),
    val dockApps: List<InstalledAppItem> = emptyList(),
    val workspaceFolders: List<WorkspaceFolder> = emptyList(),
    val workspaceWidgets: List<WorkspaceWidgetItem> = emptyList(),
    val deviceTelemetry: DeviceTelemetrySnapshot = DeviceTelemetrySnapshot(),
    val systemWidgetProviders: List<SystemWidgetProviderItem> = emptyList(),
    val drawerSearchQuery: String = "",
    val drawerSourceFilter: IconResolutionSource? = null,
    val inspectedApp: InstalledAppItem? = null,
    val inspectedAppActivities: List<AppActivityOption> = emptyList(),
    val pipelineBreakdown: IconPipelineBreakdown? = null,
    val activeThemeIconCatalog: List<CatalogIconEntry> = emptyList(),
    val activeNotificationPackages: Set<String> = emptySet(),
    val activeNotificationCounts: Map<String, Int> = emptyMap(),
    val statusBannerMessage: String? = null
)

class LauncherViewModel(
    private val repository: LauncherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private var immediateRecomputeJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                repository.initializeBuiltInThemesAndWorkspace()
                recomputeStateSnapshot(overrideConfig = null)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusBannerMessage = "Init warning: ${err.message}"
                    )
                }
            }

            // Start Room reactive observer
            observeLauncherData()

            // Observe active notification packages and counts from LauncherNotificationListenerService
            launch {
                LauncherNotificationListenerService.activePackages.collectLatest { activePkgs ->
                    _uiState.update { it.copy(activeNotificationPackages = activePkgs) }
                }
            }
            launch {
                LauncherNotificationListenerService.activeNotificationCounts.collectLatest { counts ->
                    _uiState.update { it.copy(activeNotificationCounts = counts) }
                }
            }

            // Extract remaining built-in .mtz themes in background without delaying startup
            launch(Dispatchers.IO) {
                runCatching {
                    repository.initializeSecondaryBuiltInThemes()
                    val allThemes = repository.getAllThemesOnce()
                    _uiState.update { it.copy(installedThemes = allThemes) }
                }
                // Preload system widget providers in background after themes are ready
                runCatching {
                    val currentTheme = _uiState.value.activeThemeMetadata
                    val currentConfig = _uiState.value.engineConfig
                    if (currentTheme != null) {
                        val sysWidgets = repository.querySystemAppWidgetProviders(currentTheme, currentConfig)
                        _uiState.update { it.copy(systemWidgetProviders = sysWidgets) }
                    }
                }
            }
        }
    }

    private fun observeLauncherData() {
        viewModelScope.launch {
            val coreFlow = combine(
                repository.engineConfigFlow,
                repository.themesFlow,
                repository.shortcutsFlow,
                repository.iconOverridesFlow
            ) { config, themes, shortcuts, overrides ->
                CoreDataTuple(config, themes, shortcuts, overrides)
            }

            combine(
                coreFlow,
                repository.foldersFlow,
                repository.widgetsFlow
            ) { core, folderEntities, widgetEntities ->
                Triple(core, folderEntities, widgetEntities)
            }.collectLatest { (core, folderEntities, widgetEntities) ->
                try {
                    applyResolvedData(
                        config = core.config,
                        themes = core.themes,
                        shortcuts = core.shortcuts,
                        overrides = core.overrides,
                        folderEntities = folderEntities,
                        widgetEntities = widgetEntities
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusBannerMessage = "Render pipeline error: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Immediately recomputes and pushes the updated themed state without waiting for SQLite
     * InvalidationTracker latency.
     */
    private fun triggerImmediateRecompute(overrideConfig: LauncherEngineConfig? = null) {
        immediateRecomputeJob?.cancel()
        immediateRecomputeJob = viewModelScope.launch {
            runCatching {
                recomputeStateSnapshot(overrideConfig = overrideConfig)
            }
        }
    }

    private suspend fun recomputeStateSnapshot(overrideConfig: LauncherEngineConfig?) {
        val config = overrideConfig ?: repository.getEngineConfigOnce()
        val themes = repository.getAllThemesOnce()
        val shortcuts = repository.getShortcutsOnce()
        val overrides = repository.getIconOverridesOnce()
        val folderEntities = repository.getFoldersOnce()
        val widgetEntities = repository.getWidgetsOnce()

        applyResolvedData(
            config = config,
            themes = themes,
            shortcuts = shortcuts,
            overrides = overrides,
            folderEntities = folderEntities,
            widgetEntities = widgetEntities
        )
    }

    private suspend fun applyResolvedData(
        config: LauncherEngineConfig,
        themes: List<MtzThemeEntity>,
        shortcuts: List<com.example.data.local.WorkspaceShortcutEntity>,
        overrides: List<com.example.data.local.IconOverrideEntity>,
        folderEntities: List<com.example.data.local.WorkspaceFolderEntity>,
        widgetEntities: List<com.example.data.local.WorkspaceWidgetEntity>
    ) {
        val activeThemeMeta = repository.getThemeMetadata(config.activeThemeId)
        val effectiveWidgetThemeId = config.widgetThemeId.takeIf { it.isNotBlank() } ?: config.activeThemeId
        val activeWidgetThemeMeta = if (effectiveWidgetThemeId == config.activeThemeId) {
            activeThemeMeta
        } else {
            runCatching { repository.getThemeMetadata(effectiveWidgetThemeId) }.getOrNull() ?: activeThemeMeta
        }
        val effectiveDock = repository.resolveEffectiveDockConfig(activeThemeMeta, config)
        val wallpaperBmp = repository.getThemeWallpaperBitmap(activeThemeMeta)
        val catalog = repository.getThemeIconCatalog(activeThemeMeta)

        val apps = repository.loadThemedInstalledApps(
            theme = activeThemeMeta,
            config = config,
            shortcuts = shortcuts,
            overrides = overrides
        )

        val maxDockCount = config.gridColumns.coerceIn(4, 6)
        val shortcutOrderMap = shortcuts.associate { it.packageName to it.sortOrder }
        val dockList = apps.filter { it.isPinnedToDock }
            .sortedWith(compareBy({ shortcutOrderMap[it.packageName] ?: 0 }, { it.packageName }))
            .take(maxDockCount)
        val workspaceList = apps.filter { it.isPinnedToWorkspace }
            .sortedBy { shortcutOrderMap[it.packageName] ?: 0 }

        val domainFolders = repository.mapFoldersToDomain(folderEntities, apps)
        val domainWidgets = repository.mapWidgetsToDomain(widgetEntities)
        val (resolvedWorkspaceList, resolvedFolders, resolvedWidgets) = repository.resolveWorkspace2DCoordinates(
            workspaceApps = workspaceList,
            folders = domainFolders,
            widgets = domainWidgets,
            config = config
        )
        val telemetry = withContext(Dispatchers.IO) { repository.readDeviceTelemetry() }

        val currentInspectedPkg = _uiState.value.inspectedApp?.packageName
        val targetInspectApp = apps.firstOrNull { it.packageName == currentInspectedPkg }
            ?: apps.firstOrNull { it.resolutionSource == IconResolutionSource.MTZ_DYNAMIC_MASK }
            ?: apps.firstOrNull()

        val breakdown = if (targetInspectApp != null) {
            repository.buildInspectorBreakdown(targetInspectApp, activeThemeMeta, config)
        } else {
            null
        }
        val activities = if (targetInspectApp != null) {
            repository.queryAppActivities(targetInspectApp.packageName, targetInspectApp.defaultActivityName)
        } else {
            emptyList()
        }

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                renderVersion = state.renderVersion + 1L,
                engineConfig = config,
                installedThemes = themes,
                activeThemeMetadata = activeThemeMeta,
                activeWidgetThemeMetadata = activeWidgetThemeMeta,
                effectiveDockConfig = effectiveDock,
                activeWallpaperBitmap = wallpaperBmp,
                allApps = apps,
                workspaceApps = resolvedWorkspaceList,
                dockApps = dockList,
                workspaceFolders = resolvedFolders,
                workspaceWidgets = resolvedWidgets,
                deviceTelemetry = telemetry,
                inspectedApp = targetInspectApp,
                inspectedAppActivities = activities,
                pipelineBreakdown = breakdown,
                activeThemeIconCatalog = catalog
            )
        }
    }

    private fun mutateEngineConfigImmediate(
        statusMessage: String? = null,
        transform: (LauncherEngineConfig) -> LauncherEngineConfig
    ) {
        val optimistic = transform(_uiState.value.engineConfig)
        _uiState.update { state ->
            state.copy(
                engineConfig = optimistic,
                statusBannerMessage = statusMessage ?: state.statusBannerMessage
            )
        }
        immediateRecomputeJob?.cancel()
        immediateRecomputeJob = viewModelScope.launch {
            val saved = repository.updateEngineConfig { transform(optimistic) }
            recomputeStateSnapshot(overrideConfig = saved)
        }
    }

    fun refreshTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            val snap = repository.readDeviceTelemetry()
            _uiState.update { it.copy(deviceTelemetry = snap) }
        }
    }

    fun createHyperFolder(
        title: String,
        layoutMode: HyperFolderLayoutMode,
        packageNames: List<String>,
        pageIndex: Int = 0
    ) {
        viewModelScope.launch {
            val ok = repository.createHyperFolder(title, layoutMode, packageNames, pageIndex)
            recomputeStateSnapshot(overrideConfig = null)
            if (ok) {
                postStatusMessage("Created ${layoutMode.displayName}: '$title'")
            } else {
                postStatusMessage("No space on this screen")
            }
        }
    }

    fun updateFolderLayoutMode(folderId: String, mode: HyperFolderLayoutMode) {
        viewModelScope.launch {
            val ok = repository.updateFolderLayoutMode(folderId, mode)
            recomputeStateSnapshot(overrideConfig = null)
            if (ok) {
                postStatusMessage("Resized folder to ${mode.displayName} (${mode.code})")
            } else {
                postStatusMessage("No space on this screen")
            }
        }
    }

    fun resizeFolderSpan(folderId: String, spanX: Int, spanY: Int) {
        viewModelScope.launch {
            val ok = repository.updateFolderSpan(folderId, spanX, spanY)
            recomputeStateSnapshot(overrideConfig = null)
            if (!ok) {
                postStatusMessage("No space on this screen")
            }
        }
    }

    fun cycleFolderBackgroundColor(folderId: String) {
        viewModelScope.launch {
            repository.cycleFolderBackgroundColor(folderId)
            recomputeStateSnapshot(overrideConfig = null)
        }
    }

    fun moveFolderOnWorkspace(
        folderId: String,
        pageIndex: Int,
        sortOrder: Int,
        preferredCellX: Int = -1,
        preferredCellY: Int = -1
    ) {
        viewModelScope.launch {
            val ok = repository.moveFolderToPageAndOrder(
                folderId = folderId,
                pageIndex = pageIndex,
                sortOrder = sortOrder,
                preferredCellX = preferredCellX,
                preferredCellY = preferredCellY
            )
            recomputeStateSnapshot(overrideConfig = null)
            if (!ok) {
                postStatusMessage("No space on this screen")
            }
        }
    }

    fun moveWidgetOnWorkspace(
        widgetId: String,
        pageIndex: Int,
        preferredCellX: Int,
        preferredCellY: Int
    ) {
        viewModelScope.launch {
            val ok = repository.moveWidgetToPageAndCell(
                widgetId = widgetId,
                pageIndex = pageIndex,
                preferredCellX = preferredCellX,
                preferredCellY = preferredCellY
            )
            recomputeStateSnapshot(overrideConfig = null)
            if (!ok) {
                postStatusMessage("No space on this screen")
            }
        }
    }

    fun moveAppOnWorkspace(
        app: InstalledAppItem,
        targetPage: Int,
        targetSortOrder: Int,
        isDockItem: Boolean,
        preferredCellX: Int = -1,
        preferredCellY: Int = -1
    ) {
        viewModelScope.launch {
            val ok = repository.moveAppShortcut(
                app = app,
                targetPage = targetPage,
                targetSortOrder = targetSortOrder,
                isDockItem = isDockItem,
                preferredCellX = preferredCellX,
                preferredCellY = preferredCellY
            )
            recomputeStateSnapshot(overrideConfig = null)
            if (!ok) {
                postStatusMessage("No space on this screen")
            }
        }
    }

    fun removeAppFromWorkspace(packageName: String) {
        viewModelScope.launch {
            repository.removeAppFromWorkspace(packageName)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Removed shortcut from Home screen.")
        }
    }

    fun swapWorkspaceApps(pkgA: String, pkgB: String) {
        viewModelScope.launch {
            repository.swapWorkspaceApps(pkgA, pkgB)
            recomputeStateSnapshot(overrideConfig = null)
        }
    }

    fun mergeAppsIntoNewFolder(
        draggedApp: InstalledAppItem,
        targetApp: InstalledAppItem,
        pageIndex: Int,
        sourceFolderId: String? = null
    ) {
        viewModelScope.launch {
            repository.mergeAppsIntoNewFolder(draggedApp, targetApp, pageIndex, sourceFolderId)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Created folder with ${targetApp.label} & ${draggedApp.label}")
        }
    }

    fun moveAppIntoFolder(
        folderId: String,
        app: InstalledAppItem,
        sourceFolderId: String? = null
    ) {
        viewModelScope.launch {
            repository.moveAppIntoFolder(folderId, app, sourceFolderId)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Added ${app.label} to folder")
        }
    }

    fun extractAppFromFolderToWorkspace(
        folderId: String,
        app: InstalledAppItem,
        targetPage: Int,
        targetSortOrder: Int,
        isDockItem: Boolean,
        preferredCellX: Int = -1,
        preferredCellY: Int = -1
    ) {
        viewModelScope.launch {
            val ok = repository.extractAppFromFolderToWorkspace(
                folderId = folderId,
                app = app,
                targetPage = targetPage,
                targetSortOrder = targetSortOrder,
                isDockItem = isDockItem,
                preferredCellX = preferredCellX,
                preferredCellY = preferredCellY
            )
            recomputeStateSnapshot(overrideConfig = null)
            if (ok) {
                postStatusMessage("Moved ${app.label} out of folder to Workspace")
            } else {
                postStatusMessage("No space on this screen")
            }
        }
    }

    fun batchMoveAppsToPage(packageNames: List<String>, targetPage: Int) {
        viewModelScope.launch {
            repository.batchMoveAppsToPage(packageNames, targetPage)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Moved ${packageNames.size} app(s) to page ${targetPage + 1}")
        }
    }

    fun alignWorkspacePage(pageIndex: Int, alignBottom: Boolean) {
        viewModelScope.launch {
            repository.alignWorkspacePage(pageIndex, alignBottom)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage(if (alignBottom) "Aligned icons with bottom" else "Aligned icons with top")
        }
    }

    fun updatePageTransitionEffect(effect: MiuiPageTransitionEffect) {
        mutateEngineConfigImmediate(
            statusMessage = "Transition effect: ${effect.displayName}"
        ) {
            it.copy(pageTransitionEffect = effect)
        }
    }

    fun setDefaultHomePageIndex(pageIndex: Int) {
        mutateEngineConfigImmediate(
            statusMessage = "Set page ${pageIndex + 1} as Default Home Screen"
        ) {
            it.copy(defaultHomePageIndex = pageIndex.coerceAtLeast(0))
        }
    }

    fun renameFolder(folderId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameFolder(folderId, newTitle)
            recomputeStateSnapshot(overrideConfig = null)
        }
    }

    fun toggleAppInFolder(folderId: String, packageName: String) {
        viewModelScope.launch {
            repository.toggleAppInFolder(folderId, packageName)
            recomputeStateSnapshot(overrideConfig = null)
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Removed folder from Workspace.")
        }
    }

    fun addBuiltInWidget(type: HyperWidgetType, pageIndex: Int = 0) {
        viewModelScope.launch {
            val ok = repository.addBuiltInWidget(type, pageIndex)
            recomputeStateSnapshot(overrideConfig = null)
            if (ok) {
                postStatusMessage("Added ${type.title} widget to Workspace.")
            } else {
                postStatusMessage("No space on this screen")
            }
        }
    }

    fun addNativeSystemWidget(
        appWidgetId: Int,
        providerComponent: String,
        label: String,
        pageIndex: Int = 0,
        minWidthDp: Int = 220,
        minHeightDp: Int = 110
    ) {
        viewModelScope.launch {
            val ok = repository.addNativeSystemWidget(
                appWidgetId = appWidgetId,
                providerComponent = providerComponent,
                label = label,
                pageIndex = pageIndex,
                minWidthDp = minWidthDp,
                minHeightDp = minHeightDp
            )
            recomputeStateSnapshot(overrideConfig = null)
            if (ok) {
                postStatusMessage("Bound system widget '$label' to Workspace.")
            } else {
                postStatusMessage("No space on this screen")
            }
        }
    }

    fun removeWidget(widgetId: String) {
        viewModelScope.launch {
            repository.removeWidget(widgetId)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Removed widget from Workspace.")
        }
    }

    fun resizeWidgetSpan(widgetId: String, spanX: Int, spanY: Int) {
        viewModelScope.launch {
            val ok = repository.resizeWidgetSpan(widgetId, spanX, spanY)
            recomputeStateSnapshot(overrideConfig = null)
            if (!ok) {
                postStatusMessage("No space on this screen to resize widget")
            }
        }
    }

    fun cycleWidgetSize(widgetId: String) {
        viewModelScope.launch {
            val ok = repository.cycleWidgetSize(widgetId)
            recomputeStateSnapshot(overrideConfig = null)
            if (!ok) {
                postStatusMessage("No space on this screen to resize widget")
            }
        }
    }

    fun selectTab(tab: LauncherTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun updateDrawerSearch(query: String) {
        _uiState.update { it.copy(drawerSearchQuery = query) }
    }

    fun updateDrawerSourceFilter(source: IconResolutionSource?) {
        _uiState.update { state ->
            val next = if (state.drawerSourceFilter == source) null else source
            state.copy(drawerSourceFilter = next)
        }
    }

    fun inspectAppInPipeline(app: InstalledAppItem, switchToTab: Boolean = true) {
        viewModelScope.launch {
            val theme = _uiState.value.activeThemeMetadata ?: return@launch
            val config = _uiState.value.engineConfig
            val breakdown = repository.buildInspectorBreakdown(app, theme, config)
            val activities = repository.queryAppActivities(app.packageName, app.defaultActivityName)
            _uiState.update { state ->
                state.copy(
                    inspectedApp = app,
                    inspectedAppActivities = activities,
                    pipelineBreakdown = breakdown,
                    currentTab = if (switchToTab) LauncherTab.API_PIPELINE else state.currentTab
                )
            }
        }
    }

    fun activateMtzTheme(themeId: String) {
        val themeTitle = _uiState.value.installedThemes.firstOrNull { it.id == themeId }?.title ?: themeId
        viewModelScope.launch {
            repository.setCustomWallpaperUri(null)
            mutateEngineConfigImmediate(
                statusMessage = "Applied Xiaomi MTZ theme: $themeTitle"
            ) {
                it.copy(
                    activeThemeId = themeId,
                    useCustomScaleOverride = false
                )
            }
        }
    }

    fun setCustomWallpaper(uri: Uri?) {
        viewModelScope.launch {
            val ok = repository.setCustomWallpaperUri(uri)
            if (ok) {
                recomputeStateSnapshot(overrideConfig = null)
                postStatusMessage(if (uri != null) "Applied custom wallpaper." else "Restored MTZ theme wallpaper.")
            }
        }
    }

    fun importExternalMtzArchive(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusBannerMessage = "Parsing and extracting $fileName...") }
            val result = repository.importExternalMtzUri(uri, fileName)
            result.onSuccess { meta ->
                repository.setCustomWallpaperUri(null)
                val updated = repository.updateEngineConfig {
                    it.copy(
                        activeThemeId = meta.id,
                        useCustomScaleOverride = false
                    )
                }
                recomputeStateSnapshot(overrideConfig = updated)
                postStatusMessage("Imported and activated '${meta.title}' (${meta.directIconsMap.size} icons, scale ${meta.iconScale}x)")
            }.onFailure { err ->
                postStatusMessage("MTZ Import failed: ${err.message ?: "Invalid archive format"}")
            }
        }
    }

    fun exportMtzThemeToUri(themeId: String, targetUri: Uri) {
        viewModelScope.launch {
            val result = repository.exportBuiltInMtzToUri(themeId, targetUri)
            result.onSuccess {
                postStatusMessage("Exported .mtz package successfully to device storage.")
            }.onFailure { err ->
                postStatusMessage("Export failed: ${err.message}")
            }
        }
    }

    fun deleteCustomMtzTheme(themeId: String) {
        viewModelScope.launch {
            repository.deleteCustomTheme(themeId)
            recomputeStateSnapshot(overrideConfig = null)
            postStatusMessage("Removed custom .mtz theme package.")
        }
    }

    fun setAppIconOverride(packageName: String, assetKey: String?) {
        viewModelScope.launch {
            val themeId = _uiState.value.engineConfig.activeThemeId
            repository.setPerAppIconOverride(packageName, themeId, assetKey)
            recomputeStateSnapshot(overrideConfig = null)
            val msg = when {
                assetKey == null -> "Reset icon for $packageName to automatic Xiaomi API resolution."
                assetKey.startsWith("app:") -> "Remapped icon for $packageName to ${assetKey.removePrefix("app:")}."
                else -> "Mapped $packageName to '$assetKey.png' from active .mtz archive."
            }
            postStatusMessage(msg)
        }
    }

    fun remapAppIconToPackage(targetPackageName: String, sourceAppPackageName: String?) {
        val assetKey = sourceAppPackageName?.takeIf { it.isNotBlank() }?.let { "app:$it" }
        setAppIconOverride(targetPackageName, assetKey)
    }

    suspend fun queryAppActivities(app: InstalledAppItem): List<AppActivityOption> {
        return repository.queryAppActivities(app.packageName, app.defaultActivityName)
    }

    fun setAppCustomLaunchActivity(packageName: String, customActivityName: String?) {
        viewModelScope.launch {
            val themeId = _uiState.value.engineConfig.activeThemeId
            repository.setPerAppCustomActivity(packageName, themeId, customActivityName)
            recomputeStateSnapshot(overrideConfig = null)
            val msg = if (customActivityName.isNullOrBlank()) {
                "Restored default launch activity for $packageName."
            } else {
                "Set launch activity for $packageName to ${customActivityName.substringAfterLast('.')}."
            }
            postStatusMessage(msg)
        }
    }

    fun toggleMtzWidgetStyling(enabled: Boolean) {
        val label = if (enabled) "ENABLED" else "OFF (Standard Glass)"
        mutateEngineConfigImmediate(
            statusMessage = "MTZ Theme Visual Styling for MIUI Widgets: $label"
        ) {
            it.copy(enableMtzWidgetStyling = enabled)
        }
    }

    fun setWidgetTheme(themeId: String) {
        val cleanId = themeId.trim()
        val label = if (cleanId.isEmpty()) {
            "Follow Active Icon Theme"
        } else {
            _uiState.value.installedThemes.firstOrNull { it.id == cleanId }?.title ?: cleanId
        }
        mutateEngineConfigImmediate(
            statusMessage = "Global Widget Theme: $label"
        ) {
            it.copy(widgetThemeId = cleanId)
        }
    }

    fun toggleWorkspaceNotificationDots(enabled: Boolean) {
        val label = if (enabled) "ENABLED" else "HIDDEN"
        mutateEngineConfigImmediate(
            statusMessage = "Workspace & Dock Notification Dots: $label"
        ) {
            it.copy(enableWorkspaceNotificationDots = enabled)
        }
    }

    fun togglePinApp(app: InstalledAppItem, toDock: Boolean) {
        viewModelScope.launch {
            repository.toggleWorkspacePin(app, toDock)
            recomputeStateSnapshot(overrideConfig = null)
            val targetName = if (toDock) "Dock" else "Home Workspace"
            postStatusMessage("Updated $targetName shortcut for ${app.label}.")
        }
    }

    fun updateScaleOverride(enabled: Boolean, scaleValue: Float) {
        mutateEngineConfigImmediate {
            it.copy(
                useCustomScaleOverride = enabled,
                customScaleValue = scaleValue.coerceIn(0.50f, 1.00f)
            )
        }
    }

    fun toggleDynamicMasking(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableDynamicMasking = enabled)
        }
    }

    fun toggleThirdPartyColorFilter(enabled: Boolean) {
        val stateLabel = if (enabled) "ENABLED" else "DISABLED"
        mutateEngineConfigImmediate(
            statusMessage = "3rd-Party Red->White & Green Palette Shader: $stateLabel"
        ) {
            it.copy(enableThirdPartyColorFilter = enabled)
        }
    }

    fun toggleFancyMamlIcons(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableFancyMamlIcons = enabled)
        }
    }

    fun toggleIconBorderOverlay(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableIconBorderOverlay = enabled)
        }
    }

    fun toggleIconPatternOverlay(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableIconPatternOverlay = enabled)
        }
    }

    fun toggleShortcutBadge(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableShortcutBadge = enabled)
        }
    }

    fun toggleColorMatrixFilter(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableColorMatrixFilter = enabled)
        }
    }

    fun toggleLightingFilter(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableLightingFilter = enabled)
        }
    }

    fun toggleMultiBgHash(enabled: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(enableMultiBgHashSelection = enabled)
        }
    }

    fun updateGridColumns(columns: Int) {
        mutateEngineConfigImmediate {
            it.copy(gridColumns = columns.coerceIn(4, 6))
        }
    }

    fun updateGridRows(rows: Int) {
        mutateEngineConfigImmediate {
            it.copy(gridRows = rows.coerceIn(6, 8))
        }
    }

    fun updateIconSizeDp(sizeDp: Int) {
        mutateEngineConfigImmediate {
            it.copy(iconSizeDp = sizeDp.coerceIn(36, 72))
        }
    }

    fun toggleShowAppLabels(show: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(showAppLabels = show)
        }
    }

    fun toggleShowApiSourceBadges(show: Boolean) {
        mutateEngineConfigImmediate {
            it.copy(showApiSourceBadges = show)
        }
    }

    fun updateOverlayShadeAlpha(alpha: Float) {
        mutateEngineConfigImmediate {
            it.copy(overlayShadeAlpha = alpha.coerceIn(0.0f, 0.95f))
        }
    }

    fun updateBackgroundBlurMode(mode: MiuiBackgroundBlurMode) {
        mutateEngineConfigImmediate(
            statusMessage = "Background blur: ${mode.displayName}"
        ) {
            it.copy(backgroundBlurMode = mode)
        }
    }

    fun updateFolderAnimationMode(mode: MiuiFolderAnimationMode) {
        mutateEngineConfigImmediate(
            statusMessage = "Folder open animation: ${mode.displayName}"
        ) {
            it.copy(folderAnimationMode = mode)
        }
    }

    fun updateCustomDockXml(xml: String) {
        val trimmed = xml.trim()
        val msg = if (trimmed.isEmpty()) {
            "Restored Dock to theme default / stock MIUI transparent bar."
        } else {
            "Applied custom <DockConfig> XML to Home Dock."
        }
        mutateEngineConfigImmediate(statusMessage = msg) {
            it.copy(customDockXml = trimmed)
        }
    }

    fun launchClockApp() {
        val ok = repository.launchNativeClockApp()
        if (!ok) {
            postStatusMessage("Opened MIUI Clock bridge (no external Clock app found on emulator).")
        }
    }

    fun launchCalendarApp(epochMillis: Long = System.currentTimeMillis()) {
        val ok = repository.launchNativeCalendarApp(epochMillis)
        if (!ok) {
            postStatusMessage("Opened MIUI Calendar bridge (no external Calendar app found on emulator).")
        }
    }

    fun launchWeatherApp() {
        val city = _uiState.value.deviceTelemetry.weatherCity
        val ok = repository.launchNativeWeatherOrForecast(city)
        if (!ok) {
            postStatusMessage("Opened MIUI Weather bridge for $city.")
        }
    }

    fun launchInstalledApp(app: InstalledAppItem) {
        val launched = repository.launchApp(app.packageName, app.activityName)
        if (!launched) {
            inspectAppInPipeline(app, switchToTab = true)
            postStatusMessage("Inspecting Xiaomi MAML / MTZ pipeline for ${app.label} (${app.packageName})")
        }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun dismissStatusBanner() {
        clearStatusBanner()
    }

    private fun postStatusMessage(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
    }

    private data class CoreDataTuple(
        val config: LauncherEngineConfig,
        val themes: List<MtzThemeEntity>,
        val shortcuts: List<com.example.data.local.WorkspaceShortcutEntity>,
        val overrides: List<com.example.data.local.IconOverrideEntity>
    )

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = LauncherDatabase.getInstance(context)
                    val repo = LauncherRepository(context.applicationContext, db.launcherDao())
                    return LauncherViewModel(repo) as T
                }
            }
        }
    }
}
