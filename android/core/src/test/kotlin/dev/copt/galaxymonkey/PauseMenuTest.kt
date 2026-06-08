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

class PauseMenuTest {

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
    fun `showPauseMenu adds title and three buttons`() {
        val hud = makeHud()
        hud.showPauseMenu()
        assertTrue(hud.isPauseMenuVisible)
        assertNotNull(hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(HUDController.PAUSE_MENU_RESUME_NODE_NAME))
        assertNotNull(hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(HUDController.PAUSE_MENU_SETTINGS_NODE_NAME))
        assertNotNull(hud.stage.root.findActor<com.badlogic.gdx.scenes.scene2d.Actor>(HUDController.PAUSE_MENU_QUIT_NODE_NAME))
        hud.dispose()
    }

    @Test
    fun `pauseMenuHit at Resume center returns resume node name`() {
        val hud = makeHud()
        hud.showPauseMenu()
        val result = hud.pauseMenuHit(Vector2(cx, cy + 10f))
        assertEquals(HUDController.PAUSE_MENU_RESUME_NODE_NAME, result)
        hud.dispose()
    }

    @Test
    fun `pauseMenuHit at Settings center returns settings node name`() {
        val hud = makeHud()
        hud.showPauseMenu()
        val result = hud.pauseMenuHit(Vector2(cx, cy - 40f))
        assertEquals(HUDController.PAUSE_MENU_SETTINGS_NODE_NAME, result)
        hud.dispose()
    }

    @Test
    fun `pauseMenuHit at Quit center returns quit node name`() {
        val hud = makeHud()
        hud.showPauseMenu()
        val result = hud.pauseMenuHit(Vector2(cx, cy - 90f))
        assertEquals(HUDController.PAUSE_MENU_QUIT_NODE_NAME, result)
        hud.dispose()
    }

    @Test
    fun `empty space tap returns null - no-dismiss regression guard`() {
        val hud = makeHud()
        hud.showPauseMenu()
        val result = hud.pauseMenuHit(Vector2(5f, 5f))
        assertNull(result, "empty space should NOT dismiss (returns null)")
        hud.dispose()
    }

    @Test
    fun `generous target - 120px off Resume center still hits within 280px pill`() {
        val hud = makeHud()
        hud.showPauseMenu()
        val result = hud.pauseMenuHit(Vector2(cx + 120f, cy + 10f))
        assertEquals(HUDController.PAUSE_MENU_RESUME_NODE_NAME, result,
            "120px off center should still be within the 280px wide pill")
        hud.dispose()
    }

    @Test
    fun `idempotent show + dismiss clears menu`() {
        val hud = makeHud()
        hud.showPauseMenu()
        hud.showPauseMenu()
        assertTrue(hud.isPauseMenuVisible, "double show should not break")

        hud.dismissPauseMenu()
        assertFalse(hud.isPauseMenuVisible)
        assertNull(hud.pauseMenuHit(Vector2(cx, cy + 10f)),
            "after dismiss, hit should return null")
        hud.dispose()
    }
}
