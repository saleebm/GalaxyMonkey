//
//  PlanetField.swift
//  GalaxyMonkey
//
//  Real solar system. The Sun sits at `worldCenter`; every planet starts
//  at phase 0 so the game opens on a clean planetary conjunction line,
//  then drifts apart naturally as each planet orbits at its own
//  Kepler-ish angular speed. Orbits are co-planar (no per-orbit
//  inclination) and ride the same tilted ecliptic so the system reads
//  as a single flat plane viewed at an angle. An asteroid belt fills
//  the Mars→Jupiter gap with rocks that share a belt-average speed
//  plus small per-rock jitter so the belt churns rather than locks.
//
//  Each planet's "pivot" is an unscaled SKNode that carries the orbit;
//  the planet sprite (scaled) and any moons (also unscaled) ride as its
//  children, so moons orbit in pivot-local points without being squashed
//  by the planet sprite's setScale. Orbital path rings give an orrery
//  read of the layout.
//

import SpriteKit
import UIKit

final class PlanetField {

    private final class Orbit {
        let node: SKNode
        let radius: CGFloat
        var phase: CGFloat
        let angularSpeed: CGFloat
        /// Set on planet orbits — the body sprite that receives a per-frame
        /// `a_sun_angle` attribute for the terminator shader. Moons,
        /// asteroids, and the sun leave this nil.
        weak var bodySprite: SKSpriteNode?

        init(node: SKNode, radius: CGFloat, phase: CGFloat, angularSpeed: CGFloat,
             bodySprite: SKSpriteNode? = nil) {
            self.node = node
            self.radius = radius
            self.phase = phase
            self.angularSpeed = angularSpeed
            self.bodySprite = bodySprite
        }
    }

    /// Shared day/night terminator shader. Applied to every planet's body
    /// sprite. Each sprite carries its own `a_sun_angle` value via
    /// `attributeValues`, which `update(dt:)` refreshes each frame to the
    /// sun's direction in the sprite's local (rotated) frame.
    private static let terminatorShader: SKShader = {
        let source = """
        void main() {
            vec4 base = texture2D(u_texture, v_tex_coord);
            vec2 centered = v_tex_coord - 0.5;
            vec2 sunDir = vec2(cos(a_sun_angle), sin(a_sun_angle));
            float d = dot(normalize(centered + vec2(1e-4)), sunDir);
            // Wider transition zone = softer terminator that reads as a
            // real atmospheric twilight band rather than a hard line.
            float lit = smoothstep(-0.4, 0.4, d);
            // 0.28 keeps the night side legible (planet still readable
            // as a sphere) while the day side gets a subtle boost.
            gl_FragColor = vec4(base.rgb * mix(0.28, 1.04, lit), base.a);
        }
        """
        let shader = SKShader(source: source)
        shader.attributes = [SKAttribute(name: "a_sun_angle", type: .float)]
        return shader
    }()

    private let root: SKNode
    private var orbits: [Orbit] = []
    private var cometCountdown: TimeInterval = Tuning.World.cometInitialDelay

