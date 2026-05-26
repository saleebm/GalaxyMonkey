//
//  ExplorationView.swift
//  GalaxyMonkey
//
//  SwiftUI host for the hidden 3D exploration sandbox. Wraps a RealityView
//  (RealityKit) showing the inner solar system with real Newtonian gravity
//  on the player ship; overlays the twin SwiftUI joysticks, a small
//  telemetry chip, and a Back button.
//
//  Lifecycle: the parent ContentView swaps in this view in place of the
//  SpriteKit GameScene while the main game stays paused under the hood.
//  All state — monkey position, velocity, sim time — is owned by the
//  ExplorationScene observable object and resets on every entry (we
//  explicitly opted out of UserDefaults persistence for v1).
//

import RealityKit
import SwiftUI
import simd

struct ExplorationView: View {

    /// Called when the player taps Back. Parent flips its mode state.
    let onExit: () -> Void

    @StateObject private var scene = ExplorationScene()
    @State private var leftStick: SIMD2<Float> = .zero
    @State private var rightStick: SIMD2<Float> = .zero

    var body: some View {
        ZStack {
            // 3D world fills the screen.
            RealityView { content in
                content.add(scene.makeWorld())
            } update: { _ in
                // No-op here — RealityKit's per-frame step is driven by
                // the TimelineView below so the dt is explicit and we can
                // pause cleanly on background.
            }
            .ignoresSafeArea()
            .background(Color.black)

            // Per-frame physics tick. TimelineView gives us a stable dt
            // hook even though RealityView's `update` doesn't expose one
            // on iOS without subscribing to a scene event stream.
            TimelineView(.animation) { ctx in
                Color.clear
                    .onChange(of: ctx.date) { oldDate, newDate in
                        let dt = newDate.timeIntervalSince(oldDate)
                        scene.leftStick = leftStick
                        scene.rightStick = rightStick
                        scene.step(deltaSeconds: dt)
                    }
            }
            .allowsHitTesting(false)

            // Overlay HUD.
            VStack {
                HStack(alignment: .top) {
                    // Back button — primary exit. Top-left, generous tap area.
                    Button(action: onExit) {
                        Label("Back", systemImage: "chevron.left")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                            .background(
                                Capsule().fill(Color.black.opacity(0.45))
                            )
                            .overlay(
                                Capsule().stroke(Color.white.opacity(0.25), lineWidth: 1)
                            )
                    }
                    Spacer()
                    TelemetryChip(telemetry: scene.telemetry)
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)

                Spacer()

                // Twin sticks anchored to the bottom corners.
                HStack(alignment: .bottom) {
                    SwiftUIJoystick(value: $leftStick, radius: 64, label: "MOVE")
                    Spacer()
                    SwiftUIJoystick(value: $rightStick, radius: 64, label: "LOOK")
                }
                .padding(.horizontal, 36)
                .padding(.bottom, 28)
            }
        }
        .statusBarHidden()
        // Reset stick state when entering so a stale binding from a prior
        // visit doesn't push the ship on first frame.
        .onAppear {
            leftStick = .zero
            rightStick = .zero
        }
    }
}

#Preview {
    ExplorationView(onExit: {})
}
