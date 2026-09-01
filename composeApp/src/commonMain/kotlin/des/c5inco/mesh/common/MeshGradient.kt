package des.c5inco.mesh.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.MeshGradientPainter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity

/**
 * Renders [points] with Compose's [MeshGradientPainter].
 *
 * [points] is a vertex grid. A grid of R×C vertices becomes a painter with
 * `rows = R - 1` and `columns = C - 1` patches.
 *
 * Vertex positions and colors are read from [points] during draw, so updates
 * (drags, palette changes) are reflected without reallocating the painter
 * unless the grid dimensions change.
 */
@Composable
fun Modifier.meshGradient(
    points: List<List<Pair<Offset, Color>>>,
    blurLevel: Int = 0,
    hasBicubicColor: Boolean = true,
): Modifier {
    val patchRows = (points.size - 1).coerceAtLeast(1)
    val patchColumns = ((points.firstOrNull()?.size ?: 1) - 1).coerceAtLeast(1)
    val pointsState = rememberUpdatedState(points)

    val painter = remember(patchRows, patchColumns, hasBicubicColor) {
        MeshGradientPainter(
            rows = patchRows,
            columns = patchColumns,
            hasBicubicColor = hasBicubicColor,
        ) {
            val currentPoints = pointsState.value
            for (row in currentPoints.indices) {
                val columns = currentPoints[row]
                for (column in columns.indices) {
                    val (position, color) = columns[column]
                    setVertex(
                        row = row,
                        column = column,
                        position = position,
                        color = color,
                    )
                }
            }
        }
    }

    val blurRadius = with(LocalDensity.current) { blurLevel.toDp().toPx() }

    return this
        .then(
            if (blurRadius > 0f) {
                Modifier.graphicsLayer {
                    renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
                    clip = false
                }
            } else {
                Modifier
            }
        )
        .paint(painter, contentScale = ContentScale.FillBounds)
}
