package des.c5inco.mesh.data

import androidx.compose.ui.graphics.Color
import data.MeshState
import data.getRoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import model.MeshPoint
import model.SavedColor
import model.toSavedColor

val defaultPresetColors = listOf(
    Color(0xff7766EE),
    Color(0xff8899ff),
    Color(0xff429BED),
    Color(0xff4FC1A6),
    Color(0xffF0C03E),
    Color(0xffff5599),
    Color(0xFFFF00FF),
)

class AppDataRepository {
    private val database = getRoomDatabase(getDatabaseBuilder())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            val allColors = database.savedColorDao().getAll().first()
            if (allColors.isEmpty()) {
                database.savedColorDao().insertAll(
                    *defaultPresetColors.mapIndexed { index, color ->
                        color.toSavedColor(preset = true)
                    }.toTypedArray()
                )
            }
        }
    }

    fun getAllColors(): Flow<List<SavedColor>> {
        return database.savedColorDao().getAll()
    }

    fun getPresetColors(): Flow<List<SavedColor>> {
        return database.savedColorDao().getAllPresets()
    }

    fun getCustomColors(): Flow<List<SavedColor>> {
        return database.savedColorDao().getAllCustom()
    }

    fun addColor(color: SavedColor) {
        scope.launch {
            database.savedColorDao().insertAll(color)
        }
    }

    fun deleteColor(color: SavedColor) {
        scope.launch {
            database.savedColorDao().delete(color)
        }
    }

    fun loadMeshState(): MeshState {
        return MeshStateManager.loadState()
    }

    fun saveMeshState(state: MeshState) {
        MeshStateManager.saveState(state)
    }

    fun getMeshPoints(): Flow<List<MeshPoint>> {
        return database.meshPointDao().getAll()
    }

    fun saveMeshPoints(points: List<MeshPoint>) {
        scope.launch {
            database.meshPointDao().deleteAll()
            database.meshPointDao().insertAll(*points.toTypedArray())
        }
    }
}