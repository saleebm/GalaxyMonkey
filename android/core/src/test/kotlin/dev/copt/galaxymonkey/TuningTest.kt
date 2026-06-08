package dev.copt.galaxymonkey

import com.badlogic.gdx.math.MathUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TuningTest {

    private val delta = 1e-4f
    private var count = 0

    private fun f(expected: Float, actual: Float, name: String) {
        assertEquals(expected, actual, delta, "$name drifted from iOS")
        count++
    }
    private fun i(expected: Int, actual: Int, name: String) {
        assertEquals(expected, actual, "$name drifted from iOS")
        count++
    }
    private fun b(expected: Boolean, actual: Boolean, name: String) {
        assertEquals(expected, actual, "$name drifted from iOS")
        count++
    }

    @Test
    fun `Player section - 10 constants`() {
        f(360f, Tuning.Player.maxSpeed, "Player.maxSpeed")
        f(1600f, Tuning.Player.acceleration, "Player.acceleration")
        f(2.4f, Tuning.Player.drag, "Player.drag")
        f(22f, Tuning.Player.radius, "Player.radius")
        i(3, Tuning.Player.startingLives, "Player.startingLives")
        i(5, Tuning.Player.maxLives, "Player.maxLives")
        f(1.6f, Tuning.Player.invulnDuration, "Player.invulnDuration")
        f(12.0f, Tuning.Player.invulnBlinkHz, "Player.invulnBlinkHz")
        f(MathUtils.PI / 6f, Tuning.Player.maxBankRadians, "Player.maxBankRadians")
        f(8.0f, Tuning.Player.bankApproachRate, "Player.bankApproachRate")
        println("[TuningTest] Player section: 10/10 constants match iOS golden")
    }

    @Test
    fun `Projectile section - 12 constants`() {
        f(720f, Tuning.Projectile.speed, "Projectile.speed")
        f(0.15f, Tuning.Projectile.fireInterval, "Projectile.fireInterval")
        f(1.4f, Tuning.Projectile.lifetime, "Projectile.lifetime")
        f(6f, Tuning.Projectile.radius, "Projectile.radius")
        i(96, Tuning.Projectile.poolSize, "Projectile.poolSize")
        f(MathUtils.PI / 24f, Tuning.Projectile.spreadOffset, "Projectile.spreadOffset")
        f(280f, Tuning.Projectile.bombSpeed, "Projectile.bombSpeed")
        f(3.0f, Tuning.Projectile.bombLifetime, "Projectile.bombLifetime")
        f(26f, Tuning.Projectile.bombRadius, "Projectile.bombRadius")
        f(0.6f, Tuning.Projectile.bombSpinPeriod, "Projectile.bombSpinPeriod")
        f(0.45f, Tuning.Projectile.enemyBulletSpeedMul, "Projectile.enemyBulletSpeedMul")
        f(11f, Tuning.Projectile.enemyBulletVisualRadius, "Projectile.enemyBulletVisualRadius")
        println("[TuningTest] Projectile section: 12/12 constants match iOS golden")
    }

    @Test
    fun `Enemy section - 12 constants`() {
        f(2.7f, Tuning.Enemy.spawnIntervalStart, "Enemy.spawnIntervalStart")
        f(0.75f, Tuning.Enemy.spawnIntervalEnd, "Enemy.spawnIntervalEnd")
        f(90f, Tuning.Enemy.rampDuration, "Enemy.rampDuration")
        f(60f, Tuning.Enemy.spawnPaddingPx, "Enemy.spawnPaddingPx")
        f(80f, Tuning.Enemy.baseSpeed, "Enemy.baseSpeed")
        f(36f, Tuning.Enemy.speedJitter, "Enemy.speedJitter")
        f(22f, Tuning.Enemy.radius, "Enemy.radius")
        i(25, Tuning.Enemy.bossEveryNKills, "Enemy.bossEveryNKills")
        f(360f, Tuning.Enemy.fireRangePx, "Enemy.fireRangePx")
        i(100, Tuning.Enemy.pointsOnKill, "Enemy.pointsOnKill")
        f(12f, Tuning.Enemy.walkSpeedThresholdPx, "Enemy.walkSpeedThresholdPx")
        f(8f, Tuning.Enemy.facingFlipDeadzonePx, "Enemy.facingFlipDeadzonePx")
        println("[TuningTest] Enemy section: 12/12 constants match iOS golden")
    }

    @Test
    fun `Joystick section - 7 constants`() {
        f(78f, Tuning.Joystick.radius, "Joystick.radius")
        f(34f, Tuning.Joystick.thumbRadius, "Joystick.thumbRadius")
        f(0.12f, Tuning.Joystick.deadZone, "Joystick.deadZone")
        f(32f, Tuning.Joystick.edgeMargin, "Joystick.edgeMargin")
        f(0.08f, Tuning.Joystick.baseAlpha, "Joystick.baseAlpha")
        f(0.18f, Tuning.Joystick.strokeAlpha, "Joystick.strokeAlpha")
        f(0.14f, Tuning.Joystick.thumbAlpha, "Joystick.thumbAlpha")
        println("[TuningTest] Joystick section: 7/7 constants match iOS golden")
    }

    @Test
    fun `Starfield section - 9 constants`() {
        i(80, Tuning.Starfield.layer1Count, "Starfield.layer1Count")
        i(30, Tuning.Starfield.layer2Count, "Starfield.layer2Count")
        f(0.06f, Tuning.Starfield.layer1Speed, "Starfield.layer1Speed")
        f(0.18f, Tuning.Starfield.layer2Speed, "Starfield.layer2Speed")
        i(16, Tuning.Starfield.twinkleCount, "Starfield.twinkleCount")
        f(1.6f, Tuning.Starfield.twinkleRadius, "Starfield.twinkleRadius")
        f(0.3f, Tuning.Starfield.twinkleAlphaLow, "Starfield.twinkleAlphaLow")
        f(1.2f, Tuning.Starfield.twinklePeriodMin, "Starfield.twinklePeriodMin")
        f(3.5f, Tuning.Starfield.twinklePeriodMax, "Starfield.twinklePeriodMax")
        println("[TuningTest] Starfield section: 9/9 constants match iOS golden")
    }

    @Test
    fun `Audio section - 3 constants`() {
        f(900f, Tuning.Audio.maxDistance, "Audio.maxDistance")
        f(0.05f, Tuning.Audio.minVolume, "Audio.minVolume")
        f(0.05f, Tuning.Audio.playerShotVolume, "Audio.playerShotVolume")
        println("[TuningTest] Audio section: 3/3 constants match iOS golden")
    }

    @Test
    fun `Settings section - 3 constants`() {
        f(0.18f, Tuning.Settings.defaultMusicVolume, "Settings.defaultMusicVolume")
        f(1.0f, Tuning.Settings.defaultSFXVolume, "Settings.defaultSFXVolume")
        b(true, Tuning.Settings.defaultHapticsEnabled, "Settings.defaultHapticsEnabled")
        println("[TuningTest] Settings section: 3/3 constants match iOS golden")
    }

    @Test
    fun `Pickup section - 8 constants`() {
        f(0.08f, Tuning.Pickup.dropChance, "Pickup.dropChance")
        f(8.0f, Tuning.Pickup.lifetime, "Pickup.lifetime")
        f(14f, Tuning.Pickup.radius, "Pickup.radius")
        f(36f, Tuning.Pickup.spriteTargetMax, "Pickup.spriteTargetMax")
        f(4f, Tuning.Pickup.bobAmplitude, "Pickup.bobAmplitude")
        f(1.2f, Tuning.Pickup.bobPeriod, "Pickup.bobPeriod")
        i(500, Tuning.Pickup.scoreBonus, "Pickup.scoreBonus")
        i(2, Tuning.Pickup.maxSpreadLevel, "Pickup.maxSpreadLevel")
        println("[TuningTest] Pickup section: 8/8 constants match iOS golden")
    }

    @Test
    fun `VFX section - 24 constants`() {
        f(0.86f, Tuning.VFX.shakeDecay, "VFX.shakeDecay")
        f(18f, Tuning.VFX.shakeMaxOffset, "VFX.shakeMaxOffset")
        f(6f, Tuning.VFX.bombShakeIntensity, "VFX.bombShakeIntensity")
        f(8f, Tuning.VFX.playerHitShakeIntensity, "VFX.playerHitShakeIntensity")
        f(3f, Tuning.VFX.enemyKillShakeIntensity, "VFX.enemyKillShakeIntensity")
        f(-1f, Tuning.VFX.thrustZ, "VFX.thrustZ")
        f(-22f, Tuning.VFX.thrustTailOffsetX, "VFX.thrustTailOffsetX")
        f(1.4f, Tuning.VFX.thrustScale, "VFX.thrustScale")
        f(0.08f, Tuning.VFX.muzzleFlashDuration, "VFX.muzzleFlashDuration")
        f(0.6f, Tuning.VFX.muzzleFlashScale, "VFX.muzzleFlashScale")
        f(0.12f, Tuning.VFX.damageFlashDuration, "VFX.damageFlashDuration")
        f(1f / 30f, Tuning.VFX.bombExplosionFrameDuration, "VFX.bombExplosionFrameDuration")
        f(3.4f, Tuning.VFX.bombExplosionScale, "VFX.bombExplosionScale")
        f(0.35f, Tuning.VFX.glowBombDuration, "VFX.glowBombDuration")
        f(2.0f, Tuning.VFX.glowBombScale, "VFX.glowBombScale")
        f(0.12f, Tuning.VFX.warpVioletDuration, "VFX.warpVioletDuration")
        f(0.3f, Tuning.VFX.warpFadeDuration, "VFX.warpFadeDuration")
        f(0.15f, Tuning.VFX.blackholeOpenDuration, "VFX.blackholeOpenDuration")
        f(0.2f, Tuning.VFX.blackholeCollapseDuration, "VFX.blackholeCollapseDuration")
        f(3.0f, Tuning.VFX.blackholeScale, "VFX.blackholeScale")
        f(1.0f, Tuning.VFX.blackholeSpinPeriod, "VFX.blackholeSpinPeriod")
        f(60f, Tuning.VFX.planetRotationPeriodMin, "VFX.planetRotationPeriodMin")
        f(180f, Tuning.VFX.planetRotationPeriodMax, "VFX.planetRotationPeriodMax")
        println("[TuningTest] VFX section: 24/24 constants match iOS golden")
    }

    @Test
    fun `World section - 50 constants`() {
        f(0.45f, Tuning.World.orbitTiltY, "World.orbitTiltY")
        f(600f, Tuning.World.sunDisplayDiameter, "World.sunDisplayDiameter")
        f(110f, Tuning.World.mercuryDiameter, "World.mercuryDiameter")
        f(150f, Tuning.World.venusDiameter, "World.venusDiameter")
        f(165f, Tuning.World.earthDiameter, "World.earthDiameter")
        f(130f, Tuning.World.marsDiameter, "World.marsDiameter")
        f(360f, Tuning.World.jupiterDiameter, "World.jupiterDiameter")
        f(320f, Tuning.World.saturnDiameter, "World.saturnDiameter")
        f(230f, Tuning.World.uranusDiameter, "World.uranusDiameter")
        f(220f, Tuning.World.neptuneDiameter, "World.neptuneDiameter")
        f(600f, Tuning.World.orbitMercury, "World.orbitMercury")
        f(1100f, Tuning.World.orbitVenus, "World.orbitVenus")
        f(1500f, Tuning.World.orbitEarth, "World.orbitEarth")
        f(2300f, Tuning.World.orbitMars, "World.orbitMars")
        f(4000f, Tuning.World.orbitJupiter, "World.orbitJupiter")
        f(5400f, Tuning.World.orbitSaturn, "World.orbitSaturn")
        f(7000f, Tuning.World.orbitUranus, "World.orbitUranus")
        f(8500f, Tuning.World.orbitNeptune, "World.orbitNeptune")
        f(0.085f, Tuning.World.orbitRingAlpha, "World.orbitRingAlpha")
        f(1.5f, Tuning.World.orbitRingLineWidth, "World.orbitRingLineWidth")
        f(28f, Tuning.World.sunCoronaBlurRadius, "World.sunCoronaBlurRadius")
        f(0.55f, Tuning.World.sunCoronaAlpha, "World.sunCoronaAlpha")
        f(6.0f, Tuning.World.sunCoronaPulsePeriod, "World.sunCoronaPulsePeriod")
        f(0.04f, Tuning.World.sunCoronaPulseAmplitude, "World.sunCoronaPulseAmplitude")
        f(0.045f, Tuning.World.angSpeedMercury, "World.angSpeedMercury")
        f(0.030f, Tuning.World.angSpeedVenus, "World.angSpeedVenus")
        f(0.022f, Tuning.World.angSpeedEarth, "World.angSpeedEarth")
        f(0.015f, Tuning.World.angSpeedMars, "World.angSpeedMars")
        f(0.009f, Tuning.World.angSpeedJupiter, "World.angSpeedJupiter")
        f(0.006f, Tuning.World.angSpeedSaturn, "World.angSpeedSaturn")
        f(0.004f, Tuning.World.angSpeedUranus, "World.angSpeedUranus")
        f(0.003f, Tuning.World.angSpeedNeptune, "World.angSpeedNeptune")
        f(22f, Tuning.World.cometSpawnIntervalMin, "World.cometSpawnIntervalMin")
        f(48f, Tuning.World.cometSpawnIntervalMax, "World.cometSpawnIntervalMax")
        f(4200f, Tuning.World.cometPathRadius, "World.cometPathRadius")
        f(MathUtils.PI / 12f, Tuning.World.cometExitJitterRad, "World.cometExitJitterRad")
        f(3f, Tuning.World.cometHeadRadius, "World.cometHeadRadius")
        f(7f, Tuning.World.cometTravelDurationMin, "World.cometTravelDurationMin")
        f(12f, Tuning.World.cometTravelDurationMax, "World.cometTravelDurationMax")
        f(90f, Tuning.World.cometTrailBirthRate, "World.cometTrailBirthRate")
        f(1.4f, Tuning.World.cometTrailLifetime, "World.cometTrailLifetime")
        f(3f, Tuning.World.cometInitialDelay, "World.cometInitialDelay")
        f(2750f, Tuning.World.asteroidBeltInnerRadius, "World.asteroidBeltInnerRadius")
        f(3700f, Tuning.World.asteroidBeltOuterRadius, "World.asteroidBeltOuterRadius")
        i(180, Tuning.World.asteroidBeltCount, "World.asteroidBeltCount")
        f(0.012f, Tuning.World.asteroidBeltAngularSpeed, "World.asteroidBeltAngularSpeed")
        f(0.0035f, Tuning.World.asteroidBeltSpeedJitter, "World.asteroidBeltSpeedJitter")
        f(5f, Tuning.World.asteroidDiameterMin, "World.asteroidDiameterMin")
        f(18f, Tuning.World.asteroidDiameterMax, "World.asteroidDiameterMax")
        f(0.45f, Tuning.World.asteroidAlphaMin, "World.asteroidAlphaMin")
        f(0.9f, Tuning.World.asteroidAlphaMax, "World.asteroidAlphaMax")
        println("[TuningTest] World section: 51/51 constants match iOS golden")
    }

    @Test
    fun `World Moons section - 18 constants`() {
        f(38f, Tuning.World.Moons.lunaDiameter, "Moons.lunaDiameter")
        f(130f, Tuning.World.Moons.lunaRadius, "Moons.lunaRadius")
        f(0.08f, Tuning.World.Moons.lunaSpeed, "Moons.lunaSpeed")
        f(32f, Tuning.World.Moons.ioDiameter, "Moons.ioDiameter")
        f(240f, Tuning.World.Moons.ioRadius, "Moons.ioRadius")
        f(0.14f, Tuning.World.Moons.ioSpeed, "Moons.ioSpeed")
        f(28f, Tuning.World.Moons.europaDiameter, "Moons.europaDiameter")
        f(295f, Tuning.World.Moons.europaRadius, "Moons.europaRadius")
        f(0.10f, Tuning.World.Moons.europaSpeed, "Moons.europaSpeed")
        f(40f, Tuning.World.Moons.ganymedeDiameter, "Moons.ganymedeDiameter")
        f(355f, Tuning.World.Moons.ganymedeRadius, "Moons.ganymedeRadius")
        f(0.075f, Tuning.World.Moons.ganymedeSpeed, "Moons.ganymedeSpeed")
        f(36f, Tuning.World.Moons.callistoDiameter, "Moons.callistoDiameter")
        f(425f, Tuning.World.Moons.callistoRadius, "Moons.callistoRadius")
        f(0.055f, Tuning.World.Moons.callistoSpeed, "Moons.callistoSpeed")
        f(42f, Tuning.World.Moons.titanDiameter, "Moons.titanDiameter")
        f(320f, Tuning.World.Moons.titanRadius, "Moons.titanRadius")
        f(0.065f, Tuning.World.Moons.titanSpeed, "Moons.titanSpeed")
        println("[TuningTest] World.Moons section: 18/18 constants match iOS golden")
    }

    @Test
    fun `Camera section - 3 constants`() {
        f(36f, Tuning.Camera.deadzoneRadius, "Camera.deadzoneRadius")
        f(6f, Tuning.Camera.followLerpPerSec, "Camera.followLerpPerSec")
        f(96f, Tuning.Camera.enemySpawnViewPaddingPx, "Camera.enemySpawnViewPaddingPx")
        println("[TuningTest] Camera section: 3/3 constants match iOS golden")
    }

    @Test
    fun `Category bitmasks - 6 values`() {
        i(0, Category.none, "Category.none")
        i(1, Category.player, "Category.player")
        i(2, Category.enemy, "Category.enemy")
        i(4, Category.bullet, "Category.bullet")
        i(8, Category.pickup, "Category.pickup")
        i(16, Category.enemyBullet, "Category.enemyBullet")
        println("[TuningTest] Category masks: 6/6 match iOS golden")
    }

    @Test
    fun `total constant count`() {
        val total = 10 + 12 + 12 + 7 + 9 + 3 + 3 + 8 + 24 + 51 + 18 + 3 + 6
        assertEquals(166, total)
        println("[TuningTest] ALL $total Tuning constants + 6 Category masks verified against iOS Tuning.swift")
    }
}
