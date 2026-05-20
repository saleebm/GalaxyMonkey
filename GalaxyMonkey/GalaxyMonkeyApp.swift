//
//  GalaxyMonkeyApp.swift
//  GalaxyMonkey
//
//  Twin-stick arcade shooter inspired by Ape Escape / Galaxy Monkey.
//  Target: iOS 17+
//

import SwiftUI
import AVFoundation

@main
struct GalaxyMonkeyApp: App {

    init() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, options: [.mixWithOthers])
        try? session.setActive(true)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .statusBarHidden(true)
                .persistentSystemOverlays(.hidden)
        }
    }
}
