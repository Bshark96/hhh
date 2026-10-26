package com.example.ui.components

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.domain.model.DeviceTelemetrySnapshot
import com.example.domain.model.HyperFolderLayoutMode
import com.example.domain.model.HyperWidgetType
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.InstalledAppItem
import com.example.domain.model.LauncherEngineConfig
import com.example.domain.model.MiuiFolderAnimationMode
import com.example.domain.model.MtzThemeMetadata
import com.example.domain.model.SystemWidgetProviderItem
import com.example.domain.model.WorkspaceFolder
import com.example.domain.model.WorkspaceWidgetItem
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintTertiary
import com.example.ui.theme.SageSecondary
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDeepBackground
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

/**
 * Custom [AppWidgetHost] that creates [LauncherAppWidgetHostView] instances so native Android
 * `RemoteViews` widgets pass normal taps/scrolls through to the bound widget while still triggering
 * MIUI long-press resize/drag edit mode when held down.
 */
class LauncherAppWidgetHost(
    context: Context,
    hostId: Int
) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return LauncherAppWidgetHostView(context)
    }
}

class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var isEditModeActive: Boolean = false
    var onTapInEditModeListener: (() -> Unit)? = null
    var onLongPressListener: (() -> Unit)? = null
    var onReleaseWithoutDragListener: (() -> Unit)? = null
    var onStartDragListener: ((Float, Float) -> Unit)? = null
    var onDragMoveListener: ((Float, Float) -> Unit)? = null
    var onDragDropListener: ((Float, Float) -> Unit)? = null
    var onDragCancelListener: (() -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var longPressTriggered = false
    private var isDraggingWidget = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        setPadding(0, 0, 0, 0)
    }

    private val longPressRunnable = Runnable {
        if (!isDraggingWidget) {
            longPressTriggered = true
            parent?.requestDisallowInterceptTouchEvent(true)
            onLongPressListener?.invoke()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                longPressTriggered = false
                isDraggingWidget = false
                removeCallbacks(longPressRunnable)
                if (isEditModeActive) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dist = hypot(ev.x - downX, ev.y - downY)
                if (longPressTriggered || isEditModeActive) {
                    return true
                }
                if (dist > touchSlop) {
                    removeCallbacks(longPressRunnable)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (longPressTriggered || isDraggingWidget || isEditModeActive) {
                    return true
                }
            }
        }
        return if (longPressTriggered || isEditModeActive) true else super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                if (!isEditModeActive && !longPressTriggered) {
                    removeCallbacks(longPressRunnable)
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dist = hypot(ev.x - downX, ev.y - downY)
                if (!longPressTriggered && !isEditModeActive && dist > touchSlop) {
                    removeCallbacks(longPressRunnable)
                    return false
                }
                if ((longPressTriggered || isEditModeActive) && !isDraggingWidget && dist > touchSlop) {
                    isDraggingWidget = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onStartDragListener?.invoke(ev.x, ev.y)
                }
                if (isDraggingWidget) {
                    onDragMoveListener?.invoke(ev.x, ev.y)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                when {
                    isDraggingWidget -> {
                        isDraggingWidget = false
                        longPressTriggered = false
                        onDragDropListener?.invoke(ev.x, ev.y)
                    }
                    longPressTriggered -> {
                        longPressTriggered = false
                        onReleaseWithoutDragListener?.invoke()
                    }
                    isEditModeActive -> {
                        onTapInEditModeListener?.invoke()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (isDraggingWidget) {
                    isDraggingWidget = false
                    onDragCancelListener?.invoke()
                } else if (longPressTriggered) {
                    longPressTriggered = false
                    onReleaseWithoutDragListener?.invoke()
                }
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}

private val MiuiResizeHandleBlue = Color(0xFF2196F3)
private val MiuiFolderDragHighlightBlue = Color(0xFF38BDF8)
private val sharedFolderMamlEngine by lazy { com.example.mtz.XiaomiIconRenderEngine() }
private val sharedClockWidgetMamlRenderer by lazy { com.example.mtz.MamlClockWidgetRenderer() }

private fun parseFolderPlateColor(hex: String): Color {
    return try {
        val parsed = android.graphics.Color.parseColor(hex)
        Color(parsed).copy(alpha = 0.62f)
    } catch (_: Exception) {
        Color(0xFF8C9AA8).copy(alpha = 0.62f)
    }
}

/**
 * Renders a Stock MIUI / HyperOS Folder on the Home Workspace supporting:
 * - `1x1` Regular, `2x1` Wide Capsule, `1x2` Tall Capsule, `2x2` Big Folder, `3x3` XXL
 * - Long-press Edit Mode with the MIUI floating dark pill menu (`Delete`, `Rename`, `Icon size`, `Background color`)
 *   and 4 interactive blue corner resize handles (`•`) matching stock MIUI.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HyperFolderCard(
    folder: WorkspaceFolder,
    onDirectLaunchApp: (InstalledAppItem) -> Unit,
    onAppLongClick: (InstalledAppItem) -> Unit,
    onExpandFolder: (WorkspaceFolder) -> Unit,
    onCycleLayoutMode: (WorkspaceFolder) -> Unit,
    isEditingFolder: Boolean = false,
    isWorkspaceEditMode: Boolean = false,
    onEnterEditMode: (WorkspaceFolder) -> Unit = {},
    onDismissEditMode: () -> Unit = {},
    onResizeFolderSpan: (Int, Int) -> Unit = { _, _ -> },
    onRenameFolderRequest: (WorkspaceFolder) -> Unit = {},
    onCycleBackgroundColor: (WorkspaceFolder) -> Unit = {},
    onDeleteFolderRequest: (WorkspaceFolder) -> Unit = {},
    onStartDragAppFromFolder: (InstalledAppItem, Offset) -> Unit = { _, _ -> },
    onDragAppFromFolder: (Offset) -> Unit = {},
    onEndDragAppFromFolder: (InstalledAppItem, Offset) -> Unit = { _, _ -> },
    onCancelDragAppFromFolder: () -> Unit = {},
    onStartDragFolder: (WorkspaceFolder, Offset) -> Unit = { _, _ -> },
    onDragFolder: (Offset) -> Unit = {},
    onEndDragFolder: (WorkspaceFolder, Offset) -> Unit = { _, _ -> },
    onCancelDragFolder: () -> Unit = {},
    onReleaseFingerHold: () -> Unit = {},
    isDragHovered: Boolean = false,
    animRestartKey: Any = 0,
    iconSize: Dp = 58.dp,
    showLabel: Boolean = true,
    activeTheme: MtzThemeMetadata? = null,
    enableMtzWidgetStyling: Boolean = true,
    modifier: Modifier = Modifier
) {
    val mtzFolderBgBitmap = remember(activeTheme?.iconFolderPath, enableMtzWidgetStyling) {
        if (!enableMtzWidgetStyling) {
            null
        } else {
            activeTheme?.iconFolderPath?.let { path ->
                runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val mtzFolderCoverBitmap = remember(activeTheme?.folderCoverPath, enableMtzWidgetStyling) {
        if (!enableMtzWidgetStyling) {
            null
        } else {
            activeTheme?.folderCoverPath?.let { path ->
                runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val basePlateColor = remember(folder.backgroundColorHex, mtzFolderBgBitmap) {
        val parsed = parseFolderPlateColor(folder.backgroundColorHex)
        if (mtzFolderBgBitmap != null) {
            parsed.copy(alpha = (parsed.alpha * 0.28f).coerceAtMost(0.18f))
        } else {
            parsed
        }
    }
    val plateColor = if (isDragHovered) {
        MiuiFolderDragHighlightBlue.copy(alpha = 0.34f)
    } else {
        basePlateColor
    }
    val hoverScale by animateFloatAsState(
        targetValue = if (isDragHovered) 1.045f else 1f,
        label = "folderDragHoverScale"
    )
    val density = LocalDensity.current
    val stepThresholdPx = with(density) { 46.dp.toPx() }
    val slotBoundsMap = remember(folder.id) { mutableStateMapOf<String, Rect>() }
    var clusterBounds by remember(folder.id) { mutableStateOf(Rect.Zero) }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .zIndex(if (isEditingFolder || isDragHovered) 15f else 1f)
            .testTag("hyper_folder_${folder.id}")
    ) {
        val availW = maxWidth
        val availH = maxHeight
        val spanX = folder.spanX.coerceAtLeast(1)
        val spanY = folder.spanY.coerceAtLeast(1)
        val singleCellW = availW / spanX
        val singleCellH = availH / spanY
        val labelReserve = if (showLabel) 22.dp else 4.dp

        // Align top, bottom, left, and right borders with the 1x1 app icon grid across all spanned cells:
        // - Top inset is 8.dp (identical to AppIconTile's top padding in a top-aligned cell).
        // - Width spans from the first column's left icon border to the last column's right icon border:
        //   (spanX - 1) * singleCellW + iconSize.
        // - Height spans from the first row's top icon border to the last row's bottom icon border:
        //   (spanY - 1) * singleCellH + iconSize.
        val cardSizeWidth: Dp = (singleCellW * (spanX - 1) + iconSize)
            .coerceIn(iconSize.coerceAtMost(availW - 4.dp), (availW - 4.dp).coerceAtLeast(32.dp))
        val cardSizeHeight: Dp = (singleCellH * (spanY - 1) + iconSize)
            .coerceIn(iconSize.coerceAtMost(availH - labelReserve - 8.dp), (availH - labelReserve - 8.dp).coerceAtLeast(32.dp))

        val slot2x2Size = minOf(
            (cardSizeWidth - 22.dp) / 2f,
            (cardSizeHeight - 22.dp) / 2f,
            iconSize * 0.88f
        ).coerceAtLeast(32.dp)
        val slot2x1Size = minOf(
            cardSizeHeight - 12.dp,
            (cardSizeWidth - 24.dp) / 3f,
            iconSize * 0.82f
        ).coerceAtLeast(28.dp)
        val slot1x2Size = minOf(
            cardSizeWidth - 12.dp,
            (cardSizeHeight - 24.dp) / 3f,
            iconSize * 0.82f
        ).coerceAtLeast(28.dp)
        val slot3x3Size = minOf(
            (cardSizeWidth - 28.dp) / 3f,
            (cardSizeHeight - 28.dp) / 3f,
            iconSize * 0.86f
        ).coerceAtLeast(30.dp)
        val slot1x1Size = (cardSizeWidth - 10.dp).coerceAtLeast(36.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = 2.dp)
        ) {
            // Folder Squircle Plate + Optional 4 Blue Corner Resize Handles
            Box(
                contentAlignment = Alignment.Center
            ) {
                val folderCornerShape = RoundedCornerShape(
                    if (folder.layoutMode == HyperFolderLayoutMode.REGULAR_1X1) 16.dp else 22.dp
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = cardSizeWidth, height = cardSizeHeight)
                        .graphicsLayer {
                            scaleX = hoverScale
                            scaleY = hoverScale
                        }
                        .clip(folderCornerShape)
                        .background(plateColor)
                        .border(
                            width = when {
                                isDragHovered -> 2.2.dp
                                isEditingFolder -> 1.8.dp
                                isWorkspaceEditMode -> 1.3.dp
                                else -> 0.8.dp
                            },
                            color = when {
                                isDragHovered -> MiuiFolderDragHighlightBlue
                                isEditingFolder -> Color.White.copy(alpha = 0.92f)
                                isWorkspaceEditMode -> Color.White.copy(alpha = 0.50f)
                                else -> Color.White.copy(alpha = 0.22f)
                            },
                            shape = folderCornerShape
                        )
                        .miuiIconTapLongPressAndDrag(
                            key1 = folder.id,
                            key2 = folder.pageIndex,
                            dragThreshold = 12.dp,
                            enableImmediateDrag = isEditingFolder || isWorkspaceEditMode,
                            onTap = {
                                when {
                                    isEditingFolder -> onDismissEditMode()
                                    isWorkspaceEditMode -> onEnterEditMode(folder)
                                    else -> onExpandFolder(folder)
                                }
                            },
                            onTapWithOffset = { tapRootOffset ->
                                when {
                                    isEditingFolder -> onDismissEditMode()
                                    isWorkspaceEditMode -> onEnterEditMode(folder)
                                    folder.layoutMode == HyperFolderLayoutMode.REGULAR_1X1 -> onExpandFolder(folder)
                                    clusterBounds != Rect.Zero && clusterBounds.contains(tapRootOffset) -> onExpandFolder(folder)
                                    else -> {
                                        val hitPkg = slotBoundsMap.entries
                                            .firstOrNull { (_, rect) -> rect.contains(tapRootOffset) }
                                            ?.key
                                        val hitApp = hitPkg?.let { pkg -> folder.apps.firstOrNull { it.packageName == pkg } }
                                        if (hitApp != null) {
                                            onDirectLaunchApp(hitApp)
                                        } else {
                                            onExpandFolder(folder)
                                        }
                                    }
                                }
                            },
                            onLongPressReached = {
                                onEnterEditMode(folder)
                            },
                            onReleaseWithoutDrag = {
                                onReleaseFingerHold()
                            },
                            onDragStart = { fingerRootOffset ->
                                onStartDragFolder(folder, fingerRootOffset)
                            },
                            onDragMove = { fingerRootOffset, _ ->
                                onDragFolder(fingerRootOffset)
                            },
                            onDragDrop = { fingerRootOffset ->
                                onEndDragFolder(folder, fingerRootOffset)
                            },
                            onDragCancel = {
                                onCancelDragFolder()
                            }
                        )
                ) {
                    if (mtzFolderBgBitmap != null) {
                        Image(
                            bitmap = mtzFolderBgBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        when (folder.layoutMode) {
                            HyperFolderLayoutMode.BIG_FOLDER_2X2 -> {
                                val slot0 = folder.apps.getOrNull(0)
                                val slot1 = folder.apps.getOrNull(1)
                                val slot2 = folder.apps.getOrNull(2)
                                val clusterApps = folder.apps.drop(3)

                                Column(
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        DirectFolderIconOnlySlot(
                                            app = slot0,
                                            iconSize = slot2x2Size,
                                            animRestartKey = animRestartKey,
                                            onBoundsChanged = { rect ->
                                                if (slot0 != null) slotBoundsMap[slot0.packageName] = rect
                                            }
                                        )
                                        DirectFolderIconOnlySlot(
                                            app = slot1,
                                            iconSize = slot2x2Size,
                                            animRestartKey = animRestartKey,
                                            onBoundsChanged = { rect ->
                                                if (slot1 != null) slotBoundsMap[slot1.packageName] = rect
                                            }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        DirectFolderIconOnlySlot(
                                            app = slot2,
                                            iconSize = slot2x2Size,
                                            animRestartKey = animRestartKey,
                                            onBoundsChanged = { rect ->
                                                if (slot2 != null) slotBoundsMap[slot2.packageName] = rect
                                            }
                                        )
                                        if (clusterApps.size == 1 && folder.apps.size == 4) {
                                            val slot3 = clusterApps.first()
                                            DirectFolderIconOnlySlot(
                                                app = slot3,
                                                iconSize = slot2x2Size,
                                                animRestartKey = animRestartKey,
                                                onBoundsChanged = { rect ->
                                                    slotBoundsMap[slot3.packageName] = rect
                                                }
                                            )
                                        } else {
                                            MiniClusterExpansionSlot(
                                                overflowApps = clusterApps,
                                                slotSize = slot2x2Size,
                                                onBoundsChanged = { rect -> clusterBounds = rect }
                                            )
                                        }
                                    }
                                }
                            }

                            HyperFolderLayoutMode.CAPSULE_2X1 -> {
                                val slot0 = folder.apps.getOrNull(0)
                                val slot1 = folder.apps.getOrNull(1)
                                val clusterApps = folder.apps.drop(2)

                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DirectFolderIconOnlySlot(
                                        app = slot0,
                                        iconSize = slot2x1Size,
                                        animRestartKey = animRestartKey,
                                        onBoundsChanged = { rect ->
                                            if (slot0 != null) slotBoundsMap[slot0.packageName] = rect
                                        }
                                    )
                                    DirectFolderIconOnlySlot(
                                        app = slot1,
                                        iconSize = slot2x1Size,
                                        animRestartKey = animRestartKey,
                                        onBoundsChanged = { rect ->
                                            if (slot1 != null) slotBoundsMap[slot1.packageName] = rect
                                        }
                                    )
                                    MiniClusterExpansionSlot(
                                        overflowApps = clusterApps,
                                        slotSize = slot2x1Size,
                                        onBoundsChanged = { rect -> clusterBounds = rect }
                                    )
                                }
                            }

                            HyperFolderLayoutMode.TALL_1X2 -> {
                                val slot0 = folder.apps.getOrNull(0)
                                val slot1 = folder.apps.getOrNull(1)
                                val clusterApps = folder.apps.drop(2)

                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    DirectFolderIconOnlySlot(
                                        app = slot0,
                                        iconSize = slot1x2Size,
                                        animRestartKey = animRestartKey,
                                        onBoundsChanged = { rect ->
                                            if (slot0 != null) slotBoundsMap[slot0.packageName] = rect
                                        }
                                    )
                                    DirectFolderIconOnlySlot(
                                        app = slot1,
                                        iconSize = slot1x2Size,
                                        animRestartKey = animRestartKey,
                                        onBoundsChanged = { rect ->
                                            if (slot1 != null) slotBoundsMap[slot1.packageName] = rect
                                        }
                                    )
                                    MiniClusterExpansionSlot(
                                        overflowApps = clusterApps,
                                        slotSize = slot1x2Size,
                                        onBoundsChanged = { rect -> clusterBounds = rect }
                                    )
                                }
                            }

                            HyperFolderLayoutMode.XXL_FOLDER_3X3 -> {
                                Column(
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    for (row in 0 until 3) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            for (col in 0 until 3) {
                                                val index = row * 3 + col
                                                if (index < 8) {
                                                    val app = folder.apps.getOrNull(index)
                                                    DirectFolderIconOnlySlot(
                                                        app = app,
                                                        iconSize = slot3x3Size,
                                                        animRestartKey = animRestartKey,
                                                        onBoundsChanged = { rect ->
                                                            if (app != null) slotBoundsMap[app.packageName] = rect
                                                        }
                                                    )
                                                } else {
                                                    MiniClusterExpansionSlot(
                                                        overflowApps = folder.apps.drop(8),
                                                        slotSize = slot3x3Size,
                                                        onBoundsChanged = { rect -> clusterBounds = rect }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HyperFolderLayoutMode.REGULAR_1X1 -> {
                                MiniClusterExpansionSlot(
                                    overflowApps = folder.apps,
                                    slotSize = slot1x1Size,
                                    onBoundsChanged = { rect -> clusterBounds = rect }
                                )
                            }
                        }
                    }
                    if (mtzFolderCoverBitmap != null) {
                        Image(
                            bitmap = mtzFolderCoverBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

            // 4 Blue Corner Resize Handles (`•`) matching Screenshot_20261024_193320_4ae33ac8.jpg
            if (isEditingFolder) {
                val handleAlignments = listOf(
                    Triple(Alignment.TopStart, -1, -1),
                    Triple(Alignment.TopEnd, 1, -1),
                    Triple(Alignment.BottomStart, -1, 1),
                    Triple(Alignment.BottomEnd, 1, 1)
                )
                handleAlignments.forEachIndexed { idx, (alignment, dirX, dirY) ->
                    var dragAccumX by remember(folder.id, folder.layoutMode, idx) { mutableFloatStateOf(0f) }
                    var dragAccumY by remember(folder.id, folder.layoutMode, idx) { mutableFloatStateOf(0f) }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(alignment)
                            .offset(
                                x = if (dirX < 0) (-6).dp else 6.dp,
                                y = if (dirY < 0) (-6).dp else 6.dp
                            )
                            .size(26.dp)
                            .pointerInput(folder.id, folder.layoutMode) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragAccumX += dragAmount.x * dirX
                                        dragAccumY += dragAmount.y * dirY

                                        var targetSpanX = folder.layoutMode.spanX
                                        var targetSpanY = folder.layoutMode.spanY

                                        if (dragAccumX > stepThresholdPx) {
                                            targetSpanX = (targetSpanX + 1).coerceIn(1, 2)
                                            dragAccumX = 0f
                                        } else if (dragAccumX < -stepThresholdPx) {
                                            targetSpanX = (targetSpanX - 1).coerceIn(1, 2)
                                            dragAccumX = 0f
                                        }

                                        if (dragAccumY > stepThresholdPx) {
                                            targetSpanY = (targetSpanY + 1).coerceIn(1, 2)
                                            dragAccumY = 0f
                                        } else if (dragAccumY < -stepThresholdPx) {
                                            targetSpanY = (targetSpanY - 1).coerceIn(1, 2)
                                            dragAccumY = 0f
                                        }

                                        if (targetSpanX != folder.layoutMode.spanX ||
                                            targetSpanY != folder.layoutMode.spanY
                                        ) {
                                            onResizeFolderSpan(targetSpanX, targetSpanY)
                                        }
                                    }
                                )
                            }
                            .testTag("folder_resize_handle_${folder.id}_$idx")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MiuiResizeHandleBlue)
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }
                }
            }
        }

            if (showLabel) {
                Spacer(modifier = Modifier.height(6.dp))

                // Stock MIUI Folder Title Centered Underneath (same spacing & baseline as AppIconTile)
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Floating dark pill popup menu displayed above a folder when long-pressed:
 * `Delete` | `Rename` | `Icon size` | `Background color`
 */
@Composable
fun MiuiFolderFloatingActionPill(
    folder: WorkspaceFolder,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCycleSize: () -> Unit,
    onCycleBgColor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF2B2B2E).copy(alpha = 0.94f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 8.dp,
        modifier = modifier
            .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
            .testTag("folder_floating_action_pill_${folder.id}")
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            MiuiPopupActionButton(
                icon = Icons.Default.Delete,
                label = "Delete",
                onClick = onDelete,
                testTag = "folder_popup_delete_${folder.id}"
            )
            MiuiPopupActionButton(
                icon = Icons.Default.Edit,
                label = "Rename",
                onClick = onRename,
                testTag = "folder_popup_rename_${folder.id}"
            )
            MiuiPopupActionButton(
                icon = Icons.Default.OpenInFull,
                label = "Icon size (${folder.layoutMode.code})",
                onClick = onCycleSize,
                testTag = "folder_mode_badge_${folder.id}"
            )
            MiuiPopupActionButton(
                icon = Icons.Default.Palette,
                label = "Background color",
                onClick = onCycleBgColor,
                testTag = "folder_popup_bgcolor_${folder.id}"
            )
        }
    }
}

/**
 * Floating dark pill popup menu displayed above a widget when long-pressed:
 * `Remove` | `Resize`
 */
@Composable
fun MiuiWidgetFloatingActionPill(
    widget: WorkspaceWidgetItem,
    onRemove: () -> Unit,
    onResize: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF2B2B2E).copy(alpha = 0.94f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 8.dp,
        modifier = modifier
            .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
            .testTag("widget_floating_action_pill_${widget.id}")
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            MiuiPopupActionButton(
                icon = Icons.Default.Delete,
                label = "Remove",
                onClick = onRemove,
                testTag = "widget_popup_remove_${widget.id}"
            )
            MiuiPopupActionButton(
                icon = Icons.Default.OpenInFull,
                label = "Resize (${widget.spanX}x${widget.spanY})",
                onClick = onResize,
                testTag = "widget_popup_resize_${widget.id}"
            )
        }
    }
}

