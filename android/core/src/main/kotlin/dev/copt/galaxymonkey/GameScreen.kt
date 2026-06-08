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

    private val timestep = FixedTimestep(
        step = STEP,
        maxFrameTime = MAX_FRAME_TIME,
        onClamp = { raw, clamped ->
            Gdx.app.log("GameScreen", "frame delta ${raw}s exceeds clamp ${clamped}s, discarding excess")
        },
    )

    var isStarted = false
        private set
    var isGameOver = false
        private set
    var isInPauseMenu = false
        private set
    var isInSettings = false
        private set
    var gameplayPaused = false
        private set

    override fun render(delta: Float) {
        timestep.advance(delta) { update(it) }
        draw()
    }

    fun resetClock() {
        timestep.resetClock()
    }

    private fun update(dt: Float) {
        if (!isStarted || isGameOver) return
        if (gameplayPaused) return
        // Gameplay simulation hook — wired by track3/5 systems.
    }

    private fun draw() {
        Gdx.gl.glClearColor(0.02f, 0.03f, 0.08f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()
        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.end()
    }

    // --- Lifecycle orchestration ---

    fun startGame() {
        isStarted = true
        isGameOver = false
        gameplayPaused = false
        resetClock()
        Gdx.app.log("GameScreen", "lifecycle: startGame")
    }

    fun enterPauseMenu() {
        isInPauseMenu = true
        gameplayPaused = true
        Gdx.app.log("GameScreen", "lifecycle: enterPauseMenu")
    }

    fun dismissPauseMenusAndResume() {
        isInPauseMenu = false
        isInSettings = false
        resetClock()
        gameplayPaused = false
        Gdx.app.log("GameScreen", "lifecycle: resume gameplayPaused=false")
    }

    fun enterSettings() {
        isInSettings = true
        gameplayPaused = true
        Gdx.app.log("GameScreen", "lifecycle: enterSettings")
    }

    fun dismissSettings() {
        isInSettings = false
        if (!isInPauseMenu) {
            resetClock()
            gameplayPaused = false
            Gdx.app.log("GameScreen", "lifecycle: dismissSettings, resume gameplayPaused=false")
        } else {
            Gdx.app.log("GameScreen", "lifecycle: dismissSettings, still in pause menu")
        }
    }

    fun gameOver() {
        isGameOver = true
        gameplayPaused = true
        Gdx.app.log("GameScreen", "lifecycle: gameOver")
    }

    fun restart() {
        isGameOver = false
        isInPauseMenu = false
        isInSettings = false
        gameplayPaused = false
        resetClock()
        isStarted = true
        Gdx.app.log("GameScreen", "lifecycle: restart")
    }

    fun returnToStart() {
        isStarted = false
        isGameOver = false
        isInPauseMenu = false
        isInSettings = false
        gameplayPaused = false
        resetClock()
        Gdx.app.log("GameScreen", "lifecycle: returnToStart")
    }

    override fun pause() {
        if (isStarted && !isGameOver && !isInPauseMenu && !isInSettings) {
            enterPauseMenu()
        } else {
            gameplayPaused = true
        }
        Gdx.app.log("GameScreen", "lifecycle: pause (Android home/lock)")
    }

    override fun resume() {
        if (isInPauseMenu || isInSettings) {
            Gdx.app.log("GameScreen", "lifecycle: resume, menu open — staying paused")
        } else {
            resetClock()
            gameplayPaused = false
            Gdx.app.log("GameScreen", "lifecycle: resume gameplayPaused=false")
        }
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
