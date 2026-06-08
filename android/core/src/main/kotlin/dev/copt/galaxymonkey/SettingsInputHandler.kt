package dev.copt.galaxymonkey

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2

class SettingsInputHandler(
    private val hud: HUDController,
    private val audio: AudioController,
    private val haptics: Haptics,
    private val settings: SettingsStore,
) {

    enum class ActiveDrag { MUSIC, SFX }
    enum class Action { EXIT_TO_PAUSE }

    var activeDrag: ActiveDrag? = null
        private set
    private var lastSfxPreviewValue = -1f

    fun show() {
        hud.showSettingsMenu(settings)
        audio.duckMusic()
        Gdx.app.log("SettingsInput",
            "show: ducked, musicVol=${settings.musicVolume} sfxVol=${settings.sfxVolume} haptics=${settings.hapticsEnabled}")
    }

    fun dismiss() {
        hud.dismissSettingsMenu()
        audio.unduckMusic()
        activeDrag = null
        lastSfxPreviewValue = -1f
        Gdx.app.log("SettingsInput", "dismiss: unducked")
    }

    fun touchDown(touch: Vector2): Boolean {
        if (!hud.isSettingsMenuVisible) return false
        if (hud.settingsViewportContains(touch)) {
            if (hud.musicSliderHit(touch)) {
                activeDrag = ActiveDrag.MUSIC
                applySlider(touch, ActiveDrag.MUSIC)
                return true
            }
            if (hud.sfxSliderHit(touch)) {
                activeDrag = ActiveDrag.SFX
                applySlider(touch, ActiveDrag.SFX)
                return true
            }
        }
        return true
    }

    fun touchDragged(touch: Vector2): Boolean {
        val drag = activeDrag ?: return false
        applySlider(touch, drag)
        return true
    }

    fun touchUp(touch: Vector2): Action? {
        if (!hud.isSettingsMenuVisible) return null
        if (activeDrag != null) {
            activeDrag = null
            return null
        }
        return dispatchTap(touch)
    }

    private fun dispatchTap(touch: Vector2): Action? {
        if (hud.settingsBackHit(touch)) {
            haptics.impact(ImpactStyle.LIGHT)
            Gdx.app.log("SettingsInput", "tap: back")
            return Action.EXIT_TO_PAUSE
        }

        if (hud.settingsViewportContains(touch)) {
            val hapticsHit = hud.settingsHapticsHit(touch)
            if (hapticsHit != null) {
                haptics.impact(ImpactStyle.LIGHT)
                settings.hapticsEnabled = hapticsHit
                hud.updateHapticsToggleState(hapticsHit)
                Gdx.app.log("SettingsInput", "haptics toggled: $hapticsHit")
                return null
            }
            return null
        }

        if (!hud.settingsPanelContains(touch)) {
            Gdx.app.log("SettingsInput", "tap: outside panel")
            return Action.EXIT_TO_PAUSE
        }

        return null
    }

    private fun applySlider(touch: Vector2, drag: ActiveDrag) {
        when (drag) {
            ActiveDrag.MUSIC -> {
                val center = hud.musicSliderTrackCenter()
                val v = hud.sliderValueFromTouch(touch, center.x)
                audio.setMusicVolume(v)
                hud.updateMusicSliderThumb(v)
                Gdx.app.log("SettingsInput", "music: $v")
            }
            ActiveDrag.SFX -> {
                val center = hud.sfxSliderTrackCenter()
                val v = hud.sliderValueFromTouch(touch, center.x)
                audio.setSFXVolume(v)
                hud.updateSfxSliderThumb(v)
                if (lastSfxPreviewValue < 0f || kotlin.math.abs(v - lastSfxPreviewValue) >= SFX_PREVIEW_DELTA) {
                    audio.play(AudioController.Sfx.UI_TAP)
                    lastSfxPreviewValue = v
                }
                Gdx.app.log("SettingsInput", "sfx: $v")
            }
        }
    }

    companion object {
        const val SFX_PREVIEW_DELTA = 0.05f
    }
}
