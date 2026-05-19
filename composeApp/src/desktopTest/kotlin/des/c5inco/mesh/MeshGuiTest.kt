package des.c5inco.mesh

import des.c5inco.mesh.testing.MeshAppFixture
import des.c5inco.mesh.testing.MeshTestTags
import des.c5inco.mesh.testing.addCustomColor
import des.c5inco.mesh.testing.assertColorSelectedInOpenDropdown
import des.c5inco.mesh.testing.openPointColorDropdown
import des.c5inco.mesh.testing.selectColorInOpenDropdown
import des.c5inco.mesh.testing.waitForClipboardText
import des.c5inco.mesh.testing.waitForTestTag
import des.c5inco.mesh.testing.waitForText
import java.awt.GraphicsEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
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
class MeshGuiTest {
    private val fixture = MeshAppFixture()

    @BeforeAll
    fun start() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        fixture.start()
    }

    @AfterAll
    fun stop() = fixture.stop()

    @Test
    @Order(1)
    fun `app launches and shows the gradient canvas`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForTestTag(MeshTestTags.GRADIENT_CANVAS)
            waitForVisualIdle()
        }
    }

    @Test
    @Order(2)
    fun `side panel exposes mesh controls`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            val rowsInput = waitForTestTag(MeshTestTags.ROWS_INPUT)
            assertTrue(rowsInput.editableText == "3", "Rows input should default to 3")

            val showPoints = waitForTestTag(MeshTestTags.SHOW_POINTS)
            assertFalse(showPoints.isSelected, "Show points should start unchecked")
        }
    }

    @Test
    @Order(3)
    fun `toggling show points renders point cursors on the canvas`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            assertTrue(findOneByText("0,0") == null, "Point cursors should be hidden initially")

            val showPoints = waitForTestTag(MeshTestTags.SHOW_POINTS)
            performSemanticsClick(showPoints)
            waitForVisualIdle()

            assertNotNull(waitForText("0,0"), "Point cursors should appear on the canvas")
        }
    }

    @Test
    @Order(4)
    fun `export code copies mesh points to the clipboard`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            performSemanticsClick(waitForTestTag(MeshTestTags.EXPORT_CODE))
            waitForVisualIdle()

            waitForText("📋 Points copied to the clipboard!")

            val clipboardText = waitForClipboardText("exported mesh code") { text ->
                text.contains("val colorPoints") && text.contains("Offset(")
            }

            assertTrue(
                clipboardText.contains("Offset(0.0f, 0.0f) to Color(0xFF7766EE)"),
                "Clipboard should contain the first mesh point",
            )
            assertTrue(
                clipboardText.contains("Offset(0.0f, 1.0f) to Color(0xFF429BED)"),
                "Clipboard should contain the last row mesh point",
            )
        }
    }

    @Test
    @Order(5)
    fun `updating rows adds another row section`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            val rowsInput = waitForTestTag(MeshTestTags.ROWS_INPUT)
            clearAndTypeText(rowsInput, "4")
            pressEnter()
            waitForVisualIdle()

            waitForText("Row 4")
            assertTrue(
                waitForTestTag(MeshTestTags.ROWS_INPUT).editableText == "4",
                "Rows input should reflect the updated value",
            )
        }
    }

    @Test
    @Order(6)
    fun `adding a custom color makes it available in point color dropdowns`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            addCustomColor("FF0000")
            openPointColorDropdown(row = 0, col = 0)
            waitForText("FF0000")
        }
    }

    @Test
    @Order(7)
    fun `changing a point color selects the custom color`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            addCustomColor("00FF00")

            openPointColorDropdown(row = 0, col = 0)
            selectColorInOpenDropdown("00FF00")

            openPointColorDropdown(row = 0, col = 0)
            assertColorSelectedInOpenDropdown("00FF00")
        }
    }
}
