//
//  ExplorationScene.swift
//  GalaxyMonkey
//
//  RealityKit world for the hidden 3D exploration sandbox. Builds the entity
//  graph (Sun + Mercury, Venus, Earth, Mars + asteroid belt + monkey ship +
//  chase camera), runs a fixed-timestep 120 Hz physics loop that integrates
//  the monkey under real Newtonian gravity from every planetary body, and
//  publishes a small telemetry struct for the on-screen HUD.
//
//  Planets ride their Kepler orbits analytically (forever-stable). Only the
//  monkey is integrated numerically (velocity Verlet — symplectic, conserves
//  energy over long flights). See Physics.swift for the integrator.
//

import Combine
import Foundation
import RealityKit
import SwiftUI
import simd

@MainActor
final class ExplorationScene: ObservableObject {

    // MARK: - Public telemetry

    struct Telemetry {
        var speedMps: Float = 0          // monkey speed in world m/s
        var nearestBody: String = "—"    // name of closest gravitating body
        var nearestDistance: Float = 0   // world meters to that body
        var gMagnitude: Float = 0        // |gravity acceleration| at monkey
    }

    @Published private(set) var telemetry = Telemetry()

    // MARK: - Input (driven by ExplorationView's joysticks)

    /// Left stick: x = strafe (camera-local X), y = forward/back (camera-local Z).
    /// Both already dead-zoned by the joystick view.
    var leftStick: SIMD2<Float> = .zero
    /// Right stick: x = yaw rate, y = pitch rate.
    var rightStick: SIMD2<Float> = .zero

    // MARK: - World

    private let worldAnchor = AnchorEntity(world: .zero)
    private var bodies: [Body] = []
    private var bodyEntities: [Entity] = []
    private var beltEntities: [(entity: Entity, radius: Float, speed: Float, phase: Float)] = []
    private var sunLight: PointLight?

    // Monkey state — the only thing actually integrated. SIMD3 not SIMD3
    // <Double> because RealityKit transforms are Float; mixing introduces
    // an avoidable narrowing each frame.
    private var monkeyPosition: SIMD3<Float> = .zero
    private var monkeyVelocity: SIMD3<Float> = .zero
    private var lastGravity: SIMD3<Float> = .zero
    private var cameraYaw: Float = 0
    private var cameraPitch: Float = 0
    private let monkeyEntity = Entity()
    private let cameraEntity = PerspectiveCamera()

    // MARK: - Time

    /// Compressed simulation time in seconds (drives Kepler propagation).
    /// Starts at zero on every entry — exploration state explicitly resets.
    private var simTime: Float = 0
    private var physicsAccumulator: Float = 0
    private var lastUpdateTimestamp: TimeInterval = 0

    // MARK: - Setup

