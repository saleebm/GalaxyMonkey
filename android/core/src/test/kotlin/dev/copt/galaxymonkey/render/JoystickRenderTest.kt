package dev.copt.galaxymonkey.render

import dev.copt.galaxymonkey.JoystickSide
import dev.copt.galaxymonkey.Tuning
import dev.copt.galaxymonkey.VirtualJoystick
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class JoystickRenderTest {

    private val viewW = 800f

    @Test
    fun `hidden when idle - isActive false for both sticks`() {
        val left = VirtualJoystick(JoystickSide.LEFT)
        val right = VirtualJoystick(JoystickSide.RIGHT)
        assertFalse(left.isActive, "left stick should be inactive with no touch")
        assertFalse(right.isActive, "right stick should be inactive with no touch")
        println("[JoystickRenderTest] hidden when idle: left.isActive=${left.isActive} right.isActive=${right.isActive}")
    }

    @Test
    fun `visible on claim - isActive true and position equals touch-down`() {
        val left = VirtualJoystick(JoystickSide.LEFT)
        val touchX = 100f
        val touchY = 200f
        left.begin(0, touchX, touchY, viewW)
        assertTrue(left.isActive, "left stick should be active after begin()")
        assertEquals(touchX, left.position.x, 1e-5f,
            "position.x should equal touch-down x=$touchX")
        assertEquals(touchY, left.position.y, 1e-5f,
            "position.y should equal touch-down y=$touchY")
        println("[JoystickRenderTest] visible on claim: isActive=${left.isActive} draw center=(${left.position.x},${left.position.y})")
    }

    @Test
    fun `thumb tracks - draw center equals position plus thumbOffset`() {
        val left = VirtualJoystick(JoystickSide.LEFT)
        left.begin(0, 100f, 200f, viewW)
        left.moved(0, 150f, 250f)

        val thumbDrawX = left.position.x + left.thumbOffset.x
        val thumbDrawY = left.position.y + left.thumbOffset.y

        val fingerDx = 150f - 100f
        val fingerDy = 250f - 200f
        val fingerDist = sqrt(fingerDx * fingerDx + fingerDy * fingerDy)
        val clampedDist = minOf(fingerDist, Tuning.Joystick.radius)
        val expectedOffsetMag = clampedDist

        val actualMag = sqrt(left.thumbOffset.x * left.thumbOffset.x +
            left.thumbOffset.y * left.thumbOffset.y)
        assertEquals(expectedOffsetMag, actualMag, 0.1f,
            "thumbOffset magnitude should be clamped finger distance")
        assertTrue(actualMag <= Tuning.Joystick.radius + 0.01f,
            "thumbOffset magnitude should never exceed baseRadius=${Tuning.Joystick.radius}")
        println("[JoystickRenderTest] thumb tracks: thumbOffset magnitude=%.2f draw center=(%.1f,%.1f)".format(
            actualMag, thumbDrawX, thumbDrawY))
    }

    @Test
    fun `thumb offset clamped to baseRadius even at extreme distance`() {
        val left = VirtualJoystick(JoystickSide.LEFT)
        left.begin(0, 100f, 200f, viewW)
        left.moved(0, 500f, 200f) // 400px away, way beyond radius

        val mag = sqrt(left.thumbOffset.x * left.thumbOffset.x +
            left.thumbOffset.y * left.thumbOffset.y)
        assertEquals(Tuning.Joystick.radius, mag, 0.1f,
            "thumbOffset should clamp at baseRadius=${Tuning.Joystick.radius}")
    }

    @Test
    fun `dual visible - both sticks active with distinct anchors`() {
        val left = VirtualJoystick(JoystickSide.LEFT)
        val right = VirtualJoystick(JoystickSide.RIGHT)

        left.begin(0, 100f, 300f, viewW)
        right.begin(1, 600f, 300f, viewW)

        assertTrue(left.isActive, "left should be active")
        assertTrue(right.isActive, "right should be active")
        assertNotEquals(left.position.x, right.position.x,
            "anchors should be distinct")
        println("[JoystickRenderTest] dual visible: left=(${left.position.x},${left.position.y}) right=(${right.position.x},${right.position.y})")
    }

    @Test
    fun `hide on release - isActive false after ended`() {
        val left = VirtualJoystick(JoystickSide.LEFT)
        left.begin(0, 100f, 200f, viewW)
        assertTrue(left.isActive)
        left.ended(0)
        assertFalse(left.isActive, "stick should be inactive after ended()")
        println("[JoystickRenderTest] hide on release: isActive=${left.isActive}")
    }

    @Test
    fun `alpha constants wired from Tuning`() {
        assertEquals(0.08f, Tuning.Joystick.baseAlpha, 1e-5f,
            "baseAlpha should be 0.08")
        assertEquals(0.18f, Tuning.Joystick.strokeAlpha, 1e-5f,
            "strokeAlpha should be 0.18")
        assertEquals(0.14f, Tuning.Joystick.thumbAlpha, 1e-5f,
            "thumbAlpha should be 0.14")
        println("[JoystickRenderTest] alpha constants: base=${Tuning.Joystick.baseAlpha} stroke=${Tuning.Joystick.strokeAlpha} thumb=${Tuning.Joystick.thumbAlpha}")
    }
}
