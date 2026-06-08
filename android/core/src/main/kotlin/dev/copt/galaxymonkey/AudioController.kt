package dev.copt.galaxymonkey

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable

// Central audio wrapper. Missing files are graceful no-ops (ElevenLabs
// pipeline fills them in incrementally). Port of AudioController.swift.

class AudioController(
    private val settings: SettingsStore,
    var listenerProvider: () -> Vector2 = { Vector2.Zero },
) : Disposable {

    enum class Sfx(val filename: String) {
        PLAYER_SHOT("shot.ogg"),
        ENEMY_SHOT("enemy_shot.ogg"),
        EXPLOSION("explosion.ogg"),
        WARP("warp.ogg"),
        PICKUP("pickup.ogg"),
        PLAYER_HIT("player_hit.ogg"),
        WAVE_START("wave_start.ogg"),
        GAME_OVER("game_over.ogg"),
        UI_TAP("ui_tap.ogg"),
    }

    private var bgMusic: Music? = null
    private var musicStarted = false
    private var musicDuckFactor = 1f
    private var missingMusic = false

    private val soundCache = mutableMapOf<Sfx, Sound>()
    private val missingSound = mutableSetOf<Sfx>()
    private val verifiedSound = mutableSetOf<Sfx>()

    private fun ensureExists(sfx: Sfx): Sound? {
        if (sfx in missingSound) return null
        soundCache[sfx]?.let { return it }
        val path = "sounds/${sfx.filename}"
        return try {
            if (!Gdx.files.internal(path).exists()) {
                missingSound.add(sfx)
                Gdx.app.log("AudioController", "SFX missing (no-op): ${sfx.filename}")
                null
            } else {
                val sound = Gdx.audio.newSound(Gdx.files.internal(path))
                soundCache[sfx] = sound
                verifiedSound.add(sfx)
                sound
            }
        } catch (e: Exception) {
            missingSound.add(sfx)
            Gdx.app.log("AudioController", "SFX load failed (no-op): ${sfx.filename} — ${e.message}")
            null
        }
    }

    // --- Music ---

    fun startMusic(filename: String = "bg_music.ogg") {
        if (musicStarted) return
        val path = "sounds/$filename"
        try {
            if (!Gdx.files.internal(path).exists()) {
                missingMusic = true
                Gdx.app.log("AudioController", "Music missing (no-op): $filename")
                return
            }
            val music = Gdx.audio.newMusic(Gdx.files.internal(path))
            music.isLooping = true
            music.volume = effectiveMusicVolume()
            music.play()
            bgMusic = music
            musicStarted = true
            Gdx.app.log("AudioController", "Music started: $filename vol=${effectiveMusicVolume()}")
        } catch (e: Exception) {
            missingMusic = true
            Gdx.app.log("AudioController", "Music load failed: $filename — ${e.message}")
        }
    }

    fun stopMusic() {
        bgMusic?.stop()
        bgMusic?.dispose()
        bgMusic = null
        musicStarted = false
        musicDuckFactor = 1f
        Gdx.app.log("AudioController", "Music stopped, duckFactor reset to 1.0")
    }

    fun duckMusic() {
        musicDuckFactor = 0.5f
        applyMusicVolume()
        Gdx.app.log("AudioController", "Music ducked: vol=${effectiveMusicVolume()}")
    }

    fun unduckMusic() {
        musicDuckFactor = 1f
        applyMusicVolume()
        Gdx.app.log("AudioController", "Music unducked: vol=${effectiveMusicVolume()}")
    }

    fun setMusicVolume(v: Float) {
        settings.musicVolume = v
        applyMusicVolume()
        Gdx.app.log("AudioController", "Music volume set: stored=${settings.musicVolume} effective=${effectiveMusicVolume()}")
    }

    fun setSFXVolume(v: Float) {
        settings.sfxVolume = v
        Gdx.app.log("AudioController", "SFX volume set: ${settings.sfxVolume}")
    }

    internal fun effectiveMusicVolume(): Float =
        (settings.musicVolume * musicDuckFactor).coerceIn(0f, 1f)

    private fun applyMusicVolume() {
        bgMusic?.volume = effectiveMusicVolume()
    }

    // --- SFX ---

    fun play(sfx: Sfx, volume: Float = 1f) {
        val sound = ensureExists(sfx) ?: return
        val finalVol = (volume * settings.sfxVolume).coerceIn(0f, 1f)
        sound.play(finalVol)
    }

    fun play(sfx: Sfx, at: Vector2, baseVolume: Float = 1f) {
        val listener = listenerProvider()
        val d = listener.dst(at)
        val attenuation = spatialAttenuation(d)
        play(sfx, baseVolume * attenuation)
    }

    internal val duckFactor: Float get() = musicDuckFactor

    companion object {
        fun spatialAttenuation(distance: Float): Float {
            val t = (distance / Tuning.Audio.maxDistance).coerceIn(0f, 1f)
            return (1f - t) * (1f - Tuning.Audio.minVolume) + Tuning.Audio.minVolume
        }
    }

    override fun dispose() {
        bgMusic?.stop()
        bgMusic?.dispose()
        bgMusic = null
        for (sound in soundCache.values) sound.dispose()
        soundCache.clear()
    }
}