    /// Build the entity graph and return the root anchor for the RealityView
    /// to add. Called once on view setup. Idempotent — re-entering exploration
    /// mode rebuilds from scratch (we explicitly chose not to persist state
    /// across visits).
    func makeWorld() -> Entity {
        // Reset to fresh state every entry (UserDefaults persistence was
        // explicitly opted out of). Clears any prior world so re-entering
        // doesn't pile up duplicates.
        worldAnchor.children.removeAll()
        bodies = makeInnerSolarSystem()
        bodyEntities = []
        beltEntities = []

        // Sun gets a point light so planets are shaded; ambient lift keeps
        // the night side legible (RealityKit's default is pure black).
        let light = PointLight()
        light.light.color = .white
        light.light.intensity = 30_000
        light.light.attenuationRadius = 200
        light.position = .zero
        worldAnchor.addChild(light)
        sunLight = light

        // Build planet entities (Sun at index 0, then orbiting bodies).
        for body in bodies {
            let sphere = MeshResource.generateSphere(radius: body.visualRadius)
            let material = bodyMaterial(for: body)
            let entity = ModelEntity(mesh: sphere, materials: [material])
            entity.name = body.name
            worldAnchor.addChild(entity)
            bodyEntities.append(entity)
        }

        // Asteroid belt — tiny grey instanced spheres on randomized orbits.
        // Visual only; not in the gravity sum (see Physics.swift).
        var rng = SystemRandomNumberGenerator()
        let beltMeanOmega: Float = (2 * .pi) /
            (Tuning.Exploration.beltPeriodDays * 86_400)
        let beltJitter = Tuning.Exploration.beltSpeedJitter
        let beltMaterial: RealityKit.Material = {
            var mat = PhysicallyBasedMaterial()
            mat.baseColor = .init(tint: .init(red: 0.55, green: 0.50, blue: 0.45, alpha: 1.0))
            mat.roughness = .init(floatLiteral: 0.95)
            mat.metallic = .init(floatLiteral: 0.0)
            return mat
        }()
        for _ in 0..<Tuning.Exploration.beltCount {
            let beltRange = Tuning.Exploration.beltInnerAU...Tuning.Exploration.beltOuterAU
            let radius = Float.random(in: beltRange, using: &rng) * Tuning.Exploration.auMeters
            let phase = Float.random(in: 0...(2 * .pi), using: &rng)
            let speedFactor = 1.0 + Float.random(in: -beltJitter...beltJitter, using: &rng)
            let rockRange = Tuning.Exploration.beltVisualRadiusMin...Tuning.Exploration.beltVisualRadiusMax
            let rockRadius = Float.random(in: rockRange, using: &rng)
            let mesh = MeshResource.generateSphere(radius: rockRadius)
            let rock = ModelEntity(mesh: mesh, materials: [beltMaterial])
            worldAnchor.addChild(rock)
            beltEntities.append((rock, radius, beltMeanOmega * speedFactor, phase))
        }

        // Monkey — small bright marker. Reusing the existing player sprite
        // would require texture loading; v1 uses a primitive so the focus
        // is on physics, not art.
        let monkeyMesh = MeshResource.generateSphere(radius: 0.18)
        var monkeyMat = UnlitMaterial(color: .init(red: 1.0, green: 0.85, blue: 0.30, alpha: 1.0))
        monkeyMat.blending = .opaque
        let monkeyModel = ModelEntity(mesh: monkeyMesh, materials: [monkeyMat])
        monkeyEntity.addChild(monkeyModel)
        // Forward indicator — a thin cylinder pointing along +Z so the
        // player can read the ship's heading at a glance.
        let nose = ModelEntity(
            mesh: .generateCylinder(height: 0.35, radius: 0.05),
            materials: [UnlitMaterial(color: .white)]
        )
        nose.transform.rotation = simd_quatf(angle: .pi / 2, axis: SIMD3<Float>(1, 0, 0))
        nose.position = SIMD3<Float>(0, 0, 0.25)
        monkeyEntity.addChild(nose)
        worldAnchor.addChild(monkeyEntity)

        // Spawn between Earth and Mars on a circular orbit around the Sun.
        // Initial velocity matches v_circular = sqrt(G·M_sun / r) so the
        // monkey settles into a stable cruise — the player has time to
        // look around before deciding which planet to visit. Far enough
        // from any body that no single one dominates immediately.
        let spawnR = Tuning.Exploration.spawnRadiusAU * Tuning.Exploration.auMeters
        // Place behind Earth along the +Z axis so Earth (which spawns near
        // +X due to phaseAtT0 = 2.4 rad ≈ 137°) is visible across the
        // play area, not hidden behind the camera.
        let spawnDir = SIMD3<Float>(0, 0, 1)
        monkeyPosition = spawnDir * spawnR
        let vCircular = sqrt(Tuning.Exploration.G * Tuning.Exploration.sunMass / spawnR)
        let tangent = SIMD3<Float>(1, 0, 0)
        monkeyVelocity = tangent * vCircular
        // Face roughly toward the Sun so the inner system fills the view
        // on entry rather than empty space.
        cameraYaw = .pi   // look in -Z (toward the Sun)
        cameraPitch = 0

        // Camera rides as a child of the monkey so the chase offset is
        // local-space. Look stick rotates the monkey itself (and thus the
        // camera) about world Y / local X — see step().
        cameraEntity.position = SIMD3<Float>(0,
                                              Tuning.Exploration.cameraUpOffset,
                                              -Tuning.Exploration.cameraBackOffset)
        // Camera looks +Z (forward, toward the monkey's nose). RealityKit's
        // default forward is -Z, so we rotate 180° to flip.
        cameraEntity.transform.rotation =
            simd_quatf(angle: .pi, axis: SIMD3<Float>(0, 1, 0))
        monkeyEntity.addChild(cameraEntity)

        // Seed gravity so the first Verlet step has a non-degenerate aOld.
        lastGravity = Physics.gravityAcceleration(at: monkeyPosition,
                                                  from: bodies,
                                                  atTime: 0)
        lastUpdateTimestamp = 0
        simTime = 0
        physicsAccumulator = 0

        return worldAnchor
    }

