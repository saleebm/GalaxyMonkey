package dev.copt.galaxymonkey.render

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs

class TerminatorShaderTest {

    @Test
    fun `sunAngle equals phase plus PI minus bodyRotation`() {
        val phase = 1.5f
        val rotation = 0.3f
        val expected = phase + PI.toFloat() - rotation
        val actual = TerminatorShader.sunAngleForPlanet(phase, rotation)
        assertEquals(expected, actual, 1e-5f)
        println("terminator sunLocal phase=%.2f spin=%.2f -> local=%.2f".format(phase, rotation, actual))
    }

    @Test
    fun `world-space lit direction stays fixed as planet spins`() {
        val phase = 2.0f
        val expectedWorldLit = phase + PI.toFloat()
        for (spinStep in 0..20) {
            val selfRotation = spinStep * 0.3f
            val sunLocal = TerminatorShader.sunAngleForPlanet(phase, selfRotation)
            val worldLit = sunLocal + selfRotation
            assertEquals(expectedWorldLit, worldLit, 1e-4f,
                "worldLit should be constant regardless of spin (step=$spinStep)")
            println("terminator sunLocal phase=%.2f spin=%.2f -> local=%.2f worldLit=%.2f".format(
                phase, selfRotation, sunLocal, worldLit))
        }
    }

    // Replicate the GLSL smoothstep in Kotlin to verify the brightness mapping
    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun brightness(d: Float): Float {
        val lit = smoothstep(-0.4f, 0.4f, d)
        return 0.28f + (1.04f - 0.28f) * lit // mix(0.28, 1.04, lit)
    }

    @Test
    fun `smoothstep at d=-0_4 gives lit=0 and brightness=0_28`() {
        val lit = smoothstep(-0.4f, 0.4f, -0.4f)
        assertEquals(0f, lit, 1e-5f)
        val b = brightness(-0.4f)
        assertEquals(0.28f, b, 1e-4f)
        println("terminator brightness d=-0.40 lit=%.4f bright=%.4f".format(lit, b))
    }

    @Test
    fun `smoothstep at d=0_4 gives lit=1 and brightness=1_04`() {
        val lit = smoothstep(-0.4f, 0.4f, 0.4f)
        assertEquals(1f, lit, 1e-5f)
        val b = brightness(0.4f)
        assertEquals(1.04f, b, 1e-4f)
        println("terminator brightness d=0.40 lit=%.4f bright=%.4f".format(lit, b))
    }

    @Test
    fun `smoothstep at d=0 gives lit=0_5 and mid brightness`() {
        val lit = smoothstep(-0.4f, 0.4f, 0f)
        assertEquals(0.5f, lit, 1e-4f)
        val b = brightness(0f)
        val expected = 0.28f + (1.04f - 0.28f) * 0.5f
        assertEquals(expected, b, 1e-4f)
        println("terminator brightness d=0.00 lit=%.4f bright=%.4f".format(lit, b))
    }

    @Test
    fun `deep night side clamps to 0_28`() {
        val b = brightness(-1f)
        assertEquals(0.28f, b, 1e-4f)
    }

    @Test
    fun `deep day side clamps to 1_04`() {
        val b = brightness(1f)
        assertEquals(1.04f, b, 1e-4f)
    }

    @Test
    fun `brightness is monotonically increasing with d`() {
        var prev = 0f
        for (i in -20..20) {
            val d = i / 20f
            val b = brightness(d)
            assertTrue(b >= prev, "brightness should increase: d=$d b=$b prev=$prev")
            prev = b
        }
    }
}
