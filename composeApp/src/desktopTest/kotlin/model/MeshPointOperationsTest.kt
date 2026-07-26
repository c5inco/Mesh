package model

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MeshPointOperationsTest {
    @Test
    fun `generates an evenly spaced grid and cycles row colors`() {
        val grid = generateMeshPointGrid(
            rows = 3,
            cols = 4,
            colorIds = listOf(10L, 20L),
        )

        assertEquals(
            listOf(
                Offset(0f, 0f) to 10L,
                Offset(1f / 3f, 0f) to 10L,
                Offset(2f / 3f, 0f) to 10L,
                Offset(1f, 0f) to 10L,
            ),
            grid[0],
        )
        assertEquals(20L, grid[1][0].second)
        assertEquals(Offset(1f, 1f) to 10L, grid[2][3])
    }

    @Test
    fun `constrains edge coordinates while preserving the selected color`() {
        val grid = generateMeshPointGrid(3, 3, listOf(1L))

        val updated = grid.withUpdatedMeshPoint(
            row = 0,
            col = 2,
            point = Offset(.4f, .6f) to 9L,
            constrainEdges = true,
        )

        assertEquals(Offset(1f, 0f) to 9L, updated[0][2])
        assertEquals(grid[1], updated[1])
    }

    @Test
    fun `allows edge coordinates to move when constraints are disabled`() {
        val grid = generateMeshPointGrid(2, 2, listOf(1L))

        val updated = grid.withUpdatedMeshPoint(
            row = 0,
            col = 0,
            point = Offset(.25f, .75f) to 2L,
            constrainEdges = false,
        )

        assertEquals(Offset(.25f, .75f) to 2L, updated[0][0])
    }

    @Test
    fun `redistributes offsets without changing point colors`() {
        val grid = listOf(
            listOf(Offset(.2f, .3f) to 1L, Offset(.7f, .4f) to 2L),
            listOf(Offset(.1f, .8f) to 3L, Offset(.9f, .6f) to 4L),
        )

        val distributed = grid.distributedEvenly(rows = 2, cols = 2)

        assertEquals(
            listOf(
                listOf(Offset(0f, 0f) to 1L, Offset(1f, 0f) to 2L),
                listOf(Offset(0f, 1f) to 3L, Offset(1f, 1f) to 4L),
            ),
            distributed,
        )
    }

    @Test
    fun `replaces only matching color references`() {
        val grid = listOf(
            listOf(Offset.Zero to 1L, Offset(1f, 0f) to 2L),
            listOf(Offset(0f, 1f) to 1L, Offset(1f, 1f) to 3L),
        )

        val updated = grid.replaceColor(colorId = 1L, replacementColorId = -1L)

        assertEquals(-1L, updated[0][0].second)
        assertEquals(2L, updated[0][1].second)
        assertEquals(-1L, updated[1][0].second)
        assertEquals(3L, updated[1][1].second)
    }

    @Test
    fun `round trips grid coordinates and color ids through persistence models`() {
        val grid = generateMeshPointGrid(3, 4, listOf(7L, 8L, 9L))

        val restored = grid.toSavedMeshPoints().toOffsetGrid()

        assertEquals(grid, restored)
    }
}