    // MARK: - Per-frame update

    /// Called every render frame from `RealityView.update`. Advances Kepler
    /// orbits, accumulates a fixed-timestep physics loop, and refreshes
    /// camera + telemetry. All under @MainActor since we touch entities.
    func step(deltaSeconds dt: TimeInterval) {
        // Clamp wall-clock dt so a tab-switch / breakpoint doesn't spiral
        // the substep loop. ~1/15 s is enough headroom for a hiccup, not
        // enough to fast-forward through gravity wells.
        let frameDt = Float(min(max(dt, 0), 1.0 / 15.0))

        // Advance compressed simulation time and update planets first so
        // the gravity sum sees their current positions.
        let compressedDt = frameDt * Tuning.Exploration.timeCompression
        simTime += compressedDt
        updatePlanetEntities()
        updateBeltEntities()

        // Apply look stick to camera orientation each frame (continuous,
        // not stepped — feel is better at render rate than physics rate).
        applyLookStick(dt: frameDt)

        // Physics integration loop. Multiple fixed substeps per render
        // frame, capped to prevent the spiral-of-death when the GPU stalls.
        physicsAccumulator += frameDt
        let fixedDt = Tuning.Exploration.fixedDt
        var steps = 0
        let maxSteps = Tuning.Exploration.maxSubstepsPerFrame
        while physicsAccumulator >= fixedDt && steps < maxSteps {
            stepPhysics(dt: fixedDt)
            physicsAccumulator -= fixedDt
            steps += 1
        }
        // Drain any leftover accumulator if we hit the substep cap so the
        // next frame doesn't immediately exceed it again.
        if steps >= maxSteps {
            physicsAccumulator = 0
        }

        // Push the integrated position onto the monkey entity. Orientation
        // came from the look stick already.
        monkeyEntity.position = monkeyPosition

        updateTelemetry()
    }

    // MARK: - Physics step (one fixed substep)

    private func stepPhysics(dt: Float) {
        // Thrust: left stick acts in the monkey's local frame. X = strafe,
        // Y = forward/back. (SwiftUI joystick y is +up on screen → +forward.)
        let stick = leftStick
        let monkeyRot = monkeyEntity.transform.rotation
        let localX = monkeyRot.act(SIMD3<Float>(1, 0, 0))
        let localZ = monkeyRot.act(SIMD3<Float>(0, 0, 1))
        let thrust = (localX * stick.x + localZ * stick.y) *
                     Tuning.Exploration.thrustAcceleration

        // Velocity Verlet under combined gravity + thrust. See Physics.swift.
        let (pNew, vNew, gNew) = Physics.velocityVerletStep(
            position: monkeyPosition,
            velocity: monkeyVelocity,
            previousAcceleration: lastGravity,
            externalAcceleration: thrust,
            bodies: bodies,
            atTime: simTime,
            dt: dt
        )
        monkeyPosition = pNew
        // Clamp top speed so an aggressive slingshot doesn't fling the ship
        // out of the playable region.
        var v = vNew
        let speed = simd_length(v)
        if speed > Tuning.Exploration.maxSpeed {
            v *= Tuning.Exploration.maxSpeed / speed
        }
        monkeyVelocity = v
        lastGravity = gNew
    }

    // MARK: - Look stick → monkey orientation

    private func applyLookStick(dt: Float) {
        let yawDelta = -rightStick.x * Tuning.Exploration.lookYawRate * dt
        let pitchDelta = -rightStick.y * Tuning.Exploration.lookPitchRate * dt
        cameraYaw += yawDelta
        cameraPitch += pitchDelta
        let clamp = Tuning.Exploration.pitchClamp
        if cameraPitch > clamp { cameraPitch = clamp }
        if cameraPitch < -clamp { cameraPitch = -clamp }
        let qYaw = simd_quatf(angle: cameraYaw, axis: SIMD3<Float>(0, 1, 0))
        let qPitch = simd_quatf(angle: cameraPitch, axis: SIMD3<Float>(1, 0, 0))
        monkeyEntity.transform.rotation = qYaw * qPitch
    }

    // MARK: - Planet / belt visuals

    private func updatePlanetEntities() {
        for (i, body) in bodies.enumerated() {
            bodyEntities[i].position = body.position(at: simTime)
        }
    }

