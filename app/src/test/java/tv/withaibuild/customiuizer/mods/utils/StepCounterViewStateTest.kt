package tv.withaibuild.customiuizer.mods.utils

import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class StepCounterViewStateTest {
    @After
    fun clearViews() {
        @Suppress("UNCHECKED_CAST")
        (field("stepViewList").get(null) as MutableList<Any>).clear()
        field("stepsWithGoal").set(null, null)
        (field("scope").get(null) as CoroutineScope).cancel()
    }

    @Test
    fun newlyBoundViewDisplaysTheCurrentValueEvenWhenTheNextQueryIsUnchanged() {
        field("stepsWithGoal").set(null, "1234/8000")
        val view = TextView(RuntimeEnvironment.getApplication())

        StepCounterController.bindStepView(view)

        assertEquals("new header must not stay blank until the count changes", "1234/8000", view.text.toString())
    }

    private fun field(name: String) = StepCounterController::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }
}
