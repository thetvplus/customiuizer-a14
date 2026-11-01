package tv.withaibuild.customiuizer.mods

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.view.View
import com.android.wm.shell.pip.PipTaskOrganizer
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Exercises the broadcast payload and lifecycle entry points present in both fuxi samples. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScreenshotVisibilityLifecycleTest {
    private val previousModule = XposedHelpers.moduleInst
    private val context = RegistrationContext()

    @After
    fun cleanUp() {
        ModuleHelper.unregisterModuleReceiver("pipScreenshotReceiver")
        XposedHelpers.moduleInst = previousModule
    }

    @Test
    fun repeatedStartsPreserveTheVisibilityBeforeTheFirstStart() {
        for (initial in listOf(View.VISIBLE, View.INVISIBLE, View.GONE)) {
            val view = View(context).apply { visibility = initial }
            val receiver = bind(view)
            receive(receiver, finished = false)
            receive(receiver, finished = false)
            assertEquals(View.INVISIBLE, view.visibility)
            receive(receiver, finished = true)
            assertEquals("duplicate starts must not overwrite the saved state", initial, view.visibility)
        }
    }

    @Test
    fun finishWithoutAnObservedStartMustNotRevealANewNavigationView() {
        val view = View(context).apply { visibility = View.GONE }
        val receiver = bind(view)
        receive(receiver, finished = true)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun detachRestoresTheViewAndRejectsLateCallbacksUntilReattached() {
        val view = View(context)
        val receiver = bind(view)
        val listener = receiver as View.OnAttachStateChangeListener
        receive(receiver, finished = false)
        listener.onViewDetachedFromWindow(view)
        assertEquals("a missed finish while detached must not leave the view hidden", View.VISIBLE, view.visibility)
        assertEquals(0, context.receivers.size)

        receive(receiver, finished = false)
        assertEquals("callbacks for the detached owner must do nothing", View.VISIBLE, view.visibility)
        listener.onViewAttachedToWindow(view)
        listener.onViewAttachedToWindow(view)
        assertEquals(1, context.receivers.size)
        receive(receiver, finished = false)
        receive(receiver, finished = true)
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun statusBarStillBecomesVisibleAfterItsOwnScreenshotCycle() {
        val view = View(context).apply { visibility = View.GONE }
        val receiver = bind(view, restorePrevious = false)
        receive(receiver, finished = false)
        receive(receiver, finished = true)
        assertEquals(View.VISIBLE, view.visibility)
    }

    @Test
    fun aLaterHostVisibilityChangeIsPreservedOnFinishAndDetach() {
        val view = View(context)
        val receiver = bind(view)
        receive(receiver, finished = false)
        view.visibility = View.GONE
        receive(receiver, finished = true)
        assertEquals(View.GONE, view.visibility)

        view.visibility = View.VISIBLE
        receive(receiver, finished = false)
        view.visibility = View.GONE
        (receiver as View.OnAttachStateChangeListener).onViewDetachedFromWindow(view)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun pipRegistrationRetriesAfterFailureAndStopsAfterSuccess() {
        var installed: MethodHook? = null
        val builder = object : XposedInterface.HookBuilder {
            override fun setPriority(priority: Int): XposedInterface.HookBuilder = this
            override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = this
            override fun setId(id: String?): XposedInterface.HookBuilder = this
            override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                installed = hooker as MethodHook
                return proxy(XposedInterface.HookHandle::class.java) { _, _ -> null }
            }
        }
        XposedHelpers.moduleInst = proxy(XposedInterface::class.java) { name, _ ->
            check(name == "hook") { name }
            builder
        }
        val ready = proxy(PackageReadyParam::class.java) { name, _ ->
            check(name == "getClassLoader") { name }
            PipTaskOrganizer::class.java.classLoader
        }
        SystemUIScreenshotHooks.TempHideOverlaySystemUIHook(ready)
        assertNotNull(installed)
        var owner = PipTaskOrganizer(context)
        val chain = proxy(XposedInterface.Chain::class.java) { name, _ ->
            check(name == "getThisObject") { name }
            owner
        }
        fun appeared() = installed!!.afterHook(AfterHookCallback(BeforeHookCallback(chain), null, null))

        context.failRegistration = true
        appeared()
        context.failRegistration = false
        appeared()
        appeared()
        assertEquals("one failed attempt followed by exactly one successful registration", 2, context.registerCalls)
        assertEquals(1, context.receivers.size)
        val previous = owner
        owner = PipTaskOrganizer(context)
        appeared()
        assertEquals("replacement borrows the same single receiver", 2, context.registerCalls)
        val reference = org.robolectric.util.ReflectionHelpers.getField<java.lang.ref.WeakReference<Any>>(installed, "owner")
        assertSame(owner, reference.get())
        assertFalse(installed!!.javaClass.declaredFields.any { field ->
            field.isAccessible = true
            field.get(installed) === previous || field.get(installed) === owner
        })
    }

    private fun bind(view: View, restorePrevious: Boolean = true): BroadcastReceiver {
        val type = Class.forName("tv.withaibuild.customiuizer.mods.SystemUIScreenshotHooks\$ScreenshotVisibilityReceiver")
        val constructor = type.getDeclaredConstructor(View::class.java, Boolean::class.javaPrimitiveType).apply {
            isAccessible = true
        }
        val receiver = constructor.newInstance(view, restorePrevious) as BroadcastReceiver
        type.getDeclaredMethod("bind").apply { isAccessible = true }.invoke(receiver)
        return receiver
    }

    private fun receive(receiver: BroadcastReceiver, finished: Boolean) {
        receiver.onReceive(context, Intent("miui.intent.TAKE_SCREENSHOT").putExtra("IsFinished", finished))
    }

    private fun <T> proxy(type: Class<T>, call: (String, Array<out Any?>?) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            call(method.name, args)
        })

    private class RegistrationContext : ContextWrapper(RuntimeEnvironment.getApplication()) {
        val receivers = ArrayList<BroadcastReceiver>()
        var failRegistration = false
        var registerCalls = 0
        override fun getApplicationContext(): Context = this
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? =
            registerReceiver(receiver, filter, null, null, flags)
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?,
            scheduler: Handler?, flags: Int): Intent? {
            assertEquals("native screenshot broadcasts require their sender's signature permission",
                "android.permission.READ_FRAME_BUFFER", permission)
            assertEquals(Context.RECEIVER_EXPORTED, flags)
            registerCalls++
            if (failRegistration) throw IllegalStateException("injected registration failure")
            receiver?.let { receivers.add(it) }
            return null
        }
        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            receivers.remove(receiver)
        }
    }
}
