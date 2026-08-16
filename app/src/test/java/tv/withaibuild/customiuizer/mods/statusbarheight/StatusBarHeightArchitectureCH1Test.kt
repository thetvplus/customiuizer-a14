package tv.withaibuild.customiuizer.mods.statusbarheight

import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.util.DisplayMetrics
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.StatusBarInsetsTestAccess
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Executable

class StatusBarHeightArchitectureCH1Test {

    @After
    fun tearDown() {
        StatusBarInsetsTestAccess.resetDiagnostics()
        StatusBarInsetsTestAccess.resetConfig()
    }

    @Test
    fun fieldPrimary_doesNotCallGetTypeMethod() {
        configureHeight(40, 160) // -> 40 px
        val src = DualBackedSource(1)
        val chain = fakeChain(src, fakeRect(bottom = 104))
        val typeField = DualBackedSource::class.java.getDeclaredField("mType").also { it.isAccessible = true }
        val getTypeMethod = DualBackedSource::class.java.getDeclaredMethod("getType").also { it.isAccessible = true }

        val capability = InsetsSourceCapability(
            sourceClass = null,
            setFrameOneArg = true,
            setFrameFourArg = true,
            typeInfo = modernPublicInfo(),
            typeField = typeField,
            getTypeMethod = getTypeMethod,
            getIdMethod = null,
            getFrameMethod = null,
        )

        SystemStatusBarInsetsHooks.SetFrameCallback(capability).intercept(chain)

        assertEquals(0, src.getTypeCalls)
        assertTrue(chain.calledWithArgs)
        assertEquals(40, (chain.lastArgs!![0] as Rect).bottom)
    }

    @Test
    fun methodFallback_usesGetTypeMethodWhenFieldIsAbsent() {
        configureHeight(40, 160)
        val src = MethodOnlySource(1)
        val chain = fakeChain(src, fakeRect(bottom = 104))
        val getTypeMethod = MethodOnlySource::class.java.getDeclaredMethod("getType").also { it.isAccessible = true }

        val capability = InsetsSourceCapability(
            sourceClass = null,
            setFrameOneArg = true,
            setFrameFourArg = true,
            typeInfo = modernPublicInfo(),
            typeField = null,
            getTypeMethod = getTypeMethod,
            getIdMethod = null,
            getFrameMethod = null,
        )

        SystemStatusBarInsetsHooks.SetFrameCallback(capability).intercept(chain)

        assertEquals(1, src.getTypeCalls)
        assertTrue(chain.calledWithArgs)
        assertEquals(40, (chain.lastArgs!![0] as Rect).bottom)
    }

