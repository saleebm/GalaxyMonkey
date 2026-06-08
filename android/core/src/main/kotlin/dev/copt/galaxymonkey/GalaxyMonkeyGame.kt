package dev.copt.galaxymonkey

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.SpriteBatch

class GalaxyMonkeyGame : Game() {

    lateinit var batch: SpriteBatch
        private set

    override fun create() {
        Gdx.app.log("GalaxyMonkeyGame", "create()")
        batch = SpriteBatch()
        setScreen(GameScreen(this))
    }

    override fun render() {
        super.render()
    }

    override fun dispose() {
        Gdx.app.log("GalaxyMonkeyGame", "dispose()")
        screen?.dispose()
        batch.dispose()
    }
}
