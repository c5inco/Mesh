package model

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeshPointTest {

    @Test
    fun `test MeshPoint creation with default uid`() {
        val meshPoint = MeshPoint(
            row = 1,
            col = 2,
            x = 100f,
            y = 200f,
            savedColorId = 5L
        )
        
        assertEquals(0L, meshPoint.uid)
        assertEquals(1, meshPoint.row)
        assertEquals(2, meshPoint.col)
        assertEquals(100f, meshPoint.x)
        assertEquals(200f, meshPoint.y)
        assertEquals(5L, meshPoint.savedColorId)
    }

    @Test
    fun `test MeshPoint creation with custom uid`() {
        val meshPoint = MeshPoint(
            uid = 10L,
            row = 3,
            col = 4,
            x = 150f,
            y = 250f,
            savedColorId = 7L
        )
        
        assertEquals(10L, meshPoint.uid)
        assertEquals(3, meshPoint.row)
        assertEquals(4, meshPoint.col)
        assertEquals(150f, meshPoint.x)
        assertEquals(250f, meshPoint.y)
        assertEquals(7L, meshPoint.savedColorId)
    }

    @Test
    fun `test Pair toMeshPoint conversion`() {
        val offset = Offset(300f, 400f)
        val colorId = 12L
        val pair = Pair(offset, colorId)
        
        val meshPoint = pair.toMeshPoint(row = 2, col = 3)
        
        assertEquals(0L, meshPoint.uid)
        assertEquals(2, meshPoint.row)
        assertEquals(3, meshPoint.col)
        assertEquals(300f, meshPoint.x)
        assertEquals(400f, meshPoint.y)
        assertEquals(12L, meshPoint.savedColorId)
    }

    @Test
    fun `test empty list toOffsetGrid returns empty list`() {
        val emptyList = emptyList<MeshPoint>()
        val result = emptyList.toOffsetGrid()
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test single MeshPoint toOffsetGrid`() {
        val meshPoints = listOf(
            MeshPoint(row = 0, col = 0, x = 10f, y = 20f, savedColorId = 1L)
        )
        
        val grid = meshPoints.toOffsetGrid()
        
        assertEquals(1, grid.size)
        assertEquals(1, grid[0].size)
        assertEquals(Offset(10f, 20f), grid[0][0].first)
        assertEquals(1L, grid[0][0].second)
    }

    @Test
    fun `test 2x2 grid toOffsetGrid`() {
        val meshPoints = listOf(
            MeshPoint(row = 0, col = 0, x = 10f, y = 10f, savedColorId = 1L),
            MeshPoint(row = 0, col = 1, x = 20f, y = 10f, savedColorId = 2L),
            MeshPoint(row = 1, col = 0, x = 10f, y = 20f, savedColorId = 3L),
            MeshPoint(row = 1, col = 1, x = 20f, y = 20f, savedColorId = 4L)
        )
        
        val grid = meshPoints.toOffsetGrid()
        
        assertEquals(2, grid.size)
        assertEquals(2, grid[0].size)
        assertEquals(2, grid[1].size)
        
        assertEquals(Offset(10f, 10f), grid[0][0].first)
        assertEquals(1L, grid[0][0].second)
        assertEquals(Offset(20f, 10f), grid[0][1].first)
        assertEquals(2L, grid[0][1].second)
        assertEquals(Offset(10f, 20f), grid[1][0].first)
        assertEquals(3L, grid[1][0].second)
        assertEquals(Offset(20f, 20f), grid[1][1].first)
        assertEquals(4L, grid[1][1].second)
    }

    @Test
    fun `test 3x4 grid toOffsetGrid`() {
        val meshPoints = mutableListOf<MeshPoint>()
        var colorId = 1L
        
        for (row in 0..2) {
            for (col in 0..3) {
                meshPoints.add(
                    MeshPoint(
                        row = row,
                        col = col,
                        x = col * 50f,
                        y = row * 50f,
                        savedColorId = colorId++
                    )
                )
            }
        }
        
        val grid = meshPoints.toOffsetGrid()
        
        assertEquals(3, grid.size)
        assertEquals(4, grid[0].size)
        assertEquals(4, grid[1].size)
        assertEquals(4, grid[2].size)
    }

    @Test
    fun `test toSavedMeshPoints conversion`() {
        val grid = listOf(
            listOf(
                Pair(Offset(0f, 0f), 1L),
                Pair(Offset(10f, 0f), 2L)
            ),
            listOf(
                Pair(Offset(0f, 10f), 3L),
                Pair(Offset(10f, 10f), 4L)
            )
        )
        
        val meshPoints = grid.toSavedMeshPoints()
        
        assertEquals(4, meshPoints.size)
        
        assertEquals(0, meshPoints[0].row)
        assertEquals(0, meshPoints[0].col)
        assertEquals(0f, meshPoints[0].x)
        assertEquals(0f, meshPoints[0].y)
        assertEquals(1L, meshPoints[0].savedColorId)
        
        assertEquals(0, meshPoints[1].row)
        assertEquals(1, meshPoints[1].col)
        assertEquals(10f, meshPoints[1].x)
        assertEquals(0f, meshPoints[1].y)
        assertEquals(2L, meshPoints[1].savedColorId)
        
        assertEquals(1, meshPoints[2].row)
        assertEquals(0, meshPoints[2].col)
        assertEquals(0f, meshPoints[2].x)
        assertEquals(10f, meshPoints[2].y)
        assertEquals(3L, meshPoints[2].savedColorId)
        
        assertEquals(1, meshPoints[3].row)
        assertEquals(1, meshPoints[3].col)
        assertEquals(10f, meshPoints[3].x)
        assertEquals(10f, meshPoints[3].y)
        assertEquals(4L, meshPoints[3].savedColorId)
    }

    @Test
    fun `test round trip conversion from MeshPoints to grid and back`() {
        val originalMeshPoints = listOf(
            MeshPoint(row = 0, col = 0, x = 100f, y = 100f, savedColorId = 1L),
            MeshPoint(row = 0, col = 1, x = 200f, y = 100f, savedColorId = 2L),
            MeshPoint(row = 1, col = 0, x = 100f, y = 200f, savedColorId = 3L),
            MeshPoint(row = 1, col = 1, x = 200f, y = 200f, savedColorId = 4L)
        )
        
        val grid = originalMeshPoints.toOffsetGrid()
        val reconvertedMeshPoints = grid.toSavedMeshPoints()
        
        assertEquals(originalMeshPoints.size, reconvertedMeshPoints.size)
        
        originalMeshPoints.forEachIndexed { index, original ->
            val reconverted = reconvertedMeshPoints[index]
            assertEquals(original.row, reconverted.row)
            assertEquals(original.col, reconverted.col)
            assertEquals(original.x, reconverted.x)
            assertEquals(original.y, reconverted.y)
            assertEquals(original.savedColorId, reconverted.savedColorId)
        }
    }
}
