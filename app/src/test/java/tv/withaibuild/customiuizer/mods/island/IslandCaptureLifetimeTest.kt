package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.SurfaceControl
import android.widget.FrameLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import org.robolectric.shadows.ShadowSurfaceControl
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, shadows = [CaptureSurfaceShadow::class])
@OptIn(ExperimentalCoroutinesApi::class)
class IslandCaptureLifetimeTest {
    @Test @Config(shadows = [InstantTransactionShadow::class])
    fun instantHidesOnlyItsPageWithoutCaptureAndCancellationRestoresItsReleaseOpacity() {
        InstantTransactionShadow.writes.clear()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val root = object : FrameLayout(activity.get()) { override fun isHardwareAccelerated() = true }
        activity.get().setContentView(root); root.layout(0, 0, 360, 800)
        val surface = ReflectionHelpers.newInstance(SurfaceControl::class.java)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var captures = 0
        val element = Any(); val copy = Any()
        val owner = LauncherWindowAbsorption(object : LauncherWindowAbsorption.NativeAccess {
            override fun current(element: Any, copy: Any) = true
            override fun translucent(copy: Any) = false
            override fun surface(element: Any, copy: Any) = surface
            override fun root() = root
            override fun capture(surface: SurfaceControl, width: Int, height: Int): Bitmap? { captures++; return null }
        }, Handler(Looper.getMainLooper()))
        try {
            assertNotNull(owner.begin(scope, element, copy, IslandMusicReturnSignal.Frame(
                "chosen.media", android.os.Process.myUserHandle(), 100f, 10f, 260f, 50f, 360, 800), instant = true))
            assertNull(ReflectionHelpers.getField<Any?>(owner, "captureJob"))
            val session = ReflectionHelpers.getField<Any>(owner, "session")
            assertNull(ReflectionHelpers.getField<Any?>(session, "drawing"))
            owner.frame(element, RectF(0f, 0f, 360f, 800f), 0f, 20f, .7f)
            owner.frame(element, RectF(30f, 50f, 330f, 600f), .5f, 20f, .7f)
            assertEquals(0, captures); assertTrue(owner.hides(element))
            assertEquals(listOf(0f), InstantTransactionShadow.writes)
            owner.cancel()
            assertFalse(owner.hides(element))
            assertEquals(listOf(0f, .7f), InstantTransactionShadow.writes)
        } finally { owner.cancel(); scope.cancel(); activity.pause().stop().destroy() }
    }

    @Test fun queuedCaptureIsCanceledWhenNativeTravelPassesItsTakeoverWindow() = exercise(blockCapture = true)
    @Test fun failedOptionalTextureKeepsNativeProgressAliveUntilCompletion() = exercise(blockCapture = false)
    @Test fun anInFlightLateTextureIsRecycledWithoutRevokingNativeProgress() = exercise(false, true)

    private fun exercise(blockCapture: Boolean, returnLate: Boolean = false) {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val root = object : FrameLayout(activity.get()) { override fun isHardwareAccelerated() = true }
        activity.get().setContentView(root)
        root.layout(0, 0, 360, 800)
        shadowOf(activity.get().application).grantPermissions("android.permission.READ_FRAME_BUFFER")
        val surface = ReflectionHelpers.newInstance(SurfaceControl::class.java)
        val captures = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val bitmap = if (returnLate) Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) else null
        val element = Any()
        val copy = Any()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owner = LauncherWindowAbsorption(object : LauncherWindowAbsorption.NativeAccess {
            override fun current(element: Any, copy: Any) = true
            override fun translucent(copy: Any) = false
            override fun surface(element: Any, copy: Any) = surface
            override fun root() = root
            override fun capture(surface: SurfaceControl, width: Int, height: Int): Bitmap? {
                captures.incrementAndGet()
                if (returnLate) {
                    started.countDown()
                    check(release.await(3, TimeUnit.SECONDS))
                }
                return bitmap
            }
        }, Handler(Looper.getMainLooper()))
        val mutex = ReflectionHelpers.getField<Mutex>(owner, "captureMutex")
        if (blockCapture) assertTrue(mutex.tryLock())
        try {
            val progress = owner.begin(scope, element, copy, IslandMusicReturnSignal.Frame(
                "chosen.music", android.os.Process.myUserHandle(), 100f, 10f, 260f, 50f, 360, 800))!!
            val job = ReflectionHelpers.getField<Job>(owner, "captureJob")
            owner.frame(element, RectF(0f, 0f, 360f, 800f), 0f, 20f, 1f)
            if (returnLate) assertTrue(started.await(3, TimeUnit.SECONDS))
            if (blockCapture || returnLate) {
                owner.frame(element, RectF(30f, 50f, 330f, 600f), .8f, 20f, 1f)
                if (blockCapture) mutex.unlock() else release.countDown()
            }
            runBlocking { withTimeout(3000) { job.join() } }
            assertEquals(if (blockCapture) 0 else 1, captures.get())
            if (returnLate) assertTrue(bitmap!!.isRecycled)
            assertTrue("Optional capture cannot revoke the admitted return", owner.owns(element))
            val buffer = ReflectionHelpers.getField<ByteBuffer?>(progress, "buffer")!!
            owner.frame(element, RectF(40f, 60f, 320f, 500f), .9f, 20f, 1f)
            assertEquals(900000, buffer.order(ByteOrder.nativeOrder()).getInt(0))
            owner.end(element, completed = true)
            assertFalse(owner.owns(element))
            assertNull(ReflectionHelpers.getField<ByteBuffer?>(progress, "buffer"))
        } finally {
            release.countDown()
            owner.cancel(); scope.cancel(); Dispatchers.resetMain()
            activity.pause().stop().destroy()
        }
    }
}

@Implements(SurfaceControl::class)
class CaptureSurfaceShadow : ShadowSurfaceControl() {
    @Implementation fun isValid() = true
}

@Implements(SurfaceControl.Transaction::class)
class InstantTransactionShadow {
    @org.robolectric.annotation.RealObject private lateinit var transaction: SurfaceControl.Transaction
    @Implementation fun __constructor__() {}
    @Implementation fun setAlpha(surface: SurfaceControl, alpha: Float): SurfaceControl.Transaction {
        writes.add(alpha); return transaction
    }
    @Implementation fun apply() {}
    @Implementation fun close() {}
    companion object { val writes = mutableListOf<Float>() }
}
