package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2

interface ControllerInput {
    val hasPhysicalController: Boolean
    val moveVector: Vector2
    val aimVector: Vector2
}

class InputSource(
    private val controller: ControllerInput,
    private val moveStick: VirtualJoystick,
    private val aimStick: VirtualJoystick,
) {

    val isUsingController: Boolean get() = controller.hasPhysicalController

    fun moveVector(): Vector2 =
        if (controller.hasPhysicalController) controller.moveVector else moveStick.vector

    fun aimVector(): Vector2 =
        if (controller.hasPhysicalController) controller.aimVector else aimStick.vector
}
