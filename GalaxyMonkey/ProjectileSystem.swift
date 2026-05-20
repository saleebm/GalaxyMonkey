//
//  ProjectileSystem.swift
//  GalaxyMonkey
//
//  Pooled bullet management. GameScene calls `tryFirePlayer(...)` whenever
//  the player is firing. Enemies fire via `fireEnemyBullet(...)`. Bullets are
//  recycled after lifetime expiry or on contact.
//
//  Each fire call paints a fresh visual child (texture if available via
//  SpriteCatalog, otherwise a shape primitive). The pool re-uses the
//  parent SKNode + SKPhysicsBody — the heavy parts to allocate.
//
//  Gorilla bombs follow a separate slower/larger path via `fireBomb(...)`;
//  they aren't pooled because they're rare and use a bigger physics body.
//

import SpriteKit
import UIKit

final class ProjectileSystem {

    private weak var scene: SKScene?
    private var pool: [Bullet] = []
    private var activeBullets: [Bullet] = []
    private var lastShotTime: TimeInterval = 0
    private var bounds: CGRect = .zero

    init(scene: SKScene) {
        self.scene = scene
        for _ in 0..<Tuning.Projectile.poolSize {
            pool.append(makeBullet(radius: Tuning.Projectile.radius))
        }
    }

    func updateBounds(_ rect: CGRect) { bounds = rect }

    /// Call every frame while the player is firing. Cooldown is internal.
    /// `spreadLevel` is the player's current weapon level (0 = single shot,
    /// 1 = 2-line, 2 = 3-line). Returns true if at least one bullet was
    /// spawned this call.
    @discardableResult
    func tryFirePlayer(from position: CGPoint, angle: CGFloat,
                       spreadLevel: Int, now: TimeInterval) -> Bool {
        if now - lastShotTime < Tuning.Projectile.fireInterval { return false }
        lastShotTime = now
        for a in spreadAngles(base: angle, level: spreadLevel) {
            fire(from: position, angle: a,
                 category: Category.bullet, target: Category.enemy,
                 sprite: .bullet, fallbackColor: .yellow,
                 speed: Tuning.Projectile.speed,
                 radius: Tuning.Projectile.radius,
                 lifetime: Tuning.Projectile.lifetime)
        }
        return true
    }

    private func spreadAngles(base: CGFloat, level: Int) -> [CGFloat] {
        let off = Tuning.Projectile.spreadOffset
        switch level {
        case 0: return [base]
        case 1: return [base - off, base + off]
        default: return [base - 2 * off, base, base + 2 * off]
        }
    }

    func fireEnemyBullet(from position: CGPoint, angle: CGFloat) {
        // `radius` here drives only the rendered sprite scale — the pooled
        // physics body keeps the default Projectile.radius.
        fire(from: position, angle: angle,
             category: Category.enemyBullet, target: Category.player,
             sprite: .enemyBullet,
             fallbackColor: UIColor(red: 1.0, green: 0.45, blue: 0.45, alpha: 1),
             speed: Tuning.Projectile.speed * Tuning.Projectile.enemyBulletSpeedMul,
             radius: Tuning.Projectile.enemyBulletVisualRadius,
             lifetime: Tuning.Projectile.lifetime)
    }

    /// Gorilla bomb — slow, heavy, larger radius, longer lifetime. Not pooled
    /// because the bigger physics body would corrupt the bullet pool.
    func fireBomb(from position: CGPoint, angle: CGFloat) {
        let b = makeBullet(radius: Tuning.Projectile.bombRadius, isPoolable: false)
        b.isBomb = true
        configureFiredBullet(b, position: position, angle: angle,
                             category: Category.enemyBullet, target: Category.player,
                             sprite: .bomb,
                             fallbackColor: UIColor(red: 0.18, green: 0.18, blue: 0.22, alpha: 1),
                             speed: Tuning.Projectile.bombSpeed,
                             radius: Tuning.Projectile.bombRadius,
                             lifetime: Tuning.Projectile.bombLifetime)
        scene?.addChild(b.node)
        activeBullets.append(b)
    }

    /// Fires when a bomb's lifetime expires without hitting the player.
    var onBombExpire: ((CGPoint) -> Void)?

    /// Lookup helper for contact handlers: given an SKNode (from a contact
    /// body), return the owning Bullet if any.
    func findBullet(by node: SKNode) -> Bullet? {
        activeBullets.first(where: { $0.node === node })
    }

