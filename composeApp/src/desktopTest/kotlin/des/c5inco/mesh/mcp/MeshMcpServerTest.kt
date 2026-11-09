package des.c5inco.mesh.mcp

import androidx.compose.ui.geometry.Offset
import data.MAX_GRID_SIZE
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.testing.FakeDataRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.engine.EmbeddedServer
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.mcpStreamableHttpTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64

class MeshMcpServerTest {
    // The default 3×4 grid colors its rows with IDs 1, 2, and 3.
    private val repository = FakeDataRepository(
        initialColors = listOf(
            SavedColor(uid = 1, red = 255, green = 0, blue = 0),
            SavedColor(uid = 2, red = 0, green = 255, blue = 0),
            SavedColor(uid = 3, red = 0, green = 0, blue = 255),
        ),
    )
    private val configuration = AppConfiguration(repository)
    private lateinit var server: EmbeddedServer<*, *>
    private var port = 0
    private val httpClient = HttpClient(CIO)
    private val client = Client(Implementation(name = "test", version = "1.0"))

    @BeforeEach
    fun connect() = runBlocking {
        configuration.awaitMeshLoaded()
        server = MeshMcpServer(configuration).start(port = 0)
        port = server.engine.resolvedConnectors().first().port
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
    fun `undo reverts an agent edit and redo reapplies it`() = runBlocking {
        callOk("update_point", mapOf("row" to 1, "col" to 1, "x" to 0.9, "colorId" to 3))

        callOk("undo", emptyMap())
        assertEquals(0.33f to 2L, configuration.meshPoints[1][1].let { it.first.x to it.second })
        assertEquals(true, client.callTool("undo", emptyMap()).isError)

        callOk("redo", emptyMap())
        assertEquals(0.9f to 3L, configuration.meshPoints[1][1].let { it.first.x to it.second })
    }

    @Test
    fun `set_grid resizes the grid and sets the blur, and undo takes it back`() = runBlocking {
        callOk("set_grid", mapOf("rows" to 5, "cols" to 6, "blur" to 0.2))

        val mesh = getMesh()
        assertEquals(5 to 6, mesh.int("rows") to mesh.int("cols"))
        assertEquals(30, mesh["points"]!!.jsonArray.size)
        assertEquals(0.2f, mesh["blurLevel"]!!.jsonPrimitive.float)

        // Rows, columns, and blur are each their own edit, as in the side panel.
        repeat(3) { callOk("undo", emptyMap()) }
        assertEquals(3 to 4, configuration.meshState.value.let { it.rows to it.cols })
        assertEquals(12, configuration.meshPoints.sumOf { it.size })
    }

    @Test
    fun `set_grid rejects out-of-range values without changing anything`() = runBlocking {
        val before = configuration.meshState.value

        assertEquals(true, client.callTool("set_grid", mapOf("rows" to MAX_GRID_SIZE + 1)).isError)
        assertEquals(true, client.callTool("set_grid", mapOf("cols" to 4, "blur" to 1.5)).isError)
        assertEquals(before, configuration.meshState.value)
    }

    @Test
    fun `update_points applies every edit as one undo step`() = runBlocking {
        val before = configuration.meshPoints.toList()
        callOk(
            "update_points",
            mapOf(
                "points" to listOf(
                    mapOf("row" to 1, "col" to 1, "x" to 0.25, "colorId" to 3),
                    mapOf("row" to 1, "col" to 2, "y" to 0.6),
                    mapOf("row" to 0, "col" to 1, "x" to 0.4, "y" to 0.5),
                )
            ),
        )

        assertEquals(0.25f to 3L, configuration.meshPoints[1][1].let { it.first.x to it.second })
        assertEquals(0.6f, configuration.meshPoints[1][2].first.y)
        assertEquals(Offset(0.4f, 0f), configuration.meshPoints[0][1].first, "Edge points stay on their edge")

        callOk("undo", emptyMap())
        assertEquals(before, configuration.meshPoints.toList())
    }

    @Test
    fun `update_points changes nothing when any entry is invalid`() = runBlocking {
        val before = configuration.meshPoints.toList()
        val result = client.callTool(
            "update_points",
            mapOf(
                "points" to listOf(
                    mapOf("row" to 1, "col" to 1, "x" to 0.25),
                    mapOf("row" to 9, "col" to 0, "x" to 0.5),
                )
            ),
        )

        assertEquals(true, result.isError)
        assertTrue(result.text().startsWith("points[1]:"), result.text())
        assertEquals(before, configuration.meshPoints.toList())
    }

    @Test
    fun `update_point rejects an unknown color without changing the point`() = runBlocking {
        val result = client.callTool("update_point", mapOf("row" to 1, "col" to 1, "x" to 0.9, "colorId" to 99))

        assertEquals(true, result.isError)
        assertEquals(0.33f to 2L, configuration.meshPoints[1][1].let { it.first.x to it.second })
    }

    @Test
    fun `update_point moves a point whose color was deleted without recoloring it`() = runBlocking {
        withContext(Dispatchers.Main) {
            val colorId = configuration.addColor(SavedColor(red = 1, green = 2, blue = 3))
            configuration.updateMeshPoint(1, 1, configuration.meshPoints[1][1].first to colorId)
            configuration.deleteColor(configuration.customColors.value.single())
        }

        callOk("update_point", mapOf("row" to 1, "col" to 1, "x" to 0.4))
        callOk("update_point", mapOf("row" to 1, "col" to 1, "y" to 0.6, "colorId" to -1))

        assertEquals(Offset(0.4f, 0.6f) to -1L, configuration.meshPoints[1][1])
    }

    @Test
    fun `update_point recolors a point the side panel placed outside 0 to 1`() = runBlocking {
        withContext(Dispatchers.Main) { configuration.updateMeshPoint(1, 1, Offset(2f, 0.5f) to 2L) }

        callOk("update_point", mapOf("row" to 1, "col" to 1, "colorId" to 3))
        assertEquals(Offset(2f, 0.5f) to 3L, configuration.meshPoints[1][1])

        assertEquals(true, client.callTool("update_point", mapOf("row" to 1, "col" to 1, "x" to 1.5)).isError)
    }

    @Test
    fun `render_png refuses a bitmap larger than the limit`() = runBlocking {
        withContext(Dispatchers.Main) { configuration.updateCanvasWidth(5000) }

        assertEquals(true, client.callTool("render_png", mapOf("scale" to 2)).isError)
        callOk("render_png", mapOf("scale" to 1))
        Unit
    }

    @Test
    fun `requests from a web page origin are rejected`() = runBlocking {
        val response = httpClient.post("http://127.0.0.1:$port/mcp") {
            header(HttpHeaders.Origin, "https://evil.example")
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
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
    fun `get_mesh names the open document and follows it to its file`() = runBlocking {
        assertEquals("Untitled", getMesh()["document"]!!.jsonObject["name"]!!.jsonPrimitive.content)

        val file = File(repository.documentsDirectory, "Agent.mesh")
        assertTrue(configuration.saveDocument(file))

        val document = getMesh()["document"]!!.jsonObject
        assertEquals("Agent", document["name"]!!.jsonPrimitive.content)
        assertEquals(file.absolutePath, document["file"]!!.jsonPrimitive.content)
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
