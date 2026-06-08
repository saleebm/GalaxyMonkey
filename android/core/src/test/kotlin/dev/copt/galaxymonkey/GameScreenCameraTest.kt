package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Matrix4
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.IntBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GameScreenCameraTest {

    companion object {
        private lateinit var app: HeadlessApplication
        private lateinit var game: GalaxyMonkeyGame
        private val logMessages = mutableListOf<String>()
        private val createLatch = CountDownLatch(1)

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
            game = GalaxyMonkeyGame()
            val wrapped = object : ApplicationListener {
                override fun create() { Gdx.gl = gl; Gdx.gl20 = gl; game.create(); createLatch.countDown() }
                override fun render() { game.render() }
                override fun resize(w: Int, h: Int) { game.resize(w, h) }
                override fun pause() { game.pause() }
                override fun resume() { game.resume() }
                override fun dispose() {}
            }
            val config = HeadlessApplicationConfiguration().apply { updatesPerSecond = -1 }
            app = HeadlessApplication(wrapped, config)
            Gdx.gl = gl; Gdx.gl20 = gl
            assertTrue(createLatch.await(5, TimeUnit.SECONDS))

            Gdx.app.applicationLogger = object : com.badlogic.gdx.ApplicationLogger {
                override fun log(tag: String, message: String) { synchronized(logMessages) { logMessages += "$tag: $message" } }
                override fun log(tag: String, message: String, e: Throwable?) { synchronized(logMessages) { logMessages += "$tag: $message" } }
                override fun error(tag: String, message: String) { synchronized(logMessages) { logMessages += "$tag: $message" } }
                override fun error(tag: String, message: String, e: Throwable?) { synchronized(logMessages) { logMessages += "$tag: $message" } }
                override fun debug(tag: String, message: String) { synchronized(logMessages) { logMessages += "$tag: $message" } }
                override fun debug(tag: String, message: String, e: Throwable?) { synchronized(logMessages) { logMessages += "$tag: $message" } }
            }
        }

        @JvmStatic
        @AfterAll
        fun teardown() { app.exit() }
    }

    private val screen get() = game.screen as GameScreen

    @BeforeEach
    fun reset() {
        synchronized(logMessages) { logMessages.clear() }
    }

    @Test
    fun `camera centered on world after resize`() {
        screen.resize(800, 600)
        screen.camera.update()
        val cx = screen.camera.position.x
        val cy = screen.camera.position.y
        assertTrue(cx > 0, "camera x should be positive (centered), got $cx")
        assertTrue(cy > 0, "camera y should be positive (centered), got $cy")
    }

    @Test
    fun `resize updates viewport and re-centers camera`() {
        screen.resize(1080, 1920)
        screen.camera.update()
        assertTrue(screen.camera.position.x > 0)
        assertTrue(screen.camera.position.y > 0)
        val logs = synchronized(logMessages) { logMessages.toList() }
        assertTrue(logs.any { it.contains("resize") && it.contains("1080") },
            "should log resize with new dimensions")
    }

    @Test
    fun `render one frame without exception`() {
        screen.resize(800, 600)
        assertDoesNotThrow { screen.render(0.016f) }
    }

    @Test
    fun `camera combined matrix is non-identity after update`() {
        screen.resize(800, 600)
        screen.camera.update()
        val identity = Matrix4()
        assertFalse(screen.camera.combined == identity,
            "combined projection should not be identity after camera.update")
    }
}
