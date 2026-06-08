package dev.copt.galaxymonkey

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.utils.Disposable

class GameAssets(
    resolver: FileHandleResolver = InternalFileHandleResolver()
) : Disposable {

    private val manager = AssetManager(resolver)
    private val missingSfx = mutableSetOf<AudioController.Sfx>()
    private var missingMusic = false

    val progress: Float get() = manager.progress

    fun load() {
        manager.load(ATLAS_PATH, TextureAtlas::class.java)

        val musicHandle = Gdx.files.internal(MUSIC_PATH)
        if (musicHandle.exists()) {
            manager.load(MUSIC_PATH, Music::class.java)
        } else {
            missingMusic = true
        }

        for (sfx in AudioController.Sfx.entries) {
            val path = "sounds/${sfx.filename}"
            val handle = Gdx.files.internal(path)
            if (handle.exists()) {
                manager.load(path, Sound::class.java)
            } else {
                missingSfx.add(sfx)
            }
        }
    }

    fun finishLoading() {
        manager.finishLoading()
        logLoadResult()
    }

    fun update(): Boolean {
        val done = manager.update()
        if (done) logLoadResult()
        return done
    }

    fun initCatalogs() {
        val atlas = manager.get(ATLAS_PATH, TextureAtlas::class.java)
        SpriteCatalog.init(atlas)
        AnimationCatalog.init(atlas)
    }

    fun getAtlas(): TextureAtlas = manager.get(ATLAS_PATH, TextureAtlas::class.java)

    fun getMusic(): Music? =
        if (missingMusic) null
        else try { manager.get(MUSIC_PATH, Music::class.java) } catch (_: Exception) { null }

    fun getSound(sfx: AudioController.Sfx): Sound? {
        if (sfx in missingSfx) return null
        val path = "sounds/${sfx.filename}"
        return try { manager.get(path, Sound::class.java) } catch (_: Exception) { null }
    }

    fun isMissing(sfx: AudioController.Sfx): Boolean = sfx in missingSfx

    fun getMissingSfx(): Set<AudioController.Sfx> = missingSfx.toSet()

    override fun dispose() {
        manager.dispose()
    }

    private var logged = false

    private fun logLoadResult() {
        if (logged) return
        logged = true
        val atlas = try { manager.get(ATLAS_PATH, TextureAtlas::class.java) } catch (_: Exception) { null }
        val regionCount = atlas?.regions?.size ?: 0
        val totalSfx = AudioController.Sfx.entries.size
        val presentSfx = totalSfx - missingSfx.size
        val missingNames = missingSfx.joinToString(", ") { it.name.lowercase() }
        Gdx.app.log("ASSETS-LOAD",
            "atlas OK ($regionCount regions), sounds $presentSfx/$totalSfx present" +
                if (missingSfx.isNotEmpty()) " (missing: $missingNames)" else ""
        )
    }

    companion object {
        const val ATLAS_PATH = "game.atlas"
        const val MUSIC_PATH = "sounds/bg_music.ogg"
    }
}
