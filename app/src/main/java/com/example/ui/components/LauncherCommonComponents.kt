package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.IconResolutionSource
import com.example.domain.model.InstalledAppItem
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintContainer
import com.example.ui.theme.MintTertiary
import com.example.ui.theme.SageContainer
import com.example.ui.theme.SageSecondary
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CatalogIconEntry

private val sharedMamlAnimatorEngine by lazy { com.example.mtz.XiaomiIconRenderEngine() }

/**
 * Conflict-free MIUI/HyperOS gesture modifier that unifies:
 * 1. Short Tap (`onTap`)
 * 2. Immediate Long-Press feedback (`onLongPressReached` — fires at ~380ms while the finger is still
 *    held down so the MIUI Floating Action Pill and pickup scale appear immediately)
 * 3. Smooth Drag-after-Long-Press (`onDragStart`, `onDragMove`, `onDragDrop`, `onDragCancel` — once
 *    the finger moves beyond `dragThreshold` in root coordinates, transitions from the Floating Pill
 *    to continuous drag-and-drop without losing state or colliding with inner `.combinedClickable`
 *    or parent scroll containers)
 * 4. Release without Dragging (`onReleaseWithoutDrag` — when the finger is lifted after long-press
 *    without dragging past `dragThreshold`, keeping the Floating Pill open on screen)
 */
