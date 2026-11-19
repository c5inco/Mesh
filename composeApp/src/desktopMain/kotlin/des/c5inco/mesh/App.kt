package des.c5inco.mesh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.testing.MeshTestTags
import des.c5inco.mesh.ui.BezierHandlesPopup
import des.c5inco.mesh.ui.GradientCanvas
import des.c5inco.mesh.ui.SidePanel
import model.HandleSide
import model.SavedColor
import model.effectiveHandles
import model.handlesAt
import kotlin.math.roundToInt

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
    val pointPositions = configuration.meshPoints.map { row -> row.map { it.first } }
    var appOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .testTag(MeshTestTags.APP)
            .onGloballyPositioned { appOrigin = it.localToWindow(Offset.Zero) }
    ) {
        var selectedPoint: Pair<Int, Int>? by remember { mutableStateOf(null) }
        var activeHandle: HandleSide? by remember { mutableStateOf(null) }
        var handlesPopupPoint: Pair<Int, Int>? by remember { mutableStateOf(null) }
        var handlesPopupAnchor by remember { mutableStateOf(IntOffset.Zero) }

        // A resized or replaced grid can leave the selection or the popup pointing past its new edge.
        LaunchedEffect(meshState.rows, meshState.cols) {
            selectedPoint = selectedPoint?.takeIf { (row, col) ->
                row in 0 until meshState.rows && col in 0 until meshState.cols
            }
            handlesPopupPoint = handlesPopupPoint?.takeIf { (row, col) ->
                row in 0 until meshState.rows && col in 0 until meshState.cols
            }
        }

        Row(Modifier.fillMaxSize()) {
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
                meshHandles = configuration.meshHandles,
                showPoints = uiState.showPoints,
                selectedPoint = selectedPoint,
                activeHandle = activeHandle,
                onResize = configuration::updateMeasuredCanvasSize,
                onTogglePoints = { configuration.toggleShowingPoints() },
                onPointDragStartEnd = { point ->
                    if (point != null) {
                        if (selectedPoint != point) activeHandle = null
                        selectedPoint = point
                    }
                },
                onPointSelected = { row, col ->
                    if (selectedPoint != (row to col)) activeHandle = null
                    selectedPoint = row to col
                },
                onHandleActivated = { activeHandle = it },
                onHandleDrag = { row, col, side, offset ->
                    configuration.updateMeshHandle(row, col, side, offset)
                },
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
                selectedPoint = selectedPoint,
                handlesPopupPoint = handlesPopupPoint,
                onToggleHandles = { row, col, anchor ->
                    if (handlesPopupPoint == (row to col)) {
                        handlesPopupPoint = null
                        activeHandle = null
                    } else {
                        if (selectedPoint != (row to col)) activeHandle = null
                        selectedPoint = row to col
                        handlesPopupAnchor = anchor
                        handlesPopupPoint = row to col
                    }
                },
                modifier = Modifier.width(280.dp)
            )
        }

        handlesPopupPoint?.takeIf { (row, col) ->
            row in pointPositions.indices && col in pointPositions[row].indices
        }?.let { (row, col) ->
            val explicitHandles = configuration.meshHandles.handlesAt(row, col)
            BezierHandlesPopup(
                row = row,
                col = col,
                handles = pointPositions.effectiveHandles(row, col, explicitHandles),
                explicitHandles = explicitHandles,
                mirroring = { side -> configuration.handleMirroring(row, col, side) },
                anchor = IntOffset(
                    handlesPopupAnchor.x - appOrigin.x.roundToInt(),
                    handlesPopupAnchor.y - appOrigin.y.roundToInt(),
                ),
                onUpdate = { side, offset -> configuration.updateMeshHandle(row, col, side, offset) },
                onMirroringChange = { side, mode -> configuration.setHandleMirroring(row, col, side, mode) },
                onResetAll = { configuration.resetMeshHandles(row, col) },
                onActivateSide = { activeHandle = it },
                onDismiss = {
                    handlesPopupPoint = null
                    activeHandle = null
                },
            )
        }
    }
}
