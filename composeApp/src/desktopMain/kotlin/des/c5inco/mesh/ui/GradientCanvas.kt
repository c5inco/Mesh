package des.c5inco.mesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
import des.c5inco.mesh.common.PointCursor
import des.c5inco.mesh.common.meshGradient
import des.c5inco.mesh.ui.components.CanvasSnackbar
import des.c5inco.mesh.ui.data.AppState
import des.c5inco.mesh.ui.data.DimensionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.colorPalette
import org.jetbrains.jewel.ui.util.thenIf

@Composable
fun GradientCanvas(
    exportGraphicsLayer: GraphicsLayer,
    exportScale: Int,
    modifier: Modifier = Modifier,
    onPointDrag: (Pair<Int, Int>?) -> Unit = { _ -> },
) {
    val showPoints by remember { AppState::showPoints }
    val resolution by remember { AppState::resolution }
    val colors = remember { AppState.colorPoints }

    val canvasSizeMode = AppState.canvasSizeMode
    var canvasWidth by remember { AppState::canvasWidth }
    var canvasHeight by remember { AppState::canvasHeight }

    val notifications = remember { mutableStateListOf<String>() }

    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        launch(Dispatchers.Main) {
            AppState.notificationFlow.collectLatest {
                notifications.add(0, it)
            }
        }
    }

    fun handlePositioned(coordinates: LayoutCoordinates) {
        with(density) {
            val dpWidth = coordinates.size.width.toDp()
            val dpHeight = coordinates.size.height.toDp()

            canvasWidth = dpWidth.value.toInt()
            canvasHeight = dpHeight.value.toInt()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                if (AppState.canvasBackgroundColor > -1) {
                    AppState.getColor(AppState.canvasBackgroundColor)
                } else {
                    JewelTheme.colorPalette.gray(1)
                }
            )
            .fillMaxSize()
    ) {
        val canvasAspectRatio by remember { derivedStateOf { canvasWidth.toFloat() / canvasHeight } }

        BoxWithConstraints(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            AppState.showPoints = !showPoints
                        }
                    )
                }
                .padding(32.dp)
                .thenIf(canvasSizeMode == DimensionMode.Fixed) {
                    aspectRatio(canvasAspectRatio)
                }
                .thenIf(canvasSizeMode == DimensionMode.Fill) {
                    fillMaxSize()
                }
        ) {
            val maxWidth = constraints.maxWidth
            val maxHeight = constraints.maxHeight

            fun handlePointDrag(coordinate: Pair<Int, Int>, offsetX: Float, offsetY: Float) {
                val colorPoints = colors[coordinate.second]
                val currentPoint = colorPoints[coordinate.first]
                val currentOffset = currentPoint.first

                val x = (currentOffset.x + (offsetX / maxWidth)).coerceIn(0f, 1f)
                val y = (currentOffset.y + (offsetY / maxHeight)).coerceIn(0f, 1f)

                AppState.updateColorPoint(
                    col = coordinate.first,
                    row = coordinate.second,
                    point = Pair(Offset(x = x, y = y), currentPoint.second)
                )
            }

            val graphicsLayer = rememberGraphicsLayer()

            Box(
                Modifier
                    .thenIf(canvasSizeMode == DimensionMode.Fill) {
                        onGloballyPositioned { handlePositioned(it) }
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .drawWithContent {
                        // Record content on a visible graphics layer
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }

                        // Scale and translate the export graphics layer accordingly
                        exportGraphicsLayer.apply {
                            scaleX = exportScale.toFloat()
                            scaleY = exportScale.toFloat()

                            when (exportScale) {
                                3 -> {
                                    translationX = canvasWidth.toFloat() * 3
                                    translationY = canvasHeight.toFloat() * 3
                                }

                                2 -> {
                                    translationX = canvasWidth.toFloat()
                                    translationY = canvasHeight.toFloat()
                                }

                                else -> {
                                    translationX = 0f
                                    translationY = 0f
                                }
                            }
                        }

                        // Record content on the export graphics layer
                        exportGraphicsLayer.record(
                            size = IntSize(canvasWidth * exportScale, canvasHeight * exportScale),
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
                        points = colors.map { row ->
                            row.map {
                                it.first to AppState.getColor(it.second)
                            }
                        },
                        resolutionX = resolution,
                        resolutionY = resolution,
                        showPoints = showPoints
                    )
            ) {
                Spacer(Modifier.fillMaxSize())
            }
            Layout(
                content = {
                    if (showPoints) {
                        colors.forEachIndexed { rowIdx, row ->
                            row.forEachIndexed { colIdx, col ->
                                PointCursor(
                                    xIndex = colIdx,
                                    yIndex = rowIdx,
                                    color = AppState.getColor(col.second),
                                    modifier = Modifier.pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = {
                                                onPointDrag(Pair(rowIdx, colIdx))
                                            },
                                            onDragEnd = {
                                                onPointDrag(null)
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
                            val rows = colors.size
                            val cols = colors[0].size

                            placeables.forEachIndexed { i, placeable ->
                                val row = i / cols
                                val col = i % cols

                                val xOffset = colors[row][col].first.x
                                val yOffset = colors[row][col].first.y

                                val x =
                                    ((xOffset * (constraints.maxWidth)) - cursorWidth / 2).toInt()
                                val y =
                                    ((yOffset * (constraints.maxHeight)) - cursorHeight / 2).toInt()
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
