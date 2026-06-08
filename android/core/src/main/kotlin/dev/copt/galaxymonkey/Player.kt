package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class Player(
    private val sceneCenterX: Float,
    private val sceneCenterY: Float,
    private val haptics: Haptics = NoOpHaptics,
) : Collidable {

    override val position: Vector2 = Vector2(
        sceneCenterX + Tuning.World.orbitEarth,
        sceneCenterY
    )
    override val radius: Float = Tuning.Player.radius
    override val category: Int = Category.player
    val contactTest: Int = Category.enemy

    val velocity: Vector2 = Vector2()

    var lives: Int = Tuning.Player.startingLives
        private set
    var isInvulnerable: Boolean = false
        private set
    var aimAngle: Float = 0f
        private set
    var isFiring: Boolean = false
        private set
    var spreadLevel: Int = 0
        private set
    var bankRotation: Float = 0f
        private set
    var visualAlpha: Float = 1f
        private set
    var thrustIntensity: Float = 0f
        private set

    private var invulnRemaining: Float = 0f
    private var blinkPhase: Double = 0.0

    val isAlive: Boolean get() = lives > 0

    var onLivesChanged: ((Int) -> Unit)? = null

    val zLayer: Int = 50

    fun update(dt: Float, moveStick: Vector2, aimStick: Vector2) {
        val dtF = dt

        val targetVx = moveStick.x * Tuning.Player.maxSpeed
        val targetVy = moveStick.y * Tuning.Player.maxSpeed

        if (moveStick.x == 0f && moveStick.y == 0f) {
            val damp = exp(-Tuning.Player.drag * dt).toFloat()
            velocity.x *= damp
            velocity.y *= damp
        } else {
            val dvx = targetVx - velocity.x
            val dvy = targetVy - velocity.y
            val mag = max(0.0001f, sqrt(dvx * dvx + dvy * dvy))
            val step = min(Tuning.Player.acceleration * dtF, mag)
            velocity.x += dvx / mag * step
            velocity.y += dvy / mag * step
        }

        position.x += velocity.x * dtF
        position.y += velocity.y * dtF

        val bankT = max(-1f, min(1f, velocity.x / Tuning.Player.maxSpeed))
        val targetBank = -bankT * Tuning.Player.maxBankRadians
        val approach = (1.0 - exp(-Tuning.Player.bankApproachRate.toDouble() * dt)).toFloat()
        bankRotation += (targetBank - bankRotation) * approach

        val aimMag = sqrt(aimStick.x * aimStick.x + aimStick.y * aimStick.y)
        if (aimMag > 0.001f) {
            aimAngle = atan2(aimStick.y, aimStick.x)
            isFiring = true
        } else {
            isFiring = false
        }

        if (isInvulnerable) {
            invulnRemaining -= dt
            blinkPhase += dt * Tuning.Player.invulnBlinkHz * Math.PI * 2
            visualAlpha = (0.45 + 0.55 * (0.5 + 0.5 * sin(blinkPhase))).toFloat()
            if (invulnRemaining <= 0) {
                isInvulnerable = false
                visualAlpha = 1f
            }
        }

        thrustIntensity = min(1f, sqrt(moveStick.x * moveStick.x + moveStick.y * moveStick.y))
    }

    fun upgradeSpread(): Boolean {
        if (spreadLevel >= Tuning.Pickup.maxSpreadLevel) return false
        spreadLevel++
        return true
    }

    fun restoreLife(): Boolean {
        if (lives >= Tuning.Player.maxLives) return false
        lives++
        onLivesChanged?.invoke(lives)
        return true
    }

    fun tryTakeHit(): Boolean {
        if (isInvulnerable || !isAlive) return false
        lives--
        spreadLevel = 0
        isInvulnerable = true
        invulnRemaining = Tuning.Player.invulnDuration
        blinkPhase = 0.0
        onLivesChanged?.invoke(lives)
        haptics.notification(NotificationType.WARNING)
        return true
    }

    fun reset() {
        lives = Tuning.Player.startingLives
        isInvulnerable = false
        invulnRemaining = 0f
        velocity.set(0f, 0f)
        aimAngle = 0f
        isFiring = false
        spreadLevel = 0
        visualAlpha = 1f
        bankRotation = 0f
        position.set(sceneCenterX + Tuning.World.orbitEarth, sceneCenterY)
        onLivesChanged?.invoke(lives)
    }
}
