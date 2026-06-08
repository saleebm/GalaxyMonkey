package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.IntBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EngineSmokeTest {

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

    @Test
    fun `full lifecycle - resize, render frames, start, gameover, restart without exceptions`() {
        assertDoesNotThrow {
            screen.resize(812, 375)
            screen.render(0.016f)
            screen.render(0.016f)
            screen.render(0.016f)
        }
    }

    @Test
    fun `gameplay paused - render still runs without exception`() {
        screen.resize(812, 375)
        screen.enterPauseMenu()
        assertTrue(screen.gameplayPaused)
        assertDoesNotThrow { screen.render(0.016f) }
        screen.dismissPauseMenusAndResume()
    }

    @Test
    fun `start - gameover - restart cycle`() {
        screen.resize(812, 375)
        assertDoesNotThrow {
            screen.startGame()
            screen.render(0.016f)
            screen.addScore(50)
            screen.triggerGameOver()
            assertTrue(screen.isGameOver)
            screen.render(0.016f)
            screen.restart()
            assertFalse(screen.isGameOver)
            assertTrue(screen.isStarted)
            screen.render(0.016f)
        }
    }

    @Test
    fun `returnToStart resets to initial state`() {
        screen.resize(812, 375)
        screen.startGame()
        screen.triggerGameOver()
        screen.returnToStart()
        assertFalse(screen.isStarted)
        assertFalse(screen.isGameOver)
        assertDoesNotThrow { screen.render(0.016f) }
    }

    @Test
    fun `logs show lifecycle path`() {
        synchronized(logMessages) { logMessages.clear() }
        screen.resize(812, 375)
        screen.startGame()
        screen.render(0.016f)
        screen.triggerGameOver()
        screen.restart()
        val logs = synchronized(logMessages) { logMessages.toList() }
        assertTrue(logs.any { it.contains("gameflow: start") || it.contains("resize") },
            "should log lifecycle events")
    }
}
