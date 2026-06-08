package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

class PlayerTest {

    private lateinit var player: Player
    private var hapticsCount = 0
    private val livesLog = mutableListOf<Int>()

    private val zero = Vector2(0f, 0f)
    private val dt = 1f / 60f

    @BeforeEach
    fun setup() {
        hapticsCount = 0
        livesLog.clear()
        player = Player(400f, 187.5f, haptics = Haptics { hapticsCount++ })
        player.onLivesChanged = { livesLog += it }
    }

    @Test
    fun `accel toward target - never overshoots maxSpeed`() {
        val stick = Vector2(1f, 0f)
        repeat(300) { player.update(dt, stick, zero) }
        assertTrue(player.velocity.x <= Tuning.Player.maxSpeed + 0.01f,
            "velocity ${player.velocity.x} should not exceed maxSpeed ${Tuning.Player.maxSpeed}")
        assertTrue(player.velocity.x > Tuning.Player.maxSpeed * 0.95f,
            "velocity should converge near maxSpeed after 300 frames")
    }

    @Test
    fun `exponential drag`() {
        player.velocity.set(Tuning.Player.maxSpeed, 0f)
        val prevVx = player.velocity.x
        player.update(dt, zero, zero)
        val expected = prevVx * exp(-Tuning.Player.drag * dt).toFloat()
        assertEquals(expected, player.velocity.x, 1e-4f,
            "drag: expected v*exp(-drag*dt)=$expected actual=${player.velocity.x}")

        repeat(600) { player.update(dt, zero, zero) }
        assertTrue(abs(player.velocity.x) < 1f,
            "velocity should trend to ~0 after 10s of drag, got ${player.velocity.x}")
    }

    @Test
    fun `banking ease - rightward velocity tilts negative`() {
        val stick = Vector2(1f, 0f)
        repeat(120) { player.update(dt, stick, zero) }
        assertTrue(player.bankRotation < 0f,
            "bankRotation should be negative for +x velocity, got ${player.bankRotation}")
        val target = -(player.velocity.x / Tuning.Player.maxSpeed) * Tuning.Player.maxBankRadians
        assertTrue(abs(player.bankRotation - target) < 0.05f,
            "bankRotation should approach target=$target, got ${player.bankRotation}")
    }

    @Test
    fun `banking decays toward zero with no input`() {
        val stick = Vector2(1f, 0f)
        repeat(60) { player.update(dt, stick, zero) }
        assertTrue(player.bankRotation < -0.1f)

        repeat(600) { player.update(dt, zero, zero) }
        assertTrue(abs(player.bankRotation) < 0.01f,
            "bankRotation should decay to ~0, got ${player.bankRotation}")
    }

    @Test
    fun `aim fire gate - below threshold isFiring false`() {
        player.update(dt, zero, Vector2(0.0005f, 0f))
        assertFalse(player.isFiring, "aimStick mag below 0.001 should leave isFiring=false")
    }

    @Test
    fun `aim fire gate - right stick sets aimAngle`() {
        player.update(dt, zero, Vector2(1f, 0f))
        assertTrue(player.isFiring)
        assertEquals(0f, player.aimAngle, 0.01f, "aim right should be ~0 radians")

        player.update(dt, zero, Vector2(0f, 1f))
        assertEquals(PI.toFloat() / 2f, player.aimAngle, 0.01f, "aim up should be ~PI/2")
    }

    @Test
    fun `invuln blink and expiry`() {
        player.tryTakeHit()
        assertTrue(player.isInvulnerable)

        var minAlpha = 1f
        var maxAlpha = 0f
        val frames = (Tuning.Player.invulnDuration / dt).toInt()
        for (i in 0 until frames - 5) {
            player.update(dt, zero, zero)
            if (player.isInvulnerable) {
                minAlpha = minOf(minAlpha, player.visualAlpha)
                maxAlpha = maxOf(maxAlpha, player.visualAlpha)
            }
        }
        assertTrue(minAlpha >= 0.44f, "blink alpha should not drop below ~0.45, got $minAlpha")
        assertTrue(maxAlpha <= 1.01f, "blink alpha should not exceed 1.0, got $maxAlpha")

        repeat(10) { player.update(dt, zero, zero) }
        assertFalse(player.isInvulnerable, "invuln should expire after duration")
        assertEquals(1f, player.visualAlpha, 0.001f)
    }

    @Test
    fun `spread cap`() {
        repeat(Tuning.Pickup.maxSpreadLevel) { assertTrue(player.upgradeSpread()) }
        assertEquals(Tuning.Pickup.maxSpreadLevel, player.spreadLevel)
        assertFalse(player.upgradeSpread(), "should return false at max")
        assertEquals(Tuning.Pickup.maxSpreadLevel, player.spreadLevel, "should not exceed max")
    }

    @Test
    fun `tryTakeHit resets spread and fires callbacks`() {
        player.upgradeSpread()
        assertEquals(1, player.spreadLevel)

        hapticsCount = 0
        livesLog.clear()
        assertTrue(player.tryTakeHit())
        assertEquals(0, player.spreadLevel, "spread should reset to 0 on hit")
        assertEquals(1, hapticsCount, "haptics.warning called once")
        assertEquals(1, livesLog.size, "onLivesChanged fired once")
        assertEquals(Tuning.Player.startingLives - 1, livesLog.last())
    }

    @Test
    fun `tryTakeHit while invulnerable returns false`() {
        player.tryTakeHit()
        val livesBefore = player.lives
        hapticsCount = 0
        assertFalse(player.tryTakeHit(), "should not take damage while invulnerable")
        assertEquals(livesBefore, player.lives)
        assertEquals(0, hapticsCount)
    }

    @Test
    fun `restoreLife caps at maxLives`() {
        while (player.lives < Tuning.Player.maxLives) {
            assertTrue(player.restoreLife())
        }
        assertEquals(Tuning.Player.maxLives, player.lives)
        assertFalse(player.restoreLife(), "should return false at max")
    }

    @Test
    fun `reset restores initial state`() {
        player.tryTakeHit()
        player.upgradeSpread()
        player.velocity.set(100f, 50f)

        livesLog.clear()
        player.reset()
        assertEquals(Tuning.Player.startingLives, player.lives)
        assertEquals(0f, player.velocity.x, 0.001f)
        assertEquals(0f, player.velocity.y, 0.001f)
        assertEquals(0, player.spreadLevel)
        assertEquals(0f, player.aimAngle, 0.001f)
        assertEquals(1f, player.visualAlpha, 0.001f)
        assertEquals(0f, player.bankRotation, 0.001f)
        assertFalse(player.isInvulnerable)
        assertTrue(livesLog.contains(Tuning.Player.startingLives), "reset fires onLivesChanged")
    }
}