    init(scene: SKScene) {
        let root = SKNode()
        root.position = CGPoint(x: scene.size.width / 2, y: scene.size.height / 2)
        scene.addChild(root)
        self.root = root

        installSun()
        // Orrery rings beneath everything orbital. Installed before the
        // planets so the rings render under planet bodies but above the
        // backdrop / starfield.
        installOrbitRing(radius: Tuning.World.orbitMercury)
        installOrbitRing(radius: Tuning.World.orbitVenus)
        installOrbitRing(radius: Tuning.World.orbitEarth)
        installOrbitRing(radius: Tuning.World.orbitMars)
        installOrbitRing(radius: Tuning.World.orbitJupiter)
        installOrbitRing(radius: Tuning.World.orbitSaturn)
        installOrbitRing(radius: Tuning.World.orbitUranus)
        installOrbitRing(radius: Tuning.World.orbitNeptune)
        _ = installPlanet(sprite: .mercury, diameter: Tuning.World.mercuryDiameter,
                          radius: Tuning.World.orbitMercury, angularSpeed: Tuning.World.angSpeedMercury,
                          zPosition: -45)
        _ = installPlanet(sprite: .venus, diameter: Tuning.World.venusDiameter,
                          radius: Tuning.World.orbitVenus, angularSpeed: Tuning.World.angSpeedVenus,
                          zPosition: -44)
        let earthPivot = installPlanet(sprite: .earth, diameter: Tuning.World.earthDiameter,
                                       radius: Tuning.World.orbitEarth, angularSpeed: Tuning.World.angSpeedEarth,
                                       zPosition: -43)
        installMoon(parent: earthPivot, sprite: .luna,
                    diameter: Tuning.World.Moons.lunaDiameter,
                    radius: Tuning.World.Moons.lunaRadius,
                    angularSpeed: Tuning.World.Moons.lunaSpeed)
        _ = installPlanet(sprite: .mars, diameter: Tuning.World.marsDiameter,
                          radius: Tuning.World.orbitMars, angularSpeed: Tuning.World.angSpeedMars,
                          zPosition: -42)
        installAsteroidBelt()
        let jupiterPivot = installPlanet(sprite: .jupiter, diameter: Tuning.World.jupiterDiameter,
                                         radius: Tuning.World.orbitJupiter, angularSpeed: Tuning.World.angSpeedJupiter,
                                         zPosition: -41)
        installMoon(parent: jupiterPivot, sprite: .io,
                    diameter: Tuning.World.Moons.ioDiameter,
                    radius: Tuning.World.Moons.ioRadius,
                    angularSpeed: Tuning.World.Moons.ioSpeed)
        installMoon(parent: jupiterPivot, sprite: .europa,
                    diameter: Tuning.World.Moons.europaDiameter,
                    radius: Tuning.World.Moons.europaRadius,
                    angularSpeed: Tuning.World.Moons.europaSpeed)
        installMoon(parent: jupiterPivot, sprite: .ganymede,
                    diameter: Tuning.World.Moons.ganymedeDiameter,
                    radius: Tuning.World.Moons.ganymedeRadius,
                    angularSpeed: Tuning.World.Moons.ganymedeSpeed)
        installMoon(parent: jupiterPivot, sprite: .callisto,
                    diameter: Tuning.World.Moons.callistoDiameter,
                    radius: Tuning.World.Moons.callistoRadius,
                    angularSpeed: Tuning.World.Moons.callistoSpeed)
        let saturnPivot = installPlanet(sprite: .saturn, diameter: Tuning.World.saturnDiameter,
                                        radius: Tuning.World.orbitSaturn, angularSpeed: Tuning.World.angSpeedSaturn,
                                        zPosition: -40)
        installMoon(parent: saturnPivot, sprite: .titan,
                    diameter: Tuning.World.Moons.titanDiameter,
                    radius: Tuning.World.Moons.titanRadius,
                    angularSpeed: Tuning.World.Moons.titanSpeed)
        _ = installPlanet(sprite: .uranus, diameter: Tuning.World.uranusDiameter,
                          radius: Tuning.World.orbitUranus, angularSpeed: Tuning.World.angSpeedUranus,
                          zPosition: -39)
        _ = installPlanet(sprite: .neptune, diameter: Tuning.World.neptuneDiameter,
                          radius: Tuning.World.orbitNeptune, angularSpeed: Tuning.World.angSpeedNeptune,
                          zPosition: -38)
    }

