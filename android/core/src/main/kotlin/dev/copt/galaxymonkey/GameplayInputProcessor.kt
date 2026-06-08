package dev.copt.galaxymonkey

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter

class GameplayInputProcessor(
    val moveStick: VirtualJoystick,
    val aimStick: VirtualJoystick,
    private val viewWidth: () -> Float = { Gdx.graphics.width.toFloat() },
    private val viewHeight: () -> Float = { Gdx.graphics.height.toFloat() },
) : InputAdapter() {

    private fun flipY(screenY: Int): Float = viewHeight() - screenY

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val x = screenX.toFloat()
        val y = flipY(screenY)
        val vw = viewWidth()
        val claimedMove = moveStick.begin(pointer, x, y, vw)
        val claimedAim = aimStick.begin(pointer, x, y, vw)
        return claimedMove || claimedAim
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val x = screenX.toFloat()
        val y = flipY(screenY)
        moveStick.moved(pointer, x, y)
        aimStick.moved(pointer, x, y)
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        moveStick.ended(pointer)
        aimStick.ended(pointer)
        return true
    }

    fun cancelAll() {
        moveStick.cancelAll()
        aimStick.cancelAll()
    }
}
