//
//  Body.swift
//  GalaxyMonkey
//
//  Solar-system body model for the hidden 3D exploration sandbox. A body has
//  a mass (which feeds the Newtonian gravity sum on the player ship) and
//  optionally an orbit (Sun = nil). Planets move on **fixed Kepler orbits**
//  evaluated analytically each frame, so the planetary system stays stable
//  forever — only the player is integrated numerically.
//
//  v1 uses the circular approximation (e ≈ 0); the propagator is just a
//  uniform angular sweep. Eccentricity can be reintroduced via Kepler's
//  equation solver (Newton-Raphson on M = E − e·sin E) without changing
//  any consumer.
//

import Foundation
import simd

/// Circular orbit around the world origin (= the Sun). Plane-locked at y = 0.
struct KeplerOrbit {
    /// Semi-major axis in world meters.
    let semiMajor: Float
    /// Orbital period in *real-world seconds*. Compressed by
    /// `Tuning.Exploration.timeCompression` before being applied.
    let periodSeconds: Float
    /// Phase at t = 0 (radians). Used to scatter the inner planets so they
    /// don't all spawn in a conjunction line.
    let phaseAtT0: Float

    /// Position at compressed game time `tCompressed` (seconds). The compression
    /// is done by the caller so this stays a pure orbit-propagator.
    func position(at tCompressed: Float) -> SIMD3<Float> {
        let omega = (2 * .pi) / periodSeconds                // rad / real-second
        let theta = phaseAtT0 + omega * tCompressed
        return SIMD3<Float>(semiMajor * cos(theta), 0, semiMajor * sin(theta))
    }
}

/// One solar-system body. The Sun has `orbit == nil` and sits at the world
/// origin. Planets have an orbit but no parent — single-tier hierarchy in v1.
struct Body {
    /// Display name surfaced in telemetry HUD ("nearest body").
    let name: String
    /// Mass in world kilograms (already includes `planetMassBoost` if any).
    /// Drives the gravitational pull on the player ship.
    let mass: Float
    /// Visual sphere radius in world meters. Also feeds the softening length
    /// in the gravity sum so passing through the visual disk doesn't produce
    /// an infinite acceleration spike.
    let visualRadius: Float
    /// Tint applied to the unlit sphere material. Picked per-body so the
    /// inner system reads at a glance without needing UV-mapped textures
    /// in v1.
    let color: SIMD3<Float>
    /// nil = the Sun (sits at origin).
    let orbit: KeplerOrbit?

    func position(at tCompressed: Float) -> SIMD3<Float> {
        orbit?.position(at: tCompressed) ?? .zero
    }
}
