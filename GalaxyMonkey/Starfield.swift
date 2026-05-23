//
//  Starfield.swift
//  GalaxyMonkey
//
//  Two-layer parallax starfield, baked. Each layer is a single SKTexture
//  containing N white dots; we tile it 3×3 around the camera so it wraps
//  seamlessly as the camera moves through the universe. Shifts opposite to
//  the camera (not the player) — the deadzone follow means the camera can
//  be moving while the player is still, and vice versa.
//

import SpriteKit
import UIKit

final class Starfield {

    private final class Layer {
        let root: SKNode
        var tiles: [SKSpriteNode]
        let parallaxFactor: CGFloat
        var tileSize: CGSize
        var offset: CGPoint = .zero        // accumulated screen-space shift

        init(root: SKNode, tiles: [SKSpriteNode], factor: CGFloat, tileSize: CGSize) {
            self.root = root
            self.tiles = tiles
            self.parallaxFactor = factor
            self.tileSize = tileSize
        }
    }

    private var layers: [Layer] = []
    private weak var parent: SKNode?
    private var viewSize: CGSize

    /// `parent` is the SKCameraNode (or any camera-attached node). Stars render
    /// in screen-space so they wrap seamlessly as the camera moves.
    init(parent: SKNode, viewSize: CGSize) {
        self.parent = parent
        self.viewSize = viewSize
        rebuild(viewSize: viewSize)
    }

    /// `cameraDelta` is camera.position - lastCameraPosition (world space).
    /// Stars shift in screen space by `-cameraDelta * (1 - parallaxFactor)`:
    /// factor=0 keeps stars locked to world (max parallax), factor=1 locks to
    /// screen. We then wrap each tile so the 3×3 grid stays centered on the
    /// camera origin.
    func update(dt: TimeInterval, cameraDelta: CGVector) {
        for layer in layers {
            let factor = layer.parallaxFactor
            layer.offset.x -= cameraDelta.dx * (1 - factor)
            layer.offset.y -= cameraDelta.dy * (1 - factor)

            // Wrap so accumulated offset stays inside [-tileSize, tileSize].
            let tw = layer.tileSize.width
            let th = layer.tileSize.height
            if tw > 0 {
                while layer.offset.x >  tw { layer.offset.x -= tw }
                while layer.offset.x < -tw { layer.offset.x += tw }
            }
            if th > 0 {
                while layer.offset.y >  th { layer.offset.y -= th }
                while layer.offset.y < -th { layer.offset.y += th }
            }

            // 3×3 grid of tiles, centered on the camera origin (the layer's
            // root position is .zero in camera-local space). Each tile takes
            // its base grid cell + the accumulated offset.
            var i = 0
            for gy in -1...1 {
                for gx in -1...1 {
                    guard i < layer.tiles.count else { break }
                    layer.tiles[i].position = CGPoint(
                        x: CGFloat(gx) * tw + layer.offset.x,
                        y: CGFloat(gy) * th + layer.offset.y
                    )
                    i += 1
                }
            }
        }
    }

    /// Rebake tiles (e.g., after rotation / split-view size change).
    func rebuild(viewSize: CGSize) {
        self.viewSize = viewSize
        // Remove any prior layers from the parent.
        for layer in layers { layer.root.removeFromParent() }
        layers.removeAll()

        guard let parent else { return }

        // Layer 1: smaller dots, slow parallax (deep).
        let l1 = makeLayer(parent: parent,
                           dotCount: Tuning.Starfield.layer1Count,
                           dotRadius: 1.4,
                           dotAlpha: 0.65,
                           parallaxFactor: Tuning.Starfield.layer1Speed,
                           zPosition: -50)
        // Layer 2: bigger, brighter, less parallax (closer).
        let l2 = makeLayer(parent: parent,
                           dotCount: Tuning.Starfield.layer2Count,
                           dotRadius: 2.2,
                           dotAlpha: 0.95,
                           parallaxFactor: Tuning.Starfield.layer2Speed,
                           zPosition: -49)
        layers = [l1, l2]

        installTwinkle(parent: parent, viewSize: viewSize)
    }

