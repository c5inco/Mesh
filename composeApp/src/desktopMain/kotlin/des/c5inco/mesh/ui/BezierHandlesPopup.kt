package des.c5inco.mesh.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.HandleMirroring
import des.c5inco.mesh.testing.MeshTestTags
import des.c5inco.mesh.ui.components.OffsetInputField
import mesh.composeapp.generated.resources.Res
import mesh.composeapp.generated.resources.handlesIndependent_dark
import mesh.composeapp.generated.resources.handlesMirrorAngleAndLength_dark
import mesh.composeapp.generated.resources.handlesMirrorAngle_dark
import model.HandleSide
import model.PointHandles
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.colorPalette
import kotlin.math.roundToInt

/**
 * A floating editor for one point's four Bezier handles. It opens next to the point's row, can be
 * dragged by its header, closes with its close button or Escape, and dismisses on a click outside it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BezierHandlesPopup(
    row: Int,
    col: Int,
    handles: PointHandles,
    explicitHandles: PointHandles,
    mirroring: (HandleSide) -> HandleMirroring,
    anchor: IntOffset,
    onUpdate: (HandleSide, Offset) -> Unit,
    onMirroringChange: (HandleSide, HandleMirroring) -> Unit,
    onResetAll: () -> Unit,
    onActivateSide: (HandleSide) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = JewelTheme.colorPalette.blueOrNull(6)
        ?: requireNotNull(IntUiDarkTheme.colors.blueOrNull(6))
    val shape = RoundedCornerShape(10.dp)
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val popupWidth = 336.dp
    val popupWidthPx = with(density) { popupWidth.roundToPx() }
    val estimatedHeightPx = with(density) { 170.dp.roundToPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        var position by remember(anchor) { mutableStateOf<IntOffset?>(null) }
        val gap = with(density) { 12.dp.roundToPx() }
        val basePosition = IntOffset(anchor.x - popupWidthPx - gap, anchor.y - gap)
        val minX = gap
        val minY = gap
        val maxX = (constraints.maxWidth - popupWidthPx - gap).coerceAtLeast(minX)
        val cardHeight = cardSize.height.takeIf { it > 0 } ?: estimatedHeightPx
        val maxY = (constraints.maxHeight - cardHeight - gap).coerceAtLeast(minY)
        val shown = (position ?: basePosition).let { current ->
            IntOffset(current.x.coerceIn(minX, maxX), current.y.coerceIn(minY, maxY))
        }

        fun dragBy(delta: IntOffset) {
            val current = (position ?: basePosition).let {
                IntOffset(it.x.coerceIn(minX, maxX), it.y.coerceIn(minY, maxY))
            }
            position = IntOffset(
                (current.x + delta.x).coerceIn(minX, maxX),
                (current.y + delta.y).coerceIn(minY, maxY),
            )
        }

        // The transparent scrim catches a click outside the card, which dismisses the popup.
        Box(
            Modifier
                .fillMaxSize()
                .testTag(MeshTestTags.HANDLES_POPUP_SCRIM)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )

        Column(
            Modifier
                .offset { IntOffset(shown.x, shown.y) }
                .width(popupWidth)
                .onSizeChanged { cardSize = it }
                .shadow(18.dp, shape)
                .clip(shape)
                .background(JewelTheme.globalColors.panelBackground)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                // Clicks on the card's own empty space must not reach the scrim behind it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .testTag(MeshTestTags.HANDLES_POPUP)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(anchor, minX, maxX, minY, maxY) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            dragBy(IntOffset(dragAmount.x.roundToInt(), dragAmount.y.roundToInt()))
                        }
                    }
                    .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            ) {
                Text("Control points", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!explicitHandles.isAutomatic) {
                    Text(
                        "Reset all",
                        fontSize = 10.sp,
                        color = accent,
                        modifier = Modifier
                            .testTag(MeshTestTags.HANDLES_RESET_ALL)
                            .clickable(onClick = onResetAll)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp).testTag(MeshTestTags.HANDLES_POPUP_CLOSE),
                ) {
                    Icon(
                        key = AllIconsKeys.General.CloseSmall,
                        iconClass = AllIconsKeys::class.java,
                        contentDescription = "Close control points",
                    )
                }
            }

            Divider(orientation = Orientation.Horizontal, thickness = 1.dp, modifier = Modifier.fillMaxWidth())

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
            ) {
                @Composable
                fun ControlPointPair(side: HandleSide, pair: String) {
                    val mode = mirroring(side)
                    val nextMode = HandleMirroring.entries[(mode.ordinal + 1) % HandleMirroring.entries.size]
                    val pairLabel = "${side.label}/${side.opposite.label.lowercase()}"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        ControlPointInputs(row, col, side, handles[side] ?: Offset.Zero, onUpdate, onActivateSide)
                        Tooltip(tooltip = { Text("$pairLabel: ${mode.label}. Click for ${nextMode.label.lowercase()}.") }) {
                            IconButton(
                                onClick = { onMirroringChange(side, nextMode) },
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .size(24.dp)
                                    .testTag(MeshTestTags.handleMirroring(row, col, pair))
                                    .semantics {
                                        contentDescription = "$pairLabel: ${mode.label}"
                                        stateDescription = mode.label
                                    },
                            ) {
                                Icon(
                                    painter = painterResource(
                                        when (mode) {
                                            HandleMirroring.NONE -> Res.drawable.handlesIndependent_dark
                                            HandleMirroring.ANGLE -> Res.drawable.handlesMirrorAngle_dark
                                            HandleMirroring.ANGLE_AND_LENGTH -> Res.drawable.handlesMirrorAngleAndLength_dark
                                        }
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        val opposite = side.opposite
                        ControlPointInputs(row, col, opposite, handles[opposite] ?: Offset.Zero, onUpdate, onActivateSide)
                    }
                }

                ControlPointPair(HandleSide.Left, "horizontal")
                ControlPointPair(HandleSide.Top, "vertical")
            }
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
private fun ControlPointInputs(
    row: Int,
    col: Int,
    side: HandleSide,
    value: Offset,
    onUpdate: (HandleSide, Offset) -> Unit,
    onActivate: (HandleSide) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(140.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onActivate(side) }
            .padding(vertical = 4.dp),
    ) {
        Text(side.label, fontSize = 11.sp)
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontSize = 11.sp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OffsetInputField(
                    value = value.x,
                    enabled = true,
                    paramName = "X",
                    onUpdate = {
                        onActivate(side)
                        onUpdate(side, Offset(it, value.y))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .testTag(MeshTestTags.handleField(row, col, side.slug, "x"))
                        .onFocusChanged { if (it.isFocused) onActivate(side) },
                )
                OffsetInputField(
                    value = value.y,
                    enabled = true,
                    paramName = "Y",
                    onUpdate = {
                        onActivate(side)
                        onUpdate(side, Offset(value.x, it))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .testTag(MeshTestTags.handleField(row, col, side.slug, "y"))
                        .onFocusChanged { if (it.isFocused) onActivate(side) },
                )
            }
        }
    }
}

private val HandleSide.label: String
    get() = when (this) {
        HandleSide.Left -> "Left"
        HandleSide.Top -> "Top"
        HandleSide.Right -> "Right"
        HandleSide.Bottom -> "Bottom"
    }

private val HandleSide.slug: String get() = name.lowercase()

private val HandleMirroring.label: String
    get() = when (this) {
        HandleMirroring.NONE -> "No mirroring"
        HandleMirroring.ANGLE -> "Mirror angle"
        HandleMirroring.ANGLE_AND_LENGTH -> "Mirror angle and length"
    }
