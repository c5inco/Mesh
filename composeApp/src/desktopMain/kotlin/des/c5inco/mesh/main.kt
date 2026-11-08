package des.c5inco.mesh

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.application
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.data.AppDataRepository
import des.c5inco.mesh.mcp.McpServerController
import kotlinx.coroutines.runBlocking

fun main() {
    System.setProperty("apple.awt.application.name", "Mesh")
    System.setProperty("apple.laf.useScreenMenuBar", "true")

    application {
        val configuration = remember {
            AppConfiguration(
                repository = AppDataRepository(),
            ).also { configuration ->
                Runtime.getRuntime().addShutdownHook(Thread { shutdown(configuration) })
            }
        }

        val mcpServer = remember(configuration) { McpServerController(configuration) }
        DisposableEffect(mcpServer) {
            onDispose { mcpServer.close() }
        }

        MeshWindow(
            configuration = configuration,
            onExit = ::exitApplication,
            mcpServer = mcpServer,
        )
    }
}

private fun shutdown(
    configuration: AppConfiguration
) {
    // Closing the window already wrote the document; this catches exits that skip it, such as a signal.
    // Waiting on the UI thread here could hang the exit.
    runBlocking<Unit> {
        configuration.flushPendingSave()
    }
}
