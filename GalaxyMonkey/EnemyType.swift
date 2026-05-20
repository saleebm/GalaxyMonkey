//
//  EnemyType.swift
//  GalaxyMonkey
//
//  Per-archetype stat table for the enemy roster. EnemySystem reads these
//  to size each spawn, score kills, and decide attack behavior. Add a case
//  + sprite mapping when introducing a new enemy class.
//

import CoreGraphics
import Foundation

enum EnemyType: CaseIterable {
    case droneSwarm
    case preppy
    case white
    case shady
    case heavyCosmonaut
    case plasmaJelly
    case astroSniper
    case miniBoss
    case gorilla

    var leftSprite: Sprite {
        switch self {
        case .droneSwarm:     return .droneSwarm
        case .preppy:         return .preppyLeft
        case .white:          return .whiteLeft
        case .shady:          return .shadyLeft
        case .heavyCosmonaut: return .heavyCosmonautLeft
        case .plasmaJelly:    return .plasmaJelly
        case .astroSniper:    return .astroSniperLeft
        case .miniBoss:       return .miniBossLeft
        case .gorilla:        return .gorilla
        }
    }

    var rightSprite: Sprite {
        switch self {
        case .droneSwarm:     return .droneSwarm
        case .preppy:         return .preppyRight
        case .white:          return .whiteRight
        case .shady:          return .shadyRight
        case .heavyCosmonaut: return .heavyCosmonautRight
        case .plasmaJelly:    return .plasmaJelly
        case .astroSniper:    return .astroSniperRight
        case .miniBoss:       return .miniBossRight
        case .gorilla:        return .gorilla
        }
    }

    var hp: Int {
        switch self {
        case .droneSwarm, .preppy, .white, .shady, .astroSniper: return 1
        case .plasmaJelly:    return 2
        case .heavyCosmonaut: return 3
        case .miniBoss:       return 5
        case .gorilla:        return 10
        }
    }

    var speedMul: CGFloat {
        switch self {
        case .droneSwarm:     return 1.5
        case .preppy, .white, .shady: return 1.0
        case .heavyCosmonaut: return 0.6
        case .plasmaJelly:    return 0.8
        case .astroSniper:    return 0.9
        case .miniBoss:       return 0.7
        case .gorilla:        return 0.5
        }
    }

    var radiusMul: CGFloat {
        switch self {
        case .droneSwarm:     return 0.7
        case .preppy, .white, .shady, .plasmaJelly: return 1.0
        case .astroSniper:    return 0.9
        case .heavyCosmonaut: return 1.3
        case .miniBoss:       return 1.4
        case .gorilla:        return 1.6
        }
    }

    var scoreOnKill: Int {
        switch self {
        case .droneSwarm:     return 50
        case .preppy, .white, .shady: return 100
        case .plasmaJelly:    return 150
        case .astroSniper:    return 200
        case .heavyCosmonaut: return 250
        case .miniBoss:       return 500
        case .gorilla:        return 1000
        }
    }

    /// Weight for weighted random spawn. Gorilla is 0 — its spawn is driven
    /// by the boss cadence in EnemySystem, not the regular pool.
    var spawnWeight: Double {
        switch self {
        case .droneSwarm:     return 3.0
        case .preppy, .white, .shady: return 2.0
        case .plasmaJelly:    return 1.5
        case .astroSniper:    return 1.0
        case .heavyCosmonaut: return 1.0
        case .miniBoss:       return 0.3
        case .gorilla:        return 0
        }
    }

    var attack: AttackBehavior {
        switch self {
        case .heavyCosmonaut: return .shoot(intervalRange: 2.5...4.0)
        case .astroSniper:    return .shoot(intervalRange: 1.8...3.0)
        case .gorilla:        return .bombThrow(interval: 3.0)
        default:              return .melee
        }
    }

    /// Pool of types eligible for weighted random spawn (gorilla excluded).
    static var regularPool: [EnemyType] {
        allCases.filter { $0.spawnWeight > 0 }
    }

    static func weightedRandom(from pool: [EnemyType] = EnemyType.regularPool) -> EnemyType {
        let total = pool.reduce(0.0) { $0 + $1.spawnWeight }
        var roll = Double.random(in: 0..<total)
        for t in pool {
            roll -= t.spawnWeight
            if roll <= 0 { return t }
        }
        return pool.last ?? .preppy
    }
}

enum AttackBehavior {
    case melee
    case shoot(intervalRange: ClosedRange<TimeInterval>)
    case bombThrow(interval: TimeInterval)

    /// Seed value for the per-enemy attack cooldown. Melee returns infinity
    /// so the cooldown timer is effectively disabled.
    var initialCooldown: TimeInterval {
        switch self {
        case .melee:                 return .infinity
        case .shoot(let range):      return TimeInterval.random(in: range)
        case .bombThrow(let i):      return i
        }
    }

    func nextCooldown() -> TimeInterval {
        switch self {
        case .melee:                 return .infinity
        case .shoot(let range):      return TimeInterval.random(in: range)
        case .bombThrow(let i):      return i
        }
    }
}
