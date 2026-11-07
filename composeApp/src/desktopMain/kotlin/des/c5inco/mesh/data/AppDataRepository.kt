package des.c5inco.mesh.data

import data.DataRepository
import data.MeshDocument
import data.MeshState
import data.Session
import data.defaultPresetColors
import data.getRoomDatabase
import data.legacyMeshDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import model.SavedColor
import model.toSavedColor
import java.io.File

class AppDataRepository : DataRepository {
    private val database = getRoomDatabase(getDatabaseBuilder())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionFile = File(meshDataDirectory(), "session.json")

    override val documentsDirectory = File(meshDataDirectory(), "documents")

    init {
        scope.launch {
            val allColors = database.savedColorDao().getAll().first()
            if (allColors.isEmpty()) {
                database.savedColorDao().insertAll(
                    *defaultPresetColors.map { color ->
                        color.toSavedColor(preset = true)
                    }.toTypedArray()
                )
            }
        }
    }

    override fun getPresetColors(): Flow<List<SavedColor>> {
        return database.savedColorDao().getAllPresets()
    }

    override fun loadSession(): Session? {
        if (!sessionFile.exists()) return null
        return try {
            Json.decodeFromString(Session.serializer(), sessionFile.readText())
        } catch (e: Exception) {
            println("Error loading session: ${e.message}")
            Session()
        }
    }

    override fun saveSession(session: Session) {
        sessionFile.parentFile.mkdirs()
        sessionFile.writeText(Json.encodeToString(Session.serializer(), session))
    }

    override fun loadLegacyMeshState(): MeshState = MeshStateManager.loadState()

    override suspend fun loadLegacyMesh(): MeshDocument? {
        val points = database.meshPointDao().getAll().first()
        if (points.isEmpty() && !MeshStateManager.hasSavedState()) return null
        // The old app kept its custom colors in the database with the presets, which it seeded on first launch.
        val colors = database.savedColorDao().getAll().first { it.isNotEmpty() }
        return legacyMeshDocument(
            points = points,
            savedState = MeshStateManager.loadState().takeIf { MeshStateManager.hasSavedState() },
            colors = colors,
        )
    }
}

fun meshDataDirectory() = File(System.getProperty("user.home"), ".mesh")
