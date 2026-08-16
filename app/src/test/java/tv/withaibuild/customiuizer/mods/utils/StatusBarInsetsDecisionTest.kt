package tv.withaibuild.customiuizer.mods.utils

import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.util.DisplayMetrics
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks as Insets
import tv.withaibuild.customiuizer.mods.statusbarheight.InsetsSourceCapability
import tv.withaibuild.customiuizer.mods.statusbarheight.InsetsTypeEncoding
import tv.withaibuild.customiuizer.mods.statusbarheight.InsetsTypeInfo
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Executable

class StatusBarInsetsDecisionTest {

    @After
    fun tearDown() {
        StatusBarInsetsTestAccess.resetState()
        StatusBarInsetsTestAccess.resetConfig()
    }

    @Test
    fun modernPublicStatusTypeHits() {
        configureHeight(110, 160) // -> 110 px
        val chain = fakeChain(source(type = 1), fakeRect(bottom = 104))

        callback(modernPublicInfo()).intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertTrue(chain.calledWithArgs)
        assertEquals(1, chain.lastArgs!!.size)
        assertEquals(110, (chain.lastArgs!![0] as Rect).bottom)
    }

    @Test
    fun modernNavigationDoesNotHit() {
        configureHeight(110, 160)
        val chain = fakeChain(source(type = 2), fakeRect(bottom = 104))

        callback(modernPublicInfo()).intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun modernDisplayCutoutDoesNotHit() {
        configureHeight(110, 160)
        val chain = fakeChain(source(type = 128), fakeRect(bottom = 104))

        callback(modernPublicInfo()).intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun legacyInternalStatusType0Hits() {
        configureHeight(110, 160)
        val chain = fakeChain(source(type = 0), fakeRect(bottom = 104))

        callback(legacyInternalInfo()).intercept(chain)

        assertTrue(chain.calledWithArgs)
    }

    @Test
    fun legacyInternalNavigationType1DoesNotHit() {
        configureHeight(110, 160)
        val chain = fakeChain(source(type = 1), fakeRect(bottom = 104))

        callback(legacyInternalInfo()).intercept(chain)

        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun legacyNavigationOldFrameEmptyTop0StillNotMisclassified() {
        // A navigation source with an empty old frame whose top==0 must not be treated as
        // a status bar. Type encoding is the single source of truth.
        configureHeight(110, 160)
        val chain = fakeChain(source(type = 1, frame = Rect()), fakeRect(bottom = 104))

        callback(legacyInternalInfo()).intercept(chain)

        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun unsupportedEncodingProceedsWithoutReadingType() {
        configureHeight(110, 160)
        val src = source(type = 0)
        val chain = fakeChain(src, fakeRect(bottom = 104))

        callback(unsupportedInfo()).intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
        assertEquals("unsupported encoding must not reflect at all", 0, src.getTypeCalls)
    }

    @Test
    fun disabledDoesNotReadSourceType() {
        configureHeight(11, 160) // disabled
        val src = source(type = 1)
        val chain = fakeChain(src, fakeRect(bottom = 104))

        callback(modernPublicInfo()).intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
        assertEquals("disabled must be checked before any reflection", 0, src.getTypeCalls)
    }

    @Test
    fun nonStatusSourceReadsTypeExactlyOnce() {
        configureHeight(110, 160)
        val src = source(type = 2)
        val chain = fakeChain(src, fakeRect(bottom = 104))

        callback(modernPublicInfo()).intercept(chain)

        assertEquals(1, src.getTypeCalls)
    }

    @Test
    fun cachedTypeFieldRemovesReflection() {
        configureHeight(110, 160)
        val src = FieldBackedSource(1)
        val chain = fakeChain(src, fakeRect(bottom = 104))
        val typeField = FieldBackedSource::class.java.getDeclaredField("mType").also { it.isAccessible = true }

        Insets.SetFrameCallback(
            InsetsSourceCapability(
                sourceClass = null,
                setFrameOneArg = true,
                setFrameFourArg = true,
                typeInfo = modernPublicInfo(),
                typeField = typeField,
                getTypeMethod = null,
                getIdMethod = null,
                getFrameMethod = null,
            )
        ).intercept(chain)

        assertTrue(chain.calledWithArgs)
        assertEquals(0, src.getTypeCalls)
    }

    @Test
    fun rectInputIsNotModified() {
        configureHeight(110, 160)
        val original = fakeRect(bottom = 104)
        val chain = fakeChain(source(type = 1), original)

        callback(modernPublicInfo()).intercept(chain)

        val adjusted = chain.lastArgs!![0] as Rect
        assertNotSame(original, adjusted)
        assertEquals(104, original.bottom)
        assertEquals(110, adjusted.bottom)
        assertEquals(original.left, adjusted.left)
        assertEquals(original.top, adjusted.top)
        assertEquals(original.right, adjusted.right)
    }

    @Test
    fun fourArgOrderIsCorrect() {
        configureHeight(110, 160)
        val chain = fakeChain(source(type = 0), 0, 0, 1080, 104)

        callback(legacyInternalInfo()).intercept(chain)

        val args = chain.lastArgs!!
        assertEquals(4, args.size)
        assertEquals(0, args[0])
        assertEquals(0, args[1])
        assertEquals(1080, args[2])
        assertEquals(110, args[3])
    }

    @Test
    fun preprocessingReflectionFailureProceedsOnce() {
        configureHeight(110, 160)
        val chain = fakeChain(throwingSource(), fakeRect(bottom = 104))

        callback(modernPublicInfo()).intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
        assertEquals(1, Insets.loggedRejection.size)
    }

    @Test
    fun argumentAccessFailureProceedsOnce() {
        // getArg(0) succeeds, the following argument access throws. The original method must
        // still run exactly once with the untouched arguments.
        configureHeight(40, 440) // -> 110 px
        val chain = fakeChain(source(type = 0), 0, 0, 1080, 0, throwFromSecondArg = true)

        callback(legacyInternalInfo()).intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun originalMethodNormalExceptionPropagatesAndProceedsOnce() {
        configureHeight(40, 440) // -> 110 px
        val chain = fakeChain(source(type = 1), fakeRect(bottom = 0), proceedThrow = RuntimeException("setFrame failed"))

        try {
            callback(modernPublicInfo()).intercept(chain)
            assertFalse("Expected proceed exception to propagate", true)
        } catch (t: Throwable) {
            assertTrue(t is RuntimeException)
        }

        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun originalMethodFatalPropagatesAndProceedsOnce() {
        configureHeight(40, 440) // -> 110 px
        val error = OutOfMemoryError("setFrame OOM")
        val chain = fakeChain(source(type = 1), fakeRect(bottom = 0), proceedThrow = error)

        try {
            callback(modernPublicInfo()).intercept(chain)
            assertFalse("Expected OutOfMemoryError to propagate", true)
        } catch (t: Throwable) {
            assertSame(error, t)
        }

        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun changedFalseProceedsOnce() {
        // originalTop=0, originalBottom=104, configuredPx=104 -> newBottom=104, no change
        configureHeight(104, 160) // -> 104 px
        val chain = fakeChain(source(type = 1), fakeRect(bottom = 104))

        callback(modernPublicInfo()).intercept(chain)

        assertEquals(1, chain.proceedCount)
        assertFalse(chain.calledWithArgs)
    }

    @Test
    fun statusSourceLogsOncePerGeneration() {
        configureHeight(40, 160) // -> 40 px

        val hook = Insets.SetFrameCallback(callbackCaps(modernPublicInfo(), getId = true))

        repeat(40) { index ->
            hook.intercept(fakeChain(source(type = 1, id = index), fakeRect(bottom = 104)))
        }

        assertEquals(1, Insets.loggedCritical.size)

        StatusBarHeightConfig.reconfigure(PrefMap().apply { put("system_statusbarheight", 44) })
        hook.intercept(fakeChain(source(type = 1, id = 0), fakeRect(bottom = 104)))

        assertEquals(2, Insets.loggedCritical.size)
    }

    @Test
    fun nonStatusSourcesProduceNoDiagnostics() {
        configureHeight(40, 160)

        val hook = Insets.SetFrameCallback(callbackCaps(modernPublicInfo(), getId = true))

        repeat(40) { index ->
            hook.intercept(fakeChain(source(type = 2, id = index), fakeRect(bottom = 104)))
        }

        assertEquals(0, Insets.loggedRejection.size)
        assertEquals(0, Insets.loggedCritical.size)
    }

    private fun callback(info: InsetsTypeInfo) =
        Insets.SetFrameCallback(callbackCaps(info, getId = false))

    private fun callbackCaps(info: InsetsTypeInfo, getId: Boolean): InsetsSourceCapability {
        return InsetsSourceCapability(
            sourceClass = null,
            setFrameOneArg = true,
            setFrameFourArg = true,
            typeInfo = info,
            typeField = null,
            getTypeMethod = FakeSource::class.java.getDeclaredMethod("getType").also { it.isAccessible = true },
            getIdMethod = if (getId) FakeSource::class.java.getDeclaredMethod("getId").also { it.isAccessible = true } else null,
            getFrameMethod = null,
        )
    }

    private fun modernPublicInfo() = InsetsTypeInfo(
        InsetsTypeEncoding.MODERN_PUBLIC,
        statusBarType = 1,
        navigationType = 2,
        displayCutoutType = 128,
    )

    private fun legacyInternalInfo() = InsetsTypeInfo(
        InsetsTypeEncoding.LEGACY_INTERNAL,
        statusBarType = 0,
        navigationType = 1,
        displayCutoutType = -1,
    )

    private fun unsupportedInfo() = InsetsTypeInfo(
        InsetsTypeEncoding.UNSUPPORTED,
        statusBarType = -1,
        navigationType = -1,
        displayCutoutType = -1,
    )

    private fun source(type: Int, id: Int = 0, frame: Rect = Rect()) = FakeSource(type, id, frame)

    private fun throwingSource() = ThrowingFakeSource()

    private fun fakeRect(
        left: Int = 0,
        top: Int = 0,
        right: Int = 1080,
        bottom: Int = 104,
    ): Rect = Rect().apply {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    private fun configureHeight(rawDp: Int, densityDpi: Int) {
        StatusBarHeightConfig.configure(PrefMap().apply { put("system_statusbarheight", rawDp) }, fakeResources(densityDpi))
    }

    private fun fakeChain(source: Any, firstArg: Rect, proceedThrow: Throwable? = null): FakeChain {
        return FakeChain(
            source = source,
            args = arrayOf<Any?>(firstArg),
            proceedThrow = proceedThrow,
        )
    }

    private fun fakeChain(
        source: Any,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        throwFromSecondArg: Boolean = false,
        proceedThrow: Throwable? = null,
    ): FakeChain {
        return FakeChain(
            source = source,
            args = arrayOf<Any?>(left, top, right, bottom),
            throwFromSecondArg = throwFromSecondArg,
            proceedThrow = proceedThrow,
        )
    }

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

    class FakeSource(private val _type: Int, private val _id: Int, private val _frame: Rect) {
        var getTypeCalls = 0
            private set

        fun getType(): Int {
            getTypeCalls++
            return _type
        }

        fun getId(): Int = _id
        fun getFrame(): Rect = _frame
    }

    /** Source whose type is readable through a cached `mType` field, like the real `InsetsSource`. */
    class FieldBackedSource(@JvmField val mType: Int) {
        var getTypeCalls = 0
            private set

        fun getType(): Int {
            getTypeCalls++
            return mType
        }
    }

    class ThrowingFakeSource {
        fun getType(): Int = throw RuntimeException("getType failed")
    }

    class FakeChain(
        val source: Any,
        val args: Array<Any?>,
        val throwFromSecondArg: Boolean = false,
        val proceedThrow: Throwable? = null,
    ) : XposedInterface.Chain {

        var proceedCount = 0
            private set

        var calledWithArgs = false
            private set

        var lastArgs: Array<Any>? = null
            private set

        private var argAccessCount = 0

        override fun getExecutable(): Executable = error("not used in test")
        override fun getThisObject(): Any? = source
        override fun getArgs(): List<Any?> = args.toList()

        override fun getArg(index: Int): Any? {
            if (throwFromSecondArg) {
                argAccessCount++
                if (argAccessCount >= 2) throw RuntimeException("getArg failure")
            }
            return args[index]
        }

        override fun proceed(): Any? {
            proceedCount++
            calledWithArgs = false
            proceedThrow?.let { throw it }
            return null
        }

        override fun proceed(p0: Array<Any>): Any? {
            proceedCount++
            calledWithArgs = true
            lastArgs = p0
            proceedThrow?.let { throw it }
            return null
        }

        override fun proceedWith(p0: Any): Any? = error("not used in test")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
    }
}