@Composable
private fun MiuiPopupActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF1F2429),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DirectFolderIconOnlySlot(
    app: InstalledAppItem?,
    iconSize: Dp,
    animRestartKey: Any = 0,
    onBoundsChanged: (Rect) -> Unit = {}
) {
    if (app == null) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
    } else {
        val mamlSpec = app.mamlFancySpec
        var liveBitmap by remember(app.renderKey, app.themedIconBitmap) {
            mutableStateOf(app.themedIconBitmap)
        }
        if (mamlSpec != null && mamlSpec.variables.isNotEmpty()) {
            LaunchedEffect(app.renderKey, mamlSpec.targetPackage, animRestartKey) {
                val startMs = withFrameMillis { it }
                val durationMs = 1050L
                while (true) {
                    val nowMs = withFrameMillis { it }
                    val elapsed = (nowMs - startMs).coerceAtLeast(0L)
                    val cal = java.util.Calendar.getInstance()
                    val frameBmp = sharedFolderMamlEngine.renderMamlFancyIcon(
                        spec = mamlSpec,
                        calendar = cal,
                        animTimeMs = elapsed.coerceAtMost(1200L)
                    )
                    if (frameBmp != null) {
                        liveBitmap = frameBmp
                    }
                    if (elapsed >= durationMs) break
                    delay(16L)
                }
            }
        }
        val iconBitmap = remember(liveBitmap) {
            liveBitmap.asImageBitmap()
        }
        Image(
            bitmap = iconBitmap,
            contentDescription = app.label,
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(12.dp))
                .onGloballyPositioned { coords ->
                    onBoundsChanged(coords.boundsInRoot())
                }
                .testTag("folder_direct_app_${app.packageName}")
        )
    }
}

