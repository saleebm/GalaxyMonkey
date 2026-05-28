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
    /// Camera-attached container for all screen-locked UI. Its position is
    /// `(-size.width/2, -size.height/2)` in camera-local space so that any
    /// child positioned in scene-space coords (e.g., `(24, size.height - 36)`)
    /// lands at the correct screen corner regardless of where the camera is
    /// in the world.
    private var hudRoot: SKNode!

    private var starfield: Starfield!
    private var planetField: PlanetField!
    private var player: Player!
    private var enemies: EnemySystem!
    private var bullets: ProjectileSystem!
    private var pickups: PickupSystem!
    private var hud: HUDController!
    private var audio: AudioController!
    private var vfx: VFXPool!
    private var haptics: HapticsController!
    private let settings = SettingsStore()

    private let moveStick = VirtualJoystick(side: .left)
    private let aimStick  = VirtualJoystick(side: .right)
    private let input = GameControllerInput()

    private var lastUpdateTime: TimeInterval = 0
    private var lastCameraPosition: CGPoint = .zero
    private var elapsed: TimeInterval = 0
    private var isStarted = false
    private var isGameOver = false
    private var isInPauseMenu = false
    private var isInSettings = false
    // Which settings slider is currently being dragged (nil = no drag in
    // progress). Reset on touchesEnded/Cancelled.
    private enum SliderDrag { case music, sfx }
    private var activeSliderDrag: SliderDrag?
    // Last SFX volume value at which we played a preview tap during a slider
    // drag. Used to throttle previews: only re-emit when the value has moved
    // by ≥ 0.05 since the last tap, so dragging doesn't spawn a hail of
    // overlapping nodes. Reset on touchesEnded so the next grab plays
    // immediately.
    private var lastSFXPreviewValue: Float = -1
    // Settings overlay gesture disambiguation. A touch starts `.undecided`;
    // landing on a slider track commits to `.slider` immediately, crossing the
    // vertical threshold commits to `.scroll`, and a release with no commit is
    // a `.tap` (dispatched in touchesEnded so a vertical drag over a control
    // scrolls instead of clicking).
    private enum SettingsTouch { case undecided, slider, scroll }
    private var settingsTouchPhase: SettingsTouch = .undecided
    private var settingsTouchStartScene: CGPoint = .zero
    private var settingsScrollLastY: CGFloat = 0
    private static let settingsScrollThreshold: CGFloat = 10
    // True only while a touch sequence that *began* inside the settings overlay
    // is in flight. Guards the move/end handlers so the residual touchesEnded of
    // the pause-menu "Settings" tap (which opens settings mid-gesture) isn't
    // re-interpreted as a settings tap that bounces straight back to pause.
    private var settingsTouchActive = false
    private var score: Int = 0 {
        didSet { hud?.setScore(score) }
    }

    /// Transient camera offset. Decays toward zero every frame.
    private var shakeOffset: CGVector = .zero

    // Resuming from pause must reset lastUpdateTime so the next frame's dt
    // doesn't absorb the entire paused interval (which would spawn-flood
    // enemies, expire every bullet, and teleport parallax in one tick).
    override var isPaused: Bool {
        didSet {
            if oldValue && !isPaused {
                lastUpdateTime = 0
            }
        }
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
        lastCameraPosition = cam.position

        // Camera-attached HUD container. Its origin sits at the viewport's
        // lower-left, so any HUD math expressed in scene-space coordinates
        // works unchanged.
        let hudContainer = SKNode()
        hudContainer.position = CGPoint(x: -size.width / 2, y: -size.height / 2)
        cam.addChild(hudContainer)
        self.hudRoot = hudContainer

        // Space backdrop fills the viewport regardless of where the camera
        // moves through the universe.
        if let tex = SpriteCatalog.texture(for: .spaceBackdrop) {
            let bg = SKSpriteNode(texture: tex)
            bg.anchorPoint = CGPoint(x: 0.5, y: 0.5)
            bg.position = .zero
            bg.size = size
            bg.zPosition = -100
            cam.addChild(bg)
        }

        starfield   = Starfield(parent: cam, viewSize: size)
        planetField = PlanetField(scene: self)
        haptics   = HapticsController(settings: settings)
        player    = Player(scene: self, haptics: haptics)
        enemies   = EnemySystem(scene: self)
        bullets   = ProjectileSystem(scene: self)
        pickups   = PickupSystem(scene: self)
        hud       = HUDController(parent: hudContainer, viewSize: size, initialBest: bestScore())
        audio     = AudioController(scene: self, settings: settings)
        vfx       = VFXPool(scene: self)

        // Listener tracks the camera so on-screen explosions sound right even
        // while the camera is lerping toward the player.
        audio.listenerProvider = { [weak self] in
            self?.camNode.position ?? .zero
        }

        enemies.playerPositionProvider = { [weak self] in
            self?.player.node.position ?? .zero
        }
        enemies.cameraPositionProvider = { [weak self] in
            self?.camNode.position ?? .zero
        }
        enemies.viewSizeProvider = { [weak self] in
            self?.size ?? .zero
        }
        enemies.enemyFireRequest = { [weak self] origin, angle, kind in
            guard let self else { return }
            self.audio.play(.enemyShot, at: origin)
            switch kind {
            case .bullet: self.bullets.fireEnemyBullet(from: origin, angle: angle)
            case .bomb:   self.bullets.fireBomb(from: origin, angle: angle)
            }
        }
        bullets.onBombExpire = { [weak self] pos in
            self?.detonateBomb(at: pos)
        }

        // Joysticks render in screen-space alongside the rest of the HUD so
        // they stay anchored even when the camera moves across the universe.
        hudContainer.addChild(moveStick)
        hudContainer.addChild(aimStick)

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
        installDebugForcePause()
        #endif
    }

    override func willMove(from view: SKView) {
        NotificationCenter.default.removeObserver(self)
    }

    /// Called by ContentView when scenePhase drops out of `.active` (screen
    /// lock, app switcher, Control Center, incoming call, etc.). Always
    /// pauses the scene — leaving it running while backgrounded produces
    /// `IOGPUMetalError: Insufficient Permission`. Additionally pops the
    /// pause menu when the run is active so the player sees state on return.
    func applicationDidLoseFocus() {
        isPaused = true
        if isStarted, !isGameOver, !isInPauseMenu, !isInSettings {
            enterPauseMenu()
        }
    }

    /// Called by ContentView when scenePhase returns to `.active`. SKView
    /// auto-clears `scene.isPaused` on foreground, so we re-assert the pause
    /// when a menu is open — otherwise the run would silently resume behind
    /// the pause/settings overlay. With no menu up, the scene ticks normally
    /// (start-prompt cosmos, game-over pulse, live play).
    func applicationDidGainFocus() {
        if isInPauseMenu || isInSettings {
            isPaused = true
        } else {
            isPaused = false
        }
    }

    private func enterPauseMenu() {
        // Duck music BEFORE setting isPaused so the changeVolume action gets
        // a chance to apply against an unpaused scene tick. The pause button
        // hides so the menu's Resume label is the unambiguous way out.
        audio.duckMusic()
        isPaused = true
        isInPauseMenu = true
        hud.setPauseButtonVisible(false)
        moveStick.cancelAllTouches()
        aimStick.cancelAllTouches()
        hud.showPauseMenu()
    }

    private func dismissPauseMenusAndResume() {
        hud.dismissPauseMenu()
        hud.dismissSettingsMenu()
        isInPauseMenu = false
        isInSettings = false
        activeSliderDrag = nil
        // Reset the clock so the resumed frame doesn't accumulate the paused
        // wall-clock interval (the isPaused setter override at line 52
        // depends on transitioning false→true; the post-resume tick reads
        // `lastUpdateTime == 0` and emits dt = 0).
        lastUpdateTime = 0
        isPaused = false
        audio.unduckMusic()
        hud.setPauseButtonVisible(true)
    }

    private func returnToStart() {
        hud.dismissPauseMenu()
        hud.dismissSettingsMenu()
        isInPauseMenu = false
        isInSettings = false
        activeSliderDrag = nil
        bullets.clearAll()
        pickups.clearAll()
        enemies.reset()
        physicsWorld.speed = 1
        elapsed = 0
        score = 0
        isGameOver = false
        isStarted = false
        lastUpdateTime = 0
        player.reset()
        isPaused = false
        // Tear down the music node so the next `audio.startMusic()` call (on
        // the next Tap-to-Start) spins up a fresh one. Without this, startMusic
        // silently no-ops since bgMusic is still non-nil from the prior round.
        audio.stopMusic()
        hud.setPauseButtonVisible(false)
        hud.showStartPrompt()
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
        if let t = touches.first,
           nodes(at: t.location(in: self)).contains(where: { $0.name == Self.debugForcePauseLabel }) {
            // Auto-start so XCUI can reach the pause menu without first
            // tapping the start prompt and the pause button. Mirrors the
            // debugForceGameOver auto-start above.
            if !isStarted {
                isStarted = true
                lastUpdateTime = 0
                hud.dismissStartPrompt()
                hud.setPauseButtonVisible(true)
            }
            if isStarted, !isGameOver, !isInPauseMenu, !isInSettings {
                enterPauseMenu()
            }
            return
        }
        #endif

        // A fresh gesture owns the settings overlay only if it begins there.
        settingsTouchActive = false

        // Menu routing runs before gameplay input; settings sits above pause.
        if isInSettings, let t = touches.first {
            handleSettingsTouchBegan(touch: t)
            return
        }
        if isInPauseMenu, let t = touches.first {
            handlePauseMenuTap(touch: t)
            return
        }

        // Pause button — only meaningful mid-run. Drops into the pause menu.
        if isStarted, !isGameOver, let t = touches.first,
           nodes(at: t.location(in: self)).contains(where: { $0.name == HUDController.pauseButtonNodeName }) {
            enterPauseMenu()
            return
        }

        if !isStarted {
            isStarted = true
            lastUpdateTime = 0
            hud.dismissStartPrompt()
            hud.setPauseButtonVisible(true)
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
        // Touches are read in hudRoot's local space (which equals the visible
        // viewport) so the joysticks stay screen-relative as the camera moves.
        if !input.hasPhysicalController {
            moveStick.touchesBegan(touches, in: hudRoot, viewWidth: size.width)
            aimStick.touchesBegan(touches, in: hudRoot, viewWidth: size.width)
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        if settingsTouchActive, let t = touches.first {
            switch settingsTouchPhase {
            case .slider:
                updateActiveSlider(touch: t)
            case .scroll:
                let y = t.location(in: self).y
                hud.panSettingsContent(byDeltaY: y - settingsScrollLastY)
                settingsScrollLastY = y
            case .undecided:
                let p = t.location(in: self)
                let dy = p.y - settingsTouchStartScene.y
                let dx = p.x - settingsTouchStartScene.x
                // Commit to scrolling once vertical travel dominates and clears
                // the threshold; absorb the travel so far in the first pan.
                if abs(dy) >= Self.settingsScrollThreshold && abs(dy) > abs(dx) {
                    settingsTouchPhase = .scroll
                    hud.panSettingsContent(byDeltaY: p.y - settingsScrollLastY)
                    settingsScrollLastY = p.y
                }
            }
            return
        }
        moveStick.touchesMoved(touches, in: hudRoot)
        aimStick.touchesMoved(touches, in: hudRoot)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        if settingsTouchActive, let t = touches.first {
            // A release that never became a scroll or slider drag is a tap.
            if settingsTouchPhase == .undecided {
                dispatchSettingsTap(touch: t)
            }
            resetSettingsTouchState()
            return
        }
        activeSliderDrag = nil
        lastSFXPreviewValue = -1
        moveStick.touchesEnded(touches)
        aimStick.touchesEnded(touches)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        if settingsTouchActive {
            resetSettingsTouchState()
            return
        }
        activeSliderDrag = nil
        lastSFXPreviewValue = -1
        moveStick.touchesEnded(touches)
        aimStick.touchesEnded(touches)
    }

    // MARK: - Pause / settings menu routing

    private func handlePauseMenuTap(touch t: UITouch) {
        // Only the buttons act — empty space does nothing, so a stray tap can't
        // resume the run. Generous hit rects keep the buttons easy to land on.
        switch hud.pauseMenuHit(scenePoint: t.location(in: self), in: self) {
        case HUDController.pauseMenuResumeNodeName:
            haptics.impact(.light)
            dismissPauseMenusAndResume()
        case HUDController.pauseMenuSettingsNodeName:
            haptics.impact(.light)
            hud.dismissPauseMenu()
            isInPauseMenu = false
            isInSettings = true
            hud.showSettingsMenu(store: settings)
        case HUDController.pauseMenuQuitNodeName:
            haptics.impact(.medium)
            returnToStart()
        default:
            break
        }
    }

    /// Touch-down only classifies; it never acts on buttons. Grabbing a slider
    /// track commits to `.slider` immediately (a horizontal drag must capture
    /// from the first move). Otherwise the gesture stays `.undecided` until a
    /// vertical move makes it a scroll or a release makes it a tap.
    private func handleSettingsTouchBegan(touch t: UITouch) {
        settingsTouchActive = true
        settingsTouchPhase = .undecided
        settingsTouchStartScene = t.location(in: self)
        settingsScrollLastY = settingsTouchStartScene.y
        activeSliderDrag = nil

        guard let content = hud.settingsContentNode(),
              hud.settingsViewportContains(scenePoint: settingsTouchStartScene, in: self) else {
            return
        }
        let pInContent = content.convert(settingsTouchStartScene, from: self)
        if let rect = hud.musicSliderHitRect(), rect.contains(pInContent) {
            settingsTouchPhase = .slider
            activeSliderDrag = .music
            applySliderValue(at: pInContent, drag: .music)
        } else if let rect = hud.sfxSliderHitRect(), rect.contains(pInContent) {
            settingsTouchPhase = .slider
            activeSliderDrag = .sfx
            applySliderValue(at: pInContent, drag: .sfx)
        }
    }

    /// Dispatches a settings control tap on release (when the gesture wasn't a
    /// scroll or slider drag). Controls use generous boundaries; a tap that
    /// misses them is inert inside the panel and only returns to pause when it
    /// lands fully outside the panel.
    private func dispatchSettingsTap(touch t: UITouch) {
        let scenePt = t.location(in: self)
        if hud.settingsBackHit(scenePoint: scenePt, in: self) {
            exitSettingsToPauseMenu()
            return
        }
        if hud.settingsViewportContains(scenePoint: scenePt, in: self),
           let enable = hud.settingsHapticsHit(scenePoint: scenePt, in: self) {
            haptics.impact(.light)
            settings.hapticsEnabled = enable
            hud.updateHapticsToggleState(enabled: enable)
            return
        }
        if !hud.settingsPanelContains(scenePoint: scenePt, in: self) {
            exitSettingsToPauseMenu()
        }
    }

    private func resetSettingsTouchState() {
        settingsTouchActive = false
        settingsTouchPhase = .undecided
        activeSliderDrag = nil
        lastSFXPreviewValue = -1
    }

    private func exitSettingsToPauseMenu() {
        haptics.impact(.light)
        hud.dismissSettingsMenu()
        isInSettings = false
        activeSliderDrag = nil
        lastSFXPreviewValue = -1
        isInPauseMenu = true
        hud.showPauseMenu()
    }

    /// Applies the active slider drag using the touch in content-local space.
    private func updateActiveSlider(touch t: UITouch) {
        guard let drag = activeSliderDrag,
              let content = hud.settingsContentNode() else { return }
        applySliderValue(at: content.convert(t.location(in: self), from: self), drag: drag)
    }

    /// `point` is in the settings content node's local space, the same space
    /// as the hit rects, so the scroll offset cancels out of the x mapping.
    private func applySliderValue(at point: CGPoint, drag: SliderDrag) {
        let rect: CGRect?
        switch drag {
        case .music: rect = hud.musicSliderHitRect()
        case .sfx:   rect = hud.sfxSliderHitRect()
        }
        guard let rect, rect.width > 0 else { return }
        let v = Float(max(0, min(1, (point.x - rect.minX) / rect.width)))
        switch drag {
        case .music:
            // Music is already playing under the duck, so changing the volume
            // is audible live — no preview tone needed.
            audio.setMusicVolume(v)
            hud.updateMusicSliderThumb(value: v)
        case .sfx:
            audio.setSFXVolume(v)
            hud.updateSFXSliderThumb(value: v)
            // Throttled preview tap so the user hears the new volume while
            // dragging. Only re-emit on ≥ 0.05 value-delta to avoid stacking
            // overlapping ui_tap nodes per `touchesMoved`.
            if abs(v - lastSFXPreviewValue) >= 0.05 {
                audio.play(.uiTap)
                lastSFXPreviewValue = v
            }
        }
    }

    // MARK: - Update

    override func update(_ currentTime: TimeInterval) {
        let dt: TimeInterval = lastUpdateTime == 0 ? 0 : (currentTime - lastUpdateTime)
        lastUpdateTime = currentTime

        guard isStarted, !isGameOver else { return }

        elapsed += dt

        // Physical controller takes precedence over the on-screen sticks
        // when one is paired; otherwise read the dynamic touch joysticks.
        let useMFi = input.hasPhysicalController
        let moveVec = useMFi ? input.moveVector : moveStick.vector
        let aimVec  = useMFi ? input.aimVector  : aimStick.vector
        player.update(dt: dt, moveStick: moveVec, aimStick: aimVec)

        if player.isFiring {
            let fired = bullets.tryFirePlayer(from: player.node.position,
                                              angle: player.aimAngle,
                                              spreadLevel: player.spreadLevel,
                                              now: currentTime)
            if fired {
                audio.play(.playerShot,
                           at: player.node.position,
                           baseVolume: Tuning.Audio.playerShotVolume)
                vfx.spawnMuzzleFlash(at: player.node.position, angle: player.aimAngle)
            }
        }
        bullets.update(dt: dt)
        pickups.update(dt: dt)
        enemies.update(dt: dt)

        // Camera follow with dead-zone. The camera only moves once the player
        // has drifted past the dead-zone radius; then we lerp toward the player
        // at followLerpPerSec. Done before star/planet ticks so they can read
        // the post-step camera position.
        let target = player.node.position
        let dx = target.x - camNode.position.x
        let dy = target.y - camNode.position.y
        let dist = (dx * dx + dy * dy).squareRoot()
        let dead = Tuning.Camera.deadzoneRadius
        if dist > dead {
            let lerp = min(1, CGFloat(dt) * Tuning.Camera.followLerpPerSec)
            let pull = (dist - dead) / dist * lerp
            camNode.position = CGPoint(x: camNode.position.x + dx * pull,
                                        y: camNode.position.y + dy * pull)
        }

        planetField.update(dt: dt)

        // Star parallax is driven by *camera* motion, not player motion — the
        // dead-zone follow means they diverge whenever the player is moving
        // less than the dead-zone or the camera is still catching up.
        let cameraDelta = CGVector(dx: camNode.position.x - lastCameraPosition.x,
                                    dy: camNode.position.y - lastCameraPosition.y)
        starfield.update(dt: dt, cameraDelta: cameraDelta)
        lastCameraPosition = camNode.position

        // Apply + decay screen shake on top of the followed camera. Shake is a
        // purely visual offset and must not feed back into the follow lerp.
        camNode.position.x += shakeOffset.dx
        camNode.position.y += shakeOffset.dy
        shakeOffset.dx *= Tuning.VFX.shakeDecay
        shakeOffset.dy *= Tuning.VFX.shakeDecay
    }

    override func didChangeSize(_ oldSize: CGSize) {
        super.didChangeSize(oldSize)
        guard let hudRoot else { return }
        hudRoot.position = CGPoint(x: -size.width / 2, y: -size.height / 2)
        starfield?.rebuild(viewSize: size)
    }

    /// Kicks the camera in a random direction. Decays via `shakeDecay`.
    func applyShake(_ intensity: CGFloat) {
        let clamped = min(intensity, Tuning.VFX.shakeMaxOffset)
        let angle = CGFloat.random(in: 0..<(2 * .pi))
        shakeOffset.dx = cos(angle) * clamped
        shakeOffset.dy = sin(angle) * clamped
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
                case .killed(let s, let pos, _, let deadNode):
                    score += s
                    audio.play(.warp, at: pos)
                    vfx.spawnBlackholeWarp(enemyNode: deadNode, at: pos)
                    applyShake(Tuning.VFX.enemyKillShakeIntensity)
                    haptics.impact(.light)
                    pickups.trySpawnGoldenBanana(at: pos)
                }
            }
            return
        }

        if mask == (Category.player | Category.enemy) {
            let enemyNode: SKNode? = a.categoryBitMask == Category.enemy ? a.node : b.node
            if player.tryTakeHit() {
                if let en = enemyNode, let dead = enemies.killByNode(en) {
                    vfx.spawnBlackholeWarp(enemyNode: dead, at: dead.position)
                }
                audio.play(.playerHit, at: player.node.position)
                applyShake(Tuning.VFX.playerHitShakeIntensity)
                vfx.spawnDamageFlash(target: player.visual)
                if !player.isAlive { triggerGameOver() }
            }
            return
        }

        // Enemy projectiles (bullets + bombs) hitting the player. Enemy
        // projectiles use Category.enemyBullet (separate from player bullets)
        // so they don't damage other enemies in their flight path.
        if mask == (Category.enemyBullet | Category.player) {
            let bulletNode: SKNode? = a.categoryBitMask == Category.bullet ? a.node : b.node
            guard let bn = bulletNode, let bullet = bullets.findBullet(by: bn) else { return }
            let pos = bn.position
            let wasBomb = bullet.isBomb
            bullets.recycle(node: bn)
            if wasBomb {
                detonateBomb(at: pos)
            }
            if player.tryTakeHit() {
                audio.play(.playerHit, at: player.node.position)
                vfx.spawnDamageFlash(target: player.visual)
                if !wasBomb {
                    applyShake(Tuning.VFX.playerHitShakeIntensity)
                }
                if !player.isAlive { triggerGameOver() }
            }
            return
        }

        if mask == (Category.player | Category.pickup) {
            let pickupNode: SKNode? = a.categoryBitMask == Category.pickup ? a.node : b.node
            if let pn = pickupNode {
                pickups.collect(node: pn)
                // At max spread, the banana restores a life instead (up to
                // Tuning.Player.maxLives). Score bonus applies either way,
                // including when both spread and lives are maxed out.
                if !player.upgradeSpread() {
                    _ = player.restoreLife()
                }
                score += Tuning.Pickup.scoreBonus
                audio.play(.pickup, at: player.node.position)
                haptics.impact(.light)
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
        haptics.notification(.error)

        hud.setPauseButtonVisible(false)
        hud.showGameOver(score: score, best: max(score, best))
        moveStick.cancelAllTouches()
        aimStick.cancelAllTouches()
        physicsWorld.speed = 0
    }

    private func restart() {
        hud.dismissGameOver()
        bullets.clearAll()
        pickups.clearAll()
        enemies.reset()
        physicsWorld.speed = 1
        elapsed = 0
        score = 0
        isGameOver = false
        lastUpdateTime = 0
        player.reset()
        hud.setPauseButtonVisible(true)
    }

    private func bestScore() -> Int {
        UserDefaults.standard.integer(forKey: "best_score")
    }

    /// 25-frame bomb detonation — larger, chunkier, slower per-frame than the
    /// enemy-kill explosion. Used for gorilla bombs (hit or timeout).
    private func spawnBombExplosion(at position: CGPoint) {
        guard let action = AnimationCatalog.oneShot(.bombExplosion,
                                                     frameDuration: Tuning.VFX.bombExplosionFrameDuration) else { return }
        let frames = AnimationCatalog.textures(for: .bombExplosion)
        guard let first = frames.first else { return }
        let node = SKSpriteNode(texture: first)
        let target: CGFloat = Tuning.Enemy.radius * Tuning.VFX.bombExplosionScale
        let maxDim = max(first.size().width, first.size().height)
        if maxDim > 0 { node.setScale(target / maxDim) }
        node.position = position
        node.zPosition = 50
        addChild(node)
        node.run(action)
    }

    /// All the per-event compositing for a bomb going off: animated sprite,
    /// audio, big screen shake, large glow halo.
    private func detonateBomb(at position: CGPoint) {
        spawnBombExplosion(at: position)
        audio.play(.explosion, at: position)
        applyShake(Tuning.VFX.bombShakeIntensity)
        vfx.spawnGlow(at: position,
                      scale: Tuning.VFX.glowBombScale,
                      duration: Tuning.VFX.glowBombDuration)
    }

    #if DEBUG
    private static let debugForceGameOverLabel = "debugForceGameOver"
    private static let debugForcePauseLabel = "debugForcePause"

    private func installDebugForceGameOver() {
        // SKLabelNode is the only node type SpriteKit surfaces to XCUITest
        // through automatic accessibility — see PenguinSlide's same hook.
        // Parented to hudRoot so it stays anchored top-left as the camera
        // moves through the universe.
        let node = SKLabelNode(text: Self.debugForceGameOverLabel)
        node.name = Self.debugForceGameOverLabel
        node.fontSize = 10
        node.fontColor = UIColor(red: 1, green: 0, blue: 0, alpha: 0.55)
        node.horizontalAlignmentMode = .left
        node.verticalAlignmentMode = .top
        node.position = CGPoint(x: 4, y: size.height - 4)
        node.zPosition = 10_000
        hudRoot.addChild(node)
    }

    private func installDebugForcePause() {
        // Sibling of debugForceGameOver; offset down so the two labels
        // don't overlap. Lets XCUI drop into the pause menu without
        // hit-testing the sprite-based pause button.
        let node = SKLabelNode(text: Self.debugForcePauseLabel)
        node.name = Self.debugForcePauseLabel
        node.fontSize = 10
        node.fontColor = UIColor(red: 1, green: 0, blue: 0, alpha: 0.55)
        node.horizontalAlignmentMode = .left
        node.verticalAlignmentMode = .top
        node.position = CGPoint(x: 4, y: size.height - 18)
        node.zPosition = 10_000
        hudRoot.addChild(node)
    }
    #endif
}
