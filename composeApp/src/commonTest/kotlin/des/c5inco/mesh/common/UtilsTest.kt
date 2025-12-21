package des.c5inco.mesh.common

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UtilsTest {

    @Test
    fun `test toHexStringNoHash without alpha`() {
        val color = Color(red = 1f, green = 0.5f, blue = 0.25f, alpha = 1f)
        val hex = color.toHexStringNoHash(includeAlpha = false)
        
        // Actual values from Compose Color: red=255, green=128, blue=64
        assertEquals("FF8040", hex)
    }

    @Test
    fun `test toHexStringNoHash with alpha`() {
        val color = Color(red = 1f, green = 0.5f, blue = 0.25f, alpha = 0.5f)
        val hex = color.toHexStringNoHash(includeAlpha = true)
        
        // Format is AARRGGBB (alpha first)
        // Actual values: alpha=128, red=255, green=128, blue=64
        assertEquals("80FF8040", hex)
    }

    @Test
    fun `test toHexStringNoHash with red color`() {
        val color = Color.Red
        val hex = color.toHexStringNoHash(includeAlpha = false)
        
        assertEquals("FF0000", hex)
    }

    @Test
    fun `test toHexStringNoHash with green color`() {
        val color = Color.Green
        val hex = color.toHexStringNoHash(includeAlpha = false)
        
        assertEquals("00FF00", hex)
    }

    @Test
    fun `test toHexStringNoHash with blue color`() {
        val color = Color.Blue
        val hex = color.toHexStringNoHash(includeAlpha = false)
        
        assertEquals("0000FF", hex)
    }

    @Test
    fun `test toHexStringNoHash with white color`() {
        val color = Color.White
        val hex = color.toHexStringNoHash(includeAlpha = false)
        
        assertEquals("FFFFFF", hex)
    }

    @Test
    fun `test toHexStringNoHash with black color`() {
        val color = Color.Black
        val hex = color.toHexStringNoHash(includeAlpha = false)
        
        assertEquals("000000", hex)
    }

    @Test
    fun `test toColor from hex string without hash`() {
        val hexString = "FF8040"
        val color = hexString.toColor()
        
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0.502f, color.green, 0.01f)
        assertEquals(0.251f, color.blue, 0.01f)
        assertEquals(1f, color.alpha, 0.01f)
    }

    @Test
    fun `test toColor from hex string with hash`() {
        val hexString = "#FF8040"
        val color = hexString.toColor()
        
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0.502f, color.green, 0.01f)
        assertEquals(0.251f, color.blue, 0.01f)
        assertEquals(1f, color.alpha, 0.01f)
    }

    @Test
    fun `test toColor from hex string with alpha`() {
        val hexString = "80FF8040"
        val color = hexString.toColor()
        
        assertEquals(0.502f, color.alpha, 0.01f)
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0.502f, color.green, 0.01f)
        assertEquals(0.251f, color.blue, 0.01f)
    }

    @Test
    fun `test toColor from hex string with hash and alpha`() {
        val hexString = "#80FF8040"
        val color = hexString.toColor()
        
        assertEquals(0.502f, color.alpha, 0.01f)
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0.502f, color.green, 0.01f)
        assertEquals(0.251f, color.blue, 0.01f)
    }

    @Test
    fun `test toColor from hex string with whitespace`() {
        val hexString = "  FF8040  "
        val color = hexString.toColor()
        
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0.502f, color.green, 0.01f)
        assertEquals(0.251f, color.blue, 0.01f)
    }

    @Test
    fun `test toColor with red`() {
        val hexString = "FF0000"
        val color = hexString.toColor()
        
        assertEquals(1f, color.red, 0.01f)
        assertEquals(0f, color.green, 0.01f)
        assertEquals(0f, color.blue, 0.01f)
        assertEquals(1f, color.alpha, 0.01f)
    }

    @Test
    fun `test toColor with green`() {
        val hexString = "00FF00"
        val color = hexString.toColor()
        
        assertEquals(0f, color.red, 0.01f)
        assertEquals(1f, color.green, 0.01f)
        assertEquals(0f, color.blue, 0.01f)
        assertEquals(1f, color.alpha, 0.01f)
    }

    @Test
    fun `test toColor with blue`() {
        val hexString = "0000FF"
        val color = hexString.toColor()
        
        assertEquals(0f, color.red, 0.01f)
        assertEquals(0f, color.green, 0.01f)
        assertEquals(1f, color.blue, 0.01f)
        assertEquals(1f, color.alpha, 0.01f)
    }

    @Test
    fun `test toColor with white`() {
        val hexString = "FFFFFF"
        val color = hexString.toColor()
        
        assertEquals(1f, color.red, 0.01f)
        assertEquals(1f, color.green, 0.01f)
        assertEquals(1f, color.blue, 0.01f)
        assertEquals(1f, color.alpha, 0.01f)
    }

    @Test
    fun `test toColor with black`() {
        val hexString = "000000"
        val color = hexString.toColor()
        
        assertEquals(0f, color.red, 0.01f)
        assertEquals(0f, color.green, 0.01f)
        assertEquals(0f, color.blue, 0.01f)
        assertEquals(1f, color.alpha, 0.01f)
    }

    @Test
    fun `test toColor throws exception for invalid length`() {
        assertFailsWith<IllegalArgumentException> {
            "FF0".toColor()
        }
    }

    @Test
    fun `test toColor throws exception for invalid characters`() {
        assertFailsWith<IllegalArgumentException> {
            "GGHHII".toColor()
        }
    }

    @Test
    fun `test toColor throws exception for empty string`() {
        assertFailsWith<IllegalArgumentException> {
            "".toColor()
        }
    }

    @Test
    fun `test round trip conversion from Color to hex and back`() {
        val originalColor = Color(red = 0.75f, green = 0.5f, blue = 0.25f, alpha = 1f)
        
        val hex = originalColor.toHexStringNoHash(includeAlpha = false)
        val reconvertedColor = hex.toColor()
        
        assertEquals(originalColor.red, reconvertedColor.red, 0.01f)
        assertEquals(originalColor.green, reconvertedColor.green, 0.01f)
        assertEquals(originalColor.blue, reconvertedColor.blue, 0.01f)
        assertEquals(1f, reconvertedColor.alpha, 0.01f)
    }

    @Test
    fun `test round trip conversion with alpha`() {
        val originalColor = Color(red = 0.75f, green = 0.5f, blue = 0.25f, alpha = 0.6f)
        
        val hex = originalColor.toHexStringNoHash(includeAlpha = true)
        val reconvertedColor = hex.toColor()
        
        assertEquals(originalColor.red, reconvertedColor.red, 0.01f)
        assertEquals(originalColor.green, reconvertedColor.green, 0.01f)
        assertEquals(originalColor.blue, reconvertedColor.blue, 0.01f)
        assertEquals(originalColor.alpha, reconvertedColor.alpha, 0.01f)
    }

    @Test
    fun `test formatFloat with integer value`() {
        val result = formatFloat(5f)
        assertEquals("5", result)
    }

    @Test
    fun `test formatFloat with decimal value`() {
        val result = formatFloat(3.14159f)
        assertEquals("3.1416", result)
    }

    @Test
    fun `test formatFloat with small decimal`() {
        val result = formatFloat(0.5f)
        assertEquals("0.5", result)
    }

    @Test
    fun `test formatFloat strips trailing zeros`() {
        val result = formatFloat(2.5000f)
        assertEquals("2.5", result)
    }

    @Test
    fun `test formatFloat with zero`() {
        val result = formatFloat(0f)
        assertEquals("0", result)
    }

    @Test
    fun `test formatFloat with negative value`() {
        val result = formatFloat(-3.14159f)
        assertEquals("-3.1416", result)
    }

    @Test
    fun `test formatFloat with very small value`() {
        val result = formatFloat(0.0001f)
        assertEquals("0.0001", result)
    }

    @Test
    fun `test formatFloat with large value`() {
        val result = formatFloat(1234.5678f)
        // RoundingMode.UP rounds towards positive infinity
        assertEquals("1234.5677", result)
    }

    @Test
    fun `test formatFloat precision`() {
        val result = formatFloat(1.23456789f)
        // Should be rounded UP to 4 decimal places
        assertEquals("1.2346", result)
    }

    @Test
    fun `test toColor case insensitive`() {
        val lowerCase = "ff7f3f".toColor()
        val upperCase = "FF7F3F".toColor()
        val mixedCase = "Ff7F3f".toColor()
        
        assertEquals(upperCase.red, lowerCase.red, 0.01f)
        assertEquals(upperCase.green, lowerCase.green, 0.01f)
        assertEquals(upperCase.blue, lowerCase.blue, 0.01f)
        
        assertEquals(upperCase.red, mixedCase.red, 0.01f)
        assertEquals(upperCase.green, mixedCase.green, 0.01f)
        assertEquals(upperCase.blue, mixedCase.blue, 0.01f)
    }
}
