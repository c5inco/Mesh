package data

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import model.MeshPoint
import model.PointHandles
import model.PointMirroring
import model.SavedColor
import model.defaultMeshPointGrid
import model.generateMeshPointGrid
import model.handlesAt
import model.toOffsetGrid

/** A color stored by value, so a document keeps its colors on any machine. */
@Serializable
data class DocumentColor(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Float = 1f,
) {
    init {
        require(listOf(red, green, blue).all { it in 0..255 } && alpha in 0f..1f) {
            "A color needs RGB from 0 to 255 and alpha from 0 to 1"
        }
    }
}

/** A Bezier control point offset from its point, in normalized canvas coordinates. */
@Serializable
data class DocumentControlPoint(
    val x: Float,
    val y: Float,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "A Bezier control point needs finite coordinates" }
    }
}

/** A point's four Bezier handles; a null handle is inferred from the point's neighbors. */
@Serializable
data class DocumentHandles(
    val left: DocumentControlPoint? = null,
    val top: DocumentControlPoint? = null,
    val right: DocumentControlPoint? = null,
    val bottom: DocumentControlPoint? = null,
    val mirroring: PointMirroring = PointMirroring(),
) {
    val isAutomatic: Boolean
        get() = left == null && top == null && right == null && bottom == null
}

/** A mesh point; a null color is transparent, as after its color was deleted. */
@Serializable
data class DocumentPoint(
    val x: Float,
    val y: Float,
    val color: DocumentColor?,
    val handles: DocumentHandles? = null,
)

/**
 * The contents of a `.mesh` file: the canvas settings, the grid of points, and the document's custom colors.
 * Preset colors are global, so points that use one store its value and find it again when the file opens.
 *
 * The file's format is derived from the document rather than stored: a grid the old 10 × 10 limit held and
 * automatic Bezier handles is written as format 1, which every build reads; a larger grid as format 2; and a
 * document with explicit Bezier handles as format 3. Non-default handle mirroring needs format 4,
 * so builds that would lose the modes cannot open it.
 */
@Serializable
data class MeshDocument(
    val meshState: MeshState = MeshState(),
    val backgroundColor: DocumentColor? = null,
    val customColors: List<DocumentColor> = emptyList(),
    /** Rows of points, top to bottom; each row lists its points left to right. */
    val points: List<List<DocumentPoint>>,
) {
    init {
        require(points.size >= 2 && points.all { it.size >= 2 && it.size == points[0].size }) {
            "A mesh needs a rectangular grid of at least 2 × 2 points"
        }
        // The editor, set_grid, and update_points' batch limit all assume this range.
        require(points.size in MIN_GRID_SIZE..MAX_GRID_SIZE && points[0].size in MIN_GRID_SIZE..MAX_GRID_SIZE) {
            "A mesh can have $MIN_GRID_SIZE to $MAX_GRID_SIZE rows and columns; this one has ${points.size} × ${points[0].size}"
        }
    }

    /**
     * The format to write: [MIRRORING_VERSION] for non-default modes, [HANDLES_VERSION] for explicit handles;
     * otherwise [FIRST_VERSION] while the grid fits the old app's limit, which every build reads, and
     * [LARGE_GRID_VERSION] once it doesn't, since only a build that reads larger grids can open it.
     */
    private val version: Int
        get() = when {
            points.any { row -> row.any { it.handles != null && it.handles.mirroring != PointMirroring() } } -> MIRRORING_VERSION
            points.any { row -> row.any { it.handles?.isAutomatic == false } } -> HANDLES_VERSION
            points.size > LEGACY_MAX_GRID_SIZE || points[0].size > LEGACY_MAX_GRID_SIZE -> LARGE_GRID_VERSION
            else -> FIRST_VERSION
        }

    /** The file's text, with `version` first: a reader checks it before it decodes the rest. */
    fun encode(): String {
        val fields = json.encodeToJsonElement(serializer(), this).jsonObject
        val document = buildJsonObject {
            put("version", version)
            fields.forEach { (name, value) -> put(name, value) }
        }
        return json.encodeToString(JsonObject.serializer(), document)
    }

    companion object {
        /** The newest format this build reads; a file that names a later one doesn't open. */
        const val CURRENT_VERSION = 4
        const val FILE_EXTENSION = "mesh"

        /**
         * The format Mesh wrote before larger grids. It is the oldest one, and what a file with no `version`
         * key is taken to be: the formats after it are the ones that can gain fields a file like that has no
         * value for.
         */
        private const val FIRST_VERSION = 1

        /** The format for a mesh larger than the old 10 × 10 limit. */
        private const val LARGE_GRID_VERSION = 2

        /** The format for a document with explicit Bezier handles, which older builds would drop. */
        private const val HANDLES_VERSION = 3

        /** The format for saved handle mirroring modes, including points with automatic positions. */
        private const val MIRRORING_VERSION = 4

        private val json = Json {
            prettyPrint = true
            encodeDefaults = true
            // Files from older versions may hold settings this version dropped, such as the renderer's resolution.
            ignoreUnknownKeys = true
        }

        fun decode(text: String): MeshDocument {
            val element = json.parseToJsonElement(text)
            // Checked first, since a newer format may not decode as this one.
            val version = versionOf(element)
            require(version <= CURRENT_VERSION) { "This file needs a newer version of Mesh (format $version)" }
            // The version isn't part of the document; it's derived from the grid when the file is written.
            return json.decodeFromJsonElement(serializer(), JsonObject(element.jsonObject - "version"))
        }

        /** The format [element] names; a file with no `version` key is the oldest one, [FIRST_VERSION]. */
        internal fun versionOf(element: JsonElement): Int =
            element.jsonObject["version"]?.jsonPrimitive?.intOrNull ?: FIRST_VERSION
    }
}

