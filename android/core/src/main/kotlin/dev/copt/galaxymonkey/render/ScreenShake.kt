package dev.copt.galaxymonkey.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import dev.copt.galaxymonkey.Tuning

// Transient camera shake: random-direction kick, exponential decay.
// Applied AFTER camera follow so it doesn't feed back into the follow
// baseline. Caller adds offsetX/offsetY to camera.position each frame.

class ScreenShake {

    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    fun applyShake(intensity: Float) {
        val clamped = intensity.coerceAtMost(Tuning.VFX.shakeMaxOffset)
        val angle = MathUtils.random(0f, MathUtils.PI2)
        offsetX = MathUtils.cos(angle) * clamped
        offsetY = MathUtils.sin(angle) * clamped
        Gdx.app.debug("ScreenShake", "shake applied intensity=$clamped")
    }

    fun update() {
        offsetX *= Tuning.VFX.shakeDecay
        offsetY *= Tuning.VFX.shakeDecay
        if (offsetX * offsetX + offsetY * offsetY < 0.01f) {
            offsetX = 0f
            offsetY = 0f
        }
    }
}
