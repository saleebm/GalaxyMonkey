package dev.copt.galaxymonkey

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.ExtendViewport
import dev.copt.galaxymonkey.render.CameraFollow
import dev.copt.galaxymonkey.render.HaloTextures
import dev.copt.galaxymonkey.render.JoystickRenderer
import dev.copt.galaxymonkey.render.PlanetField
import dev.copt.galaxymonkey.render.ScreenShake
import dev.copt.galaxymonkey.render.Starfield
import dev.copt.galaxymonkey.render.TerminatorShader
import dev.copt.galaxymonkey.render.ThrusterEmitter
import dev.copt.galaxymonkey.render.VFXPool
import kotlin.math.cos
import kotlin.math.sin

class GameScreen(
    private val game: GalaxyMonkeyGame,
    private val prefs: Preferences? = null,
) : ScreenAdapter() {

    val camera = OrthographicCamera()
    private val viewport = ExtendViewport(WORLD_WIDTH, WORLD_HEIGHT, camera)
    private val batch get() = game.batch

    private val timestep = FixedTimestep(
        step = STEP,
        maxFrameTime = MAX_FRAME_TIME,
        onClamp = { raw, clamped ->
            Gdx.app.log("GameScreen", "frame delta ${raw}s exceeds clamp ${clamped}s, discarding excess")
        },
    )

    // --- Render layers (track6) ---
    internal val cameraFollow = CameraFollow()
    internal val screenShake = ScreenShake()
    internal val starfield = Starfield(WORLD_WIDTH, WORLD_HEIGHT)
    internal val planetField = PlanetField(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f)
    internal val vfxPool = VFXPool()
    internal val thruster = ThrusterEmitter()
    private val shapeRenderer = ShapeRenderer()
    private val screenProjection = Matrix4()

    // --- Gameplay systems ---
    internal val player = Player(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, game.haptics)
    private val moveStick = VirtualJoystick(JoystickSide.LEFT)
    private val aimStick = VirtualJoystick(JoystickSide.RIGHT)
    private val gameplayInput = GameplayInputProcessor(moveStick, aimStick)
    private val controllerInput = GameControllerInput()
    private val inputSource = InputSource(controllerInput, moveStick, aimStick)

    internal val enemySystem = EnemySystem(
        playerPosition = { player.position },
        cameraPosition = { Vector2(camera.position.x, camera.position.y) },
        viewSize = { Vector2(viewport.worldWidth, viewport.worldHeight) },
        enemyFireRequest = { origin, angle, kind ->
            when (kind) {
                ProjectileKind.BULLET -> projectileSystem.fireEnemyBullet(origin, angle)
                ProjectileKind.BOMB -> projectileSystem.fireBomb(origin, angle)
            }
        },
    )
    internal val projectileSystem = ProjectileSystem()
    internal val pickupSystem = PickupSystem()
    internal val collisionSystem = CollisionSystem()

    private val audio = game.audio
    private val settings = game.settings
    internal val hud = HUDController(game.font, bestScore())
    private val settingsInput = SettingsInputHandler(hud, audio, game.haptics, settings)
    private val joystickRenderer = JoystickRenderer()

    private val contactHandler = GameContactHandler()
    private val contactDispatcher = ContactDispatcher(contactHandler)

    private var gameTime = 0f
    private var damageFlashTimer = 0f

    init {
        TerminatorShader.init()
        screenProjection.setToOrtho2D(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT)

        audio.listenerProvider = { player.position }

        player.onLivesChanged = { lives -> hud.setLives(lives) }

        projectileSystem.onBombExpire = { pos ->
            vfxPool.spawnGlow(pos.x, pos.y,
                Tuning.VFX.glowBombScale, Tuning.VFX.glowBombDuration)
            screenShake.applyShake(Tuning.VFX.bombShakeIntensity)
            audio.play(AudioController.Sfx.EXPLOSION, pos)
        }

        hud.onPausePressed = { enterPauseMenu() }
    }

    // --- State ---

    var isStarted = false
        private set
    var isGameOver = false
        private set
    var isInPauseMenu = false
        private set
    var isInSettings = false
        private set
    var gameplayPaused = false
        private set

    var score = 0
        private set

    var onStartRun: (() -> Unit)? = null
    var onGameOver: ((score: Int, best: Int) -> Unit)? = null
    var onRestart: (() -> Unit)? = null
    var onReturnToStart: (() -> Unit)? = null

    fun bestScore(): Int = prefs?.getInteger(KEY_BEST_SCORE, 0) ?: 0

    private fun persistBest(value: Int) {
        prefs?.putInteger(KEY_BEST_SCORE, value)
        prefs?.flush()
    }

    fun addScore(points: Int) {
        score += points
        hud.setScore(score)
    }

    // --- Per-frame ---

    override fun render(delta: Float) {
        timestep.advance(delta) { update(it) }
        draw()
        hud.render(delta)
        joystickRenderer.render(moveStick, aimStick)
    }

    fun resetClock() {
        timestep.resetClock()
    }

    private fun update(dt: Float) {
        if (!isStarted || isGameOver) return
        if (gameplayPaused) return

        gameTime += dt

        val move = inputSource.moveVector()
        val aim = inputSource.aimVector()

        player.update(dt, move, aim)

        if (player.isFiring) {
            val fired = projectileSystem.tryFirePlayer(
                player.position, player.aimAngle, player.spreadLevel, gameTime
            )
            if (fired) audio.play(AudioController.Sfx.PLAYER_SHOT, Tuning.Audio.playerShotVolume)
        }

        enemySystem.update(dt)
        projectileSystem.update(dt)
        pickupSystem.update(dt)

        collisionSystem.clear()
        collisionSystem.register(player)
        for (e in enemySystem.enemies) collisionSystem.register(e)
        for (b in projectileSystem.active) collisionSystem.register(b)
        for (p in pickupSystem.active) collisionSystem.register(PickupCollidable(p))
        val contacts = collisionSystem.update()
        contactDispatcher.dispatch(contacts)

        if (damageFlashTimer > 0f) damageFlashTimer -= dt

        cameraFollow.update(dt, player.position.x, player.position.y)
        camera.position.set(
            cameraFollow.cameraX + screenShake.offsetX,
            cameraFollow.cameraY + screenShake.offsetY,
            0f
        )

        starfield.update(dt, cameraFollow.cameraDeltaX, cameraFollow.cameraDeltaY)
        planetField.update(dt)
        vfxPool.update(dt)

        val tailOff = Tuning.VFX.thrustTailOffsetX
        thruster.emitterX = player.position.x + tailOff * cos(player.aimAngle)
        thruster.emitterY = player.position.y + tailOff * sin(player.aimAngle)
        thruster.emitterAngle = player.aimAngle + MathUtils.PI
        thruster.setIntensity(player.thrustIntensity)
        thruster.update(dt)

        screenShake.update()
    }

    private fun draw() {
        Gdx.gl.glClearColor(0.02f, 0.03f, 0.08f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()

        batch.projectionMatrix = screenProjection
        batch.begin()
        starfield.draw(batch)
        batch.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        planetField.drawRings(shapeRenderer)
        shapeRenderer.end()

        batch.projectionMatrix = camera.combined
        batch.begin()
        planetField.drawBodies(batch)
        planetField.drawComets(batch)
        drawEnemies()
        drawPickups()
        drawPlayer()
        drawBullets()
        thruster.render(batch)
        vfxPool.renderDissolves(batch)
        vfxPool.renderAdditive(batch)
        batch.end()
    }

    // --- Entity rendering ---

    private fun drawPlayer() {
        if (!isStarted) return
        val region = SpriteCatalog.region(Sprite.PLAYER) ?: return
        val w = Tuning.Player.radius * 2f
        val h = w
        val prevColor = batch.packedColor
        batch.setColor(1f, 1f, 1f, player.visualAlpha)
        batch.draw(
            region,
            player.position.x - w / 2f, player.position.y - h / 2f,
            w / 2f, h / 2f, w, h,
            1f, 1f,
            MathUtils.radiansToDegrees * player.bankRotation
        )
        batch.packedColor = prevColor
    }

    private fun drawEnemies() {
        for (e in enemySystem.enemies) {
            val sprite = if (e.facingLeft) e.type.leftSprite else e.type.rightSprite
            val region = SpriteCatalog.region(sprite) ?: continue
            val w = e.radius * 2f
            val h = w
            batch.draw(region, e.position.x - w / 2f, e.position.y - h / 2f, w, h)
        }
    }

    private fun drawBullets() {
        for (b in projectileSystem.active) {
            if (b.isHidden) continue
            val region = SpriteCatalog.region(b.spriteKey) ?: continue
            val d = b.visualRadius * 2f
            batch.draw(
                region,
                b.position.x - d / 2f, b.position.y - d / 2f,
                d / 2f, d / 2f, d, d,
                1f, 1f,
                MathUtils.radiansToDegrees * b.zRotation
            )
        }
    }

    private fun drawPickups() {
        for (p in pickupSystem.active) {
            val region = SpriteCatalog.region(p.sprite) ?: continue
            val maxDim = maxOf(region.regionWidth.toFloat(), region.regionHeight.toFloat())
            val scale = Tuning.Pickup.spriteTargetMax / maxDim
            val w = region.regionWidth * scale
            val h = region.regionHeight * scale
            batch.draw(
                region,
                p.position.x - w / 2f, p.position.y + p.bobOffsetY - h / 2f,
                w, h
            )
        }
    }

    private fun enemyRegion(e: Enemy): TextureRegion? {
        val sprite = if (e.facingLeft) e.type.leftSprite else e.type.rightSprite
        return SpriteCatalog.region(sprite)
    }

    // --- Contact handler ---

    private inner class GameContactHandler : ContactHandler {
        override fun recycleBullet(c: Collidable) {
            val bullet = projectileSystem.findBullet(c) ?: return
            projectileSystem.recycle(bullet)
        }

        override fun applyHitEnemy(enemy: Collidable): HitOutcome {
            val e = enemy as? Enemy ?: return HitOutcome.INVULNERABLE
            return when (enemySystem.applyHit(e)) {
                is EnemyHitResult.StillAlive -> HitOutcome.SURVIVED
                is EnemyHitResult.Killed -> HitOutcome.KILLED
            }
        }

        override fun onEnemyKilled(enemy: Collidable) {
            val e = enemy as? Enemy ?: return
            val region = enemyRegion(e)
            val w = e.radius * 2f
            vfxPool.spawnBlackholeWarp(e.position.x, e.position.y, region, w, w)
            audio.play(AudioController.Sfx.EXPLOSION, e.position)
            pickupSystem.trySpawnGoldenBanana(e.position)
            if (e.type == EnemyType.GORILLA) game.haptics.notification(NotificationType.SUCCESS)
        }

        override fun tryPlayerHit(): HitOutcome {
            if (!player.isAlive) return HitOutcome.KILLED
            return if (player.tryTakeHit()) {
                if (player.isAlive) HitOutcome.SURVIVED else HitOutcome.KILLED
            } else {
                HitOutcome.INVULNERABLE
            }
        }

        override fun onPlayerHit(wasBomb: Boolean) {
            audio.play(AudioController.Sfx.PLAYER_HIT)
            vfxPool.spawnDamageFlash { damageFlashTimer = it }
        }

        override fun detonateBomb(c: Collidable) {
            vfxPool.spawnGlow(c.position.x, c.position.y,
                Tuning.VFX.bombExplosionScale, Tuning.VFX.glowBombDuration)
            screenShake.applyShake(Tuning.VFX.bombShakeIntensity)
            audio.play(AudioController.Sfx.EXPLOSION, c.position)
        }

        override fun isBomb(c: Collidable): Boolean {
            val bullet = projectileSystem.findBullet(c) ?: return false
            return bullet.isBomb
        }

        override fun collectPickup(pickup: Collidable) {
            val pc = pickup as? PickupCollidable ?: return
            pickupSystem.collect(pc.pickup)
            audio.play(AudioController.Sfx.PICKUP, pc.pickup.position)
            if (!player.upgradeSpread()) player.restoreLife()
        }

        override fun applyShake(intensity: Float) {
            screenShake.applyShake(intensity)
        }

        override fun triggerGameOver() {
            this@GameScreen.triggerGameOver()
        }

        override fun addScore(points: Int) {
            this@GameScreen.addScore(points)
        }
    }

    // --- Pickup collidable adapter ---

    private class PickupCollidable(val pickup: PickupSystem.Pickup) : Collidable {
        override val position: Vector2 get() = pickup.position
        override val radius: Float = pickup.collisionRadius
        override val category: Int = pickup.categoryBits
    }

    // --- Lifecycle orchestration ---

    fun startGame() {
        isStarted = true
        isGameOver = false
        gameplayPaused = false
        score = 0
        gameTime = 0f
        damageFlashTimer = 0f

        player.reset()
        enemySystem.reset()
        projectileSystem.clearAll()
        pickupSystem.clearAll()
        collisionSystem.clear()

        cameraFollow.snapTo(player.position.x, player.position.y)
        camera.position.set(player.position.x, player.position.y, 0f)

        hud.setScore(0)
        hud.setLives(player.lives)
        hud.setPauseButtonVisible(true)
        hud.setBest(bestScore())

        audio.startMusic()
        resetClock()
        onStartRun?.invoke()
        Gdx.app.log("GameScreen", "gameflow: start")
    }

    fun enterPauseMenu() {
        isInPauseMenu = true
        gameplayPaused = true
        hud.showPauseMenu()
        hud.setPauseButtonVisible(false)
        audio.duckMusic()
        gameplayInput.cancelAll()
        Gdx.app.log("GameScreen", "lifecycle: enterPauseMenu")
    }

    fun dismissPauseMenusAndResume() {
        hud.dismissPauseMenu()
        hud.dismissSettingsMenu()
        isInPauseMenu = false
        isInSettings = false
        hud.setPauseButtonVisible(true)
        audio.unduckMusic()
        resetClock()
        gameplayPaused = false
        Gdx.app.log("GameScreen", "lifecycle: resume gameplayPaused=false")
    }

    fun enterSettings() {
        isInSettings = true
        gameplayPaused = true
        settingsInput.show()
        Gdx.app.log("GameScreen", "lifecycle: enterSettings")
    }

    fun dismissSettings() {
        isInSettings = false
        settingsInput.dismiss()
        if (!isInPauseMenu) {
            resetClock()
            gameplayPaused = false
            Gdx.app.log("GameScreen", "lifecycle: dismissSettings, resume gameplayPaused=false")
        } else {
            Gdx.app.log("GameScreen", "lifecycle: dismissSettings, still in pause menu")
        }
    }

    fun triggerGameOver() {
        if (isGameOver) return
        isGameOver = true
        gameplayPaused = true
        val best = bestScore()
        val newBest = maxOf(score, best)
        if (newBest > best) persistBest(newBest)
        hud.setPauseButtonVisible(false)
        hud.showGameOver(score, newBest)
        audio.play(AudioController.Sfx.GAME_OVER)
        audio.stopMusic()
        gameplayInput.cancelAll()
        onGameOver?.invoke(score, newBest)
        Gdx.app.log("GameScreen", "gameflow: gameover score=$score best=$newBest")
    }

    fun restart() {
        hud.dismissGameOver()
        isGameOver = false
        isInPauseMenu = false
        isInSettings = false
        gameplayPaused = false
        score = 0
        gameTime = 0f
        damageFlashTimer = 0f

        player.reset()
        enemySystem.reset()
        projectileSystem.clearAll()
        pickupSystem.clearAll()
        collisionSystem.clear()

        cameraFollow.snapTo(player.position.x, player.position.y)
        camera.position.set(player.position.x, player.position.y, 0f)

        hud.setScore(0)
        hud.setLives(player.lives)
        hud.setPauseButtonVisible(true)
        hud.setBest(bestScore())

        audio.startMusic()
        resetClock()
        isStarted = true
        onRestart?.invoke()
        Gdx.app.log("GameScreen", "gameflow: restart")
    }

    fun returnToStart() {
        hud.dismissPauseMenu()
        hud.dismissGameOver()
        hud.dismissSettingsMenu()
        settingsInput.dismiss()
        isStarted = false
        isGameOver = false
        isInPauseMenu = false
        isInSettings = false
        gameplayPaused = false
        score = 0
        gameTime = 0f
        damageFlashTimer = 0f

        player.reset()
        enemySystem.reset()
        projectileSystem.clearAll()
        pickupSystem.clearAll()
        collisionSystem.clear()

        hud.setScore(0)
        hud.setLives(player.lives)
        hud.setPauseButtonVisible(false)

        audio.stopMusic()
        resetClock()
        hud.showStartPrompt()
        onReturnToStart?.invoke()
        Gdx.app.log("GameScreen", "gameflow: returnToStart")
    }

    override fun pause() {
        if (isStarted && !isGameOver && !isInPauseMenu && !isInSettings) {
            enterPauseMenu()
        } else {
            gameplayPaused = true
        }
        Gdx.app.log("GameScreen", "lifecycle: pause (Android home/lock)")
    }

    override fun resume() {
        if (isInPauseMenu || isInSettings) {
            Gdx.app.log("GameScreen", "lifecycle: resume, menu open — staying paused")
        } else {
            resetClock()
            gameplayPaused = false
            Gdx.app.log("GameScreen", "lifecycle: resume gameplayPaused=false")
        }
    }

    override fun show() {
        hud.showStartPrompt()
        setupInputProcessors()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        starfield.resize(viewport.worldWidth, viewport.worldHeight)
        screenProjection.setToOrtho2D(0f, 0f, viewport.worldWidth, viewport.worldHeight)
        cameraFollow.snapTo(camera.position.x, camera.position.y)
        hud.resize(width, height)
        Gdx.app.log("GameScreen", "resize ${width}x${height} -> world ${viewport.worldWidth}x${viewport.worldHeight}")
    }

    override fun dispose() {
        starfield.dispose()
        planetField.dispose()
        vfxPool.dispose()
        thruster.dispose()
        shapeRenderer.dispose()
        joystickRenderer.dispose()
        hud.dispose()
        HaloTextures.dispose()
        TerminatorShader.dispose()
    }

    // --- Input multiplexing ---

    private fun setupInputProcessors() {
        val mux = InputMultiplexer()
        mux.addProcessor(menuInputAdapter)
        mux.addProcessor(hud.inputProcessor)
        mux.addProcessor(gameplayInput)
        Gdx.input.inputProcessor = mux
    }

    private val menuInputAdapter = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (isInSettings) return settingsInput.touchDown(touchToHud(screenX, screenY))
            return false
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (isInSettings && settingsInput.activeDrag != null) {
                return settingsInput.touchDragged(touchToHud(screenX, screenY))
            }
            return false
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            val touch = touchToHud(screenX, screenY)

            if (isInSettings) {
                val action = settingsInput.touchUp(touch)
                if (action == SettingsInputHandler.Action.EXIT_TO_PAUSE) dismissSettings()
                return true
            }

            if (isInPauseMenu) {
                when (hud.pauseMenuHit(touch)) {
                    HUDController.PAUSE_MENU_RESUME_NODE_NAME -> {
                        game.haptics.impact(ImpactStyle.LIGHT)
                        dismissPauseMenusAndResume()
                    }
                    HUDController.PAUSE_MENU_SETTINGS_NODE_NAME -> {
                        game.haptics.impact(ImpactStyle.LIGHT)
                        enterSettings()
                    }
                    HUDController.PAUSE_MENU_QUIT_NODE_NAME -> {
                        game.haptics.impact(ImpactStyle.LIGHT)
                        returnToStart()
                    }
                }
                return true
            }

            if (!isStarted && hud.isStartPromptVisible) {
                hud.dismissStartPrompt()
                startGame()
                return true
            }

            if (isGameOver && hud.isGameOverVisible) {
                hud.dismissGameOver()
                restart()
                return true
            }

            return false
        }
    }

    private fun touchToHud(screenX: Int, screenY: Int): Vector2 {
        return Vector2(screenX.toFloat(), Gdx.graphics.height.toFloat() - screenY)
    }

    companion object {
        const val WORLD_WIDTH = 812f
        const val WORLD_HEIGHT = 375f
        const val STEP = 1f / 60f
        const val MAX_FRAME_TIME = 0.25f
        const val KEY_BEST_SCORE = "best_score"
        const val PREFS_NAME = "galaxymonkey"
    }
}
