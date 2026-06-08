package dev.copt.galaxymonkey

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import kotlin.random.Random

class PickupSystem(private val rng: Random = Random.Default) {

    class Pickup(
        val position: Vector2,
        var remaining: Float,
        var phase: Float,
    ) {
        var bobOffsetY = 0f
        val collisionRadius = Tuning.Pickup.radius
        val categoryBits = Category.pickup
        val contactBits = Category.player
        val sprite = Sprite.GOLDEN_BANANA
    }

    val active: MutableList<Pickup> = mutableListOf()

    fun trySpawnGoldenBanana(at: Vector2) {
        if (rng.nextFloat() >= Tuning.Pickup.dropChance) return
        spawnGoldenBanana(at)
    }

    fun spawnGoldenBanana(at: Vector2) {
        active.add(Pickup(
            position = Vector2(at),
            remaining = Tuning.Pickup.lifetime,
            phase = rng.nextFloat() * MathUtils.PI2,
        ))
    }

    fun update(dt: Float) {
        val bobStep = MathUtils.PI2 / Tuning.Pickup.bobPeriod
        val iter = active.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.remaining -= dt
            p.phase += dt * bobStep
            p.bobOffsetY = MathUtils.sin(p.phase) * Tuning.Pickup.bobAmplitude
            if (p.remaining <= 0f) {
                iter.remove()
            }
        }
    }

    fun collect(pickup: Pickup) {
        active.remove(pickup)
    }

    fun clearAll() {
        active.clear()
    }
}
