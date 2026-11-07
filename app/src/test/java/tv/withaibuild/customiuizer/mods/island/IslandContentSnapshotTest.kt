package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderNode
import android.view.View
import android.view.ViewOutlineProvider
import android.graphics.Outline
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers.getField
import org.robolectric.util.ReflectionHelpers.setField

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandContentSnapshotTest {
    @Test fun outgoingPixelsCannotChangeWhenTheLiveChildDisplayListIsReplaced() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val owner=object:FrameLayout(host) { override fun isHardwareAccelerated()=true }
        val group=FrameLayout(host)
        val leaf=object:View(host) {
            var color=Color.RED
            override fun onDraw(canvas: Canvas) { canvas.drawColor(color) }
        }
        leaf.outlineProvider=object:ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0,0,32,16,8f) }
        }
        leaf.clipToOutline=true
        group.addView(leaf,FrameLayout.LayoutParams(32,16))
        owner.addView(group,FrameLayout.LayoutParams(32,16))
        host.setContentView(owner,FrameLayout.LayoutParams(32,16))
        owner.measure(View.MeasureSpec.makeMeasureSpec(32,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(16,View.MeasureSpec.EXACTLY))
        owner.layout(0,0,32,16)
        val attach=getField<Any>(owner,"mAttachInfo")
        setField(attach,"mHardwareAccelerated",true)
        val snapshot=IslandContentSnapshot()
        val update=View::class.java.getDeclaredMethod("updateDisplayListIfDirty").apply { isAccessible=true }
        val renderer=Class.forName("android.graphics.HardwareRenderer")
        val readback=renderer.getDeclaredMethod("createHardwareBitmap",RenderNode::class.java,
            Int::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
        fun color(node: RenderNode, x: Int=8, y: Int=8): Int {
            val hardware=readback.invoke(null,node,32,16) as Bitmap
            val pixels=hardware.copy(Bitmap.Config.ARGB_8888,false)
            return try { pixels.getPixel(x,y) } finally { pixels.recycle(); hardware.recycle() }
        }
        try {
            update.invoke(owner)
            assertTrue(snapshot.capture(owner,group,1f,1f))
            leaf.color=Color.BLUE; leaf.invalidate()
            // ViewGroup's traversal normally transfers PFLAG_INVALIDATED to
            // this flag before asking a child to replace its display list.
            setField(leaf,"mRecreateDisplayList",true)
            val live=update.invoke(leaf) as RenderNode
            assertEquals("The control must really display the new live pixels",Color.BLUE,color(live))
            assertEquals(Color.RED,color(getField(snapshot,"node")))
            assertEquals("Photo corner clipping survives the frozen handoff",Color.TRANSPARENT,
                color(getField(snapshot,"node"),0,0))
        } finally { snapshot.clear(); setField(attach,"mHardwareAccelerated",false); host.finish() }
    }
}
