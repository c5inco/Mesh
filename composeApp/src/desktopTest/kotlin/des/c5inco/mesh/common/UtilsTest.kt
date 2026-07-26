package des.c5inco.mesh.common

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UtilsTest {
    @Test
    fun `formats colors using the existing channel order`() {
        val color = Color(red = 0x12 / 255f, green = 0x34 / 255f, blue = 0x56 / 255f, alpha = 0x78 / 255f)

        assertEquals("123456", color.toHexStringNoHash())
        assertEquals("78123456", color.toHexStringNoHash(includeAlpha = true))
    }

    @Test
    fun `parses rgb and argb colors`() {
        assertEquals(Color(0x12, 0x34, 0x56), "#123456".toColor())
        assertEquals(Color(0x12, 0x34, 0x56, 0x78), "78123456".toColor())
    }

    @Test
    fun `rounds floats away from zero and strips trailing zeroes`() {
        assertEquals("1.2346", formatFloat(1.23451f))
        assertEquals("-1.2346", formatFloat(-1.23451f))
        assertEquals("1.2", formatFloat(1.2f))
        assertEquals("0", formatFloat(0f))
    }
}
