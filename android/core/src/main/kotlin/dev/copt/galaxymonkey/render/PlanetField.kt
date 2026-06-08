package dev.copt.galaxymonkey.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import dev.copt.galaxymonkey.Sprite
import dev.copt.galaxymonkey.SpriteCatalog
import dev.copt.galaxymonkey.Tuning
import kotlin.math.atan2

// Solar orrery: sun at world center, 8 planets starting at phase 0
// (conjunction line), moons, asteroid belt, orbit rings. Static shading
// (terminator shader is a separate bead). Port of PlanetField.swift.

class PlanetField(
    private val worldCenterX: Float,
    private val worldCenterY: Float,
) : Disposable {

    private class Orbit(
        val region: TextureRegion?,
        val radius: Float,
        var phase: Float,
        val angularSpeed: Float,
        val displayW: Float,
        val displayH: Float,
        val alpha: Float = 0.95f,
        val frontZ: Float? = null,
        val backZ: Float? = null,
        val parentOrbit: Orbit? = null,
        val spinSpeed: Float = 0f,
        val useTerminator: Boolean = false,
    ) {
        var x = 0f
        var y = 0f
        var rotation = 0f
        var drawBehindSun = false
    }

    private class Comet(
        val startX: Float, val startY: Float,
        val endX: Float, val endY: Float,
        val duration: Float,
    ) {
        var elapsed = 0f
        var x = startX
        var y = startY
        var rotation = 0f
        var alive = true
        var trailAccum = 0f
    }

    private class TrailParticle {
        var x = 0f; var y = 0f
        var age = 0f; var lifetime = 0f
        var scale = 0f; var scaleSpeed = 0f
        var alpha = 0f; var alphaSpeed = 0f
        var alive = false
    }

    private val orbits = mutableListOf<Orbit>()
    private val orbitRadii = mutableListOf<Float>()
    private var sunRegion: TextureRegion? = null
    private var sunScale = 1f
    private var coronaPhase = 0f

    private val comets = mutableListOf<Comet>()
    private val trailParticles = Array(512) { TrailParticle() }
    private var cometCountdown = Tuning.World.cometInitialDelay

    init {
        buildSun()
        buildOrbitRings()
        buildPlanets()
        buildAsteroidBelt()
    }

    fun update(dt: Float) {
        coronaPhase += dt
        for (orbit in orbits) {
            orbit.phase += orbit.angularSpeed * dt
            orbit.rotation += orbit.spinSpeed * dt

            val parentX = orbit.parentOrbit?.x ?: 0f
            val parentY = orbit.parentOrbit?.y ?: 0f
            orbit.x = parentX + MathUtils.cos(orbit.phase) * orbit.radius
            orbit.y = parentY + MathUtils.sin(orbit.phase) * orbit.radius * Tuning.World.orbitTiltY

            if (orbit.frontZ != null && orbit.backZ != null) {
                orbit.drawBehindSun = orbit.y >= 0f
            }
        }

        updateComets(dt)
    }

    fun drawRings(shapeRenderer: ShapeRenderer) {
        shapeRenderer.color = Color(1f, 1f, 1f, Tuning.World.orbitRingAlpha)
        for (r in orbitRadii) {
            val rY = r * Tuning.World.orbitTiltY
            shapeRenderer.ellipse(
                worldCenterX - r, worldCenterY - rY,
                r * 2f, rY * 2f, 120
            )
        }
    }

    fun drawBodies(batch: SpriteBatch) {
        // Corona (additive, behind sun)
        val sReg = sunRegion
        if (sReg != null) {
            val coronaPulse = 1f + Tuning.World.sunCoronaPulseAmplitude *
                MathUtils.sin(coronaPhase * MathUtils.PI2 / Tuning.World.sunCoronaPulsePeriod)
            val coronaSize = Tuning.World.sunDisplayDiameter * coronaPulse

            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            batch.setColor(1f, 1f, 1f, Tuning.World.sunCoronaAlpha)
            val halo = HaloTextures.halo256Region
            batch.draw(halo,
                worldCenterX - coronaSize / 2f, worldCenterY - coronaSize / 2f,
                coronaSize, coronaSize)
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            batch.setColor(Color.WHITE)

            // Sun body
            val sunSize = Tuning.World.sunDisplayDiameter * sunScale
            batch.draw(sReg,
                worldCenterX - sunSize / 2f, worldCenterY - sunSize / 2f,
                sunSize, sunSize)
        }

        // Behind-sun bodies first (Mercury far side)
        for (orbit in orbits) {
            if (!orbit.drawBehindSun) continue
            drawOrbit(batch, orbit)
        }

        // Normal bodies
        for (orbit in orbits) {
            if (orbit.drawBehindSun) continue
            drawOrbit(batch, orbit)
        }
    }

    private fun drawOrbit(batch: SpriteBatch, orbit: Orbit) {
        val region = orbit.region ?: return
        val cx = worldCenterX + orbit.x
        val cy = worldCenterY + orbit.y
        val hw = orbit.displayW / 2f
        val hh = orbit.displayH / 2f

        val shader = TerminatorShader.program
        if (orbit.useTerminator && shader != null) {
            batch.shader = shader
            val sunAngle = TerminatorShader.sunAngleForPlanet(orbit.phase, orbit.rotation)
            shader.setUniformf("u_sunAngle", sunAngle)
        }

        batch.setColor(1f, 1f, 1f, orbit.alpha)
        batch.draw(
            region,
            cx - hw, cy - hh,
            hw, hh,
            orbit.displayW, orbit.displayH,
            1f, 1f,
            orbit.rotation * MathUtils.radiansToDegrees
        )
        batch.setColor(Color.WHITE)

        if (orbit.useTerminator && shader != null) {
            batch.shader = null
        }
    }

    private fun buildSun() {
        sunRegion = SpriteCatalog.region(Sprite.SUN)
    }

    private fun buildOrbitRings() {
        orbitRadii.addAll(listOf(
            Tuning.World.orbitMercury, Tuning.World.orbitVenus,
            Tuning.World.orbitEarth, Tuning.World.orbitMars,
            Tuning.World.orbitJupiter, Tuning.World.orbitSaturn,
            Tuning.World.orbitUranus, Tuning.World.orbitNeptune,
        ))
    }

    private fun buildPlanets() {
        // Mercury (behindSunZ for far-side occlusion)
        addPlanet(Sprite.MERCURY, Tuning.World.mercuryDiameter,
            Tuning.World.orbitMercury, Tuning.World.angSpeedMercury,
            frontZ = -45f, backZ = -46.7f)

        addPlanet(Sprite.VENUS, Tuning.World.venusDiameter,
            Tuning.World.orbitVenus, Tuning.World.angSpeedVenus)

        val earthOrbit = addPlanet(Sprite.EARTH, Tuning.World.earthDiameter,
            Tuning.World.orbitEarth, Tuning.World.angSpeedEarth)
        addMoon(earthOrbit, Sprite.LUNA, Tuning.World.Moons.lunaDiameter,
            Tuning.World.Moons.lunaRadius, Tuning.World.Moons.lunaSpeed)

        addPlanet(Sprite.MARS, Tuning.World.marsDiameter,
            Tuning.World.orbitMars, Tuning.World.angSpeedMars)

        val jupiterOrbit = addPlanet(Sprite.JUPITER, Tuning.World.jupiterDiameter,
            Tuning.World.orbitJupiter, Tuning.World.angSpeedJupiter)
        addMoon(jupiterOrbit, Sprite.IO, Tuning.World.Moons.ioDiameter,
            Tuning.World.Moons.ioRadius, Tuning.World.Moons.ioSpeed)
        addMoon(jupiterOrbit, Sprite.EUROPA, Tuning.World.Moons.europaDiameter,
            Tuning.World.Moons.europaRadius, Tuning.World.Moons.europaSpeed)
        addMoon(jupiterOrbit, Sprite.GANYMEDE, Tuning.World.Moons.ganymedeDiameter,
            Tuning.World.Moons.ganymedeRadius, Tuning.World.Moons.ganymedeSpeed)
        addMoon(jupiterOrbit, Sprite.CALLISTO, Tuning.World.Moons.callistoDiameter,
            Tuning.World.Moons.callistoRadius, Tuning.World.Moons.callistoSpeed)

        val saturnOrbit = addPlanet(Sprite.SATURN, Tuning.World.saturnDiameter,
            Tuning.World.orbitSaturn, Tuning.World.angSpeedSaturn)
        addMoon(saturnOrbit, Sprite.TITAN, Tuning.World.Moons.titanDiameter,
            Tuning.World.Moons.titanRadius, Tuning.World.Moons.titanSpeed)

        addPlanet(Sprite.URANUS, Tuning.World.uranusDiameter,
            Tuning.World.orbitUranus, Tuning.World.angSpeedUranus)

        addPlanet(Sprite.NEPTUNE, Tuning.World.neptuneDiameter,
            Tuning.World.orbitNeptune, Tuning.World.angSpeedNeptune)
    }

    private fun addPlanet(
        sprite: Sprite, diameter: Float,
        radius: Float, angularSpeed: Float,
        frontZ: Float? = null, backZ: Float? = null,
    ): Orbit {
        val region = SpriteCatalog.region(sprite)
        val spinDir = if (MathUtils.randomBoolean()) 1f else -1f
        val spinPeriod = MathUtils.random(
            Tuning.VFX.planetRotationPeriodMin,
            Tuning.VFX.planetRotationPeriodMax
        )
        val orbit = Orbit(
            region = region,
            radius = radius,
            phase = 0f,
            angularSpeed = angularSpeed,
            displayW = diameter,
            displayH = diameter,
            frontZ = frontZ,
            backZ = backZ,
            spinSpeed = spinDir * MathUtils.PI2 / spinPeriod,
            useTerminator = true,
        )
        orbits.add(orbit)
        return orbit
    }

    private fun addMoon(parent: Orbit, sprite: Sprite, diameter: Float,
                        radius: Float, angularSpeed: Float) {
        val region = SpriteCatalog.region(sprite)
        val spinDir = if (MathUtils.randomBoolean()) 1f else -1f
        val spinPeriod = MathUtils.random(
            Tuning.VFX.planetRotationPeriodMin,
            Tuning.VFX.planetRotationPeriodMax
        )
        orbits.add(Orbit(
            region = region,
            radius = radius,
            phase = MathUtils.random(0f, MathUtils.PI2),
            angularSpeed = angularSpeed,
            displayW = diameter,
            displayH = diameter,
            parentOrbit = parent,
            spinSpeed = spinDir * MathUtils.PI2 / spinPeriod,
            useTerminator = true,
        ))
    }

    private fun buildAsteroidBelt() {
        val variants = listOf(Sprite.ROCK_01, Sprite.ROCK_03, Sprite.ROCK_04)
        val rockRegions = variants.mapNotNull { SpriteCatalog.region(it) }
        val beltSpeed = Tuning.World.asteroidBeltAngularSpeed
        val beltJitter = Tuning.World.asteroidBeltSpeedJitter

        for (i in 0 until Tuning.World.asteroidBeltCount) {
            val radius = MathUtils.random(
                Tuning.World.asteroidBeltInnerRadius,
                Tuning.World.asteroidBeltOuterRadius
            )
            val phase = MathUtils.random(0f, MathUtils.PI2)
            val diameter = MathUtils.random(
                Tuning.World.asteroidDiameterMin,
                Tuning.World.asteroidDiameterMax
            )
            val alpha = MathUtils.random(
                Tuning.World.asteroidAlphaMin,
                Tuning.World.asteroidAlphaMax
            )
            val yJitter = MathUtils.random(0.7f, 1.0f)
            val speed = beltSpeed + MathUtils.random(-beltJitter, beltJitter)
            val region = if (rockRegions.isNotEmpty()) rockRegions[i % rockRegions.size] else null

            orbits.add(Orbit(
                region = region,
                radius = radius,
                phase = phase,
                angularSpeed = speed,
                displayW = diameter,
                displayH = diameter * yJitter,
                alpha = alpha,
                spinSpeed = MathUtils.random(-1f, 1f),
            ))
        }
    }

    // --- Comet system ---

    private fun updateComets(dt: Float) {
        cometCountdown -= dt
        if (cometCountdown <= 0f) {
            spawnComet()
            cometCountdown = MathUtils.random(
                Tuning.World.cometSpawnIntervalMin,
                Tuning.World.cometSpawnIntervalMax
            )
        }

        val iter = comets.iterator()
        while (iter.hasNext()) {
            val c = iter.next()
            c.elapsed += dt
            if (c.elapsed >= c.duration) {
                c.alive = false
                iter.remove()
                continue
            }
            val t = c.elapsed / c.duration
            c.x = MathUtils.lerp(c.startX, c.endX, t)
            c.y = MathUtils.lerp(c.startY, c.endY, t)
            val dx = c.endX - c.startX
            val dy = c.endY - c.startY
            c.rotation = atan2(dy, dx)

            c.trailAccum += Tuning.World.cometTrailBirthRate * dt
            while (c.trailAccum >= 1f) {
                c.trailAccum -= 1f
                emitTrailParticle(c.x, c.y)
            }
        }

        for (p in trailParticles) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= p.lifetime) { p.alive = false; continue }
            p.scale += p.scaleSpeed * dt
            if (p.scale < 0f) p.scale = 0f
            p.alpha += p.alphaSpeed * dt
            if (p.alpha < 0f) p.alpha = 0f
        }
    }

    private fun spawnComet() {
        val r = Tuning.World.cometPathRadius
        val tilt = Tuning.World.orbitTiltY
        val entryAngle = MathUtils.random(0f, MathUtils.PI2)
        val exitAngle = entryAngle + MathUtils.PI +
            MathUtils.random(-Tuning.World.cometExitJitterRad, Tuning.World.cometExitJitterRad)
        val startX = MathUtils.cos(entryAngle) * r
        val startY = MathUtils.sin(entryAngle) * r * tilt
        val endX = MathUtils.cos(exitAngle) * r
        val endY = MathUtils.sin(exitAngle) * r * tilt
        val duration = MathUtils.random(
            Tuning.World.cometTravelDurationMin,
            Tuning.World.cometTravelDurationMax
        )
        comets.add(Comet(startX, startY, endX, endY, duration))
    }

    private fun emitTrailParticle(x: Float, y: Float) {
        val p = trailParticles.firstOrNull { !it.alive } ?: return
        p.alive = true
        p.age = 0f
        p.x = x
        p.y = y
        p.lifetime = Tuning.World.cometTrailLifetime
        p.scale = 0.18f
        p.scaleSpeed = -0.08f
        p.alpha = 0.9f
        p.alphaSpeed = -0.6f
    }

    fun drawComets(batch: SpriteBatch) {
        val haloRegion = HaloTextures.halo256Region
        val dotRegion = HaloTextures.softDot32Region
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

        for (p in trailParticles) {
            if (!p.alive) continue
            val r = MathUtils.lerp(1f, 0.82f, 0.85f)
            val g = MathUtils.lerp(1f, 0.95f, 0.85f)
            val b = MathUtils.lerp(1f, 1.0f, 0.85f)
            batch.setColor(r, g, b, p.alpha)
            val size = haloRegion.regionWidth * p.scale
            batch.draw(haloRegion,
                worldCenterX + p.x - size / 2f,
                worldCenterY + p.y - size / 2f,
                size, size)
        }

        for (c in comets) {
            batch.setColor(1f, 1f, 1f, 1f)
            val headSize = Tuning.World.cometHeadRadius * 2f
            batch.draw(dotRegion,
                worldCenterX + c.x - headSize / 2f,
                worldCenterY + c.y - headSize / 2f,
                headSize, headSize)
        }

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(Color.WHITE)
    }

    internal val activeCometCount: Int get() = comets.size
    internal val activeTrailCount: Int get() = trailParticles.count { it.alive }

    override fun dispose() {
        orbits.clear()
        comets.clear()
    }
}
