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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.MeshGradientPainter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import data.DimensionMode
import des.c5inco.mesh.common.PointCursor
import des.c5inco.mesh.data.AppConfiguration.Companion.MAX_BLUR_LEVEL
import des.c5inco.mesh.data.Notifications
import des.c5inco.mesh.testing.MeshTestTags
import des.c5inco.mesh.ui.components.CanvasSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import model.HandleSide
import model.PointHandles
import model.SavedColor
import model.effectiveHandle
import model.findColor
import model.handlesAt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.colorPalette
import kotlin.math.roundToInt

private const val DEFAULT_SUBDIVISION_STEPS = 10
private const val MAX_AXIS_SUBDIVISIONS = 100

/**
 * The painter for a grid of points, or null before a complete patch exists (points load
 * asynchronously). Shared by the canvas and the MCP render_png tool so both draw the same gradient.
 * [handles] are the points' Bezier handles; automatic ones are inferred by the painter.
 */
fun meshGradientPainter(
    colorPoints: List<List<Pair<Offset, Color>>>,
    handles: List<List<PointHandles>> = emptyList(),
): MeshGradientPainter? {
    if (colorPoints.size < 2 || colorPoints.first().size < 2) return null
    return MeshGradientPainter(
        rows = colorPoints.lastIndex,
        columns = colorPoints.first().lastIndex,
        hasBicubicColor = true,
    ) {
        colorPoints.forEachIndexed { row, points ->
            points.forEachIndexed { column, (position, color) ->
                val pointHandles = handles.handlesAt(row, column)
                setVertex(
                    row,
                    column,
                    position,
                    color,
                    leftControlPoint = pointHandles.left ?: Offset.Unspecified,
                    topControlPoint = pointHandles.top ?: Offset.Unspecified,
                    rightControlPoint = pointHandles.right ?: Offset.Unspecified,
                    bottomControlPoint = pointHandles.bottom ?: Offset.Unspecified,
                )
            }
        }
    }
}

/**
 * Normalized positions of the subdivision grid across the mesh patches, matching the bicubic
 * Bezier surface geometry and tangents of [MeshGradientPainter]. Shared patch edges and
 * corners are emitted once so semi-transparent dots don't double-blend along patch seams.
 */
internal fun meshSubdivisionPoints(
    points: List<List<Offset>>,
    handles: List<List<PointHandles>> = emptyList(),
    steps: Int = defaultSubdivisionSteps(points),
): List<Offset> {
    if (points.size < 2 || points.first().size < 2 || steps < 1) return emptyList()
    val rows = points.lastIndex
    val columns = points.first().lastIndex
    if (points.any { it.size != columns + 1 }) return emptyList()

    val leftBezier = Array(rows + 1) { Array(columns + 1) { Offset.Zero } }
    val rightBezier = Array(rows + 1) { Array(columns + 1) { Offset.Zero } }
    val topBezier = Array(rows + 1) { Array(columns + 1) { Offset.Zero } }
    val bottomBezier = Array(rows + 1) { Array(columns + 1) { Offset.Zero } }

    for (row in 0..rows) {
        for (column in 0..columns) {
            val pointHandles = handles.handlesAt(row, column)
            leftBezier[row][column] = points.effectiveHandle(row, column, HandleSide.Left, pointHandles)
            rightBezier[row][column] = points.effectiveHandle(row, column, HandleSide.Right, pointHandles)
            topBezier[row][column] = points.effectiveHandle(row, column, HandleSide.Top, pointHandles)
            bottomBezier[row][column] = points.effectiveHandle(row, column, HandleSide.Bottom, pointHandles)
        }
    }

    val stepFloats = FloatArray(steps + 1) { it / steps.toFloat() }
    return buildList((rows * steps + 1) * (columns * steps + 1)) {
        for (patchRow in 0 until rows) {
            for (patchColumn in 0 until columns) {
                val c00 = points[patchRow][patchColumn]
                val c03 = points[patchRow][patchColumn + 1]
                val c30 = points[patchRow + 1][patchColumn]
                val c33 = points[patchRow + 1][patchColumn + 1]

                val c01 = c00 + rightBezier[patchRow][patchColumn]
                val c02 = c03 + leftBezier[patchRow][patchColumn + 1]
                val c31 = c30 + rightBezier[patchRow + 1][patchColumn]
                val c32 = c33 + leftBezier[patchRow + 1][patchColumn + 1]

                val c10 = c00 + bottomBezier[patchRow][patchColumn]
                val c20 = c30 + topBezier[patchRow + 1][patchColumn]
                val c13 = c03 + bottomBezier[patchRow][patchColumn + 1]
                val c23 = c33 + topBezier[patchRow + 1][patchColumn + 1]

                val c11 = c01 + c10 - c00
                val c12 = c02 + c13 - c03
                val c21 = c20 + c31 - c30
                val c22 = c23 + c32 - c33

                val vStart = if (patchRow == 0) 0 else 1
                val uStart = if (patchColumn == 0) 0 else 1
                for (uIndex in uStart..steps) {
                    val u = stepFloats[uIndex]
                    val r0 = cubicBezier(c00, c01, c02, c03, u)
                    val r1 = cubicBezier(c10, c11, c12, c13, u)
                    val r2 = cubicBezier(c20, c21, c22, c23, u)
                    val r3 = cubicBezier(c30, c31, c32, c33, u)
                    for (vIndex in vStart..steps) {
                        add(cubicBezier(r0, r1, r2, r3, stepFloats[vIndex]))
                    }
                }
            }
        }
    }
}

