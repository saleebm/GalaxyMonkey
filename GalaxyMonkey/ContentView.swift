//
//  ContentView.swift
//  GalaxyMonkey
//

import SwiftUI
import SpriteKit

struct ContentView: View {

    /// Top-level mode switch. The main game lives in `.game`; the hidden
    /// 3D exploration sandbox (entered from the settings panel) lives in
    /// `.exploration`. Toggling between them pauses the SpriteKit scene
    /// behind the scenes; exploration state explicitly resets on every
    /// entry.
    private enum AppMode {
        case game
        case exploration
    }

    @State private var scene: GameScene?
    @State private var mode: AppMode = .game
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                if let scene {
                    SpriteView(
                        scene: scene,
                        options: [.shouldCullNonVisibleNodes]
                    )
                    .ignoresSafeArea()
                } else {
                    Color(red: 0.02, green: 0.03, blue: 0.08)
                        .ignoresSafeArea()
                }

                if mode == .exploration {
                    // Full-screen overlay. The SpriteKit scene stays paused
                    // underneath (GameScene's settings handler pauses
                    // before invoking the enter callback); we just cover
                    // it so the player sees only the 3D world.
                    ExplorationView(onExit: { mode = .game })
                        .transition(.opacity)
                }
            }
            .onAppear {
                if scene == nil {
                    let s = GameScene(size: proxy.size)
                    s.scaleMode = .resizeFill
                    // GameScene fires this when the player taps the
                    // hidden "Exploration Mode" row in the settings
                    // panel. The scene has already paused itself by the
                    // time we run.
                    s.onEnterExploration = { mode = .exploration }
                    scene = s
                }
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    scene?.applicationDidGainFocus()
                } else {
                    scene?.applicationDidLoseFocus()
                }
            }
        }
    }
}

#Preview {
    ContentView()
}
