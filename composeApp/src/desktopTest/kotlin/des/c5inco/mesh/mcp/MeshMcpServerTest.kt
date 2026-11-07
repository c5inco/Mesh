package des.c5inco.mesh.mcp

import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.testing.FakeDataRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttpTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import model.SavedColor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64

class MeshMcpServerTest {
    // The default 3×4 grid colors its rows with IDs 1, 2, and 3.
    private val repository = FakeDataRepository(
        meshPoints = flowOf(emptyList()),
        initialColors = listOf(
            SavedColor(uid = 1, red = 255, green = 0, blue = 0),
            SavedColor(uid = 2, red = 0, green = 255, blue = 0),
            SavedColor(uid = 3, red = 0, green = 0, blue = 255),
        ),
    )
    private val configuration = AppConfiguration(repository)
    private lateinit var server: EmbeddedServer<*, *>
    private val httpClient = HttpClient(CIO)
    private val client = Client(Implementation(name = "test", version = "1.0"))

    @BeforeEach
    fun connect() = runBlocking {
        configuration.saveMeshPoints() // Waits for the initial grid to load.
        server = MeshMcpServer(configuration).start(port = 0)
        val port = server.engine.resolvedConnectors().first().port
        client.connect(httpClient.mcpStreamableHttpTransport("http://127.0.0.1:$port/mcp"))
    }

    @AfterEach
    fun disconnect() = runBlocking {
        client.close()
        httpClient.close()
        server.stop()
    }

    @Test
    fun `update_point moves and recolors a point, keeping edge points on their edge`() = runBlocking {
        callOk("update_point", mapOf("row" to 1, "col" to 2, "x" to 0.25, "y" to 0.6, "colorId" to 3))
        callOk("update_point", mapOf("row" to 0, "col" to 1, "x" to 0.4, "y" to 0.5))

        val points = getMesh()["points"]!!.jsonArray.map { it.jsonObject }
        val moved = points.single { it.int("row") == 1 && it.int("col") == 2 }
        assertEquals(0.25f, moved["x"]!!.jsonPrimitive.float)
        assertEquals(0.6f, moved["y"]!!.jsonPrimitive.float)
        assertEquals(3L, moved["colorId"]!!.jsonPrimitive.long)

        val topEdge = points.single { it.int("row") == 0 && it.int("col") == 1 }
        assertEquals(0.4f, topEdge["x"]!!.jsonPrimitive.float)
        assertEquals(0f, topEdge["y"]!!.jsonPrimitive.float)
        assertEquals(1L, topEdge["colorId"]!!.jsonPrimitive.long)
    }

    @Test
    fun `update_point rejects an unknown color without changing the point`() = runBlocking {
        val result = client.callTool("update_point", mapOf("row" to 1, "col" to 1, "x" to 0.9, "colorId" to 99))

        assertEquals(true, result.isError)
        assertEquals(0.33f to 2L, configuration.meshPoints[1][1].let { it.first.x to it.second })
    }

    @Test
    fun `add_color returns an ID that update_point accepts`() = runBlocking {
        val added = callOk("add_color", mapOf("hex" to "#FF6A00")).text()
        val colorId = Regex("color ID (\\d+)").find(added)!!.groupValues[1].toLong()

        callOk("update_point", mapOf("row" to 1, "col" to 1, "colorId" to colorId))

        val color = getMesh()["colors"]!!.jsonArray.map { it.jsonObject }.single { it["id"]!!.jsonPrimitive.long == colorId }
        assertEquals("#FFFF6A00", color["hex"]!!.jsonPrimitive.content)
        assertEquals(colorId, configuration.meshPoints[1][1].second)
    }

    @Test
    fun `render_png draws the mesh colors at the canvas size times scale`() = runBlocking {
        val image = callOk("render_png", mapOf("scale" to 2)).content.filterIsInstance<ImageContent>().single()
        val png = ImageIO.read(ByteArrayInputStream(Base64.decode(image.data)))

        // The fake mesh state has no canvas size, so the render falls back to 640×480.
        assertEquals(1280 to 960, png.width to png.height)
        assertDominantChannel(png.getRGB(2, 2), shift = 16) // Top row is red.
        assertDominantChannel(png.getRGB(2, png.height - 3), shift = 0) // Bottom row is blue.
    }

    private suspend fun callOk(name: String, arguments: Map<String, Any?>): CallToolResult =
        client.callTool(name, arguments).also { assertTrue(it.isError != true, "$name failed: ${it.text()}") }

    private suspend fun getMesh(): JsonObject = Json.parseToJsonElement(callOk("get_mesh", emptyMap()).text()).jsonObject

    private fun CallToolResult.text() = content.filterIsInstance<TextContent>().joinToString { it.text }

    private fun JsonObject.int(key: String) = get(key)!!.jsonPrimitive.content.toInt()

    private fun assertDominantChannel(argb: Int, shift: Int) {
        val channels = listOf(16, 8, 0).associateWith { (argb shr it) and 0xFF }
        assertTrue(channels.getValue(shift) > 200 && channels.filterKeys { it != shift }.values.all { it < 60 }) {
            "Expected a pure ${mapOf(16 to "red", 8 to "green", 0 to "blue")[shift]} pixel, got #%08X".format(argb)
        }
    }
}
