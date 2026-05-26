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

    // Pause menu node names — hit-tested in GameScene.touchesBegan to drive
    // the pause-menu state machine.
    static let pauseMenuResumeNodeName   = "pauseMenuResume"
    static let pauseMenuSettingsNodeName = "pauseMenuSettings"
    static let pauseMenuQuitNodeName     = "pauseMenuQuit"

    // Settings menu node names.
    static let settingsMusicTrackNodeName    = "settingsMusicTrack"
    static let settingsMusicThumbNodeName    = "settingsMusicThumb"
    static let settingsSFXTrackNodeName      = "settingsSFXTrack"
    static let settingsSFXThumbNodeName      = "settingsSFXThumb"
    static let settingsHapticsOnNodeName     = "settingsHapticsOn"
    static let settingsHapticsOffNodeName    = "settingsHapticsOff"
    static let settingsJoystickLeftNodeName  = "settingsJoystickLeft"
    static let settingsJoystickRightNodeName = "settingsJoystickRight"
    static let settingsExplorationEnterNodeName = "settingsExplorationEnter"
    static let settingsBackNodeName          = "settingsBack"

    // Dim background names — tapping outside any labeled control on the
    // overlays counts as a dismiss (Resume for pause, Back for settings).
    static let pauseMenuDimNodeName    = "pauseMenuDim"
    static let settingsMenuDimNodeName = "settingsMenuDim"

    private static let maxLivesIconSlot = 5  // pre-allocate banana hearts up to this count
    private static let sliderTrackWidth: CGFloat = 200
    private static let sliderTrackHeight: CGFloat = 6
    private static let sliderThumbRadius: CGFloat = 11

    private weak var parent: SKNode?
    private var viewSize: CGSize
    private let root = SKNode()
    private let scoreLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let bestLabel  = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private let livesLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
    private var livesIcons: [SKSpriteNode] = []
    private var pauseButton: SKSpriteNode?
    private var startPrompt: SKNode?
    private var gameOver: SKNode?
    private var pauseMenu: SKNode?
    private var settingsMenu: SKNode?
    // Cached references so GameScene can move the thumbs / re-style toggles
    // in response to touch interactions without us re-walking the tree.
    private weak var musicThumb: SKShapeNode?
    private weak var sfxThumb: SKShapeNode?
    private weak var musicTrack: SKShapeNode?
    private weak var sfxTrack: SKShapeNode?
    private weak var hapticsOnLabel: SKLabelNode?
    private weak var hapticsOffLabel: SKLabelNode?
    private weak var joystickLeftLabel: SKLabelNode?
    private weak var joystickRightLabel: SKLabelNode?

    init(parent: SKNode, viewSize: CGSize, initialBest: Int) {
        self.parent = parent
        self.viewSize = viewSize
        root.zPosition = 9000
        parent.addChild(root)

        scoreLabel.text = "Score: 0"
        scoreLabel.fontSize = 22
        scoreLabel.fontColor = .white
        scoreLabel.horizontalAlignmentMode = .left
        scoreLabel.position = CGPoint(x: 24, y: viewSize.height - 36)
        root.addChild(scoreLabel)

        bestLabel.text = "Best: \(initialBest)"
        bestLabel.fontSize = 14
        bestLabel.fontColor = UIColor(white: 1, alpha: 0.7)
        bestLabel.horizontalAlignmentMode = .left
        bestLabel.position = CGPoint(x: 24, y: viewSize.height - 56)
        root.addChild(bestLabel)

        installLivesIcons()
        installPauseButton()
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

    private func installLivesIcons() {
        guard let tex = SpriteCatalog.texture(for: .lifeHeart) else {
            // Fallback to the text label if the banana-heart imageset is missing.
            livesLabel.text = "Lives: 3"
            livesLabel.fontSize = 22
            livesLabel.fontColor = .white
            livesLabel.horizontalAlignmentMode = .right
            livesLabel.position = CGPoint(x: viewSize.width - 24, y: viewSize.height - 36)
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
        let rightEdge = viewSize.width - 24
        let topY = viewSize.height - 36

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

    private func installPauseButton() {
        guard let tex = SpriteCatalog.texture(for: .pauseIcon) else { return }
        let target: CGFloat = 48
        let maxDim = max(tex.size().width, tex.size().height)
        let scale = maxDim > 0 ? target / maxDim : 1
        let btn = SKSpriteNode(texture: tex)
        btn.setScale(scale)
        btn.name = Self.pauseButtonNodeName
        // Top-right, mirroring the lives row's right edge. Hidden until the
        // round actually starts — the start prompt and game-over screen don't
        // need an affordance that's a no-op there.
        btn.position = CGPoint(x: viewSize.width - 24 - target / 2,
                               y: viewSize.height - 92)
        btn.zPosition = 9100
        btn.isHidden = true
        // SpriteKit only surfaces touchable nodes through accessibility when
        // they expose a name. The `name` above is already what XCUITest uses
        // for hit-testing — see GameScene.touchesBegan.
        root.addChild(btn)
        pauseButton = btn
    }

    /// Toggle the pause button's visibility. GameScene drives this based on
    /// the run lifecycle so the affordance only appears when it does work.
    func setPauseButtonVisible(_ visible: Bool) {
        pauseButton?.isHidden = !visible
    }

    // MARK: - Start prompt

    func showStartPrompt() {
        guard let parent, startPrompt == nil else { return }
        let card = SKNode()
        card.zPosition = 9100

        // Light dim behind the title so the prompt reads as an overlay over
        // the world, consistent with the pause and game-over screens. Lighter
        // (0.3) than pause (0.55) so the cosmos behind the title still feels
        // alive — it's an invitation, not a freeze.
        let dim = SKShapeNode(rect: CGRect(origin: .zero, size: viewSize))
        dim.fillColor = UIColor(white: 0, alpha: 0.3)
        dim.strokeColor = .clear
        card.addChild(dim)

        if let tex = SpriteCatalog.texture(for: .title) {
            // Sprite-based title. Cap to ~40% of the shorter scene dim so it
            // doesn't dwarf the subtitle and tap prompt in either orientation.
            let cap: CGFloat = min(viewSize.width, viewSize.height) * 0.55
            let s = SKSpriteNode(texture: tex)
            let widthScale = tex.size().width > 0 ? cap * 2 / tex.size().width : 1
            let heightScale = tex.size().height > 0 ? cap / tex.size().height : 1
            s.setScale(min(widthScale, heightScale))
            s.position = CGPoint(x: viewSize.width / 2, y: viewSize.height / 2 + 80)
            s.isAccessibilityElement = true
            s.accessibilityLabel = "GALAXY MONKEY"
            card.addChild(s)
        } else {
            let title = SKLabelNode(fontNamed: "AvenirNext-Heavy")
            title.text = "GALAXY MONKEY"
            title.fontSize = 40
            title.fontColor = UIColor(red: 1.0, green: 0.85, blue: 0.30, alpha: 1)
            title.position = CGPoint(x: viewSize.width / 2, y: viewSize.height / 2 + 30)
            title.isAccessibilityElement = true
            title.accessibilityLabel = "GALAXY MONKEY"
            card.addChild(title)
        }

        let sub = SKLabelNode(fontNamed: "AvenirNext-Medium")
        sub.text = "Left stick to move · Right stick to aim and fire"
        sub.fontSize = 16
        sub.fontColor = UIColor(white: 1, alpha: 0.85)
        sub.position = CGPoint(x: viewSize.width / 2, y: viewSize.height / 2 - 30)
        sub.isAccessibilityElement = true
        sub.accessibilityLabel = "Left stick to move · Right stick to aim and fire"
        card.addChild(sub)

        let tap = SKLabelNode(fontNamed: "AvenirNext-Bold")
        tap.text = "Tap to start"
        tap.fontSize = 22
        tap.fontColor = .white
        tap.position = CGPoint(x: viewSize.width / 2, y: viewSize.height / 2 - 70)
        tap.name = "Tap to start"
        tap.isAccessibilityElement = true
        tap.accessibilityLabel = "Tap to start"
        let pulse = SKAction.sequence([
            SKAction.fadeAlpha(to: 0.5, duration: 0.7),
            SKAction.fadeAlpha(to: 1.0, duration: 0.7),
        ])
        tap.run(SKAction.repeatForever(pulse))
        card.addChild(tap)

        parent.addChild(card)
        startPrompt = card
    }

    func dismissStartPrompt() {
        startPrompt?.removeFromParent()
        startPrompt = nil
    }

    // MARK: - Game over

    func showGameOver(score: Int, best: Int) {
        guard let parent, gameOver == nil else { return }
        let card = SKNode()
        card.zPosition = 9200

        let dim = SKShapeNode(rect: CGRect(origin: .zero, size: viewSize))
        dim.fillColor = UIColor(white: 0, alpha: 0.55)
        dim.strokeColor = .clear
        card.addChild(dim)

        let title = SKLabelNode(fontNamed: "AvenirNext-Heavy")
        title.text = "GAME OVER"
        title.fontSize = 42
        title.fontColor = UIColor(red: 1.0, green: 0.40, blue: 0.40, alpha: 1)
        title.position = CGPoint(x: viewSize.width / 2, y: viewSize.height / 2 + 40)
        card.addChild(title)

        let res = SKLabelNode(fontNamed: "AvenirNext-Medium")
        res.text = "Score \(score) · Best \(max(score, best))"
        res.fontSize = 18
        res.fontColor = .white
        res.position = CGPoint(x: viewSize.width / 2, y: viewSize.height / 2)
        card.addChild(res)

        let tap = SKLabelNode(fontNamed: "AvenirNext-Bold")
        tap.text = "Tap to play again"
        tap.fontSize = 22
        tap.fontColor = .white
        tap.position = CGPoint(x: viewSize.width / 2, y: viewSize.height / 2 - 40)
        tap.name = "Tap to play again"
        tap.isAccessibilityElement = true
        tap.accessibilityLabel = "Tap to play again"
        let pulse = SKAction.sequence([
            SKAction.fadeAlpha(to: 0.5, duration: 0.7),
            SKAction.fadeAlpha(to: 1.0, duration: 0.7),
        ])
        tap.run(SKAction.repeatForever(pulse))
        card.addChild(tap)

        parent.addChild(card)
        gameOver = card
    }

    func dismissGameOver() {
        gameOver?.removeFromParent()
        gameOver = nil
    }

    // MARK: - Pause menu

    func showPauseMenu() {
        guard let parent, pauseMenu == nil else { return }
        let card = SKNode()
        card.zPosition = 9300

        let dim = SKShapeNode(rect: CGRect(origin: .zero, size: viewSize))
        dim.fillColor = UIColor(white: 0, alpha: 0.55)
        dim.strokeColor = .clear
        dim.name = Self.pauseMenuDimNodeName
        card.addChild(dim)

        let title = SKLabelNode(fontNamed: "AvenirNext-Heavy")
        title.text = "PAUSED"
        title.fontSize = 42
        title.fontColor = .white
        title.position = CGPoint(x: viewSize.width / 2, y: viewSize.height / 2 + 80)
        title.isAccessibilityElement = true
        title.accessibilityLabel = "PAUSED"
        card.addChild(title)

        let pulse = SKAction.repeatForever(SKAction.sequence([
            SKAction.fadeAlpha(to: 0.55, duration: 0.7),
            SKAction.fadeAlpha(to: 1.0, duration: 0.7),
        ]))

        // Resume is the primary CTA. Larger fontSize and a slow pulse pull
        // the eye first; Settings + Quit stay static at 28pt. Same hierarchy
        // recipe as the start prompt's "Tap to start" — white + pulse — so
        // the two overlays share a CTA pattern without sharing a title style.
        let resume = pauseMenuLabel(text: "Resume",
                                    name: Self.pauseMenuResumeNodeName,
                                    y: viewSize.height / 2 + 10,
                                    fontSize: 32)
        resume.run(pulse)
        card.addChild(resume)

        let settingsRow = pauseMenuLabel(text: "Settings",
                                          name: Self.pauseMenuSettingsNodeName,
                                          y: viewSize.height / 2 - 40)
        card.addChild(settingsRow)

        // Quit is the destructive option — sized down and muted so accidental
        // taps are harder. The eye should still land on Resume first.
        let quit = pauseMenuLabel(text: "Quit to Title",
                                  name: Self.pauseMenuQuitNodeName,
                                  y: viewSize.height / 2 - 90,
                                  fontSize: 22,
                                  color: UIColor(white: 1, alpha: 0.55))
        card.addChild(quit)

        parent.addChild(card)
        pauseMenu = card
    }

    func dismissPauseMenu() {
        pauseMenu?.removeFromParent()
        pauseMenu = nil
    }

    private func pauseMenuLabel(text: String,
                                name: String,
                                y: CGFloat,
                                fontSize: CGFloat = 28,
                                color: UIColor = .white) -> SKLabelNode {
        let label = SKLabelNode(fontNamed: "AvenirNext-Bold")
        label.text = text
        label.fontSize = fontSize
        label.fontColor = color
        label.position = CGPoint(x: viewSize.width / 2, y: y)
        label.name = name
        label.isAccessibilityElement = true
        label.accessibilityLabel = text
        return label
    }

    // MARK: - Settings menu

    func showSettingsMenu(store: SettingsStore) {
        guard let parent, settingsMenu == nil else { return }
        let card = SKNode()
        card.zPosition = 9400

        let dim = SKShapeNode(rect: CGRect(origin: .zero, size: viewSize))
        dim.fillColor = UIColor(white: 0, alpha: 0.7)
        dim.strokeColor = .clear
        dim.name = Self.settingsMenuDimNodeName
        card.addChild(dim)

        // Rounded panel chrome — the form reads as a discrete dialog rather
        // than labels floating in space. Translucent fill keeps the cosmos
        // visible underneath; thin stroke defines the edge.
        // Height bumped to fit the additional "Exploration Mode" row.
        let panelSize = CGSize(width: 380, height: 460)
        let panel = SKShapeNode(rectOf: panelSize, cornerRadius: 24)
        panel.position = CGPoint(x: viewSize.width / 2, y: viewSize.height / 2 - 30)
        panel.fillColor = UIColor(white: 1, alpha: 0.05)
        panel.strokeColor = UIColor(white: 1, alpha: 0.2)
        panel.lineWidth = 1.5
        card.addChild(panel)

        let title = SKLabelNode(fontNamed: "AvenirNext-Heavy")
        title.text = "SETTINGS"
        title.fontSize = 42
        title.fontColor = .white
        title.position = CGPoint(x: viewSize.width / 2, y: viewSize.height / 2 + 130)
        title.isAccessibilityElement = true
        title.accessibilityLabel = "SETTINGS"
        card.addChild(title)

        let centerX = viewSize.width / 2
        let labelOffset: CGFloat = -150
        let controlOffset: CGFloat = 50

        let musicY = viewSize.height / 2 + 60
        card.addChild(rowLabel(text: "Music",
                               position: CGPoint(x: centerX + labelOffset, y: musicY)))
        let (mTrack, mThumb) = makeSlider(
            trackName: Self.settingsMusicTrackNodeName,
            thumbName: Self.settingsMusicThumbNodeName,
            center: CGPoint(x: centerX + controlOffset, y: musicY),
            value: store.musicVolume)
        card.addChild(mTrack)
        card.addChild(mThumb)
        musicTrack = mTrack
        musicThumb = mThumb

        let sfxY = viewSize.height / 2 + 10
        card.addChild(rowLabel(text: "SFX",
                               position: CGPoint(x: centerX + labelOffset, y: sfxY)))
        let (sTrack, sThumb) = makeSlider(
            trackName: Self.settingsSFXTrackNodeName,
            thumbName: Self.settingsSFXThumbNodeName,
            center: CGPoint(x: centerX + controlOffset, y: sfxY),
            value: store.sfxVolume)
        card.addChild(sTrack)
        card.addChild(sThumb)
        sfxTrack = sTrack
        sfxThumb = sThumb

        let hapticsY = viewSize.height / 2 - 40
        card.addChild(rowLabel(text: "Haptics",
                               position: CGPoint(x: centerX + labelOffset, y: hapticsY)))
        let (onLabel, offLabel) = makeToggle(
            leftText: "On", leftName: Self.settingsHapticsOnNodeName,
            rightText: "Off", rightName: Self.settingsHapticsOffNodeName,
            center: CGPoint(x: centerX + controlOffset, y: hapticsY),
            leftActive: store.hapticsEnabled)
        card.addChild(onLabel)
        card.addChild(offLabel)
        hapticsOnLabel = onLabel
        hapticsOffLabel = offLabel

        let stickY = viewSize.height / 2 - 90
        card.addChild(rowLabel(text: "Move Stick",
                               position: CGPoint(x: centerX + labelOffset, y: stickY)))
        let (leftLabel, rightLabel) = makeToggle(
            leftText: "Left", leftName: Self.settingsJoystickLeftNodeName,
            rightText: "Right", rightName: Self.settingsJoystickRightNodeName,
            center: CGPoint(x: centerX + controlOffset, y: stickY),
            leftActive: store.joystickLeftIsMove)
        card.addChild(leftLabel)
        card.addChild(rightLabel)
        joystickLeftLabel = leftLabel
        joystickRightLabel = rightLabel

        // Hidden-in-plain-sight: a 3D exploration sandbox that pauses the
        // run and drops the player into a Newtonian solar-system flight
        // mode. Understated row — same chrome as the others, no scary
        // wording, no developer-tools framing.
        let explorationY = viewSize.height / 2 - 140
        card.addChild(rowLabel(text: "Exploration Mode",
                               position: CGPoint(x: centerX + labelOffset, y: explorationY)))
        let enterLabel = SKLabelNode(fontNamed: "AvenirNext-Bold")
        enterLabel.text = "Enter →"
        enterLabel.fontSize = 20
        enterLabel.fontColor = UIColor(white: 1, alpha: 0.9)
        enterLabel.horizontalAlignmentMode = .center
        enterLabel.verticalAlignmentMode = .center
        enterLabel.position = CGPoint(x: centerX + controlOffset, y: explorationY)
        enterLabel.name = Self.settingsExplorationEnterNodeName
        enterLabel.isAccessibilityElement = true
        enterLabel.accessibilityLabel = "Enter exploration mode"
        card.addChild(enterLabel)

        let back = pauseMenuLabel(text: "Back",
                                  name: Self.settingsBackNodeName,
                                  y: viewSize.height / 2 - 210)
        card.addChild(back)

        parent.addChild(card)
        settingsMenu = card
    }

    func dismissSettingsMenu() {
        settingsMenu?.removeFromParent()
        settingsMenu = nil
        musicTrack = nil
        musicThumb = nil
        sfxTrack = nil
        sfxThumb = nil
        hapticsOnLabel = nil
        hapticsOffLabel = nil
        joystickLeftLabel = nil
        joystickRightLabel = nil
    }

    /// Returns the on-screen extents of the music slider track in `hudRoot`
    /// space, so GameScene can map a touch x-coord to a [0, 1] value. Returns
    /// nil when the settings menu isn't visible.
    func musicSliderHitRect() -> CGRect? { sliderHitRect(for: musicTrack) }
    func sfxSliderHitRect() -> CGRect? { sliderHitRect(for: sfxTrack) }

    private func sliderHitRect(for track: SKShapeNode?) -> CGRect? {
        guard let track else { return nil }
        let w = Self.sliderTrackWidth
        let h: CGFloat = 44  // generous vertical hit-band beyond the visual track
        return CGRect(x: track.position.x - w / 2,
                      y: track.position.y - h / 2,
                      width: w,
                      height: h)
    }

    func updateMusicSliderThumb(value: Float) {
        guard let track = musicTrack, let thumb = musicThumb else { return }
        thumb.position.x = track.position.x - Self.sliderTrackWidth / 2
            + CGFloat(max(0, min(1, value))) * Self.sliderTrackWidth
    }

    func updateSFXSliderThumb(value: Float) {
        guard let track = sfxTrack, let thumb = sfxThumb else { return }
        thumb.position.x = track.position.x - Self.sliderTrackWidth / 2
            + CGFloat(max(0, min(1, value))) * Self.sliderTrackWidth
    }

    func updateHapticsToggleState(enabled: Bool) {
        hapticsOnLabel?.alpha = enabled ? 1.0 : 0.4
        hapticsOffLabel?.alpha = enabled ? 0.4 : 1.0
    }

    func updateJoystickToggleState(leftIsMove: Bool) {
        joystickLeftLabel?.alpha = leftIsMove ? 1.0 : 0.4
        joystickRightLabel?.alpha = leftIsMove ? 0.4 : 1.0
    }

    private func rowLabel(text: String, position: CGPoint) -> SKLabelNode {
        let label = SKLabelNode(fontNamed: "AvenirNext-Medium")
        label.text = text
        label.fontSize = 20
        label.fontColor = .white
        label.horizontalAlignmentMode = .left
        label.verticalAlignmentMode = .center
        label.position = position
        return label
    }

    private func makeSlider(trackName: String,
                            thumbName: String,
                            center: CGPoint,
                            value: Float) -> (SKShapeNode, SKShapeNode) {
        let track = SKShapeNode(rectOf: CGSize(width: Self.sliderTrackWidth,
                                                height: Self.sliderTrackHeight),
                                cornerRadius: Self.sliderTrackHeight / 2)
        track.fillColor = UIColor(white: 1, alpha: 0.3)
        track.strokeColor = .clear
        track.position = center
        track.name = trackName

        let thumb = SKShapeNode(circleOfRadius: Self.sliderThumbRadius)
        thumb.fillColor = .white
        thumb.strokeColor = .clear
        thumb.name = thumbName
        let v = CGFloat(max(0, min(1, value)))
        thumb.position = CGPoint(
            x: center.x - Self.sliderTrackWidth / 2 + v * Self.sliderTrackWidth,
            y: center.y)
        return (track, thumb)
    }

    private func makeToggle(leftText: String, leftName: String,
                            rightText: String, rightName: String,
                            center: CGPoint,
                            leftActive: Bool) -> (SKLabelNode, SKLabelNode) {
        let left = SKLabelNode(fontNamed: "AvenirNext-Bold")
        left.text = leftText
        left.fontSize = 22
        left.fontColor = .white
        left.alpha = leftActive ? 1.0 : 0.4
        left.horizontalAlignmentMode = .center
        left.verticalAlignmentMode = .center
        left.position = CGPoint(x: center.x - 50, y: center.y)
        left.name = leftName
        left.isAccessibilityElement = true
        left.accessibilityLabel = leftText

        let right = SKLabelNode(fontNamed: "AvenirNext-Bold")
        right.text = rightText
        right.fontSize = 22
        right.fontColor = .white
        right.alpha = leftActive ? 0.4 : 1.0
        right.horizontalAlignmentMode = .center
        right.verticalAlignmentMode = .center
        right.position = CGPoint(x: center.x + 50, y: center.y)
        right.name = rightName
        right.isAccessibilityElement = true
        right.accessibilityLabel = rightText

        return (left, right)
    }
}
