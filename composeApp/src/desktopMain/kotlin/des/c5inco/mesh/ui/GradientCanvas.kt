package des.c5inco.mesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import data.DimensionMode
import des.c5inco.mesh.common.ControlHandleCursor
import des.c5inco.mesh.common.HandleSide
import des.c5inco.mesh.common.MeshVertexHandles
import des.c5inco.mesh.common.PointCursor
import des.c5inco.mesh.common.defaultOffset
import des.c5inco.mesh.common.isAvailable
import des.c5inco.mesh.common.isSpecified
import des.c5inco.mesh.common.meshGradient
import des.c5inco.mesh.common.resolvedOffset
import des.c5inco.mesh.data.AppConfiguration.Companion.MAX_BLUR_LEVEL
import des.c5inco.mesh.data.Notifications
import des.c5inco.mesh.ui.components.CanvasSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import model.SavedColor
import model.findColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.colorPalette
import kotlin.math.roundToInt

@Composable
fun GradientCanvas(
    exportGraphicsLayer: GraphicsLayer,
    exportScale: Int,
    canvasWidthMode: DimensionMode,
    canvasWidth: Int,
    canvasHeightMode: DimensionMode,
    canvasHeight: Int,
    blurLevel: Float = 0f,
    meshPoints: List<List<Pair<Offset, Long>>>,
    vertexHandles: Map<Pair<Int, Int>, MeshVertexHandles> = emptyMap(),
    selectedPoint: Pair<Int, Int>? = null,
    showPoints: Boolean,
    canvasBackgroundColor: Long,
    availableColors: List<SavedColor>,
    onResize: (Int, Int) -> Unit = { _, _ -> },
    onTogglePoints: () -> Unit = {},
    onSelectPoint: (Pair<Int, Int>?) -> Unit = { _ -> },
    onPointDragStartEnd: (Pair<Int, Int>?) -> Unit = { _ -> },
    onPointDrag: (row: Int, col: Int, point: Pair<Offset, Long>) -> Unit,
    onHandleDrag: (row: Int, col: Int, side: HandleSide, offset: Offset) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val notifications = remember { mutableStateListOf<String>() }

    val exportSize by derivedStateOf {
        mutableStateOf(IntSize(canvasWidth, canvasHeight))
    }

    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        launch(Dispatchers.Main) {
            Notifications.notificationFlow.collectLatest {
                notifications.add(0, it)
            }
        }
    }

    fun handlePositioned(coordinates: LayoutCoordinates) {
        with (density) {
            val dpWidth = coordinates.size.width.toDp()
            val dpHeight = coordinates.size.height.toDp()

            onResize(dpWidth.value.toInt(), dpHeight.value.toInt())
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(if (canvasBackgroundColor > -1L) {
                availableColors.findColor(canvasBackgroundColor)
            } else {
                JewelTheme.colorPalette.grayOrNull(1)
                    ?: requireNotNull(IntUiDarkTheme.colors.grayOrNull(1))
            })
            .fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            onTogglePoints()
                        }
                    )
                }
                .padding(32.dp)
                .then(
                    if (canvasWidthMode == DimensionMode.Fill) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.width(canvasWidth.dp)
                    }
                )
                .then(
                    if (canvasHeightMode == DimensionMode.Fill) {
                        Modifier.fillMaxHeight()
                    } else {
                        Modifier.height(canvasHeight.dp)
                    }
                )
        ) {
            val maxWidth = constraints.maxWidth
            val maxHeight = constraints.maxHeight

            fun handlePointDrag(coordinate: Pair<Int, Int>, offsetX: Float, offsetY: Float) {
                val colorPoints = meshPoints[coordinate.second]
                val currentPoint = colorPoints[coordinate.first]
                val currentOffset = currentPoint.first

                val x = (currentOffset.x + (offsetX / maxWidth)).coerceIn(0f, 1f)
                val y = (currentOffset.y + (offsetY / maxHeight)).coerceIn(0f, 1f)

                println("$x, $y")
                onPointDrag(
                    coordinate.second,
                    coordinate.first,
                    Pair(Offset(x = x, y = y), currentPoint.second)
                )
            }

            val graphicsLayer = rememberGraphicsLayer()

            Box(
                Modifier
                    .then(
                        if (canvasWidthMode == DimensionMode.Fill || canvasHeightMode == DimensionMode.Fill) {
                            Modifier.onGloballyPositioned { handlePositioned(it) }
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .drawWithContent {
                        // Record content on visible graphics layer
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }

                        val (width, height) = exportSize.value

                        // Scale and translate the export graphics layer accordingly
                        exportGraphicsLayer.apply {
                            scaleX = exportScale.toFloat()
                            scaleY = exportScale.toFloat()

                            when (exportScale) {
                                3 -> {
                                    translationX = width.toFloat() * 3
                                    translationY = height.toFloat() * 3
                                }
                                2 -> {
                                    translationX = width.toFloat()
                                    translationY = height.toFloat()
                                }
                                else -> {
                                    translationX = 0f
                                    translationY = 0f
                                }
                            }
                        }

                        // Record content on the export graphics layer
                        exportGraphicsLayer.record(
                            size = IntSize(width * exportScale, height * exportScale),
                        ) {
                            scale(
                                scale = 1f / density.density,
                                pivot = Offset.Zero,
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        }

                        // Draw the visible graphics layer on the canvas
                        drawLayer(graphicsLayer)
                    }
                    .meshGradient(
                        points = meshPoints.map { row ->
                            row.map {
                                it.first to availableColors.findColor(it.second)
                            }
                        },
                        blurLevel = (blurLevel * MAX_BLUR_LEVEL).roundToInt(),
                        handles = vertexHandles,
                    )
            ) {
                Spacer(Modifier.fillMaxSize())
            }
            val selectedHandles = selectedPoint?.let { vertexHandles[it] } ?: MeshVertexHandles()
            val handleOverlay = if (showPoints && selectedPoint != null) {
                HandleSide.entries.filter { side ->
                    side.isAvailable(
                        row = selectedPoint.first,
                        col = selectedPoint.second,
                        rows = meshPoints.size,
                        cols = meshPoints[0].size,
                    )
                }
            } else {
                emptyList()
            }

            Layout(
                modifier = Modifier.drawBehind {
                    if (!showPoints || meshPoints.isEmpty()) return@drawBehind

                    fun toCanvas(point: Offset): Offset =
                        Offset(point.x * size.width, point.y * size.height)

                    fun drawMeshCurve(
                        start: Offset,
                        startHandle: Offset,
                        endHandle: Offset,
                        end: Offset,
                        emphasized: Boolean,
                    ) {
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            cubicTo(
                                startHandle.x, startHandle.y,
                                endHandle.x, endHandle.y,
                                end.x, end.y,
                            )
                        }
                        drawPath(
                            path = path,
                            color = if (emphasized) {
                                Color(0xFFFFC14D).copy(alpha = 0.95f)
                            } else {
                                Color.White.copy(alpha = 0.38f)
                            },
                            style = Stroke(
                                width = if (emphasized) 2.5.dp.toPx() else 1.25.dp.toPx(),
                                cap = StrokeCap.Round,
                            ),
                        )
                    }

                    val rows = meshPoints.size
                    val cols = meshPoints[0].size
                    for (row in 0 until rows) {
                        for (col in 0 until cols - 1) {
                            val start = meshPoints[row][col].first
                            val end = meshPoints[row][col + 1].first
                            val startHandle = start + vertexHandles.resolvedOffset(row, col, HandleSide.Right)
                            val endHandle = end + vertexHandles.resolvedOffset(row, col + 1, HandleSide.Left)
                            val touchesSelection = selectedPoint == (row to col) || selectedPoint == (row to col + 1)
                            drawMeshCurve(
                                start = toCanvas(start),
                                startHandle = toCanvas(startHandle),
                                endHandle = toCanvas(endHandle),
                                end = toCanvas(end),
                                emphasized = touchesSelection,
                            )
                        }
                    }
                    for (row in 0 until rows - 1) {
                        for (col in 0 until cols) {
                            val start = meshPoints[row][col].first
                            val end = meshPoints[row + 1][col].first
                            val startHandle = start + vertexHandles.resolvedOffset(row, col, HandleSide.Bottom)
                            val endHandle = end + vertexHandles.resolvedOffset(row + 1, col, HandleSide.Top)
                            val touchesSelection = selectedPoint == (row to col) || selectedPoint == (row + 1 to col)
                            drawMeshCurve(
                                start = toCanvas(start),
                                startHandle = toCanvas(startHandle),
                                endHandle = toCanvas(endHandle),
                                end = toCanvas(end),
                                emphasized = touchesSelection,
                            )
                        }
                    }

                    if (selectedPoint != null) {
                        val vertex = meshPoints[selectedPoint.first][selectedPoint.second].first
                        val origin = toCanvas(vertex)
                        handleOverlay.forEach { side ->
                            val handleOffset = selectedHandles.offset(side).let { offset ->
                                if (offset.isSpecified) offset else side.defaultOffset()
                            }
                            drawLine(
                                color = Color.White.copy(alpha = 0.7f),
                                start = origin,
                                end = toCanvas(vertex + handleOffset),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f)),
                            )
                        }
                    }
                },
                content = {
                    if (showPoints) {
                        meshPoints.forEachIndexed { rowIdx, row ->
                            row.forEachIndexed { colIdx, col ->
                                PointCursor(
                                    xIndex = colIdx,
                                    yIndex = rowIdx,
                                    color = availableColors.findColor(col.second),
                                    selected = selectedPoint == Pair(rowIdx, colIdx),
                                    modifier = Modifier
                                        .pointerInput(rowIdx, colIdx) {
                                            detectTapGestures(
                                                onTap = { onSelectPoint(Pair(rowIdx, colIdx)) },
                                            )
                                        }
                                        .pointerInput(rowIdx, colIdx) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    onSelectPoint(Pair(rowIdx, colIdx))
                                                    onPointDragStartEnd(Pair(rowIdx, colIdx))
                                                },
                                                onDragEnd = {
                                                    onPointDragStartEnd(null)
                                                }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                handlePointDrag(
                                                    coordinate = Pair(colIdx, rowIdx),
                                                    offsetX = dragAmount.x,
                                                    offsetY = dragAmount.y
                                                )
                                            }
                                        },
                                )
                            }
                        }
                        handleOverlay.forEach { side ->
                            ControlHandleCursor(
                                side = side,
                                modifier = Modifier.pointerInput(side) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val current = selectedHandles.offset(side).let { offset ->
                                            if (offset.isSpecified) offset else side.defaultOffset()
                                        }
                                        onHandleDrag(
                                            selectedPoint!!.first,
                                            selectedPoint.second,
                                            side,
                                            Offset(
                                                x = (current.x + dragAmount.x / maxWidth).coerceIn(-0.6f, 0.6f),
                                                y = (current.y + dragAmount.y / maxHeight).coerceIn(-0.6f, 0.6f),
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                },
                measurePolicy = { measurables, constraints ->
                    val vertexCount = if (showPoints) meshPoints.size * meshPoints[0].size else 0
                    val vertexPlaceables = measurables.take(vertexCount).map { it.measure(constraints) }
                    val handlePlaceables = measurables.drop(vertexCount).map { it.measure(constraints) }

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        if (vertexPlaceables.isNotEmpty()) {
                            val cursorWidth = vertexPlaceables[0].width
                            val cursorHeight = vertexPlaceables[0].height
                            val cols = meshPoints[0].size

                            vertexPlaceables.forEachIndexed { i, placeable ->
                                val row = i / cols
                                val col = i % cols
                                val xOffset = meshPoints[row][col].first.x
                                val yOffset = meshPoints[row][col].first.y
                                val x = ((xOffset * constraints.maxWidth) - cursorWidth / 2).toInt()
                                val y = ((yOffset * constraints.maxHeight) - cursorHeight / 2).toInt()
                                placeable.place(x, y)
                            }
                        }

                        if (selectedPoint != null && handlePlaceables.isNotEmpty()) {
                            val vertex = meshPoints[selectedPoint.first][selectedPoint.second].first
                            handlePlaceables.forEachIndexed { index, placeable ->
                                val side = handleOverlay[index]
                                val handleOffset = selectedHandles.offset(side).let { offset ->
                                    if (offset.isSpecified) offset else side.defaultOffset()
                                }
                                val x = (((vertex.x + handleOffset.x) * constraints.maxWidth) - placeable.width / 2).toInt()
                                val y = (((vertex.y + handleOffset.y) * constraints.maxHeight) - placeable.height / 2).toInt()
                                placeable.place(x, y)
                            }
                        }
                    }
                }
            )
        }

        notifications.reversed().forEach {
            CanvasSnackbar(
                onDismiss = {
                    notifications.removeLast()
                    println("dismiss")
                },
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Text(
                    text = it,
                )
            }
        }
    }
}
