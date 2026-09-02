package des.c5inco.mesh

import des.c5inco.mesh.testing.MeshAppFixture
import des.c5inco.mesh.testing.MeshTestTags
import des.c5inco.mesh.testing.addCustomColor
import des.c5inco.mesh.testing.captureWindowPng
import des.c5inco.mesh.testing.openPointColorDropdown
import des.c5inco.mesh.testing.selectColorInOpenDropdown
import des.c5inco.mesh.testing.waitForContentDescription
import des.c5inco.mesh.testing.waitForTestTag
import des.c5inco.mesh.testing.waitForText
import java.awt.GraphicsEnvironment
import java.awt.Point
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@Tag("spectre")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MeshFourVertexHandleDemoTest {
    private val fixture = MeshAppFixture(title = "Mesh Four Vertices")

    @BeforeAll
    fun start() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        fixture.start()
    }

    @AfterAll
    fun stop() = fixture.stop()

    @Test
    fun `rotates handles on four differently colored vertices by at least 30 degrees`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            if (findOneByText("1,1") == null) {
                performSemanticsClick(waitForTestTag(MeshTestTags.SHOW_POINTS))
                waitForVisualIdle()
            }
            waitForText("1,1")

            val constrain = waitForTestTag(MeshTestTags.CONSTRAIN_EDGE_POINTS)
            if (constrain.isSelected) {
                performSemanticsClick(constrain)
                waitForVisualIdle()
            }

            val targets = listOf(
                VertexColor(row = 1, col = 1, hex = "FF3B30", handle = "r", offsetX = "0.1", offsetY = "-0.12"),
                VertexColor(row = 1, col = 2, hex = "34C759", handle = "b", offsetX = "0.12", offsetY = "0.1"),
                VertexColor(row = 0, col = 1, hex = "007AFF", handle = "b", offsetX = "-0.12", offsetY = "0.1"),
                VertexColor(row = 2, col = 2, hex = "FFCC00", handle = "t", offsetX = "-0.12", offsetY = "-0.1"),
            )
            targets.forEach { target ->
                addCustomColor(target.hex)
                openPointColorDropdown(target.row, target.col)
                selectColorInOpenDropdown(target.hex)
            }
            waitForVisualIdle()
            captureWindowPng("four-vertices-colored.png")

            targets.forEachIndexed { index, target ->
                click(waitForTestTag(MeshTestTags.pointCursor(row = target.row, col = target.col)))
                waitForVisualIdle()
                waitForText("Handles for ${target.col},${target.row}")
                waitForContentDescription(
                    when (target.handle) {
                        "l" -> "Left control handle"
                        "t" -> "Top control handle"
                        "r" -> "Right control handle"
                        else -> "Bottom control handle"
                    },
                )

                val vertex = waitForTestTag(MeshTestTags.pointCursor(row = target.row, col = target.col)).centerOnScreen
                val handle = waitForTestTag(MeshTestTags.controlHandle(target.handle))
                val start = handle.centerOnScreen
                val before = angleDegrees(vertex, start)

                val xField = waitForTestTag(MeshTestTags.handleOffsetX(target.handle))
                clearAndTypeText(xField, target.offsetX)
                pressEnter()
                waitForVisualIdle()
                val yField = waitForTestTag(MeshTestTags.handleOffsetY(target.handle))
                clearAndTypeText(yField, target.offsetY)
                pressEnter()
                waitForVisualIdle()
                delay(200)

                val afterHandle = waitForTestTag(MeshTestTags.controlHandle(target.handle)).centerOnScreen
                val after = angleDegrees(vertex, afterHandle)
                val delta = abs(shortestAngleDelta(before, after))
                assertTrue(
                    delta >= 30.0,
                    "Vertex ${target.col},${target.row} handle '${target.handle}' rotated only $delta degrees (before=$before after=$after)",
                )
                captureWindowPng("four-vertices-step-${index + 1}-${target.hex}.png")
            }

            captureWindowPng("four-vertices-final.png")
        }
    }

    private data class VertexColor(
        val row: Int,
        val col: Int,
        val hex: String,
        val handle: String,
        val offsetX: String,
        val offsetY: String,
    )

    private fun angleDegrees(origin: Point, point: Point): Double {
        val degrees = Math.toDegrees(atan2((point.y - origin.y).toDouble(), (point.x - origin.x).toDouble()))
        return if (degrees < 0) degrees + 360.0 else degrees
    }

    private fun shortestAngleDelta(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }
}
