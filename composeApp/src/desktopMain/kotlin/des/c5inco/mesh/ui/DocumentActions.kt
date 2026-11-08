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
import des.c5inco.mesh.data.DocumentChange
import des.c5inco.mesh.data.DocumentRevision
import des.c5inco.mesh.testing.MeshTestTags
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Runs New, Open, Save, Save As, and Close for the window, one at a time: asks for files, asks whether to
 * save an untitled document's changes before an action would discard them, and asks what to do when a
 * document's last edits can't be written. A document with a file autosaves, so it needs no other prompt.
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

    /** The question the window is asking, if any; the action that asked waits for the answer. */
    var prompt: DocumentPrompt? by mutableStateOf(null)
        private set

    /** The action in progress. Others are ignored until it finishes, so that actions never interleave. */
    private var running: Job? = null

    fun new() = perform { replaceDocument { configuration.newDocument(it) } }

    fun open() = perform {
        val file = chooseFile(FileDialog.LOAD) ?: return@perform
        replaceDocument { configuration.openDocument(file, it) }
    }

    fun save() = perform { save(saveAs = false) }

    fun saveAs() = perform { save(saveAs = true) }

    fun close() = perform {
        if (replaceDocument { configuration.closeDocument(it) }) onClose()
    }

    private fun perform(action: suspend () -> Unit) {
        if (running?.isActive == true) return
        focusManager?.clearFocus()
        running = scope.launch { action() }
    }

    /**
     * Runs [change], which closes the open document, and asks the user whenever it would lose edits.
     * [change] gets the revision the user agreed to lose, if any. Returns whether the change happened.
     */
    private suspend fun replaceDocument(change: suspend (discarding: DocumentRevision?) -> DocumentChange): Boolean {
        var discarding: DocumentRevision? = null
        while (true) {
            val name = configuration.document.value.name
            when (val result = change(discarding)) {
                DocumentChange.Done -> return true
                DocumentChange.Failed -> return false
                // After Save, whether it worked or not, the loop asks again until nothing would be lost.
                DocumentChange.UnsavedChanges -> when (
                    ask("Save changes to “$name”?", "Your changes will be lost if you don’t save them.", DONT_SAVE, SAVE)
                ) {
                    SAVE -> save(saveAs = false)
                    DONT_SAVE -> discarding = configuration.revision()
                    else -> return false
                }
                is DocumentChange.SaveFailed -> when (
                    ask(
                        "Couldn’t save “$name”",
                        "${result.message}\nYour latest changes will be lost if you don’t save them.",
                        DONT_SAVE, SAVE_AS, TRY_AGAIN,
                    )
                ) {
                    TRY_AGAIN -> Unit
                    SAVE_AS -> save(saveAs = true)
                    DONT_SAVE -> discarding = configuration.revision()
                    else -> return false
                }
            }
        }
    }

    private suspend fun save(saveAs: Boolean): Boolean {
        val file = configuration.document.value.file.takeUnless { saveAs }
            ?: chooseSaveFile()
            ?: return false
        return configuration.saveDocument(file)
    }

    private suspend fun chooseSaveFile(): File? {
        val chosen = chooseFile(FileDialog.SAVE) ?: return null
        val extension = MeshDocument.FILE_EXTENSION
        if (chosen.extension == extension) return chosen
        // The dialog asked about replacing the name as typed, not this one with the extension added.
        val file = File("${chosen.path}.$extension")
        val replace = !file.exists() ||
            ask("“${file.name}” already exists", "Do you want to replace it?", null, REPLACE) == REPLACE
        return file.takeIf { replace }
    }

    /**
     * Asks a question over the window and returns the chosen answer, or null for Cancel. [discard] is shown
     * apart from the other [choices], the last of which is the default.
     */
    private suspend fun ask(title: String, message: String, discard: String?, vararg choices: String): String? {
        val answer = CompletableDeferred<String?>()
        prompt = DocumentPrompt(title, message, discard, choices.toList()) { answer.complete(it) }
        return try {
            answer.await()
        } finally {
            prompt = null
        }
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
        return File(dialog.directory ?: return null, dialog.file ?: return null)
    }

    private fun defaultDirectory(): File {
        val home = File(System.getProperty("user.home"))
        return File(home, "Documents").takeIf { it.isDirectory } ?: home
    }

    private companion object {
        const val DONT_SAVE = "Don’t Save"
        const val SAVE = "Save"
        const val SAVE_AS = "Save As…"
        const val TRY_AGAIN = "Try Again"
        const val REPLACE = "Replace"
    }
}

/** A question for the user; [answer] takes one of the choices, or null to cancel. */
class DocumentPrompt(
    val title: String,
    val message: String,
    val discard: String?,
    val choices: List<String>,
    val answer: (String?) -> Unit,
)

/** Shows [prompt] over the window. */
@Composable
fun DocumentPromptView(prompt: DocumentPrompt) {
    val defaultFocus = remember { FocusRequester() }
    LaunchedEffect(prompt) { defaultFocus.requestFocus() }

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
                    it.key == Key.Enter || it.key == Key.NumPadEnter -> true.also { prompt.answer(prompt.choices.last()) }
                    it.key == Key.Escape -> true.also { prompt.answer(null) }
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
                .testTag(MeshTestTags.DOCUMENT_PROMPT)
        ) {
            Text(prompt.title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(prompt.message, color = JewelTheme.globalColors.text.info)
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                prompt.discard?.let { OutlinedButton(onClick = { prompt.answer(it) }) { Text(it) } }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { prompt.answer(null) }) { Text("Cancel") }
                prompt.choices.dropLast(1).forEach { OutlinedButton(onClick = { prompt.answer(it) }) { Text(it) } }
                val default = prompt.choices.last()
                DefaultButton(onClick = { prompt.answer(default) }, modifier = Modifier.focusRequester(defaultFocus)) {
                    Text(default)
                }
            }
        }
    }
}
