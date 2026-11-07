package des.c5inco.mesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import data.MeshDocument
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.testing.MeshTestTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Runs New, Open, Save, Save As, and Close for the window: asks for files, and asks whether to save an
 * untitled document's changes before an action would discard them. A document with a file autosaves, and
 * its last edits are written before another document replaces it or the app exits, so it needs no prompt.
 */
class DocumentActions(
    private val configuration: AppConfiguration,
    private val scope: CoroutineScope,
    private val onClose: () -> Unit,
) {
    /** The window that file dialogs belong to. */
    var window: Frame? = null

    /** The window's focus, cleared before each action so a field being edited commits its value first. */
    var focusManager: FocusManager? = null

    /** An action waiting for the user to decide what happens to unsaved changes. */
    var pendingAction: (() -> Unit)? by mutableStateOf(null)
        private set

    fun new() {
        commitEdits()
        afterUnsavedChanges { scope.launch { configuration.newDocument() } }
    }

    fun open() {
        commitEdits()
        val file = chooseFile(FileDialog.LOAD) ?: return
        afterUnsavedChanges { scope.launch { configuration.openDocument(file) } }
    }

    fun save() {
        commitEdits()
        scope.launch { save(saveAs = false) }
    }

    fun saveAs() {
        commitEdits()
        scope.launch { save(saveAs = true) }
    }

    fun close() {
        commitEdits()
        afterUnsavedChanges(onClose)
    }

    fun saveChanges() {
        val action = takePendingAction()
        scope.launch { if (save(saveAs = false)) action() }
    }

    fun discardChanges() = takePendingAction()()

    fun keepChanges() {
        pendingAction = null
    }

    private fun commitEdits() {
        focusManager?.clearFocus()
    }

    private fun takePendingAction(): () -> Unit = requireNotNull(pendingAction).also { pendingAction = null }

    private fun afterUnsavedChanges(action: () -> Unit) {
        val document = configuration.document.value
        if (document.modified && document.file == null) pendingAction = action else action()
    }

    private suspend fun save(saveAs: Boolean): Boolean {
        val file = configuration.document.value.file.takeUnless { saveAs }
            ?: chooseFile(FileDialog.SAVE)
            ?: return false
        return configuration.saveDocument(file)
    }

    private fun chooseFile(mode: Int): File? {
        val document = configuration.document.value
        val extension = MeshDocument.FILE_EXTENSION
        val dialog = FileDialog(window, if (mode == FileDialog.SAVE) "Save As" else "Open", mode).apply {
            directory = (document.file?.parentFile ?: defaultDirectory()).path
            if (mode == FileDialog.SAVE) {
                file = "${document.name}.$extension"
            } else {
                setFilenameFilter { _, name -> name.endsWith(".$extension") }
            }
            isVisible = true
        }
        val chosen = File(dialog.directory ?: return null, dialog.file ?: return null)
        return if (mode == FileDialog.SAVE && chosen.extension != extension) File("${chosen.path}.$extension") else chosen
    }

    private fun defaultDirectory(): File {
        val home = File(System.getProperty("user.home"))
        return File(home, "Documents").takeIf { it.isDirectory } ?: home
    }
}

/** Asks, over the window, whether to save the document's changes before they would be discarded. */
@Composable
fun UnsavedChangesPrompt(
    documentName: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    val saveFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { saveFocus.requestFocus() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            // Keeps clicks from reaching the editor underneath.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .onPreviewKeyEvent {
                when {
                    it.type != KeyEventType.KeyDown -> false
                    it.key == Key.Enter || it.key == Key.NumPadEnter -> true.also { onSave() }
                    it.key == Key.Escape -> true.also { onCancel() }
                    else -> false
                }
            },
    ) {
        val shape = RoundedCornerShape(8.dp)
        Column(
            Modifier
                .width(420.dp)
                .background(JewelTheme.globalColors.panelBackground, shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .padding(20.dp)
                .testTag(MeshTestTags.UNSAVED_CHANGES_PROMPT)
        ) {
            Text("Save changes to “$documentName”?", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your changes will be lost if you don’t save them.",
                color = JewelTheme.globalColors.text.info,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = onDiscard) { Text("Don’t Save") }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                DefaultButton(onClick = onSave, modifier = Modifier.focusRequester(saveFocus)) { Text("Save") }
            }
        }
    }
}
