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

class StartPromptTest {

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
    fun `showStartPrompt adds dim, title, subtitle, tap elements`() {
        val hud = makeHud()
        hud.showStartPrompt()
        assertTrue(hud.isStartPromptVisible)
        val labels = hud.stage.actors.filterIsInstance<Label>()
        assertTrue(labels.any { it.text.toString().contains("Left stick to move") }, "subtitle should be present")
        assertTrue(labels.any { it.text.toString() == "Tap to start" }, "tap label should be present")
        assertTrue(hud.stage.actors.size >= 4, "should have at least dim + title + subtitle + tap + base labels")
        hud.dispose()
    }

    @Test
    fun `idempotency - double show does not duplicate`() {
        val hud = makeHud()
        hud.showStartPrompt()
        val countAfterFirst = hud.stage.actors.size
        hud.showStartPrompt()
        assertEquals(countAfterFirst, hud.stage.actors.size, "double show should not add actors")
        hud.dispose()
    }

    @Test
    fun `dismissStartPrompt removes overlay`() {
        val hud = makeHud()
        hud.showStartPrompt()
        assertTrue(hud.isStartPromptVisible)
        hud.dismissStartPrompt()
        assertFalse(hud.isStartPromptVisible)
        val labels = hud.stage.actors.filterIsInstance<Label>()
        assertFalse(labels.any { it.text.toString() == "Tap to start" }, "tap label should be gone after dismiss")
        hud.dispose()
    }

    @Test
    fun `dim alpha is START_DIM_ALPHA (0_3)`() {
        assertEquals(0.3f, HUDController.START_DIM_ALPHA, 0.001f)
    }

    @Test
    fun `onStartTapped callback wiring`() {
        val hud = makeHud()
        var fired = 0
        hud.onStartTapped = { fired++ }
        hud.showStartPrompt()
        hud.onStartTapped?.invoke()
        assertEquals(1, fired, "callback should fire once")
        hud.dispose()
    }

    @Test
    fun `title fallback - GALAXY MONKEY text when no title region`() {
        val hud = makeHud()
        hud.showStartPrompt()
        val labels = hud.stage.actors.filterIsInstance<Label>()
        assertTrue(labels.any { it.text.toString() == "GALAXY MONKEY" },
            "without SpriteCatalog title region, should show 'GALAXY MONKEY' text label")
        hud.dispose()
    }
}
