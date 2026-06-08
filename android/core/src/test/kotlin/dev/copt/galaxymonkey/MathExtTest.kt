package dev.copt.galaxymonkey

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

class MathExtTest {

    @Test
    fun `expApproach converges on target`() {
        var v = 0f
        for (i in 0 until 300) {
            v = expApproach(v, 100f, 5f, 1f / 60f)
        }
        assertTrue(abs(v - 100f) < 0.01f, "should converge to 100, got $v")
    }

    @Test
    fun `expApproach at dt=0 returns current`() {
        val result = expApproach(50f, 100f, 5f, 0f)
        assertEquals(50f, result, 1e-5f)
    }

    @Test
    fun `expDamp reduces magnitude`() {
        val v = com.badlogic.gdx.math.Vector2(100f, 0f)
        val result = expDamp(v, 2.4f, 1f / 60f)
        assertTrue(result.len() < 100f, "damped len ${result.len()} should be < 100")
        assertTrue(result.len() > 90f, "single frame damp should be gentle, got ${result.len()}")
    }

    @Test
    fun `angleRadSafe returns fallback for zero vector`() {
        val v = com.badlogic.gdx.math.Vector2(0f, 0f)
        assertEquals(0f, v.angleRadSafe(), 1e-5f)
        assertEquals(1.5f, v.angleRadSafe(1.5f), 1e-5f)
    }

    @Test
    fun `angleRadSafe returns atan2 for nonzero vector`() {
        val v = com.badlogic.gdx.math.Vector2(1f, 0f)
        assertEquals(0f, v.angleRadSafe(), 1e-3f)
        val v2 = com.badlogic.gdx.math.Vector2(0f, 1f)
        assertEquals(PI.toFloat() / 2f, v2.angleRadSafe(), 1e-3f)
    }

    @Test
    fun `lerpAngle takes shortest arc`() {
        val from = 0.1f
        val to = (2 * PI - 0.1).toFloat()
        val mid = lerpAngle(from, to, 0.5f)
        assertTrue(abs(mid) < 0.2f, "should wrap short way, got $mid")
    }

    @Test
    fun `lerpAngle at t=0 returns from`() {
        assertEquals(1f, lerpAngle(1f, 5f, 0f), 1e-5f)
    }

    @Test
    fun `lerpAngle at t=1 returns to`() {
        val result = lerpAngle(0f, 1f, 1f)
        assertEquals(1f, result, 1e-5f)
    }

    @Test
    fun `applyDeadzone returns zero inside deadzone`() {
        val raw = com.badlogic.gdx.math.Vector2(0.05f, 0.05f)
        val result = applyDeadzone(raw, 0.12f)
        assertEquals(0f, result.len(), 1e-5f)
    }

    @Test
    fun `applyDeadzone ramps from zero at edge`() {
        val raw = com.badlogic.gdx.math.Vector2(0.12f, 0f)
        val result = applyDeadzone(raw, 0.12f)
        assertEquals(0f, result.len(), 1e-3f)

        val raw2 = com.badlogic.gdx.math.Vector2(0.56f, 0f)
        val result2 = applyDeadzone(raw2, 0.12f)
        assertTrue(result2.len() > 0f && result2.len() < 0.56f,
            "mid-range should remap, got ${result2.len()}")
    }

    @Test
    fun `applyDeadzone full stick returns magnitude 1`() {
        val raw = com.badlogic.gdx.math.Vector2(1f, 0f)
        val result = applyDeadzone(raw, 0.12f)
        assertEquals(1f, result.len(), 1e-3f)
    }

    @Test
    fun `dist2 and withinRange`() {
        val a = com.badlogic.gdx.math.Vector2(0f, 0f)
        val b = com.badlogic.gdx.math.Vector2(3f, 4f)
        assertEquals(25f, dist2(a, b), 1e-5f)
        assertTrue(withinRange(a, b, 5f))
        assertFalse(withinRange(a, b, 4.9f))
    }
}
