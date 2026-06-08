package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.IntBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GameScreenLifecycleTest {

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
                    buf.put(0, if (pname == GL20.GL_LINK_STATUS) 1 else 0)
                    null
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
    fun resetState() {
        screen.returnToStart()
        synchronized(logMessages) { logMessages.clear() }
    }

    @Test
    fun `not started - render does not advance gameplay`() {
        assertFalse(screen.isStarted)
        screen.render(1f / 60f)
        assertFalse(screen.isStarted, "render should not auto-start the game")
    }

    @Test
    fun `started not paused - gameplay active`() {
        screen.startGame()
        assertTrue(screen.isStarted)
        assertFalse(screen.gameplayPaused)
        assertFalse(screen.isGameOver)
    }

    @Test
    fun `enterPauseMenu sets gameplayPaused true`() {
        screen.startGame()
        screen.enterPauseMenu()
        assertTrue(screen.gameplayPaused, "gameplayPaused must be true after enterPauseMenu")
        assertTrue(screen.isInPauseMenu)
    }

    @Test
    fun `pause during active run enters pause menu`() {
        screen.startGame()
        screen.pause()
        assertTrue(screen.isInPauseMenu, "pause() should enter pause menu during active gameplay")
        assertTrue(screen.gameplayPaused)
        val entered = synchronized(logMessages) { logMessages.any { it.contains("enterPauseMenu") } }
        assertTrue(entered, "enterPauseMenu log expected")
    }

    @Test
    fun `resume while inPauseMenu stays frozen`() {
        screen.startGame()
        screen.enterPauseMenu()
        screen.resume()
        assertTrue(screen.gameplayPaused, "resume() must NOT unfreeze gameplay while pause menu is open")
        assertTrue(screen.isInPauseMenu)
        val stayedPaused = synchronized(logMessages) { logMessages.any { it.contains("menu open") } }
        assertTrue(stayedPaused, "resume-with-menu-open log expected")
    }

    @Test
    fun `dismissPauseMenusAndResume unfreezes gameplay`() {
        screen.startGame()
        screen.enterPauseMenu()
        assertTrue(screen.gameplayPaused)

        screen.dismissPauseMenusAndResume()
        assertFalse(screen.gameplayPaused, "gameplayPaused must be false after dismissPauseMenusAndResume")
        assertFalse(screen.isInPauseMenu)
        assertFalse(screen.isInSettings)
        val resumed = synchronized(logMessages) { logMessages.any { it.contains("resume gameplayPaused=false") } }
        assertTrue(resumed, "dismissPauseMenusAndResume log expected")
    }

    @Test
    fun `dismissSettings while in pause menu stays paused`() {
        screen.startGame()
        screen.enterPauseMenu()
        screen.enterSettings()
        assertTrue(screen.isInSettings)
        assertTrue(screen.isInPauseMenu)

        screen.dismissSettings()
        assertFalse(screen.isInSettings)
        assertTrue(screen.isInPauseMenu, "should remain in pause menu")
        assertTrue(screen.gameplayPaused, "should remain paused")
    }

    @Test
    fun `gameOver freezes gameplay`() {
        screen.startGame()
        screen.triggerGameOver()
        assertTrue(screen.isGameOver)
        assertTrue(screen.gameplayPaused)
    }

    @Test
    fun `restart clears all flags and resumes`() {
        screen.startGame()
        screen.enterPauseMenu()
        screen.triggerGameOver()
        screen.restart()
        assertTrue(screen.isStarted)
        assertFalse(screen.isGameOver)
        assertFalse(screen.isInPauseMenu)
        assertFalse(screen.gameplayPaused)
    }
}
