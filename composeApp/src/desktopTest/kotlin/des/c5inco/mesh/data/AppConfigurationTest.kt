package des.c5inco.mesh.data

import data.DataRepository
import data.MeshState
import des.c5inco.mesh.testing.waitForClipboardText
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import model.MeshPoint
import model.SavedColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppConfigurationTest {
    @Test
    fun `code export includes the current rounded blur and a usable paint modifier`() = runBlocking {
        val repository = FakeDataRepository(
            meshPoints = flowOf(emptyList()),
            initialColors = (1L..3L).map { SavedColor(uid = it, red = (it * 70).toInt(), green = 150, blue = 200) },
            onSaveMeshPoints = {},
        )
        val configuration = AppConfiguration(repository)
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard

        for ((level, radius) in listOf(0f to 0, .012f to 0, .0125f to 1, .513f to 21, 1f to 40)) {
            configuration.updateBlurLevel(level)
            clipboard.setContents(StringSelection(""), null)
            configuration.exportMeshPointsAsCode()
            val code = waitForClipboardText("mesh modifier at blur $level") {
                it.contains("val meshGradientModifier")
            }

            assertTrue(code.contains("import androidx.compose.ui.draw.paint"))
            assertTrue(code.contains("hasBicubicColor = true"))
            assertTrue(code.contains(".paint(meshGradient, sizeToIntrinsics = false)"))
            if (radius == 0) {
                assertFalse(code.contains("BlurEffect"), "A zero-radius blur must not be created")
                assertFalse(code.contains("graphicsLayer"), "An unblurred export needs no effect layer")
            } else {
                assertTrue(code.contains("import androidx.compose.ui.graphics.graphicsLayer"))
                assertTrue(code.contains("renderEffect = BlurEffect($radius.0f, $radius.0f, TileMode.Clamp)"))
                assertTrue(code.indexOf(".graphicsLayer") < code.indexOf(".paint("), "Blur must wrap the painted mesh")
            }
        }
    }

    @Test
    fun `saving waits for initial mesh loading and preserves the default grid`() = runBlocking {
        val loadGate = CompletableDeferred<Unit>()
        val savedPoints = AtomicReference<List<MeshPoint>>()
        val repository = FakeDataRepository(
            meshPoints = flow {
                loadGate.await()
                emit(emptyList())
            },
            onSaveMeshPoints = savedPoints::set,
        )
        val configuration = AppConfiguration(repository)

        val save = async { configuration.saveMeshPoints() }
        assertFalse(save.isCompleted)

        loadGate.complete(Unit)
        save.await()

        assertEquals(12, savedPoints.get().size)
        assertEquals(1L, savedPoints.get().first().savedColorId)
        assertEquals(3L, savedPoints.get().last().savedColorId)
    }

    @Test
    fun `regeneration replaces rather than appends to a delayed initial load`() = runBlocking {
        val loadGate = CompletableDeferred<Unit>()
        val savedPoints = AtomicReference<List<MeshPoint>>()
        val repository = FakeDataRepository(
            meshPoints = flow {
                loadGate.await()
                emit(emptyList())
            },
            initialColors = listOf(SavedColor(uid = 42L, red = 1, green = 2, blue = 3)),
            onSaveMeshPoints = savedPoints::set,
        )
        val configuration = AppConfiguration(repository)

        configuration.updateTotalRows(4)
        loadGate.complete(Unit)
        configuration.saveMeshPoints()

        assertEquals(4, configuration.meshPoints.size)
        assertEquals(16, savedPoints.get().size)
        assertEquals(3, savedPoints.get().maxOf(MeshPoint::row))
    }

    @Test
    fun `failed initial loading prevents an empty save`() {
        val savedPoints = AtomicReference<List<MeshPoint>>()
        val repository = FakeDataRepository(
            meshPoints = flow { throw IllegalStateException("load failed") },
            onSaveMeshPoints = savedPoints::set,
        )
        val configuration = AppConfiguration(repository)

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { configuration.saveMeshPoints() }
        }

        assertEquals("load failed", error.message)
        assertNull(savedPoints.get())
    }

    @Test
    fun `saving waits for pending regeneration`() = runBlocking {
        val savedPoints = AtomicReference<List<MeshPoint>>()
        val repository = FakeDataRepository(
            meshPoints = flowOf(emptyList()),
            onSaveMeshPoints = savedPoints::set,
        )
        val configuration = AppConfiguration(repository)
        configuration.saveMeshPoints()
        savedPoints.set(null)

        configuration.updateTotalRows(4)
        val save = async { configuration.saveMeshPoints() }
        yield()
        assertFalse(save.isCompleted)
        assertNull(savedPoints.get())

        repository.setColors(listOf(SavedColor(uid = 42L, red = 1, green = 2, blue = 3)))
        save.await()

        assertEquals(16, savedPoints.get().size)
        assertEquals(3, savedPoints.get().maxOf(MeshPoint::row))
    }

    @Test
    fun `rapid dimension updates save only the latest requested grid`() = runBlocking {
        val savedPoints = AtomicReference<List<MeshPoint>>()
        val repository = FakeDataRepository(
            meshPoints = flowOf(emptyList()),
            onSaveMeshPoints = savedPoints::set,
        )
        val configuration = AppConfiguration(repository)
        configuration.saveMeshPoints()
        savedPoints.set(null)

        configuration.updateTotalRows(4)
        configuration.updateTotalCols(5)
        repository.setColors(listOf(SavedColor(uid = 42L, red = 1, green = 2, blue = 3)))
        configuration.saveMeshPoints()

        assertEquals(20, savedPoints.get().size)
        assertEquals(3, savedPoints.get().maxOf(MeshPoint::row))
        assertEquals(4, savedPoints.get().maxOf(MeshPoint::col))
        assertEquals(4, configuration.meshPoints.size)
        assertEquals(List(4) { 5 }, configuration.meshPoints.map { it.size })
    }
}

private class FakeDataRepository(
    private val meshPoints: Flow<List<MeshPoint>>,
    initialColors: List<SavedColor> = emptyList(),
    private val onSaveMeshPoints: (List<MeshPoint>) -> Unit,
) : DataRepository {
    private val colors = MutableStateFlow(initialColors)

    override fun getAllColors(): Flow<List<SavedColor>> = colors

    override fun getPresetColors(): Flow<List<SavedColor>> = colors

    override fun getCustomColors(): Flow<List<SavedColor>> = colors

    fun setColors(value: List<SavedColor>) {
        colors.value = value
    }

    override fun addColor(color: SavedColor) = Unit

    override fun deleteColor(color: SavedColor) = Unit

    override fun loadMeshState(): MeshState = MeshState()

    override fun saveMeshState(state: MeshState) = Unit

    override fun getMeshPoints(): Flow<List<MeshPoint>> = meshPoints

    override suspend fun saveMeshPoints(points: List<MeshPoint>) = onSaveMeshPoints(points)
}
