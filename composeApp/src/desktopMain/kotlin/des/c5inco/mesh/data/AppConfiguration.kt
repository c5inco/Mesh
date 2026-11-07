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
import kotlin.math.roundToInt

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
            loadMeshPoints.await()
            val availableColorsAsList = availableColors.first { it.isNotEmpty() }
            val colorPoints = meshPointsMutex.withLock { meshPoints.toList() }
            val blurRadius = (meshState.value.blurLevel * MAX_BLUR_LEVEL).roundToInt().toFloat()

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
