package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.IntBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class GalaxyMonkeyGameTest {

    companion object {
        private lateinit var app: HeadlessApplication
        private lateinit var game: GalaxyMonkeyGame
        private val logMessages = mutableListOf<String>()
        private val createLatch = CountDownLatch(1)

        private fun stubGL20(): GL20 {
            val handler = InvocationHandler { _, method, args ->
                when (method.name) {
                    "glCreateShader" -> 1
                    "glCreateProgram" -> 1
                    "glGetShaderiv" -> {
                        val buf = args[2] as IntBuffer
                        buf.put(0, 1) // GL_COMPILE_STATUS = success
                        null
                    }
                    "glGetProgramiv" -> {
                        val buf = args[2] as IntBuffer
                        val pname = args[1] as Int
                        // GL_LINK_STATUS (0x8B82) -> 1 (success), everything else -> 0
                        buf.put(0, if (pname == GL20.GL_LINK_STATUS) 1 else 0)
                        null
                    }
                    "glGetActiveUniform", "glGetActiveAttrib" -> "stub"
                    "glGetUniformLocation" -> 0
                    "glGetAttribLocation" -> 0
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
            }
            return Proxy.newProxyInstance(
                GL20::class.java.classLoader,
                arrayOf(GL20::class.java),
                handler
            ) as GL20
        }

        @JvmStatic
        @BeforeAll
        fun setup() {
            val mockGL = stubGL20()
            game = GalaxyMonkeyGame()

            val wrappedGame = object : ApplicationListener {
                override fun create() {
                    Gdx.gl = mockGL
                    Gdx.gl20 = mockGL
                    game.create()
                    createLatch.countDown()
                }
                override fun render() { game.render() }
                override fun resize(w: Int, h: Int) { game.resize(w, h) }
                override fun pause() { game.pause() }
                override fun resume() { game.resume() }
                override fun dispose() {}
            }

            val config = HeadlessApplicationConfiguration().apply {
                updatesPerSecond = -1
            }
            app = HeadlessApplication(wrappedGame, config)
            Gdx.gl = mockGL
            Gdx.gl20 = mockGL

            assertTrue(createLatch.await(5, TimeUnit.SECONDS), "create() did not run within 5s")

            Gdx.app.applicationLogger = object : com.badlogic.gdx.ApplicationLogger {
                override fun log(tag: String, message: String) {
                    synchronized(logMessages) { logMessages.add("$tag: $message") }
                }
                override fun log(tag: String, message: String, exception: Throwable?) {
                    synchronized(logMessages) { logMessages.add("$tag: $message") }
                }
                override fun error(tag: String, message: String) {
                    synchronized(logMessages) { logMessages.add("$tag: $message") }
                }
                override fun error(tag: String, message: String, exception: Throwable?) {
                    synchronized(logMessages) { logMessages.add("$tag: $message") }
                }
                override fun debug(tag: String, message: String) {
                    synchronized(logMessages) { logMessages.add("$tag: $message") }
                }
                override fun debug(tag: String, message: String, exception: Throwable?) {
                    synchronized(logMessages) { logMessages.add("$tag: $message") }
                }
            }
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            app.exit()
        }
    }

    @Test
    @Order(1)
    fun `create sets a non-null GameScreen`() {
        assertNotNull(game.screen, "active screen should be set after create()")
        assertTrue(game.screen is GameScreen, "active screen should be GameScreen")
    }

    @Test
    @Order(2)
    fun `SpriteBatch is non-null after create`() {
        assertNotNull(game.batch, "shared SpriteBatch should be non-null")
    }

    @Test
    @Order(3)
    fun `dispose does not throw and logs`() {
        logMessages.clear()
        assertDoesNotThrow { game.dispose() }
        val disposeCount = synchronized(logMessages) {
            logMessages.count { it == "GalaxyMonkeyGame: dispose()" }
        }
        assertEquals(1, disposeCount, "dispose() should be logged exactly once")
    }
}
