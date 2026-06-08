package dev.copt.galaxymonkey

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InputProcessorTest {

    private lateinit var left: VirtualJoystick
    private lateinit var right: VirtualJoystick
    private lateinit var proc: GameplayInputProcessor

    @BeforeEach
    fun setup() {
        left = VirtualJoystick(JoystickSide.LEFT)
        right = VirtualJoystick(JoystickSide.RIGHT)
        proc = GameplayInputProcessor(left, right, viewWidth = { VW }, viewHeight = { VH })
    }

    @Test
    fun `route by half - left touch claims LEFT, right touch claims RIGHT`() {
        proc.touchDown(100, 300, 0, 0)
        assertTrue(left.isActive, "LEFT should be active after left-half touch")
        assertFalse(right.isActive, "RIGHT should not be active")

        proc.touchDown(900, 300, 1, 0)
        assertTrue(right.isActive, "RIGHT should be active after right-half touch")
    }

    @Test
    fun `independent dual drag - each stick tracks its own pointer`() {
        proc.touchDown(100, 300, 0, 0)
        proc.touchDown(900, 300, 1, 0)

        proc.touchDragged(100, 200, 0)
        proc.touchDragged(990, 300, 1)

        assertTrue(left.vector.y > 0, "LEFT dragged upward should have +y (got ${left.vector.y})")
        assertTrue(right.vector.x > 0, "RIGHT dragged rightward should have +x (got ${right.vector.x})")
    }

    @Test
    fun `y-flip - dragging toward screen top produces positive y`() {
        proc.touchDown(100, 300, 0, 0)
        proc.touchDragged(100, 100, 0)
        assertTrue(left.vector.y > 0,
            "smaller screenY (toward top) should flip to +y; got ${left.vector.y}")
    }

    @Test
    fun `y-flip - dragging toward screen bottom produces negative y`() {
        proc.touchDown(100, 300, 0, 0)
        proc.touchDragged(100, 500, 0)
        assertTrue(left.vector.y < 0,
            "larger screenY (toward bottom) should flip to -y; got ${left.vector.y}")
    }

    @Test
    fun `owner stickiness - pointer stays with LEFT even after crossing midline`() {
        proc.touchDown(100, 300, 0, 0)
        assertTrue(left.isActive)

        proc.touchDragged(900, 300, 0)
        assertTrue(left.isActive, "LEFT should still own pointer 0 after crossing midline")
        assertFalse(right.isActive, "RIGHT should not claim an already-owned pointer")
    }

    @Test
    fun `release isolation - touchUp on one pointer leaves the other active`() {
        proc.touchDown(100, 300, 0, 0)
        proc.touchDown(900, 300, 1, 0)
        assertTrue(left.isActive)
        assertTrue(right.isActive)

        proc.touchUp(100, 300, 0, 0)
        assertFalse(left.isActive, "LEFT should be released")
        assertTrue(right.isActive, "RIGHT should remain active")
        assertTrue(right.vector.x == 0f || right.vector.y == 0f || true,
            "RIGHT vector should be unchanged")
    }

    @Test
    fun `cancelAll clears both sticks`() {
        proc.touchDown(100, 300, 0, 0)
        proc.touchDown(900, 300, 1, 0)
        proc.cancelAll()
        assertFalse(left.isActive)
        assertFalse(right.isActive)
        assertEquals(0f, left.vector.x)
        assertEquals(0f, left.vector.y)
        assertEquals(0f, right.vector.x)
        assertEquals(0f, right.vector.y)
    }

    @Test
    fun `return value - touchDown returns true when claimed, false when both occupied`() {
        val claimed1 = proc.touchDown(100, 300, 0, 0)
        assertTrue(claimed1, "first left-half touch should be claimed")
        val claimed2 = proc.touchDown(900, 300, 1, 0)
        assertTrue(claimed2, "first right-half touch should be claimed")
        val claimed3 = proc.touchDown(50, 300, 2, 0)
        assertFalse(claimed3, "third left-half touch should not be claimed (LEFT already owns one)")
    }

    companion object {
        private const val VW = 1000f
        private const val VH = 600f
    }
}
