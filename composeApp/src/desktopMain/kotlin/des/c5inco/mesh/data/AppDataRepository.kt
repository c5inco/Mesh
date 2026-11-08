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
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AppDataRepository : DataRepository {
    private val database = getRoomDatabase(getDatabaseBuilder())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionFile = File(meshDataDirectory(), "session.json")
    // A later version of Mesh may record more in the session.
    private val sessionJson = Json { ignoreUnknownKeys = true }

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
            sessionJson.decodeFromString(Session.serializer(), sessionFile.readText())
        } catch (e: Exception) {
            // Without a session there is no telling whether the old mesh was migrated, so it may be migrated again.
            println("Error loading session: ${e.message}")
            null
        }
    }

    override fun saveSession(session: Session) {
        val directory = sessionFile.parentFile
        directory.mkdirs()
        // Written through a temporary file, so a crash mid-write can't leave a broken session.
        val temporaryFile = File(directory, ".${sessionFile.name}.tmp")
        temporaryFile.writeText(sessionJson.encodeToString(Session.serializer(), session))
        Files.move(
            temporaryFile.toPath(),
            sessionFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
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
