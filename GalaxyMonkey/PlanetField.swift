//
//  PlanetField.swift
//  GalaxyMonkey
//
//  Parallax planet backdrop. Sits between the two starfield layers in z, so
//  reads as bodies floating in deep space behind the dogfight. Drifts opposite
//  to the player's velocity at a slower factor than the starfield's near layer.
//

import SpriteKit
import UIKit

final class PlanetField {

    private struct Layer {
        let node: SKNode
        let parallaxFactor: CGFloat
    }

    private let layers: [Layer]
    private let bounds: CGRect

    init(scene: SKScene) {
        let b = CGRect(origin: .zero, size: scene.size)
        bounds = b

        // Pool of available planet sprites. We sample without replacement per
        // layer so the same planet doesn't appear twice in one band.
        let palette: [Sprite] = [.sun, .mercury, .venus, .earth, .mars,
                                  .jupiter, .saturn, .uranus, .neptune]
        var available = palette

        let factors = Tuning.VFX.planetParallaxFactors
        let zPositions = Tuning.VFX.planetZPositions
        let perLayer = Tuning.VFX.planetsPerLayer

        var built: [Layer] = []
        for layerIndex in 0..<factors.count {
            let node = SKNode()
            node.zPosition = zPositions[layerIndex]
            scene.addChild(node)

            for _ in 0..<perLayer {
                guard !available.isEmpty else { break }
                let pick = available.remove(at: Int.random(in: 0..<available.count))
                if let planet = PlanetField.makePlanet(sprite: pick, bounds: b) {
                    node.addChild(planet)
                }
            }
            built.append(Layer(node: node, parallaxFactor: factors[layerIndex]))
        }
        self.layers = built
    }

    func update(dt: TimeInterval, playerVelocity: CGVector) {
        let dtF = CGFloat(dt)
        for layer in layers {
            let dx = -playerVelocity.dx * layer.parallaxFactor * dtF
            let dy = -playerVelocity.dy * layer.parallaxFactor * dtF
            for child in layer.node.children {
                var p = child.position
                p.x += dx
                p.y += dy
                // Wrap with padding so planets don't pop in mid-frame.
                let pad: CGFloat = 80
                if p.x < bounds.minX - pad { p.x += bounds.width + pad * 2 }
                if p.x > bounds.maxX + pad { p.x -= bounds.width + pad * 2 }
                if p.y < bounds.minY - pad { p.y += bounds.height + pad * 2 }
                if p.y > bounds.maxY + pad { p.y -= bounds.height + pad * 2 }
                child.position = p
            }
        }
    }

    private static func makePlanet(sprite: Sprite, bounds: CGRect) -> SKNode? {
        guard let tex = SpriteCatalog.texture(for: sprite) else { return nil }
        let node = SKSpriteNode(texture: tex)
        let scale = CGFloat.random(in: Tuning.VFX.planetScaleMin...Tuning.VFX.planetScaleMax)
        node.setScale(scale)
        node.position = CGPoint(x: .random(in: bounds.minX...bounds.maxX),
                                y: .random(in: bounds.minY...bounds.maxY))
        node.alpha = 0.85

        let periodMin = Tuning.VFX.planetRotationPeriodMin
        let periodMax = Tuning.VFX.planetRotationPeriodMax
        let period = TimeInterval.random(in: periodMin...periodMax)
        let direction: CGFloat = Bool.random() ? 1 : -1
        let spin = SKAction.rotate(byAngle: direction * 2 * .pi, duration: period)
        node.run(SKAction.repeatForever(spin))

        return node
    }
}
