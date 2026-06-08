package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class EnemySystemTest {

    private val cam = Vector2(400f, 300f)
    private val vs = Vector2(812f, 375f)
    private lateinit var sys: EnemySystem

    private fun make(seed: Int = 42): EnemySystem = EnemySystem(
        playerPosition = { Vector2(400f, 300f) },
        cameraPosition = { cam },
        viewSize = { vs },
        rng = Random(seed)
    )

    @BeforeEach
    fun setup() { sys = make() }

    @Test
    fun `ramp interval at elapsed 0 equals spawnIntervalStart`() {
        val start = Tuning.Enemy.spawnIntervalStart
        val end = Tuning.Enemy.spawnIntervalEnd
        val dur = Tuning.Enemy.rampDuration

        // At elapsed=0, interval should be spawnIntervalStart
        // After first update spawns, the timer is set to the interval
        sys.update(0.001f)
        assertEquals(1, sys.enemies.size, "first update should spawn")
        // Drive to just past the interval — the second spawn should take ~start seconds
        val preCount = sys.enemies.size
        sys.update(start - 0.01f)
        assertEquals(preCount, sys.enemies.size, "should not spawn before interval elapses")
    }

    @Test
    fun `ramp interval at midpoint is between start and end`() {
        val start = Tuning.Enemy.spawnIntervalStart
        val end = Tuning.Enemy.spawnIntervalEnd
        val dur = Tuning.Enemy.rampDuration
        val mid = (start + end) / 2f

        // Fast-forward to 45s elapsed using large steps to reach the midpoint ramp
        var t = 0f
        while (t < dur / 2f) {
            sys.update(0.5f)
            t += 0.5f
        }
        val countBefore = sys.enemies.size
        // At the midpoint, interval should be roughly mid
        // Step forward by end (shortest possible) and check spawn happened
        sys.update(mid)
        assertTrue(sys.enemies.size > countBefore,
            "at midpoint ramp, an interval of ~$mid seconds should trigger a spawn")
    }

    @Test
    fun `ramp interval clamps at spawnIntervalEnd after rampDuration`() {
        val end = Tuning.Enemy.spawnIntervalEnd
        val dur = Tuning.Enemy.rampDuration

        // Fast-forward past ramp duration
        var t = 0f
        while (t < dur + 10f) {
            sys.update(1f)
            t += 1f
        }
        val countBefore = sys.enemies.size
        // After full ramp, interval should be spawnIntervalEnd (0.75)
        // Step by end + epsilon — should spawn
        sys.update(end + 0.01f)
        assertTrue(sys.enemies.size > countBefore,
            "after ramp, interval should be clamped to $end")
    }

    @Test
    fun `spawn cadence - gaps shrink over 90s ramp`() {
        val sys2 = make(99)
        val spawnTimes = mutableListOf<Float>()
        var elapsed = 0f
        val dt = 1f / 60f
        var lastCount = 0
        while (elapsed < 100f) {
            sys2.update(dt)
            elapsed += dt
            if (sys2.enemies.size > lastCount) {
                spawnTimes.add(elapsed)
                lastCount = sys2.enemies.size
            }
        }
        assertTrue(spawnTimes.size >= 5, "should have multiple spawns over 100s")
        // Compare average gap in first 30s vs last 30s
        val earlyGaps = mutableListOf<Float>()
        val lateGaps = mutableListOf<Float>()
        for (i in 1 until spawnTimes.size) {
            val gap = spawnTimes[i] - spawnTimes[i - 1]
            if (spawnTimes[i] < 30f) earlyGaps.add(gap)
            else if (spawnTimes[i] > 70f) lateGaps.add(gap)
        }
        if (earlyGaps.isNotEmpty() && lateGaps.isNotEmpty()) {
            val earlyAvg = earlyGaps.average()
            val lateAvg = lateGaps.average()
            assertTrue(lateAvg < earlyAvg,
                "late gaps ($lateAvg) should be shorter than early gaps ($earlyAvg)")
        }
    }

    // TODO: needs a spawn-position observer hook to capture positions before homing moves them
    @Disabled("Homing AI moves enemies off their spawn edge within the same update() call")
    @Test
    fun `edge placement - spawns on padded boundary, never inside visible window`() {
    }

    @Test
    fun `boss cadence - gorilla spawns after bossEveryNKills, bossAlive guards`() {
        sys.regularKillsSinceBoss = Tuning.Enemy.bossEveryNKills - 1
        sys.bossAlive = false

        // Trigger a spawn by stepping with tiny dt (timer starts at 0)
        sys.update(0.001f)

        // Next spawn should be gorilla because the counter just reached threshold
        // The first update already spawned one. Set counter again and force another.
        sys.enemies.clear()
        sys.regularKillsSinceBoss = Tuning.Enemy.bossEveryNKills
        sys.update(3f) // long enough to trigger spawn
        val gorilla = sys.enemies.find { it.type == EnemyType.GORILLA }
        assertNotNull(gorilla, "should spawn gorilla when kill count >= bossEveryNKills")
        assertTrue(sys.bossAlive, "bossAlive should be true after gorilla spawn")
        assertEquals(0, sys.regularKillsSinceBoss, "kill counter should reset")

        // While bossAlive, subsequent spawns should never be gorilla
        repeat(50) { sys.update(0.001f) }
        val extraGorillas = sys.enemies.count { it.type == EnemyType.GORILLA }
        assertEquals(1, extraGorillas,
            "only one gorilla while bossAlive is true")
    }

    @Test
    fun `speed jitter bounded within type range`() {
        repeat(100) { sys.update(0.001f) }
        for (e in sys.enemies) {
            val base = Tuning.Enemy.baseSpeed * e.type.speedMul
            val jitter = Tuning.Enemy.speedJitter
            assertTrue(e.speed >= base - jitter,
                "${e.type} speed ${e.speed} below floor ${base - jitter}")
            assertTrue(e.speed <= base + jitter,
                "${e.type} speed ${e.speed} above ceiling ${base + jitter}")
        }
    }

    @Test
    fun `spawn-time facing - right of camera faces left, left of camera faces right`() {
        repeat(200) { sys.update(0.001f) }
        for (e in sys.enemies) {
            if (e.position.x >= cam.x) {
                assertTrue(e.facingLeft,
                    "enemy at x=${e.position.x} (>= cam ${cam.x}) should face left")
            } else {
                assertFalse(e.facingLeft,
                    "enemy at x=${e.position.x} (< cam ${cam.x}) should face right")
            }
        }
    }

    @Test
    fun `determinism - same seed yields identical spawn sequence`() {
        val a = make(77)
        val b = make(77)
        repeat(50) {
            a.update(0.1f)
            b.update(0.1f)
        }
        assertEquals(a.enemies.size, b.enemies.size, "same count")
        for (i in a.enemies.indices) {
            assertEquals(a.enemies[i].type, b.enemies[i].type, "type mismatch at $i")
            assertEquals(a.enemies[i].speed, b.enemies[i].speed, 1e-6f, "speed mismatch at $i")
            assertEquals(a.enemies[i].facingLeft, b.enemies[i].facingLeft, "facing mismatch at $i")
            assertEquals(a.enemies[i].position.x, b.enemies[i].position.x, 1e-4f, "pos.x mismatch at $i")
            assertEquals(a.enemies[i].position.y, b.enemies[i].position.y, 1e-4f, "pos.y mismatch at $i")
        }
    }

    @Test
    fun `reset clears enemies and zeroes counters`() {
        repeat(30) { sys.update(0.1f) }
        assertTrue(sys.enemies.isNotEmpty())
        sys.bossAlive = true
        sys.regularKillsSinceBoss = 10

        sys.reset()

        assertTrue(sys.enemies.isEmpty(), "enemies should be cleared")
        assertFalse(sys.bossAlive, "bossAlive should be false")
        assertEquals(0, sys.regularKillsSinceBoss, "kill counter should be 0")
    }
}
