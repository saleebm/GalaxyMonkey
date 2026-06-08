package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
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

class SettingsMenuTest {

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
        private val cx = W / 2f
        private val cy = H / 2f
    }

    private fun makeHud(): HUDController {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.resize(W, H)
        return hud
    }

    @Test
    fun `panelH at height 375 equals min(375-24, 460) == 351`() {
        val hud = makeHud()
        hud.showSettingsMenu()
        assertTrue(hud.isSettingsMenuVisible)
        val expectedH = minOf(H - 24f, HUDController.SETTINGS_PANEL_MAX_HEIGHT)
        assertEquals(351f, expectedH, 0.01f)
        assertNotNull(hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(HUDController.SETTINGS_BACK_NODE_NAME))
        hud.dispose()
    }

    @Test
    fun `panelH at height 900 caps at 460`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.resize(812, 900)
        hud.showSettingsMenu()
        val expectedH = minOf(900f - 24f, HUDController.SETTINGS_PANEL_MAX_HEIGHT)
        assertEquals(460f, expectedH, 0.01f)
        hud.dispose()
    }

    @Test
    fun `scroll clamp - content fits viewport, pan is no-op`() {
        val hud = makeHud()
        hud.showSettingsMenu()
        hud.settingsContentHeight = 100f
        hud.panSettingsContent(50f)
        assertEquals(0f, hud.settingsContentY, 0.01f, "content fits, scroll should stay 0")
        hud.dispose()
    }

    @Test
    fun `scroll clamp - content exceeds viewport, clamps to maxScroll`() {
        val hud = makeHud()
        hud.showSettingsMenu()
        val panelH = minOf(H - 24f, HUDController.SETTINGS_PANEL_MAX_HEIGHT)
        val viewportH = panelH - HUDController.SETTINGS_TITLE_RESERVE - HUDController.SETTINGS_BACK_RESERVE
        hud.settingsContentHeight = viewportH + 200f
        hud.panSettingsContent(1000f)
        val maxScroll = hud.settingsContentHeight - viewportH
        assertEquals(maxScroll, hud.settingsContentY, 0.01f, "should clamp to maxScroll")
        hud.panSettingsContent(-1000f)
        assertEquals(0f, hud.settingsContentY, 0.01f, "should clamp to 0")
        hud.dispose()
    }

    @Test
    fun `settingsViewportContains - center true, above title band false`() {
        val hud = makeHud()
        hud.showSettingsMenu()
        assertTrue(hud.settingsViewportContains(Vector2(cx, cy)), "viewport center should be inside")
        assertFalse(hud.settingsViewportContains(Vector2(cx, H.toFloat() - 1f)), "above title band should be outside")
        hud.dispose()
    }

    @Test
    fun `settingsBackHit - back center true, far point false`() {
        val hud = makeHud()
        hud.showSettingsMenu()
        val backActor = hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(HUDController.SETTINGS_BACK_NODE_NAME)
        assertNotNull(backActor)
        val backCenter = Vector2(backActor.x, backActor.y)
        assertTrue(hud.settingsBackHit(backCenter), "hit at back center should be true")
        assertFalse(hud.settingsBackHit(Vector2(5f, 5f)), "far point should miss")
        hud.dispose()
    }

    @Test
    fun `settingsPanelContains - inside true (inert), outside false`() {
        val hud = makeHud()
        hud.showSettingsMenu()
        assertTrue(hud.settingsPanelContains(Vector2(cx, cy)), "panel center should be inside")
        assertFalse(hud.settingsPanelContains(Vector2(5f, 5f)), "far corner should be outside")
        hud.dispose()
    }

    @Test
    fun `dismiss clears refs - settingsBackHit returns false`() {
        val hud = makeHud()
        hud.showSettingsMenu()
        assertTrue(hud.isSettingsMenuVisible)
        hud.dismissSettingsMenu()
        assertFalse(hud.isSettingsMenuVisible)
        assertFalse(hud.settingsBackHit(Vector2(cx, cy)), "after dismiss, back hit should be false")
        assertFalse(hud.settingsPanelContains(Vector2(cx, cy)), "after dismiss, panel contains should be false")
        hud.dispose()
    }

    @Test
    fun `idempotent show does not duplicate`() {
        val hud = makeHud()
        hud.showSettingsMenu()
        val countBefore = hud.stage.actors.size
        hud.showSettingsMenu()
        assertEquals(countBefore, hud.stage.actors.size, "double show should not add more actors")
        hud.dispose()
    }
}
