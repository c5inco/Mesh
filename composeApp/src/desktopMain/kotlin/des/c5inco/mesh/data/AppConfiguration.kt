package des.c5inco.mesh.data

import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import data.DimensionMode
import data.MeshDocument
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
    val currentDocumentName: String = "Untitled",
    val currentDocumentPath: File? = null,
    val hasUnsavedChanges: Boolean = false,
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
            currentDocumentName = "Untitled",
            currentDocumentPath = null,
            hasUnsavedChanges = false,
        )
    )

    fun addColor(color: SavedColor) {
        repository.addColor(color)
    }

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
        markAsModified()
    }

    fun updateCanvasWidth(width: Int) {
        meshState.update {
            it.copy(canvasWidth = width)
        }
        markAsModified()
    }

    fun updateCanvasHeightMode() {
        meshState.update {
            val current = it.canvasHeightMode
            it.copy(
                canvasHeightMode = if (current == DimensionMode.Fixed) DimensionMode.Fill else DimensionMode.Fixed
            )
        }
        markAsModified()
    }

    fun updateCanvasHeight(height: Int) {
        meshState.update {
            it.copy(canvasHeight = height)
        }
        markAsModified()
    }

    fun updateBlurLevel(level: Float) {
        meshState.update {
            it.copy(
                blurLevel = level
            )
        }
        markAsModified()
    }

    fun updateCanvasBackgroundColor(color: Long) {
        canvasBackgroundColor.update { color }
        markAsModified()
    }

    fun updateTotalRows(rows: Int) {
        meshState.update {
            it.copy(rows = rows.coerceIn(2, 10))
        }
        generateMeshPoints()
        markAsModified()
    }

    fun updateTotalCols(cols: Int) {
        meshState.update {
            it.copy(cols = cols.coerceIn(2, 10))
        }
        generateMeshPoints()
        markAsModified()
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

    fun updateMeshPoint(row: Int, col: Int, point: Pair<Offset, Long>) {
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
        markAsModified()
    }

    fun distributeMeshPointsEvenly() {
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
        markAsModified()
    }

    suspend fun saveMeshPoints() {
        val points = meshPoints.toSavedMeshPoints()
        println("Saved: $points")
        repository.saveMeshPoints(points)
    }

    fun removeColorFromMeshPoints(colorToRemove: Long) {
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

    private fun markAsModified() {
        uiState.update {
            it.copy(hasUnsavedChanges = true)
        }
    }

    /**
     * Creates a new document, replacing the current state
     */
    fun newDocument() {
        val document = DocumentManager.createNewDocument()
        loadDocumentState(document, null)
        Notifications.send("📄 New document created")
    }

    /**
     * Opens a document using a file chooser dialog
     */
    fun openDocument() {
        val file = DocumentManager.showOpenDialog() ?: return
        val document = DocumentManager.loadDocument(file)
        
        if (document != null) {
            loadDocumentState(document, file)
            Notifications.send("📂 Opened ${file.name}")
        } else {
            Notifications.send("❌ Failed to open document")
        }
    }

    /**
     * Saves the current document to its current path, or shows save dialog if no path
     */
    fun saveDocument(): Boolean {
        val currentPath = uiState.value.currentDocumentPath
        
        val file = if (currentPath != null) {
            currentPath
        } else {
            DocumentManager.showSaveDialog(uiState.value.currentDocumentName) ?: return false
        }
        
        return saveDocumentToFile(file)
    }

    /**
     * Shows a save dialog and saves the document to a new location
     */
    fun saveDocumentAs(): Boolean {
        val file = DocumentManager.showSaveDialog(uiState.value.currentDocumentName) ?: return false
        return saveDocumentToFile(file)
    }

    private fun saveDocumentToFile(file: File): Boolean {
        val document = MeshDocument(
            name = file.nameWithoutExtension,
            meshState = meshState.value,
            meshPoints = meshPoints.toSavedMeshPoints(),
            canvasBackgroundColor = canvasBackgroundColor.value
        )
        
        val success = DocumentManager.saveDocument(document, file)
        if (success) {
            uiState.update {
                it.copy(
                    currentDocumentName = file.nameWithoutExtension,
                    currentDocumentPath = file,
                    hasUnsavedChanges = false
                )
            }
            Notifications.send("💾 Saved ${file.name}")
        } else {
            Notifications.send("❌ Failed to save document")
        }
        return success
    }

    private fun loadDocumentState(document: MeshDocument, file: File?) {
        // Update mesh state
        meshState.update { document.meshState }
        
        // Update canvas background color
        canvasBackgroundColor.update { document.canvasBackgroundColor }
        
        // Update mesh points
        meshPoints.clear()
        if (document.meshPoints.isEmpty()) {
            meshPoints.addAll(defaultColorPoints)
        } else {
            meshPoints.addAll(document.meshPoints.toOffsetGrid())
        }
        
        // Update UI state
        uiState.update {
            it.copy(
                currentDocumentName = document.name,
                currentDocumentPath = file,
                hasUnsavedChanges = false
            )
        }
    }

    /**
     * Gets the current document for saving
     */
    fun getCurrentDocument(): MeshDocument {
        return MeshDocument(
            name = uiState.value.currentDocumentName,
            meshState = meshState.value,
            meshPoints = meshPoints.toSavedMeshPoints(),
            canvasBackgroundColor = canvasBackgroundColor.value
        )
    }
}