    func update(dt: TimeInterval) {
        let dtF = CGFloat(dt)
        for orbit in orbits {
            orbit.phase += orbit.angularSpeed * dtF
            let x = cos(orbit.phase) * orbit.radius
            let y = sin(orbit.phase) * orbit.radius * Tuning.World.orbitTiltY
            orbit.node.position = CGPoint(x: x, y: y)
            if let body = orbit.bodySprite {
                // Sun direction from planet to sun in world coords =
                // phase + π (the sun sits at world origin, the planet at
                // angle `phase` from it). The shader expects the angle in
                // the sprite's local frame, which is rotated by zRotation
                // — subtract that so the lit side stays world-fixed while
                // the planet's surface spins underneath.
                let sunLocal = Float(orbit.phase + .pi - body.zRotation)
                body.setValue(SKAttributeValue(float: sunLocal),
                              forAttribute: "a_sun_angle")
            }
        }
        cometCountdown -= dt
        if cometCountdown <= 0 {
            spawnComet()
            cometCountdown = TimeInterval.random(in: Tuning.World.cometSpawnIntervalMin...Tuning.World.cometSpawnIntervalMax)
        }
    }

    // MARK: - Build

    private func installOrbitRing(radius: CGFloat) {
        let tilt = Tuning.World.orbitTiltY
        let rect = CGRect(x: -radius, y: -radius * tilt,
                          width: radius * 2, height: radius * 2 * tilt)
        let ring = SKShapeNode(ellipseIn: rect)
        ring.strokeColor = UIColor.white.withAlphaComponent(Tuning.World.orbitRingAlpha)
        ring.lineWidth = Tuning.World.orbitRingLineWidth
        ring.fillColor = .clear
        ring.zPosition = -47
        root.addChild(ring)
    }

    private func installSun() {
        guard let tex = SpriteCatalog.texture(for: .sun) else { return }
        let maxDim = max(tex.size().width, tex.size().height)
        let sunScale: CGFloat = maxDim > 0 ? Tuning.World.sunDisplayDiameter / maxDim : 1

        // Corona: a heavily blurred copy of the sun, rasterized via
        // SKEffectNode. Gaussian blur extends the disk's alpha smoothly
        // past its silhouette and reaches zero before any rectangular
        // texture bound is visible — the failure mode the raw-sprite
        // attempt hit.
        let corona = SKEffectNode()
        corona.shouldRasterize = true
        corona.shouldEnableEffects = true
        corona.filter = CIFilter(name: "CIGaussianBlur",
                                 parameters: [kCIInputRadiusKey: Tuning.World.sunCoronaBlurRadius])
        corona.blendMode = .add
        corona.alpha = Tuning.World.sunCoronaAlpha
        corona.zPosition = -46.5
        corona.position = .zero
        let coronaSprite = SKSpriteNode(texture: tex)
        coronaSprite.setScale(sunScale)
        corona.addChild(coronaSprite)
        root.addChild(corona)

        let amp = Tuning.World.sunCoronaPulseAmplitude
        let half = Tuning.World.sunCoronaPulsePeriod / 2
        let up = SKAction.scale(by: 1 + amp, duration: half)
        up.timingMode = .easeInEaseOut
        let down = SKAction.scale(by: 1 / (1 + amp), duration: half)
        down.timingMode = .easeInEaseOut
        corona.run(SKAction.repeatForever(SKAction.sequence([up, down])))

        // Crisp sun in front of the corona.
        let sun = SKSpriteNode(texture: tex)
        sun.setScale(sunScale)
        sun.position = .zero
        sun.zPosition = -46
        root.addChild(sun)

        let spin = SKAction.rotate(byAngle: 2 * .pi,
                                   duration: TimeInterval.random(in: Tuning.VFX.planetRotationPeriodMin...Tuning.VFX.planetRotationPeriodMax))
        sun.run(SKAction.repeatForever(spin))
    }

