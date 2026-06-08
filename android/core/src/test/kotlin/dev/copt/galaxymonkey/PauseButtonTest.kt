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

class PauseButtonTest {

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
            stubTexture = Texture(Pixmap(48, 48, Pixmap.Format.RGBA8888))
            val atlas = TextureAtlas()
            atlas.addRegion("PauseIcon", TextureRegion(stubTexture, 0, 0, 48, 48))
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
    fun `default visibility is hidden`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        assertNotNull(hud.pauseButton, "pause button should exist with PauseIcon region")
        assertFalse(hud.pauseButton!!.isVisible, "default hidden")
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `setPauseButtonVisible toggles visibility`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.setPauseButtonVisible(true)
        assertTrue(hud.pauseButton!!.isVisible)
        hud.setPauseButtonVisible(false)
        assertFalse(hud.pauseButton!!.isVisible)
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `position near top-right within margins`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        hud.resize(812, 375)
        val btn = hud.pauseButton!!
        val rightEdge = 812f - HUDController.PAUSE_RIGHT_MARGIN
        assertTrue(btn.x + btn.width <= rightEdge + 1f,
            "button right edge=${btn.x + btn.width} should be within margin of right edge=$rightEdge")
        val expectedY = 375f - HUDController.PAUSE_Y_OFFSET
        assertEquals(expectedY, btn.y, 1f,
            "button y should be at height - PAUSE_Y_OFFSET")
        hud.dispose()
        font.dispose()
    }

    @Test
    fun `absent asset path - no button, setPauseButtonVisible is safe`() {
        val emptyAtlas = TextureAtlas()
        emptyAtlas.addRegion("LifeHeart", TextureRegion(stubTexture, 0, 0, 32, 32))
        SpriteCatalog.init(emptyAtlas)

        val font = BitmapFont()
        val hud = HUDController(font)
        assertNull(hud.pauseButton, "no button when PauseIcon is absent")
        assertDoesNotThrow { hud.setPauseButtonVisible(true) }
        hud.dispose()
        font.dispose()

        // Restore full atlas
        val fullAtlas = TextureAtlas()
        fullAtlas.addRegion("PauseIcon", TextureRegion(stubTexture, 0, 0, 48, 48))
        fullAtlas.addRegion("LifeHeart", TextureRegion(stubTexture, 0, 0, 32, 32))
        SpriteCatalog.init(fullAtlas)
    }

    @Test
    fun `onPausePressed callback wiring`() {
        val font = BitmapFont()
        val hud = HUDController(font)
        var fired = 0
        hud.onPausePressed = { fired++ }
        hud.onPausePressed?.invoke()
        assertEquals(1, fired, "callback should be settable and invocable")
        hud.dispose()
        font.dispose()
    }
}
