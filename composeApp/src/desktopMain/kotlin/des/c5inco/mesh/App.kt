package des.c5inco.mesh

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.testing.MeshTestTags
import des.c5inco.mesh.ui.GradientCanvas
import des.c5inco.mesh.ui.SidePanel
import model.SavedColor

@Composable
fun App(
    configuration: AppConfiguration,
    exportGraphicsLayer: GraphicsLayer,
    exportScale: Int,
) {
    val presetColors by configuration.presetColors.collectAsState(initial = emptyList())
    val customColors by configuration.customColors.collectAsState()
    val availableColors by configuration.availableColors.collectAsState(initial = emptyList())
    val canvasBackgroundColor by configuration.canvasBackgroundColor.collectAsState()
    val uiState by configuration.uiState.collectAsState()
    val meshState by configuration.meshState.collectAsState()

    Row(
        Modifier
            .fillMaxSize()
            .testTag(MeshTestTags.APP)
    ) {
        var selectedColorPoint: Pair<Int, Int>? by remember { mutableStateOf(null) }

        GradientCanvas(
            exportGraphicsLayer = exportGraphicsLayer,
            exportScale = exportScale,
            canvasWidthMode = meshState.canvasWidthMode,
            canvasWidth = meshState.canvasWidth,
            canvasHeightMode = meshState.canvasHeightMode,
            canvasHeight = meshState.canvasHeight,
            blurLevel = meshState.blurLevel,
            availableColors = availableColors,
            canvasBackgroundColor = canvasBackgroundColor,
            meshPoints = configuration.meshPoints,
            showPoints = uiState.showPoints,
            onResize = configuration::updateMeasuredCanvasSize,
            onTogglePoints = { configuration.toggleShowingPoints() },
            onPointDragStartEnd = { selectedColorPoint = it },
            onPointDrag = { row, col, point ->
                configuration.updateMeshPoint(row, col, point)
            },
            modifier = Modifier
                .weight(1f)
                .testTag(MeshTestTags.GRADIENT_CANVAS)
        )
        SidePanel(
            presetColors = presetColors,
            customColors = customColors,
            canvasBackgroundColor = canvasBackgroundColor,
            canvasWidthMode = meshState.canvasWidthMode,
            canvasWidth = meshState.canvasWidth,
            canvasHeightMode = meshState.canvasHeightMode,
            canvasHeight = meshState.canvasHeight,
            blurLevel = meshState.blurLevel,
            totalRows = meshState.rows,
            totalCols = meshState.cols,
            meshPoints = configuration.meshPoints,
            showPoints = uiState.showPoints,
            constrainEdgePoints = uiState.constrainEdgePoints,
            onCanvasWidthModeChange = configuration::updateCanvasWidthMode,
            onCanvasWidthChange = configuration::updateCanvasWidth,
            onCanvasHeightModeChange = configuration::updateCanvasHeightMode,
            onCanvasHeightChange = configuration::updateCanvasHeight,
            onBlurLevelChange = configuration::updateBlurLevel,
            onUpdateTotalRows = configuration::updateTotalRows,
            onUpdateTotalCols = configuration::updateTotalCols,
            onUpdateMeshPoint = { row, col, point ->
                configuration.updateMeshPoint(row, col, point)
            },
            onTogglePoints = { configuration.toggleShowingPoints() },
            onToggleConstrainingEdgePoints = { configuration.toggleConstrainingEdgePoints() },
            onDistributeMeshPointsEvenly = configuration::distributeMeshPointsEvenly,
            onCanvasBackgroundColorChange = configuration::updateCanvasBackgroundColor,
            onAddColor = {
                configuration.addColor(
                    SavedColor(
                        red = (255 * it.red).toInt(),
                        green = (255 * it.green).toInt(),
                        blue = (255 * it.blue).toInt(),
                        alpha = it.alpha
                    )
                )
            },
            onRemoveColor = configuration::deleteColor,
            selectedColorPoint = selectedColorPoint,
            modifier = Modifier.width(280.dp)
        )
    }
}
