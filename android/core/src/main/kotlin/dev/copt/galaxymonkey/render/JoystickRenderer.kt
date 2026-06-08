package dev.copt.galaxymonkey.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import dev.copt.galaxymonkey.Tuning
import dev.copt.galaxymonkey.VirtualJoystick

class JoystickRenderer {

    private val shapeRenderer = ShapeRenderer()

    fun render(vararg sticks: VirtualJoystick) {
        val anyActive = sticks.any { it.isActive }
        if (!anyActive) return

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix.setToOrtho2D(
            0f, 0f,
            Gdx.graphics.width.toFloat(),
            Gdx.graphics.height.toFloat()
        )

        for (stick in sticks) {
            if (!stick.isActive) continue
            val cx = stick.position.x
            val cy = stick.position.y

            // Base fill
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(1f, 1f, 1f, Tuning.Joystick.baseAlpha)
            shapeRenderer.circle(cx, cy, Tuning.Joystick.radius, 48)
            shapeRenderer.end()

            // Base stroke
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.setColor(1f, 1f, 1f, Tuning.Joystick.strokeAlpha)
            shapeRenderer.circle(cx, cy, Tuning.Joystick.radius, 48)
            shapeRenderer.end()

            // Thumb fill
            val tx = cx + stick.thumbOffset.x
            val ty = cy + stick.thumbOffset.y
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(1f, 1f, 1f, Tuning.Joystick.thumbAlpha)
            shapeRenderer.circle(tx, ty, Tuning.Joystick.thumbRadius, 32)
            shapeRenderer.end()

            // Thumb stroke
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.setColor(1f, 1f, 1f, Tuning.Joystick.strokeAlpha)
            shapeRenderer.circle(tx, ty, Tuning.Joystick.thumbRadius, 32)
            shapeRenderer.end()
        }

        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    fun dispose() {
        shapeRenderer.dispose()
    }
}