@Composable
private fun MiniClusterExpansionSlot(
    overflowApps: List<InstalledAppItem>,
    slotSize: Dp,
    onBoundsChanged: (Rect) -> Unit = {}
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(slotSize)
            .clip(RoundedCornerShape(12.dp))
            .padding(2.dp)
            .onGloballyPositioned { coords ->
                onBoundsChanged(coords.boundsInRoot())
            }
    ) {
        if (overflowApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            )
        } else {
            val miniSize = (slotSize - 8.dp) / 2
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    MiniIconThumb(overflowApps.getOrNull(0), miniSize)
                    MiniIconThumb(overflowApps.getOrNull(1), miniSize)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    MiniIconThumb(overflowApps.getOrNull(2), miniSize)
                    MiniIconThumb(overflowApps.getOrNull(3), miniSize)
                }
            }
        }
    }
}

@Composable
private fun MiniIconThumb(app: InstalledAppItem?, size: Dp) {
    if (app != null) {
        val iconBitmap = remember(app.renderKey, app.themedIconBitmap) {
            app.themedIconBitmap.asImageBitmap()
        }
        Image(
            bitmap = iconBitmap,
            contentDescription = app.label,
            modifier = Modifier.size(size)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.12f))
        )
    }
}

/**
 * Renders an active HyperOS Widget or Native Android System AppWidget on the Home Workspace.
 */
