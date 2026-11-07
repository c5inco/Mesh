package des.c5inco.mesh

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.data.AppDataRepository
import des.c5inco.mesh.mcp.McpServerController
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.ui.ComponentStyling

fun main() {
    System.setProperty("apple.awt.application.name", "Mesh")

    application {
        val configuration = AppConfiguration(
            repository = AppDataRepository(),
        )
        val windowState = rememberWindowState(
            width = 1024.dp,
            height = 768.dp
        )

        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown(configuration)
        })

        val mcpServer = remember(configuration) { McpServerController(configuration) }
        DisposableEffect(mcpServer) {
            onDispose { mcpServer.close() }
        }

        val themeDefinition = JewelTheme.darkThemeDefinition()

        Window(
            state = windowState,
            onCloseRequest = {
                exitApplication()
            },
            title = "Mesh",
            // Text fields handle these keys themselves first, so their own text undo still works.
            onKeyEvent = { handleUndoShortcut(it, configuration) },
        ) {
            IntUiTheme(
                theme = themeDefinition,
                styling = ComponentStyling.default()
            ) {
                App(
                    configuration = configuration,
                    mcpServer = mcpServer,
                )
            }
        }
    }
}

private fun shutdown(
    configuration: AppConfiguration
) {
    configuration.saveMeshState()
    runBlocking {
        configuration.saveMeshPoints()
    }
}

private val isMacOs = System.getProperty("os.name").startsWith("Mac")

private fun handleUndoShortcut(event: KeyEvent, configuration: AppConfiguration): Boolean {
    val primaryModifier = if (isMacOs) event.isMetaPressed else event.isCtrlPressed
    if (event.type != KeyEventType.KeyDown || !primaryModifier) return false
    when {
        event.key == Key.Z && !event.isShiftPressed -> configuration.undo()
        event.key == Key.Z || (!isMacOs && event.key == Key.Y) -> configuration.redo()
        else -> return false
    }
    return true
}
