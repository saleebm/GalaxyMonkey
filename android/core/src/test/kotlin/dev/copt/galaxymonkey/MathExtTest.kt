package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

class MathExtTest {

    // --- expApproach ---

    @Test
    fun `expApproach at dt=0 returns current unchanged`() {
        val result = expApproach(5f, 10f, 8f, 0f)
        assertEquals(5f, result, 1e-6f,
            "expApproach(current=5, target=10, rate=8, dt=0) should return current=5")
    }

    @Test
    fun `expApproach converges to target over many steps`() {
        var current = 0f
        val target = 100f
        val rate = 8f
        val dt = 1f / 60f
        for (i in 0 until 600) {
            current = expApproach(current, target, rate, dt)
        }
        assertEquals(target, current, 1e-3f,
            "expApproach should converge to target after ~10s at rate=8")
    }

    @Test
    fun `expApproach is monotonic toward target`() {
        var current = 0f
        val target = 50f
        val rate = 8f
        val dt = 1f / 60f
        for (i in 0 until 120) {
            val next = expApproach(current, target, rate, dt)
            assertTrue(next >= current,
                "expApproach should monotonically approach target: step $i current=$current next=$next")
            current = next
        }
    }

    @Test
    fun `expApproach dt-doubling for frame-rate independence`() {
        val current = 10f
        val target = 50f
        val rate = 8f
        val dt = 1f / 60f
        val oneBigStep = expApproach(current, target, rate, dt * 2)
        val step1 = expApproach(current, target, rate, dt)
        val twoSmallSteps = expApproach(step1, target, rate, dt)
        assertEquals(oneBigStep, twoSmallSteps, 0.5f,
            "one step of 2*dt should approximate two steps of dt")
    }

    // --- expDamp ---

    @Test
    fun `expDamp velocity strictly decreases each step`() {
        val vel = Vector2(100f, 50f)
        val drag = 2.4f
        val dt = 1f / 60f
        var prevLen = vel.len()
        for (i in 0 until 60) {
            expDamp(vel, drag, dt)
            val len = vel.len()
            assertTrue(len < prevLen,
                "expDamp: velocity should decrease: step $i prev=$prevLen current=$len")
            prevLen = len
        }
    }

    @Test
    fun `expDamp never flips sign`() {
        val vel = Vector2(10f, -20f)
        val origAngle = atan2(vel.y, vel.x)
        val drag = 2.4f
        val dt = 1f / 60f
        for (i in 0 until 200) {
            expDamp(vel, drag, dt)
            if (vel.len2() > 1e-10f) {
                val angle = atan2(vel.y, vel.x)
                assertEquals(origAngle.toDouble(), angle.toDouble(), 1e-3,
                    "expDamp should preserve direction, step $i")
            }
        }
    }

    @Test
    fun `expDamp reaches near-zero`() {
        val vel = Vector2(500f, 300f)
        val drag = 2.4f
        val dt = 1f / 60f
        for (i in 0 until 600) expDamp(vel, drag, dt)
        assertTrue(vel.len() < 1e-3f,
            "expDamp should reach near-zero after many steps, got ${vel.len()}")
    }

    // --- applyDeadzone ---

    @Test
    fun `applyDeadzone inside deadzone returns zero`() {
        val raw = Vector2(0.08f, 0.06f) // len = 0.1
        val result = applyDeadzone(raw, 0.12f)
        assertEquals(0f, result.len(), 1e-6f,
            "input inside deadzone (len=0.10) should return zero")
    }

    @Test
    fun `applyDeadzone at max returns length 1`() {
        val raw = Vector2(0.6f, 0.8f) // len = 1.0
        val result = applyDeadzone(raw, 0.12f)
        assertEquals(1f, result.len(), 1e-4f,
            "input at max (len=1.0) should return length=1.0")
    }

    @Test
    fun `applyDeadzone just past edge ramps from zero`() {
        val raw = Vector2(0.13f, 0f)
        val result = applyDeadzone(raw, 0.12f)
        assertTrue(result.len() > 0f, "just past deadzone should produce >0 magnitude")
        assertTrue(result.len() < 0.05f,
            "just past deadzone should produce small magnitude, not jump to raw length")
        println("[MathExtTest] deadzone: edge ramp OK (in=0.13 -> out=%.4f, dir preserved)".format(result.len()))
    }

    @Test
    fun `applyDeadzone preserves direction`() {
        val raw = Vector2(0.6f, 0.8f)
        val rawAngle = atan2(raw.y, raw.x)
        val result = applyDeadzone(raw, 0.12f)
        val resultAngle = atan2(result.y, result.x)
        assertEquals(rawAngle.toDouble(), resultAngle.toDouble(), 1e-4,
            "applyDeadzone should preserve direction")
    }

    // --- lerpAngle ---

    @Test
    fun `lerpAngle t=0 returns from, t=1 returns to`() {
        val from = 1.0f
        val to = 2.5f
        assertEquals(from, lerpAngle(from, to, 0f), 1e-5f)
        assertEquals(to, lerpAngle(from, to, 1f), 1e-5f)
    }

    @Test
    fun `lerpAngle takes short arc across 2pi boundary`() {
        val from = 3.0f
        val to = -3.0f
        val mid = lerpAngle(from, to, 0.5f)
        assertTrue(abs(mid) > 2.5f,
            "lerpAngle should take short arc near ±pi boundary, got $mid")
    }

    // --- angleRadSafe ---

    @Test
    fun `angleRadSafe zero-length returns fallback without NaN`() {
        val v = Vector2(0f, 0f)
        val result = v.angleRadSafe(0f)
        assertFalse(result.isNaN(), "angleRadSafe should never return NaN")
        assertEquals(0f, result, 1e-6f)
        assertEquals(1.5f, v.angleRadSafe(1.5f), 1e-5f)
    }

    @Test
    fun `angleRadSafe (1,0) returns 0`() {
        assertEquals(0f, Vector2(1f, 0f).angleRadSafe(), 1e-4f)
    }

    @Test
    fun `angleRadSafe (0,1) returns PI div 2`() {
        assertEquals(PI.toFloat() / 2f, Vector2(0f, 1f).angleRadSafe(), 1e-4f)
    }

    // --- dist2 / withinRange ---

    @Test
    fun `dist2 matches Vector2 dst2`() {
        val a = Vector2(10f, 20f)
        val b = Vector2(50f, 80f)
        assertEquals(a.dst2(b), dist2(a, b), 1e-3f)
    }

    @Test
    fun `withinRange true at r-epsilon, false at r+epsilon`() {
        val a = Vector2(0f, 0f)
        val r = 360f
        assertTrue(withinRange(a, Vector2(359.99f, 0f), r))
        assertFalse(withinRange(a, Vector2(360.01f, 0f), r))
    }

    // --- edge-case NaN/Inf safety ---

    @Test
    fun `no NaN or Inf from zero-vector edge cases`() {
        val zero = Vector2(0f, 0f)
        assertFalse(expApproach(0f, 0f, 8f, 0f).isNaN())
        assertFalse(expApproach(0f, 0f, 8f, 0f).isInfinite())
        val damped = expDamp(Vector2(0f, 0f), 2.4f, 1f / 60f)
        assertFalse(damped.x.isNaN()); assertFalse(damped.y.isNaN())
        val dz = applyDeadzone(zero, 0.12f)
        assertFalse(dz.x.isNaN()); assertFalse(dz.y.isNaN())
        assertFalse(zero.angleRadSafe().isNaN())
        assertFalse(dist2(zero, zero).isNaN())
        println("[MathExtTest] all vector-math feel helpers verified")
    }
}
