package dev.copt.galaxymonkey

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FixedTimestepTest {

    private val step = 1f / 60f
    private val maxFrameTime = 0.25f

    @Test
    fun `1s delta produces clamped number of steps not 60`() {
        val clampLogs = mutableListOf<Pair<Float, Float>>()
        val ts = FixedTimestep(step, maxFrameTime) { raw, clamped ->
            clampLogs.add(raw to clamped)
        }
        // First call is post-reset, so dt=0 effectively
        ts.advance(0f) {}

        var stepCount = 0
        ts.advance(1.0f) { stepCount++ }

        assertEquals(15, stepCount,
            "1.0s delta clamped to 0.25s at step=1/60 should yield 15 steps (not 60)")
        assertTrue(ts.remainder < step,
            "remainder should be less than one step")
        assertTrue(clampLogs.isNotEmpty(),
            "clamp callback should fire when delta exceeds maxFrameTime")
        assertEquals(1.0f, clampLogs[0].first, 1e-3f)
        assertEquals(maxFrameTime, clampLogs[0].second, 1e-5f)
    }

    @Test
    fun `first frame after resetClock yields zero gameplay dt`() {
        val ts = FixedTimestep(step, maxFrameTime)
        var stepCount = 0
        ts.advance(step) { stepCount++ }
        assertEquals(0, stepCount,
            "first frame after reset should produce 0 steps (dt=0)")
    }

    @Test
    fun `resetClock mid-session resets first frame behavior`() {
        val ts = FixedTimestep(step, maxFrameTime)
        // Consume first frame
        ts.advance(step) {}
        // Normal frame
        var count1 = 0
        ts.advance(step) { count1++ }
        assertTrue(count1 >= 1, "normal frame should produce steps")

        ts.resetClock()
        var count2 = 0
        ts.advance(step) { count2++ }
        assertEquals(0, count2,
            "first frame after resetClock should produce 0 steps")
    }

    @Test
    fun `two consecutive 16ms frames each run exactly one step`() {
        val ts = FixedTimestep(step, maxFrameTime)
        // Consume first-frame-after-reset
        ts.advance(step) {}

        var count1 = 0
        ts.advance(step) { count1++ }

        var count2 = 0
        ts.advance(step) { count2++ }

        assertEquals(1, count1, "first 16ms frame should run 1 step")
        assertEquals(1, count2, "second 16ms frame should run 1 step")
    }

    @Test
    fun `accumulator carries remainder across frames`() {
        val ts = FixedTimestep(step, maxFrameTime)
        ts.advance(0f) {} // consume reset frame

        // Feed half a step
        var count = 0
        ts.advance(step / 2f) { count++ }
        assertEquals(0, count, "half-step should not trigger a tick")
        assertTrue(ts.remainder > 0f, "remainder should be non-zero")

        // Feed another half plus a tiny bit — should trigger exactly 1 tick
        count = 0
        ts.advance(step / 2f + 0.001f) { count++ }
        assertEquals(1, count, "accumulated half-steps should trigger 1 tick")
    }

    @Test
    fun `clamp callback not fired for normal frames`() {
        var clampFired = false
        val ts = FixedTimestep(step, maxFrameTime) { _, _ -> clampFired = true }
        ts.advance(step) {}
        ts.advance(step) {}
        assertFalse(clampFired, "clamp should not fire for normal 16ms frames")
    }

    @Test
    fun `clamp callback fires with correct raw and clamped values`() {
        val logs = mutableListOf<Pair<Float, Float>>()
        val ts = FixedTimestep(step, maxFrameTime) { raw, clamped -> logs.add(raw to clamped) }
        ts.advance(0f) {} // consume reset
        ts.advance(0.5f) {}
        assertEquals(1, logs.size)
        assertEquals(0.5f, logs[0].first, 1e-3f)
        assertEquals(maxFrameTime, logs[0].second, 1e-5f)
    }
}
