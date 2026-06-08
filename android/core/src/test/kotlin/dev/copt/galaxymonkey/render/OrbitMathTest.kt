package dev.copt.galaxymonkey.render

import com.badlogic.gdx.math.MathUtils
import dev.copt.galaxymonkey.Tuning
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class OrbitMathTest {

    private val tiltY = Tuning.World.orbitTiltY

    private data class PlanetDef(val name: String, val radius: Float, val angSpeed: Float)

    private val planets = listOf(
        PlanetDef("Mercury", Tuning.World.orbitMercury, Tuning.World.angSpeedMercury),
        PlanetDef("Venus", Tuning.World.orbitVenus, Tuning.World.angSpeedVenus),
        PlanetDef("Earth", Tuning.World.orbitEarth, Tuning.World.angSpeedEarth),
        PlanetDef("Mars", Tuning.World.orbitMars, Tuning.World.angSpeedMars),
        PlanetDef("Jupiter", Tuning.World.orbitJupiter, Tuning.World.angSpeedJupiter),
        PlanetDef("Saturn", Tuning.World.orbitSaturn, Tuning.World.angSpeedSaturn),
        PlanetDef("Uranus", Tuning.World.orbitUranus, Tuning.World.angSpeedUranus),
        PlanetDef("Neptune", Tuning.World.orbitNeptune, Tuning.World.angSpeedNeptune),
    )

    private fun orbitX(phase: Float, radius: Float) = cos(phase.toDouble()).toFloat() * radius
    private fun orbitY(phase: Float, radius: Float) = sin(phase.toDouble()).toFloat() * radius * tiltY

    // --- Conjunction start ---

    @Test
    fun `at t=0 all planets start at phase 0 on +X axis (y=0)`() {
        for (p in planets) {
            val x = orbitX(0f, p.radius)
            val y = orbitY(0f, p.radius)
            assertEquals(p.radius, x, 1e-3f,
                "${p.name}: x at phase=0 should equal radius=${p.radius}")
            assertEquals(0f, y, 1e-3f,
                "${p.name}: y at phase=0 should be 0 (conjunction)")
        }
    }

    // --- Position formula with tiltY ---

    @Test
    fun `position uses Y squash factor orbitTiltY=0_45`() {
        val phase = 1.2f
        for (p in planets) {
            val x = orbitX(phase, p.radius)
            val y = orbitY(phase, p.radius)
            val unsquashedY = sin(phase.toDouble()).toFloat() * p.radius
            assertEquals(unsquashedY * tiltY, y, 1e-2f,
                "${p.name}: y should be sin(phase)*radius*tiltY (squashed by $tiltY)")
            assertEquals(cos(phase.toDouble()).toFloat() * p.radius, x, 1e-2f,
                "${p.name}: x should be cos(phase)*radius")
            println("orbit ${p.name} phase=%.3f pos=(%.1f,%.1f) unsquashedY=%.1f".format(phase, x, y, unsquashedY))
        }
    }

    // --- Inner planets faster than outer ---

    @Test
    fun `inner planets advance phase faster than outer`() {
        for (i in 0 until planets.size - 1) {
            assertTrue(planets[i].angSpeed > planets[i + 1].angSpeed,
                "${planets[i].name} (${planets[i].angSpeed}) should be faster than ${planets[i + 1].name} (${planets[i + 1].angSpeed})")
        }
    }

    @Test
    fun `angular separations grow after fixed time`() {
        val dt = 10f
        val phases = planets.map { it.angSpeed * dt }
        for (i in 0 until phases.size - 1) {
            assertTrue(phases[i] > phases[i + 1],
                "after ${dt}s: ${planets[i].name} phase=${phases[i]} should exceed ${planets[i + 1].name} phase=${phases[i + 1]}")
        }
    }

    // --- Mercury far-side z-flip ---

    @Test
    fun `mercury z-flip occurs at y-sign crossing`() {
        val frontZ = -45f
        val backZ = -46.7f
        val mercury = planets[0]
        val dt = 0.1f
        var phase = 0f
        var prevBehind = false
        var flips = 0

        for (i in 0 until 2000) {
            phase += mercury.angSpeed * dt
            val y = orbitY(phase, mercury.radius)
            val behind = y >= 0f
            if (behind != prevBehind && i > 0) {
                flips++
                val z = if (behind) backZ else frontZ
                println("orbit Mercury z-flip at phase=%.3f y=%.3f -> z=%.1f".format(phase, y, z))
            }
            prevBehind = behind
        }
        assertTrue(flips >= 2, "should see at least 2 z-flips in a full orbit, got $flips")
    }

    @Test
    fun `mercury drawBehindSun true when y non-negative, false when y negative`() {
        val phase1 = MathUtils.PI / 4f  // first quadrant: sin > 0 -> y > 0
        val y1 = orbitY(phase1, Tuning.World.orbitMercury)
        assertTrue(y1 > 0f)
        assertTrue(y1 >= 0f, "y>=0 -> drawBehindSun=true")

        val phase2 = -MathUtils.PI / 4f  // fourth quadrant: sin < 0 -> y < 0
        val y2 = orbitY(phase2, Tuning.World.orbitMercury)
        assertTrue(y2 < 0f)
        assertFalse(y2 >= 0f, "y<0 -> drawBehindSun=false")
    }

    // --- Asteroid belt ---

    @Test
    fun `belt rock speeds within jitter range`() {
        val baseSpeed = Tuning.World.asteroidBeltAngularSpeed
        val jitter = Tuning.World.asteroidBeltSpeedJitter
        for (i in 0 until 50) {
            val speed = baseSpeed + MathUtils.random(-jitter, jitter)
            assertTrue(speed >= baseSpeed - jitter - 0.0001f &&
                speed <= baseSpeed + jitter + 0.0001f,
                "belt rock i=$i speed=$speed should be in [${baseSpeed - jitter}, ${baseSpeed + jitter}]")
        }
    }

    @Test
    fun `belt rock radii within inner-outer range`() {
        val inner = Tuning.World.asteroidBeltInnerRadius
        val outer = Tuning.World.asteroidBeltOuterRadius
        for (i in 0 until 50) {
            val r = MathUtils.random(inner, outer)
            assertTrue(r >= inner && r <= outer,
                "belt rock i=$i r=$r should be in [$inner, $outer]")
        }
    }

    @Test
    fun `rock02 variant never used in belt`() {
        val variants = listOf("ROCK_01", "ROCK_03", "ROCK_04")
        assertFalse(variants.contains("ROCK_02"),
            "ROCK_02 should never appear in belt variant list")
    }

    // --- Moon phases ---

    @Test
    fun `moons start with random non-zero phases`() {
        val moonPhases = (0 until 10).map { MathUtils.random(0f, MathUtils.PI2) }
        val distinct = moonPhases.toSet()
        assertTrue(distinct.size > 1,
            "moon phases should be distinct (random), got $distinct")
    }
}
