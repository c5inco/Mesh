package model

import androidx.compose.ui.geometry.Offset
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity
data class MeshPoint(
    @PrimaryKey(true) val uid: Long = 0,
    val row: Int,
    val col: Int,
    val x: Float,
    val y: Float,
    val savedColorId: Long,
)

fun Pair<Offset, Long>.toMeshPoint(row: Int, col: Int): MeshPoint {
    return MeshPoint(
        row = row,
        col = col,
        x = first.x,
        y = first.y,
        savedColorId = second
    )
}

fun List<MeshPoint>.toOffsetGrid(): List<List<Pair<Offset, Long>>> {
    if (isEmpty()) return emptyList()

    // Determine the dimensions of the grid.
    val maxRow = maxOf { it.row }
    val maxCol = maxOf { it.col }

    // Initialize an empty grid.
    val grid = MutableList(maxRow + 1) { MutableList(maxCol + 1) { Pair(Offset.Zero, 0L) } }

    // Populate the grid with the MeshPoint data.
    this.forEach { meshPoint ->
        grid[meshPoint.row][meshPoint.col] = Pair(Offset(meshPoint.x, meshPoint.y), meshPoint.savedColorId)
    }

    // Convert the mutable lists to immutable lists.
    return grid.map { it.toList() }.toList()
}

fun List<List<Pair<Offset, Long>>>.toSavedMeshPoints(): List<MeshPoint> {
    val meshPoints = mutableListOf<MeshPoint>()
    this.forEachIndexed { rowIndex, row ->
        row.forEachIndexed { colIndex, pair ->
            meshPoints.add(
                MeshPoint(
                    row = rowIndex,
                    col = colIndex,
                    x = pair.first.x,
                    y = pair.first.y,
                    savedColorId = pair.second
                )
            )
        }
    }
    return meshPoints
}

fun generateMeshPointGrid(
    rows: Int,
    cols: Int,
    colorIds: List<Long>,
): List<List<Pair<Offset, Long>>> {
    require(rows >= 2) { "A mesh requires at least two rows" }
    require(cols >= 2) { "A mesh requires at least two columns" }
    require(colorIds.isNotEmpty()) { "A mesh requires at least one color" }

    return List(rows) { row ->
        val y = row.toFloat() / (rows - 1)
        val colorId = colorIds[row % colorIds.size]

        List(cols) { col ->
            val x = col.toFloat() / (cols - 1)
            Offset(x, y) to colorId
        }
    }
}

fun List<List<Pair<Offset, Long>>>.withUpdatedMeshPoint(
    row: Int,
    col: Int,
    point: Pair<Offset, Long>,
    constrainEdges: Boolean,
): List<List<Pair<Offset, Long>>> {
    val updatedRow = this[row].toMutableList()
    val offset = if (constrainEdges) {
        Offset(
            x = when (col) {
                0 -> 0f
                updatedRow.lastIndex -> 1f
                else -> point.first.x
            },
            y = when (row) {
                0 -> 0f
                lastIndex -> 1f
                else -> point.first.y
            },
        )
    } else {
        point.first
    }

    updatedRow[col] = offset to point.second
    return mapIndexed { index, existingRow ->
        if (index == row) updatedRow.toList() else existingRow
    }
}

fun List<List<Pair<Offset, Long>>>.distributedEvenly(
    rows: Int,
    cols: Int,
): List<List<Pair<Offset, Long>>> =
    mapIndexed { row, points ->
        val y = row.toFloat() / (rows - 1)
        List(cols) { col ->
            val x = col.toFloat() / (cols - 1)
            Offset(x, y) to points[col].second
        }
    }

fun List<List<Pair<Offset, Long>>>.replaceColor(
    colorId: Long,
    replacementColorId: Long,
): List<List<Pair<Offset, Long>>> =
    map { row ->
        row.map { point ->
            if (point.second == colorId) point.first to replacementColorId else point
        }
    }
