package des.c5inco.mesh

import des.c5inco.mesh.testing.MeshAppFixture
import des.c5inco.mesh.testing.MeshTestTags
import des.c5inco.mesh.testing.captureWindowPng
import des.c5inco.mesh.testing.waitForContentDescription
import des.c5inco.mesh.testing.waitForTestTag
import des.c5inco.mesh.testing.waitForText
import java.awt.GraphicsEnvironment
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@Tag("spectre")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MeshHandlePrototypeTest {
    private val fixture = MeshAppFixture(title = "Mesh Handles")

    @BeforeAll
    fun start() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        fixture.start()
    }

    @AfterAll
    fun stop() = fixture.stop()

    @Test
    fun `selecting a vertex shows bezier handles that can be dragged`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            if (findOneByText("1,1") == null) {
                performSemanticsClick(waitForTestTag(MeshTestTags.SHOW_POINTS))
                waitForVisualIdle()
            }
            waitForText("1,1")
            captureWindowPng("handles-curves-visible.png")

            click(waitForTestTag(MeshTestTags.pointCursor(row = 1, col = 1)))
            waitForVisualIdle()
            waitForTestTag(MeshTestTags.SELECTED_HANDLES)
            waitForText("Handles for 1,1")
            waitForContentDescription("Right control handle")
            captureWindowPng("handles-curves-selected.png")

            val rightHandle = waitForTestTag(MeshTestTags.controlHandle("r"))
            val start = rightHandle.centerOnScreen
            swipe(
                startX = start.x,
                startY = start.y,
                endX = start.x + 90,
                endY = start.y - 40,
                steps = 16,
                duration = 700.milliseconds,
            )
            waitForVisualIdle()
            delay(250)
            captureWindowPng("handles-curves-right-dragged.png")

            val bottomHandle = waitForTestTag(MeshTestTags.controlHandle("b"))
            val bottomStart = bottomHandle.centerOnScreen
            swipe(
                startX = bottomStart.x,
                startY = bottomStart.y,
                endX = bottomStart.x + 110,
                endY = bottomStart.y + 70,
                steps = 16,
                duration = 700.milliseconds,
            )
            waitForVisualIdle()
            captureWindowPng("handles-curves-bottom-dragged.png")

            assertNotNull(findOneByText("Handles for 1,1"))
            waitForTestTag(MeshTestTags.GRADIENT_CANVAS)
        }
    }
}
