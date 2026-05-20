//
//  PickupSystem.swift
//  GalaxyMonkey
//
//  Manages golden-banana pickups that drop rarely on enemy kills. Each
//  pickup is a static node with a Category.pickup physics body; collecting
//  it bumps Player.spreadLevel and awards a score bonus. Pickups despawn
//  after Tuning.Pickup.lifetime or when leaving the playfield.
//
//  Drops are rare (~8%) so we skip pooling — lazy creation is simpler and
//  the physics body churn is negligible at this rate.
//

import SpriteKit
import UIKit

final class PickupSystem {

    private weak var scene: SKScene?
    private var active: [Pickup] = []
    private var bounds: CGRect = .zero

    init(scene: SKScene) {
        self.scene = scene
    }

    func updateBounds(_ rect: CGRect) { bounds = rect }

    /// Rolls Tuning.Pickup.dropChance and spawns a golden banana on success.
    /// Call this on every enemy kill.
    func trySpawnGoldenBanana(at position: CGPoint) {
        guard Double.random(in: 0..<1) < Tuning.Pickup.dropChance else { return }
        spawnGoldenBanana(at: position)
    }

    /// Force-spawn — useful for debug/testing. The public API stays a roll.
    func spawnGoldenBanana(at position: CGPoint) {
        guard let scene else { return }
        let node = SKNode()
        node.position = position
        node.zPosition = 45

        let body = SKPhysicsBody(circleOfRadius: Tuning.Pickup.radius)
        body.isDynamic = true
        body.affectedByGravity = false
        body.allowsRotation = false
        body.collisionBitMask = 0
        body.categoryBitMask = Category.pickup
        body.contactTestBitMask = Category.player
        node.physicsBody = body

        let visual: SKNode
        if let tex = SpriteCatalog.texture(for: .goldenBanana) {
            let s = SKSpriteNode(texture: tex)
            let maxDim = max(tex.size().width, tex.size().height)
            if maxDim > 0 { s.setScale(Tuning.Pickup.spriteTargetMax / maxDim) }
            visual = s
        } else {
            // Fallback so pickups read clearly before the SpriteCook asset lands.
            let shape = SKShapeNode(circleOfRadius: 12)
            shape.fillColor = UIColor(red: 1.0, green: 0.85, blue: 0.2, alpha: 1)
            shape.strokeColor = UIColor(red: 1.0, green: 0.95, blue: 0.55, alpha: 1)
            shape.lineWidth = 2
            visual = shape
        }
        node.addChild(visual)

        scene.addChild(node)
        // Start phases at random so simultaneous drops don't bob in lockstep.
        active.append(Pickup(node: node, visual: visual,
                             remaining: Tuning.Pickup.lifetime,
                             phase: .random(in: 0..<(2 * .pi))))
    }

    func update(dt: TimeInterval) {
        let twoPi = 2 * Double.pi
        let bobStep = twoPi / Tuning.Pickup.bobPeriod
        var i = 0
        while i < active.count {
            let p = active[i]
            p.remaining -= dt
            p.phase += dt * bobStep
            p.visual.position.y = sin(CGFloat(p.phase)) * Tuning.Pickup.bobAmplitude

            let pos = p.node.position
            let outOfBounds = pos.x < bounds.minX - 40 || pos.x > bounds.maxX + 40 ||
                              pos.y < bounds.minY - 40 || pos.y > bounds.maxY + 40
            if p.remaining <= 0 || outOfBounds {
                p.node.removeFromParent()
                active.remove(at: i)
                continue
            }
            i += 1
        }
    }

    /// Called from GameScene contact handler. Removes the matching pickup.
    /// No-op if the node isn't tracked (already despawned).
    func collect(node: SKNode) {
        guard let idx = active.firstIndex(where: { $0.node === node }) else { return }
        active[idx].node.removeFromParent()
        active.remove(at: idx)
    }

    func clearAll() {
        for p in active { p.node.removeFromParent() }
        active.removeAll()
    }

    final class Pickup {
        let node: SKNode
        let visual: SKNode
        var remaining: TimeInterval
        var phase: Double
        init(node: SKNode, visual: SKNode, remaining: TimeInterval, phase: Double) {
            self.node = node
            self.visual = visual
            self.remaining = remaining
            self.phase = phase
        }
    }
}
