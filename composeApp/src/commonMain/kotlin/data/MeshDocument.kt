package data

import kotlinx.serialization.Serializable
import model.MeshPoint

@Serializable
data class MeshDocument(
    val name: String = "Untitled",
    val meshState: MeshState = MeshState(),
    val meshPoints: List<MeshPoint> = emptyList(),
    val canvasBackgroundColor: Long = -1L,
)
