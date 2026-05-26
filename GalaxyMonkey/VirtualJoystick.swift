//
//  VirtualJoystick.swift
//  GalaxyMonkey
//
//  Dynamic per-touch virtual joystick. The "center" is wherever the player
//  first touches on its side of the screen; the thumb follows the finger
//  clamped to a circle. Output vector is [-1, 1] x [-1, 1] with dead zone
//  applied. No output if no touch is active on this side. Visible only while
//  a finger is engaging the stick; alpha is intentionally very low so the
//  ring barely tints the gameplay underneath.
//

import SpriteKit
import UIKit

final class VirtualJoystick: SKNode {

    enum Side { case left, right }

    let side: Side
    private let baseRadius: CGFloat
    private let thumbRadius: CGFloat
    private let deadZone: CGFloat

    private var base: SKShapeNode!
    private var thumb: SKShapeNode!
    private var trackedTouch: UITouch?
    private var anchor: CGPoint = .zero

    /// Latest stick vector. .zero when no finger is down. Magnitude clamped to 1.
    private(set) var vector: CGVector = .zero

    /// True while a finger is engaging this stick.
    var isActive: Bool { trackedTouch != nil }

    init(side: Side,
         baseRadius: CGFloat = Tuning.Joystick.radius,
         thumbRadius: CGFloat = Tuning.Joystick.thumbRadius,
         deadZone: CGFloat = Tuning.Joystick.deadZone) {
        self.side = side
        self.baseRadius = baseRadius
        self.thumbRadius = thumbRadius
        self.deadZone = deadZone
        super.init()
        buildNodes()
        setVisible(false)
        zPosition = 1000
    }

    required init?(coder aDecoder: NSCoder) { fatalError("init(coder:) not implemented") }

    private func buildNodes() {
        let baseFill   = UIColor(white: 1.0, alpha: Tuning.Joystick.baseAlpha)
        let baseStroke = UIColor(white: 1.0, alpha: Tuning.Joystick.strokeAlpha)
        base = SKShapeNode(circleOfRadius: baseRadius)
        base.fillColor = baseFill
        base.strokeColor = baseStroke
        base.lineWidth = 1
        base.isAntialiased = true
        addChild(base)

        thumb = SKShapeNode(circleOfRadius: thumbRadius)
        thumb.fillColor = UIColor(white: 1.0, alpha: Tuning.Joystick.thumbAlpha)
        thumb.strokeColor = baseStroke
        thumb.lineWidth = 1
        thumb.isAntialiased = true
        addChild(thumb)
    }

    private func setVisible(_ visible: Bool) {
        base.alpha = visible ? 1 : 0
        thumb.alpha = visible ? 1 : 0
    }

    /// `parent` is the joystick's parent SKNode (the camera-attached `hudRoot`
    /// in GameScene). Touch coords are read in that node's local space so the
    /// joystick stays screen-relative as the camera moves through the world.
    /// `viewWidth` is the visible viewport width — used to split touches into
    /// left vs right halves.
    func touchesBegan(_ touches: Set<UITouch>, in parent: SKNode, viewWidth: CGFloat) {
        guard trackedTouch == nil else { return }
        for touch in touches {
            let p = touch.location(in: parent)
            let onLeft = p.x < viewWidth / 2
            if (side == .left && onLeft) || (side == .right && !onLeft) {
                trackedTouch = touch
                anchor = p
                position = p
                thumb.position = .zero
                vector = .zero
                setVisible(true)
                return
            }
        }
    }

    func touchesMoved(_ touches: Set<UITouch>, in parent: SKNode) {
        guard let tracked = trackedTouch, touches.contains(tracked) else { return }
        let p = tracked.location(in: parent)
        let dx = p.x - anchor.x
        let dy = p.y - anchor.y
        let dist = (dx * dx + dy * dy).squareRoot()
        let clamped = min(dist, baseRadius)
        let nx = dist == 0 ? 0 : dx / dist
        let ny = dist == 0 ? 0 : dy / dist
        thumb.position = CGPoint(x: nx * clamped, y: ny * clamped)

        let mag = clamped / baseRadius
        let outMag = mag < deadZone ? 0 : (mag - deadZone) / (1 - deadZone)
        vector = CGVector(dx: nx * outMag, dy: ny * outMag)
    }

    func touchesEnded(_ touches: Set<UITouch>) {
        guard let tracked = trackedTouch else { return }
        if touches.contains(tracked) {
            trackedTouch = nil
            vector = .zero
            setVisible(false)
        }
    }

    func cancelAllTouches() {
        trackedTouch = nil
        vector = .zero
        setVisible(false)
    }
}
