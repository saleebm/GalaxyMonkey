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
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.IntBuffer

class SliderTest {

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

    private fun makeHudWithStore(musicVol: Float = 0.5f, sfxVol: Float = 0.5f): Pair<HUDController, SettingsStore> {
        val prefs = FakePrefs()
        val store = SettingsStore(prefs)
        store.musicVolume = musicVol
        store.sfxVolume = sfxVol
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.resize(W, H)
        hud.showSettingsMenu(store)
        return hud to store
    }

    @Test
    fun `music thumb at center when value is 0_5`() {
        val (hud, _) = makeHudWithStore(musicVol = 0.5f)
        val trackCenter = hud.musicSliderTrackCenter()
        val thumbX = hud.musicSliderThumbX()
        assertEquals(trackCenter.x, thumbX, 0.01f, "v=0.5 thumb should be at track center")
        hud.dispose()
    }

    @Test
    fun `sfx thumb at left end when value is 0`() {
        val (hud, _) = makeHudWithStore(sfxVol = 0.0f)
        val trackCenter = hud.sfxSliderTrackCenter()
        val expectedLeft = trackCenter.x - HUDController.SLIDER_TRACK_WIDTH / 2f
        assertEquals(expectedLeft, hud.sfxSliderThumbX(), 0.01f, "v=0.0 thumb at left end")
        hud.dispose()
    }

    @Test
    fun `sfx thumb at right end when value is 1`() {
        val (hud, _) = makeHudWithStore(sfxVol = 1.0f)
        val trackCenter = hud.sfxSliderTrackCenter()
        val expectedRight = trackCenter.x + HUDController.SLIDER_TRACK_WIDTH / 2f
        assertEquals(expectedRight, hud.sfxSliderThumbX(), 0.01f, "v=1.0 thumb at right end")
        hud.dispose()
    }

    @Test
    fun `updateMusicSliderThumb clamps 2_0 to right, -1_0 to left`() {
        val (hud, _) = makeHudWithStore(musicVol = 0.5f)
        val trackCenter = hud.musicSliderTrackCenter()
        val rightEnd = trackCenter.x + HUDController.SLIDER_TRACK_WIDTH / 2f
        val leftEnd = trackCenter.x - HUDController.SLIDER_TRACK_WIDTH / 2f

        hud.updateMusicSliderThumb(2.0f)
        assertEquals(rightEnd, hud.musicSliderThumbX(), 0.01f, "clamp 2.0 -> right")
        assertEquals(1.0f, hud.musicSliderValue, 0.01f)

        hud.updateMusicSliderThumb(-1.0f)
        assertEquals(leftEnd, hud.musicSliderThumbX(), 0.01f, "clamp -1.0 -> left")
        assertEquals(0.0f, hud.musicSliderValue, 0.01f)
        hud.dispose()
    }

    @Test
    fun `position to value - center yields 0_5, ends yield 0 and 1, overflow clamps`() {
        val (hud, _) = makeHudWithStore()
        val trackCenter = hud.musicSliderTrackCenter()
        val cx = trackCenter.x

        assertEquals(0.5f, hud.sliderValueFromTouch(Vector2(cx, trackCenter.y), cx), 0.01f)
        assertEquals(0.0f, hud.sliderValueFromTouch(Vector2(cx - 100f, trackCenter.y), cx), 0.01f)
        assertEquals(1.0f, hud.sliderValueFromTouch(Vector2(cx + 100f, trackCenter.y), cx), 0.01f)
        assertEquals(1.0f, hud.sliderValueFromTouch(Vector2(cx + 500f, trackCenter.y), cx), 0.01f, "overflow clamps to 1")
        hud.dispose()
    }

    @Test
    fun `generous hit band - 20px above track center still hits`() {
        val (hud, _) = makeHudWithStore()
        val trackCenter = hud.musicSliderTrackCenter()
        assertTrue(hud.musicSliderHit(Vector2(trackCenter.x, trackCenter.y + 20f)),
            "20px above center should hit within 44px band")
        assertTrue(hud.musicSliderHit(Vector2(trackCenter.x, trackCenter.y - 20f)),
            "20px below center should hit within 44px band")
        assertFalse(hud.musicSliderHit(Vector2(trackCenter.x, trackCenter.y + 25f)),
            "25px above should miss (44/2 = 22)")
        hud.dispose()
    }

    @Test
    fun `callback fires on updateMusicSliderThumb`() {
        val (hud, _) = makeHudWithStore()
        var received = -1f
        hud.onMusicVolumeChanged = { received = it }
        hud.updateMusicSliderThumb(0.75f)
        assertEquals(0.75f, received, 0.01f)
        hud.dispose()
    }

    @Test
    fun `callback fires on updateSfxSliderThumb`() {
        val (hud, _) = makeHudWithStore()
        var received = -1f
        hud.onSfxVolumeChanged = { received = it }
        hud.updateSfxSliderThumb(0.3f)
        assertEquals(0.3f, received, 0.01f)
        hud.dispose()
    }
}
