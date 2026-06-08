package dev.copt.galaxymonkey

import com.badlogic.gdx.math.Vector2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InputSourceTest {

    private class FakeController : ControllerInput {
        var connected = false
        var move = Vector2()
        var aim = Vector2()

        override val hasPhysicalController: Boolean get() = connected
        override val moveVector: Vector2 get() = move
        override val aimVector: Vector2 get() = aim
    }

    private fun makeSource(
        controller: FakeController = FakeController()
    ): Triple<InputSource, FakeController, Pair<VirtualJoystick, VirtualJoystick>> {
        val left = VirtualJoystick(JoystickSide.LEFT)
        val right = VirtualJoystick(JoystickSide.RIGHT)
        return Triple(InputSource(controller, left, right), controller, left to right)
    }

    @Test
    fun `touch only - returns joystick vectors when no controller`() {
        val (source, ctrl, sticks) = makeSource()
        val (left, right) = sticks
        ctrl.connected = false

        left.begin(0, 100f, 300f, 1000f)
        left.moved(0, 150f, 300f)
        right.begin(1, 900f, 300f, 1000f)
        right.moved(1, 900f, 370f)

        val move = source.moveVector()
        val aim = source.aimVector()
        assertTrue(move.x > 0, "touch move should have +x, got ${move.x}")
        assertTrue(aim.y > 0, "touch aim should have +y, got ${aim.y}")
    }

    @Test
    fun `controller wins - overrides touch vectors when connected`() {
        val (source, ctrl, sticks) = makeSource()
        val (left, right) = sticks
        left.begin(0, 100f, 300f, 1000f)
        left.moved(0, 150f, 300f)
        right.begin(1, 900f, 300f, 1000f)
        right.moved(1, 900f, 370f)

        ctrl.connected = true
        ctrl.move = Vector2(0.9f, 0f)
        ctrl.aim = Vector2(0f, -0.3f)

        val move = source.moveVector()
        val aim = source.aimVector()
        assertEquals(0.9f, move.x, 0.001f, "controller move.x should win")
        assertEquals(0f, move.y, 0.001f, "controller move.y should win")
        assertEquals(0f, aim.x, 0.001f, "controller aim.x should win")
        assertEquals(-0.3f, aim.y, 0.001f, "controller aim.y should win")
    }

    @Test
    fun `fallback - disconnecting controller returns to touch`() {
        val (source, ctrl, sticks) = makeSource()
        val (left, right) = sticks
        left.begin(0, 100f, 300f, 1000f)
        left.moved(0, 150f, 300f)

        ctrl.connected = true
        ctrl.move = Vector2(0.9f, 0f)
        assertEquals(0.9f, source.moveVector().x, 0.001f)

        ctrl.connected = false
        val move = source.moveVector()
        assertTrue(move.x > 0 && move.x < 0.9f,
            "after disconnect, should return touch vector, got ${move.x}")
    }

    @Test
    fun `isUsingController matches hasPhysicalController`() {
        val (source, ctrl, _) = makeSource()
        ctrl.connected = false
        assertFalse(source.isUsingController)
        ctrl.connected = true
        assertTrue(source.isUsingController)
    }

    @Test
    fun `zero safety - connected controller at rest returns zero vectors`() {
        val (source, ctrl, _) = makeSource()
        ctrl.connected = true
        ctrl.move = Vector2(0f, 0f)
        ctrl.aim = Vector2(0f, 0f)
        assertEquals(0f, source.moveVector().x, 0.001f)
        assertEquals(0f, source.moveVector().y, 0.001f)
        assertEquals(0f, source.aimVector().x, 0.001f)
        assertEquals(0f, source.aimVector().y, 0.001f)
    }
}
