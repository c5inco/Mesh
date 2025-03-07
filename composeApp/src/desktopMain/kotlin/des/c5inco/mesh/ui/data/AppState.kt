package des.c5inco.mesh.ui.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import des.c5inco.mesh.common.toHexStringNoHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private val defaultColors = listOf(
    Color(0xff7766EE),
    Color(0xff8899ff),
    Color(0xff429BED),
    Color(0xff4FC1A6),
    Color(0xffF0C03E),
    Color(0xffff5599),
    Color(0xFFFF00FF),
)

private val defaultColorPoints = listOf(
    listOf(
        Offset(0f, 0f) to 0,
        Offset(.33f, 0f) to 0,
        Offset(.67f, 0f) to 0,
        Offset(1f, 0f) to 0,
    ),
    listOf(
        Offset(0f, .4f) to 1,
        Offset(.33f, .8f) to 1,
        Offset(.67f, .8f) to 1,
        Offset(1f, .4f) to 1,
    ),
    listOf(
        Offset(0f, 1f) to 2,
        Offset(.33f, 1f) to 2,
        Offset(.67f, 1f) to 2,
        Offset(1f, 1f) to 2,
    )
)

enum class DimensionMode {
    Fixed,
    Fill
}

object AppState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var resolution by mutableStateOf(10)
    var showPoints by mutableStateOf(false)
    var constrainEdgePoints by mutableStateOf(true)
    val colors = defaultColors.toMutableStateList()
    var canvasBackgroundColor: Int by mutableStateOf(-1)
    var canvasSizeMode by mutableStateOf(DimensionMode.Fill)

    private val _notificationFlow = MutableSharedFlow<String>()
    val notificationFlow = _notificationFlow

    fun sendNotification(message: String) {
        scope.launch {
            _notificationFlow.emit(message)
        }
    }

    var canvasWidth: Int by mutableStateOf(200)
    var canvasHeight: Int by mutableStateOf(100)
    var colorPointsRows by mutableStateOf(3)
    var colorPointsCols by mutableStateOf(4)
    val colorPoints = defaultColorPoints.toMutableStateList()

    fun updatePointsRows(rows: Int) {
        colorPointsRows = rows
        generateColorPoints()
    }

    fun updatePointsCols(cols: Int) {
        colorPointsCols = cols
        generateColorPoints()
    }

    fun distributeOffsetsEvenly() {
        val newPoints = colorPoints.mapIndexed { rowIdx, currentPoints ->
            val newPoints = mutableListOf<Pair<Offset, Int>>()

            // Calculate the Y position for this row
            val yPosition = rowIdx.toFloat() / (colorPointsRows - 1)

            // Iterate through columns to create points
            repeat(colorPointsCols) { colIdx ->
                // Calculate the X position for this column
                val xPosition = colIdx.toFloat() / (colorPointsCols - 1)

                newPoints.add(
                    Pair(Offset(xPosition, yPosition), currentPoints[colIdx].second)
                )
            }

            newPoints.toList()
        }
        colorPoints.clear()
        colorPoints.addAll(newPoints)
    }

    private fun generateColorPoints() {
        colorPoints.clear()

        repeat(colorPointsRows) { rowIdx ->
            val newColorIndex = rowIdx % colors.size

            val newPoints = mutableListOf<Pair<Offset, Int>>()

            // Calculate the Y position for this row
            val yPosition = rowIdx.toFloat() / (colorPointsRows - 1)

            // Iterate through columns to create points
            repeat(colorPointsCols) { colIdx ->
                // Calculate the X position for this column
                val xPosition = colIdx.toFloat() / (colorPointsCols - 1)

                newPoints.add(
                    Pair(Offset(xPosition, yPosition), newColorIndex)
                )
            }

            colorPoints.add(newPoints.toList())
        }
    }

    fun getColor(index: Int): Color {
        return colors.getOrElse(index) { _ -> Color.Transparent }
    }

    fun removeColor(index: Int) {
        if (colors.size == 1) return

        colorPoints.forEachIndexed { idx, points ->
            val newPoints = points.mapIndexed { pidx, point ->
                if (point.second == index) {
                    // Reset to first color
                    Pair(point.first, 0)
                } else if (point.second > 0 && index != colors.size - 1) {
                    // Shift non-zero colors left
                    Pair(point.first, point.second - 1)
                } else {
                    point
                }
            }
            colorPoints.set(index = idx, element = newPoints.toList())
        }
        colors.removeAt(index)
    }

    fun updateColorPoint(col: Int, row: Int, point: Pair<Offset, Int>) {
        val colorPointsInRow = colorPoints[row].toMutableList()

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
                colorPoints.size - 1 -> 1f
                else -> newY
            }
        }

        val newPoint = Pair(Offset(x = newX, y = newY), point.second)
        colorPointsInRow.set(index = col, element = newPoint)

        colorPoints.set(index = row, element = colorPointsInRow.toList())
    }

    fun exportPointsAsCode(): String {
        if (colorPoints.isEmpty()) return ""

        val offsetType = Offset::class.asClassName()
        val colorType = Color::class.asClassName()
        val pairType = Pair::class.asClassName().parameterizedBy(offsetType, colorType)
        val innerListType = List::class.asClassName().parameterizedBy(pairType)
        val outerListType = List::class.asClassName().parameterizedBy(innerListType)

        val listInitializer = CodeBlock.builder()
            .add("listOf(\n")
            .indent()

        colorPoints.forEachIndexed { _, innerList ->
            listInitializer.add("listOf(\n")
            listInitializer.indent()

            innerList.forEachIndexed { _, pair ->
                val hexString = colors[pair.second].toHexStringNoHash(includeAlpha = true)
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

        return codeSpec.toString()
    }

    fun resetDefaults() {
        colors.clear()
        colors.addAll(defaultColors)
        colorPoints.clear()
        colorPoints.addAll(defaultColorPoints)
    }

    fun saveImage(image: BufferedImage, scale: Int) {
        try {
            val desktopPath = System.getProperty("user.home") + File.separator + "Desktop"
            val scaleSuffix = if (scale == 1) "" else "@${scale}x"
            val filename = "mesh-export$scaleSuffix.png"
            val file = File(desktopPath, filename) // You can change the filename and extension

            ImageIO.write(image, "png", file)
            sendNotification("🖼 Exported $filename to ${file.absolutePath.substringBeforeLast("/")}")
            println("Image saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            println("Error saving image: ${e.message}")
            e.printStackTrace()
        }
    }
}
