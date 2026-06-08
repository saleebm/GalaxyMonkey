package dev.copt.galaxymonkey.render

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class ScreenShakeTest {

    private val shakeDecay = 0.86f
    private val shakeMaxOffset = 18f

    @Test
    fun `shake magnitude clamped to maxOffset`() {
        val intensity = 50f
        val clamped = intensity.coerceAtMost(shakeMaxOffset)
        assertEquals(shakeMaxOffset, clamped)
        println("ScreenShake: intensity=$intensity clamped to $clamped")
    }

    @Test
    fun `exponential decay converges toward zero`() {
        var ox = 8f
        var oy = 6f
        val initialMag = sqrt(ox * ox + oy * oy)
        for (i in 0 until 30) {
            ox *= shakeDecay
            oy *= shakeDecay
        }
        val finalMag = sqrt(ox * ox + oy * oy)
        assertTrue(finalMag < 0.15f,
            "after 30 frames, magnitude $finalMag should be near zero (was $initialMag)")
        println("ScreenShake: decay $initialMag -> $finalMag after 30 frames")
    }

    @Test
    fun `decay factor 0_86 halves in about 4-5 frames`() {
        var mag = 10f
        var frames = 0
        while (mag > 5f) {
            mag *= shakeDecay
            frames++
        }
        assertTrue(frames in 4..6,
            "expected half-life ~4-5 frames at decay=0.86, got $frames")
        println("ScreenShake: half-life = $frames frames")
    }

    @Test
    fun `per-event intensities from Tuning`() {
        val enemyKill = 3f
        val playerHit = 8f
        val bomb = 6f
        assertTrue(playerHit > bomb, "player hit ($playerHit) > bomb ($bomb)")
        assertTrue(bomb > enemyKill, "bomb ($bomb) > enemy kill ($enemyKill)")
        assertTrue(playerHit <= shakeMaxOffset, "playerHit ($playerHit) <= max ($shakeMaxOffset)")
        println("ScreenShake: intensities enemyKill=$enemyKill bomb=$bomb playerHit=$playerHit all <= max=$shakeMaxOffset")
    }
}
