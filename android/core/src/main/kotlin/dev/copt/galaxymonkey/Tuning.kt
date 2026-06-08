package dev.copt.galaxymonkey

import com.badlogic.gdx.math.MathUtils

object Tuning {

    object Player {
        const val maxSpeed = 360f             // pt/s at full stick
        const val acceleration = 1600f        // pt/s² toward target velocity
        const val drag = 2.4f                 // exponential damping when no input
        const val radius = 22f                // collision radius
        const val startingLives = 3
        // Hard cap on lives, including life-restore pickups. Matches the
        // HUD's pre-allocated banana-heart icon slots (HUDController).
        const val maxLives = 5
        const val invulnDuration = 1.6f
        const val invulnBlinkHz = 12.0f
        // Max bank angle in radians the ship tilts to when sliding sideways
        // at full horizontal stick. 30° reads as a real bank without the
        // sprite looking like it's spinning 2D around its center.
        val maxBankRadians = MathUtils.PI / 6f
        // Exponential approach rate (1/s) toward target bank angle. Higher =
        // snappier. ~8 gives a 125 ms time constant — the ship visibly leans
        // into a slide but doesn't snap.
        const val bankApproachRate = 8.0f
    }

    object Projectile {
        const val speed = 720f
        const val fireInterval = 0.15f
        const val lifetime = 1.4f
        const val radius = 6f
        const val poolSize = 96
        // Per-side angular offset for the golden-banana spread shot.
        // Level 1 fires at ±offset; level 2 fires at 0 and ±2·offset.
        val spreadOffset = MathUtils.PI / 24f   // 7.5°
        // Gorilla "poison banana" projectile — slow, telegraphed, giant, and
        // spinning. Internally still called "bomb" (same fire path / Sprite.bomb).
        const val bombSpeed = 280f
        const val bombLifetime = 3.0f
        const val bombRadius = 26f
        // Seconds per full spin of the banana while it flies.
        const val bombSpinPeriod = 0.6f
        // Enemy bullets — slowed and visually enlarged so the player can
        // read incoming shots. Physics radius stays the pool default; only
        // the rendered sprite scales up.
        const val enemyBulletSpeedMul = 0.45f
        const val enemyBulletVisualRadius = 11f
    }

    object Enemy {
        const val spawnIntervalStart = 2.7f
        const val spawnIntervalEnd = 0.75f
        const val rampDuration = 90f
        const val spawnPaddingPx = 60f          // beyond the visible bounds
        const val baseSpeed = 80f
        const val speedJitter = 36f             // ± random
        const val radius = 22f
        // Spawn one gorilla boss for every N regular kills. The counter
        // resets on boss spawn — so killing the gorilla doesn't roll into
        // back-to-back boss waves.
        const val bossEveryNKills = 25
        // Shooters only fire when the player is closer than this distance.
        // Keeps off-screen enemies quiet and biases attack pressure to the
        // visible play area.
        const val fireRangePx = 360f
        // Default score per kill. Per-type overrides live on EnemyType.
        const val pointsOnKill = 100
        // Minimum per-frame velocity (pt/s) for the visual to play the walk
        // loop. Below this the enemy switches to the idle loop. Enemies move
        // at ~80·speedMul pt/s baseline, so the threshold sits well below
        // even the slowest archetype (gorilla at 40 pt/s).
        const val walkSpeedThresholdPx = 12f

        // Min horizontal distance-to-player (world px) before an enemy flips
        // its L/R facing. Deadzone prevents flicker on near-vertical approaches.
        const val facingFlipDeadzonePx = 8f
    }

    object Joystick {
        const val radius = 78f
        const val thumbRadius = 34f
        const val deadZone = 0.12f              // fraction of radius
        const val edgeMargin = 32f              // off-screen edge padding for resting positions
        // Visible-but-barely alphas. The sticks are hidden entirely when no
        // finger is down; these only apply while the stick is engaged.
        const val baseAlpha = 0.08f
        const val strokeAlpha = 0.18f
        const val thumbAlpha = 0.14f
    }

    object Starfield {
        const val layer1Count = 80
        const val layer2Count = 30
        const val layer1Speed = 0.06f           // parallax factor
        const val layer2Speed = 0.18f
        // Twinkle pass — a small set of bright screen-locked dots that
        // pulse asynchronously over the baked starfield. Sub-pixel bound,
        // no parallax (these are camera-fixed "near" stars).
        const val twinkleCount = 16
        const val twinkleRadius = 1.6f
        const val twinkleAlphaLow = 0.3f
        const val twinklePeriodMin = 1.2f
        const val twinklePeriodMax = 3.5f
    }

    object Audio {
        // Distance at which spatial SFX hit the minimum volume floor.
        const val maxDistance = 900f
        // Volume floor so distant sounds don't fully vanish.
        const val minVolume = 0.05f
        // Banana shot is intentionally near-silent (~95% reduction).
        const val playerShotVolume = 0.05f
    }

