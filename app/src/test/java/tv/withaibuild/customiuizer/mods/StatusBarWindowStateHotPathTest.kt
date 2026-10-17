package tv.withaibuild.customiuizer.mods

import android.graphics.Rect
import android.util.DisplayMetrics
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.mods.statusbarheight.DecorInsetsCapability
import tv.withaibuild.customiuizer.mods.statusbarheight.InsetsSourceCapability
import tv.withaibuild.customiuizer.mods.statusbarheight.InsetsTypeEncoding
import tv.withaibuild.customiuizer.mods.statusbarheight.InsetsTypeInfo
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightAbi
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightEffect
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightRefreshCapability
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightResolver
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightRuntime
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.StatusBarInsetsTestAccess
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Executable

private fun StatusBarHeightRuntime.latestRef(): WeakReference<Any>? = latestKnownStatusBar

private fun StatusBarHeightRuntime.knownSnapshot(): Array<WeakReference<Any>?> = knownOwners.copyOf()

/**
 * Behavioral tests for the WMS hot paths in [SystemStatusBarInsetsHooks]:
 * [onLayoutWindowLw] and [onSetFrames].
 */
class StatusBarWindowStateHotPathTest {

    @Before
    fun setUp() {
        StatusBarInsetsTestAccess.resetState()
        StatusBarInsetsTestAccess.resetConfig()
        setStatusBarHeightEffect(makeTestEffect())
    }

    @After
    fun tearDown() {
        StatusBarInsetsTestAccess.resetState()
        StatusBarInsetsTestAccess.resetConfig()
    }

    @Test
    fun disabledUnknownWindowStateDoesNotDiscoverStatusBar() {
        val win = FakeWindowState(statusType = true)

        val chain = FakeChain(argList = listOf(win))

        val result = SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue(result == null || result == Unit)
        assertFalse("unknown WindowState must not be discovered in disabled path", isKnownStatusBarWindow(win))
    }

    @Test
    fun disabledKnownStatusBarRestoresOriginalHeight() {
        val win = FakeWindowState(statusType = true)
        win.mAttrs.height = 80
        rememberStatusBarWindow(win)
        XposedHelpers.setAdditionalInstanceField(win, "customiuizer_originalStatusBarHeight", 48)

        val chain = FakeChain(argList = listOf(win))

        val result = SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals("known status bar height must be restored when disabled", 48, win.mAttrs.height)
        assertTrue(result == null || result == Unit)
    }

