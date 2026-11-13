package tv.withaibuild.customiuizer.mods.recents

import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class DeckDepthOrderTest {
    @Test fun runningTaskBoostAndDelayedNativeZeroCannotFlipOverlappingCards() {
        val parent=FrameLayout(RuntimeEnvironment.getApplication())
        val projection=7f*parent.resources.displayMetrics.density
        val order=DeckDepthOrder(parent)
        val views=Array(3) { View(parent.context).also { v -> parent.addView(v); v.elevation=2f } }
        val entries=views.mapIndexed { i,v -> order.add(v,i) }
        fun assertOrder() {
            order.apply()
            assertEquals(projection+1f,views[0].z,.00001f)
            for(i in 1 until views.size) assertTrue(views[i-1].z-views[i].z>=.1f)
        }
        assertOrder()
        // Returning from the middle/older app gives it the highest native Z;
        // its delayed completion then clears Z independently of layout updates.
        views[1].translationZ=10f; assertOrder()
        views[0].translationZ=0f; views[1].translationZ=0f; assertOrder()
        views[2].translationZ=12f; assertOrder()
        // Native current task indices, rather than an old layout frame, own rank.
        entries[0].index=1; entries[1].index=0; order.apply()
        assertTrue(views[1].z>views[0].z); assertTrue(views[0].z>views[2].z)
    }
    @Test fun rearCardShadowsKeepSeparateHwuiLayersAtLargeTaskIndices() {
        val parent=FrameLayout(RuntimeEnvironment.getApplication())
        val order=DeckDepthOrder(parent)
        val cards=List(24) { index ->
            val view=View(parent.context)
            parent.addView(view)
            view to (800+index*3)
        }
        for((view,index) in cards.asReversed()) order.add(view,index)
        order.apply()
        assertTrue(cards.last().first.z>0f)
        for(i in 1 until cards.size) {
            assertTrue("Android 14 merges shadow batches below 0.1px",
                cards[i-1].first.z-cards[i].first.z>=.1f)
        }
    }

    @Test fun swipeLiftNegativeDismissDepthAndCancelKeepTheDeckOrder() {
        val parent=FrameLayout(RuntimeEnvironment.getApplication())
        val view=View(parent.context); parent.addView(view)
        val projection=7f*parent.resources.displayMetrics.density
        val order=DeckDepthOrder(parent); val entry=order.add(view,2)
        order.apply()
        for(z in floatArrayOf(10f,10f,-2f,-5f,-10f)) {
            view.translationZ=z; order.apply(); assertEquals(projection+1f,view.z,.00001f)
        }
        // A cancelled dismissal restores its ordinary Deck rank before drawing.
        view.translationZ=0f; order.apply()
        assertEquals(projection+1f,view.z,.00001f)
        order.remove(entry); assertEquals(0f,view.translationZ,0f)
        view.translationZ=7f; order.apply(); assertEquals(7f,view.translationZ,0f)
        // A newer native owner and a reparented view are never overwritten.
        val second=order.add(view,0); order.apply()
        view.translationZ=projection+2f
        order.remove(second); assertEquals(projection+2f,view.translationZ,0f)
        val third=order.add(view,0); parent.removeView(view); order.apply()
        assertEquals(projection+2f,view.translationZ,0f); order.remove(third)
    }
}
