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
    static let haloTexture: SKTexture = {
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

    // MARK: - Blackhole warp (enemy death)

    /// Kid-friendly enemy death: the sprite flushes violet, then gently shrinks
    /// and fades into a soft violet halo that glows in and out. Deliberately
    /// subtle — no spiral, no big spin. The caller hands off a detached enemy
    /// node (physics off, frame loop frozen); this routine owns removing it.
    func spawnBlackholeWarp(enemyNode: SKNode, at position: CGPoint) {
        guard let scene else { enemyNode.removeFromParent(); return }

        // Soft violet halo behind the enemy: glows in, then fades out.
        let hole = makeBlackhole(at: position)
        hole.alpha = 0
        scene.addChild(hole)
        hole.run(SKAction.sequence([
            SKAction.fadeAlpha(to: 0.7, duration: Tuning.VFX.blackholeOpenDuration),
            SKAction.wait(forDuration: Tuning.VFX.warpFadeDuration * 0.4),
            SKAction.fadeOut(withDuration: Tuning.VFX.blackholeCollapseDuration),
            SKAction.removeFromParent(),
        ]))

        // Gentle shrink + fade, carried by the container so the whole node
        // (sprite + children) leaves together.
        let dissolve = SKAction.group([
            SKAction.scale(to: 0.05, duration: Tuning.VFX.warpFadeDuration),
            SKAction.fadeAlpha(to: 0, duration: Tuning.VFX.warpFadeDuration),
        ])
        dissolve.timingMode = .easeInEaseOut

        // colorize only applies to SKSpriteNode; the placeholder triangle
        // (art missing) just dissolves without the tint.
        let sprite: SKSpriteNode? = enemyNode as? SKSpriteNode
            ?? enemyNode.children.compactMap { $0 as? SKSpriteNode }.first
        if let s = sprite {
            s.run(SKAction.colorize(with: UIColor(red: 0.62, green: 0.20, blue: 0.95, alpha: 1),
                                    colorBlendFactor: 0.7,
                                    duration: Tuning.VFX.warpVioletDuration))
        }

        let delay = sprite == nil ? 0 : Tuning.VFX.warpVioletDuration
        enemyNode.run(SKAction.sequence([
            SKAction.wait(forDuration: delay),
            dissolve,
            SKAction.removeFromParent(),
        ]))
    }

    private func makeBlackhole(at position: CGPoint) -> SKSpriteNode {
        // Soft white→transparent halo, tinted violet under additive blend.
        let node = SKSpriteNode(texture: Self.haloTexture)
        node.color = UIColor(red: 0.55, green: 0.20, blue: 0.85, alpha: 1)
        node.colorBlendFactor = 0.9
        node.blendMode = .add
        node.run(SKAction.repeatForever(
            SKAction.rotate(byAngle: -.pi * 2, duration: Tuning.VFX.blackholeSpinPeriod)))
        let maxDim = max(node.size.width, node.size.height)
        if maxDim > 0 {
            node.setScale(Tuning.Enemy.radius * 2 * Tuning.VFX.blackholeScale / maxDim)
        }
        node.position = position
        node.zPosition = 44
        return node
    }
}
