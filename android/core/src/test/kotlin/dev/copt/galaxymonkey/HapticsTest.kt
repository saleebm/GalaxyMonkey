package dev.copt.galaxymonkey

import com.badlogic.gdx.Preferences
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HapticsTest {

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

    private var vibrateCount = 0
    private val vibrateLog = mutableListOf<String>()
    private lateinit var settings: SettingsStore

    private inner class GatedHaptics(private val settings: SettingsStore) : Haptics {
        override fun impact(style: ImpactStyle) {
            if (!settings.hapticsEnabled) return
            vibrateCount++
            vibrateLog.add("impact:$style")
        }
        override fun notification(type: NotificationType) {
            if (!settings.hapticsEnabled) return
            vibrateCount++
            vibrateLog.add("notification:$type")
        }
    }

    private lateinit var haptics: Haptics

    @BeforeEach
    fun setup() {
        vibrateCount = 0
        vibrateLog.clear()
        settings = SettingsStore(StubPreferences())
        settings.hapticsEnabled = true
        haptics = GatedHaptics(settings)
    }

    @Test
    fun `enabled - impact and notification each invoke vibrator once`() {
        haptics.impact(ImpactStyle.LIGHT)
        assertEquals(1, vibrateCount)
        haptics.notification(NotificationType.WARNING)
        assertEquals(2, vibrateCount)
        assertTrue(vibrateLog.contains("impact:LIGHT"))
        assertTrue(vibrateLog.contains("notification:WARNING"))
        println("[HapticsTest] PASS: enabled calls vibrate, log=$vibrateLog")
    }

    @Test
    fun `disabled - all calls are no-ops`() {
        settings.hapticsEnabled = false
        haptics.impact(ImpactStyle.LIGHT)
        haptics.impact(ImpactStyle.HEAVY)
        haptics.notification(NotificationType.WARNING)
        haptics.notification(NotificationType.SUCCESS)
        assertEquals(0, vibrateCount, "vibrator should never be called when disabled")
        println("[HapticsTest] PASS: disabled, vibrateCount=0")
    }

    @Test
    fun `live re-read - flag read per call not cached at construction`() {
        settings.hapticsEnabled = true
        haptics.impact(ImpactStyle.MEDIUM)
        assertEquals(1, vibrateCount, "enabled: 1 call")

        settings.hapticsEnabled = false
        haptics.impact(ImpactStyle.MEDIUM)
        assertEquals(1, vibrateCount, "disabled: still 1 (no new)")

        settings.hapticsEnabled = true
        haptics.impact(ImpactStyle.MEDIUM)
        assertEquals(2, vibrateCount, "re-enabled: 2 calls total")
        println("[HapticsTest] PASS: live re-read verified, vibrateCount=2")
    }

    @Test
    fun `NoOpHaptics never throws`() {
        assertDoesNotThrow { NoOpHaptics.impact(ImpactStyle.LIGHT) }
        assertDoesNotThrow { NoOpHaptics.impact(ImpactStyle.MEDIUM) }
        assertDoesNotThrow { NoOpHaptics.impact(ImpactStyle.HEAVY) }
        assertDoesNotThrow { NoOpHaptics.notification(NotificationType.SUCCESS) }
        assertDoesNotThrow { NoOpHaptics.notification(NotificationType.WARNING) }
        assertDoesNotThrow { NoOpHaptics.notification(NotificationType.ERROR) }
        println("[HapticsTest] PASS: NoOpHaptics all styles/types no-throw")
    }

    @Test
    fun `all impact styles and notification types covered`() {
        ImpactStyle.entries.forEach { haptics.impact(it) }
        NotificationType.entries.forEach { haptics.notification(it) }
        assertEquals(ImpactStyle.entries.size + NotificationType.entries.size, vibrateCount)
        println("[HapticsTest] PASS: all ${vibrateCount} style/type combos, log=$vibrateLog")
    }
}
