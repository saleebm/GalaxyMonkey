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

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        layoutLabels()
    }

    fun render(delta: Float) {
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
