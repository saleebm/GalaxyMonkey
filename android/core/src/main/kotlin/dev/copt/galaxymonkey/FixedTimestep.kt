package dev.copt.galaxymonkey

class FixedTimestep(
    private val step: Float = 1f / 60f,
    private val maxFrameTime: Float = 0.25f,
    private val onClamp: ((raw: Float, clamped: Float) -> Unit)? = null,
) {
    private var accumulator = 0f
    private var firstFrameAfterReset = true

    fun advance(delta: Float, tick: (Float) -> Unit): Int {
        val clamped = if (firstFrameAfterReset) {
            firstFrameAfterReset = false
            accumulator = 0f
            0f
        } else if (delta > maxFrameTime) {
            onClamp?.invoke(delta, maxFrameTime)
            maxFrameTime
        } else {
            delta
        }

        accumulator += clamped
        var steps = 0
        while (accumulator >= step) {
            tick(step)
            accumulator -= step
            steps++
        }
        return steps
    }

    val remainder: Float get() = accumulator

    fun resetClock() {
        firstFrameAfterReset = true
        accumulator = 0f
    }
}