@Composable
fun HyperWorkspaceWidgetCard(
    widget: WorkspaceWidgetItem,
    timeString: String,
    dateString: String,
    activeTheme: MtzThemeMetadata?,
    engineConfig: LauncherEngineConfig,
    telemetry: DeviceTelemetrySnapshot,
    allApps: List<InstalledAppItem>,
    appWidgetHost: AppWidgetHost?,
    onOpenMtzStudio: () -> Unit,
    onOpenApiPipeline: () -> Unit,
    onOpenWidgetsPicker: () -> Unit,
    onRefreshTelemetry: () -> Unit,
    onRemoveWidget: (String) -> Unit,
    onLaunchApp: (InstalledAppItem) -> Unit,
    onLaunchClockApp: () -> Unit = {},
    onLaunchCalendarApp: (Long) -> Unit = {},
    onLaunchWeatherApp: () -> Unit = {},
    onRequestWidgetPermissions: () -> Unit = {},
    isEditingWidget: Boolean = false,
    isWorkspaceEditMode: Boolean = false,
    onEnterEditMode: (WorkspaceWidgetItem) -> Unit = {},
    onDismissEditMode: () -> Unit = {},
    onStartDragWidget: (WorkspaceWidgetItem, Offset) -> Unit = { _, _ -> },
    onDragWidget: (Offset) -> Unit = {},
    onEndDragWidget: (WorkspaceWidgetItem, Offset) -> Unit = { _, _ -> },
    onCancelDragWidget: () -> Unit = {},
    onReleaseFingerHold: () -> Unit = {},
    onResizeWidgetSpan: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val stepThresholdPx = with(density) { 46.dp.toPx() }
    val maxCols = engineConfig.gridColumns.coerceIn(4, 6)
    val maxRows = 4.coerceAtMost(engineConfig.gridRows.coerceIn(6, 8))
    val minSpanX = if (widget.widgetType == HyperWidgetType.NATIVE_APPWIDGET) 1 else 2

    val useMtzWidgetStyle = engineConfig.enableMtzWidgetStyling && activeTheme != null

    val themeAccentColor = remember(activeTheme?.titleColorHex, activeTheme?.filterWhiteColorHex, useMtzWidgetStyle) {
        if (!useMtzWidgetStyle) {
            Color(0xFF38BDF8)
        } else {
            try {
                val hex = activeTheme?.titleColorHex ?: "#38BDF8"
                val parsed = Color(android.graphics.Color.parseColor(hex))
                if (parsed == Color.White) {
                    val whiteHex = activeTheme?.filterWhiteColorHex ?: "#38BDF8"
                    val alt = Color(android.graphics.Color.parseColor(whiteHex))
                    if (alt == Color.White) Color(0xFF38BDF8) else alt
                } else {
                    parsed
                }
            } catch (_: Exception) {
                Color(0xFF38BDF8)
            }
        }
    }

    val themePrimarySurfaceColor = remember(activeTheme?.filterBlackColorHex, useMtzWidgetStyle) {
        if (!useMtzWidgetStyle) {
            Color(0xFF10243E)
        } else {
            runCatching {
                Color(android.graphics.Color.parseColor(activeTheme?.filterBlackColorHex ?: "#10243E"))
            }.getOrDefault(Color(0xFF10243E))
        }
    }

    val themeSecondarySurfaceColor = remember(activeTheme?.filterWhiteColorHex, useMtzWidgetStyle) {
        if (!useMtzWidgetStyle) {
            Color(0xFF1E3B58)
        } else {
            runCatching {
                Color(android.graphics.Color.parseColor(activeTheme?.filterWhiteColorHex ?: "#1E3B58"))
            }.getOrDefault(Color(0xFF1E3B58))
        }
    }

    val mtzCustomFontFamily = remember(activeTheme?.customFontPath, useMtzWidgetStyle) {
        if (!useMtzWidgetStyle) {
            null
        } else {
            activeTheme?.customFontPath?.let { path ->
                runCatching {
                    val tf = android.graphics.Typeface.createFromFile(path)
                    androidx.compose.ui.text.font.FontFamily(tf)
                }.getOrNull()
            }
        }
    }

    val mtzWidgetBgBitmap = remember(activeTheme?.widgetBackgroundPath, useMtzWidgetStyle) {
        if (!useMtzWidgetStyle) {
            null
        } else {
            activeTheme?.widgetBackgroundPath?.let { path ->
                runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    val mtzGadgetCircleBitmap = remember(activeTheme?.widgetGadgetCirclePath, useMtzWidgetStyle) {
        if (!useMtzWidgetStyle) {
            null
        } else {
            activeTheme?.widgetGadgetCirclePath?.let { path ->
                runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    val mtzGadgetIconBitmap = remember(activeTheme?.widgetGadgetIconPath, useMtzWidgetStyle) {
        if (!useMtzWidgetStyle) {
            null
        } else {
            activeTheme?.widgetGadgetIconPath?.let { path ->
                runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    val mtzWeatherIconBitmap = remember(
        activeTheme?.widgetWeatherIconPaths,
        telemetry.weatherCondition,
        useMtzWidgetStyle
    ) {
        if (!useMtzWidgetStyle) {
            null
        } else {
            val map = activeTheme?.widgetWeatherIconPaths.orEmpty()
            val condLower = telemetry.weatherCondition.lowercase()
            val matchedPath = when {
                condLower.contains("rain") || condLower.contains("storm") -> map["rain"]
                condLower.contains("cloud") || condLower.contains("overcast") -> map["cloudy"]
                condLower.contains("snow") -> map["snow"]
                condLower.contains("night") -> map["night"]
                else -> map["sunny"]
            } ?: map["default"] ?: map.values.firstOrNull()
            matchedPath?.let { path ->
                runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    val mtzDigitSprites = remember(activeTheme?.widgetDigitSpritePaths, useMtzWidgetStyle) {
        if (!useMtzWidgetStyle) {
            emptyMap()
        } else {
            val rawMap = activeTheme?.widgetDigitSpritePaths.orEmpty()
            buildMap {
                for ((ch, path) in rawMap) {
                    runCatching {
                        android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap()?.let { put(ch, it) }
                    }
                }
            }
        }
    }

    val activeMamlClockSpec = remember(
        activeTheme?.clock2x4WidgetSpec,
        activeTheme?.clock3x4WidgetSpec,
        widget.spanY,
        widget.widgetType,
        useMtzWidgetStyle
    ) {
        if (!useMtzWidgetStyle) {
            null
        } else if (
            widget.widgetType != HyperWidgetType.CLOCK_MTZ_STATUS &&
            widget.widgetType != HyperWidgetType.MIUI_CLOCK_WEATHER_2X4
        ) {
            null
        } else {
            val preferred = if (widget.spanY >= 3) {
                activeTheme?.clock3x4WidgetSpec ?: activeTheme?.clock2x4WidgetSpec
            } else {
                activeTheme?.clock2x4WidgetSpec ?: activeTheme?.clock3x4WidgetSpec
            }
            preferred?.takeIf { it.nodes.isNotEmpty() }
        }
    }

    val isNativeAppWidget = widget.widgetType == HyperWidgetType.NATIVE_APPWIDGET
    val isFramelessWidget = isNativeAppWidget ||
        widget.widgetType == HyperWidgetType.CLOCK_MTZ_STATUS ||
        activeMamlClockSpec != null
    var cardBoundsInRoot by remember(widget.id) { mutableStateOf(Rect.Zero) }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(if (isNativeAppWidget) 0.dp else 4.dp)
            .onGloballyPositioned { coords ->
                cardBoundsInRoot = coords.boundsInRoot()
            }
    ) {
        val cardWidthDp = maxWidth
        val cardHeightDp = maxHeight

        val boxModifier = Modifier
            .fillMaxSize()
            .then(
                if (isNativeAppWidget && !isEditingWidget && !isWorkspaceEditMode) {
                    Modifier
                } else {
                    Modifier.clip(RoundedCornerShape(if (isNativeAppWidget) 12.dp else 20.dp))
                }
            )
            .background(
                when {
                    isFramelessWidget -> Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.Transparent)
                    )
                    widget.widgetType == HyperWidgetType.MIUI_CLOCK_WEATHER_2X4 ||
                        widget.widgetType == HyperWidgetType.MIUI_WEATHER_BRIDGE -> if (useMtzWidgetStyle) {
                        Brush.linearGradient(
                            colors = listOf(
                                themePrimarySurfaceColor.copy(alpha = 0.86f),
                                themeSecondarySurfaceColor.copy(alpha = 0.42f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF10243E).copy(alpha = 0.86f),
                                Color(0xFF1E3B58).copy(alpha = 0.78f)
                            )
                        )
                    }
                    widget.widgetType == HyperWidgetType.MIUI_CALENDAR_BRIDGE -> if (useMtzWidgetStyle) {
                        Brush.linearGradient(
                            colors = listOf(
                                themePrimarySurfaceColor.copy(alpha = 0.88f),
                                SlateSurface.copy(alpha = 0.84f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1A2230).copy(alpha = 0.88f),
                                Color(0xFF121822).copy(alpha = 0.84f)
                            )
                        )
                    }
                    else -> if (useMtzWidgetStyle) {
                        Brush.linearGradient(
                            colors = listOf(
                                themePrimarySurfaceColor.copy(alpha = 0.76f),
                                SlateSurface.copy(alpha = 0.76f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                SlateSurface.copy(alpha = 0.72f),
                                SlateSurface.copy(alpha = 0.72f)
                            )
                        )
                    }
                }
            )
            .then(
                when {
                    isEditingWidget -> Modifier.border(
                        width = 1.8.dp,
                        color = MiuiResizeHandleBlue,
                        shape = RoundedCornerShape(if (isNativeAppWidget) 12.dp else 20.dp)
                    )
                    isWorkspaceEditMode -> Modifier.border(
                        width = 1.3.dp,
                        color = Color.White.copy(alpha = 0.50f),
                        shape = RoundedCornerShape(if (isNativeAppWidget) 12.dp else 20.dp)
                    )
                    isFramelessWidget -> Modifier
                    useMtzWidgetStyle -> Modifier.border(
                        width = 1.2.dp,
                        color = themeAccentColor.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    else -> Modifier.border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            )
            .miuiIconTapLongPressAndDrag(
                key1 = widget.id,
                key2 = widget.pageIndex,
                dragThreshold = 12.dp,
                enableImmediateDrag = isEditingWidget || isWorkspaceEditMode,
                onTap = {
                    if (isEditingWidget) {
                        onDismissEditMode()
                    } else if (isWorkspaceEditMode) {
                        onEnterEditMode(widget)
                    } else {
                        when (widget.widgetType) {
                            HyperWidgetType.CLOCK_MTZ_STATUS -> onLaunchClockApp()
                            HyperWidgetType.MIUI_CLOCK_WEATHER_2X4 -> onLaunchClockApp()
                            HyperWidgetType.MIUI_WEATHER_BRIDGE -> onLaunchWeatherApp()
                            HyperWidgetType.MIUI_CALENDAR_BRIDGE -> onLaunchCalendarApp(
                                telemetry.upcomingCalendarEvents.firstOrNull()?.startTimeMillis ?: System.currentTimeMillis()
                            )
                            HyperWidgetType.MAML_LIVE_DUO -> onLaunchClockApp()
                            else -> {}
                        }
                    }
                },
                onTapWithOffset = { tapRootOffset ->
                    if (isEditingWidget) {
                        onDismissEditMode()
                    } else if (isWorkspaceEditMode) {
                        onEnterEditMode(widget)
                    } else {
                        val relX = if (cardBoundsInRoot.width > 0f) {
                            (tapRootOffset.x - cardBoundsInRoot.left) / cardBoundsInRoot.width
                        } else {
                            0.5f
                        }
                        val relY = if (cardBoundsInRoot.height > 0f) {
                            (tapRootOffset.y - cardBoundsInRoot.top) / cardBoundsInRoot.height
                        } else {
                            0.5f
                        }
                        when (widget.widgetType) {
                            HyperWidgetType.CLOCK_MTZ_STATUS -> {
                                if (relY > 0.62f) {
                                    onLaunchCalendarApp(System.currentTimeMillis())
                                } else {
                                    onLaunchClockApp()
                                }
                            }
                            HyperWidgetType.MIUI_CLOCK_WEATHER_2X4 -> {
                                when {
                                    relX > 0.56f -> onLaunchWeatherApp()
                                    relY > 0.68f -> onLaunchCalendarApp(System.currentTimeMillis())
                                    else -> onLaunchClockApp()
                                }
                            }
                            HyperWidgetType.MIUI_WEATHER_BRIDGE -> onLaunchWeatherApp()
                            HyperWidgetType.MIUI_CALENDAR_BRIDGE -> {
                                val events = telemetry.upcomingCalendarEvents.take(2)
                                val targetMillis = if (events.size >= 2 && relY > 0.72f) {
                                    events[1].startTimeMillis
                                } else {
                                    events.firstOrNull()?.startTimeMillis ?: System.currentTimeMillis()
                                }
                                onLaunchCalendarApp(targetMillis)
                            }
                            HyperWidgetType.MAML_LIVE_DUO -> {
                                if (relX in 0.22f..0.52f) {
                                    onLaunchCalendarApp(System.currentTimeMillis())
                                } else {
                                    onLaunchClockApp()
                                }
                            }
                            else -> {}
                        }
                    }
                },
                onLongPressReached = {
                    onEnterEditMode(widget)
                },
                onReleaseWithoutDrag = {
                    onReleaseFingerHold()
                },
                onDragStart = { fingerRootOffset ->
                    onStartDragWidget(widget, fingerRootOffset)
                },
                onDragMove = { fingerRootOffset, _ ->
                    onDragWidget(fingerRootOffset)
                },
                onDragDrop = { fingerRootOffset ->
                    onEndDragWidget(widget, fingerRootOffset)
                },
                onDragCancel = {
                    onCancelDragWidget()
                }
            )
            .testTag("workspace_widget_${widget.id}")

        Box(
            modifier = boxModifier
        ) {
            if (!isFramelessWidget && mtzWidgetBgBitmap != null) {
                Image(
                    bitmap = mtzWidgetBgBitmap,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.32f)
                )
            }

            when (widget.widgetType) {
                HyperWidgetType.CLOCK_MTZ_STATUS -> {
                    if (activeMamlClockSpec != null) {
                        MamlClockWidgetCanvas(
                            spec = activeMamlClockSpec,
                            cardWidthDp = cardWidthDp,
                            cardHeightDp = cardHeightDp,
                            telemetry = telemetry,
                            customFontPath = activeTheme?.customFontPath,
                            widgetId = widget.id
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (useMtzWidgetStyle && mtzDigitSprites.size >= 5) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        val digitHeight = if (widget.spanY <= 1) 30.dp else 40.dp
                                        timeString.forEach { ch ->
                                            val sprite = mtzDigitSprites[ch]
                                            if (sprite != null) {
                                                Image(
                                                    bitmap = sprite,
                                                    contentDescription = ch.toString(),
                                                    modifier = Modifier.height(digitHeight)
                                                )
                                            } else {
                                                Text(
                                                    text = ch.toString(),
                                                    style = MaterialTheme.typography.displayMedium.copy(
                                                        fontSize = if (widget.spanY <= 1) 30.sp else 42.sp,
                                                        fontFamily = mtzCustomFontFamily
                                                    ),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Light
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = timeString,
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontSize = if (widget.spanY <= 1) 30.sp else 42.sp,
                                            fontFamily = mtzCustomFontFamily
                                        ),
                                        color = Color.White,
                                        fontWeight = FontWeight.Light,
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (mtzWeatherIconBitmap != null) {
                                        Image(
                                            bitmap = mtzWeatherIconBitmap,
                                            contentDescription = telemetry.weatherCondition,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = "$dateString • ${telemetry.weatherTempCelsius}°C ${telemetry.weatherCondition}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = mtzCustomFontFamily
                                        ),
                                        color = if (useMtzWidgetStyle) {
                                            Color(android.graphics.Color.parseColor(activeTheme?.titleColorHex ?: "#FFFFFF")).copy(alpha = 0.92f)
                                        } else {
                                            Color.White.copy(alpha = 0.88f)
                                        },
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                HyperWidgetType.MIUI_CLOCK_WEATHER_2X4 -> {
                    if (activeMamlClockSpec != null) {
                        MamlClockWidgetCanvas(
                            spec = activeMamlClockSpec,
                            cardWidthDp = cardWidthDp,
                            cardHeightDp = cardHeightDp,
                            telemetry = telemetry,
                            customFontPath = activeTheme?.customFontPath,
                            widgetId = widget.id
                        )
                    } else {
                        val clockApp = allApps.firstOrNull { it.packageName.contains("clock", ignoreCase = true) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (useMtzWidgetStyle && clockApp != null) {
                                    Image(
                                        bitmap = clockApp.themedIconBitmap.asImageBitmap(),
                                        contentDescription = "MIUI Theme Clock",
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Clock",
                                        tint = themeAccentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Text(
                                    text = if (useMtzWidgetStyle) {
                                        activeTheme?.title?.let { "$it • 2x4" } ?: "MIUI Clock 2x4"
                                    } else {
                                        "MIUI Clock 2x4"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = mtzCustomFontFamily
                                    ),
                                    color = themeAccentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = timeString,
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontSize = if (widget.spanY <= 1) 28.sp else 38.sp,
                                    fontFamily = mtzCustomFontFamily
                                ),
                                color = Color.White,
                                fontWeight = FontWeight.Light,
                                maxLines = 1
                            )
                            Text(
                                text = dateString,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = mtzCustomFontFamily
                                ),
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (useMtzWidgetStyle) themeAccentColor.copy(alpha = 0.14f)
                                    else Color.White.copy(alpha = 0.10f)
                                )
                                .border(
                                    width = if (useMtzWidgetStyle) 0.8.dp else 0.dp,
                                    color = if (useMtzWidgetStyle) themeAccentColor.copy(alpha = 0.35f) else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (mtzWeatherIconBitmap != null) {
                                    Image(
                                        bitmap = mtzWeatherIconBitmap,
                                        contentDescription = "MTZ Weather",
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = "Weather",
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "${telemetry.weatherTempCelsius}°C",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = mtzCustomFontFamily
                                    ),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${telemetry.weatherCity} • ${telemetry.weatherCondition}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = mtzCustomFontFamily
                                ),
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "H:${telemetry.weatherHighCelsius}° L:${telemetry.weatherLowCelsius}° • AQI ${telemetry.weatherAqi}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = mtzCustomFontFamily
                                ),
                                color = if (useMtzWidgetStyle) themeAccentColor else MintTertiary,
                                maxLines = 1
                            )
                            if (!telemetry.hasLocationPermission) {
                                Text(
                                    text = "Tap to Grant GPS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable { onRequestWidgetPermissions() }
                                )
                            }
                        }
                    }
                    }
                }

                HyperWidgetType.MIUI_WEATHER_BRIDGE -> {
                    val weatherApp = allApps.firstOrNull { it.packageName.contains("weather", ignoreCase = true) }
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Location",
                                    tint = themeAccentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = telemetry.weatherCity,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = mtzCustomFontFamily
                                    ),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = themeAccentColor.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "AQI ${telemetry.weatherAqi}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontFamily = mtzCustomFontFamily
                                        ),
                                        color = themeAccentColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!telemetry.hasLocationPermission) {
                                    Surface(
                                        color = EmeraldPrimary.copy(alpha = 0.28f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .clickable { onRequestWidgetPermissions() }
                                            .testTag("widget_grant_location_${widget.id}")
                                    ) {
                                        Text(
                                            text = "Grant GPS",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = EmeraldBright,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Weather",
                                    tint = Color.White.copy(alpha = 0.78f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable(onClick = onRefreshTelemetry)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                when {
                                    mtzWeatherIconBitmap != null -> {
                                        Image(
                                            bitmap = mtzWeatherIconBitmap,
                                            contentDescription = telemetry.weatherCondition,
                                            modifier = Modifier.size(38.dp)
                                        )
                                    }
                                    useMtzWidgetStyle && weatherApp != null -> {
                                        Image(
                                            bitmap = weatherApp.themedIconBitmap.asImageBitmap(),
                                            contentDescription = telemetry.weatherCondition,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.WbSunny,
                                            contentDescription = telemetry.weatherCondition,
                                            tint = Color(0xFFFBBF24),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "${telemetry.weatherTempCelsius}°C",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = mtzCustomFontFamily
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        text = telemetry.weatherCondition,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = mtzCustomFontFamily
                                        ),
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "H:${telemetry.weatherHighCelsius}°  L:${telemetry.weatherLowCelsius}°",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = mtzCustomFontFamily
                                    ),
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Humidity ${telemetry.weatherHumidity}% • Wind ${telemetry.weatherWindKmh} km/h",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontFamily = mtzCustomFontFamily
                                    ),
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }

                HyperWidgetType.MIUI_CALENDAR_BRIDGE -> {
                    val calApp = allApps.firstOrNull { it.packageName.contains("calendar", ignoreCase = true) }
                    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (useMtzWidgetStyle && calApp != null) {
                                    Image(
                                        bitmap = calApp.themedIconBitmap.asImageBitmap(),
                                        contentDescription = "MIUI Calendar",
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Calendar",
                                        tint = if (useMtzWidgetStyle) themeAccentColor else EmeraldBright,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = dateString,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = mtzCustomFontFamily
                                        ),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (useMtzWidgetStyle && activeTheme != null) {
                                            "${activeTheme.title} • Calendar Bridge"
                                        } else {
                                            "MIUI Theme Calendar Bridge"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontFamily = mtzCustomFontFamily
                                        ),
                                        color = if (useMtzWidgetStyle) themeAccentColor else TextSecondary
                                    )
                                }
                            }
                            if (!telemetry.hasCalendarPermission) {
                                Surface(
                                    color = EmeraldPrimary.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clickable { onRequestWidgetPermissions() }
                                        .testTag("widget_grant_calendar_${widget.id}")
                                ) {
                                    Text(
                                        text = "Grant Calendar",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = EmeraldBright,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Calendar",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable(onClick = onRefreshTelemetry)
                                )
                            }
                        }

                        if (telemetry.upcomingCalendarEvents.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                telemetry.upcomingCalendarEvents.take(2).forEach { ev ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = ev.title,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = mtzCustomFontFamily
                                            ),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (ev.isAllDay) "All day" else timeFmt.format(Date(ev.startTimeMillis)),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = mtzCustomFontFamily
                                            ),
                                            color = if (useMtzWidgetStyle) themeAccentColor else EmeraldBright
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (telemetry.hasCalendarPermission) {
                                        "No upcoming events • Tap to open Calendar"
                                    } else {
                                        "Tap to open native Calendar or grant READ_CALENDAR"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = mtzCustomFontFamily
                                    ),
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

            HyperWidgetType.DEVICE_TELEMETRY -> {
                val primaryBarColor = if (useMtzWidgetStyle) themeAccentColor else EmeraldPrimary
                val secondaryBarColor = if (useMtzWidgetStyle) themeSecondarySurfaceColor else MintTertiary
                val titleAccentColor = if (useMtzWidgetStyle) themeAccentColor else EmeraldBright
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (mtzGadgetIconBitmap != null || mtzGadgetCircleBitmap != null) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    if (mtzGadgetCircleBitmap != null) {
                                        Image(
                                            bitmap = mtzGadgetCircleBitmap,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    if (mtzGadgetIconBitmap != null) {
                                        Image(
                                            bitmap = mtzGadgetIconBitmap,
                                            contentDescription = "Gadget Status",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.BatteryChargingFull,
                                    contentDescription = "Battery",
                                    tint = titleAccentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "System Status",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = mtzCustomFontFamily ?: FontFamily.Default,
                                color = titleAccentColor
                            )
                        }
                        Row {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Telemetry",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(onClick = onRefreshTelemetry)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Widget",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onRemoveWidget(widget.id) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (telemetry.isCharging) "Battery (Charging)" else "Battery",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "${telemetry.batteryPercent}% (${String.format(Locale.US, "%.1f", telemetry.batteryTempCelsius)}C)",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = mtzCustomFontFamily ?: FontFamily.Default,
                            color = titleAccentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (telemetry.batteryPercent / 100f).coerceIn(0.05f, 1f) },
                        color = primaryBarColor,
                        trackColor = SlateSurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val ramRatio = (telemetry.usedRamMb.toFloat() / telemetry.totalRamMb.coerceAtLeast(1L)).coerceIn(0.05f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "System RAM",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "${telemetry.usedRamMb} / ${telemetry.totalRamMb} MB",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = mtzCustomFontFamily ?: FontFamily.Default,
                            color = secondaryBarColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { ramRatio },
                        color = secondaryBarColor,
                        trackColor = SlateSurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            HyperWidgetType.STORAGE_AND_PACKS -> {
                val storageAccentColor = if (useMtzWidgetStyle) themeAccentColor else SageSecondary
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (mtzGadgetCircleBitmap != null) {
                                Image(
                                    bitmap = mtzGadgetCircleBitmap,
                                    contentDescription = "Storage",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = "Storage",
                                    tint = storageAccentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Storage Status",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = mtzCustomFontFamily ?: FontFamily.Default,
                                color = storageAccentColor
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Widget",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onRemoveWidget(widget.id) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val storageRatio = (telemetry.usedStorageGb / telemetry.totalStorageGb.coerceAtLeast(1f)).coerceIn(0.05f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Data Partition",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f / %.0f GB", telemetry.usedStorageGb, telemetry.totalStorageGb),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = mtzCustomFontFamily ?: FontFamily.Default,
                            color = storageAccentColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { storageRatio },
                        color = storageAccentColor,
                        trackColor = SlateSurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val mamlCount = activeTheme?.fancyIconSpecs?.size
                        ?: allApps.count { it.resolutionSource == IconResolutionSource.MTZ_FANCY_MAML }
                    val directCount = activeTheme?.directIconsMap?.size
                        ?: allApps.count { it.resolutionSource == IconResolutionSource.MTZ_DIRECT_MAP }
                    val ruleCount = activeTheme?.iconRulesMap?.size
                        ?: allApps.count { it.resolutionSource == IconResolutionSource.MTZ_RULE_MAP }
                    val maskCount = allApps.count { it.resolutionSource == IconResolutionSource.MTZ_DYNAMIC_MASK }

                    Text(
                        text = "MTZ: $directCount PNG | $ruleCount Rule | $mamlCount MAML | $maskCount Masked",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HyperWidgetType.MAML_LIVE_DUO -> {
                val clockFancyBmp = remember(activeTheme, useMtzWidgetStyle, timeString) {
                    if (!useMtzWidgetStyle || activeTheme == null) null
                    else activeTheme.fancyIconSpecs.entries.firstOrNull { (k, _) ->
                        k.contains("deskclock", ignoreCase = true) || k.contains("clock", ignoreCase = true)
                    }?.value?.let { spec ->
                        runCatching {
                            sharedFolderMamlEngine.renderMamlFancyIcon(
                                spec = spec,
                                calendar = java.util.Calendar.getInstance(),
                                animTimeMs = 0L
                            )?.asImageBitmap()
                        }.getOrNull()
                    }
                }
                val calFancyBmp = remember(activeTheme, useMtzWidgetStyle, dateString) {
                    if (!useMtzWidgetStyle || activeTheme == null) null
                    else activeTheme.fancyIconSpecs.entries.firstOrNull { (k, _) ->
                        k.contains("calendar", ignoreCase = true)
                    }?.value?.let { spec ->
                        runCatching {
                            sharedFolderMamlEngine.renderMamlFancyIcon(
                                spec = spec,
                                calendar = java.util.Calendar.getInstance(),
                                animTimeMs = 0L
                            )?.asImageBitmap()
                        }.getOrNull()
                    }
                }
                val clockDirectBmp = remember(activeTheme, useMtzWidgetStyle) {
                    if (!useMtzWidgetStyle || activeTheme == null) null
                    else activeTheme.directIconsMap.entries.firstOrNull { (k, _) ->
                        k.contains("deskclock", ignoreCase = true)
                    }?.value?.let { path ->
                        runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
                    }
                }
                val calDirectBmp = remember(activeTheme, useMtzWidgetStyle) {
                    if (!useMtzWidgetStyle || activeTheme == null) null
                    else activeTheme.directIconsMap.entries.firstOrNull { (k, _) ->
                        k.contains("calendar", ignoreCase = true)
                    }?.value?.let { path ->
                        runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
                    }
                }
                val clockApp = allApps.firstOrNull { it.packageName.contains("deskclock") }
                val calApp = allApps.firstOrNull { it.packageName.contains("calendar") }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val effectiveClockBmp = clockFancyBmp ?: clockDirectBmp ?: clockApp?.themedIconBitmap?.asImageBitmap()
                        val effectiveCalBmp = calFancyBmp ?: calDirectBmp ?: calApp?.themedIconBitmap?.asImageBitmap()
                        if (effectiveClockBmp != null) {
                            Image(
                                bitmap = effectiveClockBmp,
                                contentDescription = "MAML Clock",
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        if (effectiveCalBmp != null) {
                            Image(
                                bitmap = effectiveCalBmp,
                                contentDescription = "MAML Calendar",
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Clock & Calendar",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = mtzCustomFontFamily ?: FontFamily.Default,
                                color = if (useMtzWidgetStyle) themeAccentColor else Color.White
                            )
                            Text(
                                text = activeTheme?.title?.let { "MAML: $it" } ?: "Live MAML Dynamic Icons",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove Widget",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onRemoveWidget(widget.id) }
                    )
                }
            }

            HyperWidgetType.NATIVE_APPWIDGET -> {
                val context = LocalContext.current
                val providerComp = widget.providerComponent?.let { ComponentName.unflattenFromString(it) }
                val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
                val providerInfo = remember(widget.appWidgetId, providerComp) {
                    if (widget.appWidgetId >= 0) {
                        appWidgetManager.getAppWidgetInfo(widget.appWidgetId)
                            ?: appWidgetManager.installedProviders.firstOrNull { it.provider == providerComp }
                    } else {
                        appWidgetManager.installedProviders.firstOrNull { it.provider == providerComp }
                    }
                }
                val hostApp = remember(providerComp, allApps) {
                    val pkg = providerComp?.packageName
                    allApps.firstOrNull { it.packageName == pkg }
                }

                val bindHostCallbacks: (LauncherAppWidgetHostView) -> Unit = { hostView ->
                    hostView.setPadding(0, 0, 0, 0)
                    hostView.isEditModeActive = isEditingWidget || isWorkspaceEditMode
                    hostView.onTapInEditModeListener = {
                        if (isEditingWidget) {
                            onDismissEditMode()
                        } else if (isWorkspaceEditMode) {
                            onEnterEditMode(widget)
                        }
                    }
                    hostView.onLongPressListener = {
                        onEnterEditMode(widget)
                    }
                    hostView.onReleaseWithoutDragListener = {
                        onReleaseFingerHold()
                    }
                    hostView.onStartDragListener = { localX, localY ->
                        onStartDragWidget(
                            widget,
                            Offset(cardBoundsInRoot.left + localX, cardBoundsInRoot.top + localY)
                        )
                    }
                    hostView.onDragMoveListener = { localX, localY ->
                        onDragWidget(
                            Offset(cardBoundsInRoot.left + localX, cardBoundsInRoot.top + localY)
                        )
                    }
                    hostView.onDragDropListener = { localX, localY ->
                        onEndDragWidget(
                            widget,
                            Offset(cardBoundsInRoot.left + localX, cardBoundsInRoot.top + localY)
                        )
                    }
                    hostView.onDragCancelListener = {
                        onCancelDragWidget()
                    }
                }

                if (appWidgetHost != null && widget.appWidgetId >= 0 && providerInfo != null) {
                    val widthDpInt = cardWidthDp.value.toInt().coerceAtLeast(60)
                    val heightDpInt = cardHeightDp.value.toInt().coerceAtLeast(60)
                    AndroidView(
                        factory = { ctx ->
                            appWidgetHost.createView(ctx, widget.appWidgetId, providerInfo).apply {
                                setPadding(0, 0, 0, 0)
                                setAppWidget(widget.appWidgetId, providerInfo)
                                if (this is LauncherAppWidgetHostView) {
                                    bindHostCallbacks(this)
                                }
                                runCatching {
                                    updateAppWidgetSize(
                                        android.os.Bundle(),
                                        widthDpInt,
                                        heightDpInt,
                                        widthDpInt,
                                        heightDpInt
                                    )
                                }
                            }
                        },
                        update = { hostView ->
                            hostView.setPadding(0, 0, 0, 0)
                            if (hostView is LauncherAppWidgetHostView) {
                                bindHostCallbacks(hostView)
                            }
                            runCatching {
                                hostView.updateAppWidgetSize(
                                    android.os.Bundle(),
                                    widthDpInt,
                                    heightDpInt,
                                    widthDpInt,
                                    heightDpInt
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Clean frameless native shortcut fallback if widget ID was not bound yet
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(enabled = !isEditingWidget && !isWorkspaceEditMode) {
                                val pkg = providerComp?.packageName
                                when {
                                    pkg?.contains("clock", ignoreCase = true) == true -> onLaunchClockApp()
                                    pkg?.contains("calendar", ignoreCase = true) == true -> onLaunchCalendarApp(System.currentTimeMillis())
                                    pkg?.contains("weather", ignoreCase = true) == true -> onLaunchWeatherApp()
                                    hostApp != null -> onLaunchApp(hostApp)
                                }
                            }
                    ) {
                        if (hostApp != null) {
                            Image(
                                bitmap = hostApp.themedIconBitmap.asImageBitmap(),
                                contentDescription = widget.customTitle,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                }
            }
        }
        }

        // 4 Blue Corner Resize Handles (`•`) when Widget is in Long-Press Edit Mode
        if (isEditingWidget) {
            val handleAlignments = listOf(
                Triple(Alignment.TopStart, -1, -1),
                Triple(Alignment.TopEnd, 1, -1),
                Triple(Alignment.BottomStart, -1, 1),
                Triple(Alignment.BottomEnd, 1, 1)
            )
            handleAlignments.forEachIndexed { idx, (alignment, dirX, dirY) ->
                var dragAccumX by remember(widget.id, widget.spanX, widget.spanY, idx) { mutableFloatStateOf(0f) }
                var dragAccumY by remember(widget.id, widget.spanX, widget.spanY, idx) { mutableFloatStateOf(0f) }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(alignment)
                        .offset(
                            x = if (dirX < 0) (-4).dp else 4.dp,
                            y = if (dirY < 0) (-4).dp else 4.dp
                        )
                        .size(28.dp)
                        .pointerInput(widget.id, widget.spanX, widget.spanY, maxCols) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragAccumX += dragAmount.x * dirX
                                    dragAccumY += dragAmount.y * dirY

                                    var targetSpanX = widget.spanX.coerceIn(minSpanX, maxCols)
                                    var targetSpanY = widget.spanY.coerceIn(1, maxRows)

                                    if (dragAccumX > stepThresholdPx) {
                                        targetSpanX = (targetSpanX + 1).coerceIn(minSpanX, maxCols)
                                        dragAccumX = 0f
                                    } else if (dragAccumX < -stepThresholdPx) {
                                        targetSpanX = (targetSpanX - 1).coerceIn(minSpanX, maxCols)
                                        dragAccumX = 0f
                                    }

                                    if (dragAccumY > stepThresholdPx) {
                                        targetSpanY = (targetSpanY + 1).coerceIn(1, maxRows)
                                        dragAccumY = 0f
                                    } else if (dragAccumY < -stepThresholdPx) {
                                        targetSpanY = (targetSpanY - 1).coerceIn(1, maxRows)
                                        dragAccumY = 0f
                                    }

                                    if (targetSpanX != widget.spanX || targetSpanY != widget.spanY) {
                                        onResizeWidgetSpan(targetSpanX, targetSpanY)
                                    }
                                }
                            )
                        }
                        .testTag("widget_resize_handle_${widget.id}_$idx")
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MiuiResizeHandleBlue)
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Stock MIUI Full-Screen Open Folder Overlay (`Screenshot_20261024_193657_d38c2e39.jpg`):
 * - Translucent darkened wallpaper backdrop
 * - Centered white folder title in upper-third (tappable to rename)
 * - Clean grid of `.mtz`-styled app icons with white labels
 * - Supports long-pressing an icon for the MIUI Floating Action Pill (`Delete`, `Uninstall`, `Share`, `App info`, `Edit icon`)
 * - Supports dragging an icon outside the folder grid onto the Workspace (automatically closing the folder overlay
 *   and dissolving the folder if 1 icon remains)
 * - Rounded-square outline `"+"` tile labeled `"Add"` at the end of the grid to add/remove apps
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandedHyperFolderSheet(
    folder: WorkspaceFolder,
    allApps: List<InstalledAppItem>,
    onDismiss: () -> Unit,
    onLaunchApp: (InstalledAppItem) -> Unit,
    onSelectLayoutMode: (HyperFolderLayoutMode) -> Unit,
    onRenameFolder: (String) -> Unit,
    onToggleAppMembership: (String) -> Unit,
    onDeleteFolder: () -> Unit,
    onOpenAppEditSheet: (InstalledAppItem) -> Unit = {},
    onUninstallApp: (InstalledAppItem) -> Unit = {},
    onShareApp: (InstalledAppItem) -> Unit = {},
    onOpenAppInfo: (InstalledAppItem) -> Unit = {},
    onStartDragOutOfFolder: (InstalledAppItem, Offset) -> Unit = { _, _ -> },
    onUpdateDragOutOfFolder: (Offset) -> Unit = {},
    onDropDragOutOfFolder: (InstalledAppItem, Offset) -> Unit = { _, _ -> },
    onCancelDragOutOfFolder: () -> Unit = {},
    overlayShadeAlpha: Float = 0.60f,
    folderAnimationMode: MiuiFolderAnimationMode = MiuiFolderAnimationMode.MIUI_ZOOM_EXPAND,
    sourceFolderBounds: Rect? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var showAddAppsPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var titleInput by remember(folder.title) { mutableStateOf(folder.title) }
    var longPressedAppInFolder by remember { mutableStateOf<InstalledAppItem?>(null) }
    var isDraggingOutToWorkspace by remember { mutableStateOf(false) }
    var overlayBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    val openProgress = remember(folder.id, folderAnimationMode) {
        Animatable(if (folderAnimationMode == MiuiFolderAnimationMode.OFF) 1f else 0f)
    }

    LaunchedEffect(folder.id, folderAnimationMode) {
        if (folderAnimationMode == MiuiFolderAnimationMode.OFF) {
            openProgress.snapTo(1f)
        } else {
            openProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (folderAnimationMode == MiuiFolderAnimationMode.MIUI_ZOOM_EXPAND) 260 else 200,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    val triggerAnimatedDismiss: () -> Unit = {
        if (folderAnimationMode == MiuiFolderAnimationMode.OFF) {
            onDismiss()
        } else {
            coroutineScope.launch {
                openProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 190,
                        easing = FastOutSlowInEasing
                    )
                )
                onDismiss()
            }
        }
    }

    BackHandler(onBack = {
        when {
            longPressedAppInFolder != null -> longPressedAppInFolder = null
            showAddAppsPicker -> showAddAppsPicker = false
            else -> triggerAnimatedDismiss()
        }
    })

    val animFraction = openProgress.value.coerceIn(0f, 1f)
    val effectiveBackdropAlpha = (overlayShadeAlpha.coerceIn(0f, 0.95f) * animFraction).coerceIn(0f, 0.95f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(45f)
            .alpha(if (isDraggingOutToWorkspace) 0f else 1f)
            .background(Color.Black.copy(alpha = effectiveBackdropAlpha))
            .onGloballyPositioned { coords ->
                overlayBoundsInRoot = coords.boundsInRoot()
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    when {
                        longPressedAppInFolder != null -> longPressedAppInFolder = null
                        showAddAppsPicker -> showAddAppsPicker = false
                        else -> triggerAnimatedDismiss()
                    }
                }
            )
            .testTag("expanded_folder_overlay_${folder.id}")
    ) {
        if (!showAddAppsPicker) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        when (folderAnimationMode) {
                            MiuiFolderAnimationMode.OFF -> {
                                alpha = 1f
                                scaleX = 1f
                                scaleY = 1f
                            }
                            MiuiFolderAnimationMode.SMOOTH_FADE -> {
                                alpha = animFraction
                                val s = 0.94f + 0.06f * animFraction
                                scaleX = s
                                scaleY = s
                            }
                            MiuiFolderAnimationMode.MIUI_ZOOM_EXPAND -> {
                                alpha = animFraction
                                val startScale = 0.28f
                                val curScale = startScale + (1f - startScale) * animFraction
                                scaleX = curScale
                                scaleY = curScale
                                val src = sourceFolderBounds
                                if (src != null && src != Rect.Zero && overlayBoundsInRoot.width > 0f) {
                                    val targetCenterX = overlayBoundsInRoot.center.x
                                    val targetCenterY = overlayBoundsInRoot.top + overlayBoundsInRoot.height * 0.42f
                                    val dx = src.center.x - targetCenterX
                                    val dy = src.center.y - targetCenterY
                                    translationX = dx * (1f - animFraction)
                                    translationY = dy * (1f - animFraction)
                                }
                            }
                        }
                    }
                    .padding(top = 112.dp, start = 28.dp, end = 28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (longPressedAppInFolder != null) {
                                longPressedAppInFolder = null
                            }
                        }
                    )
            ) {
                // Centered MIUI Folder Title (Tappable to Rename)
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showRenameDialog = true }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("expanded_folder_title")
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Folder App Grid + "+" Add Tile matching Screenshot_20261024_193657_d38c2e39.jpg
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    userScrollEnabled = !isDraggingOutToWorkspace && longPressedAppInFolder == null,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(folder.apps, key = { "${it.packageName}_${it.renderKey}" }) { app ->
                        var isPickedUp by remember { mutableStateOf(false) }

                        val iconBitmap = remember(app.renderKey, app.themedIconBitmap) {
                            app.themedIconBitmap.asImageBitmap()
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .miuiIconTapLongPressAndDrag(
                                    key1 = app.packageName,
                                    key2 = folder.id,
                                    dragThreshold = 12.dp,
                                    onTap = {
                                        if (longPressedAppInFolder != null) {
                                            longPressedAppInFolder = null
                                        } else {
                                            onDismiss()
                                            onLaunchApp(app)
                                        }
                                    },
                                    onLongPressReached = {
                                        isPickedUp = true
                                        longPressedAppInFolder = app
                                    },
                                    onReleaseWithoutDrag = {
                                        isPickedUp = false
                                    },
                                    onDragStart = { fingerRootPos ->
                                        isPickedUp = false
                                        longPressedAppInFolder = null
                                        isDraggingOutToWorkspace = true
                                        onStartDragOutOfFolder(app, fingerRootPos)
                                    },
                                    onDragMove = { fingerRootPos, _ ->
                                        if (isDraggingOutToWorkspace) {
                                            onUpdateDragOutOfFolder(fingerRootPos)
                                        }
                                    },
                                    onDragDrop = { fingerRootPos ->
                                        if (isDraggingOutToWorkspace) {
                                            isDraggingOutToWorkspace = false
                                            onDropDragOutOfFolder(app, fingerRootPos)
                                            onDismiss()
                                        }
                                    },
                                    onDragCancel = {
                                        isPickedUp = false
                                        if (isDraggingOutToWorkspace) {
                                            isDraggingOutToWorkspace = false
                                            onCancelDragOutOfFolder()
                                        }
                                    }
                                )
                                .padding(vertical = 4.dp)
                                .testTag("folder_modal_app_${app.packageName}")
                        ) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = app.label,
                                modifier = Modifier.size(if (isPickedUp) 66.dp else 62.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // "+" Add Tile at the end of the folder grid
                    item(key = "folder_add_app_tile") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showAddAppsPicker = true }
                                .padding(vertical = 4.dp)
                                .testTag("folder_sheet_edit_toggle")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(
                                        width = 1.2.dp,
                                        color = Color.White.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add apps to folder",
                                    tint = Color.White.copy(alpha = 0.88f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color.White,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Floating MIUI App Action Pill overlay (does not shift folder grid items during long-press)
            val activePillApp = longPressedAppInFolder
            if (activePillApp != null && !isDraggingOutToWorkspace) {
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 54.dp)
                        .zIndex(52f)
                ) {
                    MiuiAppFloatingActionPill(
                        app = activePillApp,
                        showDeleteShortcut = true,
                        onDeleteShortcut = {
                            longPressedAppInFolder = null
                            onToggleAppMembership(activePillApp.packageName)
                            if (folder.apps.size <= 2) {
                                onDismiss()
                            }
                        },
                        onUninstallApp = {
                            longPressedAppInFolder = null
                            onUninstallApp(activePillApp)
                        },
                        onShareApp = {
                            longPressedAppInFolder = null
                            onShareApp(activePillApp)
                        },
                        onAppInfo = {
                            longPressedAppInFolder = null
                            onOpenAppInfo(activePillApp)
                        },
                        onEditIcon = {
                            longPressedAppInFolder = null
                            onOpenAppEditSheet(activePillApp)
                        }
                    )
                }
            }
        } else {
            // App Checklist Selector when "+" (Add) is tapped
            Surface(
                color = Color(0xFF1F242A).copy(alpha = 0.96f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 64.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select apps for ${folder.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = { showAddAppsPicker = false }) {
                            Text("Done", color = EmeraldBright, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                    ) {
                        items(allApps, key = { it.packageName }) { app ->
                            val inFolder = app.packageName in folder.packageNames
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (inFolder) EmeraldContainer else SlateSurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (inFolder) EmeraldBright else SlateBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        onToggleAppMembership(app.packageName)
                                        if (inFolder && folder.packageNames.size <= 2) {
                                            showAddAppsPicker = false
                                            onDismiss()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Image(
                                    bitmap = app.themedIconBitmap.asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier.size(38.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                if (inFolder) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = EmeraldBright
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = SlateSurface,
            title = {
                Text("Rename folder", color = TextPrimary)
            },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
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
                        onRenameFolder(titleInput)
                        showRenameDialog = false
                    }
                ) {
                    Text("OK", color = EmeraldBright)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * Modal Bottom Sheet for creating new Folders or pinning Widgets to the Workspace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsAndFoldersPickerSheet(
    allApps: List<InstalledAppItem>,
    systemWidgetProviders: List<SystemWidgetProviderItem>,
    onDismiss: () -> Unit,
    onCreateFolder: (String, HyperFolderLayoutMode, List<String>) -> Unit,
    onAddBuiltInWidget: (HyperWidgetType) -> Unit,
    onBindSystemWidget: (SystemWidgetProviderItem) -> Unit
) {
    var folderTitle by remember { mutableStateOf("Folder") }
    var selectedMode by remember { mutableStateOf(HyperFolderLayoutMode.BIG_FOLDER_2X2) }
    val selectedPackages = remember {
        mutableStateListOf<String>().apply {
            addAll(allApps.take(5).map { it.packageName })
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        contentColor = TextPrimary
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    text = "Folders & Widgets",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Create interactive 1x1, 2x1, 1x2, or 2x2 Big Folders or pin widgets",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            item {
                Surface(
                    color = SlateDeepBackground,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "Create Big Folder",
                                tint = EmeraldBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create Folder",
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldBright
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = folderTitle,
                            onValueChange = { folderTitle = it },
                            label = { Text("Folder Name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = SlateBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_folder_title_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HyperFolderLayoutMode.entries.forEach { mode ->
                                val isChosen = selectedMode == mode
                                Surface(
                                    color = if (isChosen) EmeraldContainer else SlateSurfaceElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            1.dp,
                                            if (isChosen) EmeraldBright else SlateBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedMode = mode }
                                ) {
                                    Text(
                                        text = mode.code,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isChosen) EmeraldBright else TextSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Select Apps (${selectedPackages.size} selected):",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(allApps, key = { it.packageName }) { app ->
                                val picked = app.packageName in selectedPackages
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (picked) EmeraldContainer else SlateSurfaceElevated)
                                        .border(
                                            1.dp,
                                            if (picked) EmeraldBright else SlateBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            if (picked) selectedPackages.remove(app.packageName)
                                            else selectedPackages.add(app.packageName)
                                        }
                                        .padding(6.dp)
                                ) {
                                    Image(
                                        bitmap = app.themedIconBitmap.asImageBitmap(),
                                        contentDescription = app.label,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onCreateFolder(folderTitle, selectedMode, selectedPackages.toList())
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_create_big_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Folder",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add ${selectedMode.displayName} to Workspace",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "MIUI Theme & Native Bridge Widgets",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            val builtInTypes = listOf(
                HyperWidgetType.CLOCK_MTZ_STATUS,
                HyperWidgetType.MIUI_CLOCK_WEATHER_2X4,
                HyperWidgetType.MIUI_WEATHER_BRIDGE,
                HyperWidgetType.MIUI_CALENDAR_BRIDGE,
                HyperWidgetType.DEVICE_TELEMETRY,
                HyperWidgetType.STORAGE_AND_PACKS,
                HyperWidgetType.MAML_LIVE_DUO
            )

            items(builtInTypes, key = { it.code }) { wType ->
                Surface(
                    color = SlateDeepBackground,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            onAddBuiltInWidget(wType)
                            onDismiss()
                        }
                        .testTag("add_builtin_widget_${wType.code}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = wType.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldBright
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = SlateSurfaceElevated,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = wType.badgeSize,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = wType.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Widget",
                            tint = EmeraldBright
                        )
                    }
                }
            }

            if (systemWidgetProviders.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Android System Widgets (${systemWidgetProviders.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                items(systemWidgetProviders, key = { it.providerComponent }) { provider ->
                    Surface(
                        color = SlateDeepBackground,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                onBindSystemWidget(provider)
                                onDismiss()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Image(
                                bitmap = provider.iconBitmap.asImageBitmap(),
                                contentDescription = provider.label,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = provider.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${provider.packageName} (${provider.minWidthDp}x${provider.minHeightDp}dp)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Bind System Widget",
                                tint = EmeraldBright
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MamlClockWidgetCanvas(
    spec: com.example.domain.model.MamlClockWidgetSpec,
    cardWidthDp: Dp,
    cardHeightDp: Dp,
    telemetry: DeviceTelemetrySnapshot,
    customFontPath: String?,
    widgetId: String
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetWidthPx = with(density) { cardWidthDp.roundToPx().coerceIn(240, 1440) }
    val targetHeightPx = with(density) { cardHeightDp.roundToPx().coerceIn(140, 1440) }
    val is24Hour = remember(context) {
        android.text.format.DateFormat.is24HourFormat(context)
    }

    val hasContinuousRotation = remember(spec.rawManifestXml) {
        spec.rawManifestXml.contains("#time_sys") ||
            spec.rawManifestXml.contains("#second") ||
            spec.rawManifestXml.contains("#ms") ||
            spec.varAnimations.isNotEmpty()
    }

    var nowTick by remember(spec.directoryPath) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(spec.directoryPath, hasContinuousRotation) {
        val intervalMs = if (hasContinuousRotation) 66L else 1000L
        while (true) {
            delay(intervalMs)
            nowTick = System.currentTimeMillis()
        }
    }

    val renderedBitmap = remember(
        spec,
        targetWidthPx,
        targetHeightPx,
        nowTick,
        telemetry.weatherCondition,
        telemetry.weatherTempCelsius,
        telemetry.weatherCity,
        customFontPath,
        is24Hour
    ) {
        sharedClockWidgetMamlRenderer.renderWidgetBitmap(
            spec = spec,
            targetWidthPx = targetWidthPx,
            targetHeightPx = targetHeightPx,
            nowMillis = nowTick,
            telemetry = telemetry,
            customFontPath = customFontPath,
            is24HourFormat = is24Hour
        ).asImageBitmap()
    }

    Image(
        bitmap = renderedBitmap,
        contentDescription = "MIUI MAML Clock Widget (${spec.moduleName})",
        modifier = Modifier
            .fillMaxSize()
            .testTag("maml_clock_widget_canvas_$widgetId")
    )
}

