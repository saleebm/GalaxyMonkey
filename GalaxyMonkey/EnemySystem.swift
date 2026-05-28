//
//  EnemySystem.swift
//  GalaxyMonkey
//
//  Spawns enemies outside the visible bounds and drives them toward the
//  player. Spawn cadence ramps from start → end over `Enemy.rampDuration`.
//  Each enemy has a typed stat block from EnemyType (HP, speed mul, score,
//  attack behavior). Contact resolution lives in GameScene; it calls back
//  via `applyHit(...)` so this system owns HP bookkeeping.
//

import SpriteKit
import UIKit

final class EnemySystem {

    enum HitOutcome {
        case stillAlive
        // `node` is detached from the simulation but still in the scene, so the
        // caller can run a death animation (the blackhole warp) and remove it
        // when the FX finishes.
        case killed(score: Int, position: CGPoint, type: EnemyType, node: SKNode)
    }

    private weak var scene: SKScene?
    private var enemies: [Enemy] = []
    private var spawnTimer: TimeInterval = 0
    private var elapsed: TimeInterval = 0
    private var regularKillsSinceBoss: Int = 0
    private var bossAlive: Bool = false

    /// External provider for the player's current world position.
    var playerPositionProvider: () -> CGPoint = { .zero }
    /// Current camera position in world space — drives the spawn rectangle so
    /// enemies appear just outside what the player can actually see, even as
    /// the camera moves across the universe.
    var cameraPositionProvider: () -> CGPoint = { .zero }
    /// Visible viewport size in points.
    var viewSizeProvider: () -> CGSize = { .zero }

    /// Called when a shooting/bombing enemy wants to fire. The closure
    /// resolves which projectile path to use (bullet vs bomb) and routes
    /// to ProjectileSystem.
    var enemyFireRequest: (_ origin: CGPoint, _ angle: CGFloat, _ kind: ProjectileKind) -> Void = { _, _, _ in }

    enum ProjectileKind {
        case bullet
        case bomb
    }

    init(scene: SKScene) {
        self.scene = scene
    }

    func reset() {
        for e in enemies { e.node.removeFromParent() }
        enemies.removeAll()
        spawnTimer = 0
        elapsed = 0
        regularKillsSinceBoss = 0
        bossAlive = false
    }

    func update(dt: TimeInterval) {
        elapsed += dt
        spawnTimer -= dt

        let t = min(1, elapsed / Tuning.Enemy.rampDuration)
        let interval = Tuning.Enemy.spawnIntervalStart +
            (Tuning.Enemy.spawnIntervalEnd - Tuning.Enemy.spawnIntervalStart) * t
        if spawnTimer <= 0 {
            spawn()
            spawnTimer = interval
        }

        let target = playerPositionProvider()
        let dtF = CGFloat(dt)
        var i = 0
        while i < enemies.count {
            let e = enemies[i]
            let prev = e.node.position
            let dx = target.x - e.node.position.x
            let dy = target.y - e.node.position.y
            let mag = max(0.0001, (dx * dx + dy * dy).squareRoot())
            e.node.position.x += dx / mag * e.speed * dtF
            e.node.position.y += dy / mag * e.speed * dtF

            updateMotionState(e, prev: prev, dt: dt)
            tickAttack(e, dx: dx, dy: dy, distance: mag, dt: dt)
            i += 1
        }
    }

    /// Swaps the visual between walk and idle loops based on observed
    /// per-frame motion. The windup action takes its own animation key, so
    /// this won't fight a bomb-throw mid-play.
    private func updateMotionState(_ e: Enemy, prev: CGPoint, dt: TimeInterval) {
        guard let visual = e.visual as? SKSpriteNode, dt > 0 else { return }
        let dxp = e.node.position.x - prev.x
        let dyp = e.node.position.y - prev.y
        let speed = (dxp * dxp + dyp * dyp).squareRoot() / CGFloat(dt)
        let desired: Enemy.AnimState = (speed >= Tuning.Enemy.walkSpeedThresholdPx) ? .walk : .idle
        guard desired != e.animState else { return }

        let set: AnimationSet? = (desired == .walk)
            ? walkAnimation(for: e.type, facingLeft: e.facingLeft)
            : idleAnimation(for: e.type, facingLeft: e.facingLeft)
        // Walk atlas may be absent for omni archetypes — fall back to idle
        // rather than blanking the sprite.
        let resolved = set ?? idleAnimation(for: e.type, facingLeft: e.facingLeft)
        guard let resolved, let loop = AnimationCatalog.loop(resolved, frameDuration: 0.125) else {
            e.animState = desired
            return
        }
        visual.removeAction(forKey: "loop")
        visual.run(loop, withKey: "loop")
        e.animState = desired
    }

