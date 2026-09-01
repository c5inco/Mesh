package des.c5inco.mesh.testing

import dev.sebastiano.spectre.core.ComposeAutomator
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun screenshotOutputDir(): File {
    val configured = System.getProperty("mesh.screenshot.dir")
    val dir = if (!configured.isNullOrBlank()) {
        File(configured)
    } else {
        File("build/spectre-screenshots")
    }
    dir.mkdirs()
    return dir
}

fun BufferedImage.writePng(destination: File) {
    destination.parentFile?.mkdirs()
    check(ImageIO.write(this, "png", destination)) {
        "Failed to write PNG to ${destination.absolutePath}"
    }
}

suspend fun ComposeAutomator.captureWindowPng(fileName: String): File {
    waitForTestTag(MeshTestTags.GRADIENT_CANVAS)
    waitForVisualIdle()
    val destination = File(screenshotOutputDir(), fileName)
    screenshot().writePng(destination)
    return destination
}

fun BufferedImage.crop(x: Int, y: Int, width: Int, height: Int): BufferedImage {
    val safeX = x.coerceIn(0, this.width)
    val safeY = y.coerceIn(0, this.height)
    val safeWidth = width.coerceAtMost(this.width - safeX).coerceAtLeast(1)
    val safeHeight = height.coerceAtMost(this.height - safeY).coerceAtLeast(1)
    return getSubimage(safeX, safeY, safeWidth, safeHeight)
}

data class ImageDiff(
    val width: Int,
    val height: Int,
    val comparedPixels: Int,
    val changedPixels: Int,
    val meanAbsoluteChannelDelta: Double,
    val maxChannelDelta: Int,
) {
    val changedRatio: Double
        get() = if (comparedPixels == 0) 0.0 else changedPixels.toDouble() / comparedPixels
}

fun diffImages(expected: BufferedImage, actual: BufferedImage): ImageDiff {
    val width = minOf(expected.width, actual.width)
    val height = minOf(expected.height, actual.height)
    var changed = 0
    var totalDelta = 0L
    var maxDelta = 0
    val compared = width * height

    for (y in 0 until height) {
        for (x in 0 until width) {
            val expectedPixel = expected.getRGB(x, y)
            val actualPixel = actual.getRGB(x, y)
            val delta = channelDelta(expectedPixel, actualPixel)
            totalDelta += delta
            if (delta > maxDelta) maxDelta = delta
            if (delta > 0) changed++
        }
    }

    return ImageDiff(
        width = width,
        height = height,
        comparedPixels = compared,
        changedPixels = changed,
        meanAbsoluteChannelDelta = if (compared == 0) 0.0 else totalDelta.toDouble() / compared,
        maxChannelDelta = maxDelta,
    )
}

fun writeDiffPreview(expected: BufferedImage, actual: BufferedImage, destination: File): File {
    val width = minOf(expected.width, actual.width)
    val height = minOf(expected.height, actual.height)
    val preview = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val expectedPixel = expected.getRGB(x, y)
            val actualPixel = actual.getRGB(x, y)
            preview.setRGB(
                x,
                y,
                if (expectedPixel == actualPixel) {
                    grayscale(expectedPixel)
                } else {
                    0xFFFF00FF.toInt()
                },
            )
        }
    }
    preview.writePng(destination)
    return destination
}

private fun channelDelta(expected: Int, actual: Int): Int {
    val alpha = kotlin.math.abs(((expected ushr 24) and 0xFF) - ((actual ushr 24) and 0xFF))
    val red = kotlin.math.abs(((expected ushr 16) and 0xFF) - ((actual ushr 16) and 0xFF))
    val green = kotlin.math.abs(((expected ushr 8) and 0xFF) - ((actual ushr 8) and 0xFF))
    val blue = kotlin.math.abs((expected and 0xFF) - (actual and 0xFF))
    return maxOf(alpha, red, green, blue)
}

private fun grayscale(pixel: Int): Int {
    val red = (pixel ushr 16) and 0xFF
    val green = (pixel ushr 8) and 0xFF
    val blue = pixel and 0xFF
    val gray = ((red * 30 + green * 59 + blue * 11) / 100).coerceIn(0, 255)
    return (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
}
