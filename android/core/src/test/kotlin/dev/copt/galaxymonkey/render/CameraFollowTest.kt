package dev.copt.galaxymonkey.render

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class CameraFollowTest {

    private val deadzone = 36f
    private val lerpPerSec = 6f

    @Test
    fun `no movement inside deadzone`() {
        val cf = CameraFollow()
        cf.update(1f / 60f, 10f, 10f)
        assertEquals(0f, cf.cameraX, 1e-5f)
        assertEquals(0f, cf.cameraY, 1e-5f)
        println("CameraFollow: target at (10,10), dist=${sqrt(200f)} < deadzone=$deadzone, camera stayed at origin")
    }

    @Test
    fun `camera moves when target exceeds deadzone`() {
        val cf = CameraFollow()
        cf.update(1f / 60f, 100f, 0f)
        assertTrue(cf.cameraX > 0f, "camera should move toward target, got ${cf.cameraX}")
        assertTrue(cf.cameraX < 100f, "camera should not overshoot target, got ${cf.cameraX}")
        println("CameraFollow: target at (100,0), camera moved to (${cf.cameraX}, ${cf.cameraY})")
    }

    @Test
    fun `camera converges on target over many frames`() {
        val cf = CameraFollow()
        for (i in 0 until 300) {
            cf.update(1f / 60f, 200f, 150f)
        }
        assertTrue(abs(cf.cameraX - 200f) < deadzone + 1f,
            "camera should converge near target, got (${cf.cameraX}, ${cf.cameraY})")
    }

    @Test
    fun `camera never overshoots`() {
        val cf = CameraFollow()
        val target = 100f
        for (i in 0 until 600) {
            cf.update(1f / 60f, target, 0f)
            assertTrue(cf.cameraX <= target,
                "camera should never overshoot: frame $i cameraX=${cf.cameraX}")
        }
    }

    @Test
    fun `cameraDelta tracks movement`() {
        val cf = CameraFollow()
        cf.update(1f / 60f, 100f, 0f)
        val dx = cf.cameraDeltaX
        assertTrue(dx > 0f, "cameraDelta should be positive when moving right")
        assertEquals(cf.cameraX - cf.lastCameraX, dx, 1e-5f)
    }

    @Test
    fun `snapTo sets position with zero delta`() {
        val cf = CameraFollow()
        cf.snapTo(50f, 75f)
        assertEquals(50f, cf.cameraX, 1e-5f)
        assertEquals(75f, cf.cameraY, 1e-5f)
        assertEquals(0f, cf.cameraDeltaX, 1e-5f)
        assertEquals(0f, cf.cameraDeltaY, 1e-5f)
    }
}
