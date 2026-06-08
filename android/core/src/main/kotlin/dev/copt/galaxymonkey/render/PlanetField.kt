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
    ) {
        var x = 0f
        var y = 0f
        var rotation = 0f
        var drawBehindSun = false
    }

    private val orbits = mutableListOf<Orbit>()
    private val orbitRadii = mutableListOf<Float>()
    private var sunRegion: TextureRegion? = null
    private var sunScale = 1f
    private var coronaPhase = 0f

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

    override fun dispose() {
        orbits.clear()
    }
}
