//
//  SpriteCatalog.swift
//  GalaxyMonkey
//
//  Single entry point for loading game art from Assets.xcassets.
//  Adding a sprite = one enum case + one .imageset in Assets.xcassets.
//
//  All textures are optional: art is generated incrementally via the
//  SpriteCook pipeline. Subsystems should check `hasTexture(...)` and
//  fall back to primitive shape nodes when art is missing.
//

import SpriteKit
import UIKit

enum Sprite: String, CaseIterable {
    case player        = "Player"
    case bullet        = "Bullet"
    case enemyBullet   = "EnemyBullet"
    case bomb          = "Bomb"
    case goldenBanana  = "GoldenBanana"
    case preppyLeft    = "PreppyLeft"
    case preppyRight   = "PreppyRight"
    case whiteLeft     = "WhiteLeft"
    case whiteRight    = "WhiteRight"
    case shadyLeft     = "ShadyLeft"
    case shadyRight    = "ShadyRight"
    case gorilla       = "Gorilla"
    case gorillaWindup = "GorillaWindup"
    case droneSwarm    = "DroneSwarm"
    case heavyCosmonautLeft  = "HeavyCosmonautLeft"
    case heavyCosmonautRight = "HeavyCosmonautRight"
    case plasmaJelly   = "PlasmaJelly"
    case astroSniperLeft  = "AstroSniperLeft"
    case astroSniperRight = "AstroSniperRight"
    case miniBossLeft  = "MiniBossLeft"
    case miniBossRight = "MiniBossRight"
    case spaceBackdrop = "SpaceBackdrop"
    case explosion     = "Explosion"
    case title         = "Title"
    case pauseIcon     = "PauseIcon"
    case lifeHeart     = "LifeHeart"
    // Parallax backdrop set + glow halo.
    case sun           = "Sun"
    case mercury       = "Mercury"
    case venus         = "Venus"
    case earth         = "Earth"
    case mars          = "Mars"
    case jupiter       = "Jupiter"
    case saturn        = "Saturn"
    case uranus        = "Uranus"
    case neptune       = "Neptune"
    case glow          = "Glow"
}

enum SpriteCatalog {

    private static var cache: [Sprite: SKTexture] = [:]
    private static var missing: Set<Sprite> = []

    /// Returns nil if the imageset is missing from Assets.xcassets. Callers
    /// should branch on nil and draw a placeholder primitive instead.
    static func texture(for sprite: Sprite) -> SKTexture? {
        if let cached = cache[sprite] { return cached }
        if missing.contains(sprite) { return nil }
        guard UIImage(named: sprite.rawValue) != nil else {
            missing.insert(sprite)
            return nil
        }
        let texture = SKTexture(imageNamed: sprite.rawValue)
        texture.filteringMode = .linear
        cache[sprite] = texture
        return texture
    }

    static func hasTexture(_ sprite: Sprite) -> Bool {
        texture(for: sprite) != nil
    }

    static func preload() {
        Sprite.allCases.forEach { _ = texture(for: $0) }
    }
}