    /// Installs a planet and returns its (unscaled) orbit pivot so callers
    /// can attach moons at planet-local coordinates without inheriting the
    /// planet sprite's scale.
    @discardableResult
    private func installPlanet(sprite: Sprite, diameter: CGFloat,
                               radius: CGFloat, angularSpeed: CGFloat,
                               zPosition: CGFloat) -> SKNode {
        let pivot = SKNode()
        pivot.zPosition = zPosition
        root.addChild(pivot)
        // phase = 0 → planet starts on the +X axis from the Sun. With every
        // planet doing the same, the game opens on a planetary conjunction
        // that then drifts apart naturally as inner planets pull ahead.
        orbits.append(Orbit(node: pivot, radius: radius, phase: 0, angularSpeed: angularSpeed))

        guard let tex = SpriteCatalog.texture(for: sprite) else { return pivot }
        let body = SKSpriteNode(texture: tex)
        let maxDim = max(tex.size().width, tex.size().height)
        if maxDim > 0 { body.setScale(diameter / maxDim) }
        body.alpha = 0.95
        // Day/night terminator. The shared shader reads `a_sun_angle` from
        // each sprite's own attributeValues, which update(dt:) refreshes
        // each frame to the sun direction in the sprite's local frame.
        body.shader = PlanetField.terminatorShader
        body.attributeValues = ["a_sun_angle": SKAttributeValue(float: 0)]
        pivot.addChild(body)

        let period = TimeInterval.random(in: Tuning.VFX.planetRotationPeriodMin...Tuning.VFX.planetRotationPeriodMax)
        let direction: CGFloat = Bool.random() ? 1 : -1
        body.run(SKAction.repeatForever(SKAction.rotate(byAngle: direction * 2 * .pi, duration: period)))

        // Attach the body so the orbit-tick can refresh the shader's
        // sun-angle attribute. Stored as the *last* appended orbit's body.
        orbits.last?.bodySprite = body

        return pivot
    }

    /// Installs a moon as a child of the given planet pivot. The moon's
    /// position is set each frame in the pivot's local (unscaled) frame,
    /// so the moon orbits the planet at `radius` points regardless of the
    /// planet sprite's scale.
    private func installMoon(parent: SKNode, sprite: Sprite, diameter: CGFloat,
                             radius: CGFloat, angularSpeed: CGFloat) {
        guard let tex = SpriteCatalog.texture(for: sprite) else { return }
        let moon = SKSpriteNode(texture: tex)
        let maxDim = max(tex.size().width, tex.size().height)
        if maxDim > 0 { moon.setScale(diameter / maxDim) }
        // Slightly above the planet body in pivot-local z so moons in the
        // foreground render in front of the planet they orbit. (Perfect
        // depth-correct occlusion would need orbit math beyond what
        // orbitTiltY squash captures.)
        moon.zPosition = 0.1
        parent.addChild(moon)

        let period = TimeInterval.random(in: Tuning.VFX.planetRotationPeriodMin...Tuning.VFX.planetRotationPeriodMax)
        let direction: CGFloat = Bool.random() ? 1 : -1
        moon.run(SKAction.repeatForever(SKAction.rotate(byAngle: direction * 2 * .pi, duration: period)))

        // Random starting phase so the moons don't all line up.
        let startPhase = CGFloat.random(in: 0..<(2 * .pi))
        orbits.append(Orbit(node: moon, radius: radius, phase: startPhase, angularSpeed: angularSpeed))
    }

