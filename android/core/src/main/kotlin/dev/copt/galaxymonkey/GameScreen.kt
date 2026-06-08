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

    private var accumulator = 0f
    private var firstFrameAfterReset = true

    override fun render(delta: Float) {
        val clamped = if (firstFrameAfterReset) {
            firstFrameAfterReset = false
            accumulator = 0f
            0f
        } else if (delta > MAX_FRAME_TIME) {
            Gdx.app.log("GameScreen", "frame delta ${delta}s exceeds clamp ${MAX_FRAME_TIME}s, discarding excess")
            MAX_FRAME_TIME
        } else {
            delta
        }

        accumulator += clamped
        while (accumulator >= STEP) {
            update(STEP)
            accumulator -= STEP
        }

        draw()
    }

    fun resetClock() {
        firstFrameAfterReset = true
        accumulator = 0f
    }

    private fun update(dt: Float) {
        // Gameplay simulation hook — wired by track3 systems.
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
        const val STEP = 1f / 60f
        const val MAX_FRAME_TIME = 0.25f
    }
}