private fun defaultSubdivisionSteps(points: List<List<Offset>>): Int {
    if (points.size < 2 || points.first().size < 2) return DEFAULT_SUBDIVISION_STEPS
    val maxPatchesOnAxis = maxOf(points.lastIndex, points.first().lastIndex)
    return (MAX_AXIS_SUBDIVISIONS / maxPatchesOnAxis).coerceIn(2, DEFAULT_SUBDIVISION_STEPS)
}

private fun cubicBezier(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val t2 = t * t
    val t3 = t2 * t
    val b0 = -t3 + 3f * t2 - 3f * t + 1f
    val b1 = 3f * t3 - 6f * t2 + 3f * t
    val b2 = -3f * t3 + 3f * t2
    val b3 = t3
    return Offset(
        x = b0 * p0.x + b1 * p1.x + b2 * p2.x + b3 * p3.x,
        y = b0 * p0.y + b1 * p1.y + b2 * p2.y + b3 * p3.y,
    )
}

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
    meshHandles: List<List<PointHandles>> = emptyList(),
    showPoints: Boolean,
    selectedPoint: Pair<Int, Int>? = null,
    activeHandle: HandleSide? = null,
    canvasBackgroundColor: Long,
    availableColors: List<SavedColor>,
    onResize: (Int, Int) -> Unit = { _, _ -> },
    onTogglePoints: () -> Unit = {},
    onPointDragStartEnd: (Pair<Int, Int>?) -> Unit = { _ -> },
    onPointSelected: (row: Int, col: Int) -> Unit = { _, _ -> },
    onHandleActivated: (side: HandleSide) -> Unit = {},
    onHandleDrag: (row: Int, col: Int, side: HandleSide, offset: Offset) -> Unit = { _, _, _, _ -> },
    onPointDrag: (row: Int, col: Int, point: Pair<Offset, Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val notifications = remember { mutableStateListOf<String>() }

    val exportSize by derivedStateOf {
        mutableStateOf(IntSize(canvasWidth, canvasHeight))
    }

    val density = LocalDensity.current
    val colorPoints = meshPoints.map { row ->
        row.map { (position, colorId) -> position to availableColors.findColor(colorId) }
    }
    val pointPositions = meshPoints.map { row -> row.map { (position, _) -> position } }
    // A copy read in composition, so any handle change redraws the painter and the subdivision dots.
    val handlesSnapshot = meshHandles.toList()
    val gradientPainter = remember(colorPoints, handlesSnapshot) { meshGradientPainter(colorPoints, handlesSnapshot) }
    val subdivisionPoints = remember(pointPositions, handlesSnapshot, showPoints) {
        if (showPoints) meshSubdivisionPoints(pointPositions, handlesSnapshot) else emptyList()
    }
    val selectionColor = JewelTheme.colorPalette.blueOrNull(6)
        ?: requireNotNull(IntUiDarkTheme.colors.blueOrNull(6))
    val selection = selectedPoint?.takeIf { (row, col) ->
        row in pointPositions.indices && col in pointPositions[row].indices
    }
    val hasSelection = selection != null
    // A zero-length handle, such as the outward one on an edge point, sits on top of its point and
    // would make the point hard to drag, so only handles with a direction get a dot on the canvas.
    val handleSides = selection?.let { (row, col) ->
        val pointHandles = handlesSnapshot.handlesAt(row, col)
        HandleSide.entries.filter { side ->
            pointPositions.effectiveHandle(row, col, side, pointHandles).getDistance() > 0.0001f
        }
    } ?: emptyList()
    val blurRadius = (blurLevel * MAX_BLUR_LEVEL).roundToInt().toFloat()

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
                        if (showPoints && subdivisionPoints.isNotEmpty()) {
                            drawPoints(
                                points = subdivisionPoints.map { Offset(it.x * size.width, it.y * size.height) },
                                pointMode = PointMode.Points,
                                color = Color.White.copy(alpha = 0.9f),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                        if (showPoints && hasSelection) {
                            val (row, col) = selection
                            val anchor = pointPositions[row][col]
                            val handles = handlesSnapshot.handlesAt(row, col)
                            val curveDash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx()))
                            HandleSide.entries.forEach { side ->
                                val handle = pointPositions.effectiveHandle(row, col, side, handles)
                                val start = Offset(anchor.x * size.width, anchor.y * size.height)
                                val end = Offset(
                                    (anchor.x + handle.x) * size.width,
                                    (anchor.y + handle.y) * size.height,
                                )
                                val active = side == activeHandle
                                val (neighborRow, neighborCol) = when (side) {
                                    HandleSide.Left -> row to col - 1
                                    HandleSide.Top -> row - 1 to col
                                    HandleSide.Right -> row to col + 1
                                    HandleSide.Bottom -> row + 1 to col
                                }
                                val neighbor = pointPositions.getOrNull(neighborRow)?.getOrNull(neighborCol)
                                if (neighbor != null) {
                                    val facingHandle = pointPositions.effectiveHandle(
                                        neighborRow, neighborCol, side.opposite,
                                        handlesSnapshot.handlesAt(neighborRow, neighborCol),
                                    )
                                    // The edge uses the handles at both ends, exactly as the mesh painter does.
                                    val curve = Path().apply {
                                        moveTo(start.x, start.y)
                                        cubicTo(
                                            end.x, end.y,
                                            (neighbor.x + facingHandle.x) * size.width,
                                            (neighbor.y + facingHandle.y) * size.height,
                                            neighbor.x * size.width, neighbor.y * size.height,
                                        )
                                    }
                                    drawPath(curve, Color.Black.copy(alpha = 0.35f), style = Stroke(4.dp.toPx(), pathEffect = curveDash))
                                    drawPath(
                                        curve,
                                        if (active) selectionColor else Color.White.copy(alpha = 0.95f),
                                        style = Stroke(if (active) 2.5.dp.toPx() else 1.75.dp.toPx(), pathEffect = curveDash),
                                    )
                                }
                                drawLine(
                                    color = Color.Black.copy(alpha = 0.25f),
                                    start = start,
                                    end = end,
                                    strokeWidth = 3.dp.toPx(),
                                )
                                drawLine(
                                    color = if (active) selectionColor else Color.White.copy(alpha = 0.75f),
                                    start = start,
                                    end = end,
                                    strokeWidth = if (active) 1.5.dp.toPx() else 1.dp.toPx(),
                                )
                            }
                        }
                    }
                    .graphicsLayer {
                        renderEffect = if (blurRadius > 0f) {
                            BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
                        } else {
                            null
                        }
                    }
                    .then(
                        if (gradientPainter != null) {
                            Modifier.paint(gradientPainter, sizeToIntrinsics = false)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Spacer(Modifier.fillMaxSize())
            }
            Layout(
                content = {
                    if (showPoints) {
                        meshPoints.forEachIndexed { rowIdx, row ->
                            row.forEachIndexed { colIdx, col ->
                                PointCursor(
                                    xIndex = colIdx,
                                    yIndex = rowIdx,
                                    color = availableColors.findColor(col.second),
                                    selected = selection == (rowIdx to colIdx),
                                    selectedColor = selectionColor,
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        // A press selects the point, so its Bezier handles show for editing.
                                        .pointerInput(rowIdx, colIdx) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    if (event.type == PointerEventType.Press) {
                                                        onPointSelected(rowIdx, colIdx)
                                                    }
                                                }
                                            }
                                        }
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    onPointSelected(rowIdx, colIdx)
                                                    onPointDragStartEnd(Pair(rowIdx, colIdx))
                                                },
                                                onDragEnd = {
                                                    onPointDragStartEnd(null)
                                                },
                                                onDragCancel = {
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
            // The selected point's four handles sit above its cursor, at the ends of its tangent lines.
            Layout(
                content = {
                    if (showPoints && hasSelection) {
                        val (row, col) = selection
                        handleSides.forEach { side ->
                            BezierHandleCursor(
                                color = availableColors.findColor(meshPoints[row][col].second),
                                active = side == activeHandle,
                                activeColor = selectionColor,
                                modifier = Modifier
                                    .testTag(MeshTestTags.bezierHandle(row, col, side.name.lowercase()))
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    // A press makes this the active handle before a drag begins.
                                    .pointerInput(row, col, side) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                if (event.type == PointerEventType.Press) {
                                                    onPointSelected(row, col)
                                                    onHandleActivated(side)
                                                }
                                            }
                                        }
                                    }
                                    .pointerInput(row, col, side, maxWidth, maxHeight) {
                                        // The handle's live position, so a drag that began from an automatic
                                        // handle keeps following the pointer instead of snapping back each frame.
                                        var position: Offset? = null
                                        detectDragGestures(
                                            onDragStart = {
                                                onHandleActivated(side)
                                                position = pointPositions.effectiveHandle(
                                                    row, col, side, meshHandles.handlesAt(row, col)
                                                )
                                            },
                                            onDragEnd = { position = null },
                                            onDragCancel = { position = null },
                                        ) { change, dragAmount ->
                                            change.consume()
                                            val next = (position ?: pointPositions.effectiveHandle(
                                                row, col, side, meshHandles.handlesAt(row, col)
                                            )) + Offset(dragAmount.x / maxWidth, dragAmount.y / maxHeight)
                                            position = next
                                            onHandleDrag(row, col, side, next)
                                        }
                                    }
                            )
                        }
                    }
                },
                measurePolicy = { measurables, constraints ->
                    val placeables = measurables.map { it.measure(constraints) }
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        if (measurables.isNotEmpty()) {
                            val (row, col) = selection!!
                            val anchor = pointPositions[row][col]
                            val handles = handlesSnapshot.handlesAt(row, col)
                            placeables.forEachIndexed { i, placeable ->
                                val offset = pointPositions.effectiveHandle(row, col, handleSides[i], handles)
                                val x = (anchor.x + offset.x) * constraints.maxWidth - placeable.width / 2f
                                val y = (anchor.y + offset.y) * constraints.maxHeight - placeable.height / 2f
                                placeable.place(x.roundToInt(), y.roundToInt())
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

/** A draggable dot at the end of a Bezier tangent line, filled with its point's color. */
@Composable
private fun BezierHandleCursor(
    color: Color,
    active: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .drawBehind {
                if (active) {
                    drawCircle(color = activeColor.copy(alpha = 0.3f), radius = 10.dp.toPx())
                    drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = 8.dp.toPx())
                    drawCircle(color = Color.White, radius = 6.dp.toPx())
                    drawCircle(
                        color = activeColor,
                        radius = 6.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(color = color, radius = 3.dp.toPx())
                } else {
                    drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = 7.dp.toPx())
                    drawCircle(color = Color.White, radius = 5.dp.toPx())
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.5f),
                        radius = 5.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawCircle(color = color, radius = 2.5.dp.toPx())
                }
            }
    )
}
