package data

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow
import model.MeshPoint
import model.SavedColor

val defaultPresetColors = listOf(
    Color(0xff7766EE),
    Color(0xff8899ff),
    Color(0xff429BED),
    Color(0xff4FC1A6),
    Color(0xffF0C03E),
    Color(0xffff5599),
    Color(0xFFFF00FF),
)

interface DataRepository {
    fun getAllColors(): Flow<List<SavedColor>>
    fun getPresetColors(): Flow<List<SavedColor>>
    fun getCustomColors(): Flow<List<SavedColor>>
    /** Returns the new color's ID. */
    suspend fun addColor(color: SavedColor): Long
    fun deleteColor(color: SavedColor)
    fun loadMeshState(): MeshState
    fun saveMeshState(state: MeshState)
    fun getMeshPoints(): Flow<List<MeshPoint>>
    suspend fun saveMeshPoints(points: List<MeshPoint>)
}