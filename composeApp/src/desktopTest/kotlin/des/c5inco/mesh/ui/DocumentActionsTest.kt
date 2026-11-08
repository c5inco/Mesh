package des.c5inco.mesh.ui

import data.MeshDocument
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.testing.FakeDataRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DocumentActionsTest {
    @TempDir
    lateinit var directory: File

    private val scope = CoroutineScope(Dispatchers.Main)
    private val closed = CompletableDeferred<Unit>()

    @AfterEach
    fun cancelActions() = scope.cancel()

    @Test
    fun `an action asked for while a prompt is open is ignored`() = runBlocking {
        val configuration = loadedConfiguration()
        val actions = DocumentActions(configuration, scope, onClose = { closed.complete(Unit) })
        configuration.updateBlurLevel(0.5f)

        withContext(Dispatchers.Main) { actions.new() }
        val prompt = eventually { actions.prompt }
        assertEquals("Save changes to “Untitled”?", prompt.title)
        // Such as Quit while New is asking.
        withContext(Dispatchers.Main) { actions.close() }
        withContext(Dispatchers.Main) { prompt.answer(prompt.discard) }

        eventually { configuration.meshState.value.blurLevel.takeIf { it == 0f } }
        assertFalse(closed.isCompleted, "Don't Save answered New, not Quit")
    }

    @Test
    fun `the window stays open while the document's last edit can't be saved`() = runBlocking {
        val configuration = loadedConfiguration()
        val actions = DocumentActions(configuration, scope, onClose = { closed.complete(Unit) })
        val file = File(directory, "Test.mesh")
        configuration.saveDocument(file)

        directory.setWritable(false)
        val prompt = try {
            configuration.updateBlurLevel(0.5f)
            withContext(Dispatchers.Main) { actions.close() }
            eventually { actions.prompt }
        } finally {
            directory.setWritable(true)
        }
        assertEquals("Couldn’t save “Test”", prompt.title)
        assertFalse(closed.isCompleted)

        withContext(Dispatchers.Main) { prompt.answer("Try Again") }
        withTimeout(10.seconds) { closed.await() }
        assertEquals(0.5f, MeshDocument.decode(file.readText()).meshState.blurLevel)
    }

    private suspend fun loadedConfiguration() =
        AppConfiguration(FakeDataRepository(documentsDirectory = directory)).also { it.awaitMeshLoaded() }

    private suspend fun <T : Any> eventually(value: () -> T?): T = withTimeout(10.seconds) {
        var result = value()
        while (result == null) {
            delay(50.milliseconds)
            result = value()
        }
        result
    }
}
