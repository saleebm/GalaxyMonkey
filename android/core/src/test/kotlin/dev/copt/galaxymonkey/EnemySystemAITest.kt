package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

class EnemySystemAITest {

    data class FireRecord(val origin: Vector2, val angle: Float, val kind: ProjectileKind)

    private val fires = mutableListOf<FireRecord>()
    private val playerPos = Vector2(400f, 300f)

    private fun make(seed: Int = 42): EnemySystem = EnemySystem(
        playerPosition = { playerPos },
        cameraPosition = { Vector2(400f, 300f) },
        viewSize = { Vector2(812f, 375f) },
        enemyFireRequest = { origin, angle, kind ->
            fires.add(FireRecord(Vector2(origin), angle, kind))
        },
        rng = Random(seed)
    )

    private fun placeEnemy(sys: EnemySystem, type: EnemyType, x: Float, y: Float, cooldown: Double = Double.POSITIVE_INFINITY): Enemy {
        val e = Enemy(
            type = type,
            hp = type.hp,
            speed = Tuning.Enemy.baseSpeed * type.speedMul,
            radius = Tuning.Enemy.radius * type.radiusMul,
            facingLeft = x >= 400f,
            attackCooldown = cooldown
        )
        e.position.set(x, y)
        e.currentSet = EnemySystem.idleAnimation(type, e.facingLeft)
        sys.enemies.add(e)
        return e
    }

    @Test
    fun `homing - enemy moves toward player`() {
        val sys = make()
        sys.enemies.clear()
        val e = placeEnemy(sys, EnemyType.PREPPY, 600f, 300f)
        val distBefore = Vector2.dst(e.position.x, e.position.y, playerPos.x, playerPos.y)
        sys.update(1f / 60f)
        val distAfter = Vector2.dst(e.position.x, e.position.y, playerPos.x, playerPos.y)
        assertTrue(distAfter < distBefore, "enemy should move closer to player")
    }

    @Test
    fun `homing - displacement magnitude roughly speed times dt`() {
        val sys = make()
        sys.enemies.clear()
        val e = placeEnemy(sys, EnemyType.PREPPY, 600f, 300f)
        val startX = e.position.x
        val startY = e.position.y
        val dt = 1f / 60f
        sys.update(dt)
        val dx = e.position.x - startX
        val dy = e.position.y - startY
        val moved = sqrt(dx * dx + dy * dy)
        val expected = e.speed * dt
        assertEquals(expected, moved, 1f, "displacement should be ~speed*dt")
    }

    @Test
    fun `facing deadzone - dx within deadzone does not flip`() {
        val sys = make()
        sys.enemies.clear()
        val e = placeEnemy(sys, EnemyType.PREPPY, playerPos.x, playerPos.y + 200f)
        e.facingLeft = true
        sys.update(1f / 60f)
        assertTrue(e.facingLeft, "dx~0 (enemy directly above) should not flip facing")
    }

    @Test
    fun `facing flip - player far left flips to facingLeft true`() {
        val sys = make()
        sys.enemies.clear()
        val e = placeEnemy(sys, EnemyType.PREPPY, playerPos.x + 200f, playerPos.y)
        e.facingLeft = false
        sys.update(1f / 60f)
        assertTrue(e.facingLeft, "enemy right of player should face left")
    }

    @Test
    fun `facing flip - player far right flips to facingLeft false`() {
        val sys = make()
        sys.enemies.clear()
        val e = placeEnemy(sys, EnemyType.PREPPY, playerPos.x - 200f, playerPos.y)
        e.facingLeft = true
        sys.update(1f / 60f)
        assertFalse(e.facingLeft, "enemy left of player should face right")
    }

    @Test
    fun `walk idle switch - fast enemy has WALK state`() {
        val sys = make()
        sys.enemies.clear()
        val e = placeEnemy(sys, EnemyType.PREPPY, 600f, 300f)
        e.animState = Enemy.AnimState.IDLE
        sys.update(1f / 60f)
        assertEquals(Enemy.AnimState.WALK, e.animState,
            "moving enemy should switch to WALK")
    }

    @Test
    fun `walk idle switch - enemy at player position switches to IDLE`() {
        val sys = make()
        sys.enemies.clear()
        val e = placeEnemy(sys, EnemyType.PREPPY, playerPos.x, playerPos.y)
        e.animState = Enemy.AnimState.WALK
        sys.update(1f / 60f)
        assertEquals(Enemy.AnimState.IDLE, e.animState,
            "enemy co-located with player should switch to IDLE")
    }