@Composable
fun Modifier.miuiIconTapLongPressAndDrag(
    key1: Any?,
    key2: Any? = Unit,
    dragThreshold: Dp = 12.dp,
    enableImmediateDrag: Boolean = false,
    onTap: () -> Unit,
    onTapWithOffset: ((fingerRootOffset: Offset) -> Unit)? = null,
    onLongPressReached: (fingerRootOffset: Offset) -> Unit = {},
    onReleaseWithoutDrag: () -> Unit = {},
    onDragStart: (fingerRootOffset: Offset) -> Unit = {},
    onDragMove: (fingerRootOffset: Offset, deltaRoot: Offset) -> Unit = { _, _ -> },
    onDragDrop: (fingerRootOffset: Offset) -> Unit = {},
    onDragCancel: () -> Unit = {}
): Modifier {
    val currentEnableImmediateDrag by rememberUpdatedState(enableImmediateDrag)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnTapWithOffset by rememberUpdatedState(onTapWithOffset)
    val currentOnLongPressReached by rememberUpdatedState(onLongPressReached)
    val currentOnReleaseWithoutDrag by rememberUpdatedState(onReleaseWithoutDrag)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragMove by rememberUpdatedState(onDragMove)
    val currentOnDragDrop by rememberUpdatedState(onDragDrop)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    val density = LocalDensity.current
    val dragThresholdPx = with(density) { dragThreshold.toPx() }
    var layoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun toRootOffset(localOffset: Offset, fallbackRoot: Offset, localDelta: Offset): Offset {
        val coords = layoutCoords
        return if (coords != null && coords.isAttached) {
            coords.localToRoot(localOffset)
        } else {
            fallbackRoot + localDelta
        }
    }

    return this
        .onGloballyPositioned { coords ->
            layoutCoords = coords
        }
        .semantics {
            onClick {
                val centerRoot = layoutCoords?.takeIf { it.isAttached }?.boundsInRoot()?.center ?: Offset.Zero
                currentOnTapWithOffset?.invoke(centerRoot) ?: currentOnTap()
                true
            }
            onLongClick {
                val centerRoot = layoutCoords?.takeIf { it.isAttached }?.boundsInRoot()?.center ?: Offset.Zero
                currentOnLongPressReached(centerRoot)
                currentOnReleaseWithoutDrag()
                true
            }
        }
        .pointerInput(key1, key2) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                down.consume()
                val pointerId = down.id
                val startLocalPos = down.position
                val startRootPos = toRootOffset(down.position, down.position, Offset.Zero)
                val touchSlop = viewConfiguration.touchSlop
                val longPressTimeout = viewConfiguration.longPressTimeoutMillis.coerceIn(300L, 420L)

                var latestLocalPos = startLocalPos
                var latestRootPos = startRootPos
                var releasedBeforeLongPress = false
                var cancelledByScrollOrSlop = false
                var startedImmediateDrag = false

                val timedOut = withTimeoutOrNull(longPressTimeout) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
                        if (change == null) {
                            cancelledByScrollOrSlop = true
                            break
                        }
                        latestLocalPos = change.position
                        latestRootPos = toRootOffset(change.position, latestRootPos, Offset.Zero)

                        if (!change.pressed) {
                            change.consume()
                            releasedBeforeLongPress = true
                            break
                        }

                        val distFromStart = (latestRootPos - startRootPos).getDistance()
                        if (distFromStart > touchSlop) {
                            if (currentEnableImmediateDrag) {
                                change.consume()
                                startedImmediateDrag = true
                                break
                            } else {
                                cancelledByScrollOrSlop = true
                                break
                            }
                        }
                        if (change.isConsumed) {
                            cancelledByScrollOrSlop = true
                            break
                        }
                    }
                    false
                } ?: true

                if (releasedBeforeLongPress && !cancelledByScrollOrSlop && !startedImmediateDrag) {
                    currentOnTapWithOffset?.invoke(latestRootPos) ?: currentOnTap()
                } else if ((timedOut || startedImmediateDrag) && !cancelledByScrollOrSlop && !releasedBeforeLongPress) {
                    val longPressRootPos = latestRootPos
                    var lastLocalPos = latestLocalPos
                    var lastRootPos = longPressRootPos
                    var hasStartedDragging = startedImmediateDrag

                    if (startedImmediateDrag) {
                        currentOnDragStart(longPressRootPos)
                    } else {
                        // Immediately show Floating Action Pill and pickup animation while finger is still held down
                        currentOnLongPressReached(longPressRootPos)
                    }

                    try {
                        while (true) {
                            // Intercept on Initial pass so no parent Pager/LazyColumn/Grid can steal or cancel the drag
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) {
                                event.changes.forEach { it.consume() }
                                break
                            }

                            val localDelta = change.position - lastLocalPos
                            lastLocalPos = change.position
                            change.consume()

                            val newRootPos = toRootOffset(change.position, lastRootPos, localDelta)
                            val rootDelta = newRootPos - lastRootPos
                            lastRootPos = newRootPos

                            val totalDistFromLongPress = (newRootPos - longPressRootPos).getDistance()
                            if (!hasStartedDragging && totalDistFromLongPress >= dragThresholdPx) {
                                hasStartedDragging = true
                                currentOnDragStart(newRootPos)
                            }
                            if (hasStartedDragging && rootDelta != Offset.Zero) {
                                currentOnDragMove(newRootPos, rootDelta)
                            }
                        }

                        if (hasStartedDragging) {
                            currentOnDragDrop(lastRootPos)
                        } else {
                            currentOnReleaseWithoutDrag()
                        }
                    } catch (c: kotlinx.coroutines.CancellationException) {
                        if (hasStartedDragging) {
                            currentOnDragCancel()
                        }
                        throw c
                    }
                }
            }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconTile(
    app: InstalledAppItem,
    iconSize: Dp,
    showLabel: Boolean,
    showApiBadge: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showEditSelectionBadge: Boolean = false,
    isSelectedForEdit: Boolean = false,
    enableClickHandling: Boolean = true,
    isPickedUp: Boolean = false
) {
    val mamlSpec = app.mamlFancySpec
    var liveBitmap by remember(app.renderKey, app.themedIconBitmap) {
        mutableStateOf(app.themedIconBitmap)
    }

    if (mamlSpec != null && (mamlSpec.variables.isNotEmpty() || mamlSpec.isLiveClockOrCalendar)) {
        androidx.compose.runtime.LaunchedEffect(app.renderKey, mamlSpec.targetPackage) {
            if (mamlSpec.variables.isNotEmpty()) {
                val startMs = androidx.compose.runtime.withFrameMillis { it }
                val durationMs = 1050L
                while (true) {
                    val nowMs = androidx.compose.runtime.withFrameMillis { it }
                    val elapsed = (nowMs - startMs).coerceAtLeast(0L)
                    val cal = java.util.Calendar.getInstance()
                    val frameBmp = sharedMamlAnimatorEngine.renderMamlFancyIcon(
                        spec = mamlSpec,
                        calendar = cal,
                        animTimeMs = elapsed.coerceAtMost(1200L)
                    )
                    if (frameBmp != null) {
                        liveBitmap = frameBmp
                    }
                    if (elapsed >= durationMs) break
                    kotlinx.coroutines.delay(16L)
                }
            }
            if (mamlSpec.isLiveClockOrCalendar && mamlSpec.targetPackage.contains("clock", ignoreCase = true)) {
                while (true) {
                    kotlinx.coroutines.delay(1000L)
                    val cal = java.util.Calendar.getInstance()
                    val tickBmp = sharedMamlAnimatorEngine.renderMamlFancyIcon(
                        spec = mamlSpec,
                        calendar = cal,
                        animTimeMs = 1200L
                    )
                    if (tickBmp != null) {
                        liveBitmap = tickBmp
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (enableClickHandling) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("app_tile_${app.packageName}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val imageBitmap = remember(liveBitmap) {
            liveBitmap.asImageBitmap()
        }
        Box(contentAlignment = Alignment.Center) {
            Image(
                bitmap = imageBitmap,
                contentDescription = app.label,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        val s = if (isPickedUp) 1.09f else 1f
                        scaleX = s
                        scaleY = s
                    }
            )
            if (showApiBadge) {
                Surface(
                    color = SlateSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .border(0.75.dp, EmeraldPrimary.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = shortCodeForSource(app.resolutionSource),
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldBright,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
            // MIUI Workspace Edit Mode Top-Left Circular Multi-Select Badge (`Screenshot_20261025_022655_a0b875de.jpg`)
            if (showEditSelectionBadge) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelectedForEdit) Color(0xFFFF4081)
                            else Color(0xFF4A4E54).copy(alpha = 0.65f)
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isSelectedForEdit) Color(0xFFFF4081) else Color.White.copy(alpha = 0.85f),
                            shape = CircleShape
                        )
                        .testTag("edit_select_badge_${app.packageName}")
                ) {
                    if (isSelectedForEdit) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        if (showLabel) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Stock MIUI App Icon Long-Press Floating Action Pill (`Screenshot_20261025_022417_89523028.jpg`):
 * Renders a dark floating rounded pill with circular action buttons:
 * - `Delete`: White circle with dark minus (`−`) icon (removes shortcut from workspace/folder)
 * - `Uninstall`: Soft pink/red circle (`#FADBD8`) with red trash icon (launches package uninstall intent)
 * - `Share`: White circle with dark share icon (shares app package/info)
 * - `App info`: White circle with dark info (`i`) icon (launches system App Details settings)
 * - `Edit icon`: White circle with dark tune icon (opens per-icon MTZ Pipeline Inspector & Override Sheet)
 */
@Composable
fun MiuiAppFloatingActionPill(
    app: InstalledAppItem,
    showDeleteShortcut: Boolean = true,
    onDeleteShortcut: () -> Unit,
    onUninstallApp: () -> Unit,
    onShareApp: () -> Unit,
    onAppInfo: () -> Unit,
    onEditIcon: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF28282C).copy(alpha = 0.96f),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 10.dp,
        shadowElevation = 12.dp,
        modifier = modifier
            .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("app_floating_action_pill_${app.packageName}")
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (showDeleteShortcut) {
                MiuiAppPillButton(
                    icon = Icons.Default.Remove,
                    label = "Delete",
                    circleBg = Color.White,
                    iconTint = Color(0xFF1F2429),
                    onClick = onDeleteShortcut,
                    testTag = "icon_popup_delete_${app.packageName}"
                )
            }
            MiuiAppPillButton(
                icon = Icons.Default.DeleteOutline,
                label = "Uninstall",
                circleBg = Color(0xFFFDE0E0),
                iconTint = Color(0xFFE53935),
                onClick = onUninstallApp,
                testTag = "icon_popup_uninstall_${app.packageName}"
            )
            MiuiAppPillButton(
                icon = Icons.Default.Share,
                label = "Share",
                circleBg = Color.White,
                iconTint = Color(0xFF1F2429),
                onClick = onShareApp,
                testTag = "icon_popup_share_${app.packageName}"
            )
            MiuiAppPillButton(
                icon = Icons.Default.Info,
                label = "App info",
                circleBg = Color.White,
                iconTint = Color(0xFF1F2429),
                onClick = onAppInfo,
                testTag = "icon_popup_app_info_${app.packageName}"
            )
            MiuiAppPillButton(
                icon = Icons.Default.Tune,
                label = "Edit icon",
                circleBg = Color.White,
                iconTint = Color(0xFF1F2429),
                onClick = onEditIcon,
                testTag = "icon_popup_edit_${app.packageName}"
            )
        }
    }
}

@Composable
private fun MiuiAppPillButton(
    icon: ImageVector,
    label: String,
    circleBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(54.dp)
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
                .background(circleBg)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
            color = Color.White.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ResolutionBadgeChip(
    source: IconResolutionSource,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (source) {
        IconResolutionSource.MTZ_FANCY_MAML -> EmeraldContainer to EmeraldBright
        IconResolutionSource.MTZ_DIRECT_MAP -> SageContainer to SageSecondary
        IconResolutionSource.MTZ_RULE_MAP -> MintContainer to EmeraldBright
        IconResolutionSource.MTZ_DYNAMIC_MASK -> MintContainer to MintTertiary
        IconResolutionSource.MANUAL_OVERRIDE -> EmeraldContainer to EmeraldPrimary
        IconResolutionSource.SYSTEM_RAW -> SlateSurfaceElevated to TextSecondary
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.border(1.dp, fg.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
    ) {
        Text(
            text = source.code,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun shortCodeForSource(source: IconResolutionSource): String = when (source) {
    IconResolutionSource.MANUAL_OVERRIDE -> "OVR"
    IconResolutionSource.MTZ_FANCY_MAML -> "MAML"
    IconResolutionSource.MTZ_DIRECT_MAP -> "PNG"
    IconResolutionSource.MTZ_RULE_MAP -> "RULE"
    IconResolutionSource.MTZ_DYNAMIC_MASK -> "MASK"
    IconResolutionSource.SYSTEM_RAW -> "RAW"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionBottomSheet(
    app: InstalledAppItem,
    iconCatalog: List<CatalogIconEntry>,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onInspectPipeline: () -> Unit,
    onToggleWorkspacePin: () -> Unit,
    onToggleDockPin: () -> Unit,
    onSelectOverrideAsset: (String?) -> Unit
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
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    bitmap = app.themedIconBitmap.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ResolutionBadgeChip(source = app.resolutionSource)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = app.matchedAssetName,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onLaunch()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sheet_launch_app_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = "Launch",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch App", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onInspectPipeline()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sheet_inspect_pipeline_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Inspect API Pipeline",
                        tint = EmeraldBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Inspect API", color = EmeraldBright)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onToggleWorkspacePin()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Workspace Pin",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (app.isPinnedToWorkspace) "Unpin Home" else "Pin to Home",
                        color = TextPrimary
                    )
                }

                OutlinedButton(
                    onClick = {
                        onToggleDockPin()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Dock Pin",
                        tint = EmeraldBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (app.isPinnedToDock) "Unpin Dock" else "Pin to Dock",
                        color = EmeraldBright
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SlateBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manual .MTZ Icon Override (res/drawable-xxhdpi)",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                if (app.customOverrideAssetKey != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                onSelectOverrideAsset(null)
                                onDismiss()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Reset Override",
                            tint = EmeraldBright,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset Auto",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldBright
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(iconCatalog, key = { it.key }) { entry ->
                    val isSelected = app.customOverrideAssetKey == entry.key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) EmeraldContainer else SlateSurfaceElevated)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) EmeraldBright else SlateBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                onSelectOverrideAsset(entry.key)
                                onDismiss()
                            }
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = entry.bitmap.asImageBitmap(),
                            contentDescription = entry.key,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.key.substringAfterLast('.'),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
