package data

import model.MeshPoint
import model.SavedColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LegacyMeshDocumentTest {
    private val presets = List(3) { SavedColor(uid = it + 1L, red = 40 * (it + 1), green = 0, blue = 0, preset = true) }
    private val custom = SavedColor(uid = 8L, red = 204, green = 204, blue = 204)

    @Test
    fun `the saved points win over a saved grid size that doesn't match them, keeping their colors`() {
        // The settings file says 3×4 (the default), but the points table holds a 2×2 grid.
        val twoByTwo = listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1).map { (row, col) ->
            MeshPoint(row = row, col = col, x = col.toFloat(), y = row.toFloat(), savedColorId = if (row == 1 && col == 1) 8L else 2L)
        }

        val document = legacyMeshDocument(twoByTwo, MeshState(), presets + custom)!!

        assertEquals(2 to 2, document.meshState.let { it.rows to it.cols })
        assertEquals(listOf(DocumentColor(204, 204, 204)), document.customColors)
        assertEquals(DocumentPoint(1f, 1f, DocumentColor(204, 204, 204)), document.points[1][1])
        assertEquals(DocumentColor(80, 0, 0), document.points[0][0].color)
    }

    @Test
    fun `a saved grid size survives a missing points table`() {
        val document = legacyMeshDocument(emptyList(), MeshState(rows = 5, cols = 6), presets)!!

        assertEquals(List(5) { 6 }, document.points.map { it.size })
        assertEquals(5 to 6, document.meshState.let { it.rows to it.cols })
    }

    @Test
    fun `stray points past the old 10 by 10 limit or at a negative index are dropped`() {
        val points = (-1..11).flatMap { row ->
            (0..12).map { col -> MeshPoint(row = row, col = col, x = col / 12f, y = row / 12f, savedColorId = 2L) }
        }

        val document = legacyMeshDocument(points, MeshState(), presets)!!

        assertEquals(List(10) { 10 }, document.points.map { it.size })
        assertEquals(10 to 10, document.meshState.let { it.rows to it.cols })
        assertEquals(DocumentPoint(9 / 12f, 9 / 12f, DocumentColor(80, 0, 0)), document.points[9][9])
    }

    @Test
    fun `points that leave no 2 by 2 grid fall back to a fresh grid at the saved size`() {
        val points = listOf(MeshPoint(row = 0, col = 0, x = 0f, y = 0f, savedColorId = 2L), MeshPoint(row = -3, col = 40, x = 0f, y = 0f, savedColorId = 2L))

        val document = legacyMeshDocument(points, MeshState(rows = 5, cols = 6), presets)!!

        assertEquals(List(5) { 6 }, document.points.map { it.size })
    }

    @Test
    fun `nothing saved means nothing to migrate`() {
        assertNull(legacyMeshDocument(emptyList(), savedState = null, colors = presets + custom))
    }
}
