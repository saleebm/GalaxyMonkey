package dev.copt.galaxymonkey.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import dev.copt.galaxymonkey.Tuning

// One-shot transient VFX: muzzle flash, glow halo, blackhole warp.
// Each live effect is a lightweight struct advanced by update(dt) and
// drawn by renderAdditive(batch). Damage flash is entity-side (a timer
// on the entity; VFX just sets it).

class VFXPool : Disposable {

    // --- Effect types ---

    private enum class EffectKind { MUZZLE_FLASH, GLOW, BLACKHOLE_HALO }

    private class Effect {
        var kind = EffectKind.MUZZLE_FLASH
        var x = 0f
        var y = 0f
        var rotation = 0f
        var scaleW = 0f
        var scaleH = 0f
        var tintR = 1f
        var tintG = 1f
        var tintB = 1f
        var colorBlendFactor = 0f
        var elapsed = 0f
        var duration = 0f
        var alive = false

        // Blackhole-specific
        var phase = 0 // 0=open, 1=hold, 2=collapse
        var openDur = 0f
        var holdDur = 0f
        var collapseDur = 0f
        var spinRate = 0f

        fun reset() {
            alive = false
            elapsed = 0f
            phase = 0
        }
    }

    // Blackhole warp enemy dissolve data
    class EnemyDissolve {
        var x = 0f
        var y = 0f
        var region: TextureRegion? = null
        var originW = 0f
        var originH = 0f
        var elapsed = 0f
        var alive = false

        // Phase 0 = violet tint ramp, Phase 1 = shrink+fade
        var phase = 0
        var tintR = 1f
        var tintG = 1f
        var tintB = 1f
        var blendFactor = 0f
        var scale = 1f
        var alpha = 1f

        fun reset() {
            alive = false
            elapsed = 0f
            phase = 0
            blendFactor = 0f
            scale = 1f
            alpha = 1f
            region = null
        }
    }

    private val effects = Array(64) { Effect() }
    private val dissolves = Array(16) { EnemyDissolve() }

    private fun obtainEffect(): Effect? = effects.firstOrNull { !it.alive }?.also {
        it.reset()
        it.alive = true
    }

    private fun obtainDissolve(): EnemyDissolve? = dissolves.firstOrNull { !it.alive }?.also {
        it.reset()
        it.alive = true
    }

    // --- Spawn API ---

    fun spawnMuzzleFlash(x: Float, y: Float, angleRad: Float) {
        val e = obtainEffect() ?: return
        e.kind = EffectKind.MUZZLE_FLASH
        e.x = x
        e.y = y
        e.rotation = angleRad
        val displaySize = Tuning.Player.radius * 2f * Tuning.VFX.muzzleFlashScale
        e.scaleW = displaySize
        e.scaleH = displaySize
        e.tintR = 1f; e.tintG = 0.92f; e.tintB = 0.55f
        e.colorBlendFactor = 0.7f
        e.duration = Tuning.VFX.muzzleFlashDuration
    }

    fun spawnGlow(
        x: Float, y: Float, scale: Float, duration: Float,
        r: Float = 1f, g: Float = 0.85f, b: Float = 0.4f,
    ) {
        val e = obtainEffect() ?: return
        e.kind = EffectKind.GLOW
        e.x = x
        e.y = y
        e.rotation = 0f
        val displaySize = Tuning.Enemy.radius * 2f * scale
        e.scaleW = displaySize
        e.scaleH = displaySize
        e.tintR = r; e.tintG = g; e.tintB = b
        e.colorBlendFactor = 0.8f
        e.duration = duration
    }

    fun spawnBlackholeWarp(
        x: Float, y: Float,
        enemyRegion: TextureRegion?,
        enemyW: Float, enemyH: Float,
    ) {
        // Halo effect
        val e = obtainEffect() ?: return
        e.kind = EffectKind.BLACKHOLE_HALO
        e.x = x
        e.y = y
        e.rotation = 0f
        val displaySize = Tuning.Enemy.radius * 2f * Tuning.VFX.blackholeScale
        e.scaleW = displaySize
        e.scaleH = displaySize
        e.tintR = 0.55f; e.tintG = 0.20f; e.tintB = 0.85f
        e.colorBlendFactor = 0.9f
        e.openDur = Tuning.VFX.blackholeOpenDuration
        e.holdDur = Tuning.VFX.warpFadeDuration * 0.4f
        e.collapseDur = Tuning.VFX.blackholeCollapseDuration
        e.duration = e.openDur + e.holdDur + e.collapseDur
        e.spinRate = -MathUtils.PI2 / Tuning.VFX.blackholeSpinPeriod

        // Enemy dissolve
        if (enemyRegion != null) {
            val d = obtainDissolve() ?: return
            d.x = x
            d.y = y
            d.region = enemyRegion
            d.originW = enemyW
            d.originH = enemyH
        }
    }