    private func tickAttack(_ e: Enemy, dx: CGFloat, dy: CGFloat, distance: CGFloat, dt: TimeInterval) {
        guard e.attackCooldown != .infinity else { return }
        e.attackCooldown -= dt
        guard e.attackCooldown <= 0 else { return }

        switch e.type.attack {
        case .melee:
            return
        case .shoot:
            guard distance <= Tuning.Enemy.fireRangePx else {
                // Hold the trigger ready; don't burn cooldown while out of range.
                e.attackCooldown = 0.1
                return
            }
            let angle = atan2(dy, dx)
            enemyFireRequest(e.node.position, angle, .bullet)
            e.attackCooldown = e.type.attack.nextCooldown()
        case .bombThrow:
            // Boss bomb: capture origin + angle now, then drive the windup
            // animation. The actual bomb spawns on the release frame.
            let angle = atan2(dy, dx)
            let origin = CGPoint(x: e.node.position.x, y: e.node.position.y + e.radius * 0.4)
            let fire: () -> Void = { [weak self] in
                self?.enemyFireRequest(origin, angle, .bomb)
            }
            playGorillaWindup(enemy: e, fireAtRelease: fire)
            // Add the ~1s windup duration so the next attack waits for
            // this one to play out cleanly.
            e.attackCooldown = e.type.attack.nextCooldown() + 1.0
        }
    }

    private func playGorillaWindup(enemy e: Enemy, fireAtRelease: @escaping () -> Void) {
        guard let visual = e.visual as? SKSpriteNode else {
            fireAtRelease()
            return
        }
        let frames = AnimationCatalog.textures(for: .gorillaWindup)
        guard !frames.isEmpty else {
            fireAtRelease()
            return
        }
        // Per-frame timing curve: slow anticipation → quick release →
        // soft follow-through. Smooths out the 8fps frame-pop by holding
        // key poses longer and rushing the throw itself. Sums to ~0.8s
        // so the +1.0s cooldown padding in `tick` still lines up.
        let timings: [TimeInterval] = [0.16, 0.14, 0.12, 0.10, 0.08, 0.08, 0.14, 0.18]
        // The friendly windup-throw atlas (7 frames) releases the banana on
        // frame index 3 (arm snaps forward, banana leaves the hand).
        let releaseIdx = min(3, frames.count - 1)

        var actions: [SKAction] = []
        for i in 0..<frames.count {
            if i == releaseIdx {
                actions.append(SKAction.run(fireAtRelease))
            }
            actions.append(SKAction.setTexture(frames[i], resize: false))
            let dur = i < timings.count ? timings[i] : 0.12
            actions.append(SKAction.wait(forDuration: dur))
        }
        actions.append(SKAction.run { [weak visual, weak e] in
            guard let visual else { return }
            if let loop = AnimationCatalog.loop(.gorillaIdle, frameDuration: 0.125) {
                visual.run(loop, withKey: "loop")
            }
            // Gorilla never enters the walk state (no walk atlas), so reset
            // the tracked state so updateMotionState doesn't try to switch
            // back into a stale "walk".
            e?.animState = .idle
        })
        visual.removeAction(forKey: "loop")
        visual.run(SKAction.sequence(actions), withKey: "windup")
    }

