package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ProjectileSystemTest {

    private lateinit var sys: ProjectileSystem
    private val origin = Vector2(100f, 200f)

    @BeforeEach
    fun setup() { sys = ProjectileSystem() }

    @Test
    fun `fire cooldown - rejects within interval, accepts after`() {
        assertTrue(sys.tryFirePlayer(origin, 0f, 0, 0f))
        assertFalse(sys.tryFirePlayer(origin, 0f, 0, 0.10f), "0.10 < 0.15 fireInterval")
        assertTrue(sys.tryFirePlayer(origin, 0f, 0, 0.15f), "exactly at fireInterval")
    }

    @Test
    fun `spread level 0 - single bullet at base angle`() {
        sys.tryFirePlayer(origin, 1.0f, 0, 0f)
        assertEquals(1, sys.active.size)
        assertEquals(1.0f, sys.active[0].zRotation, 1e-4f)
    }

    @Test
    fun `spread level 1 - two bullets at base +- offset`() {
        val off = Tuning.Projectile.spreadOffset
        sys.tryFirePlayer(origin, 0f, 1, 0f)
        assertEquals(2, sys.active.size)
        val angles = sys.active.map { it.zRotation }.sorted()
        assertEquals(-off, angles[0], 1e-4f, "first angle should be -pi/24")
        assertEquals(off, angles[1], 1e-4f, "second angle should be +pi/24")
    }

    @Test
    fun `spread level 2 - three bullets at base and +- 2 offset`() {
        val off = Tuning.Projectile.spreadOffset
        sys.tryFirePlayer(origin, 0f, 2, 0f)
        assertEquals(3, sys.active.size)
        val angles = sys.active.map { it.zRotation }.sorted()
        assertEquals(-2 * off, angles[0], 1e-4f)
        assertEquals(0f, angles[1], 1e-4f)
        assertEquals(2 * off, angles[2], 1e-4f)
    }

    @Test
    fun `bullet velocity matches cos sin times speed`() {
        val angle = 0.5f
        sys.tryFirePlayer(origin, angle, 0, 0f)
        val b = sys.active[0]
        val spd = Tuning.Projectile.speed
        assertEquals(cos(angle) * spd, b.velocity.x, 0.01f)
        assertEquals(sin(angle) * spd, b.velocity.y, 0.01f)
    }

    @Test
    fun `pool reuse - recycled bullet is reused on next fire`() {
        sys.tryFirePlayer(origin, 0f, 0, 0f)
        val first = sys.active[0]
        sys.recycle(first)
        assertEquals(0, sys.active.size)

        sys.tryFirePlayer(origin, 0f, 0, 1f)
        val second = sys.active[0]
        assertSame(first, second, "should reuse the pooled Bullet instance")
    }

    @Test
    fun `enemy bullet - category and speed mul`() {
        sys.fireEnemyBullet(origin, 0f)
        val b = sys.active[0]
        assertEquals(Category.enemyBullet, b.category)
        assertEquals(Category.player, b.contactTest)
        val expectedSpeed = Tuning.Projectile.speed * Tuning.Projectile.enemyBulletSpeedMul
        assertEquals(expectedSpeed, b.velocity.x, 0.1f)
        assertEquals(Tuning.Projectile.radius, b.radius, 1e-4f, "collision radius stays default")
        assertEquals(Tuning.Projectile.enemyBulletVisualRadius, b.visualRadius, 1e-4f)
    }

    @Test
    fun `bomb - non-poolable with correct stats`() {
        sys.fireBomb(origin, 0f)
        val b = sys.active[0]
        assertTrue(b.isBomb)
        assertFalse(b.isPoolable)
        assertEquals(Tuning.Projectile.bombRadius, b.radius, 1e-4f)
        assertEquals(Tuning.Projectile.bombSpinPeriod, b.spinPeriod!!, 1e-4f)
        val expectedSpeed = Tuning.Projectile.bombSpeed
        assertEquals(expectedSpeed, b.velocity.x, 0.1f)
    }

    @Test
    fun `bomb detonation - onBombExpire fires on timeout`() {
        val explosions = mutableListOf<Vector2>()
        sys.onBombExpire = { explosions.add(it) }
        sys.fireBomb(origin, 0f)

        val steps = (Tuning.Projectile.bombLifetime / (1f / 60f)).toInt() + 5
        repeat(steps) { sys.update(1f / 60f) }

        assertEquals(1, explosions.size, "onBombExpire should fire exactly once")
        assertEquals(0, sys.active.size, "bomb should be removed after timeout")
    }

    @Test
    fun `player bullet timeout - recycled silently, no bomb callback`() {
        val explosions = mutableListOf<Vector2>()
        sys.onBombExpire = { explosions.add(it) }
        sys.tryFirePlayer(origin, 0f, 0, 0f)

        val steps = (Tuning.Projectile.lifetime / (1f / 60f)).toInt() + 5
        repeat(steps) { sys.update(1f / 60f) }

        assertEquals(0, sys.active.size, "bullet should be recycled after lifetime")
        assertEquals(0, explosions.size, "onBombExpire should NOT fire for regular bullets")
    }

    @Test
    fun `manual integration - position updates correctly`() {
        sys.tryFirePlayer(origin, 0f, 0, 0f)
        val b = sys.active[0]
        val startX = b.position.x
        val startY = b.position.y
        val dt = 1f / 60f
        sys.update(dt)
        assertEquals(startX + b.velocity.x * dt, b.position.x, 0.01f)
        assertEquals(startY + b.velocity.y * dt, b.position.y, 0.01f)
    }

    @Test
    fun `clearAll empties active list`() {
        sys.tryFirePlayer(origin, 0f, 2, 0f)
        sys.fireBomb(origin, 0f)
        assertTrue(sys.active.size >= 4)
        sys.clearAll()
        assertEquals(0, sys.active.size)
    }
}
