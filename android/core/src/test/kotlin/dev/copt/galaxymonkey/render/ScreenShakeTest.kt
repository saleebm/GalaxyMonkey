package dev.copt.galaxymonkey.render

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.ApplicationLogger
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.math.MathUtils
import dev.copt.galaxymonkey.Tuning
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class ScreenShakeTest {

    companion object {
        private lateinit var app: HeadlessApplication
        private val logMessages = mutableListOf<String>()

        @JvmStatic
        @BeforeAll
        fun setup() {
            val config = HeadlessApplicationConfiguration().apply { updatesPerSecond = -1 }
            app = HeadlessApplication(object : ApplicationAdapter() {}, config)
            Gdx.app.logLevel = Application.LOG_DEBUG
            Gdx.app.applicationLogger = object : ApplicationLogger {
                override fun log(tag: String, message: String) {}
                override fun log(tag: String, message: String, exception: Throwable?) {}
                override fun error(tag: String, message: String) {}
                override fun error(tag: String, message: String, exception: Throwable?) {}
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

    private lateinit var shake: ScreenShake

    @BeforeEach
    fun init() {
        shake = ScreenShake()
        synchronized(logMessages) { logMessages.clear() }
        MathUtils.random.setSeed(42L)
    }

    private fun magnitude(): Float = sqrt(shake.offsetX * shake.offsetX + shake.offsetY * shake.offsetY)

    @Test
    fun `applyShake 8 yields magnitude approximately 8`() {
        shake.applyShake(8f)
        assertEquals(8f, magnitude(), 0.01f)
    }

    @Test
    fun `applyShake direction is unit-random scaled by intensity`() {
        shake.applyShake(5f)
        val mag = magnitude()
        assertEquals(5f, mag, 0.01f)
        val dirX = shake.offsetX / mag
        val dirY = shake.offsetY / mag
        assertEquals(1f, dirX * dirX + dirY * dirY, 0.01f)
    }

    @Test
    fun `applyShake 100 is clamped to shakeMaxOffset 18`() {
        shake.applyShake(100f)
        assertEquals(Tuning.VFX.shakeMaxOffset, magnitude(), 0.01f)
    }

    @Test
    fun `one decay step multiplies magnitude by 0_86`() {
        shake.applyShake(10f)
        val before = magnitude()
        shake.update()
        val after = magnitude()
        assertEquals(before * Tuning.VFX.shakeDecay, after, 0.02f)
    }

    @Test
    fun `after 30 decay steps magnitude is effectively zero`() {
        shake.applyShake(Tuning.VFX.shakeMaxOffset)
        for (i in 0 until 55) shake.update()
        assertTrue(magnitude() < 0.01f, "magnitude should be ~0 after 55 decay steps, got ${magnitude()}")
        assertEquals(0f, shake.offsetX)
        assertEquals(0f, shake.offsetY)
    }

    @Test
    fun `decay never produces negative magnitude or NaN`() {
        shake.applyShake(Tuning.VFX.shakeMaxOffset)
        for (i in 0 until 200) {
            shake.update()
            val m = magnitude()
            assertTrue(m >= 0f, "magnitude should never be negative: $m")
            assertFalse(m.isNaN(), "magnitude should never be NaN")
            assertFalse(shake.offsetX.isNaN())
            assertFalse(shake.offsetY.isNaN())
        }
    }

    @Test
    fun `applyShake emits debug log`() {
        shake.applyShake(8f)
        synchronized(logMessages) {
            assertTrue(logMessages.any { it.contains("shake applied intensity=") },
                "expected 'shake applied intensity=' log, got: $logMessages")
        }
    }

    @Test
    fun `plain decay does not emit shake log`() {
        shake.applyShake(5f)
        synchronized(logMessages) { logMessages.clear() }
        shake.update()
        synchronized(logMessages) {
            assertFalse(logMessages.any { it.contains("shake applied") },
                "decay frame should not log 'shake applied', got: $logMessages")
        }
    }
}
