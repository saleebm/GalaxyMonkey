package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.IntBuffer

class LivesIconTest {

    companion object {
        private lateinit var app: HeadlessApplication
        private lateinit var stubTexture: Texture

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
            stubTexture = Texture(Pixmap(32, 32, Pixmap.Format.RGBA8888))
            val atlas = TextureAtlas()
            atlas.addRegion("LifeHeart", TextureRegion(stubTexture, 0, 0, 32, 32))
            SpriteCatalog.init(atlas)
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            stubTexture.dispose()
            app.exit()
        }
    }

    @Test
    fun `setLives 3 - first 3 visible, rest hidden`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.setLives(3)
        assertEquals(5, hud.livesIcons.size)
        for (i in 0 until 3) assertTrue(hud.livesIcons[i].isVisible, "icon $i should be visible")
        for (i in 3 until 5) assertFalse(hud.livesIcons[i].isVisible, "icon $i should be hidden")
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `setLives 0 - all hidden`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.setLives(0)
        for (i in hud.livesIcons.indices) assertFalse(hud.livesIcons[i].isVisible, "icon $i should be hidden")
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `setLives 5 - all visible`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.setLives(5)
        for (i in hud.livesIcons.indices) assertTrue(hud.livesIcons[i].isVisible, "icon $i should be visible")
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `layout - slot 0 rightmost, descending x`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.resize(812, 375)
        for (i in 0 until hud.livesIcons.size - 1) {
            assertTrue(hud.livesIcons[i].x > hud.livesIcons[i + 1].x,
                "icon[$i].x=${hud.livesIcons[i].x} should be > icon[${i+1}].x=${hud.livesIcons[i+1].x}")
        }
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `fallback label when no heart region`() {
        val emptyAtlas = TextureAtlas()
        SpriteCatalog.init(emptyAtlas)
        val font = BitmapFont()
        val hud = HUDController(font)
        assertTrue(hud.livesIcons.isEmpty(), "should have no icons with empty atlas")
        hud.setLives(2)
        val fallbackActor = hud.stage.actors.filterIsInstance<com.badlogic.gdx.scenes.scene2d.ui.Label>()
            .find { it.text.toString().startsWith("Lives:") }
        assertNotNull(fallbackActor, "fallback label should exist")
        assertEquals("Lives: 2", fallbackActor!!.text.toString())
        hud.dispose()
        font.dispose()

        // Restore atlas for other tests
        val restoreAtlas = TextureAtlas()
        restoreAtlas.addRegion("LifeHeart", TextureRegion(stubTexture, 0, 0, 32, 32))
        SpriteCatalog.init(restoreAtlas)
    }
}
