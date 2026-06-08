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
    static let settingsBackNodeName          = "settingsBack"

    // Dim backdrops behind each overlay.
    static let pauseMenuDimNodeName    = "pauseMenuDim"
    static let settingsMenuDimNodeName = "settingsMenuDim"

    private static let maxLivesIconSlot = 5  // pre-allocate banana hearts up to this count
    private static let sliderTrackWidth: CGFloat = 200
    private static let sliderTrackHeight: CGFloat = 6
    private static let sliderThumbRadius: CGFloat = 11
    // Generous tap boundaries so near-misses still land on the right control.
    private static let togglePillSize = CGSize(width: 80, height: 42)
    private static let backPillSize   = CGSize(width: 160, height: 46)
    private static let pauseHitSize   = CGSize(width: 280, height: 48)

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
    private weak var hapticsOnPill: SKShapeNode?
    private weak var hapticsOffPill: SKShapeNode?
    // Scrollable settings viewport. `settingsContent` holds the control rows
    // and pans vertically inside `settingsViewport` (an SKCropNode). The
    // title and Back button are pinned outside it. Heights drive scroll
    // clamping; width/height drive the hit-test bounds check in GameScene.
    private weak var settingsContent: SKNode?
    private weak var settingsViewport: SKCropNode?
    private var settingsContentHeight: CGFloat = 0
    private var settingsViewportWidth: CGFloat = 0
    private var settingsViewportHeight: CGFloat = 0
    // `settingsCard` and `settingsBackNode` drive the pinned-control hit tests
    // and the panel-bounds check (taps off a control but inside the panel are
    // inert; only taps outside the panel return to pause).
    private weak var settingsCard: SKNode?
    private weak var settingsBackNode: SKLabelNode?
    private var settingsPanelRect: CGRect = .zero   // card-local
    // Pause buttons, hit-tested with generous rects so empty-space taps no
    // longer resume — only the buttons themselves act.
    private weak var pauseResumeNode: SKLabelNode?
    private weak var pauseSettingsNode: SKLabelNode?
    private weak var pauseQuitNode: SKLabelNode?

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

        // Resume is the primary CTA: bigger and pulsing. Settings sits below it,
        // Quit is muted so accidental taps land on it least.
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

        let quit = pauseMenuLabel(text: "Quit to Title",
                                  name: Self.pauseMenuQuitNodeName,
                                  y: viewSize.height / 2 - 90,
                                  fontSize: 22,
                                  color: UIColor(white: 1, alpha: 0.55))
        card.addChild(quit)

        pauseResumeNode = resume
        pauseSettingsNode = settingsRow
        pauseQuitNode = quit

        parent.addChild(card)
        pauseMenu = card
    }

    /// Returns the node name of the pause button under `scenePoint`, tested
    /// against generous rects so near-misses still register. Empty space
    /// returns nil — the run only resumes via the Resume button.
    func pauseMenuHit(scenePoint: CGPoint, in scene: SKScene) -> String? {
        for node in [pauseResumeNode, pauseSettingsNode, pauseQuitNode] {
            if let node, let name = node.name,
               rectHit(node, size: Self.pauseHitSize, scenePoint: scenePoint, in: scene) {
                return name
            }
        }
        return nil
    }

    /// Whether `scenePoint` lands within a rect of `size` centered on `node`,
    /// evaluated in the node's parent space so it tracks any transform.
    private func rectHit(_ node: SKNode, size: CGSize, scenePoint: CGPoint, in scene: SKScene) -> Bool {
        guard let parent = node.parent else { return false }
        let p = parent.convert(scenePoint, from: scene)
        return abs(p.x - node.position.x) <= size.width / 2
            && abs(p.y - node.position.y) <= size.height / 2
    }

    func dismissPauseMenu() {
        pauseMenu?.removeFromParent()
        pauseMenu = nil
        pauseResumeNode = nil
        pauseSettingsNode = nil
        pauseQuitNode = nil
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
        settingsCard = card

        let dim = SKShapeNode(rect: CGRect(origin: .zero, size: viewSize))
        dim.fillColor = UIColor(white: 0, alpha: 0.7)
        dim.strokeColor = .clear
        dim.name = Self.settingsMenuDimNodeName
        card.addChild(dim)

        let screenCenter = CGPoint(x: viewSize.width / 2, y: viewSize.height / 2)

        // Panel sized to the available (landscape) height so the dialog never
        // overflows the short screen edge; capped so iPad doesn't get a giant
        // card. The title and Back button are pinned to the panel's top/bottom
        // bands; the control rows live in a scrollable viewport between them,
        // so Back is always reachable regardless of screen height.
        let panelW: CGFloat = 380
        let panelH = min(viewSize.height - 24, 460)
        let titleReserve: CGFloat = 64
        let backReserve: CGFloat = 56

        let panel = SKShapeNode(rectOf: CGSize(width: panelW, height: panelH), cornerRadius: 24)
        panel.position = screenCenter
        panel.fillColor = UIColor(white: 1, alpha: 0.05)
        panel.strokeColor = UIColor(white: 1, alpha: 0.2)
        panel.lineWidth = 1.5
        card.addChild(panel)
        settingsPanelRect = CGRect(x: screenCenter.x - panelW / 2,
                                   y: screenCenter.y - panelH / 2,
                                   width: panelW, height: panelH)

        let title = SKLabelNode(fontNamed: "AvenirNext-Heavy")
        title.text = "SETTINGS"
        title.fontSize = 36
        title.fontColor = .white
        title.verticalAlignmentMode = .center
        title.position = CGPoint(x: screenCenter.x,
                                 y: screenCenter.y + (panelH - titleReserve) / 2)
        title.isAccessibilityElement = true
        title.accessibilityLabel = "SETTINGS"
        card.addChild(title)

        let back = pauseMenuLabel(text: "Back",
                                  name: Self.settingsBackNodeName,
                                  y: screenCenter.y - (panelH - backReserve) / 2)
        back.verticalAlignmentMode = .center   // center text within its pill
        let backPill = addButtonBacking(to: back, size: Self.backPillSize)
        backPill.fillColor = UIColor(white: 1, alpha: 0.06)
        backPill.strokeColor = UIColor(white: 1, alpha: 0.5)
        card.addChild(back)
        settingsBackNode = back

        // Scrollable viewport. SKCropNode clips *rendering* to the mask but
        // NOT hit-testing, so GameScene bounds-checks taps via
        // `settingsViewportContains` before honoring a scrolled-away control.
        let viewportW = panelW - 40
        let viewportH = panelH - titleReserve - backReserve
        let viewport = SKCropNode()
        viewport.position = CGPoint(x: screenCenter.x,
                                    y: screenCenter.y + (backReserve - titleReserve) / 2)
        let mask = SKShapeNode(rectOf: CGSize(width: viewportW, height: viewportH))
        mask.fillColor = .white
        mask.strokeColor = .clear
        viewport.maskNode = mask
        card.addChild(viewport)

        // Control rows live in content-local space: the origin is the viewport
        // centre when content.position == .zero. x offsets mirror the proven
        // pre-scroll layout (label 150pt left of centre, control 50pt right).
        let content = SKNode()
        viewport.addChild(content)

        let rowCount = 3   // Music, SFX, Haptics
        let rowSpacing: CGFloat = 52
        let labelX: CGFloat = -150
        let ctrlX: CGFloat = 50
        let topY = viewportH / 2 - rowSpacing / 2
        func rowY(_ i: Int) -> CGFloat { topY - CGFloat(i) * rowSpacing }

        content.addChild(rowLabel(text: "Music",
                                  position: CGPoint(x: labelX, y: rowY(0))))
        let (mTrack, mThumb) = makeSlider(
            trackName: Self.settingsMusicTrackNodeName,
            thumbName: Self.settingsMusicThumbNodeName,
            center: CGPoint(x: ctrlX, y: rowY(0)),
            value: store.musicVolume)
        content.addChild(mTrack)
        content.addChild(mThumb)
        musicTrack = mTrack
        musicThumb = mThumb

        content.addChild(rowLabel(text: "SFX",
                                  position: CGPoint(x: labelX, y: rowY(1))))
        let (sTrack, sThumb) = makeSlider(
            trackName: Self.settingsSFXTrackNodeName,
            thumbName: Self.settingsSFXThumbNodeName,
            center: CGPoint(x: ctrlX, y: rowY(1)),
            value: store.sfxVolume)
        content.addChild(sTrack)
        content.addChild(sThumb)
        sfxTrack = sTrack
        sfxThumb = sThumb

        content.addChild(rowLabel(text: "Haptics",
                                  position: CGPoint(x: labelX, y: rowY(2))))
        let toggle = makeToggle(
            leftText: "On", leftName: Self.settingsHapticsOnNodeName,
            rightText: "Off", rightName: Self.settingsHapticsOffNodeName,
            center: CGPoint(x: ctrlX, y: rowY(2)),
            leftActive: store.hapticsEnabled)
        content.addChild(toggle.left)
        content.addChild(toggle.right)
        hapticsOnLabel = toggle.left
        hapticsOffLabel = toggle.right
        hapticsOnPill = toggle.leftPill
        hapticsOffPill = toggle.rightPill

        settingsContent = content
        settingsViewport = viewport
        settingsViewportWidth = viewportW
        settingsViewportHeight = viewportH
        // One rowSpacing slot per row; when ≤ the viewport height the content
        // fits and scrolling clamps to a no-op.
        settingsContentHeight = CGFloat(rowCount) * rowSpacing

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
        hapticsOnPill = nil
        hapticsOffPill = nil
        settingsContent = nil
        settingsViewport = nil
        settingsCard = nil
        settingsBackNode = nil
        settingsPanelRect = .zero
        settingsContentHeight = 0
        settingsViewportWidth = 0
        settingsViewportHeight = 0
    }

    /// Generous hit test for the pinned Back button.
    func settingsBackHit(scenePoint: CGPoint, in scene: SKScene) -> Bool {
        guard let back = settingsBackNode else { return false }
        return rectHit(back, size: Self.backPillSize, scenePoint: scenePoint, in: scene)
    }

    /// Which haptics option a tap lands on: true = On, false = Off, nil = neither.
    func settingsHapticsHit(scenePoint: CGPoint, in scene: SKScene) -> Bool? {
        if let on = hapticsOnLabel, rectHit(on, size: Self.togglePillSize, scenePoint: scenePoint, in: scene) {
            return true
        }
        if let off = hapticsOffLabel, rectHit(off, size: Self.togglePillSize, scenePoint: scenePoint, in: scene) {
            return false
        }
        return nil
    }

    /// Whether the point is inside the dialog panel. Taps off a control but
    /// inside the panel are inert; only taps outside it return to pause.
    func settingsPanelContains(scenePoint: CGPoint, in scene: SKScene) -> Bool {
        guard let card = settingsCard else { return false }
        return settingsPanelRect.contains(card.convert(scenePoint, from: scene))
    }

    /// The node holding the scrollable settings rows. GameScene uses it to
    /// convert touches into content-local space (which absorbs the pan
    /// offset) for slider hit-testing. Nil when settings isn't visible.
    func settingsContentNode() -> SKNode? { settingsContent }

    /// Pans the settings content vertically, clamped so it can't scroll past
    /// its extents. `dy` is the touch's scene-space y-delta since the last
    /// move; content follows the finger (drag up reveals lower rows). When
    /// the content fits the viewport this is a no-op (maxScroll == 0).
    func panSettingsContent(byDeltaY dy: CGFloat) {
        guard let content = settingsContent else { return }
        let maxScroll = max(0, settingsContentHeight - settingsViewportHeight)
        content.position.y = max(0, min(content.position.y + dy, maxScroll))
    }

    /// Whether a scene-space point falls inside the scrollable viewport.
    /// Required because SKCropNode clips rendering but not `nodes(at:)`, so a
    /// row scrolled out of view would otherwise still register taps.
    func settingsViewportContains(scenePoint: CGPoint, in scene: SKScene) -> Bool {
        guard let vp = settingsViewport else { return false }
        let p = vp.convert(scenePoint, from: scene)
        return abs(p.x) <= settingsViewportWidth / 2
            && abs(p.y) <= settingsViewportHeight / 2
    }

    /// Slider track extents in *content-local* space (the space GameScene maps
    /// touches into via `settingsContentNode`). Building from `track.position`
    /// keeps the rect correct under any scroll offset. Nil when not visible.
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
        styleToggleOption(label: hapticsOnLabel, pill: hapticsOnPill, active: enabled)
        styleToggleOption(label: hapticsOffLabel, pill: hapticsOffPill, active: !enabled)
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
                            leftActive: Bool)
        -> (left: SKLabelNode, right: SKLabelNode, leftPill: SKShapeNode, rightPill: SKShapeNode) {
        let left = toggleLabel(text: leftText, name: leftName,
                               position: CGPoint(x: center.x - 46, y: center.y))
        let right = toggleLabel(text: rightText, name: rightName,
                                position: CGPoint(x: center.x + 46, y: center.y))
        let leftPill = addButtonBacking(to: left, size: Self.togglePillSize)
        let rightPill = addButtonBacking(to: right, size: Self.togglePillSize)
        styleToggleOption(label: left, pill: leftPill, active: leftActive)
        styleToggleOption(label: right, pill: rightPill, active: !leftActive)
        return (left, right, leftPill, rightPill)
    }

    private func toggleLabel(text: String, name: String, position: CGPoint) -> SKLabelNode {
        let label = SKLabelNode(fontNamed: "AvenirNext-Bold")
        label.text = text
        label.fontSize = 20
        label.horizontalAlignmentMode = .center
        label.verticalAlignmentMode = .center
        label.position = position
        label.name = name
        label.isAccessibilityElement = true
        label.accessibilityLabel = text
        return label
    }

    /// Selected = solid fill with dark text; unselected = outline with light
    /// text. The fill/outline contrast reads as the active choice.
    private func styleToggleOption(label: SKLabelNode?, pill: SKShapeNode?, active: Bool) {
        guard let label, let pill else { return }
        if active {
            pill.fillColor = UIColor(white: 0.96, alpha: 1)
            pill.strokeColor = .clear
            label.fontColor = UIColor(red: 0.05, green: 0.06, blue: 0.12, alpha: 1)
        } else {
            pill.fillColor = UIColor(white: 1, alpha: 0.06)
            pill.strokeColor = UIColor(white: 1, alpha: 0.5)
            label.fontColor = UIColor(white: 1, alpha: 0.85)
        }
    }

    /// Adds a rounded "button" backing behind a label so the tap area is
    /// visible. Drawn behind the text; the caller styles fill/stroke.
    @discardableResult
    private func addButtonBacking(to label: SKLabelNode, size: CGSize) -> SKShapeNode {
        let pill = SKShapeNode(rectOf: size, cornerRadius: 11)
        pill.lineWidth = 1.5
        pill.isAntialiased = true
        pill.zPosition = -1
        label.addChild(pill)
        return pill
    }
}
