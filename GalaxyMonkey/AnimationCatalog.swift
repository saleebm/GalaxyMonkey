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
