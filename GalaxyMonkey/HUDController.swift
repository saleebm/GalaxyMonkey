//
//  HUDController.swift
//  GalaxyMonkey
//
//  Score, lives, start prompt, pause button, and game-over overlay.
//  Accessibility labels match the UI-test fixture in GalaxyMonkeyUITests
//  so XCUITest can drive the round without touching gameplay code.
//
//  Title / pause / lives use SpriteCook-generated art when present, falling
//  back to text labels when the imagesets are missing — same gating pattern
//  as the rest of the catalog.
//

import SpriteKit
import UIKit

final class HUDController {

    static let pauseButtonNodeName = "pauseButton"
    private static let maxLivesIconSlot = 5  // pre-allocate banana hearts up to this count

    private weak var scene: SKScene?
    private let root = SKNode()
    private let scoreLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let bestLabel  = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let livesLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private var livesIcons: [SKSpriteNode] = []
    private var pauseButton: SKSpriteNode?
    private var startPrompt: SKNode?
    private var gameOver: SKNode?

    init(scene: SKScene, initialBest: Int) {
        self.scene = scene
        root.zPosition = 9000
        scene.addChild(root)

        scoreLabel.text = "Score: 0"
        scoreLabel.fontSize = 22
        scoreLabel.fontColor = .white
        scoreLabel.horizontalAlignmentMode = .left
        scoreLabel.position = CGPoint(x: 24, y: scene.size.height - 36)
        root.addChild(scoreLabel)

        bestLabel.text = "Best: \(initialBest)"
        bestLabel.fontSize = 14
        bestLabel.fontColor = UIColor(white: 1, alpha: 0.7)
        bestLabel.horizontalAlignmentMode = .left
        bestLabel.position = CGPoint(x: 24, y: scene.size.height - 56)
        root.addChild(bestLabel)

        installLivesIcons(scene: scene)
        installPauseButton(scene: scene)
    }

    func setScore(_ s: Int) { scoreLabel.text = "Score: \(s)" }
    func setBest(_ b: Int)  { bestLabel.text  = "Best: \(b)" }

    func setLives(_ l: Int) {
        if livesIcons.isEmpty {
            livesLabel.text = "Lives: \(l)"
        } else {
            for (i, icon) in livesIcons.enumerated() {
                icon.isHidden = i >= l
            }
        }
    }

    // MARK: - Lives icons

    private func installLivesIcons(scene: SKScene) {
        guard let tex = SpriteCatalog.texture(for: .lifeHeart) else {
            // Fallback to the text label if the banana-heart imageset is missing.
            livesLabel.text = "Lives: 3"
            livesLabel.fontSize = 22
            livesLabel.fontColor = .white
            livesLabel.horizontalAlignmentMode = .right
            livesLabel.position = CGPoint(x: scene.size.width - 24, y: scene.size.height - 36)
            root.addChild(livesLabel)
            return
        }

        // Sized so the row fits comfortably in the top-right strip. Scale
        // each icon so its longest side is ~32pt; pack with 4pt gaps.
        let target: CGFloat = 32
        let maxDim = max(tex.size().width, tex.size().height)
        let scale = maxDim > 0 ? target / maxDim : 1
        let iconWidth = tex.size().width * scale
        let gap: CGFloat = 4
        let rightEdge = scene.size.width - 24
        let topY = scene.size.height - 36

        for i in 0..<Self.maxLivesIconSlot {
            let icon = SKSpriteNode(texture: tex)
            icon.setScale(scale)
            // Lay out right-to-left so slot 0 is the rightmost.
            icon.position = CGPoint(
                x: rightEdge - (CGFloat(i) * (iconWidth + gap)) - iconWidth / 2,
                y: topY
            )
            icon.zPosition = 9001
            icon.isHidden = true
            root.addChild(icon)
            livesIcons.append(icon)
        }
    }

    private func installPauseButton(scene: SKScene) {
        guard let tex = SpriteCatalog.texture(for: .pauseIcon) else { return }
        let target: CGFloat = 48
        let maxDim = max(tex.size().width, tex.size().height)
        let scale = maxDim > 0 ? target / maxDim : 1
        let btn = SKSpriteNode(texture: tex)
        btn.setScale(scale)
        btn.name = Self.pauseButtonNodeName
        // Top-left, below the score line.
        btn.position = CGPoint(x: 24 + target / 2, y: scene.size.height - 92)
        btn.zPosition = 9100
        // SpriteKit only surfaces touchable nodes through accessibility when
        // they expose a name. The `name` above is already what XCUITest uses
        // for hit-testing — see GameScene.touchesBegan.
        root.addChild(btn)
        pauseButton = btn
    }

    // MARK: - Start prompt

