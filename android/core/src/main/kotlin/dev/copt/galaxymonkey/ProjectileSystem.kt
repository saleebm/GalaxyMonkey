package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Pool as GdxPool
import kotlin.math.cos
import kotlin.math.sin

class Bullet : Collidable, GdxPool.Poolable {
    override val position: Vector2 = Vector2()
    val velocity: Vector2 = Vector2()
    override var radius: Float = Tuning.Projectile.radius
    override var category: Int = Category.bullet
    var contactTest: Int = Category.enemy
    var remaining: Float = 0f
    var isPoolable: Boolean = true
    var isBomb: Boolean = false
    var spriteKey: Sprite = Sprite.BULLET
    var visualRadius: Float = Tuning.Projectile.radius
    var spinPeriod: Float? = null
    var zRotation: Float = 0f
    var isHidden: Boolean = false

    override fun reset() {
        position.set(0f, 0f)
        velocity.set(0f, 0f)
        radius = Tuning.Projectile.radius
        category = Category.bullet
        contactTest = Category.enemy
        remaining = 0f
        isPoolable = true
        isBomb = false
        spriteKey = Sprite.BULLET
        visualRadius = Tuning.Projectile.radius
        spinPeriod = null
        zRotation = 0f
        isHidden = false
    }
}

class ProjectileSystem {

    private val pool = GmPool(::Bullet, initialCapacity = Tuning.Projectile.poolSize)
    private val _active = mutableListOf<Bullet>()
    val active: List<Bullet> get() = _active

    private var lastShotTime: Float = -1f

    var onBombExpire: ((Vector2) -> Unit)? = null

    fun tryFirePlayer(from: Vector2, angle: Float, spreadLevel: Int, now: Float): Boolean {
        if (now - lastShotTime < Tuning.Projectile.fireInterval) return false
        lastShotTime = now

        for (a in spreadAngles(angle, spreadLevel)) {
            val b = pool.obtain()
            configureFired(b, from, a, Tuning.Projectile.speed, Tuning.Projectile.lifetime,
                Tuning.Projectile.radius, Category.bullet, Category.enemy,
                Sprite.BULLET, Tuning.Projectile.radius, null, isPoolable = true, isBomb = false)
            _active.add(b)
        }
        return true
    }

    fun fireEnemyBullet(from: Vector2, angle: Float) {
        val b = pool.obtain()
        val speed = Tuning.Projectile.speed * Tuning.Projectile.enemyBulletSpeedMul
        configureFired(b, from, angle, speed, Tuning.Projectile.lifetime,
            Tuning.Projectile.radius, Category.enemyBullet, Category.player,
            Sprite.ENEMY_BULLET, Tuning.Projectile.enemyBulletVisualRadius, null,
            isPoolable = true, isBomb = false)
        _active.add(b)
    }

    fun fireBomb(from: Vector2, angle: Float) {
        val b = Bullet()
        configureFired(b, from, angle, Tuning.Projectile.bombSpeed, Tuning.Projectile.bombLifetime,
            Tuning.Projectile.bombRadius, Category.enemyBullet, Category.player,
            Sprite.BOMB, Tuning.Projectile.bombRadius, Tuning.Projectile.bombSpinPeriod,
            isPoolable = false, isBomb = true)
        _active.add(b)
    }

    fun update(dt: Float) {
        val iter = _active.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            b.remaining -= dt
            b.position.x += b.velocity.x * dt
            b.position.y += b.velocity.y * dt
            if (b.remaining <= 0) {
                if (b.isBomb) onBombExpire?.invoke(Vector2(b.position))
                iter.remove()
                recycleBullet(b)
            }
        }
    }

    fun recycle(bullet: Bullet) {
        _active.remove(bullet)
        recycleBullet(bullet)
    }

    fun clearAll() {
        for (b in _active) recycleBullet(b)
        _active.clear()
    }

    fun findBullet(c: Collidable): Bullet? = _active.find { it === c }

    private fun recycleBullet(b: Bullet) {
        if (b.isPoolable) pool.free(b) // bombs are not returned to pool
    }

    private fun configureFired(
        b: Bullet, from: Vector2, angle: Float, speed: Float, lifetime: Float,
        collisionRadius: Float, cat: Int, contactCat: Int,
        sprite: Sprite, visRadius: Float, spin: Float?,
        isPoolable: Boolean, isBomb: Boolean
    ) {
        b.position.set(from)
        b.zRotation = angle
        b.velocity.set(cos(angle) * speed, sin(angle) * speed)
        b.remaining = lifetime
        b.radius = collisionRadius
        b.category = cat
        b.contactTest = contactCat
        b.spriteKey = sprite
        b.visualRadius = visRadius
        b.spinPeriod = spin
        b.isPoolable = isPoolable
        b.isBomb = isBomb
        b.isHidden = false
    }

    companion object {
        fun spreadAngles(base: Float, level: Int): List<Float> {
            val off = Tuning.Projectile.spreadOffset
            return when (level) {
                0 -> listOf(base)
                1 -> listOf(base - off, base + off)
                else -> listOf(base - 2 * off, base, base + 2 * off)
            }
        }
    }
}
