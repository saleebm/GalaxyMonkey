//
//  Tuning.swift
//  GalaxyMonkey
//
//  Grouped tuning knobs. Adjust feel here; subsystems read these constants
//  rather than embedding numbers in code paths.
//

import CoreGraphics
import Foundation

enum Tuning {

    enum Player {
        static let maxSpeed: CGFloat = 360         // pt/s at full stick
        static let acceleration: CGFloat = 1600    // pt/s² toward target velocity
        static let drag: CGFloat = 2.4             // exponential damping when no input
        static let radius: CGFloat = 22            // collision radius
        static let startingLives: Int = 3
        static let invulnDuration: TimeInterval = 1.6
        static let invulnBlinkHz: Double = 12
        // Ship stays upright instead of fully rotating to face the aim
        // direction. It mirrors L/R via xScale and tilts by up to this many
        // radians based on the aim's vertical component (≈12°).
        static let aimTiltMax: CGFloat = 0.21
    }

    enum Projectile {
        static let speed: CGFloat = 720
        static let fireInterval: TimeInterval = 0.15
        static let lifetime: TimeInterval = 1.4
        static let radius: CGFloat = 6
        static let poolSize: Int = 96
        // Gorilla bomb — slow, heavy, larger contact radius.
        static let bombSpeed: CGFloat = 280
        static let bombLifetime: TimeInterval = 3.0
        static let bombRadius: CGFloat = 16
    }

    enum Enemy {
        static let spawnIntervalStart: TimeInterval = 1.6
        static let spawnIntervalEnd: TimeInterval = 0.45
        static let rampDuration: TimeInterval = 90
        static let spawnPaddingPx: CGFloat = 60     // beyond the visible bounds
        static let baseSpeed: CGFloat = 130
        static let speedJitter: CGFloat = 60        // ± random
        static let radius: CGFloat = 22
        // Spawn one gorilla boss for every N regular kills. The counter
        // resets on boss spawn — so killing the gorilla doesn't roll into
        // back-to-back boss waves.
        static let bossEveryNKills: Int = 25
        // Shooters only fire when the player is closer than this distance.
        // Keeps off-screen enemies quiet and biases attack pressure to the
        // visible play area.
        static let fireRangePx: CGFloat = 360
        // Default score per kill. Per-type overrides live on EnemyType.
        static let pointsOnKill: Int = 100
    }

    enum Joystick {
        static let radius: CGFloat = 78
        static let thumbRadius: CGFloat = 34
        static let deadZone: CGFloat = 0.12         // fraction of radius
        static let edgeMargin: CGFloat = 32         // off-screen edge padding for resting positions
        // Visible-but-barely alphas. The sticks are hidden entirely when no
        // finger is down; these only apply while the stick is engaged.
        static let baseAlpha: CGFloat = 0.08
        static let strokeAlpha: CGFloat = 0.18
        static let thumbAlpha: CGFloat = 0.14
    }

    enum Starfield {
        static let layer1Count: Int = 80
        static let layer2Count: Int = 30
        static let layer1Speed: CGFloat = 0.06      // parallax factor
        static let layer2Speed: CGFloat = 0.18
    }

    enum Audio {
        // Distance at which spatial SFX hit the minimum volume floor.
        static let maxDistance: CGFloat = 900
        // Volume floor so distant sounds don't fully vanish.
        static let minVolume: Float = 0.05
        // Banana shot is intentionally near-silent (~95% reduction).
        static let playerShotVolume: Float = 0.05
    }
}

/// Physics category bitmasks for SKPhysicsContact wiring.
struct Category {
    static let none:    UInt32 = 0
    static let player:  UInt32 = 1 << 0
    static let enemy:   UInt32 = 1 << 1
    static let bullet:  UInt32 = 1 << 2
    static let pickup:  UInt32 = 1 << 3
}
