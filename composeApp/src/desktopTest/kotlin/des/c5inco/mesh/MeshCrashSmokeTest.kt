package des.c5inco.mesh

import des.c5inco.mesh.testing.MeshAppFixture
import des.c5inco.mesh.testing.MeshTestTags
import des.c5inco.mesh.testing.addCustomColor
import des.c5inco.mesh.testing.eventually
import des.c5inco.mesh.testing.openPointColorDropdown
import des.c5inco.mesh.testing.selectColorInOpenDropdown
import des.c5inco.mesh.testing.waitForClipboardText
import des.c5inco.mesh.testing.waitForContentDescription
import des.c5inco.mesh.testing.waitForTestTag
import des.c5inco.mesh.testing.waitForText
import java.awt.GraphicsEnvironment
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

@Tag("spectre")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MeshCrashSmokeTest {
    private val fixture = MeshAppFixture(title = "Mesh Crash Smoke")

    @BeforeAll
    fun start() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        fixture.start()
    }

    @AfterAll
    fun stop() = fixture.stop()

    @Test
    @Order(1)
    fun `app stays alive through mesh editing controls`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForTestTag(MeshTestTags.GRADIENT_CANVAS)
            waitForVisualIdle()

            performSemanticsClick(waitForTestTag(MeshTestTags.SHOW_POINTS))
            waitForVisualIdle()
            assertNotNull(waitForText("0,0"))
            assertNotNull(waitForText("3,2"))

            performSemanticsClick(waitForTestTag(MeshTestTags.CONSTRAIN_EDGE_POINTS))
            waitForVisualIdle()
            performSemanticsClick(waitForTestTag(MeshTestTags.CONSTRAIN_EDGE_POINTS))
            waitForVisualIdle()

            val cols = waitForTestTag(MeshTestTags.COLS_INPUT)
            clearAndTypeText(cols, "2")
            pressEnter()
            waitForVisualIdle()
            waitForText("Row 1")
            assertTrue(waitForTestTag(MeshTestTags.COLS_INPUT).editableText == "2")

            val rows = waitForTestTag(MeshTestTags.ROWS_INPUT)
            clearAndTypeText(rows, "10")
            pressEnter()
            waitForVisualIdle()
            waitForText("Row 10")

            clearAndTypeText(waitForTestTag(MeshTestTags.ROWS_INPUT), "3")
            pressEnter()
            waitForVisualIdle()
            waitForText("Row 3")

            clearAndTypeText(waitForTestTag(MeshTestTags.COLS_INPUT), "4")
            pressEnter()
            waitForVisualIdle()

            performSemanticsClick(waitForTestTag(MeshTestTags.DISTRIBUTE_EVENLY))
            waitForVisualIdle()
            waitForTestTag(MeshTestTags.GRADIENT_CANVAS)
        }
    }

    @Test
    @Order(2)
    fun `export code image scale and blur do not crash`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            performSemanticsClick(waitForTestTag(MeshTestTags.EXPORT_CODE))
            waitForVisualIdle()
            waitForText("📋 Points copied to the clipboard!")
            waitForClipboardText("exported mesh code") { text ->
                text.contains("val colorPoints") && text.contains("Offset(")
            }

            performSemanticsClick(waitForTestTag(MeshTestTags.EXPORT_SCALE))
            waitForVisualIdle()
            waitForText("2x")
            performSemanticsClick(waitForText("2x"))
            waitForVisualIdle()
            waitForText("@2x")

            performSemanticsClick(waitForTestTag(MeshTestTags.EXPORT_IMAGE))
            waitForVisualIdle()
            val exportDir = File(System.getProperty("user.home"), "Desktop").absolutePath
            waitForText("🖼 Exported mesh-export@2x.png to $exportDir")
            assertTrue(File(exportDir, "mesh-export@2x.png").isFile)

            val blur = waitForTestTag(MeshTestTags.BLUR_SLIDER)
            click(blur)
            waitForVisualIdle()

            waitForTestTag(MeshTestTags.GRADIENT_CANVAS)
        }
    }

    @Test
    @Order(3)
    fun `dragging an interior canvas point updates its offset fields`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            if (findOneByText("1,1") == null) {
                performSemanticsClick(waitForTestTag(MeshTestTags.SHOW_POINTS))
                waitForVisualIdle()
            }
            waitForText("1,1")

            val cursor = waitForTestTag(MeshTestTags.pointCursor(row = 1, col = 1))
            val offsetX = waitForTestTag(MeshTestTags.pointOffsetX(row = 1, col = 1))
            val offsetY = waitForTestTag(MeshTestTags.pointOffsetY(row = 1, col = 1))
            val beforeX = offsetX.editableText.orEmpty()
            val beforeY = offsetY.editableText.orEmpty()
            val start = cursor.centerOnScreen

            swipe(
                startX = start.x,
                startY = start.y,
                endX = start.x + 80,
                endY = start.y + 60,
                steps = 12,
            )
            waitForVisualIdle()

            val moved = eventually(description = "interior point offset fields to change after drag") {
                val nextX = waitForTestTag(MeshTestTags.pointOffsetX(row = 1, col = 1)).editableText.orEmpty()
                val nextY = waitForTestTag(MeshTestTags.pointOffsetY(row = 1, col = 1)).editableText.orEmpty()
                if (nextX != beforeX || nextY != beforeY) nextX to nextY else null
            }
            assertTrue(moved.first != beforeX || moved.second != beforeY)
        }
    }

    @Test
    @Order(4)
    fun `toggling canvas width and height modes enables fixed size inputs`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            val widthInput = waitForTestTag(MeshTestTags.CANVAS_WIDTH_INPUT)
            val heightInput = waitForTestTag(MeshTestTags.CANVAS_HEIGHT_INPUT)
            assertTrue(!widthInput.editableText.isNullOrBlank(), "Width input should show a value in Fill mode")
            assertTrue(!heightInput.editableText.isNullOrBlank(), "Height input should show a value in Fill mode")

            performSemanticsClick(waitForTestTag(MeshTestTags.CANVAS_WIDTH_MODE))
            waitForVisualIdle()
            waitForContentDescription("Canvas width mode Fixed")

            val fixedWidth = waitForTestTag(MeshTestTags.CANVAS_WIDTH_INPUT)
            clearAndTypeText(fixedWidth, "420")
            pressEnter()
            waitForVisualIdle()
            assertTrue(
                waitForTestTag(MeshTestTags.CANVAS_WIDTH_INPUT).editableText == "420",
                "Width input should accept a fixed size",
            )

            performSemanticsClick(waitForTestTag(MeshTestTags.CANVAS_HEIGHT_MODE))
            waitForVisualIdle()
            waitForContentDescription("Canvas height mode Fixed")

            val fixedHeight = waitForTestTag(MeshTestTags.CANVAS_HEIGHT_INPUT)
            clearAndTypeText(fixedHeight, "320")
            pressEnter()
            waitForVisualIdle()
            assertTrue(
                waitForTestTag(MeshTestTags.CANVAS_HEIGHT_INPUT).editableText == "320",
                "Height input should accept a fixed size",
            )

            performSemanticsClick(waitForTestTag(MeshTestTags.CANVAS_WIDTH_MODE))
            performSemanticsClick(waitForTestTag(MeshTestTags.CANVAS_HEIGHT_MODE))
            waitForVisualIdle()
            waitForContentDescription("Canvas width mode Fill")
            waitForContentDescription("Canvas height mode Fill")
            waitForTestTag(MeshTestTags.GRADIENT_CANVAS)
        }
    }

    @Test
    @Order(5)
    fun `custom colors and point color changes keep the canvas alive`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            addCustomColor("FF8800")
            openPointColorDropdown(row = 1, col = 1)
            selectColorInOpenDropdown("FF8800")
            waitForVisualIdle()

            waitForTestTag(MeshTestTags.GRADIENT_CANVAS)
            waitForText("Colors")
            waitForText("Canvas")
            waitForText("Points")
        }
    }
}
