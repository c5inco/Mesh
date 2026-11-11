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
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import model.SavedColor
import model.findColor
import model.withUpdatedMeshPoint
import org.jetbrains.skia.EncodedImageFormat
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.encoding.Base64
import kotlin.math.roundToInt

/**
 * Exposes the open mesh to MCP clients over Streamable HTTP on localhost.
 *
 * Tools act on the document open in the window, through the same [AppConfiguration], so edits appear on the
 * canvas live. Opening another document switches the tools to it; color IDs belong to the open document.
 */
class MeshMcpServer(private val configuration: AppConfiguration) {

    /**
     * The server every session connects to. The SDK calls its factory once per session, so returning the
     * same server keeps one session registry, which is what lets [connectedClients] see them all.
     */
    private val server by lazy { createServer() }

    /** When an agent last called a tool, or 0 if none has since the app started. */
    private val lastToolCall = AtomicLong(0)

    fun start(port: Int = DEFAULT_PORT): EmbeddedServer<*, *> =
        embeddedServer(CIO, host = "127.0.0.1", port = port) {
            mcpStreamableHttp { server }
        }.start(wait = false)

    /**
     * One name per connected session, as each client sent it when it initialized. A client that has
     * connected but not finished initializing has no name yet, so it is not counted. Names repeat when
     * the same client software holds more than one session, so callers must count entries, not names.
     */
    fun connectedClients(): List<String> =
        server.sessions.values.mapNotNull { session -> session.clientVersion?.name }

    /** When an agent last called a tool, or 0 if none has since the app started. */
    fun lastToolCallMillis(): Long = lastToolCall.get()

    /**
     * Registers [handler] as a tool, recording the call so the toolbar can show an agent at work.
     */
    private fun Server.addTrackedTool(
        name: String,
        description: String,
        inputSchema: ToolSchema = ToolSchema(),
        handler: suspend ClientConnection.(CallToolRequest) -> CallToolResult,
    ) {
        addTool(name = name, description = description, inputSchema = inputSchema) { request ->
            lastToolCall.set(System.currentTimeMillis())
            handler(request)
        }
    }

