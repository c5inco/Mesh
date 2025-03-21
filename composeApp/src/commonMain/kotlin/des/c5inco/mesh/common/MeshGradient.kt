package des.c5inco.mesh.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// Adapted from: https://gist.github.com/sinasamaki/05725557c945c5329fdba4a3494aaecb
@Composable
fun Modifier.meshGradient(
    points: List<List<Pair<Offset, Color>>>,
    blurLevel: Int = 0,
    gradientBlendMode: BlendMode = BlendMode.DstIn,
    resolutionX: Int = 1,
    resolutionY: Int = 1,
    showPoints: Boolean = false,
    indicesModifier: (List<Int>) -> List<Int> = { it }
): Modifier {
    val pointData by remember(points, resolutionX, resolutionY) {
        derivedStateOf {
            PointData(points, resolutionX, resolutionY)
        }
    }

    val pointSize = with (LocalDensity.current) { 3.dp.toPx() }

    val pointsPaint = remember {
        Paint().apply {
            color = Color.White.copy(alpha = .9f)
            strokeWidth = pointSize
            strokeCap = StrokeCap.Round
            blendMode = BlendMode.SrcOver
        }
    }
    val meshGraphicLayer = rememberGraphicsLayer()
    val blurRadius = with (LocalDensity.current) { blurLevel.toDp().toPx() }

    return drawWithCache{
        onDrawWithContent {
            // Record content on a visible graphics layer
            meshGraphicLayer.apply {
                val horizontalBlurPixels = blurRadius
                val verticalBlurPixels = blurRadius
                this.renderEffect =
                    // Only non-zero blur radii are valid BlurEffect parameters
                    if (horizontalBlurPixels > 0f && verticalBlurPixels > 0f) {
                        BlurEffect(horizontalBlurPixels, verticalBlurPixels, TileMode.Clamp)
                    } else {
                        null
                    }
                this.clip = false
            }
            meshGraphicLayer.record {
                scale(
                    scaleX = size.width,
                    scaleY = size.height,
                    pivot = Offset.Zero
                ) {
                    drawContext.canvas.drawVertices(
                        vertices = Vertices(
                            vertexMode = VertexMode.Triangles,
                            positions = pointData.offsets,
                            textureCoordinates = pointData.offsets,
                            colors = pointData.colors,
                            indices = indicesModifier(pointData.indices)
                        ),
                        blendMode = gradientBlendMode,
                        paint = paint,
                    )
                }
            }
            drawLayer(meshGraphicLayer)
            if (showPoints) {
                drawIntoCanvas {
                    val intermediatePoints = pointData.offsets
                        .map { Offset(it.x * size.width, it.y * size.height) }

                    it.drawPoints(
                        pointMode = PointMode.Points,
                        points = intermediatePoints,
                        paint = pointsPaint
                    )
                }
            }
        }
    }
}

