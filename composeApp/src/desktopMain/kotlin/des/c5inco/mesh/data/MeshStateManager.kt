package des.c5inco.mesh.data

import data.MeshState
import kotlinx.serialization.json.Json
import java.io.File

object MeshStateManager {
    private val meshStateFile = File(System.getProperty("user.home") + File.separator +
            ".mesh" + File.separator + "mesh1.json")
    // Older saved settings may include the custom renderer's resolution.
    private val json = Json { ignoreUnknownKeys = true }

    fun saveState(config: MeshState) {
        val jsonString = json.encodeToString(MeshState.serializer(), config)
        println("Saved meshState: $jsonString")
        meshStateFile.writeText(jsonString)
    }

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