    private fun createServer() = Server(
        serverInfo = Implementation(name = "mesh", version = "1.1.1"),
        options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools())),
        instructions = "Mesh edits a mesh gradient: a grid of points, each with a position and a color. " +
            "Call get_mesh first to read the grid and the color IDs; a grid of more than $MAX_READ_POINTS points " +
            "comes back without its points unless you pass rowStart, rowEnd, colStart, and colEnd, which must select " +
            "a region of at most $MAX_READ_POINTS points and is rejected otherwise. " +
            "Then edit with set_grid, update_point (or update_points for many at once), and add_color, " +
            "and check the result with render_png. Use undo to take back an edit.",
    ) {
        addTrackedTool(
            name = "get_mesh",
            description = "Returns the open document's mesh as JSON: the document's name and file, grid size, " +
                "canvas settings, the preset and custom colors with their IDs, and the points' row, column, " +
                "position, and color ID. Positions are fractions of the canvas from 0 to 1, with (0, 0) at the top " +
                "left. Returns at most $MAX_READ_POINTS points per call: pass rowStart, rowEnd, colStart, and " +
                "colEnd to read a region of a larger grid, which otherwise comes back without its points. A region " +
                "over $MAX_READ_POINTS points is rejected, and so is a field other than those four. " +
                "Color IDs belong to the open document and change when another document opens.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putRangeProperty("rowStart", "First row to return, inclusive. Must be a row of the current grid; omitted defaults to its first row.")
                    putRangeProperty("rowEnd", "Last row to return, inclusive. Must be a row of the current grid; omitted defaults to its last row, and 0 asks for row 0 alone.")
                    putRangeProperty("colStart", "First column to return, inclusive. Must be a column of the current grid; omitted defaults to its first column.")
                    putRangeProperty("colEnd", "Last column to return, inclusive. Must be a column of the current grid; omitted defaults to its last column, and 0 asks for column 0 alone.")
                },
            ),
        ) { configuration.awaitMeshLoaded(); getMesh(it) }

        addTrackedTool(
            name = "update_point",
            description = "Moves and/or recolors the point at a row and column. Omitted fields keep their " +
                "current value; fields other than row, col, x, y, and colorId are rejected. When edge constraints " +
                "are on (the default), edge points stay on their edge.",
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

        addTrackedTool(
            name = "update_points",
            description = "Moves and/or recolors many points as a single undoable edit, like update_point for each " +
                "entry in order, and returns where each edited point ended up. Takes 1 to $MAX_BATCH_POINTS entries. " +
                "If any entry is invalid, no point changes.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("points") {
                        put("type", "array")
                        put("minItems", 1)
                        put("maxItems", MAX_BATCH_POINTS)
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
                            put("additionalProperties", false)
                        }
                    }
                },
                required = listOf("points"),
            ),
        ) { configuration.awaitMeshLoaded(); updatePoints(it) }

        addTrackedTool(
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

        addTrackedTool(
            name = "set_grid",
            description = "Changes the grid size and/or blur as one undoable edit; pass at least one. Omitted fields " +
                "keep their current value. Changing " +
                "rows or columns replaces every point with an evenly spaced grid, so call get_mesh again afterwards.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("rows") {
                        put("type", "integer"); put("minimum", MIN_GRID_SIZE); put("maximum", MAX_GRID_SIZE)
                        put("description", "Rows, $MIN_GRID_SIZE to $MAX_GRID_SIZE")
                    }
                    putJsonObject("cols") {
                        put("type", "integer"); put("minimum", MIN_GRID_SIZE); put("maximum", MAX_GRID_SIZE)
                        put("description", "Columns, $MIN_GRID_SIZE to $MAX_GRID_SIZE")
                    }
                    putJsonObject("blur") {
                        put("type", "number"); put("minimum", 0); put("maximum", 1)
                        put("description", "Blur level, 0 to 1")
                    }
                },
            ),
        ) { configuration.awaitMeshLoaded(); setGrid(it) }

        addTrackedTool(
            name = "undo",
            description = "Undoes the last edit to the mesh, whether the user or an agent made it.",
        ) { undoOrRedo(redo = false) }

        addTrackedTool(
            name = "redo",
            description = "Redoes the last undone edit.",
        ) { undoOrRedo(redo = true) }

        addTrackedTool(
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

    /**
     * Returns the document, the settings, and the colors, plus the points the caller asked for. A whole grid of more
     * than [MAX_READ_POINTS] points comes back without them, so one call can't flood an agent's context; a requested
     * range over the limit is an error, never a silent cut.
     */
    private suspend fun getMesh(request: CallToolRequest): CallToolResult {
        val args = request.arguments ?: JsonObject(emptyMap())
        val range = try {
            args.requireOnly(RANGE_FIELDS, "get_mesh")
            MeshRange(
                rowStart = args.optionalInt("rowStart"),
                rowEnd = args.optionalInt("rowEnd"),
                colStart = args.optionalInt("colStart"),
                colEnd = args.optionalInt("colEnd"),
            )
        } catch (e: InvalidArgument) {
            return error(e.message)
        }

        return withContext(Dispatchers.Main) {
            val colors = configuration.palette()
            val meshState = configuration.meshState.value
            val document = configuration.document.value
            val points = configuration.meshPoints
            val grid = "${meshState.rows} rows and ${meshState.cols} columns"
            val firstRow = range.rowStart ?: 0
            val lastRow = range.rowEnd ?: meshState.rows - 1
            val firstCol = range.colStart ?: 0
            val lastCol = range.colEnd ?: meshState.cols - 1
            // A range is validated against the grid, not clamped: an end past the grid is a mistake worth reporting.
            if (firstRow !in 0 until meshState.rows) return@withContext error("rowStart $firstRow is outside the grid; it has $grid")
            if (lastRow !in 0 until meshState.rows) return@withContext error("rowEnd $lastRow is outside the grid; it has $grid")
            if (firstRow > lastRow) return@withContext error("rowStart $firstRow is past rowEnd $lastRow")
            if (firstCol !in 0 until meshState.cols) return@withContext error("colStart $firstCol is outside the grid; it has $grid")
            if (lastCol !in 0 until meshState.cols) return@withContext error("colEnd $lastCol is outside the grid; it has $grid")
            if (firstCol > lastCol) return@withContext error("colStart $firstCol is past colEnd $lastCol")

            val count = (lastRow - firstRow + 1) * (lastCol - firstCol + 1)
            val omitted = range.isWholeGrid && count > MAX_READ_POINTS
            if (!range.isWholeGrid && count > MAX_READ_POINTS) {
                return@withContext error("The range has $count points; the limit is $MAX_READ_POINTS per call, so pass a smaller range")
            }

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
                put("constrainEdgePoints", configuration.constrainEdgePoints)
                put("colors", buildJsonArray {
                    colors.forEach { color ->
                        add(buildJsonObject {
                            put("id", color.uid)
                            put("hex", "#" + colors.findColor(color.uid).toHexStringNoHash(includeAlpha = true))
                            put("preset", color.preset)
                        })
                    }
                })
                if (omitted) {
                    // The whole grid's bounds are the call that errors, so name a region that fits the limit.
                    val exampleLastRow = minOf(meshState.rows - 1, (MAX_READ_POINTS / meshState.cols).coerceAtLeast(1) - 1)
                    put(
                        "pointsOmitted",
                        "The grid has $count points and get_mesh returns at most $MAX_READ_POINTS per call; pass " +
                            "a region of at most $MAX_READ_POINTS points, such as rowStart 0, rowEnd $exampleLastRow, " +
                            "colStart 0, colEnd ${meshState.cols - 1}, or call render_png to see the mesh.",
                    )
                } else {
                    put("points", buildJsonArray {
                        for (row in firstRow..lastRow) {
                            for (col in firstCol..lastCol) {
                                val (offset, colorId) = points[row][col]
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
            }
            CallToolResult(content = listOf(TextContent(json.toString())))
        }
    }

    /** Runs on the UI thread without suspending, so the point and palette can't change between read and write. */
    private suspend fun updatePoint(request: CallToolRequest): CallToolResult = withContext(Dispatchers.Main) {
        val paletteIds = configuration.palette().map { it.uid }.toSet()
        updatePoint(request, paletteIds)
    }

    private fun updatePoint(request: CallToolRequest, paletteIds: Set<Long>): CallToolResult {
        val edit = try {
            parsePointEdit(request.arguments ?: JsonObject(emptyMap()), configuration.meshPoints, paletteIds)
        } catch (e: InvalidArgument) {
            return error(e.message)
        }
        configuration.updateMeshPoint(edit.row, edit.col, edit.point)
        val (offset, newColorId) = configuration.meshPoints[edit.row][edit.col]
        return CallToolResult(
            content = listOf(TextContent("Point (${edit.row}, ${edit.col}) is now at x=${offset.x}, y=${offset.y} with color $newColorId"))
        )
    }

    /** Validates every edit before applying any, so a bad entry leaves the mesh untouched. */
    private suspend fun updatePoints(request: CallToolRequest): CallToolResult = withContext(Dispatchers.Main) {
        try {
            request.arguments?.requireOnly(setOf("points"), "update_points")
        } catch (e: InvalidArgument) {
            return@withContext error(e.message)
        }
        val entries = (request.arguments?.get("points") as? JsonArray)
            ?: return@withContext error("points is required: an array of {row, col, x?, y?, colorId?}")
        if (entries.isEmpty()) return@withContext error("points is empty; pass at least one {row, col, x?, y?, colorId?}")
        if (entries.size > MAX_BATCH_POINTS) {
            return@withContext error("points has ${entries.size} entries; the limit is $MAX_BATCH_POINTS, one per point of the largest grid")
        }
        val paletteIds = configuration.palette().map { it.uid }.toSet()
        val constrainEdges = configuration.constrainEdgePoints
        // Each entry is read against the points as the entries before it leave them, as separate update_point
        // calls would be; updateMeshPoints then replays the same edits onto the mesh as one undo step.
        var points: List<List<Pair<Offset, Long>>> = configuration.meshPoints.toList()
        val edits = entries.mapIndexed { index, entry ->
            val args = entry as? JsonObject ?: return@withContext error("points[$index] must be an object")
            val edit = try {
                parsePointEdit(args, points, paletteIds)
            } catch (e: InvalidArgument) {
                return@withContext error("points[$index]: ${e.message}")
            }
            points = points.withUpdatedMeshPoint(edit.row, edit.col, edit.point, constrainEdges)
            edit
        }
        val changed = configuration.updateMeshPoints(edits)
        // Read back from the mesh, so the caller sees where edge constraints kept a point.
        val edited = buildJsonArray {
            edits.map { it.row to it.col }.distinct().forEach { (row, col) ->
                val (offset, colorId) = configuration.meshPoints[row][col]
                add(buildJsonObject {
                    put("row", row)
                    put("col", col)
                    put("x", offset.x)
                    put("y", offset.y)
                    put("colorId", colorId)
                })
            }
        }
        val summary = if (changed) {
            "Updated ${edited.size} points as one edit"
        } else {
            "No change: the points already had these values, so there is nothing to undo"
        }
        CallToolResult(content = listOf(TextContent("$summary. The edited points are now: $edited")))
    }

    /** Reads the edit that [args] describe, checking it against [points] and [paletteIds], or throws [InvalidArgument]. */
    private fun parsePointEdit(args: JsonObject, points: List<List<Pair<Offset, Long>>>, paletteIds: Set<Long>): MeshPointEdit {
        args.requireOnly(POINT_EDIT_FIELDS, "a point edit")
        val row = args.optionalInt("row") ?: throw InvalidArgument("row is required")
        val col = args.optionalInt("col") ?: throw InvalidArgument("col is required")
        if (row !in points.indices || col !in points[row].indices) {
            throw InvalidArgument("No point at row $row, col $col; the grid has ${points.size} rows and ${points.firstOrNull()?.size ?: 0} columns")
        }

        val (current, currentColorId) = points[row][col]
        // Validate only what the caller sent, so echoing a point's current values back always works,
        // including the transparent ID of a deleted color or an offset typed outside 0–1 in the side panel.
        val requestedColorId = args.optionalLong("colorId")
        if (requestedColorId != null && requestedColorId != currentColorId && requestedColorId !in paletteIds) {
            throw InvalidArgument("No color with ID $requestedColorId; call get_mesh for color IDs or add_color to create one")
        }
        val requestedX = args.optionalFloat("x")
        val requestedY = args.optionalFloat("y")
        if (requestedX.isOutOfRange(current.x) || requestedY.isOutOfRange(current.y)) {
            throw InvalidArgument("x and y must be between 0 and 1")
        }
        val x = requestedX ?: current.x
        val y = requestedY ?: current.y
        return MeshPointEdit(row, col, Offset(x, y) to (requestedColorId ?: currentColorId))
    }

    private suspend fun setGrid(request: CallToolRequest): CallToolResult {
        val args = request.arguments ?: JsonObject(emptyMap())
        val (rows, cols, blur) = try {
            // An unknown field such as a misspelled cols must not let the rest of this destructive edit through.
            args.requireOnly(setOf("rows", "cols", "blur"), "set_grid")
            Triple(args.optionalInt("rows"), args.optionalInt("cols"), args.optionalFloat("blur"))
        } catch (e: InvalidArgument) {
            return error(e.message)
        }
        if (rows?.let { it !in MIN_GRID_SIZE..MAX_GRID_SIZE } == true || cols?.let { it !in MIN_GRID_SIZE..MAX_GRID_SIZE } == true) {
            return error("rows and cols must be between $MIN_GRID_SIZE and $MAX_GRID_SIZE")
        }
        if (blur?.let { it !in 0f..1f } == true) return error("blur must be between 0 and 1")
        if (rows == null && cols == null && blur == null) return error("Pass at least one of rows, cols, and blur")

        return withContext(Dispatchers.Main) {
            val changed = configuration.setGrid(rows, cols, blur)
            val state = configuration.meshState.value
            val grid = "${state.rows}×${state.cols} with blur ${state.blurLevel}"
            CallToolResult(
                content = listOf(TextContent(if (changed) "The grid is now $grid" else "No change: the grid is already $grid"))
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
        val scale = try {
            request.arguments?.optionalInt("scale") ?: 1
        } catch (e: InvalidArgument) {
            return error(e.message)
        }
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

    /** A tool argument the tool can't use; the tool reports [message] and changes nothing. */
    private class InvalidArgument(override val message: String) : Exception(message)

    /** The inclusive, 0-based point range a get_mesh call asked for; a null end means the grid's last row or column. */
    private class MeshRange(val rowStart: Int?, val rowEnd: Int?, val colStart: Int?, val colEnd: Int?) {
        val isWholeGrid = rowStart == null && rowEnd == null && colStart == null && colEnd == null
    }

    // Strict readers: an absent or null field is null, and a field that is present but not a number of the right
    // kind, such as a string, a boolean, or a fraction where an integer belongs, is an InvalidArgument.
    private fun JsonObject.optionalInt(key: String) =
        optionalNumber(key, "an integer") { it.integerOrNull()?.takeIf { value -> value in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() }
    private fun JsonObject.optionalLong(key: String) = optionalNumber(key, "an integer") { it.integerOrNull() }
    private fun JsonObject.optionalFloat(key: String) = optionalNumber(key, "a number") { it.floatOrNull }

    private inline fun <T : Any> JsonObject.optionalNumber(key: String, kind: String, read: (JsonPrimitive) -> T?): T? {
        val value = get(key)?.takeUnless { it is JsonNull } ?: return null
        return (value as? JsonPrimitive)?.takeUnless { it.isString }?.let(read) ?: throw InvalidArgument("$key must be $kind")
    }

    /**
     * An integer, including one a client wrote with a zero fraction such as 2.0, as JSON Schema allows. Reads the
     * number's exact decimal value, so a fraction that survives as a literal is rejected.
     *
     * The MCP SDK re-encodes number literals before a handler sees them, which writes them as a Long or a Double,
     * so 2.0000000000000001 arrives as 2.0 and is deliberately accepted as an integer: it is 2 in IEEE-754, and
     * rejecting it would mean rejecting 2.0 too or reading the raw request ahead of the SDK.
     */
    private fun JsonPrimitive.integerOrNull(): Long? {
        val value = content.toBigDecimalOrNull()?.stripTrailingZeros()?.takeIf { it.scale() <= 0 } ?: return null
        return try {
            value.longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Throws [InvalidArgument] for any field outside [fields], naming [taker] as what takes them. */
    private fun JsonObject.requireOnly(fields: Set<String>, taker: String) {
        val unknown = keys - fields
        if (unknown.isNotEmpty()) throw InvalidArgument("Unknown field ${unknown.joinToString()}; $taker takes ${fields.joinToString()}")
    }

    /** Whether a requested coordinate is outside 0–1; echoing the [current] one back is always allowed. */
    private fun Float?.isOutOfRange(current: Float) = this != null && this != current && this !in 0f..1f

    /** Adds an inclusive get_mesh range argument: an index of the open grid, from 0. */
    private fun JsonObjectBuilder.putRangeProperty(name: String, description: String) {
        putJsonObject(name) {
            put("type", "integer"); put("minimum", 0)
            put("description", description)
        }
    }

    companion object {
        const val DEFAULT_PORT = 7777
        private const val MAX_RENDER_SIDE = 8192

        // update_points runs on the UI thread, so bound its work. One entry per point of the largest grid is
        // enough to edit every point once; a longer batch must repeat points, which only adds UI-thread work.
        const val MAX_BATCH_POINTS = MAX_GRID_SIZE * MAX_GRID_SIZE

        // get_mesh returns at most this many points, so one call can't flood an agent's context: 16×16 is about
        // 16 KB of JSON, while the largest grid's 4,096 points are a few hundred KB.
        const val MAX_READ_POINTS = 256

        private val POINT_EDIT_FIELDS = setOf("row", "col", "x", "y", "colorId")
        private val RANGE_FIELDS = setOf("rowStart", "rowEnd", "colStart", "colEnd")
    }
}
