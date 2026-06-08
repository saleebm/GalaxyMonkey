package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RectHitTest {

    private val center = Vector2(200f, 100f)
    private val size = Vector2(280f, 48f)

    @Test
    fun `center point is a hit`() {
        assertTrue(HUDController.rectHit(center, size, Vector2(200f, 100f)))
    }

    @Test
    fun `x edge - just inside half-width hits, just outside misses`() {
        assertTrue(HUDController.rectHit(center, size, Vector2(200f + 139f, 100f)))
        assertFalse(HUDController.rectHit(center, size, Vector2(200f + 141f, 100f)))
    }

    @Test
    fun `y edge - just inside half-height hits, just outside misses`() {
        assertTrue(HUDController.rectHit(center, size, Vector2(200f, 100f + 23f)))
        assertFalse(HUDController.rectHit(center, size, Vector2(200f, 100f + 25f)))
    }

    @Test
    fun `far point is a miss`() {
        assertFalse(HUDController.rectHit(center, size, Vector2(500f, 500f)))
    }

    @Test
    fun `toggle pill size - inside hits, outside misses`() {
        val pillCenter = Vector2(50f, 50f)
        val pillSize = Vector2(
            HUDController.TOGGLE_PILL_WIDTH,
            HUDController.TOGGLE_PILL_HEIGHT
        )
        assertTrue(HUDController.rectHit(pillCenter, pillSize, Vector2(50f + 39f, 50f)))
        assertFalse(HUDController.rectHit(pillCenter, pillSize, Vector2(50f + 41f, 50f)))
    }

    @Test
    fun `empty space outside all button rects returns no hit - no-empty-space-dismiss guard`() {
        val pauseCenter = Vector2(400f, 20f)
        val pauseSize = Vector2(HUDController.PAUSE_HIT_WIDTH, HUDController.PAUSE_HIT_HEIGHT)

        val toggleCenter = Vector2(200f, 300f)
        val toggleSize = Vector2(HUDController.TOGGLE_PILL_WIDTH, HUDController.TOGGLE_PILL_HEIGHT)

        val backCenter = Vector2(200f, 400f)
        val backSize = Vector2(HUDController.BACK_PILL_WIDTH, HUDController.BACK_PILL_HEIGHT)

        val emptyPoint = Vector2(600f, 600f)
        assertFalse(HUDController.rectHit(pauseCenter, pauseSize, emptyPoint))
        assertFalse(HUDController.rectHit(toggleCenter, toggleSize, emptyPoint))
        assertFalse(HUDController.rectHit(backCenter, backSize, emptyPoint))
    }

    @Test
    fun `negative offsets - left and below center`() {
        assertTrue(HUDController.rectHit(center, size, Vector2(200f - 139f, 100f)))
        assertFalse(HUDController.rectHit(center, size, Vector2(200f - 141f, 100f)))
        assertTrue(HUDController.rectHit(center, size, Vector2(200f, 100f - 23f)))
        assertFalse(HUDController.rectHit(center, size, Vector2(200f, 100f - 25f)))
    }

    @Test
    fun `exact boundary - at half-width and half-height is a hit`() {
        assertTrue(HUDController.rectHit(center, size, Vector2(200f + 140f, 100f)))
        assertTrue(HUDController.rectHit(center, size, Vector2(200f, 100f + 24f)))
    }
}
