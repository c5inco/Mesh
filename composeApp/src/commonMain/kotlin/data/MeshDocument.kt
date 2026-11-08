package data

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.MeshPoint
import model.SavedColor
import model.defaultMeshPointGrid
import model.generateMeshPointGrid
import model.toOffsetGrid

/** A color stored by value, so a document keeps its colors on any machine. */
@Serializable
data class DocumentColor(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Float = 1f,
)

/** A mesh point; a null color is transparent, as after its color was deleted. */
@Serializable
data class DocumentPoint(
    val x: Float,
    val y: Float,
    val color: DocumentColor?,
)

/**
 * The contents of a `.mesh` file: the canvas settings, the grid of points, and the document's custom colors.
 * Preset colors are global, so points that use one store its value and find it again when the file opens.
 */
@Serializable
data class MeshDocument(
    val version: Int = CURRENT_VERSION,
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
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_EXTENSION = "mesh"

        private val json = Json {
            prettyPrint = true
            encodeDefaults = true
            // Files from older versions may hold settings this version dropped, such as the renderer's resolution.
            ignoreUnknownKeys = true
        }

        fun decode(text: String): MeshDocument {
            val element = json.parseToJsonElement(text)
            // Checked first, since a newer format may not decode as this one.
            val version = element.jsonObject["version"]?.jsonPrimitive?.intOrNull ?: CURRENT_VERSION
            require(version <= CURRENT_VERSION) { "This file needs a newer version of Mesh (format $version)" }
            return json.decodeFromJsonElement(serializer(), element)
        }
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
)

/** Builds a document from the editor's state; [colors] must include every color the IDs refer to. */
fun meshDocumentOf(
    meshState: MeshState,
    backgroundColorId: Long,
    customColors: List<SavedColor>,
    points: List<List<Pair<Offset, Long>>>,
    colors: List<SavedColor>,
): MeshDocument {
    fun colorOf(id: Long) = colors.find { it.uid == id }?.toDocumentColor()
    return MeshDocument(
        meshState = meshState,
        backgroundColor = colorOf(backgroundColorId),
        customColors = customColors.map { it.toDocumentColor() },
        points = points.map { row -> row.map { (offset, colorId) -> DocumentPoint(offset.x, offset.y, colorOf(colorId)) } },
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
    )
}

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
    val rows = state.rows.coerceIn(2, 10)
    val cols = state.cols.coerceIn(2, 10)
    val grid = points.toOffsetGrid().takeIf { it.size >= 2 && it[0].size >= 2 }
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
