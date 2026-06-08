package dev.copt.galaxymonkey

import kotlin.random.Random

enum class EnemyType(
    val hp: Int,
    val speedMul: Float,
    val radiusMul: Float,
    val scoreOnKill: Int,
    val spawnWeight: Double,
    val leftSprite: Sprite,
    val rightSprite: Sprite,
    val attack: AttackBehavior
) {
    DRONE_SWARM(1, 1.5f, 0.7f, 50, 3.0,
        Sprite.DRONE_SWARM_LEFT, Sprite.DRONE_SWARM_RIGHT, AttackBehavior.Melee),
    PREPPY(1, 1.0f, 1.0f, 100, 2.0,
        Sprite.PREPPY_LEFT, Sprite.PREPPY_RIGHT, AttackBehavior.Melee),
    WHITE(1, 1.0f, 1.0f, 100, 2.0,
        Sprite.WHITE_LEFT, Sprite.WHITE_RIGHT, AttackBehavior.Melee),
    SHADY(1, 1.0f, 1.0f, 100, 2.0,
        Sprite.SHADY_LEFT, Sprite.SHADY_RIGHT, AttackBehavior.Melee),
    HEAVY_COSMONAUT(3, 0.6f, 1.3f, 250, 1.0,
        Sprite.HEAVY_COSMONAUT_LEFT, Sprite.HEAVY_COSMONAUT_RIGHT,
        AttackBehavior.Shoot(2.5..4.0)),
    PLASMA_JELLY(2, 0.8f, 1.0f, 150, 1.5,
        Sprite.PLASMA_JELLY, Sprite.PLASMA_JELLY, AttackBehavior.Melee),
    ASTRO_SNIPER(1, 0.9f, 0.9f, 200, 1.0,
        Sprite.ASTRO_SNIPER_LEFT, Sprite.ASTRO_SNIPER_RIGHT,
        AttackBehavior.Shoot(1.8..3.0)),
    MINI_BOSS(5, 0.7f, 1.4f, 500, 0.3,
        Sprite.MINI_BOSS_LEFT, Sprite.MINI_BOSS_RIGHT, AttackBehavior.Melee),
    GORILLA(10, 0.5f, 1.6f, 1000, 0.0,
        Sprite.GORILLA_LEFT, Sprite.GORILLA_RIGHT,
        AttackBehavior.BombThrow(3.0));

    companion object {
        val regularPool: List<EnemyType> = entries.filter { it.spawnWeight > 0 }

        fun weightedRandom(
            pool: List<EnemyType> = regularPool,
            rng: Random = Random
        ): EnemyType {
            val total = pool.sumOf { it.spawnWeight }
            var roll = rng.nextDouble(0.0, total)
            for (t in pool) {
                roll -= t.spawnWeight
                if (roll <= 0) return t
            }
            return pool.lastOrNull() ?: PREPPY
        }
    }
}

sealed class AttackBehavior {
    data object Melee : AttackBehavior()
    data class Shoot(val intervalRange: ClosedRange<Double>) : AttackBehavior()
    data class BombThrow(val interval: Double) : AttackBehavior()

    fun initialCooldown(rng: Random = Random): Double = when (this) {
        is Melee -> Double.POSITIVE_INFINITY
        is Shoot -> rng.nextDouble(intervalRange.start, intervalRange.endInclusive)
        is BombThrow -> interval
    }

    fun nextCooldown(rng: Random = Random): Double = when (this) {
        is Melee -> Double.POSITIVE_INFINITY
        is Shoot -> rng.nextDouble(intervalRange.start, intervalRange.endInclusive)
        is BombThrow -> interval
    }
}
