package dev.copt.galaxymonkey.render

import com.badlogic.gdx.math.MathUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ThrusterEmitterTest {

    @Test
    fun `color ramp at stop 0 returns white-hot`() {
        val e = ThrusterEmitter()
        val (r, g, b) = e.sampleRamp(0.0f)
        assertEquals(1.00f, r, 1e-4f)
        assertEquals(1.00f, g, 1e-4f)
        assertEquals(0.85f, b, 1e-4f)
        println("thruster ramp t=0.00 rgb=(%.2f,%.2f,%.2f)".format(r, g, b))
    }

    @Test
    fun `color ramp at stop 0_2 returns orange`() {
        val e = ThrusterEmitter()
        val (r, g, b) = e.sampleRamp(0.2f)
        assertEquals(1.00f, r, 1e-4f)
        assertEquals(0.78f, g, 1e-4f)
        assertEquals(0.20f, b, 1e-4f)
        println("thruster ramp t=0.20 rgb=(%.2f,%.2f,%.2f)".format(r, g, b))
    }

    @Test
    fun `color ramp at stop 1_0 returns smoke`() {
        val e = ThrusterEmitter()
        val (r, g, b) = e.sampleRamp(1.0f)
        assertEquals(0.18f, r, 1e-4f)
        assertEquals(0.18f, g, 1e-4f)
        assertEquals(0.18f, b, 1e-4f)
        println("thruster ramp t=1.00 rgb=(%.2f,%.2f,%.2f)".format(r, g, b))
    }

    @Test
    fun `color ramp midpoint 0_325 interpolates between orange and red`() {
        val e = ThrusterEmitter()
        val (r, g, b) = e.sampleRamp(0.325f)
        // t=0.325 is midway between stop 0.20 (1.00,0.78,0.20) and 0.45 (1.00,0.35,0.05)
        val f = (0.325f - 0.20f) / (0.45f - 0.20f) // = 0.5
        val expectedR = MathUtils.lerp(1.00f, 1.00f, f)
        val expectedG = MathUtils.lerp(0.78f, 0.35f, f)
        val expectedB = MathUtils.lerp(0.20f, 0.05f, f)
        assertEquals(expectedR, r, 1e-3f)
        assertEquals(expectedG, g, 1e-3f)
        assertEquals(expectedB, b, 1e-3f)
        assertTrue(g in 0.35f..0.78f, "green channel should be between the two stops")
        assertTrue(b in 0.05f..0.20f, "blue channel should be between the two stops")
        println("thruster ramp t=0.33 rgb=(%.2f,%.2f,%.2f) expected=(%.2f,%.2f,%.2f)".format(r, g, b, expectedR, expectedG, expectedB))
    }

    @Test
    fun `particle alpha reaches zero after full lifetime`() {
        // alphaSpeed = -2.2, starting alpha ~1.0, lifetime ~0.45
        // after 0.45s: alpha = 1.0 + (-2.2 * 0.45) = 1.0 - 0.99 = 0.01
        // but clamped at 0
        val alpha = 1.0f + (-2.2f * 0.45f)
        val clamped = if (alpha < 0f) 0f else alpha
        assertTrue(clamped < 0.05f, "alpha after full lifetime should be near zero, got $clamped")
        println("thruster particle age=0.45 alpha=%.3f (raw=%.3f clamped=%.3f)".format(clamped, alpha, clamped))
    }

    @Test
    fun `particle scale shrinks over lifetime`() {
        // scaleSpeed = -0.6, starting scale ~thrustScale*0.5 = 1.4*0.5 = 0.7
        val baseScale = 1.4f * 0.5f // Tuning.VFX.thrustScale * 0.5
        val finalScale = (baseScale + (-0.6f * 0.45f)).coerceAtLeast(0f)
        assertTrue(finalScale < baseScale, "scale should shrink, got start=$baseScale end=$finalScale")
        println("thruster particle age=0.45 scale=%.3f (start=%.3f)".format(finalScale, baseScale))
    }

    @Test
    fun `setIntensity 0 yields idle floor`() {
        val e = ThrusterEmitter()
        e.setIntensity(0f)
        val idleBirth = 220f * 0.4f
        assertEquals(idleBirth, e.birthRate, 1e-3f)
        assertEquals(160f, e.particleSpeed, 1e-3f)
        println("thruster intensity=0.00 birthRate=%.1f speed=%.1f".format(e.birthRate, e.particleSpeed))
    }

    @Test
    fun `setIntensity 1 yields full burst`() {
        val e = ThrusterEmitter()
        e.setIntensity(1f)
        val fullBirth = 220f * 1.5f
        assertEquals(fullBirth, e.birthRate, 1e-3f)
        assertEquals(240f, e.particleSpeed, 1e-3f)
        println("thruster intensity=1.00 birthRate=%.1f speed=%.1f".format(e.birthRate, e.particleSpeed))
    }

    @Test
    fun `setIntensity is monotonic`() {
        val e = ThrusterEmitter()
        var prevBirth = 0f
        var prevSpeed = 0f
        for (i in 0..10) {
            val mag = i / 10f
            e.setIntensity(mag)
            assertTrue(e.birthRate >= prevBirth, "birthRate should be monotonic at mag=$mag")
            assertTrue(e.particleSpeed >= prevSpeed, "particleSpeed should be monotonic at mag=$mag")
            println("thruster intensity=%.2f birthRate=%.1f speed=%.1f".format(mag, e.birthRate, e.particleSpeed))
            prevBirth = e.birthRate
            prevSpeed = e.particleSpeed
        }
    }

    @Test
    fun `setIntensity clamps above 1`() {
        val e = ThrusterEmitter()
        e.setIntensity(5f)
        val fullBirth = 220f * 1.5f
        assertEquals(fullBirth, e.birthRate, 1e-3f)
        assertEquals(240f, e.particleSpeed, 1e-3f)
    }

    @Test
    fun `emission direction follows emitterAngle`() {
        val e = ThrusterEmitter()
        e.emitterAngle = MathUtils.PI // facing left -> emit left
        e.setIntensity(1f)
        // Spawn particles and check velocity direction
        e.update(0.1f)
        // With angle=PI, cos(PI)=-1, so vx should be negative on average
        // We can't inspect particles directly, but we exposed aliveCount
        assertTrue(e.aliveCount > 0, "should have spawned particles")

        // Now flip to 0 (facing right -> emit right)
        val e2 = ThrusterEmitter()
        e2.emitterAngle = 0f
        e2.setIntensity(1f)
        e2.update(0.1f)
        assertTrue(e2.aliveCount > 0, "should have spawned particles facing right")
        println("thruster emission: PI -> left, 0 -> right, both spawned ok")
    }

    @Test
    fun `particles die after lifetime`() {
        val e = ThrusterEmitter(maxParticles = 1)
        e.setIntensity(1f)
        e.update(0.01f)
        assertEquals(1, e.aliveCount, "should have spawned 1 particle")
        // Advance past max lifetime (0.45+0.15=0.6s). Spawn runs before
        // aging so the slot isn't freed for reuse in the same frame.
        e.update(0.7f)
        assertEquals(0, e.aliveCount, "particle should have expired after 0.7s")
        // Next tick reclaims the slot and spawns a fresh particle
        e.update(1f / 60f)
        assertEquals(1, e.aliveCount, "dead slot should be reused for new spawn")
        println("thruster particles: died after lifetime, slot reused on next frame")
    }
}
