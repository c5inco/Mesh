package des.c5inco.mesh.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import data.DataRepository
import data.DimensionMode
import des.c5inco.mesh.common.toHexStringNoHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.SavedColor
import model.distributedEvenly
import model.findColor
import model.generateMeshPointGrid
import model.replaceColor
import model.toOffsetGrid
import model.toSavedMeshPoints
import model.withUpdatedMeshPoint
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

data class AppUiState(
    val showPoints: Boolean = false,
    val constrainEdgePoints: Boolean = true,
)

private val defaultColorPoints = listOf(
    listOf(
        Offset(0f, 0f) to 1L,
        Offset(.33f, 0f) to 1L,
        Offset(.67f, 0f) to 1L,
        Offset(1f, 0f) to 1L,
    ),
    listOf(
        Offset(0f, .4f) to 2L,
        Offset(.33f, .8f) to 2L,
        Offset(.67f, .8f) to 2L,
        Offset(1f, .4f) to 2L,
    ),
    listOf(
        Offset(0f, 1f) to 3L,
        Offset(.33f, 1f) to 3L,
        Offset(.67f, 1f) to 3L,
        Offset(1f, 1f) to 3L,
    )
)

/**
 * The parts of the document that undo and redo restore. The grid size comes from [points] rather
 * than [MeshState], which changes before a regenerated grid arrives.
 */
private data class EditSnapshot(
    val points: List<List<Pair<Offset, Long>>>,
    val blurLevel: Float,
    val canvasBackgroundColor: Long,
)

