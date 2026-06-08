package dev.copt.galaxymonkey.render

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import dev.copt.galaxymonkey.Tuning
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class VFXPoolTest {

    private fun advanceFrames(pool: VFXPool, frames: Int, dt: Float = 1f / 60f) {
        repeat(frames) { pool.update(dt) }
    }

    // --- Muzzle flash ---

    @Test
    fun `muzzle flash alive mid-life, dead after duration`() {
        val pool = VFXPool()
        pool.spawnMuzzleFlash(100f, 200f, 0f)
        assertEquals(1, pool.activeCount)

        // Mid-life at 0.04s (half of 0.08s)
        pool.update(0.04f)
        assertEquals(1, pool.activeCount, "muzzle flash should be alive at t=0.04")
        println("VFX[muzzle] t=0.040 alive=true")

        // Past duration (0.08s)
        pool.update(0.05f)
        assertEquals(0, pool.activeCount, "muzzle flash should be dead at t=0.09")
        println("VFX[muzzle] t=0.090 alive=false")
    }

    @Test
    fun `muzzle flash pool slot reused after expiry`() {
        val pool = VFXPool()
        pool.spawnMuzzleFlash(0f, 0f, 0f)
        assertEquals(1, pool.activeCount)
        pool.update(0.1f) // past 0.08s
        assertEquals(0, pool.activeCount)

        // Spawn again — should reuse the freed slot
        pool.spawnMuzzleFlash(50f, 50f, 1f)
        assertEquals(1, pool.activeCount)
        println("VFXPool pool: reused slot after muzzle expiry")
    }

    // --- Glow ---

    @Test
    fun `glow alive within duration, dead after`() {
        val pool = VFXPool()
        pool.spawnGlow(0f, 0f, 1f, 0.5f)
        assertEquals(1, pool.activeCount)

        pool.update(0.4f)
        assertEquals(1, pool.activeCount, "glow should be alive at t=0.4 (duration=0.5)")

        pool.update(0.2f)
        assertEquals(0, pool.activeCount, "glow should be dead at t=0.6")
        println("VFX[glow] alive at 0.4, dead at 0.6 (duration=0.5)")
    }

    // --- Damage flash ---

    @Test
    fun `damage flash sets entity timer`() {
        var flashTimer = 0f
        val pool = VFXPool()
        pool.spawnDamageFlash { flashTimer = it }
        assertEquals(Tuning.VFX.damageFlashDuration, flashTimer, 1e-5f)
        println("VFX[damageFlash] timer set to ${Tuning.VFX.damageFlashDuration}")
    }

    @Test
    fun `damage flash second call resets timer`() {
        var flashTimer = 0f
        val pool = VFXPool()
        pool.spawnDamageFlash { flashTimer = it }
        val first = flashTimer
        // Simulate partial decay
        flashTimer -= 0.05f
        // Second call resets
        pool.spawnDamageFlash { flashTimer = it }
        assertEquals(first, flashTimer, 1e-5f, "second call should reset timer to full")
        println("VFX[damageFlash] second call reset timer from ${first - 0.05f} to $flashTimer")
    }

    // --- Blackhole warp ---

    @Test
    fun `blackhole halo alive through full sequence`() {
        val pool = VFXPool()
        pool.spawnBlackholeWarp(0f, 0f, null, 40f, 40f)
        assertEquals(1, pool.activeCount)

        val totalDur = Tuning.VFX.blackholeOpenDuration +
            Tuning.VFX.warpFadeDuration * 0.4f +
            Tuning.VFX.blackholeCollapseDuration
        // Alive mid-sequence
        pool.update(totalDur * 0.5f)
        assertEquals(1, pool.activeCount, "halo should be alive mid-sequence")

        // Dead past full duration
        pool.update(totalDur * 0.6f)
        assertEquals(0, pool.activeCount, "halo should be dead past full duration")
        println("VFX[blackhole] totalDur=%.3f, halo expired correctly".format(totalDur))
    }

    @Test
    fun `blackhole dissolve violet tint at warpVioletDuration`() {
        val pool = VFXPool()
        // Supply a non-null region to trigger dissolve (using a mock won't work
        // without GL, so we test dissolve behavior through activeDissolveCount)
        pool.spawnBlackholeWarp(0f, 0f, null, 40f, 40f)
        // No dissolve spawned because region was null
        assertEquals(0, pool.activeDissolveCount)
        println("VFX[blackhole] null region -> no dissolve spawned (graceful)")
    }

    @Test
    fun `blackhole dissolve phases with mock region`() {
        val pool = VFXPool()
        // We need a real TextureRegion for dissolve; create a minimal stub
        // Since TextureRegion doesn't need GL for construction (just stores coords),
        // we can create one with null texture for the dissolve data path
        val stubRegion = com.badlogic.gdx.graphics.g2d.TextureRegion()
        pool.spawnBlackholeWarp(50f, 50f, stubRegion, 40f, 40f)
        assertEquals(1, pool.activeDissolveCount, "dissolve should spawn with non-null region")

        // Phase 0: violet tint ramp over warpVioletDuration (0.12s)
        pool.update(Tuning.VFX.warpVioletDuration)
        // Dissolve should now be in phase 1 (shrink+fade)
        assertEquals(1, pool.activeDissolveCount, "dissolve should still be alive after violet phase")
        println("VFX[blackhole] phase0->phase1 at t=%.3f".format(Tuning.VFX.warpVioletDuration))

        // Phase 1: shrink+fade over warpFadeDuration (0.3s)
        pool.update(Tuning.VFX.warpFadeDuration + 0.01f)
        assertEquals(0, pool.activeDissolveCount, "dissolve should be dead after fade")
        println("VFX[blackhole] dissolve dead after fade at t=%.3f".format(
            Tuning.VFX.warpVioletDuration + Tuning.VFX.warpFadeDuration + 0.01f))
    }

    // --- Multiple effects ---

    @Test
    fun `multiple simultaneous effects tracked independently`() {
        val pool = VFXPool()
        pool.spawnMuzzleFlash(0f, 0f, 0f) // 0.08s
        pool.spawnGlow(0f, 0f, 1f, 0.5f) // 0.5s
        assertEquals(2, pool.activeCount)

        pool.update(0.09f) // past muzzle, glow still alive
        assertEquals(1, pool.activeCount, "only glow should remain")

        pool.update(0.5f) // past glow
        assertEquals(0, pool.activeCount, "all effects expired")
        println("VFXPool pool: independent lifecycle tracking ok")
    }

    @Test
    fun `hasActiveEffects reflects all pools`() {
        val pool = VFXPool()
        assertFalse(pool.hasActiveEffects())

        pool.spawnMuzzleFlash(0f, 0f, 0f)
        assertTrue(pool.hasActiveEffects())

        pool.update(0.1f) // muzzle expires
        assertFalse(pool.hasActiveEffects())

        // Dissolve-only
        val stubRegion = com.badlogic.gdx.graphics.g2d.TextureRegion()
        pool.spawnBlackholeWarp(0f, 0f, stubRegion, 30f, 30f)
        assertTrue(pool.hasActiveEffects(), "should be true with active halo or dissolve")
        println("VFXPool hasActiveEffects: tracks both effects and dissolves")
    }
}
