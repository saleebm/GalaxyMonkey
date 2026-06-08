package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import kotlin.math.min
import kotlin.random.Random

enum class ProjectileKind { BULLET, BOMB }

class EnemySystem(
    private val playerPosition: () -> Vector2 = { Vector2() },
    private val cameraPosition: () -> Vector2 = { Vector2() },
    private val viewSize: () -> Vector2 = { Vector2(812f, 375f) },
    private val enemyFireRequest: (origin: Vector2, angle: Float, kind: ProjectileKind) -> Unit = { _, _, _ -> },
    private val rng: Random = Random
) {

    val enemies = mutableListOf<Enemy>()
    private var spawnTimer = 0.0
    private var elapsed = 0.0
    var regularKillsSinceBoss = 0
    var bossAlive = false

    fun update(dt: Float) {
        elapsed += dt
        spawnTimer -= dt

        if (spawnTimer <= 0) {
            spawn()
            val t = min(1.0, elapsed / Tuning.Enemy.rampDuration)
            val interval = Tuning.Enemy.spawnIntervalStart +
                (Tuning.Enemy.spawnIntervalEnd - Tuning.Enemy.spawnIntervalStart) * t
            spawnTimer = interval.toDouble()
        }
    }

    private fun spawn() {
        val cam = cameraPosition()
        val vs = viewSize()
        if (vs.x <= 0 || vs.y <= 0) return

        val pad = Tuning.Camera.enemySpawnViewPaddingPx
        val hw = vs.x / 2f + pad
        val hh = vs.y / 2f + pad
        val minX = cam.x - hw
        val maxX = cam.x + hw
        val minY = cam.y - hh
        val maxY = cam.y + hh

        val side = rng.nextInt(4)
        val pos = when (side) {
            0 -> Vector2(minX, rng.nextFloat() * (maxY - minY) + minY)
            1 -> Vector2(maxX, rng.nextFloat() * (maxY - minY) + minY)
            2 -> Vector2(rng.nextFloat() * (maxX - minX) + minX, minY)
            else -> Vector2(rng.nextFloat() * (maxX - minX) + minX, maxY)
        }

        val type = if (!bossAlive && regularKillsSinceBoss >= Tuning.Enemy.bossEveryNKills) {
            bossAlive = true
            regularKillsSinceBoss = 0
            EnemyType.GORILLA
        } else {
            EnemyType.weightedRandom(rng = rng)
        }

        makeEnemy(type, pos, cam)
    }

    private fun makeEnemy(type: EnemyType, pos: Vector2, cam: Vector2) {
        val radius = Tuning.Enemy.radius * type.radiusMul
        val baseSpeed = Tuning.Enemy.baseSpeed * type.speedMul
        val jitter = rng.nextFloat() * 2 * Tuning.Enemy.speedJitter - Tuning.Enemy.speedJitter
        val speed = baseSpeed + jitter
        val facingLeft = pos.x >= cam.x
        val cooldown = type.attack.initialCooldown(rng)

        val enemy = Enemy(
            type = type,
            hp = type.hp,
            speed = speed,
            radius = radius,
            facingLeft = facingLeft,
            attackCooldown = cooldown
        )
        enemy.position.set(pos)
        enemy.currentSet = walkAnimation(type, facingLeft) ?: idleAnimation(type, facingLeft)
        enemies.add(enemy)
    }

    fun reset() {
        enemies.clear()
        spawnTimer = 0.0
        elapsed = 0.0
        regularKillsSinceBoss = 0
        bossAlive = false
    }

    companion object {
        fun walkAnimation(type: EnemyType, facingLeft: Boolean): AnimationSet? = when (type) {
            EnemyType.GORILLA -> null
            EnemyType.DRONE_SWARM -> null
            EnemyType.PLASMA_JELLY -> null
            EnemyType.MINI_BOSS -> AnimationSet.MINI_BOSS_WALK
            EnemyType.PREPPY -> if (facingLeft) AnimationSet.PREPPY_LEFT_WALK else AnimationSet.PREPPY_RIGHT_WALK
            EnemyType.WHITE -> if (facingLeft) AnimationSet.WHITE_LEFT_WALK else AnimationSet.WHITE_RIGHT_WALK
            EnemyType.SHADY -> if (facingLeft) AnimationSet.SHADY_LEFT_WALK else AnimationSet.SHADY_RIGHT_WALK
            EnemyType.HEAVY_COSMONAUT -> if (facingLeft) AnimationSet.HEAVY_COSMONAUT_LEFT_WALK else AnimationSet.HEAVY_COSMONAUT_RIGHT_WALK
            EnemyType.ASTRO_SNIPER -> if (facingLeft) AnimationSet.ASTRO_SNIPER_LEFT_WALK else AnimationSet.ASTRO_SNIPER_RIGHT_WALK
        }

        fun idleAnimation(type: EnemyType, facingLeft: Boolean): AnimationSet? = when (type) {
            EnemyType.GORILLA -> if (facingLeft) AnimationSet.GORILLA_LEFT_IDLE else AnimationSet.GORILLA_RIGHT_IDLE
            EnemyType.DRONE_SWARM -> if (facingLeft) AnimationSet.DRONE_SWARM_LEFT_IDLE else AnimationSet.DRONE_SWARM_RIGHT_IDLE
            EnemyType.PLASMA_JELLY -> AnimationSet.PLASMA_JELLY_IDLE
            EnemyType.MINI_BOSS -> if (facingLeft) AnimationSet.MINI_BOSS_LEFT_IDLE else AnimationSet.MINI_BOSS_RIGHT_IDLE
            EnemyType.PREPPY -> if (facingLeft) AnimationSet.PREPPY_LEFT_IDLE else AnimationSet.PREPPY_RIGHT_IDLE
            EnemyType.WHITE -> if (facingLeft) AnimationSet.WHITE_LEFT_IDLE else AnimationSet.WHITE_RIGHT_IDLE
            EnemyType.SHADY -> if (facingLeft) AnimationSet.SHADY_LEFT_IDLE else AnimationSet.SHADY_RIGHT_IDLE
            EnemyType.HEAVY_COSMONAUT -> if (facingLeft) AnimationSet.HEAVY_COSMONAUT_LEFT_IDLE else AnimationSet.HEAVY_COSMONAUT_RIGHT_IDLE
            EnemyType.ASTRO_SNIPER -> if (facingLeft) AnimationSet.ASTRO_SNIPER_LEFT_IDLE else AnimationSet.ASTRO_SNIPER_RIGHT_IDLE
        }
    }
}
