package des.c5inco.mesh.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.DimensionMode
import data.MAX_GRID_SIZE
import data.MIN_GRID_SIZE
import des.c5inco.mesh.common.toColor
import des.c5inco.mesh.common.toHexStringNoHash
import des.c5inco.mesh.ui.components.ColorDropdown
import des.c5inco.mesh.ui.components.ColorSwatch
import des.c5inco.mesh.ui.components.DimensionInputField
import des.c5inco.mesh.ui.components.OffsetInputField
import des.c5inco.mesh.testing.MeshTestTags
import kotlinx.coroutines.flow.collectLatest
import mesh.composeapp.generated.resources.Res
import mesh.composeapp.generated.resources.distributeEvenly_dark
import mesh.composeapp.generated.resources.modeFilled_dark
import mesh.composeapp.generated.resources.modeFixed_dark
import model.SavedColor
import model.toColor
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupContainer
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography
import org.jetbrains.jewel.ui.theme.colorPalette

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SidePanel(
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
    onCanvasBackgroundColorChange: (Long) -> Unit = { _ -> },
    onAddColor: (Color) -> Unit = { _ -> },
    onRemoveColor: (SavedColor) -> Unit = { _ -> },
    selectedColorPoint: Pair<Int, Int>? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    // Kept here rather than in their items, so they survive those items scrolling out of view.
    var colorInput by remember { mutableStateOf<ColorInputState?>(null) }
    var colorPendingDeletion by remember { mutableStateOf<SavedColor?>(null) }
    // One list for every point row, so rows whose point didn't change can skip recomposing.
    val availableColors = remember(presetColors, customColors) { presetColors + customColors }
    val listState = rememberLazyListState()

    VerticallyScrollableContainer(
        scrollState = listState,
        modifier = modifier
            .fillMaxHeight()
            .background(JewelTheme.globalColors.panelBackground)
            .testTag(MeshTestTags.SIDE_PANEL),
    ) {
        // Only the rows on screen are composed, so a large grid costs no more to show than a small one.
        LazyColumn(state = listState) {
            item(key = "colors") {
                // The delete confirmation is anchored to a swatch in this item: when the item scrolls away,
                // close it rather than have it reappear whenever the item scrolls back.
                DisposableEffect(Unit) {
                    onDispose { colorPendingDeletion = null }
                }
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
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
                            onClick = {
                                val open = colorInput
                                if (open == null) colorInput = ColorInputState() else open.focusRequester.requestFocus()
                            },
                            modifier = Modifier
                                .size(14.dp)
                                .testTag(MeshTestTags.ADD_CUSTOM_COLOR),
                        ) {
                            Icon(
                                key = AllIconsKeys.General.InlineAdd,
                                iconClass = AllIconsKeys::class.java,
                                contentDescription = "Add color"
                            )
                        }
                    }
                    val editingColor = colorInput
                    if (editingColor != null) {
                        ColorInput(
                            state = editingColor,
                            onCancel = { colorInput = null },
                            onSubmit = {
                                colorInput = null
                                onAddColor(it)
                            },
                            modifier = Modifier.padding(vertical = 8.dp),
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
                            // The confirmation popup anchors to the swatch that opened it.
                            Box {
                                ColorSwatch(
                                    color = savedColor.toColor(),
                                    modifier = Modifier
                                        .testTag(MeshTestTags.customColor(savedColor.toColor().toHexStringNoHash()))
                                        // Until the mesh loads, its points can't be checked or cleared.
                                        .clickable(enabled = meshPoints.isNotEmpty()) {
                                            colorInput = null
                                            colorPendingDeletion = savedColor
                                        }
                                )
                                if (colorPendingDeletion == savedColor) {
                                    DeleteColorConfirmation(
                                        color = savedColor,
                                        pointsUsingColor = meshPoints.sumOf { row -> row.count { it.second == savedColor.uid } },
                                        backgroundUsesColor = canvasBackgroundColor == savedColor.uid,
                                        onCancel = { colorPendingDeletion = null },
                                        onConfirm = {
                                            colorPendingDeletion = null
                                            onRemoveColor(savedColor)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Divider(
                    orientation = Orientation.Horizontal,
                    thickness = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item(key = "canvas") {
                CanvasSection(
                    backgroundColor = canvasBackgroundColor,
                    canvasWidthMode = canvasWidthMode,
                    canvasWidth = canvasWidth,
                    canvasHeightMode = canvasHeightMode,
                    canvasHeight = canvasHeight,
                    blurLevel = blurLevel,
                    availableColors = availableColors,
                    onBackgroundColorChange = { onCanvasBackgroundColorChange(it) },
                    onCanvasWidthModeChange = onCanvasWidthModeChange,
                    onCanvasWidthChange = onCanvasWidthChange,
                    onCanvasHeightModeChange = onCanvasHeightModeChange,
                    onCanvasHeightChange = onCanvasHeightChange,
                    onBlurLevelChange = { onBlurLevelChange(it) }
                )
            }

            item(key = "points") {
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
                            min = MIN_GRID_SIZE,
                            max = MAX_GRID_SIZE,
                            onUpdate = onUpdateTotalRows,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(MeshTestTags.ROWS_INPUT)
                        )
                        Spacer(Modifier.width(8.dp))
                        DimensionInputField(
                            value = totalCols,
                            enabled = true,
                            paramName = "Cols",
                            min = MIN_GRID_SIZE,
                            max = MAX_GRID_SIZE,
                            onUpdate = onUpdateTotalCols,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(MeshTestTags.COLS_INPUT)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    CheckboxRow(
                        text = "Show points",
                        checked = showPoints,
                        onCheckedChange = { onTogglePoints() },
                        modifier = Modifier.testTag(MeshTestTags.SHOW_POINTS),
                    )
                    CheckboxRow(
                        text = "Constrain edge points",
                        checked = constrainEdgePoints,
                        onCheckedChange = { onToggleConstrainingEdgePoints() },
                    )
                }
            }

            meshPoints.forEachIndexed { rowIdx, rowPoints ->
                item(key = "row-$rowIdx", contentType = "row-header") {
                    Text(
                        text = "Row ${rowIdx + 1}",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                itemsIndexed(
                    items = rowPoints,
                    key = { colIdx, _ -> "point-$rowIdx-$colIdx" },
                    contentType = { _, _ -> "point" },
                ) { colIdx, point ->
                    val lastInRow = colIdx == rowPoints.lastIndex
                    PointListItem(
                        row = rowIdx,
                        col = colIdx,
                        point = point,
                        constrainX = constrainEdgePoints && (colIdx == 0 || lastInRow),
                        constrainY = constrainEdgePoints && (rowIdx == 0 || rowIdx == meshPoints.lastIndex),
                        availableColors = availableColors,
                        selected = selectedColorPoint == Pair(rowIdx, colIdx),
                        onUpdateMeshPoint = onUpdateMeshPoint,
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = when {
                                !lastInRow -> 8.dp
                                rowIdx == meshPoints.lastIndex -> 16.dp
                                else -> 0.dp
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** One point's editor in the list: its color and position, with a bar when it's selected on the canvas. */
@Composable
private fun PointListItem(
    row: Int,
    col: Int,
    point: Pair<Offset, Long>,
    constrainX: Boolean,
    constrainY: Boolean,
    availableColors: List<SavedColor>,
    selected: Boolean,
    onUpdateMeshPoint: (row: Int, col: Int, point: Pair<Offset, Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier,
    ) {
        ColorPointRow(
            x = point.first.x,
            y = point.first.y,
            constrainX = constrainX,
            constrainY = constrainY,
            colorId = point.second,
            availableColors = availableColors,
            colorDropdownTestTag = MeshTestTags.pointColor(row, col),
            onUpdatePoint = { onUpdateMeshPoint(row, col, it) },
            modifier = Modifier.fillMaxWidth()
        )

        if (selected) {
            val selectedPointColor = JewelTheme.colorPalette.blueOrNull(6)
                ?: requireNotNull(IntUiDarkTheme.colors.blueOrNull(6))
            Spacer(
                Modifier
                    .offset(x = -16.dp)
                    .background(selectedPointColor)
                    .width(4.dp)
                    .height(24.dp)
            )
        }
    }
}

@Composable
private fun DeleteColorConfirmation(
    color: SavedColor,
    pointsUsingColor: Int,
    backgroundUsesColor: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val gapPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    PopupContainer(
        onDismissRequest = { onCancel() },
        horizontalAlignment = Alignment.End,
        popupPositionProvider = remember(gapPx) { SwatchPopupPositionProvider(gapPx) },
    ) {
        Column(Modifier.width(260.dp).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorSwatch(color = color.toColor())
                Spacer(Modifier.width(8.dp))
                Text("Delete #${color.toColor().toHexStringNoHash()}?")
            }
            Spacer(Modifier.height(4.dp))
            val consequences = buildList {
                when (pointsUsingColor) {
                    0 -> {}
                    1 -> add("1 point uses it and will turn transparent.")
                    else -> add("$pointsUsingColor points use it and will turn transparent.")
                }
                if (backgroundUsesColor) add("The canvas background uses it and will reset to the default.")
            }.ifEmpty { listOf("Nothing in the mesh uses this color.") }
            consequences.forEach {
                Text(it, fontSize = 12.sp, color = JewelTheme.globalColors.text.info)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.testTag(MeshTestTags.DELETE_COLOR_CANCEL)) {
                    Text("Cancel")
                }
                DefaultButton(onClick = onConfirm, modifier = Modifier.testTag(MeshTestTags.DELETE_COLOR_CONFIRM)) {
                    Text("Delete")
                }
            }
        }
    }
}

/**
 * Places a popup below its swatch, right-aligned so it opens toward the canvas, and keeps it inside
 * the window: Jewel's menu positioning doesn't move popups back inside the window horizontally.
 */
private class SwatchPopupPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
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
            style = JewelTheme.typography.h4TextStyle,
            fontWeight = FontWeight.SemiBold
        )
        Row {
            actions()
        }
    }
}

/**
 * The custom color editor's state. The panel keeps it while the editor is open, because the editor's list
 * item is disposed whenever it scrolls out of view.
 */
@Stable
private class ColorInputState {
    var color: Color by mutableStateOf(Color.LightGray)
    val textFieldState = TextFieldState(color.toHexStringNoHash(false))
    var isColorValid by mutableStateOf(true)
    var isFocused = false
    val focusRequester = FocusRequester()

    /** Focus the field when it's first shown, since the user just opened it; not when it scrolls back into view. */
    var focusWhenShown = true
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorInput(
    state: ColorInputState,
    onSubmit: (Color) -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var color by state::color
    val textFieldState = state.textFieldState
    var isColorValid by state::isColorValid
    val focusRequester = state.focusRequester

    LaunchedEffect(state) {
        if (state.focusWhenShown) {
            state.focusWhenShown = false
            focusRequester.requestFocus()
        }
        snapshotFlow { textFieldState.text }
            .collectLatest {
                var text = it.toString()
                if (text.startsWith(("#"))) {
                    // Up to six digits after the hash: a lone "#" or a short color must not throw.
                    text = text.drop(1).take(6)
                    textFieldState.edit { replace(0, it.length, text) }
                }

                runCatching { text.toColor() }.onSuccess { color = it }
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
            return true
        } catch (e: Exception) {
            reset()
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
                .testTag(MeshTestTags.CUSTOM_COLOR_INPUT)
                // Only when focus really moves: not when the field comes back into view, which would
                // overwrite a half-typed color.
                .onFocusChanged {
                    if (it.isFocused != state.isFocused) {
                        state.isFocused = it.isFocused
                        validate()
                    }
                }
                .onKeyEvent {
                    when (it.key) {
                        // Only on key down: the key-up can reach this field before it closes and add the color again.
                        Key.Enter, Key.NumPadEnter -> if (it.type == KeyEventType.KeyDown) {
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
    colorDropdownTestTag: String? = null,
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
            onSelected = { onUpdatePoint(Pair(Offset(x = x, y = y), it)) },
            modifier = colorDropdownTestTag?.let { Modifier.testTag(it) } ?: Modifier,
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
    availableColors: List<SavedColor> = emptyList(),
    backgroundColor: Long,
    canvasWidthMode: DimensionMode,
    canvasWidth: Int,
    canvasHeightMode: DimensionMode,
    canvasHeight: Int,
    blurLevel: Float,
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