    @Test
    fun noReader_proceedsOnceWithoutMutation() {
        configureHeight(40, 160)
        val src = MethodOnlySource(1)
        val chain = fakeChain(src, fakeRect(bottom = 104))

        val capability = InsetsSourceCapability(
            sourceClass = null,
            setFrameOneArg = true,
            setFrameFourArg = true,
            typeInfo = modernPublicInfo(),
            typeField = null,
            getTypeMethod = null,
            getIdMethod = null,
            getFrameMethod = null,
        )

        SystemStatusBarInsetsHooks.SetFrameCallback(capability).intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun getTypeMethodNonfatalFailure_proceedsOnceWithOriginalArgs() {
        configureHeight(40, 160)
        val chain = fakeChain(ThrowingMethodOnlySource(RuntimeException("getType failed")), fakeRect(bottom = 104))
        val getTypeMethod = ThrowingMethodOnlySource::class.java.getDeclaredMethod("getType").also { it.isAccessible = true }

        val capability = InsetsSourceCapability(
            sourceClass = null,
            setFrameOneArg = true,
            setFrameFourArg = true,
            typeInfo = modernPublicInfo(),
            typeField = null,
            getTypeMethod = getTypeMethod,
            getIdMethod = null,
            getFrameMethod = null,
        )

        SystemStatusBarInsetsHooks.SetFrameCallback(capability).intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun getTypeMethodFatalIdentity_propagatesOriginalFatalAndDoesNotProceed() {
        configureHeight(40, 160)
        val oom = OutOfMemoryError("getType OOM")
        val chain = fakeChain(ThrowingMethodOnlySource(oom), fakeRect(bottom = 104))
        val getTypeMethod = ThrowingMethodOnlySource::class.java.getDeclaredMethod("getType").also { it.isAccessible = true }

        val capability = InsetsSourceCapability(
            sourceClass = null,
            setFrameOneArg = true,
            setFrameFourArg = true,
            typeInfo = modernPublicInfo(),
            typeField = null,
            getTypeMethod = getTypeMethod,
            getIdMethod = null,
            getFrameMethod = null,
        )

        try {
            SystemStatusBarInsetsHooks.SetFrameCallback(capability).intercept(chain)
            assertFalse("Expected OutOfMemoryError to propagate", true)
        } catch (t: Throwable) {
            assertSame(oom, t)
        }

        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun singleSnapshot_callbackUsesEntryConfigEvenIfGetTypeReconfigures() {
        configureHeight(40, 160)
        val chain1 = fakeChain(ReconfiguringSource(44, 1), fakeRect(bottom = 104))
        val chain2 = fakeChain(ReconfiguringSource(44, 1), fakeRect(bottom = 104))
        val getTypeMethod = ReconfiguringSource::class.java.getDeclaredMethod("getType").also { it.isAccessible = true }

        val capability = InsetsSourceCapability(
            sourceClass = null,
            setFrameOneArg = true,
            setFrameFourArg = true,
            typeInfo = modernPublicInfo(),
            typeField = null,
            getTypeMethod = getTypeMethod,
            getIdMethod = null,
            getFrameMethod = null,
        )

        SystemStatusBarInsetsHooks.SetFrameCallback(capability).intercept(chain1)
        assertTrue(chain1.calledWithArgs)
        assertEquals(40, (chain1.lastArgs!![0] as Rect).bottom)

        SystemStatusBarInsetsHooks.SetFrameCallback(capability).intercept(chain2)
        assertTrue(chain2.calledWithArgs)
        assertEquals(44, (chain2.lastArgs!![0] as Rect).bottom)
    }

    private fun configureHeight(rawDp: Int, densityDpi: Int) {
        StatusBarHeightConfig.configure(PrefMap().apply { put("system_statusbarheight", rawDp) }, fakeResources(densityDpi))
    }

    private fun fakeRect(left: Int = 0, top: Int = 0, right: Int = 1080, bottom: Int = 104) =
        Rect().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }

    private fun fakeChain(source: Any, firstArg: Rect): FakeChain =
        FakeChain(source = source, args = arrayOf<Any?>(firstArg))

    private fun fakeResources(densityDpi: Int): Resources {
        val metrics = DisplayMetrics().apply { this.densityDpi = densityDpi }
        val constructor = AssetManager::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        val assetManager = constructor.newInstance()
        return FakeResources(assetManager, metrics)
    }

    private class FakeResources(assetManager: AssetManager, private val metrics: DisplayMetrics) :
        Resources(assetManager, metrics, Configuration()) {
        override fun getDisplayMetrics(): DisplayMetrics = metrics
    }

    private fun modernPublicInfo() = InsetsTypeInfo(
        InsetsTypeEncoding.MODERN_PUBLIC,
        statusBarType = 1,
        navigationType = 2,
        displayCutoutType = 128,
    )

    class DualBackedSource(@JvmField val mType: Int) {
        var getTypeCalls = 0
            private set

        fun getType(): Int {
            getTypeCalls++
            return mType
        }
    }

    class MethodOnlySource(private val _type: Int) {
        var getTypeCalls = 0
            private set

        fun getType(): Int {
            getTypeCalls++
            return _type
        }
    }

    class ThrowingMethodOnlySource(private val error: Throwable) {
        fun getType(): Int = throw error
    }

    class ReconfiguringSource(private val newRawDp: Int, private val _type: Int) {
        fun getType(): Int {
            StatusBarHeightConfig.reconfigure(PrefMap().apply { put("system_statusbarheight", newRawDp) })
            return _type
        }
    }

    class FakeChain(
        val source: Any,
        val args: Array<Any?>,
    ) : XposedInterface.Chain {

        var proceedCount = 0
            private set

        var calledWithArgs = false
            private set

        var lastArgs: Array<Any>? = null
            private set

        override fun getExecutable(): Executable = error("not used in test")
        override fun getThisObject(): Any? = source
        override fun getArgs(): List<Any?> = args.toList()
        override fun getArg(index: Int): Any? = args[index]

        override fun proceed(): Any? {
            proceedCount++
            calledWithArgs = false
            return null
        }

        override fun proceed(p0: Array<Any>): Any? {
            proceedCount++
            calledWithArgs = true
            lastArgs = p0
            return null
        }

        override fun proceedWith(p0: Any): Any? = error("not used in test")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
    }
}