    @Test
    fun `walk idle - omni type falls back to idle without crash`() {
        val sys = make()
        sys.enemies.clear()
        val e = placeEnemy(sys, EnemyType.GORILLA, 600f, 300f)
        assertDoesNotThrow { sys.update(1f / 60f) }
        assertNotNull(e.currentSet, "gorilla should have an idle animation set")
    }

    @Test
    fun `shoot range gate - beyond range holds fire`() {
        val sys = make()
        sys.enemies.clear()
        fires.clear()
        val farX = playerPos.x + Tuning.Enemy.fireRangePx + 200f
        val e = placeEnemy(sys, EnemyType.HEAVY_COSMONAUT, farX, playerPos.y, cooldown = 0.0)
        sys.update(1f / 60f)
        assertTrue(fires.isEmpty(), "should NOT fire beyond fireRangePx")
        assertEquals(0.1, e.attackCooldown, 0.01, "cooldown should be set to 0.1 when out of range")
    }

    @Test
    fun `shoot range gate - within range fires bullet`() {
        val sys = make()
        sys.enemies.clear()
        fires.clear()
        val closeX = playerPos.x + 100f
        placeEnemy(sys, EnemyType.HEAVY_COSMONAUT, closeX, playerPos.y, cooldown = 0.0)
        sys.update(1f / 60f)
        assertEquals(1, fires.size, "should fire once within range")
        assertEquals(ProjectileKind.BULLET, fires[0].kind)
        val expectedAngle = atan2(playerPos.y - playerPos.y, playerPos.x - closeX)
        assertEquals(expectedAngle, fires[0].angle, 0.1f)
    }

    @Test
    fun `melee never fires`() {
        val sys = make()
        sys.enemies.clear()
        fires.clear()
        placeEnemy(sys, EnemyType.PREPPY, playerPos.x + 50f, playerPos.y)
        repeat(120) { sys.update(1f / 60f) }
        assertTrue(fires.isEmpty(), "melee enemy should never call enemyFireRequest")
    }

    @Test
    fun `gorilla windup fires bomb exactly once at release frame`() {
        val sys = make()
        sys.enemies.clear()
        fires.clear()
        val e = placeEnemy(sys, EnemyType.GORILLA, playerPos.x + 200f, playerPos.y, cooldown = 0.0)
        val facingBefore = e.facingLeft

        val totalWindup = EnemySystem.WINDUP_FRAME_DURATIONS.sum()
        val dt = 1f / 60f
        val steps = ((totalWindup + 0.5f) / dt).toInt()
        for (i in 0 until steps) {
            sys.update(dt)
        }

        val bombFires = fires.filter { it.kind == ProjectileKind.BOMB }
        assertEquals(1, bombFires.size, "gorilla should fire exactly one bomb during windup")
        val bombOrigin = bombFires[0].origin
        assertEquals(e.radius * 0.4f, bombOrigin.y - e.position.y, 5f,
            "bomb origin y offset should be ~radius*0.4")
    }

    @Test
    fun `gorilla facing does not flip during windup`() {
        val sys = make()
        sys.enemies.clear()
        fires.clear()
        val e = placeEnemy(sys, EnemyType.GORILLA, playerPos.x + 200f, playerPos.y, cooldown = 0.0)
        sys.update(1f / 60f)
        assertTrue(e.windupActive, "gorilla should start windup when cooldown reaches 0")
        val facingDuringWindup = e.facingLeft
        playerPos.set(e.position.x + 500f, e.position.y)
        repeat(10) { sys.update(1f / 60f) }
        assertEquals(facingDuringWindup, e.facingLeft,
            "facing should not change during windup")
        playerPos.set(400f, 300f)
    }

    @Test
    fun `determinism - same seed same cooldown reseeds`() {
        fires.clear()
        val a = make(77)
        a.enemies.clear()
        placeEnemy(a, EnemyType.HEAVY_COSMONAUT, playerPos.x + 100f, playerPos.y, cooldown = 0.0)

        val firesA = mutableListOf<FireRecord>()
        repeat(300) { a.update(1f / 60f) }
        firesA.addAll(fires)

        fires.clear()
        val b = make(77)
        b.enemies.clear()
        placeEnemy(b, EnemyType.HEAVY_COSMONAUT, playerPos.x + 100f, playerPos.y, cooldown = 0.0)
        repeat(300) { b.update(1f / 60f) }

        assertEquals(firesA.size, fires.size, "same seed should yield same fire count")
        for (i in firesA.indices) {
            assertEquals(firesA[i].angle, fires[i].angle, 0.01f, "angle mismatch at fire $i")
        }
    }
}
