package des.c5inco.mesh

import data.MAX_GRID_SIZE
import des.c5inco.mesh.testing.MeshAppFixture
import des.c5inco.mesh.testing.MeshTestTags
import des.c5inco.mesh.testing.addCustomColor
import des.c5inco.mesh.testing.assertColorSelectedInOpenDropdown
import des.c5inco.mesh.testing.eventually
import des.c5inco.mesh.testing.openPointColorDropdown
import des.c5inco.mesh.testing.selectColorInOpenDropdown
import des.c5inco.mesh.testing.waitForClipboardText
import des.c5inco.mesh.testing.waitForSidePanelNode
import des.c5inco.mesh.testing.waitForSidePanelTag
import des.c5inco.mesh.testing.waitForSidePanelText
import des.c5inco.mesh.testing.waitForTestTag
import des.c5inco.mesh.testing.waitForText
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.JMenuItem
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

            val rowsInput = waitForSidePanelTag(MeshTestTags.ROWS_INPUT)
            assertTrue(rowsInput.editableText == "3", "Rows input should default to 3")

            assertEquals("Untitled", waitForSidePanelTag(MeshTestTags.DOCUMENT_NAME).text)
            assertEquals("Untitled", fixture.windowTitle, "Showing the document should not edit it")

            val showPoints = waitForSidePanelTag(MeshTestTags.SHOW_POINTS)
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

            val showPoints = waitForSidePanelTag(MeshTestTags.SHOW_POINTS)
            performSemanticsClick(showPoints)
            waitForVisualIdle()

            assertNotNull(waitForText("0,0"), "Point cursors should appear on the canvas")
        }
    }

    @Test
    @Order(4)
    fun `export code copies an official mesh gradient painter to the clipboard`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            performSemanticsClick(waitForSidePanelTag(MeshTestTags.EXPORT_CODE))
            waitForVisualIdle()

            waitForText("📋 Points copied to the clipboard!")

            val clipboardText = waitForClipboardText("exported mesh code") { text ->
                text.contains("val meshGradient") && text.contains("setVertex(")
            }

            assertTrue(
                clipboardText.contains("MeshGradientPainter(rows = 2, columns = 3, hasBicubicColor = true)"),
                "Painter dimensions count patches, not vertices",
            )
            assertTrue(
                clipboardText.contains(
                    "setVertex(0, 0, Offset(0.0f, 0.0f), Color(0xFF7766EE))",
                ),
                "Clipboard should contain the first mesh point",
            )
            assertTrue(
                clipboardText.contains(
                    "setVertex(2, 0, Offset(0.0f, 1.0f), Color(0xFF429BED))",
                ),
                "Clipboard should contain the last row mesh point",
            )
            assertTrue(clipboardText.contains("val meshGradientModifier"))
            assertTrue(clipboardText.contains(".paint(meshGradient, sizeToIntrinsics = false)"))
            assertFalse(clipboardText.contains("BlurEffect"), "The default mesh has no blur")
        }
    }

    @Test
    @Order(5)
    fun `updating rows adds another row section`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            val rowsInput = waitForSidePanelTag(MeshTestTags.ROWS_INPUT)
            clearAndTypeText(rowsInput, "4")
            pressEnter()
            waitForVisualIdle()

            waitForSidePanelText("Row 4")
            assertTrue(
                waitForSidePanelTag(MeshTestTags.ROWS_INPUT).editableText == "4",
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

    @Test
    @Order(8)
    fun `deleting a custom color asks for confirmation first`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()

            addCustomColor("0000FF")
            performSemanticsClick(waitForSidePanelTag(MeshTestTags.customColor("0000FF")))
            waitForVisualIdle()
            waitForText("Delete #0000FF?")

            performSemanticsClick(waitForTestTag(MeshTestTags.DELETE_COLOR_CANCEL))
            waitForVisualIdle()
            assertNull(findOneByText("Delete #0000FF?"), "Cancel should close the confirmation")
            assertNotNull(waitForSidePanelTag(MeshTestTags.customColor("0000FF")), "Cancel should keep the color")

            performSemanticsClick(waitForSidePanelTag(MeshTestTags.customColor("0000FF")))
            waitForVisualIdle()
            performSemanticsClick(waitForTestTag(MeshTestTags.DELETE_COLOR_CONFIRM))
            // The add button shares the swatches' list item: while it's composed, a missing swatch is really gone.
            waitForSidePanelTag(MeshTestTags.ADD_CUSTOM_COLOR)
            eventually(description = "custom color 0000FF to be deleted") {
                val colorsShown = findOneByTestTag(MeshTestTags.ADD_CUSTOM_COLOR) != null
                if (colorsShown && findOneByTestTag(MeshTestTags.customColor("0000FF")) == null) Unit else null
            }
        }
    }

    @Test
    @Order(9)
    fun `a new document asks before discarding unsaved changes`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()
            assertEquals("Untitled — Edited", fixture.windowTitle)

            performSemanticsClick(waitForSidePanelTag(MeshTestTags.NEW_DOCUMENT))
            waitForTestTag(MeshTestTags.DOCUMENT_PROMPT)
            waitForText("Save changes to “Untitled”?")
            performSemanticsClick(waitForText("Don’t Save"))
            waitForVisualIdle()

            waitForSidePanelNode("the new document's default grid") {
                findOneByTestTag(MeshTestTags.ROWS_INPUT)?.takeIf { it.editableText == "3" }
            }
            eventually(description = "an unedited window title") {
                fixture.windowTitle.takeIf { it == "Untitled" }
            }

            openPointColorDropdown(row = 0, col = 0)
            assertNull(findOneByText("FF0000"), "Custom colors belong to the discarded document")
            pressKey(KeyEvent.VK_ESCAPE)
            waitForVisualIdle()
        }
    }

    @Test
    @Order(10)
    fun `a document shortcut commits the field being edited first`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            waitForVisualIdle()
            assertEquals("Untitled", fixture.windowTitle)

            // Type a new row count without pressing Enter, then press New.
            clearAndTypeText(waitForSidePanelTag(MeshTestTags.ROWS_INPUT), "5")
            if (System.getProperty("os.name").lowercase().contains("mac")) {
                // On macOS the File menu owns ⌘N, and synthetic key events don't reach menus,
                // so pick the menu item that the shortcut picks.
                SwingUtilities.invokeAndWait {
                    val file = (fixture.window as JFrame).jMenuBar.getMenu(0)
                    file.menuComponents.filterIsInstance<JMenuItem>().first { it.text == "New" }.doClick()
                }
            } else {
                pressKey(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK)
            }
            waitForVisualIdle()

            waitForTestTag(MeshTestTags.DOCUMENT_PROMPT)
            eventually(description = "the typed row count to be committed") {
                fixture.configuration.meshState.value.rows.takeIf { it == 5 }
            }
            performSemanticsClick(waitForText("Don’t Save"))
            waitForVisualIdle()
        }
    }

    @Test
    @Order(11)
    fun `a large grid only composes the point rows on screen`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            val configuration = fixture.configuration
            withContext(Dispatchers.Main) {
                configuration.updateTotalRows(MAX_GRID_SIZE)
                configuration.updateTotalCols(MAX_GRID_SIZE)
            }
            waitForSidePanelText("Row 1")

            val total = MAX_GRID_SIZE * MAX_GRID_SIZE
            val composed = allNodes().count { it.testTag?.startsWith(MeshTestTags.POINT_COLOR_PREFIX) == true }
            assertTrue(composed in 1 until total / 2, "Expected only the visible point rows, but $composed of $total were composed")

            withContext(Dispatchers.Main) { repeat(2) { configuration.undo() } }
        }
    }
}
