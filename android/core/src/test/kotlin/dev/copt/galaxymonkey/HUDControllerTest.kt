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

class HUDControllerTest {

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
    }

    @Test
    fun `construct without exception`() {
        val font = BitmapFont()
        val hud = HUDController(font, initialBest = 0)
        assertNotNull(hud.stage)
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `render 3 times no exception`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        assertDoesNotThrow { repeat(3) { hud.render(0.016f) } }
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `dispose is idempotent`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        assertDoesNotThrow { hud.dispose() }
        assertDoesNotThrow { hud.dispose() }
        font.dispose()
    }

    @Test
    fun `ported constants match iOS values`() {
        assertEquals(280f, HUDController.PAUSE_HIT_WIDTH, 1e-4f)
        assertEquals(48f, HUDController.PAUSE_HIT_HEIGHT, 1e-4f)
        assertEquals(80f, HUDController.TOGGLE_PILL_WIDTH, 1e-4f)
        assertEquals(42f, HUDController.TOGGLE_PILL_HEIGHT, 1e-4f)
        assertEquals(160f, HUDController.BACK_PILL_WIDTH, 1e-4f)
        assertEquals(46f, HUDController.BACK_PILL_HEIGHT, 1e-4f)
        assertEquals(200f, HUDController.SLIDER_TRACK_WIDTH, 1e-4f)
        assertEquals(6f, HUDController.SLIDER_TRACK_HEIGHT, 1e-4f)
        assertEquals(11f, HUDController.SLIDER_THUMB_RADIUS, 1e-4f)
        assertEquals(5, HUDController.MAX_LIVES_ICON_SLOT)
    }

    @Test
    fun `rectHit - center hits, far away misses`() {
        val center = Vector2(100f, 200f)
        val size = Vector2(280f, 48f)
        assertTrue(HUDController.rectHit(center, size, Vector2(100f, 200f)))
        assertFalse(HUDController.rectHit(center, size, Vector2(300f, 200f)))
        assertTrue(HUDController.rectHit(center, size, Vector2(239f, 200f)))
        assertFalse(HUDController.rectHit(center, size, Vector2(241f, 200f)))
    }
}
