package des.c5inco.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.ui.theme.colorPalette

@Composable
fun ColorSwatch(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .size(16.dp)
    ) {
        if (color == Color.Transparent) {
            Spacer(Modifier
                .drawBehind {
                    drawIntoCanvas {
                        drawPath(
                            path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, size.height)
                                close()
                            },
                            color = Color.Red,
                            style = Stroke(width = 2f)
                        )
                    }
                }
                .border(
                    1.dp,
                    JewelTheme.colorPalette.grayOrNull(7)
                        ?: requireNotNull(IntUiDarkTheme.colors.grayOrNull(7)),
                    RoundedCornerShape(4.dp),
                )
                .fillMaxSize()
            )
        } else Spacer(Modifier.fillMaxSize().background(color))
    }
}
