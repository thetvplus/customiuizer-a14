package tv.withaibuild.customiuizer.mods.island

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import com.android.deskclock.timer.TimerService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import tv.withaibuild.customiuizer.utils.PrefMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandNativeTimerLifecycleTest {
    private fun runWithClock(block: (Any, Any, TimerService) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).installPackage(android.content.pm.PackageInfo().apply {
            packageName = "com.android.systemui"
            applicationInfo = ApplicationInfo().apply { packageName = "com.android.systemui"; uid = 1000 }
        })
        val context = object : ContextWrapper(app) {
            override fun getPackageName() = IslandLiveKind.CLOCK_PACKAGE
        }
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3")
            put(IslandLiveKind.PREFERENCE, setOf("timer"))
        }
        val root = IslandNativeAppSource::class.java.name
        val installType = Class.forName("$root\$Installation")
        val installation = installType.declaredConstructors.single().apply { isAccessible = true }.newInstance()
        val clockType = Class.forName("$root\$Clock")
        val clock = clockType.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(javaClass.classLoader, prefs, installation)
        val publisherType = Class.forName("$root\$Publisher")
        val publisher = publisherType.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(context, prefs, {})
        val service = Robolectric.buildService(TimerService::class.java).create().get()
        ReflectionHelpers.setField(clock, "publisher", publisher)
        ReflectionHelpers.setField(clock, "timerOwner", java.lang.ref.WeakReference(service))
        try { block(clock, publisher, service) }
        finally {
            (publisher as AutoCloseable).close()
            service.onDestroy()
        }
    }

    private fun snapshot(clock: Any) { ReflectionHelpers.callInstanceMethod<Void>(clock, "snapshot") }
    private fun destroy(clock: Any, service: TimerService) {
        ReflectionHelpers.callInstanceMethod<Void>(clock, "timerDestroyed",
            ReflectionHelpers.ClassParameter.from(Any::class.java, service))
    }
    private fun timer(publisher: Any) = ReflectionHelpers.getField<Map<IslandLiveKind, IslandLiveActivity>>(
        publisher, "values")[IslandLiveKind.TIMER]

    @Test fun destroyedRunningOrPausedServiceCannotKeepPublishingItsOldClock() {
        for (state in 1..2) runWithClock { clock, publisher, service ->
            service.mTimer!!.state = state
            snapshot(clock)
            assertNotNull(timer(publisher))
            destroy(clock, service)
            assertNull("A live app-process token cannot keep a destroyed timer Service alive", timer(publisher))
            snapshot(clock)
            assertNull(timer(publisher))
        }
    }

    @Test fun anOldServiceDestructionCannotRetireTheCurrentTimer() = runWithClock { clock, publisher, service ->
        snapshot(clock)
        val old = Robolectric.buildService(TimerService::class.java).create().get()
        destroy(clock, old)
        assertNotNull(timer(publisher))
        old.onDestroy()
        destroy(clock, service)
    }

    @Test fun completionGraceAndAnAlreadyRingingAlertSurviveCountdownOwnerDestruction() = runWithClock { clock, publisher, service ->
        service.mTimer!!.deadline = System.currentTimeMillis()
        snapshot(clock)
        val ending = timer(publisher)
        assertNotNull(ending)
        service.mTimer = null
        destroy(clock, service)
        assertEquals(ending, timer(publisher))
        ReflectionHelpers.setField(clock, "ringing", IslandLiveKind.TIMER)
        snapshot(clock)
        assertTrue(timer(publisher)!!.alert)
    }
}
