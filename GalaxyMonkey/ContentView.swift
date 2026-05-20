//
//  ContentView.swift
//  GalaxyMonkey
//

import SwiftUI
import SpriteKit

struct ContentView: View {

    @State private var scene: GameScene?
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
            }
            .onAppear {
                if scene == nil {
                    let s = GameScene(size: proxy.size)
                    s.scaleMode = .resizeFill
                    scene = s
                }
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase != .active {
                    scene?.applicationDidLoseFocus()
                }
            }
        }
    }
}

#Preview {
    ContentView()
}
