package tv.withaibuild.customiuizer.mods.recents

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
            thumbnail.layout(0, 45, 300, 600)
        }
    }

    @Test fun nativeForegroundDrawsOnlyAShortBottomEdgeInTheCardsPose() {
        val scene = Scene()
        val card = scene.card
        val shadow = DeckCardShadow(card, scene.thumbnail, 0f)
        assertTrue(shadow.owned)
        assertSame(shadow, card.foreground)
        val image = Bitmap.createBitmap(500, 800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(image)
            val depth = 5f * card.resources.displayMetrics.density
            for (offset in listOf(0f, -240f, 75f)) {
                image.eraseColor(Color.TRANSPARENT)
                card.translationY = offset
                card.translationX = 80f
                scene.parent.draw(canvas)
                val bottom = (card.bottom + offset).toInt()
                val center = card.left + 80 + card.width / 2
                assertEquals(0, Color.alpha(image.getPixel(center, bottom - 1)))
                val atEdge = Color.alpha(image.getPixel(center, bottom))
                val half = Color.alpha(image.getPixel(center, bottom + (depth * .5f).toInt()))
                assertTrue(atEdge in 1..36)
                assertTrue(half in 1 until atEdge)
                assertEquals(0, Color.alpha(image.getPixel(center, bottom + depth.toInt() + 1)))
                assertEquals(0, Color.alpha(image.getPixel(card.left + 80, bottom)))
                assertEquals(0, Color.alpha(image.getPixel(center, bottom - 50)))
            }
        } finally { shadow.close(); image.recycle() }
    }

    @Test fun reversalHiddenContentAndRelayoutKeepTheShadowAtTheLiveBottom() {
        val scene = Scene()
        val card = scene.card
        val thumbnail = scene.thumbnail
        thumbnail.alpha = 0f
        val shadow = DeckCardShadow(card, thumbnail, 0f)
        val image = Bitmap.createBitmap(500, 800, Bitmap.Config.ARGB_8888)
        try {
            fun alpha(y: Int = thumbnail.bottom): Int {
                image.eraseColor(Color.TRANSPARENT)
                card.draw(Canvas(image))
                return Color.alpha(image.getPixel(150, y))
            }
            assertEquals(0, alpha())
            thumbnail.alpha = 1f
            val full = alpha()
            assertTrue("First draw reads the current thumbnail", full > 0)
            for (progress in floatArrayOf(1f, .8f, .4f, 0f, .4f, .8f, 1f, 0f)) {
                shadow.syncContentAlpha(progress)
                assertEquals(full * (1f - progress), alpha().toFloat(), 2f)
            }
            val oldBottom = thumbnail.bottom
            thumbnail.layout(0, 45, 300, 420)
            shadow.syncContentAlpha(0f)
            assertEquals(0, alpha(oldBottom))
            assertEquals(full, alpha())
            thumbnail.visibility = View.INVISIBLE
            assertEquals(0, alpha())
            thumbnail.visibility = View.VISIBLE
            thumbnail.layout(0, 45, 1, 420)
            assertEquals(0, alpha())
        } finally { shadow.close(); image.recycle() }
    }

    @Test fun shadowSuppressesNativeProjectionAndRestoresOnlyItsOwnProperties() {
        val scene = Scene()
        val card = scene.card
        val original = ViewOutlineProvider.BOUNDS
        card.outlineProvider = original
        val shadow = DeckCardShadow(card, scene.thumbnail, 0f)
        val outline = Outline()
        card.outlineProvider.getOutline(card, outline)
        val rect = Rect()
        assertTrue(outline.getRect(rect))
        assertEquals(Rect(0, 0, 300, 600), rect)
        assertEquals(0f, outline.alpha, 0f)
        shadow.close()
        assertNull(card.foreground)
        assertSame(original, card.outlineProvider)
        val next = DeckCardShadow(card, scene.thumbnail, 0f)
        val replacement = ColorDrawable(Color.BLUE)
        card.foreground = replacement
        next.syncContentAlpha(0f)
        assertSame(replacement, card.foreground)
        assertSame(original, card.outlineProvider)
        assertFalse(DeckCardShadow(card, scene.thumbnail, 0f).owned)
        card.foreground = null
        scene.parent.clipChildren = true
        assertFalse(DeckCardShadow(card, scene.thumbnail, 0f).owned)
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
