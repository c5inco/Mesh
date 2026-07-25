package des.c5inco.mesh.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import des.c5inco.mesh.common.ExpressiveMotionTokens.SpringFastSpatialDamping
import des.c5inco.mesh.common.ExpressiveMotionTokens.SpringFastSpatialStiffness
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.ui.typography

@Composable
fun CanvasSnackbar(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var dismiss by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val visibleState = remember {
        MutableTransitionState(false).apply {
            // Start the animation immediately.
            targetState = true
        }
    }

    if (visibleState.isIdle && visibleState.currentState) {
        scope.launch {
            delay(3000)
            println("Start dismiss")
            visibleState.targetState = false
            dismiss = true
        }
    }

    LaunchedEffect(dismiss) {
        if (dismiss) {
            delay(2000)
            println("Finish dismiss")
            onDismiss()
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = scaleIn(spring(dampingRatio = SpringFastSpatialDamping, stiffness = SpringFastSpatialStiffness)),
        exit = fadeOut(tween(2000)),
        modifier = modifier
    ) {
        CompositionLocalProvider(
            LocalContentColor provides requireNotNull(IntUiDarkTheme.colors.grayOrNull(12)),
            LocalTextStyle provides JewelTheme.typography.labelTextStyle.copy(fontSize = 11.sp),
        ) {
            Box(
                modifier =
                    Modifier.shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(4.dp),
                        ambientColor = Color(0x66000000),
                        spotColor = Color.Transparent,
                    )
                        .background(
                            color = requireNotNull(IntUiDarkTheme.colors.grayOrNull(2)),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = requireNotNull(IntUiDarkTheme.colors.grayOrNull(3)),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(vertical = 9.dp, horizontal = 12.dp)
            ) {
                content()
            }
        }
    }
}
