package des.c5inco.mesh.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Undo and redo stacks of state snapshots.
 *
 * Consecutive edits in the same group less than [mergeWindowMillis] apart, such as the steps of a
 * drag, merge into one undo step.
 */
class EditHistory<T>(
    private val limit: Int = 100,
    private val mergeWindowMillis: Long = 1_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    private var lastGroup: String? = null
    private var lastEditMillis = 0L

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /** Records [before], the state an edit just replaced. */
    fun record(before: T, group: String?) {
        val now = clock()
        val merges = group != null && group == lastGroup && now - lastEditMillis < mergeWindowMillis
        if (!merges || undoStack.isEmpty()) {
            undoStack.addLast(before)
            if (undoStack.size > limit) undoStack.removeFirst()
        }
        redoStack.clear()
        lastGroup = group
        lastEditMillis = now
        updateFlags()
    }

    /** Returns the state to restore, or null when there is nothing to undo. */
    fun undo(current: T): T? = move(from = undoStack, to = redoStack, current)

    /** Returns the state to restore, or null when there is nothing to redo. */
    fun redo(current: T): T? = move(from = redoStack, to = undoStack, current)

    /** Forgets every recorded state, such as when another document opens. */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastGroup = null
        updateFlags()
    }

    private fun move(from: ArrayDeque<T>, to: ArrayDeque<T>, current: T): T? {
        val target = from.removeLastOrNull() ?: return null
        to.addLast(current)
        lastGroup = null
        updateFlags()
        return target
    }

    private fun updateFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}
