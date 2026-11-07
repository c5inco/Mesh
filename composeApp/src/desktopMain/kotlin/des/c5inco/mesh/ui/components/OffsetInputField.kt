package des.c5inco.mesh.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import des.c5inco.mesh.common.formatFloat
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun OffsetInputField(
    value: Float,
    enabled: Boolean = false,
    paramName: String,
    onUpdate: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val textFieldState = remember(value) { TextFieldState(formatFloat(value)) }

    fun reset() {
        textFieldState.edit { replace(0, textFieldState.text.length, formatFloat(value)) }
    }

    fun validate() {
        val text = textFieldState.text.toString()
        // Leaving a field without typing must not write its rounded display value back as an edit.
        if (text == formatFloat(value)) return
        text.toFloatOrNull()?.let { next ->
            onUpdate(next)
        } ?: run {
            reset()
        }
    }

    TextField(
        state = textFieldState,
        enabled = enabled,
        leadingIcon = {
            ParameterSwatch(text = paramName, modifier = Modifier.size(16.dp).padding(end = 6.dp))
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = {
            validate()
            focusManager.clearFocus()
        },
        modifier = modifier
            .onFocusChanged { validate() }
            .onKeyEvent {
                when (it.key) {
                    Key.Tab -> {
                        validate()
                        return@onKeyEvent false
                    }

                    Key.Escape -> {
                        reset()
                        focusManager.clearFocus()
                    }
                }
                return@onKeyEvent true
            }
    )
}