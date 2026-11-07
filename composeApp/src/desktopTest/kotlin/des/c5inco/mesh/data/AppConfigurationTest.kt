package des.c5inco.mesh.data

import des.c5inco.mesh.testing.FakeDataRepository
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import androidx.compose.ui.geometry.Offset
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

    @Test
    fun `undo merges repeated edits to one point but not edits to another`() = runBlocking {
        val configuration = loadedConfiguration()
        val original = configuration.meshPoints.toList()

        configuration.updateMeshPoint(1, 1, Offset(0.2f, 0.5f) to 2L)
        configuration.updateMeshPoint(1, 1, Offset(0.25f, 0.55f) to 2L)
        configuration.updateMeshPoint(1, 2, Offset(0.6f, 0.3f) to 3L)

        configuration.undo()
        assertEquals(Offset(0.67f, 0.8f) to 2L, configuration.meshPoints[1][2])
        assertEquals(Offset(0.25f, 0.55f) to 2L, configuration.meshPoints[1][1])

        configuration.undo()
        assertEquals(original, configuration.meshPoints.toList())
        assertFalse(configuration.canUndo.value)

        configuration.redo()
        configuration.redo()
        assertEquals(Offset(0.25f, 0.55f) to 2L, configuration.meshPoints[1][1])
        assertEquals(Offset(0.6f, 0.3f) to 3L, configuration.meshPoints[1][2])
    }

    @Test
    fun `edits that change nothing add no undo step`() = runBlocking {
        val configuration = loadedConfiguration()

        configuration.updateMeshPoint(1, 1, configuration.meshPoints[1][1])
        configuration.updateBlurLevel(configuration.meshState.value.blurLevel)

        assertFalse(configuration.canUndo.value)
    }

    @Test
    fun `undoing a grid resize restores the previous grid and points`() = runBlocking {
        val configuration = loadedConfiguration()
        configuration.updateMeshPoint(1, 1, Offset(0.1f, 0.2f) to 3L)
        val beforeResize = configuration.meshPoints.toList()

        configuration.updateTotalRows(5)
        configuration.saveMeshPoints() // Waits for the new grid.
        assertEquals(5, configuration.meshPoints.size)

        configuration.undo()
        assertEquals(3, configuration.meshState.value.rows)
        assertEquals(beforeResize, configuration.meshPoints.toList())

        configuration.redo()
        assertEquals(5, configuration.meshState.value.rows)
        assertEquals(5, configuration.meshPoints.size)
    }

    @Test
    fun `undo keeps the grid size and points consistent while a regeneration is pending`() = runBlocking {
        val repository = FakeDataRepository(meshPoints = flowOf(emptyList()))
        val configuration = AppConfiguration(repository)
        configuration.saveMeshPoints() // Waits for the initial grid to load.

        // With no colors yet, regeneration waits, so the column change sees the old 3×4 points.
        configuration.updateTotalRows(5)
        configuration.updateTotalCols(6)
        repository.setColors(listOf(SavedColor(uid = 1L, red = 1, green = 2, blue = 3)))
        configuration.saveMeshPoints()

        configuration.undo()
        assertEquals(3 to 4, configuration.meshState.value.let { it.rows to it.cols })
        assertEquals(List(3) { 4 }, configuration.meshPoints.map { it.size })
    }

    @Test
    fun `quick resizes before the grid arrives make one undo step`() = runBlocking {
        val repository = FakeDataRepository(meshPoints = flowOf(emptyList()))
        val configuration = AppConfiguration(repository)
        configuration.saveMeshPoints() // Waits for the initial grid to load.

        configuration.updateTotalRows(4)
        configuration.updateTotalRows(5)
        repository.setColors(listOf(SavedColor(uid = 1L, red = 1, green = 2, blue = 3)))
        configuration.saveMeshPoints() // Waits for the new grid.
        assertEquals(5, configuration.meshPoints.size)

        configuration.undo()
        assertEquals(3, configuration.meshPoints.size)
        assertFalse(configuration.canUndo.value)

        configuration.redo()
        assertEquals(5, configuration.meshPoints.size)
        assertEquals(5, configuration.meshState.value.rows)
    }

    @Test
    fun `a regeneration that finishes after undo does not overwrite the undone state`() = runBlocking {
        val repository = FakeDataRepository(meshPoints = flowOf(emptyList()))
        val configuration = AppConfiguration(repository)
        configuration.saveMeshPoints()
        configuration.updateMeshPoint(1, 1, Offset(0.2f, 0.5f) to 3L)

        configuration.updateTotalRows(5) // Waits for colors.
        configuration.undo()
        repository.setColors(listOf(SavedColor(uid = 1L, red = 1, green = 2, blue = 3)))
        configuration.saveMeshPoints()

        assertEquals(3, configuration.meshPoints.size)
        assertEquals(3, configuration.meshState.value.rows)
        assertEquals(Offset(0.33f, 0.8f) to 2L, configuration.meshPoints[1][1])
        configuration.redo()
        assertEquals(Offset(0.2f, 0.5f) to 3L, configuration.meshPoints[1][1])
        assertEquals(3, configuration.meshPoints.size)
    }

    @Test
    fun `deleting the background color resets the background, also in history`() = runBlocking {
        val configuration = loadedConfiguration()
        configuration.updateCanvasBackgroundColor(2L)
        configuration.updateBlurLevel(0.5f)

        configuration.removeColorFromMeshPoints(2L)
        assertEquals(-1L, configuration.canvasBackgroundColor.value)

        configuration.undo()
        assertEquals(-1L, configuration.canvasBackgroundColor.value)
        configuration.redo()
        assertEquals(-1L, configuration.canvasBackgroundColor.value)
    }

    @Test
    fun `undo does not bring back a deleted palette color`() = runBlocking {
        val configuration = loadedConfiguration()
        configuration.updateMeshPoint(1, 1, Offset(0.2f, 0.5f) to 2L)

        configuration.removeColorFromMeshPoints(2L)
        configuration.undo()

        assertEquals(Offset(0.33f, 0.8f) to -1L, configuration.meshPoints[1][1])
        assertTrue(configuration.meshPoints.flatten().none { it.second == 2L })
        assertFalse(configuration.canUndo.value)
    }

    @Test
    fun `a new edit after undo clears redo`() = runBlocking {
        val configuration = loadedConfiguration()
        configuration.updateCanvasBackgroundColor(2L)
        configuration.undo()
        assertTrue(configuration.canRedo.value)

        configuration.updateBlurLevel(0.5f)

        assertFalse(configuration.canRedo.value)
        configuration.redo()
        assertEquals(-1L, configuration.canvasBackgroundColor.value)
    }

    private suspend fun loadedConfiguration() = AppConfiguration(
        FakeDataRepository(
            meshPoints = flowOf(emptyList()),
            initialColors = listOf(SavedColor(uid = 1L, red = 1, green = 2, blue = 3)),
        )
    ).also { it.saveMeshPoints() } // Waits for the initial grid to load.
}
