package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.view.View
import android.os.Handler
import android.os.Looper
import com.android.systemui.qs.tileimpl.QSTileImpl
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Executable
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class CollapseControlCenterTest {
    @Test fun queuedStateUpdatesDoNotLeaveAHeaderLeaseForLaterNativeMessages() = withHooks { hooks ->
        val tile=QSTileImpl()
        val handler=Handler(Looper.getMainLooper())
        invoke(hooks,"click",tile,arrayOfNulls<Any>(1)) {
            tile.click(null)
            handler.post { tile.refresh(1) }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0,tile.headerMessages)
        handler.post { tile.refresh(2);tile.showStateMessage("Independent hint") }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,tile.headerMessages)
        assertEquals(2,tile.accessibilityCallbacks)
    }
    @Test fun nativeClickStillRunsButItsLateHeaderMessageCannotSurviveAutoCollapse() = withHooks { hooks ->
        val tile=QSTileImpl()
        invoke(hooks,"click",tile,arrayOfNulls<Any>(1)) { tile.click(null) }
        assertEquals(1,tile.nativeClicks)
        assertEquals(1,tile.mHost.collapses)
        tile.refresh(1)
        assertEquals(0,tile.headerMessages)
        assertEquals(1,tile.stateCallbacks)
        assertEquals(1,tile.accessibilityCallbacks)
        invoke(hooks,"showStateMessage",tile,arrayOf("Mode enabled")) { tile.showStateMessage("Mode enabled") }
        assertEquals("Explicit native feedback must remain available",1,tile.headerMessages)
        assertEquals(1,tile.getState().state)
    }

    @Test fun unavailableAndEditTilesKeepTheNativePanelAndMessages() = withHooks { hooks ->
        for(tile in listOf(QSTileImpl(state=0),QSTileImpl(spec="edit"))) {
            invoke(hooks,"click",tile,arrayOfNulls<Any>(1)) { tile.click(null) }
            invoke(hooks,"showStateMessage",tile,arrayOf("Native hint")) { tile.showStateMessage("Native hint") }
            assertEquals(1,tile.nativeClicks)
            assertEquals(0,tile.mHost.collapses)
            assertEquals(1,tile.headerMessages)
        }
    }

    @Test fun failedNativeClickDoesNotCloseThePanel() = withHooks { hooks ->
        val tile=QSTileImpl()
        val error=IllegalStateException("native click failed")
        assertSame(error,assertThrows(IllegalStateException::class.java) {
            invoke(hooks,"click",tile,arrayOfNulls<Any>(1)) { throw error }
        })
        assertEquals(0,tile.mHost.collapses)
    }

    @Test fun failedCollapseOrStateReadPreservesNativeFeedback() = withHooks { hooks ->
        for(failState in listOf(false,true)) {
            val tile=QSTileImpl()
            if(failState) tile.stateFailure=IllegalStateException("state read")
            else tile.mHost.failure=IllegalStateException("collapse")
            invoke(hooks,"click",tile,arrayOfNulls<Any>(1)) { tile.click(null) }
            assertEquals(0,tile.mHost.collapses)
            assertTrue(tile.mPendingShowStateMessage)
            tile.stateFailure=null;tile.refresh(1)
            assertEquals(1,tile.headerMessages)
        }
    }

    @Test fun reentrantNewClickAndDestroyedTilesKeepNativeOneShotOwnership() = withHooks { hooks ->
        val tile=QSTileImpl()
        tile.mHost.collapsing={ tile.click(null) }
        invoke(hooks,"click",tile,arrayOfNulls<Any>(1)) { tile.click(null) }
        assertTrue("Do not consume a newer click",tile.mPendingShowStateMessage)
        tile.refresh(1);assertEquals(1,tile.headerMessages)
        tile.mHost.collapsing=null
        invoke(hooks,"click",tile,arrayOfNulls<Any>(1)) { tile.click(null) }
        tile.destroy();tile.refresh(2)
        assertEquals(1,tile.headerMessages)
    }

    private fun invoke(hooks: Map<Executable,XposedInterface.Hooker>,name: String,tile: QSTileImpl,
        args: Array<out Any?>,original: ()->Unit) {
        val method=QSTileImpl::class.java.getDeclaredMethod(name,if(name=="click") View::class.java else CharSequence::class.java)
        val chain=object: XposedInterface.Chain {
            override fun getExecutable()=method
            override fun getThisObject()=tile
            override fun getArgs(): List<Any?> = args.toList()
            override fun getArg(index: Int)=args[index]
            override fun proceed(): Any? { original(); return null }
            override fun proceed(args: Array<Any?>)=proceed()
            override fun proceedWith(thisObject: Any)=proceed()
            override fun proceedWith(thisObject: Any,args: Array<Any?>)=proceed()
        }
        val hook=hooks[method]
        if(hook==null) original() else hook.intercept(chain)
    }
    private fun withHooks(block: (Map<Executable,XposedInterface.Hooker>)->Unit) {
        val previous=XposedHelpers.moduleInst
        val installed=LinkedHashMap<Executable,XposedInterface.Hooker>()
        try {
            XposedHelpers.moduleInst=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(XposedInterface::class.java)) { _,call,args ->
                if(call.name!="hook") return@newProxyInstance null
                val target=args!![0] as Executable
                object: XposedInterface.HookBuilder {
                    override fun setPriority(priority: Int): XposedInterface.HookBuilder=this
                    override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder=this
                    override fun setId(id: String?): XposedInterface.HookBuilder=error("No new ID")
                    override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                        assertNull(installed.put(target,hooker))
                        return Proxy.newProxyInstance(javaClass.classLoader,arrayOf(XposedInterface.HookHandle::class.java)) { _,_,_ -> null } as XposedInterface.HookHandle
                    }
                }
            } as XposedInterface
            val param=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(PackageReadyParam::class.java)) { _,call,_ ->
                if(call.name=="getClassLoader") javaClass.classLoader else null
            } as PackageReadyParam
            SystemUIControlCenterHooks.CollapseCCAfterClickHook(param)
            block(installed)
        } finally { XposedHelpers.moduleInst=previous }
    }
}
