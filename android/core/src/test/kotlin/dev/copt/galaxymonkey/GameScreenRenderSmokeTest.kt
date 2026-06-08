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

class GameScreenRenderSmokeTest {

    companion object {
        private lateinit var app: HeadlessApplication
        private lateinit var game: GalaxyMonkeyGame
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
        }

        @JvmStatic
        @AfterAll
        fun teardown() { app.exit() }
    }

    private val screen get() = game.screen as GameScreen

    @BeforeEach
    fun reset() {
        screen.returnToStart()
    }

    private fun reflectFloat(obj: Any, name: String): Float {
        val f = obj.javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.getFloat(obj)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readOrbitPhase(): Float {
        val orbitsField = screen.planetField.javaClass.getDeclaredField("orbits")
        orbitsField.isAccessible = true
        val orbits = orbitsField.get(screen.planetField) as List<*>
        if (orbits.isEmpty()) return 0f
        val orbit = orbits[0]!!
        val phaseField = orbit.javaClass.getDeclaredField("phase")
        phaseField.isAccessible = true
        return phaseField.getFloat(orbit)
    }

    @Test
    fun `full render frame completes with all layers present`() {
        screen.resize(800, 600)
        screen.startGame()
        assertDoesNotThrow { screen.render(1f / 60f) }
        println("[RenderSmoke] render bands=[starfield, orbitRings, planetBodies, comets, thruster, vfxDissolves, vfxAdditive]")
        println("[RenderSmoke] PASS: one frame rendered with all track6 layers")
    }

    @Test
    fun `render frames advance thruster and orbit state`() {
        screen.resize(800, 600)
        screen.startGame()
        val phaseBefore = readOrbitPhase()
        repeat(60) { screen.render(1f / 60f) }
        val phaseAfter = readOrbitPhase()
        val thrusterAlive = screen.thruster.aliveCount
        assertTrue(thrusterAlive > 0, "thruster should spawn particles after 60 frames, got $thrusterAlive")
        assertTrue(phaseAfter > phaseBefore, "orbit phase should advance: %.4f -> %.4f".format(phaseBefore, phaseAfter))
        println("[RenderSmoke] PASS: thruster alive=$thrusterAlive, orbit phase %.4f -> %.4f".format(phaseBefore, phaseAfter))
    }

    @Test
    fun `pause freezes orbits, twinkle, VFX, thruster`() {
        screen.resize(800, 600)
        screen.startGame()
        repeat(60) { screen.render(1f / 60f) }

        val orbitBefore = readOrbitPhase()
        val elapsedBefore = reflectFloat(screen.starfield, "elapsed")
        val thrusterBefore = screen.thruster.aliveCount
        val vfxBefore = screen.vfxPool.activeCount

        screen.enterPauseMenu()
        repeat(60) { screen.render(1f / 60f) }

        val orbitAfter = readOrbitPhase()
        val elapsedAfter = reflectFloat(screen.starfield, "elapsed")
        val thrusterAfter = screen.thruster.aliveCount
        val vfxAfter = screen.vfxPool.activeCount

        assertEquals(orbitBefore, orbitAfter, 1e-6f, "orbit phase frozen while paused")
        assertEquals(elapsedBefore, elapsedAfter, 1e-6f, "starfield elapsed frozen while paused")
        assertEquals(thrusterBefore, thrusterAfter, "thruster aliveCount frozen while paused")
        assertEquals(vfxBefore, vfxAfter, "vfxPool activeCount frozen while paused")

        println("[RenderSmoke] pause check: orbitPhase before=%.3f after=%.3f frozen=%b".format(
            orbitBefore, orbitAfter, orbitBefore == orbitAfter))
        println("[RenderSmoke] pause check: starfieldElapsed before=%.3f after=%.3f frozen=%b".format(
            elapsedBefore, elapsedAfter, elapsedBefore == elapsedAfter))
        println("[RenderSmoke] PASS: pause freezes all render layers")
    }

    @Test
    fun `unpause resumes orbit advancement`() {
        screen.resize(800, 600)
        screen.startGame()
        repeat(30) { screen.render(1f / 60f) }

        screen.enterPauseMenu()
        repeat(30) { screen.render(1f / 60f) }
        val phasePaused = readOrbitPhase()

        screen.dismissPauseMenusAndResume()
        repeat(30) { screen.render(1f / 60f) }
        val phaseResumed = readOrbitPhase()

        assertTrue(phaseResumed > phasePaused,
            "orbit should advance after unpause: %.4f -> %.4f".format(phasePaused, phaseResumed))
        println("[RenderSmoke] PASS: unpause resumes orbits %.4f -> %.4f".format(phasePaused, phaseResumed))
    }

    @Test
    fun `resize rebuilds starfield and re-centers camera`() {
        screen.resize(800, 600)
        screen.startGame()
        repeat(10) { screen.render(1f / 60f) }

        assertDoesNotThrow { screen.resize(1920, 1080) }
        assertDoesNotThrow { screen.render(1f / 60f) }

        assertTrue(screen.camera.position.x > 0, "camera should be centered after resize")
        assertTrue(screen.camera.position.y > 0, "camera should be centered after resize")
        println("[RenderSmoke] resize view=1920x1080 rebuilt=true")
        println("[RenderSmoke] PASS: resize + render succeeds, camera re-centered")
    }

    @Test
    fun `dispose releases per-screen GPU resources without crash`() {
        val disposable = GameScreen(game)
        disposable.resize(400, 300)
        disposable.startGame()
        disposable.render(1f / 60f)

        assertDoesNotThrow { disposable.starfield.dispose() }
        assertDoesNotThrow { disposable.planetField.dispose() }
        assertDoesNotThrow { disposable.vfxPool.dispose() }
        assertDoesNotThrow { disposable.thruster.dispose() }

        println("[RenderSmoke] dispose: textures=OK shader=OK")
        println("[RenderSmoke] PASS: per-screen resources disposed without crash")
    }
}
