package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2

interface Collidable {
    val position: Vector2
    val radius: Float
    val category: Int
}

data class Contact(val a: Collidable, val b: Collidable)

class CollisionSystem {

    private val buckets = HashMap<Int, MutableList<Collidable>>()
    private val contacts = mutableListOf<Contact>()

    private val categoryPairs = listOf(
        Category.bullet to Category.enemy,
        Category.player to Category.enemy,
        Category.enemyBullet to Category.player,
        Category.player to Category.pickup
    )

    fun register(c: Collidable) {
        buckets.getOrPut(c.category) { mutableListOf() }.add(c)
    }

    fun unregister(c: Collidable) {
        buckets[c.category]?.remove(c)
    }

    fun clear() {
        buckets.values.forEach { it.clear() }
    }

    fun update(): List<Contact> {
        contacts.clear()

        for ((catA, catB) in categoryPairs) {
            val listA = buckets[catA] ?: continue
            val listB = buckets[catB] ?: continue
            if (listA.isEmpty() || listB.isEmpty()) continue

            val gridA = buildGrid(listA)
            testGridPairs(gridA, listB)
        }

        return contacts
    }

    private fun buildGrid(list: List<Collidable>): HashMap<Long, MutableList<Collidable>> {
        val grid = HashMap<Long, MutableList<Collidable>>(list.size)
        for (c in list) {
            val key = cellKey(c.position.x, c.position.y)
            grid.getOrPut(key) { mutableListOf() }.add(c)
        }
        return grid
    }

    private fun testGridPairs(gridA: HashMap<Long, MutableList<Collidable>>, listB: List<Collidable>) {
        for (b in listB) {
            val cx = cellX(b.position.x)
            val cy = cellY(b.position.y)
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val key = packKey(cx + dx, cy + dy)
                    val cell = gridA[key] ?: continue
                    for (a in cell) {
                        if (overlaps(a, b)) {
                            contacts.add(Contact(a, b))
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val CELL_SIZE = 64f

        private fun cellX(x: Float): Int = (x / CELL_SIZE).toInt() - (if (x < 0) 1 else 0)
        private fun cellY(y: Float): Int = (y / CELL_SIZE).toInt() - (if (y < 0) 1 else 0)
        private fun packKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
        private fun cellKey(x: Float, y: Float): Long = packKey(cellX(x), cellY(y))

        fun overlaps(a: Collidable, b: Collidable): Boolean {
            val dx = a.position.x - b.position.x
            val dy = a.position.y - b.position.y
            val r = a.radius + b.radius
            return dx * dx + dy * dy <= r * r
        }
    }
}
