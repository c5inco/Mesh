package des.c5inco.mesh.data

import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import data.MeshState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Represents a snapshot of the application state that can be undone/redone
 */
data class AppStateSnapshot(
    val meshPoints: List<List<Pair<Offset, Long>>>,
    val meshState: MeshState,
    val canvasBackgroundColor: Long,
)

/**
 * Manages undo/redo functionality for the application
 */
class UndoRedoManager {
    private val undoStack = mutableListOf<AppStateSnapshot>()
    private val redoStack = mutableListOf<AppStateSnapshot>()
    private val maxStackSize = 100 // Limit history to prevent memory issues
    
    val canUndo: Boolean
        get() = undoStack.isNotEmpty()
    
    val canRedo: Boolean
        get() = redoStack.isNotEmpty()
    
    /**
     * Saves the current state before making a change
     */
    fun saveState(snapshot: AppStateSnapshot) {
        // Add to undo stack
        undoStack.add(snapshot)
        
        // Clear redo stack when a new change is made
        redoStack.clear()
        
        // Limit stack size
        if (undoStack.size > maxStackSize) {
            undoStack.removeAt(0)
        }
    }
    
    /**
     * Undoes the last change and returns the previous state
     */
    fun undo(currentSnapshot: AppStateSnapshot): AppStateSnapshot? {
        if (!canUndo) return null
        
        // Move current state to redo stack
        redoStack.add(currentSnapshot)
        
        // Get and remove the last state from undo stack
        val previousState = undoStack.removeAt(undoStack.size - 1)
        
        return previousState
    }
    
    /**
     * Redoes the last undone change and returns the next state
     */
    fun redo(currentSnapshot: AppStateSnapshot): AppStateSnapshot? {
        if (!canRedo) return null
        
        // Move current state to undo stack
        undoStack.add(currentSnapshot)
        
        // Get and remove the last state from redo stack
        val nextState = redoStack.removeAt(redoStack.size - 1)
        
        return nextState
    }
    
    /**
     * Clears all undo/redo history
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
