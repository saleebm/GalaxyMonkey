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

class SettingsInputHandlerTest {

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

    private class RecordingHaptics : Haptics {
        val impacts = mutableListOf<ImpactStyle>()
        val notifications = mutableListOf<NotificationType>()
        override fun impact(style: ImpactStyle) { impacts += style }
        override fun notification(type: NotificationType) { notifications += type }
    }

    private lateinit var prefs: FakePrefs
    private lateinit var settings: SettingsStore
    private lateinit var audio: AudioController
    private lateinit var haptics: RecordingHaptics
    private lateinit var hud: HUDController
    private lateinit var handler: SettingsInputHandler

    @BeforeEach
    fun init() {
        prefs = FakePrefs()
        settings = SettingsStore(prefs)
        settings.musicVolume = 0.5f
        settings.sfxVolume = 0.8f
        settings.hapticsEnabled = true
        audio = AudioController(settings)
        haptics = RecordingHaptics()
        hud = HUDController(BitmapFont())
        hud.resize(W, H)
        handler = SettingsInputHandler(hud, audio, haptics, settings)
    }

    @Test
    fun `show ducks music and initializes from store`() {
        handler.show()
        assertTrue(hud.isSettingsMenuVisible)
        assertEquals(0.5f, audio.duckFactor, "music should be ducked")
        assertEquals(0.5f, hud.musicSliderValue, 0.01f)
        assertEquals(0.8f, hud.sfxSliderValue, 0.01f)
    }

    @Test
    fun `dismiss unducks music and clears drag`() {
        handler.show()
        assertEquals(0.5f, audio.duckFactor)
        handler.dismiss()
        assertFalse(hud.isSettingsMenuVisible)
        assertEquals(1f, audio.duckFactor, "music should be unducked")
        assertNull(handler.activeDrag)
    }

    @Test
    fun `music slider drag updates audio and hud`() {
        handler.show()
        val trackCenter = hud.musicSliderTrackCenter()
        val trackRight = Vector2(trackCenter.x + HUDController.SLIDER_TRACK_WIDTH / 2f, trackCenter.y)
        handler.touchDown(trackRight)
        assertEquals(SettingsInputHandler.ActiveDrag.MUSIC, handler.activeDrag)
        assertTrue(settings.musicVolume > 0.9f, "music volume should be near 1.0 after dragging to right edge")
        assertEquals(settings.musicVolume, hud.musicSliderValue, 0.01f)
        handler.touchUp(trackRight)
        assertNull(handler.activeDrag)
    }

    @Test
    fun `sfx slider drag updates audio and hud with throttled preview`() {
        handler.show()
        val trackCenter = hud.sfxSliderTrackCenter()
        val mid = Vector2(trackCenter.x, trackCenter.y)
        handler.touchDown(mid)
        assertEquals(SettingsInputHandler.ActiveDrag.SFX, handler.activeDrag)

        val v1 = hud.sfxSliderValue
        val slight = Vector2(trackCenter.x + 2f, trackCenter.y)
        handler.touchDragged(slight)
        val v2 = hud.sfxSliderValue
        assertTrue(kotlin.math.abs(v2 - v1) < 0.05f, "small drag should be within throttle delta")

        handler.touchUp(slight)
    }

    @Test
    fun `haptics toggle tap fires haptic and updates store`() {
        handler.show()
        val onActor = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(
            HUDController.SETTINGS_HAPTICS_OFF_NODE_NAME
        )!!
        val tapOff = Vector2(onActor.x, onActor.y)
        handler.touchDown(tapOff)
        val result = handler.touchUp(tapOff)
        assertNull(result, "haptics toggle should not navigate")
        assertFalse(settings.hapticsEnabled, "haptics should now be disabled")
        assertEquals(1, haptics.impacts.size)
        assertEquals(ImpactStyle.LIGHT, haptics.impacts[0])
    }

    @Test
    fun `back button tap returns EXIT_TO_PAUSE with haptic`() {
        handler.show()
        val backActor = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(
            HUDController.SETTINGS_BACK_NODE_NAME
        )!!
        val tap = Vector2(backActor.x, backActor.y)
        handler.touchDown(tap)
        val result = handler.touchUp(tap)
        assertEquals(SettingsInputHandler.Action.EXIT_TO_PAUSE, result)
        assertEquals(1, haptics.impacts.size)
    }

    @Test
    fun `outside panel tap returns EXIT_TO_PAUSE`() {
        handler.show()
        val outside = Vector2(5f, 5f)
        handler.touchDown(outside)
        val result = handler.touchUp(outside)
        assertEquals(SettingsInputHandler.Action.EXIT_TO_PAUSE, result)
    }

    @Test
    fun `inside panel off-control tap is inert`() {
        handler.show()
        val w = hud.stage.viewport.worldWidth
        val h = hud.stage.viewport.worldHeight
        val panelCenter = Vector2(w / 2f, h / 2f)
        handler.touchDown(panelCenter)
        val result = handler.touchUp(panelCenter)
        assertNull(result, "inside-panel-off-control should be inert")
        assertTrue(haptics.impacts.isEmpty(), "no haptic for inert tap")
    }

    @Test
    fun `sfx preview throttle - no extra play within 0_05 delta`() {
        handler.show()
        val trackCenter = hud.sfxSliderTrackCenter()
        val left = Vector2(trackCenter.x - HUDController.SLIDER_TRACK_WIDTH / 2f, trackCenter.y)
        handler.touchDown(left)
        assertEquals(SettingsInputHandler.ActiveDrag.SFX, handler.activeDrag)

        val tiny = Vector2(left.x + 1f, left.y)
        handler.touchDragged(tiny)
        val v1 = hud.sfxSliderValue
        assertTrue(v1 < 0.05f, "should be near left edge")

        handler.touchUp(tiny)
    }

    @Test
    fun `touchDown returns false when settings not showing`() {
        assertFalse(handler.touchDown(Vector2(100f, 100f)))
    }

    @Test
    fun `touchUp returns null when settings not showing`() {
        assertNull(handler.touchUp(Vector2(100f, 100f)))
    }
}
