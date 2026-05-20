//
//  GameControllerInput.swift
//  GalaxyMonkey
//
//  MFi physical controller bridge. Observes GCControllerDidConnect/Disconnect
//  and exposes the active extendedGamepad's stick vectors. The on-screen
//  joysticks are owned by VirtualJoystick.swift (touch-only). When a physical
//  controller is paired GameScene reads its vectors here in preference to
//  the touch sticks.
//

import CoreGraphics
import Foundation
import GameController

final class GameControllerInput {

    private var physicalControllerIDs: Set<ObjectIdentifier> = []

    init() {
        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(handleConnect(_:)),
                       name: .GCControllerDidConnect, object: nil)
        nc.addObserver(self, selector: #selector(handleDisconnect(_:)),
                       name: .GCControllerDidDisconnect, object: nil)

        for c in GCController.controllers() {
            physicalControllerIDs.insert(ObjectIdentifier(c))
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    var hasPhysicalController: Bool { !physicalControllerIDs.isEmpty }

    var moveVector: CGVector { read(activeGamepad?.leftThumbstick) }
    var aimVector:  CGVector { read(activeGamepad?.rightThumbstick) }

    private var activeGamepad: GCExtendedGamepad? {
        for c in GCController.controllers() {
            if let gp = c.extendedGamepad { return gp }
        }
        return nil
    }

    private func read(_ stick: GCControllerDirectionPad?) -> CGVector {
        guard let s = stick else { return .zero }
        let x = CGFloat(s.xAxis.value)
        let y = CGFloat(s.yAxis.value)
        let mag = (x * x + y * y).squareRoot()
        guard mag > Tuning.Joystick.deadZone else { return .zero }
        let scaled = (mag - Tuning.Joystick.deadZone) / (1 - Tuning.Joystick.deadZone)
        return CGVector(dx: x / mag * scaled, dy: y / mag * scaled)
    }

    @objc private func handleConnect(_ note: Notification) {
        guard let c = note.object as? GCController else { return }
        physicalControllerIDs.insert(ObjectIdentifier(c))
    }

    @objc private func handleDisconnect(_ note: Notification) {
        guard let c = note.object as? GCController else { return }
        physicalControllerIDs.remove(ObjectIdentifier(c))
    }
}
