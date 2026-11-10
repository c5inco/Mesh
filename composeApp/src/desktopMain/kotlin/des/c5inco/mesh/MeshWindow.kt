package des.c5inco.mesh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.rememberWindowState
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.data.Notifications
import des.c5inco.mesh.mcp.McpServerController
import des.c5inco.mesh.ui.DocumentActions
import des.c5inco.mesh.ui.DocumentPromptView
import des.c5inco.mesh.ui.MeshToolbar
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import java.awt.Desktop
import javax.swing.SwingUtilities

private val isMac = System.getProperty("os.name").lowercase().contains("mac")

/**
 * The editor window for the open document. Its title bar is the toolbar, so the document, the agent,
 * and the export stay visible whatever the inspector is scrolled to.
 */
@Composable
fun MeshWindow(
    configuration: AppConfiguration,
    onExit: () -> Unit,
    mcpServer: McpServerController? = null,
) {
    val document by configuration.document.collectAsState()
    val meshState by configuration.meshState.collectAsState()
    val availableColors by configuration.availableColors.collectAsState(initial = emptyList())
    val saveFailure by configuration.saveFailure.collectAsState()
    val mcpServerState = mcpServer?.state?.collectAsState()?.value
    val coroutineScope = rememberCoroutineScope()
    val actions = remember { DocumentActions(configuration, coroutineScope, onClose = onExit) }
    var exportScale by remember { mutableStateOf(1) }

    // The theme wraps the window: the decorated window reads its own style from it.
    IntUiTheme(
        theme = JewelTheme.darkThemeDefinition(),
        styling = ComponentStyling.default().decoratedWindow(),
    ) {
        DecoratedWindow(
            state = rememberWindowState(width = 1024.dp, height = 768.dp),
            onCloseRequest = actions::close,
            title = if (document.modified) "${document.name} — Edited" else document.name,
            // macOS takes these shortcuts from the File menu; elsewhere the window handles them.
            onPreviewKeyEvent = { event ->
                !isMac && event.type == KeyEventType.KeyDown && event.isCtrlPressed && when (event.key) {
                    Key.N -> true.also { actions.new() }
                    Key.O -> true.also { actions.open() }
                    Key.S -> true.also { if (event.isShiftPressed) actions.saveAs() else actions.save() }
                    else -> false
                }
            },
            // Text fields handle these keys themselves first, so their own text undo still works.
            onKeyEvent = { handleUndoShortcut(it, configuration) },
        ) {
            val focusManager = LocalFocusManager.current
            DisposableEffect(window, focusManager) {
                actions.window = window
                actions.focusManager = focusManager
                onDispose {
                    actions.window = null
                    actions.focusManager = null
                }
            }
            LaunchedEffect(document.modified) {
                // Shows the unsaved-changes dot in the macOS close button.
                window.rootPane.putClientProperty("Window.documentModified", document.modified)
            }
            QuitHandler(onQuit = actions::close)

            if (isMac) {
                MenuBar {
                    Menu("File") {
                        Item("New", shortcut = KeyShortcut(Key.N, meta = true), onClick = actions::new)
                        Item("Open…", shortcut = KeyShortcut(Key.O, meta = true), onClick = actions::open)
                        Separator()
                        Item("Save", shortcut = KeyShortcut(Key.S, meta = true), onClick = actions::save)
                        Item("Save As…", shortcut = KeyShortcut(Key.S, meta = true, shift = true), onClick = actions::saveAs)
                    }
                }
            }

            // Inside the window's composition: the layer records from the canvas it is handed to.
            val exportGraphicsLayer = rememberGraphicsLayer()

            MeshToolbar(
                documentName = document.name,
                documentUntitled = document.file == null,
                documentFile = document.file,
                saveFailure = saveFailure,
                meshPoints = configuration.meshPoints,
                availableColors = availableColors,
                exportScale = exportScale,
                canvasSize = meshState.canvasWidth to meshState.canvasHeight,
                mcpServerState = mcpServerState,
                onNewDocument = actions::new,
                onOpenDocument = actions::open,
                onSaveDocument = actions::save,
                onSaveDocumentAs = actions::saveAs,
                onRetrySave = configuration::retrySave,
                onExportScaleChange = { exportScale = it },
                onExport = {
                    coroutineScope.launch {
                        AppConfiguration.saveImage(image = exportGraphicsLayer.toImageBitmap().toAwtImage(), scale = exportScale)
                    }
                },
                onCopyCode = {
                    configuration.exportMeshPointsAsCode()
                    Notifications.send("📋 Points copied to the clipboard!")
                },
                onMcpServerEnabledChange = { mcpServer?.setEnabled(it) },
                onMcpServerPortChange = { mcpServer?.setPort(it) },
            )

            Box(Modifier.fillMaxSize()) {
                App(
                    configuration = configuration,
                    exportGraphicsLayer = exportGraphicsLayer,
                    exportScale = exportScale,
                )

                actions.prompt?.let { DocumentPromptView(it) }
            }
        }
    }
}

private fun handleUndoShortcut(event: KeyEvent, configuration: AppConfiguration): Boolean {
    val primaryModifier = if (isMac) event.isMetaPressed else event.isCtrlPressed
    if (event.type != KeyEventType.KeyDown || !primaryModifier) return false
    when {
        event.key == Key.Z && !event.isShiftPressed -> configuration.undo()
        event.key == Key.Z || (!isMac && event.key == Key.Y) -> configuration.redo()
        else -> return false
    }
    return true
}

/** Routes the macOS Quit command through [onQuit], which would otherwise end the app without asking. */
@Composable
private fun QuitHandler(onQuit: () -> Unit) {
    DisposableEffect(Unit) {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        if (desktop?.isSupported(Desktop.Action.APP_QUIT_HANDLER) != true) return@DisposableEffect onDispose { }
        desktop.setQuitHandler { _, response ->
            response.cancelQuit()
            SwingUtilities.invokeLater(onQuit)
        }
        onDispose { desktop.setQuitHandler(null) }
    }
}
