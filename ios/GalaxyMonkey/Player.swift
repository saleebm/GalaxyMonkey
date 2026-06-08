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
    /// 0 = single shot, 1 = 2-line spread, 2 = 3-line spread (max). Bumped by
    /// the golden-banana pickup, fully reset on hit or game reset.
    private(set) var spreadLevel: Int = 0

    private var invulnRemaining: TimeInterval = 0
    private var blinkPhase: Double = 0
    private weak var scene: SKScene?
    private let haptics: HapticsController
    private let thrusterEmitter: SKEmitterNode

    var onLivesChanged: ((Int) -> Void)?
    var isAlive: Bool { lives > 0 }

    init(scene: SKScene, haptics: HapticsController) {
        self.scene = scene
        self.haptics = haptics
        let root = SKNode()
        // Start near Earth's orbit so the player isn't inside the Sun sprite.
        root.position = CGPoint(x: scene.size.width / 2 + Tuning.World.orbitEarth,
                                y: scene.size.height / 2)
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

        // Anti-grav exhaust beneath the saucer disc, shooting straight down.
        // Lives on `body`, so it inherits the bank tilt — when the disc leans
        // into a slide the thrust visibly vectors with it. targetNode = scene
        // detaches emitted particles into world space so they trail in place.
        let emitter = ThrusterEmitter.make()
        emitter.position = CGPoint(x: 0, y: -Tuning.Player.radius)
        emitter.emissionAngle = -.pi / 2
        emitter.zPosition = Tuning.VFX.thrustZ + 0.5
        body.addChild(emitter)
        self.thrusterEmitter = emitter

        let pb = SKPhysicsBody(circleOfRadius: Tuning.Player.radius)
        pb.isDynamic = true
        pb.affectedByGravity = false
        pb.allowsRotation = false
        pb.categoryBitMask = Category.player
        pb.contactTestBitMask = Category.enemy
        pb.collisionBitMask = 0
        root.physicsBody = pb

        scene.addChild(root)
        emitter.targetNode = scene
    }

    func reset() {
        lives = Tuning.Player.startingLives
        isInvulnerable = false
        invulnRemaining = 0
        velocity = .zero
        aimAngle = 0
        isFiring = false
        spreadLevel = 0
        visual.alpha = 1
        visual.zRotation = 0
        guard let scene else { return }
        node.position = CGPoint(x: scene.size.width / 2 + Tuning.World.orbitEarth,
                                y: scene.size.height / 2)
        onLivesChanged?(lives)
    }

    func update(dt: TimeInterval,
                moveStick: CGVector,
                aimStick: CGVector) {
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

        node.position.x += velocity.dx * dtF
        node.position.y += velocity.dy * dtF

        // Bank the visual into the slide. Negative zRotation = CW = top of
        // ship tilts right, which matches rightward velocity. Exponential
        // approach toward the target so the ship eases in/out of bank.
        let bankT = max(-1, min(1, velocity.dx / Tuning.Player.maxSpeed))
        let targetBank = -bankT * Tuning.Player.maxBankRadians
        let approach = CGFloat(1.0 - exp(-Double(Tuning.Player.bankApproachRate) * dt))
        visual.zRotation += (targetBank - visual.zRotation) * approach

        // Aim: only update when right stick is meaningfully engaged. Drives
        // ProjectileSystem firing direction independently of the ship's bank.
        let aimMag = (aimStick.dx * aimStick.dx + aimStick.dy * aimStick.dy).squareRoot()
        if aimMag > 0.001 {
            aimAngle = atan2(aimStick.dy, aimStick.dx)
            isFiring = true
        } else {
            isFiring = false
        }

        if isInvulnerable {
            invulnRemaining -= dt
            blinkPhase += dt * Tuning.Player.invulnBlinkHz * .pi * 2
            visual.alpha = 0.45 + 0.55 * CGFloat(0.5 + 0.5 * sin(blinkPhase))
            if invulnRemaining <= 0 {
                isInvulnerable = false
                visual.alpha = 1
            }
        }

        // Particle flame intensity scales with stick magnitude: always-on
        // cruise base, dramatic burst on full input. Direction is inherited
        // from `visual` via the emitter's local emissionAngle.
        let stickMag = min(1.0, (moveStick.dx * moveStick.dx + moveStick.dy * moveStick.dy).squareRoot())
        thrusterEmitter.particleBirthRate = 220 + 720 * stickMag
        thrusterEmitter.particleSpeed = 200 + 240 * stickMag
        thrusterEmitter.particleLifetime = 0.45 + 0.25 * stickMag
    }

    /// Called when a golden-banana pickup is collected. Returns true if the
    /// spread level actually increased (false at max — caller still awards
    /// the score bonus).
    @discardableResult
    func upgradeSpread() -> Bool {
        guard spreadLevel < Tuning.Pickup.maxSpreadLevel else { return false }
        spreadLevel += 1
        return true
    }

    /// Adds a life up to `Tuning.Player.maxLives`. Returns true if a life was
    /// actually added. Used as the "overflow" reward for collecting a banana
    /// while already at max spread.
    @discardableResult
    func restoreLife() -> Bool {
        guard lives < Tuning.Player.maxLives else { return false }
        lives += 1
        onLivesChanged?(lives)
        return true
    }

    /// Called by GameScene on enemy contact. Returns true if damage was taken.
    @discardableResult
    func tryTakeHit() -> Bool {
        guard !isInvulnerable, isAlive else { return false }
        lives -= 1
        spreadLevel = 0
        isInvulnerable = true
        invulnRemaining = Tuning.Player.invulnDuration
        blinkPhase = 0
        onLivesChanged?(lives)
        haptics.notification(.warning)
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
