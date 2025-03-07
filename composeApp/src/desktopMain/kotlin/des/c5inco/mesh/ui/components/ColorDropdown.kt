package des.c5inco.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import des.c5inco.mesh.common.toHexStringNoHash
import des.c5inco.mesh.ui.data.AppState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.DropdownState
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.outline
import org.jetbrains.jewel.ui.theme.dropdownStyle
import org.jetbrains.jewel.ui.util.thenIf

@Composable
fun ColorDropdown(
    selectedColor: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    allowTransparency: Boolean = false,
    onSelected: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    DropdownButton(
        modifier = modifier,
        menuModifier = Modifier.offset(x = (-2).dp),
        menuContent = {
            if (allowTransparency) {
                selectableItem(
                    selected = selectedColor == -1,
                    onClick = {
                        focusManager.clearFocus()
                        onSelected(-1)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorSwatch(color = Color.Transparent)
                        Spacer(Modifier.width(8.dp))
                        Text("Transparent")
                    }
                }
            }
            colors.forEachIndexed { index, color ->
                selectableItem(
                    selected = selectedColor == index,
                    onClick = {
                        focusManager.clearFocus()
                        onSelected(index)
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorSwatch(color = color)
                        Spacer(Modifier.width(8.dp))
                        Text(color.toHexStringNoHash(false))
                    }
                }
            }
        },
    ) {
        ColorSwatch(
            color = AppState.getColor(selectedColor),
            modifier = Modifier.padding(vertical = 5.dp, horizontal = 8.dp)
        )
    }
}

// Forked from Jewel Dropdown
@Composable
private fun DropdownButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuModifier: Modifier = Modifier,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: DropdownStyle = JewelTheme.dropdownStyle,
    menuContent: MenuScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var skipNextClick by remember { mutableStateOf(false) }

    var dropdownState by remember(interactionSource) { mutableStateOf(DropdownState.of(enabled = enabled)) }

    remember(enabled) { dropdownState = dropdownState.copy(enabled = enabled) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> dropdownState = dropdownState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> dropdownState = dropdownState.copy(pressed = false)
                is HoverInteraction.Enter -> dropdownState = dropdownState.copy(hovered = true)
                is HoverInteraction.Exit -> dropdownState = dropdownState.copy(hovered = false)
                is FocusInteraction.Focus -> dropdownState = dropdownState.copy(focused = true)
                is FocusInteraction.Unfocus -> dropdownState = dropdownState.copy(focused = false)
            }
        }
    }

    val colors = style.colors
    val metrics = style.metrics
    val minSize = metrics.minSize
    val shape = RoundedCornerShape(style.metrics.cornerSize)

    var componentWidth by remember { mutableIntStateOf(-1) }
    Box(
        modifier =
        modifier
            .clickable(
                onClick = {
                    // TODO: Trick to skip click event when close menu by click dropdown
                    if (!skipNextClick) {
                        expanded = !expanded
                    }
                    skipNextClick = false
                },
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
            )
            .background(colors.backgroundFor(dropdownState).value, shape)
            .thenIf(outline == Outline.None) { focusOutline(dropdownState, shape) }
            .outline(dropdownState, outline, shape)
            .defaultMinSize(minHeight = minSize.height)
            .onSizeChanged { componentWidth = it.width },
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.contentFor(dropdownState).value,
            LocalTextStyle provides LocalTextStyle.current.copy(color = colors.contentFor(dropdownState).value),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                content = content,
            )
        }

        if (expanded) {
            val density = LocalDensity.current
            PopupMenu(
                onDismissRequest = {
                    expanded = false
                    if (it == InputMode.Touch && dropdownState.isHovered) {
                        skipNextClick = true
                    }
                    true
                },
                modifier =
                menuModifier
                    .focusProperties { canFocus = true }
                    .defaultMinSize(minWidth = with(density) { componentWidth.toDp() }),
                style = style.menuStyle,
                horizontalAlignment = Alignment.Start,
                content = menuContent,
            )
        }
    }
}
