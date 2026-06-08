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

class HapticsToggleTest {

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

    private fun makeHud(hapticsEnabled: Boolean = true): HUDController {
        val prefs = FakePrefs()
        val store = SettingsStore(prefs)
        store.hapticsEnabled = hapticsEnabled
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.resize(W, H)
        hud.showSettingsMenu(store)
        return hud
    }

    @Test
    fun `hit On pill returns true, Off pill returns false, empty returns null`() {
        val hud = makeHud()
        val onActor = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(HUDController.SETTINGS_HAPTICS_ON_NODE_NAME)
        val offActor = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(HUDController.SETTINGS_HAPTICS_OFF_NODE_NAME)
        assertNotNull(onActor); assertNotNull(offActor)

        assertEquals(true, hud.settingsHapticsHit(Vector2(onActor!!.x, onActor.y)))
        assertEquals(false, hud.settingsHapticsHit(Vector2(offActor!!.x, offActor.y)))
        assertNull(hud.settingsHapticsHit(Vector2(5f, 5f)), "empty space should return null")
        hud.dispose()
    }

    @Test
    fun `generous boundary - off-center still hits within 80x42 pill`() {
        val hud = makeHud()
        val onActor = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(HUDController.SETTINGS_HAPTICS_ON_NODE_NAME)!!
        val nearEdge = Vector2(onActor.x + 35f, onActor.y + 15f)
        assertEquals(true, hud.settingsHapticsHit(nearEdge), "should hit within generous pill")
        hud.dispose()
    }

    @Test
    fun `updateHapticsToggleState switches active styling`() {
        val hud = makeHud(hapticsEnabled = true)
        val onLabel = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.ui.Label>(HUDController.SETTINGS_HAPTICS_ON_NODE_NAME)!!
        val offLabel = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.ui.Label>(HUDController.SETTINGS_HAPTICS_OFF_NODE_NAME)!!

        assertEquals(1f, onLabel.color.a, 0.01f, "On should be bright when enabled")
        assertTrue(offLabel.color.a < 0.5f, "Off should be dimmed when enabled")

        hud.updateHapticsToggleState(false)
        assertTrue(onLabel.color.a < 0.5f, "On should be dimmed when disabled")
        assertEquals(1f, offLabel.color.a, 0.01f, "Off should be bright when disabled")

        hud.updateHapticsToggleState(true)
        assertEquals(1f, onLabel.color.a, 0.01f, "On back to bright")
        hud.dispose()
    }

    @Test
    fun `initial active matches store hapticsEnabled=false`() {
        val hud = makeHud(hapticsEnabled = false)
        val onLabel = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.ui.Label>(HUDController.SETTINGS_HAPTICS_ON_NODE_NAME)!!
        val offLabel = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.ui.Label>(HUDController.SETTINGS_HAPTICS_OFF_NODE_NAME)!!

        assertTrue(onLabel.color.a < 0.5f, "On should start dimmed when store says disabled")
        assertEquals(1f, offLabel.color.a, 0.01f, "Off should start bright")
        hud.dispose()
    }

    @Test
    fun `dismiss clears refs - settingsHapticsHit returns null`() {
        val hud = makeHud()
        hud.dismissSettingsMenu()
        assertNull(hud.settingsHapticsHit(Vector2(W / 2f, H / 2f)))
        hud.dispose()
    }
}
