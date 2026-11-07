package des.c5inco.mesh.mcp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import des.c5inco.mesh.common.meshGradient
import des.c5inco.mesh.common.toColor
import des.c5inco.mesh.common.toHexStringNoHash
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.data.AppConfiguration.Companion.MAX_BLUR_LEVEL
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import model.SavedColor
import model.findColor
import org.jetbrains.skia.EncodedImageFormat
import kotlin.io.encoding.Base64
import kotlin.math.roundToInt

/**
 * Exposes the open mesh to MCP clients over Streamable HTTP on localhost.
 *
 * Tools act on the same [AppConfiguration] the window shows, so edits appear on the canvas live.
 */
class MeshMcpServer(private val configuration: AppConfiguration) {

    fun start(port: Int = DEFAULT_PORT): EmbeddedServer<*, *> =
        embeddedServer(CIO, host = "127.0.0.1", port = port) {
            mcpStreamableHttp { createServer() }
        }.start(wait = false)

    private fun createServer() = Server(
        serverInfo = Implementation(name = "mesh", version = "1.0.0"),
        options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools())),
        instructions = "Mesh edits a mesh gradient: a grid of points, each with a position and a color. " +
            "Call get_mesh first to read the grid and the color IDs, then edit with update_point and add_color, " +
            "and check the result with render_png. Use undo to take back an edit.",
    ) {
        addTool(
            name = "get_mesh",
            description = "Returns the current mesh as JSON: grid size, canvas settings, the saved colors with " +
                "their IDs, and every point's row, column, position, and color ID. Positions are fractions of " +
                "the canvas from 0 to 1, with (0, 0) at the top left.",
        ) { configuration.awaitMeshLoaded(); getMesh() }

        addTool(
            name = "update_point",
            description = "Moves and/or recolors the point at a row and column. Omitted fields keep their " +
                "current value. When edge constraints are on (the default), edge points stay on their edge.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("row") { put("type", "integer"); put("description", "Row index, from 0") }
                    putJsonObject("col") { put("type", "integer"); put("description", "Column index, from 0") }
                    putJsonObject("x") { put("type", "number"); put("description", "Horizontal position, 0 to 1") }
                    putJsonObject("y") { put("type", "number"); put("description", "Vertical position, 0 to 1") }
                    putJsonObject("colorId") { put("type", "integer"); put("description", "A color ID from get_mesh") }
                },
                required = listOf("row", "col"),
            ),
        ) { configuration.awaitMeshLoaded(); updatePoint(it) }

        addTool(
            name = "add_color",
            description = "Adds a color to the custom palette and returns its color ID.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("hex") {
                        put("type", "string")
                        put("description", "#RRGGBB, or #AARRGGBB with alpha first")
                    }
                },
                required = listOf("hex"),
            ),
        ) { addColor(it) }

        addTool(
            name = "undo",
            description = "Undoes the last edit to the mesh, whether the user or an agent made it.",
        ) { undoOrRedo(redo = false) }

        addTool(
            name = "redo",
            description = "Redoes the last undone edit.",
        ) { undoOrRedo(redo = true) }

        addTool(
            name = "render_png",
            description = "Renders the current mesh to a PNG image at the canvas size, without point handles.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("scale") {
                        put("type", "integer")
                        put("description", "Pixel density, 1 to 3. Defaults to 1.")
                    }
                },
            ),
        ) { configuration.awaitMeshLoaded(); renderPng(it) }
    }

    private suspend fun getMesh(): CallToolResult = withContext(Dispatchers.Main) {
        val colors = configuration.availableColors.first()
        val meshState = configuration.meshState.value
        val json = buildJsonObject {
            put("rows", meshState.rows)
            put("cols", meshState.cols)
            put("canvasWidth", meshState.canvasWidth)
            put("canvasHeight", meshState.canvasHeight)
            put("blurLevel", meshState.blurLevel)
            put("constrainEdgePoints", configuration.uiState.value.constrainEdgePoints)
            put("colors", buildJsonArray {
                colors.forEach { color ->
                    add(buildJsonObject {
                        put("id", color.uid)
                        put("hex", "#" + colors.findColor(color.uid).toHexStringNoHash(includeAlpha = true))
                        put("preset", color.preset)
                    })
                }
            })
            put("points", buildJsonArray {
                configuration.meshPoints.forEachIndexed { row, points ->
                    points.forEachIndexed { col, (offset, colorId) ->
                        add(buildJsonObject {
                            put("row", row)
                            put("col", col)
                            put("x", offset.x)
                            put("y", offset.y)
                            put("colorId", colorId)
                        })
                    }
                }
            })
        }
        CallToolResult(content = listOf(TextContent(json.toString())))
    }

    private suspend fun updatePoint(request: CallToolRequest): CallToolResult {
        // Read the palette first: suspending after reading the point could let a drag or another
        // call change it, and the stale values would then overwrite that edit.
        val paletteIds = configuration.availableColors.first().map { it.uid }.toSet()
        return withContext(Dispatchers.Main) { updatePoint(request, paletteIds) }
    }

    /** Runs on the UI thread without suspending, so the point can't change between read and write. */
    private fun updatePoint(request: CallToolRequest, paletteIds: Set<Long>): CallToolResult = run {
        val args = request.arguments ?: JsonObject(emptyMap())
        val row = args.int("row") ?: return@run error("row is required")
        val col = args.int("col") ?: return@run error("col is required")
        val points = configuration.meshPoints
        if (row !in points.indices || col !in points[row].indices) {
            return@run error("No point at row $row, col $col; the grid has ${points.size} rows and ${points.firstOrNull()?.size ?: 0} columns")
        }

        val (current, currentColorId) = points[row][col]
        // Validate only what the caller sent, so echoing a point's current values back always works,
        // including the transparent ID of a deleted color or an offset typed outside 0–1 in the side panel.
        val requestedColorId = args.long("colorId")
        if (requestedColorId != null && requestedColorId != currentColorId && requestedColorId !in paletteIds) {
            return@run error("No color with ID $requestedColorId; call get_mesh for color IDs or add_color to create one")
        }
        val requestedX = args.float("x")
        val requestedY = args.float("y")
        if (requestedX?.let { it !in 0f..1f } == true || requestedY?.let { it !in 0f..1f } == true) {
            return@run error("x and y must be between 0 and 1")
        }
        val colorId = requestedColorId ?: currentColorId
        val x = requestedX ?: current.x
        val y = requestedY ?: current.y

        configuration.updateMeshPoint(row, col, Offset(x, y) to colorId)
        val (offset, newColorId) = configuration.meshPoints[row][col]
        CallToolResult(
            content = listOf(TextContent("Point ($row, $col) is now at x=${offset.x}, y=${offset.y} with color $newColorId"))
        )
    }

    private suspend fun undoOrRedo(redo: Boolean): CallToolResult = withContext(Dispatchers.Main) {
        val available = if (redo) configuration.canRedo.value else configuration.canUndo.value
        if (!available) return@withContext error("Nothing to ${if (redo) "redo" else "undo"}")
        if (redo) configuration.redo() else configuration.undo()
        CallToolResult(content = listOf(TextContent(if (redo) "Redid the last undone edit" else "Undid the last edit")))
    }

    private suspend fun addColor(request: CallToolRequest): CallToolResult {
        val hex = request.arguments?.get("hex")?.jsonPrimitive?.contentOrNull ?: return error("hex is required")
        val color = try {
            hex.toColor()
        } catch (e: IllegalArgumentException) {
            return error("Invalid color $hex; use #RRGGBB or #AARRGGBB")
        }
        val savedColor = SavedColor(
            red = (255 * color.red).toInt(),
            green = (255 * color.green).toInt(),
            blue = (255 * color.blue).toInt(),
            alpha = color.alpha,
        )

        val colorId = configuration.addColor(savedColor)
        // Wait until the palette lists the color, so update_point accepts its ID right away.
        withTimeout(5_000) { configuration.availableColors.first { colors -> colors.any { it.uid == colorId } } }
        return CallToolResult(content = listOf(TextContent("Added $hex as color ID $colorId")))
    }

    private suspend fun renderPng(request: CallToolRequest): CallToolResult {
        val scale = request.arguments?.int("scale") ?: 1
        if (scale !in 1..3) return error("scale must be 1, 2, or 3")

        val colors = configuration.availableColors.first()
        val (meshState, points) = withContext(Dispatchers.Main) {
            configuration.meshState.value to configuration.meshPoints.map { row ->
                row.map { (offset, colorId) -> offset to colors.findColor(colorId) }
            }
        }
        val width = meshState.canvasWidth.takeIf { it > 0 } ?: 640
        val height = meshState.canvasHeight.takeIf { it > 0 } ?: 480
        // A fixed canvas has no maximum size, so bound the bitmap.
        if (width.toLong() * scale > MAX_RENDER_SIDE || height.toLong() * scale > MAX_RENDER_SIDE) {
            return error("The render would be ${width * scale}×${height * scale} px; the limit is $MAX_RENDER_SIDE px per side, so use a smaller scale")
        }

        val png = renderComposeScene(width * scale, height * scale, Density(scale.toFloat())) {
            Box(
                Modifier.fillMaxSize().meshGradient(
                    points = points,
                    blurLevel = (meshState.blurLevel * MAX_BLUR_LEVEL).roundToInt(),
                    resolutionX = meshState.resolution,
                    resolutionY = meshState.resolution,
                )
            )
        }.use { image ->
            // Skia objects hold native memory until closed.
            requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { it.bytes }
        }
        return CallToolResult(
            content = listOf(
                ImageContent(data = Base64.encode(png), mimeType = "image/png"),
                TextContent("Rendered ${width * scale}×${height * scale} px"),
            )
        )
    }

    private fun error(message: String) = CallToolResult(content = listOf(TextContent(message)), isError = true)

    private fun JsonObject.int(key: String) = (get(key) as? JsonPrimitive)?.intOrNull
    private fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
    private fun JsonObject.float(key: String) = (get(key) as? JsonPrimitive)?.floatOrNull

    companion object {
        const val DEFAULT_PORT = 7777
        private const val MAX_RENDER_SIDE = 8192
    }
}
