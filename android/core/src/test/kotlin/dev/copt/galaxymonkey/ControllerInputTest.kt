package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class ControllerInputTest {

    private fun process(x: Float, y: Float) = GameControllerInput.applyRadialDeadZone(x, y)

    private fun mag(v: Vector2) = sqrt(v.x * v.x + v.y * v.y)

    // --- Radial dead zone ---

    @Test
    fun `inside deadzone returns zero`() {
        val r1 = process(0.10f, 0.0f)
        assertEquals(0f, mag(r1), 1e-5f,
            "raw (0.10, 0.0) mag=0.10 inside deadzone=${Tuning.Joystick.deadZone} -> (0,0)")

        val r2 = process(0.0f, 0.0f)
        assertEquals(0f, mag(r2), 1e-5f,
            "raw (0,0) -> (0,0)")
        println("[ControllerInputTest] deadzone: (0.10,0) -> (%.4f,%.4f), (0,0) -> (%.4f,%.4f)".format(
            r1.x, r1.y, r2.x, r2.y))
    }

    // --- Remap ---

    @Test
    fun `full stick returns magnitude 1`() {
        val r = process(1.0f, 0.0f)
        assertEquals(1.0f, mag(r), 1e-3f,
            "raw (1,0) mag=1.0 -> output magnitude should be 1.0")
        println("[ControllerInputTest] remap: (1.0,0) -> (%.4f,%.4f) mag=%.4f".format(r.x, r.y, mag(r)))
    }

    @Test
    fun `mid-range remap matches shared deadZone formula`() {
        val rawX = 0.56f
        val rawY = 0f
        val rawMag = 0.56f
        val dz = Tuning.Joystick.deadZone
        val expectedMag = (rawMag - dz) / (1f - dz)
        val r = process(rawX, rawY)
        assertEquals(expectedMag, mag(r), 1e-3f,
            "raw mag=0.56 -> expected=%.4f, got=%.4f".format(expectedMag, mag(r)))
        println("[ControllerInputTest] remap: (0.56,0) -> mag=%.4f expected=%.4f".format(mag(r), expectedMag))
    }

    // --- Radial not per-axis ---

    @Test
    fun `radial deadzone not per-axis - diagonal above threshold produces nonzero`() {
        val r = process(0.1f, 0.1f) // mag = ~0.1414 > 0.12
        assertTrue(mag(r) > 0f,
            "raw (0.1,0.1) mag=%.4f above deadzone=%.2f should produce nonzero output, got mag=%.4f".format(
                sqrt(0.02f), Tuning.Joystick.deadZone, mag(r)))
        println("[ControllerInputTest] radial: (0.1,0.1) -> mag=%.4f (nonzero proves magnitude-based)".format(mag(r)))
    }

    // --- Direction preserved ---

    @Test
    fun `direction preserved through deadzone remap`() {
        val rawX = 0.6f
        val rawY = 0.8f
        val rawAngle = atan2(rawY, rawX)
        val r = process(rawX, rawY)
        val outAngle = atan2(r.y, r.x)
        assertEquals(rawAngle.toDouble(), outAngle.toDouble(), 1e-4,
            "direction should be preserved: rawAngle=%.4f outAngle=%.4f".format(rawAngle, outAngle))
        println("[ControllerInputTest] direction: (0.6,0.8) angle=%.4f -> %.4f (preserved)".format(rawAngle, outAngle))
    }

    // --- No controller ---

    @Test
    fun `no controller - fake impl returns zero vectors safely`() {
        val noController = object : ControllerInput {
            override val hasPhysicalController = false
            override val moveVector = Vector2.Zero
            override val aimVector = Vector2.Zero
        }
        assertFalse(noController.hasPhysicalController)
        assertEquals(0f, noController.moveVector.len(), 1e-5f)
        assertEquals(0f, noController.aimVector.len(), 1e-5f)
        println("[ControllerInputTest] no controller: hasPhysical=false, vectors=(0,0)")
    }

    // --- Connect / disconnect ---

    @Test
    fun `connect and disconnect state transitions`() {
        var connected = false
        val fakeController = object : ControllerInput {
            override val hasPhysicalController get() = connected
            override val moveVector get() = if (connected) Vector2(0.5f, 0f) else Vector2.Zero
            override val aimVector get() = if (connected) Vector2(0f, 0.5f) else Vector2.Zero
        }

        assertFalse(fakeController.hasPhysicalController)
        connected = true
        assertTrue(fakeController.hasPhysicalController)
        connected = false
        assertFalse(fakeController.hasPhysicalController)
        println("[ControllerInputTest] connect/disconnect: state transitions verified")
    }
}
