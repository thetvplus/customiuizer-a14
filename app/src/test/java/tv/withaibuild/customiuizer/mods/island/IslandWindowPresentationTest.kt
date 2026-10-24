package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandWindowPresentationTest {
    private lateinit var activity: Activity
    private lateinit var root: FrameLayout
    private lateinit var pill: View
    private lateinit var lease: IslandWindowPresentation
    private var allowed = true
    private var shows = 0
    private var hides = 0

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        root = FrameLayout(activity)
        pill = View(activity)
        root.addView(pill, FrameLayout.LayoutParams(184, 40))
        activity.setContentView(root)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        layout()
        // Robolectric leaves AttachInfo at GONE for this synthetic Activity window.
        // Model a real foreground window explicitly; production still checks its visibility.
        val attach = org.robolectric.util.ReflectionHelpers.getField<Any>(pill, "mAttachInfo")
        org.robolectric.util.ReflectionHelpers.setField(attach, "mWindowVisibility", View.VISIBLE)
        val rectangle = android.graphics.Rect()
        assertTrue("attached=${pill.isAttachedToWindow} window=${pill.windowVisibility} shown=${pill.isShown} " +
            "laid=${pill.isLaidOut} requested=${pill.isLayoutRequested} size=${pill.width}x${pill.height} " +
            "global=${pill.getGlobalVisibleRect(rectangle)} $rectangle", pill.isAttachedToWindow &&
                pill.isShown && pill.isLaidOut && !pill.isLayoutRequested && pill.windowVisibility == View.VISIBLE)
        lease = IslandWindowPresentation(root, { pill }, { allowed }, { shows++ }, { hides++ })
    }

    @After fun cleanup() {
        if (::lease.isInitialized) lease.cancel()
        DynamicIslandStatusBarFade.ownerRef = null
        activity.finish()
    }

    private fun layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 400, 100)
    }

    @Test fun shellStateChangingAfterPrepareVetoesTheFirstFrameWithoutShowingOrReservingSpace() {
        lease.cancel()
        var canDraw = true
        var rejected = 0
        lease = IslandWindowPresentation(root, { pill }, { allowed }, { shows++ }, { hides++ },
            frameAllowed = { canDraw }, rejected = { rejected++ })
        val event = Any()
        lease.prepare(event, 184)
        canDraw = false // Shell published PiP after render but before this traversal.
        assertFalse(lease.onPreDraw())
        assertEquals(1, rejected)
        assertEquals(0, shows)
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        assertEquals(IslandWindowPresentation.Phase.PREPARED, lease.phase)
        lease.cancel()
        canDraw = true
        assertTrue(lease.onPreDraw())
        assertEquals("A rejected old identity must not reappear", 0, shows)
    }

    @Test fun aVisibleFrameCanBeVetoedBeforeItsControlledExitWithoutReacquiringAnOldLease() {
        lease.cancel()
        var canDraw = true
        lease = IslandWindowPresentation(root, { pill }, { allowed }, { shows++ }, { hides++ },
            frameAllowed = { canDraw })
        lease.prepare(Any(), 184); assertTrue(lease.onPreDraw())
        assertEquals(1, shows)
        canDraw = false
        assertFalse(lease.onPreDraw())
        assertEquals(1, hides)
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        assertEquals(IslandWindowPresentation.Phase.VISIBLE, lease.phase)
        lease.exiting()
        assertEquals(IslandWindowPresentation.Phase.EXITING, lease.phase)
        lease.cancel()
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
    }

    @Test fun preparationAndAnAttachedButTransparentWindowCannotReserveTheStatusBar() {
        val event = Any()
        pill.alpha = 0f
        lease.prepare(event, 184)
        assertEquals(IslandWindowPresentation.Phase.PREPARED, lease.phase)
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        lease.onPreDraw()
        assertEquals(0, shows)
        pill.alpha = 1f
        lease.onPreDraw()
        assertSame(event, DynamicIslandStatusBarFade.ownerRef?.get())
        assertEquals(1, shows)
        assertEquals(IslandWindowPresentation.Phase.VISIBLE, lease.phase)
    }

    @Test fun shadeLockInvalidLayoutAndDetachAllReleaseTheMatchingVisibleEvent() {
        val event = Any()
        lease.prepare(event, 184)
        lease.onPreDraw()
        assertEquals(1, shows)
        allowed = false
        lease.refresh()
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        assertEquals(1, hides)
        allowed = true
        pill.visibility = View.GONE
        lease.onPreDraw()
        assertEquals(1, shows)
        pill.visibility = View.VISIBLE
        layout()
        lease.onPreDraw()
        assertEquals(2, shows)
        activity.setContentView(View(activity))
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        assertEquals(2, hides)
        assertEquals(IslandWindowPresentation.Phase.IDLE, lease.phase)
    }

    @Test fun anOldWindowCannotReleaseItsSuccessorAndPreparedNativeFeedbackIsNotAcknowledged() {
        val old = Any()
        lease.prepare(old, 184)
        lease.onPreDraw()
        val newer = IslandWindowPresentation(root, { pill }, { allowed })
        val event = Any()
        newer.prepare(event, 240)
        assertSame(old, DynamicIslandStatusBarFade.ownerRef?.get())
        newer.onPreDraw()
        lease.cancel()
        assertSame(event, DynamicIslandStatusBarFade.ownerRef?.get())
        newer.cancel()
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
    }

    @Test fun aCompleteNativeFallbackCanPreemptWithoutAcquiringModuleStatusBarSpace() {
        lease.prepare(Any(), 0)
        lease.onPreDraw()
        assertEquals(1, shows)
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        lease.exiting()
        assertEquals(IslandWindowPresentation.Phase.EXITING, lease.phase)
        lease.cancel()
        assertEquals(1, hides)
    }

    @Test fun resumingTheSameIdentityAfterExitRequiresANewVisibleFrame() {
        val event=Any()
        lease.prepare(event,184); lease.onPreDraw()
        assertEquals(1,shows)
        lease.exiting()
        lease.prepare(event,184)
        assertEquals(IslandWindowPresentation.Phase.PREPARED,lease.phase)
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        lease.onPreDraw()
        assertEquals(IslandWindowPresentation.Phase.VISIBLE,lease.phase)
        assertEquals(2,shows); assertEquals(1,hides)
    }

    @Test fun visibleLeaseUsesPhysicalScreenCoordinatesAcrossWindowOffsets() {
        val attach = org.robolectric.util.ReflectionHelpers.getField<Any>(pill, "mAttachInfo")
        org.robolectric.util.ReflectionHelpers.setField(attach, "mWindowLeft", 37)
        org.robolectric.util.ReflectionHelpers.setField(attach, "mWindowTop", 61)
        val location = IntArray(2)
        pill.getLocationOnScreen(location)
        lease.prepare(Any(), 184)
        lease.onPreDraw()
        val physical = org.robolectric.util.ReflectionHelpers.getField<android.graphics.Rect>(
            DynamicIslandStatusBarFade, "islandBounds")
        assertEquals(location[0], physical.left); assertEquals(location[1], physical.top)
        assertEquals(pill.width, physical.width()); assertEquals(pill.height, physical.height())
    }

    @Test fun aWindowRootUsesItsOwnMatrixInsteadOfItsUntransformedGlobalVisibleRect() {
        val direct = object : View(activity) {
            override fun isAttachedToWindow() = true
            override fun isShown() = true
            override fun getWindowVisibility() = View.VISIBLE
            override fun getGlobalVisibleRect(rect: android.graphics.Rect, offset: android.graphics.Point?): Boolean {
                // This is ViewRootImpl's behavior for the addView(shell) production root.
                rect.set(0, 0, width, height)
                return true
            }
            override fun getLocationOnScreen(location: IntArray) {
                val origin = floatArrayOf(0f, 0f)
                matrix.mapPoints(origin)
                location[0] = kotlin.math.round(273 + origin[0]).toInt()
                location[1] = kotlin.math.round(25 + origin[1]).toInt()
            }
        }
        direct.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
        direct.layout(0, 0, 240, 40)
        direct.scaleX = .25f; direct.translationX = 12f
        val rootLease = IslandWindowPresentation(direct, eligible = { true })
        try {
            rootLease.prepare(Any(), 240); rootLease.onPreDraw()
            val physical = org.robolectric.util.ReflectionHelpers.getField<android.graphics.Rect>(
                DynamicIslandStatusBarFade, "islandBounds")
            assertEquals(android.graphics.Rect(375, 25, 435, 65), physical)
            // A shrinking island can still draw beyond its new logical window via outsets.
            direct.scaleX = 1.25f; direct.translationY = -8f
            rootLease.onPreDraw()
            assertEquals(android.graphics.Rect(255, 17, 555, 57), physical)
        } finally { rootLease.cancel() }
    }

}