    /** First-launch defaults for user-tunable settings. The live values are
     *  read/written through SettingsStore (Preferences-backed); these are
     *  only consulted when no persisted value exists yet. */
    object Settings {
        const val defaultMusicVolume = 0.18f
        const val defaultSFXVolume = 1.0f
        const val defaultHapticsEnabled = true
    }

    object Pickup {
        // Drop probability per enemy kill (rolled in PickupSystem.trySpawnGoldenBanana).
        const val dropChance = 0.08f            // ~1 in 12 kills
        const val lifetime = 8.0f
        const val radius = 14f
        const val spriteTargetMax = 36f
        const val bobAmplitude = 4f
        const val bobPeriod = 1.2f
        const val scoreBonus = 500
        // Player.spreadLevel is clamped to this. 0 = single, 1 = 2-line, 2 = 3-line.
        const val maxSpreadLevel = 2
    }

    object VFX {
        // Screen shake — per-frame decay, max offset, and per-event intensities.
        const val shakeDecay = 0.86f
        const val shakeMaxOffset = 18f
        // Soft thud for the poison-banana splash (was 14 for the old bomb blast).
        const val bombShakeIntensity = 6f
        const val playerHitShakeIntensity = 8f
        const val enemyKillShakeIntensity = 3f

        // Engine thrust plume (particle emitter — see ThrusterEmitter / Player).
        // Z just below the player's body so the plume reads as exhaust.
        const val thrustZ = -1f
        // Plume tail offset behind the ship's local +X axis.
        const val thrustTailOffsetX = -22f
        // Scale relative to ship radius.
        const val thrustScale = 1.4f

        // Muzzle flash.
        const val muzzleFlashDuration = 0.08f
        const val muzzleFlashScale = 0.6f

        // Damage flash (white tint on the ship body).
        const val damageFlashDuration = 0.12f

        // Bomb explosion (the 25-frame chunky sequence).
        const val bombExplosionFrameDuration = 1f / 30f
        // Visual size = Enemy.radius × this.
        const val bombExplosionScale = 3.4f

        // Glow halo composited under the bomb detonation.
        const val glowBombDuration = 0.35f
        const val glowBombScale = 2.0f

        // Blackhole warp — the kid-friendly enemy death. The sprite flushes
        // violet, then gently shrinks and fades into a soft violet halo that
        // glows in and out. Deliberately subtle: no spiral, no big spin.
        const val warpVioletDuration = 0.12f
        const val warpFadeDuration = 0.3f
        const val blackholeOpenDuration = 0.15f
        const val blackholeCollapseDuration = 0.2f
        // Halo display size = Enemy.radius × this.
        const val blackholeScale = 3.0f
        // Seconds per full rotation of the soft halo.
        const val blackholeSpinPeriod = 1.0f

        // Slow self-rotation period in seconds (per planet, on its own axis).
        const val planetRotationPeriodMin = 60f
        const val planetRotationPeriodMax = 180f
    }

