//
//  ThrusterEmitter.swift
//  GalaxyMonkey
//
//  Real chemical-rocket exhaust plume rendered with SKEmitterNode. The
//  particle color sequence runs white-hot → orange → smoke so each particle
//  cools as it trails behind the ship. Caller positions the emitter behind
//  the hull and modulates particleBirthRate / particleSpeed each frame to
//  scale intensity from cruise idle up to full burst.
//
//  Native SpriteKit instead of a baked sprite animation because SpriteCook
//  refused to override the source ship's blue thruster nozzle color with
//  orange combustion flame.
//

import SpriteKit
import UIKit

enum ThrusterEmitter {

    private static let particleTexture: SKTexture = makeSoftDot(size: 32)

    static func make() -> SKEmitterNode {
        let e = SKEmitterNode()
        e.particleTexture = particleTexture

        // Cruise defaults — Player.update() modulates birthRate/speed each frame.
        e.particleBirthRate = 220
        e.particleLifetime = 0.45
        e.particleLifetimeRange = 0.15

        // Emit pointing left in local coords — negative X is the ship's rear
        // at default xScale=1. visual.xScale flipping when the ship turns to
        // face left automatically mirrors the emission direction.
        e.emissionAngle = .pi
        e.emissionAngleRange = .pi / 9       // ±20° plume spread

        e.particleSpeed = 200
        e.particleSpeedRange = 70

        // Vertical spread covers both rear nozzles.
        e.particlePositionRange = CGVector(dx: 2, dy: 14)

        // White-hot incandescent core → bright orange → deep red → smoke.
        // Real flame color ramp; additive blend below makes the early colors
        // read as glowing fire rather than flat fill.
        e.particleColor = .white
        e.particleColorBlendFactor = 1.0
        e.particleColorSequence = SKKeyframeSequence(
            keyframeValues: [
                UIColor(red: 1.00, green: 1.00, blue: 0.85, alpha: 1.0),
                UIColor(red: 1.00, green: 0.78, blue: 0.20, alpha: 1.0),
                UIColor(red: 1.00, green: 0.35, blue: 0.05, alpha: 1.0),
                UIColor(red: 0.40, green: 0.20, blue: 0.10, alpha: 1.0),
                UIColor(red: 0.18, green: 0.18, blue: 0.18, alpha: 1.0),
            ],
            times: [0.0, 0.2, 0.45, 0.75, 1.0]
        )

        e.particleScale = Tuning.VFX.thrustScale * 0.5
        e.particleScaleRange = 0.25
        e.particleScaleSpeed = -0.6          // shrinks as it cools

        e.particleAlpha = 1.0
        e.particleAlphaRange = 0.1
        e.particleAlphaSpeed = -2.2          // fade to invisible by end of life

        e.particleBlendMode = .add           // additive glow for the fire core

        return e
    }

    /// White radial-gradient dot used as the particle texture. Soft falloff
    /// keeps the fire-glow blending clean under additive composition.
    private static func makeSoftDot(size: CGFloat) -> SKTexture {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
        let img = renderer.image { ctx in
            let cg = ctx.cgContext
            let space = CGColorSpaceCreateDeviceRGB()
            let colors = [
                UIColor.white.cgColor,
                UIColor(white: 1, alpha: 0.6).cgColor,
                UIColor(white: 1, alpha: 0).cgColor,
            ]
            let gradient = CGGradient(colorsSpace: space,
                                      colors: colors as CFArray,
                                      locations: [0, 0.5, 1])!
            cg.drawRadialGradient(
                gradient,
                startCenter: CGPoint(x: size / 2, y: size / 2),
                startRadius: 0,
                endCenter: CGPoint(x: size / 2, y: size / 2),
                endRadius: size / 2,
                options: []
            )
        }
        return SKTexture(image: img)
    }
}
