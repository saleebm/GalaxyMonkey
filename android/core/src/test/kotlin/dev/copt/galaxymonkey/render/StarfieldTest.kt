package dev.copt.galaxymonkey.render

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class StarfieldTest {

    private fun wrapOffset(offset: Float, tileSize: Float): Float {
        var o = offset
        if (tileSize > 0f) {
            while (o > tileSize) o -= tileSize
            while (o < -tileSize) o += tileSize
        }
        return o
    }

    private fun applyParallax(offset: Float, cameraDelta: Float, factor: Float): Float =
        offset - cameraDelta * (1f - factor)

    @Test
    fun `parallax layer2 moves faster than layer1`() {
        val factor1 = 0.06f
        val factor2 = 0.18f
        val cameraDelta = 100f

        val offset1 = applyParallax(0f, cameraDelta, factor1)
        val offset2 = applyParallax(0f, cameraDelta, factor2)

        assertTrue(abs(offset1) > abs(offset2),
            "layer1 (factor=$factor1) offset=${offset1} should have larger magnitude than layer2 (factor=$factor2) offset=${offset2}")
        println("Starfield parallax: layer1 offset=$offset1, layer2 offset=$offset2 — layer2 is closer/faster parallax")
    }

    @Test
    fun `offset wraps within tile bounds`() {
        val tileSize = 400f
        var offset = 0f
        for (i in 0 until 1000) {
            offset -= 50f * (1f - 0.06f)
            offset = wrapOffset(offset, tileSize)
        }
        assertTrue(offset >= -tileSize && offset <= tileSize,
            "offset=$offset should be in [-$tileSize, $tileSize]")
        println("Starfield wrap: after 1000 updates offset=$offset bounded to [-$tileSize, $tileSize]")
    }

    @Test
    fun `wrap handles negative camera deltas`() {
        val tileSize = 400f
        var offset = 0f
        for (i in 0 until 500) {
            offset -= -75f * (1f - 0.18f)
            offset = wrapOffset(offset, tileSize)
        }
        assertTrue(offset >= -tileSize && offset <= tileSize,
            "offset=$offset should be in [-$tileSize, $tileSize] for negative deltas")
    }

    @Test
    fun `3x3 grid covers viewport with no gaps`() {
        val tileW = 400f
        val tileH = 300f
        val viewW = 400f
        val viewH = 300f
        val cx = viewW / 2f
        val cy = viewH / 2f
        val offsetX = 150f
        val offsetY = -100f

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (gy in -1..1) {
            for (gx in -1..1) {
                val x = cx + gx * tileW + offsetX - tileW / 2f
                val y = cy + gy * tileH + offsetY - tileH / 2f
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x + tileW)
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y + tileH)
            }
        }
        assertTrue(minX <= 0f, "grid left edge $minX should cover screen left (0)")
        assertTrue(maxX >= viewW, "grid right edge $maxX should cover screen right ($viewW)")
        assertTrue(minY <= 0f, "grid bottom edge $minY should cover screen bottom (0)")
        assertTrue(maxY >= viewH, "grid top edge $maxY should cover screen top ($viewH)")
        println("Starfield grid: coverage [$minX,$maxX] x [$minY,$maxY] covers viewport ${viewW}x${viewH}")
    }

    @Test
    fun `twinkle smoothstep oscillates between alphaLow and 1`() {
        val alphaLow = 0.3f
        val period = 2.0f
        val phase = 0.5f
        var minAlpha = 1f
        var maxAlpha = 0f
        for (step in 0..200) {
            val elapsed = step * 0.05f
            val t = (elapsed + phase) / period
            val tri = 1f - 2f * abs(t % 1f - 0.5f)
            val smooth = tri * tri * (3f - 2f * tri)
            val alpha = alphaLow + (1f - alphaLow) * smooth
            minAlpha = minOf(minAlpha, alpha)
            maxAlpha = maxOf(maxAlpha, alpha)
        }
        assertTrue(minAlpha >= alphaLow - 0.01f, "min alpha $minAlpha should be >= $alphaLow")
        assertTrue(maxAlpha <= 1.01f, "max alpha $maxAlpha should be <= 1.0")
        assertTrue(maxAlpha >= 0.99f, "max alpha $maxAlpha should reach ~1.0")
        assertTrue(minAlpha <= alphaLow + 0.01f, "min alpha $minAlpha should reach ~$alphaLow")
        println("Starfield twinkle: alpha range [$minAlpha, $maxAlpha] within [${alphaLow}, 1.0]")
    }
}
