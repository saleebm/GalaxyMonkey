# Feel-Parity Checklist: Android (Tuning.kt) vs iOS (Tuning.swift)

Bead: **penguinslide-f5a.122**
Date: 2026-06-08
Verified by: GreenCat (automated side-by-side diff)

Sources:
- iOS: `ios/GalaxyMonkey/Tuning.swift`
- Android: `android/core/src/main/kotlin/dev/copt/galaxymonkey/Tuning.kt`

---

## Player

| Constant | iOS | Android | Match |
|---|---|---|---|
| maxSpeed | 360 | 360f | [x] |
| acceleration | 1600 | 1600f | [x] |
| drag | 2.4 | 2.4f | [x] |
| radius | 22 | 22f | [x] |
| startingLives | 3 | 3 | [x] |
| maxLives | 5 | 5 | [x] |
| invulnDuration | 1.6 | 1.6f | [x] |
| invulnBlinkHz | 12 | 12.0f | [x] |
| maxBankRadians | .pi / 6 | MathUtils.PI / 6f | [x] |
| bankApproachRate | 8.0 | 8.0f | [x] |

## Projectile

| Constant | iOS | Android | Match |
|---|---|---|---|
| speed | 720 | 720f | [x] |
| fireInterval | 0.15 | 0.15f | [x] |
| lifetime | 1.4 | 1.4f | [x] |
| radius | 6 | 6f | [x] |
| poolSize | 96 | 96 | [x] |
| spreadOffset | .pi / 24 | MathUtils.PI / 24f | [x] |
| bombSpeed | 280 | 280f | [x] |
| bombLifetime | 3.0 | 3.0f | [x] |
| bombRadius | 26 | 26f | [x] |
| bombSpinPeriod | 0.6 | 0.6f | [x] |
| enemyBulletSpeedMul | 0.45 | 0.45f | [x] |
| enemyBulletVisualRadius | 11 | 11f | [x] |

## Enemy

| Constant | iOS | Android | Match |
|---|---|---|---|
| spawnIntervalStart | 2.7 | 2.7f | [x] |
| spawnIntervalEnd | 0.75 | 0.75f | [x] |
| rampDuration | 90 | 90f | [x] |
| spawnPaddingPx | 60 | 60f | [x] |
| baseSpeed | 80 | 80f | [x] |
| speedJitter | 36 | 36f | [x] |
| radius | 22 | 22f | [x] |
| bossEveryNKills | 25 | 25 | [x] |
| fireRangePx | 360 | 360f | [x] |
| pointsOnKill | 100 | 100 | [x] |
| walkSpeedThresholdPx | 12 | 12f | [x] |
| facingFlipDeadzonePx | 8 | 8f | [x] |

## Joystick

| Constant | iOS | Android | Match |
|---|---|---|---|
| radius | 78 | 78f | [x] |
| thumbRadius | 34 | 34f | [x] |
| deadZone | 0.12 | 0.12f | [x] |
| edgeMargin | 32 | 32f | [x] |
| baseAlpha | 0.08 | 0.08f | [x] |
| strokeAlpha | 0.18 | 0.18f | [x] |
| thumbAlpha | 0.14 | 0.14f | [x] |

## Starfield

| Constant | iOS | Android | Match |
|---|---|---|---|
| layer1Count | 80 | 80 | [x] |
| layer2Count | 30 | 30 | [x] |
| layer1Speed | 0.06 | 0.06f | [x] |
| layer2Speed | 0.18 | 0.18f | [x] |
| twinkleCount | 16 | 16 | [x] |
| twinkleRadius | 1.6 | 1.6f | [x] |
| twinkleAlphaLow | 0.3 | 0.3f | [x] |
| twinklePeriodMin | 1.2 | 1.2f | [x] |
| twinklePeriodMax | 3.5 | 3.5f | [x] |

## Audio

| Constant | iOS | Android | Match |
|---|---|---|---|
| maxDistance | 900 | 900f | [x] |
| minVolume | 0.05 | 0.05f | [x] |
| playerShotVolume | 0.05 | 0.05f | [x] |

## Settings

| Constant | iOS | Android | Match |
|---|---|---|---|
| defaultMusicVolume | 0.18 | 0.18f | [x] |
| defaultSFXVolume | 1.0 | 1.0f | [x] |
| defaultHapticsEnabled | true | true | [x] |

## Pickup

