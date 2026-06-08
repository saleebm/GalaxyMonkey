package dev.copt.galaxymonkey.render

import com.badlogic.gdx.math.Vector2
import dev.copt.galaxymonkey.Tuning
import kotlin.math.min
import kotlin.math.sqrt

// Dead-zone camera follow: camera doesn't move until the player drifts
// past deadzoneRadius, then eases toward the player. Runs BEFORE
// starfield/planet parallax so they read the post-step camera position.

class CameraFollow {

    var cameraX = 0f
        private set
    var cameraY = 0f
        private set

    var lastCameraX = 0f
        private set
    var lastCameraY = 0f
        private set

    val cameraDeltaX: Float get() = cameraX - lastCameraX
    val cameraDeltaY: Float get() = cameraY - lastCameraY

    fun update(dt: Float, targetX: Float, targetY: Float) {
        lastCameraX = cameraX
        lastCameraY = cameraY

        val dx = targetX - cameraX
        val dy = targetY - cameraY
        val dist = sqrt(dx * dx + dy * dy)

        if (dist > Tuning.Camera.deadzoneRadius) {
            val pull = (dist - Tuning.Camera.deadzoneRadius) / dist *
                min(1f, dt * Tuning.Camera.followLerpPerSec)
            cameraX += dx * pull
            cameraY += dy * pull
        }
    }

    fun snapTo(x: Float, y: Float) {
        cameraX = x
        cameraY = y
        lastCameraX = x
        lastCameraY = y
    }
}
