package model

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedColorTest {

    @Test
    fun `test SavedColor creation with default values`() {
        val savedColor = SavedColor(
            red = 255,
            green = 128,
            blue = 64
        )
        
        assertEquals(0L, savedColor.uid)
        assertEquals(255, savedColor.red)
        assertEquals(128, savedColor.green)
        assertEquals(64, savedColor.blue)
        assertEquals(1f, savedColor.alpha)
        assertFalse(savedColor.preset)
    }

    @Test
    fun `test SavedColor creation with custom values`() {
        val savedColor = SavedColor(
            uid = 5L,
            red = 200,
            green = 100,
            blue = 50,
            alpha = 0.5f,
            preset = true
        )
        
        assertEquals(5L, savedColor.uid)
        assertEquals(200, savedColor.red)
        assertEquals(100, savedColor.green)
        assertEquals(50, savedColor.blue)
        assertEquals(0.5f, savedColor.alpha)
        assertTrue(savedColor.preset)
    }

    @Test
    fun `test SavedColor toColor conversion`() {
        val savedColor = SavedColor(
            red = 255,
            green = 128,
            blue = 64,
            alpha = 0.8f
        )
        
        val color = savedColor.toColor()
        
        assertEquals(1f, color.red, 0.01f)
        assertEquals(128f / 255f, color.green, 0.01f)
        assertEquals(64f / 255f, color.blue, 0.01f)
        assertEquals(0.8f, color.alpha, 0.01f)
    }

    @Test
    fun `test SavedColor toColor with black`() {
        val savedColor = SavedColor(
            red = 0,
            green = 0,
            blue = 0,
            alpha = 1f
        )
        
        val color = savedColor.toColor()
        
        assertEquals(0f, color.red, 0.01f)
        assertEquals(0f, color.green, 0.01f)
        assertEquals(0f, color.blue, 0.01f)
        assertEquals(1f, color.alpha, 0.01f)
    }

    @Test
    fun `test SavedColor toColor with white`() {
        val savedColor = SavedColor(
            red = 255,
            green = 255,
            blue = 255,
            alpha = 1f
        )
        
        val color = savedColor.toColor()
        
        assertEquals(1f, color.red, 0.01f)
        assertEquals(1f, color.green, 0.01f)
        assertEquals(1f, color.blue, 0.01f)
        assertEquals(1f, color.alpha, 0.01f)
    }

    @Test
    fun `test Color toSavedColor conversion`() {
        val color = Color(1f, 0.5f, 0.25f, 0.75f)
        
        val savedColor = color.toSavedColor()
        
        assertEquals(0L, savedColor.uid)
        assertEquals(255, savedColor.red)
        assertEquals(128, savedColor.green) // 0.5 * 255 in Compose = 128
        assertEquals(64, savedColor.blue) // 0.25 * 255 in Compose = 64
        assertEquals(0.75f, savedColor.alpha, 0.01f) // Allow small tolerance for rounding
        assertFalse(savedColor.preset)
    }

    @Test
    fun `test Color toSavedColor with custom uid and preset`() {
        val color = Color.Red
        
        val savedColor = color.toSavedColor(uid = 10L, preset = true)
        
        assertEquals(10L, savedColor.uid)
        assertEquals(255, savedColor.red)
        assertEquals(0, savedColor.green)
        assertEquals(0, savedColor.blue)
        assertEquals(1f, savedColor.alpha)
        assertTrue(savedColor.preset)
    }

    @Test
    fun `test round trip conversion from SavedColor to Color and back`() {
        val originalSavedColor = SavedColor(
            uid = 15L,
            red = 200,
            green = 150,
            blue = 100,
            alpha = 0.9f,
            preset = true
        )
        
        val color = originalSavedColor.toColor()
        val reconvertedSavedColor = color.toSavedColor(uid = 15L, preset = true)
        
        // Allow for small rounding differences
        assertEquals(originalSavedColor.red, reconvertedSavedColor.red, "Red component mismatch")
        assertEquals(originalSavedColor.green, reconvertedSavedColor.green, "Green component mismatch")
        assertEquals(originalSavedColor.blue, reconvertedSavedColor.blue, "Blue component mismatch")
        assertEquals(originalSavedColor.alpha, reconvertedSavedColor.alpha, 0.01f)
    }

    @Test
    fun `test findColor returns correct color for matching uid`() {
        val colors = listOf(
            SavedColor(uid = 1L, red = 255, green = 0, blue = 0),
            SavedColor(uid = 2L, red = 0, green = 255, blue = 0),
            SavedColor(uid = 3L, red = 0, green = 0, blue = 255)
        )
        
        val foundColor = colors.findColor(2L)
        
        assertEquals(0f, foundColor.red, 0.01f)
        assertEquals(1f, foundColor.green, 0.01f)
        assertEquals(0f, foundColor.blue, 0.01f)
    }

    @Test
    fun `test findColor returns transparent for non-matching uid`() {
        val colors = listOf(
            SavedColor(uid = 1L, red = 255, green = 0, blue = 0),
            SavedColor(uid = 2L, red = 0, green = 255, blue = 0)
        )
        
        val foundColor = colors.findColor(99L)
        
        assertEquals(Color.Transparent, foundColor)
    }

    @Test
    fun `test findColor with empty list returns transparent`() {
        val colors = emptyList<SavedColor>()
        
        val foundColor = colors.findColor(1L)
        
        assertEquals(Color.Transparent, foundColor)
    }

    @Test
    fun `test SavedColor with transparent alpha`() {
        val savedColor = SavedColor(
            red = 100,
            green = 100,
            blue = 100,
            alpha = 0f
        )
        
        val color = savedColor.toColor()
        
        assertEquals(0f, color.alpha)
    }

    @Test
    fun `test common colors conversion`() {
        val testColors = listOf(
            Color.Red to SavedColor(red = 255, green = 0, blue = 0),
            Color.Green to SavedColor(red = 0, green = 255, blue = 0),
            Color.Blue to SavedColor(red = 0, green = 0, blue = 255),
            Color.White to SavedColor(red = 255, green = 255, blue = 255),
            Color.Black to SavedColor(red = 0, green = 0, blue = 0)
        )
        
        testColors.forEach { (color, expectedSavedColor) ->
            val savedColor = color.toSavedColor()
            assertEquals(expectedSavedColor.red, savedColor.red)
            assertEquals(expectedSavedColor.green, savedColor.green)
            assertEquals(expectedSavedColor.blue, savedColor.blue)
        }
    }
}
