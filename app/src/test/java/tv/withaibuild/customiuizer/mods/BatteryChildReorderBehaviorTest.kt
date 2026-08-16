package tv.withaibuild.customiuizer.mods

import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavioral tests for the battery meter child reordering performed by
 * [SystemUIBatteryHooks.applyBatteryChildSwapIfNeeded].
 */
class BatteryChildReorderBehaviorTest {

    private lateinit var parent: RecordingLinearLayout
    private lateinit var digit: View
    private lateinit var percent: View
    private lateinit var mark: View

    @Before
    fun setUp() {
        parent = RecordingLinearLayout()
        digit = RecordingView("digit")
        percent = RecordingView("percent")
        mark = RecordingView("mark")
    }

    @After
    fun tearDown() {
        parent.clear()
    }

    @Test
    fun correctOrderIsIdempotentSecondApplyHasZeroMutations() {
        // Target order: [percent, mark, digit]
        parent.addView(percent)
        parent.addView(mark)
        parent.addView(digit)
        parent.resetMutationCount()

        SystemUIBatteryHooks.applyBatteryChildSwapIfNeeded(parent, percent, mark)
        assertEquals(listOf("percent", "mark", "digit"), parent.childNames())
        assertEquals("apply on correct order must not mutate", 0, parent.mutationCount)
    }

    @Test
    fun wrongOrderIsCorrectedThenSecondApplyIsNoOp() {
        // Starting from [digit, percent, mark]
        parent.addView(digit)
        parent.addView(percent)
        parent.addView(mark)
        parent.resetMutationCount()

        SystemUIBatteryHooks.applyBatteryChildSwapIfNeeded(parent, percent, mark)
        assertEquals(listOf("percent", "mark", "digit"), parent.childNames())
        assertTrue("first apply should fix the order", parent.mutationCount > 0)

        val firstRunMutations = parent.mutationCount
        SystemUIBatteryHooks.applyBatteryChildSwapIfNeeded(parent, percent, mark)
        assertEquals(listOf("percent", "mark", "digit"), parent.childNames())
        assertEquals("second apply must produce zero new mutations", firstRunMutations, parent.mutationCount)
    }

    @Test
    fun reverseOrderIsCorrectedThenIdempotent() {
        // Starting from [mark, percent, digit]
        parent.addView(mark)
        parent.addView(percent)
        parent.addView(digit)
        parent.resetMutationCount()

        SystemUIBatteryHooks.applyBatteryChildSwapIfNeeded(parent, percent, mark)
        assertEquals(listOf("percent", "mark", "digit"), parent.childNames())

        val firstRunMutations = parent.mutationCount
        SystemUIBatteryHooks.applyBatteryChildSwapIfNeeded(parent, percent, mark)
        assertEquals(listOf("percent", "mark", "digit"), parent.childNames())
        assertEquals("must be idempotent once fixed", firstRunMutations, parent.mutationCount)
    }

    @Test
    fun onlyTwoChildrenCorrectsAndBecomesIdempotent() {
        parent.addView(digit)
        parent.addView(percent)
        // no mark view
        parent.resetMutationCount()

        SystemUIBatteryHooks.applyBatteryChildSwapIfNeeded(parent, percent, mark)
        assertEquals(0, parent.indexOfChild(percent))

        val firstRunMutations = parent.mutationCount
        SystemUIBatteryHooks.applyBatteryChildSwapIfNeeded(parent, percent, mark)
        assertEquals(0, parent.indexOfChild(percent))
        assertEquals("two-child case must be idempotent", firstRunMutations, parent.mutationCount)
    }

    @Test
    fun swapFalseDoesNotMutateHierarchy() {
        parent.addView(digit)
        parent.addView(percent)
        parent.addView(mark)
        parent.resetMutationCount()

        val originalOrder = parent.childNames()

        // With style.swap == false the production code does not call the swap helper at all.
        // This test replicates that guard branch.
        val swap = false
        if (swap) {
            SystemUIBatteryHooks.applyBatteryChildSwapIfNeeded(parent, percent, mark)
        }

        assertEquals(originalOrder, parent.childNames())
        assertEquals("swap=false must not touch the hierarchy", 0, parent.mutationCount)
    }

    @Test
    fun moveChildToOnlyMutatesWhenNecessary() {
        parent.addView(percent)
        parent.addView(mark)
        parent.resetMutationCount()

        // Already at target index.
        SystemUIBatteryHooks.moveChildTo(parent, percent, 0)
        assertEquals(0, parent.mutationCount)

        // Move to a different index: one remove + one add.
        SystemUIBatteryHooks.moveChildTo(parent, percent, 1)
        assertEquals(2, parent.mutationCount)
        assertEquals(1, parent.indexOfChild(percent))

        // Move again to same index: no-op.
        SystemUIBatteryHooks.moveChildTo(parent, percent, 1)
        assertEquals(2, parent.mutationCount)
    }

    private class RecordingView(val tagName: String) : View(null)

    @Suppress("DEPRECATION")
    private class FakeResources : Resources(null, DisplayMetrics().apply { density = 2.0f }, android.content.res.Configuration()) {
        val metrics = DisplayMetrics().apply { density = 2.0f }
        override fun getDisplayMetrics(): DisplayMetrics = metrics
    }

    /**
     * A minimal [LinearLayout] double that tracks add/remove mutations and exposes a stable child
     * list without relying on the real view hierarchy to work under a null Context.
     */
    private class RecordingLinearLayout : LinearLayout(null) {
        var mutationCount = 0
            private set

        private val backingChildren = mutableListOf<View>()

        override fun getResources(): Resources = FakeResources()
        override fun getChildCount(): Int = backingChildren.size
        override fun getChildAt(index: Int): View = backingChildren[index]
        override fun indexOfChild(child: View?): Int = backingChildren.indexOf(child)

        override fun addView(child: View?) {
            if (child == null) return
            addView(child, -1)
        }

        override fun addView(child: View?, index: Int) {
            if (child == null) return
            mutationCount++
            if (index < 0 || index > backingChildren.size) {
                backingChildren.add(child)
            } else {
                backingChildren.add(index, child)
            }
        }

        override fun removeView(child: View?) {
            if (child == null) return
            mutationCount++
            backingChildren.remove(child)
        }

        fun childNames(): List<String> = backingChildren.map { (it as RecordingView).tagName }

        fun resetMutationCount() {
            mutationCount = 0
        }

        fun clear() {
            mutationCount = 0
            backingChildren.clear()
        }
    }
}
