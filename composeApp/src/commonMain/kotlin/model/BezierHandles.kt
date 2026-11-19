package model

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable

/** The edge a Bezier handle bends, named for where it leaves its point. */
enum class HandleSide {
    Left,
    Top,
    Right,
    Bottom;

    val opposite: HandleSide
        get() = when (this) {
            Left -> Right
            Top -> Bottom
            Right -> Left
            Bottom -> Top
        }
}

/** How edits couple opposite handles, independently of whether their positions are automatic. */
@Serializable
enum class HandleMirroring { NONE, ANGLE, ANGLE_AND_LENGTH }

/** One mode for left/right and one for top/bottom, shared by both handles in each pair. */
@Serializable
data class PointMirroring(
    val horizontal: HandleMirroring = HandleMirroring.NONE,
    val vertical: HandleMirroring = HandleMirroring.NONE,
) {
    operator fun get(side: HandleSide): HandleMirroring = when (side) {
        HandleSide.Left, HandleSide.Right -> horizontal
        HandleSide.Top, HandleSide.Bottom -> vertical
    }

    fun with(side: HandleSide, mode: HandleMirroring): PointMirroring = when (side) {
        HandleSide.Left, HandleSide.Right -> copy(horizontal = mode)
        HandleSide.Top, HandleSide.Bottom -> copy(vertical = mode)
    }
}

/**
 * One point's Bezier handles: offsets from the point in normalized canvas coordinates, one for each
 * edge that leaves it. A null handle is automatic, which makes the mesh infer a smooth tangent from
 * the point's neighbors, as Compose's MeshGradientPainter does by default.
 */
data class PointHandles(
    val left: Offset? = null,
    val top: Offset? = null,
    val right: Offset? = null,
    val bottom: Offset? = null,
    val mirroring: PointMirroring = PointMirroring(),
) {
    val isAutomatic: Boolean
        get() = left == null && top == null && right == null && bottom == null

    operator fun get(side: HandleSide): Offset? = when (side) {
        HandleSide.Left -> left
        HandleSide.Top -> top
        HandleSide.Right -> right
        HandleSide.Bottom -> bottom
    }

    fun with(side: HandleSide, offset: Offset?): PointHandles = when (side) {
        HandleSide.Left -> copy(left = offset)
        HandleSide.Top -> copy(top = offset)
        HandleSide.Right -> copy(right = offset)
        HandleSide.Bottom -> copy(bottom = offset)
    }
}

/** A grid of fully automatic handles, one per point. */
fun automaticHandles(rows: Int, cols: Int): List<List<PointHandles>> =
    List(rows) { List(cols) { PointHandles() } }

/** The handle at a point, or auto handles when the grid has none (such as before a document loads). */
fun List<List<PointHandles>>.handlesAt(row: Int, col: Int): PointHandles =
    getOrNull(row)?.getOrNull(col) ?: PointHandles()

/**
 * The handle Compose's MeshGradientPainter infers for [side] at ([row], [col]): a third of the way
 * toward the neighbor in that direction, along the tangent between the neighbors on either side.
 * An edge point has no neighbor beyond the edge, so its outward handle is zero.
 */
fun List<List<Offset>>.inferredHandle(row: Int, col: Int, side: HandleSide): Offset {
    val current = this[row][col]
    val leftNeighbor = if (col > 0) this[row][col - 1] else current
    val rightNeighbor = if (col < this[row].lastIndex) this[row][col + 1] else current
    val horizontalVector = (rightNeighbor - leftNeighbor).normalized() * 0.33f

    val topNeighbor = if (row > 0) this[row - 1][col] else current
    val bottomNeighbor = if (row < lastIndex) this[row + 1][col] else current
    val verticalVector = (bottomNeighbor - topNeighbor).normalized() * 0.33f

    return when (side) {
        HandleSide.Left -> -horizontalVector * (leftNeighbor - current).getDistance()
        HandleSide.Right -> horizontalVector * (rightNeighbor - current).getDistance()
        HandleSide.Top -> -verticalVector * (topNeighbor - current).getDistance()
        HandleSide.Bottom -> verticalVector * (bottomNeighbor - current).getDistance()
    }
}

/** The [side] handle's offset: the explicit one when set, or the one the mesh infers for it. */
fun List<List<Offset>>.effectiveHandle(
    row: Int,
    col: Int,
    side: HandleSide,
    handles: PointHandles,
): Offset = handles[side] ?: inferredHandle(row, col, side)

/** All four handles for a point, with automatic ones filled in as the mesh infers them. */
fun List<List<Offset>>.effectiveHandles(row: Int, col: Int, handles: PointHandles): PointHandles =
    PointHandles(
        left = effectiveHandle(row, col, HandleSide.Left, handles),
        top = effectiveHandle(row, col, HandleSide.Top, handles),
        right = effectiveHandle(row, col, HandleSide.Right, handles),
        bottom = effectiveHandle(row, col, HandleSide.Bottom, handles),
    )

private fun Offset.normalized(): Offset {
    val distance = getDistance()
    return if (distance == 0f) Offset.Zero else Offset(x / distance, y / distance)
}
