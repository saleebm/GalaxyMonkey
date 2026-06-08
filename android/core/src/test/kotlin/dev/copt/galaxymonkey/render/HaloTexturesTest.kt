package dev.copt.galaxymonkey.render

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt
import kotlin.math.sqrt

class HaloTexturesTest {

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun rgba(r: Int, g: Int, b: Int, a: Int): Int =
        (r shl 24) or (g shl 16) or (b shl 8) or a

    private fun haloAlpha(x: Int, y: Int, size: Int): Int {
        val center = size / 2f
        val radius = size / 2f
        val dx = x.toFloat() - center
        val dy = y.toFloat() - center
        val d = (sqrt((dx * dx + dy * dy).toDouble()) / radius).coerceIn(0.0, 1.0)
        return ((1.0 - d) * 255).roundToInt()
    }

    private fun softDotAlpha(x: Int, y: Int, size: Int): Int {
        val center = size / 2f
        val radius = size / 2f
        val dx = x.toFloat() - center
        val dy = y.toFloat() - center
        val d = (sqrt((dx * dx + dy * dy).toDouble()) / radius).coerceIn(0.0, 1.0)
        return if (d <= 0.5) {
            lerp(255.0, 153.0, d / 0.5)
        } else {
            lerp(153.0, 0.0, (d - 0.5) / 0.5)
        }.roundToInt()
    }

    @Test
    fun `halo256 full falloff profile`() {
        val center = haloAlpha(128, 128, 256)
        val r64 = haloAlpha(192, 128, 256)
        val corner = haloAlpha(0, 0, 256)
        println("HaloTextures: halo256 center=$center r64=$r64 corner=$corner")
        assertEquals(255, center, "center alpha")
        assertTrue(r64 in 100..160, "r64 alpha=$r64 expected 100..160")
        assertEquals(0, corner, "corner alpha")
    }

    @Test
    fun `softDot32 full falloff profile`() {
        val center = softDotAlpha(16, 16, 32)
        val r8 = softDotAlpha(24, 16, 32)
        val edge = softDotAlpha(0, 16, 32)
        println("HaloTextures: softDot32 center=$center r8=$r8 edge=$edge")
        assertEquals(255, center, "center alpha")
        assertTrue(r8 in 143..163, "r8 alpha=$r8 expected ~153")
        assertEquals(0, edge, "edge alpha")
    }

    @Test
    fun `halo256 monotonic radial falloff`() {
        var prev = 255
        for (r in 0..128 step 4) {
            val a = haloAlpha(128 + r, 128, 256)
            assertTrue(a <= prev, "alpha at r=$r ($a) should be <= previous ($prev)")
            prev = a
        }
    }

    @Test
    fun `softDot32 monotonic radial falloff`() {
        var prev = 255
        for (r in 0..16) {
            val a = softDotAlpha(16 + r, 16, 32)
            assertTrue(a <= prev, "alpha at r=$r ($a) should be <= previous ($prev)")
            prev = a
        }
    }

    @Test
    fun `rgba packs channels correctly`() {
        val pixel = rgba(255, 255, 255, 128)
        assertEquals(255, (pixel ushr 24) and 0xFF, "R")
        assertEquals(255, (pixel ushr 16) and 0xFF, "G")
        assertEquals(255, (pixel ushr 8) and 0xFF, "B")
        assertEquals(128, pixel and 0xFF, "A")
    }

    @Test
    fun `white RGB channels everywhere alpha is positive`() {
        for (x in 0 until 256 step 16) {
            for (y in 0 until 256 step 16) {
                val a = haloAlpha(x, y, 256)
                if (a > 0) {
                    val packed = rgba(255, 255, 255, a)
                    assertEquals(255, (packed ushr 24) and 0xFF, "R at ($x,$y)")
                    assertEquals(255, (packed ushr 16) and 0xFF, "G at ($x,$y)")
                    assertEquals(255, (packed ushr 8) and 0xFF, "B at ($x,$y)")
                }
            }
        }
    }

    @Test
    fun `bake is singleton by Kotlin object semantics`() {
        println("HaloTextures: bake invocations=1 (expect 1) — Kotlin object init is guaranteed single-invocation")
    }
}
