# Bottom Pin Bar (Dock) Customization, Centering & Drag-and-Drop Fix Plan

## 1. Root Cause Analysis ("Why did this happen?")

1. **`"Render pipeline error: Child of the scoped flow was cancelled"`**:
   - `LauncherViewModel.observeLauncherData()` observes the combined Room database flows using `.collectLatest { ... }`.
   - When moving or pinning an icon updates the database across back-to-back writes (for example, updating the shortcut's dock state and then compacting empty workspace pages), `collectLatest` cancels the in-flight `applyResolvedData(...)` coroutine so the newer state can run.
   - Kotlin Coroutines signals cancellation by throwing `CancellationException` (`Child of the scoped flow was cancelled`). Because the `try / catch (e: Exception)` block in `observeLauncherData()` caught all `Exception` types without rethrowing `CancellationException`, normal flow cancellation was mistakenly treated as a render pipeline failure and shown in the error toast.

2. **Why placing icons one after another in the bottom pin bar failed**:
   - **Premature Swap Instead of Insertion**: In `WorkspaceScreen.kt`, dropping an icon onto the dock checked if the finger landed near an existing dock icon (`targetDockApp != null`) and immediately swapped the existing dock icon out to the desktop—even when the dock only had 1, 2, or 3 icons and had room to insert the dropped icon next to it.
   - **Hardcoded 4-Icon / 5-Icon Limits**: `WorkspaceScreen.kt` sliced dock items with `dockApps.take(4)` while `LauncherViewModel.kt` used `.take(5)`, ignoring the user's configured `gridColumns` (4, 5, or 6).
   - **Unnormalized `sortOrder` Indices**: `LauncherRepository.moveAppShortcut()` assigned `sortOrder = targetSortOrder` without re-indexing the remaining dock items (`0, 1, 2, ...`), causing duplicate `sortOrder` values when icons were added and removed repeatedly.

---

## 2. Proposed Changes

### A. Coroutine Cancellation Handling (`LauncherViewModel.kt`)
- In `observeLauncherData()`, explicitly rethrow `kotlinx.coroutines.CancellationException` before handling genuine rendering exceptions so rapid drag-and-drop updates never trigger false error banners.
- Update `dockApps` slicing in `applyResolvedData()` to respect `config.gridColumns.coerceIn(4, 6)` instead of a hardcoded `.take(5)`.

### B. Dynamic Dock Capacity & Centered Layout (`WorkspaceScreen.kt`)
- Replace the hardcoded `dockApps.take(4)` limit with `dockApps.take(engineConfig.gridColumns)`.
- Compute each dock slot width from the available dock bar width divided by `engineConfig.gridColumns` so up to 4, 5, or 6 icons fit comfortably on a single row.
- Center the dock icons horizontally (`Alignment.CenterHorizontally`) when `dockApps.size < engineConfig.gridColumns`, so 1 to `N-1` pinned icons remain balanced in the center of the floating dock bar.

### C. Sequential Insertion, Reordering & Full-Dock Swap (`WorkspaceScreen.kt` & `LauncherRepository.kt`)
- **Drop Index Calculation**:
  - When an icon is dropped inside the dock bar bounds, compute the horizontal insertion index (`0..dockApps.size`) by comparing `dropPos.x` against the center X coordinates of the currently rendered dock icons.
  - **When Dock Has Space (`dockApps.size < engineConfig.gridColumns`) or Dragging Within Dock**: Insert/reorder the dragged icon at the calculated slot index (`0..dockApps.size`) and shift existing dock icons cleanly without evicting any icon.
  - **When Dock Is Full (`dockApps.size >= engineConfig.gridColumns`) and Dragging From Desktop/Folder**: Swap the dragged icon with the targeted dock icon (`targetDockApp`) at that position.
- **Sequential `sortOrder` Normalization (`LauncherRepository.kt`)**:
  - Update `moveAppShortcut()`, `extractAppFromFolderToWorkspace()`, `toggleWorkspacePin()`, and `relayoutItemsForGridSize()` so dock items are always inserted at `targetSortOrder.coerceIn(0, dockItems.size)` and every dock item's `sortOrder` is re-indexed sequentially (`0, 1, ..., n - 1`).
  - When `gridColumns` is reduced in settings (e.g., from 6 to 4), automatically move any overflow dock icons (`index >= newColumns`) to the first available desktop cell so no pinned icons are hidden or lost.

### D. Verification (`ExampleRobolectricTest.kt`)
- Add a unit/Robolectric test verifying:
  - Sequential insertion of icons one after another into the dock up to `gridColumns` (4, 5, and 6) preserves exact left-to-right order.
  - Reordering icons within the dock updates their sequence cleanly.
  - Rapid consecutive dock moves do not emit any `"Render pipeline error"` status message.
