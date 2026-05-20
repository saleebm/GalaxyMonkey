//
//  GameScene.swift
//  GalaxyMonkey
//
//  Orchestrator. Owns subsystems, routes input, drives the frame loop, and
//  resolves contacts. Subsystem ordering inside `update(_:)` is guarded by
//  isStarted/!isGameOver — see PenguinSlide's note on physics-step timing.
//

import SpriteKit
import UIKit

final class GameScene: SKScene, SKPhysicsContactDelegate {

    private var camNode: SKCameraNode!

    private var starfield: Starfield!
    private var player: Player!
    private var enemies: EnemySystem!
    private var bullets: ProjectileSystem!
    private var hud: HUDController!
    private var audio: AudioController!

    private let moveStick = VirtualJoystick(side: .left)
    private let aimStick  = VirtualJoystick(side: .right)
    private let input = GameControllerInput()

    private var lastUpdateTime: TimeInterval = 0
    private var elapsed: TimeInterval = 0
    private var isStarted = false
    private var isGameOver = false
    private var score: Int = 0 {
        didSet { hud?.setScore(score) }
    }

    // MARK: - Lifecycle

    override func didMove(to view: SKView) {
        anchorPoint = .zero
        backgroundColor = UIColor(red: 0.02, green: 0.03, blue: 0.08, alpha: 1)
        physicsWorld.gravity = .zero
        physicsWorld.contactDelegate = self

        // SpriteView's UIView defaults to single-touch; twin-stick needs both fingers.
        view.isMultipleTouchEnabled = true

        let cam = SKCameraNode()
        cam.position = CGPoint(x: size.width / 2, y: size.height / 2)
        addChild(cam)
        camera = cam
        camNode = cam

        // Optional space backdrop sprite; falls back to flat color above.
        if let tex = SpriteCatalog.texture(for: .spaceBackdrop) {
            let bg = SKSpriteNode(texture: tex)
            bg.anchorPoint = .zero
            bg.position = .zero
            bg.size = size
            bg.zPosition = -100
            addChild(bg)
        }

        starfield = Starfield(scene: self)
        player    = Player(scene: self)
        enemies   = EnemySystem(scene: self)
        bullets   = ProjectileSystem(scene: self)
        hud       = HUDController(scene: self, initialBest: bestScore())
        audio     = AudioController(scene: self)
        audio.listenerProvider = { [weak self] in
            self?.player.node.position ?? .zero
        }

        let inset = CGRect(origin: .zero, size: size).insetBy(dx: Tuning.Player.radius,
                                                              dy: Tuning.Player.radius)
        enemies.playerPositionProvider = { [weak self] in
            self?.player.node.position ?? .zero
        }
        enemies.enemyFireRequest = { [weak self] origin, angle, kind in
            guard let self else { return }
            self.audio.play(.enemyShot, at: origin)
            switch kind {
            case .bullet: self.bullets.fireEnemyBullet(from: origin, angle: angle)
            case .bomb:   self.bullets.fireBomb(from: origin, angle: angle)
            }
        }
        enemies.updateBounds(CGRect(origin: .zero, size: size))
        bullets.updateBounds(CGRect(origin: .zero, size: size))
        _ = inset

        // Joysticks live in scene space so the touch coordinates passed to
        // VirtualJoystick (location(in: scene)) match their parent's space.
        addChild(moveStick)
        addChild(aimStick)

        player.onLivesChanged = { [weak self] l in self?.hud.setLives(l) }
        hud.setLives(player.lives)
        hud.showStartPrompt()

        // Audio engine init is deferred until after first user interaction.
        // The iOS Simulator's coreaudio host frequently RPC-times-out when
        // an SKAudioNode is added during didMove(to:) before the scene has
        // ticked at least once — symptom is a SIGABRT inside AURemoteIO.
        // Real devices don't hit this; we still start audio there.

        #if DEBUG
        installDebugForceGameOver()
        #endif
    }

