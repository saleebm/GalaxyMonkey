package dev.copt.galaxymonkey

import com.badlogic.gdx.Gdx

enum class HitOutcome { SURVIVED, KILLED, INVULNERABLE }

interface ContactHandler {
    fun recycleBullet(c: Collidable)
    fun applyHitEnemy(enemy: Collidable): HitOutcome
    fun onEnemyKilled(enemy: Collidable)
    fun tryPlayerHit(): HitOutcome
    fun onPlayerHit(wasBomb: Boolean)
    fun detonateBomb(c: Collidable)
    fun isBomb(c: Collidable): Boolean
    fun collectPickup(pickup: Collidable)
    fun applyShake(intensity: Float)
    fun triggerGameOver()
    fun addScore(points: Int)
}

class ContactDispatcher(private val handler: ContactHandler) {

    fun dispatch(contacts: List<Contact>) {
        for (contact in contacts) {
            val mask = contact.a.category or contact.b.category
            when (mask) {
                Category.bullet or Category.enemy -> onBulletEnemy(contact)
                Category.player or Category.enemy -> onPlayerEnemy(contact)
                Category.enemyBullet or Category.player -> onEnemyBulletPlayer(contact)
                Category.player or Category.pickup -> onPlayerPickup(contact)
            }
        }
    }

    private fun resolve(contact: Contact, cat: Int): Pair<Collidable, Collidable> {
        return if (contact.a.category == cat) contact.a to contact.b
        else contact.b to contact.a
    }

    private fun onBulletEnemy(contact: Contact) {
        val (bullet, enemy) = resolve(contact, Category.bullet)
        Gdx.app.debug("ContactDispatcher", "contact: bullet|enemy -> handler")
        handler.recycleBullet(bullet)
        val outcome = handler.applyHitEnemy(enemy)
        if (outcome == HitOutcome.KILLED) {
            handler.addScore(Tuning.Enemy.pointsOnKill)
            handler.onEnemyKilled(enemy)
            handler.applyShake(Tuning.VFX.enemyKillShakeIntensity)
        }
    }

    private fun onPlayerEnemy(contact: Contact) {
        Gdx.app.debug("ContactDispatcher", "contact: player|enemy -> handler")
        val (_, enemy) = resolve(contact, Category.player)
        val outcome = handler.tryPlayerHit()
        if (outcome == HitOutcome.KILLED) {
            handler.onPlayerHit(false)
            handler.applyShake(Tuning.VFX.playerHitShakeIntensity)
            handler.onEnemyKilled(enemy)
            handler.triggerGameOver()
        } else if (outcome == HitOutcome.SURVIVED) {
            handler.onPlayerHit(false)
            handler.applyShake(Tuning.VFX.playerHitShakeIntensity)
            handler.onEnemyKilled(enemy)
        }
    }

    private fun onEnemyBulletPlayer(contact: Contact) {
        val (enemyBullet, _) = resolve(contact, Category.enemyBullet)
        val wasBomb = handler.isBomb(enemyBullet)
        Gdx.app.debug("ContactDispatcher", "contact: enemyBullet|player wasBomb=$wasBomb -> handler")
        handler.recycleBullet(enemyBullet)
        if (wasBomb) handler.detonateBomb(enemyBullet)
        val outcome = handler.tryPlayerHit()
        if (outcome == HitOutcome.KILLED) {
            handler.onPlayerHit(wasBomb)
            if (!wasBomb) handler.applyShake(Tuning.VFX.playerHitShakeIntensity)
            handler.triggerGameOver()
        } else if (outcome == HitOutcome.SURVIVED) {
            handler.onPlayerHit(wasBomb)
            if (!wasBomb) handler.applyShake(Tuning.VFX.playerHitShakeIntensity)
        }
    }

    private fun onPlayerPickup(contact: Contact) {
        val (_, pickup) = resolve(contact, Category.player)
        Gdx.app.debug("ContactDispatcher", "contact: player|pickup -> handler")
        handler.collectPickup(pickup)
        handler.addScore(Tuning.Pickup.scoreBonus)
    }
}
