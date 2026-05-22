//
//  PlanetField.swift
//  GalaxyMonkey
//
//  Real solar system. The Sun sits at `worldCenter`; each planet rides a
//  tilted elliptical orbit (semiMinor = orbitTiltY × semiMajor) around it,
//  with a small per-ring inclination so the system reads in 3D rather than
//  flat. Planets also spin slowly on their own axis. No parallax — these are
//  real world-space objects, not decoration.
//

import SpriteKit
import UIKit

final class PlanetField {

    private final class Orbit {
        let node: SKSpriteNode
        let radius: CGFloat
        var phase: CGFloat
        let angularSpeed: CGFloat
        let inclinationSin: CGFloat
        let inclinationCos: CGFloat

        init(node: SKSpriteNode, radius: CGFloat, phase: CGFloat,
             angularSpeed: CGFloat, inclination: CGFloat) {
            self.node = node
            self.radius = radius
            self.phase = phase
            self.angularSpeed = angularSpeed
            self.inclinationSin = sin(inclination)
            self.inclinationCos = cos(inclination)
        }
    }

    private let root: SKNode
    private var orbits: [Orbit] = []

    init(scene: SKScene) {
        let root = SKNode()
        root.position = CGPoint(x: scene.size.width / 2, y: scene.size.height / 2)
        scene.addChild(root)
        self.root = root

        installSun()
        installPlanet(sprite: .mercury, diameter: Tuning.World.mercuryDiameter,
                      radius: Tuning.World.orbitMercury, angularSpeed: Tuning.World.angSpeedMercury,
                      zPosition: -45)
        installPlanet(sprite: .venus, diameter: Tuning.World.venusDiameter,
                      radius: Tuning.World.orbitVenus, angularSpeed: Tuning.World.angSpeedVenus,
                      zPosition: -44)
        installPlanet(sprite: .earth, diameter: Tuning.World.earthDiameter,
                      radius: Tuning.World.orbitEarth, angularSpeed: Tuning.World.angSpeedEarth,
                      zPosition: -43)
        installPlanet(sprite: .mars, diameter: Tuning.World.marsDiameter,
                      radius: Tuning.World.orbitMars, angularSpeed: Tuning.World.angSpeedMars,
                      zPosition: -42)
        installPlanet(sprite: .jupiter, diameter: Tuning.World.jupiterDiameter,
                      radius: Tuning.World.orbitJupiter, angularSpeed: Tuning.World.angSpeedJupiter,
                      zPosition: -41)
        installPlanet(sprite: .saturn, diameter: Tuning.World.saturnDiameter,
                      radius: Tuning.World.orbitSaturn, angularSpeed: Tuning.World.angSpeedSaturn,
                      zPosition: -40)
        installPlanet(sprite: .uranus, diameter: Tuning.World.uranusDiameter,
                      radius: Tuning.World.orbitUranus, angularSpeed: Tuning.World.angSpeedUranus,
                      zPosition: -39)
        installPlanet(sprite: .neptune, diameter: Tuning.World.neptuneDiameter,
                      radius: Tuning.World.orbitNeptune, angularSpeed: Tuning.World.angSpeedNeptune,
                      zPosition: -38)
    }

    func update(dt: TimeInterval) {
        let dtF = CGFloat(dt)
        for orbit in orbits {
            orbit.phase += orbit.angularSpeed * dtF
            let cx = cos(orbit.phase) * orbit.radius
            let cy = sin(orbit.phase) * orbit.radius * Tuning.World.orbitTiltY
            // Rotate the orbit plane by per-ring inclination so different
            // rings aren't all aligned to the same axis.
            let x = orbit.inclinationCos * cx - orbit.inclinationSin * cy
            let y = orbit.inclinationSin * cx + orbit.inclinationCos * cy
            orbit.node.position = CGPoint(x: x, y: y)
        }
    }

    // MARK: - Build

    private func installSun() {
        guard let tex = SpriteCatalog.texture(for: .sun) else { return }
        let sun = SKSpriteNode(texture: tex)
        let maxDim = max(tex.size().width, tex.size().height)
        if maxDim > 0 { sun.setScale(Tuning.World.sunDisplayDiameter / maxDim) }
        sun.position = .zero
        sun.zPosition = -46
        root.addChild(sun)

        let spin = SKAction.rotate(byAngle: 2 * .pi,
                                   duration: TimeInterval.random(in: Tuning.VFX.planetRotationPeriodMin...Tuning.VFX.planetRotationPeriodMax))
        sun.run(SKAction.repeatForever(spin))
    }

    private func installPlanet(sprite: Sprite, diameter: CGFloat,
                               radius: CGFloat, angularSpeed: CGFloat,
                               zPosition: CGFloat) {
        guard let tex = SpriteCatalog.texture(for: sprite) else { return }
        let node = SKSpriteNode(texture: tex)
        let maxDim = max(tex.size().width, tex.size().height)
        if maxDim > 0 { node.setScale(diameter / maxDim) }
        node.zPosition = zPosition
        node.alpha = 0.95
        root.addChild(node)

        let phase = CGFloat.random(in: 0..<(2 * .pi))
        let inclination = CGFloat.random(in: -Tuning.World.inclinationRange...Tuning.World.inclinationRange)

        let period = TimeInterval.random(in: Tuning.VFX.planetRotationPeriodMin...Tuning.VFX.planetRotationPeriodMax)
        let direction: CGFloat = Bool.random() ? 1 : -1
        node.run(SKAction.repeatForever(SKAction.rotate(byAngle: direction * 2 * .pi, duration: period)))

        orbits.append(Orbit(node: node, radius: radius, phase: phase,
                            angularSpeed: angularSpeed, inclination: inclination))
    }
}