    fun spawnDamageFlash(flashTimerSetter: (Float) -> Unit) {
        flashTimerSetter(Tuning.VFX.damageFlashDuration)
    }

    // --- Update ---

    fun update(dt: Float) {
        for (e in effects) {
            if (!e.alive) continue
            e.elapsed += dt
            if (e.kind == EffectKind.BLACKHOLE_HALO) {
                e.rotation += e.spinRate * dt
            }
            if (e.elapsed >= e.duration) {
                e.alive = false
            }
        }

        for (d in dissolves) {
            if (!d.alive) continue
            d.elapsed += dt
            when (d.phase) {
                0 -> {
                    val t = (d.elapsed / Tuning.VFX.warpVioletDuration).coerceIn(0f, 1f)
                    d.tintR = MathUtils.lerp(1f, 0.62f, t)
                    d.tintG = MathUtils.lerp(1f, 0.20f, t)
                    d.tintB = MathUtils.lerp(1f, 0.95f, t)
                    d.blendFactor = 0.7f * t
                    if (d.elapsed >= Tuning.VFX.warpVioletDuration) {
                        d.phase = 1
                        d.elapsed = 0f
                    }
                }
                1 -> {
                    val t = Interpolation.smooth.apply(
                        (d.elapsed / Tuning.VFX.warpFadeDuration).coerceIn(0f, 1f)
                    )
                    d.scale = MathUtils.lerp(1f, 0.05f, t)
                    d.alpha = 1f - t
                    if (d.elapsed >= Tuning.VFX.warpFadeDuration) {
                        d.alive = false
                    }
                }
            }
        }
    }

    // --- Render ---

    fun renderAdditive(batch: SpriteBatch) {
        val haloRegion = HaloTextures.halo256Region
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

        for (e in effects) {
            if (!e.alive) continue

            val alpha = when (e.kind) {
                EffectKind.MUZZLE_FLASH, EffectKind.GLOW -> {
                    val t = (e.elapsed / e.duration).coerceIn(0f, 1f)
                    1f - t
                }
                EffectKind.BLACKHOLE_HALO -> {
                    val t = e.elapsed
                    when {
                        t < e.openDur -> 0.7f * (t / e.openDur)
                        t < e.openDur + e.holdDur -> 0.7f
                        else -> {
                            val ct = (t - e.openDur - e.holdDur) / e.collapseDur
                            0.7f * (1f - ct.coerceIn(0f, 1f))
                        }
                    }
                }
            }

            val r = MathUtils.lerp(1f, e.tintR, e.colorBlendFactor)
            val g = MathUtils.lerp(1f, e.tintG, e.colorBlendFactor)
            val b = MathUtils.lerp(1f, e.tintB, e.colorBlendFactor)
            batch.setColor(r, g, b, alpha)

            val hw = e.scaleW / 2f
            val hh = e.scaleH / 2f
            batch.draw(
                haloRegion,
                e.x - hw, e.y - hh,
                hw, hh,
                e.scaleW, e.scaleH,
                1f, 1f,
                e.rotation * MathUtils.radiansToDegrees
            )
        }

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(Color.WHITE)
    }

    fun renderDissolves(batch: SpriteBatch) {
        for (d in dissolves) {
            if (!d.alive) continue
            val region = d.region ?: continue
            val r = MathUtils.lerp(1f, d.tintR, d.blendFactor)
            val g = MathUtils.lerp(1f, d.tintG, d.blendFactor)
            val b = MathUtils.lerp(1f, d.tintB, d.blendFactor)
            batch.setColor(r, g, b, d.alpha)
            val w = d.originW * d.scale
            val h = d.originH * d.scale
            batch.draw(region, d.x - w / 2f, d.y - h / 2f, w, h)
        }
        batch.setColor(Color.WHITE)
    }

    fun hasActiveEffects(): Boolean = effects.any { it.alive } || dissolves.any { it.alive }

    val activeCount: Int get() = effects.count { it.alive }
    val activeDissolveCount: Int get() = dissolves.count { it.alive }

    override fun dispose() {}
}