    func showStartPrompt() {
        guard let scene, startPrompt == nil else { return }
        let card = SKNode()
        card.zPosition = 9100

        if let tex = SpriteCatalog.texture(for: .title) {
            // Sprite-based title. Cap to ~40% of the shorter scene dim so it
            // doesn't dwarf the subtitle and tap prompt in either orientation.
            let cap: CGFloat = min(scene.size.width, scene.size.height) * 0.55
            let s = SKSpriteNode(texture: tex)
            let widthScale = tex.size().width > 0 ? cap * 2 / tex.size().width : 1
            let heightScale = tex.size().height > 0 ? cap / tex.size().height : 1
            s.setScale(min(widthScale, heightScale))
            s.position = CGPoint(x: scene.size.width / 2, y: scene.size.height / 2 + 80)
            s.isAccessibilityElement = true
            s.accessibilityLabel = "GALAXY MONKEY"
            card.addChild(s)
        } else {
            let title = SKLabelNode(fontNamed: "AvenirNext-Heavy")
            title.text = "GALAXY MONKEY"
            title.fontSize = 40
            title.fontColor = UIColor(red: 1.0, green: 0.85, blue: 0.30, alpha: 1)
            title.position = CGPoint(x: scene.size.width / 2, y: scene.size.height / 2 + 30)
            title.isAccessibilityElement = true
            title.accessibilityLabel = "GALAXY MONKEY"
            card.addChild(title)
        }

        let sub = SKLabelNode(fontNamed: "AvenirNext-Medium")
        sub.text = "Left stick to move · Right stick to aim and fire"
        sub.fontSize = 16
        sub.fontColor = UIColor(white: 1, alpha: 0.85)
        sub.position = CGPoint(x: scene.size.width / 2, y: scene.size.height / 2 - 30)
        sub.isAccessibilityElement = true
        sub.accessibilityLabel = "Left stick to move · Right stick to aim and fire"
        card.addChild(sub)

        let tap = SKLabelNode(fontNamed: "AvenirNext-Bold")
        tap.text = "Tap to start"
        tap.fontSize = 22
        tap.fontColor = .white
        tap.position = CGPoint(x: scene.size.width / 2, y: scene.size.height / 2 - 70)
        tap.name = "Tap to start"
        tap.isAccessibilityElement = true
        tap.accessibilityLabel = "Tap to start"
        let pulse = SKAction.sequence([
            SKAction.fadeAlpha(to: 0.5, duration: 0.7),
            SKAction.fadeAlpha(to: 1.0, duration: 0.7),
        ])
        tap.run(SKAction.repeatForever(pulse))
        card.addChild(tap)

        scene.addChild(card)
        startPrompt = card
    }

    func dismissStartPrompt() {
        startPrompt?.removeFromParent()
        startPrompt = nil
    }

    // MARK: - Game over

    func showGameOver(score: Int, best: Int) {
        guard let scene, gameOver == nil else { return }
        let card = SKNode()
        card.zPosition = 9200

        let dim = SKShapeNode(rect: CGRect(origin: .zero, size: scene.size))
        dim.fillColor = UIColor(white: 0, alpha: 0.55)
        dim.strokeColor = .clear
        card.addChild(dim)

        let title = SKLabelNode(fontNamed: "AvenirNext-Heavy")
        title.text = "GAME OVER"
        title.fontSize = 42
        title.fontColor = UIColor(red: 1.0, green: 0.40, blue: 0.40, alpha: 1)
        title.position = CGPoint(x: scene.size.width / 2, y: scene.size.height / 2 + 40)
        card.addChild(title)

        let res = SKLabelNode(fontNamed: "AvenirNext-Medium")
        res.text = "Score \(score) · Best \(max(score, best))"
        res.fontSize = 18
        res.fontColor = .white
        res.position = CGPoint(x: scene.size.width / 2, y: scene.size.height / 2)
        card.addChild(res)

        let tap = SKLabelNode(fontNamed: "AvenirNext-Bold")
        tap.text = "Tap to play again"
        tap.fontSize = 22
        tap.fontColor = .white
        tap.position = CGPoint(x: scene.size.width / 2, y: scene.size.height / 2 - 40)
        tap.name = "Tap to play again"
        tap.isAccessibilityElement = true
        tap.accessibilityLabel = "Tap to play again"
        let pulse = SKAction.sequence([
            SKAction.fadeAlpha(to: 0.5, duration: 0.7),
            SKAction.fadeAlpha(to: 1.0, duration: 0.7),
        ])
        tap.run(SKAction.repeatForever(pulse))
        card.addChild(tap)

        scene.addChild(card)
        gameOver = card
    }

    func dismissGameOver() {
        gameOver?.removeFromParent()
        gameOver = nil
    }
}
