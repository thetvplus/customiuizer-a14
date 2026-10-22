package tv.withaibuild.customiuizer.utils

import android.content.Context
import android.os.RemoteException
import io.github.libxposed.service.RemotePreferences
import io.github.libxposed.service.XposedService
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PreferenceRestartSyncTest {
    @Test fun restartCannotOvertakeTheMirrorQueueAndSeesTheLatestDockValues() {
        val commits = AtomicInteger()
        val remote = remote(commits)
        val local = RuntimeEnvironment.getApplication().getSharedPreferences("restart-sync", Context.MODE_PRIVATE)
        local.edit().clear().putInt("pref_key_launcher_dock_background_opacity", 55).commit()
        XposedServiceManagerFatalBoundaryTest.withMirrorPreferences(local, remote) { _, _, _ ->
            val dispatcher = XposedServiceManager::class.java.getDeclaredField("mirrorDispatcher")
                .apply { isAccessible = true }.get(null) as CoroutineDispatcher
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val blocker = CoroutineScope(dispatcher).launch {
                entered.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
            }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                runBlocking {
                    val result = async(Dispatchers.Default) { XposedServiceManager.synchronizeBeforeRestart() }
                    // The old restart path had no barrier and would read the old remote snapshot.
                    assertFalse(result.isCompleted)
                    assertFalse(remote.contains("pref_key_launcher_dock_background_opacity"))
                    release.countDown()
                    assertTrue(result.await())
                    assertEquals(55, remote.getInt("pref_key_launcher_dock_background_opacity", -1))
                    assertEquals(1, commits.get())
                    assertTrue(XposedServiceManager.synchronizeBeforeRestart())
                    assertEquals("a confirmed unchanged snapshot needs no second commit", 1, commits.get())
                }
            } finally {
                release.countDown()
                runBlocking { blocker.join() }
            }
        }
    }

    @Test fun rejectedCommitAndItsOptimisticSdkCacheCannotAuthorizeRestart() {
        val commits = AtomicInteger()
        val remote = remote(commits, reject = true)
        val local = RuntimeEnvironment.getApplication().getSharedPreferences("restart-reject", Context.MODE_PRIVATE)
        local.edit().clear().putInt("pref_key_launcher_dock_background_radius", 18).commit()
        XposedServiceManagerFatalBoundaryTest.withMirrorPreferences(local, remote) { state, _, _ ->
            runBlocking {
                assertFalse(XposedServiceManager.synchronizeBeforeRestart())
                assertEquals(18, remote.getInt("pref_key_launcher_dock_background_radius", -1))
                assertFalse(XposedServiceManager.synchronizeBeforeRestart())
                assertTrue(state.hasUndeliveredChanges)
                assertEquals(1, commits.get())
            }
        }
    }

    @Test fun disconnectedGenerationDoesNotClaimThatSettingsWereDelivered() {
        val remote = remote(AtomicInteger())
        val local = RuntimeEnvironment.getApplication().getSharedPreferences("restart-unbound", Context.MODE_PRIVATE)
        XposedServiceManagerFatalBoundaryTest.withMirrorPreferences(local, remote) { state, _, _ ->
            state.onUnbind()
            runBlocking { assertFalse(XposedServiceManager.synchronizeBeforeRestart()) }
            assertTrue(state.hasUndeliveredChanges)
        }
    }

    private fun remote(commits: AtomicInteger, reject: Boolean = false): RemotePreferences {
        val binderType = Class.forName("io.github.libxposed.service.IXposedService")
        val binder = Proxy.newProxyInstance(binderType.classLoader, arrayOf(binderType)) { _, method, _ ->
            check(method.name == "updateRemotePreferences") { "Unexpected Binder call: ${method.name}" }
            commits.incrementAndGet()
            if (reject) throw RemoteException("test transport failure")
            null
        }
        val service = XposedService::class.java.getDeclaredConstructor(binderType).apply { isAccessible = true }
            .newInstance(binder)
        val remote = RemotePreferences::class.java.getDeclaredConstructor(XposedService::class.java, String::class.java)
            .apply { isAccessible = true }.newInstance(service, "restart-test")
        RemotePreferences::class.java.getDeclaredField("mMap").apply { isAccessible = true }
            .set(remote, linkedMapOf<String, Any>())
        return remote
    }
}
