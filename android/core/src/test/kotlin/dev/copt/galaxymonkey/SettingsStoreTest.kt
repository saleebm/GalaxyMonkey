package dev.copt.galaxymonkey

import com.badlogic.gdx.Preferences
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsStoreTest {

    private class FakePrefs : Preferences {
        val map = mutableMapOf<String, Any>()

        override fun putBoolean(key: String, v: Boolean): Preferences { map[key] = v; return this }
        override fun putInteger(key: String, v: Int): Preferences { map[key] = v; return this }
        override fun putLong(key: String, v: Long): Preferences { map[key] = v; return this }
        override fun putFloat(key: String, v: Float): Preferences { map[key] = v; return this }
        override fun putString(key: String, v: String): Preferences { map[key] = v; return this }
        override fun put(vals: MutableMap<String, *>): Preferences { map.putAll(vals.mapValues { it.value!! }); return this }

        override fun getBoolean(key: String): Boolean = map[key] as? Boolean ?: false
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun getInteger(key: String): Int = map[key] as? Int ?: 0
        override fun getInteger(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String): Long = map[key] as? Long ?: 0L
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String): Float = map[key] as? Float ?: 0f
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getString(key: String): String = map[key] as? String ?: ""
        override fun getString(key: String, defValue: String): String = map[key] as? String ?: defValue
        override fun get(): MutableMap<String, *> = map.toMutableMap()

        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun clear() { map.clear() }
        override fun remove(key: String) { map.remove(key) }
        override fun flush() {}
    }

    private lateinit var prefs: FakePrefs
    private lateinit var store: SettingsStore

    @BeforeEach
    fun setup() {
        prefs = FakePrefs()
        store = SettingsStore(prefs)
    }

    @Test
    fun `defaults when absent`() {
        assertEquals(Tuning.Settings.defaultMusicVolume, store.musicVolume)
        assertEquals(Tuning.Settings.defaultSFXVolume, store.sfxVolume)
        assertEquals(Tuning.Settings.defaultHapticsEnabled, store.hapticsEnabled)
    }

    @Test
    fun `contains vs default - stored zero is not confused with default`() {
        store.musicVolume = 0.0f
        assertEquals(0.0f, store.musicVolume, "stored 0.0 must not return default ${Tuning.Settings.defaultMusicVolume}")
    }

    @Test
    fun `clamp high - musicVolume 2 becomes 1`() {
        store.musicVolume = 2.0f
        assertEquals(1.0f, store.musicVolume)
    }

    @Test
    fun `clamp low - sfxVolume negative becomes 0`() {
        store.sfxVolume = -0.5f
        assertEquals(0.0f, store.sfxVolume)
    }

    @Test
    fun `persistence across instances`() {
        store.musicVolume = 0.42f
        store.sfxVolume = 0.77f
        store.hapticsEnabled = false

        val store2 = SettingsStore(prefs)
        assertEquals(0.42f, store2.musicVolume)
        assertEquals(0.77f, store2.sfxVolume)
        assertFalse(store2.hapticsEnabled)
    }

    @Test
    fun `key names match iOS convention`() {
        store.musicVolume = 0.5f
        store.sfxVolume = 0.5f
        store.hapticsEnabled = true

        assertTrue(prefs.map.containsKey("settings.musicVolume"))
        assertTrue(prefs.map.containsKey("settings.sfxVolume"))
        assertTrue(prefs.map.containsKey("settings.hapticsEnabled"))
        assertEquals(3, prefs.map.size, "exactly 3 keys written")
    }
}
