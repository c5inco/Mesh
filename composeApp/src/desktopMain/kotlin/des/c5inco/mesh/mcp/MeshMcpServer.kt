package des.c5inco.mesh.mcp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import des.c5inco.mesh.common.toColor
import des.c5inco.mesh.common.toHexStringNoHash
import data.MAX_GRID_SIZE
import data.MIN_GRID_SIZE
import des.c5inco.mesh.data.AppConfiguration
import des.c5inco.mesh.data.AppConfiguration.Companion.MAX_BLUR_LEVEL
import des.c5inco.mesh.data.MeshPointEdit
import des.c5inco.mesh.ui.meshGradientPainter
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
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
 * Tools act on the document open in the window, through the same [AppConfiguration], so edits appear on the
 * canvas live. Opening another document switches the tools to it; color IDs belong to the open document.
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
            "Call get_mesh first to read the grid and the color IDs, then edit with set_grid, update_point (or update_points for many at once), " +
            "and add_color, " +
            "and check the result with render_png. Use undo to take back an edit.",
    ) {
        addTool(
            name = "get_mesh",
            description = "Returns the open document's mesh as JSON: the document's name and file, grid size, " +
                "canvas settings, the preset and custom colors with their IDs, and every point's row, column, " +
                "position, and color ID. Positions are fractions of the canvas from 0 to 1, with (0, 0) at the top " +
                "left. Color IDs belong to the open document and change when another document opens.",
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
            name = "update_points",
            description = "Moves and/or recolors many points as a single undoable edit, like update_point for each " +
                "entry in order. If any entry is invalid, no point changes.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("points") {
                        put("type", "array")
                        put("description", "Point edits, each {row, col, x?, y?, colorId?} as in update_point")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("row") { put("type", "integer") }
                                putJsonObject("col") { put("type", "integer") }
                                putJsonObject("x") { put("type", "number") }
                                putJsonObject("y") { put("type", "number") }
                                putJsonObject("colorId") { put("type", "integer") }
                            }
                            put("required", buildJsonArray { add(JsonPrimitive("row")); add(JsonPrimitive("col")) })
                        }
                    }
                },
                required = listOf("points"),
            ),
        ) { configuration.awaitMeshLoaded(); updatePoints(it) }

        addTool(
            name = "add_color",
            description = "Adds a custom color to the open document and returns its color ID.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("hex") {
                        put("type", "string")
                        put("description", "#RRGGBB, or #AARRGGBB with alpha first")
                    }
                },
                required = listOf("hex"),
            ),
        ) { configuration.awaitMeshLoaded(); addColor(it) }

        addTool(
            name = "set_grid",
            description = "Changes the grid size and/or blur. Omitted fields keep their current value. Changing " +
                "rows or columns replaces every point with an evenly spaced grid, so call get_mesh again afterwards.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("rows") { put("type", "integer"); put("description", "Rows, $MIN_GRID_SIZE to $MAX_GRID_SIZE") }
                    putJsonObject("cols") { put("type", "integer"); put("description", "Columns, $MIN_GRID_SIZE to $MAX_GRID_SIZE") }
                    putJsonObject("blur") { put("type", "number"); put("description", "Blur level, 0 to 1") }
                },
            ),
        ) { configuration.awaitMeshLoaded(); setGrid(it) }

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
        val colors = configuration.palette()
        val meshState = configuration.meshState.value
        val document = configuration.document.value
        val json = buildJsonObject {
            put("document", buildJsonObject {
                put("name", document.name)
                put("file", document.file?.absolutePath)
                put("modified", document.modified)
            })
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

    /** Runs on the UI thread without suspending, so the point and palette can't change between read and write. */
    private suspend fun updatePoint(request: CallToolRequest): CallToolResult = withContext(Dispatchers.Main) {
        val paletteIds = configuration.palette().map { it.uid }.toSet()
        updatePoint(request, paletteIds)
    }

    private fun updatePoint(request: CallToolRequest, paletteIds: Set<Long>): CallToolResult {
        val edit = when (val parsed = parsePointEdit(request.arguments ?: JsonObject(emptyMap()), paletteIds)) {
            is ParsedEdit.Valid -> parsed.edit
            is ParsedEdit.Invalid -> return error(parsed.message)
        }
        configuration.updateMeshPoint(edit.row, edit.col, edit.point)
        val (offset, newColorId) = configuration.meshPoints[edit.row][edit.col]
        return CallToolResult(
            content = listOf(TextContent("Point (${edit.row}, ${edit.col}) is now at x=${offset.x}, y=${offset.y} with color $newColorId"))
        )
    }

    /** Validates every edit before applying any, so a bad entry leaves the mesh untouched. */
    private suspend fun updatePoints(request: CallToolRequest): CallToolResult = withContext(Dispatchers.Main) {
        val entries = (request.arguments?.get("points") as? JsonArray)
            ?: return@withContext error("points is required: an array of {row, col, x?, y?, colorId?}")
        val paletteIds = configuration.palette().map { it.uid }.toSet()
        val edits = entries.mapIndexed { index, entry ->
            val args = entry as? JsonObject ?: return@withContext error("points[$index] must be an object")
            when (val parsed = parsePointEdit(args, paletteIds)) {
                is ParsedEdit.Valid -> parsed.edit
                is ParsedEdit.Invalid -> return@withContext error("points[$index]: ${parsed.message}")
            }
        }
        configuration.updateMeshPoints(edits)
        CallToolResult(content = listOf(TextContent("Updated ${edits.size} points as one edit")))
    }

    private sealed interface ParsedEdit {
        data class Valid(val edit: MeshPointEdit) : ParsedEdit
        data class Invalid(val message: String) : ParsedEdit
    }

    /** Reads the edit that [args] describe, checking it against the open mesh and [paletteIds]. */
    private fun parsePointEdit(args: JsonObject, paletteIds: Set<Long>): ParsedEdit {
        val row = args.int("row") ?: return ParsedEdit.Invalid("row is required")
        val col = args.int("col") ?: return ParsedEdit.Invalid("col is required")
        val points = configuration.meshPoints
        if (row !in points.indices || col !in points[row].indices) {
            return ParsedEdit.Invalid("No point at row $row, col $col; the grid has ${points.size} rows and ${points.firstOrNull()?.size ?: 0} columns")
        }

        val (current, currentColorId) = points[row][col]
        // Validate only what the caller sent, so echoing a point's current values back always works,
        // including the transparent ID of a deleted color or an offset typed outside 0–1 in the side panel.
        val requestedColorId = args.long("colorId")
        if (requestedColorId != null && requestedColorId != currentColorId && requestedColorId !in paletteIds) {
            return ParsedEdit.Invalid("No color with ID $requestedColorId; call get_mesh for color IDs or add_color to create one")
        }
        val requestedX = args.float("x")
        val requestedY = args.float("y")
        if (requestedX?.let { it !in 0f..1f } == true || requestedY?.let { it !in 0f..1f } == true) {
            return ParsedEdit.Invalid("x and y must be between 0 and 1")
        }
        val x = requestedX ?: current.x
        val y = requestedY ?: current.y
        return ParsedEdit.Valid(MeshPointEdit(row, col, Offset(x, y) to (requestedColorId ?: currentColorId)))
    }

    private suspend fun setGrid(request: CallToolRequest): CallToolResult {
        val args = request.arguments ?: JsonObject(emptyMap())
        val rows = args.int("rows")
        val cols = args.int("cols")
        val blur = args.float("blur")
        if (rows?.let { it !in MIN_GRID_SIZE..MAX_GRID_SIZE } == true || cols?.let { it !in MIN_GRID_SIZE..MAX_GRID_SIZE } == true) {
            return error("rows and cols must be between $MIN_GRID_SIZE and $MAX_GRID_SIZE")
        }
        if (blur?.let { it !in 0f..1f } == true) return error("blur must be between 0 and 1")

        return withContext(Dispatchers.Main) {
            rows?.let(configuration::updateTotalRows)
            cols?.let(configuration::updateTotalCols)
            blur?.let(configuration::updateBlurLevel)
            val state = configuration.meshState.value
            CallToolResult(
                content = listOf(TextContent("The grid is now ${state.rows}×${state.cols} with blur ${state.blurLevel}"))
            )
        }
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

        val colorId = withContext(Dispatchers.Main) { configuration.addColor(savedColor) }
        return CallToolResult(content = listOf(TextContent("Added $hex as color ID $colorId")))
    }

    private suspend fun renderPng(request: CallToolRequest): CallToolResult {
        val scale = request.arguments?.int("scale") ?: 1
        if (scale !in 1..3) return error("scale must be 1, 2, or 3")

        val (meshState, points) = withContext(Dispatchers.Main) {
            val colors = configuration.palette()
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

        val painter = meshGradientPainter(points) ?: return error("The mesh has no complete patch to render")
        // The canvas blurs by this many pixels at 1x; scale it like the app's 2x and 3x PNG export.
        val blurRadius = (meshState.blurLevel * MAX_BLUR_LEVEL).roundToInt().toFloat() * scale
        val png = renderComposeScene(width * scale, height * scale, Density(scale.toFloat())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = if (blurRadius > 0f) BlurEffect(blurRadius, blurRadius, TileMode.Clamp) else null
                    }
                    .paint(painter, sizeToIntrinsics = false)
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