    /// Spawn a single comet that streaks across the world on a straight
    /// chord, trailing particles. Despawns when its `SKAction.follow`
    /// completes. The trail emitter has `targetNode = root` so particles
    /// detach into world space behind the comet rather than being dragged
    /// along, which gives the streak look.
    private func spawnComet() {
        let r = Tuning.World.cometPathRadius
        let tilt = Tuning.World.orbitTiltY
        // Pick entry and exit points roughly opposite each other so the
        // chord crosses the visible play area, with some angular jitter.
        let entryAngle = CGFloat.random(in: 0..<(2 * .pi))
        let exitAngle = entryAngle + .pi
            + CGFloat.random(in: -Tuning.World.cometExitJitterRad...Tuning.World.cometExitJitterRad)
        let start = CGPoint(x: cos(entryAngle) * r, y: sin(entryAngle) * r * tilt)
        let end = CGPoint(x: cos(exitAngle) * r, y: sin(exitAngle) * r * tilt)

        let comet = SKNode()
        comet.position = start
        comet.zPosition = -42.5
        root.addChild(comet)

        let head = SKShapeNode(circleOfRadius: Tuning.World.cometHeadRadius)
        head.fillColor = .white
        head.strokeColor = .clear
        head.blendMode = .add
        comet.addChild(head)

        if let glowTex = SpriteCatalog.texture(for: .glow) {
            let trail = SKEmitterNode()
            trail.particleTexture = glowTex
            trail.particleBirthRate = Tuning.World.cometTrailBirthRate
            trail.particleLifetime = CGFloat(Tuning.World.cometTrailLifetime)
            trail.particleAlpha = 0.9
            trail.particleAlphaSpeed = -0.6
            trail.particleScale = 0.18
            trail.particleScaleSpeed = -0.08
            trail.particleColor = UIColor(red: 0.82, green: 0.95, blue: 1.0, alpha: 1)
            trail.particleColorBlendFactor = 0.85
            trail.particleBlendMode = .add
            trail.particleSpeed = 0
            // Detach particles into the world so they're left behind when
            // the comet moves on — no tail dragged with the head.
            trail.targetNode = root
            comet.addChild(trail)
        }

        let path = CGMutablePath()
        path.move(to: start)
        path.addLine(to: end)
        let duration = TimeInterval.random(in: Tuning.World.cometTravelDurationMin...Tuning.World.cometTravelDurationMax)
        let follow = SKAction.follow(path, asOffset: false, orientToPath: true, duration: duration)
        comet.run(SKAction.sequence([follow, .removeFromParent()]))
    }

    private func installAsteroidBelt() {
        let beltSpeed = Tuning.World.asteroidBeltAngularSpeed
        let beltJitter = Tuning.World.asteroidBeltSpeedJitter
        // Rock02 dropped — its elongated jagged shape + metallic iron streaks
        // read as a steel streak line at belt scale rather than a rock.
        let variants: [Sprite] = [.rock01, .rock03, .rock04]
        let rockTextures: [SKTexture] = variants.compactMap { SpriteCatalog.texture(for: $0) }
        for _ in 0..<Tuning.World.asteroidBeltCount {
            let radius = CGFloat.random(in: Tuning.World.asteroidBeltInnerRadius...Tuning.World.asteroidBeltOuterRadius)
            let phase = CGFloat.random(in: 0..<(2 * .pi))
            let diameter = CGFloat.random(in: Tuning.World.asteroidDiameterMin...Tuning.World.asteroidDiameterMax)
            let alpha = CGFloat.random(in: Tuning.World.asteroidAlphaMin...Tuning.World.asteroidAlphaMax)

            // Slight elliptical squash for visual variety; applied as a
            // multiplier on the base scale (not an overwrite) so a textured
            // rock stays small instead of getting blown back up to the
            // texture's native height. Shapes start at scale 1, so the
            // jitter is applied directly there.
            let yJitter = CGFloat.random(in: 0.7...1.0)

            let rock: SKNode
            if let tex = rockTextures.randomElement() {
                let sprite = SKSpriteNode(texture: tex)
                let maxDim = max(tex.size().width, tex.size().height)
                let baseScale: CGFloat = maxDim > 0 ? diameter / maxDim : 1
                sprite.xScale = baseScale
                sprite.yScale = baseScale * yJitter
                sprite.alpha = alpha
                rock = sprite
            } else {
                // Fallback when art is missing — grey shape circle.
                let grey = CGFloat.random(in: 0.42...0.78)
                let warmth = CGFloat.random(in: 0.85...0.98)
                let shape = SKShapeNode(circleOfRadius: diameter / 2)
                shape.fillColor = UIColor(red: grey, green: grey * warmth, blue: grey * warmth * 0.9, alpha: 1)
                shape.strokeColor = .clear
                shape.alpha = alpha
                shape.yScale = yJitter
                rock = shape
            }
            rock.zPosition = -41.5
            rock.zRotation = CGFloat.random(in: 0..<(2 * .pi))
            root.addChild(rock)
            let speed = beltSpeed + CGFloat.random(in: -beltJitter...beltJitter)
            orbits.append(Orbit(node: rock, radius: radius, phase: phase, angularSpeed: speed))
        }
    }
}