    private func updateBeltEntities() {
        for i in 0..<beltEntities.count {
            let rock = beltEntities[i]
            let theta = rock.phase + rock.speed * simTime
            beltEntities[i].entity.position = SIMD3<Float>(
                rock.radius * cos(theta), 0, rock.radius * sin(theta)
            )
        }
    }

    // MARK: - Telemetry

    private func updateTelemetry() {
        let speed = simd_length(monkeyVelocity)
        var nearest: (name: String, dist: Float) = ("—", .greatestFiniteMagnitude)
        for b in bodies {
            let d = simd_distance(monkeyPosition, b.position(at: simTime))
            if d < nearest.dist {
                nearest = (b.name, d)
            }
        }
        telemetry = Telemetry(
            speedMps: speed,
            nearestBody: nearest.name,
            nearestDistance: nearest.dist,
            gMagnitude: simd_length(lastGravity)
        )
    }

    // MARK: - Solar system construction

    private func makeInnerSolarSystem() -> [Body] {
        let auM = Tuning.Exploration.auMeters
        let boost = Tuning.Exploration.planetMassBoost
        let sunMass = Tuning.Exploration.sunMass
        // Real periods given in days → seconds for Kepler propagator.
        let dayToSec: Float = 86_400

        return [
            Body(
                name: "Sun",
                mass: sunMass,
                visualRadius: Tuning.Exploration.sunVisualRadius,
                color: SIMD3<Float>(1.0, 0.90, 0.55),
                orbit: nil
            ),
            Body(
                name: "Mercury",
                mass: sunMass * Tuning.Exploration.mercuryMassRatio * boost,
                visualRadius: Tuning.Exploration.mercuryVisualRadius,
                color: SIMD3<Float>(0.65, 0.55, 0.45),
                orbit: KeplerOrbit(
                    semiMajor: Tuning.Exploration.mercuryOrbitAU * auM,
                    periodSeconds: Tuning.Exploration.mercuryPeriodDays * dayToSec,
                    phaseAtT0: 0.0
                )
            ),
            Body(
                name: "Venus",
                mass: sunMass * Tuning.Exploration.venusMassRatio * boost,
                visualRadius: Tuning.Exploration.venusVisualRadius,
                color: SIMD3<Float>(0.95, 0.82, 0.55),
                orbit: KeplerOrbit(
                    semiMajor: Tuning.Exploration.venusOrbitAU * auM,
                    periodSeconds: Tuning.Exploration.venusPeriodDays * dayToSec,
                    phaseAtT0: 1.2
                )
            ),
            Body(
                name: "Earth",
                mass: sunMass * Tuning.Exploration.earthMassRatio * boost,
                visualRadius: Tuning.Exploration.earthVisualRadius,
                color: SIMD3<Float>(0.30, 0.55, 0.85),
                orbit: KeplerOrbit(
                    semiMajor: Tuning.Exploration.earthOrbitAU * auM,
                    periodSeconds: Tuning.Exploration.earthPeriodDays * dayToSec,
                    phaseAtT0: 2.4
                )
            ),
            Body(
                name: "Mars",
                mass: sunMass * Tuning.Exploration.marsMassRatio * boost,
                visualRadius: Tuning.Exploration.marsVisualRadius,
                color: SIMD3<Float>(0.85, 0.40, 0.30),
                orbit: KeplerOrbit(
                    semiMajor: Tuning.Exploration.marsOrbitAU * auM,
                    periodSeconds: Tuning.Exploration.marsPeriodDays * dayToSec,
                    phaseAtT0: 4.0
                )
            ),
        ]
    }

    private func bodyMaterial(for body: Body) -> RealityKit.Material {
        // Sun: unlit + bright so it self-illuminates instead of going dark
        // on its own night side (it doesn't have one).
        if body.orbit == nil {
            return UnlitMaterial(color: .init(
                red: CGFloat(body.color.x),
                green: CGFloat(body.color.y),
                blue: CGFloat(body.color.z),
                alpha: 1.0))
        }
        var mat = PhysicallyBasedMaterial()
        mat.baseColor = .init(tint: .init(
            red: CGFloat(body.color.x),
            green: CGFloat(body.color.y),
            blue: CGFloat(body.color.z),
            alpha: 1.0))
        mat.roughness = .init(floatLiteral: 0.85)
        mat.metallic = .init(floatLiteral: 0.0)
        return mat
    }
}