    override func willMove(from view: SKView) {
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - Input

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        #if DEBUG
        if let t = touches.first,
           nodes(at: t.location(in: self)).contains(where: { $0.name == Self.debugForceGameOverLabel }) {
            if !isStarted {
                isStarted = true
                lastUpdateTime = 0
                hud.dismissStartPrompt()
            }
            triggerGameOver()
            return
        }
        #endif

        // Pause toggle. Allowed any time after the game has started and
        // before game-over so the user can step away mid-run.
        if isStarted, !isGameOver, let t = touches.first,
           nodes(at: t.location(in: self)).contains(where: { $0.name == HUDController.pauseButtonNodeName }) {
            isPaused = !isPaused
            return
        }

        if !isStarted {
            isStarted = true
            lastUpdateTime = 0
            hud.dismissStartPrompt()
            audio.startMusic()
            return
        }
        if isGameOver, let t = touches.first {
            // Tap anywhere on the game-over overlay restarts.
            let onTapLabel = nodes(at: t.location(in: self))
                .contains(where: { $0.name == "Tap to play again" })
            if onTapLabel || touches.count > 0 {
                restart()
                return
            }
        }
        // Touch sticks are skipped while a physical controller drives input.
        if !input.hasPhysicalController {
            moveStick.touchesBegan(touches, in: self)
            aimStick.touchesBegan(touches, in: self)
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        moveStick.touchesMoved(touches, in: self)
        aimStick.touchesMoved(touches, in: self)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        moveStick.touchesEnded(touches, in: self)
        aimStick.touchesEnded(touches, in: self)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        moveStick.touchesEnded(touches, in: self)
        aimStick.touchesEnded(touches, in: self)
    }

    // MARK: - Update

    override func update(_ currentTime: TimeInterval) {
        let dt: TimeInterval = lastUpdateTime == 0 ? 0 : (currentTime - lastUpdateTime)
        lastUpdateTime = currentTime

        guard isStarted, !isGameOver else { return }

        elapsed += dt

        let bounds = CGRect(origin: .zero, size: size).insetBy(dx: Tuning.Player.radius,
                                                                dy: Tuning.Player.radius)
        // Physical controller takes precedence over the on-screen sticks
        // when one is paired; otherwise read the dynamic touch joysticks.
        let useMFi = input.hasPhysicalController
        let moveVec = useMFi ? input.moveVector : moveStick.vector
        let aimVec  = useMFi ? input.aimVector  : aimStick.vector
        player.update(dt: dt,
                      moveStick: moveVec,
                      aimStick: aimVec,
                      bounds: bounds)

        if player.isFiring {
            let fired = bullets.tryFirePlayer(from: player.node.position,
                                              angle: player.aimAngle,
                                              now: currentTime)
            if fired {
                audio.play(.playerShot,
                           at: player.node.position,
                           baseVolume: Tuning.Audio.playerShotVolume)
            }
        }
        bullets.update(dt: dt)
        enemies.update(dt: dt)
        starfield.update(dt: dt, playerVelocity: player.velocity)
    }

    // MARK: - Contact

    func didBegin(_ contact: SKPhysicsContact) {
        let a = contact.bodyA
        let b = contact.bodyB
        let mask = a.categoryBitMask | b.categoryBitMask

        if mask == (Category.bullet | Category.enemy) {
            let bulletNode: SKNode? = a.categoryBitMask == Category.bullet ? a.node : b.node
            let enemyNode:  SKNode? = a.categoryBitMask == Category.enemy  ? a.node : b.node
            if let bn = bulletNode { bullets.recycle(node: bn) }
            if let en = enemyNode {
                switch enemies.applyHit(node: en) {
                case .stillAlive:
                    // No score, no explosion — just absorb the hit. A future
                    // pass could flash the sprite white here.
                    break
                case .killed(let s, let pos, _):
                    score += s
                    audio.play(.explosion, at: pos)
                    spawnExplosion(at: pos)
                    let gen = UIImpactFeedbackGenerator(style: .medium)
                    gen.impactOccurred()
                }
            }
            return
        }

        if mask == (Category.player | Category.enemy) {
            let enemyNode: SKNode? = a.categoryBitMask == Category.enemy ? a.node : b.node
            if player.tryTakeHit() {
                if let en = enemyNode { _ = enemies.killByNode(en) }
                audio.play(.playerHit, at: player.node.position)
                if !player.isAlive { triggerGameOver() }
            }
            return
        }
    }

    // MARK: - Game flow

    private func triggerGameOver() {
        guard !isGameOver else { return }
        isGameOver = true

        let best = bestScore()
        if score > best {
            UserDefaults.standard.set(score, forKey: "best_score")
        }

        audio.play(.gameOver)
        let gen = UINotificationFeedbackGenerator()
        gen.notificationOccurred(.error)

        hud.showGameOver(score: score, best: max(score, best))
        moveStick.cancelAllTouches()
        aimStick.cancelAllTouches()
        physicsWorld.speed = 0
    }

    private func restart() {
        hud.dismissGameOver()
        bullets.clearAll()
        enemies.reset()
        physicsWorld.speed = 1
        elapsed = 0
        score = 0
        isGameOver = false
        lastUpdateTime = 0
        player.reset()
    }

    private func bestScore() -> Int {
        UserDefaults.standard.integer(forKey: "best_score")
    }

    /// Drops a one-shot explosion sprite at the given world position. If the
    /// ExplosionAnim atlas is missing, this is a no-op — audio still plays.
    private func spawnExplosion(at position: CGPoint) {
        guard let action = AnimationCatalog.oneShot(.explosion, frameDuration: 1.0 / 24) else { return }
        let frames = AnimationCatalog.textures(for: .explosion)
        guard let first = frames.first else { return }
        let node = SKSpriteNode(texture: first)
        // Match the rough kill-site radius (≈ 2× Enemy.radius) so the blast
        // visually contains the enemy that just died.
        let target: CGFloat = Tuning.Enemy.radius * 2.4
        let maxDim = max(first.size().width, first.size().height)
        if maxDim > 0 { node.setScale(target / maxDim) }
        node.position = position
        node.zPosition = 50
        addChild(node)
        node.run(action)
    }

    #if DEBUG
    private static let debugForceGameOverLabel = "debugForceGameOver"

    private func installDebugForceGameOver() {
        // SKLabelNode is the only node type SpriteKit surfaces to XCUITest
        // through automatic accessibility — see PenguinSlide's same hook.
        let node = SKLabelNode(text: Self.debugForceGameOverLabel)
        node.name = Self.debugForceGameOverLabel
        node.fontSize = 10
        node.fontColor = UIColor(red: 1, green: 0, blue: 0, alpha: 0.55)
        node.horizontalAlignmentMode = .left
        node.verticalAlignmentMode = .top
        node.position = CGPoint(x: 4, y: size.height - 4)
        node.zPosition = 10_000
        addChild(node)
    }
    #endif
}
