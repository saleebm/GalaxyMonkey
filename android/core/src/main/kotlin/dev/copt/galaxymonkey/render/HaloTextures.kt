package dev.copt.galaxymonkey.render

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import kotlin.math.sqrt
import kotlin.math.roundToInt

object HaloTextures : Disposable {

    val halo256: Texture = bakeHalo(256)
    val softDot32: Texture = bakeSoftDot(32)

    val halo256Region: TextureRegion = TextureRegion(halo256)
    val softDot32Region: TextureRegion = TextureRegion(softDot32)

    override fun dispose() {
        halo256.dispose()
        softDot32.dispose()
    }

    private fun bakeHalo(size: Int): Texture {
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val center = size / 2f
        val radius = size / 2f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x.toFloat() - center
                val dy = y.toFloat() - center
                val d = (sqrt((dx * dx + dy * dy).toDouble()) / radius).coerceIn(0.0, 1.0)
                val alpha = ((1.0 - d) * 255).roundToInt()
                pixmap.drawPixel(x, y, rgba(255, 255, 255, alpha))
            }
        }
        val texture = Texture(pixmap)
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        pixmap.dispose()
        return texture
    }

    private fun bakeSoftDot(size: Int): Texture {
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val center = size / 2f
        val radius = size / 2f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x.toFloat() - center
                val dy = y.toFloat() - center
                val d = (sqrt((dx * dx + dy * dy).toDouble()) / radius).coerceIn(0.0, 1.0)
                val alpha = if (d <= 0.5) {
                    lerp(255.0, 153.0, d / 0.5)
                } else {
                    lerp(153.0, 0.0, (d - 0.5) / 0.5)
                }.roundToInt()
                pixmap.drawPixel(x, y, rgba(255, 255, 255, alpha))
            }
        }
        val texture = Texture(pixmap)
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        pixmap.dispose()
        return texture
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun rgba(r: Int, g: Int, b: Int, a: Int): Int =
        (r shl 24) or (g shl 16) or (b shl 8) or a
}
