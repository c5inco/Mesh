package data

import kotlinx.serialization.Serializable

/** The fewest and most rows or columns a mesh can have. More points cost more to draw and to edit. */
const val MIN_GRID_SIZE = 2
const val MAX_GRID_SIZE = 64

enum class DimensionMode {
    Fixed,
    Fill
}

@Serializable
data class MeshState(
    val canvasWidthMode: DimensionMode = DimensionMode.Fill,
    val canvasWidth: Int = 0,
    val canvasHeightMode: DimensionMode = DimensionMode.Fill,
    val canvasHeight: Int = 0,
    val blurLevel: Float = 0f,
    val rows: Int = 3,
    val cols: Int = 4,
)
