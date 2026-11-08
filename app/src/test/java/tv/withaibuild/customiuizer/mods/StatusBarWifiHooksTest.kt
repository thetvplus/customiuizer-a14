package tv.withaibuild.customiuizer.mods

import android.view.View
import com.android.systemui.statusbar.StatusBarWifiView
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy.WifiIconState
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.*
import tv.withaibuild.customiuizer.mods.utils.feature.SystemUiFeatures

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
class StatusBarWifiHooksTest {
    private val savedPrefs=MainModule.mPrefs.getAll()
    private val savedModule=XposedHelpers.moduleInst
    private val savedMode=SystemStatusBarIconHooks.wifiStandard
    private val hooks=ArrayList<XposedInterface.Hooker>()
    private val target=StatusBarWifiView::class.java.getDeclaredMethod("applyWifiState",WifiIconState::class.java)
    private val keys=setOf("system_networkindicator_wifi","system_statusbaricons_wifistandard")
    private val classes=ArrayList<Class<*>>()
    private var installs=0
    private var installFailure: Throwable?=null

    @Before fun setup() {
        FeatureInstallStateTestAccess.clear()
        XposedHelpers.moduleInst=proxy(XposedInterface::class.java) { name,args -> when(name) {
            "hook" -> builder(args!![0] as Method)
            else -> throw AssertionError(name)
        } }
        classes.add(target.declaringClass)
    }
    @After fun cleanup() {
        classes.forEach { XposedHelpers.removeAdditionalStaticField(it,"a14.wifi.native.update") }
        FeatureInstallStateTestAccess.clear()
        MainModule.mPrefs.replaceSnapshot(savedPrefs)
        org.robolectric.util.ReflectionHelpers.setStaticField(SystemStatusBarIconHooks::class.java,"wifiStandard",savedMode)
        XposedHelpers.moduleInst=savedModule
    }
    private fun wifiView()=StatusBarWifiView().apply {
        mWifiActivityView=ActivityView()
    }
    private class ActivityView: android.widget.ImageView(org.robolectric.RuntimeEnvironment.getApplication()) {
        var writes=0
        override fun setVisibility(visibility: Int) { writes++;super.setVisibility(visibility) }
    }
    private fun state(code: Int=6,shown: Boolean=false)=WifiIconState().apply {
        wifiStandard=code;showWifiStandard=shown
    }
    private fun register(activity: Boolean,mode: Int): List<FeatureInstallResult> {
        MainModule.mPrefs.replaceSnapshot(mapOf("system_networkindicator_wifi" to activity,
            "system_statusbaricons_wifistandard" to mode.toString()))
        val param=proxy(PackageReadyParam::class.java) { name,_ -> when(name) {
            "getClassLoader" -> target.declaringClass.classLoader
            "getPackageName" -> "com.android.systemui"
            else -> throw AssertionError(name)
        } }
        val registry=FeatureInstallRegistry()
        SystemUiFeatures.all(param,MainModule.mPrefs).filter { it.preferenceKey in keys }.forEach(registry::register)
        return registry.installAll(FeatureTarget.SYSTEM_UI,InstallPhase.PACKAGE_READY,MainModule.mPrefs,true)
    }
    @Test fun enabledCombinationsInstallOnlyTheirNecessaryNativeEntry() {
        assertEquals(listOf(FeatureInstallResult.SKIPPED,FeatureInstallResult.SKIPPED),register(false,1))
        assertEquals(0,installs)
        for((activity,mode) in listOf(true to 1,false to 2,true to 2)) {
            hooks.clear();installs=0
            XposedHelpers.removeAdditionalStaticField(target.declaringClass,"a14.wifi.native.update")
            FeatureInstallStateTestAccess.clear()
            assertEquals(listOf(if(activity) FeatureInstallResult.INSTALLED else FeatureInstallResult.SKIPPED,
                if(mode>1) FeatureInstallResult.INSTALLED else FeatureInstallResult.SKIPPED),register(activity,mode))
            assertEquals(1,installs)
            val view=wifiView();val state=state()
            val chain=Chain(view,state)
            assertSame(chain.result,chain.invoke())
            assertEquals(mode==2,chain.nativeStandard)
            assertEquals(if(activity) View.INVISIBLE else View.VISIBLE,view.mWifiActivityView!!.visibility)
            assertEquals(1,chain.nativeCalls)
            assertEquals(0,chain.arrays)
            assertEquals(1,chain.interceptions)
        }
    }
    @Test fun currentStandardModesAndNullStateKeepNativeControl() {
        register(true,2)
        val view=wifiView()
        for((mode,code,initial,want) in listOf(
            listOf(1,6,true,true),listOf(1,6,false,false),listOf(2,6,false,true),
            listOf(2,0,true,false),listOf(3,6,true,false))) {
            org.robolectric.util.ReflectionHelpers.setStaticField(SystemStatusBarIconHooks::class.java,"wifiStandard",mode)
            val state=state(code as Int,initial as Boolean)
            val chain=Chain(view,state)
            chain.invoke()
            assertEquals(want,chain.nativeStandard)
            assertEquals(1,chain.nativeCalls)
        }
        val cleared=Chain(view,null);cleared.invoke()
        assertEquals(1,cleared.nativeCalls)
        assertEquals(View.INVISIBLE,view.mWifiActivityView!!.visibility)
    }
    @Test fun ordinaryNativeFailureKeepsItsIdentityAndActivityAfterPhase() {
        register(true,2)
        val failure=IllegalStateException("native")
        val view=wifiView();val chain=Chain(view,state(),failure)
        assertSame(failure,runCatching { chain.invoke() }.exceptionOrNull())
        assertEquals(View.INVISIBLE,view.mWifiActivityView!!.visibility)
        assertEquals(1,chain.nativeCalls)
    }
    @Test fun bothEffectsPreserveTheNativeBodyResultAndArgumentIdentity() {
        register(true,2)
        val view=wifiView();val state=state()
        val chain=Chain(view,state)
        assertSame(chain.result,chain.invoke())
        assertTrue(chain.nativeStandard==true)
        assertEquals(View.INVISIBLE,view.mWifiActivityView!!.visibility)
        assertEquals(1,chain.nativeCalls)
        assertSame(state,chain.nativeArgument)
        assertEquals(0,chain.arrays)
    }
    @Test fun anIndependentAfterObserverKeepsItsNativePhase() {
        register(true,1)
        var seen: Boolean?=null
        var visibility: Int?=null
        hooks.add(object: HookerClassHelper.MethodHook() {
            override fun after(callback: HookerClassHelper.AfterHookCallback) {
                seen=(callback.getArg(0) as WifiIconState).showWifiStandard
                visibility=((callback.getThisObject() as StatusBarWifiView).mWifiActivityView)!!.visibility
            }
        })
        register(true,2)
        val view=wifiView();val chain=Chain(view,state())
        chain.invoke()
        assertTrue(seen==true)
        assertEquals(View.VISIBLE,visibility)
        assertEquals(View.INVISIBLE,view.mWifiActivityView!!.visibility)
        assertEquals(1,chain.nativeCalls)
    }
    @Test fun unchangedNativeUpdatesDoNotResubmitTheHiddenView() {
        register(true,2)
        val view=wifiView();val activity=view.mWifiActivityView as ActivityView
        activity.visibility=View.INVISIBLE;activity.writes=0
        repeat(100) { Chain(view,state(shown=true),nativeWritesActivity=false).invoke() }
        assertEquals(0,activity.writes)
    }
    @Test fun aLostActivityViewCannotBreakTheOtherPatchOrNativeReturn() {
        register(true,2)
        val view=wifiView();view.mWifiActivityView=null
        val chain=Chain(view,state(),nativeWritesActivity=false)
        assertSame(chain.result,chain.invoke())
        assertTrue(chain.nativeStandard==true)
        assertEquals(1,chain.nativeCalls)
    }
    @Test fun fatalNativeFailureEscapesWithoutRunningTheAfterPhase() {
        register(true,2)
        val failure=OutOfMemoryError("fatal")
        val view=wifiView();val chain=Chain(view,state(),failure)
        assertSame(failure,runCatching { chain.invoke() }.exceptionOrNull())
        assertEquals(View.VISIBLE,view.mWifiActivityView!!.visibility)
        assertEquals(1,chain.nativeCalls)
    }
    @Test fun fieldCapabilitiesFailIndependently() {
        val method=NoStandard::class.java.getDeclaredMethod("applyWifiState",Any::class.java)
        classes.add(method.declaringClass)
        assertEquals(FeatureInstallResult.FAILED_PERMANENT,StatusBarWifiHooks.install(method,true))
        assertTrue(StatusBarWifiHooks.install(method,false).isActive)
        val method2=NoActivity::class.java.getDeclaredMethod("applyWifiState",WifiIconState::class.java)
        classes.add(method2.declaringClass)
        assertEquals(FeatureInstallResult.FAILED_PERMANENT,StatusBarWifiHooks.install(method2,false))
        assertTrue(StatusBarWifiHooks.install(method2,true).isActive)
        assertEquals(2,installs)
    }
    @Test fun unconfirmedInstallationRemainsPassiveAndCannotBeInstalledAgain() {
        installFailure=IllegalStateException("framework")
        assertTrue(register(true,2).all { !it.isActive })
        assertEquals(1,installs)
        val view=wifiView();val chain=Chain(view,state())
        chain.invoke()
        assertEquals(false,chain.nativeStandard)
        assertEquals(View.VISIBLE,view.mWifiActivityView!!.visibility)
        assertEquals(1,chain.nativeCalls)
    }
    @Test fun aMissingStateInOneLoaderCannotPoisonTheSameViewNameInAnother() {
        val name=target.declaringClass.name
        val bytes=target.declaringClass.classLoader!!.getResourceAsStream(name.replace('.','/')+".class")!!.use { it.readBytes() }
        fun loader(missing: Boolean)=object: ClassLoader(target.declaringClass.classLoader) {
            override fun loadClass(requested: String,resolve: Boolean): Class<*> {
                if(missing && requested==WifiIconState::class.java.name) throw ClassNotFoundException(requested)
                if(requested!=name) return super.loadClass(requested,resolve)
                return findLoadedClass(requested) ?: defineClass(requested,bytes,0,bytes.size).also(classes::add)
            }
        }
        val missing=loader(true);val complete=loader(false)
        assertEquals(FeatureInstallResult.FAILED_PERMANENT,StatusBarWifiHooks.install(missing,false))
        assertTrue(StatusBarWifiHooks.install(complete,false).isActive)
        assertEquals(FeatureInstallResult.ALREADY_INSTALLED,StatusBarWifiHooks.install(complete,false))
        assertNotSame(missing.loadClass(name),complete.loadClass(name))
        assertEquals(1,installs)
    }
    @Test fun delegatedOwnersUseTheirOwnStateLoaderAndReuseOneEntry() {
        fun requester(block: Boolean)=object: ClassLoader(target.declaringClass.classLoader) {
            override fun loadClass(name: String,resolve: Boolean): Class<*> {
                if(block && name==WifiIconState::class.java.name) throw ClassNotFoundException(name)
                return super.loadClass(name,resolve)
            }
        }
        assertTrue(StatusBarWifiHooks.install(requester(true),false).isActive)
        assertEquals(FeatureInstallResult.ALREADY_INSTALLED,StatusBarWifiHooks.install(requester(false),false))
        assertEquals(1,installs)
    }
    class NoStandard {
        @JvmField val mWifiActivityView=View(org.robolectric.RuntimeEnvironment.getApplication())
        fun applyWifiState(state: Any?)=Unit
    }
    class NoActivity { fun applyWifiState(state: WifiIconState?)=Unit }
    private fun builder(method: Method): XposedInterface.HookBuilder = proxy(XposedInterface.HookBuilder::class.java) { name,args ->
        when(name) {
            "setPriority" -> { assertEquals(XposedInterface.PRIORITY_DEFAULT,args!![0]); builder(method) }
            "setExceptionMode" -> { assertEquals(XposedInterface.ExceptionMode.PASSTHROUGH,args!![0]); builder(method) }
            "intercept" -> {
                installs++;hooks.add(args!![0] as XposedInterface.Hooker)
                installFailure?.let { throw it }
                proxy(XposedInterface.HookHandle::class.java) { member,_ -> when(member) {
                    "getExecutable" -> method
                    "unhook" -> null
                    else -> throw AssertionError(member)
                } }
            }
            else -> throw AssertionError(name)
        }
    }
    private fun <T> proxy(type: Class<T>,body: (String,Array<out Any?>?)->Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { _,method,args -> body(method.name,args) })!!
    private inner class Chain(val view: StatusBarWifiView,val state: WifiIconState?,val failure: Throwable?=null,
        val index: Int=0,val root: Chain?=null,val nativeWritesActivity: Boolean=true): XposedInterface.Chain {
        val counters get()=root ?: this
        val result=Any()
        var arrays=0;var nativeCalls=0;var interceptions=0;var nativeStandard: Boolean?=null
        var nativeArgument: WifiIconState?=null
        fun invoke(): Any? {
            if(index<hooks.size) {
                counters.interceptions++
                return hooks[index].intercept(this)
            }
            counters.nativeCalls++;counters.nativeStandard=state?.showWifiStandard;counters.nativeArgument=state
            if(nativeWritesActivity) view.mWifiActivityView!!.visibility=View.VISIBLE
            failure?.let { throw it }
            return counters.result
        }
        override fun getExecutable()=target
        override fun getThisObject(): Any=view
        override fun getArgs(): List<Any?> { counters.arrays++;return listOf(state) }
        override fun getArg(index: Int): Any?=state
        override fun proceed(): Any?=Chain(view,state,failure,index+1,counters,nativeWritesActivity).invoke()
        override fun proceed(args: Array<Any?>): Any? {
            counters.arrays++
            return Chain(view,args[0] as WifiIconState?,failure,index+1,counters,nativeWritesActivity).invoke()
        }
        override fun proceedWith(receiver: Any): Any?=throw AssertionError("receiver changed")
        override fun proceedWith(receiver: Any,args: Array<Any?>): Any?=throw AssertionError("receiver changed")
    }
}
