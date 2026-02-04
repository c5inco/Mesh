package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshStateTest {

    @Test
    fun `test MeshState creation with default values`() {
        val meshState = MeshState()
        
        assertEquals(DimensionMode.Fill, meshState.canvasWidthMode)
        assertEquals(0, meshState.canvasWidth)
        assertEquals(DimensionMode.Fill, meshState.canvasHeightMode)
        assertEquals(0, meshState.canvasHeight)
        assertEquals(10, meshState.resolution)
        assertEquals(0f, meshState.blurLevel)
        assertEquals(3, meshState.rows)
        assertEquals(4, meshState.cols)
    }

    @Test
    fun `test MeshState creation with custom values`() {
        val meshState = MeshState(
            canvasWidthMode = DimensionMode.Fixed,
            canvasWidth = 800,
            canvasHeightMode = DimensionMode.Fixed,
            canvasHeight = 600,
            resolution = 20,
            blurLevel = 5f,
            rows = 5,
            cols = 6
        )
        
        assertEquals(DimensionMode.Fixed, meshState.canvasWidthMode)
        assertEquals(800, meshState.canvasWidth)
        assertEquals(DimensionMode.Fixed, meshState.canvasHeightMode)
        assertEquals(600, meshState.canvasHeight)
        assertEquals(20, meshState.resolution)
        assertEquals(5f, meshState.blurLevel)
        assertEquals(5, meshState.rows)
        assertEquals(6, meshState.cols)
    }

    @Test
    fun `test MeshState copy with canvasWidthMode change`() {
        val original = MeshState()
        val modified = original.copy(canvasWidthMode = DimensionMode.Fixed)
        
        assertEquals(DimensionMode.Fixed, modified.canvasWidthMode)
        assertEquals(original.canvasWidth, modified.canvasWidth)
        assertEquals(original.canvasHeightMode, modified.canvasHeightMode)
    }

    @Test
    fun `test MeshState copy with canvasWidth change`() {
        val original = MeshState()
        val modified = original.copy(canvasWidth = 1024)
        
        assertEquals(1024, modified.canvasWidth)
        assertEquals(original.canvasWidthMode, modified.canvasWidthMode)
    }

    @Test
    fun `test MeshState copy with canvasHeight change`() {
        val original = MeshState()
        val modified = original.copy(canvasHeight = 768)
        
        assertEquals(768, modified.canvasHeight)
        assertEquals(original.canvasHeightMode, modified.canvasHeightMode)
    }

    @Test
    fun `test MeshState copy with resolution change`() {
        val original = MeshState()
        val modified = original.copy(resolution = 15)
        
        assertEquals(15, modified.resolution)
    }

    @Test
    fun `test MeshState copy with blurLevel change`() {
        val original = MeshState()
        val modified = original.copy(blurLevel = 10f)
        
        assertEquals(10f, modified.blurLevel)
    }

    @Test
    fun `test MeshState copy with rows change`() {
        val original = MeshState()
        val modified = original.copy(rows = 7)
        
        assertEquals(7, modified.rows)
        assertEquals(original.cols, modified.cols)
    }

    @Test
    fun `test MeshState copy with cols change`() {
        val original = MeshState()
        val modified = original.copy(cols = 8)
        
        assertEquals(8, modified.cols)
        assertEquals(original.rows, modified.rows)
    }

    @Test
    fun `test MeshState copy with multiple changes`() {
        val original = MeshState()
        val modified = original.copy(
            canvasWidthMode = DimensionMode.Fixed,
            canvasWidth = 1920,
            canvasHeightMode = DimensionMode.Fixed,
            canvasHeight = 1080,
            resolution = 25,
            blurLevel = 15f,
            rows = 10,
            cols = 12
        )
        
        assertEquals(DimensionMode.Fixed, modified.canvasWidthMode)
        assertEquals(1920, modified.canvasWidth)
        assertEquals(DimensionMode.Fixed, modified.canvasHeightMode)
        assertEquals(1080, modified.canvasHeight)
        assertEquals(25, modified.resolution)
        assertEquals(15f, modified.blurLevel)
        assertEquals(10, modified.rows)
        assertEquals(12, modified.cols)
    }

    @Test
    fun `test MeshState equality`() {
        val state1 = MeshState(
            canvasWidth = 800,
            canvasHeight = 600,
            resolution = 10
        )
        val state2 = MeshState(
            canvasWidth = 800,
            canvasHeight = 600,
            resolution = 10
        )
        
        assertEquals(state1, state2)
    }

    @Test
    fun `test MeshState inequality`() {
        val state1 = MeshState(canvasWidth = 800)
        val state2 = MeshState(canvasWidth = 600)
        
        assertFalse(state1 == state2)
    }

    @Test
    fun `test DimensionMode enum values`() {
        assertEquals(2, DimensionMode.entries.size)
        assertTrue(DimensionMode.entries.contains(DimensionMode.Fixed))
        assertTrue(DimensionMode.entries.contains(DimensionMode.Fill))
    }

    @Test
    fun `test MeshState with minimum values`() {
        val meshState = MeshState(
            canvasWidth = 0,
            canvasHeight = 0,
            resolution = 0,
            blurLevel = 0f,
            rows = 0,
            cols = 0
        )
        
        assertEquals(0, meshState.canvasWidth)
        assertEquals(0, meshState.canvasHeight)
        assertEquals(0, meshState.resolution)
        assertEquals(0f, meshState.blurLevel)
        assertEquals(0, meshState.rows)
        assertEquals(0, meshState.cols)
    }

    @Test
    fun `test MeshState with large values`() {
        val meshState = MeshState(
            canvasWidth = 10000,
            canvasHeight = 10000,
            resolution = 100,
            blurLevel = 100f,
            rows = 100,
            cols = 100
        )
        
        assertEquals(10000, meshState.canvasWidth)
        assertEquals(10000, meshState.canvasHeight)
        assertEquals(100, meshState.resolution)
        assertEquals(100f, meshState.blurLevel)
        assertEquals(100, meshState.rows)
        assertEquals(100, meshState.cols)
    }

    @Test
    fun `test MeshState serialization compatible structure`() {
        // Test that MeshState can be created with all required fields
        val meshState = MeshState(
            canvasWidthMode = DimensionMode.Fill,
            canvasWidth = 0,
            canvasHeightMode = DimensionMode.Fill,
            canvasHeight = 0,
            resolution = 10,
            blurLevel = 0f,
            rows = 3,
            cols = 4
        )
        
        // Verify all fields are accessible
        assertEquals(DimensionMode.Fill, meshState.canvasWidthMode)
        assertEquals(0, meshState.canvasWidth)
        assertEquals(DimensionMode.Fill, meshState.canvasHeightMode)
        assertEquals(0, meshState.canvasHeight)
        assertEquals(10, meshState.resolution)
        assertEquals(0f, meshState.blurLevel)
        assertEquals(3, meshState.rows)
        assertEquals(4, meshState.cols)
    }
}
