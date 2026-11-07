package tv.withaibuild.customiuizer.mods.homeindicator

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Method

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=android.app.Application::class)
class HomeIndicatorLifecycleTest {
    class Handle(context: Context): View(context) {
        @JvmField val mPaint=Paint().apply { isAntiAlias=true }
        public override fun onDraw(canvas: Canvas) {}
    }
    class Bar(context: Context): FrameLayout(context) {
        @JvmField var mNavBarMode=2
        fun updateIcons(config: Configuration) {}
    }
    class Incomplete(context: Context): View(context) {
        public override fun onDraw(canvas: Canvas) {}
    }
    class MutablePaint(context: Context): View(context) {
        @JvmField var mPaint=Paint()
        public override fun onDraw(canvas: Canvas) {}
    }
    private class Drawing: Canvas() {
        var paint: Paint?=null
        val bounds=RectF()
        override fun drawPath(path: Path, paint: Paint) {
            this.paint=paint; path.computeBounds(bounds,true)
        }
    }
    @Test fun nativeModeChangesMissingHostAndGeometryFailureKeepNativeDrawing() = scene { bar,handle,invoke ->
        val canvas=Drawing()
        assertFalse(invoke(handle,"onDraw",canvas))
        assertSame(handle.mPaint,canvas.paint)
        bar.mNavBarMode=0; invoke(bar,"updateIcons",Configuration())
        assertTrue(invoke(handle,"onDraw",Drawing()))
        bar.mNavBarMode=1; invoke(bar,"updateIcons",Configuration())
        assertTrue(invoke(handle,"onDraw",Drawing()))
        bar.mNavBarMode=2; invoke(bar,"updateIcons",Configuration())
        assertFalse(invoke(handle,"onDraw",Drawing()))
        handle.layout(0,0,200,1)
        assertTrue(invoke(handle,"onDraw",Drawing()))
        bar.removeView(handle)
        assertNull(XposedHelpers.getAdditionalInstanceField(handle,"a14.home.indicator.owner"))
        assertTrue(invoke(handle,"onDraw",Drawing()))
    }
    @Test fun detachReinflateSizeAndNativePaintChangesUseTheCurrentOwner() = scene { bar,handle,invoke ->
        val first=Drawing(); assertFalse(invoke(handle,"onDraw",first))
        handle.mPaint.color=0xff123456.toInt()
        val color=Drawing(); assertFalse(invoke(handle,"onDraw",color));assertEquals(handle.mPaint.color,color.paint!!.color)
        handle.layout(0,0,100,80)
        val narrow=Drawing();assertFalse(invoke(handle,"onDraw",narrow));assertTrue(narrow.bounds.width()<=100f)
        bar.removeView(handle)
        handle.mPaint.color=0xff654321.toInt()
        bar.addView(handle);handle.layout(0,0,280,80)
        val reattached=Drawing();assertFalse(invoke(handle,"onDraw",reattached));assertSame(handle.mPaint,reattached.paint)
        bar.removeView(handle)
        val inflated=Handle(bar.context);bar.addView(inflated);inflated.layout(0,0,280,80)
        val reinflated=Drawing()
        assertFalse(invoke(inflated,"onDraw",reinflated));assertSame(inflated.mPaint,reinflated.paint)
        bar.removeView(inflated)
        assertNull(XposedHelpers.getAdditionalInstanceField(inflated,"a14.home.indicator.owner"))
    }
    @Test fun incompleteNativeContractFailsBeforeAnyHookCanInstall() {
        assertThrows(NoSuchFieldException::class.java) { HomeIndicatorHooks.prepare(Incomplete::class.java,Bar::class.java) }
        assertThrows(IllegalArgumentException::class.java) { HomeIndicatorHooks.prepare(MutablePaint::class.java,Bar::class.java) }
        val support=tv.withaibuild.customiuizer.utils.HomeIndicatorSupport
        assertTrue(support.supported(34,arrayOf("arm64-v8a","armeabi-v7a")))
        assertFalse(support.supported(35,arrayOf("arm64-v8a")))
        assertFalse(support.supported(34,arrayOf("x86_64")))
    }
    private fun scene(block: (Bar,Handle,(View,String,Any)->Boolean)->Unit) {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val host=controller.get();val bar=Bar(host);val handle=Handle(host)
            host.setContentView(bar);bar.addView(handle)
            val screen=host.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            bar.rootView.layout(0,0,screen.width(),screen.height())
            bar.layout(0,0,screen.width(),80);handle.layout(0,0,280,80)
            val hooks=HomeIndicatorHooks.prepare(Handle::class.java,Bar::class.java).toMap()
            block(bar,handle) { receiver,name,arg ->
                val method=receiver.javaClass.getDeclaredMethod(name,if(name=="onDraw") Canvas::class.java else Configuration::class.java)
                var native=false
                val chain=object: XposedInterface.Chain {
                    override fun getExecutable(): Method=method
                    override fun getThisObject(): Any=receiver
                    override fun getArgs(): List<Any?> = listOf(arg)
                    override fun getArg(index: Int): Any=arg
                    override fun proceed(): Any? { native=true;return null }
                    override fun proceed(args: Array<Any?>)=proceed()
                    override fun proceedWith(receiver: Any)=proceed()
                    override fun proceedWith(receiver: Any,args: Array<Any?>)=proceed()
                }
                hooks.getValue(method).intercept(chain);native
            }
        } finally { controller.pause().stop().destroy() }
    }
}
