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
fun ControlHandleCursor(
    side: HandleSide,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(16.dp)
            .testTag(MeshTestTags.controlHandle(side.shortLabel.lowercase()))
            .semantics(mergeDescendants = true) {
                contentDescription = "${side.label} control handle"
            }
            .drawWithContent {
                drawContent()
                drawCircle(color = Color(0xFFFFC14D))
                drawCircle(
                    color = Color.White,
                    style = Stroke(width = 3.dp.toPx()),
                )
            },
    ) {
        Text(side.shortLabel, color = Color.Black)
    }
}
