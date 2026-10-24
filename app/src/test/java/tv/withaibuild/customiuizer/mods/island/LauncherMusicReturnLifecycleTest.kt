package tv.withaibuild.customiuizer.mods.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.PrefMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherMusicReturnLifecycleTest {
    private class ContextSpy : ContextWrapper(RuntimeEnvironment.getApplication()) {
        val business = mutableSetOf<BroadcastReceiver>()
        var removals = 0
        override fun getApplicationContext(): Context = this
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, scheduler: Handler?, flags: Int): Intent? {
            if (filter?.hasAction(IslandMusicReturnSignal.STATE) == true) { business.add(receiver!!); return null }
            return super.registerReceiver(receiver, filter, permission, scheduler, flags)
        }
        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            if (business.remove(receiver)) removals++ else super.unregisterReceiver(receiver)
        }
        override fun sendBroadcast(intent: Intent, permission: String?, options: android.os.Bundle?) {}
    }
    private fun owner(context: Context, prefs: PrefMap): Any {
        val access = LauncherMusicReturnAccess(LauncherMusicReturnAccessTest.Nav::class.java,
            LauncherMusicReturnAccessTest.Element::class.java, LauncherMusicReturnAccessTest.Params::class.java,
            LauncherMusicReturnAccessTest.Targets::class.java, LauncherMusicReturnAccessTest.Remote::class.java,
            LauncherMusicReturnAccessTest.DeviceConfig::class.java)
        val type = Class.forName("${LauncherMusicReturnHooks::class.java.name}\$Owner")
        return type.declaredConstructors.single().apply { isAccessible = true }.newInstance(View(context), prefs, access)
    }
    private fun dispose(owner: Any) { owner.javaClass.getDeclaredMethod("dispose").apply { isAccessible = true }.invoke(owner) }

    @Test fun disablingAndDetachingReleaseTheReceiverAndScopeWithoutDroppingFrameworkDeliveries() {
        val context = ContextSpy()
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3"); put("system_island_media", true)
            put("system_island_media_return", true); put("system_island_media_return_apps", setOf("chosen.music"))
        }
        val owner = owner(context, prefs)
        try {
            assertEquals(1, context.business.size)
            val oldScope = ReflectionHelpers.getField<CoroutineScope>(owner, "scope")
            val observer = ReflectionHelpers.getField<ModuleHelper.PreferenceObserver>(owner, "observer")
            prefs.put("system_island_media_return", false); observer.onChange("system_island_media_return")
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(context.business.isEmpty())
            assertTrue(oldScope.coroutineContext[Job]!!.isCancelled)
            assertNull(ReflectionHelpers.getField<Any?>(owner, "scope"))
            assertNull(ReflectionHelpers.getField<Any?>(owner, "snapshot"))
            prefs.put("system_island_media_return", true); observer.onChange("system_island_media_return")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, context.business.size)
            assertNotSame(oldScope, ReflectionHelpers.getField<CoroutineScope>(owner, "scope"))
            val handler = ReflectionHelpers.getField<Handler>(owner, "handler")
            var deliveries = 0
            handler.post { deliveries++ } // Framework broadcast completion shares this handler.
            dispose(owner); dispose(owner)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, deliveries)
            assertTrue(context.business.isEmpty()); assertEquals(2, context.removals)
            assertNull(ReflectionHelpers.getField<Any?>(owner, "scope"))
        } finally { dispose(owner) }
    }

    @Test fun anInactiveInstalledOwnerCreatesNoBusinessReceiverOrScope() {
        val context = ContextSpy()
        val owner = owner(context, PrefMap())
        try {
            assertTrue(context.business.isEmpty())
            assertNull(ReflectionHelpers.getField<Any?>(owner, "scope"))
        } finally { dispose(owner) }
    }
}
