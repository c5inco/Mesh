package des.c5inco.mesh.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.DimensionMode
import des.c5inco.mesh.common.toColor
import des.c5inco.mesh.common.toHexStringNoHash
import des.c5inco.mesh.ui.components.ColorDropdown
import des.c5inco.mesh.ui.components.ColorSwatch
import des.c5inco.mesh.ui.components.DimensionInputField
import des.c5inco.mesh.ui.components.OffsetInputField
import kotlinx.coroutines.flow.collectLatest
import mesh.composeapp.generated.resources.Res
import mesh.composeapp.generated.resources.distributeEvenly_dark
import mesh.composeapp.generated.resources.featureCodeBlock_dark
import mesh.composeapp.generated.resources.modeFilled_dark
import mesh.composeapp.generated.resources.modeFixed_dark
import model.SavedColor
import model.toColor
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.DropdownLink
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.colorPalette

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SidePanel(
    exportScale: Int,
    presetColors: List<SavedColor> = emptyList(),
    customColors: List<SavedColor> = emptyList(),
    canvasBackgroundColor: Long,
    canvasWidthMode: DimensionMode,
    canvasWidth: Int,
    canvasHeightMode: DimensionMode,
    canvasHeight: Int,
    blurLevel: Float = 0f,
    totalRows: Int,
    totalCols: Int,
    meshPoints: List<List<Pair<Offset, Long>>> = emptyList(),
    showPoints: Boolean,
    constrainEdgePoints: Boolean,
    currentDocumentName: String = "Untitled",
    hasUnsavedChanges: Boolean = false,
    onCanvasWidthModeChange: () -> Unit = {},
    onCanvasWidthChange: (Int) -> Unit = {},
    onCanvasHeightModeChange: () -> Unit = {},
    onCanvasHeightChange: (Int) -> Unit = {},
    onBlurLevelChange: (Float) -> Unit = {},
    onUpdateTotalRows: (Int) -> Unit = {},
    onUpdateTotalCols: (Int) -> Unit = {},
    onUpdateMeshPoint: (row: Int, col: Int, point: Pair<Offset, Long>) -> Unit,
    onTogglePoints: () -> Unit = {},
    onToggleConstrainingEdgePoints: () -> Unit = {},
    onDistributeMeshPointsEvenly: () -> Unit = {},
    onExportScaleChange: (Int) -> Unit,
    onExport: () -> Unit = {},
    onExportCode: () -> Unit = {},
    onCanvasBackgroundColorChange: (Long) -> Unit = { _ -> },
    onAddColor: (Color) -> Unit = { _ -> },
    onRemoveColor: (SavedColor) -> Unit = { _ -> },
    onNewDocument: () -> Unit = {},
    onOpenDocument: () -> Unit = {},
    onSaveDocument: () -> Boolean = { false },
    onSaveDocumentAs: () -> Boolean = { false },
    selectedColorPoint: Pair<Int, Int>? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    
    VerticallyScrollableContainer(
        modifier = modifier
            .fillMaxHeight()
            .background(JewelTheme.globalColors.panelBackground),
    ) {
        Column {
            // Document section at the top
            DocumentSection(
                documentName = currentDocumentName,
                hasUnsavedChanges = hasUnsavedChanges,
                onNewDocument = onNewDocument,
                onOpenDocument = onOpenDocument,
                onSaveDocument = onSaveDocument,
                onSaveDocumentAs = onSaveDocumentAs,
            )

            Divider(
                orientation = Orientation.Horizontal,
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                var showColorInput by remember { mutableStateOf(false) }

                SectionHeader(title = "Colors")

                Spacer(Modifier.height(12.dp))
                Text("Presets", fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
                FlowRow(
                    maxItemsInEachRow = 10,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    presetColors.forEach { presetSavedColor ->
                        ColorSwatch(color = presetSavedColor.toColor())
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Custom", fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { showColorInput = true },
                        modifier = Modifier.size(14.dp)
                    ) {
                        Icon(
                            key = AllIconsKeys.General.InlineAdd,
                            iconClass = AllIconsKeys::class.java,
                            contentDescription = "Add color"
                        )
                    }
                }
                if (showColorInput) {
                    ColorInput(
                        onCancel = { showColorInput = false },
                        onSubmit = {
                            showColorInput = false
                            onAddColor(it)
                        },
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                FlowRow(
                    maxItemsInEachRow = 10,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    customColors.forEach { savedColor ->
                        ColorSwatch(
                            color = savedColor.toColor(),
                            modifier = Modifier.clickable {
                                onRemoveColor(savedColor)
                            }
                        )
                    }
                }
            }

            Divider(
                orientation = Orientation.Horizontal,
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            CanvasSection(
                exportScale = exportScale,
                backgroundColor = canvasBackgroundColor,
                canvasWidthMode = canvasWidthMode,
                canvasWidth = canvasWidth,
                canvasHeightMode = canvasHeightMode,
                canvasHeight = canvasHeight,
                blurLevel = blurLevel,
                availableColors = presetColors + customColors,
                onExportScaleChange = onExportScaleChange,
                onExport = onExport,
                onBackgroundColorChange = { onCanvasBackgroundColorChange(it) },
                onCanvasWidthModeChange = onCanvasWidthModeChange,
                onCanvasWidthChange = onCanvasWidthChange,
                onCanvasHeightModeChange = onCanvasHeightModeChange,
                onCanvasHeightChange = onCanvasHeightChange,
                onBlurLevelChange = { onBlurLevelChange(it) }
            )

            Divider(
                orientation = Orientation.Horizontal,
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            ) {
                SectionHeader(
                    title = "Points",
                    actions = {
                        Tooltip(tooltip = { Text("Export points as code") }) {
                            IconButton(onClick = {
                                onExportCode()
                                focusManager.clearFocus()
                            }) {
                                Icon(
                                    painter = painterResource(resource = Res.drawable.featureCodeBlock_dark),
                                    contentDescription = "Export points as code"
                                )
                            }
                        }
                        Spacer(Modifier.width(2.dp))
                        Tooltip(tooltip = { Text("Distribute points evenly") }) {
                            IconButton(
                                onClick = {
                                    onDistributeMeshPointsEvenly()
                                    focusManager.clearFocus()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(resource = Res.drawable.distributeEvenly_dark),
                                    contentDescription = "Distribute points evenly"
                                )
                            }
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    DimensionInputField(
                        value = totalRows,
                        enabled = true,
                        paramName = "Rows",
                        min = 2,
                        max = 10,
                        onUpdate = onUpdateTotalRows,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    DimensionInputField(
                        value = totalCols,
                        enabled = true,
                        paramName = "Cols",
                        min = 2,
                        max = 10,
                        onUpdate = onUpdateTotalCols,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                CheckboxRow(
                    text = "Show points",
                    checked = showPoints,
                    onCheckedChange = { onTogglePoints() },
                )
                CheckboxRow(
                    text = "Constrain edge points",
                    checked = constrainEdgePoints,
                    onCheckedChange = { onToggleConstrainingEdgePoints() },
                )
            }

            val selectedPointColor = JewelTheme.colorPalette.blue(6)
            val selectedHighlightHeight = 24.dp

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                meshPoints.forEachIndexed { rowIdx, rowPoints ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Row ${rowIdx + 1}",
                            fontWeight = FontWeight.SemiBold
                        )
                        rowPoints.forEachIndexed { colIdx, point ->
                            Box(
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                ColorPointRow(
                                    x = point.first.x,
                                    y = point.first.y,
                                    constrainX = constrainEdgePoints && (colIdx == 0 || colIdx == rowPoints.size - 1),
                                    constrainY = constrainEdgePoints && (rowIdx == 0 || rowIdx == meshPoints.size - 1),
                                    colorId = point.second,
                                    availableColors = presetColors + customColors,
                                    onUpdatePoint = { (nextOffset, nextColor) ->
                                        onUpdateMeshPoint(rowIdx, colIdx, Pair(Offset(x = nextOffset.x, y = nextOffset.y), nextColor))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (selectedColorPoint == Pair(rowIdx, colIdx)) {
                                    Spacer(
                                        Modifier
                                            .offset(x = -16.dp)
                                            .background(selectedPointColor)
                                            .width(4.dp)
                                            .height(selectedHighlightHeight)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = Typography.h4TextStyle(),
            fontWeight = FontWeight.SemiBold
        )
        Row {
            actions()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorInput(
    onSubmit: (Color) -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var color: Color by remember { mutableStateOf(Color.LightGray) }
    val textFieldState = remember { TextFieldState() }
    var isColorValid by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        snapshotFlow { textFieldState.text }
            .collectLatest {
                var text = it.toString()
                if (text.startsWith(("#"))) {
                    text = text.substring(1, 7)
                    textFieldState.edit { replace(0, it.length, text) }
                }

                try {
                    color = text.toColor()
                    println("converted: $color")
                } catch (e: Exception) {
                    println("fail")
                }
            }
    }

    fun reset() {
        textFieldState.edit { replace(0, textFieldState.text.length, color.toHexStringNoHash(false)) }
    }

    fun validate(): Boolean {
        val trimmed = textFieldState.text.toString().replace(Regex("[^A-Fa-f0-9]"), "")
        val formattedText = if (trimmed.length > 6) {
            trimmed.substring(0, 6)
        } else {
            trimmed
        }

        try {
            color = formattedText.toColor()
            textFieldState.edit { replace(0, originalText.length, formattedText) }
            println("converted: $color")
            return true
        } catch (e: Exception) {
            reset()
            println("fail")
        }
        return false
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        TextField(
            state = textFieldState,
            leadingIcon = {
                ColorSwatch(
                    color = color,
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            outline = if (!isColorValid) Outline.Error else Outline.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { validate() }
                .onKeyEvent {
                    when (it.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            isColorValid = validate()
                            if (isColorValid) onSubmit(color)
                        }
                        Key.Tab -> {
                            isColorValid = validate()
                            return@onKeyEvent false
                        }
                        Key.Escape -> {
                            onCancel()
                        }
                    }
                    return@onKeyEvent true
                }
                .weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Tooltip(tooltip = { Text("Cancel") }) {
            IconButton(
                onClick = onCancel
            ) {
                Icon(
                    key = AllIconsKeys.General.CloseSmall,
                    iconClass = AllIconsKeys::class.java,
                    contentDescription = "Cancel"
                )
            }
        }
    }
}

@Composable
private fun ColorPointRow(
    x: Float,
    y: Float,
    constrainX: Boolean,
    constrainY: Boolean,
    colorId: Long,
    availableColors: List<SavedColor> = emptyList(),
    onUpdatePoint: (Pair<Offset, Long>) -> Unit = { _ -> },
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        ColorDropdown(
            selectedColorId = colorId,
            colors = availableColors,
            onSelected = { onUpdatePoint(Pair(Offset(x = x, y = y), it)) }
        )
        OffsetInputField(
            value = x,
            enabled = !constrainX,
            paramName = "X",
            onUpdate = { onUpdatePoint(Pair(Offset(x = it, y = y), colorId)) },
            modifier = Modifier.weight(1f)
        )
        OffsetInputField(
            value = y,
            enabled = !constrainY,
            paramName = "Y",
            onUpdate = { onUpdatePoint(Pair(Offset(x = x, y = it), colorId)) },
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasSection(
    exportScale: Int,
    availableColors: List<SavedColor> = emptyList(),
    backgroundColor: Long,
    canvasWidthMode: DimensionMode,
    canvasWidth: Int,
    canvasHeightMode: DimensionMode,
    canvasHeight: Int,
    blurLevel: Float,
    onExportScaleChange: (Int) -> Unit,
    onExport: () -> Unit = {},
    onBackgroundColorChange: (Long) -> Unit = {},
    onCanvasWidthModeChange: () -> Unit = {},
    onCanvasWidthChange: (Int) -> Unit = {},
    onCanvasHeightModeChange: () -> Unit = {},
    onCanvasHeightChange: (Int) -> Unit = {},
    onBlurLevelChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier.padding(16.dp),
    ) {
        SectionHeader(title = "Canvas")
        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ColorDropdown(
                selectedColorId = backgroundColor,
                colors = availableColors,
                allowTransparency = true,
                onSelected = { onBackgroundColorChange(it) }
            )
            DimensionInputField(
                value = canvasWidth,
                enabled = canvasWidthMode == DimensionMode.Fixed,
                paramName = "W",
                min = 100,
                trailingIcon = {
                    Tooltip(tooltip = {
                        Text(text = "Toggle to ${if (canvasWidthMode == DimensionMode.Fixed) "Filled" else "Fixed"}")
                    }) {
                        IconButton(
                            onClick = {
                                onCanvasWidthModeChange()
                                focusManager.clearFocus()
                            }
                        ) {
                            Icon(
                                painter = painterResource(getModeIcon(canvasWidthMode)),
                                contentDescription = null
                            )
                        }
                    }
                },
                onUpdate = onCanvasWidthChange,
                modifier = Modifier.weight(1f)
            )
            DimensionInputField(
                value = canvasHeight,
                enabled = canvasHeightMode == DimensionMode.Fixed,
                paramName = "H",
                min = 100,
                trailingIcon = {
                    Tooltip(tooltip = {
                        Text(text = "Toggle to ${if (canvasHeightMode == DimensionMode.Fixed) "Filled" else "Fixed"}")
                    }) {
                        IconButton(
                            onClick = {
                                onCanvasHeightModeChange()
                                focusManager.clearFocus()
                            }
                        ) {
                            Icon(
                                painter = painterResource(getModeIcon(canvasHeightMode)),
                                contentDescription = null
                            )
                        }
                    }
                },
                onUpdate = onCanvasHeightChange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Link(
                text = "Export",
                onClick = onExport
            )
            Spacer(Modifier.width(4.dp))
            DropdownLink(
                text = "@${exportScale}x",
            ) {
                repeat(3) {
                    selectableItem(
                        selected = exportScale == it + 1,
                        onClick = {
                            onExportScaleChange(it + 1)
                        },
                    ) {
                        Text("${it + 1}x")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Blur")
            Spacer(Modifier.width(16.dp))
            Slider(
                value = blurLevel,
                onValueChange = onBlurLevelChange
            )
        }
    }
}

private fun getModeIcon(mode: DimensionMode): DrawableResource {
    return if (mode == DimensionMode.Fixed) {
        Res.drawable.modeFixed_dark
    } else {
        Res.drawable.modeFilled_dark
    }
}

@Composable
private fun DocumentSection(
    documentName: String,
    hasUnsavedChanges: Boolean,
    onNewDocument: () -> Unit = {},
    onOpenDocument: () -> Unit = {},
    onSaveDocument: () -> Boolean = { false },
    onSaveDocumentAs: () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Document",
                style = Typography.h4TextStyle(),
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Document name with unsaved indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = documentName + if (hasUnsavedChanges) " •" else "",
                fontSize = 13.sp,
                color = JewelTheme.globalColors.text.normal,
                fontWeight = if (hasUnsavedChanges) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Document controls
        FlowRow(
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Link(
                text = "New",
                onClick = onNewDocument
            )
            Link(
                text = "Open",
                onClick = onOpenDocument
            )
            Link(
                text = "Save",
                onClick = { onSaveDocument() }
            )
            Link(
                text = "Save As...",
                onClick = { onSaveDocumentAs() }
            )
        }
    }
}