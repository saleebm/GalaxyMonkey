package dev.copt.galaxymonkey

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport

class HUDController(
    private val font: BitmapFont,
    initialBest: Int = 0
) : Disposable {

    val stage: Stage = Stage(ScreenViewport())
    val shapeRenderer: ShapeRenderer = ShapeRenderer()

    var bestScore: Int = initialBest
        private set

    val inputProcessor: InputProcessor get() = stage

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    fun render(delta: Float) {
        stage.act(delta)
        stage.draw()
    }

    override fun dispose() {
        stage.dispose()
        shapeRenderer.dispose()
    }

    companion object {
        const val PAUSE_HIT_WIDTH = 280f
        const val PAUSE_HIT_HEIGHT = 48f
        const val TOGGLE_PILL_WIDTH = 80f
        const val TOGGLE_PILL_HEIGHT = 42f
        const val BACK_PILL_WIDTH = 160f
        const val BACK_PILL_HEIGHT = 46f
        const val SLIDER_TRACK_WIDTH = 200f
        const val SLIDER_TRACK_HEIGHT = 6f
        const val SLIDER_THUMB_RADIUS = 11f
        const val MAX_LIVES_ICON_SLOT = 5
    }
}
