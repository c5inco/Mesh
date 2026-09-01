package des.c5inco.mesh

import des.c5inco.mesh.testing.MeshAppFixture
import des.c5inco.mesh.testing.MeshTestTags
import des.c5inco.mesh.testing.captureWindowPng
import des.c5inco.mesh.testing.diffImages
import des.c5inco.mesh.testing.screenshotOutputDir
import des.c5inco.mesh.testing.waitForTestTag
import des.c5inco.mesh.testing.writeDiffPreview
import java.awt.GraphicsEnvironment
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@Tag("spectre")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MeshGradientVisualTest {
    private val fixture = MeshAppFixture(title = "Mesh Visual")

    @BeforeAll
    fun start() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        fixture.start()
    }

    @AfterAll
    fun stop() = fixture.stop()

    @Test
    fun `captures the default mesh gradient window`(): Unit = runBlocking {
        with(fixture.automator) {
            waitForTestTag(MeshTestTags.GRADIENT_CANVAS)
            waitForVisualIdle()
            val screenshotName = System.getProperty("mesh.screenshot.name", "gradient-current.png")
            val file = captureWindowPng(screenshotName)
            check(file.isFile && file.length() > 0) {
                "Expected screenshot at ${file.absolutePath}"
            }
        }
    }

    @Test
    fun `mesh gradient stays visually close to the custom renderer baseline`(): Unit = runBlocking {
        val baselinePath = System.getProperty("mesh.screenshot.baseline")
        assumeTrue(!baselinePath.isNullOrBlank(), "No baseline screenshot configured")
        val baseline = File(baselinePath)
        assumeTrue(baseline.isFile, "Baseline screenshot missing: $baselinePath")

        with(fixture.automator) {
            waitForTestTag(MeshTestTags.GRADIENT_CANVAS)
            waitForVisualIdle()
            val actualFile = captureWindowPng("gradient-after-meshgradientpainter.png")
            val expected = ImageIO.read(baseline)
            val actual = ImageIO.read(actualFile)
            val diff = diffImages(expected, actual)
            val preview = writeDiffPreview(
                expected = expected,
                actual = actual,
                destination = File(screenshotOutputDir(), "gradient-diff.png"),
            )

            check(expected.width == actual.width && expected.height == actual.height) {
                "Screenshot size changed: ${expected.width}x${expected.height} -> ${actual.width}x${actual.height}"
            }
            check(diff.meanAbsoluteChannelDelta < 12.0) {
                "Mean channel delta ${diff.meanAbsoluteChannelDelta} exceeded 12. " +
                    "Changed ${diff.changedPixels}/${diff.comparedPixels} pixels " +
                    "(${"%.2f".format(diff.changedRatio * 100)}%). Diff preview: ${preview.absolutePath}"
            }
            check(diff.maxChannelDelta < 80) {
                "Max channel delta ${diff.maxChannelDelta} exceeded 80. Diff preview: ${preview.absolutePath}"
            }
        }
    }
}
