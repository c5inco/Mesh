package des.c5inco.mesh.data

import data.DataRepository
import data.MeshState
import data.defaultPresetColors
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

class AppDataRepository : DataRepository {
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

    override fun getAllColors(): Flow<List<SavedColor>> {
        return database.savedColorDao().getAll()
    }

    override fun getPresetColors(): Flow<List<SavedColor>> {
        return database.savedColorDao().getAllPresets()
    }

    override fun getCustomColors(): Flow<List<SavedColor>> {
        return database.savedColorDao().getAllCustom()
    }

    override fun addColor(color: SavedColor) {
        scope.launch {
            database.savedColorDao().insertAll(color)
        }
    }

    override fun deleteColor(color: SavedColor) {
        scope.launch {
            database.savedColorDao().delete(color)
        }
    }

    override fun loadMeshState(): MeshState {
        return MeshStateManager.loadState()
    }

    override fun saveMeshState(state: MeshState) {
        MeshStateManager.saveState(state)
    }

    override fun getMeshPoints(): Flow<List<MeshPoint>> {
        return database.meshPointDao().getAll()
    }

    override suspend fun saveMeshPoints(points: List<MeshPoint>) {
        database.meshPointDao().replaceAll(points)
    }
}