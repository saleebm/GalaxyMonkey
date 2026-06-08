package dev.copt.galaxymonkey

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class PickupSystemTest {

    @Test
    fun `drop chance matches Tuning within tolerance`() {
        val seed = 42L
        val rng = Random(seed)
        val sys = PickupSystem(rng)
        val trials = 100_000
        val pos = Vector2(0f, 0f)
        for (i in 0 until trials) sys.trySpawnGoldenBanana(pos)
        val rate = sys.active.size.toFloat() / trials
        val expected = Tuning.Pickup.dropChance
        assertTrue(abs(rate - expected) < 0.01f,
            "empirical rate=$rate should be within 1% of dropChance=$expected (seed=$seed, n=$trials)")
        println("PickupTest: drop rate=%.4f expected=%.2f seed=$seed n=$trials".format(rate, expected))
    }

    @Test
    fun `deterministic seed produces same sequence`() {
        fun countDrops(seed: Long): Int {
            val sys = PickupSystem(Random(seed))
            val pos = Vector2(0f, 0f)
            repeat(200) { sys.trySpawnGoldenBanana(pos) }
            return sys.active.size
        }
        assertEquals(countDrops(99L), countDrops(99L))
    }

    @Test
    fun `force spawn always adds pickup`() {
        val sys = PickupSystem()
        assertEquals(0, sys.active.size)
        sys.spawnGoldenBanana(Vector2(100f, 200f))
        assertEquals(1, sys.active.size)
        sys.spawnGoldenBanana(Vector2(300f, 400f))
        assertEquals(2, sys.active.size)
    }

    @Test
    fun `sine bob matches formula and stays in bounds`() {
        val sys = PickupSystem(Random(7))
        sys.spawnGoldenBanana(Vector2(0f, 0f))
        val p = sys.active[0]
        val bobStep = MathUtils.PI2 / Tuning.Pickup.bobPeriod
        val dt = 1f / 60f
        for (frame in 0 until 120) {
            sys.update(dt)
            val expectedBob = sin(p.phase) * Tuning.Pickup.bobAmplitude
            assertEquals(expectedBob, p.bobOffsetY, 1e-3f,
                "frame $frame: bobOffsetY=%.4f expected=%.4f".format(p.bobOffsetY, expectedBob))
            assertTrue(p.bobOffsetY >= -Tuning.Pickup.bobAmplitude - 0.01f &&
                p.bobOffsetY <= Tuning.Pickup.bobAmplitude + 0.01f,
                "bobOffsetY=${p.bobOffsetY} out of bounds")
        }
        println("PickupTest: sine bob verified over 120 frames")
    }

    @Test
    fun `two pickups have different starting phases`() {
        val sys = PickupSystem(Random(123))
        sys.spawnGoldenBanana(Vector2(0f, 0f))
        sys.spawnGoldenBanana(Vector2(10f, 10f))
        val phase0 = sys.active[0].phase
        val phase1 = sys.active[1].phase
        assertNotEquals(phase0, phase1, "phases should differ to prevent lockstep bob")
        println("PickupTest: phase0=%.4f phase1=%.4f (desync ok)".format(phase0, phase1))
    }

    @Test
    fun `lifetime despawn removes pickup at 8s`() {
        val sys = PickupSystem()
        sys.spawnGoldenBanana(Vector2(0f, 0f))
        // Advance to just before lifetime
        sys.update(Tuning.Pickup.lifetime - 0.01f)
        assertEquals(1, sys.active.size, "pickup should still be alive just before lifetime")
        // Push past lifetime
        sys.update(0.02f)
        assertEquals(0, sys.active.size, "pickup should be removed after lifetime")
        println("PickupTest: despawned at lifetime=${Tuning.Pickup.lifetime}")
    }

    @Test
    fun `collect removes pickup and double collect is safe no-op`() {
        val sys = PickupSystem()
        sys.spawnGoldenBanana(Vector2(0f, 0f))
        val pickup = sys.active[0]
        sys.collect(pickup)
        assertEquals(0, sys.active.size)
        // Second collect is no-op
        sys.collect(pickup)
        assertEquals(0, sys.active.size)
        println("PickupTest: collect + double-collect no-op ok")
    }

    @Test
    fun `clearAll empties active list`() {
        val sys = PickupSystem()
        sys.spawnGoldenBanana(Vector2(0f, 0f))
        sys.spawnGoldenBanana(Vector2(10f, 10f))
        sys.spawnGoldenBanana(Vector2(20f, 20f))
        assertEquals(3, sys.active.size)
        sys.clearAll()
        assertEquals(0, sys.active.size)
    }
}
