package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL20
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.io.File
import java.lang.reflect.Proxy
import java.nio.IntBuffer

class GameAssetsTest {

    companion object {
        private lateinit var app: HeadlessApplication
        private val assetsDir: String = System.getProperty("assetsDir", "")

        @JvmStatic
        fun assetsExist(): Boolean {
            return assetsDir.isNotEmpty() && File(assetsDir, "game.atlas").exists()
        }

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
            val config = HeadlessApplicationConfiguration().apply { updatesPerSecond = -1 }
            app = HeadlessApplication(object : ApplicationAdapter() {}, config)
            Gdx.gl = gl; Gdx.gl20 = gl
            Thread.sleep(200)
        }

        @JvmStatic
        @AfterAll
        fun teardown() { app.exit() }
    }

    private fun assetsResolver(): FileHandleResolver {
        val base = File(assetsDir)
        return FileHandleResolver { fileName -> FileHandle(File(base, fileName)) }
    }

    @Test
    @EnabledIf("assetsExist")
    fun `load and finishLoading complete without throwing`() {
        val assets = GameAssets(assetsResolver())
        assertDoesNotThrow {
            assets.load()
            assets.finishLoading()
        }
        assets.dispose()
    }

    @Test
    @EnabledIf("assetsExist")
    fun `atlas loaded with regions`() {
        val assets = GameAssets(assetsResolver())
        assets.load()
        assets.finishLoading()
        val atlas = assets.getAtlas()
        assertTrue(atlas.regions.size > 0, "atlas should have regions")
        assets.dispose()
    }

    @Test
    @EnabledIf("assetsExist")
    fun `missing sfx returns null, present sfx returns non-null`() {
        val assets = GameAssets(assetsResolver())
        assets.load()
        assets.finishLoading()

        assertNull(assets.getSound(AudioController.Sfx.PICKUP), "pickup should be missing")
        assertNull(assets.getSound(AudioController.Sfx.WAVE_START), "wave_start should be missing")
        assertTrue(assets.isMissing(AudioController.Sfx.PICKUP))
        assertTrue(assets.isMissing(AudioController.Sfx.WAVE_START))

        assertNotNull(assets.getSound(AudioController.Sfx.PLAYER_SHOT), "shot should be present")
        assertNotNull(assets.getSound(AudioController.Sfx.EXPLOSION), "explosion should be present")
        assertNotNull(assets.getSound(AudioController.Sfx.GAME_OVER), "game_over should be present")
        assertFalse(assets.isMissing(AudioController.Sfx.PLAYER_SHOT))
        assets.dispose()
    }

    @Test
    @EnabledIf("assetsExist")
    fun `music loads successfully`() {
        val assets = GameAssets(assetsResolver())
        assets.load()
        assets.finishLoading()
        assertNotNull(assets.getMusic(), "bg_music should be present")
        assets.dispose()
    }

    @Test
    @EnabledIf("assetsExist")
    fun `getMissingSfx returns exactly pickup and wave_start`() {
        val assets = GameAssets(assetsResolver())
        assets.load()
        assets.finishLoading()
        val missing = assets.getMissingSfx()
        assertEquals(2, missing.size)
        assertTrue(missing.contains(AudioController.Sfx.PICKUP))
        assertTrue(missing.contains(AudioController.Sfx.WAVE_START))
        assets.dispose()
    }

    @Test
    @EnabledIf("assetsExist")
    fun `dispose runs without error`() {
        val assets = GameAssets(assetsResolver())
        assets.load()
        assets.finishLoading()
        assertDoesNotThrow { assets.dispose() }
    }
}
