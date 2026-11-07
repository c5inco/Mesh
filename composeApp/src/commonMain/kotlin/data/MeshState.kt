package data

import kotlinx.serialization.Serializable

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
