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
import dev.sebastiano.spectre.core.ComposeAutomator
import java.awt.GraphicsEnvironment
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.JMenuItem
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
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
            restoringDocument {
                val configuration = fixture.configuration
                withContext(Dispatchers.Main) {
                    configuration.updateTotalRows(MAX_GRID_SIZE)
                    configuration.updateTotalCols(MAX_GRID_SIZE)
                }
                // The grid is only rebuilt on the next frame: count once the panel shows it.
                waitForSidePanelNode("the side panel to show a $MAX_GRID_SIZE-column grid") {
                    findOneByTestTag(MeshTestTags.COLS_INPUT)?.takeIf { it.editableText == "$MAX_GRID_SIZE" }
                }
                waitForSidePanelTag(MeshTestTags.pointColor(0, MAX_GRID_SIZE - 1))
                waitForVisualIdle()

                // A window shows a few dozen point editors; composing every one of them would be thousands.
                val composed = allNodes().count { it.testTag?.startsWith(MeshTestTags.POINT_COLOR_PREFIX) == true }
                assertTrue(composed in 1 until 200, "Expected only the visible point rows, but $composed were composed")
            }
        }
    }

    @Test
    @Order(12)
    fun `the side panel reaches the last point of a full grid and scrolls back to the top`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            restoringDocument {
                val configuration = fixture.configuration
                withContext(Dispatchers.Main) {
                    configuration.updateTotalRows(MAX_GRID_SIZE)
                    configuration.updateTotalCols(MAX_GRID_SIZE)
                }

                waitForSidePanelTag(MeshTestTags.pointColor(MAX_GRID_SIZE - 1, MAX_GRID_SIZE - 1))
                assertNull(findOneByTestTag(MeshTestTags.DOCUMENT_NAME), "The document controls should be scrolled far away")
                assertEquals("Untitled", waitForSidePanelTag(MeshTestTags.DOCUMENT_NAME).text)
            }
        }
    }

    @Test
    @Order(13)
    fun `the custom color editor keeps its color while scrolled out of view`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            restoringDocument {
                // The last point of a full-height grid is far below the Colors section in any window.
                withContext(Dispatchers.Main) { fixture.configuration.updateTotalRows(MAX_GRID_SIZE) }

                performSemanticsClick(waitForSidePanelTag(MeshTestTags.ADD_CUSTOM_COLOR))
                val input = eventually(description = "focused custom color input") {
                    findOneByTestTag(MeshTestTags.CUSTOM_COLOR_INPUT)?.takeIf { it.isFocused }
                }
                // The editor drops a leading hash, including the lone "#" typed first.
                clearAndTypeText(input, "#ABCDEF")
                eventually(description = "custom color input to read ABCDEF") {
                    findOneByTestTag(MeshTestTags.CUSTOM_COLOR_INPUT)?.takeIf { it.editableText == "ABCDEF" }
                }
                // Move focus elsewhere, since the list keeps a focused item composed.
                performSemanticsClick(waitForSidePanelTag(MeshTestTags.ROWS_INPUT))
                waitForVisualIdle()

                waitForSidePanelTag(MeshTestTags.pointColor(MAX_GRID_SIZE - 1, 2))
                assertNull(findOneByTestTag(MeshTestTags.CUSTOM_COLOR_INPUT), "The editor should be scrolled out of view")

                waitForSidePanelTag(MeshTestTags.CUSTOM_COLOR_INPUT)
                waitForVisualIdle()
                refreshWindows()
                val shownAgain = findOneByTestTag(MeshTestTags.CUSTOM_COLOR_INPUT)
                assertEquals("ABCDEF", shownAgain?.editableText, "The editor should keep what was typed")
                assertFalse(shownAgain!!.isFocused, "Scrolling the editor back should not take focus")

                // Add on an open editor focuses it again.
                performSemanticsClick(waitForSidePanelTag(MeshTestTags.ADD_CUSTOM_COLOR))
                eventually(description = "focused custom color input") {
                    findOneByTestTag(MeshTestTags.CUSTOM_COLOR_INPUT)?.takeIf { it.isFocused && it.editableText == "ABCDEF" }
                }
                pressEnter()
                waitForSidePanelTag(MeshTestTags.customColor("ABCDEF"))
            }
        }
    }

    @Test
    @Order(14)
    fun `a delete confirmation closes when its color scrolls out of view`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.APP)
            restoringDocument {
                withContext(Dispatchers.Main) { fixture.configuration.updateTotalRows(MAX_GRID_SIZE) }

                addCustomColor("123456")
                performSemanticsClick(waitForSidePanelTag(MeshTestTags.customColor("123456")))
                waitForText("Delete #123456?")

                waitForSidePanelTag(MeshTestTags.pointColor(MAX_GRID_SIZE - 1, 2))
                waitForSidePanelTag(MeshTestTags.customColor("123456"))
                waitForVisualIdle()
                refreshWindows()
                assertNull(findOneByText("Delete #123456?"), "Scrolling back should not reopen the confirmation")
            }
        }
    }

    /**
     * Runs [block], then undoes its grid and custom color edits and closes the color editor, even when it
     * fails: a full grid or an open editor left behind would make every later test time out.
     */
    private suspend fun ComposeAutomator.restoringDocument(block: suspend ComposeAutomator.() -> Unit) {
        val configuration = fixture.configuration
        fun document() = Triple(
            configuration.meshState.value.rows,
            configuration.meshState.value.cols,
            configuration.customColors.value,
        )
        val before = withContext(Dispatchers.Main) { document() }
        try {
            block()
        } finally {
            withContext(Dispatchers.Main) {
                while (document() != before && configuration.canUndo.value) configuration.undo()
            }
            closeCustomColorEditor()
        }
    }

    private suspend fun ComposeAutomator.closeCustomColorEditor() {
        val input = runCatching {
            waitForSidePanelNode("an open custom color editor", timeout = 1.seconds) {
                findOneByTestTag(MeshTestTags.CUSTOM_COLOR_INPUT)
            }
        }.getOrNull() ?: return
        performSemanticsClick(input)
        eventually(description = "focused custom color input") {
            findOneByTestTag(MeshTestTags.CUSTOM_COLOR_INPUT)?.takeIf { it.isFocused }
        }
        pressKey(KeyEvent.VK_ESCAPE)
        eventually(description = "the custom color editor to close") {
            if (findOneByTestTag(MeshTestTags.CUSTOM_COLOR_INPUT) == null) Unit else null
        }
    }
}
