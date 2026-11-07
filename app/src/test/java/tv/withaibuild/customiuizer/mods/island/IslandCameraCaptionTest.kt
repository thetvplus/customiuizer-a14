package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers.getField
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class,qualifiers="w393dp-h873dp-440dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandCameraCaptionTest {
    @Test fun cameraSideCaptionAndClipStayPinnedThroughResizeAndHorizontalInterruption() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val parent=FrameLayout(host)
        host.setContentView(parent)
        val density=host.resources.displayMetrics.density
        fun dp(value: Int)=(value*density).roundToInt()
        val width=dp(280);val height=dp(40);val edge=dp(IslandSizing.EDGE_DP)
        // Distinct declared camera reservations in the audited A14 profiles.
        // These are geometry inputs, not copied ROM resources or a device allowlist.
        try {
            for(holeWidth in intArrayOf(54,68,78)) for(rtl in booleanArrayOf(false,true)) {
                val shell=IslandActivityView(host)
                shell.layoutDirection=if(rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                parent.addView(shell,FrameLayout.LayoutParams(width,height))
                val gap=maxOf(dp(40),holeWidth+dp(16))
                val sign=if(rtl) -1f else 1f
                shell.bind("Ready\nSaved",null,null,false,false,false,0,false,expanded=true)
                val title=getField<TextView>(shell,"title")
                val lane=shell.contentAt(1)
                try {
                    for(offset in floatArrayOf(-dp(8).toFloat(),0f,dp(8).toFloat())) {
                        shell.geometry(width,height,gap,width,cameraOffset=offset)
                        parent.measure(exact(width),exact(height));parent.layout(0,0,width,height)
                        shell.measure(exact(width),exact(height));shell.layout(0,0,width,height)
                        val cameraEdge=floatArrayOf(width*.5f+offset+sign*gap*.5f,0f)
                        val parentMatrix=Matrix();parent.transformMatrixToGlobal(parentMatrix)
                        parentMatrix.mapPoints(cameraEdge)
                        for(scale in floatArrayOf(.55f,.8f,1f,1.14f)) for(shift in floatArrayOf(-dp(8).toFloat(),0f,dp(8).toFloat())) {
                            shell.scaleX=scale;shell.translationX=shift
                            // An unchanged geometry call samples the real View transforms.
                            shell.geometry(width,height,gap,width,cameraOffset=offset)
                            val layout=title.layout
                            val matrix=Matrix();title.transformMatrixToGlobal(matrix)
                            assertEquals(2,layout.lineCount)
                            for(line in 0 until layout.lineCount) {
                                val textEdge=floatArrayOf(title.totalPaddingLeft+
                                    (if(rtl) layout.getLineRight(line) else layout.getLineLeft(line)),0f)
                                matrix.mapPoints(textEdge)
                                assertEquals("Each caption line follows the inner edge: rtl=$rtl scale=$scale shift=$shift",
                                    cameraEdge[0],textEdge[0],1.1f)
                            }
                            val clip=RectF(requireNotNull(lane.clipBounds))
                            matrix.reset();lane.transformMatrixToGlobal(matrix);matrix.mapRect(clip)
                            assertEquals("Clipping shares the caption's inner anchor",cameraEdge[0],if(rtl) clip.right else clip.left,1.1f)
                            val cap=floatArrayOf(width*.5f+shift+sign*(width*scale*.5f-edge),0f)
                            parentMatrix.mapPoints(cap)
                            if(rtl) assertTrue("Visible text stays inside the outer margin",clip.left>=cap[0]-1.1f)
                            else assertTrue("Visible text stays inside the outer margin",clip.right<=cap[0]+1.1f)
                        }
                        shell.scaleX=1f;shell.translationX=0f
                        shell.geometry(width,height,gap,width,cameraOffset=offset)
                        val ink=inkBounds(title)
                        val matrix=Matrix();title.transformMatrixToGlobal(matrix);matrix.mapRect(ink)
                        assertTrue("Actual short glyphs do not retain unused outer-alignment space",
                            kotlin.math.abs((if(rtl) ink.right else ink.left)-cameraEdge[0])<title.textSize*.2f+2f)
                    }
                } finally { parent.removeView(shell) }
            }
        } finally { host.finish() }
    }

    @Test fun sidePlacementReleasesTheCameraAnchorAndKeepsItsOuterTextMargin() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val parent=FrameLayout(host);host.setContentView(parent)
        val shell=IslandActivityView(host)
        val density=host.resources.displayMetrics.density
        fun dp(value: Int)=(value*density).roundToInt()
        val width=dp(280);val height=dp(40)
        parent.addView(shell,FrameLayout.LayoutParams(width,height))
        shell.bind("Ready",null,null,false,false,false,0,false,expanded=true)
        try {
            shell.geometry(width,height,dp(40))
            parent.measure(exact(width),exact(height));parent.layout(0,0,width,height)
            shell.geometry(width,height,0,alignment=2)
            parent.measure(exact(width),exact(height));parent.layout(0,0,width,height)
            val title=getField<TextView>(shell,"title")
            for(scale in floatArrayOf(.7f,1f,1.1f)) {
                shell.scaleX=scale;shell.geometry(width,height,0,alignment=2)
                val point=floatArrayOf(title.totalPaddingLeft+title.layout.getLineRight(0),0f)
                val matrix=Matrix();title.transformMatrixToGlobal(matrix);matrix.mapPoints(point)
                val edge=floatArrayOf(width*.5f+width*scale*.5f-dp(IslandSizing.EDGE_DP),0f)
                matrix.reset();parent.transformMatrixToGlobal(matrix);matrix.mapPoints(edge)
                assertEquals("A side without a central camera keeps the original outer anchor",edge[0],point[0],1.1f)
            }
        } finally { parent.removeView(shell);host.finish() }
    }

    private fun exact(size: Int)=View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY)

    private fun inkBounds(view: TextView): RectF {
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            var left=bitmap.width;var right=0;var top=bitmap.height;var bottom=0
            for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) {
                if(Color.alpha(bitmap.getPixel(x,y))<128) continue
                left=minOf(left,x);right=maxOf(right,x+1);top=minOf(top,y);bottom=maxOf(bottom,y+1)
            }
            assertTrue("The real TextView must draw visible glyphs",right>left && bottom>top)
            return RectF(left.toFloat(),top.toFloat(),right.toFloat(),bottom.toFloat())
        } finally { bitmap.recycle() }
    }
}
