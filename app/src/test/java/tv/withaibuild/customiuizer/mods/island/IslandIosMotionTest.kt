package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.app.Activity
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers.getField

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandIosMotionTest {
    @Test fun pressingClipsBothAxesToTheAvailableBodyEnvelope() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible()
        ShadowChoreographer.setPaused(true)
        try {
            val shell=IslandActivityView(host.get())
            host.get().setContentView(shell);shell.geometry(260,40,40,280);shell.layout(0,0,260,40)
            shell.transition(260,40,260,40,false);getField<ValueAnimator>(shell,"motion").end()
            val event=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,130f,20f,0)
            try { shell.dispatchTouchEvent(event) } finally { event.recycle() }
            getField<ValueAnimator>(shell,"motion").end()
            assertEquals("Width clipping must not leave a disproportionate vertical balloon",shell.scaleX,shell.scaleY,.001f)
            assertTrue("Stay inside the existing 14% body envelope",shell.scaleY<=1.14001f)
        } finally { ShadowChoreographer.setPaused(false);host.pause().stop().destroy() }
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun systemFeedbackShowsTheActualModeAndKeepsItWhenTheSecondaryRowCannotFit() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            val shell = IslandActivityView(host.get())
            host.get().setContentView(shell); shell.geometry(260, 40, 40)
            shell.bind("Enabled", "Silent", null, false, false, false, 0L, false, status = true)
            val title = getField<android.widget.Chronometer>(shell, "title")
            val subtitle = getField<android.widget.TextView>(shell, "subtitle")
            assertEquals("Silent", title.text.toString())
            assertEquals("Enabled", subtitle.text.toString())
            assertEquals(View.VISIBLE, subtitle.visibility)
            shell.geometry(260, 24, 40)
            assertEquals(View.GONE, subtitle.visibility)
            assertEquals("The mode must survive a short user-defined shell", "Silent", title.text.toString())
            assertTrue(shell.contentDescription.contains("Silent"))
        } finally { host.pause().stop().destroy() }
    }

    @Test fun compactReplacementStartsImmediatelyWithTheSubcomponentProfile() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        ShadowChoreographer.setPaused(true)
        try {
            val shell = IslandActivityView(host.get())
            host.get().setContentView(shell); shell.geometry(260, 40, 40); shell.layout(0, 0, 260, 40)
            shell.bind("A", null, null, false, false, false, 0L, false)
            shell.transition(260, 40, 260, 40, false); getField<ValueAnimator>(shell, "motion").end()
            shell.bind("B", null, null, false, false, false, 0L, false)
            shell.transition(260, 40, 260, 40, false)
            assertNull(getField<Any?>(shell, "pendingContent"))
            assertEquals("B", getField<android.widget.Chronometer>(shell, "title").text.toString())
            val expected = IslandMotion.Spring().apply { retarget(0f, 1f, IslandMotion.IOS_SLOTS, false) }
            val clock = getField<ValueAnimator>(shell, "motion")
            clock.currentPlayTime = 80
            assertEquals(expected.sample(.08f), getField<Float>(shell, "layerReveal"), .001f)
            shell.powerSaveMotion = true
            assertNull(getField<ValueAnimator?>(shell, "motion"))
            assertEquals(1f, getField<Float>(shell, "layerReveal"), 0f)
        } finally { ShadowChoreographer.setPaused(false); host.pause().stop().destroy() }
    }

    @Test fun ordinaryTapPressesTheBoundedBodyAndOpensImmediatelyWhileReleaseKeepsTheVisibleVelocity() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible()
        ShadowChoreographer.setPaused(true)
        try {
            val shell=IslandActivityView(host.get())
            host.get().setContentView(shell); shell.geometry(260,40,40,280); shell.layout(0,0,260,40)
            shell.transition(260,40,260,40,false); getField<ValueAnimator>(shell,"motion").end()
            var opened=0; shell.onOpen={ opened++ }
            fun touch(action: Int) {
                val now=android.os.SystemClock.uptimeMillis()
                val e=android.view.MotionEvent.obtain(now,now,action,130f,20f,0)
                try { shell.dispatchTouchEvent(e) } finally { e.recycle() }
            }
            touch(android.view.MotionEvent.ACTION_DOWN)
            val press=getField<ValueAnimator>(shell,"motion"); press.currentPlayTime=120
            assertTrue(shell.scaleX>1f); assertTrue(shell.scaleY>1f)
            assertTrue(shell.scaleX*260<=280.001f)
            val visible=shell.scaleX
            touch(android.view.MotionEvent.ACTION_UP)
            assertEquals("Launching must not wait for feedback completion",1,opened)
            val release=getField<ValueAnimator>(shell,"motion")
            assertNotSame(press,release); assertEquals(visible,shell.scaleX,.001f)
            release.end(); assertEquals(1f,shell.scaleX,0f); assertEquals(1f,shell.scaleY,0f)
            touch(android.view.MotionEvent.ACTION_DOWN)
            getField<ValueAnimator>(shell,"motion").end()
            touch(android.view.MotionEvent.ACTION_CANCEL)
            getField<ValueAnimator>(shell,"motion").end()
            assertEquals(1,opened); assertEquals(1f,shell.scaleX,0f)
        } finally { ShadowChoreographer.setPaused(false); host.pause().stop().destroy() }
    }
    @Test fun pressingDoesNotStealAnArrivalOrLayoutOwner() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible()
        ShadowChoreographer.setPaused(true)
        try {
            val shell=IslandActivityView(host.get())
            host.get().setContentView(shell); shell.geometry(280,40,40,340); shell.layout(0,0,280,40)
            shell.transition(180,40,280,40,false)
            val arrival=getField<ValueAnimator>(shell,"motion")
            val e=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,140f,20f,0)
            try { shell.dispatchTouchEvent(e) } finally { e.recycle() }
            assertSame(arrival,getField<ValueAnimator>(shell,"motion"))
            arrival.end()
        } finally { ShadowChoreographer.setPaused(false); host.pause().stop().destroy() }
    }
    @Test fun activityTextRevealsThroughItsAvailableLaneWithoutAWidthGateOrFontScalePulse() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            val shell = IslandActivityView(host)
            host.setContentView(shell); shell.geometry(184,40,40,340)
            shell.bind("Old",null,null,false,false,false,0L,false)
            shell.transition(184,40,184,40,false); getField<ValueAnimator>(shell,"motion").end()
            shell.bind("Long destination label",null,null,false,false,false,0L,false)
            shell.geometry(320,40,40,340)
            shell.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(40,View.MeasureSpec.EXACTLY))
            shell.layout(0,0,320,40); shell.prepareContentArrivalFromCurrent(); shell.transition(184,40,320,40,false)
            val clock=getField<ValueAnimator>(shell,"motion"); clock.currentPlayTime=140L
            val text=getField<View>(shell,"text")
            assertTrue("New text is already fading in while its lane is still opening",text.alpha > .01f)
            assertEquals("The reveal stays pinned to the outer caption edge",text.width,text.clipBounds!!.right)
            assertTrue("Only the currently available lane is visible",text.clipBounds!!.width() in 1 until text.width)
            assertEquals("Typography keeps its physical size",1f,text.scaleX*shell.scaleX,.001f)
            clock.end(); assertEquals(text.width,text.clipBounds!!.width()); assertEquals(1f,text.alpha,.001f)
        } finally { host.finish(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun ringerBodyMovesAndWidensOnOneClockAndInterruptionKeepsItsVisiblePosition() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            val shell = IslandActivityView(host)
            host.setContentView(shell); shell.geometry(260, 40, 40, 340); shell.layout(0, 0, 260, 40)
            shell.bind("Ringing", null, null, false, false, false, 0L, false)
            shell.transition(260, 40, 260, 40, false, ringerShake = true)
            val motion = getField<ValueAnimator>(shell, "motion")
            motion.currentPlayTime = 65L
            assertTrue(shell.translationX < -1f)
            assertTrue(shell.scaleX > 1f)
            assertEquals(0f, shell.rotation, 0f)
            val width = shell.scaleX; val x = shell.translationX
            shell.bind("Next activity", null, null, false, false, false, 0L, false)
            shell.transition(260, 40, 260, 40, false)
            val next = getField<ValueAnimator>(shell, "motion")
            next.currentPlayTime = 0L
            assertEquals(width, shell.scaleX, .001f); assertEquals(x, shell.translationX, .001f)
            next.end(); assertEquals(1f, shell.scaleX, 0f); assertEquals(0f, shell.translationX, 0f)
        } finally { host.finish(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun ringerResponseIsFiniteAndMirrorsDirectionWithoutDependingOnRefreshRate() {
        val ltr = IslandRingerResponse(1f, false); val rtl = IslandRingerResponse(1f, true)
        for (ms in 0L..ltr.durationMillis step 7L) {
            ltr.sample(ms / 1000f); rtl.sample(ms / 1000f)
            assertEquals(ltr.width, rtl.width, .0001f); assertEquals(ltr.x, -rtl.x, .0001f)
            assertTrue(ltr.width in 0f..22.1f); assertTrue(kotlin.math.abs(ltr.x) < 20f)
        }
        ltr.sample(ltr.durationMillis / 1000f)
        assertEquals(0f, ltr.x, 0f); assertEquals(0f, ltr.width, 0f)
    }
    @Test fun enablingPowerSavingEndsAnActiveBodyResponseWithoutLeavingAnOffset() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            val shell = IslandActivityView(host)
            host.setContentView(shell); shell.geometry(240, 40, 40, 300); shell.layout(0, 0, 240, 40)
            shell.transition(240, 40, 240, 40, false, ringerShake = true)
            getField<ValueAnimator>(shell, "motion").currentPlayTime = 80L
            shell.powerSaveMotion = true
            assertNull(getField<ValueAnimator?>(shell, "motion"))
            assertEquals(0f, shell.translationX, 0f); assertEquals(1f, shell.scaleX, 0f)
        } finally { host.finish(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun newActivityAppearsWithoutWaitingForOldContentSpringToSettle() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            val shell = IslandActivityView(host)
            host.setContentView(shell); shell.geometry(260, 40, 40); shell.layout(0, 0, 260, 40)
            shell.bind("Activity A", null, null, false, false, false, 0L, false)
            shell.transition(260, 40, 260, 40, true)
            getField<ValueAnimator>(shell, "motion").end()
            shell.bind("Activity B", null, null, false, false, false, 0L, false)
            shell.transition(260, 40, 260, 40, false)
            val clock = getField<ValueAnimator>(shell, "motion")
            clock.currentPlayTime = 80L
            assertNull("Old content must release within the short handoff", getField<Any?>(shell, "pendingContent"))
            assertEquals("Activity B", getField<String>(getField<Any>(shell, "displayedContent"), "label"))
            clock.currentPlayTime = 240L
            assertTrue("New content is readable while the outer spring is still moving", getField<Float>(shell, "layerReveal") > .6f)
            shell.bind("Activity C", null, null, false, false, false, 0L, false)
            shell.transition(260, 40, 260, 40, false)
            val redirected = getField<ValueAnimator>(shell, "motion")
            redirected.currentPlayTime = 80L
            assertEquals("Activity C", getField<String>(getField<Any>(shell, "displayedContent"), "label"))
            redirected.end()
        } finally { host.finish(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun nativePeriodIsIndependentOfLegacyDurationAndPreservesVelocity() {
        val spring = IslandMotion.Spring()
        spring.retarget(0f, 400f, IslandMotion.IOS_BOUNDS, false)
        assertTrue(spring.sample(.2f) in 240f..350f)
        val position = spring.value; val velocity = spring.velocity
        spring.retarget(position, 100f, IslandMotion.IOS_COLLAPSE, true)
        assertEquals(position, spring.sample(0f), .001f)
        assertEquals(velocity, spring.velocity, .001f)
        assertTrue(spring.sample(.001f) > position)
    }
    @Test fun profileDeadlineSettlesPhysicalPositionAtDifferentRefreshRates() {
        for (profile in listOf(IslandMotion.IOS_BOUNDS, IslandMotion.IOS_IDLE, IslandMotion.IOS_COLLAPSE)) {
            val a = IslandMotion.Spring(); val b = IslandMotion.Spring()
            a.retarget(0f, 400f, profile, false); b.retarget(0f, 400f, profile, false)
            val deadline = profile.horizon(-400f, 0f)
            assertTrue(deadline in 600L..1400L)
            for (frame in 0..(deadline * 60 / 1000).toInt()) {
                val time = frame / 60f
                b.sample(maxOf(0f, time - 1f / 120f))
                assertEquals(a.sample(time), b.sample(time), .001f)
            }
            assertEquals(400f, a.sample(deadline / 1000f), .25f)
            assertTrue(kotlin.math.abs(a.velocity) <= 2.1f)
        }
    }
    @Test fun experimentalEntryHasOneFiniteClockAndNoLocalCatchPulse() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            val shell = IslandActivityView(host).apply { entranceStyle = IslandEntranceStyle.CENTER }
            host.setContentView(shell)
            shell.geometry(260, 40, 40); shell.cameraAnchor(20f, 16f)
            shell.measure(View.MeasureSpec.makeMeasureSpec(260, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
            shell.layout(0, 0, 260, 40)
            shell.prepareEntry(); shell.transition(260, 40, 260, 40, true)
            val clock = getField<ValueAnimator>(shell, "motion")
            assertTrue(clock.duration > 600L)
            clock.currentPlayTime = 200L
            assertEquals(0f, getField<Float>(shell, "breadthExtraVelocity"), 0f)
            assertTrue(shell.width * shell.scaleX < 250f)
            clock.end()
            assertNull(getField<ValueAnimator?>(shell, "motion"))
            assertEquals(1f, shell.scaleX, 0f)
            assertEquals(1f, shell.alpha, 0f)
        } finally { host.finish(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun iosLongIslandRespectsTheSelectedVerticalEntryAndExit() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        ShadowChoreographer.setPaused(true)
        try {
            for (ringerShake in listOf(false, true)) {
                val shell = IslandActivityView(host.get()).apply { entranceStyle = IslandEntranceStyle.TOP; windowTop = 12 }
                host.get().setContentView(shell); shell.geometry(320, 40, 40, 380); shell.cameraAnchor(20f, 16f)
                shell.bind("A message", "Synthetic sender", null, false, false, false, 0L, false, expanded = true)
                shell.layout(0, 0, 320, 40)
                if (ringerShake) {
                    shell.prepareContentArrivalFromCurrent()
                    shell.transition(320, 40, 320, 40, false, ringerShake = true)
                    getField<ValueAnimator>(shell, "motion").currentPlayTime = 80
                    assertTrue("Exercise a real interrupted horizontal receive", shell.scaleX > 1f)
                }
                shell.prepareEntry()
                assertTrue("A new long island starts above its window", shell.translationY < -40f)
                assertEquals("Vertical entry retains the selected long width", 1f, shell.scaleX, 0f)
                shell.transition(320, 40, 320, 40, true, ringerShake = ringerShake)
                val entry = getField<ValueAnimator>(shell, "motion")
                entry.currentPlayTime = 80
                assertTrue(shell.translationY < 0f)
                for (time in 0L until entry.duration step 16L) {
                    entry.currentPlayTime = time
                    assertEquals("Vertical entry cannot add a horizontal receive", 1f, shell.scaleX, .0001f)
                    assertEquals("Vertical entry cannot add a sideways ringer shake", 0f, shell.translationX, .0001f)
                }
                entry.end(); assertEquals(0f, shell.translationY, 0f)
                var ended = false
                shell.exit { ended = true }
                val exit = getField<ValueAnimator>(shell, "motion")
                assertEquals(IslandMotion.EXIT, exit.duration)
                exit.currentPlayTime = 80
                assertTrue(shell.translationY < 0f)
                assertEquals(1f, shell.scaleX, 0f)
                exit.end(); assertTrue(ended)
            }
        } finally { ShadowChoreographer.setPaused(false); host.pause().stop().destroy() }
    }
    @Test fun centerMusicExitKeepsItsOriginalDeadlineWithoutTranslucentTail() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            val shell = IslandActivityView(host).apply { entranceStyle = IslandEntranceStyle.CENTER }
            host.setContentView(shell)
            shell.geometry(260, 40, 40); shell.cameraAnchor(20f, 16f)
            shell.layout(0, 0, 260, 40)
            var ended = 0
            shell.exit { ended++ }
            val clock = getField<ValueAnimator>(shell, "motion")
            assertEquals(IslandMotion.EXIT, clock.duration)
            clock.currentPlayTime = IslandMotion.EXIT
            clock.end()
            assertEquals(0f, shell.alpha, 0f)
            assertNull(getField<ValueAnimator?>(shell, "motion"))
            assertEquals(1, ended)
        } finally { host.finish(); ShadowChoreographer.setPaused(false) }
    }
}
