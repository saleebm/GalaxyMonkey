package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.IntBuffer

class HUDScoreBestTest {

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
    fun `initial labels - score is 0 and best matches constructor arg`() {
        val font = BitmapFont()
        val hud = HUDController(font, initialBest = 42)
        assertEquals("Score: 0", hud.scoreLabel.text.toString())
        assertEquals("Best: 42", hud.bestLabel.text.toString())
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `setScore updates score label text`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.setScore(1500)
        assertEquals("Score: 1500", hud.scoreLabel.text.toString())
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `setBest updates best label text and bestScore field`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.setBest(2000)
        assertEquals("Best: 2000", hud.bestLabel.text.toString())
        assertEquals(2000, hud.bestScore)
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `score label positioned at x=24, near top of viewport`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.resize(812, 375)
        assertEquals(HUDController.LABEL_X, hud.scoreLabel.x, 0.01f)
        val expectedY = 375f - HUDController.SCORE_Y_OFFSET
        assertEquals(expectedY, hud.scoreLabel.y, 1f)
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `best label positioned below score label`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.resize(812, 375)
        assertEquals(HUDController.LABEL_X, hud.bestLabel.x, 0.01f)
        assertTrue(hud.bestLabel.y < hud.scoreLabel.y,
            "best y=${hud.bestLabel.y} should be below score y=${hud.scoreLabel.y}")
        val expectedY = 375f - HUDController.BEST_Y_OFFSET
        assertEquals(expectedY, hud.bestLabel.y, 1f)
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `labels re-pin to top-left after resize`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.resize(812, 375)
        val scoreYBefore = hud.scoreLabel.y

        hud.resize(375, 812)
        val scoreYAfter = hud.scoreLabel.y
        val expectedY = 812f - HUDController.SCORE_Y_OFFSET
        assertEquals(expectedY, scoreYAfter, 1f)
        assertTrue(scoreYAfter != scoreYBefore,
            "y should change after resize to different height")
        hud.dispose()
        font.dispose()
    }
}
