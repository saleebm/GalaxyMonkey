package dev.copt.galaxymonkey

import com.badlogic.gdx.utils.Pool as GdxPool

// Thin wrapper over LibGDX Pool that pre-warms instances at construction
// so the first burst of obtain() calls is allocation-free. Canonical use:
// ProjectileSystem prewarms Tuning.Projectile.poolSize=96 bullets.
// Gorilla bombs are intentionally NOT pooled (different physics body size
// would corrupt the bullet pool) — the bomb path should bypass this pool.

class GmPool<T : GdxPool.Poolable>(
    private val factory: () -> T,
    initialCapacity: Int = 0,
    max: Int = Int.MAX_VALUE,
) : GdxPool<T>(initialCapacity, max) {

    init {
        for (i in 0 until initialCapacity) {
            free(factory())
        }
    }

    override fun newObject(): T = factory()

    val freeCount: Int get() = getFree()

    val peakActive: Int get() = peak
}
