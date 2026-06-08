package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import kotlin.math.min
import kotlin.math.sqrt

enum class JoystickSide { LEFT, RIGHT }

class VirtualJoystick(
    val side: JoystickSide,
    private val baseRadius: Float = Tuning.Joystick.radius,
    private val thumbRadius: Float = Tuning.Joystick.thumbRadius,
    private val deadZone: Float = Tuning.Joystick.deadZone
) {
    private var trackedTouch: Int = -1

    val vector: Vector2 = Vector2()
    val anchor: Vector2 = Vector2()
    val thumbOffset: Vector2 = Vector2()
    val position: Vector2 = Vector2()

    val isActive: Boolean get() = trackedTouch != -1

    fun begin(pointerId: Int, x: Float, y: Float, viewWidth: Float): Boolean {
        if (trackedTouch != -1) return false
        val onLeft = x < viewWidth / 2f
        val claim = (side == JoystickSide.LEFT && onLeft) || (side == JoystickSide.RIGHT && !onLeft)
        if (!claim) return false

        trackedTouch = pointerId
        anchor.set(x, y)
        position.set(x, y)
        thumbOffset.set(0f, 0f)
        vector.set(0f, 0f)
        return true
    }

    fun moved(pointerId: Int, x: Float, y: Float) {
        if (trackedTouch != pointerId) return

        val dx = x - anchor.x
        val dy = y - anchor.y
        val dist = sqrt(dx * dx + dy * dy)
        val clamped = min(dist, baseRadius)

        val nx = if (dist == 0f) 0f else dx / dist
        val ny = if (dist == 0f) 0f else dy / dist

        thumbOffset.set(nx * clamped, ny * clamped)

        val mag = clamped / baseRadius
        val outMag = if (mag < deadZone) 0f else (mag - deadZone) / (1f - deadZone)
        vector.set(nx * outMag, ny * outMag)
    }

    fun ended(pointerId: Int) {
        if (trackedTouch != pointerId) return
        trackedTouch = -1
        vector.set(0f, 0f)
    }

    fun cancelAll() {
        trackedTouch = -1
        vector.set(0f, 0f)
    }
}
