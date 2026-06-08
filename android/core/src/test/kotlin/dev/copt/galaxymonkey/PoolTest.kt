package dev.copt.galaxymonkey

import com.badlogic.gdx.utils.Pool.Poolable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PoolTest {

    class Probe : Poolable {
        var resets = 0
        var alive = true
        override fun reset() {
            resets++
            alive = false
        }
    }

    private var creationCount = 0

    private fun makePool(initialCapacity: Int = 0, max: Int = Int.MAX_VALUE): GmPool<Probe> {
        creationCount = 0
        return GmPool(
            factory = { creationCount++; Probe() },
            initialCapacity = initialCapacity,
            max = max,
        )
    }

    @Test
    fun `prewarm allocates exactly initialCapacity instances`() {
        val pool = makePool(initialCapacity = 96)
        assertEquals(96, creationCount, "factory should be called 96 times during prewarm")
        assertEquals(96, pool.freeCount, "freeCount should be 96 after prewarm")
        println("[PoolTest] prewarm: 96 instances allocated up front, freeCount=96")
    }

    @Test
    fun `no-alloc-while-free - obtain reuses prewarmed instances`() {
        val pool = makePool(initialCapacity = 96)
        val items = (0 until 96).map { pool.obtain() }
        assertEquals(96, creationCount,
            "creationCount should stay 96 after obtaining 96 prewarmed items")
        assertEquals(0, pool.freeCount, "freeCount should be 0 after obtaining all")
        assertEquals(96, items.size)
        println("[PoolTest] no-alloc-while-free: creationCount stayed 96 across 96 obtains")
    }

    @Test
    fun `identity reuse - free then obtain returns same instance`() {
        val pool = makePool(initialCapacity = 1)
        val first = pool.obtain()
        pool.free(first)
        val second = pool.obtain()
        assertSame(first, second,
            "obtain after free should return the exact same instance")
        println("[PoolTest] reuse: obtained same identity after free (no realloc)")
    }

    @Test
    fun `reset contract - free calls reset on item`() {
        val pool = makePool(initialCapacity = 1)
        val item = pool.obtain()
        assertTrue(item.alive, "item should be alive before free")
        assertEquals(1, item.resets, "prewarm calls reset once via free()")
        pool.free(item)
        assertEquals(2, item.resets, "free should invoke reset (now 2: prewarm + explicit)")
        assertFalse(item.alive, "alive should be false after reset")
    }

    @Test
    fun `overflow beyond prewarm allocates on demand`() {
        val pool = makePool(initialCapacity = 96)
        (0 until 96).forEach { pool.obtain() }
        assertEquals(96, creationCount)
        val overflow = pool.obtain()
        assertEquals(97, creationCount,
            "one more obtain should allocate exactly one new instance")
        assertNotNull(overflow)
    }

    @Test
    fun `freeAll restores pool and resets each item`() {
        val pool = makePool(initialCapacity = 4)
        val items = (0 until 4).map { pool.obtain() }
        assertEquals(0, pool.freeCount)
        items.forEach { it.alive = true }
        items.forEach { pool.free(it) }
        assertEquals(4, pool.freeCount, "freeCount should be restored after freeing all")
        items.forEach { item ->
            assertFalse(item.alive, "each freed item should have been reset")
            assertEquals(2, item.resets, "each item: 1 prewarm reset + 1 explicit free reset")
        }
        println("[PoolTest] object pool recycle contract verified (mirrors iOS ProjectileSystem prewarm/recycle)")
    }
}
