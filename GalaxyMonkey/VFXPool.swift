//
//  VFXPool.swift
//  GalaxyMonkey
//
//  One-shot transient effects: muzzle flash, damage flash, glow.
//  Each effect is short enough that fresh SKAction sequences cost less
//  than pool bookkeeping.
//

import SpriteKit
import UIKit

final class VFXPool {

    private weak var scene: SKScene?

    init(scene: SKScene) {
        self.scene = scene
    }

    // MARK: - Halo texture
    //
    // Soft white-center → transparent-edge radial gradient, drawn once via
    // Core Graphics and cached as an SKTexture. Replaces the bundled
    // Glow.imageset asset, which SpriteCook generated as a fully-opaque 1024×1024
    // white square — under additive blend that read as a colored box around
    // explosions and muzzle flashes instead of a soft halo.
    private static let haloTexture: SKTexture = {
        let size: CGFloat = 256
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
        let image = renderer.image { ctx in
            let cg = ctx.cgContext
            let colors = [
                UIColor(white: 1, alpha: 1).cgColor,
                UIColor(white: 1, alpha: 0).cgColor,
            ] as CFArray
            let space = CGColorSpaceCreateDeviceRGB()
            // Force-unwrap is safe: deviceRGB + 2-stop gradient never fails.
            let gradient = CGGradient(colorsSpace: space, colors: colors, locations: [0, 1])!
            let center = CGPoint(x: size / 2, y: size / 2)
            cg.drawRadialGradient(gradient,
                                  startCenter: center, startRadius: 0,
                                  endCenter:   center, endRadius:   size / 2,
                                  options: [])
        }
        return SKTexture(image: image)
    }()

    // MARK: - Spawn API

    func spawnMuzzleFlash(at position: CGPoint, angle: CGFloat) {
        guard let scene else { return }
        let s = SKSpriteNode(texture: Self.haloTexture)
        let maxDim = max(s.size.width, s.size.height)
        if maxDim > 0 {
            s.setScale(Tuning.Player.radius * 2 * Tuning.VFX.muzzleFlashScale / maxDim)
        }
        s.color = UIColor(red: 1.0, green: 0.92, blue: 0.55, alpha: 1)
        s.colorBlendFactor = 0.7
        s.blendMode = .add
        s.position = position
        s.zRotation = angle
        s.zPosition = 48
        scene.addChild(s)
        s.run(SKAction.sequence([
            SKAction.fadeOut(withDuration: Tuning.VFX.muzzleFlashDuration),
            SKAction.removeFromParent(),
        ]))
    }

    func spawnDamageFlash(target: SKNode) {
        // SKSpriteNode is the only common node with colorize semantics; if the
        // visual is a SKNode container, walk it and tint the first sprite child.
        let sprite: SKSpriteNode? = target as? SKSpriteNode
            ?? target.children.compactMap { $0 as? SKSpriteNode }.first
        guard let s = sprite else { return }
        s.removeAction(forKey: "damageFlash")
        let seq = SKAction.sequence([
            SKAction.colorize(with: .white, colorBlendFactor: 1.0, duration: 0),
            SKAction.colorize(withColorBlendFactor: 0.0, duration: Tuning.VFX.damageFlashDuration),
        ])
        s.run(seq, withKey: "damageFlash")
    }

    func spawnGlow(at position: CGPoint, scale: CGFloat, duration: TimeInterval) {
        guard let scene else { return }
        let s = SKSpriteNode(texture: Self.haloTexture)
        let maxDim = max(s.size.width, s.size.height)
        if maxDim > 0 {
            s.setScale(Tuning.Enemy.radius * 2 * scale / maxDim)
        }
        s.color = UIColor(red: 1.0, green: 0.85, blue: 0.4, alpha: 1)
        s.colorBlendFactor = 0.8
        s.blendMode = .add
        s.position = position
        s.zPosition = 49
        scene.addChild(s)
        s.run(SKAction.sequence([
            SKAction.fadeOut(withDuration: duration),
            SKAction.removeFromParent(),
        ]))
    }
}
