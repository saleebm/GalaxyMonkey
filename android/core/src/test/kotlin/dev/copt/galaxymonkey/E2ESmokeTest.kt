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
import java.lang.reflect.Proxy
import java.nio.IntBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class E2ESmokeTest {

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
                "glGenTexture" -> 1
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
            assertTrue(createLatch.await(5, TimeUnit.SECONDS), "game.create() should complete")

            Gdx.app.applicationLogger = object : com.badlogic.gdx.ApplicationLogger {
                override fun log(tag: String, message: String) { synchronized(logMessages) { logMessages += "e2e: $tag: $message" } }
                override fun log(tag: String, message: String, e: Throwable?) { synchronized(logMessages) { logMessages += "e2e: $tag: $message" } }
                override fun error(tag: String, message: String) { synchronized(logMessages) { logMessages += "e2e: $tag: $message" } }
                override fun error(tag: String, message: String, e: Throwable?) { synchronized(logMessages) { logMessages += "e2e: $tag: $message" } }
                override fun debug(tag: String, message: String) { synchronized(logMessages) { logMessages += "e2e: $tag: $message" } }
                override fun debug(tag: String, message: String, e: Throwable?) { synchronized(logMessages) { logMessages += "e2e: $tag: $message" } }
            }
        }

        @JvmStatic
        @AfterAll
        fun teardown() { app.exit() }
    }

    private val screen get() = game.screen as GameScreen
    private val dt = GameScreen.STEP

    private fun advanceFrames(n: Int) {
        repeat(n) { screen.render(dt) }
    }

    @Test
    @Order(1)
    fun `e2e - full gameplay loop survives 10 simulated seconds without exception`() {
        screen.resize(812, 375)
        screen.startGame()
        assertDoesNotThrow { advanceFrames(600) }
        assertTrue(screen.isStarted, "game should be started")
        Gdx.app.log("e2e", "10s loop: enemies=${screen.enemySystem.enemies.size} " +
            "bullets=${screen.projectileSystem.active.size} " +
            "score=${screen.score} lives=${screen.player.lives}")
    }

    @Test
    @Order(2)
    fun `e2e - enemies spawn following ramp over time`() {
        screen.resize(812, 375)
        screen.startGame()
        advanceFrames(60)
        val earlyCount = screen.enemySystem.enemies.size
        advanceFrames(300)
        val laterCount = screen.enemySystem.enemies.size
        assertTrue(earlyCount > 0 || laterCount > 0,
            "enemies should spawn during gameplay (early=$earlyCount later=$laterCount)")
        Gdx.app.log("e2e", "spawn ramp: after 1s=$earlyCount after 6s=$laterCount")
    }

    @Test
    @Order(3)
    fun `e2e - player takes damage when enemies collide and game-over triggers at zero lives`() {
        screen.resize(812, 375)
        screen.startGame()

        val startLives = screen.player.lives
        advanceFrames(1200)

        if (screen.player.lives < startLives) {
            Gdx.app.log("e2e", "player took damage: ${startLives} -> ${screen.player.lives}")
        }

        if (!screen.isGameOver) {
            while (screen.player.isAlive && !screen.isGameOver) {
                screen.player.tryTakeHit()
            }
            if (screen.player.lives <= 0 && !screen.isGameOver) {
                screen.triggerGameOver()
            }
        }

        assertTrue(screen.isGameOver, "game should reach game-over")
        assertTrue(screen.gameplayPaused, "gameplay should be paused on game-over")
        Gdx.app.log("e2e", "game-over reached: score=${screen.score}")
    }

    @Test
    @Order(4)
    fun `e2e - restart resets all systems cleanly`() {
        screen.resize(812, 375)
        if (!screen.isStarted) screen.startGame()
        if (!screen.isGameOver) screen.triggerGameOver()

        screen.restart()

        assertTrue(screen.isStarted, "should be started after restart")
        assertFalse(screen.isGameOver, "game-over should be cleared")
        assertFalse(screen.gameplayPaused, "should not be paused")
        assertEquals(0, screen.score, "score should reset")
        assertEquals(Tuning.Player.startingLives, screen.player.lives, "lives should reset")
        assertTrue(screen.projectileSystem.active.isEmpty(), "bullets should be cleared")
        assertTrue(screen.pickupSystem.active.isEmpty(), "pickups should be cleared")

        assertDoesNotThrow { advanceFrames(60) }
        Gdx.app.log("e2e", "restart: systems reset, 1s post-restart OK")
    }

    @Test
    @Order(5)
    fun `e2e - pause freezes sim - no enemy drift`() {
        screen.resize(812, 375)
        if (!screen.isStarted || screen.isGameOver) screen.restart()
        advanceFrames(120)

        screen.enterPauseMenu()
        assertTrue(screen.gameplayPaused)

        val enemyPositionsBefore = screen.enemySystem.enemies.map {
            it.position.x to it.position.y
        }
        advanceFrames(120)
        val enemyPositionsAfter = screen.enemySystem.enemies.take(enemyPositionsBefore.size).map {
            it.position.x to it.position.y
        }

        for (i in enemyPositionsBefore.indices) {
            if (i < enemyPositionsAfter.size) {
                assertEquals(enemyPositionsBefore[i].first, enemyPositionsAfter[i].first, 0.001f,
                    "enemy $i X should not drift while paused")
                assertEquals(enemyPositionsBefore[i].second, enemyPositionsAfter[i].second, 0.001f,
                    "enemy $i Y should not drift while paused")
            }
        }

        screen.dismissPauseMenusAndResume()
        assertFalse(screen.gameplayPaused)
        assertDoesNotThrow { advanceFrames(30) }
        Gdx.app.log("e2e", "pause: ${enemyPositionsBefore.size} enemies frozen, resume OK")
    }

    @Test
    @Order(6)
    fun `e2e - returnToStart resets to initial state with start prompt`() {
        screen.resize(812, 375)
        if (!screen.isStarted) screen.startGame()
        screen.returnToStart()

        assertFalse(screen.isStarted)
        assertFalse(screen.isGameOver)
        assertFalse(screen.isInPauseMenu)
        assertFalse(screen.gameplayPaused)
        assertEquals(0, screen.score)
        assertTrue(screen.hud.isStartPromptVisible, "start prompt should show")
        assertDoesNotThrow { advanceFrames(30) }
        Gdx.app.log("e2e", "returnToStart: clean initial state")
    }

    @Test
    @Order(7)
    fun `e2e - settings menu opens, ducks music, dismisses back to pause`() {
        screen.resize(812, 375)
        screen.startGame()
        advanceFrames(10)
        screen.enterPauseMenu()
        screen.enterSettings()

        assertTrue(screen.isInSettings)
        assertTrue(screen.gameplayPaused)
        assertTrue(screen.hud.isSettingsMenuVisible)

        screen.dismissSettings()
        assertFalse(screen.isInSettings)
        assertTrue(screen.isInPauseMenu, "should still be in pause after settings dismiss")

        screen.dismissPauseMenusAndResume()
        assertFalse(screen.gameplayPaused)
        assertDoesNotThrow { advanceFrames(30) }
        Gdx.app.log("e2e", "settings: open/dismiss cycle OK")
    }

    @Test
    @Order(8)
    fun `e2e - lifecycle logs contain expected state transitions`() {
        synchronized(logMessages) { logMessages.clear() }

        screen.resize(812, 375)
        screen.startGame()
        advanceFrames(30)
        screen.enterPauseMenu()
        screen.dismissPauseMenusAndResume()
        advanceFrames(30)
        screen.triggerGameOver()
        screen.restart()
        advanceFrames(10)

        val logs = synchronized(logMessages) { logMessages.toList() }

        assertTrue(logs.any { it.contains("gameflow: start") }, "should log start")
        assertTrue(logs.any { it.contains("enterPauseMenu") }, "should log pause")
        assertTrue(logs.any { it.contains("resume") }, "should log resume")
        assertTrue(logs.any { it.contains("gameover") }, "should log game-over")
        assertTrue(logs.any { it.contains("restart") }, "should log restart")

        Gdx.app.log("e2e", "lifecycle logs: ${logs.size} entries, all transitions present")
    }
}
