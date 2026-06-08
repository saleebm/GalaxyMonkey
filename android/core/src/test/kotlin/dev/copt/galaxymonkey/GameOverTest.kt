package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.IntBuffer

class GameOverTest {

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

    private fun makeHud(): HUDController {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.resize(W, H)
        return hud
    }

    @Test
    fun `showGameOver new high - result shows max(score, best)`() {
        val hud = makeHud()
        hud.showGameOver(120, 90)
        assertEquals("Score 120 · Best 120", hud.gameOverResultLabel?.text.toString())
        hud.dispose()
    }

    @Test
    fun `showGameOver existing high - result keeps best`() {
        val hud = makeHud()
        hud.showGameOver(50, 200)
        assertEquals("Score 50 · Best 200", hud.gameOverResultLabel?.text.toString())
        hud.dispose()
    }

    @Test
    fun `elements present - GAME OVER title, result, tap to play again`() {
        val hud = makeHud()
        hud.showGameOver(100, 100)
        assertTrue(hud.isGameOverVisible)
        val labels = hud.stage.actors.filterIsInstance<Label>()
        assertTrue(labels.any { it.text.toString() == "GAME OVER" }, "title should be present")
        assertTrue(labels.any { it.text.toString().startsWith("Score ") }, "result should be present")
        assertTrue(labels.any { it.text.toString() == "Tap to play again" }, "replay label should be present")
        hud.dispose()
    }

    @Test
    fun `idempotency - double showGameOver does not duplicate`() {
        val hud = makeHud()
        hud.showGameOver(100, 100)
        val countAfterFirst = hud.stage.actors.size
        hud.showGameOver(200, 200)
        assertEquals(countAfterFirst, hud.stage.actors.size, "double show should not add actors")
        hud.dispose()
    }

    @Test
    fun `dismissGameOver clears overlay`() {
        val hud = makeHud()
        hud.showGameOver(100, 100)
        assertTrue(hud.isGameOverVisible)
        hud.dismissGameOver()
        assertFalse(hud.isGameOverVisible)
        val labels = hud.stage.actors.filterIsInstance<Label>()
        assertFalse(labels.any { it.text.toString() == "GAME OVER" }, "title should be gone")
        assertFalse(labels.any { it.text.toString() == "Tap to play again" }, "replay should be gone")
        hud.dispose()
    }

    @Test
    fun `dim alpha matches PAUSE_DIM_ALPHA (0_55)`() {
        assertEquals(0.55f, HUDController.PAUSE_DIM_ALPHA, 0.001f)
    }

    @Test
    fun `onRestartTapped callback wiring`() {
        val hud = makeHud()
        var fired = 0
        hud.onRestartTapped = { fired++ }
        hud.showGameOver(100, 100)
        hud.onRestartTapped?.invoke()
        assertEquals(1, fired, "callback should fire once")
        hud.dispose()
    }
}
