package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Animation.PlayMode
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.IntBuffer

class CatalogTest {

    companion object {
        private lateinit var app: HeadlessApplication
        private lateinit var stubTexture: Texture

        private val expectedAnimFrameCounts = mapOf(
            AnimationSet.BOMB_EXPLOSION to 25,
        ).withDefault { 7 }

        private fun stubGL20(): GL20 = Proxy.newProxyInstance(
            GL20::class.java.classLoader, arrayOf(GL20::class.java)
        ) { _, method, args ->
            when (method.name) {
                "glCreateShader" -> 1
                "glCreateProgram" -> 1
                "glGenTexture" -> 1
                "glGetShaderiv" -> { (args!![2] as IntBuffer).put(0, 1); null }
                "glGetProgramiv" -> {
                    val buf = args!![2] as IntBuffer
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

        private fun buildFullAtlas(): TextureAtlas {
            val atlas = TextureAtlas()
            val region = TextureRegion(stubTexture, 0, 0, 32, 32)
            Sprite.entries.forEach { atlas.addRegion(it.regionName, region) }
            AnimationSet.entries.forEach { animSet ->
                val frameCount = expectedAnimFrameCounts.getValue(animSet)
                for (idx in 0 until frameCount) {
                    val ar = atlas.addRegion(animSet.atlasBase, region)
                    ar.index = idx
                }
            }
            return atlas
        }

        @JvmStatic
        @BeforeAll
        fun setup() {
            val gl = stubGL20()
            val config = HeadlessApplicationConfiguration().apply { updatesPerSecond = -1 }
            app = HeadlessApplication(object : ApplicationAdapter() {}, config)
            Gdx.gl = gl; Gdx.gl20 = gl
            Thread.sleep(200)
            stubTexture = Texture(Pixmap(32, 32, Pixmap.Format.RGBA8888))
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            stubTexture.dispose()
            app.exit()
        }
    }

    @Nested
    inner class SpriteCatalogTests {

        @BeforeEach
        fun init() {
            SpriteCatalog.init(buildFullAtlas())
        }

        @Test
        fun `all 44 sprites resolve to non-null region`() {
            val failures = mutableListOf<String>()
            Sprite.entries.forEach { sprite ->
                val r = SpriteCatalog.region(sprite)
                if (r == null) failures.add(sprite.name)
            }
            assertTrue(failures.isEmpty(),
                "missing sprites: $failures")
            println("CATALOG-TEST sprites: ${Sprite.entries.size}/${Sprite.entries.size} resolved")
        }

        @Test
        fun `has returns true for all sprites`() {
            Sprite.entries.forEach { sprite ->
                assertTrue(SpriteCatalog.has(sprite), "has(${sprite.name}) should be true")
            }
        }

        @Test
        fun `cache hit - second call returns same instance`() {
            Sprite.entries.forEach { sprite ->
                val first = SpriteCatalog.region(sprite)
                val second = SpriteCatalog.region(sprite)
                assertSame(first, second,
                    "region(${sprite.name}) should return cached instance")
            }
            println("CATALOG-TEST sprites: cache-hit verified")
        }

        @Test
        fun `preload does not throw`() {
            assertDoesNotThrow { SpriteCatalog.preload() }
        }

        @Test
        fun `missing fallback - empty atlas returns null without throwing`() {
            val emptyAtlas = TextureAtlas()
            SpriteCatalog.init(emptyAtlas)
            val result = SpriteCatalog.region(Sprite.PLAYER)
            assertNull(result, "region on empty atlas should return null")
            assertFalse(SpriteCatalog.has(Sprite.PLAYER))
            println("MISSING-FALLBACK OK: region(PLAYER)=null on empty atlas, no throw, cached")
        }

        @Test
        fun `missing sprite is cached - second call also returns null`() {
            val emptyAtlas = TextureAtlas()
            SpriteCatalog.init(emptyAtlas)
            SpriteCatalog.region(Sprite.BULLET)
            val second = SpriteCatalog.region(Sprite.BULLET)
            assertNull(second, "second call should also return null (cached miss)")
        }
    }

    @Nested
    inner class AnimationCatalogTests {

        @BeforeEach
        fun init() {
            AnimationCatalog.init(buildFullAtlas())
        }

        @Test
        fun `all 31 animation sets have expected frame count`() {
            val failures = mutableListOf<String>()
            AnimationSet.entries.forEach { animSet ->
                val frames = AnimationCatalog.frames(animSet)
                val expected = expectedAnimFrameCounts.getValue(animSet)
                if (frames.size != expected) {
                    failures.add("${animSet.name}: expected=$expected got=${frames.size}")
                }
            }
            assertTrue(failures.isEmpty(), "wrong frame counts: $failures")
            println("CATALOG-TEST anims: ${AnimationSet.entries.size}/${AnimationSet.entries.size} resolved")
        }

        @Test
        fun `frames returned in ascending index order`() {
            AnimationSet.entries.forEach { animSet ->
                val frames = AnimationCatalog.frames(animSet)
                for (i in 0 until frames.size - 1) {
                    assertTrue(frames[i].index <= frames[i + 1].index,
                        "${animSet.name}: frames not in ascending index order at $i")
                }
            }
            println("CATALOG-TEST anims: ordering OK")
        }

        @Test
        fun `missing fallback - empty atlas returns empty array without throwing`() {
            val emptyAtlas = TextureAtlas()
            AnimationCatalog.init(emptyAtlas)
            val frames = AnimationCatalog.frames(AnimationSet.GORILLA_LEFT_IDLE)
            assertEquals(0, frames.size, "empty atlas should return 0 frames")
            assertFalse(AnimationCatalog.has(AnimationSet.GORILLA_LEFT_IDLE))
            println("MISSING-FALLBACK OK: frames(GORILLA_LEFT_IDLE)=empty on empty atlas, no throw, cached")
        }

        @Test
        fun `missing animation is cached - second call also returns empty`() {
            val emptyAtlas = TextureAtlas()
            AnimationCatalog.init(emptyAtlas)
            AnimationCatalog.frames(AnimationSet.PLASMA_JELLY_IDLE)
            val second = AnimationCatalog.frames(AnimationSet.PLASMA_JELLY_IDLE)
            assertEquals(0, second.size, "cached miss should also return empty")
        }

        @Test
        fun `oneShot returns NORMAL playmode with correct duration`() {
            val frameDuration = 0.1f
            val anim = AnimationCatalog.oneShot(AnimationSet.GORILLA_LEFT_IDLE, frameDuration)
            assertNotNull(anim)
            val expected = expectedAnimFrameCounts.getValue(AnimationSet.GORILLA_LEFT_IDLE)
            assertEquals(expected * frameDuration, anim!!.animationDuration, 0.001f)
            assertEquals(PlayMode.NORMAL, anim.playMode)
            println("CATALOG-TEST anims: oneShot duration=${anim.animationDuration} OK")
        }

        @Test
        fun `loop returns LOOP playmode`() {
            val anim = AnimationCatalog.loop(AnimationSet.GORILLA_LEFT_IDLE, 0.1f)
            assertNotNull(anim)
            assertEquals(PlayMode.LOOP, anim!!.playMode)
        }

        @Test
        fun `oneShot returns null when frames empty`() {
            AnimationCatalog.init(TextureAtlas())
            val anim = AnimationCatalog.oneShot(AnimationSet.GORILLA_LEFT_IDLE, 0.1f)
            assertNull(anim)
        }

        @Test
        fun `loop returns null when frames empty`() {
            AnimationCatalog.init(TextureAtlas())
            val anim = AnimationCatalog.loop(AnimationSet.GORILLA_LEFT_IDLE, 0.1f)
            assertNull(anim)
        }
    }
}
