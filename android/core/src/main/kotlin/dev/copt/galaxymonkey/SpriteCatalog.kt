package dev.copt.galaxymonkey

import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion

// All textures are optional: art is generated incrementally via the
// SpriteCook pipeline. Subsystems check has() and fall back to
// primitive shape drawing when art is missing.

enum class Sprite(val regionName: String) {
    PLAYER("Player"),
    BULLET("Bullet"),
    ENEMY_BULLET("EnemyBullet"),
    BOMB("Bomb"),
    GOLDEN_BANANA("GoldenBanana"),
    PREPPY_LEFT("PreppyLeft"),
    PREPPY_RIGHT("PreppyRight"),
    WHITE_LEFT("WhiteLeft"),
    WHITE_RIGHT("WhiteRight"),
    SHADY_LEFT("ShadyLeft"),
    SHADY_RIGHT("ShadyRight"),
    GORILLA_LEFT("GorillaLeft"),
    GORILLA_RIGHT("GorillaRight"),
    GORILLA_WINDUP("GorillaWindup"),
    DRONE_SWARM_LEFT("DroneSwarmLeft"),
    DRONE_SWARM_RIGHT("DroneSwarmRight"),
    HEAVY_COSMONAUT_LEFT("HeavyCosmonautLeft"),
    HEAVY_COSMONAUT_RIGHT("HeavyCosmonautRight"),
    PLASMA_JELLY("PlasmaJelly"),
    ASTRO_SNIPER_LEFT("AstroSniperLeft"),
    ASTRO_SNIPER_RIGHT("AstroSniperRight"),
    MINI_BOSS_LEFT("MiniBossLeft"),
    MINI_BOSS_RIGHT("MiniBossRight"),
    SPACE_BACKDROP("SpaceBackdrop"),
    EXPLOSION("Explosion"),
    TITLE("Title"),
    PAUSE_ICON("PauseIcon"),
    LIFE_HEART("LifeHeart"),
    SUN("Sun"),
    MERCURY("Mercury"),
    VENUS("Venus"),
    EARTH("Earth"),
    MARS("Mars"),
    JUPITER("Jupiter"),
    SATURN("Saturn"),
    URANUS("Uranus"),
    NEPTUNE("Neptune"),
    GLOW("Glow"),
    LUNA("Luna"),
    IO("Io"),
    EUROPA("Europa"),
    GANYMEDE("Ganymede"),
    CALLISTO("Callisto"),
    TITAN("Titan"),
    ROCK_01("Rock01"),
    ROCK_02("Rock02"),
    ROCK_03("Rock03"),
    ROCK_04("Rock04"),
}

object SpriteCatalog {

    private lateinit var atlas: TextureAtlas
    private val cache = mutableMapOf<Sprite, TextureRegion>()
    private val missing = mutableSetOf<Sprite>()

    fun init(atlas: TextureAtlas) {
        this.atlas = atlas
        cache.clear()
        missing.clear()
    }

    fun region(sprite: Sprite): TextureRegion? {
        if (!::atlas.isInitialized) return null
        cache[sprite]?.let { return it }
        if (sprite in missing) return null
        val region = atlas.findRegion(sprite.regionName)
        if (region == null) {
            missing.add(sprite)
            return null
        }
        cache[sprite] = region
        return region
    }

    fun has(sprite: Sprite): Boolean = region(sprite) != null

    fun preload() {
        Sprite.entries.forEach { region(it) }
    }
}
