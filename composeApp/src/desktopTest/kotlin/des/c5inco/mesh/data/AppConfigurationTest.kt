package des.c5inco.mesh.data

import androidx.compose.ui.geometry.Offset
import data.DocumentColor
import data.MAX_GRID_SIZE
import data.MIN_GRID_SIZE
import data.MeshDocument
import data.MeshState
import data.Session
import data.TRANSPARENT_COLOR_ID
import data.legacyMeshDocument
import data.meshDocumentOf
import des.c5inco.mesh.testing.FakeDataRepository
import des.c5inco.mesh.testing.fakePresetColors
import des.c5inco.mesh.testing.waitForClipboardText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.MeshPoint
import model.SavedColor
import model.defaultMeshPointGrid
import model.generateMeshPointGrid
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AppConfigurationTest {
    @TempDir
    lateinit var directory: File

    private val documentFile get() = File(directory, "Test.mesh")

    @Test
    fun `code export includes the current rounded blur and a usable paint modifier`() = runBlocking {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a system clipboard")
        val repository = FakeDataRepository(
            initialColors = (1L..3L).map { SavedColor(uid = it, red = (it * 70).toInt(), green = 150, blue = 200, preset = true) },
            documentsDirectory = directory,
        )
        val configuration = AppConfiguration(repository)
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard

        for ((level, radius) in listOf(0f to 0, .012f to 0, .0125f to 1, .513f to 21, 1f to 40)) {
            configuration.updateBlurLevel(level)
            clipboard.setContents(StringSelection(""), null)
            configuration.exportMeshPointsAsCode()
            val code = waitForClipboardText("mesh modifier at blur $level") {
                it.contains("val meshGradientModifier")
            }

            assertTrue(code.contains("import androidx.compose.ui.draw.paint"))
            assertTrue(code.contains("hasBicubicColor = true"))
            assertTrue(code.contains(".paint(meshGradient, sizeToIntrinsics = false)"))
            if (radius == 0) {
                assertFalse(code.contains("BlurEffect"), "A zero-radius blur must not be created")
                assertFalse(code.contains("graphicsLayer"), "An unblurred export needs no effect layer")
            } else {
                assertTrue(code.contains("import androidx.compose.ui.graphics.graphicsLayer"))
                assertTrue(code.contains("renderEffect = BlurEffect($radius.0f, $radius.0f, TileMode.Clamp)"))
                assertTrue(code.indexOf(".graphicsLayer") < code.indexOf(".paint("), "Blur must wrap the painted mesh")
            }
        }
    }

    @Test
    fun `saving waits for the startup document and keeps the default grid`() = runBlocking {
        val presetsGate = CompletableDeferred<Unit>()
        val configuration = AppConfiguration(
            FakeDataRepository(presetColors = flow { presetsGate.await(); emit(fakePresetColors) }, documentsDirectory = directory)
        )

        val save = async { configuration.saveDocument(documentFile) }
        assertFalse(save.isCompleted)

        presetsGate.complete(Unit)
        assertTrue(save.await())

        assertEquals(defaultDocument().points, saved(documentFile).points)
        assertEquals(DocumentColor(red = 40, green = 0, blue = 0), saved(documentFile).points[0][0].color)
    }

    @Test
    fun `failed startup loading prevents saving`() {
        val configuration = AppConfiguration(
            FakeDataRepository(presetColors = flow { throw IllegalStateException("load failed") }, documentsDirectory = directory)
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { configuration.saveDocument(documentFile) }
        }

        assertEquals("load failed", error.message)
        assertFalse(documentFile.exists())
    }

    @Test
    fun `a resize made while loading applies on load and can be undone back to the loaded grid`() = runBlocking {
        val presetsGate = CompletableDeferred<Unit>()
        val configuration = AppConfiguration(
            FakeDataRepository(presetColors = flow { presetsGate.await(); emit(fakePresetColors) }, documentsDirectory = directory)
        )

        configuration.updateTotalRows(5)
        presetsGate.complete(Unit)
        configuration.awaitMeshLoaded()
        assertEquals(5, configuration.meshPoints.size)
        assertEquals(5, configuration.meshState.value.rows)
        assertTrue(configuration.document.value.modified)

        configuration.undo()
        assertEquals(3, configuration.meshPoints.size)
        assertEquals(3, configuration.meshState.value.rows)
    }

    @Test
    fun `undoing a resize made while loading restores the loaded grid and blur`() = runBlocking {
        val presetsGate = CompletableDeferred<Unit>()
        val file = writeDocument(documentFile, MeshState(blurLevel = 0.25f))
        val configuration = AppConfiguration(
            FakeDataRepository(
                presetColors = flow { presetsGate.await(); emit(fakePresetColors) },
                documentsDirectory = directory,
                session = Session(lastDocument = file.absolutePath),
            )
        )

        withContext(Dispatchers.Main) { configuration.setGrid(rows = 5, cols = 6, blur = 0.5f) }
        presetsGate.complete(Unit)
        configuration.awaitMeshLoaded()
        assertEquals(List(5) { 6 }, configuration.meshPoints.map { it.size })
        assertEquals(5 to 6, configuration.meshState.value.let { it.rows to it.cols })
        assertEquals(0.5f, configuration.meshState.value.blurLevel)
        // The resized grid undo must bring back, so compare the points rather than their shape.
        val resizedPoints = configuration.meshPoints.toList()

        withContext(Dispatchers.Main) { configuration.undo() }
        assertEquals(List(3) { 4 }, configuration.meshPoints.map { it.size })
        assertEquals(3 to 4, configuration.meshState.value.let { it.rows to it.cols })
        assertEquals(0.25f, configuration.meshState.value.blurLevel)
        // The loaded grid, not an evenly spaced one: defaultMeshPointGrid curves its middle row.
        assertEquals(defaultMeshPointGrid(fakePresetColors.map(SavedColor::uid)), configuration.meshPoints.toList())
        assertFalse(configuration.canUndo.value)

        withContext(Dispatchers.Main) { configuration.redo() }
        assertEquals(List(5) { 6 }, configuration.meshPoints.map { it.size })
        assertEquals(5 to 6, configuration.meshState.value.let { it.rows to it.cols })
        assertEquals(0.5f, configuration.meshState.value.blurLevel)
        assertEquals(resizedPoints, configuration.meshPoints.toList())
    }

    @Test
    fun `setGrid with a size or blur out of range changes nothing`() = runBlocking {
        val configuration = loadedConfiguration()
        val points = configuration.meshPoints.toList()
        val meshState = configuration.meshState.value

        for ((rows, cols, blur) in listOf(
            Triple(MIN_GRID_SIZE - 1, null, null),
            Triple(null, MAX_GRID_SIZE + 1, null),
            Triple(5, 5, 1.5f),
        )) {
            assertThrows(IllegalArgumentException::class.java) { configuration.setGrid(rows, cols, blur) }
        }

        assertEquals(points, configuration.meshPoints.toList())
        assertEquals(meshState, configuration.meshState.value)
        assertFalse(configuration.canUndo.value)
        assertFalse(configuration.document.value.modified)
        // The side panel's controls still clamp instead.
        configuration.updateTotalRows(MAX_GRID_SIZE + 5)
        assertEquals(MAX_GRID_SIZE, configuration.meshPoints.size)
    }

    @Test
    fun `resizing replaces the grid right away`() = runBlocking {
        val configuration = loadedConfiguration()

        configuration.updateTotalRows(4)
        configuration.updateTotalCols(5)
        assertEquals(List(4) { 5 }, configuration.meshPoints.map { it.size })

        assertTrue(configuration.saveDocument(documentFile))
        assertEquals(List(4) { 5 }, saved(documentFile).points.map { it.size })
        assertEquals(4 to 5, saved(documentFile).meshState.let { it.rows to it.cols })
    }

    @Test
    fun `undo merges repeated edits to one point but not edits to another`() = runBlocking {
        val configuration = loadedConfiguration()
        val original = configuration.meshPoints.toList()

        configuration.updateMeshPoint(1, 1, Offset(0.2f, 0.5f) to 2L)
        configuration.updateMeshPoint(1, 1, Offset(0.25f, 0.55f) to 2L)
        configuration.updateMeshPoint(1, 2, Offset(0.6f, 0.3f) to 3L)

        configuration.undo()
        assertEquals(Offset(0.67f, 0.8f) to 2L, configuration.meshPoints[1][2])
        assertEquals(Offset(0.25f, 0.55f) to 2L, configuration.meshPoints[1][1])

        configuration.undo()
        assertEquals(original, configuration.meshPoints.toList())
        assertFalse(configuration.canUndo.value)

        configuration.redo()
        configuration.redo()
        assertEquals(Offset(0.25f, 0.55f) to 2L, configuration.meshPoints[1][1])
        assertEquals(Offset(0.6f, 0.3f) to 3L, configuration.meshPoints[1][2])
    }

    @Test
    fun `edits that change nothing add no undo step and leave the document unedited`() = runBlocking {
        val configuration = loadedConfiguration()

        configuration.updateMeshPoint(1, 1, configuration.meshPoints[1][1])
        configuration.updateBlurLevel(configuration.meshState.value.blurLevel)
        configuration.updateMeasuredCanvasSize(640, 480)

        assertFalse(configuration.canUndo.value)
        assertFalse(configuration.document.value.modified, "Following the window and unchanged values aren't edits")
    }

    @Test
    fun `undoing a grid resize restores the previous grid and points`() = runBlocking {
        val configuration = loadedConfiguration()
        configuration.updateMeshPoint(1, 1, Offset(0.1f, 0.2f) to 3L)
        val beforeResize = configuration.meshPoints.toList()

        configuration.updateTotalRows(5)
        assertEquals(5, configuration.meshPoints.size)

        configuration.undo()
        assertEquals(3, configuration.meshState.value.rows)
        assertEquals(beforeResize, configuration.meshPoints.toList())

        configuration.redo()
        assertEquals(5, configuration.meshState.value.rows)
        assertEquals(5, configuration.meshPoints.size)
    }

    @Test
    fun `each resize is its own undo step`() = runBlocking {
        val configuration = loadedConfiguration()

        configuration.updateTotalRows(5)
        configuration.updateTotalCols(6)

        configuration.undo()
        assertEquals(5 to 4, configuration.meshState.value.let { it.rows to it.cols })
        assertEquals(List(5) { 4 }, configuration.meshPoints.map { it.size })
        configuration.undo()
        assertEquals(3 to 4, configuration.meshState.value.let { it.rows to it.cols })
        assertEquals(List(3) { 4 }, configuration.meshPoints.map { it.size })
    }

    @Test
    fun `undo steps back through a point edit, a resize, and a later edit in order`() = runBlocking {
        val configuration = loadedConfiguration()
        configuration.updateMeshPoint(1, 1, Offset(0.2f, 0.5f) to 3L)
        configuration.updateTotalRows(5)
        configuration.updateBlurLevel(0.5f)

        configuration.undo()
        assertEquals(0f, configuration.meshState.value.blurLevel)
        assertEquals(5, configuration.meshPoints.size)

        configuration.undo()
        assertEquals(3, configuration.meshState.value.rows)
        assertEquals(Offset(0.2f, 0.5f) to 3L, configuration.meshPoints[1][1])

        configuration.undo()
        assertEquals(Offset(0.33f, 0.8f) to 2L, configuration.meshPoints[1][1])
        assertFalse(configuration.canUndo.value)
    }

    @Test
    fun `deleting a custom color clears its points and the background, and undo brings them back`() = runBlocking {
        val configuration = loadedConfiguration()
        val red = configuration.addColor(SavedColor(red = 255, green = 0, blue = 0))
        configuration.updateMeshPoint(1, 1, Offset(0.2f, 0.5f) to red)
        configuration.updateCanvasBackgroundColor(red)

        configuration.deleteColor(configuration.customColors.value.single())
        assertEquals(emptyList<SavedColor>(), configuration.customColors.value)
        assertEquals(Offset(0.2f, 0.5f) to TRANSPARENT_COLOR_ID, configuration.meshPoints[1][1])
        assertEquals(TRANSPARENT_COLOR_ID, configuration.canvasBackgroundColor.value)

        configuration.undo()
        assertEquals(listOf(red), configuration.customColors.value.map(SavedColor::uid))
        assertEquals(Offset(0.2f, 0.5f) to red, configuration.meshPoints[1][1])
        assertEquals(red, configuration.canvasBackgroundColor.value)
    }

    @Test
    fun `a new edit after undo clears redo`() = runBlocking {
        val configuration = loadedConfiguration()
        configuration.updateCanvasBackgroundColor(2L)
        configuration.undo()
        assertTrue(configuration.canRedo.value)

        configuration.updateBlurLevel(0.5f)

        assertFalse(configuration.canRedo.value)
        configuration.redo()
        assertEquals(TRANSPARENT_COLOR_ID, configuration.canvasBackgroundColor.value)
    }

    @Test
    fun `edits to a document with a file are saved to it without waiting for the app to quit`() = runBlocking {
        val configuration = openedConfiguration(writeDocument(documentFile))

        configuration.updateMeshPoint(1, 1, Offset(0.2f, 0.5f) to 3L)
        configuration.updateMeshPoint(1, 1, Offset(0.3f, 0.6f) to 3L)
        assertTrue(configuration.document.value.modified)

        eventually { saved(documentFile).points[1][1].takeIf { it.x == 0.3f && it.y == 0.6f } }
        eventually { configuration.document.value.takeIf { !it.modified } }
        assertEquals(DocumentColor(red = 120, green = 0, blue = 0), saved(documentFile).points[1][1].color)
    }

    @Test
    fun `an untitled document is never autosaved`() = runBlocking {
        val configuration = loadedConfiguration()

        configuration.updateBlurLevel(0.5f)
        delay(2.seconds)
        configuration.flushPendingSave()

        assertEquals(emptyList<String>(), directory.list()!!.toList())
        assertTrue(configuration.document.value.modified)
    }

    @Test
    fun `the exit save writes a grid size that matches the saved points`() = runBlocking {
        val configuration = openedConfiguration(writeDocument(documentFile))
        configuration.updateMeshPoint(1, 1, Offset(0.2f, 0.5f) to 3L)

        configuration.updateTotalRows(5)
        configuration.flushPendingSave()

        assertEquals(5, saved(documentFile).meshState.rows)
        assertEquals(5, saved(documentFile).points.size)
    }

    @Test
    fun `a failed autosave is retried`() = runBlocking {
        val configuration = openedConfiguration(writeDocument(documentFile))

        directory.setWritable(false)
        try {
            configuration.updateMeshPoint(1, 1, Offset(0.3f, 0.6f) to 3L)
            delay(1500.milliseconds) // The first attempt fails after about a second.
        } finally {
            directory.setWritable(true)
        }

        val point = eventually { saved(documentFile).points[1][1].takeIf { it.x == 0.3f } }
        assertEquals(0.3f, point.x)
    }

    @Test
    fun `a document's grid size follows its points when it opens`() = runBlocking {
        // The file says 5×6, but holds a 3×4 grid.
        val configuration = openedConfiguration(writeDocument(documentFile, MeshState(rows = 5, cols = 6)))

        assertEquals(3 to 4, configuration.meshState.value.let { it.rows to it.cols })
        assertFalse(configuration.document.value.modified, "Reading a file isn't an edit")
    }

    @Test
    fun `a grid regenerated by a resize while loading is saved, even back at the saved size`() = runBlocking {
        val presetsGate = CompletableDeferred<Unit>()
        // An older save left the grid size at 5 rows, but the points at 3.
        val file = writeDocument(documentFile, MeshState(rows = 5, cols = 4))
        val configuration = AppConfiguration(
            FakeDataRepository(
                presetColors = flow { presetsGate.await(); emit(fakePresetColors) },
                documentsDirectory = directory,
                session = Session(lastDocument = file.absolutePath),
            )
        )

        configuration.updateTotalRows(6)
        configuration.updateTotalRows(5)
        presetsGate.complete(Unit)
        configuration.awaitMeshLoaded()
        configuration.flushPendingSave()

        assertEquals(5, configuration.meshPoints.size)
        assertEquals(List(5) { 4 }, saved(file).points.map { it.size })
    }

    @Test
    fun `an edit made while the document loads is kept and saved`() = runBlocking {
        val presetsGate = CompletableDeferred<Unit>()
        val file = writeDocument(documentFile)
        val configuration = AppConfiguration(
            FakeDataRepository(
                presetColors = flow { presetsGate.await(); emit(fakePresetColors) },
                documentsDirectory = directory,
                session = Session(lastDocument = file.absolutePath),
            )
        )

        withContext(Dispatchers.Main) { configuration.updateBlurLevel(0.5f) }
        presetsGate.complete(Unit)
        configuration.awaitMeshLoaded()
        configuration.flushPendingSave()

        assertEquals(0.5f, configuration.meshState.value.blurLevel)
        assertEquals(0.5f, saved(file).meshState.blurLevel)
    }

    @Test
    fun `a document keeps its colors on a machine whose presets have other IDs`() = runBlocking {
        val configuration = loadedConfiguration()
        val red = configuration.addColor(SavedColor(red = 255, green = 0, blue = 0, alpha = 0.5f))
        configuration.updateMeshPoint(0, 0, Offset(0f, 0f) to red)
        configuration.updateMeshPoint(1, 1, Offset(0.25f, 0.5f) to 3L)
        configuration.updateCanvasBackgroundColor(red)
        assertTrue(configuration.saveDocument(documentFile))

        val otherPresets = fakePresetColors.map { it.copy(uid = it.uid + 100) }
        val elsewhere = loadedConfiguration(FakeDataRepository(initialColors = otherPresets, documentsDirectory = directory))
        assertEquals(DocumentChange.Done, elsewhere.openDocument(documentFile))

        val customColor = elsewhere.customColors.value.single()
        assertEquals(listOf(255, 0, 0, 0.5f), listOf(customColor.red, customColor.green, customColor.blue, customColor.alpha))
        assertEquals(customColor.uid, elsewhere.meshPoints[0][0].second)
        assertEquals(customColor.uid, elsewhere.canvasBackgroundColor.value)
        assertEquals(Offset(0.25f, 0.5f) to 103L, elsewhere.meshPoints[1][1])
        assertEquals(DocumentStatus(file = documentFile, modified = false), elsewhere.document.value)
    }

    @Test
    fun `a grid the old app's limit held is saved in format 1 and a larger one in format 2`() = runBlocking {
        val configuration = loadedConfiguration()

        // An 11 × 10 document is written in format 2, and reads back as itself.
        val large = documentWithGrid(11, 10)
        assertEquals(2, fileVersion(large.encode()))
        assertEquals(large, MeshDocument.decode(large.encode()))

        val versions = listOf(11 to 10, 10 to 11, 10 to 10, 64 to 64).map { (rows, cols) ->
            configuration.updateTotalRows(rows)
            configuration.updateTotalCols(cols)
            assertTrue(configuration.saveDocument(documentFile))
            fileVersion(documentFile)
        }
        assertEquals(listOf(2, 2, 1, 2), versions)

        // The format 2 file this build wrote opens again, with its whole grid.
        assertEquals(DocumentChange.Done, configuration.openDocument(documentFile))
        assertEquals(64 to 64, configuration.meshState.value.let { it.rows to it.cols })
        assertEquals(List(64) { 64 }, configuration.meshPoints.map { it.size })
    }

    @Test
    fun `a file with no version key reads as the first format`() = runBlocking {
        val withVersion = defaultDocument().encode()
        val withoutVersion = withVersion.replaceFirst(Regex("\\{\\s*\"version\": \\d+,"), "{")
        assertFalse(withoutVersion.contains("\"version\""))

        // The oldest format, so it reads as the same document the file naming format 1 does.
        assertEquals(1, MeshDocument.versionOf(Json.parseToJsonElement(withoutVersion)))
        assertEquals(MeshDocument.decode(withVersion), MeshDocument.decode(withoutVersion))

        val file = File(directory, "NoVersion.mesh").apply { writeText(withoutVersion) }
        val configuration = openedConfiguration(file)
        assertEquals(DocumentStatus(file = file, modified = false), configuration.document.value)
        assertEquals(3 to 4, configuration.meshState.value.let { it.rows to it.cols })
    }

    @Test
    fun `the format a file names doesn't decide the grid it may hold`() = runBlocking {
        val configuration = loadedConfiguration()
        // A build since the grid limit grew writes a larger grid in format 1, before the format said so.
        val larger = writeDocument(File(directory, "Larger.mesh"), documentWithGrid(11, 11), version = 1)
        assertEquals(1, fileVersion(larger))
        assertEquals(DocumentChange.Done, configuration.openDocument(larger))
        assertEquals(11 to 11, configuration.meshState.value.let { it.rows to it.cols })
        assertEquals(List(11) { 11 }, configuration.meshPoints.map { it.size })

        val smaller = writeDocument(File(directory, "Smaller.mesh"), documentWithGrid(3, 4), version = 2)
        assertEquals(2, fileVersion(smaller))
        assertEquals(DocumentChange.Done, configuration.openDocument(smaller))
        assertEquals(3 to 4, configuration.meshState.value.let { it.rows to it.cols })
    }

    @Test
    fun `a file from a newer version of Mesh doesn't replace the open document`() = runBlocking {
        val configuration = loadedConfiguration()
        // A newer format may not decode as this one at all.
        documentFile.writeText("""{"version": ${MeshDocument.CURRENT_VERSION + 1}, "layers": []}""")

        val error = assertThrows(IllegalArgumentException::class.java) { MeshDocument.decode(documentFile.readText()) }
        assertTrue(error.message!!.contains("needs a newer version"), error.message)
        assertEquals(DocumentChange.Failed, configuration.openDocument(documentFile))
        assertEquals(DocumentStatus(), configuration.document.value)
    }

    @Test
    fun `a file with more rows than the editor allows doesn't open`() = runBlocking {
        val configuration = loadedConfiguration()
        writeOversizedDocument(documentFile)

        val error = assertThrows(IllegalArgumentException::class.java) { MeshDocument.decode(documentFile.readText()) }
        assertTrue(error.message!!.contains("$MIN_GRID_SIZE to $MAX_GRID_SIZE rows and columns; this one has ${MAX_GRID_SIZE + 1} × 2"), error.message)
        assertEquals(DocumentChange.Failed, configuration.openDocument(documentFile))
        assertEquals(DocumentStatus(), configuration.document.value)
        assertEquals(3 to 4, configuration.meshState.value.let { it.rows to it.cols })
    }

    @Test
    fun `opening an unopenable file leaves the open document and the session it recorded alone`() = runBlocking {
        val file = writeDocument(documentFile)
        val repository = FakeDataRepository(
            documentsDirectory = directory,
            session = Session(lastDocument = file.absolutePath),
        )
        val configuration = loadedConfiguration(repository)
        // Opening a document records it, so a failed open that touched the session would be visible here.
        assertEquals(
            Session(lastDocument = file.absolutePath),
            withTimeout(5.seconds) { repository.savedSession.first { it != null } },
        )

        val oversized = writeOversizedDocument(File(directory, "OpenBig.mesh"))
        assertEquals(DocumentChange.Failed, configuration.openDocument(oversized))

        assertEquals(DocumentStatus(file = file, modified = false), configuration.document.value)
        val message = withTimeout(5.seconds) {
            Notifications.notificationFlow.first { it.startsWith("Couldn't open OpenBig.mesh") }
        }
        assertTrue(message.contains("$MAX_GRID_SIZE rows and columns"), message)
        delay(500.milliseconds) // Sessions are written in the background.
        // The session still names the open document, so the next launch reopens it, not the file that failed.
        assertEquals(Session(lastDocument = file.absolutePath), repository.savedSession.value)
    }

    @Test
    fun `a last document that can't open in this version isn't tried again next launch`() = runBlocking {
        val file = writeOversizedDocument(File(directory, "Big.mesh"))
        val bytes = file.readBytes()
        val repository = FakeDataRepository(
            documentsDirectory = directory,
            session = Session(lastDocument = file.absolutePath),
        )
        val configuration = AppConfiguration(repository)
        configuration.awaitMeshLoaded()

        // The file is still rejected rather than trimmed; the app opens the default document instead.
        assertEquals(defaultMeshPointGrid(fakePresetColors.map(SavedColor::uid)), configuration.meshPoints.toList())
        assertEquals(DocumentStatus(), configuration.document.value)
        // The session is cleared, so the next launch doesn't retry a file this version can never open.
        val session = withTimeout(5.seconds) { repository.savedSession.first { it != null } }
        assertEquals(Session(lastDocument = null), session)
        // And nothing was written over the file the user still has.
        assertArrayEquals(bytes, file.readBytes())
        // The user is told why, though the file was read before the UI started collecting notifications.
        val message = withTimeout(5.seconds) {
            Notifications.notificationFlow.first { it.startsWith("Couldn't open Big.mesh") }
        }
        assertTrue(message.contains("$MAX_GRID_SIZE rows and columns"), message)

        // The next launch reads the session this one recorded, so it opens a blank document too. That it
        // records an empty session again is what tells the file apart from one this launch left for retrying.
        val nextLaunchRepository = FakeDataRepository(documentsDirectory = directory, session = session)
        val nextLaunch = loadedConfiguration(nextLaunchRepository)
        assertEquals(DocumentStatus(), nextLaunch.document.value)
        assertEquals(defaultMeshPointGrid(fakePresetColors.map(SavedColor::uid)), nextLaunch.meshPoints.toList())
        assertEquals(Session(lastDocument = null), withTimeout(5.seconds) { nextLaunchRepository.savedSession.first { it != null } })
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `a last document from a newer version is dropped at startup like any other that can't open`() = runBlocking {
        val file = File(directory, "Newer.mesh")
        file.writeText("""{"version": ${MeshDocument.CURRENT_VERSION + 1}, "layers": []}""")
        val bytes = file.readBytes()
        val repository = FakeDataRepository(
            documentsDirectory = directory,
            session = Session(lastDocument = file.absolutePath),
        )
        val configuration = AppConfiguration(repository)
        configuration.awaitMeshLoaded()

        assertEquals(defaultMeshPointGrid(fakePresetColors.map(SavedColor::uid)), configuration.meshPoints.toList())
        assertEquals(DocumentStatus(), configuration.document.value)
        // Deliberately the same rule as for an oversized grid: a file this version can't read is dropped at
        // startup whatever it needs, so one behaviour covers every unopenable document. Reopening the file in a
        // build that knows the format is the way back to it, not keeping it as the last document forever.
        assertEquals(Session(lastDocument = null), withTimeout(5.seconds) { repository.savedSession.first { it != null } })
        assertArrayEquals(bytes, file.readBytes())
        val message = withTimeout(5.seconds) {
            Notifications.notificationFlow.first { it.startsWith("Couldn't open Newer.mesh") }
        }
        assertTrue(message.contains("needs a newer version"), message)
    }

    @Test
    fun `the first launch migrates the saved mesh to a document and later launches reopen it`() = runBlocking {
        val legacyCustomColor = DocumentColor(red = 1, green = 2, blue = 3)
        val legacyMesh = defaultDocument().let { mesh ->
            mesh.copy(
                customColors = listOf(legacyCustomColor),
                points = mesh.points.map { row -> row.map { it.copy(color = legacyCustomColor) } },
            )
        }
        val firstLaunch = FakeDataRepository(documentsDirectory = directory, session = null, legacyMesh = legacyMesh)
        val migrated = loadedConfiguration(firstLaunch)
        val session = firstLaunch.savedSession.first { it != null }

        val migratedFile = File(directory, "My Mesh.mesh")
        assertEquals(Session(lastDocument = migratedFile.absolutePath), session)
        assertEquals(legacyMesh, saved(migratedFile))
        // The migrated grid fits the old limit, so it stays in the format every build reads.
        assertEquals(1, fileVersion(migratedFile))
        assertEquals(DocumentStatus(file = migratedFile, modified = false), migrated.document.value)
        val customColorId = migrated.customColors.value.single().uid
        assertTrue(migrated.meshPoints.flatten().all { it.second == customColorId })

        val nextLaunch = FakeDataRepository(documentsDirectory = directory, session = session, legacyMesh = legacyMesh)
        val reopened = loadedConfiguration(nextLaunch)
        assertEquals(DocumentStatus(file = migratedFile, modified = false), reopened.document.value)
        assertEquals(listOf("My Mesh.mesh"), directory.list()!!.toList())
    }

    @Test
    fun `the first launch still loads legacy points past the old grid limit`() = runBlocking {
        // An old database could hold stray points past the 10 × 10 grid the old app allowed.
        val strayPoints = (0 until 12).flatMap { row ->
            (0 until 12).map { col -> MeshPoint(row = row, col = col, x = col / 11f, y = row / 11f, savedColorId = 1L) }
        }
        val legacyMesh = legacyMeshDocument(strayPoints, savedState = null, colors = fakePresetColors)
        val migrated = loadedConfiguration(FakeDataRepository(documentsDirectory = directory, session = null, legacyMesh = legacyMesh))

        assertEquals(List(10) { 10 }, migrated.meshPoints.map { it.size })
        assertEquals(Offset(9 / 11f, 9 / 11f), migrated.meshPoints[9][9].first)
        assertEquals(File(directory, "My Mesh.mesh"), migrated.document.value.file)
    }

    @Test
    fun `saving an untitled document gives it a file and clears its edited state`() = runBlocking {
        val configuration = loadedConfiguration()

        configuration.updateBlurLevel(0.5f)
        assertEquals(DocumentStatus(file = null, modified = true), configuration.document.value)

        assertTrue(configuration.saveDocument(documentFile))
        assertEquals(DocumentStatus(file = documentFile, modified = false), configuration.document.value)
        assertEquals(0.5f, saved(documentFile).meshState.blurLevel)
    }

    @Test
    fun `opening another document first saves the open one's last edit and starts a fresh history`() = runBlocking {
        val first = writeDocument(documentFile)
        val second = writeDocument(File(directory, "Second.mesh"))
        val configuration = openedConfiguration(first)

        configuration.updateMeshPoint(1, 1, Offset(0.3f, 0.6f) to 3L)
        assertEquals(DocumentChange.Done, configuration.openDocument(second))

        assertEquals(0.3f, saved(first).points[1][1].x, "The edit was still waiting for its autosave")
        assertEquals(DocumentStatus(file = second, modified = false), configuration.document.value)
        assertFalse(configuration.canUndo.value)

        assertEquals(DocumentChange.Done, configuration.newDocument())
        assertEquals(DocumentStatus(), configuration.document.value)
        assertEquals(defaultDocument().points.map { it.size }, configuration.meshPoints.map { it.size })
    }

    @Test
    fun `reopening the open document shows its last edit`() = runBlocking {
        val configuration = openedConfiguration(writeDocument(documentFile))

        configuration.updateBlurLevel(0.5f)
        assertEquals(DocumentChange.Done, configuration.openDocument(documentFile))

        assertEquals(0.5f, configuration.meshState.value.blurLevel, "The file was read before the edit was saved to it")
        assertEquals(0.5f, saved(documentFile).meshState.blurLevel)
    }

    @Test
    fun `an untitled document's edits are discarded only as far as the user agreed`() = runBlocking {
        val configuration = loadedConfiguration()

        configuration.updateBlurLevel(0.5f)
        val agreed = configuration.newDocument() as DocumentChange.UnsavedChanges
        // Such as an agent's edit while the user answers.
        configuration.updateBlurLevel(0.7f)

        val again = configuration.newDocument(discarding = agreed.revision) as DocumentChange.UnsavedChanges
        assertEquals(0.7f, configuration.meshState.value.blurLevel)
        assertEquals(DocumentChange.Done, configuration.newDocument(discarding = again.revision))
        assertEquals(DocumentStatus(), configuration.document.value)
    }

    @Test
    fun `a failed Save As still leaves the edits its document's file is missing protected`() = runBlocking {
        val configuration = openedConfiguration(writeDocument(documentFile))
        val elsewhere = File(directory, "Elsewhere").apply { mkdir() }

        directory.setWritable(false)
        try {
            elsewhere.setWritable(false)
            configuration.updateBlurLevel(0.5f)
            assertFalse(configuration.saveDocument(File(elsewhere, "Copy.mesh")))
            assertTrue(configuration.closeDocument() is DocumentChange.SaveFailed)
            assertTrue(configuration.newDocument() is DocumentChange.SaveFailed)

            // Once a copy holds the edits, the document's new file has everything and nothing is pending.
            elsewhere.setWritable(true)
            assertTrue(configuration.saveDocument(File(elsewhere, "Copy.mesh")))
            assertEquals(DocumentChange.Done, configuration.closeDocument())
            assertEquals(0.5f, saved(File(elsewhere, "Copy.mesh")).meshState.blurLevel)
        } finally {
            directory.setWritable(true)
            elsewhere.setWritable(true)
        }
    }

    @Test
    fun `closing waits for the session to record the document's file`() = runBlocking {
        val repository = FakeDataRepository(documentsDirectory = directory, sessionWriteDelay = 300.milliseconds)
        val configuration = loadedConfiguration(repository)

        assertTrue(configuration.saveDocument(documentFile))
        assertEquals(DocumentChange.Done, configuration.closeDocument())

        assertEquals(Session(lastDocument = documentFile.absolutePath), repository.savedSession.value)
    }

    @Test
    fun `a file with a color channel out of range doesn't open`() = runBlocking {
        val configuration = loadedConfiguration()
        documentFile.writeText(defaultDocument().encode().replaceFirst("\"red\": 40", "\"red\": 300"))

        assertEquals(DocumentChange.Failed, configuration.openDocument(documentFile))
        assertEquals(DocumentStatus(), configuration.document.value)
    }

    @Test
    fun `closing a document whose last edit can't be saved asks first`() = runBlocking {
        val configuration = openedConfiguration(writeDocument(documentFile))

        directory.setWritable(false)
        try {
            configuration.updateBlurLevel(0.5f)
            assertTrue(configuration.closeDocument() is DocumentChange.SaveFailed)
            assertTrue(configuration.document.value.modified)
        } finally {
            directory.setWritable(true)
        }

        assertEquals(DocumentChange.Done, configuration.closeDocument())
        assertEquals(0.5f, saved(documentFile).meshState.blurLevel)
    }

    @Test
    fun `a launch that can't open or migrate its document leaves the session for the next launch to try again`() =
        runBlocking {
            val notADirectory = File(directory, "file").apply { writeText("") }
            val failedMigration = FakeDataRepository(
                documentsDirectory = File(notADirectory, "documents"),
                session = null,
                legacyMesh = defaultDocument(),
            )
            val migrating = loadedConfiguration(failedMigration)
            assertEquals(DocumentStatus(file = null, modified = true), migrating.document.value)

            val missingDocument = FakeDataRepository(
                documentsDirectory = directory,
                session = Session(lastDocument = File(directory, "Missing.mesh").absolutePath),
            )
            loadedConfiguration(missingDocument)

            delay(500.milliseconds) // Sessions are written in the background.
            assertEquals(null, failedMigration.savedSession.value)
            assertEquals(null, missingDocument.savedSession.value)
        }

    private suspend fun loadedConfiguration(
        repository: FakeDataRepository = FakeDataRepository(documentsDirectory = directory),
    ) = AppConfiguration(repository).also { it.awaitMeshLoaded() }

    private suspend fun openedConfiguration(file: File) = loadedConfiguration(
        FakeDataRepository(documentsDirectory = directory, session = Session(lastDocument = file.absolutePath))
    )

    private fun defaultDocument() = meshDocumentOf(
        meshState = MeshState(),
        backgroundColorId = TRANSPARENT_COLOR_ID,
        customColors = emptyList(),
        points = defaultMeshPointGrid(fakePresetColors.map(SavedColor::uid)),
        colors = fakePresetColors,
    )

    private fun writeDocument(file: File, meshState: MeshState = MeshState()) =
        file.apply { writeText(defaultDocument().copy(meshState = meshState).encode()) }

    /** Writes [document] to [file] naming the given format, as an older build or an edited file might. */
    private fun writeDocument(file: File, document: MeshDocument, version: Int) =
        file.apply { writeText(document.encode().replace(Regex("\"version\": \\d+"), "\"version\": $version")) }

    private fun documentWithGrid(rows: Int, cols: Int) = meshDocumentOf(
        meshState = MeshState(rows = rows, cols = cols),
        backgroundColorId = TRANSPARENT_COLOR_ID,
        customColors = emptyList(),
        points = generateMeshPointGrid(rows, cols, fakePresetColors.map(SavedColor::uid)),
        colors = fakePresetColors,
    )

    /** The format named in [text], read as a file's text rather than decoded, which drops the name. */
    private fun fileVersion(text: String) = Json.parseToJsonElement(text)
        .jsonObject.getValue("version").jsonPrimitive.int

    private fun fileVersion(file: File) = fileVersion(file.readText())

    /** A file holding a grid larger than the editor allows, which [MeshDocument.decode] rejects. */
    private fun writeOversizedDocument(file: File) = file.apply {
        val row = List(2) { """{"x": 0.5, "y": 0.5, "color": null}""" }.joinToString(prefix = "[", postfix = "]")
        writeText("""{"points": ${List(MAX_GRID_SIZE + 1) { row }.joinToString(prefix = "[", postfix = "]")}}""")
    }

    private fun saved(file: File) = MeshDocument.decode(file.readText())

    private suspend fun <T : Any> eventually(value: () -> T?): T = withTimeout(10.seconds) {
        var result = value()
        while (result == null) {
            delay(50.milliseconds)
            result = value()
        }
        result
    }
}