    /** Solar-system layout. The Sun sits at worldCenter (computed from scene
     *  size in GameScreen). Planet rings are scaled into a roomy universe
     *  roughly 25× the scene width across; past Neptune is empty space. */
    object World {
        const val orbitTiltY = 0.45f                   // semiMinor/semiMajor → tilted ecliptic
        const val sunDisplayDiameter = 600f
        // Per-planet display diameter (points).
        const val mercuryDiameter = 110f
        const val venusDiameter = 150f
        const val earthDiameter = 165f
        const val marsDiameter = 130f
        const val jupiterDiameter = 360f
        const val saturnDiameter = 320f
        const val uranusDiameter = 230f
        const val neptuneDiameter = 220f
        // Orbital radii in points around the Sun.
        // Ratios follow real AU distances (Mercury 0.39, Venus 0.72,
        // Earth 1.00, Mars 1.52, Jupiter 5.20, Saturn 9.58, Uranus 19.2,
        // Neptune 30.05) with mild compression past Jupiter so Neptune
        // still fits inside the playable universe. The Mars→Jupiter jump
        // preserves the asteroid-belt gap.
        const val orbitMercury = 600f
        const val orbitVenus = 1100f
        const val orbitEarth = 1500f
        const val orbitMars = 2300f
        const val orbitJupiter = 4000f
        const val orbitSaturn = 5400f
        const val orbitUranus = 7000f
        const val orbitNeptune = 8500f
        // Orrery orbit rings — faint elliptical strokes tracing each
        // planet's track around the Sun. Vector strokes (ShapeRenderer) so
        // there's no texture rectangle to leak at large scale.
        const val orbitRingAlpha = 0.085f
        const val orbitRingLineWidth = 1.5f
        // Sun corona — a blurred copy of the sun sprite so the rasterized
        // alpha falls off continuously past the disk. Subtle scale pulse
        // so the star breathes.
        const val sunCoronaBlurRadius = 28f
        const val sunCoronaAlpha = 0.55f
        const val sunCoronaPulsePeriod = 6.0f
        const val sunCoronaPulseAmplitude = 0.04f      // ±4% scale
        // Angular speed (rad/s) per planet. Ratios are Kepler-ish (inner
        // planets faster) but compressed so even Neptune visibly orbits
        // in a play session. All planets start at phase 0 so the game
        // opens on a clean conjunction line that drifts apart naturally.
        const val angSpeedMercury = 0.045f
        const val angSpeedVenus = 0.030f
        const val angSpeedEarth = 0.022f
        const val angSpeedMars = 0.015f
        const val angSpeedJupiter = 0.009f
        const val angSpeedSaturn = 0.006f
        const val angSpeedUranus = 0.004f
        const val angSpeedNeptune = 0.003f
        // Comets — random spawn that streak across the world on a straight
        // chord with a particle trail. Adds non-orbital motion to the
        // cosmos. Particles detach into world space so the trail is left
        // behind in the comet's wake (not dragged along with the head).
        const val cometSpawnIntervalMin = 22f
        const val cometSpawnIntervalMax = 48f
        // Path radius is chosen so chords pass through the inner-to-middle
        // solar system where the player typically is (Earth at 1500,
        // asteroid belt at ~3200, Jupiter at 4000). Far enough out to feel
        // like an interloper; close enough that the player actually sees
        // one occasionally.
        const val cometPathRadius = 4200f
        // Max angular deviation from a 180° (origin-crossing) chord. With
        // ±15° jitter the closest approach to origin is r·sin(7.5°) ≈
        // 0.13·r ≈ 550pt, so comets pass through the inner planets area.
        val cometExitJitterRad = MathUtils.PI / 12f
        const val cometHeadRadius = 3f
        const val cometTravelDurationMin = 7f
        const val cometTravelDurationMax = 12f
        const val cometTrailBirthRate = 90f
        const val cometTrailLifetime = 1.4f
        const val cometInitialDelay = 3f         // first comet within seconds of game start
        // Moons — Luna (Earth), Galileans (Jupiter ×4), Titan (Saturn).
        // Diameter is the rendered point size; radius is the orbital radius
        // around the parent planet's center in points; angular speed in
        // rad/s. Inner moons orbit faster than outer ones (Kepler-ish).
        object Moons {
            // Earth's Luna.
            const val lunaDiameter = 38f
            const val lunaRadius = 130f
            const val lunaSpeed = 0.08f
            // Jupiter's Galileans, inside → out.
            const val ioDiameter = 32f
            const val ioRadius = 240f
            const val ioSpeed = 0.14f
            const val europaDiameter = 28f
            const val europaRadius = 295f
            const val europaSpeed = 0.10f
            const val ganymedeDiameter = 40f
            const val ganymedeRadius = 355f
            const val ganymedeSpeed = 0.075f
            const val callistoDiameter = 36f
            const val callistoRadius = 425f
            const val callistoSpeed = 0.055f
            // Saturn's Titan.
            const val titanDiameter = 42f
            const val titanRadius = 320f
            const val titanSpeed = 0.065f
        }
        // Asteroid belt between Mars and Jupiter. The mean angular speed
        // sits between Mars and Jupiter; each rock gets a small ±jitter
        // so the belt churns rather than rotates as a rigid disk.
        const val asteroidBeltInnerRadius = 2750f
        const val asteroidBeltOuterRadius = 3700f
        const val asteroidBeltCount = 180
        const val asteroidBeltAngularSpeed = 0.012f
        const val asteroidBeltSpeedJitter = 0.0035f    // ±rad/s
        const val asteroidDiameterMin = 5f
        const val asteroidDiameterMax = 18f
        const val asteroidAlphaMin = 0.45f
        const val asteroidAlphaMax = 0.9f
    }

    object Camera {
        /** Player can drift this many points off-center before the camera reacts. */
        const val deadzoneRadius = 36f
        /** Per-second lerp factor toward the target. Larger = snappier follow. */
        const val followLerpPerSec = 6f
        /** Extra padding beyond the camera viewport for enemy spawn placement. */
        const val enemySpawnViewPaddingPx = 96f
    }
}

/** Collision category bitmasks for custom circle-overlap dispatch.
 *  Player-fired projectiles use [bullet]; enemy projectiles use [enemyBullet]
 *  so friendly fire doesn't damage other enemies in flight path. */
object Category {
    const val none = 0
    const val player = 1 shl 0
    const val enemy = 1 shl 1
    const val bullet = 1 shl 2
    const val pickup = 1 shl 3
    const val enemyBullet = 1 shl 4
}
