package dev.copt.galaxymonkey

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch

class GalaxyMonkeyGame : Game() {

    lateinit var batch: SpriteBatch
        private set
    lateinit var font: BitmapFont
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var audio: AudioController
        private set
    var haptics: Haptics = NoOpHaptics

    private var assets: GameAssets? = null

    override fun create() {
        Gdx.app.log("GalaxyMonkeyGame", "create()")
        batch = SpriteBatch()
        font = BitmapFont()

        val settingsPrefs = Gdx.app.getPreferences(SettingsStore.PREFS_NAME)
        settings = SettingsStore(settingsPrefs)
        audio = AudioController(settings)

        try {
            val ga = GameAssets()
            ga.load()
            ga.finishLoading()
            ga.initCatalogs()
            assets = ga
        } catch (e: Exception) {
            Gdx.app.log("GalaxyMonkeyGame", "Asset load skipped: ${e.message}")
        }

        val gamePrefs = Gdx.app.getPreferences(GameScreen.PREFS_NAME)
        setScreen(GameScreen(this, gamePrefs))
    }

    override fun render() {
        super.render()
    }

    override fun dispose() {
        Gdx.app.log("GalaxyMonkeyGame", "dispose()")
        screen?.dispose()
        audio.dispose()
        assets?.dispose()
        batch.dispose()
        font.dispose()
    }
}
