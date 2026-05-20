//
//  Starfield.swift
//  GalaxyMonkey
//
//  Two-layer parallax starfield. Stars are drawn as small dots and shifted
//  opposite to the player's velocity to sell motion through space.
//

import SpriteKit
import UIKit

final class Starfield {

    private let layer1 = SKNode()
    private let layer2 = SKNode()
    private let bounds: CGRect

    init(scene: SKScene) {
        bounds = CGRect(origin: .zero, size: scene.size)
        layer1.zPosition = -50
        layer2.zPosition = -40
        scene.addChild(layer1)
        scene.addChild(layer2)
        populate(layer: layer1, count: Tuning.Starfield.layer1Count, size: 1.4, alpha: 0.65)
        populate(layer: layer2, count: Tuning.Starfield.layer2Count, size: 2.2, alpha: 0.9)
    }

    func update(dt: TimeInterval, playerVelocity: CGVector) {
        let dtF = CGFloat(dt)
        shift(layer: layer1, by: CGVector(dx: -playerVelocity.dx * Tuning.Starfield.layer1Speed * dtF,
                                          dy: -playerVelocity.dy * Tuning.Starfield.layer1Speed * dtF))
        shift(layer: layer2, by: CGVector(dx: -playerVelocity.dx * Tuning.Starfield.layer2Speed * dtF,
                                          dy: -playerVelocity.dy * Tuning.Starfield.layer2Speed * dtF))
    }

    private func shift(layer: SKNode, by v: CGVector) {
        for child in layer.children {
            var p = child.position
            p.x += v.dx
            p.y += v.dy
            if p.x < bounds.minX { p.x += bounds.width }
            if p.x > bounds.maxX { p.x -= bounds.width }
            if p.y < bounds.minY { p.y += bounds.height }
            if p.y > bounds.maxY { p.y -= bounds.height }
            child.position = p
        }
    }

    private func populate(layer: SKNode, count: Int, size: CGFloat, alpha: CGFloat) {
        for _ in 0..<count {
            let dot = SKShapeNode(circleOfRadius: size)
            dot.fillColor = UIColor(white: 1.0, alpha: alpha)
            dot.strokeColor = .clear
            dot.position = CGPoint(x: .random(in: bounds.minX...bounds.maxX),
                                   y: .random(in: bounds.minY...bounds.maxY))
            layer.addChild(dot)
        }
    }
}