    private func spawn() {
        guard let scene else { return }

        // Live viewport rectangle: a window centered on the camera. Enemies
        // pop in just past its edge so they're never inside the visible area
        // when they appear, even while the camera is mid-lerp toward the
        // player.
        let cam = cameraPositionProvider()
        let view = viewSizeProvider()
        guard view.width > 0, view.height > 0 else { return }
        let pad = Tuning.Camera.enemySpawnViewPaddingPx
        let r = CGRect(x: cam.x - view.width / 2,
                       y: cam.y - view.height / 2,
                       width: view.width,
                       height: view.height).insetBy(dx: -pad, dy: -pad)
        let side = Int.random(in: 0...3)
        let pos: CGPoint
        switch side {
        case 0: pos = CGPoint(x: r.minX, y: .random(in: r.minY...r.maxY))
        case 1: pos = CGPoint(x: r.maxX, y: .random(in: r.minY...r.maxY))
        case 2: pos = CGPoint(x: .random(in: r.minX...r.maxX), y: r.minY)
        default: pos = CGPoint(x: .random(in: r.minX...r.maxX), y: r.maxY)
        }

        // Boss cadence: when the kill counter clears the threshold and no
        // boss is alive, the next spawn is the gorilla.
        let type: EnemyType
        if !bossAlive && regularKillsSinceBoss >= Tuning.Enemy.bossEveryNKills {
            type = .gorilla
            bossAlive = true
            regularKillsSinceBoss = 0
        } else {
            type = EnemyType.weightedRandom()
        }

        // Facing: if the spawn is right of the camera center, the enemy
        // moves left toward the player → use the left-facing sprite; vice
        // versa.
        let facingLeft = pos.x >= cam.x
        let sprite = facingLeft ? type.leftSprite : type.rightSprite

        let e = makeEnemy(at: pos, type: type, facingLeft: facingLeft, sprite: sprite)
        scene.addChild(e.node)
        enemies.append(e)
    }

    /// Apply a single hit. Returns whether the enemy survived or died (with
    /// score). GameScene calls this on bullet/enemy contact.
    func applyHit(node: SKNode) -> HitOutcome {
        guard let idx = enemies.firstIndex(where: { $0.node === node }) else { return .stillAlive }
        let e = enemies[idx]
        e.hp -= 1
        if e.hp > 0 { return .stillAlive }
        let dead = detachForDeath(at: idx)
        return .killed(score: dead.type.scoreOnKill,
                       position: dead.node.position,
                       type: dead.type,
                       node: dead.node)
    }

    /// Cleanup kill (e.g., on player-enemy collision the enemy is consumed
    /// regardless of HP). Returns the detached node so the caller can run the
    /// death animation, or nil if the node wasn't an active enemy.
    @discardableResult
    func killByNode(_ node: SKNode) -> SKNode? {
        guard let idx = enemies.firstIndex(where: { $0.node === node }) else { return nil }
        return detachForDeath(at: idx).node
    }

    /// Detaches a dying enemy from active simulation but leaves its node in the
    /// scene: physics off (no further contacts mid-animation), frame loop
    /// frozen, boss/kill counters updated. The caller owns removing the node
    /// once its death FX completes. Reset/off-screen cleanup removes directly.
    private func detachForDeath(at idx: Int) -> Enemy {
        let e = enemies.remove(at: idx)
        e.node.physicsBody = nil
        e.visual.removeAction(forKey: "loop")
        if e.type == .gorilla {
            bossAlive = false
        } else {
            regularKillsSinceBoss += 1
        }
        return e
    }

    func contains(node: SKNode) -> Bool {
        enemies.contains(where: { $0.node === node })
    }

    private func makeEnemy(at pos: CGPoint, type: EnemyType, facingLeft: Bool, sprite: Sprite) -> Enemy {
        let radius = Tuning.Enemy.radius * type.radiusMul
        let baseSpeed = Tuning.Enemy.baseSpeed * type.speedMul
        let speed = baseSpeed + .random(in: -Tuning.Enemy.speedJitter...Tuning.Enemy.speedJitter)

        let root = SKNode()
        root.position = pos
        root.zPosition = (type == .gorilla) ? 47 : 45

        let visualChild = makeEnemyVisual(type: type, facingLeft: facingLeft, sprite: sprite, radius: radius)
        root.addChild(visualChild)

        let body = SKPhysicsBody(circleOfRadius: radius)
        body.isDynamic = true
        body.affectedByGravity = false
        body.allowsRotation = false
        body.categoryBitMask = Category.enemy
        body.contactTestBitMask = Category.player | Category.bullet
        body.collisionBitMask = 0
        root.physicsBody = body

        return Enemy(node: root,
                     type: type,
                     hp: type.hp,
                     speed: speed,
                     radius: radius,
                     facingLeft: facingLeft,
                     visual: visualChild,
                     attackCooldown: type.attack.initialCooldown)
    }

