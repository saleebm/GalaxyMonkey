package dev.copt.galaxymonkey

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class VirtualJoystickTest {

    private val R = Tuning.Joystick.radius
    private val DZ = Tuning.Joystick.deadZone
    private val VW = 1000f

    private fun mag(x: Float, y: Float) = sqrt(x * x + y * y)

    @Test
    fun `side claim - LEFT claims left half, rejects right`() {
        val left = VirtualJoystick(JoystickSide.LEFT)
        assertTrue(left.begin(0, 100f, 500f, VW))
        assertTrue(left.isActive)

        val left2 = VirtualJoystick(JoystickSide.LEFT)
        assertFalse(left2.begin(0, 900f, 500f, VW))
        assertFalse(left2.isActive)
    }

    @Test
    fun `side claim - RIGHT claims right half, rejects left`() {
        val right = VirtualJoystick(JoystickSide.RIGHT)
        assertTrue(right.begin(0, 900f, 500f, VW))
        assertTrue(right.isActive)

        val right2 = VirtualJoystick(JoystickSide.RIGHT)
        assertFalse(right2.begin(0, 100f, 500f, VW))
    }

    @Test
    fun `single owner - second pointer rejected while first active`() {
        val js = VirtualJoystick(JoystickSide.LEFT)
        assertTrue(js.begin(0, 200f, 500f, VW))
        assertFalse(js.begin(1, 300f, 500f, VW), "should reject while pointer 0 tracked")
    }

    @Test
    fun `dead zone - inside dead zone vector is zero`() {
        val js = VirtualJoystick(JoystickSide.LEFT)
        js.begin(0, 200f, 500f, VW)
        val dist = DZ * R * 0.8f
        js.moved(0, 200f + dist, 500f)
        assertEquals(0f, js.vector.x, 1e-4f, "inside dead zone x should be 0")
        assertEquals(0f, js.vector.y, 1e-4f, "inside dead zone y should be 0")
    }

    @Test
    fun `dead zone boundary - at dead zone edge vector is near zero`() {
        val js = VirtualJoystick(JoystickSide.LEFT)
        js.begin(0, 200f, 500f, VW)
        js.moved(0, 200f + DZ * R, 500f)
        assertTrue(mag(js.vector.x, js.vector.y) < 0.01f, "at dead zone edge magnitude should be ~0")
    }

    @Test
    fun `full deflection - at baseRadius vector magnitude is 1`() {
        val js = VirtualJoystick(JoystickSide.LEFT)
        js.begin(0, 200f, 500f, VW)
        js.moved(0, 200f + R, 500f)
        val m = mag(js.vector.x, js.vector.y)
        assertEquals(1f, m, 1e-4f, "full deflection magnitude should be 1.0, got $m")
    }

    @Test
    fun `remap linearity - mid-range stick maps to ~0_5 magnitude`() {
        val js = VirtualJoystick(JoystickSide.LEFT)
        js.begin(0, 200f, 500f, VW)
        val targetMag = DZ + 0.5f * (1f - DZ)
        js.moved(0, 200f + targetMag * R, 500f)
        val m = mag(js.vector.x, js.vector.y)
        assertTrue(abs(m - 0.5f) < 0.01f,
            "at mag=${targetMag} expected outMag~0.5 got $m")
    }

    @Test
    fun `clamp - beyond baseRadius thumbOffset clamped, vector is 1`() {
        val js = VirtualJoystick(JoystickSide.LEFT)
        js.begin(0, 200f, 500f, VW)
        js.moved(0, 200f + 2 * R, 500f)
        val thumbMag = mag(js.thumbOffset.x, js.thumbOffset.y)
        assertEquals(R, thumbMag, 0.01f, "thumbOffset should clamp to baseRadius")
        val vecMag = mag(js.vector.x, js.vector.y)
        assertEquals(1f, vecMag, 1e-4f, "vector magnitude should be 1.0 when clamped")
    }

    @Test
    fun `direction - pure x yields positive x vector`() {
        val js = VirtualJoystick(JoystickSide.LEFT)
        js.begin(0, 200f, 500f, VW)
        js.moved(0, 200f + R, 500f)
        assertTrue(js.vector.x > 0.99f, "pure +x should yield positive x: ${js.vector.x}")
        assertTrue(abs(js.vector.y) < 0.01f, "pure +x should have ~0 y: ${js.vector.y}")
    }

    @Test
    fun `direction - pure y yields positive y vector`() {
        val js = VirtualJoystick(JoystickSide.LEFT)
        js.begin(0, 200f, 500f, VW)
        js.moved(0, 200f, 500f + R)
        assertTrue(abs(js.vector.x) < 0.01f)
        assertTrue(js.vector.y > 0.99f)
    }

    @Test
    fun `release - ended zeros vector and deactivates`() {
        val js = VirtualJoystick(JoystickSide.LEFT)
        js.begin(0, 200f, 500f, VW)
        js.moved(0, 200f + R, 500f)
        js.ended(0)
        assertFalse(js.isActive)
        assertEquals(0f, js.vector.x, 1e-6f)
        assertEquals(0f, js.vector.y, 1e-6f)
    }

    @Test
    fun `release wrong pointer - no-op`() {
        val js = VirtualJoystick(JoystickSide.LEFT)
        js.begin(0, 200f, 500f, VW)
        js.moved(0, 200f + R, 500f)
        js.ended(1)
        assertTrue(js.isActive, "wrong pointer should not deactivate")
        assertTrue(js.vector.x > 0.5f, "vector should not change")
    }

    @Test
    fun `cancelAll zeros and deactivates`() {
        val js = VirtualJoystick(JoystickSide.LEFT)
        js.begin(0, 200f, 500f, VW)
        js.moved(0, 200f + R, 500f)
        js.cancelAll()
        assertFalse(js.isActive)
        assertEquals(0f, js.vector.x, 1e-6f)
    }
}