    /// Bright screen-locked twinkle stars layered on top of the baked
    /// starfield. Each dot fades between full alpha and `twinkleAlphaLow`
    /// on its own random period so the cluster pulses asynchronously.
    /// These don't parallax — they're "near" stars locked to the
    /// viewport, which sells the depth contrast with the baked layers.
    private func installTwinkle(parent: SKNode, viewSize: CGSize) {
        let radius = Tuning.Starfield.twinkleRadius
        for _ in 0..<Tuning.Starfield.twinkleCount {
            let dot = SKShapeNode(circleOfRadius: radius)
            dot.fillColor = .white
            dot.strokeColor = .clear
            dot.zPosition = -48.5
            // Random position across the viewport in camera-local coords.
            dot.position = CGPoint(
                x: CGFloat.random(in: -viewSize.width / 2 ... viewSize.width / 2),
                y: CGFloat.random(in: -viewSize.height / 2 ... viewSize.height / 2))
            parent.addChild(dot)

            let period = TimeInterval.random(in: Tuning.Starfield.twinklePeriodMin...Tuning.Starfield.twinklePeriodMax)
            let half = period / 2
            let dim = SKAction.fadeAlpha(to: Tuning.Starfield.twinkleAlphaLow, duration: half)
            dim.timingMode = .easeInEaseOut
            let bright = SKAction.fadeAlpha(to: 1.0, duration: half)
            bright.timingMode = .easeInEaseOut
            // Random initial delay so the cluster desynchronizes.
            let stagger = SKAction.wait(forDuration: TimeInterval.random(in: 0...period))
            dot.run(SKAction.sequence([stagger,
                                       SKAction.repeatForever(SKAction.sequence([dim, bright]))]))
        }
    }

    private func makeLayer(parent: SKNode, dotCount: Int,
                           dotRadius: CGFloat, dotAlpha: CGFloat,
                           parallaxFactor: CGFloat, zPosition: CGFloat) -> Layer {
        let tileSize = CGSize(width: max(64, viewSize.width),
                              height: max(64, viewSize.height))
        let texture = bakeStarTexture(size: tileSize,
                                      dotCount: dotCount,
                                      dotRadius: dotRadius,
                                      dotAlpha: dotAlpha)

        let root = SKNode()
        root.position = .zero
        root.zPosition = zPosition
        parent.addChild(root)

        var tiles: [SKSpriteNode] = []
        for _ in 0..<9 {
            let s = SKSpriteNode(texture: texture)
            s.size = tileSize
            s.anchorPoint = CGPoint(x: 0.5, y: 0.5)
            root.addChild(s)
            tiles.append(s)
        }

        let layer = Layer(root: root, tiles: tiles,
                          factor: parallaxFactor, tileSize: tileSize)
        // Position once so first frame is sensible even before any update.
        layer.offset = .zero
        var i = 0
        for gy in -1...1 {
            for gx in -1...1 {
                tiles[i].position = CGPoint(x: CGFloat(gx) * tileSize.width,
                                             y: CGFloat(gy) * tileSize.height)
                i += 1
            }
        }
        return layer
    }

    private func bakeStarTexture(size: CGSize, dotCount: Int,
                                 dotRadius: CGFloat, dotAlpha: CGFloat) -> SKTexture {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = UIScreen.main.scale
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let image = renderer.image { ctx in
            let cg = ctx.cgContext
            cg.setFillColor(UIColor(white: 1, alpha: dotAlpha).cgColor)
            for _ in 0..<dotCount {
                let x = CGFloat.random(in: 0..<size.width)
                let y = CGFloat.random(in: 0..<size.height)
                let rect = CGRect(x: x - dotRadius, y: y - dotRadius,
                                  width: dotRadius * 2, height: dotRadius * 2)
                cg.fillEllipse(in: rect)
            }
        }
        let tex = SKTexture(image: image)
        tex.filteringMode = .nearest
        return tex
    }
}
