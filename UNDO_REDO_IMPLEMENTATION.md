# Undo/Redo Implementation Documentation

## Overview
This implementation adds comprehensive undo/redo functionality to the Mesh gradient editor, allowing users to revert and restore changes made to mesh points, canvas settings, and other state modifications.

## Architecture

### 1. Core Components

#### UndoRedoManager (`UndoRedoManager.kt`)
A dedicated class that manages the undo/redo history using two stacks:
- **Undo Stack**: Stores previous states that can be undone
- **Redo Stack**: Stores undone states that can be redone
- **Max Stack Size**: Limited to 100 entries to prevent memory issues

Key Methods:
- `saveState(snapshot)`: Saves a state snapshot before making changes
- `undo(currentSnapshot)`: Returns the previous state and moves current to redo stack
- `redo(currentSnapshot)`: Returns the next state and moves current to undo stack
- `canUndo()`: Check if undo is available
- `canRedo()`: Check if redo is available

#### AppStateSnapshot (Data Class)
Captures a complete snapshot of the application state:
```kotlin
data class AppStateSnapshot(
    val meshPoints: List<List<Pair<Offset, Long>>>,
    val meshState: MeshState,
    val canvasBackgroundColor: Long,
)
```

### 2. Integration Points

#### AppConfiguration.kt
Added undo/redo support to all state-modifying operations:

**State-Saving Operations:**
- `updateCanvasWidthMode()` - Canvas width mode toggle
- `updateCanvasHeightMode()` - Canvas height mode toggle
- `updateBlurLevel()` - Blur level adjustments
- `updateCanvasBackgroundColor()` - Background color changes
- `updateTotalRows()` - Mesh row count changes
- `updateTotalCols()` - Mesh column count changes
- `distributeMeshPointsEvenly()` - Point distribution
- `removeColorFromMeshPoints()` - Color removal from points
- `updateMeshPoint()` - Point position/data updates (with special drag handling)

**Special Handling for Drag Operations:**
- `prepareForDrag()`: Called once when drag starts to save initial state
- `updateMeshPoint()`: Accepts `saveForUndo` parameter (false during dragging)
- This prevents creating hundreds of undo entries for a single drag operation

**Non-Undoable Operations:**
- `updateCanvasWidth(width)` - Automatic resize (no undo state saved)
- `updateCanvasHeight(height)` - Automatic resize (no undo state saved)
- These are called during window resize and shouldn't be undoable

**Public API:**
- `undo()`: Reverts to previous state
- `redo()`: Restores next state
- `canUndo()`: Returns true if undo is available
- `canRedo()`: Returns true if redo is available

#### App.kt
Added keyboard event handling for undo/redo shortcuts:

**Keyboard Shortcuts:**
- **Ctrl+Z / Cmd+Z**: Undo (cross-platform)
- **Ctrl+Shift+Z / Cmd+Shift+Z**: Redo (cross-platform)
- **Ctrl+Y**: Alternative Redo (Windows/Linux only)

**Implementation Details:**
- Root `Row` composable is made focusable
- Focus is automatically requested on startup
- Key events are intercepted before reaching child components
- Returns `true` when handling undo/redo to prevent event propagation

## User Experience

### Keyboard Shortcuts
- Works on Windows, Linux, and macOS with platform-appropriate modifiers
- Consistent with standard application behavior (Ctrl on Windows/Linux, Cmd on macOS)
- Alternative Ctrl+Y shortcut for Windows/Linux users familiar with that convention

### Undo/Redo Behavior
1. **Point Movement**: Each drag operation creates one undo entry
2. **State Changes**: Each modification (blur, dimensions, colors) creates one undo entry
3. **Redo Stack**: Cleared when a new change is made after undoing
4. **History Limit**: Maximum 100 operations to prevent memory issues

### What Can Be Undone/Redone
✅ Mesh point position changes (dragging)
✅ Mesh point color changes
✅ Canvas dimension mode changes (Fill/Fixed)
✅ Blur level adjustments
✅ Canvas background color changes
✅ Mesh row/column count changes
✅ Distribute points evenly operation
✅ Color removal from mesh points

### What Cannot Be Undone
❌ Automatic canvas width/height updates during window resize
❌ Toggling point visibility
❌ Export operations
❌ Code export operations
❌ Color palette additions/deletions (separate feature)

## Technical Decisions

### 1. Snapshot-Based Approach
- **Decision**: Store complete state snapshots rather than incremental changes
- **Rationale**: Simpler implementation, easier to maintain, sufficient performance for 100 entries
- **Trade-off**: Higher memory usage, but negligible for mesh data size

### 2. Drag Operation Optimization
- **Decision**: Save state once at drag start, not on every movement
- **Rationale**: Prevents creating dozens/hundreds of undo entries per drag
- **Implementation**: `prepareForDrag()` called on drag start, `updateMeshPoint()` with `saveForUndo=false` during drag

### 3. Stack Size Limit
- **Decision**: Maximum 100 undo/redo entries
- **Rationale**: Balance between functionality and memory usage
- **Behavior**: Oldest entries are removed when limit is reached

### 4. Keyboard Shortcut Handling
- **Decision**: Handle at root composable level with focusable modifier
- **Rationale**: Ensures shortcuts work regardless of which UI element has focus
- **Implementation**: Focus requested on startup, events intercepted before children

## Testing Recommendations

### Manual Testing Checklist
- [ ] Undo/redo point movements
- [ ] Undo/redo blur level changes
- [ ] Undo/redo dimension changes
- [ ] Undo/redo background color changes
- [ ] Undo/redo distribute points evenly
- [ ] Verify redo stack clears after new change
- [ ] Test keyboard shortcuts on different platforms
- [ ] Verify drag operation creates single undo entry
- [ ] Test history limit (make 101+ changes)

### Edge Cases to Test
- [ ] Undo/redo at application startup
- [ ] Undo/redo when at stack limits
- [ ] Multiple rapid undo/redo operations
- [ ] Undo/redo with different mesh dimensions
- [ ] Undo/redo after loading saved state

## Future Enhancements

### Potential Improvements
1. **Visual Feedback**: Show toast/notification for undo/redo actions
2. **History UI**: Optional panel showing undo/redo history
3. **Named States**: Add labels to snapshots (e.g., "Moved point", "Changed blur")
4. **Selective Undo**: Ability to undo specific operations from history
5. **Persistence**: Save undo/redo history across sessions
6. **Performance**: Implement incremental changes for very large meshes

### Known Limitations
1. Undo/redo history is lost when application closes
2. No visual indication of undo/redo availability
3. No undo for color palette management
4. No undo for constraint settings

## Conclusion

The undo/redo implementation provides a robust, user-friendly way to revert and restore changes in the Mesh gradient editor. It follows platform conventions, handles edge cases appropriately, and maintains good performance characteristics.
