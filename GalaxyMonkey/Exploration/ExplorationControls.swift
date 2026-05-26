//
//  ExplorationControls.swift
//  GalaxyMonkey
//
//  SwiftUI dual joysticks + overlay chrome for the 3D exploration mode.
//
//  Mirrors the feel of the in-game SpriteKit `VirtualJoystick` (see that
//  file for the 50% dead-zone rationale) but draws with pure SwiftUI so it
//  composes over a `RealityView`. Each stick emits a normalized
//  `SIMD2<Float>` in [-1, 1] after dead-zone curving; the parent reads the
//  bindings and forwards them into `ExplorationScene`.
//

import SwiftUI
import simd

/// A single virtual joystick. The visible base is fixed under the resting
/// finger; only the thumb travels. Outside-radius drags clamp to the rim.
struct SwiftUIJoystick: View {

    /// Emitted vector in [-1, 1]² after dead-zone curving. (0, 0) when idle.
    @Binding var value: SIMD2<Float>

    let radius: CGFloat
    let label: String

    @State private var thumbOffset: CGSize = .zero
    @State private var isActive: Bool = false

    var body: some View {
        ZStack {
            // Base ring — barely-there until touched, then visible. Matches
            // the in-game joystick's quiet baseAlpha so the overlay doesn't
            // drown out the sky.
            Circle()
                .stroke(Color.white.opacity(isActive ? 0.35 : 0.18), lineWidth: 1.5)
                .background(
                    Circle()
                        .fill(Color.white.opacity(isActive ? 0.12 : 0.04))
                )
                .frame(width: radius * 2, height: radius * 2)
            // Thumb.
            Circle()
                .fill(Color.white.opacity(isActive ? 0.35 : 0.18))
                .frame(width: radius * 0.7, height: radius * 0.7)
                .offset(thumbOffset)
            // Label below the stick so the player learns which is which.
            Text(label)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.white.opacity(0.5))
                .offset(y: radius + 18)
        }
        .frame(width: radius * 2, height: radius * 2)
        .contentShape(Circle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { gesture in
                    isActive = true
                    // Translation is relative to the start of the drag —
                    // perfect for a sticky-base joystick (the base stays
                    // where the finger first landed; the thumb follows).
                    let dx = gesture.translation.width
                    let dy = gesture.translation.height
                    var v = CGSize(width: dx, height: dy)
                    let len = sqrt(dx * dx + dy * dy)
                    if len > radius {
                        v = CGSize(width: dx * radius / len, height: dy * radius / len)
                    }
                    thumbOffset = v
                    // Normalise to [-1, 1]. Y-axis flipped because SwiftUI
                    // y grows downward but we want "stick up = +y".
                    var nx = Float(v.width / radius)
                    var ny = Float(-v.height / radius)
                    let mag = sqrt(nx * nx + ny * ny)
                    let dead = Tuning.Exploration.stickDeadZone
                    if mag < dead {
                        nx = 0; ny = 0
                    } else {
                        // Remap (dead, 1] → (0, 1] so the response curve
                        // doesn't have a flat dead patch followed by a
                        // sudden onset; reaches full deflection at rim.
                        let scaled = (mag - dead) / (1 - dead) / mag
                        nx *= scaled; ny *= scaled
                    }
                    value = SIMD2<Float>(nx, ny)
                }
                .onEnded { _ in
                    isActive = false
                    thumbOffset = .zero
                    value = .zero
                }
        )
    }
}

/// Telemetry chip — small monospaced read-out so the player feels the
/// physics, not just sees the bodies move.
struct TelemetryChip: View {
    let telemetry: ExplorationScene.Telemetry

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            row(label: "v", value: String(format: "%.2f m/s", telemetry.speedMps))
            row(label: "g", value: String(format: "%.3f m/s²", telemetry.gMagnitude))
            row(label: "near", value: "\(telemetry.nearestBody) · \(format(distance: telemetry.nearestDistance))")
        }
        .font(.system(size: 11, weight: .semibold, design: .monospaced))
        .foregroundStyle(.white.opacity(0.85))
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(Color.black.opacity(0.4))
        )
    }

    private func row(label: String, value: String) -> some View {
        HStack(spacing: 6) {
            Text(label).foregroundStyle(.white.opacity(0.45))
            Text(value)
        }
    }

    private func format(distance: Float) -> String {
        // Show distance in AU when the nearest body is further than the
        // Sun's display radius — otherwise meters reads clearer.
        let au = distance / Tuning.Exploration.auMeters
        if au >= 0.05 {
            return String(format: "%.2f AU", au)
        }
        return String(format: "%.1f m", distance)
    }
}