/**
 * Custom colors get IDs from here up, so they never collide with the presets' database IDs.
 * IDs last only while a document is open; the file stores colors by value.
 */
const val FIRST_CUSTOM_COLOR_ID = 1_000_000L

/** The ID points and the background use for a transparent color. */
const val TRANSPARENT_COLOR_ID = -1L

/** A document whose colors are resolved to the IDs the editor works with. */
data class OpenedMesh(
    val meshState: MeshState,
    val backgroundColorId: Long,
    val customColors: List<SavedColor>,
    val points: List<List<Pair<Offset, Long>>>,
    val handles: List<List<PointHandles>>,
)

/** Builds a document from the editor's state; [colors] must include every color the IDs refer to. */
fun meshDocumentOf(
    meshState: MeshState,
    backgroundColorId: Long,
    customColors: List<SavedColor>,
    points: List<List<Pair<Offset, Long>>>,
    colors: List<SavedColor>,
    handles: List<List<PointHandles>> = emptyList(),
): MeshDocument {
    fun colorOf(id: Long) = colors.find { it.uid == id }?.toDocumentColor()
    return MeshDocument(
        meshState = meshState,
        backgroundColor = colorOf(backgroundColorId),
        customColors = customColors.map { it.toDocumentColor() },
        points = points.mapIndexed { row, pointsInRow ->
            pointsInRow.mapIndexed { col, (offset, colorId) ->
                DocumentPoint(
                    x = offset.x,
                    y = offset.y,
                    color = colorOf(colorId),
                    handles = handles.handlesAt(row, col).toDocumentHandles(),
                )
            }
        },
    )
}

/**
 * Resolves the document's colors to IDs, taking new custom color IDs from [newCustomColorId]. A color that
 * matches a preset uses the preset; any other color becomes a custom color, even one missing from
 * [MeshDocument.customColors], so no color is lost. The grid size follows the points.
 */
