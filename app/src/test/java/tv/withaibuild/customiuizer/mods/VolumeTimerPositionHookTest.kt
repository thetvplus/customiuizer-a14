package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VolumeTimerPositionHookTest {
    private val hook = SystemUIControlCenterHooks.VolumeTimerPositionHook()

    @Test
    fun aMissingWidthFieldUsesTheExistingNamedDimension() {
        val resources = TimerResources()
        val item = TimerItem(TimerContext(resources), segment = 2)
        val chain = Chain(item)
        assertEquals(51.0f, hook.intercept(chain))
        assertEquals(0, chain.proceedCount)
        assertEquals(listOf(WIDTH_ID), resources.dimensionReads)
        assertEquals(listOf(WIDTH_NAME, MARGIN_NAME), resources.identifierReads)
    }

    @Test
    fun missingFieldFallbackReadsTheCurrentResourceConfiguration() {
        val resources = TimerResources()
        val item = TimerItem(TimerContext(resources), segment = 10)
        assertEquals(221.0f, hook.intercept(Chain(item)))
        resources.dimensions[WIDTH_ID] = 69.0f
        resources.marginPixels = 13
        assertEquals(74.5f, hook.intercept(Chain(item)))
    }

    @Test
    fun anExistingWidthIsAResourceIdAndPreservesAllElevenPositions() {
        val resources = TimerResources()
        val item = TimerItemWithWidth(TimerContext(resources))
        val expected = listOf(8.5f, 29.75f, 51.0f, 72.25f, 93.5f, 114.75f,
            136.0f, 157.25f, 178.5f, 199.75f, 221.0f)
        expected.forEachIndexed { index, position ->
            item.mDeterminedSegment = index
            val chain = Chain(item, progress = 999)
            assertEquals(position, hook.intercept(chain))
            assertEquals(0, chain.proceedCount)
            assertEquals(index, item.mDeterminedSegment)
        }
        assertEquals(List(11) { WIDTH_ID }, resources.dimensionReads)
        assertFalse(resources.identifierReads.contains(WIDTH_NAME))
    }

    @Test
    fun theFourStreamFieldKeepsItsOwnWidthAndCurrentDimensions() {
        val resources = TimerResources()
        val item = TimerItemWithWidth(TimerContext(resources), FOUR_STREAM_ID, segment = 10)
        assertEquals(202.5f, hook.intercept(Chain(item)))
        resources.dimensions[FOUR_STREAM_ID] = 201.25f
        assertEquals(209.75f, hook.intercept(Chain(item)))
        assertEquals(listOf(FOUR_STREAM_ID, FOUR_STREAM_ID), resources.dimensionReads)
        assertFalse(resources.identifierReads.contains(WIDTH_NAME))
    }

    @Test
    fun aMissingNamedWidthKeepsNativeBehaviorWithoutDereferencingZero() {
        val resources = TimerResources().apply { widthNameMissing = true }
        val chain = Chain(TimerItem(TimerContext(resources)), nativeResult = -19.25f)
        assertEquals(-19.25f, hook.intercept(chain))
        assertEquals(1, chain.proceedCount)
        assertFalse(resources.dimensionReads.contains(0))
        assertFalse(resources.pixelReads.contains(0))
    }

    @Test
    fun aZeroWidthFieldKeepsNativeBehaviorWithoutGuessingAnotherLayout() {
        val resources = TimerResources()
        val chain = Chain(TimerItemWithWidth(TimerContext(resources), 0), nativeResult = 42.5f)
        assertEquals(42.5f, hook.intercept(chain))
        assertEquals(1, chain.proceedCount)
        assertFalse(resources.dimensionReads.contains(0))
        assertFalse(resources.identifierReads.contains(WIDTH_NAME))
    }

    @Test
    fun aMissingMarginKeepsNativeBehaviorWithoutDereferencingZero() {
        val resources = TimerResources().apply { marginNameMissing = true }
        val chain = Chain(TimerItemWithWidth(TimerContext(resources)), nativeResult = 8.25f)
        assertEquals(8.25f, hook.intercept(chain))
        assertEquals(1, chain.proceedCount)
        assertFalse(resources.pixelReads.contains(0))
    }

    @Test
    fun unavailableDimensionsRunTheNativeMethodOnceWithTheOriginalArgument() {
        for (failureAtMargin in listOf(false, true)) {
            val resources = TimerResources().apply {
                if (failureAtMargin) pixelFailure = Resources.NotFoundException("margin")
                else dimensionFailure = Resources.NotFoundException("width")
            }
            val chain = Chain(TimerItemWithWidth(TimerContext(resources)), progress = 713,
                nativeResult = 17.5f)
            assertEquals(17.5f, hook.intercept(chain))
            assertEquals(1, chain.proceedCount)
            assertEquals(713, chain.nativeProgress)
        }
    }

    @Test
    fun anInvalidNonzeroWidthDoesNotOverrideTheNativeLayoutChoice() {
        val resources = TimerResources()
        val chain = Chain(TimerItemWithWidth(TimerContext(resources), 12345), nativeResult = -5.0f)
        assertEquals(-5.0f, hook.intercept(chain))
        assertEquals(1, chain.proceedCount)
        assertFalse(resources.identifierReads.contains(WIDTH_NAME))
    }

    @Test
    fun anUnknownWidthFieldTypeKeepsNativeBehavior() {
        val resources = TimerResources()
        val item = TimerItemWithUnknownWidth(TimerContext(resources))
        val chain = Chain(item, nativeResult = 3.0f)
        assertEquals(3.0f, hook.intercept(chain))
        assertEquals(1, chain.proceedCount)
        assertFalse(resources.identifierReads.contains(WIDTH_NAME))
    }

    @Test
    fun aFailedLabelMeasurementKeepsNativeBehavior() {
        val item = TimerItemWithWidth(TimerContext(TimerResources())).apply {
            mTimerTime.failure = IllegalStateException("view unavailable")
        }
        val chain = Chain(item, nativeResult = 21.5f)
        assertEquals(21.5f, hook.intercept(chain))
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun ordinaryNativeFailuresRemainUnchangedAfterResourceFallback() {
        for (failure in listOf(IllegalStateException("native"),
            InvocationTargetException(IllegalArgumentException("native")))) {
            val resources = TimerResources().apply { marginNameMissing = true }
            val chain = Chain(TimerItemWithWidth(TimerContext(resources))) { throw failure }
            assertSame(failure, assertThrows(Throwable::class.java) { hook.intercept(chain) })
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test
    fun fatalResourceFailuresEscapeBeforeNativeFallback() {
        for (fatal in fatals()) {
            for (failure in listOf(fatal, InvocationTargetException(ExecutionException(fatal)))) {
                val resources = TimerResources().apply { dimensionFailure = failure }
                val chain = Chain(TimerItemWithWidth(TimerContext(resources)))
                assertSame(fatal, assertThrows(Throwable::class.java) { hook.intercept(chain) })
                assertEquals(0, chain.proceedCount)
            }
        }
    }

    @Test
    fun fatalLabelMeasurementFailuresKeepTheReflectionFatalBoundary() {
        for (fatal in fatals()) {
            val item = TimerItemWithWidth(TimerContext(TimerResources())).apply {
                mTimerTime.failure = ExecutionException(fatal)
            }
            val chain = Chain(item)
            assertSame(fatal, assertThrows(Throwable::class.java) { hook.intercept(chain) })
            assertEquals(0, chain.proceedCount)
        }
    }

    @Test
    fun fatalNativeFailuresAreUnwrappedAndPropagatedAfterResourceFallback() {
        for (fatal in fatals()) {
            for (failure in listOf(fatal, InvocationTargetException(ExecutionException(fatal)))) {
                val resources = TimerResources().apply { marginNameMissing = true }
                val chain = Chain(TimerItemWithWidth(TimerContext(resources))) { throw failure }
                assertSame(fatal, assertThrows(Throwable::class.java) { hook.intercept(chain) })
                assertEquals(1, chain.proceedCount)
            }
        }
    }

    private fun fatals(): List<Throwable> =
        listOf(OutOfMemoryError("timer"), StackOverflowError("timer"), ThreadDeath())

    private open class TimerItem(
        @JvmField val mContext: Context,
        segment: Int = 2,
    ) {
        @JvmField val mTimerTime = TimerLabel(15)
        @JvmField var mDeterminedSegment = segment
    }

    private class TimerItemWithWidth(context: Context, widthId: Int = WIDTH_ID, segment: Int = 2) :
        TimerItem(context, segment) {
        @JvmField val mTimerSeekbarWidth = widthId
    }

    private class TimerItemWithUnknownWidth(context: Context) : TimerItem(context) {
        @JvmField val mTimerSeekbarWidth = "unknown"
    }

    private class TimerLabel(private val pixels: Int) {
        var failure: Throwable? = null
        fun getWidth(): Int {
            failure?.let { throw it }
            return pixels
        }
    }

    @Suppress("DEPRECATION")
    private class TimerResources :
        Resources(RuntimeEnvironment.getApplication().assets, DisplayMetrics(), Configuration()) {
        val dimensions = mutableMapOf(WIDTH_ID to 212.5f, FOUR_STREAM_ID to 194.0f)
        var marginPixels = 16
        var widthNameMissing = false
        var marginNameMissing = false
        var dimensionFailure: Throwable? = null
        var pixelFailure: Throwable? = null
        val identifierReads = mutableListOf<String?>()
        val dimensionReads = mutableListOf<Int>()
        val pixelReads = mutableListOf<Int>()

        override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
            identifierReads.add(name)
            if (defType != "dimen" || defPackage != "miui.systemui.plugin") return 0
            return when (name) {
                WIDTH_NAME -> if (widthNameMissing) 0 else WIDTH_ID
                MARGIN_NAME -> if (marginNameMissing) 0 else MARGIN_ID
                else -> 0
            }
        }

        override fun getDimension(id: Int): Float {
            dimensionReads.add(id)
            dimensionFailure?.let { throw it }
            return dimensions[id] ?: throw NotFoundException("dimension $id")
        }

        override fun getDimensionPixelSize(id: Int): Int {
            pixelReads.add(id)
            pixelFailure?.let { throw it }
            return if (id == MARGIN_ID) marginPixels else throw NotFoundException("margin $id")
        }
    }

    private class TimerContext(private val timerResources: Resources) : ContextWrapper(null) {
        override fun getResources(): Resources = timerResources
    }

    private class Chain(
        private val item: Any,
        private val progress: Int = 37,
        private val nativeResult: Float = -100.0f,
        private val native: () -> Unit = {},
    ) : XposedInterface.Chain {
        var proceedCount = 0
        var nativeProgress: Int? = null
        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any = item
        override fun getArgs(): List<Any?> = listOf(progress)
        override fun getArg(index: Int): Any? = if (index == 0) progress else error("not used")
        override fun proceed(): Any? {
            proceedCount++
            nativeProgress = progress
            native()
            return nativeResult
        }
        override fun proceed(args: Array<Any?>): Any? = error("arguments must remain unchanged")
        override fun proceedWith(receiver: Any): Any? = error("not used")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("not used")
    }

    private companion object {
        const val WIDTH_NAME = "miui_volume_timer_seekbar_width"
        const val MARGIN_NAME = "miui_volume_timer_seekbar_margin_left"
        const val WIDTH_ID = 0x7f0702fa
        const val FOUR_STREAM_ID = 0x7f0702fb
        const val MARGIN_ID = 0x7f0702f9
    }
}
