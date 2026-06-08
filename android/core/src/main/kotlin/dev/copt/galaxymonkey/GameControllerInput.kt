package dev.copt.galaxymonkey

import com.badlogic.gdx.controllers.Controller
import com.badlogic.gdx.controllers.ControllerListener
import com.badlogic.gdx.controllers.Controllers
import com.badlogic.gdx.math.Vector2
import kotlin.math.sqrt

class GameControllerInput : ControllerListener {

    private val connectedIds = mutableSetOf<String>()

    val hasPhysicalController: Boolean get() = connectedIds.isNotEmpty()

    val activeGamepad: Controller?
        get() = try {
            Controllers.getControllers().firstOrNull { controllerKey(it) in connectedIds }
        } catch (_: Exception) {
            null
        }

    val moveVector: Vector2
        get() {
            val pad = activeGamepad ?: return Vector2.Zero
            val rawX = pad.getAxis(0)
            val rawY = -pad.getAxis(1)
            return applyRadialDeadZone(rawX, rawY)
        }

    val aimVector: Vector2
        get() {
            val pad = activeGamepad ?: return Vector2.Zero
            val rawX = pad.getAxis(2)
            val rawY = -pad.getAxis(3)
            return applyRadialDeadZone(rawX, rawY)
        }

    init {
        try {
            for (c in Controllers.getControllers()) {
                connectedIds.add(controllerKey(c))
            }
            Controllers.addListener(this)
        } catch (_: Exception) {
            // No controller backend available (headless tests)
        }
    }

    override fun connected(controller: Controller) {
        connectedIds.add(controllerKey(controller))
    }

    override fun disconnected(controller: Controller) {
        connectedIds.remove(controllerKey(controller))
    }

    override fun buttonDown(controller: Controller, buttonCode: Int): Boolean = false
    override fun buttonUp(controller: Controller, buttonCode: Int): Boolean = false
    override fun axisMoved(controller: Controller, axisCode: Int, value: Float): Boolean = false

    private fun controllerKey(c: Controller): String = "${c.name}:${c.hashCode()}"

    companion object {
        fun applyRadialDeadZone(x: Float, y: Float): Vector2 {
            val dz = Tuning.Joystick.deadZone
            val mag = sqrt(x * x + y * y)
            if (mag <= dz) return Vector2.Zero
            val scaled = (mag - dz) / (1f - dz)
            return Vector2(x / mag * scaled, y / mag * scaled)
        }
    }
}