| Constant | iOS | Android | Match |
|---|---|---|---|
| dropChance | 0.08 | 0.08f | [x] |
| lifetime | 8.0 | 8.0f | [x] |
| radius | 14 | 14f | [x] |
| spriteTargetMax | 36 | 36f | [x] |
| bobAmplitude | 4 | 4f | [x] |
| bobPeriod | 1.2 | 1.2f | [x] |
| scoreBonus | 500 | 500 | [x] |
| maxSpreadLevel | 2 | 2 | [x] |

## VFX

| Constant | iOS | Android | Match |
|---|---|---|---|
| shakeDecay | 0.86 | 0.86f | [x] |
| shakeMaxOffset | 18 | 18f | [x] |
| bombShakeIntensity | 6 | 6f | [x] |
| playerHitShakeIntensity | 8 | 8f | [x] |
| enemyKillShakeIntensity | 3 | 3f | [x] |
| thrustZ | -1 | -1f | [x] |
| thrustTailOffsetX | -22 | -22f | [x] |
| thrustScale | 1.4 | 1.4f | [x] |
| muzzleFlashDuration | 0.08 | 0.08f | [x] |
| muzzleFlashScale | 0.6 | 0.6f | [x] |
| damageFlashDuration | 0.12 | 0.12f | [x] |
| bombExplosionFrameDuration | 1.0 / 30 | 1f / 30f | [x] |
| bombExplosionScale | 3.4 | 3.4f | [x] |
| glowBombDuration | 0.35 | 0.35f | [x] |
| glowBombScale | 2.0 | 2.0f | [x] |
| warpVioletDuration | 0.12 | 0.12f | [x] |
| warpFadeDuration | 0.3 | 0.3f | [x] |
| blackholeOpenDuration | 0.15 | 0.15f | [x] |
| blackholeCollapseDuration | 0.2 | 0.2f | [x] |
| blackholeScale | 3.0 | 3.0f | [x] |
| blackholeSpinPeriod | 1.0 | 1.0f | [x] |
| planetRotationPeriodMin | 60 | 60f | [x] |
| planetRotationPeriodMax | 180 | 180f | [x] |

## World

| Constant | iOS | Android | Match |
|---|---|---|---|
| orbitTiltY | 0.45 | 0.45f | [x] |
| sunDisplayDiameter | 600 | 600f | [x] |
| mercuryDiameter | 110 | 110f | [x] |
| venusDiameter | 150 | 150f | [x] |
| earthDiameter | 165 | 165f | [x] |
| marsDiameter | 130 | 130f | [x] |
| jupiterDiameter | 360 | 360f | [x] |
| saturnDiameter | 320 | 320f | [x] |
| uranusDiameter | 230 | 230f | [x] |
| neptuneDiameter | 220 | 220f | [x] |
| orbitMercury | 600 | 600f | [x] |
| orbitVenus | 1100 | 1100f | [x] |
| orbitEarth | 1500 | 1500f | [x] |
| orbitMars | 2300 | 2300f | [x] |
| orbitJupiter | 4000 | 4000f | [x] |
| orbitSaturn | 5400 | 5400f | [x] |
| orbitUranus | 7000 | 7000f | [x] |
| orbitNeptune | 8500 | 8500f | [x] |
| orbitRingAlpha | 0.085 | 0.085f | [x] |
| orbitRingLineWidth | 1.5 | 1.5f | [x] |
| sunCoronaBlurRadius | 28 | 28f | [x] |
| sunCoronaAlpha | 0.55 | 0.55f | [x] |
| sunCoronaPulsePeriod | 6.0 | 6.0f | [x] |
| sunCoronaPulseAmplitude | 0.04 | 0.04f | [x] |
| angSpeedMercury | 0.045 | 0.045f | [x] |
| angSpeedVenus | 0.030 | 0.030f | [x] |
| angSpeedEarth | 0.022 | 0.022f | [x] |
| angSpeedMars | 0.015 | 0.015f | [x] |
| angSpeedJupiter | 0.009 | 0.009f | [x] |
| angSpeedSaturn | 0.006 | 0.006f | [x] |
| angSpeedUranus | 0.004 | 0.004f | [x] |
| angSpeedNeptune | 0.003 | 0.003f | [x] |
| cometSpawnIntervalMin | 22 | 22f | [x] |
| cometSpawnIntervalMax | 48 | 48f | [x] |
| cometPathRadius | 4200 | 4200f | [x] |
| cometExitJitterRad | .pi / 12 | MathUtils.PI / 12f | [x] |
| cometHeadRadius | 3 | 3f | [x] |
| cometTravelDurationMin | 7 | 7f | [x] |
| cometTravelDurationMax | 12 | 12f | [x] |
| cometTrailBirthRate | 90 | 90f | [x] |
| cometTrailLifetime | 1.4 | 1.4f | [x] |
| cometInitialDelay | 3 | 3f | [x] |

