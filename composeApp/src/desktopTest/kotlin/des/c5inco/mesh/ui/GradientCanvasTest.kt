package des.c5inco.mesh.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import data.DimensionMode
import kotlinx.coroutines.runBlocking
import model.SavedColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.ui.ComponentStyling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class GradientCanvasTest {

    @Test
    fun `subdivision points match MeshGradientPainter patch surface geometry without duplicate seams`() {
        // Asymmetric 3x4 vertex grid (2x3 patches) with non-collinear interior and edge points.
        val points = listOf(
            listOf(Offset(0.0f, 0.0f), Offset(0.24f, 0.05f), Offset(0.67f, 0.0f), Offset(1.0f, 0.0f)),
            listOf(Offset(0.02f, 0.62f), Offset(0.33f, 0.33f), Offset(0.64f, 0.54f), Offset(0.98f, 0.41f)),
            listOf(Offset(0.0f, 1.0f), Offset(0.31f, 0.96f), Offset(0.69f, 1.0f), Offset(1.0f, 1.0f)),
        )
        val painter = requireNotNull(
            meshGradientPainter(points.map { row -> row.map { it to Color.Magenta } })
        )

        // Choose a canvas size where MeshGradientPainter's largest patch average width and height
        // are in (80px, 88px], so its adaptive tessellation uses ceil(max / 8f) = 11 vertices
        // (10 steps) along both U and V.
        val drawSize = Size(225f, 155f)
        val capturedPatchVertices = mutableListOf<List<Offset>>()
        val delegateCanvas = Canvas(ImageBitmap(200, 140))
        val capturingCanvas = object : Canvas by delegateCanvas {
            override fun drawVertices(vertices: Vertices, blendMode: androidx.compose.ui.graphics.BlendMode, paint: Paint) {
                val raw = vertices.positions
                capturedPatchVertices.add(
                    List(raw.size / 2) { i ->
                        Offset(raw[i * 2] / drawSize.width, raw[i * 2 + 1] / drawSize.height)
                    }
                )
            }
        }

        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = capturingCanvas,
            size = drawSize,
        ) {
            with(painter) { draw(drawSize) }
        }

        // 2x3 = 6 patches, each 11x11 = 121 vertices.
        assertEquals(6, capturedPatchVertices.size)
        capturedPatchVertices.forEach { assertEquals(121, it.size) }

        // Deduplicate shared patch seams (uIndex == 0 when patchCol > 0, vIndex == 0 when patchRow > 0)
        // in the same patch traversal order as MeshGradientPainter.
        val expectedFromPainter = buildList {
            for (patchIdx in capturedPatchVertices.indices) {
                val patchRow = patchIdx / 3
                val patchCol = patchIdx % 3
                val patch = capturedPatchVertices[patchIdx]
                val uStart = if (patchCol == 0) 0 else 1
                val vStart = if (patchRow == 0) 0 else 1
                for (uIndex in uStart..10) {
                    for (vIndex in vStart..10) {
                        add(patch[uIndex * 11 + vIndex])
                    }
                }
            }
        }

        val actual = meshSubdivisionPoints(points, steps = 10)
        assertEquals((2 * 10 + 1) * (3 * 10 + 1), actual.size)
        assertEquals(expectedFromPainter.size, actual.size)

        for (i in actual.indices) {
            val expectedOffset = expectedFromPainter[i]
            val actualOffset = actual[i]
            assertTrue(
                abs(expectedOffset.x - actualOffset.x) < 1e-4f &&
                    abs(expectedOffset.y - actualOffset.y) < 1e-4f,
                "Mismatch at index $i: expected $expectedOffset from MeshGradientPainter, got $actualOffset",
            )
        }
    }

    @Test
    fun `subdivision points handle incomplete grids and coincident vertices without NaN`() {
        assertTrue(meshSubdivisionPoints(emptyList()).isEmpty())
        assertTrue(meshSubdivisionPoints(listOf(listOf(Offset.Zero, Offset(1f, 0f)))).isEmpty())
        assertTrue(meshSubdivisionPoints(listOf(listOf(Offset.Zero), listOf(Offset(0f, 1f)))).isEmpty())

        // Coincident neighboring vertices have zero distance between neighbors.
        val coincident = listOf(
            listOf(Offset(0.5f, 0.5f), Offset(0.5f, 0.5f)),
            listOf(Offset(0.5f, 0.5f), Offset(1.0f, 1.0f)),
        )
        val result = meshSubdivisionPoints(coincident, steps = 4)
        assertEquals(25, result.size)
        assertFalse(result.any { it.x.isNaN() || it.y.isNaN() })
    }

    @OptIn(InternalComposeUiApi::class)
    @Test
    fun `showPoints renders white subdivision dots on canvas without including them in exported layer`() = runBlocking {
        val gradientColor = SavedColor(uid = 1L, red = 119, green = 102, blue = 238)
        val meshPoints = listOf(
            listOf(Offset(0f, 0f) to 1L, Offset(1f, 0f) to 1L),
            listOf(Offset(0f, 1f) to 1L, Offset(1f, 1f) to 1L),
        )

        suspend fun renderCanvas(showPoints: Boolean): Pair<androidx.compose.ui.graphics.PixelMap, androidx.compose.ui.graphics.PixelMap> {
            lateinit var exportLayer: GraphicsLayer
            val scene = ImageComposeScene(width = 300, height = 300, density = Density(1f))
            try {
                scene.setContent {
                    IntUiTheme(
                        theme = JewelTheme.darkThemeDefinition(),
                        styling = ComponentStyling.default(),
                    ) {
                        exportLayer = rememberGraphicsLayer()
                        GradientCanvas(
                            exportGraphicsLayer = exportLayer,
                            exportScale = 1,
                            canvasWidthMode = DimensionMode.Fixed,
                            canvasWidth = 200,
                            canvasHeightMode = DimensionMode.Fixed,
                            canvasHeight = 200,
                            blurLevel = 0.25f,
                            meshPoints = meshPoints,
                            showPoints = showPoints,
                            canvasBackgroundColor = -1L,
                            availableColors = listOf(gradientColor),
                            onPointDrag = { _, _, _ -> },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                val scenePixels = scene.render(0).use { it.toComposeImageBitmap().toPixelMap() }
                val exportPixels = exportLayer.toImageBitmap().toPixelMap()
                return scenePixels to exportPixels
            } finally {
                scene.close()
            }
        }

        val (hiddenScenePixels, hiddenExportPixels) = renderCanvas(showPoints = false)
        val (shownScenePixels, shownExportPixels) = renderCanvas(showPoints = true)

        // The 200x200 fixed canvas is centered in the 300x300 scene (top-left at (50, 50)).
        // The interior subdivision dot at (u = 0.5, v = 0.5) is at scene center (150, 150),
        // far from the 4 corner PointCursor handles at (50, 50), (250, 50), (50, 250), (250, 250).
        val centerBefore = hiddenScenePixels[150, 150]
        val centerAfter = shownScenePixels[150, 150]
        assertTrue(
            centerBefore.red in 0.4f..0.55f && centerBefore.blue > 0.85f,
            "Expected purple gradient before showPoints, got $centerBefore",
        )
        assertTrue(
            centerAfter.red > 0.9f && centerAfter.green > 0.9f && centerAfter.blue > 0.9f,
            "Expected white subdivision dot at patch midpoint, got $centerAfter",
        )

        // Exported PNG layer must contain the purple gradient and omit subdivision dots when showPoints is enabled.
        val exportCenterBefore = hiddenExportPixels[100, 100]
        val exportCenterAfter = shownExportPixels[100, 100]
        assertTrue(
            exportCenterAfter.red in 0.4f..0.55f && exportCenterAfter.blue > 0.85f,
            "Exported midpoint should be the purple gradient color (not empty or white), got $exportCenterAfter",
        )
        assertEquals(
            (exportCenterBefore.red * 255).roundToInt(),
            (exportCenterAfter.red * 255).roundToInt(),
            "Exported layer should not include subdivision dots when showPoints is enabled",
        )
        assertEquals(
            (exportCenterBefore.green * 255).roundToInt(),
            (exportCenterAfter.green * 255).roundToInt(),
            "Exported layer should not include subdivision dots when showPoints is enabled",
        )
    }
}
