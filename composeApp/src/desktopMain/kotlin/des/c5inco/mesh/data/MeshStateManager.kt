package des.c5inco.mesh.data

import data.MeshState
import kotlinx.serialization.json.Json
import java.io.File

/** Reads the canvas settings of the single mesh Mesh saved before it had documents. */
object MeshStateManager {
    private val meshStateFile get() = File(meshDataDirectory(), "mesh1.json")
    // Older saved settings may include the custom renderer's resolution.
    private val json = Json { ignoreUnknownKeys = true }

    fun hasSavedState(): Boolean = meshStateFile.exists()

    fun loadState(): MeshState {
        return try {
            val jsonString = meshStateFile.readText()
            println("Loaded meshState: $jsonString")
            json.decodeFromString(MeshState.serializer(), jsonString)
        } catch (e: Exception) {
            // Handle file not found or invalid JSON
            println("Error loading meshState: ${e.message}")
            MeshState() // Return default config if loading fails
        }
    }
}
