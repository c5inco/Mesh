package des.c5inco.mesh.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.MeshGradientPainter
import androidx.compose.ui.graphics.TileMode
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import data.DataRepository
import data.DimensionMode
import data.FIRST_CUSTOM_COLOR_ID
import data.MAX_GRID_SIZE
import data.MIN_GRID_SIZE
import data.MeshDocument
import data.MeshState
import data.OpenedMesh
import data.Session
import data.TRANSPARENT_COLOR_ID
import data.meshDocumentOf
import data.open
import des.c5inco.mesh.common.toHexStringNoHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import model.SavedColor
import model.defaultMeshPointGrid
import model.distributedEvenly
import model.findColor
import model.generateMeshPointGrid
import model.replaceColor
import model.withUpdatedMeshPoint
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

data class AppUiState(
    val showPoints: Boolean = false,
    val constrainEdgePoints: Boolean = true,
)

/** The open document: the file it was opened from or saved to, if any, and whether it has edits the file lacks. */
data class DocumentStatus(
    val file: File? = null,
    val modified: Boolean = false,
) {
    val name: String get() = file?.nameWithoutExtension ?: "Untitled"
}

/** Which document is open and how many edits it has had, so an action can tell whether it changed since the user decided. */
data class DocumentRevision(val generation: Int, val edits: Long)

/** What became of a request to close the open document or replace it with another. */
sealed interface DocumentChange {
    data object Done : DocumentChange

    /** It didn't happen, and the user was told why, such as a file that couldn't be read. */
    data object Failed : DocumentChange

    /**
     * The document has edits no file holds, and the request didn't agree to discard them. To discard them,
     * repeat the request with [revision], taken with this check, so that a later edit is never discarded unasked.
     */
    data class UnsavedChanges(val revision: DocumentRevision) : DocumentChange

    /** The open document's last edits couldn't be written to its file; [revision] works as for [UnsavedChanges]. */
    data class SaveFailed(val message: String, val revision: DocumentRevision) : DocumentChange
}

/** Moves and recolors the point at [row] and [col]; see [AppConfiguration.updateMeshPoints]. */
data class MeshPointEdit(val row: Int, val col: Int, val point: Pair<Offset, Long>)

/** The parts of the document that undo and redo restore. The grid size comes from [points]. */
private data class EditSnapshot(
    val points: List<List<Pair<Offset, Long>>>,
    val blurLevel: Float,
    val canvasBackgroundColor: Long,
    val customColors: List<SavedColor>,
)

/**
 * Holds the open document and the app's UI state. Points, canvas settings, the background color, and
 * custom colors belong to the document; preset colors are global and come from [DataRepository].
 * The document is written only on the UI thread.
 */
