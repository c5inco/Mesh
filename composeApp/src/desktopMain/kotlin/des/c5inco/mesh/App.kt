package des.c5inco.mesh

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.dp
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.data.Notifications
import des.c5inco.mesh.ui.GradientCanvas
import des.c5inco.mesh.ui.SidePanel
import kotlinx.coroutines.launch
import model.SavedColor

@Composable
fun App(
    configuration: AppConfiguration,
) {
    val presetColors by configuration.presetColors.collectAsState(initial = emptyList())
    val customColors by configuration.customColors.collectAsState(initial = emptyList())
    val availableColors by configuration.availableColors.collectAsState()
    val canvasBackgroundColor by configuration.canvasBackgroundColor.collectAsState()
    val uiState by configuration.uiState.collectAsState()
    val resolution by configuration.resolution.collectAsState()
    val canvasWidthMode by configuration.canvasWidthMode.collectAsState()
    val canvasWidth by configuration.canvasWidth.collectAsState()
    val canvasHeightMode by configuration.canvasHeightMode.collectAsState()
    val canvasHeight by configuration.canvasHeight.collectAsState()
    val blurLevel by configuration.blurLevel.collectAsState()
    val totalRows by configuration.totalRows.collectAsState()
    val totalCols by configuration.totalCols.collectAsState()

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
            resolution = resolution,
            canvasWidthMode = canvasWidthMode,
            canvasWidth = canvasWidth,
            canvasHeightMode = canvasHeightMode,
            canvasHeight = canvasHeight,
            blurLevel = blurLevel,
            availableColors = availableColors,
            canvasBackgroundColor = canvasBackgroundColor,
            meshPoints = configuration.meshPoints,
            showPoints = uiState.showPoints,
            onResize = { width, height ->
                configuration.updateCanvasWidth(width)
                configuration.updateCanvasHeight(height)
            },
            onTogglePoints = { configuration.toggleShowingPoints() },
            onPointDragStartEnd = { selectedColorPoint = it },
            onPointDrag = { row, col, point ->
                configuration.updateMeshPoint(row, col, point)
            },
            modifier = Modifier.weight(1f)
        )
        SidePanel(
            exportScale = exportScale,
            presetColors = presetColors,
            customColors = customColors,
            canvasBackgroundColor = canvasBackgroundColor,
            canvasWidthMode = canvasWidthMode,
            canvasWidth = canvasWidth,
            canvasHeightMode = canvasHeightMode,
            canvasHeight = canvasHeight,
            blurLevel = blurLevel,
            totalRows = totalRows,
            totalCols = totalCols,
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
            onExportScaleChange = { exportScale = it },
            onExport = {
                coroutineScope.launch {
                    val bitmap = exportGraphicsLayer.toImageBitmap()
                    val awtImage = bitmap.toAwtImage()

                    AppConfiguration.saveImage(image = awtImage, scale = exportScale)
                }
            },
            onExportCode = {
                configuration.exportMeshPointsAsCode()
                Notifications.send("📋 Points copied to the clipboard!")
            },
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
            onRemoveColor = {
                configuration.removeColorFromMeshPoints(it.uid)
                configuration.deleteColor(it)
            },
            selectedColorPoint = selectedColorPoint,
            modifier = Modifier.width(280.dp)
        )
    }
}