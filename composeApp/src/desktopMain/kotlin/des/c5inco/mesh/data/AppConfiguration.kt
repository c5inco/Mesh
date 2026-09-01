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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                val desktop = File(desktopPath)
                desktop.mkdirs()
                val file = File(desktop, filename)

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

    val meshPoints = mutableStateListOf<List<Pair<Offset, Long>>>()
    private val meshPointsMutex = Mutex()

    private val loadMeshPoints = scope.async {
        val initialMeshPoints = repository.getMeshPoints().first()
        println("Loaded points: $initialMeshPoints")
        val loadedPoints = if (initialMeshPoints.isEmpty()) {
            defaultColorPoints
        } else {
            initialMeshPoints.toOffsetGrid()
        }

        meshPointsMutex.withLock {
            meshPoints.clear()
            meshPoints.addAll(loadedPoints)
        }
    }

    @Volatile
    private var generateMeshPointsJob: Deferred<Unit>? = null

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

    fun updateBlurLevel(level: Float) {
        meshState.update {
            it.copy(
                blurLevel = level
            )
        }
    }

    fun updateCanvasBackgroundColor(color: Long) {
        canvasBackgroundColor.update { color }
    }

    fun updateTotalRows(rows: Int) {
        meshState.update {
            it.copy(rows = rows.coerceIn(2, 10))
        }
        generateMeshPoints()
    }

    fun updateTotalCols(cols: Int) {
        meshState.update {
            it.copy(cols = cols.coerceIn(2, 10))
        }
        generateMeshPoints()
    }

    fun saveMeshState() {
        repository.saveMeshState(meshState.value)
    }

    private fun generateMeshPoints() {
        val requestedMeshState = meshState.value
        val previousJob = generateMeshPointsJob
        generateMeshPointsJob = scope.async {
            loadMeshPoints.await()
            val allColors = availableColors.first { it.isNotEmpty() }
            val generatedPoints = generateMeshPointGrid(
                rows = requestedMeshState.rows,
                cols = requestedMeshState.cols,
                colorIds = allColors.map(SavedColor::uid),
            )

            meshPointsMutex.withLock {
                meshPoints.clear()
                meshPoints.addAll(generatedPoints)
            }
        }
        previousJob?.cancel()
    }

    fun updateMeshPoint(row: Int, col: Int, point: Pair<Offset, Long>) {
        val updatedPoints = meshPoints.withUpdatedMeshPoint(
            row = row,
            col = col,
            point = point,
            constrainEdges = constrainEdgePoints,
        )
        meshPoints[row] = updatedPoints[row]
    }

    fun distributeMeshPointsEvenly() {
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

        while (true) {
            val pendingGeneration = generateMeshPointsJob
            try {
                pendingGeneration?.await()
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
                if (pendingGeneration === generateMeshPointsJob) throw error
                continue
            }
            if (pendingGeneration === generateMeshPointsJob) break
        }

        meshPointsMutex.withLock {
            val points = meshPoints.toSavedMeshPoints()
            println("Saved: $points")
            repository.saveMeshPoints(points)
        }
    }

    fun removeColorFromMeshPoints(colorToRemove: Long) {
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
