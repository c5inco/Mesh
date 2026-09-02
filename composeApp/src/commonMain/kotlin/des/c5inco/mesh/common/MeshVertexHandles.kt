package des.c5inco.mesh.common

import androidx.compose.ui.geometry.Offset

/**
 * Bezier control-point offsets for one [MeshGradientPainter] vertex.
 *
 * Offsets are relative to the vertex position in normalized mesh space.
 * [Offset.Unspecified] leaves that tangent inferred by Compose.
 */
data class MeshVertexHandles(
    val left: Offset = Offset.Unspecified,
    val top: Offset = Offset.Unspecified,
    val right: Offset = Offset.Unspecified,
    val bottom: Offset = Offset.Unspecified,
) {
    fun with(side: HandleSide, offset: Offset): MeshVertexHandles = when (side) {
        HandleSide.Left -> copy(left = offset)
        HandleSide.Top -> copy(top = offset)
        HandleSide.Right -> copy(right = offset)
        HandleSide.Bottom -> copy(bottom = offset)
    }

    fun offset(side: HandleSide): Offset = when (side) {
        HandleSide.Left -> left
        HandleSide.Top -> top
        HandleSide.Right -> right
        HandleSide.Bottom -> bottom
    }

    val hasCustomHandles: Boolean
        get() = left.isSpecified || top.isSpecified || right.isSpecified || bottom.isSpecified
}

enum class HandleSide(val label: String, val shortLabel: String) {
    Left("Left", "L"),
    Top("Top", "T"),
    Right("Right", "R"),
    Bottom("Bottom", "B"),
}

fun HandleSide.isAvailable(row: Int, col: Int, rows: Int, cols: Int): Boolean = when (this) {
    HandleSide.Left -> col > 0
    HandleSide.Right -> col < cols - 1
    HandleSide.Top -> row > 0
    HandleSide.Bottom -> row < rows - 1
}

/** Visible seed used when a tangent is still inferred, so the handle can be grabbed. */
fun HandleSide.defaultOffset(): Offset = when (this) {
    HandleSide.Left -> Offset(-DEFAULT_HANDLE_DISTANCE, 0f)
    HandleSide.Right -> Offset(DEFAULT_HANDLE_DISTANCE, 0f)
    HandleSide.Top -> Offset(0f, -DEFAULT_HANDLE_DISTANCE)
    HandleSide.Bottom -> Offset(0f, DEFAULT_HANDLE_DISTANCE)
}

const val DEFAULT_HANDLE_DISTANCE = 0.14f

val Offset.isSpecified: Boolean
    get() = this != Offset.Unspecified

fun Map<Pair<Int, Int>, MeshVertexHandles>.resolvedOffset(
    row: Int,
    col: Int,
    side: HandleSide,
): Offset {
    val stored = this[row to col]?.offset(side)
    return if (stored != null && stored.isSpecified) stored else side.defaultOffset()
}