    private func fire(from position: CGPoint, angle: CGFloat,
                      category: UInt32, target: UInt32,
                      sprite: Sprite, fallbackColor: UIColor,
                      speed: CGFloat, radius: CGFloat, lifetime: TimeInterval) {
        guard let b = takeBullet() else { return }
        configureFiredBullet(b, position: position, angle: angle,
                             category: category, target: target,
                             sprite: sprite, fallbackColor: fallbackColor,
                             speed: speed, radius: radius, lifetime: lifetime)
        scene?.addChild(b.node)
        activeBullets.append(b)
    }

    private func configureFiredBullet(_ b: Bullet,
                                      position: CGPoint, angle: CGFloat,
                                      category: UInt32, target: UInt32,
                                      sprite: Sprite, fallbackColor: UIColor,
                                      speed: CGFloat, radius: CGFloat, lifetime: TimeInterval) {
        b.node.removeAllChildren()
        b.node.position = position
        b.node.zRotation = angle
        b.velocity = CGVector(dx: cos(angle) * speed, dy: sin(angle) * speed)
        b.remaining = lifetime
        b.node.physicsBody?.categoryBitMask = category
        b.node.physicsBody?.contactTestBitMask = target

        if let tex = SpriteCatalog.texture(for: sprite) {
            let s = SKSpriteNode(texture: tex)
            let maxDim = max(tex.size().width, tex.size().height)
            if maxDim > 0 { s.setScale(radius * 2.4 / maxDim) }
            b.node.addChild(s)
        } else {
            let shape = SKShapeNode(circleOfRadius: radius)
            shape.fillColor = fallbackColor
            shape.strokeColor = fallbackColor.withAlphaComponent(0.85)
            shape.lineWidth = 1
            b.node.addChild(shape)
        }

        b.node.isHidden = false
    }

    func update(dt: TimeInterval) {
        let dtF = CGFloat(dt)
        var i = 0
        while i < activeBullets.count {
            let b = activeBullets[i]
            b.remaining -= dt
            b.node.position.x += b.velocity.dx * dtF
            b.node.position.y += b.velocity.dy * dtF
            let p = b.node.position
            let expired = b.remaining <= 0
            let offscreen = p.x < bounds.minX - 40 || p.x > bounds.maxX + 40 ||
                             p.y < bounds.minY - 40 || p.y > bounds.maxY + 40
            if expired || offscreen {
                // A bomb that runs out the clock should still detonate
                // visually — an off-screen bomb is treated as a dud and
                // recycled silently.
                if b.isBomb && expired && !offscreen {
                    onBombExpire?(p)
                }
                recycle(at: i)
                continue
            }
            i += 1
        }
    }

    /// Called by GameScene on bullet contact. Pass the bullet node from the contact.
    func recycle(node: SKNode) {
        guard let idx = activeBullets.firstIndex(where: { $0.node === node }) else { return }
        recycle(at: idx)
    }

    private func recycle(at index: Int) {
        let b = activeBullets.remove(at: index)
        b.node.removeFromParent()
        b.node.removeAllChildren()
        b.node.isHidden = true
        if b.isPoolable { pool.append(b) }
    }

    func clearAll() {
        for b in activeBullets {
            b.node.removeFromParent()
            b.node.removeAllChildren()
            if b.isPoolable { pool.append(b) }
        }
        activeBullets.removeAll()
    }

    private func takeBullet() -> Bullet? {
        if let b = pool.popLast() { return b }
        return makeBullet(radius: Tuning.Projectile.radius)
    }

    private func makeBullet(radius: CGFloat, isPoolable: Bool = true) -> Bullet {
        let node = SKNode()
        node.zPosition = 40
        let body = SKPhysicsBody(circleOfRadius: radius)
        body.isDynamic = true
        body.affectedByGravity = false
        body.allowsRotation = false
        body.collisionBitMask = 0
        body.categoryBitMask = Category.bullet
        node.physicsBody = body
        return Bullet(node: node, isPoolable: isPoolable)
    }

    final class Bullet {
        let node: SKNode
        let isPoolable: Bool
        var velocity: CGVector = .zero
        var remaining: TimeInterval = 0
        var isBomb: Bool = false
        init(node: SKNode, isPoolable: Bool) {
            self.node = node
            self.isPoolable = isPoolable
        }
    }
}
