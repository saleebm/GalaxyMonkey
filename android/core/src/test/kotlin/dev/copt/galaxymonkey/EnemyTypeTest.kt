package dev.copt.galaxymonkey

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class EnemyTypeTest {

    @Test
    fun `stat table snapshot`() {
        data class Expected(val hp: Int, val speedMul: Float, val radiusMul: Float, val scoreOnKill: Int, val spawnWeight: Double)
        val golden = mapOf(
            EnemyType.DRONE_SWARM to Expected(1, 1.5f, 0.7f, 50, 3.0),
            EnemyType.PREPPY to Expected(1, 1.0f, 1.0f, 100, 2.0),
            EnemyType.WHITE to Expected(1, 1.0f, 1.0f, 100, 2.0),
            EnemyType.SHADY to Expected(1, 1.0f, 1.0f, 100, 2.0),
            EnemyType.HEAVY_COSMONAUT to Expected(3, 0.6f, 1.3f, 250, 1.0),
            EnemyType.PLASMA_JELLY to Expected(2, 0.8f, 1.0f, 150, 1.5),
            EnemyType.ASTRO_SNIPER to Expected(1, 0.9f, 0.9f, 200, 1.0),
            EnemyType.MINI_BOSS to Expected(5, 0.7f, 1.4f, 500, 0.3),
            EnemyType.GORILLA to Expected(10, 0.5f, 1.6f, 1000, 0.0),
        )
        for ((type, exp) in golden) {
            assertEquals(exp.hp, type.hp, "$type.hp")
            assertEquals(exp.speedMul, type.speedMul, 1e-4f, "$type.speedMul")
            assertEquals(exp.radiusMul, type.radiusMul, 1e-4f, "$type.radiusMul")
            assertEquals(exp.scoreOnKill, type.scoreOnKill, "$type.scoreOnKill")
            assertEquals(exp.spawnWeight, type.spawnWeight, 1e-6, "$type.spawnWeight")
        }
        assertEquals(9, golden.size)
    }

    @Test
    fun `regularPool excludes gorilla and contains all others`() {
        val pool = EnemyType.regularPool
        assertFalse(pool.contains(EnemyType.GORILLA), "gorilla must not be in regularPool")
        assertEquals(8, pool.size)
        for (t in pool) assertTrue(t.spawnWeight > 0, "$t spawnWeight should be >0")
    }

    @Test
    fun `weightedRandom distribution within tolerance`() {
        val seed = 42L
        val rng = Random(seed)
        val n = 100_000
        val counts = mutableMapOf<EnemyType, Int>()
        repeat(n) {
            val t = EnemyType.weightedRandom(rng = rng)
            counts[t] = (counts[t] ?: 0) + 1
        }

        assertNull(counts[EnemyType.GORILLA], "gorilla should never be drawn from regularPool")

        val total = EnemyType.regularPool.sumOf { it.spawnWeight }
        for (t in EnemyType.regularPool) {
            val expected = t.spawnWeight / total
            val observed = (counts[t] ?: 0).toDouble() / n
            val diff = kotlin.math.abs(observed - expected)
            assertTrue(diff < 0.02,
                "$t: expected ${String.format("%.3f", expected)} observed ${String.format("%.3f", observed)} diff ${String.format("%.4f", diff)}")
        }
    }

    @Test
    fun `weightedRandom determinism - same seed same sequence`() {
        val draws1 = (0 until 100).map { EnemyType.weightedRandom(rng = Random(99)) }
        val draws2 = (0 until 100).map { EnemyType.weightedRandom(rng = Random(99)) }
        assertEquals(draws1, draws2)
    }

    @Test
    fun `attack behavior mapping`() {
        assertTrue(EnemyType.HEAVY_COSMONAUT.attack is AttackBehavior.Shoot)
        val hcShoot = EnemyType.HEAVY_COSMONAUT.attack as AttackBehavior.Shoot
        assertEquals(2.5, hcShoot.intervalRange.start, 1e-6)
        assertEquals(4.0, hcShoot.intervalRange.endInclusive, 1e-6)

        assertTrue(EnemyType.ASTRO_SNIPER.attack is AttackBehavior.Shoot)
        val asShoot = EnemyType.ASTRO_SNIPER.attack as AttackBehavior.Shoot
        assertEquals(1.8, asShoot.intervalRange.start, 1e-6)
        assertEquals(3.0, asShoot.intervalRange.endInclusive, 1e-6)

        assertTrue(EnemyType.GORILLA.attack is AttackBehavior.BombThrow)
        assertEquals(3.0, (EnemyType.GORILLA.attack as AttackBehavior.BombThrow).interval, 1e-6)

        for (t in EnemyType.entries) {
            if (t != EnemyType.HEAVY_COSMONAUT && t != EnemyType.ASTRO_SNIPER && t != EnemyType.GORILLA) {
                assertTrue(t.attack is AttackBehavior.Melee, "$t should be Melee")
            }
        }
    }

    @Test
    fun `cooldown - melee returns infinity`() {
        val cd = AttackBehavior.Melee.initialCooldown()
        assertEquals(Double.POSITIVE_INFINITY, cd)
        assertEquals(Double.POSITIVE_INFINITY, AttackBehavior.Melee.nextCooldown())
    }

    @Test
    fun `cooldown - shoot returns value in range`() {
        val rng = Random(77)
        val shoot = AttackBehavior.Shoot(2.5..4.0)
        repeat(100) {
            val cd = shoot.initialCooldown(rng)
            assertTrue(cd >= 2.5 && cd < 4.0, "cooldown $cd out of range 2.5..4.0")
        }
        repeat(100) {
            val cd = shoot.nextCooldown(rng)
            assertTrue(cd >= 2.5 && cd < 4.0, "nextCooldown $cd out of range")
        }
    }

    @Test
    fun `cooldown - shoot deterministic with seed`() {
        val c1 = AttackBehavior.Shoot(1.8..3.0).initialCooldown(Random(42))
        val c2 = AttackBehavior.Shoot(1.8..3.0).initialCooldown(Random(42))
        assertEquals(c1, c2)
    }

    @Test
    fun `cooldown - bombThrow returns exact interval`() {
        val bomb = AttackBehavior.BombThrow(3.0)
        assertEquals(3.0, bomb.initialCooldown(), 1e-9)
        assertEquals(3.0, bomb.nextCooldown(), 1e-9)
    }
}
