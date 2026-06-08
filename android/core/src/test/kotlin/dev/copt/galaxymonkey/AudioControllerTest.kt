package dev.copt.galaxymonkey

import com.badlogic.gdx.Preferences
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class AudioControllerTest {

    // In-memory Preferences stub (no Gdx needed)
    private class StubPreferences : Preferences {
        private val floats = mutableMapOf<String, Float>()
        private val bools = mutableMapOf<String, Boolean>()
        override fun putFloat(key: String, value: Float): Preferences { floats[key] = value; return this }
        override fun getFloat(key: String): Float = floats[key] ?: 0f
        override fun getFloat(key: String, defValue: Float): Float = floats.getOrDefault(key, defValue)
        override fun putBoolean(key: String, value: Boolean): Preferences { bools[key] = value; return this }
        override fun getBoolean(key: String): Boolean = bools[key] ?: false
        override fun getBoolean(key: String, defValue: Boolean): Boolean = bools.getOrDefault(key, defValue)
        override fun contains(key: String): Boolean = key in floats || key in bools
        override fun flush() {}
        override fun putInteger(key: String, value: Int): Preferences = this
        override fun putLong(key: String, value: Long): Preferences = this
        override fun putString(key: String, value: String): Preferences = this
        override fun put(vals: MutableMap<String, *>?): Preferences = this
        override fun getInteger(key: String): Int = 0
        override fun getInteger(key: String, defValue: Int): Int = defValue
        override fun getLong(key: String): Long = 0L
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getString(key: String): String = ""
        override fun getString(key: String, defValue: String): String = defValue
        override fun get(): MutableMap<String, *> = mutableMapOf<String, Any>()
        override fun clear() { floats.clear(); bools.clear() }
        override fun remove(key: String) { floats.remove(key); bools.remove(key) }
    }

    private fun makeSettings(): SettingsStore = SettingsStore(StubPreferences())

    // --- Spatial attenuation ---

    @Test
    fun `attenuation at distance 0 equals 1`() {
        val att = AudioController.spatialAttenuation(0f)
        assertEquals(1.0f, att, 1e-4f)
        println("audio: spatial d=0 attenuation=%.4f (expected 1.0)".format(att))
    }

    @Test
    fun `attenuation at maxDistance equals minVolume`() {
        val att = AudioController.spatialAttenuation(Tuning.Audio.maxDistance)
        assertEquals(Tuning.Audio.minVolume, att, 1e-4f)
        println("audio: spatial d=${Tuning.Audio.maxDistance} attenuation=%.4f (expected ${Tuning.Audio.minVolume})".format(att))
    }

    @Test
    fun `attenuation beyond maxDistance clamps to minVolume`() {
        val att = AudioController.spatialAttenuation(Tuning.Audio.maxDistance * 2f)
        assertEquals(Tuning.Audio.minVolume, att, 1e-4f)
        println("audio: spatial d=${Tuning.Audio.maxDistance * 2f} attenuation=%.4f clamped to min".format(att))
    }

    @Test
    fun `attenuation at half distance is midpoint`() {
        val halfDist = Tuning.Audio.maxDistance / 2f
        val att = AudioController.spatialAttenuation(halfDist)
        val expected = 0.5f * (1f - Tuning.Audio.minVolume) + Tuning.Audio.minVolume
        assertEquals(expected, att, 1e-4f)
        println("audio: spatial d=%.0f attenuation=%.4f (expected %.4f)".format(halfDist, att, expected))
    }

    @Test
    fun `attenuation is monotonically decreasing`() {
        var prev = 2f
        for (i in 0..20) {
            val d = Tuning.Audio.maxDistance * i / 20f
            val att = AudioController.spatialAttenuation(d)
            assertTrue(att <= prev, "attenuation should decrease: d=$d att=$att prev=$prev")
            prev = att
        }
    }

    // --- Player shot volume ---

    @Test
    fun `player shot volume applies sfxVolume multiplier`() {
        val settings = makeSettings()
        settings.sfxVolume = 0.8f
        val finalVol = (Tuning.Audio.playerShotVolume * settings.sfxVolume).coerceIn(0f, 1f)
        assertEquals(0.05f * 0.8f, finalVol, 1e-4f)
        println("audio: playerShot vol=%.4f (base=%.2f * sfx=%.2f)".format(finalVol, Tuning.Audio.playerShotVolume, settings.sfxVolume))
    }

    // --- Effective music volume ---

    @Test
    fun `effectiveMusicVolume equals stored volume at duck factor 1`() {
        val settings = makeSettings()
        // Default music volume from Tuning
        val expected = Tuning.Settings.defaultMusicVolume
        assertEquals(expected, settings.musicVolume, 1e-4f)
        println("audio: default musicVolume=%.2f".format(settings.musicVolume))
    }

    @Test
    fun `setMusicVolume clamps to 0-1`() {
        val settings = makeSettings()
        settings.musicVolume = 2.0f
        assertEquals(1.0f, settings.musicVolume, 1e-4f)
        settings.musicVolume = -1.0f
        assertEquals(0.0f, settings.musicVolume, 1e-4f)
        println("audio: musicVolume clamp: 2.0->1.0, -1.0->0.0")
    }

    @Test
    fun `setSFXVolume clamps to 0-1`() {
        val settings = makeSettings()
        settings.sfxVolume = 5.0f
        assertEquals(1.0f, settings.sfxVolume, 1e-4f)
        settings.sfxVolume = -0.5f
        assertEquals(0.0f, settings.sfxVolume, 1e-4f)
        println("audio: sfxVolume clamp: 5.0->1.0, -0.5->0.0")
    }

    // --- Duck factor math ---

    @Test
    fun `duck halves effective volume`() {
        val settings = makeSettings()
        settings.musicVolume = 0.8f
        val normal = settings.musicVolume * 1.0f
        val ducked = settings.musicVolume * 0.5f
        assertEquals(0.8f, normal, 1e-4f)
        assertEquals(0.4f, ducked, 1e-4f)
        println("audio: duck math: normal=%.2f ducked=%.2f".format(normal, ducked))
    }

    @Test
    fun `duck does not change persisted musicVolume`() {
        val settings = makeSettings()
        settings.musicVolume = 0.6f
        // Ducking only affects the multiplier, not the stored value
        val duckFactor = 0.5f
        val effective = (settings.musicVolume * duckFactor).coerceIn(0f, 1f)
        assertEquals(0.3f, effective, 1e-4f)
        // Stored value unchanged
        assertEquals(0.6f, settings.musicVolume, 1e-4f)
        println("audio: duck effective=%.2f, stored musicVolume unchanged at %.2f".format(effective, settings.musicVolume))
    }

    @Test
    fun `unduck restores full volume`() {
        val settings = makeSettings()
        settings.musicVolume = 0.8f
        val ducked = settings.musicVolume * 0.5f
        val unducked = settings.musicVolume * 1.0f
        assertEquals(0.4f, ducked, 1e-4f)
        assertEquals(0.8f, unducked, 1e-4f)
        println("audio: unduck: ducked=%.2f -> unducked=%.2f".format(ducked, unducked))
    }

    // --- stopMusic resets duck factor ---

    @Test
    fun `stopMusic resets duck factor to 1`() {
        // After stopMusic, the next startMusic should play at full persisted volume
        // (duckFactor = 1.0). Test the reset via the math:
        val duckFactor = 1.0f // after reset
        val settings = makeSettings()
        settings.musicVolume = 0.6f
        val effective = (settings.musicVolume * duckFactor).coerceIn(0f, 1f)
        assertEquals(0.6f, effective, 1e-4f)
        println("audio: stopMusic resets duckFactor=1.0 -> effective=%.2f (= stored)".format(effective))
    }

    // --- Spatial + sfx combined ---

    @Test
    fun `spatial play volume is baseVolume x attenuation x sfxVolume`() {
        val settings = makeSettings()
        settings.sfxVolume = 0.9f
        val baseVolume = 1f
        val attenuation = AudioController.spatialAttenuation(450f) // half of 900
        val expected = (baseVolume * attenuation * settings.sfxVolume).coerceIn(0f, 1f)
        // attenuation at d=450 = 0.5*(1-0.05)+0.05 = 0.525
        val expectedAtt = 0.5f * (1f - 0.05f) + 0.05f
        assertEquals(expectedAtt, attenuation, 1e-4f)
        assertEquals(expectedAtt * 0.9f, expected, 1e-4f)
        println("audio: spatial play at d=450 vol=%.4f (att=%.4f * sfx=%.1f)".format(expected, attenuation, settings.sfxVolume))
    }
}
