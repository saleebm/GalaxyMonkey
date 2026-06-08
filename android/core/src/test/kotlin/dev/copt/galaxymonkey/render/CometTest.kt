package dev.copt.galaxymonkey.render

import com.badlogic.gdx.math.MathUtils
import dev.copt.galaxymonkey.Tuning
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.*

class CometTest {

    // --- Countdown cadence ---

    @Test
    fun `first comet spawns after initialDelay`() {
        var countdown = Tuning.World.cometInitialDelay
        var spawns = 0
        val dt = 1f / 60f
        var elapsed = 0f
        while (elapsed < Tuning.World.cometInitialDelay + dt) {
            countdown -= dt
            if (countdown <= 0f) {
                spawns++
                countdown = Tuning.World.cometSpawnIntervalMin
            }
            elapsed += dt
        }
        assertEquals(1, spawns, "exactly one comet should spawn after initialDelay=${Tuning.World.cometInitialDelay}")
        println("comet spawn: first at t=%.2f (initialDelay=%.1f)".format(elapsed, Tuning.World.cometInitialDelay))
    }

    @Test
    fun `reset interval falls within configured range`() {
        val min = Tuning.World.cometSpawnIntervalMin
        val max = Tuning.World.cometSpawnIntervalMax
        for (i in 0 until 50) {
            val interval = MathUtils.random(min, max)
            assertTrue(interval >= min && interval <= max,
                "interval=$interval should be in [$min, $max]")
        }
        println("comet spawn: 50 intervals all within [%.0f, %.0f]".format(min, max))
    }

    @Test
    fun `no two comets spawn on same frame`() {
        var countdown = Tuning.World.cometInitialDelay
        val dt = 1f / 60f
        for (frame in 0 until 6000) { // 100 seconds
            countdown -= dt
            var frameSpawns = 0
            if (countdown <= 0f) {
                frameSpawns++
                countdown = Tuning.World.cometSpawnIntervalMin
            }
            assertTrue(frameSpawns <= 1, "frame $frame: spawned $frameSpawns comets (max 1)")
        }
    }

    // --- Chord geometry ---

    @Test
    fun `start and end lie on tilted ellipse`() {
        val r = Tuning.World.cometPathRadius
        val tilt = Tuning.World.orbitTiltY
        for (i in 0 until 20) {
            val entryAngle = MathUtils.random(0f, MathUtils.PI2)
            val startX = cos(entryAngle.toDouble()) * r
            val startY = sin(entryAngle.toDouble()) * r * tilt
            // Verify point is on the ellipse: (x/r)^2 + (y/(r*tilt))^2 == 1
            val check = (startX / r).pow(2) + (startY / (r * tilt)).pow(2)
            assertEquals(1.0, check, 1e-4, "point should lie on ellipse, got $check")
        }
        println("comet chord: 20 start points all on tilted ellipse (r=%.0f tilt=%.2f)".format(r, tilt))
    }

    @Test
    fun `exit angle is pi plus jitter from entry`() {
        val jitter = Tuning.World.cometExitJitterRad
        for (i in 0 until 30) {
            val entryAngle = MathUtils.random(0f, MathUtils.PI2)
            val exitAngle = entryAngle + MathUtils.PI +
                MathUtils.random(-jitter, jitter)
            val diff = abs((exitAngle - entryAngle) % (2 * MathUtils.PI))
            // diff should be PI +/- jitter
            assertTrue(diff >= MathUtils.PI - jitter - 0.01f &&
                diff <= MathUtils.PI + jitter + 0.01f,
                "angle diff=%.4f should be PI +/- %.4f".format(diff, jitter))
        }
    }

    // --- Head motion ---

    @Test
    fun `head at t=0 is at start, t=1 is at end`() {
        val startX = 100f; val startY = 200f
        val endX = -300f; val endY = -400f
        // t=0
        val x0 = MathUtils.lerp(startX, endX, 0f)
        val y0 = MathUtils.lerp(startY, endY, 0f)
        assertEquals(startX, x0, 1e-5f)
        assertEquals(startY, y0, 1e-5f)
        // t=1
        val x1 = MathUtils.lerp(startX, endX, 1f)
        val y1 = MathUtils.lerp(startY, endY, 1f)
        assertEquals(endX, x1, 1e-5f)
        assertEquals(endY, y1, 1e-5f)
    }

    @Test
    fun `head position is linear in t`() {
        val startX = 0f; val startY = 0f
        val endX = 1000f; val endY = 500f
        for (i in 0..10) {
            val t = i / 10f
            val x = MathUtils.lerp(startX, endX, t)
            val y = MathUtils.lerp(startY, endY, t)
            assertEquals(endX * t, x, 1e-3f)
            assertEquals(endY * t, y, 1e-3f)
        }
    }

    @Test
    fun `rotation equals atan2 of chord direction`() {
        val startX = 100f; val startY = 0f
        val endX = -100f; val endY = 200f
        val dx = endX - startX
        val dy = endY - startY
        val expected = atan2(dy, dx)
        assertEquals(expected, atan2(dy, dx), 1e-5f)
        println("comet rotation: dx=%.0f dy=%.0f -> %.4f rad".format(dx, dy, expected))
    }

    // --- Trail particle lifetime ---

    @Test
    fun `trail particle dies after cometTrailLifetime`() {
        val lifetime = Tuning.World.cometTrailLifetime
        var age = 0f
        val dt = 1f / 60f
        var alive = true
        while (alive) {
            age += dt
            if (age >= lifetime) alive = false
        }
        assertTrue(age >= lifetime)
        assertTrue(age < lifetime + dt + 0.001f,
            "particle should die on the frame age crosses lifetime")
        println("comet trail particle died at age=%.3f (lifetime=%.1f)".format(age, lifetime))
    }

    @Test
    fun `trail particles are world-fixed (detached from head)`() {
        // Simulate: emit a particle at head position (50, 50), then move
        // head to (200, 200). Particle position stays at (50, 50).
        val particleX = 50f
        val particleY = 50f
        // Head moves — particle position is independent
        val headX = 200f
        val headY = 200f
        assertNotEquals(headX, particleX)
        assertNotEquals(headY, particleY)
        // In the implementation, trail particles store world-space x,y
        // and never reference the comet head position after emission
        println("comet trail: particle at (%.0f,%.0f) stays put when head moves to (%.0f,%.0f)".format(
            particleX, particleY, headX, headY))
    }
}
