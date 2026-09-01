package des.c5inco.mesh.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.MeshGradientPainter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeshGradientPainterApiTest {
    @Test
    fun `official MeshGradientPainter is on the Compose UI classpath`() {
        val painter = MeshGradientPainter(rows = 1, columns = 1, hasBicubicColor = true) {
            setVertex(0, 0, Offset(0f, 0f), Color.Red)
            setVertex(0, 1, Offset(1f, 0f), Color.Blue)
            setVertex(1, 0, Offset(0f, 1f), Color.Green)
            setVertex(1, 1, Offset(1f, 1f), Color.Yellow)
        }

        assertEquals(
            "androidx.compose.ui.graphics.MeshGradientPainter",
            painter::class.qualifiedName,
        )
        assertTrue(painter.toString().contains("MeshGradientPainter"))
    }
}
