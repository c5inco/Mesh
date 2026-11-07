package data

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import model.SavedColor
import java.io.File

val defaultPresetColors = listOf(
    Color(0xff7766EE),
    Color(0xff8899ff),
    Color(0xff429BED),
    Color(0xff4FC1A6),
    Color(0xffF0C03E),
    Color(0xffff5599),
    Color(0xFFFF00FF),
)

/** What the app reopens at launch. */
@Serializable
data class Session(
    /** Path of the document that was open last, or null for an untitled one. */
    val lastDocument: String? = null,
)

interface DataRepository {
    /** Where Mesh keeps documents it creates itself, such as the one migrated from the single saved mesh. */
    val documentsDirectory: File

    fun getPresetColors(): Flow<List<SavedColor>>

    /** The last session, or null when this version of Mesh has never run, so the old mesh still needs migrating. */
    fun loadSession(): Session?
    fun saveSession(session: Session)

    /** The canvas settings of the mesh saved before documents existed, or the defaults. Reads only a small file. */
    fun loadLegacyMeshState(): MeshState

    /** The mesh and custom colors saved before documents existed, or null when there are none. */
    suspend fun loadLegacyMesh(): MeshDocument?
}