fun MeshDocument.open(presetColors: List<SavedColor>, newCustomColorId: () -> Long): OpenedMesh {
    val custom = mutableListOf<SavedColor>()
    fun addCustom(color: DocumentColor) =
        SavedColor(uid = newCustomColorId(), red = color.red, green = color.green, blue = color.blue, alpha = color.alpha)
            .also { custom += it }
            .uid

    fun idOf(color: DocumentColor?): Long {
        if (color == null) return TRANSPARENT_COLOR_ID
        return presetColors.find { it.toDocumentColor() == color }?.uid
            ?: custom.find { it.toDocumentColor() == color }?.uid
            ?: addCustom(color)
    }

    customColors.forEach(::addCustom)
    return OpenedMesh(
        meshState = meshState.copy(rows = points.size, cols = points[0].size),
        backgroundColorId = idOf(backgroundColor),
        points = points.map { row -> row.map { Offset(it.x, it.y) to idOf(it.color) } },
        customColors = custom,
        handles = points.map { row -> row.map { it.handles.toPointHandles() } },
    )
}

/** A point's handles and modes, or null when all positions are automatic and both pairs independent. */
fun PointHandles.toDocumentHandles(): DocumentHandles? = if (isAutomatic && mirroring == PointMirroring()) {
    null
} else {
    DocumentHandles(
        left = left?.toDocumentControlPoint(),
        top = top?.toDocumentControlPoint(),
        right = right?.toDocumentControlPoint(),
        bottom = bottom?.toDocumentControlPoint(),
        mirroring = mirroring,
    )
}

/** A file's handles as the editor's, filling in automatic ones for a point that has none. */
fun DocumentHandles?.toPointHandles(): PointHandles = PointHandles(
    left = this?.left?.let { Offset(it.x, it.y) },
    top = this?.top?.let { Offset(it.x, it.y) },
    right = this?.right?.let { Offset(it.x, it.y) },
    bottom = this?.bottom?.let { Offset(it.x, it.y) },
    mirroring = this?.mirroring ?: PointMirroring(),
)

private fun Offset.toDocumentControlPoint() = DocumentControlPoint(x = x, y = y)

/**
 * The largest grid the old app could save. It bounds legacy data, decides when a document is written in the
 * newer format, and must not follow [MAX_GRID_SIZE], which has since grown.
 */
private const val LEGACY_MAX_GRID_SIZE = 10

/**
 * The single mesh Mesh saved before it had documents, as a document: its points, the canvas settings from
 * [savedState] (null when there was no settings file), and every custom color in [colors], which holds the
 * old database's presets and custom colors. Returns null when nothing was saved.
 *
 * The settings file and the points table were written separately, so their grid sizes can disagree; the
 * points win. Without saved points the mesh is the default grid, or a fresh grid at a saved size that isn't
 * the default, as the old app showed.
 */
fun legacyMeshDocument(points: List<MeshPoint>, savedState: MeshState?, colors: List<SavedColor>): MeshDocument? {
    if (points.isEmpty() && savedState == null) return null

    val state = savedState ?: MeshState()
    val colorIds = colors.map(SavedColor::uid)
    val rows = state.rows.coerceIn(2, LEGACY_MAX_GRID_SIZE)
    val cols = state.cols.coerceIn(2, LEGACY_MAX_GRID_SIZE)
    // Points outside the old app's 10 × 10 limit, or at a negative index, are strays; dropping them keeps the
    // grid within the size a document allows. Points that leave no 2 × 2 grid fall back to a fresh one.
    val grid = points.filter { it.row in 0 until LEGACY_MAX_GRID_SIZE && it.col in 0 until LEGACY_MAX_GRID_SIZE }
        .toOffsetGrid()
        .takeIf { it.size >= 2 && it[0].size >= 2 }
        ?: if (rows == 3 && cols == 4) defaultMeshPointGrid(colorIds) else generateMeshPointGrid(rows, cols, colorIds)
    return meshDocumentOf(
        meshState = state.copy(rows = grid.size, cols = grid[0].size),
        backgroundColorId = TRANSPARENT_COLOR_ID,
        customColors = colors.filterNot(SavedColor::preset),
        points = grid,
        colors = colors,
    )
}

fun SavedColor.toDocumentColor() = DocumentColor(red = red, green = green, blue = blue, alpha = alpha)
