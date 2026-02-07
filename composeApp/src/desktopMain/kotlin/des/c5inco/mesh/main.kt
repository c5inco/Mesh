package des.c5inco.mesh

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.data.AppDataRepository
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

        // Load last auto-saved document on startup
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown(configuration)
        })

        val themeDefinition = JewelTheme.darkThemeDefinition()

        Window(
            state = windowState,
            onCloseRequest = {
                exitApplication()
            },
            title = "Mesh",
        ) {
            IntUiTheme(
                theme = themeDefinition,
                styling = ComponentStyling.default()
            ) {
                App(
                    configuration = configuration,
                )
            }
        }
    }
}

private fun shutdown(
    configuration: AppConfiguration
) {
    // Auto-save current state for recovery
    configuration.saveMeshState()
    runBlocking {
        configuration.saveMeshPoints()
    }
}