package dev.copt.galaxymonkey

import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.Animation.PlayMode
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Array as GdxArray

// Mirrors SpriteCatalog's caching + null-on-missing pattern so callers
// branch gracefully when art isn't present yet. Frames are loaded via
// atlas.findRegions which returns them in index order (matching iOS
// textureNames.sorted() ordering).

enum class AnimationSet(val atlasBase: String) {
    GORILLA_LEFT_IDLE("gorilla_left_idle"),
    GORILLA_RIGHT_IDLE("gorilla_right_idle"),
    GORILLA_LEFT_WINDUP("gorilla_left_windup"),
    GORILLA_RIGHT_WINDUP("gorilla_right_windup"),
    BOMB_EXPLOSION("bomb_explosion"),
    DRONE_SWARM_LEFT_IDLE("drone_swarm_left_idle"),
    DRONE_SWARM_RIGHT_IDLE("drone_swarm_right_idle"),
    PLASMA_JELLY_IDLE("plasma_jelly_idle"),
    PREPPY_LEFT_IDLE("preppy_left_idle"),
    PREPPY_RIGHT_IDLE("preppy_right_idle"),
    WHITE_LEFT_IDLE("white_left_idle"),
    WHITE_RIGHT_IDLE("white_right_idle"),
    SHADY_LEFT_IDLE("shady_left_idle"),
    SHADY_RIGHT_IDLE("shady_right_idle"),
    HEAVY_COSMONAUT_LEFT_IDLE("heavy_cosmonaut_left_idle"),
    HEAVY_COSMONAUT_RIGHT_IDLE("heavy_cosmonaut_right_idle"),
    ASTRO_SNIPER_LEFT_IDLE("astro_sniper_left_idle"),
    ASTRO_SNIPER_RIGHT_IDLE("astro_sniper_right_idle"),
    MINI_BOSS_LEFT_IDLE("mini_boss_left_idle"),
    MINI_BOSS_RIGHT_IDLE("mini_boss_right_idle"),
    PREPPY_LEFT_WALK("preppy_left_walk"),
    PREPPY_RIGHT_WALK("preppy_right_walk"),
    WHITE_LEFT_WALK("white_left_walk"),
    WHITE_RIGHT_WALK("white_right_walk"),
    SHADY_LEFT_WALK("shady_left_walk"),
    SHADY_RIGHT_WALK("shady_right_walk"),
    HEAVY_COSMONAUT_LEFT_WALK("heavy_cosmonaut_left_walk"),
    HEAVY_COSMONAUT_RIGHT_WALK("heavy_cosmonaut_right_walk"),
    ASTRO_SNIPER_LEFT_WALK("astro_sniper_left_walk"),
    ASTRO_SNIPER_RIGHT_WALK("astro_sniper_right_walk"),
    MINI_BOSS_WALK("mini_boss_walk"),
}

object AnimationCatalog {

    private lateinit var atlas: TextureAtlas
    private val cache = mutableMapOf<AnimationSet, GdxArray<TextureAtlas.AtlasRegion>>()
    private val missing = mutableSetOf<AnimationSet>()

    fun init(atlas: TextureAtlas) {
        this.atlas = atlas
        cache.clear()
        missing.clear()
    }

    fun frames(set: AnimationSet): GdxArray<TextureAtlas.AtlasRegion> {
        cache[set]?.let { return it }
        if (set in missing) return GdxArray(0)
        val regions = atlas.findRegions(set.atlasBase)
        if (regions.size == 0) {
            missing.add(set)
            return regions
        }
        cache[set] = regions
        return regions
    }

    fun has(set: AnimationSet): Boolean = frames(set).size > 0

    fun oneShot(set: AnimationSet, frameDuration: Float): Animation<TextureRegion>? {
        val f = frames(set)
        if (f.size == 0) return null
        return Animation(frameDuration, f, PlayMode.NORMAL)
    }

    fun loop(set: AnimationSet, frameDuration: Float): Animation<TextureRegion>? {
        val f = frames(set)
        if (f.size == 0) return null
        return Animation(frameDuration, f, PlayMode.LOOP)
    }

    fun totalDuration(animation: Animation<TextureRegion>): Float =
        animation.frameDuration * animation.keyFrames.size
}