    private func makeEnemyVisual(type: EnemyType, facingLeft: Bool, sprite: Sprite, radius: CGFloat) -> SKNode {
        // Prefer an animated walk atlas first (enemies spawn in motion);
        // fall back to the idle atlas, then the static imageset, then the
        // placeholder triangle. updateMotionState swaps the loop in/out of
        // walk/idle via the shared "loop" key as velocity changes.
        let preferred = walkAnimation(for: type, facingLeft: facingLeft)
            ?? idleAnimation(for: type, facingLeft: facingLeft)
        if let set = preferred,
           let first = AnimationCatalog.textures(for: set).first {
            let s = SKSpriteNode(texture: first)
            let maxDim = max(first.size().width, first.size().height)
            if maxDim > 0 { s.setScale(radius * 2.4 / maxDim) }
            if let loop = AnimationCatalog.loop(set, frameDuration: 0.125) {
                s.run(loop, withKey: "loop")
            }
            return s
        }

        if let tex = SpriteCatalog.texture(for: sprite) {
            let s = SKSpriteNode(texture: tex)
            let maxDim = max(tex.size().width, tex.size().height)
            if maxDim > 0 { s.setScale(radius * 2.4 / maxDim) }
            return s
        }
        return placeholderTriangle(radius: radius)
    }

    private func idleAnimation(for type: EnemyType, facingLeft: Bool) -> AnimationSet? {
        switch type {
        case .gorilla:        return .gorillaIdle
        case .droneSwarm:     return .droneSwarmIdle
        case .plasmaJelly:    return .plasmaJellyIdle
        case .preppy:         return facingLeft ? .preppyLeftIdle         : .preppyRightIdle
        case .white:          return facingLeft ? .whiteLeftIdle          : .whiteRightIdle
        case .shady:          return facingLeft ? .shadyLeftIdle          : .shadyRightIdle
        case .heavyCosmonaut: return facingLeft ? .heavyCosmonautLeftIdle : .heavyCosmonautRightIdle
        case .astroSniper:    return facingLeft ? .astroSniperLeftIdle    : .astroSniperRightIdle
        case .miniBoss:       return facingLeft ? .miniBossLeftIdle       : .miniBossRightIdle
        }
    }

    /// Walk-cycle atlas per (type, facing). Returns nil for archetypes that
    /// don't have a walk cycle (omni floaters, boss) — callers should fall
    /// back to the idle atlas.
    private func walkAnimation(for type: EnemyType, facingLeft: Bool) -> AnimationSet? {
        switch type {
        case .preppy:         return facingLeft ? .preppyLeftWalk         : .preppyRightWalk
        case .white:          return facingLeft ? .whiteLeftWalk          : .whiteRightWalk
        case .shady:          return facingLeft ? .shadyLeftWalk          : .shadyRightWalk
        case .heavyCosmonaut: return facingLeft ? .heavyCosmonautLeftWalk : .heavyCosmonautRightWalk
        case .astroSniper:    return facingLeft ? .astroSniperLeftWalk    : .astroSniperRightWalk
        // Front-3/4 stomp — same atlas regardless of facing.
        case .miniBoss:       return .miniBossWalk
        case .gorilla, .droneSwarm, .plasmaJelly:
            return nil
        }
    }

    private func placeholderTriangle(radius r: CGFloat) -> SKShapeNode {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: r, y: 0))
        path.addLine(to: CGPoint(x: -r * 0.8, y: r * 0.7))
        path.addLine(to: CGPoint(x: -r * 0.8, y: -r * 0.7))
        path.closeSubpath()
        let shape = SKShapeNode(path: path)
        shape.fillColor = UIColor(red: 0.95, green: 0.30, blue: 0.32, alpha: 1)
        shape.strokeColor = UIColor(red: 1.0, green: 0.75, blue: 0.75, alpha: 1)
        shape.lineWidth = 1.5
        return shape
    }

    final class Enemy {
        enum AnimState { case walk, idle }

        let node: SKNode
        let type: EnemyType
        var hp: Int
        let speed: CGFloat
        let radius: CGFloat
        let facingLeft: Bool
        let visual: SKNode
        var attackCooldown: TimeInterval
        var animState: AnimState

        init(node: SKNode, type: EnemyType, hp: Int, speed: CGFloat,
             radius: CGFloat, facingLeft: Bool, visual: SKNode,
             attackCooldown: TimeInterval) {
            self.node = node
            self.type = type
            self.hp = hp
            self.speed = speed
            self.radius = radius
            self.facingLeft = facingLeft
            self.visual = visual
            self.attackCooldown = attackCooldown
            self.animState = .walk
        }
    }
}
