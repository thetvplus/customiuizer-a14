package tv.withaibuild.customiuizer.utils

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper

/** Tests the real View's asynchronous work without creating a native audio effect. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioVisualizerAsyncLifecycleTest {
    private val scheduler = TestCoroutineScheduler()
    private lateinit var view: AudioVisualizer

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        MainModule.mPrefs.clear()
    }

    @After
    fun tearDown() = runTest(StandardTestDispatcher(scheduler)) {
        if (::view.isInitialized) {
            view.dispose()
            (get("viewScope") as CoroutineScope).coroutineContext[Job]?.cancelAndJoin()
        }
        MainModule.mPrefs.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun intervalChangesWhileHiddenDoNotSchedulePeriodicWork() {
        create(AudioVisualizer.ColorMode.DYNAMIC, displaying = false)
        change("system_visualizer_dyntime", 3)
        scheduler.runCurrent()

        assertFalse("hidden views must not own a periodic job", job("randomizeColorJob")?.isActive == true)
        val color = get("mOpaqueColor")
        scheduler.advanceTimeBy(30_000)
        scheduler.runCurrent()
        assertEquals("virtual time must not produce hidden color updates", color, get("mOpaqueColor"))
    }

    @Test
    fun switchingToDynamicWhileVisibleStartsOnlyOnePeriodicJob() {
        create(AudioVisualizer.ColorMode.STATIC)
        change("system_visualizer_color", AudioVisualizer.ColorMode.DYNAMIC.ordinal.toString())
        scheduler.runCurrent()
        val timer = job("randomizeColorJob")
        assertTrue("switching modes must start dynamic colors immediately", timer?.isActive == true)

        repeat(16) { change("system_visualizer_color", AudioVisualizer.ColorMode.DYNAMIC.ordinal.toString()) }
        scheduler.runCurrent()
        assertSame("repeated notifications must keep one timer and its cadence", timer, job("randomizeColorJob"))
    }

    @Test
    fun switchingToStaticCancelsPeriodicWorkAndKeepsTheSelectedColor() {
        create(AudioVisualizer.ColorMode.DYNAMIC)
        change("system_visualizer_dyntime", 3)
        scheduler.runCurrent()
        val timer = job("randomizeColorJob")!!
        change("system_visualizer_colorval", Color.BLUE)
        change("system_visualizer_color", AudioVisualizer.ColorMode.STATIC.ordinal.toString())

        assertTrue("leaving dynamic mode must cancel its timer", timer.isCancelled)
        scheduler.advanceTimeBy(30_000)
        scheduler.runCurrent()
        assertEquals(Color.BLUE, get("mOpaqueColor"))
    }

    @Test
    fun stoppingDisplayCancelsPeriodicWork() {
        create(AudioVisualizer.ColorMode.DYNAMIC)
        change("system_visualizer_dyntime", 3)
        scheduler.runCurrent()
        val timer = job("randomizeColorJob")!!

        view.setPlaying(false)

        assertTrue(timer.isCancelled)
        scheduler.advanceTimeBy(30_000)
        scheduler.runCurrent()
        assertFalse(job("randomizeColorJob")?.isActive == true)
    }

    @Test
    fun removingArtCancelsTheQueuedPaletteBeforeItCanPublish() {
        create(AudioVisualizer.ColorMode.MATCH)
        view.updateMusicArt(bitmap())
        val palette = job("paletteGenerationJob")!!

        view.updateMusicArt(null)

        assertTrue("old artwork must lose publication rights when media clears", palette.isCancelled)
        assertNull(get("mProcessedArt"))
        scheduler.runCurrent()
        assertEquals(Color.WHITE, get("mOpaqueColor"))
    }

    @Test
    fun leavingMatchModeCancelsTheQueuedPalette() {
        create(AudioVisualizer.ColorMode.MATCH)
        view.updateMusicArt(bitmap())
        val palette = job("paletteGenerationJob")!!
        change("system_visualizer_colorval", Color.GREEN)

        change("system_visualizer_color", AudioVisualizer.ColorMode.STATIC.ordinal.toString())

        assertTrue("a stale palette must not overwrite a static color", palette.isCancelled)
        assertNull(get("mProcessedArt"))
        scheduler.runCurrent()
        assertEquals(Color.GREEN, get("mOpaqueColor"))
    }

    @Test
    fun hiddenViewsDeferPaletteWorkUntilTheyCanDisplayIt() {
        create(AudioVisualizer.ColorMode.MATCH, displaying = false)
        val art = bitmap()
        view.updateMusicArt(art)
        assertNull("hidden art must not start background extraction", job("paletteGenerationJob"))

        set("mDisplaying", true)
        view.updateMusicArt(art)
        assertTrue(job("paletteGenerationJob")?.isActive == true)
    }

    @Test
    fun aCancelledPaletteCanBeRetriedForTheSameBitmap() {
        create(AudioVisualizer.ColorMode.MATCH)
        val art = bitmap()
        view.updateMusicArt(art)
        val first = job("paletteGenerationJob")!!
        view.setPlaying(false)
        assertTrue("hiding must cancel color extraction", first.isCancelled)

        set("mDisplaying", true)
        view.updateMusicArt(art)
        val retry = job("paletteGenerationJob")
        assertNotSame(first, retry)
        assertTrue("the same source must remain retryable after cancellation", retry?.isActive == true)
    }

    @Test
    fun preferenceObserverMovesViewMutationToTheMainDispatcher() {
        create(AudioVisualizer.ColorMode.STATIC)
        view.setColor(Color.BLUE)
        MainModule.mPrefs.put("system_visualizer_colorval", Color.RED)
        val observer = get("preferenceObserver") as ModuleHelper.PreferenceObserver

        val listener = Thread { observer.onChange("system_visualizer_colorval") }
        listener.start()
        listener.join(5_000)
        assertFalse("preference listener must finish without waiting for Main", listener.isAlive)
        assertEquals("listener thread must not mutate paint or animation state", Color.BLUE, get("mOpaqueColor"))

        scheduler.runCurrent()
        assertEquals(Color.RED, get("mOpaqueColor"))
    }

    @Test
    fun disposedOwnersDropQueuedPreferenceChanges() {
        create(AudioVisualizer.ColorMode.STATIC)
        view.setColor(Color.BLUE)
        MainModule.mPrefs.put("system_visualizer_colorval", Color.RED)
        (get("preferenceObserver") as ModuleHelper.PreferenceObserver).onChange("system_visualizer_colorval")
        view.dispose()
        scheduler.runCurrent()

        assertEquals("released views must not receive queued changes", Color.BLUE, get("mOpaqueColor"))
    }

    private fun create(mode: AudioVisualizer.ColorMode, displaying: Boolean = true) {
        MainModule.mPrefs.put("system_visualizer_color", mode.ordinal.toString())
        view = AudioVisualizer(RuntimeEnvironment.getApplication())
        // Only emulate visibility; native Visualizer linking is outside this scheduler test.
        set("isMusicPlaying", true)
        set("mPlaying", displaying)
        set("mDisplaying", displaying)
        set("viewAttached", true)
    }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    private fun change(key: String, value: Any) {
        MainModule.mPrefs.put(key, value)
        AudioVisualizer::class.java.getDeclaredMethod("handlePreferenceChanged", String::class.java).apply {
            isAccessible = true
        }.invoke(view, key)
    }

    private fun job(name: String) = get(name) as Job?
    private fun get(name: String): Any? = field(name).get(view)
    private fun set(name: String, value: Any) = field(name).set(view, value)
    private fun field(name: String) = AudioVisualizer::class.java.getDeclaredField(name).apply { isAccessible = true }
}
