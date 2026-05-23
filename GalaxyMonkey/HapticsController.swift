//
//  HapticsController.swift
//  GalaxyMonkey
//
//  Central gate for impact + notification haptics. All gameplay call sites
//  go through here so a single SettingsStore.hapticsEnabled toggle silences
//  the lot. Reads the flag live on each call so flipping it in the settings
//  menu takes effect immediately.
//

import UIKit

final class HapticsController {

    private let settings: SettingsStore

    init(settings: SettingsStore) {
        self.settings = settings
    }

    func impact(_ style: UIImpactFeedbackGenerator.FeedbackStyle) {
        guard settings.hapticsEnabled else { return }
        let gen = UIImpactFeedbackGenerator(style: style)
        gen.impactOccurred()
    }

    func notification(_ type: UINotificationFeedbackGenerator.FeedbackType) {
        guard settings.hapticsEnabled else { return }
        let gen = UINotificationFeedbackGenerator()
        gen.notificationOccurred(type)
    }
}
