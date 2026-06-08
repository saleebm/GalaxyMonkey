package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.IntBuffer

class SettingsLiveWiringTest {

    companion object {
        private lateinit var app: HeadlessApplication

        private fun stubGL20(): GL20 = Proxy.newProxyInstance(
            GL20::class.java.classLoader, arrayOf(GL20::class.java)
        ) { _, method, args ->
            when (method.name) {
                "glCreateShader" -> 1
                "glCreateProgram" -> 1
                "glGetShaderiv" -> { (args[2] as IntBuffer).put(0, 1); null }
                "glGetProgramiv" -> {
                    val buf = args[2] as IntBuffer
                    val pname = args[1] as Int
                    buf.put(0, if (pname == GL20.GL_LINK_STATUS) 1 else 0); null
                }
                "glGetActiveUniform", "glGetActiveAttrib" -> "stub"
                "glGetUniformLocation", "glGetAttribLocation" -> 0
                "glGetError" -> 0
                "glGetString" -> ""
                else -> when (method.returnType) {
                    Int::class.java, java.lang.Integer.TYPE -> 0
                    Boolean::class.java, java.lang.Boolean.TYPE -> false
                    Float::class.java, java.lang.Float.TYPE -> 0f
                    Long::class.java, java.lang.Long.TYPE -> 0L
                    String::class.java -> ""
                    Void.TYPE -> null
                    else -> null
                }
            }
        } as GL20

        @JvmStatic
        @BeforeAll
        fun setup() {
            val gl = stubGL20()
            val config = HeadlessApplicationConfiguration().apply { updatesPerSecond = -1 }
            app = HeadlessApplication(object : ApplicationAdapter() {}, config)
            Gdx.gl = gl; Gdx.gl20 = gl
            Thread.sleep(200)
        }

        @JvmStatic
        @AfterAll
        fun teardown() { app.exit() }

        private const val W = 812
        private const val H = 375
    }

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

    private class RecordingAudio(settings: SettingsStore) : AudioController(settings) {
        val musicVolumes = mutableListOf<Float>()
        val sfxVolumes = mutableListOf<Float>()
        val playedSfx = mutableListOf<Sfx>()
        var duckCount = 0
        var unduckCount = 0

        override fun setMusicVolume(v: Float) { musicVolumes += v; super.setMusicVolume(v) }
        override fun setSFXVolume(v: Float) { sfxVolumes += v; super.setSFXVolume(v) }
        override fun play(sfx: Sfx, volume: Float) { playedSfx += sfx }
        override fun duckMusic() { duckCount++; super.duckMusic() }
        override fun unduckMusic() { unduckCount++; super.unduckMusic() }
    }

    private class RecordingHaptics : Haptics {
        val impacts = mutableListOf<ImpactStyle>()
        val notifications = mutableListOf<NotificationType>()
        override fun impact(style: ImpactStyle) { impacts += style }
        override fun notification(type: NotificationType) { notifications += type }
    }

    private lateinit var prefs: FakePrefs
    private lateinit var settings: SettingsStore
    private lateinit var audio: RecordingAudio
    private lateinit var haptics: RecordingHaptics
    private lateinit var hud: HUDController
    private lateinit var handler: SettingsInputHandler

    private fun build(musicVol: Float = 0.5f, sfxVol: Float = 0.8f, hapticsOn: Boolean = true) {
        prefs = FakePrefs()
        settings = SettingsStore(prefs)
        settings.musicVolume = musicVol
        settings.sfxVolume = sfxVol
        settings.hapticsEnabled = hapticsOn
        audio = RecordingAudio(settings)
        haptics = RecordingHaptics()
        hud = HUDController(BitmapFont())
        hud.resize(W, H)
        handler = SettingsInputHandler(hud, audio, haptics, settings)
    }

    @BeforeEach
    fun init() { build() }

    @Test
    fun `music slider drag from left to 75pct updates audio and store`() {
        handler.show()
        val tc = hud.musicSliderTrackCenter()
        val left = Vector2(tc.x - HUDController.SLIDER_TRACK_WIDTH / 2f, tc.y)
        val target = Vector2(tc.x + HUDController.SLIDER_TRACK_WIDTH * 0.25f, tc.y)

        handler.touchDown(left)
        assertEquals(SettingsInputHandler.ActiveDrag.MUSIC, handler.activeDrag)

        val steps = 10
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val x = left.x + (target.x - left.x) * t
            handler.touchDragged(Vector2(x, tc.y))
        }
        handler.touchUp(target)

