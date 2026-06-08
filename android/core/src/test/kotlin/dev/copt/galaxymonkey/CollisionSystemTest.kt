package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CollisionSystemTest {

    private data class Stub(
        override val position: Vector2 = Vector2(),
        override val radius: Float = 10f,
        override val category: Int
    ) : Collidable

    private lateinit var sys: CollisionSystem

    @BeforeEach
    fun reset() { sys = CollisionSystem() }

    @Test
    fun `overlaps - just touching returns true`() {
        val a = Stub(position = Vector2(0f, 0f), radius = 10f, category = 0)
        val b = Stub(position = Vector2(20f, 0f), radius = 10f, category = 0)
        assertTrue(CollisionSystem.overlaps(a, b), "circles at distance == rA+rB should overlap")
    }

    @Test
    fun `overlaps - overlapping returns true`() {
        val a = Stub(position = Vector2(0f, 0f), radius = 10f, category = 0)
        val b = Stub(position = Vector2(15f, 0f), radius = 10f, category = 0)
        assertTrue(CollisionSystem.overlaps(a, b))
    }

    @Test
    fun `overlaps - just separated returns false`() {
        val a = Stub(position = Vector2(0f, 0f), radius = 10f, category = 0)
        val b = Stub(position = Vector2(20.01f, 0f), radius = 10f, category = 0)
        assertFalse(CollisionSystem.overlaps(a, b), "circles beyond rA+rB should not overlap")
    }

    @Test
    fun `only allowed category pairs produce contacts`() {
        val bullet = Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.bullet)
        val enemy = Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.enemy)
        val player = Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.player)
        val enemyBullet = Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.enemyBullet)
        val pickup = Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.pickup)

        sys.register(bullet)
        sys.register(enemy)
        sys.register(player)
        sys.register(enemyBullet)
        sys.register(pickup)

        val contacts = sys.update()
        val masks = contacts.map { setOf(it.a.category, it.b.category) }.toSet()

        assertTrue(masks.contains(setOf(Category.bullet, Category.enemy)))
        assertTrue(masks.contains(setOf(Category.player, Category.enemy)))
        assertTrue(masks.contains(setOf(Category.enemyBullet, Category.player)))
        assertTrue(masks.contains(setOf(Category.player, Category.pickup)))
        assertEquals(4, contacts.size, "exactly 4 allowed pairs, no extras")
    }

    @Test
    fun `disallowed pairs produce no contacts`() {
        val bullet1 = Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.bullet)
        val bullet2 = Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.bullet)
        val pickup = Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.pickup)

        sys.register(bullet1)
        sys.register(bullet2)
        sys.register(pickup)

        val contacts = sys.update()
        assertTrue(contacts.isEmpty(), "bullet|bullet and bullet|pickup are not allowed pairs")
    }

    @Test
    fun `single overlapping pair yields exactly one contact`() {
        val bullet = Stub(position = Vector2(5f, 5f), radius = 10f, category = Category.bullet)
        val enemy = Stub(position = Vector2(8f, 5f), radius = 10f, category = Category.enemy)
        sys.register(bullet)
        sys.register(enemy)

        val contacts = sys.update()
        assertEquals(1, contacts.size, "one overlapping pair -> exactly one Contact")
    }

    @Test
    fun `well-separated entities produce no contacts`() {
        for (i in 0 until 500) {
            sys.register(Stub(
                position = Vector2(i * 200f, 0f), radius = 5f, category = Category.bullet
            ))
        }
        for (i in 0 until 500) {
            sys.register(Stub(
                position = Vector2(i * 200f + 100f, 0f), radius = 5f, category = Category.enemy
            ))
        }

        val contacts = sys.update()
        assertEquals(0, contacts.size, "1000 well-separated entities should yield zero contacts")
    }

    @Test
    fun `update clears contacts between frames`() {
        val bullet = Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.bullet)
        val enemy = Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.enemy)
        sys.register(bullet)
        sys.register(enemy)

        sys.update()
        sys.unregister(bullet)
        sys.unregister(enemy)

        val contacts = sys.update()
        assertEquals(0, contacts.size, "stale contacts should not persist after entities removed")
    }

    @Test
    fun `clear removes all entities`() {
        sys.register(Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.bullet))
        sys.register(Stub(position = Vector2(0f, 0f), radius = 50f, category = Category.enemy))
        sys.clear()

        val contacts = sys.update()
        assertEquals(0, contacts.size)
    }
}
