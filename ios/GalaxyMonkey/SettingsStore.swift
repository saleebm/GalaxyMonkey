//
//  SettingsStore.swift
//  GalaxyMonkey
//
//  UserDefaults-backed wrapper for user-tunable settings (volumes, haptics).
//  Reads fall back to Tuning.Settings defaults when no value is persisted yet.
//

import Foundation

final class SettingsStore {

    private enum Keys {
        static let musicVolume = "settings.musicVolume"
        static let sfxVolume = "settings.sfxVolume"
        static let hapticsEnabled = "settings.hapticsEnabled"
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var musicVolume: Float {
        get {
            guard defaults.object(forKey: Keys.musicVolume) != nil else {
                return Tuning.Settings.defaultMusicVolume
            }
            return defaults.float(forKey: Keys.musicVolume)
        }
        set {
            defaults.set(max(0, min(1, newValue)), forKey: Keys.musicVolume)
        }
    }

    var sfxVolume: Float {
        get {
            guard defaults.object(forKey: Keys.sfxVolume) != nil else {
                return Tuning.Settings.defaultSFXVolume
            }
            return defaults.float(forKey: Keys.sfxVolume)
        }
        set {
            defaults.set(max(0, min(1, newValue)), forKey: Keys.sfxVolume)
        }
    }

    var hapticsEnabled: Bool {
        get {
            guard defaults.object(forKey: Keys.hapticsEnabled) != nil else {
                return Tuning.Settings.defaultHapticsEnabled
            }
            return defaults.bool(forKey: Keys.hapticsEnabled)
        }
        set {
            defaults.set(newValue, forKey: Keys.hapticsEnabled)
        }
    }
}
