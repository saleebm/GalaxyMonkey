package dev.copt.galaxymonkey

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.utils.viewport.ExtendViewport

class GameScreen(private val game: GalaxyMonkeyGame) : ScreenAdapter() {

    val camera = OrthographicCamera()
    private val viewport = ExtendViewport(WORLD_WIDTH, WORLD_HEIGHT, camera)
    private val batch get() = game.batch

    override fun render(delta: Float) {
        update(delta)
        draw()
    }

    private fun update(dt: Float) {
        // Gameplay simulation hook — wired by track3-fixedstep.
    }

    private fun draw() {
        Gdx.gl.glClearColor(0.02f, 0.03f, 0.08f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()
        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.end()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        Gdx.app.log("GameScreen", "resize ${width}x${height} -> world ${viewport.worldWidth}x${viewport.worldHeight}")
    }

    override fun dispose() {
        // SpriteBatch owned by GalaxyMonkeyGame, not disposed here.
    }

    companion object {
        const val WORLD_WIDTH = 812f
        const val WORLD_HEIGHT = 375f
    }
}
