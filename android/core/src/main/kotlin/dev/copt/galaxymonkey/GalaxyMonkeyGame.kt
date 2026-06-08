package dev.copt.galaxymonkey

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20

class GalaxyMonkeyGame : Game() {

    override fun create() {
        Gdx.app.log("GalaxyMonkeyGame", "create()")
    }

    override fun render() {
        Gdx.gl.glClearColor(0.02f, 0.03f, 0.08f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        super.render()
    }
}
