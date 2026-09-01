package des.c5inco.mesh.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import des.c5inco.mesh.testing.MeshTestTags
import org.jetbrains.jewel.ui.component.Text

@Composable
fun PointCursor(
    xIndex: Int,
    yIndex: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(20.dp)
            .testTag(MeshTestTags.pointCursor(row = yIndex, col = xIndex))
            .semantics(mergeDescendants = true) {
                contentDescription = "Mesh point $xIndex,$yIndex"
            }
            .drawWithContent {
                drawContent()
                drawCircle(
                    color = color
                )
                drawCircle(
                    color = Color.White,
                    style = Stroke(width = 4.dp.toPx())
                ) // Fill is transparent by default
            }
    ) {
        Text("$xIndex,$yIndex", color = Color.White)
    }
}
