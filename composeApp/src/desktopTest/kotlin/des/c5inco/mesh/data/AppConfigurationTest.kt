package des.c5inco.mesh.data

import des.c5inco.mesh.testing.FakeDataRepository
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
}
