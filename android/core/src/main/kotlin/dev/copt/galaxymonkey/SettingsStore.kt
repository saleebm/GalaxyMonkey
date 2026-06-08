package dev.copt.galaxymonkey

import com.badlogic.gdx.Preferences

class SettingsStore(private val prefs: Preferences) {

    var musicVolume: Float
        get() = if (prefs.contains(KEY_MUSIC_VOLUME))
            prefs.getFloat(KEY_MUSIC_VOLUME)
        else
            Tuning.Settings.defaultMusicVolume
        set(value) {
            prefs.putFloat(KEY_MUSIC_VOLUME, value.coerceIn(0f, 1f))
            prefs.flush()
        }

    var sfxVolume: Float
        get() = if (prefs.contains(KEY_SFX_VOLUME))
            prefs.getFloat(KEY_SFX_VOLUME)
        else
            Tuning.Settings.defaultSFXVolume
        set(value) {
            prefs.putFloat(KEY_SFX_VOLUME, value.coerceIn(0f, 1f))
            prefs.flush()
        }

    var hapticsEnabled: Boolean
        get() = if (prefs.contains(KEY_HAPTICS_ENABLED))
            prefs.getBoolean(KEY_HAPTICS_ENABLED)
        else
            Tuning.Settings.defaultHapticsEnabled
        set(value) {
            prefs.putBoolean(KEY_HAPTICS_ENABLED, value)
            prefs.flush()
        }

    companion object {
        const val PREFS_NAME = "galaxymonkey.settings"
        private const val KEY_MUSIC_VOLUME = "settings.musicVolume"
        private const val KEY_SFX_VOLUME = "settings.sfxVolume"
        private const val KEY_HAPTICS_ENABLED = "settings.hapticsEnabled"
    }
}
