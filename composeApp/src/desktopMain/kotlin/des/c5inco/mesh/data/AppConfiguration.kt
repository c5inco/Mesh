package des.c5inco.mesh.data

import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import data.DimensionMode
import des.c5inco.mesh.common.toHexStringNoHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import model.SavedColor
import model.findColor
import model.toOffsetGrid
import model.toSavedMeshPoints
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

class AppConfiguration(
    private val repository: AppDataRepository,
    showPoints: Boolean = false,
    private var constrainEdgePoints: Boolean = true,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val undoRedoManager = UndoRedoManager()

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

    var canvasBackgroundColor = MutableStateFlow(-1L)

    var meshState = MutableStateFlow(repository.loadMeshState())

    var meshPoints = mutableListOf<List<Pair<Offset, Long>>>().toMutableStateList()
        private set

    init {
        scope.launch {
            val initialMeshPoints = repository.getMeshPoints().first()
            println("Loaded points: $initialMeshPoints")
            meshPoints = if (initialMeshPoints.isEmpty()) {
                    defaultColorPoints
                } else {
                    initialMeshPoints.toOffsetGrid()
                }.toMutableStateList()
        }
    }

    val uiState = MutableStateFlow(
        AppUiState(
            showPoints = showPoints,
            constrainEdgePoints = constrainEdgePoints,
        )
    )

    fun addColor(color: SavedColor) {
        repository.addColor(color)
    }

    fun deleteColor(color: SavedColor) {
        repository.deleteColor(color)
    }

    fun updateCanvasWidthMode() {
        saveStateForUndo()
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
        saveStateForUndo()
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

    fun updateBlurLevel(level: Float) {
        saveStateForUndo()
        meshState.update {
            it.copy(
                blurLevel = level
            )
        }
    }

    fun updateCanvasBackgroundColor(color: Long) {
        saveStateForUndo()
        canvasBackgroundColor.update { color }
    }

    fun updateTotalRows(rows: Int) {
        saveStateForUndo()
        meshState.update {
            it.copy(rows = rows.coerceIn(2, 10))
        }
        generateMeshPoints()
    }

    fun updateTotalCols(cols: Int) {
        saveStateForUndo()
        meshState.update {
            it.copy(cols = cols.coerceIn(2, 10))
        }
        generateMeshPoints()
    }

    fun saveMeshState() {
        repository.saveMeshState(meshState.value)
    }

    private fun generateMeshPoints() {
        scope.launch {
            meshPoints.clear()
            val allColors = availableColors.first()
            val currentMeshState = meshState.value

            repeat(currentMeshState.rows) { rowIdx ->
                val newColorIndex =
                    allColors[rowIdx % allColors.size].uid

                val newPoints = mutableListOf<Pair<Offset, Long>>()

                // Calculate the Y position for this row
                val yPosition = rowIdx.toFloat() / (currentMeshState.rows - 1)

                // Iterate through columns to create points
                repeat(currentMeshState.cols) { colIdx ->
                    // Calculate the X position for this column
                    val xPosition = colIdx.toFloat() / (currentMeshState.cols - 1)

                    newPoints.add(
                        Pair(Offset(xPosition, yPosition), newColorIndex)
                    )
                }

                meshPoints.add(newPoints.toList())
            }
        }
    }

    fun updateMeshPoint(row: Int, col: Int, point: Pair<Offset, Long>, saveForUndo: Boolean = true) {
        if (saveForUndo) {
            saveStateForUndo()
        }
        val colorPointsInRow = meshPoints[row].toMutableList()

        var newX = point.first.x
        var newY = point.first.y

        if (constrainEdgePoints) {
            newX = when (col) {
                0 -> 0f
                colorPointsInRow.size - 1 -> 1f
                else -> newX
            }
            newY = when (row) {
                0 -> 0f
                meshPoints.size - 1 -> 1f
                else -> newY
            }
        }

        val newPoint = Pair(Offset(x = newX, y = newY), point.second)
        colorPointsInRow.set(index = col, element = newPoint)

        meshPoints.set(index = row, element = colorPointsInRow.toList())
    }
    
    fun prepareForDrag() {
        saveStateForUndo()
    }

    fun distributeMeshPointsEvenly() {
        saveStateForUndo()
        val currentMeshState = meshState.value
        val newPoints = meshPoints.mapIndexed { rowIdx, currentPoints ->
            val newPoints = mutableListOf<Pair<Offset, Long>>()

            // Calculate the Y position for this row
            val yPosition = rowIdx.toFloat() / (currentMeshState.rows - 1)

            // Iterate through columns to create points
            repeat(currentMeshState.cols) { colIdx ->
                // Calculate the X position for this column
                val xPosition = colIdx.toFloat() / (currentMeshState.cols - 1)

                newPoints.add(
                    Pair(Offset(xPosition, yPosition), currentPoints[colIdx].second)
                )
            }

            newPoints.toList()
        }
        meshPoints.clear()
        meshPoints.addAll(newPoints)
    }

    suspend fun saveMeshPoints() {
        val points = meshPoints.toSavedMeshPoints()
        println("Saved: $points")
        repository.saveMeshPoints(points)
    }

    fun removeColorFromMeshPoints(colorToRemove: Long) {
        saveStateForUndo()
        meshPoints.forEachIndexed { idx, points ->
            val newPoints = points.map { point ->
                if (point.second == colorToRemove) {
                    // Reset to transparent
                    Pair(point.first, -1L)
                } else {
                    point
                }
            }
            meshPoints.set(index = idx, element = newPoints.toList())
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

    // Undo/Redo functionality
    
    private fun createSnapshot(): AppStateSnapshot {
        return AppStateSnapshot(
            meshPoints = meshPoints.map { it.toList() },
            meshState = meshState.value,
            canvasBackgroundColor = canvasBackgroundColor.value,
        )
    }
    
    private fun applySnapshot(snapshot: AppStateSnapshot) {
        // Apply mesh points
        meshPoints.clear()
        meshPoints.addAll(snapshot.meshPoints.map { it.toMutableStateList() })
        
        // Apply mesh state
        meshState.value = snapshot.meshState
        
        // Apply canvas background color
        canvasBackgroundColor.value = snapshot.canvasBackgroundColor
    }
    
    private fun saveStateForUndo() {
        val snapshot = createSnapshot()
        undoRedoManager.saveState(snapshot)
    }
    
    fun undo() {
        val currentSnapshot = createSnapshot()
        val previousSnapshot = undoRedoManager.undo(currentSnapshot)
        
        if (previousSnapshot != null) {
            applySnapshot(previousSnapshot)
        }
    }
    
    fun redo() {
        val currentSnapshot = createSnapshot()
        val nextSnapshot = undoRedoManager.redo(currentSnapshot)
        
        if (nextSnapshot != null) {
            applySnapshot(nextSnapshot)
        }
    }
    
    fun canUndo(): Boolean = undoRedoManager.canUndo
    
    fun canRedo(): Boolean = undoRedoManager.canRedo
}