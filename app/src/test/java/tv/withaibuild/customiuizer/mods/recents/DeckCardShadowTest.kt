package tv.withaibuild.customiuizer.mods.recents

import android.animation.ObjectAnimator
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckCardShadowTest {
    @Test fun settledWindowDoesNotLoseAndRegainItsOutlineWhenTheThumbnailArrivesLate() {
        val card=FrameLayout(RuntimeEnvironment.getApplication())
        val header=View(card.context);val thumbnail=View(card.context)
        card.addView(header);card.addView(thumbnail)
        card.layout(0,0,300,700);header.layout(0,0,300,50);thumbnail.layout(0,50,300,700)
        thumbnail.alpha=0f
        val shadow=DeckCardShadow(card,header,22f,thumbnail,0f)
        val bounds=RectF(0f,50f,300f,700f);val outline=Outline()
        try {
            for(p in floatArrayOf(0f,.1f,.5f,.9f,1f,.9f,.5f)) {
                shadow.projectWindow(bounds,p);shadow.getOutline(card,outline)
                assertEquals(p*p*(3f-2f*p),outline.alpha,.0001f)
            }
            shadow.finishWindow(false);shadow.getOutline(card,outline)
            assertEquals("Cancel restores the native hidden-thumbnail state",0f,outline.alpha,0f)
            shadow.projectWindow(bounds,1f);shadow.finishWindow(true)
            for(alpha in floatArrayOf(0f,.15f,.6f,1f)) {
                thumbnail.alpha=alpha;shadow.syncContentAlpha(0f);shadow.getOutline(card,outline)
                assertEquals("The same projection persists through native finish and thumbnail restore",1f,outline.alpha,0f)
                val shown=Rect();assertTrue(outline.getRect(shown));assertEquals(Rect(0,50,300,700),shown)
            }
            thumbnail.alpha=.4f;shadow.syncContentAlpha(0f);shadow.getOutline(card,outline)
            assertEquals("The thumbnail owns the outline again",.4f,outline.alpha,0f)
        } finally { shadow.close() }
    }
    @Test fun fullscreenSnapshotHasNoDeckShadowAndContinuousReversalReusesNativeProgress() {
        val card=FrameLayout(RuntimeEnvironment.getApplication())
        val header=View(card.context);val thumbnail=View(card.context)
        card.addView(header);card.addView(thumbnail)
        card.layout(0,0,300,700);header.layout(0,0,300,50);thumbnail.layout(0,50,300,700)
        thumbnail.alpha=.8f
        val shadow=DeckCardShadow(card,header,22f,thumbnail,1f)
        val outline=Outline()
        try {
            shadow.getOutline(card,outline);assertEquals(0f,outline.alpha,0f)
            for(progress in floatArrayOf(1f,.8f,.4f,0f,.4f,.8f,1f)) {
                shadow.syncContentAlpha(progress);shadow.getOutline(card,outline)
                assertEquals(.8f*(1f-progress),outline.alpha,.0001f)
                assertEquals("Projection does not rewrite native thumbnail opacity",.8f,thumbnail.alpha,0f)
                assertEquals(1f,card.scaleX,0f)
            }
        } finally { shadow.close() }
    }
    @Test fun lastCardFirstOutlineReadsContentBeforeRegistrationAndAfterBindingWithoutTouch() {
        repeat(4) {
            val card=FrameLayout(RuntimeEnvironment.getApplication())
            val header=View(card.context); val thumbnail=View(card.context)
            card.addView(header); card.addView(thumbnail)
            card.layout(0,0,300,700); header.layout(0,0,300,50); thumbnail.layout(0,50,300,700)
            card.alpha=.8f; thumbnail.alpha=0f // native handoff before CARD registration
            val shadow=DeckCardShadow(card,header,22f,thumbnail,0f)
            val outline=Outline(); shadow.getOutline(card,outline)
            assertEquals(0f,outline.alpha,0f); assertEquals(.8f,card.alpha,0f)
            thumbnail.alpha=.45f // bind updates content without a module setter callback
            shadow.getOutline(card,outline); assertEquals(.45f,outline.alpha,0f)
            shadow.syncContentAlpha(0f); thumbnail.alpha=1f
            shadow.getOutline(card,outline); assertEquals(1f,outline.alpha,0f)
            shadow.close()
            val reused=DeckCardShadow(card,header,22f,thumbnail,0f)
            reused.getOutline(card,outline); assertEquals(1f,outline.alpha,0f); reused.close()
        }
    }
    @Test fun shadowCannotAppearBeforeItsContentOrWhileTheNativeThumbnailIsHidden() {
        val card=FrameLayout(RuntimeEnvironment.getApplication())
        val header=View(card.context);val thumbnail=View(card.context)
        card.addView(header);card.addView(thumbnail)
        card.layout(0,0,300,700);header.layout(0,0,300,50)
        val shadow=DeckCardShadow(card,header,22f,thumbnail,0f)
        val outline=Outline()
        try {
            shadow.getOutline(card,outline)
            assertEquals("No orphan shadow while the thumbnail has no bounds",0f,outline.alpha,0f)
            thumbnail.layout(0,50,300,700)
            shadow.syncContentAlpha(0f);shadow.getOutline(card,outline)
            assertEquals(1f,outline.alpha,0f)
            assertEquals(22f,outline.radius,0f)
            for(visibility in intArrayOf(View.INVISIBLE,View.GONE,View.VISIBLE,View.INVISIBLE,View.VISIBLE)) {
                thumbnail.visibility=visibility
                shadow.syncContentAlpha(0f);shadow.getOutline(card,outline)
                assertEquals(if(visibility==View.VISIBLE) 1f else 0f,outline.alpha,0f)
                assertEquals("Native thumbnail alpha is not rewritten",1f,thumbnail.alpha,0f)
            }
        } finally { shadow.close() }
    }
    @Test fun nativeUpwardNegativeZeroUsesTranslationWhileOtherDirectionsStayNative() {
        assertTrue(DeckDismissMotion.admitted(1,-0.0f))
        assertTrue(DeckDismissMotion.admitted(1,0.0f))
        assertFalse(DeckDismissMotion.admitted(0,-0.0f))
        assertFalse(DeckDismissMotion.admitted(1,300f))
    }
    @Test fun contentOutlineExcludesHeaderAndPositiveProjectionKeepsOrderThroughNativeZChanges() {
        val parent=FrameLayout(RuntimeEnvironment.getApplication())
        val order=DeckDepthOrder(parent)
        val cards=Array(3) { FrameLayout(parent.context).also { parent.addView(it); it.layout(0,0,300,600) } }
        val shadows=cards.map { view ->
            val header=View(parent.context); view.addView(header); header.layout(0,0,300,40)
            DeckCardShadow(view,header,24f,view,0f)
        }
        val entries=cards.mapIndexed { i,view -> order.add(view,i,shadows[i].height) }
        cards[1].translationZ=100f; order.apply()
        assertTrue(cards[0].z>cards[1].z); assertTrue(cards[1].z>cards[2].z)
        assertTrue(cards.all { it.z>shadows[0].height })
        val outline=Outline(); val rect=Rect()
        shadows[0].getOutline(cards[0],outline)
        assertTrue(outline.getRect(rect)); assertEquals(Rect(0,40,300,600),rect)
        assertEquals(24f,outline.radius,0f)
        shadows[0].radius(18f); shadows[0].getOutline(cards[0],outline)
        assertEquals(18f,outline.radius,0f)
        entries.forEach(order::remove); shadows.forEach { it.close() }
        assertEquals(100f,cards[1].translationZ,0f)
        assertTrue(parent.clipChildren); assertTrue(parent.clipToPadding)
    }

    @Test fun recyclingRestoresOwnedPropertiesAndPreservesNewNativeWrites() {
        val card=FrameLayout(RuntimeEnvironment.getApplication())
        val header=View(card.context); card.addView(header)
        val original=card.outlineProvider
        card.outlineAmbientShadowColor=0xff112233.toInt()
        card.outlineSpotShadowColor=0xff445566.toInt()
        val shadow=DeckCardShadow(card,header,22f,card,0f)
        assertEquals("Keep native ambient shadow color",0xff112233.toInt(),card.outlineAmbientShadowColor)
        assertEquals("Keep native spot shadow color",0xff445566.toInt(),card.outlineSpotShadowColor)
        shadow.close(); shadow.close()
        assertSame(original,card.outlineProvider)
        assertEquals(0xff112233.toInt(),card.outlineAmbientShadowColor)
        assertEquals(0xff445566.toInt(),card.outlineSpotShadowColor)
        val second=DeckCardShadow(card,header,22f,card,0f)
        card.outlineProvider=ViewOutlineProvider.BOUNDS; card.outlineSpotShadowColor=0xff778899.toInt()
        second.close()
        assertSame(ViewOutlineProvider.BOUNDS,card.outlineProvider)
        assertEquals(0xff778899.toInt(),card.outlineSpotShadowColor)
        card.clipToOutline=true
        val clipped=DeckCardShadow(card,header,22f,card,0f)
        assertFalse(clipped.owned); assertEquals(second.height,clipped.height,0f)
        assertTrue(card.clipToOutline)
    }

    @Test fun invisibleNativeContentCannotKeepCastingADeckShadowBehindItsAppSurface() {
        val card=FrameLayout(RuntimeEnvironment.getApplication())
        val header=View(card.context); card.addView(header)
        card.layout(0,0,300,700); header.layout(0,0,300,50)
        val shadow=DeckCardShadow(card,header,22f,card,0f)
        val outline=android.graphics.Outline()
        card.alpha=0f; shadow.syncContentAlpha(0f); shadow.getOutline(card,outline)
        assertEquals(0f,outline.alpha,0f)
        card.alpha=.4f; shadow.syncContentAlpha(0f); shadow.getOutline(card,outline)
        assertEquals(.4f,outline.alpha,0f)
        card.alpha=1f; shadow.syncContentAlpha(0f); shadow.getOutline(card,outline)
        assertEquals(1f,outline.alpha,0f)
        shadow.close()
    }

    @Test fun committedDismissalTranslatesOffscreenWithoutShrinkingAndRetainsNativeCompletion() {
        val host=Robolectric.buildActivity(android.app.Activity::class.java).setup().visible()
        ShadowChoreographer.setPaused(true)
        try {
            val parent=FrameLayout(host.get()); host.get().setContentView(parent)
            val card=View(host.get()); parent.addView(card); parent.layout(0,0,600,1000); card.layout(0,100,300,700)
            card.scaleX=.83f; card.scaleY=.83f; card.alpha=.82f; card.translationY=-70f
            val native=ObjectAnimator.ofFloat(card,View.SCALE_X,0f).apply {
                duration=450; interpolator=LinearInterpolator()
            }
            val geometry=DeckCardGeometry(card,parent).apply { apply(1f) }
            val motion=DeckDismissMotion.upward(geometry,native)
            assertEquals(native.duration,motion.duration); assertSame(native.interpolator,motion.interpolator)
            var ended=false
            motion.addListener(object:android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation:android.animation.Animator) { ended=true }
            })
            motion.start(); motion.currentPlayTime=0
            assertEquals(-70f,card.translationY,.001f)
            motion.currentPlayTime=200
            assertTrue(card.translationY < -70f)
            assertEquals(.83f,card.scaleX,0f); assertEquals(.83f,card.scaleY,0f); assertEquals(.82f,card.alpha,0f)
            motion.end(); assertTrue(ended)
            assertTrue(card.translationY+card.top+card.height*card.scaleY<0f)
        } finally { ShadowChoreographer.setPaused(false); host.pause().stop().destroy() }
    }
}
