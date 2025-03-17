package des.c5inco.mesh.data

import data.MeshState
import kotlinx.serialization.json.Json
import java.io.File

object MeshStateManager {
    private val meshStateFile = File(System.getProperty("user.home") + File.separator +
            ".mesh" + File.separator + "mesh1.json")

    fun saveState(config: MeshState) {
        val jsonString = Json.encodeToString(MeshState.serializer(), config)
        println("Saved meshState: $jsonString")
        meshStateFile.writeText(jsonString)
    }

    fun loadState(): MeshState {
        return try {
            val jsonString = meshStateFile.readText()
            println("Loaded meshState: $jsonString")
            Json.decodeFromString(MeshState.serializer(), jsonString)
        } catch (e: Exception) {
            // Handle file not found or invalid JSON
            println("Error loading meshState: ${e.message}")
            MeshState() // Return default config if loading fails
        }
    }
}