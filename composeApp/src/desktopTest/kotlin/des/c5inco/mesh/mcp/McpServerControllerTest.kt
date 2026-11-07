package des.c5inco.mesh.mcp

import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.mcp.McpServerState.Status
import des.c5inco.mesh.testing.FakeDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.prefs.Preferences

class McpServerControllerTest {
    private val configuration = AppConfiguration(FakeDataRepository(meshPoints = flowOf(emptyList())))
    private val preferences = Preferences.userRoot().node("des/c5inco/mesh/test-${UUID.randomUUID()}")
    private val controllers = mutableListOf<McpServerController>()

    @AfterEach
    fun cleanUp() {
        controllers.forEach { it.close() }
        preferences.removeNode()
    }

    @Test
    fun `server is off until enabled, then listens and stays enabled on the next launch`() = runBlocking {
        val port = freePort()
        val first = controller()
        assertEquals(Status.Off, first.state.value.status)

        first.setPort(port)
        assertEquals(false, isListening(port))

        first.setEnabled(true)
        assertEquals(Status.Running("http://127.0.0.1:$port/mcp"), first.settledStatus())
        assertEquals(true, isListening(port))
        first.close()

        val relaunched = controller()
        assertEquals(port, relaunched.state.value.port)
        assertEquals(Status.Running("http://127.0.0.1:$port/mcp"), relaunched.settledStatus())
    }

    @Test
    fun `a busy port reports a failure until the user picks a free one`() = runBlocking {
        ServerSocket(0).use { busy ->
            val controller = controller()
            controller.setPort(busy.localPort)
            controller.setEnabled(true)
            assertEquals(Status.Failed("Port ${busy.localPort} is in use"), controller.settledStatus())

            val freePort = freePort()
            controller.setPort(freePort)
            assertEquals(Status.Running("http://127.0.0.1:$freePort/mcp"), controller.settledStatus())
        }
    }

    @Test
    fun `disabling stops the server`() = runBlocking {
        val port = freePort()
        val controller = controller()
        controller.setPort(port)
        controller.setEnabled(true)
        controller.settledStatus()

        controller.setEnabled(false)
        assertEquals(Status.Off, controller.settledStatus())
        assertEquals(false, isListening(port))
    }

    @Test
    fun `closing while the server starts leaves nothing listening`() {
        repeat(20) {
            val port = freePort()
            val controller = controller()
            controller.setPort(port)
            controller.setEnabled(true)
            Thread.sleep(it.toLong()) // Lands the close at different points of startup.
            controller.close()
            Thread.sleep(200)
            assertEquals(false, isListening(port), "Port $port still listening after close (attempt $it)")
        }
    }

    private fun controller() = McpServerController(configuration, preferences).also { controllers += it }

    private suspend fun McpServerController.settledStatus(): Status =
        withTimeout(10_000) { state.first { it.status != Status.Starting && (it.enabled || it.status == Status.Off) }.status }

    private fun freePort() = ServerSocket(0).use { it.localPort }

    private fun isListening(port: Int) = try {
        Socket("127.0.0.1", port).close()
        true
    } catch (e: ConnectException) {
        false
    }
}
