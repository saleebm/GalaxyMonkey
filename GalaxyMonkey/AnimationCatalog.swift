//
//  AnimationCatalog.swift
//  GalaxyMonkey
//
//  Loads SKTextureAtlas-backed frame sequences from `.spriteatlas` folders
//  in Assets.xcassets. Mirrors SpriteCatalog's caching + nil-on-missing
//  pattern so callers can branch gracefully when the art isn't present.
//
//  Frames are alpha-sorted so they play in numeric file order
//  (e.g. gorilla_windup_01..08).
//

import SpriteKit

enum AnimationSet: String, CaseIterable {
    case gorillaIdle   = "GorillaIdleAnim"
    case gorillaWindup = "GorillaWindupAnim"
    case bombExplosion = "BombExplosionAnim"
    case droneSwarmIdle           = "DroneSwarmIdleAnim"
    case plasmaJellyIdle          = "PlasmaJellyIdleAnim"
    case preppyLeftIdle           = "PreppyLeftIdleAnim"
    case preppyRightIdle          = "PreppyRightIdleAnim"
    case whiteLeftIdle            = "WhiteLeftIdleAnim"
    case whiteRightIdle           = "WhiteRightIdleAnim"
    case shadyLeftIdle            = "ShadyLeftIdleAnim"
    case shadyRightIdle           = "ShadyRightIdleAnim"
    case heavyCosmonautLeftIdle   = "HeavyCosmonautLeftIdleAnim"
    case heavyCosmonautRightIdle  = "HeavyCosmonautRightIdleAnim"
    case astroSniperLeftIdle      = "AstroSniperLeftIdleAnim"
    case astroSniperRightIdle     = "AstroSniperRightIdleAnim"
    case miniBossLeftIdle         = "MiniBossLeftIdleAnim"
    case miniBossRightIdle        = "MiniBossRightIdleAnim"
    // Walk cycles. Used while the enemy is in motion; idle plays only when
    // the enemy is effectively stationary (e.g. attacking from range).
    case preppyLeftWalk           = "PreppyLeftWalkAnim"
    case preppyRightWalk          = "PreppyRightWalkAnim"
    case whiteLeftWalk            = "WhiteLeftWalkAnim"
    case whiteRightWalk           = "WhiteRightWalkAnim"
    case shadyLeftWalk            = "ShadyLeftWalkAnim"
    case shadyRightWalk           = "ShadyRightWalkAnim"
    case heavyCosmonautLeftWalk   = "HeavyCosmonautLeftWalkAnim"
    case heavyCosmonautRightWalk  = "HeavyCosmonautRightWalkAnim"
    case astroSniperLeftWalk      = "AstroSniperLeftWalkAnim"
    case astroSniperRightWalk     = "AstroSniperRightWalkAnim"
    // Mini-boss walk is a front-3/4 stomp with no lateral motion — one
    // atlas serves both facings.
    case miniBossWalk             = "MiniBossWalkAnim"
}

enum AnimationCatalog {

    private static var cache: [AnimationSet: [SKTexture]] = [:]
    private static var missing: Set<AnimationSet> = []

    /// Returns the ordered frame list. Empty array if the atlas is missing
    /// or contains no frames — call sites should check `isEmpty`.
    static func textures(for set: AnimationSet) -> [SKTexture] {
        if let cached = cache[set] { return cached }
        if missing.contains(set) { return [] }

        let atlas = SKTextureAtlas(named: set.rawValue)
        let names = atlas.textureNames.sorted()
        guard !names.isEmpty else {
            missing.insert(set)
            return []
        }
        let textures = names.map { atlas.textureNamed($0) }
        for t in textures { t.filteringMode = .linear }
        cache[set] = textures
        return textures
    }

    static func hasTextures(_ set: AnimationSet) -> Bool {
        !textures(for: set).isEmpty
    }

    /// Convenience: build an animate-then-remove action for a one-shot fx.
    static func oneShot(_ set: AnimationSet, frameDuration: TimeInterval) -> SKAction? {
        let frames = textures(for: set)
        guard !frames.isEmpty else { return nil }
        return SKAction.sequence([
            SKAction.animate(with: frames, timePerFrame: frameDuration),
            SKAction.removeFromParent(),
        ])
    }

    /// Convenience: build a forever-repeating action (for idle loops).
    static func loop(_ set: AnimationSet, frameDuration: TimeInterval) -> SKAction? {
        let frames = textures(for: set)
        guard !frames.isEmpty else { return nil }
        return SKAction.repeatForever(SKAction.animate(with: frames, timePerFrame: frameDuration))
    }
}
