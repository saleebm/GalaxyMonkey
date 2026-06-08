package dev.copt.galaxymonkey

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {

    @Test
    fun activityReachesResumedWithoutCrash() {
        val scenario = ActivityScenario.launch(AndroidLauncher::class.java)
        scenario.onActivity { activity ->
            Log.i(TAG, "scenario RESUMED — activity=$activity")
            assertTrue("GL surface should be non-null", activity.graphics != null)
        }
        assertTrue(
            "Activity should reach RESUMED",
            scenario.state.isAtLeast(Lifecycle.State.RESUMED)
        )
        scenario.close()
        Log.i(TAG, "scenario closed cleanly")
    }

    companion object {
        private const val TAG = "LaunchSmokeTest"
    }
}
