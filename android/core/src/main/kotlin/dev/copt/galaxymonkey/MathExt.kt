@file:JvmName("MathExt")

package dev.copt.galaxymonkey

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import kotlin.math.abs
import kotlin.math.exp

// Frame-rate-independent exponential approach: current converges on
// target at the given rate. Used by Player.bankApproachRate and
// Camera.followLerpPerSec for dt-stable feel.
fun expApproach(current: Float, target: Float, rate: Float, dt: Float): Float =
    current + (target - current) * (1f - exp(-rate * dt).toFloat())

// Exponential velocity damping (mutates in place, zero alloc).
// Applies Player.drag when no joystick input.
fun expDamp(velocity: Vector2, drag: Float, dt: Float): Vector2 =
    velocity.scl(exp(-drag * dt).toFloat())

// atan2(y,x) that returns 0 when length is near zero instead of an
// undefined/noisy angle. Mirrors iOS guarding facing math against zero
// velocity (tied to Enemy.walkSpeedThresholdPx).
fun Vector2.angleRadSafe(fallback: Float = 0f): Float =
    if (len2() < 1e-8f) fallback else MathUtils.atan2(y, x)

// Shortest-arc angular lerp in radians. Avoids the 2pi wrap snap that
// makes the ship look like it spins (iOS maxBankRadians comment).
fun lerpAngle(from: Float, to: Float, t: Float): Float {
    var delta = (to - from) % MathUtils.PI2
    if (delta > MathUtils.PI) delta -= MathUtils.PI2
    if (delta < -MathUtils.PI) delta += MathUtils.PI2
    return from + delta * t
}

// Joystick deadzone normalization (returns new Vector2).
// If |raw| <= deadZoneFraction, returns zero. Otherwise rescales so
// output ramps 0..1 from the deadzone edge to 1 — no jump at the
// boundary. Mirrors Joystick.deadZone=0.12 semantics.
fun applyDeadzone(raw: Vector2, deadZoneFraction: Float): Vector2 {
    val len = raw.len()
    if (len <= deadZoneFraction) return Vector2.Zero
    val scale = (len - deadZoneFraction) / (1f - deadZoneFraction) / len
    return Vector2(raw.x * scale, raw.y * scale)
}

// Squared distance — cheap range check without sqrt per frame.
// Used by Enemy.fireRangePx and Audio.maxDistance gating.
fun dist2(a: Vector2, b: Vector2): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

fun withinRange(a: Vector2, b: Vector2, r: Float): Boolean = dist2(a, b) <= r * r
