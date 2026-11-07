package tv.withaibuild.customiuizer.mods.recents

import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckHeaderOwnerTest {
    @Test fun unmeasuredLandscapeHeaderGetsItsDepthOpacityBeforeTheFirstGeometryFrame() {
        val root=FrameLayout(RuntimeEnvironment.getApplication())
        val icon=View(root.context);val title=TextView(root.context);val badge=View(root.context)
        for(v in arrayOf(icon,title,badge)) root.addView(v)
        val owner=DeckHeaderOwner(root,icon,title,badge,DeckHeaderConfig(0,0))
        try {
            assertEquals(0,icon.width);assertEquals(0,title.height)
            owner.pose(1f,3,-.35f,true)
            assertEquals(.7075f,root.alpha,.001f)
            assertEquals(0f,title.alpha,0f)
            // A native alpha write can precede child measurement as well.
            root.alpha=.8f;owner.refreshOpacity(true)
            assertEquals(.8f*.7075f,root.alpha,.001f)
            owner.pose(1f,0,-.025f,true)
            assertEquals(1f,title.alpha,0f)
            owner.pose(1f,0,-.025f,false)
            assertEquals(.08f,title.alpha,.001f)
            owner.pose(1f,0,-.025f,true)
            assertEquals(1f,title.alpha,0f)
        } finally { owner.close() }
    }
    @Test fun nativeChildrenAlphaDuringEntryKeepsDepthCorrectBeforeAnyTouch() {
        val root=FrameLayout(RuntimeEnvironment.getApplication()); root.layout(0,0,300,80)
        val icon=View(root.context); val title=TextView(root.context); val badge=View(root.context)
        for(v in arrayOf(icon,title,badge)) root.addView(v)
        icon.layout(0,10,40,50); title.layout(48,10,200,50); badge.layout(210,10,230,30)
        val owner=DeckHeaderOwner(root,icon,title,badge,DeckHeaderConfig(0,0))
        owner.pose(.9f,3,-.35f)
        val depthAlpha=root.alpha
        assertTrue(depthAlpha<1f); assertEquals(0f,title.alpha,0f)
        // Native entry runs after the Deck pose and resets all children alpha.
        // The setter's completion must restore the depth before first drawing.
        repeat(3) {
            root.alpha=1f; owner.refreshOpacity(true)
            assertEquals(depthAlpha,root.alpha,.001f); assertEquals(0f,title.alpha,0f)
        }
        owner.close(); assertEquals(1f,root.alpha,0f)
    }
    @Test fun autoMeasuresDrawableContentInsteadOfPaddedIconView() {
        val root=FrameLayout(RuntimeEnvironment.getApplication()); root.layout(0,0,300,80)
        val icon=ImageView(root.context).apply { setPadding(4,6,5,7); setImageDrawable(ColorDrawable(Color.RED)) }
        val text=TextView(root.context); val badge=View(root.context)
        for(v in arrayOf(icon,text,badge)) root.addView(v)
        icon.layout(10,10,58,58); text.layout(70,10,200,50); badge.layout(210,10,230,30)
        val owner=DeckHeaderOwner(root,icon,text,badge,DeckHeaderConfig(0,0)); owner.pose(1.7f)
        val bitmap=Bitmap.createBitmap(510,136,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap); canvas.scale(1.7f,1.7f); root.draw(canvas)
            var left=510; var right=-1; var top=136; var bottom=-1
            for(y in 0 until 136) for(x in 0 until 510) {
                val color=bitmap.getPixel(x,y)
                if(Color.alpha(color)>128 && Color.red(color)>240 && Color.green(color)<10) {
                    left=minOf(left,x); right=maxOf(right,x); top=minOf(top,y); bottom=maxOf(bottom,y)
                }
            }
            assertEquals(26f,maxOf(right-left+1,bottom-top+1).toFloat(),1f)
        } finally { owner.close(); bitmap.recycle() }
    }
    @Test fun depthSeparatesTitleAndIconAndHiddenHeaderCannotOwnTouch() {
        val root=FrameLayout(RuntimeEnvironment.getApplication()); root.layout(0,0,300,80)
        val icon=View(root.context); val text=TextView(root.context); val badge=View(root.context)
        for(v in arrayOf(icon,text,badge)) root.addView(v)
        icon.layout(0,10,40,50); text.layout(48,10,200,50); badge.layout(210,10,230,30)
        root.alpha=.8f; text.alpha=.7f
        val owner=DeckHeaderOwner(root,icon,text,badge,DeckHeaderConfig(0,0))
        owner.pose(1.3f,1,-.2f)
        assertEquals(.8f,root.alpha,.001f)
        assertTrue(text.alpha in .3f.. .4f)
        owner.pose(1.2f,2,-.35f)
        assertTrue(root.alpha>0f); assertEquals(0f,text.alpha,0f)
        owner.pose(1f,5,-.6f)
        assertEquals(View.INVISIBLE,root.visibility)
        owner.pose(1.3f,0,-.2f)
        assertEquals(View.VISIBLE,root.visibility); assertEquals(.7f,text.alpha,.001f)
        root.alpha=.4f; owner.refreshOpacity(true)
        owner.close(); assertEquals(.4f,root.alpha,0f); assertEquals(.7f,text.alpha,0f)
        assertSame(icon,root.getChildAt(0)); assertSame(text,root.getChildAt(1))
    }
    @Test fun autoKeepsIconTextGapsAndBaselineIndependentOfDeckMagnificationAndReleases() {
        val root=FrameLayout(RuntimeEnvironment.getApplication())
        val icon=View(root.context); val text=TextView(root.context).apply { setTextSize(TypedValue.COMPLEX_UNIT_PX,20f); setText("Sample") }
        val badge=View(root.context)
        root.addView(icon); root.addView(text); root.addView(badge)
        root.layout(0,0,400,80); icon.layout(10,16,58,64); text.layout(66,20,200,60); badge.layout(210,28,234,52)
        val originalPivot=icon.pivotX
        val owner=DeckHeaderOwner(root,icon,text,badge,DeckHeaderConfig(0,0))
        for(deck in floatArrayOf(.7f,1f,1.4f,1.8f)) {
            owner.pose(deck)
            assertEquals(26f,icon.width*icon.scaleX*deck,.001f)
            assertEquals(15f,text.textSize*text.scaleY*deck,.001f)
            val iconBottom=icon.top+icon.height*.5f+icon.translationY+icon.height*.5f*icon.scaleY
            assertEquals(8f,(root.height-iconBottom)*deck,.001f)
            val baseline=text.top+text.height*.5f+text.translationY+(text.baseline-text.height*.5f)*text.scaleY
            assertEquals(16f,(root.height-baseline)*deck,.001f)
            val right=icon.left+icon.translationX+icon.width*icon.scaleX
            val left=text.left+text.translationX
            assertEquals(8f,(left-right)*deck,.001f)
            assertEquals(20f,text.textSize,0f)
        }
        owner.close(); assertEquals(1f,icon.scaleX,0f); assertEquals(1f,text.scaleY,0f)
        assertEquals(0f,text.translationX,0f); assertEquals(0f,icon.translationY,0f); assertEquals(originalPivot,icon.pivotX,0f)
        owner.pose(2f); assertEquals(1f,icon.scaleX,0f)
    }
    @Test fun manualControlsAreIndependentAndRelativeToNativeHeader() {
        val root=FrameLayout(RuntimeEnvironment.getApplication()); root.layout(0,0,300,80)
        val icon=View(root.context); val title=TextView(root.context).apply { setTextSize(TypedValue.COMPLEX_UNIT_PX,20f) }; val badge=View(root.context)
        for(v in arrayOf(icon,title,badge)) root.addView(v)
        icon.layout(0,10,40,50); title.layout(48,10,200,50); badge.layout(210,10,230,30)
        val owner=DeckHeaderOwner(root,icon,title,badge,DeckHeaderConfig(75,110))
        owner.pose(1.5f)
        assertEquals(30f,icon.width*icon.scaleX*1.5f,.001f)
        assertEquals(22f,title.textSize*title.scaleY*1.5f,.001f)
        owner.close()
    }
}
