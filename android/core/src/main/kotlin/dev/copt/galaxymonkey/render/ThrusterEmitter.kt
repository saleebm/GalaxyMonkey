package dev.copt.galaxymonkey.render

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import dev.copt.galaxymonkey.Tuning

// Manual particle system for the ship's chemical-rocket exhaust plume.
// Color ramp: white-hot -> orange -> red -> smoke. Intensity modulated
// by joystick magnitude via setIntensity(). Uses softDot32 from
// HaloTextures. Port of ThrusterEmitter.swift.

class ThrusterEmitter(private val maxParticles: Int = 256) : Disposable {

    private class Particle {
        var alive = false
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var age = 0f
        var lifetime = 0f
        var scale = 0f
        var scaleSpeed = 0f
        var alpha = 0f
        var alphaSpeed = 0f
    }

    // 5-stop color ramp: white-hot -> orange -> red -> brown -> smoke
    private data class ColorStop(val t: Float, val r: Float, val g: Float, val b: Float)
    private val colorRamp = arrayOf(
        ColorStop(0.00f, 1.00f, 1.00f, 0.85f),
        ColorStop(0.20f, 1.00f, 0.78f, 0.20f),
        ColorStop(0.45f, 1.00f, 0.35f, 0.05f),
        ColorStop(0.75f, 0.40f, 0.20f, 0.10f),
        ColorStop(1.00f, 0.18f, 0.18f, 0.18f),
    )

    private val particles = Array(maxParticles) { Particle() }
    private var spawnAccum = 0f
    internal var birthRate = 220f * 0.4f
        private set
    internal var particleSpeed = 160f
        private set

    var emitterX = 0f
    var emitterY = 0f
    var emitterAngle = MathUtils.PI

    fun setIntensity(mag: Float) {
        val t = mag.coerceIn(0f, 1f)
        birthRate = MathUtils.lerp(220f * 0.4f, 220f * 1.5f, t)
        particleSpeed = MathUtils.lerp(160f, 240f, t)
    }

    fun update(dt: Float) {
        // Spawn new particles
        if (birthRate > 0f) {
            spawnAccum += birthRate * dt
            while (spawnAccum >= 1f) {
                spawnAccum -= 1f
                spawn()
            }
        }

        // Advance live particles
        for (p in particles) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.lifetime) {
                p.alive = false
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.scale += p.scaleSpeed * dt
            if (p.scale < 0f) p.scale = 0f
            p.alpha += p.alphaSpeed * dt
            if (p.alpha < 0f) p.alpha = 0f
        }
    }

    fun render(batch: SpriteBatch) {
        val tex = HaloTextures.softDot32Region
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

        for (p in particles) {
            if (!p.alive) continue
            val t = (p.age / p.lifetime).coerceIn(0f, 1f)
            val (r, g, b) = sampleRamp(t)
            batch.setColor(r, g, b, p.alpha)
            val size = p.scale
            batch.draw(tex, p.x - size / 2f, p.y - size / 2f, size, size)
        }

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun spawn() {
        val p = particles.firstOrNull { !it.alive } ?: return
        p.alive = true
        p.age = 0f
        p.lifetime = 0.45f + MathUtils.random(-0.15f, 0.15f)

        val spread = MathUtils.PI / 9f
        val angle = emitterAngle + MathUtils.random(-spread, spread)
        val speed = particleSpeed + MathUtils.random(-70f, 70f)
        p.vx = MathUtils.cos(angle) * speed
        p.vy = MathUtils.sin(angle) * speed

        p.x = emitterX + MathUtils.random(-1f, 1f)
        p.y = emitterY + MathUtils.random(-7f, 7f)

        val baseScale = Tuning.VFX.thrustScale * 0.5f
        p.scale = (baseScale + MathUtils.random(-0.25f, 0.25f)).coerceAtLeast(0.1f)
        p.scaleSpeed = -0.6f
        p.alpha = 1f + MathUtils.random(-0.1f, 0.1f)
        p.alphaSpeed = -2.2f
    }

    internal val aliveCount: Int get() = particles.count { it.alive }

    internal fun sampleRamp(t: Float): Triple<Float, Float, Float> {
        if (t <= 0f) return Triple(colorRamp[0].r, colorRamp[0].g, colorRamp[0].b)
        for (i in 1 until colorRamp.size) {
            if (t <= colorRamp[i].t) {
                val a = colorRamp[i - 1]
                val b = colorRamp[i]
                val f = (t - a.t) / (b.t - a.t)
                return Triple(
                    MathUtils.lerp(a.r, b.r, f),
                    MathUtils.lerp(a.g, b.g, f),
                    MathUtils.lerp(a.b, b.b, f),
                )
            }
        }
        val last = colorRamp.last()
        return Triple(last.r, last.g, last.b)
    }

    override fun dispose() {}
}
