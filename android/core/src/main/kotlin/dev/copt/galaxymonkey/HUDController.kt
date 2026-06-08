package dev.copt.galaxymonkey

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport

class HUDController(
    private val font: BitmapFont,
    initialBest: Int = 0
) : Disposable {

    val stage: Stage = Stage(ScreenViewport())
    val shapeRenderer: ShapeRenderer = ShapeRenderer()

    var bestScore: Int = initialBest
        private set

    private val scoreStyle = Label.LabelStyle(font, Color.WHITE)
    private val bestStyle = Label.LabelStyle(font, Color(1f, 1f, 1f, 0.7f))

    val scoreLabel: Label = Label("Score: 0", scoreStyle).apply {
        setFontScale(SCORE_FONT_SCALE)
        setAlignment(Align.left)
    }

    val bestLabel: Label = Label("Best: $initialBest", bestStyle).apply {
        setFontScale(BEST_FONT_SCALE)
        setAlignment(Align.left)
    }

    val livesIcons: List<Image>
    private val livesFallbackLabel: Label?

    var pauseButton: Image? = null
        private set
    var onPausePressed: (() -> Unit)? = null

    init {
        stage.addActor(scoreLabel)
        stage.addActor(bestLabel)

        val heartRegion = try { SpriteCatalog.region(Sprite.LIFE_HEART) } catch (_: UninitializedPropertyAccessException) { null }
        if (heartRegion != null) {
            val scale = ICON_TARGET_SIZE / maxOf(heartRegion.regionWidth.toFloat(), heartRegion.regionHeight.toFloat())
            val iconW = heartRegion.regionWidth * scale
            val iconH = heartRegion.regionHeight * scale
            livesIcons = List(MAX_LIVES_ICON_SLOT) {
                Image(TextureRegionDrawable(heartRegion)).apply {
                    setSize(iconW, iconH)
                    isVisible = false
                }
            }
            livesIcons.forEach { stage.addActor(it) }
            livesFallbackLabel = null
        } else {
            livesIcons = emptyList()
            livesFallbackLabel = Label("Lives: 0", scoreStyle).apply {
                setFontScale(SCORE_FONT_SCALE)
                setAlignment(Align.right)
            }
            stage.addActor(livesFallbackLabel)
        }

        val pauseRegion = try { SpriteCatalog.region(Sprite.PAUSE_ICON) } catch (_: UninitializedPropertyAccessException) { null }
        if (pauseRegion != null) {
            val scale = PAUSE_ICON_SIZE / maxOf(pauseRegion.regionWidth.toFloat(), pauseRegion.regionHeight.toFloat())
            pauseButton = Image(TextureRegionDrawable(pauseRegion)).apply {
                setSize(pauseRegion.regionWidth * scale, pauseRegion.regionHeight * scale)
                isVisible = false
                name = PAUSE_BUTTON_NODE_NAME
                addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        onPausePressed?.invoke()
                    }
                })
            }
            stage.addActor(pauseButton)
        }

        layoutLabels()
    }

    val inputProcessor: InputProcessor get() = stage

    fun setScore(s: Int) {
        scoreLabel.setText("Score: $s")
    }

    fun setBest(b: Int) {
        bestScore = b
        bestLabel.setText("Best: $b")
    }

    fun setPauseButtonVisible(visible: Boolean) {
        pauseButton?.isVisible = visible
    }

    fun setLives(l: Int) {
        if (livesIcons.isNotEmpty()) {
            for (i in livesIcons.indices) {
                livesIcons[i].isVisible = i < l
            }
        } else {
            livesFallbackLabel?.setText("Lives: $l")
        }
    }

    private fun layoutLabels() {
        val h = stage.viewport.worldHeight
        val w = stage.viewport.worldWidth
        scoreLabel.setPosition(LABEL_X, h - SCORE_Y_OFFSET)
        bestLabel.setPosition(LABEL_X, h - BEST_Y_OFFSET)

        if (livesIcons.isNotEmpty()) {
            val rightEdge = w - LIVES_RIGHT_MARGIN
            val topY = h - SCORE_Y_OFFSET
            val iconW = livesIcons[0].width
            for (i in livesIcons.indices) {
                val x = rightEdge - i * (iconW + ICON_GAP) - iconW
                livesIcons[i].setPosition(x, topY)
            }
        }
        livesFallbackLabel?.setPosition(w - LIVES_RIGHT_MARGIN, h - SCORE_Y_OFFSET)

        pauseButton?.let { btn ->
            btn.setPosition(w - PAUSE_RIGHT_MARGIN - btn.width, h - PAUSE_Y_OFFSET)
        }
    }

    private var pauseMenuShown = false
    private var pauseDimLabel: Label? = null
    private var pauseTitleLabel: Label? = null
    private var pauseResumeLabel: Label? = null
    private var pauseSettingsLabel: Label? = null
    private var pauseQuitLabel: Label? = null
    private var resumePulseTime = 0f

    val isPauseMenuVisible: Boolean get() = pauseMenuShown

    fun showPauseMenu() {
        if (pauseMenuShown) return
        pauseMenuShown = true

        val w = stage.viewport.worldWidth
        val h = stage.viewport.worldHeight
        val cx = w / 2f
        val cy = h / 2f

        val dimStyle = Label.LabelStyle(font, Color(0f, 0f, 0f, PAUSE_DIM_ALPHA))
        pauseDimLabel = Label("", dimStyle).apply {
            setSize(w, h)
            setPosition(0f, 0f)
            name = PAUSE_MENU_DIM_NODE_NAME
        }
        stage.addActor(pauseDimLabel)

        val titleStyle = Label.LabelStyle(font, Color.WHITE)
        pauseTitleLabel = Label("PAUSED", titleStyle).apply {
            setFontScale(PAUSE_TITLE_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(cx, cy + 80f)
            setSize(0f, 0f)
        }
        stage.addActor(pauseTitleLabel)

        val resumeStyle = Label.LabelStyle(font, Color.WHITE)
        pauseResumeLabel = Label("Resume", resumeStyle).apply {
            setFontScale(PAUSE_OPTION_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(cx, cy + 10f)
            name = PAUSE_MENU_RESUME_NODE_NAME
        }
        stage.addActor(pauseResumeLabel)

        val settingsStyle = Label.LabelStyle(font, Color.WHITE)
        pauseSettingsLabel = Label("Settings", settingsStyle).apply {
            setFontScale(PAUSE_SETTINGS_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(cx, cy - 40f)
            name = PAUSE_MENU_SETTINGS_NODE_NAME
        }
        stage.addActor(pauseSettingsLabel)

        val quitStyle = Label.LabelStyle(font, Color(1f, 1f, 1f, 0.55f))
        pauseQuitLabel = Label("Quit to Title", quitStyle).apply {
            setFontScale(PAUSE_QUIT_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(cx, cy - 90f)
            name = PAUSE_MENU_QUIT_NODE_NAME
        }
        stage.addActor(pauseQuitLabel)
    }

    fun dismissPauseMenu() {
        if (!pauseMenuShown) return
        pauseMenuShown = false
        resumePulseTime = 0f
        pauseDimLabel?.remove(); pauseDimLabel = null
        pauseTitleLabel?.remove(); pauseTitleLabel = null
        pauseResumeLabel?.remove(); pauseResumeLabel = null
        pauseSettingsLabel?.remove(); pauseSettingsLabel = null
        pauseQuitLabel?.remove(); pauseQuitLabel = null
    }

    fun pauseMenuHit(touch: Vector2): String? {
        if (!pauseMenuShown) return null
        val hitSize = Vector2(PAUSE_HIT_WIDTH, PAUSE_HIT_HEIGHT)
        val resumePos = pauseResumeLabel?.let { Vector2(it.x, it.y) } ?: return null
        if (rectHit(resumePos, hitSize, touch)) return PAUSE_MENU_RESUME_NODE_NAME
        val settingsPos = pauseSettingsLabel?.let { Vector2(it.x, it.y) } ?: return null
        if (rectHit(settingsPos, hitSize, touch)) return PAUSE_MENU_SETTINGS_NODE_NAME
        val quitPos = pauseQuitLabel?.let { Vector2(it.x, it.y) } ?: return null
        if (rectHit(quitPos, hitSize, touch)) return PAUSE_MENU_QUIT_NODE_NAME
        return null
    }

    private var startPromptShown = false
    private var startDimLabel: Label? = null
    private var startTitleActor: com.badlogic.gdx.scenes.scene2d.Actor? = null
    private var startSubtitleLabel: Label? = null
    private var startTapLabel: Label? = null

    val isStartPromptVisible: Boolean get() = startPromptShown

    var onStartTapped: (() -> Unit)? = null

    fun showStartPrompt() {
        if (startPromptShown) return
        startPromptShown = true

        val w = stage.viewport.worldWidth
        val h = stage.viewport.worldHeight
        val cx = w / 2f
        val cy = h / 2f

        val dimStyle = Label.LabelStyle(font, Color(0f, 0f, 0f, START_DIM_ALPHA))
        startDimLabel = Label("", dimStyle).apply {
            setSize(w, h)
            setPosition(0f, 0f)
        }
        stage.addActor(startDimLabel)

        val titleRegion = try { SpriteCatalog.region(Sprite.TITLE) } catch (_: UninitializedPropertyAccessException) { null }
        if (titleRegion != null) {
            val capSize = minOf(w, h) * 0.55f
            val scale = minOf(capSize * 2f / titleRegion.regionWidth, capSize / titleRegion.regionHeight)
            startTitleActor = Image(TextureRegionDrawable(titleRegion)).apply {
                setSize(titleRegion.regionWidth * scale, titleRegion.regionHeight * scale)
                setPosition(cx - width / 2f, cy + 80f - height / 2f)
            }
        } else {
            val goldStyle = Label.LabelStyle(font, TITLE_GOLD_COLOR)
            startTitleActor = Label("GALAXY MONKEY", goldStyle).apply {
                setFontScale(START_TITLE_FONT_SCALE)
                setAlignment(Align.center)
                setPosition(cx, cy + 30f)
            }
        }
        stage.addActor(startTitleActor)

        val subStyle = Label.LabelStyle(font, Color(1f, 1f, 1f, 0.85f))
        startSubtitleLabel = Label("Left stick to move · Right stick to aim and fire", subStyle).apply {
            setFontScale(START_SUBTITLE_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(cx, cy - 30f)
        }
        stage.addActor(startSubtitleLabel)

        val tapStyle = Label.LabelStyle(font, Color.WHITE)
        startTapLabel = Label("Tap to start", tapStyle).apply {
            setFontScale(START_TAP_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(cx, cy - 70f)
        }
        stage.addActor(startTapLabel)
    }

    fun dismissStartPrompt() {
        if (!startPromptShown) return
        startPromptShown = false
        startTapPulseTime = 0f
        startDimLabel?.remove(); startDimLabel = null
        startTitleActor?.remove(); startTitleActor = null
        startSubtitleLabel?.remove(); startSubtitleLabel = null
        startTapLabel?.remove(); startTapLabel = null
    }

    private var startTapPulseTime = 0f

    private var gameOverShown = false
    private var gameOverDimLabel: Label? = null
    private var gameOverTitleLabel: Label? = null
    var gameOverResultLabel: Label? = null
        private set
    private var gameOverReplayLabel: Label? = null
    private var replayPulseTime = 0f

    val isGameOverVisible: Boolean get() = gameOverShown

    var onRestartTapped: (() -> Unit)? = null

    fun showGameOver(score: Int, best: Int) {
        if (gameOverShown) return
        gameOverShown = true

        val w = stage.viewport.worldWidth
        val h = stage.viewport.worldHeight
        val cx = w / 2f
        val cy = h / 2f
        val displayBest = maxOf(score, best)

        val dimStyle = Label.LabelStyle(font, Color(0f, 0f, 0f, PAUSE_DIM_ALPHA))
        gameOverDimLabel = Label("", dimStyle).apply {
            setSize(w, h)
            setPosition(0f, 0f)
        }
        stage.addActor(gameOverDimLabel)

        val titleStyle = Label.LabelStyle(font, GAME_OVER_RED)
        gameOverTitleLabel = Label("GAME OVER", titleStyle).apply {
            setFontScale(PAUSE_TITLE_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(cx, cy + 40f)
        }
        stage.addActor(gameOverTitleLabel)

        val resultStyle = Label.LabelStyle(font, Color.WHITE)
        gameOverResultLabel = Label("Score $score · Best $displayBest", resultStyle).apply {
            setFontScale(GAME_OVER_RESULT_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(cx, cy)
        }
        stage.addActor(gameOverResultLabel)

        val replayStyle = Label.LabelStyle(font, Color.WHITE)
        gameOverReplayLabel = Label("Tap to play again", replayStyle).apply {
            setFontScale(START_TAP_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(cx, cy - 40f)
        }
        stage.addActor(gameOverReplayLabel)
    }

    fun dismissGameOver() {
        if (!gameOverShown) return
        gameOverShown = false
        replayPulseTime = 0f
        gameOverDimLabel?.remove(); gameOverDimLabel = null
        gameOverTitleLabel?.remove(); gameOverTitleLabel = null
        gameOverResultLabel?.remove(); gameOverResultLabel = null
        gameOverReplayLabel?.remove(); gameOverReplayLabel = null
    }

    private var settingsMenuShown = false
    private var settingsDimLabel: Label? = null
    private var settingsTitleLabel: Label? = null
    private var settingsBackLabel: Label? = null
    private var settingsPanelX = 0f
    private var settingsPanelY = 0f
    private var settingsPanelW = 0f
    private var settingsPanelH = 0f
    private var settingsViewportX = 0f
    private var settingsViewportY = 0f
    private var settingsViewportW = 0f
    private var settingsViewportH = 0f
    var settingsContentY = 0f
        private set
    var settingsContentHeight = 0f

    val isSettingsMenuVisible: Boolean get() = settingsMenuShown

    var musicSliderValue = 0f
        private set
    var sfxSliderValue = 0f
        private set
    private var musicSliderLabel: Label? = null
    private var sfxSliderLabel: Label? = null
    private var musicRowContentY = 0f
    private var sfxRowContentY = 0f
    private var sliderCtrlCenterX = 0f

    var onMusicVolumeChanged: ((Float) -> Unit)? = null
    var onSfxVolumeChanged: ((Float) -> Unit)? = null

    fun showSettingsMenu(store: SettingsStore? = null) {
        if (settingsMenuShown) return
        settingsMenuShown = true

        val w = stage.viewport.worldWidth
        val h = stage.viewport.worldHeight
        val cx = w / 2f
        val cy = h / 2f

        settingsPanelW = SETTINGS_PANEL_WIDTH
        settingsPanelH = minOf(h - 24f, SETTINGS_PANEL_MAX_HEIGHT)
        settingsPanelX = cx - settingsPanelW / 2f
        settingsPanelY = cy - settingsPanelH / 2f

        settingsViewportW = settingsPanelW - 40f
        settingsViewportH = settingsPanelH - SETTINGS_TITLE_RESERVE - SETTINGS_BACK_RESERVE
        settingsViewportX = cx - settingsViewportW / 2f
        settingsViewportY = settingsPanelY + SETTINGS_BACK_RESERVE

        val dimStyle = Label.LabelStyle(font, Color(0f, 0f, 0f, SETTINGS_DIM_ALPHA))
        settingsDimLabel = Label("", dimStyle).apply {
            setSize(w, h)
            setPosition(0f, 0f)
        }
        stage.addActor(settingsDimLabel)

        val titleStyle = Label.LabelStyle(font, Color.WHITE)
        settingsTitleLabel = Label("SETTINGS", titleStyle).apply {
            setFontScale(SETTINGS_TITLE_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(cx, settingsPanelY + settingsPanelH - SETTINGS_TITLE_RESERVE / 2f)
        }
        stage.addActor(settingsTitleLabel)

        val backStyle = Label.LabelStyle(font, Color.WHITE)
        settingsBackLabel = Label("Back", backStyle).apply {
            setFontScale(START_TAP_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(cx, settingsPanelY + SETTINGS_BACK_RESERVE / 2f)
            name = SETTINGS_BACK_NODE_NAME
        }
        stage.addActor(settingsBackLabel)

        sliderCtrlCenterX = cx + SLIDER_LABEL_CTRL_X
        musicRowContentY = SLIDER_MUSIC_ROW_Y
        sfxRowContentY = SLIDER_SFX_ROW_Y

        val labelStyle = Label.LabelStyle(font, Color.WHITE)
        musicSliderLabel = Label("Music", labelStyle).apply {
            setFontScale(BEST_FONT_SCALE)
            setAlignment(Align.left)
            setPosition(cx + SLIDER_LABEL_X, settingsViewportY + musicRowContentY)
            name = SETTINGS_MUSIC_LABEL_NODE_NAME
        }
        stage.addActor(musicSliderLabel)

        sfxSliderLabel = Label("SFX", labelStyle).apply {
            setFontScale(BEST_FONT_SCALE)
            setAlignment(Align.left)
            setPosition(cx + SLIDER_LABEL_X, settingsViewportY + sfxRowContentY)
            name = SETTINGS_SFX_LABEL_NODE_NAME
        }
        stage.addActor(sfxSliderLabel)

        musicSliderValue = store?.musicVolume ?: 0.75f
        sfxSliderValue = store?.sfxVolume ?: 0.75f

        val hapticsRowY = HAPTICS_ROW_Y
        hapticsLabelActor = Label("Haptics", labelStyle).apply {
            setFontScale(BEST_FONT_SCALE)
            setAlignment(Align.left)
            setPosition(cx + SLIDER_LABEL_X, settingsViewportY + hapticsRowY)
            name = SETTINGS_HAPTICS_LABEL_NODE_NAME
        }
        stage.addActor(hapticsLabelActor)

        val onX = cx + SLIDER_LABEL_CTRL_X - TOGGLE_PILL_WIDTH / 2f - 4f
        val offX = cx + SLIDER_LABEL_CTRL_X + TOGGLE_PILL_WIDTH / 2f + 4f
        val toggleY = settingsViewportY + hapticsRowY

        hapticsOnLabel = Label("On", labelStyle).apply {
            setFontScale(BEST_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(onX, toggleY)
            name = SETTINGS_HAPTICS_ON_NODE_NAME
        }
        stage.addActor(hapticsOnLabel)

        hapticsOffLabel = Label("Off", labelStyle).apply {
            setFontScale(BEST_FONT_SCALE)
            setAlignment(Align.center)
            setPosition(offX, toggleY)
            name = SETTINGS_HAPTICS_OFF_NODE_NAME
        }
        stage.addActor(hapticsOffLabel)

        hapticsEnabled = store?.hapticsEnabled ?: true
        updateHapticsToggleState(hapticsEnabled)

        settingsContentHeight = SLIDER_MUSIC_ROW_Y + 30f
        settingsContentY = 0f
    }

    fun dismissSettingsMenu() {
        if (!settingsMenuShown) return
        settingsMenuShown = false
        settingsDimLabel?.remove(); settingsDimLabel = null
        settingsTitleLabel?.remove(); settingsTitleLabel = null
        settingsBackLabel?.remove(); settingsBackLabel = null
        musicSliderLabel?.remove(); musicSliderLabel = null
        sfxSliderLabel?.remove(); sfxSliderLabel = null
        hapticsLabelActor?.remove(); hapticsLabelActor = null
        hapticsOnLabel?.remove(); hapticsOnLabel = null
        hapticsOffLabel?.remove(); hapticsOffLabel = null
        settingsPanelX = 0f; settingsPanelY = 0f
        settingsPanelW = 0f; settingsPanelH = 0f
        settingsViewportW = 0f; settingsViewportH = 0f
        settingsContentY = 0f
        musicSliderValue = 0f; sfxSliderValue = 0f
    }

    fun panSettingsContent(dy: Float) {
        if (!settingsMenuShown) return
        val maxScroll = maxOf(0f, settingsContentHeight - settingsViewportH)
        settingsContentY = (settingsContentY + dy).coerceIn(0f, maxScroll)
    }

    fun settingsBackHit(touch: Vector2): Boolean {
        if (!settingsMenuShown) return false
        val backCenter = settingsBackLabel?.let { Vector2(it.x, it.y) } ?: return false
        return rectHit(backCenter, Vector2(BACK_PILL_WIDTH, BACK_PILL_HEIGHT), touch)
    }

    fun settingsPanelContains(touch: Vector2): Boolean {
        if (!settingsMenuShown) return false
        val dx = touch.x - (settingsPanelX + settingsPanelW / 2f)
        val dy = touch.y - (settingsPanelY + settingsPanelH / 2f)
        return kotlin.math.abs(dx) <= settingsPanelW / 2f && kotlin.math.abs(dy) <= settingsPanelH / 2f
    }

    fun settingsViewportContains(touch: Vector2): Boolean {
        if (!settingsMenuShown) return false
        val vpCx = settingsViewportX + settingsViewportW / 2f
        val vpCy = settingsViewportY + settingsViewportH / 2f
        val dx = touch.x - vpCx
        val dy = touch.y - vpCy
        return kotlin.math.abs(dx) <= settingsViewportW / 2f && kotlin.math.abs(dy) <= settingsViewportH / 2f
    }

    fun musicSliderTrackCenter(): Vector2 {
        val screenY = settingsViewportY + musicRowContentY - settingsContentY
        return Vector2(sliderCtrlCenterX, screenY)
    }

    fun sfxSliderTrackCenter(): Vector2 {
        val screenY = settingsViewportY + sfxRowContentY - settingsContentY
        return Vector2(sliderCtrlCenterX, screenY)
    }

    fun musicSliderThumbX(): Float {
        val trackLeft = sliderCtrlCenterX - SLIDER_TRACK_WIDTH / 2f
        return trackLeft + musicSliderValue.coerceIn(0f, 1f) * SLIDER_TRACK_WIDTH
    }

    fun sfxSliderThumbX(): Float {
        val trackLeft = sliderCtrlCenterX - SLIDER_TRACK_WIDTH / 2f
        return trackLeft + sfxSliderValue.coerceIn(0f, 1f) * SLIDER_TRACK_WIDTH
    }

    fun musicSliderHit(touch: Vector2): Boolean {
        if (!settingsMenuShown) return false
        val center = musicSliderTrackCenter()
        return rectHit(center, Vector2(SLIDER_TRACK_WIDTH, SLIDER_HIT_HEIGHT), touch)
    }

    fun sfxSliderHit(touch: Vector2): Boolean {
        if (!settingsMenuShown) return false
        val center = sfxSliderTrackCenter()
        return rectHit(center, Vector2(SLIDER_TRACK_WIDTH, SLIDER_HIT_HEIGHT), touch)
    }

    fun updateMusicSliderThumb(value: Float) {
        musicSliderValue = value.coerceIn(0f, 1f)
        onMusicVolumeChanged?.invoke(musicSliderValue)
    }

    fun updateSfxSliderThumb(value: Float) {
        sfxSliderValue = value.coerceIn(0f, 1f)
        onSfxVolumeChanged?.invoke(sfxSliderValue)
    }

    fun sliderValueFromTouch(touch: Vector2, trackCenterX: Float): Float {
        val trackLeft = trackCenterX - SLIDER_TRACK_WIDTH / 2f
        return ((touch.x - trackLeft) / SLIDER_TRACK_WIDTH).coerceIn(0f, 1f)
    }

    private var hapticsOnLabel: Label? = null
    private var hapticsOffLabel: Label? = null
    private var hapticsLabelActor: Label? = null
    private var hapticsEnabled = true

    fun settingsHapticsHit(touch: Vector2): Boolean? {
        if (!settingsMenuShown) return null
        val onCenter = hapticsOnLabel?.let { Vector2(it.x, it.y) } ?: return null
        val offCenter = hapticsOffLabel?.let { Vector2(it.x, it.y) } ?: return null
        val pillSize = Vector2(TOGGLE_PILL_WIDTH, TOGGLE_PILL_HEIGHT)
        if (rectHit(onCenter, pillSize, touch)) return true
        if (rectHit(offCenter, pillSize, touch)) return false
        return null
    }

    fun updateHapticsToggleState(enabled: Boolean) {
        hapticsEnabled = enabled
        hapticsOnLabel?.color = if (enabled) Color.WHITE else Color(1f, 1f, 1f, 0.35f)
        hapticsOffLabel?.color = if (!enabled) Color.WHITE else Color(1f, 1f, 1f, 0.35f)
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        layoutLabels()
    }

    fun render(delta: Float) {
        if (pauseMenuShown) {
            resumePulseTime += delta
            val alpha = 0.775f + 0.225f * kotlin.math.sin(resumePulseTime * 2f * kotlin.math.PI.toFloat() / RESUME_PULSE_PERIOD)
            pauseResumeLabel?.color?.a = alpha
        }
        if (startPromptShown) {
            startTapPulseTime += delta
            val alpha = 0.75f + 0.25f * kotlin.math.sin(startTapPulseTime * 2f * kotlin.math.PI.toFloat() / START_TAP_PULSE_PERIOD)
            startTapLabel?.color?.a = alpha
        }
        if (gameOverShown) {
            replayPulseTime += delta
            val alpha = 0.75f + 0.25f * kotlin.math.sin(replayPulseTime * 2f * kotlin.math.PI.toFloat() / START_TAP_PULSE_PERIOD)
            gameOverReplayLabel?.color?.a = alpha
        }
        stage.act(delta)
        stage.draw()
    }

    private var disposed = false

    override fun dispose() {
        if (disposed) return
        disposed = true
        stage.dispose()
        shapeRenderer.dispose()
    }

    companion object {
        fun rectHit(center: Vector2, size: Vector2, touch: Vector2): Boolean {
            val dx = touch.x - center.x
            val dy = touch.y - center.y
            return kotlin.math.abs(dx) <= size.x / 2f && kotlin.math.abs(dy) <= size.y / 2f
        }

        const val LABEL_X = 24f
        const val SCORE_Y_OFFSET = 36f
        const val BEST_Y_OFFSET = 56f
        const val SCORE_FONT_SCALE = 22f / 15f
        const val BEST_FONT_SCALE = 14f / 15f

        const val LIVES_RIGHT_MARGIN = 24f
        const val ICON_TARGET_SIZE = 32f
        const val ICON_GAP = 4f

        const val PAUSE_ICON_SIZE = 48f
        const val PAUSE_RIGHT_MARGIN = 24f
        const val PAUSE_Y_OFFSET = 92f
        const val PAUSE_BUTTON_NODE_NAME = "pauseButton"

        const val PAUSE_DIM_ALPHA = 0.55f
        const val PAUSE_TITLE_FONT_SCALE = 42f / 15f
        const val PAUSE_OPTION_FONT_SCALE = 32f / 15f
        const val PAUSE_SETTINGS_FONT_SCALE = 28f / 15f
        const val PAUSE_QUIT_FONT_SCALE = 22f / 15f
        const val RESUME_PULSE_PERIOD = 0.7f

        const val PAUSE_MENU_DIM_NODE_NAME = "pauseMenuDim"
        const val PAUSE_MENU_RESUME_NODE_NAME = "pauseMenuResume"
        const val PAUSE_MENU_SETTINGS_NODE_NAME = "pauseMenuSettings"
        const val PAUSE_MENU_QUIT_NODE_NAME = "pauseMenuQuit"

        val GAME_OVER_RED = Color(1f, 0.40f, 0.40f, 1f)
        const val GAME_OVER_RESULT_FONT_SCALE = 18f / 15f

        const val START_DIM_ALPHA = 0.3f
        val TITLE_GOLD_COLOR = Color(1f, 0.85f, 0.30f, 1f)
        const val START_TITLE_FONT_SCALE = 40f / 15f
        const val START_SUBTITLE_FONT_SCALE = 16f / 15f
        const val START_TAP_FONT_SCALE = 22f / 15f
        const val START_TAP_PULSE_PERIOD = 0.7f

        const val PAUSE_HIT_WIDTH = 280f
        const val PAUSE_HIT_HEIGHT = 48f
        const val TOGGLE_PILL_WIDTH = 80f
        const val TOGGLE_PILL_HEIGHT = 42f
        const val BACK_PILL_WIDTH = 160f
        const val BACK_PILL_HEIGHT = 46f
        const val SLIDER_TRACK_WIDTH = 200f
        const val SLIDER_TRACK_HEIGHT = 6f
        const val SLIDER_THUMB_RADIUS = 11f
        const val MAX_LIVES_ICON_SLOT = 5

        const val SETTINGS_PANEL_WIDTH = 380f
        const val SETTINGS_PANEL_MAX_HEIGHT = 460f
        const val SETTINGS_TITLE_RESERVE = 64f
        const val SETTINGS_BACK_RESERVE = 56f
        const val SETTINGS_DIM_ALPHA = 0.6f
        const val SETTINGS_TITLE_FONT_SCALE = 28f / 15f
        const val SETTINGS_BACK_NODE_NAME = "settingsBack"
        const val SETTINGS_MUSIC_LABEL_NODE_NAME = "settingsMusicLabel"
        const val SETTINGS_SFX_LABEL_NODE_NAME = "settingsSfxLabel"

        const val SLIDER_LABEL_X = -150f
        const val SLIDER_LABEL_CTRL_X = 50f
        const val SLIDER_MUSIC_ROW_Y = 80f
        const val SLIDER_SFX_ROW_Y = 40f
        const val SLIDER_HIT_HEIGHT = 44f

        const val HAPTICS_ROW_Y = 0f
        const val SETTINGS_HAPTICS_LABEL_NODE_NAME = "settingsHapticsLabel"
        const val SETTINGS_HAPTICS_ON_NODE_NAME = "settingsHapticsOn"
        const val SETTINGS_HAPTICS_OFF_NODE_NAME = "settingsHapticsOff"
    }
}
