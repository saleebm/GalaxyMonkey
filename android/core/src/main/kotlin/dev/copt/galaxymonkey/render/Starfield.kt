package dev.copt.galaxymonkey.render

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import dev.copt.galaxymonkey.Tuning
import kotlin.math.abs

// Two baked parallax star layers tiled 3x3 around the camera so they
// wrap seamlessly, plus screen-locked async twinkle dots. Port of
// Starfield.swift — all drawing is screen-space (camera-fixed).

class Starfield(viewWidth: Float, viewHeight: Float) : Disposable {

    private class Layer(
        var texture: Texture,
        val parallaxFactor: Float,
        var tileW: Float,
        var tileH: Float,
    ) {
        var offsetX = 0f
        var offsetY = 0f
    }

    private class TwinkleDot(
        val x: Float,
        val y: Float,
        val period: Float,
        val phase: Float,
    )

    private var viewW = viewWidth
    private var viewH = viewHeight
    private val layers = mutableListOf<Layer>()
    private val twinkleDots = mutableListOf<TwinkleDot>()
    private var twinkleTexture: Texture? = null
    private var elapsed = 0f

    init {
        rebuild(viewWidth, viewHeight)
    }

    fun update(dt: Float, cameraDeltaX: Float, cameraDeltaY: Float) {
        elapsed += dt
        for (layer in layers) {
            layer.offsetX -= cameraDeltaX * (1f - layer.parallaxFactor)
            layer.offsetY -= cameraDeltaY * (1f - layer.parallaxFactor)

            val tw = layer.tileW
            val th = layer.tileH
            if (tw > 0f) {
                while (layer.offsetX > tw) layer.offsetX -= tw
                while (layer.offsetX < -tw) layer.offsetX += tw
            }
            if (th > 0f) {
                while (layer.offsetY > th) layer.offsetY -= th
                while (layer.offsetY < -th) layer.offsetY += th
            }
        }
    }

    fun draw(batch: SpriteBatch) {
        for (layer in layers) {
            val tw = layer.tileW
            val th = layer.tileH
            val cx = viewW / 2f
            val cy = viewH / 2f
            for (gy in -1..1) {
                for (gx in -1..1) {
                    val x = cx + gx * tw + layer.offsetX - tw / 2f
                    val y = cy + gy * th + layer.offsetY - th / 2f
                    batch.draw(layer.texture, x, y, tw, th)
                }
            }
        }

        val tex = twinkleTexture ?: return
        val dotSize = Tuning.Starfield.twinkleRadius * 2f
        for (dot in twinkleDots) {
            val t = (elapsed + dot.phase) / dot.period
            val tri = 1f - 2f * abs(t % 1f - 0.5f)
            val smooth = tri * tri * (3f - 2f * tri)
            val alpha = MathUtils.lerp(Tuning.Starfield.twinkleAlphaLow, 1f, smooth)
            batch.setColor(1f, 1f, 1f, alpha)
            batch.draw(tex, dot.x - dotSize / 2f, dot.y - dotSize / 2f, dotSize, dotSize)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    fun resize(width: Float, height: Float) {
        rebuild(width, height)
    }

    private fun rebuild(width: Float, height: Float) {
        for (layer in layers) layer.texture.dispose()
        layers.clear()
        twinkleTexture?.dispose()
        twinkleDots.clear()

        viewW = width
        viewH = height

        val tileW = maxOf(64f, width)
        val tileH = maxOf(64f, height)

        layers.add(makeLayer(
            tileW, tileH,
            Tuning.Starfield.layer1Count, 1.4f, 0.65f,
            Tuning.Starfield.layer1Speed
        ))
        layers.add(makeLayer(
            tileW, tileH,
            Tuning.Starfield.layer2Count, 2.2f, 0.95f,
            Tuning.Starfield.layer2Speed
        ))

        twinkleTexture = bakeDotTexture(8)
        for (i in 0 until Tuning.Starfield.twinkleCount) {
            twinkleDots.add(TwinkleDot(
                x = MathUtils.random(0f, width),
                y = MathUtils.random(0f, height),
                period = MathUtils.random(
                    Tuning.Starfield.twinklePeriodMin,
                    Tuning.Starfield.twinklePeriodMax
                ),
                phase = MathUtils.random(
                    0f, Tuning.Starfield.twinklePeriodMax
                )
            ))
        }
    }

    private fun makeLayer(
        tileW: Float, tileH: Float,
        dotCount: Int, dotRadius: Float, dotAlpha: Float,
        parallaxFactor: Float,
    ): Layer {
        val texture = bakeStarTexture(
            tileW.toInt(), tileH.toInt(), dotCount, dotRadius, dotAlpha
        )
        return Layer(texture, parallaxFactor, tileW, tileH)
    }

    private fun bakeStarTexture(
        w: Int, h: Int, dotCount: Int, dotRadius: Float, dotAlpha: Float,
    ): Texture {
        val pixmap = Pixmap(w, h, Pixmap.Format.RGBA8888)
        pixmap.setColor(1f, 1f, 1f, dotAlpha)
        val r = dotRadius.toInt().coerceAtLeast(1)
        for (i in 0 until dotCount) {
            val x = MathUtils.random(r, w - 1 - r)
            val y = MathUtils.random(r, h - 1 - r)
            pixmap.fillCircle(x, y, r)
        }
        val texture = Texture(pixmap)
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
        pixmap.dispose()
        return texture
    }

    private fun bakeDotTexture(size: Int): Texture {
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pixmap.setColor(1f, 1f, 1f, 1f)
        pixmap.fillCircle(size / 2, size / 2, size / 2)
        val texture = Texture(pixmap)
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear)
        pixmap.dispose()
        return texture
    }

    override fun dispose() {
        for (layer in layers) layer.texture.dispose()
        layers.clear()
        twinkleTexture?.dispose()
        twinkleTexture = null
        twinkleDots.clear()
    }
}