        assertTrue(settings.musicVolume in 0.7f..0.8f,
            "final musicVolume ${settings.musicVolume} should be ~0.75")
        assertEquals(settings.musicVolume, hud.musicSliderValue, 0.01f)
        assertTrue(audio.musicVolumes.size >= steps, "should record volume for each drag step")
        assertTrue(audio.playedSfx.none { it == AudioController.Sfx.UI_TAP },
            "music slider must NOT emit ui_tap preview")
    }

    @Test
    fun `sfx slider drag emits throttled previews spaced by at least 0_05`() {
        handler.show()
        val tc = hud.sfxSliderTrackCenter()
        val left = Vector2(tc.x - HUDController.SLIDER_TRACK_WIDTH / 2f, tc.y)
        val right = Vector2(tc.x + HUDController.SLIDER_TRACK_WIDTH / 2f, tc.y)

        handler.touchDown(left)
        assertEquals(SettingsInputHandler.ActiveDrag.SFX, handler.activeDrag)

        val steps = 40
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val x = left.x + (right.x - left.x) * t
            handler.touchDragged(Vector2(x, tc.y))
        }
        handler.touchUp(right)

        val taps = audio.playedSfx.count { it == AudioController.Sfx.UI_TAP }
        assertTrue(taps in 1..25,
            "ui_tap count $taps should be throttled (not 1:1 with $steps drag moves)")

        val recordedValues = audio.sfxVolumes
        assertTrue(recordedValues.last() > 0.9f, "final sfx value should be near 1.0")
    }

    @Test
    fun `haptics toggle Off then On - store, hud, and haptic feedback`() {
        handler.show()
        val offActor = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(
            HUDController.SETTINGS_HAPTICS_OFF_NODE_NAME
        )!!
        val onActor = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(
            HUDController.SETTINGS_HAPTICS_ON_NODE_NAME
        )!!

        val tapOff = Vector2(offActor.x, offActor.y)
        handler.touchDown(tapOff)
        assertNull(handler.touchUp(tapOff))
        assertFalse(settings.hapticsEnabled)
        assertEquals(1, haptics.impacts.size)
        assertEquals(ImpactStyle.LIGHT, haptics.impacts[0])

        val onLabel = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.ui.Label>(
            HUDController.SETTINGS_HAPTICS_ON_NODE_NAME
        )!!
        assertTrue(onLabel.color.a < 0.5f, "On should be dimmed after disabling")

        val tapOn = Vector2(onActor.x, onActor.y)
        handler.touchDown(tapOn)
        assertNull(handler.touchUp(tapOn))
        assertTrue(settings.hapticsEnabled)
        assertEquals(2, haptics.impacts.size)
    }

    @Test
    fun `back hit dismisses and exits to pause with haptic`() {
        handler.show()
        val back = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(
            HUDController.SETTINGS_BACK_NODE_NAME
        )!!
        val tap = Vector2(back.x, back.y)
        handler.touchDown(tap)
        val result = handler.touchUp(tap)
        assertEquals(SettingsInputHandler.Action.EXIT_TO_PAUSE, result)
        assertEquals(1, haptics.impacts.size)
        assertEquals(ImpactStyle.LIGHT, haptics.impacts[0])
    }

    @Test
    fun `outside panel tap exits to pause`() {
        handler.show()
        val outside = Vector2(5f, 5f)
        handler.touchDown(outside)
        assertEquals(SettingsInputHandler.Action.EXIT_TO_PAUSE, handler.touchUp(outside))
        assertTrue(haptics.impacts.isEmpty(), "outside tap should not fire haptic")
    }

    @Test
    fun `inside panel off-control tap is inert`() {
        handler.show()
        val cx = hud.stage.viewport.worldWidth / 2f
        val cy = hud.stage.viewport.worldHeight / 2f
        val inert = Vector2(cx, cy)
        handler.touchDown(inert)
        assertNull(handler.touchUp(inert), "should not navigate")
        assertTrue(haptics.impacts.isEmpty())
        assertEquals(0, audio.sfxVolumes.size, "should not change volume")
    }

    @Test
    fun `duck on show, unduck on dismiss, persisted volumes unaffected`() {
        handler.show()
        assertEquals(1, audio.duckCount)
        assertEquals(0.5f, audio.duckFactor)
        val storedMusic = settings.musicVolume

        handler.dismiss()
        assertEquals(1, audio.unduckCount)
        assertEquals(1f, audio.duckFactor)
        assertEquals(storedMusic, settings.musicVolume, 0.001f,
            "persisted volume should not be affected by duck/unduck")
    }

    @Test
    fun `init from store with custom values`() {
        build(musicVol = 0.3f, sfxVol = 0.6f, hapticsOn = false)
        handler.show()

        assertEquals(0.3f, hud.musicSliderValue, 0.01f)
        assertEquals(0.6f, hud.sfxSliderValue, 0.01f)

        val onLabel = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.ui.Label>(
            HUDController.SETTINGS_HAPTICS_ON_NODE_NAME
        )!!
        val offLabel = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.ui.Label>(
            HUDController.SETTINGS_HAPTICS_OFF_NODE_NAME
        )!!
        assertTrue(onLabel.color.a < 0.5f, "On should be dimmed when haptics=false")
        assertEquals(1f, offLabel.color.a, 0.01f, "Off should be bright when haptics=false")
    }

    @Test
    fun `drag release does not dispatch tap`() {
        handler.show()
        val tc = hud.musicSliderTrackCenter()
        val start = Vector2(tc.x - 20f, tc.y)
        val end = Vector2(tc.x + 20f, tc.y)

        handler.touchDown(start)
        handler.touchDragged(end)
        val result = handler.touchUp(end)
        assertNull(result, "drag release should not trigger navigation")
    }
}