private class PointData(
    private val points: List<List<Pair<Offset, Color>>>,
    private val stepsX: Int,
    private val stepsY: Int,
) {
    val offsets: MutableList<Offset>
    val colors: MutableList<Color>
    val indices: List<Int>
    private val xLength: Int = (points[0].size * stepsX) - (stepsX - 1)
    private val yLength: Int = (points.size * stepsY) - (stepsY - 1)
    private val measure = PathMeasure()

    private val indicesBlocks: List<IndicesBlock>

    init {
        offsets = buildList {
            repeat((xLength - 0) * (yLength - 0)) {
                add(Offset(0f, 0f))
            }
        }.toMutableList()

        colors = buildList {
            repeat((xLength - 0) * (yLength - 0)) {
                add(Color.Transparent)
            }
        }.toMutableList()

        indicesBlocks =
            buildList {
                for (y in 0..yLength - 2) {
                    for (x in 0..xLength - 2) {

                        val a = (y * xLength) + x
                        val b = a + 1
                        val c = ((y + 1) * xLength) + x
                        val d = c + 1

                        add(
                            IndicesBlock(
                                indices = buildList {
                                    add(a)
                                    add(c)
                                    add(d)

                                    add(a)
                                    add(b)
                                    add(d)
                                },
                                x = x, y = y
                            )
                        )

                    }
                }
            }

        indices = indicesBlocks.flatMap { it.indices }
        generateInterpolatedOffsets()
    }

    private fun generateInterpolatedOffsets() {
        for (y in 0..points.lastIndex) {
            for (x in 0..points[y].lastIndex) {
                this[x * stepsX, y * stepsY] = points[y][x].first
                this[x * stepsX, y * stepsY] = points[y][x].second

                if (x != points[y].lastIndex) {
                    val path = cubicPathX(
                        point1 = points[y][x].first,
                        point2 = points[y][x + 1].first,
                        when (x) {
                            0 -> 0
                            points[y].lastIndex - 1 -> 2
                            else -> 1
                        }
                    )
                    measure.setPath(path, false)

                    for (i in 1..<stepsX) {
                        measure.getPosition(i / stepsX.toFloat() * measure.length).let {
                            this[(x * stepsX) + i, (y * stepsY)] = Offset(it.x, it.y)
                            this[(x * stepsX) + i, (y * stepsY)] =
                                lerp(
                                    points[y][x].second,
                                    points[y][x + 1].second,
                                    i / stepsX.toFloat(),
                                )
                        }
                    }
                }
            }
        }

        for (y in 0..<points.lastIndex) {
            for (x in 0..<this.xLength) {
                val path = cubicPathY(
                    point1 = this[x, y * stepsY].let { Offset(it.x, it.y) },
                    point2 = this[x, (y + 1) * stepsY].let { Offset(it.x, it.y) },
                    when (y) {
                        0 -> 0
                        points[y].lastIndex - 1 -> 2
                        else -> 1
                    }
                )
                measure.setPath(path, false)
                for (i in (1..<stepsY)) {
                    val point3 = measure.getPosition(i / stepsY.toFloat() * measure.length).let {
                        Offset(it.x, it.y)
                    }

                    this[x, ((y * stepsY) + i)] = point3

                    this[x, ((y * stepsY) + i)] = lerp(
                        this.getColor(x, y * stepsY),
                        this.getColor(x, (y + 1) * stepsY),
                        i / stepsY.toFloat(),
                    )

                }
            }
        }
    }

    data class IndicesBlock(val indices: List<Int>, val x: Int, val y: Int)

    operator fun get(x: Int, y: Int): Offset {
        val index = (y * xLength) + x
        return offsets[index]
    }

    private fun getColor(x: Int, y: Int): Color {
        val index = (y * xLength) + x
        return colors[index]
    }

    private operator fun set(x: Int, y: Int, offset: Offset) {
        val index = (y * xLength) + x
        offsets[index] = Offset(offset.x, offset.y)
    }

    private operator fun set(x: Int, y: Int, color: Color) {
        val index = (y * xLength) + x
        colors[index] = color
    }
}

private fun cubicPathX(point1: Offset, point2: Offset, position: Int): Path {
    val path = Path().apply {
        moveTo(point1.x, point1.y)
        val delta = (point2.x - point1.x) * .5f
        when (position) {
            0 -> cubicTo(
                point1.x, point1.y,
                point2.x - delta, point2.y,
                point2.x, point2.y
            )

            2 -> cubicTo(
                point1.x + delta, point1.y,
                point2.x, point2.y,
                point2.x, point2.y
            )

            else -> cubicTo(
                point1.x + delta, point1.y,
                point2.x - delta, point2.y,
                point2.x, point2.y
            )
        }

        lineTo(point2.x, point2.y)
    }
    return path
}

private fun cubicPathY(point1: Offset, point2: Offset, position: Int): Path {
    val path = Path().apply {
        moveTo(point1.x, point1.y)
        val delta = (point2.y - point1.y) * .5f
        when (position) {
            0 -> cubicTo(
                point1.x, point1.y,
                point2.x, point2.y - delta,
                point2.x, point2.y
            )

            2 -> cubicTo(
                point1.x, point1.y + delta,
                point2.x, point2.y,
                point2.x, point2.y
            )

            else -> cubicTo(
                point1.x, point1.y + delta,
                point2.x, point2.y - delta,
                point2.x, point2.y
            )
        }

        lineTo(point2.x, point2.y)
    }
    return path
}

private val paint = Paint()