class AppConfiguration(
    private val repository: DataRepository,
    showPoints: Boolean = false,
    private var constrainEdgePoints: Boolean = true,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val MAX_BLUR_LEVEL = 40

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
    val customColors = repository.getCustomColors()
    val availableColors = repository.getAllColors()

    /** Palette IDs that new grid points take their colors from; non-empty once the mesh has loaded. */
    private val paletteIds = availableColors
        .map { colors -> colors.map(SavedColor::uid) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    var canvasBackgroundColor = MutableStateFlow(-1L)

    var meshState = MutableStateFlow(repository.loadMeshState())

    /**
     * The grid of points. Like the rest of the mesh state, it is written only on the UI thread:
     * background work such as loading hands its result to the UI thread.
     */
    val meshPoints = mutableStateListOf<List<Pair<Offset, Long>>>()

    private var resizedWhileLoading = false

    private val loadMeshPoints = scope.async {
        val initialMeshPoints = repository.getMeshPoints().first()
        println("Loaded points: $initialMeshPoints")
        val loadedPoints = if (initialMeshPoints.isEmpty()) {
            defaultColorPoints
        } else {
            initialMeshPoints.toOffsetGrid()
        }
        // Resizes need the palette, so the mesh counts as loaded only once it has arrived.
        val colorIds = paletteIds.first { it.isNotEmpty() }

        withContext(Dispatchers.Main) {
            meshPoints.clear()
            meshPoints.addAll(loadedPoints)
            // Apply a resize requested while loading.
            if (resizedWhileLoading) {
                val (rows, cols) = meshState.value.let { it.rows to it.cols }
                if (rows != loadedPoints.size || cols != loadedPoints[0].size) {
                    meshPoints.clear()
                    meshPoints.addAll(generateMeshPointGrid(rows, cols, colorIds))
                }
            }
        }
    }

    /** Suspends until the saved mesh has loaded, so callers never see an empty grid. */
    suspend fun awaitMeshLoaded() = loadMeshPoints.await()


    private val history = EditHistory<EditSnapshot>()
    val canUndo = history.canUndo
    val canRedo = history.canRedo

    val uiState = MutableStateFlow(
        AppUiState(
            showPoints = showPoints,
            constrainEdgePoints = constrainEdgePoints,
        )
    )

    /** Returns the new color's ID. */
    suspend fun addColor(color: SavedColor): Long = repository.addColor(color)

    fun deleteColor(color: SavedColor) {
        repository.deleteColor(color)
    }

    fun updateCanvasWidthMode() {
        meshState.update {
            val current = it.canvasWidthMode
            it.copy(
                canvasWidthMode = if (current == DimensionMode.Fixed) DimensionMode.Fill else DimensionMode.Fixed
            )
        }
    }

    fun updateCanvasWidth(width: Int) {
        meshState.update {
            it.copy(canvasWidth = width)
        }
    }

    fun updateCanvasHeightMode() {
        meshState.update {
            val current = it.canvasHeightMode
            it.copy(
                canvasHeightMode = if (current == DimensionMode.Fixed) DimensionMode.Fill else DimensionMode.Fixed
            )
        }
    }

    fun updateCanvasHeight(height: Int) {
        meshState.update {
            it.copy(canvasHeight = height)
        }
    }

    fun updateBlurLevel(level: Float) = recordEdit(group = "blur") {
        meshState.update {
            it.copy(
                blurLevel = level
            )
        }
    }

    fun updateCanvasBackgroundColor(color: Long) = recordEdit {
        canvasBackgroundColor.update { color }
    }

    fun updateTotalRows(rows: Int) = resize(rows = rows.coerceIn(2, 10), cols = meshState.value.cols)

    fun updateTotalCols(cols: Int) = resize(rows = meshState.value.rows, cols = cols.coerceIn(2, 10))

    private fun resize(rows: Int, cols: Int) {
        if (meshPoints.isEmpty()) {
            // Applied when the mesh finishes loading.
            resizedWhileLoading = true
            meshState.update { it.copy(rows = rows, cols = cols) }
            return
        }
        recordEdit {
            meshState.update { it.copy(rows = rows, cols = cols) }
            if (rows != meshPoints.size || cols != meshPoints[0].size) {
                val generatedPoints = generateMeshPointGrid(rows, cols, paletteIds.value)
                meshPoints.clear()
                meshPoints.addAll(generatedPoints)
            }
        }
    }

    fun saveMeshState() {
        repository.saveMeshState(meshState.value)
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

    fun distributeMeshPointsEvenly() = recordEdit {
        val currentMeshState = meshState.value
        val newPoints = meshPoints.distributedEvenly(
            rows = currentMeshState.rows,
            cols = currentMeshState.cols,
        )
        meshPoints.clear()
        meshPoints.addAll(newPoints)
    }

    suspend fun saveMeshPoints() {
        loadMeshPoints.await()

        // toList() takes an immutable copy, so a concurrent UI edit can't break the iteration.
        val points = meshPoints.toList().toSavedMeshPoints()
        println("Saved: $points")
        repository.saveMeshPoints(points)
    }

    /**
     * Makes points that used a deleted palette color transparent, and resets the canvas background
     * to the default if it used the color. Palette deletion is not undoable, so this rewrites history
     * too rather than letting undo bring back the deleted ID.
     */
    fun removeColorFromMeshPoints(colorToRemove: Long) {
        fun Long.withoutDeletedColor() = if (this == colorToRemove) -1L else this
        history.replaceAll {
            it.copy(
                points = it.points.replaceColor(colorToRemove, replacementColorId = -1L),
                canvasBackgroundColor = it.canvasBackgroundColor.withoutDeletedColor(),
            )
        }
        canvasBackgroundColor.update { it.withoutDeletedColor() }
        val updatedPoints = meshPoints.replaceColor(
            colorId = colorToRemove,
            replacementColorId = -1L,
        )
        updatedPoints.forEachIndexed { index, row ->
            meshPoints[index] = row
        }
    }

    fun exportMeshPointsAsCode() {
        scope.launch {
            val availableColorsAsList = availableColors.first()

            val offsetType = Offset::class.asClassName()
            val colorType = Color::class.asClassName()
            val pairType = Pair::class.asClassName().parameterizedBy(offsetType, colorType)
            val innerListType = List::class.asClassName().parameterizedBy(pairType)
            val outerListType = List::class.asClassName().parameterizedBy(innerListType)

            val listInitializer = CodeBlock.builder()
                .add("listOf(\n")
                .indent()

            meshPoints.forEachIndexed { _, innerList ->
                listInitializer.add("listOf(\n")
                listInitializer.indent()

                innerList.forEachIndexed { _, pair ->
                    val hexString = availableColorsAsList.findColor(pair.second)
                        .toHexStringNoHash(includeAlpha = true)
                    listInitializer.add(
                        "Offset(%Lf, %Lf) to Color(0x%L),\n",
                        pair.first.x,
                        pair.first.y,
                        hexString
                    )
                }

                listInitializer.unindent()
                listInitializer.add("),\n")
            }

            listInitializer.unindent()
            listInitializer.add(")")

            val codeSpec = PropertySpec.builder("colorPoints", outerListType)
                .initializer(listInitializer.build())
                .build()

            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(codeSpec.toString()), null)
            println(codeSpec.toString())
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
    )

    /** Runs [edit] and adds an undo step if it changed anything. */
    private inline fun recordEdit(group: String? = null, edit: () -> Unit) {
        // Before the saved mesh loads there is nothing to go back to.
        if (meshPoints.isEmpty()) return edit()

        val before = snapshot()
        edit()
        if (snapshot() != before) history.record(before, group)
    }

    private fun restore(snapshot: EditSnapshot) {
        meshState.update {
            it.copy(rows = snapshot.points.size, cols = snapshot.points[0].size, blurLevel = snapshot.blurLevel)
        }
        canvasBackgroundColor.value = snapshot.canvasBackgroundColor
        meshPoints.clear()
        meshPoints.addAll(snapshot.points)
    }

    fun toggleShowingPoints() {
        uiState.update {
            it.copy(showPoints = !it.showPoints)
        }
    }

    fun toggleConstrainingEdgePoints() {
        constrainEdgePoints = !constrainEdgePoints
        uiState.update {
            it.copy(constrainEdgePoints = constrainEdgePoints)
        }
    }
}
