package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DynamicIslandStatusBarFadeTest {
    private lateinit var activity: Activity
    private lateinit var root: FrameLayout
    private lateinit var covered: View
    private lateinit var clear: View
    private val islandA = Any()
    private val islandB = Any()
    private val bounds = Rect(110, 0, 290, 50)

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        root = FrameLayout(activity).apply { alpha = .9f }
        val clock = View(activity)
        val battery = View(activity)
        covered = View(activity).apply { alpha = .6f }
        clear = View(activity).apply { alpha = .8f }
        root.addView(clock); root.addView(battery); root.addView(covered); root.addView(clear)
        activity.setContentView(root)
        root.layout(0, 0, 400, 50); clock.layout(12, 0, 70, 50); battery.layout(340, 0, 388, 50)
        covered.layout(120, 0, 145, 50); clear.layout(300, 0, 330, 50)
        val position = IntArray(2)
        root.getLocationOnScreen(position)
        bounds.offset(position[0], position[1])
        DynamicIslandStatusBarFade.ownerRef = null
        DynamicIslandStatusBarFade.bind(root)
        ReflectionHelpers.setField(DynamicIslandStatusBarFade, "compact",
            IslandStatusBarCompact(root, clock, battery, animationsEnabled = { false }))
    }

    @After fun cleanup() {
        DynamicIslandStatusBarFade.ownerRef?.get()?.let { DynamicIslandStatusBarFade.release(it) }
        DynamicIslandStatusBarFade.bind(View(activity))
        DynamicIslandStatusBarFade.contentsRef = null
        activity.finish()
    }

    @Test fun leaseOnlyHidesIntersectingIconsAndNeverChangesTheWholeContentsAlpha() {
        DynamicIslandStatusBarFade.acquire(islandA, bounds)
        assertEquals(0f, covered.alpha); assertEquals(.8f, clear.alpha); assertEquals(.9f, root.alpha)
        DynamicIslandStatusBarFade.release(islandA)
        assertEquals(.6f, covered.alpha); assertEquals(.8f, clear.alpha)
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
    }

    @Test fun staleDetachCannotRestoreASuccessorsCoveredIcon() {
        DynamicIslandStatusBarFade.acquire(islandA, bounds)
        DynamicIslandStatusBarFade.acquire(islandB, bounds)
        DynamicIslandStatusBarFade.release(islandA)
        assertSame(islandB, DynamicIslandStatusBarFade.ownerRef?.get())
        assertEquals(0f, covered.alpha); assertEquals(.8f, clear.alpha)
    }

    @Test fun changedPhysicalBoundsRestoreAnIconAsSoonAsItIsClear() {
        DynamicIslandStatusBarFade.acquire(islandA, bounds)
        DynamicIslandStatusBarFade.acquire(islandA, Rect(bounds.left + 50, bounds.top, bounds.right - 50, bounds.bottom))
        assertEquals(.6f, covered.alpha); assertEquals(.8f, clear.alpha)
    }

    @Test fun missingOrReplacedRomHierarchyNeverFallsBackToHidingAllContents() {
        DynamicIslandStatusBarFade.acquire(islandA, bounds)
        val unknown = View(activity).apply { alpha = .7f }
        DynamicIslandStatusBarFade.bind(unknown)
        assertEquals(.6f, covered.alpha)
        assertEquals(.7f, unknown.alpha)
        DynamicIslandStatusBarFade.acquire(islandB, bounds)
        DynamicIslandStatusBarFade.release(islandB)
        assertEquals(.7f, unknown.alpha)
    }

    @Test fun incomingBoundsAreCopiedAndAnUnboundOwnerCanStillRelease() {
        val mutable = Rect(bounds)
        DynamicIslandStatusBarFade.acquire(islandA, mutable)
        mutable.set(bounds.left + 50, bounds.top, bounds.right - 50, bounds.bottom)
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(0f, covered.alpha)
        DynamicIslandStatusBarFade.bind(View(activity))
        DynamicIslandStatusBarFade.contentsRef = null
        DynamicIslandStatusBarFade.release(islandA)
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
    }
}
