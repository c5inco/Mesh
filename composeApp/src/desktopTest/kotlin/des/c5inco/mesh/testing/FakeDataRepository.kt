package des.c5inco.mesh.testing

import data.DataRepository
import data.MeshDocument
import data.MeshState
import data.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import model.SavedColor
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration

/** Three distinct presets with IDs 1 to 3, like the first three of the app's. */
val fakePresetColors = List(3) { SavedColor(uid = it + 1L, red = 40 * (it + 1), green = 0, blue = 0, preset = true) }

class FakeDataRepository(
    // Like the app's presets, the palette is never empty.
    initialColors: List<SavedColor> = fakePresetColors,
    /** Replaces [initialColors], such as to hold the load back until a test is ready. */
    private val presetColors: Flow<List<SavedColor>> = flowOf(initialColors),
    override val documentsDirectory: File = Files.createTempDirectory("mesh-documents-").toFile(),
    /** Null for the first launch after upgrading, which migrates [legacyMesh]. */
    private val session: Session? = Session(),
    private val legacyMeshState: MeshState = MeshState(),
    private val legacyMesh: MeshDocument? = null,
    /** How long each session write takes, like a slow disk. */
    private val sessionWriteDelay: Duration = Duration.ZERO,
) : DataRepository {
    /** The last session the app recorded; it records one each time a document opens or is saved. */
    val savedSession = MutableStateFlow<Session?>(null)

    override fun getPresetColors(): Flow<List<SavedColor>> = presetColors

    override fun loadSession(): Session? = session

    override fun saveSession(session: Session) {
        Thread.sleep(sessionWriteDelay.inWholeMilliseconds)
        savedSession.value = session
    }

    override fun loadLegacyMeshState(): MeshState = legacyMeshState

    override suspend fun loadLegacyMesh(): MeshDocument? = legacyMesh
}
