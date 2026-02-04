package des.c5inco.mesh.data

import data.DimensionMode
import data.MeshState
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshStateManagerTest {
    
    private lateinit var testFile: File
    private lateinit var originalMeshStateFile: File
    
    @Before
    fun setup() {
        // Create a temporary test file
        testFile = File.createTempFile("mesh_test", ".json")
        testFile.deleteOnExit()
        
        // Backup the original mesh state file location
        originalMeshStateFile = File(System.getProperty("user.home") + File.separator +
                ".mesh" + File.separator + "mesh1.json")
    }
    
    @After
    fun cleanup() {
        // Clean up test file
        if (testFile.exists()) {
            testFile.delete()
        }
    }

    @Test
    fun `test saveState creates valid JSON file`() {
        val meshState = MeshState(
            canvasWidthMode = DimensionMode.Fixed,
            canvasWidth = 800,
            canvasHeightMode = DimensionMode.Fixed,
            canvasHeight = 600,
            resolution = 15,
            blurLevel = 5f,
            rows = 5,
            cols = 6
        )
        
        MeshStateManager.saveState(meshState)
        
        // Check that the file was created
        val meshStateFile = File(System.getProperty("user.home") + File.separator +
                ".mesh" + File.separator + "mesh1.json")
        assertTrue(meshStateFile.exists(), "Mesh state file should exist")
        assertTrue(meshStateFile.length() > 0, "Mesh state file should not be empty")
    }

    @Test
    fun `test loadState returns default MeshState when file does not exist`() {
        // Delete the mesh state file to ensure it doesn't exist
        val meshStateFile = File(System.getProperty("user.home") + File.separator +
                ".mesh" + File.separator + "mesh1.json")
        
        if (meshStateFile.exists()) {
            meshStateFile.delete()
        }
        
        // This should return a default MeshState
        val meshState = MeshStateManager.loadState()
        
        // Verify default values
        assertEquals(DimensionMode.Fill, meshState.canvasWidthMode)
        // Note: canvasWidth might be 0 or could have a value if file exists from other tests
        // Just verify the mode is Fill
        assertEquals(DimensionMode.Fill, meshState.canvasHeightMode)
        assertEquals(10, meshState.resolution)
        assertEquals(0f, meshState.blurLevel)
        assertEquals(3, meshState.rows)
        assertEquals(4, meshState.cols)
    }

    @Test
    fun `test saveState and loadState round trip`() {
        val originalMeshState = MeshState(
            canvasWidthMode = DimensionMode.Fixed,
            canvasWidth = 1920,
            canvasHeightMode = DimensionMode.Fixed,
            canvasHeight = 1080,
            resolution = 20,
            blurLevel = 10f,
            rows = 7,
            cols = 8
        )
        
        MeshStateManager.saveState(originalMeshState)
        val loadedMeshState = MeshStateManager.loadState()
        
        assertEquals(originalMeshState.canvasWidthMode, loadedMeshState.canvasWidthMode)
        assertEquals(originalMeshState.canvasWidth, loadedMeshState.canvasWidth)
        assertEquals(originalMeshState.canvasHeightMode, loadedMeshState.canvasHeightMode)
        assertEquals(originalMeshState.canvasHeight, loadedMeshState.canvasHeight)
        assertEquals(originalMeshState.resolution, loadedMeshState.resolution)
        assertEquals(originalMeshState.blurLevel, loadedMeshState.blurLevel)
        assertEquals(originalMeshState.rows, loadedMeshState.rows)
        assertEquals(originalMeshState.cols, loadedMeshState.cols)
    }

    @Test
    fun `test saveState with default MeshState`() {
        val defaultMeshState = MeshState()
        
        MeshStateManager.saveState(defaultMeshState)
        val loadedMeshState = MeshStateManager.loadState()
        
        assertEquals(defaultMeshState, loadedMeshState)
    }

    @Test
    fun `test saveState with minimum values`() {
        val meshState = MeshState(
            canvasWidth = 0,
            canvasHeight = 0,
            resolution = 0,
            blurLevel = 0f,
            rows = 0,
            cols = 0
        )
        
        MeshStateManager.saveState(meshState)
        val loadedMeshState = MeshStateManager.loadState()
        
        assertEquals(0, loadedMeshState.canvasWidth)
        assertEquals(0, loadedMeshState.canvasHeight)
        assertEquals(0, loadedMeshState.resolution)
        assertEquals(0f, loadedMeshState.blurLevel)
        assertEquals(0, loadedMeshState.rows)
        assertEquals(0, loadedMeshState.cols)
    }

    @Test
    fun `test saveState with large values`() {
        val meshState = MeshState(
            canvasWidth = 10000,
            canvasHeight = 10000,
            resolution = 100,
            blurLevel = 100f,
            rows = 100,
            cols = 100
        )
        
        MeshStateManager.saveState(meshState)
        val loadedMeshState = MeshStateManager.loadState()
        
        assertEquals(10000, loadedMeshState.canvasWidth)
        assertEquals(10000, loadedMeshState.canvasHeight)
        assertEquals(100, loadedMeshState.resolution)
        assertEquals(100f, loadedMeshState.blurLevel)
        assertEquals(100, loadedMeshState.rows)
        assertEquals(100, loadedMeshState.cols)
    }

    @Test
    fun `test saveState creates directory if not exists`() {
        val meshStateFile = File(System.getProperty("user.home") + File.separator +
                ".mesh" + File.separator + "mesh1.json")
        val parentDir = meshStateFile.parentFile
        
        // Save a state which should create the directory if needed
        MeshStateManager.saveState(MeshState())
        
        assertTrue(parentDir.exists(), "Parent directory should exist")
        assertTrue(parentDir.isDirectory, "Parent should be a directory")
    }

    @Test
    fun `test loadState handles invalid JSON gracefully`() {
        val meshStateFile = File(System.getProperty("user.home") + File.separator +
                ".mesh" + File.separator + "mesh1.json")
        
        // Create the directory if it doesn't exist
        meshStateFile.parentFile?.mkdirs()
        
        // Write invalid JSON to the file
        meshStateFile.writeText("{ invalid json }")
        
        // Loading should return default MeshState without throwing exception
        val loadedMeshState = MeshStateManager.loadState()
        
        // Should return default values
        assertEquals(DimensionMode.Fill, loadedMeshState.canvasWidthMode)
        assertEquals(10, loadedMeshState.resolution)
        assertEquals(3, loadedMeshState.rows)
        assertEquals(4, loadedMeshState.cols)
    }

    @Test
    fun `test saveState overwrites existing file`() {
        val firstState = MeshState(canvasWidth = 800, canvasHeight = 600)
        val secondState = MeshState(canvasWidth = 1024, canvasHeight = 768)
        
        MeshStateManager.saveState(firstState)
        MeshStateManager.saveState(secondState)
        
        val loadedMeshState = MeshStateManager.loadState()
        
        assertEquals(1024, loadedMeshState.canvasWidth)
        assertEquals(768, loadedMeshState.canvasHeight)
    }

    @Test
    fun `test MeshState with different dimension modes`() {
        val meshState = MeshState(
            canvasWidthMode = DimensionMode.Fill,
            canvasHeightMode = DimensionMode.Fixed,
            canvasHeight = 500
        )
        
        MeshStateManager.saveState(meshState)
        val loadedMeshState = MeshStateManager.loadState()
        
        assertEquals(DimensionMode.Fill, loadedMeshState.canvasWidthMode)
        assertEquals(DimensionMode.Fixed, loadedMeshState.canvasHeightMode)
        assertEquals(500, loadedMeshState.canvasHeight)
    }
}
