package des.c5inco.mesh

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.dp
import des.c5inco.mesh.ui.GradientCanvas
import des.c5inco.mesh.ui.SidePanel
import des.c5inco.mesh.ui.data.AppState
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    Row(
        Modifier.fillMaxSize()
    ) {
        var selectedColorPoint: Pair<Int, Int>? by remember { mutableStateOf(null) }
        var exportScale by remember { mutableStateOf(1) }
        val exportGraphicsLayer = rememberGraphicsLayer()
        val coroutineScope = rememberCoroutineScope()

        GradientCanvas(
          exportGraphicsLayer = exportGraphicsLayer,
          exportScale = exportScale,
          modifier = Modifier.weight(1f)
        ) { selectedColorPoint = it }
      SidePanel(
            exportScale = exportScale,
            onExportScaleChange = { exportScale = it },
            onExport = {
                coroutineScope.launch {
                    val bitmap = exportGraphicsLayer.toImageBitmap()
                    val awtImage = bitmap.toAwtImage()

                    AppState.saveImage(image = awtImage, scale = exportScale)
                }
            },
            selectedColorPoint = selectedColorPoint,
            modifier = Modifier.width(280.dp)
        )
    }
}