class AppConfiguration(
    private val repository: DataRepository,
    showPoints: Boolean = false,
    constrainEdgePoints: Boolean = true,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val MAX_BLUR_LEVEL = 40
        const val MIGRATED_DOCUMENT_NAME = "My Mesh"
        private val AUTOSAVE_DELAY = 1.seconds

        fun saveImage(image: BufferedImage, scale: Int) {
            try {
                val desktopPath = System.getProperty("user.home") + File.separator + "Desktop"
                val scaleSuffix = if (scale == 1) "" else "@${scale}x"
                val filename = "mesh-export$scaleSuffix.png"
                val file = File(desktopPath, filename) // You can change the filename and extension

                ImageIO.write(image, "png", file)
                Notifications.send("🖼 Exported $filename to ${file.absolutePath.substringBeforeLast("/")}")
                println("Image saved to: ${file.absolutePath}")
            } catch (e: Exception) {
                println("Error saving image: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    val presetColors = repository.getPresetColors()
    val customColors = MutableStateFlow(emptyList<SavedColor>())
    val availableColors = combine(presetColors, customColors) { presets, custom -> presets + custom }

    /** The presets the open document's colors were resolved against; empty until the first document loads. */
    @Volatile
    private var loadedPresetColors = emptyList<SavedColor>()

    /** Every color the open document can use. Call on the UI thread. */
    fun palette(): List<SavedColor> = loadedPresetColors + customColors.value

    /** Palette IDs that new grid points take their colors from; non-empty once the mesh has loaded. */
    private fun paletteIds() = palette().map(SavedColor::uid)

    /** Custom color IDs are never reused, so an ID from a closed document can't name a color in the open one. */
    private val nextCustomColorId = AtomicLong(FIRST_CUSTOM_COLOR_ID)

    val canvasBackgroundColor = MutableStateFlow(TRANSPARENT_COLOR_ID)

    private val session = repository.loadSession()
    private val startupFile = session?.lastDocument?.let(::File)

    /** The document to reopen, read now so that edits made while it loads apply on top of its settings. */
    private val startupDocument = startupFile?.let(::readDocumentOrNull)

    val meshState = MutableStateFlow(
        startupDocument?.meshState ?: if (session == null) repository.loadLegacyMeshState() else MeshState()
    )

    /**
     * The grid of points. Like the rest of the document, it is written only on the UI thread:
     * background work such as loading hands its result to the UI thread.
     */
    val meshPoints = mutableStateListOf<List<Pair<Offset, Long>>>()

    val document = MutableStateFlow(DocumentStatus())

    /** Counts edits, so a save can tell whether the document changed after the save captured it. */
    private val edits = AtomicLong()

    /** Bumped whenever another document replaces the open one, so a save of the old one can't touch the new one. */
    private val generation = AtomicInteger()

    /** The size the canvas fills in the window, which a document in Fill mode takes instead of its saved size. */
    private var measuredCanvasSize: Pair<Int, Int>? = null

    /** Suspends until the startup document has loaded, so callers never see an empty grid or palette. */
    suspend fun awaitMeshLoaded() = loadStartupDocument.await()

    /** The open document and the file to autosave it to, captured together on the UI thread after each edit. */
    private class PendingSave(val document: MeshDocument, val file: File, val generation: Int, val edits: Long)

    @Volatile
    private var latestSave: PendingSave? = null
    @Volatile
    private var lastWrittenSave: PendingSave? = null // Written under saveMutex.
    private val saveMutex = Mutex()
    /** Buffered so no request is lost before collection starts. */
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)

    /** The session to record for the next launch; only the latest matters. */
    private val sessions = MutableStateFlow<Session?>(null)

    init {
        // Save soon after edits stop, so a crash loses at most the last moment of work.
        scope.launch {
            @OptIn(FlowPreview::class)
            saveRequests.receiveAsFlow().debounce(AUTOSAVE_DELAY).collect {
                try {
                    flushPendingSave()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    println("Autosave failed, retrying: ${e.message}")
                    delay(AUTOSAVE_DELAY)
                    saveRequests.trySend(Unit)
                }
            }
        }
        scope.launch {
            sessions.filterNotNull().collect { flushSession() }
        }
    }

    @Volatile
    private var writtenSession: Session? = null // Written under sessionMutex.
    private val sessionMutex = Mutex()

    /**
     * Writes the latest session, if it hasn't been written yet. Closing and exiting wait for it, so the next
     * launch reopens the document that was open last. A failed write is reported and tried again next time.
     */
    suspend fun flushSession() = sessionMutex.withLock {
        val session = sessions.value ?: return@withLock
        if (session == writtenSession) return@withLock
        try {
            withContext(Dispatchers.IO) { repository.saveSession(session) }
            writtenSession = session
        } catch (e: IOException) {
            println("Error saving session: ${e.message}")
        }
    }

    /** Call on the UI thread after the document changes: marks it edited and schedules its autosave. */
    private fun markEdited() {
        // The loaded document replaces the mesh, so the load applies edits made before then itself.
        if (meshPoints.isEmpty()) {
            editedWhileLoading = true
            return
        }
        edits.incrementAndGet()
        document.update { it.copy(modified = true) }
        captureSave()
    }

    /** Captures the open document for autosave. An untitled document has no file to save to. */
    private fun captureSave() {
        val file = document.value.file ?: return
        latestSave = PendingSave(currentDocument(), file, generation.get(), edits.get())
        saveRequests.trySend(Unit)
    }

    private fun currentDocument() = meshDocumentOf(
        meshState = meshState.value,
        backgroundColorId = canvasBackgroundColor.value,
        customColors = customColors.value,
        points = meshPoints.toList(),
        colors = palette(),
    )

    /**
     * Writes the latest captured document, if any. Safe from any thread, including a shutdown hook:
     * it never waits on the UI thread, and writes never interleave.
     */
    suspend fun flushPendingSave() = saveMutex.withLock {
        // Keep going until nothing newer was captured during a write.
        while (true) {
            val save = latestSave
            if (save == null || save === lastWrittenSave) return@withLock
            writeDocument(save.document, save.file)
            lastWrittenSave = save
            // The file now holds the document, unless it was edited or replaced since the capture.
            document.update {
                val current = generation.get() == save.generation && it.file == save.file && edits.get() == save.edits
                if (current) it.copy(modified = false) else it
            }
        }
    }

    /**
     * Writes the open document's pending autosave, if any. Call on the UI thread right before replacing the
     * document, with no suspension in between, so that no edit slips in after the write and is dropped.
     */
    private suspend fun finishPendingSave() {
        while (latestSave.let { it != null && it !== lastWrittenSave }) flushPendingSave()
    }

    private val history = EditHistory<EditSnapshot>()
    val canUndo = history.canUndo
    val canRedo = history.canRedo

    // Edits made before the startup document loads, which the load applies on top of it.
    private var editedWhileLoading = false
    private var resizedWhileLoading = false
    private var backgroundSetWhileLoading = false

    // Declared after the fields it touches, which it may reach before the constructor finishes.
    private val loadStartupDocument = scope.async {
        // Opening a document needs the presets, so the mesh counts as loaded only once they have arrived.
        val presets = repository.getPresetColors().first { it.isNotEmpty() }
        val startup = when {
            startupDocument != null -> StartupDocument(startupDocument, startupFile)
            session == null -> migrateLegacyMesh()
            // A last document that can't be read now, such as one on a drive that isn't mounted, is tried again next launch.
            else -> StartupDocument(null, null, recordSession = startupFile == null)
        }
        withContext(Dispatchers.Main) {
            loadedPresetColors = presets
            showStartupDocument(startup)
        }
    }

    private class StartupDocument(
        val document: MeshDocument?,
        val file: File?,
        val modified: Boolean = false,
        /** False to keep the last session, so the next launch tries again what this one couldn't open or migrate. */
        val recordSession: Boolean = true,
    )

    /** Saves the single mesh from before documents existed as the first document, so upgrading loses nothing. */
    private suspend fun migrateLegacyMesh(): StartupDocument {
        val legacyMesh = repository.loadLegacyMesh() ?: return StartupDocument(null, null)
        val file = generateSequence(1) { it + 1 }
            .map { n -> if (n == 1) MIGRATED_DOCUMENT_NAME else "$MIGRATED_DOCUMENT_NAME $n" }
            .map { name -> File(repository.documentsDirectory, "$name.${MeshDocument.FILE_EXTENSION}") }
            .first { !it.exists() }
        return try {
            writeDocument(legacyMesh, file)
            StartupDocument(legacyMesh, file)
        } catch (e: IOException) {
            // Keep the mesh open as unsaved changes rather than lose it, and migrate it again next launch.
            println("Error saving the migrated mesh: ${e.message}")
            StartupDocument(legacyMesh, null, modified = true, recordSession = false)
        }
    }

    private fun showStartupDocument(startup: StartupDocument) {
        val editedState = meshState.value
        val colorsAddedWhileLoading = customColors.value
        val backgroundWhileLoading = canvasBackgroundColor.value
        show(startup.document, startup.file, startup.modified, startup.recordSession)
        if (!editedWhileLoading) return

        // Colors added while loading have IDs that no color in the document uses.
        customColors.update { it + colorsAddedWhileLoading }
        if (backgroundSetWhileLoading) canvasBackgroundColor.value = backgroundWhileLoading
        // A resize requested while loading, as an undo step back to the loaded grid and blur. The snapshot is
        // taken while the loaded state is still in place, so undoing the resize restores the loaded blur too.
        val resized = resizedWhileLoading && (editedState.rows != meshPoints.size || editedState.cols != meshPoints[0].size)
        val before = if (resized) snapshot() else null
        meshState.value = editedState.copy(rows = meshPoints.size, cols = meshPoints[0].size)
        if (before != null) {
            val generatedPoints = generateMeshPointGrid(editedState.rows, editedState.cols, paletteIds())
            meshPoints.clear()
            meshPoints.addAll(generatedPoints)
            meshState.update { it.copy(rows = editedState.rows, cols = editedState.cols) }
            history.record(before, group = null)
        }
        markEdited()
    }

    /** Replaces the open document. Call on the UI thread once the presets have loaded. */
    private fun show(replacement: MeshDocument?, file: File?, modified: Boolean = false, recordSession: Boolean = true) {
        val opened = replacement?.open(loadedPresetColors, nextCustomColorId::getAndIncrement)
            ?: OpenedMesh(MeshState(), TRANSPARENT_COLOR_ID, emptyList(), defaultMeshPointGrid(paletteIds()))
        generation.incrementAndGet()
        // In Fill mode the canvas keeps the size it fills in the window, not the size saved with the file.
        val measured = measuredCanvasSize
        meshState.value = opened.meshState.let { state ->
            if (measured == null) return@let state
            state.copy(
                canvasWidth = if (state.canvasWidthMode == DimensionMode.Fill) measured.first else state.canvasWidth,
                canvasHeight = if (state.canvasHeightMode == DimensionMode.Fill) measured.second else state.canvasHeight,
            )
        }
        canvasBackgroundColor.value = opened.backgroundColorId
        customColors.value = opened.customColors
        meshPoints.clear()
        meshPoints.addAll(opened.points)
        history.clear()
        document.value = DocumentStatus(file, modified)
        if (recordSession) sessions.value = Session(lastDocument = file?.absolutePath)
    }

    /** The open document and how far it has been edited. */
    private fun revision() = DocumentRevision(generation.get(), edits.get())

    /**
     * Replaces the open document with a new, untitled one. Edits no file holds are discarded only when
     * [discarding] is the revision from the [DocumentChange.UnsavedChanges] or [DocumentChange.SaveFailed]
     * the user agreed to.
     */
    suspend fun newDocument(discarding: DocumentRevision? = null): DocumentChange = replaceDocument(null, discarding)

    /** Opens the document in [file], replacing the open one, with unsaved changes treated as by [newDocument]. */
    suspend fun openDocument(file: File, discarding: DocumentRevision? = null): DocumentChange =
        replaceDocument(file, discarding)

    /**
     * Gets the open document ready for the window to close: writes its last edits to its file and checks
     * for changes that would be lost, as [newDocument] does. On [DocumentChange.Done] nothing is left to save.
     */
    suspend fun closeDocument(discarding: DocumentRevision? = null): DocumentChange {
        // Before the startup document loads there is nothing to save, and waiting for it could keep the app from quitting.
        if (!loadStartupDocument.isCompleted) return DocumentChange.Done
        // Closing doesn't change the session, so record it first, such as after a Save As just before closing.
        flushSession()
        return withContext(Dispatchers.Main) { prepareToClose(discarding) ?: DocumentChange.Done }
    }

    private suspend fun replaceDocument(file: File?, discarding: DocumentRevision?): DocumentChange {
        loadStartupDocument.await()
        return withContext(Dispatchers.Main) {
            prepareToClose(discarding)?.let { return@withContext it }
            // Read only now, after the open document's last save, so reopening it shows what was just written.
            // The file is small, and reading it here leaves no moment for an edit to slip in before the switch.
            val replacement = file?.let { readDocumentOrNull(it) ?: return@withContext DocumentChange.Failed }
            show(replacement, file)
            DocumentChange.Done
        }
    }

    /**
     * Writes the open document's last edits and checks that closing it loses nothing the caller didn't agree
     * to lose with [discarding]. Returns why it can't close, or null. Call on the UI thread, and close the
     * document without suspending after a null, so no edit slips in between.
     */
    private suspend fun prepareToClose(discarding: DocumentRevision?): DocumentChange? {
        try {
            finishPendingSave()
        } catch (e: IOException) {
            if (discarding != revision()) return DocumentChange.SaveFailed(e.message ?: e.toString(), revision())
            // The user chose to lose the edits the file is missing.
            latestSave = null
        }
        // An untitled document has no file to hold its edits; a saved one is edited only until a save writes them.
        if (document.value.modified && discarding != revision()) return DocumentChange.UnsavedChanges(revision())
        return null
    }

    /** Writes the open document to [file], which becomes its file; returns false and says why when it can't. */
    suspend fun saveDocument(file: File): Boolean {
        loadStartupDocument.await()
        return withContext(Dispatchers.Main) {
            // The document to save is the one open now, even if another replaces it while this waits.
            val target = generation.get()
            try {
                var currentFileFailed = false
                try {
                    // The current file gets the last edits too, even when this saves the document elsewhere.
                    finishPendingSave()
                } catch (e: IOException) {
                    // Saving elsewhere still keeps those edits, in the new file.
                    if (file == document.value.file) throw e
                    currentFileFailed = true
                }
                if (generation.get() != target) return@withContext false
                val save = PendingSave(currentDocument(), file, target, edits.get())
                // The current file's unwritten save stays pending until the new file holds its edits,
                // so that if this write fails too, closing the document still asks first.
                val unwritten = latestSave.takeIf { currentFileFailed }
                saveMutex.withLock {
                    writeDocument(save.document, file)
                    if (unwritten != null && latestSave === unwritten) lastWrittenSave = unwritten
                }
                // Another document may have opened while the file was being written.
                if (generation.get() == save.generation) {
                    document.value = DocumentStatus(file, modified = edits.get() != save.edits)
                    sessions.value = Session(lastDocument = file.absolutePath)
                    // Edits made during the write belong in the document's new file.
                    if (document.value.modified) captureSave()
                }
                Notifications.send("💾 Saved ${file.name}")
                true
            } catch (e: IOException) {
                Notifications.send("Couldn't save ${file.name}: ${e.message}")
                false
            }
        }
    }

    /** Reads a document, or says why it can't and returns null. */
    private fun readDocumentOrNull(file: File): MeshDocument? = try {
        MeshDocument.decode(file.readText())
    } catch (e: IOException) {
        Notifications.send("Couldn't open ${file.name}: ${e.message}")
        null
    } catch (e: IllegalArgumentException) {
        Notifications.send("Couldn't open ${file.name}: ${e.message}")
        null
    }

    /** Writes through a temporary file, so a failed save leaves the previous file intact. */
    private suspend fun writeDocument(document: MeshDocument, file: File) = withContext(Dispatchers.IO) {
        val directory = file.absoluteFile.parentFile
        directory.mkdirs()
        val temporaryFile = File(directory, ".${file.name}.tmp")
        temporaryFile.writeText(document.encode())
        Files.move(
            temporaryFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    val uiState = MutableStateFlow(
        AppUiState(
            showPoints = showPoints,
            constrainEdgePoints = constrainEdgePoints,
        )
    )

    /** Whether point edits keep edge points on their edge; [uiState] holds it, and every edit reads it from here. */
    val constrainEdgePoints: Boolean get() = uiState.value.constrainEdgePoints

    /** Adds a custom color to the open document and returns its ID. Call on the UI thread. */
    fun addColor(color: SavedColor): Long {
        val id = nextCustomColorId.getAndIncrement()
        recordEdit { customColors.update { it + color.copy(uid = id, preset = false) } }
        return id
    }

    /**
     * Removes a custom color from the document. Points that used it become transparent, and the canvas
     * background resets to the default if it used it. Undo brings the color back with them.
     */
    fun deleteColor(color: SavedColor) = recordEdit {
        customColors.update { colors -> colors.filterNot { it.uid == color.uid } }
        canvasBackgroundColor.update { if (it == color.uid) TRANSPARENT_COLOR_ID else it }
        val updatedPoints = meshPoints.replaceColor(
            colorId = color.uid,
            replacementColorId = TRANSPARENT_COLOR_ID,
        )
        updatedPoints.forEachIndexed { index, row ->
            meshPoints[index] = row
        }
    }

    fun updateCanvasWidthMode() = editMeshState {
        it.copy(canvasWidthMode = if (it.canvasWidthMode == DimensionMode.Fixed) DimensionMode.Fill else DimensionMode.Fixed)
    }

    fun updateCanvasWidth(width: Int) = editMeshState { it.copy(canvasWidth = width) }

    fun updateCanvasHeightMode() = editMeshState {
        it.copy(canvasHeightMode = if (it.canvasHeightMode == DimensionMode.Fixed) DimensionMode.Fill else DimensionMode.Fixed)
    }

    fun updateCanvasHeight(height: Int) = editMeshState { it.copy(canvasHeight = height) }

    /** Records the size the canvas fills in the window. Not an edit: it follows the window. */
    fun updateMeasuredCanvasSize(width: Int, height: Int) {
        measuredCanvasSize = width to height
        meshState.update { it.copy(canvasWidth = width, canvasHeight = height) }
    }

    /** Changes canvas settings that undo doesn't cover. */
    private fun editMeshState(transform: (MeshState) -> MeshState) {
        val before = meshState.getAndUpdate(transform)
        if (meshState.value != before) markEdited()
    }

    fun updateBlurLevel(level: Float) {
        // Grouped so the steps of a slider drag merge into one undo step.
        editGrid(rows = null, cols = null, blur = level.coerceIn(0f, 1f), group = "blur")
    }

    fun updateCanvasBackgroundColor(color: Long) = recordEdit {
        if (meshPoints.isEmpty()) backgroundSetWhileLoading = true
        canvasBackgroundColor.update { color }
    }

    fun updateTotalRows(rows: Int) {
        editGrid(rows = rows.coerceIn(MIN_GRID_SIZE, MAX_GRID_SIZE), cols = null, blur = null, group = null)
    }

    fun updateTotalCols(cols: Int) {
        editGrid(rows = null, cols = cols.coerceIn(MIN_GRID_SIZE, MAX_GRID_SIZE), blur = null, group = null)
    }

    /**
     * Sets the grid size and blur, leaving null ones as they are, as a single undo step. A new size replaces
     * the points with an evenly spaced grid. Returns whether the mesh changed. Call on the UI thread with rows
     * and columns from [MIN_GRID_SIZE] to [MAX_GRID_SIZE] and blur from 0 to 1.
     */
    fun setGrid(rows: Int?, cols: Int?, blur: Float?): Boolean = editGrid(rows, cols, blur, group = null)

    private fun editGrid(rows: Int?, cols: Int?, blur: Float?, group: String?): Boolean {
        // Checked before anything changes: a size outside the range can't be rebuilt or saved as a document.
        require(rows == null || rows in MIN_GRID_SIZE..MAX_GRID_SIZE) { "rows must be from $MIN_GRID_SIZE to $MAX_GRID_SIZE, not $rows" }
        require(cols == null || cols in MIN_GRID_SIZE..MAX_GRID_SIZE) { "cols must be from $MIN_GRID_SIZE to $MAX_GRID_SIZE, not $cols" }
        require(blur == null || blur in 0f..1f) { "blur must be from 0 to 1, not $blur" }
        if (meshPoints.isEmpty()) {
            // Applied when the mesh finishes loading.
            if (rows != null || cols != null) resizedWhileLoading = true
            editedWhileLoading = true
            val before = meshState.getAndUpdate {
                it.copy(rows = rows ?: it.rows, cols = cols ?: it.cols, blurLevel = blur ?: it.blurLevel)
            }
            return meshState.value != before
        }
        return recordEdit(group) {
            val state = meshState.updateAndGet {
                it.copy(rows = rows ?: it.rows, cols = cols ?: it.cols, blurLevel = blur ?: it.blurLevel)
            }
            // Straight to the final size, so the points are rebuilt at most once.
            if (state.rows != meshPoints.size || state.cols != meshPoints[0].size) {
                val generatedPoints = generateMeshPointGrid(state.rows, state.cols, paletteIds())
                meshPoints.clear()
                meshPoints.addAll(generatedPoints)
            }
        }
    }

    fun updateMeshPoint(row: Int, col: Int, point: Pair<Offset, Long>) = recordEdit(group = "point $row,$col") {
        val updatedPoints = meshPoints.withUpdatedMeshPoint(
            row = row,
            col = col,
            point = point,
            constrainEdges = constrainEdgePoints,
        )
        meshPoints[row] = updatedPoints[row]
    }

    /**
     * Applies several point edits, in order, as a single undo step, and returns whether the mesh changed.
     * Call on the UI thread once the mesh has loaded; every edit must name a point in the grid.
     */
    fun updateMeshPoints(edits: List<MeshPointEdit>): Boolean {
        require(meshPoints.isNotEmpty()) { "The mesh hasn't loaded yet" }
        edits.forEach { (row, col) ->
            require(row in meshPoints.indices && col in meshPoints[row].indices) { "No point at row $row, col $col" }
        }
        return recordEdit {
            var points = meshPoints.toList()
            edits.forEach { (row, col, point) ->
                points = points.withUpdatedMeshPoint(row = row, col = col, point = point, constrainEdges = constrainEdgePoints)
            }
            points.forEachIndexed { row, updatedRow -> if (meshPoints[row] != updatedRow) meshPoints[row] = updatedRow }
        }
    }

    fun distributeMeshPointsEvenly() = recordEdit {
        val currentMeshState = meshState.value
        val newPoints = meshPoints.distributedEvenly(
            rows = currentMeshState.rows,
            cols = currentMeshState.cols,
        )
        meshPoints.clear()
        meshPoints.addAll(newPoints)
    }

    fun exportMeshPointsAsCode() {
        scope.launch {
            loadStartupDocument.await()
            // Read on the UI thread, which owns the mesh, so a grid being replaced can't be caught half-written.
            val (colorPoints, blurLevel, availableColorsAsList) = withContext(Dispatchers.Main) {
                Triple(meshPoints.toList(), meshState.value.blurLevel, palette())
            }
            val blurRadius = (blurLevel * MAX_BLUR_LEVEL).roundToInt().toFloat()

            val painterType = MeshGradientPainter::class.asClassName()
            val initializer = CodeBlock.builder()
                .add(
                    "%T(rows = %L, columns = %L, hasBicubicColor = true) {\n",
                    painterType,
                    colorPoints.lastIndex,
                    colorPoints.first().lastIndex,
                )
                .indent()

            colorPoints.forEachIndexed { row, points ->
                points.forEachIndexed { column, pair ->
                    val hexString = availableColorsAsList.findColor(pair.second)
                        .toHexStringNoHash(includeAlpha = true)
                    initializer.add(
                        "setVertex(%L, %L, %T(%Lf, %Lf), %T(0x%L))\n",
                        row,
                        column,
                        Offset::class,
                        pair.first.x,
                        pair.first.y,
                        Color::class,
                        hexString,
                    )
                }
            }

            initializer.unindent()
            initializer.add("}")

            val codeSpec = PropertySpec.builder("meshGradient", painterType)
                .initializer(initializer.build())
                .build()

            val modifierInitializer = CodeBlock.builder()
                .add("%T", Modifier::class)
            if (blurRadius > 0f) {
                modifierInitializer
                    .add("\n")
                    .indent()
                    .beginControlFlow(".%M", MemberName("androidx.compose.ui.graphics", "graphicsLayer"))
                    .addStatement(
                        "renderEffect = %T(%Lf, %Lf, %T.Clamp)",
                        BlurEffect::class,
                        blurRadius,
                        blurRadius,
                        TileMode::class,
                    )
                    .endControlFlow()
                    .unindent()
            }
            modifierInitializer.indent()
                .add(".%M(meshGradient, sizeToIntrinsics = false)", MemberName("androidx.compose.ui.draw", "paint"))
                .unindent()

            val code = FileSpec.builder("", "MeshGradient")
                .addProperty(codeSpec)
                .addProperty(
                    PropertySpec.builder("meshGradientModifier", Modifier::class)
                        .initializer(modifierInitializer.build())
                        .build(),
                )
                .build()
                .toString()

            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(code), null)
            println(code)
        }
    }

    fun undo() {
        history.undo(snapshot())?.let(::restore)
    }

    fun redo() {
        history.redo(snapshot())?.let(::restore)
    }

    private fun snapshot() = EditSnapshot(
        points = meshPoints.toList(),
        blurLevel = meshState.value.blurLevel,
        canvasBackgroundColor = canvasBackgroundColor.value,
        customColors = customColors.value,
    )

    /**
     * Runs [edit] and adds an undo step if it changed anything. Returns whether it did; an edit before the
     * document loads counts as a change, since the load applies it.
     */
    private inline fun recordEdit(group: String? = null, edit: () -> Unit): Boolean {
        // Before the document loads there is nothing to go back to.
        if (meshPoints.isEmpty()) {
            edit()
            editedWhileLoading = true
            return true
        }

        val before = snapshot()
        edit()
        if (snapshot() == before) return false
        history.record(before, group)
        markEdited()
        return true
    }

    private fun restore(snapshot: EditSnapshot) {
        meshState.update {
            it.copy(rows = snapshot.points.size, cols = snapshot.points[0].size, blurLevel = snapshot.blurLevel)
        }
        canvasBackgroundColor.value = snapshot.canvasBackgroundColor
        customColors.value = snapshot.customColors
        meshPoints.clear()
        meshPoints.addAll(snapshot.points)
        markEdited()
    }

    fun toggleShowingPoints() {
        uiState.update {
            it.copy(showPoints = !it.showPoints)
        }
    }

    fun toggleConstrainingEdgePoints() {
        uiState.update {
            it.copy(constrainEdgePoints = !it.constrainEdgePoints)
        }
    }
}
