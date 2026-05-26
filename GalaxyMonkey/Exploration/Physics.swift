//
//  Physics.swift
//  GalaxyMonkey
//
//  Newtonian N-body gravity on the player ship + symplectic integrator.
//
//  Planets are NOT integrated here — they ride their fixed Kepler orbits.
//  Only the monkey's state (position, velocity) is advanced by these
//  routines. Each body contributes  a = −G·M·(r − r_body) / (|r − r_body|² + ε²)^(3/2),
//  with `ε` a per-body Plummer softening length so close approaches don't
//  produce a 1/r² singularity at the body center.
//
//  The integrator is **velocity Verlet** — symplectic, conserves energy
//  over long horizons, and trivially handles the position-dependent
//  acceleration field. Forward Euler would spiral the monkey out of any
//  orbit within minutes; we deliberately avoid it.
//

import Foundation
import simd

enum Physics {

    /// Sum of gravitational acceleration on a point mass at `point` from every
    /// body in `bodies` at the given compressed simulation time. Mass of the
    /// test particle cancels (a = F/m = ΣGM_i/r²).
    ///
    /// - Parameters:
    ///   - point: Player position in world meters.
    ///   - bodies: All gravitating bodies (Sun + planets). The asteroid belt
    ///             is excluded from the gravity sum — it's purely visual to
    ///             keep the inner loop tight.
    ///   - tCompressed: Game-compressed seconds since exploration start
    ///                  (drives Kepler propagation).
    /// - Returns: Acceleration vector in world m/s².
    static func gravityAcceleration(
        at point: SIMD3<Float>,
        from bodies: [Body],
        atTime tCompressed: Float
    ) -> SIMD3<Float> {
        var a = SIMD3<Float>.zero
        let G = Tuning.Exploration.G
        let softMul = Tuning.Exploration.softeningRadiusMultiplier
        for b in bodies {
            let bodyPos = b.position(at: tCompressed)
            let r = point - bodyPos                          // from body to point
            // Plummer softening: replace |r| with sqrt(|r|² + ε²). Smooth,
            // conserves energy, equivalent to treating the body as a
            // distributed sphere rather than a point mass.
            let eps = b.visualRadius * softMul
            let r2 = simd_length_squared(r) + eps * eps
            let invR3 = 1.0 / (r2 * sqrt(r2))                // 1 / (r² + ε²)^(3/2)
            a -= G * b.mass * r * invR3                      // attract toward body
        }
        return a
    }

    /// One velocity-Verlet step. Reads the acceleration field twice — once
    /// at the old position (passed in as `aOld`, so callers can reuse the
    /// previous frame's value), once at the proposed new position. This is
    /// the standard "kick-drift-kick" formulation.
    ///
    /// - Returns: `(newPos, newVel, newAcc)`. Callers pass `newAcc` back in
    ///            as `aOld` next step.
    static func velocityVerletStep(
        position p: SIMD3<Float>,
        velocity v: SIMD3<Float>,
        previousAcceleration aOld: SIMD3<Float>,
        externalAcceleration aExt: SIMD3<Float>,
        bodies: [Body],
        atTime tCompressed: Float,
        dt: Float
    ) -> (SIMD3<Float>, SIMD3<Float>, SIMD3<Float>) {
        // Position update with half-step velocity. External acceleration
        // (thrust) is treated as constant over the substep, which is fine
        // because the player applies it directly through the joystick.
        let aTotalOld = aOld + aExt
        let pNew = p + v * dt + 0.5 * aTotalOld * (dt * dt)

        // Recompute gravity at the new position; planets have moved a tiny
        // amount under Kepler propagation in the meantime — include that.
        let gNew = gravityAcceleration(at: pNew, from: bodies, atTime: tCompressed + dt)
        let aTotalNew = gNew + aExt

        let vNew = v + 0.5 * (aTotalOld + aTotalNew) * dt

        // Return the gravity-only acceleration so the next step's `aOld`
        // doesn't double-count the external thrust.
        return (pNew, vNew, gNew)
    }
}
