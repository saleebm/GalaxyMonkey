package dev.copt.galaxymonkey

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.IntBuffer

class ContactDispatcherTest {

    companion object {
        private lateinit var app: HeadlessApplication

        @JvmStatic
        @BeforeAll
        fun setup() {
            val gl = Proxy.newProxyInstance(
                GL20::class.java.classLoader, arrayOf(GL20::class.java)
            ) { _, method, _ ->
                when (method.returnType) {
                    Int::class.java, java.lang.Integer.TYPE -> 0
                    Boolean::class.java, java.lang.Boolean.TYPE -> false
                    Float::class.java, java.lang.Float.TYPE -> 0f
                    Long::class.java, java.lang.Long.TYPE -> 0L
                    String::class.java -> ""
                    Void.TYPE -> null
                    else -> null
                }
            } as GL20
            val config = HeadlessApplicationConfiguration().apply { updatesPerSecond = -1 }
            app = HeadlessApplication(object : ApplicationAdapter() {}, config)
            Gdx.gl = gl
            Gdx.gl20 = gl
        }

        @JvmStatic
        @AfterAll
        fun teardown() { app.exit() }
    }

    private data class Stub(
        override val position: Vector2 = Vector2(),
        override val radius: Float = 10f,
        override val category: Int,
        val isBombFlag: Boolean = false
    ) : Collidable

    private class SpyHandler : ContactHandler {
        val calls = mutableListOf<String>()
        var hitEnemyOutcome = HitOutcome.KILLED
        var tryPlayerHitOutcome = HitOutcome.KILLED

        override fun recycleBullet(c: Collidable) { calls += "recycleBullet" }
        override fun applyHitEnemy(enemy: Collidable): HitOutcome {
            calls += "applyHitEnemy"
            return hitEnemyOutcome
        }
        override fun onEnemyKilled(enemy: Collidable) { calls += "onEnemyKilled" }
        override fun tryPlayerHit(): HitOutcome {
            calls += "tryPlayerHit"
            return tryPlayerHitOutcome
        }
        override fun onPlayerHit(wasBomb: Boolean) { calls += "onPlayerHit($wasBomb)" }
        override fun detonateBomb(c: Collidable) { calls += "detonateBomb" }
        override fun isBomb(c: Collidable): Boolean {
            calls += "isBomb"
            return (c as? Stub)?.isBombFlag ?: false
        }
        override fun collectPickup(pickup: Collidable) { calls += "collectPickup" }
        override fun applyShake(intensity: Float) { calls += "applyShake($intensity)" }
        override fun triggerGameOver() { calls += "triggerGameOver" }
        override fun addScore(points: Int) { calls += "addScore($points)" }
    }

    private lateinit var spy: SpyHandler
    private lateinit var dispatcher: ContactDispatcher

    @BeforeEach
    fun reset() {
        spy = SpyHandler()
        dispatcher = ContactDispatcher(spy)
    }

    @Test
    fun `bullet enemy killed - recycle first then score and shake`() {
        val bullet = Stub(category = Category.bullet)
        val enemy = Stub(category = Category.enemy)
        dispatcher.dispatch(listOf(Contact(bullet, enemy)))

        assertEquals(
            listOf(
                "recycleBullet", "applyHitEnemy", "addScore(${Tuning.Enemy.pointsOnKill})",
                "onEnemyKilled", "applyShake(${Tuning.VFX.enemyKillShakeIntensity})"
            ),
            spy.calls
        )
    }

    @Test
    fun `bullet enemy survived - recycle and hit only, no score or shake`() {
        spy.hitEnemyOutcome = HitOutcome.SURVIVED
        val bullet = Stub(category = Category.bullet)
        val enemy = Stub(category = Category.enemy)
        dispatcher.dispatch(listOf(Contact(bullet, enemy)))

        assertEquals(listOf("recycleBullet", "applyHitEnemy"), spy.calls)
    }

    @Test
    fun `player enemy - player killed triggers game over`() {
        spy.tryPlayerHitOutcome = HitOutcome.KILLED
        val player = Stub(category = Category.player)
        val enemy = Stub(category = Category.enemy)
        dispatcher.dispatch(listOf(Contact(player, enemy)))

        assertEquals(
            listOf(
                "tryPlayerHit", "onPlayerHit(false)",
                "applyShake(${Tuning.VFX.playerHitShakeIntensity})",
                "onEnemyKilled", "triggerGameOver"
            ),
            spy.calls
        )
    }

    @Test
    fun `player enemy - player survived, no game over`() {
        spy.tryPlayerHitOutcome = HitOutcome.SURVIVED
        val player = Stub(category = Category.player)
        val enemy = Stub(category = Category.enemy)
        dispatcher.dispatch(listOf(Contact(player, enemy)))

        assertEquals(
            listOf(
                "tryPlayerHit", "onPlayerHit(false)",
                "applyShake(${Tuning.VFX.playerHitShakeIntensity})",
                "onEnemyKilled"
            ),
            spy.calls
        )
        assertFalse(spy.calls.contains("triggerGameOver"))
    }

    @Test
    fun `enemyBullet player bomb - detonates, no extra shake`() {
        spy.tryPlayerHitOutcome = HitOutcome.KILLED
        val bomb = Stub(category = Category.enemyBullet, isBombFlag = true)
        val player = Stub(category = Category.player)
        dispatcher.dispatch(listOf(Contact(bomb, player)))

        assertTrue(spy.calls.contains("detonateBomb"))
        assertTrue(spy.calls.contains("onPlayerHit(true)"))
        val shakeCalls = spy.calls.filter { it.startsWith("applyShake") }
        assertEquals(0, shakeCalls.size, "bomb path suppresses shake — detonateBomb handles its own")
    }

    @Test
    fun `enemyBullet player non-bomb - shake applied`() {
        spy.tryPlayerHitOutcome = HitOutcome.KILLED
        val bullet = Stub(category = Category.enemyBullet, isBombFlag = false)
        val player = Stub(category = Category.player)
        dispatcher.dispatch(listOf(Contact(bullet, player)))

        assertFalse(spy.calls.contains("detonateBomb"))
        assertTrue(spy.calls.contains("onPlayerHit(false)"))
        assertTrue(spy.calls.contains("applyShake(${Tuning.VFX.playerHitShakeIntensity})"))
    }

    @Test
    fun `player pickup - collect and score`() {
        val player = Stub(category = Category.player)
        val pickup = Stub(category = Category.pickup)
        dispatcher.dispatch(listOf(Contact(player, pickup)))

        assertEquals(
            listOf("collectPickup", "addScore(${Tuning.Pickup.scoreBonus})"),
            spy.calls
        )
    }

    @Test
    fun `unhandled mask - zero handler calls`() {
        val bullet = Stub(category = Category.bullet)
        val pickup = Stub(category = Category.pickup)
        dispatcher.dispatch(listOf(Contact(bullet, pickup)))

        assertTrue(spy.calls.isEmpty(), "unrecognized mask should produce no handler calls")
    }

    @Test
    fun `reversed contact order - still routes correctly`() {
        val enemy = Stub(category = Category.enemy)
        val bullet = Stub(category = Category.bullet)
        dispatcher.dispatch(listOf(Contact(enemy, bullet)))

        assertTrue(spy.calls.contains("recycleBullet"))
        assertTrue(spy.calls.contains("applyHitEnemy"))
    }
}
