//
//  Player.swift
//  GalaxyMonkey
//
//  Player ship. Holds position/velocity, aim, lives, and invulnerability.
//  GameScene drives `update(dt:moveStick:aimStick:)`; collisions are
//  resolved by GameScene contact delegate via `tryTakeHit()`.
//

import SpriteKit
import UIKit

final class Player {

    let node: SKNode
    let visual: SKNode

    private(set) var lives: Int = Tuning.Player.startingLives
    private(set) var isInvulnerable: Bool = false
    private(set) var velocity: CGVector = .zero
    /// Current aim angle in radians (0 = right). Defaults to right.
    private(set) var aimAngle: CGFloat = 0
    /// True while the right stick is engaged enough to fire.
    private(set) var isFiring: Bool = false

    private var invulnRemaining: TimeInterval = 0
    private var blinkPhase: Double = 0
    private weak var scene: SKScene?

    var onLivesChanged: ((Int) -> Void)?
    var isAlive: Bool { lives > 0 }

    init(scene: SKScene) {
        self.scene = scene
        let root = SKNode()
        root.position = CGPoint(x: scene.size.width / 2, y: scene.size.height / 2)
        root.zPosition = 50

        let body = SKNode()
        if let tex = SpriteCatalog.texture(for: .player) {
            let sprite = SKSpriteNode(texture: tex)
            sprite.setScale(Player.scaleToFit(textureSize: tex.size(),
                                              targetMax: Tuning.Player.radius * 2.6))
            body.addChild(sprite)
        } else {
            body.addChild(Player.placeholderHexagon(radius: Tuning.Player.radius))
        }
        root.addChild(body)
        self.visual = body
        self.node = root

        let pb = SKPhysicsBody(circleOfRadius: Tuning.Player.radius)
        pb.isDynamic = true
        pb.affectedByGravity = false
        pb.allowsRotation = false
        pb.categoryBitMask = Category.player
        pb.contactTestBitMask = Category.enemy
        pb.collisionBitMask = 0
        root.physicsBody = pb

        scene.addChild(root)
    }

    func reset() {
        lives = Tuning.Player.startingLives
        isInvulnerable = false
        invulnRemaining = 0
        velocity = .zero
        aimAngle = 0
        isFiring = false
        visual.alpha = 1
        guard let scene else { return }
        node.position = CGPoint(x: scene.size.width / 2, y: scene.size.height / 2)
        onLivesChanged?(lives)
    }

    func update(dt: TimeInterval,
                moveStick: CGVector,
                aimStick: CGVector,
                bounds: CGRect) {
        let dtF = CGFloat(dt)

        // Target velocity from stick magnitude/direction.
        let targetVx = moveStick.dx * Tuning.Player.maxSpeed
        let targetVy = moveStick.dy * Tuning.Player.maxSpeed

        if moveStick.dx == 0 && moveStick.dy == 0 {
            // Exponential drag toward 0 — feels like inertia, not friction.
            let damp = CGFloat(exp(-Tuning.Player.drag * dt))
            velocity.dx *= damp
            velocity.dy *= damp
        } else {
            let dvx = targetVx - velocity.dx
            let dvy = targetVy - velocity.dy
            let mag = max(0.0001, (dvx * dvx + dvy * dvy).squareRoot())
            let step = min(Tuning.Player.acceleration * dtF, mag)
            velocity.dx += dvx / mag * step
            velocity.dy += dvy / mag * step
        }

        var p = node.position
        p.x = max(bounds.minX, min(bounds.maxX, p.x + velocity.dx * dtF))
        p.y = max(bounds.minY, min(bounds.maxY, p.y + velocity.dy * dtF))
        node.position = p

        // Aim: only update when right stick is meaningfully engaged.
        let aimMag = (aimStick.dx * aimStick.dx + aimStick.dy * aimStick.dy).squareRoot()
        if aimMag > 0.001 {
            aimAngle = atan2(aimStick.dy, aimStick.dx)
            isFiring = true
        } else {
            isFiring = false
        }
        // Ship stays upright. Body mirrors L/R based on aim's X sign, and
        // tilts ±Tuning.Player.aimTiltMax based on aim's Y component — reads
        // as "leaning into the shot" rather than spinning to face it.
        let aimX = cos(aimAngle)
        let aimY = sin(aimAngle)
        let facingRight = aimX >= 0
        visual.xScale = facingRight ? abs(visual.xScale) : -abs(visual.xScale)
        // Tilt sign flips with facing so the lean reads naturally on both sides.
        let tiltSign: CGFloat = facingRight ? 1 : -1
        visual.zRotation = aimY * Tuning.Player.aimTiltMax * tiltSign

        if isInvulnerable {
            invulnRemaining -= dt
            blinkPhase += dt * Tuning.Player.invulnBlinkHz * .pi * 2
            visual.alpha = 0.45 + 0.55 * CGFloat(0.5 + 0.5 * sin(blinkPhase))
            if invulnRemaining <= 0 {
                isInvulnerable = false
                visual.alpha = 1
            }
        }
    }

    /// Called by GameScene on enemy contact. Returns true if damage was taken.
    @discardableResult
    func tryTakeHit() -> Bool {
        guard !isInvulnerable, isAlive else { return false }
        lives -= 1
        isInvulnerable = true
        invulnRemaining = Tuning.Player.invulnDuration
        blinkPhase = 0
        onLivesChanged?(lives)
        let gen = UINotificationFeedbackGenerator()
        gen.notificationOccurred(.warning)
        return true
    }

    // MARK: - Placeholder art

    private static func placeholderHexagon(radius r: CGFloat) -> SKShapeNode {
        let path = CGMutablePath()
        for i in 0..<6 {
            let a = CGFloat(i) * .pi / 3
            let p = CGPoint(x: cos(a) * r, y: sin(a) * r)
            if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        path.closeSubpath()
        let shape = SKShapeNode(path: path)
        shape.fillColor = UIColor(red: 0.45, green: 0.75, blue: 1.0, alpha: 1)
        shape.strokeColor = UIColor(red: 0.85, green: 0.95, blue: 1.0, alpha: 1)
        shape.lineWidth = 2
        // Nose marker so aim direction is readable.
        let nose = SKShapeNode(path: CGPath(rect: CGRect(x: r * 0.4, y: -2, width: r * 0.7, height: 4), transform: nil))
        nose.fillColor = .white
        nose.strokeColor = .clear
        shape.addChild(nose)
        return shape
    }

    private static func scaleToFit(textureSize: CGSize, targetMax: CGFloat) -> CGFloat {
        let maxDim = max(textureSize.width, textureSize.height)
        guard maxDim > 0 else { return 1 }
        return targetMax / maxDim
    }
}
