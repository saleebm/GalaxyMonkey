package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

enum class ProjectileKind { BULLET, BOMB }

sealed class EnemyHitResult {
    data object StillAlive : EnemyHitResult()
    data class Killed(val score: Int, val position: Vector2, val type: EnemyType, val enemy: Enemy) : EnemyHitResult()
}

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

        val target = playerPosition()
        for (e in enemies) {
            val prevX = e.position.x
            val prevY = e.position.y

            val dx = target.x - e.position.x
            val dy = target.y - e.position.y
            val mag = max(0.0001f, sqrt(dx * dx + dy * dy))
            e.position.x += dx / mag * e.speed * dt
            e.position.y += dy / mag * e.speed * dt

            updateFacing(e, dx)
            updateMotionState(e, prevX, prevY, dt)
            tickAttack(e, dx, dy, mag, dt)
            if (e.windupActive) tickWindup(e, dt)
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

    private fun updateFacing(e: Enemy, dx: Float) {
        if (abs(dx) < Tuning.Enemy.facingFlipDeadzonePx) return
        val desiredLeft = dx < 0
        if (desiredLeft == e.facingLeft) return
        if (e.windupActive) return
        e.facingLeft = desiredLeft
        applyFacingVisual(e)
    }

    private fun applyFacingVisual(e: Enemy) {
        val anim = if (e.animState == Enemy.AnimState.WALK)
            walkAnimation(e.type, e.facingLeft) ?: idleAnimation(e.type, e.facingLeft)
        else
            idleAnimation(e.type, e.facingLeft)
        if (anim != e.currentSet) e.currentSet = anim
    }

    private fun updateMotionState(e: Enemy, prevX: Float, prevY: Float, dt: Float) {
        if (dt <= 0f) return
        val ddx = e.position.x - prevX
        val ddy = e.position.y - prevY
        val observedSpeed = sqrt(ddx * ddx + ddy * ddy) / dt
        val desired = if (observedSpeed >= Tuning.Enemy.walkSpeedThresholdPx)
            Enemy.AnimState.WALK else Enemy.AnimState.IDLE
        if (desired == e.animState) return
        e.animState = desired
        val anim = if (desired == Enemy.AnimState.WALK)
            walkAnimation(e.type, e.facingLeft) ?: idleAnimation(e.type, e.facingLeft)
        else
            idleAnimation(e.type, e.facingLeft)
        if (anim != e.currentSet) e.currentSet = anim
    }

    private fun tickAttack(e: Enemy, dx: Float, dy: Float, dist: Float, dt: Float) {
        if (e.attackCooldown == Double.POSITIVE_INFINITY) return
        e.attackCooldown -= dt
        if (e.attackCooldown > 0) return

        when (val atk = e.type.attack) {
            is AttackBehavior.Melee -> return
            is AttackBehavior.Shoot -> {
                if (dist > Tuning.Enemy.fireRangePx) {
                    e.attackCooldown = 0.1
                    return
                }
                val angle = atan2(dy, dx)
                enemyFireRequest(Vector2(e.position.x, e.position.y), angle, ProjectileKind.BULLET)
                e.attackCooldown = atk.nextCooldown(rng)
            }
            is AttackBehavior.BombThrow -> {
                val angle = atan2(dy, dx)
                startWindup(e, angle)
                e.attackCooldown = atk.nextCooldown(rng) + WINDUP_COOLDOWN_PAD
            }
        }
    }

    private fun startWindup(e: Enemy, angle: Float) {
        e.windupActive = true
        e.windupTimer = 0f
        e.windupFrameIndex = 0
        e.windupFired = false
        e.currentSet = if (e.facingLeft)
            AnimationSet.GORILLA_LEFT_WINDUP else AnimationSet.GORILLA_RIGHT_WINDUP
        windupAngles[e] = angle
    }

    private val windupAngles = mutableMapOf<Enemy, Float>()

    private fun tickWindup(e: Enemy, dt: Float) {
        e.windupTimer += dt
        var accumulated = 0f
        for (i in WINDUP_FRAME_DURATIONS.indices) {
            accumulated += WINDUP_FRAME_DURATIONS[i]
            if (e.windupTimer < accumulated) {
                e.windupFrameIndex = i
                break
            }
            if (i == WINDUP_FRAME_DURATIONS.lastIndex) {
                finishWindup(e)
                return
            }
        }
        if (!e.windupFired && e.windupFrameIndex >= WINDUP_RELEASE_FRAME) {
            e.windupFired = true
            val angle = windupAngles[e] ?: 0f
            val origin = Vector2(e.position.x, e.position.y + e.radius * 0.4f)
            enemyFireRequest(origin, angle, ProjectileKind.BOMB)
        }
    }

    private fun finishWindup(e: Enemy) {
        if (!e.windupFired) {
            val angle = windupAngles[e] ?: 0f
            val origin = Vector2(e.position.x, e.position.y + e.radius * 0.4f)
            enemyFireRequest(origin, angle, ProjectileKind.BOMB)
            e.windupFired = true
        }
        e.windupActive = false
        e.windupTimer = 0f
        e.windupFrameIndex = 0
        windupAngles.remove(e)
        e.animState = Enemy.AnimState.IDLE
        e.currentSet = idleAnimation(e.type, e.facingLeft)
    }

    fun applyHit(enemy: Enemy): EnemyHitResult {
        val idx = enemies.indexOf(enemy)
        if (idx < 0) return EnemyHitResult.StillAlive
        enemy.hp -= 1
        if (enemy.hp > 0) return EnemyHitResult.StillAlive
        detachForDeath(idx)
        return EnemyHitResult.Killed(enemy.type.scoreOnKill, Vector2(enemy.position), enemy.type, enemy)
    }

    fun killByRef(enemy: Enemy): Enemy? {
        val idx = enemies.indexOf(enemy)
        if (idx < 0) return null
        detachForDeath(idx)
        return enemy
    }

    fun contains(enemy: Enemy): Boolean = enemy in enemies

    private fun detachForDeath(index: Int): Enemy {
        val e = enemies.removeAt(index)
        e.currentSet = null
        if (e.type == EnemyType.GORILLA) {
            bossAlive = false
        } else {
            regularKillsSinceBoss += 1
        }
        windupAngles.remove(e)
        return e
    }

    fun reset() {
        enemies.clear()
        windupAngles.clear()
        spawnTimer = 0.0
        elapsed = 0.0
        regularKillsSinceBoss = 0
        bossAlive = false
    }

    companion object {
        val WINDUP_FRAME_DURATIONS = floatArrayOf(0.16f, 0.14f, 0.12f, 0.10f, 0.08f, 0.08f, 0.14f, 0.18f)
        const val WINDUP_RELEASE_FRAME = 3
        const val WINDUP_COOLDOWN_PAD = 1.0

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
