package dev.copt.galaxymonkey

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20

class GameScreen(private val game: GalaxyMonkeyGame) : ScreenAdapter() {

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.02f, 0.03f, 0.08f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }
}
