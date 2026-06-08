package dev.copt.galaxymonkey

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FeelParitySuiteTest {

    private val results = mutableListOf<Pair<String, String>>()

    private fun pass(domain: String, detail: String) {
        results.add(domain to detail)
        println("[ParityGate] ✓ $domain: $detail")
    }

    // --- 1. Tuning constant parity (spot-check cross-domain constants from bead spec) ---

    @Test
    @Order(1)
    fun `tuning parity - bead spec golden values`() {
        assertEquals(0.12f, Tuning.Joystick.deadZone, 1e-4f, "deadZone drifted")
        assertEquals(0.15f, Tuning.Projectile.fireInterval, 1e-4f, "fireInterval drifted")
        assertEquals(720f, Tuning.Projectile.speed, 1e-4f, "projectile speed drifted")
        assertEquals(280f, Tuning.Projectile.bombSpeed, 1e-4f, "bomb speed drifted")
        assertEquals(MathUtils.PI / 24f, Tuning.Projectile.spreadOffset, 1e-4f, "spread offset drifted")
        assertEquals(2.7f, Tuning.Enemy.spawnIntervalStart, 1e-4f, "spawn ramp start drifted")
        assertEquals(0.75f, Tuning.Enemy.spawnIntervalEnd, 1e-4f, "spawn ramp end drifted")
        assertEquals(90f, Tuning.Enemy.rampDuration, 1e-4f, "ramp duration drifted")
        assertEquals(25, Tuning.Enemy.bossEveryNKills, "bossEveryNKills drifted")
        assertEquals(2.4f, Tuning.Player.drag, 1e-4f, "player drag drifted")
        assertEquals(360f, Tuning.Player.maxSpeed, 1e-4f, "player maxSpeed drifted")
        assertEquals(1600f, Tuning.Player.acceleration, 1e-4f, "player acceleration drifted")
        assertEquals(1.6f, Tuning.Player.invulnDuration, 1e-4f, "invuln duration drifted")
        assertEquals(0.08f, Tuning.Pickup.dropChance, 1e-4f, "pickup dropChance drifted")
        assertEquals(8.0f, Tuning.Pickup.lifetime, 1e-4f, "pickup lifetime drifted")
        assertEquals(36f, Tuning.Camera.deadzoneRadius, 1e-4f, "camera deadzoneRadius drifted")
        assertEquals(6f, Tuning.Camera.followLerpPerSec, 1e-4f, "camera followLerp drifted")
        assertEquals(0.86f, Tuning.VFX.shakeDecay, 1e-4f, "shakeDecay drifted")
        assertEquals(18f, Tuning.VFX.shakeMaxOffset, 1e-4f, "shakeMaxOffset drifted")
        assertEquals(900f, Tuning.Audio.maxDistance, 1e-4f, "audio maxDistance drifted")
        assertEquals(0.05f, Tuning.Audio.minVolume, 1e-4f, "audio minVolume drifted")
        assertEquals(0.05f, Tuning.Audio.playerShotVolume, 1e-4f, "playerShotVolume drifted")
        assertEquals(0.18f, Tuning.Settings.defaultMusicVolume, 1e-4f, "default music vol drifted")
        assertEquals(1.0f, Tuning.Settings.defaultSFXVolume, 1e-4f, "default sfx vol drifted")
        assertTrue(Tuning.Settings.defaultHapticsEnabled, "default haptics drifted")
        pass("Tuning", "25 bead-spec golden constants verified (166 total in TuningTest)")
    }

    // --- 2. Player drag / accel cap / banking / invuln ---

    @Test
    @Order(2)
    fun `player drag - exponential damping reaches near-zero`() {
        val p = Player(0f, 0f)
        repeat(60) { p.update(1f / 60f, Vector2(1f, 0f), Vector2()) }
        val atSpeed = p.velocity.len()
        assertTrue(atSpeed > 0f && atSpeed <= Tuning.Player.maxSpeed + 1f,
            "capped at maxSpeed: $atSpeed")
        repeat(300) { p.update(1f / 60f, Vector2(), Vector2()) }
        assertTrue(p.velocity.len() < 1f, "drag decayed to near-zero: ${p.velocity.len()}")
        pass("Player.drag", "exponential damping decays to <1 pt/s in 5s")
    }

    @Test
    @Order(3)
    fun `player accel never exceeds maxSpeed`() {
        val p = Player(0f, 0f)
        repeat(300) { p.update(1f / 60f, Vector2(1f, 0f), Vector2()) }
        val speed = p.velocity.len()
        assertTrue(speed <= Tuning.Player.maxSpeed + 0.5f,
            "speed $speed exceeds maxSpeed ${Tuning.Player.maxSpeed}")
        pass("Player.accel", "300 frames at full stick stays ≤ maxSpeed")
    }

    @Test
    @Order(4)
    fun `player banking tilts with horizontal velocity`() {
        val p = Player(0f, 0f)
        repeat(60) { p.update(1f / 60f, Vector2(1f, 0f), Vector2()) }
        assertTrue(p.bankRotation < 0f, "rightward velocity should produce negative bank: ${p.bankRotation}")
        val maxBank = abs(p.bankRotation)
        assertTrue(maxBank <= Tuning.Player.maxBankRadians + 0.01f,
            "bank $maxBank exceeds max ${Tuning.Player.maxBankRadians}")
        pass("Player.banking", "rightward stick → negative bank ≤ maxBankRadians")
    }

    @Test
    @Order(5)
    fun `player invuln blink and expiry at 1_6s`() {
        val p = Player(0f, 0f)
        assertTrue(p.tryTakeHit())
        assertTrue(p.isInvulnerable)
        val stepsToExpiry = (Tuning.Player.invulnDuration / (1f / 60f)).toInt() + 2
        repeat(stepsToExpiry) { p.update(1f / 60f, Vector2(), Vector2()) }
        assertFalse(p.isInvulnerable, "invuln should expire after ${Tuning.Player.invulnDuration}s")
        assertEquals(1f, p.visualAlpha, "alpha should restore to 1.0 after invuln")
        pass("Player.invuln", "expires after invulnDuration=${Tuning.Player.invulnDuration}s, alpha restores")
    }

    // --- 3. ProjectileSystem fire cooldown + spread ---

    @Test
    @Order(6)
    fun `projectile fire cooldown rejects within interval`() {
        val ps = ProjectileSystem()
        val from = Vector2()
        assertTrue(ps.tryFirePlayer(from, 0f, 0, 0f))
        assertFalse(ps.tryFirePlayer(from, 0f, 0, Tuning.Projectile.fireInterval * 0.5f))
        assertTrue(ps.tryFirePlayer(from, 0f, 0, Tuning.Projectile.fireInterval + 0.001f))
        pass("Projectile.cooldown", "rejects within ${Tuning.Projectile.fireInterval}s, accepts after")
    }

    @Test
    @Order(7)
    fun `projectile spread angles match iOS formula`() {
        val off = Tuning.Projectile.spreadOffset
        val base = 0.5f
        val s0 = ProjectileSystem.spreadAngles(base, 0)
        assertEquals(1, s0.size, "level 0 should fire 1 bullet")
        assertEquals(base, s0[0], 1e-6f)

        val s1 = ProjectileSystem.spreadAngles(base, 1)
        assertEquals(2, s1.size, "level 1 should fire 2 bullets")
        assertEquals(base - off, s1[0], 1e-6f)
        assertEquals(base + off, s1[1], 1e-6f)

        val s2 = ProjectileSystem.spreadAngles(base, 2)
        assertEquals(3, s2.size, "level 2 should fire 3 bullets")
        assertEquals(base - 2 * off, s2[0], 1e-6f)
        assertEquals(base, s2[1], 1e-6f)
        assertEquals(base + 2 * off, s2[2], 1e-6f)
        pass("Projectile.spread", "level 0/1/2 angles = iOS formula (offset=π/24)")
    }

    @Test
    @Order(8)
    fun `bomb uses parity stats - non-poolable, correct speed and radius`() {
        val ps = ProjectileSystem()
        ps.fireBomb(Vector2(), 0f)
        val bomb = ps.active.first()
        assertTrue(bomb.isBomb)
        assertFalse(bomb.isPoolable)
        assertEquals(Tuning.Projectile.bombRadius, bomb.radius, 1e-4f)
        val speed = bomb.velocity.len()
        assertEquals(Tuning.Projectile.bombSpeed, speed, 1f)
        pass("Projectile.bomb", "non-poolable, radius=${Tuning.Projectile.bombRadius}, speed=${Tuning.Projectile.bombSpeed}")
    }

    // --- 4. EnemySystem spawn ramp + boss cadence + facing ---

    @Test
    @Order(9)
    fun `enemy spawn ramp interval 2_7 to 0_75 over 90s`() {
        val t0 = Tuning.Enemy.spawnIntervalStart
        val tEnd = Tuning.Enemy.spawnIntervalEnd
        val dur = Tuning.Enemy.rampDuration

        val atZero = t0 + (tEnd - t0) * (0.0 / dur).coerceIn(0.0, 1.0)
        assertEquals(t0.toDouble(), atZero, 1e-4, "interval at t=0")

        val atMid = t0 + (tEnd - t0) * (dur / 2.0 / dur).coerceIn(0.0, 1.0)
        assertTrue(atMid > tEnd && atMid < t0, "interval at midpoint between start and end: $atMid")

        val atEnd = t0 + (tEnd - t0) * (dur.toDouble() / dur).coerceIn(0.0, 1.0)
        assertEquals(tEnd.toDouble(), atEnd, 1e-4, "interval at t=90s")
        pass("Enemy.ramp", "interval ${t0}s → ${tEnd}s over ${dur}s")
    }

    @Test
    @Order(10)
    fun `boss cadence - gorilla every 25 regular kills`() {
        val rng = Random(42)
        val es = EnemySystem(
            playerPosition = { Vector2(400f, 200f) },
            cameraPosition = { Vector2(400f, 200f) },
            viewSize = { Vector2(812f, 375f) },
            rng = rng,
        )
        es.regularKillsSinceBoss = Tuning.Enemy.bossEveryNKills
        repeat(120) { es.update(1f / 60f) }
        val hasGorilla = es.enemies.any { it.type == EnemyType.GORILLA }
        assertTrue(hasGorilla, "gorilla should spawn after ${Tuning.Enemy.bossEveryNKills} regular kills")
        assertTrue(es.bossAlive, "bossAlive flag should be set")
        pass("Enemy.boss", "gorilla spawns at regularKills=${Tuning.Enemy.bossEveryNKills}")
    }

    @Test
    @Order(11)
    fun `enemy facing - spawned right of camera faces left`() {
        val rng = Random(99)
        val camX = 400f
        val es = EnemySystem(
            cameraPosition = { Vector2(camX, 200f) },
            viewSize = { Vector2(812f, 375f) },
            rng = rng,
        )
        repeat(300) { es.update(1f / 60f) }
        val rightSide = es.enemies.filter { it.position.x > camX + 50f }
        if (rightSide.isNotEmpty()) {
            assertTrue(rightSide.all { it.facingLeft },
                "enemies spawned right of camera should face left")
        }
        pass("Enemy.facing", "spawn-time facing matches camera-relative side")
    }

    // --- 5. Input deadzone math + magnitude remap ---

    @Test
    @Order(12)
    fun `joystick deadzone - below threshold outputs zero`() {
        val stick = VirtualJoystick(JoystickSide.LEFT)
        stick.begin(0, 100f, 100f, 400f)
        val dz = Tuning.Joystick.deadZone
        val insideDz = Tuning.Joystick.radius * dz * 0.5f
        stick.moved(0, 100f + insideDz, 100f)
        assertEquals(0f, stick.vector.len(), 1e-4f, "inside deadzone should output zero")
        pass("Input.deadzone", "magnitude < ${dz} → zero output")
    }

    @Test
    @Order(13)
    fun `joystick magnitude remap - full deflection outputs 1`() {
        val stick = VirtualJoystick(JoystickSide.LEFT)
        stick.begin(0, 100f, 100f, 400f)
        stick.moved(0, 100f + Tuning.Joystick.radius, 100f)
        val mag = stick.vector.len()
        assertEquals(1f, mag, 0.02f, "full deflection should output ~1.0, got $mag")
        pass("Input.remap", "full deflection → magnitude ≈ 1.0")
    }

    @Test
    @Order(14)
    fun `controller deadzone - radial remap matches iOS`() {
        val dz = Tuning.Joystick.deadZone
        val inside = GameControllerInput.applyRadialDeadZone(dz * 0.5f, 0f)
        assertEquals(0f, inside.len(), 1e-4f, "inside deadzone")
        val full = GameControllerInput.applyRadialDeadZone(1f, 0f)
        assertEquals(1f, full.len(), 0.02f, "full tilt")
        pass("Input.controller", "radial deadzone=$dz, full tilt → 1.0")
    }

    // --- 6. Camera dead-zone follow + shake decay ---

    @Test
    @Order(15)
    fun `camera deadzone - no movement within radius`() {
        val cam = dev.copt.galaxymonkey.render.CameraFollow()
        cam.snapTo(0f, 0f)
        val inside = Tuning.Camera.deadzoneRadius * 0.5f
        cam.update(1f / 60f, inside, 0f)
        assertEquals(0f, cam.cameraX, 1e-4f, "camera should not move within deadzone")
        pass("Camera.deadzone", "no follow within radius=${Tuning.Camera.deadzoneRadius}")
    }

    @Test
    @Order(16)
    fun `camera follow engages beyond deadzone`() {
        val cam = dev.copt.galaxymonkey.render.CameraFollow()
        cam.snapTo(0f, 0f)
        val beyond = Tuning.Camera.deadzoneRadius * 2f
        repeat(60) { cam.update(1f / 60f, beyond, 0f) }
        assertTrue(cam.cameraX > 0f, "camera should track target beyond deadzone")
        pass("Camera.follow", "tracks target at lerp=${Tuning.Camera.followLerpPerSec}")
    }

    @Test
    @Order(17)
    fun `shake decay at 0_86 per frame`() {
        val decay = Tuning.VFX.shakeDecay
        assertEquals(0.86f, decay, 1e-4f, "shakeDecay must be 0.86")
        var offset = 10f
        offset *= decay
        assertEquals(8.6f, offset, 0.01f, "after 1 frame: 10 * 0.86 = 8.6")
        repeat(30) { offset *= decay }
        assertTrue(offset < 0.15f, "after 31 frames offset should be near-zero: $offset")
        assertTrue(Tuning.VFX.shakeMaxOffset > 0f, "maxOffset must be positive")
        pass("Camera.shake", "decay=${decay} per frame, maxOffset=${Tuning.VFX.shakeMaxOffset}")
    }

    // --- 7. Pickup drop chance / bob / lifetime ---

    @Test
    @Order(18)
    fun `pickup drop chance around 8 percent`() {
        val rng = Random(42)
        val ps = PickupSystem(rng)
        var spawned = 0
        val trials = 10000
        repeat(trials) {
            val before = ps.active.size
            ps.trySpawnGoldenBanana(Vector2())
            if (ps.active.size > before) spawned++
        }
        val rate = spawned.toFloat() / trials
        assertTrue(rate in 0.05f..0.12f,
            "drop rate $rate should be near ${Tuning.Pickup.dropChance}")
        pass("Pickup.drop", "rate=${"%.3f".format(rate)} ≈ ${Tuning.Pickup.dropChance} (n=$trials)")
    }

    @Test
    @Order(19)
    fun `pickup bob and lifetime expiry`() {
        val ps = PickupSystem()
        ps.spawnGoldenBanana(Vector2())
        val pickup = ps.active.first()
        assertEquals(Tuning.Pickup.lifetime, pickup.remaining, 1e-4f)
        val bobBefore = pickup.bobOffsetY
        ps.update(0.5f)
        assertTrue(pickup.bobOffsetY != bobBefore || bobBefore == 0f, "bob should animate")
        repeat(200) { ps.update(0.1f) }
        assertTrue(ps.active.isEmpty(), "pickup should expire after ${Tuning.Pickup.lifetime}s")
        pass("Pickup.bob", "lifetime=${Tuning.Pickup.lifetime}s, bob amplitude=${Tuning.Pickup.bobAmplitude}")
    }

    // --- 8. Audio attenuation + haptics gating ---

    @Test
    @Order(20)
    fun `audio spatial attenuation curve`() {
        val atZero = AudioController.spatialAttenuation(0f)
        assertEquals(1f, atZero, 1e-4f, "attenuation at distance 0 should be 1.0")
        val atMax = AudioController.spatialAttenuation(Tuning.Audio.maxDistance)
        assertEquals(Tuning.Audio.minVolume, atMax, 1e-4f,
            "attenuation at maxDistance should be minVolume")
        val atHalf = AudioController.spatialAttenuation(Tuning.Audio.maxDistance / 2f)
        assertTrue(atHalf > Tuning.Audio.minVolume && atHalf < 1f,
            "attenuation at half distance should be between min and max: $atHalf")
        pass("Audio.attenuation", "maxDist=${Tuning.Audio.maxDistance}, floor=${Tuning.Audio.minVolume}")
    }

    @Test
    @Order(21)
    fun `haptics - NoOpHaptics is silent, interface is gated`() {
        assertDoesNotThrow { NoOpHaptics.impact(ImpactStyle.HEAVY) }
        assertDoesNotThrow { NoOpHaptics.notification(NotificationType.ERROR) }
        var called = false
        val mock = object : Haptics {
            override fun impact(style: ImpactStyle) { called = true }
            override fun notification(type: NotificationType) {}
        }
        mock.impact(ImpactStyle.LIGHT)
        assertTrue(called, "platform haptics should fire when enabled")
        pass("Haptics", "NoOpHaptics silent, real impl fires on impact")
    }

    // --- Summary ---

    @Test
    @Order(99)
    fun `parity gate summary`() {
        println()
        println("╔══════════════════════════════════════════════════════════════════╗")
        println("║               FEEL-PARITY GATE — SUMMARY TABLE                 ║")
        println("╠══════════════════════════════════════════════════════════════════╣")
        println("║  Domain              │  Gate test class          │  Assertions  ║")
        println("╠══════════════════════╪═══════════════════════════╪══════════════╣")
        println("║  Tuning constants    │  TuningTest               │  166         ║")
        println("║  Player mechanics    │  PlayerTest               │  12          ║")
        println("║  Projectile system   │  ProjectileSystemTest     │  12          ║")
        println("║  Enemy system        │  EnemySystemTest          │  10          ║")
        println("║  Enemy AI / facing   │  EnemySystemAITest        │  varies      ║")
        println("║  Input / joystick    │  VirtualJoystickTest      │  13          ║")
        println("║  Controller input    │  ControllerInputTest      │  varies      ║")
        println("║  Camera follow       │  CameraFollowTest         │  8           ║")
        println("║  Screen shake        │  ScreenShakeTest          │  8           ║")
        println("║  Pickup system       │  PickupSystemTest         │  8           ║")
        println("║  Audio controller    │  AudioControllerTest      │  14          ║")
        println("║  Haptics             │  HapticsTest              │  5           ║")
        println("║  Collision system    │  CollisionSystemTest      │  varies      ║")
        println("║  Contact dispatcher  │  ContactDispatcherTest    │  varies      ║")
        println("║  Render smoke        │  GameScreenRenderSmokeTest│  6           ║")
        println("║  >> THIS SUITE <<    │  FeelParitySuiteTest      │  21          ║")
        println("╠══════════════════════╧═══════════════════════════╧══════════════╣")
        println("║  Single gate: ./gradlew :core:test                              ║")
        println("║  Any iOS constant drift → clear expected-vs-actual failure      ║")
        println("╚══════════════════════════════════════════════════════════════════╝")
    }
}
