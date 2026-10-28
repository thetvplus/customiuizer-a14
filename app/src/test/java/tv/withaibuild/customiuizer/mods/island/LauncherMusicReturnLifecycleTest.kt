package tv.withaibuild.customiuizer.mods.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.graphics.RectF
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
        return type.declaredConstructors.single().apply { isAccessible = true }.newInstance(View(context), prefs, access, null)
    }
    private fun dispose(owner: Any) { owner.javaClass.getDeclaredMethod("dispose").apply { isAccessible = true }.invoke(owner) }

    @Test fun repeatedNativeClosesWithoutNewTouchCommitIndependentlyAndOldCallbacksCannotStealTheNextReturn() {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3"); put("system_island_media", true)
            put("system_island_media_return", true); put("system_island_media_return_apps", setOf("chosen.music"))
        }
        val owner = owner(ContextSpy(), prefs)
        try {
            val epoch = ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicLong>(owner, "gesture")
            val active = ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicLong>(owner, "activeAttempt")
            val attemptType = Class.forName("${LauncherMusicReturnHooks::class.java.name}\$Attempt")
            val make = attemptType.declaredConstructors.single().apply { isAccessible = true }
            val commit = owner.javaClass.getDeclaredMethod("committed", attemptType,
                Any::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            val frame = IslandMusicReturnSignal.Frame("chosen.music", UserHandle.getUserHandleForUid(1000), 280f, 15f, 800f, 125f, 1080, 2400)
            val snapshot = IslandMusicReturnSignal.Snapshot(frame, SystemClock.uptimeMillis(), 1)
            val params = LauncherMusicReturnAccessTest.Params(null, null, frame.rectangle(), 0f, 55f, 1f, 1f,
                LauncherMusicReturnAccessTest.Type.CLOSE_TO_HOME, true, true, false, null, null, null,
                true, 0, 0, true, 0, false, false)
            val element = LauncherMusicReturnAccessTest.Element().apply {
                currentRectFParams = params; running = true; mAnim.targetRect = RectF(frame.rectangle())
            }
            var stale: Any? = null
            repeat(48) { index ->
                val sequence = 100L + index
                active.set(sequence)
                val attempt = make.newInstance(params, snapshot, epoch.get(), sequence, .4f)
                stale?.let { commit.invoke(owner, it, element, true) }
                commit.invoke(owner, attempt, element, false)
                assertEquals(sequence, ReflectionHelpers.getField<Long>(owner, "committedAttempt"))
                commit.invoke(owner, attempt, element, true)
                assertEquals(sequence, ReflectionHelpers.getField<Long>(owner, "committedAttempt"))
                stale = attempt
            }
            val invalidate = owner.javaClass.getDeclaredMethod("invalidateAttempt").apply { isAccessible = true }
            invalidate.invoke(owner)
            commit.invoke(owner, stale, element, true)
            assertEquals(147L, ReflectionHelpers.getField<Long>(owner, "committedAttempt"))
            assertEquals(0L, active.get())
        } finally { dispose(owner) }
    }

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
