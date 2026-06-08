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
    }
}
