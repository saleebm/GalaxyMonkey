package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
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

class GameFlowTest {

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

    private class FakePrefs : Preferences {
        val map = mutableMapOf<String, Any>()
        var flushCount = 0
        override fun putBoolean(key: String, v: Boolean): Preferences { map[key] = v; return this }
        override fun putInteger(key: String, v: Int): Preferences { map[key] = v; return this }
        override fun putLong(key: String, v: Long): Preferences { map[key] = v; return this }
        override fun putFloat(key: String, v: Float): Preferences { map[key] = v; return this }
        override fun putString(key: String, v: String): Preferences { map[key] = v; return this }
        override fun put(vals: MutableMap<String, *>): Preferences { map.putAll(vals.mapValues { it.value!! }); return this }
        override fun getBoolean(key: String): Boolean = map[key] as? Boolean ?: false
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun getInteger(key: String): Int = map[key] as? Int ?: 0
        override fun getInteger(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String): Long = map[key] as? Long ?: 0L
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String): Float = map[key] as? Float ?: 0f
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getString(key: String): String = map[key] as? String ?: ""
        override fun getString(key: String, defValue: String): String = map[key] as? String ?: defValue
        override fun get(): MutableMap<String, *> = map.toMutableMap()
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun clear() { map.clear() }
        override fun remove(key: String) { map.remove(key) }
        override fun flush() { flushCount++ }
    }

    private lateinit var prefs: FakePrefs
    private lateinit var screen: GameScreen

    @BeforeEach
    fun reset() {
        synchronized(logMessages) { logMessages.clear() }
        prefs = FakePrefs()
        screen = GameScreen(game, prefs)
    }

    @Test
    fun `triggerGameOver is idempotent`() {
        var callbackCount = 0
        screen.onGameOver = { _, _ -> callbackCount++ }
        screen.startGame()
        screen.addScore(100)
        screen.triggerGameOver()
        screen.triggerGameOver()
        assertEquals(1, callbackCount, "callback should only fire once")
        assertTrue(screen.isGameOver)
    }

    @Test
    fun `new high score is persisted`() {
        prefs.putInteger(GameScreen.KEY_BEST_SCORE, 50)
        screen.startGame()
        screen.addScore(120)
        screen.triggerGameOver()
        assertEquals(120, prefs.getInteger(GameScreen.KEY_BEST_SCORE))
        assertTrue(prefs.flushCount > 0, "should flush after persisting best")
    }

    @Test
    fun `lower score does not overwrite best`() {
        prefs.putInteger(GameScreen.KEY_BEST_SCORE, 200)
        val flushBefore = prefs.flushCount
        screen.startGame()
        screen.addScore(50)
        screen.triggerGameOver()
        assertEquals(200, prefs.getInteger(GameScreen.KEY_BEST_SCORE))
        assertEquals(flushBefore, prefs.flushCount, "should not flush when best is not beaten")
    }

    @Test
    fun `update tick is frozen after triggerGameOver`() {
        screen.startGame()
        screen.triggerGameOver()
        assertTrue(screen.isGameOver)
        assertTrue(screen.gameplayPaused)
    }

    @Test
    fun `restart zeroes score and resets flags`() {
        screen.startGame()
        screen.addScore(500)
        screen.triggerGameOver()
        screen.restart()
        assertEquals(0, screen.score, "score should be reset")
        assertTrue(screen.isStarted)
        assertFalse(screen.isGameOver)
        assertFalse(screen.gameplayPaused)
    }

    @Test
    fun `restart fires callback`() {
        var restarted = false
        screen.onRestart = { restarted = true }
        screen.startGame()
        screen.triggerGameOver()
        screen.restart()
        assertTrue(restarted)
    }

    @Test
    fun `returnToStart fires callback and clears isStarted`() {
        var returned = false
        screen.onReturnToStart = { returned = true }
        screen.startGame()
        screen.triggerGameOver()
        screen.returnToStart()
        assertTrue(returned)
        assertFalse(screen.isStarted)
        assertFalse(screen.isGameOver)
    }

    @Test
    fun `log messages follow expected format`() {
        screen.startGame()
        screen.addScore(75)
        screen.triggerGameOver()
        screen.restart()
        val logs = synchronized(logMessages) { logMessages.toList() }
        assertTrue(logs.any { it.contains("gameflow: start") }, "should log start")
        assertTrue(logs.any { it.contains("gameflow: gameover score=75 best=75") }, "should log gameover with score and best")
        assertTrue(logs.any { it.contains("gameflow: restart") }, "should log restart")
    }

    @Test
    fun `best_score key matches iOS convention`() {
        assertEquals("best_score", GameScreen.KEY_BEST_SCORE)
    }
}