## World.Moons

| Constant | iOS | Android | Match |
|---|---|---|---|
| lunaDiameter | 38 | 38f | [x] |
| lunaRadius | 130 | 130f | [x] |
| lunaSpeed | 0.08 | 0.08f | [x] |
| ioDiameter | 32 | 32f | [x] |
| ioRadius | 240 | 240f | [x] |
| ioSpeed | 0.14 | 0.14f | [x] |
| europaDiameter | 28 | 28f | [x] |
| europaRadius | 295 | 295f | [x] |
| europaSpeed | 0.10 | 0.10f | [x] |
| ganymedeDiameter | 40 | 40f | [x] |
| ganymedeRadius | 355 | 355f | [x] |
| ganymedeSpeed | 0.075 | 0.075f | [x] |
| callistoDiameter | 36 | 36f | [x] |
| callistoRadius | 425 | 425f | [x] |
| callistoSpeed | 0.055 | 0.055f | [x] |
| titanDiameter | 42 | 42f | [x] |
| titanRadius | 320 | 320f | [x] |
| titanSpeed | 0.065 | 0.065f | [x] |

## World (Asteroid Belt)

| Constant | iOS | Android | Match |
|---|---|---|---|
| asteroidBeltInnerRadius | 2750 | 2750f | [x] |
| asteroidBeltOuterRadius | 3700 | 3700f | [x] |
| asteroidBeltCount | 180 | 180 | [x] |
| asteroidBeltAngularSpeed | 0.012 | 0.012f | [x] |
| asteroidBeltSpeedJitter | 0.0035 | 0.0035f | [x] |
| asteroidDiameterMin | 5 | 5f | [x] |
| asteroidDiameterMax | 18 | 18f | [x] |
| asteroidAlphaMin | 0.45 | 0.45f | [x] |
| asteroidAlphaMax | 0.9 | 0.9f | [x] |

## Camera

| Constant | iOS | Android | Match |
|---|---|---|---|
| deadzoneRadius | 36 | 36f | [x] |
| followLerpPerSec | 6 | 6f | [x] |
| enemySpawnViewPaddingPx | 96 | 96f | [x] |

## Category (Bitmasks)

| Constant | iOS | Android | Match |
|---|---|---|---|
| none | 0 | 0 | [x] |
| player | 1 << 0 | 1 shl 0 | [x] |
| enemy | 1 << 1 | 1 shl 1 | [x] |
| bullet | 1 << 2 | 1 shl 2 | [x] |
| pickup | 1 << 3 | 1 shl 3 | [x] |
| enemyBullet | 1 << 4 | 1 shl 4 | [x] |

---

## User-Specified Golden Values

| Parameter | Expected | iOS | Android | Match |
|---|---|---|---|---|
| deadzone | 0.12 | 0.12 | 0.12f | [x] |
| fireInterval | 0.15 | 0.15 | 0.15f | [x] |
| projectile speed | 720 | 720 | 720f | [x] |
| bomb speed | 280 | 280 | 280f | [x] |
| spread | pi/24 | .pi / 24 | MathUtils.PI / 24f | [x] |
| spawn ramp start | 2.7 | 2.7 | 2.7f | [x] |
| spawn ramp end | 0.75 | 0.75 | 0.75f | [x] |
| spawn ramp duration | 90s | 90 | 90f | [x] |
| bossEveryNKills | 25 | 25 | 25 | [x] |
| drag | 2.4 | 2.4 | 2.4f | [x] |
| maxSpeed | 360 | 360 | 360f | [x] |
| pickup dropChance | 8% | 0.08 | 0.08f | [x] |
| pickup lifetime | 8s | 8.0 | 8.0f | [x] |
| camera deadzone | 36 | 36 | 36f | [x] |
| camera lerp | 6 | 6 | 6f | [x] |
| shake decay | 0.86 | 0.86 | 0.86f | [x] |

---

**Result: 166/166 constants match. 16/16 golden values match. PASS.**
