package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.session.PlaybackState
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers.getField
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandPolishTest {
    @Test fun measuredWaveInterpolatesWithoutKeepingAClockAfterSilenceOrDetachment() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val wave=IslandWaveView(activity)
        activity.setContentView(wave,FrameLayout.LayoutParams(25,24))
        wave.onVisibilityAggregated(true)
        wave.spectrum(FloatArray(5))
        wave.playing=true
        assertFalse(getField(wave,"scheduled"))
        wave.spectrum(FloatArray(5) { 1f })
        assertEquals(0f,getField<FloatArray>(wave,"energies")[0],0f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(33))
        assertTrue(getField<FloatArray>(wave,"energies")[0] in .1f.. .99f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals(1f,getField<FloatArray>(wave,"energies")[0],.006f)
        assertFalse(getField(wave,"scheduled"))
        wave.spectrum(FloatArray(5) { .01f })
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        assertEquals(0f,getField<FloatArray>(wave,"energies")[0],.006f)
        assertFalse(getField(wave,"scheduled"))
        wave.spectrum(null)
        assertTrue(getField(wave,"scheduled"))
        activity.setContentView(FrameLayout(activity))
        assertFalse(getField(wave,"scheduled"))
        activity.finish()
    }

    @Test fun smallDragDuringLengtheningPreservesMotionAndFinalContent() = morph(false)
    @Test fun releasingALongPressCannotCreateOrCancelASecondAnimation() = morph(true)

    private fun morph(hold: Boolean) {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell=IslandActivityView(activity)
        activity.setContentView(shell,FrameLayout.LayoutParams(280,40))
        shell.bind("Sender","Public message",null,false,false,false,0,false)
        shell.geometry(184,40,40); layout(shell,184,40)
        shadowOf(Looper.getMainLooper()).idle()
        ShadowChoreographer.setPaused(true)
        try {
            shell.geometry(280,40,40); layout(shell,280,40)
            shell.transition(184,40,280,40,false)
            val motion=getField<ValueAnimator>(shell,"motion")
            val duration = motion.duration
            motion.currentPlayTime=100
            touch(shell,MotionEvent.ACTION_DOWN,140f,20f)
            if (hold) assertFalse(shell.performLongClick())
            else touch(shell,MotionEvent.ACTION_MOVE,140f,30f)
            touch(shell,MotionEvent.ACTION_UP,140f,30f)
            assertSame(motion,getField(shell,"motion"))
            assertEquals(duration,motion.duration)
            motion.end()
            assertEquals(40,shell.height)
            assertEquals(1f,shell.scaleX,.001f)
            for (index in 0..2) assertEquals(1f,shell.getChildAt(index).alpha,.001f)
            val art=(shell.getChildAt(0) as LinearLayout).getChildAt(0)
            assertEquals(1f,art.scaleX,.001f); assertEquals(1f,art.scaleY,.001f)
        } finally { ShadowChoreographer.setPaused(false); activity.finish() }
    }

    @Test fun compactFeedbackKeepsOneVerticalCentreAndContainsNoHiddenControlPanel() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell=IslandActivityView(activity).apply { simplified=true }
        activity.setContentView(shell)
        val art=BitmapDrawable(activity.resources,Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888))
        for (height in 38..44) {
            shell.bind("Saved",null,art,false,false,false,0,false)
            shell.geometry(240,height,40); layout(shell,240,height)
            for (index in 0..1) {
                val group=shell.getChildAt(index) as LinearLayout
                val child=group.getChildAt(0)
                assertEquals(height/2f,group.top+child.top+child.height/2f,.5f)
            }
            assertEquals(3,shell.childCount)
        }
        val height=shell.statusHeight("Enabled",240,40,40)!!
        shell.bind("Enabled","Do Not Disturb",art,false,false,false,0,false,status=true)
        shell.geometry(240,height,40); layout(shell,240,height)
        assertEquals(40,height)
        assertEquals(1,(shell.getChildAt(1) as LinearLayout).childCount)
        assertTrue(shell.contentDescription.toString().contains("Do Not Disturb"))
        assertFalse(shell.performLongClick())
        activity.finish()
    }

    @Test fun swipesAreAlwaysAvailableButUnsupportedTrackActionsRemainBlocked() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val shell=IslandActivityView(activity).apply { simplified=true }
        activity.setContentView(shell)
        shell.bind("Track",null,null,true,true,true,PlaybackState.ACTION_SKIP_TO_NEXT,true)
        shell.geometry(280,40,40); layout(shell,280,40)
        var skips=0;var dismissals=0
        shell.onGesture={skips++};shell.onDismiss={dismissals++}
        fun swipe(x:Float,y:Float) {
            touch(shell,MotionEvent.ACTION_DOWN,140f,20f)
            touch(shell,MotionEvent.ACTION_MOVE,x,y); touch(shell,MotionEvent.ACTION_UP,x,y)
        }
        swipe(30f,20f);assertEquals(1,skips)
        swipe(140f,-100f);assertEquals(1,dismissals)
        swipe(30f,20f);assertEquals(2,skips)
        swipe(140f,-100f);assertEquals(2,dismissals)
        assertFalse(shell.performLongClick())
        shell.bind("Track",null,null,true,true,true,0,true)
        swipe(30f,20f);assertEquals(2,skips)
        activity.finish()
    }

    @Test fun waveformAndArtworkKeepTheirPhysicalAspectRatioDuringWidthChanges() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val shell=IslandActivityView(activity)
        activity.setContentView(shell,FrameLayout.LayoutParams(280,40))
        shell.bind("Track",null,null,true,true,true,0,false)
        shell.geometry(280,40,40);layout(shell,280,40)
        shadowOf(Looper.getMainLooper()).idle();ShadowChoreographer.setPaused(true)
        try {
            shell.transition(184,40,280,40,false)
            val motion=getField<ValueAnimator>(shell,"motion")
            for (time in 0..320 step 8) {
                motion.currentPlayTime=time.toLong()
                val art=(shell.getChildAt(0) as LinearLayout).getChildAt(0)
                val wave=shell.getChildAt(2)
                for (view in listOf(art,wave)) {
                    assertEquals(1f,shell.scaleX*view.scaleX,.001f)
                    assertEquals(1f,shell.scaleY*view.scaleY,.001f)
                }
            }
            motion.end()
        } finally { ShadowChoreographer.setPaused(false);activity.finish() }
    }

    private fun layout(shell:View,width:Int,height:Int) {
        shell.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        shell.layout(0,0,width,height)
    }
    private fun touch(shell:View,action:Int,x:Float,y:Float) {
        val event=MotionEvent.obtain(0,10,action,x,y,0)
        try {shell.dispatchTouchEvent(event)} finally {event.recycle()}
    }
}
