package des.c5inco.mesh.common

import androidx.compose.ui.graphics.Color
import java.math.RoundingMode

fun Color.toHexStringNoHash(includeAlpha: Boolean = false): String {
    return if (includeAlpha) {
        String.format(
            "%02X%02X%02X%02X",
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    } else {
        String.format(
            "%02X%02X%02X",
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}

fun String.toColor(): Color {
    var processedColorString = this.trim()

    // Remove the '#' if it exists
    if (processedColorString.startsWith("#")) {
        processedColorString = processedColorString.substring(1)
    }

    // Check for valid length
    if (processedColorString.length != 6 && processedColorString.length != 8) {
        throw IllegalArgumentException("Invalid color string: $this")
    }

    // Parse the hex value
    val colorLong = processedColorString.toLongOrNull(16)
        ?: throw IllegalArgumentException("Invalid color string: $this")

    // Extract components
    val alpha = if (processedColorString.length == 8) {
        (colorLong shr 24 and 0xFF).toFloat() / 255f
    } else {
        1.0f // Default alpha to 1.0 if not provided
    }
    val red = (colorLong shr 16 and 0xFF).toFloat() / 255f
    val green = (colorLong shr 8 and 0xFF).toFloat() / 255f
    val blue = (colorLong and 0xFF).toFloat() / 255f

    return Color(red, green, blue, alpha)
}

fun formatFloat(number: Float): String {
    return number.toBigDecimal().setScale(4, RoundingMode.UP).stripTrailingZeros().toPlainString()
}
