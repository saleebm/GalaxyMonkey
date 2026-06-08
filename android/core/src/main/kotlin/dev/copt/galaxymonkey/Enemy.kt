package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2

class Enemy(
    val type: EnemyType,
    var hp: Int,
    val speed: Float,
    override val radius: Float,
    var facingLeft: Boolean,
    var attackCooldown: Double
) : Collidable {

    enum class AnimState { WALK, IDLE }

    override val position: Vector2 = Vector2()
    override val category: Int = Category.enemy
    val contactTest: Int = Category.player or Category.bullet

    var animState: AnimState = AnimState.WALK
    var currentSet: AnimationSet? = null

    val zLayer: Int get() = if (type == EnemyType.GORILLA) 47 else 45
}
