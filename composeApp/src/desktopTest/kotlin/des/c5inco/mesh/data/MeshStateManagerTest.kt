package des.c5inco.mesh.data

import data.DimensionMode
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MeshStateManagerTest {
    @Test
    fun `settings from the custom renderer retain canvas and grid configuration`() {
        val originalHome = System.getProperty("user.home")
        val home = Files.createTempDirectory("mesh-state-").toFile()
        try {
            System.setProperty("user.home", home.absolutePath)
            val settings = home.resolve(".mesh/mesh1.json")
            settings.parentFile.mkdirs()
            settings.writeText(
                """{"canvasWidthMode":"Fixed","canvasWidth":640,"resolution":20,"blurLevel":0.5,"rows":5,"cols":6}""",
            )

            val state = MeshStateManager.loadState()

            assertEquals(DimensionMode.Fixed, state.canvasWidthMode)
            assertEquals(640, state.canvasWidth)
            assertEquals(0.5f, state.blurLevel)
            assertEquals(5, state.rows)
            assertEquals(6, state.cols)
        } finally {
            System.setProperty("user.home", originalHome)
            home.deleteRecursively()
        }
    }
}
