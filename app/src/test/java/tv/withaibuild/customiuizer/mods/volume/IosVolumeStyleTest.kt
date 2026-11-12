package tv.withaibuild.customiuizer.mods.volume

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
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
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosVolumeStyleTest {
    private fun attachColumn(dialog: FrameLayout): IosVolumeStyle.Binding {
        val context = dialog.context
        val column = FrameLayout(context)
        val slider = SeekBar(context)
        val fill = View(context)
        val track = FrameLayout(context)
        val icon = ImageView(context)
        // Keep the fixture out of the parent's material and footer assertions.
        track.addView(fill)
        for (view in listOf(slider, track, icon)) column.addView(view, FrameLayout.LayoutParams(58, 170))
        dialog.addView(column, 0, FrameLayout.LayoutParams(58, 170))
        return IosVolumeStyle.Binding(column, slider, fill, track, icon, startCompact = true)
    }

    @Test fun queuedOldKeyUpCannotReleaseANewerHardwarePress() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            val queued=ArrayList<Runnable>()
            val root=object: FrameLayout(host) {
                override fun post(action: Runnable): Boolean { queued.add(action);return true }
            }
            val slider=SeekBar(host);val fill=View(host);val track=FrameLayout(host);val icon=ImageView(host)
            for(v in listOf(slider,fill,track,icon)) root.addView(v,FrameLayout.LayoutParams(58,170))
            host.setContentView(root)
            val binding=IosVolumeStyle.Binding(root,slider,fill,track,icon,startCompact=true)
            val session=IosVolumeStyle.Session(root,root,null,binding,{0},{},{})
            val input=IosVolumeStyle.NativeInput().apply { this.session=java.lang.ref.WeakReference(session) }
            try {
                val paint=getField<IosVolumeStyle.Track>(binding,"paint")
                binding.nativeVolume(15,0,15)
                input.record(0x1011);session.noteInput()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
                input.record(0x1010)
                val stale=queued.last()
                input.record(0x1011) // worker has seen DOWN, Main show is still pending
                stale.run()
                assertEquals(1.06f,getField<Float>(paint,"pressTarget"),0f)
                session.noteInput();stale.run()
                assertTrue(getField<Boolean>(binding,"hardwarePending"))
                assertEquals(1.06f,getField<Float>(paint,"pressTarget"),0f)
                input.record(0x1010);queued.last().run()
                assertEquals(1f,getField<Float>(paint,"pressTarget"),0f)
            } finally { session.close() }
        } finally { host.finish();ShadowChoreographer.setPaused(false) }
    }

    @Test fun sustainedEndpointEventuallyStopsRetargetingWithinAPhysicalPixelBudget() {
        ShadowChoreographer.setPaused(true)
        try {
            for(density in listOf(1f,2.75f,3.5f)) for(direction in listOf(1f,-1f)) {
                val track=IosVolumeStyle.Track(density,true) { _,_,_,_ -> }
                try {
                    repeat(4000) { track.endpointKick(direction);getField<ValueAnimator?>(track,"clock")?.end() }
                    val target=getField<Float>(track,"buttonTarget")
                    repeat(400) { track.endpointKick(direction) }
                    assertNull(getField<ValueAnimator?>(track,"clock"))
                    assertEquals(target,getField<Float>(track,"buttonTarget"),0f)
                    track.endpointKick(-direction)
                    assertNotNull(getField<ValueAnimator?>(track,"clock"))
                    track.releaseEndpoint();getField<ValueAnimator>(track,"clock").end()
                    assertEquals(0f,getField<Float>(track,"buttonValue"),0f)
                } finally { track.stop() }
            }
        } finally { ShadowChoreographer.setPaused(false) }
    }

    @Test fun arrivingAtTheBoundaryDoesNotInventAnUnclampedStep() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            val root=FrameLayout(host);val slider=SeekBar(host);val fill=View(host)
            val track=FrameLayout(host);val icon=ImageView(host)
            for(v in listOf(slider,fill,track,icon)) root.addView(v,FrameLayout.LayoutParams(58,170))
            host.setContentView(root)
            val binding=IosVolumeStyle.Binding(root,slider,fill,track,icon,startCompact=true)
            try {
                val paint=getField<IosVolumeStyle.Track>(binding,"paint")
                binding.nativeVolume(14,0,15,initial=true)
                binding.noteInput();binding.nativeVolume(15,0,15)
                getField<ValueAnimator>(paint,"clock").end()
                assertEquals(0f,getField<Float>(paint,"buttonValue"),0f)
                assertEquals(1.06f,getField<Float>(paint,"pressScale"),0f)
                binding.noteInput()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
                getField<ValueAnimator>(paint,"clock").end()
                assertTrue(getField<Float>(paint,"buttonValue")>0f)
            } finally { binding.close() }
        } finally { host.finish();ShadowChoreographer.setPaused(false) }
    }

    @Test fun touchTakesOverWithoutDroppingTheRetiringButtonDisplacement() {
        ShadowChoreographer.setPaused(true)
        var offset=0f
        val track=IosVolumeStyle.Track(1f,true) { _,_,_,y -> offset=y }
        try {
            track.endpointKick(1f)
            val clock=getField<ValueAnimator>(track,"clock")
            clock.currentPlayTime=160L
            val held=offset
            assertTrue(held<0f)
            track.releaseEndpoint();track.beginTouch()
            track.overdrag(0f,16)
            assertEquals(held,offset,.00001f)
            assertSame(clock,getField<ValueAnimator>(track,"clock"))
            track.overdrag(3f,16)
            assertTrue("Touch contributes immediately while the old response retires",offset<held)
            clock.end()
            assertEquals(0f,getField<Float>(track,"buttonValue"),0f)
            assertEquals(3f,getField<Float>(track,"stretch"),0f)
            track.dragging=false;track.form(7f,134f,.408,1.0)
            getField<ValueAnimator>(track,"clock").end()
            assertEquals(0f,offset,0f)
        } finally { track.stop();ShadowChoreographer.setPaused(false) }
    }

    @Test fun repeatedBoundaryInputAdvancesTheNativeUnclampedValueAndRubberBand() {
        ShadowChoreographer.setPaused(true)
        var width=0f;var height=0f;var offset=0f
        val track=IosVolumeStyle.Track(1f,true) { w,h,_,y -> width=w;height=h;offset=y }
        try {
            track.endpointKick(1f);getField<ValueAnimator>(track,"clock").end()
            val first=height
            track.endpointKick(1f);getField<ValueAnimator?>(track,"clock")?.end()
            assertTrue("The native repeat advances an unclamped value; it is not an idempotent scale pulse",height>first)
            assertEquals(6.2439067f,width,.001f)
            assertEquals(147.19129f,height,.001f)
            assertEquals(-5.433551f,offset,.001f)
        } finally { track.stop();ShadowChoreographer.setPaused(false) }
    }

    @Test fun aClosedSessionCannotQueueAnotherHardwareRelease() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            var posts=0
            val root=object: FrameLayout(host) {
                override fun post(action: Runnable): Boolean { posts++;return super.post(action) }
            }
            host.setContentView(root)
            var ended: IosVolumeStyle.Session?=null
            val session=IosVolumeStyle.Session(root,root,null,attachColumn(root),{0},{},{ended=it})
            session.close();assertSame(session,ended)
            val before=posts
            session.releaseHardwareAsync(IosVolumeStyle.NativeInput(),0L)
            assertEquals(before,posts)
        } finally { host.finish() }
    }

    @Test fun onlyFreshNativeHardwareShowsCanPressTheTrack() {
        val input=IosVolumeStyle.NativeInput()
        input.record(1);assertFalse(input.isKey())
        input.record(0x1011);assertTrue(input.isKey())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(301))
        assertFalse(input.isKey())
        input.record(0x5011);assertTrue(input.isKey())
        input.record(0x1014);assertFalse(input.isKey())
    }

    @Test fun nativeKeyUpReleasesBeforeTheQuietFallbackAndProgrammaticShowsCannotPress() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            val root=FrameLayout(host);val slider=SeekBar(host);val fill=View(host)
            val track=FrameLayout(host);val icon=ImageView(host)
            for(v in listOf(slider,fill,track,icon)) root.addView(v,FrameLayout.LayoutParams(58,170))
            host.setContentView(root)
            val binding=IosVolumeStyle.Binding(root,slider,fill,track,icon,startCompact=true)
            val session=IosVolumeStyle.Session(root,root,null,binding,{0},{},{})
            val input=IosVolumeStyle.NativeInput()
            input.session=java.lang.ref.WeakReference(session)
            try {
                val paint=getField<IosVolumeStyle.Track>(binding,"paint")
                binding.nativeVolume(15,0,15)
                input.record(1);session.noteInput(input.isKey())
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
                assertEquals(1f,getField<Float>(paint,"pressTarget"),0f)
                input.record(0x1011);session.noteInput(input.isKey())
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
                getField<ValueAnimator>(paint,"clock").end()
                assertEquals(1.06f,getField<Float>(paint,"pressScale"),0f)
                // AudioService removes PLAY_SOUND for a media stream.
                input.record(0x1010)
                assertSame(session,input.session.get())
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
                assertEquals(1f,getField<Float>(paint,"pressTarget"),0f)
                assertFalse(getField<Boolean>(binding,"hardwarePending"))
                assertNotNull(getField<ValueAnimator?>(paint,"clock"))
            } finally { session.close() }
        } finally { host.finish();ShadowChoreographer.setPaused(false) }
    }

    @Test fun buttonBoundaryHoldsTheNativeDirectionalScaleUntilRelease() {
        ShadowChoreographer.setPaused(true)
        var width=0f; var height=0f
        val track=IosVolumeStyle.Track(1f,true) { w,h,_,_ -> width=w; height=h }
        try {
            for ((direction,scale) in listOf(1f to 1.06f,-1f to .92f)) {
                track.endpointKick(direction)
                getField<ValueAnimator>(track,"clock").end()
                assertEquals(scale,getField<Float>(track,"pressScale"),.001f)
                assertTrue(height>134f*scale);assertTrue(width<7f*scale)
                val heldWidth=width;val heldHeight=height
                repeat(120) { track.form(7f,134f,.408,1.0) }
                assertNull("Settled form notifications must not replay a held response",getField<ValueAnimator?>(track,"clock"))
                assertEquals(heldHeight,height,.001f)
                assertEquals(heldWidth,width,.001f)
                track.releaseEndpoint()
                getField<ValueAnimator>(track,"clock").end()
                assertEquals(134f,height,.001f)
                assertEquals(7f,width,.001f)
            }
        } finally { track.stop(); ShadowChoreographer.setPaused(false) }
    }

    @Test fun buttonReleaseRetargetsPresentationAndVelocityWithoutASecondClock() {
        ShadowChoreographer.setPaused(true)
        val track=IosVolumeStyle.Track(1f,true) { _,_,_,_ -> }
        try {
            track.endpointKick(1f)
            val clock=getField<ValueAnimator>(track,"clock")
            clock.currentPlayTime=160L
            val scale=getField<Float>(track,"pressScale")
            val velocity=getField<Double>(track,"pressVelocity")
            // Analytic response .8 / .9, not the .408 shape or .4 value spring.
            assertEquals(1.0226494f,scale,.00001f)
            assertTrue(velocity>0.0)
            track.releaseEndpoint()
            assertSame(clock,getField<ValueAnimator>(track,"clock"))
            assertEquals(scale,getField<Float>(track,"pressScale"),0f)
            assertEquals(velocity,getField<Double>(track,"pressVelocity"),0.0)
            clock.currentPlayTime=240L
            track.endpointKick(-1f)
            assertSame(clock,getField<ValueAnimator>(track,"clock"))
            clock.end()
            assertEquals(.92f,getField<Float>(track,"pressScale"),0f)
            track.stop()
            assertNull(getField<ValueAnimator?>(track,"clock"))
            assertEquals(1f,getField<Float>(track,"pressScale"),0f)
        } finally { track.stop(); ShadowChoreographer.setPaused(false) }
    }

    @Test fun disablingMotionDiscardsVelocityBeforeTheNextForm() {
        ShadowChoreographer.setPaused(true)
        val scale=ValueAnimator.getDurationScale()
        val track=IosVolumeStyle.Track(1f) { _,_,_,_ -> }
        try {
            track.form(47f,148f,.8,.9)
            getField<ValueAnimator>(track,"clock").currentPlayTime=120
            assertNotEquals(0.0,getField<Double>(track,"velocityW"),0.0)
            ValueAnimator::class.java.getDeclaredMethod("setDurationScale",Float::class.javaPrimitiveType).invoke(null,0f)
            track.form(7f,134f,.408,1.0)
            for(axis in listOf("velocityW","velocityH","velocityGlyph","velocityStretch"))
                assertEquals(axis,0.0,getField<Double>(track,axis),0.0)
            assertNull(getField<ValueAnimator?>(track,"clock"))
            ValueAnimator::class.java.getDeclaredMethod("setDurationScale",Float::class.javaPrimitiveType).invoke(null,scale)
            track.form(7f,134f,.408,1.0)
            assertNull("Re-enabling motion cannot revive a completed form",getField<ValueAnimator?>(track,"clock"))
        } finally {
            ValueAnimator::class.java.getDeclaredMethod("setDurationScale",Float::class.javaPrimitiveType).invoke(null,scale)
            track.stop(); ShadowChoreographer.setPaused(false)
        }
    }

    @Test fun settledFormRequestsDoNotCreateClocksOrPublishFrames() {
        ShadowChoreographer.setPaused(true)
        try {
            for ((w,h) in listOf(7f to 134f,47f to 148f,58f to 170f)) {
                var frames=0
                val track=IosVolumeStyle.Track(1f) { _,_,_,_ -> frames++ }
                try {
                    track.form(w,h,.408,1.0)
                    getField<ValueAnimator>(track,"clock").end()
                    val settledFrames=frames
                    repeat(120) {
                        track.form(w,h,.408,1.0)
                        assertNull("A settled form has no work to schedule",getField<ValueAnimator?>(track,"clock"))
                    }
                    assertEquals(settledFrames,frames)
                } finally { track.stop() }
            }
        } finally { ShadowChoreographer.setPaused(false) }
    }

    @Test fun sameFormStillStartsEntranceResumesInterruptionAndReleasesStretch() {
        ShadowChoreographer.setPaused(true)
        val track=IosVolumeStyle.Track(1f) { _,_,_,_ -> }
        try {
            track.form(47f,148f,.8,.9)
            val entrance=getField<ValueAnimator>(track,"clock")
            entrance.currentPlayTime=120
            track.form(47f,148f,.8,.9)
            assertSame(entrance,getField<ValueAnimator>(track,"clock"))
            track.stop()
            track.form(47f,148f,.8,.9)
            val resumed=getField<ValueAnimator>(track,"clock")
            assertNotSame(entrance,resumed)
            resumed.end()
            track.beginTouch(); track.overdrag(12f,16); track.dragging=false
            track.form(47f,148f,.408,1.0)
            getField<ValueAnimator>(track,"clock").end()
            assertEquals(0f,getField<Float>(track,"stretch"),0f)
            assertEquals(0.0,getField<Double>(track,"velocityStretch"),0.0)
        } finally { track.stop(); ShadowChoreographer.setPaused(false) }
    }

    @Test fun repeatedHardwareEndpointSignalsRetainOneContinuousAnimation() {
        ShadowChoreographer.setPaused(true)
        val track=IosVolumeStyle.Track(1f,true) { _,_,_,_-> }
        try {
            track.endpointKick(1f)
            val clock=getField<ValueAnimator>(track,"clock")
            repeat(80) {
                clock.currentPlayTime=(it*50L).coerceAtMost(clock.duration-1)
                track.endpointKick(1f)
                assertSame("A saturated key repeat must keep the existing motion",clock,getField<ValueAnimator>(track,"clock"))
            }
            clock.end()
            var continued: ValueAnimator?=null
            repeat(80) {
                track.endpointKick(1f)
                val next=getField<ValueAnimator?>(track,"clock")
                if(continued==null) continued=next else assertSame(continued,next)
            }
            assertNotNull("Accumulated visible displacement must still animate",continued)
            continued!!.end()
            track.endpointKick(-1f)
            val reverse=getField<ValueAnimator>(track,"clock")
            assertNotSame(continued,reverse)
            reverse.end(); track.releaseEndpoint(); track.endpointKick(-1f)
            assertNotNull("Release re-arms the next independent press",getField<ValueAnimator?>(track,"clock"))
        } finally { track.stop(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun constructorFailureRestoresNativePropertiesAndUnregistersTheHalfBinding() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root=FrameLayout(host); val slider=SeekBar(host); val fill=View(host)
        val track=FrameLayout(host); val icon=ImageView(host)
        for(v in listOf(slider,fill,track,icon)) root.addView(v,FrameLayout.LayoutParams(80,250))
        host.setContentView(root)
        val native=ColorDrawable(Color.RED); root.background=native
        val foreground=ColorDrawable(Color.BLUE); track.foreground=foreground
        val failure=IllegalStateException("blur unavailable")
        val calls=ArrayList<Boolean>()
        assertSame(failure,assertThrows(IllegalStateException::class.java) {
            IosVolumeStyle.Binding(root,slider,fill,track,icon) {
                calls.add(it); if(!it) throw failure
            }
        })
        assertEquals(listOf(false,true),calls)
        assertSame(native,root.background); assertSame(foreground,track.foreground)
        assertEquals(1f,fill.alpha,0f); assertEquals(80,slider.layoutParams.width)
        assertNull(tv.withaibuild.customiuizer.mods.utils.XposedHelpers.getAdditionalInstanceField(slider,"a14.iosVolume.binding"))
    }
    @Test fun failedBlurRestoreStillReleasesCallbacksGeometryAndBindingFields() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root=FrameLayout(host); val slider=SeekBar(host); val fill=View(host)
        val track=FrameLayout(host); val icon=ImageView(host)
        for(v in listOf(slider,fill,track,icon)) root.addView(v,FrameLayout.LayoutParams(80,250))
        host.setContentView(root)
        val native=ColorDrawable(Color.RED); root.background=native
        val binding=IosVolumeStyle.Binding(root,slider,fill,track,icon) { if(it) error("restore") }
        assertThrows(IllegalStateException::class.java) { binding.close() }
        assertSame(native,root.background); assertEquals(1f,fill.alpha,0f)
        assertEquals(80,slider.layoutParams.width); assertTrue(root.clipChildren)
        assertNull(tv.withaibuild.customiuizer.mods.utils.XposedHelpers.getAdditionalInstanceField(track,"a14.iosVolume.binding"))
        assertNull(getField<Any?>(getField<Any>(binding,"paint"),"clock"))
        binding.close()
    }
    @Test fun closePreservesLaterNativeWritesIncludingDetachedParentMaterial() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root=FrameLayout(host); val slider=SeekBar(host); val fill=View(host)
        val track=FrameLayout(host); val icon=ImageView(host)
        for(v in listOf(slider,fill,track,icon)) root.addView(v,FrameLayout.LayoutParams(80,250))
        host.setContentView(root)
        val binding=IosVolumeStyle.Binding(root,slider,fill,track,icon)
        val newer=ColorDrawable(Color.GREEN)
        root.background=newer; icon.translationY=999f; fill.alpha=.7f
        slider.layoutParams=FrameLayout.LayoutParams(91,92)
        binding.close()
        assertSame(newer,root.background); assertEquals(999f,icon.translationY,0f)
        assertEquals(.7f,fill.alpha,0f); assertEquals(91,slider.layoutParams.width)
        var mode=1
        val parent=FrameLayout(host).apply { background=newer }
        val session=IosVolumeStyle.Session(root,parent,null,attachColumn(root),{mode},{mode=it},{})
        // Closing before attachment must never suppress a material it did not own.
        session.close(); assertSame(newer,parent.background); assertEquals(1,mode)
    }
    @Test fun sessionInitializationFailureRestoresBackgroundEvenWhenModeRestoreAlsoFails() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root=FrameLayout(host); val content=FrameLayout(host)
        root.addView(content); host.setContentView(root)
        val native=ColorDrawable(Color.RED); content.background=native
        var ended=false
        assertThrows(IllegalStateException::class.java) {
            IosVolumeStyle.Session(root,content,null,attachColumn(root),{1},{error("mode")},{ended=true})
        }
        assertSame(native,content.background); assertTrue(ended)
        assertNull(tv.withaibuild.customiuizer.mods.utils.XposedHelpers.getAdditionalInstanceField(root,"a14.iosVolume.owner"))
    }
    @Test fun closingFooterPreservesLaterNativeLayoutAndClickWrites() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root=FrameLayout(host); val content=FrameLayout(host); val more=ImageView(host)
        root.addView(content); root.addView(more,FrameLayout.LayoutParams(20,20))
        host.setContentView(root)
        val session=IosVolumeStyle.Session(root,content,null,attachColumn(root),{0},{},{},more,null,moreAction={})
        val current=more.layoutParams as FrameLayout.LayoutParams
        current.width=91; current.topMargin=17
        var clicked=false; more.setOnClickListener { clicked=true }
        session.close()
        assertSame(current,more.layoutParams); assertEquals(91,more.layoutParams.width)
        assertEquals(17,(more.layoutParams as FrameLayout.LayoutParams).topMargin)
        more.performClick(); assertTrue(clicked)
    }
    @Test fun nativePaletteRewriteAfterAttachCannotStackASecondParentMaterial() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root=FrameLayout(host); val content=FrameLayout(host); val shadow=View(host)
        root.addView(content); root.addView(shadow); host.setContentView(root)
        var mode=1
        val session=IosVolumeStyle.Session(root,content,shadow,attachColumn(root),{mode},{mode=it},{})
        val newer=ColorDrawable(Color.GRAY)
        content.background=newer; mode=2; shadow.visibility=View.VISIBLE
        session.refreshMaterial()
        assertNull(content.background); assertEquals(0,mode)
        assertEquals(View.INVISIBLE,shadow.visibility)
        session.close()
        assertSame(newer,content.background); assertEquals(2,mode)
        assertEquals(View.VISIBLE,shadow.visibility)
    }

    @Test fun materialRefreshRestoresTheLatestNativePaletteInsteadOfTheInitialOne() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root=FrameLayout(host); val slider=SeekBar(host); val fill=View(host)
        val track=FrameLayout(host); val icon=ImageView(host)
        for(v in listOf(slider,fill,track,icon)) root.addView(v,FrameLayout.LayoutParams(80,250))
        host.setContentView(root)
        val binding=IosVolumeStyle.Binding(root,slider,fill,track,icon)
        val newer=ColorDrawable(Color.GREEN); val progress=ColorDrawable(Color.BLUE)
        root.background=newer; slider.progressDrawable=progress
        binding.refreshMaterial(); binding.close()
        assertSame(newer,root.background); assertSame(progress,slider.progressDrawable)
    }
    @Test fun startCompactHasNoWideFirstFrameAndReturnsToThinAfterTouch() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        ShadowChoreographer.setPaused(true)
        try {
            for (orientation in listOf(android.content.res.Configuration.ORIENTATION_PORTRAIT,
                android.content.res.Configuration.ORIENTATION_LANDSCAPE)) {
                val configuration = android.content.res.Configuration(host.get().resources.configuration).apply {
                    this.orientation = orientation
                }
                val context = host.get().createConfigurationContext(configuration)
                val root = FrameLayout(context); val slider = SeekBar(context); val fill = View(context)
                val track = FrameLayout(context); val icon = ImageView(context)
                for (v in listOf(slider, fill, track, icon)) root.addView(v, FrameLayout.LayoutParams(58,170))
                host.get().setContentView(root); slider.max = 100; slider.progress = 50
                val binding = IosVolumeStyle.Binding(root, slider, fill, track, icon, startCompact = true)
                val paint = getField<IosVolumeStyle.Track>(binding, "paint")
                assertTrue(paint.isCompact)
                assertEquals(7f, getField<Float>(paint, "width"), 0f)
                assertEquals(134f, getField<Float>(paint, "height"), 0f)
                assertEquals(0f, icon.alpha, 0f)
                assertNull(getField<ValueAnimator?>(paint, "clock"))
                binding.noteInput(); binding.noteInput()
                assertNull("Hardware input must keep the initial thin shape", getField<ValueAnimator?>(paint, "clock"))
                binding.interact(true); getField<ValueAnimator>(paint, "clock").end()
                assertEquals(58f, getField<Float>(paint, "width"), 0f)
                binding.interact(false); getField<ValueAnimator>(paint, "clock").end()
                assertEquals(7f, getField<Float>(paint, "width"), 0f)
                assertEquals(50, slider.progress)
                binding.close()
                assertEquals(58, slider.layoutParams.width)
            }
        } finally { ShadowChoreographer.setPaused(false); host.pause().stop().destroy() }
    }

    @Test fun compactCapsMatchCircularEndsAtDeviceDensity() {
        val track = IosVolumeStyle.Track(3f, startCompact = true) { _, _, _, _ -> }
        val actual = Bitmap.createBitmap(174, 510, Bitmap.Config.ARGB_8888)
        val expected = Bitmap.createBitmap(174, 510, Bitmap.Config.ARGB_8888)
        try {
            track.setBounds(0, 0, 174, 510); track.level = 10000; track.draw(Canvas(actual))
            val capsule = android.graphics.Path().apply {
                addRoundRect(0f, 0f, 21f, 402f, 10.5f, 10.5f, android.graphics.Path.Direction.CW)
            }
            val canvas = Canvas(expected)
            canvas.translate(153f, 54f); canvas.clipRect(0f, 0f, 21f, 402f)
            canvas.drawPath(capsule,
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            for (y in 54 until 456) for (x in 153 until 174) {
                val error = kotlin.math.abs(Color.alpha(actual.getPixel(x, y)) - Color.alpha(expected.getPixel(x, y)))
                assertTrue("Circular cap coverage differs at $x,$y by $error", error <= 3)
            }
        } finally { track.stop(); actual.recycle(); expected.recycle() }
    }

    @Test fun compactPanCapturesItsNativeStretchRangeBeforeChangingToInteracting() {
        ShadowChoreographer.setPaused(true)
        val track=IosVolumeStyle.Track(1f) { _,_,_,_ -> }
        try {
            track.form(7f,134f,.408,1.0); getField<ValueAnimator>(track,"clock").end()
            track.beginTouch()
            track.form(58f,170f,.408,1.0); getField<ValueAnimator>(track,"clock").end()
            assertEquals(16.70886f,track.panOverflow(100f),.001f)
            assertEquals(-track.panOverflow(100f),track.panOverflow(-100f),.0001f)
            assertEquals(0f,track.panOverflow(0f),0f)
        } finally { track.stop(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun nativeShadowFollowsThePaintedOutlineAndRestoresTheRouteIconLayer() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible()
        ShadowChoreographer.setPaused(true)
        try {
            val root=FrameLayout(host.get()); val slider=SeekBar(host.get()); val fill=View(host.get())
            val track=FrameLayout(host.get()); val icon=ImageView(host.get())
            for(v in listOf(slider,fill,track,icon)) root.addView(v,FrameLayout.LayoutParams(58,170))
            host.get().setContentView(root)
            val original=track.outlineProvider; track.elevation=2f; icon.translationZ=3f
            track.outlineAmbientShadowColor=Color.RED; track.outlineSpotShadowColor=Color.BLUE
            val binding=IosVolumeStyle.Binding(root,slider,fill,track,icon)
            val paint=getField<IosVolumeStyle.Track>(binding,"paint"); paint.setBounds(0,0,58,170)
            paint.form(7f,134f,.408,1.0); getField<ValueAnimator>(paint,"clock").end()
            val outline=android.graphics.Outline(); track.outlineProvider.getOutline(track,outline)
            val bounds=android.graphics.Rect(); assertTrue(outline.getRect(bounds))
            assertEquals(7,bounds.width()); assertEquals(134,bounds.height()); assertEquals(.3f,outline.alpha,.001f)
            assertTrue("The native route icon must remain above the shadow owner",icon.z>track.z)
            binding.close()
            assertSame(original,track.outlineProvider); assertEquals(2f,track.elevation,0f)
            assertEquals(Color.RED,track.outlineAmbientShadowColor); assertEquals(Color.BLUE,track.outlineSpotShadowColor)
            assertEquals(3f,icon.translationZ,0f)
        } finally { ShadowChoreographer.setPaused(false); host.pause().stop().destroy() }
    }
    @Test fun nativeLightPaletteCannotRecolorTheIndependentMoreDotsWhite() {
        val more=IosVolumeStyle.MoreGlyph(1f)
        val image=Bitmap.createBitmap(58,28,Bitmap.Config.ARGB_8888)
        try {
            more.setBounds(0,0,58,28); more.draw(Canvas(image))
            val color=image.getPixel(29,11)
            more.setTintList(android.content.res.ColorStateList.valueOf(Color.WHITE))
            more.colorFilter=android.graphics.PorterDuffColorFilter(Color.WHITE,android.graphics.PorterDuff.Mode.SRC_IN)
            image.eraseColor(Color.TRANSPARENT); more.draw(Canvas(image))
            assertEquals("Native volume palette updates must not change footer ink",color,image.getPixel(29,11))
            assertTrue(Color.red(color)<200)
        } finally { image.recycle() }
    }
    @Test fun nativeNonzeroMinimumCanOverdragWithoutWritingAnotherAudioValue() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible()
        ShadowChoreographer.setPaused(true)
        try {
            val root=FrameLayout(host.get()); val slider=SeekBar(host.get()); val fill=View(host.get())
            val track=FrameLayout(host.get()); val icon=ImageView(host.get())
            for(v in listOf(slider,fill,track,icon)) root.addView(v,FrameLayout.LayoutParams(58,170))
            host.get().setContentView(root); slider.max=15000; slider.progress=1000
            val binding=IosVolumeStyle.Binding(root,slider,fill,track,icon)
            binding.nativeVolume(1,1,15)
            fun touch(action: Int,y: Float,time: Long) {
                val e=MotionEvent.obtain(0,time,action,20f,y,0)
                try { binding.observeTouch(e) } finally { e.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN,500f,1)
            binding.interact(true)
            val paint=getField<IosVolumeStyle.Track>(binding,"paint")
            getField<ValueAnimator>(paint,"clock").end()
            touch(MotionEvent.ACTION_MOVE,600f,101)
            assertTrue("The native minimum need not be progress zero",getField<Float>(paint,"stretch")<0f)
            assertEquals(1000,slider.progress)
            touch(MotionEvent.ACTION_CANCEL,600f,102); binding.interact(false)
            getField<ValueAnimator>(paint,"clock").end()
            assertEquals(0f,getField<Float>(paint,"stretch"),0f)
            binding.close()
        } finally { ShadowChoreographer.setPaused(false); host.pause().stop().destroy() }
    }
    @Test fun hardwareKeysAtBothNativeEndpointsStartOneContinuousElasticResponse() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            for (endpoint in listOf(0,15)) {
                val root = FrameLayout(host); val slider = SeekBar(host); val fill = View(host)
                val track = FrameLayout(host); val icon = ImageView(host)
                for (v in listOf(slider,fill,track,icon)) root.addView(v,FrameLayout.LayoutParams(58,170))
                host.setContentView(root)
                track.clipToOutline = true
                val binding = IosVolumeStyle.Binding(root,slider,fill,track,icon)
                val paint = getField<IosVolumeStyle.Track>(binding,"paint")
                binding.nativeVolume(endpoint,0,15); binding.noteInput()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
                val clock = getField<ValueAnimator>(paint,"clock")
                clock.currentPlayTime = 65L
                val scale = getField<Float>(paint,"pressScale")
                assertTrue(if(endpoint==15) scale > 1f else scale < 1f)
                assertEquals("Button input is distinct from touch overflow",0f,getField<Float>(paint,"stretch"),0f)
                assertFalse(root.clipChildren); assertFalse(track.clipToOutline)
                clock.end(); assertEquals(0f,getField<Float>(paint,"stretch"),0f)
                binding.noteInput(); binding.nativeVolume(if(endpoint==15) 14 else 1,0,15)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40))
                assertEquals("Moving away from an endpoint must not kick it",0f,getField<Float>(paint,"stretch"),0f)
                binding.close()
                assertTrue(root.clipChildren); assertTrue(track.clipToOutline)
            }
        } finally { host.finish(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun footerFollowsThePaintedBottomWithoutOverlappingTheNativeSlider() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            val root = FrameLayout(host); val slider = SeekBar(host); val fill = View(host)
            val track = FrameLayout(host); val icon = ImageView(host); val more = ImageView(host)
            for (v in listOf(slider, fill, track, icon)) root.addView(v, FrameLayout.LayoutParams(58, 170))
            root.addView(more, FrameLayout.LayoutParams(58, 28)); host.setContentView(root)
            val binding = IosVolumeStyle.Binding(root, slider, fill, track, icon)
            track.layout(0, 20, 58, 190); more.layout(0, 230, 58, 258)
            binding.attachMore(IosVolumeStyle.MoreGlyph(1f), more)
            val paint = getField<IosVolumeStyle.Track>(binding, "paint")
            for ((w,h) in listOf(47f to 148f, 7f to 134f, 58f to 170f)) {
                paint.form(w,h,.408,1.0); getField<ValueAnimator>(paint,"clock").end()
                val bottom = track.top + (track.height+h)*.5f
                assertEquals("The existing footer touch box starts at the painted bottom", bottom, more.y, .01f)
                assertEquals(28, more.height)
            }
            binding.close()
        } finally { host.finish(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun nativeFillFramesRemainAuthoritativeOverTheSeekBarsParallelValueAnimation() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val root = FrameLayout(host); val slider = SeekBar(host); val fill = View(host)
            val track = FrameLayout(host); val icon = ImageView(host)
            for (v in listOf(slider, fill, track, icon)) root.addView(v, FrameLayout.LayoutParams(58, 170))
            host.setContentView(root); slider.max = 100; slider.progress = 50
            val binding = IosVolumeStyle.Binding(root, slider, fill, track, icon, nativeFill = true)
            val paint = getField<IosVolumeStyle.Track>(binding, "paint")
            binding.nativeProgress(50, 200); assertEquals(2500, paint.level)
            slider.progress = 80; binding.refresh(); assertEquals(2500, paint.level)
            binding.nativeProgress(80, 200); assertEquals(4000, paint.level)
            binding.close(); binding.nativeProgress(200, 200); assertEquals(4000, paint.level)
        } finally { host.finish() }
    }
    @Test fun filledAntialiasedPerimeterHasNoDarkUnderpaint() {
        ShadowChoreographer.setPaused(true)
        val track = IosVolumeStyle.Track(1f) { _, _, _, _ -> }
        val image = Bitmap.createBitmap(58, 170, Bitmap.Config.ARGB_8888)
        try {
            track.setBounds(0, 0, 58, 170); track.form(47f, 148f, .8, .9)
            getField<ValueAnimator>(track, "clock").end(); track.level = 10000
            track.draw(Canvas(image))
            var edgePixels = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val pixel = image.getPixel(x, y)
                if (Color.alpha(pixel) in 40..240) {
                    edgePixels++
                    assertTrue("A translucent edge must contain only the white fill", Color.red(pixel) >= 246)
                }
            }
            assertTrue(edgePixels > 0)
        } finally { track.stop(); image.recycle(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun moreDotsCollapseToOnePointAlignedWithTheCompactStripOnTheSameClock() {
        ShadowChoreographer.setPaused(true)
        val track = IosVolumeStyle.Track(1f) { _, _, _, _ -> }
        val more = IosVolumeStyle.MoreGlyph(1f)
        val image = Bitmap.createBitmap(58, 28, Bitmap.Config.ARGB_8888)
        try {
            more.setBounds(0, 0, 58, 28); track.attachMore(more)
            track.form(47f, 148f, .8, .9); getField<ValueAnimator>(track, "clock").end()
            more.draw(Canvas(image))
            for (x in listOf(23, 29, 35)) assertTrue(Color.alpha(image.getPixel(x, 11)) > 0)
            assertEquals(0,Color.alpha(image.getPixel(29,16)))
            image.eraseColor(Color.TRANSPARENT)
            track.form(7f, 134f, .408, 1.0); getField<ValueAnimator>(track, "clock").end()
            more.draw(Canvas(image))
            assertTrue(Color.alpha(image.getPixel(54, 11)) > 0)
            assertEquals("The single dot matches the 7px compact strip",7,(0 until 58).count { Color.alpha(image.getPixel(it,11))>128 })
            for (x in listOf(23, 29, 35)) assertEquals(0, Color.alpha(image.getPixel(x, 11)))
        } finally { track.stop(); image.recycle(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun nativeEndpointOverdragKeepsTheAudioValueAndReleasesOnTheExistingFiniteClock() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            for (endpoint in listOf(0, 100)) {
                val root = FrameLayout(host); val slider = SeekBar(host); val fill = View(host)
                val track = FrameLayout(host); val icon = ImageView(host)
                for (v in listOf(slider, fill, track, icon)) root.addView(v, FrameLayout.LayoutParams(58, 170))
                host.setContentView(root); slider.max = 100; slider.progress = endpoint
                val binding = IosVolumeStyle.Binding(root, slider, fill, track, icon)
                val paint = getField<IosVolumeStyle.Track>(binding, "paint")
                fun touch(action: Int, y: Float, time: Long) {
                    val event = MotionEvent.obtain(0L, time, action, 20f, y, 0)
                    try { binding.observeTouch(event) } finally { event.recycle() }
                }
                touch(MotionEvent.ACTION_DOWN, 500f, 1L); binding.interact(true)
                getField<ValueAnimator>(paint, "clock").end()
                touch(MotionEvent.ACTION_MOVE, if (endpoint == 100) 400f else 600f, 101L)
                val stretch = getField<Float>(paint, "stretch")
                assertTrue(if (endpoint == 100) stretch > 0f else stretch < 0f)
                assertTrue(kotlin.math.abs(stretch) < 18f)
                assertEquals(endpoint, slider.progress)
                assertNull("Tracking invalidates only on input, without another animation clock", getField<ValueAnimator?>(paint, "clock"))
                touch(MotionEvent.ACTION_UP, 500f, 150L); binding.interact(false)
                getField<ValueAnimator>(paint, "clock").end()
                assertEquals(0f, getField<Float>(paint, "stretch"), 0f)
                binding.close(); assertTrue(root.clipChildren)
                assertEquals(endpoint, slider.progress)
            }
        } finally { host.finish(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun hiddenTemporaryContainerDoesNotCountAsAnotherVisibleVolumeColumn() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            val hidden = FrameLayout(host); val column = View(host)
            hidden.addView(column)
            assertTrue(IosVolumeStyle.visibleColumn(column))
            hidden.visibility = View.GONE
            assertEquals(View.VISIBLE, column.visibility)
            assertFalse(IosVolumeStyle.visibleColumn(column))
        } finally { host.finish() }
    }
    @Test fun sessionPreservesNativeWindowTravelThroughoutItsLifetime() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val parent = FrameLayout(host); val dialog = FrameLayout(host); val content = FrameLayout(host)
            parent.addView(dialog, FrameLayout.LayoutParams(58, 198)); dialog.addView(content)
            host.setContentView(parent); parent.layout(0, 0, 400, 800); dialog.layout(330, 180, 388, 378)
            val session = IosVolumeStyle.Session(dialog, content, null, attachColumn(dialog), { 0 }, {}, {})
            assertEquals(330f, dialog.x, .01f)
            dialog.x = 360f
            session.close(); assertEquals(360f, dialog.x, .01f)
        } finally { host.finish() }
    }
    @Test fun compactPaintMovesToNativeTrailingEdgeWithinTheUnchangedTouchBox() {
        ShadowChoreographer.setPaused(true)
        val track = IosVolumeStyle.Track(1f) { _, _, _, _ -> }
        val image = Bitmap.createBitmap(58, 170, Bitmap.Config.ARGB_8888)
        try {
            track.setBounds(0, 0, 58, 170); track.level = 5000
            track.form(47f, 148f, .8, .9); getField<ValueAnimator>(track, "clock").end()
            track.draw(Canvas(image)); assertTrue(Color.alpha(image.getPixel(29, 85)) > 0)
            image.eraseColor(Color.TRANSPARENT)
            track.form(7f, 134f, .408, 1.0); getField<ValueAnimator>(track, "clock").end()
            track.draw(Canvas(image))
            assertEquals(0, Color.alpha(image.getPixel(29, 85)))
            assertTrue(Color.alpha(image.getPixel(55, 85)) > 0)
            assertEquals(58, track.bounds.width())
        } finally { track.stop(); image.recycle(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun regularCornersRemainDistinctFromCompactCapsuleEnds() {
        assertEquals(14.9977f, IosVolumeStyle.cornerRadius(47f), .0001f)
        assertEquals(3.5f, IosVolumeStyle.cornerRadius(7f), .0001f)
        assertEquals(IosVolumeStyle.cornerRadius(17f), IosVolumeStyle.cornerRadius(17.0001f), .0001f)
    }
    @Test fun footerWinsOverTheNativeSliderSiblingAndIncludesTheTranslatedWindowEdge() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            val root = FrameLayout(activity.get()); val more = ImageView(activity.get()); val content = FrameLayout(activity.get())
            root.addView(more,FrameLayout.LayoutParams(80,80)); root.addView(content,FrameLayout.LayoutParams(100,100))
            activity.get().setContentView(root)
            // The native ImageButton can take focus on the first UP instead of
            // clicking. Exercise a real touch from another focused view.
            more.isFocusableInTouchMode=true; root.isFocusableInTouchMode=true
            var native=0; var intercepted=0
            content.setOnTouchListener { _,_ -> intercepted++; true }
            val session=IosVolumeStyle.Session(root,content,null,attachColumn(root),{ 0 },{},{},more,null) { native++ }
            root.measure(View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY))
            root.layout(0,0,100,100)
            root.requestFocus(); assertFalse(more.hasFocus())
            assertTrue(more.z>content.z)
            val x=more.x+more.width*.5f; val y=more.y+more.height*.5f
            for(action in intArrayOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val event=MotionEvent.obtain(0,10,action,x,y,0)
                try { root.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1,native); assertEquals(0,intercepted)
            root.translationX=12f
            val location=IntArray(2); root.getLocationInWindow(location)
            val edgeX=location[0]+root.width-1; val edgeY=location[1]+root.height/2
            val region=android.graphics.Region(root.left,root.top,root.right,root.bottom)
            assertFalse(region.contains(edgeX,edgeY))
            session.includeTouchableRegion(region)
            assertTrue(region.contains(edgeX,edgeY))
            session.close(); assertEquals(0f,more.translationZ,0f)
            assertTrue(more.isFocusableInTouchMode)
            val closed=android.graphics.Region(root.left,root.top,root.right,root.bottom)
            session.includeTouchableRegion(closed); assertFalse(closed.contains(edgeX,edgeY))
        } finally { activity.pause().stop().destroy() }
    }
    @Test fun systemRouteIconAndItsDrawableStateSurviveStylingAndRecoverOnClose() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible()
        ShadowChoreographer.setPaused(true)
        try {
            val root=FrameLayout(host.get()); val slider=SeekBar(host.get()); val fill=View(host.get())
            val track=FrameLayout(host.get()); val icon=ImageView(host.get())
            for(v in listOf(slider,fill,track,icon)) root.addView(v,FrameLayout.LayoutParams(58,170))
            host.get().setContentView(root)
            val drawable=android.graphics.drawable.ColorDrawable(Color.WHITE).apply { level=1234 }
            val tint=android.content.res.ColorStateList.valueOf(Color.GRAY)
            icon.setImageDrawable(drawable); icon.imageTintList=tint
            val binding=IosVolumeStyle.Binding(root,slider,fill,track,icon,nativeFill=true)
            val paint=getField<IosVolumeStyle.Track>(binding,"paint")
            paint.form(47f,148f,.8,.9); getField<ValueAnimator>(paint,"clock").end()
            binding.nativeProgress(0,100)
            assertSame(drawable,icon.drawable); assertEquals(1234,icon.drawable.level); assertTrue(icon.alpha>0f)
            val emptyTint=icon.imageTintList!!.defaultColor
            binding.nativeProgress(100,100)
            assertNotEquals(emptyTint,icon.imageTintList!!.defaultColor)
            assertSame(drawable,icon.drawable)
            binding.close(); assertSame(drawable,icon.drawable); assertSame(tint,icon.imageTintList)
        } finally { ShadowChoreographer.setPaused(false); host.pause().stop().destroy() }
    }
    @Test fun separateMoreFooterInvokesNativeActionAndRestoresItsListener() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val root = FrameLayout(host); val content = FrameLayout(host); val more = ImageView(host)
            root.addView(content); root.addView(more, FrameLayout.LayoutParams(80, 80))
            host.setContentView(root)
            val image = ColorDrawable(Color.RED)
            more.setImageDrawable(image); more.scaleType = ImageView.ScaleType.CENTER; more.translationY = 6.5f
            var original = 0; var native = 0
            val listener = View.OnClickListener { original++ }
            more.setOnClickListener(listener); more.isClickable = false
            val layout = more.layoutParams
            val session = IosVolumeStyle.Session(root, content, null, attachColumn(root), { 0 }, {}, {}, more, listener) { native++ }
            assertTrue(more.isClickable); more.performClick(); assertEquals(1, native); assertEquals(0, original)
            session.close(); assertFalse(more.isClickable); assertSame(layout, more.layoutParams)
            assertSame(image, more.drawable); assertEquals(ImageView.ScaleType.CENTER, more.scaleType)
            assertEquals(6.5f, more.translationY, 0f)
            more.performClick(); assertEquals(1, original); assertEquals(1, native)
        } finally { host.finish() }
    }
    @Test fun parentMaterialCreatedByNativeAttachIsRestoredBeforeExpandedControls() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val root = FrameLayout(host); val content = FrameLayout(host); val shadow = View(host)
            root.addView(content); root.addView(shadow)
            val native = ColorDrawable(Color.GRAY)
            var mode = 0; var ended = 0; var writes = 0
            content.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { content.background = native; mode = 1 }
                override fun onViewDetachedFromWindow(v: View) { content.background = null }
            })
            val session = IosVolumeStyle.Session(root, content, shadow, attachColumn(root), { mode }, { mode = it; writes++ }, { ended++ })
            host.setContentView(root)
            assertNull(content.background); assertEquals(0, mode); assertEquals(View.INVISIBLE, shadow.visibility)
            val firstWrites=writes
            repeat(8) { session.refreshMaterial() }
            assertEquals(firstWrites,writes)
            val latest=ColorDrawable(Color.BLUE); content.background=latest; mode=2
            session.refreshMaterial()
            assertEquals(firstWrites+1,writes)
            session.close(); session.close()
            assertSame(latest, content.background); assertEquals(2, mode); assertEquals(View.VISIBLE, shadow.visibility)
            assertEquals(1, ended)
        } finally { host.finish() }
    }
    @Test fun cosmeticBindingKeepsNativeListenerAndValueAndRestoresEveryOwnedProperty() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        ShadowChoreographer.setPaused(true)
        try {
            val root = FrameLayout(host); val slider = SeekBar(host); val fill = android.view.View(host)
            val track = FrameLayout(host); val icon = ImageView(host)
            for (v in listOf(slider, fill, track, icon)) root.addView(v, FrameLayout.LayoutParams(80, 250))
            host.setContentView(root)
            val bg = ColorDrawable(Color.RED); val fg = ColorDrawable(Color.BLUE)
            root.background = bg; track.foreground = fg
            slider.max = 15000; slider.progress = 4000
            var nativeChanges = 0
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) { nativeChanges++ }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
            val blurChanges = mutableListOf<Boolean>()
            val binding = IosVolumeStyle.Binding(root, slider, fill, track, icon) { blurChanges += it }
            assertEquals(4000, slider.progress)
            slider.progress = 7000; binding.refresh()
            assertEquals(1, nativeChanges)
            assertEquals(7000, slider.progress)
            assertEquals(0f, fill.alpha, 0f)
            val paint = getField<IosVolumeStyle.Track>(binding, "paint")
            binding.noteInput()
            assertFalse("Initial appearance stays full", paint.isCompact)
            binding.noteInput()
            assertTrue("Subsequent hardware values select the compact strip", paint.isCompact)
            binding.interact(true)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200))
            assertFalse("A native drag must not collapse underneath the pointer", paint.isCompact)
            binding.interact(false)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200))
            assertTrue(paint.isCompact)
            binding.interact(true)
            assertFalse(paint.isCompact)
            binding.interact(false)
            assertTrue("A drag that started compact returns to compact immediately", paint.isCompact)
            binding.noteInput()
            assertTrue("Hardware updates keep the compact strip", paint.isCompact)
            slider.progress = 8000; binding.refresh()
            assertEquals(8000, slider.progress)
            assertEquals(5333, paint.level)
            assertTrue(paint.isCompact)
            binding.close()
            binding.close()
            assertEquals(listOf(false, true), blurChanges)
            assertSame(bg, root.background); assertSame(fg, track.foreground)
            assertEquals(80, slider.layoutParams.width); assertEquals(250, slider.layoutParams.height)
            assertEquals(1f, fill.alpha, 0f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200))
            assertNull(getField<ValueAnimator?>(paint, "clock"))
        } finally { host.finish(); ShadowChoreographer.setPaused(false) }
    }
    @Test fun fullSizedTrackUsesAVerticalCapsuleAndNativeLevelFill() {
        ShadowChoreographer.setPaused(true)
        val track = IosVolumeStyle.Track(1f) { _, _, _, _ -> }
        val image = Bitmap.createBitmap(58, 170, Bitmap.Config.ARGB_8888)
        try {
            track.setBounds(0, 0, 58, 170); track.form(47f, 148f, .8, .9)
            getField<ValueAnimator>(track, "clock").end()
            track.level = 5000; track.draw(Canvas(image))
            assertTrue(Color.alpha(image.getPixel(10, 50)) > 0)
            assertEquals(0, Color.alpha(image.getPixel(6, 12)))
            assertEquals(0xfffafafa.toInt(), image.getPixel(29, 140))
            assertNotEquals(0xfffafafa.toInt(), image.getPixel(29, 35))
            image.eraseColor(Color.TRANSPARENT); track.alpha = 0; track.draw(Canvas(image))
            assertEquals(0, Color.alpha(image.getPixel(29, 100)))
        } finally { track.stop(); image.recycle(); ShadowChoreographer.setPaused(false) }
    }
}
