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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import data.DimensionMode
import des.c5inco.mesh.common.PointCursor
import des.c5inco.mesh.common.meshGradient
import des.c5inco.mesh.data.AppConfiguration.Companion.MAX_BLUR_LEVEL
import des.c5inco.mesh.data.Notifications
import des.c5inco.mesh.ui.components.CanvasSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import model.SavedColor
import model.findColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.colorPalette
import org.jetbrains.jewel.ui.util.thenIf
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GradientCanvas(
    exportGraphicsLayer: GraphicsLayer,
    exportScale: Int,
    resolution: Int,
    canvasWidthMode: DimensionMode,
    canvasWidth: Int,
    canvasHeightMode: DimensionMode,
    canvasHeight: Int,
    blurLevel: Float = 0f,
    meshPoints: List<List<Pair<Offset, Long>>>,
    showPoints: Boolean,
    canvasBackgroundColor: Long,
    availableColors: List<SavedColor>,
    onResize: (Int, Int) -> Unit = { _, _ -> },
    onTogglePoints: () -> Unit = {},
    onPointDragStartEnd: (Pair<Int, Int>?) -> Unit = { _ -> },
    onPointDrag: (row: Int, col: Int, point: Pair<Offset, Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val notifications = remember { mutableStateListOf<String>() }
    val zoomState = remember { mutableStateOf(1f) }
    val panState = remember { mutableStateOf(Offset.Zero) }

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
        with(density) {
            val dpWidth = coordinates.size.width.toDp()
            val dpHeight = coordinates.size.height.toDp()

            onResize(dpWidth.value.toInt(), dpHeight.value.toInt())
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                if (canvasBackgroundColor > -1L) {
                    availableColors.findColor(canvasBackgroundColor)
                } else {
                    JewelTheme.colorPalette.gray(1)
                }
            )
            .fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val scrollY = event.changes.first().scrollDelta.y
                    val zoomFactor = if (scrollY > 0) 0.9f else 1.1f
                    zoomState.value = (zoomState.value * zoomFactor).coerceIn(0.5f, 5f)
                }
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

                val scaledOffsetX = offsetX / (maxWidth * zoomState.value)
                val scaledOffsetY = offsetY / (maxHeight * zoomState.value)
                val x = (currentOffset.x + scaledOffsetX).coerceIn(0f, 1f)
                val y = (currentOffset.y + scaledOffsetY).coerceIn(0f, 1f)

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
                    .graphicsLayer(
                        scaleX = zoomState.value,
                        scaleY = zoomState.value
                    )
                    .thenIf(canvasWidthMode == DimensionMode.Fill || canvasHeightMode == DimensionMode.Fill) {
                        Modifier.onGloballyPositioned { handlePositioned(it) }
                    }
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
                        resolutionX = resolution,
                        resolutionY = resolution,
                        showPoints = showPoints
                    )
            ) {
                Spacer(Modifier.fillMaxSize())
            }
            // Points layout outside the zoomed area but manually positioned
            Layout(
                content = {
                    if (showPoints) {
                        meshPoints.forEachIndexed { rowIdx, row ->
                            row.forEachIndexed { colIdx, col ->
                                PointCursor(
                                    xIndex = colIdx,
                                    yIndex = rowIdx,
                                    color = availableColors.findColor(col.second),
                                    modifier = Modifier.pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = {
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
                                    })
                            }
                        }
                    }
                },
                measurePolicy = { measurables, constraints ->
                    val placeables = measurables.map { measurable ->
                        measurable.measure(constraints)
                    }

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        if (placeables.isNotEmpty()) {
                            val cursorWidth = placeables[0].width
                            val cursorHeight = placeables[0].height
                            val rows = meshPoints.size
                            val cols = meshPoints[0].size

                            placeables.forEachIndexed { i, placeable ->
                                val row = i / cols
                                val col = i % cols

                                val xOffset = meshPoints[row][col].first.x
                                val yOffset = meshPoints[row][col].first.y

                                // Apply zoom transformation manually (centered)
                                val baseX = (xOffset * constraints.maxWidth) - cursorWidth / 2
                                val baseY = (yOffset * constraints.maxHeight) - cursorHeight / 2

                                val centerX = constraints.maxWidth / 2f
                                val centerY = constraints.maxHeight / 2f

                                val zoomedX = centerX + (baseX - centerX) * zoomState.value
                                val zoomedY = centerY + (baseY - centerY) * zoomState.value

                                placeable.place(zoomedX.toInt(), zoomedY.toInt())
                            }
                        }
                    }
                }
            )
        }

        // Zoom level display for debugging
        Text(
            text = "Zoom: ${String.format("%.1f", zoomState.value)}x",
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(
                    JewelTheme.colorPalette.gray(2).copy(alpha = 0.8f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

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