    @Test
    fun enabledUnknownWindowStateDiscoversAndAppliesHeight() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true)
        win.mAttrs.height = 80

        assertTrue("fake must be recognized as status bar", SystemStatusBarInsetsHooks.isStatusBarWindow(win))

        val chain = FakeChain(argList = listOf(win))

        val result = SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue("unknown status bar must be discovered in enabled path", isKnownStatusBarWindow(win))
        assertEquals(44, win.mAttrs.height)
        assertTrue(result == null || result == Unit)
    }

    @Test
    fun setFramesNonKnownWindowStateProceedsImmediately() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true)
        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)

        val chain = FakeChain(target = win, argList = listOf(clientFrames))

        val result = SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(80, clientFrames.frame.bottom)
        assertTrue(result == null || result == Unit)
    }

    @Test
    fun setFramesKnownWindowStateModifiesFrameBottom() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true)
        rememberStatusBarWindow(win)
        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)

        val chain = FakeChain(target = win, argList = listOf(clientFrames))

        val result = SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(44, clientFrames.frame.bottom)
        assertTrue(result == null || result == Unit)
    }

    @Test
    fun layoutWindowLwProceedsExactlyOnce() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true)

        val chain = FakeChain(argList = listOf(win))

        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals("chain.proceed must be called exactly once", 1, chain.proceedCount)
    }

    @Test
    fun layoutWindowLw_usesSingleConfigSnapshotEvenIfHelperMutatesConfig() {
        configureHeight(40)
        val win = FakeWindowState(statusType = true, onGetDisplayMetrics = {
            StatusBarHeightConfig.reconfigure(PrefMap().apply { put("system_statusbarheight", 44) })
        })

        val chain = FakeChain(argList = listOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(40, win.mAttrs.height)
        assertEquals(44, StatusBarHeightConfig.configuredDp)
        assertEquals(44, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun layoutWindowLw_densityChange_recomputesAndUsesNewPxInSameCallback() {
        configureHeight(40)
        val metrics = DisplayMetrics().apply {
            densityDpi = 320
            density = 2.0f
        }
        val win = FakeWindowState(statusType = true).apply { mDisplayMetrics = metrics }

        val chain = FakeChain(argList = listOf(win))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(80, win.mAttrs.height) // 40dp @ 320dpi
        assertEquals(320, StatusBarHeightConfig.densityDpi)
        assertEquals(80, StatusBarHeightConfig.configuredPx)
        assertEquals(40, StatusBarHeightConfig.configuredDp)
    }

    @Test
    fun layoutWindowLw_preProceedMetricsRuntimeException_proceedsOriginalOnce() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true).apply {
            mAttrs.height = 80
            onGetDisplayMetrics = { throw RuntimeException("metrics failed") }
        }

        val chain = FakeChain(argList = listOf(win))
        val result = SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(80, win.mAttrs.height)
        assertTrue(result == null || result == Unit)
    }

    @Test
    fun layoutWindowLw_preProceedMetricsOom_propagatesSameIdentityWithoutProceed() {
        configureHeight(44)
        val oom = OutOfMemoryError("metrics OOM")
        val win = FakeWindowState(statusType = true).apply {
            mAttrs.height = 80
            onGetDisplayMetrics = { throw oom }
        }

        val chain = FakeChain(argList = listOf(win))

        val thrown = try {
            SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)
            null
        } catch (t: Throwable) {
            t
        }

        assertSame(oom, thrown)
        assertEquals(0, chain.proceedCount)
        assertEquals(80, win.mAttrs.height)
    }

    @Test
    fun setFramesProceedsExactlyOnce() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true)
        rememberStatusBarWindow(win)
        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)

        val chain = FakeChain(target = win, argList = listOf(clientFrames))

        SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals("chain.proceed must be called exactly once", 1, chain.proceedCount)
    }

    @Test
    fun layoutWindowLwFatalPropagatesAndProceedsOnce() {
        val error = OutOfMemoryError("layout OOM")
        val win = FakeWindowState(statusType = true)
        val chain = FakeChain(argList = listOf(win), proceedThrow = error)

        try {
            SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)
            assertTrue("fatal error must propagate", false)
        } catch (t: Throwable) {
            assertSame(error, t)
        }

        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun knownEnabledStatusBarUpdatesLatestWithoutRebuildingWeakRef() {
        configureHeight(44)
        val a = FakeWindowState(statusType = true)
        val b = FakeWindowState(statusType = true)
        val runtime = statusBarHeightRuntime()

        runtime.rememberStatusBar(a)
        val refA = runtime.rememberStatusBar(a)
        runtime.rememberStatusBar(b)

        // Establish latest = b, then layout a again.
        runtime.markLatestIfKnown(b)
        assertSame(b, runtime.latestRef()?.get())

        val snapshotBefore = runtime.knownSnapshot()
        val refAIndex = snapshotBefore.indexOfFirst { it === refA }
        assertTrue(refAIndex >= 0)

        val chain = FakeChain(argList = listOf(a))
        SystemStatusBarInsetsHooks.onLayoutWindowLw(chain)

        assertSame(a, runtime.latestRef()?.get())
        assertSame(refA, runtime.latestRef())
        assertSame(refA, snapshotBefore[refAIndex])
    }

    @Test
    fun sourceGuard_usesSinglePassOwnerLookup() {
        val lines = facadeSourceLines()

        val isStatusBarCalls = callCount(lines, "isStatusBarWindow(")
        val isKnownCalls = callCount(lines, "isKnownStatusBarWindow(")
        val markLatestCalls = callCount(lines, "markLatestIfKnownStatusBar(")

        assertEquals(
            "isStatusBarWindow must have one production call (onLayoutWindowLw) plus one test/compat wrapper call",
            2,
            isStatusBarCalls
        )
        assertEquals(
            "H3 (setFrames) must be the only isKnownStatusBarWindow caller",
            1,
            isKnownCalls
        )
        assertEquals(
            "markLatestIfKnownStatusBar must only be called from disabled path and isStatusBarWindow",
            2,
            markLatestCalls
        )
    }

    @Test
    fun setFramesFatalPropagatesAndProceedsOnce() {
        configureHeight(44)
        val error = OutOfMemoryError("setFrames OOM")
        val win = FakeWindowState(statusType = true)
        rememberStatusBarWindow(win)
        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)
        val chain = FakeChain(target = win, argList = listOf(clientFrames), proceedThrow = error)

        try {
            SystemStatusBarInsetsHooks.onSetFrames(chain)
            assertTrue("fatal error must propagate", false)
        } catch (t: Throwable) {
            assertSame(error, t)
        }

        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun setFrames_usesSingleConfigSnapshotEvenIfHelperMutatesConfig() {
        configureHeight(40)
        val win = FakeWindowState(statusType = true, onGetDisplayId = {
            StatusBarHeightConfig.reconfigure(PrefMap().apply { put("system_statusbarheight", 44) })
        })
        rememberStatusBarWindow(win)

        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)

        val chain = FakeChain(target = win, argList = listOf(clientFrames))
        SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(40, clientFrames.frame.bottom) // 40dp @ 160dpi, not 44
        assertEquals(44, StatusBarHeightConfig.configuredDp)
        assertEquals(44, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun setFrames_secondaryDisplay_usesLocalPxAndDoesNotPolluteGlobal() {
        val globalMetrics = DisplayMetrics().apply {
            densityDpi = 469
            density = 2.93125f
        }
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            metrics = globalMetrics,
        )
        val win = FakeWindowState(statusType = true).apply {
            mDisplayId = 1
            mDisplayMetrics = DisplayMetrics().apply {
                densityDpi = 200
                density = 1.25f
            }
        }
        rememberStatusBarWindow(win)

        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)

        val chain = FakeChain(target = win, argList = listOf(clientFrames))
        SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(55, clientFrames.frame.bottom) // 44dp @ 200dpi
        assertEquals(469, StatusBarHeightConfig.densityDpi)
        assertEquals(129, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun setFrames_preProceedMetricsRuntimeException_proceedsOriginalOnce() {
        configureHeight(44)
        val win = FakeWindowState(statusType = true).apply {
            mDisplayId = 1
            onGetDisplayMetrics = { throw RuntimeException("metrics failed") }
        }
        rememberStatusBarWindow(win)

        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)

        val chain = FakeChain(target = win, argList = listOf(clientFrames))
        val result = SystemStatusBarInsetsHooks.onSetFrames(chain)

        assertEquals(1, chain.proceedCount)
        assertEquals(80, clientFrames.frame.bottom)
        assertTrue(result == null || result == Unit)
    }

    @Test
    fun setFrames_preProceedMetricsOom_propagatesSameIdentityWithoutProceed() {
        configureHeight(44)
        val oom = OutOfMemoryError("metrics OOM")
        val win = FakeWindowState(statusType = true).apply {
            mDisplayId = 1
            onGetDisplayMetrics = { throw oom }
        }
        rememberStatusBarWindow(win)

        val clientFrames = FakeClientWindowFrames()
        setRect(clientFrames.frame, 0, 0, 1080, 80)

        val chain = FakeChain(target = win, argList = listOf(clientFrames))

        val thrown = try {
            SystemStatusBarInsetsHooks.onSetFrames(chain)
            null
        } catch (t: Throwable) {
            t
        }

        assertSame(oom, thrown)
        assertEquals(0, chain.proceedCount)
        assertEquals(80, clientFrames.frame.bottom)
    }

    private fun configureHeight(dp: Int) {
        StatusBarHeightConfig.configure(PrefMap().apply { put("system_statusbarheight", dp) })
    }

    private fun facadeSourceLines(): List<String> {
        val root = java.lang.System.getProperty("user.dir") ?: ""
        val project = if (File(root, "app").isDirectory) root else File(root).parent
        val file = File(project, "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarInsetsHooks.kt")
        return if (file.exists()) file.readLines() else emptyList()
    }

    private fun callCount(lines: List<String>, call: String): Int {
        return lines.count { it.contains(call) && !it.contains("fun ") }
    }

    private fun setStatusBarHeightEffect(effect: StatusBarHeightEffect?) {
        val field = SystemStatusBarInsetsHooks::class.java.getDeclaredField("statusBarHeightEffect")
        field.isAccessible = true
        field.set(null, effect)
    }

    private fun makeTestEffect(): StatusBarHeightEffect {
        val wm = StatusBarHeightResolver.resolveWindowManagerClass(
            FakeWindowState::class.java,
            FakeLayoutParams::class.java,
        ).copy(
            clientWindowFramesClass = FakeClientWindowFrames::class.java,
            clientWindowFramesFrameField = FakeClientWindowFrames::class.java.getDeclaredField("frame").also { it.isAccessible = true },
        )
        val decor = DecorInsetsCapability(
            infoClass = null,
            updateMethod = null,
            displayContentClass = FakeDisplayContent::class.java,
            displayContentGetDisplayMetricsMethod = FakeDisplayContent::class.java.getDeclaredMethod("getDisplayMetrics").also { it.isAccessible = true },
            nonDecorInsetsField = null,
            nonDecorFrameField = null,
        )
        val insets = InsetsSourceCapability(
            sourceClass = null,
            setFrameOneArg = true,
            setFrameFourArg = true,
            typeInfo = InsetsTypeInfo(InsetsTypeEncoding.MODERN_PUBLIC, 1, 2, 128),
            typeField = null,
            getTypeMethod = null,
            getIdMethod = null,
            getFrameMethod = null,
        )
        return StatusBarHeightEffect(StatusBarHeightAbi(insets, wm, decor, StatusBarHeightRefreshCapability(null, null, null, null, null)))
    }

    private fun statusBarHeightRuntime(): StatusBarHeightRuntime {
        val field = SystemStatusBarInsetsHooks::class.java.getDeclaredField("statusBarHeightRuntime")
        field.isAccessible = true
        return field.get(null) as StatusBarHeightRuntime
    }

    private fun rememberStatusBarWindow(win: Any) {
        statusBarHeightRuntime().rememberStatusBar(win)
    }

    private fun isKnownStatusBarWindow(win: Any): Boolean {
        return statusBarHeightRuntime().isKnownStatusBar(win)
    }

    class FakeWindowState(
        statusType: Boolean = true,
        var onGetDisplayMetrics: (() -> Unit)? = null,
        var onGetDisplayId: (() -> Unit)? = null,
    ) {
        val mAttrs: FakeLayoutParams = FakeLayoutParams(
            type = if (statusType) TYPE_STATUS_BAR else 1,
            packageName = if (statusType) "com.android.systemui" else "com.example",
        )
        var mDisplayId = 0
        var mDisplayMetrics = DisplayMetrics().apply {
            densityDpi = 160
            density = 1.0f
        }
        var mDisplayContent = FakeDisplayContent(mDisplayMetrics)

        fun getDisplayId(): Int {
            onGetDisplayId?.invoke()
            return mDisplayId
        }
        fun getDisplayMetrics(): DisplayMetrics {
            onGetDisplayMetrics?.invoke()
            return mDisplayMetrics
        }

        override fun toString(): String = if (mAttrs.type == TYPE_STATUS_BAR) {
            "FakeWindowState{...StatusBar...}"
        } else {
            "FakeWindowState"
        }
    }

    class FakeDisplayContent(private val metrics: DisplayMetrics) {
        fun getDisplayMetrics(): DisplayMetrics = metrics
        fun getDisplayId(): Int = 0
    }

    class FakeLayoutParams(var type: Int = 0, var packageName: String = "", var height: Int = 0)

    class FakeClientWindowFrames(val frame: Rect = Rect())

    private fun setRect(rect: Rect, left: Int, top: Int, right: Int, bottom: Int) {
        rect.left = left
        rect.top = top
        rect.right = right
        rect.bottom = bottom
    }

    private class FakeChain(
        private val target: Any? = null,
        private val argList: List<Any?> = emptyList(),
        private val proceedThrow: Throwable? = null,
    ) : XposedInterface.Chain {

        var proceedCount = 0
            private set

        override fun getExecutable(): Executable = error("not used in test")
        override fun getThisObject(): Any? = target
        override fun getArgs(): List<Any?> = argList
        override fun getArg(index: Int): Any? = if (index in argList.indices) argList[index] else null

        override fun proceed(): Any? {
            proceedCount++
            proceedThrow?.let { throw it }
            return null
        }

        override fun proceed(p0: Array<Any>): Any? {
            proceedCount++
            proceedThrow?.let { throw it }
            return null
        }

        override fun proceedWith(p0: Any): Any? = error("not used in test")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
    }

    companion object {
        private const val TYPE_STATUS_BAR = 2000
    }
}
