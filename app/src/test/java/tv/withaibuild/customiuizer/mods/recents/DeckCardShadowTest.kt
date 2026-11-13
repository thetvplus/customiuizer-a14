package tv.withaibuild.customiuizer.mods.recents

import android.animation.ObjectAnimator
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
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
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckCardShadowTest {
    private class Scene {
        val parent = FrameLayout(RuntimeEnvironment.getApplication()).apply { clipChildren = false }
        val card = FrameLayout(parent.context)
        val thumbnail = View(parent.context)
        init {
            parent.addView(card)
            card.addView(thumbnail)
            parent.layout(0, 0, 500, 800)
            card.layout(40, 50, 340, 650)
            thumbnail.layout(0, 0, 300, 600)
        }
    }

    @Test fun projectionUsesTheThumbnailSilhouetteAndLeavesTheHeaderUnclipped() {
        val scene = Scene()
        val card = scene.card
        val foreground = ColorDrawable(0xff000000.toInt())
        card.foreground = foreground
        scene.thumbnail.layout(6, 0, 293, 590)
        val shadow = DeckCardShadow(card, scene.thumbnail)
        try {
            shadow.sync(0f, 18, 45)
            assertTrue(shadow.owned)
            assertSame(shadow, card.outlineProvider)
            assertSame(foreground, card.foreground)
            val outline = Outline()
            card.outlineProvider.getOutline(card, outline)
            val rect = Rect()
            assertTrue(outline.getRect(rect))
            val density = card.resources.displayMetrics.density
            val left = density.roundToInt()
            val up = (2f * density).roundToInt()
            assertEquals(Rect(6 - left, 45 - up, 293 - left, 590 - up), rect)
            assertEquals(18f, outline.radius, 0f)
            assertEquals(1f, outline.alpha, 0f)
            assertFalse(card.clipToOutline)
        } finally { shadow.close() }
    }

    @Test fun reversalAndHiddenThumbnailsUpdateOneOutlineBeforeTheNextDraw() {
        val scene = Scene()
        val card = scene.card
        val thumbnail = scene.thumbnail
        thumbnail.alpha = 0f
        val shadow = DeckCardShadow(card, thumbnail)
        try {
            val outline = Outline()
            card.outlineProvider.getOutline(card, outline)
            assertTrue(outline.isEmpty)
            thumbnail.alpha = .75f
            for (progress in floatArrayOf(1f, .8f, .4f, 0f, .4f, .8f, 1f, 0f)) {
                shadow.sync(progress, 18, 45)
                card.outlineProvider.getOutline(card, outline)
                assertEquals(.75f * (1f - progress), outline.alpha, 0f)
            }
            thumbnail.layout(0, 0, 300, 420)
            shadow.sync(0f, 27, 45)
            card.outlineProvider.getOutline(card, outline)
            val rect = Rect()
            assertTrue(outline.getRect(rect))
            assertEquals(420 - (2f * card.resources.displayMetrics.density).roundToInt(), rect.bottom)
            assertEquals(27f, outline.radius, 0f)
            thumbnail.visibility = View.INVISIBLE
            shadow.sync(0f, 27, 45)
            card.outlineProvider.getOutline(card, outline)
            assertTrue(outline.isEmpty)
            assertEquals(0f, outline.alpha, 0f)
        } finally { shadow.close() }
    }

    @Test fun rearProjectionFollowsNativeHoldAndRunningSnapshotJoinsOnHandoff() {
        val front = Scene()
        val rear = Scene()
        val frontShadow = DeckCardShadow(front.card, front.thumbnail)
        val rearShadow = DeckCardShadow(rear.card, rear.thumbnail)
        val frontOutline = Outline()
        val rearOutline = Outline()
        fun frame(fullscreen: Float) {
            frontShadow.sync(fullscreen, 18, 45, 1f - fullscreen)
            rearShadow.sync(fullscreen, 18, 45)
            frontShadow.getOutline(front.card, frontOutline)
            rearShadow.getOutline(rear.card, rearOutline)
        }
        try {
            front.thumbnail.alpha = 0f
            frame(1f)
            assertTrue(frontOutline.isEmpty)
            assertTrue(rearOutline.isEmpty)
            frame(.5f)
            assertEquals(.5f, frontOutline.alpha, 0f)
            assertEquals(.5f, rearOutline.alpha, 0f)
            // Reverse before releasing. Only the native position changes.
            frame(.75f)
            assertEquals(.25f, rearOutline.alpha, 0f)
            assertEquals(.25f, frontOutline.alpha, 0f)
            frame(1f)
            assertTrue(rearOutline.isEmpty)
            frame(.25f)
            assertEquals(.75f, rearOutline.alpha, 0f)
            front.thumbnail.alpha = .5f
            frame(.25f)
            assertEquals(.75f, frontOutline.alpha, 0f)
            front.thumbnail.alpha = 1f
            frame(.25f)
            assertEquals(rearOutline.alpha, frontOutline.alpha, 0f)
            frame(0f)
            assertEquals(1f, frontOutline.alpha, 0f)
            assertEquals(rearOutline.alpha, frontOutline.alpha, 0f)
        } finally {
            frontShadow.close()
            rearShadow.close()
        }
    }

    @Test fun shadowRestoresOnlyItsOwnOutlineAndRespectsNativeClipping() {
        val scene = Scene()
        val card = scene.card
        val original = ViewOutlineProvider.BOUNDS
        val replacement = ViewOutlineProvider.BACKGROUND
        card.outlineProvider = original
        val nativeSpot = 0xff445566.toInt()
        val ambient = 0xff112233.toInt()
        card.outlineSpotShadowColor = nativeSpot
        card.outlineAmbientShadowColor = ambient
        val shadow = DeckCardShadow(card, scene.thumbnail)
        assertEquals(nativeSpot, card.outlineSpotShadowColor)
        assertEquals(ambient, card.outlineAmbientShadowColor)
        shadow.close()
        assertSame(original, card.outlineProvider)
        assertEquals(nativeSpot, card.outlineSpotShadowColor)
        val next = DeckCardShadow(card, scene.thumbnail)
        card.outlineProvider = replacement
        card.outlineSpotShadowColor = 0xff223344.toInt()
        next.sync(0f, 18, 45)
        assertSame(replacement, card.outlineProvider)
        assertEquals(0xff223344.toInt(), card.outlineSpotShadowColor)
        card.clipToOutline = true
        assertFalse(DeckCardShadow(card, scene.thumbnail).owned)
        card.clipToOutline = false
        scene.parent.clipChildren = true
        assertFalse(DeckCardShadow(card, scene.thumbnail).owned)
        assertTrue(scene.parent.clipChildren)
    }

    @Test fun nativeUpwardNegativeZeroUsesTranslationWhileOtherDirectionsStayNative() {
        assertTrue(DeckDismissMotion.admitted(1,-0.0f))
        assertTrue(DeckDismissMotion.admitted(1,0f))
        assertFalse(DeckDismissMotion.admitted(0,-0.0f))
        assertFalse(DeckDismissMotion.admitted(1,300f))
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
