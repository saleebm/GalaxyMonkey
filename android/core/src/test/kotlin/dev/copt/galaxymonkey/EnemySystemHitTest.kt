package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class EnemySystemHitTest {

    private lateinit var sys: EnemySystem

    @BeforeEach
    fun setup() {
        sys = EnemySystem(rng = Random(42))
    }

    private fun inject(type: EnemyType, x: Float = 100f, y: Float = 100f): Enemy {
        val e = Enemy(
            type = type,
            hp = type.hp,
            speed = Tuning.Enemy.baseSpeed * type.speedMul,
            radius = Tuning.Enemy.radius * type.radiusMul,
            facingLeft = false,
            attackCooldown = Double.POSITIVE_INFINITY
        )
        e.position.set(x, y)
        sys.enemies.add(e)
        return e
    }

    @Test
    fun `hp decrement - heavyCosmonaut survives 2 hits, dies on 3rd`() {
        val e = inject(EnemyType.HEAVY_COSMONAUT)
        assertEquals(3, e.hp)

        val r1 = sys.applyHit(e)
        assertInstanceOf(EnemyHitResult.StillAlive::class.java, r1)
        assertEquals(2, e.hp)

        val r2 = sys.applyHit(e)
        assertInstanceOf(EnemyHitResult.StillAlive::class.java, r2)
        assertEquals(1, e.hp)

        val r3 = sys.applyHit(e)
        assertInstanceOf(EnemyHitResult.Killed::class.java, r3)
        val killed = r3 as EnemyHitResult.Killed
        assertEquals(250, killed.score)
        assertEquals(EnemyType.HEAVY_COSMONAUT, killed.type)
        assertSame(e, killed.enemy)
    }

    @Test
    fun `one-hit enemy dies on first applyHit`() {
        val e = inject(EnemyType.PREPPY)
        assertEquals(1, e.hp)

        val r = sys.applyHit(e)
        assertInstanceOf(EnemyHitResult.Killed::class.java, r)
        assertEquals(100, (r as EnemyHitResult.Killed).score)
    }

    @Test
    fun `boss counter - non-gorilla kills increment regularKillsSinceBoss`() {
        assertEquals(0, sys.regularKillsSinceBoss)
        val e1 = inject(EnemyType.PREPPY)
        sys.applyHit(e1)
        assertEquals(1, sys.regularKillsSinceBoss)

        val e2 = inject(EnemyType.WHITE)
        sys.applyHit(e2)
        assertEquals(2, sys.regularKillsSinceBoss)
    }

    @Test
    fun `boss counter - gorilla kill clears bossAlive, does not increment counter`() {
        sys.bossAlive = true
        sys.regularKillsSinceBoss = 5
        val gorilla = inject(EnemyType.GORILLA)
        gorilla.hp = 1

        sys.applyHit(gorilla)
        assertFalse(sys.bossAlive, "bossAlive should be false after gorilla killed")
        assertEquals(5, sys.regularKillsSinceBoss, "gorilla kill should not increment counter")
    }

    @Test
    fun `killByRef consumes enemy regardless of HP`() {
        val e = inject(EnemyType.HEAVY_COSMONAUT)
        assertEquals(3, e.hp)

        val returned = sys.killByRef(e)
        assertSame(e, returned)
        assertFalse(sys.contains(e), "enemy should be removed from active list")

        val r = sys.applyHit(e)
        assertInstanceOf(EnemyHitResult.StillAlive::class.java, r,
            "applyHit on removed enemy should return StillAlive (not found)")
    }

    @Test
    fun `detach removes from active sim`() {
        val e = inject(EnemyType.PREPPY, 600f, 300f)
        assertTrue(sys.contains(e))

        sys.applyHit(e)
        assertFalse(sys.contains(e), "killed enemy should not be in active list")
        assertNull(e.currentSet, "detached enemy should have currentSet cleared")
    }

    @Test
    fun `detach - homing no longer moves dead enemy`() {
        val sys2 = EnemySystem(
            playerPosition = { Vector2(400f, 300f) },
            rng = Random(42)
        )
        val e = Enemy(
            type = EnemyType.PREPPY, hp = 1,
            speed = Tuning.Enemy.baseSpeed,
            radius = Tuning.Enemy.radius,
            facingLeft = false,
            attackCooldown = Double.POSITIVE_INFINITY
        )
        e.position.set(600f, 300f)
        sys2.enemies.add(e)
        sys2.applyHit(e)
        val posAfterDeath = Vector2(e.position)
        sys2.update(1f / 60f)
        assertEquals(posAfterDeath.x, e.position.x, 0.001f, "dead enemy should not move")
        assertEquals(posAfterDeath.y, e.position.y, 0.001f, "dead enemy should not move")
    }

    @Test
    fun `applyHit on unknown enemy returns StillAlive`() {
        val orphan = Enemy(
            type = EnemyType.PREPPY, hp = 1,
            speed = 80f, radius = 22f,
            facingLeft = false,
            attackCooldown = Double.POSITIVE_INFINITY
        )
        val r = sys.applyHit(orphan)
        assertInstanceOf(EnemyHitResult.StillAlive::class.java, r)
        assertEquals(1, orphan.hp, "hp should not change for unknown enemy")
    }

    @Test
    fun `killByRef on unknown enemy returns null`() {
        val orphan = Enemy(
            type = EnemyType.PREPPY, hp = 1,
            speed = 80f, radius = 22f,
            facingLeft = false,
            attackCooldown = Double.POSITIVE_INFINITY
        )
        assertNull(sys.killByRef(orphan))
    }
}
