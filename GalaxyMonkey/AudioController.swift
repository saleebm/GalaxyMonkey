//
//  AudioController.swift
//  GalaxyMonkey
//
//  Music + SFX wrapper. Audio files are bundled under Sounds/ as .caf.
//  Calls are no-ops while the corresponding file is missing — the
//  ElevenLabs pipeline generates them incrementally.
//

import SpriteKit
import AVFoundation

enum Sfx: String {
    case playerShot   = "shot.caf"
    case enemyShot    = "enemy_shot.caf"
    case explosion    = "explosion.caf"
    case pickup       = "pickup.caf"
    case playerHit    = "player_hit.caf"
    case waveStart    = "wave_start.caf"
    case gameOver     = "game_over.caf"
    case uiTap        = "ui_tap.caf"
}

final class AudioController {

    private weak var scene: SKScene?
    private let settings: SettingsStore
    private var bgMusic: SKAudioNode?
    private var verified: Set<Sfx> = []
    private var missingSfx: Set<Sfx> = []
    private var missingMusic: Set<String> = []

    /// Provides the listener (player) position used by `play(_:at:)` for
    /// distance-based attenuation. GameScene wires this to the player.
    var listenerProvider: () -> CGPoint = { .zero }

    // The iOS Simulator's coreaudio host frequently RPC-times-out under
    // SpriteKit's audio engine (AURemoteIO Cleanup → SIGABRT). XCUITest also
    // injects environment that triggers extra accessibility traffic during
    // setup. To keep tests reliable we disable audio when either is true; on
    // real hardware audio runs normally.
    private static let isDisabled: Bool = {
        #if targetEnvironment(simulator)
        return true
        #else
        return ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
        #endif
    }()

    init(scene: SKScene, settings: SettingsStore) {
        self.scene = scene
        self.settings = settings
    }

    func startMusic(filename: String = "bg_music.caf") {
        if Self.isDisabled { return }
        guard scene != nil, bgMusic == nil else { return }
        guard Bundle.main.url(forResource: filename, withExtension: nil) != nil else {
            missingMusic.insert(filename)
            return
        }
        let node = SKAudioNode(fileNamed: filename)
        node.autoplayLooped = true
        node.isPositional = false
        node.run(SKAction.changeVolume(to: settings.musicVolume, duration: 0))
        scene?.addChild(node)
        bgMusic = node
    }

    func pauseMusic() { bgMusic?.run(SKAction.pause()) }
    func resumeMusic() { bgMusic?.run(SKAction.play()) }

    /// Persists the new volume and applies it live to the playing music node.
    func setMusicVolume(_ v: Float) {
        let clamped = max(0, min(1, v))
        settings.musicVolume = clamped
        bgMusic?.run(SKAction.changeVolume(to: clamped, duration: 0))
    }

    /// Persists the new SFX volume. New play(...) calls pick it up on demand;
    /// in-flight SFX nodes finish at their previous volume.
    func setSFXVolume(_ v: Float) {
        settings.sfxVolume = max(0, min(1, v))
    }

    /// Non-spatial SFX. Volume is applied directly, scaled by the user's
    /// SFX volume preference.
    func play(_ sfx: Sfx, volume: Float = 1.0) {
        if Self.isDisabled { return }
        guard let scene else { return }
        guard ensureExists(sfx) else { return }
        // SKAction.playSoundFileNamed ignores grouped changeVolume, so we
        // emit a short-lived SKAudioNode per call and let it remove itself
        // after a generous cleanup window.
        let node = SKAudioNode(fileNamed: sfx.rawValue)
        node.autoplayLooped = false
        node.isPositional = false
        scene.addChild(node)
        let v = max(0, min(1, volume * settings.sfxVolume))
        node.run(SKAction.sequence([
            SKAction.changeVolume(to: v, duration: 0),
            SKAction.play(),
            SKAction.wait(forDuration: 5.0),
            SKAction.removeFromParent(),
        ]))
    }

    /// Spatial SFX. Attenuates by distance from the listener position
    /// (set via `listenerProvider`). `baseVolume` multiplies the
    /// distance-attenuated value — pass `< 1.0` for sounds that should be
    /// inherently quieter regardless of distance.
    func play(_ sfx: Sfx, at position: CGPoint, baseVolume: Float = 1.0) {
        if Self.isDisabled { return }
        let listener = listenerProvider()
        let dx = position.x - listener.x
        let dy = position.y - listener.y
        let d = (dx * dx + dy * dy).squareRoot()
        let t = Float(min(1, max(0, d / Tuning.Audio.maxDistance)))
        let attenuation = (1 - t) * (1 - Tuning.Audio.minVolume) + Tuning.Audio.minVolume
        play(sfx, volume: baseVolume * attenuation)
    }

    private func ensureExists(_ sfx: Sfx) -> Bool {
        if missingSfx.contains(sfx) { return false }
        if verified.contains(sfx) { return true }
        guard Bundle.main.url(forResource: sfx.rawValue, withExtension: nil) != nil else {
            missingSfx.insert(sfx)
            return false
        }
        verified.insert(sfx)
        return true
    }
}
