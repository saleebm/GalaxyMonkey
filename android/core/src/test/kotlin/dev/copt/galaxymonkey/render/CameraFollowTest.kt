package dev.copt.galaxymonkey.render

import dev.copt.galaxymonkey.Tuning
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.min
import kotlin.math.sqrt

class CameraFollowTest {

    private val dz = Tuning.Camera.deadzoneRadius
    private val lerp = Tuning.Camera.followLerpPerSec

    @Test
    fun `target inside deadzone - camera does not move`() {
        val cam = CameraFollow()
        cam.snapTo(100f, 100f)
        cam.update(1f / 60f, 100f + dz - 1f, 100f)
        assertEquals(100f, cam.cameraX, 1e-5f, "cameraX should not move inside deadzone")
        assertEquals(100f, cam.cameraY, 1e-5f, "cameraY should not move inside deadzone")
        assertEquals(0f, cam.cameraDeltaX, 1e-5f)
        assertEquals(0f, cam.cameraDeltaY, 1e-5f)
    }

    @Test
    fun `target exactly at deadzone boundary - no movement`() {
        val cam = CameraFollow()
        cam.snapTo(0f, 0f)
        cam.update(1f / 60f, dz, 0f)
        assertEquals(0f, cam.cameraX, 1e-5f)
    }

    @Test
    fun `target far away - camera moves by documented fraction`() {
        val cam = CameraFollow()
        cam.snapTo(0f, 0f)
        val targetX = 200f
        val dt = 1f / 60f

        cam.update(dt, targetX, 0f)

        val dist = 200f
        val expectedPull = (dist - dz) / dist * min(1f, dt * lerp)
        val expectedX = dist * expectedPull
        assertEquals(expectedX, cam.cameraX, 1e-3f,
            "camera should move by (dist-dead)/dist * min(1, dt*lerp)")
    }

    @Test
    fun `no overshoot even with large dt`() {
        val cam = CameraFollow()
        cam.snapTo(0f, 0f)
        val targetX = 100f

        for (i in 0 until 600) {
            val prevDist = sqrt((targetX - cam.cameraX) * (targetX - cam.cameraX) +
                cam.cameraY * cam.cameraY)
            cam.update(1.0f, targetX, 0f)
            val newDist = sqrt((targetX - cam.cameraX) * (targetX - cam.cameraX) +
                cam.cameraY * cam.cameraY)
            assertTrue(newDist <= prevDist + 1e-5f,
                "distance should never increase (overshoot): step $i prev=$prevDist new=$newDist")
        }
    }

    @Test
    fun `cameraDelta equals new minus old for parallax`() {
        val cam = CameraFollow()
        cam.snapTo(0f, 0f)
        cam.update(1f / 60f, 200f, 150f)
        assertEquals(cam.cameraX - cam.lastCameraX, cam.cameraDeltaX, 1e-5f)
        assertEquals(cam.cameraY - cam.lastCameraY, cam.cameraDeltaY, 1e-5f)
    }

    @Test
    fun `camera converges near target over many frames`() {
        val cam = CameraFollow()
        cam.snapTo(0f, 0f)
        val dt = 1f / 60f
        for (i in 0 until 600) cam.update(dt, 500f, 300f)
        val finalDist = sqrt((500f - cam.cameraX) * (500f - cam.cameraX) +
            (300f - cam.cameraY) * (300f - cam.cameraY))
        assertTrue(finalDist <= dz + 1f,
            "camera should converge to within deadzone, got dist=$finalDist")
    }

    @Test
    fun `snapTo sets position and zeroes delta`() {
        val cam = CameraFollow()
        cam.snapTo(42f, 99f)
        assertEquals(42f, cam.cameraX, 1e-5f)
        assertEquals(99f, cam.cameraY, 1e-5f)
        assertEquals(0f, cam.cameraDeltaX, 1e-5f)
        assertEquals(0f, cam.cameraDeltaY, 1e-5f)
    }

    @Test
    fun `camera does not move on first frame from origin`() {
        val cam = CameraFollow()
        cam.update(1f / 60f, 10f, 10f)
        val dist = sqrt(200f)
        assertTrue(dist < dz, "sanity: (10,10) is inside deadzone")
        assertEquals(0f, cam.cameraX, 1e-5f)
        assertEquals(0f, cam.cameraY, 1e-5f)
    }
}
