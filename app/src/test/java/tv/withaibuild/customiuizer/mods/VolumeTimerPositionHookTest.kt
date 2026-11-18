package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.widget.FrameLayout
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
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
    private val hook = SystemUIControlCenterHooks.VolumeTimerPositionHook(TimerItem::class.java, MARGIN_ID)

    @Test
    fun theNativeWidthIdPreservesAllElevenPositionsWithoutNameLookups() {
        val resources = TimerResources()
        val item = TimerItem(TimerContext(resources))
        val expected = listOf(8.5f, 29.75f, 51.0f, 72.25f, 93.5f, 114.75f,
            136.0f, 157.25f, 178.5f, 199.75f, 221.0f)
        expected.forEachIndexed { index, position ->
            item.mDeterminedSegment = index
            val chain = Chain(item, progress = 999)
            assertEquals(position, hook.intercept(chain))
            assertEquals(0, chain.proceedCount)
            assertEquals(index, item.mDeterminedSegment)
        }
        assertEquals(0, resources.identifierReads)
    }

    @Test
    fun theFourStreamLayoutUsesCurrentWidthAndMargin() {
        val resources = TimerResources()
        val item = TimerItem(TimerContext(resources), FOUR_STREAM_ID, segment = 10)
        assertEquals(202.5f, hook.intercept(Chain(item)))
        resources.dimensions[FOUR_STREAM_ID] = 201.25f
        resources.marginPixels = 13
        assertEquals(206.75f, hook.intercept(Chain(item)))
        assertEquals(0, resources.identifierReads)
    }

    @Test
    fun unavailableDimensionsRunTheNativeMethodOnceWithTheOriginalArgument() {
        for (failureAtMargin in listOf(false, true)) {
            val resources = TimerResources().apply {
                if (failureAtMargin) pixelFailure = Resources.NotFoundException("margin")
                else dimensionFailure = Resources.NotFoundException("width")
            }
            val chain = Chain(TimerItem(TimerContext(resources)), progress = 713)
            assertEquals(17.5f, hook.intercept(chain))
            assertEquals(1, chain.proceedCount)
            assertEquals(713, chain.nativeProgress)
        }
    }

    @Test
    fun fatalResourceFailuresEscapeBeforeNativeFallback() {
        val fatal = OutOfMemoryError("timer width")
        for (failure in listOf(fatal, InvocationTargetException(ExecutionException(fatal)))) {
            val resources = TimerResources().apply { dimensionFailure = failure }
            val chain = Chain(TimerItem(TimerContext(resources)))
            assertSame(fatal, assertThrows(Throwable::class.java) { hook.intercept(chain) })
            assertEquals(0, chain.proceedCount)
        }
    }

    @Test
    fun nativeFailuresPropagateAfterResourceFallback() {
        val ordinary = InvocationTargetException(IllegalArgumentException("native"))
        val fatal = StackOverflowError("native")
        val resources = TimerResources().apply { pixelFailure = Resources.NotFoundException("margin") }
        val item = TimerItem(TimerContext(resources))
        for ((failure, expected) in listOf(ordinary to ordinary,
            InvocationTargetException(ExecutionException(fatal)) to fatal)) {
            val chain = Chain(item) { throw failure }
            assertSame(expected, assertThrows(Throwable::class.java) { hook.intercept(chain) })
            assertEquals(1, chain.proceedCount)
        }
    }

    private class TimerItem(
        @JvmField val mContext: Context,
        @JvmField val mTimerSeekbarWidth: Int = WIDTH_ID,
        segment: Int = 2,
    ) {
        @JvmField val mTimerTime = FrameLayout(RuntimeEnvironment.getApplication()).apply { layout(0, 0, 15, 16) }
        @JvmField var mDeterminedSegment = segment
    }

    @Suppress("DEPRECATION")
    private class TimerResources :
        Resources(RuntimeEnvironment.getApplication().assets, DisplayMetrics(), Configuration()) {
        val dimensions = mutableMapOf(WIDTH_ID to 212.5f, FOUR_STREAM_ID to 194.0f)
        var marginPixels = 16
        var dimensionFailure: Throwable? = null
        var pixelFailure: Throwable? = null
        var identifierReads = 0

        override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
            identifierReads++
            return 0
        }

        override fun getDimension(id: Int): Float {
            dimensionFailure?.let { throw it }
            return dimensions[id] ?: throw NotFoundException("dimension $id")
        }

        override fun getDimensionPixelSize(id: Int): Int {
            pixelFailure?.let { throw it }
            return if (id == MARGIN_ID) marginPixels else throw NotFoundException("margin $id")
        }
    }

    private class TimerContext(private val timerResources: Resources) : ContextWrapper(null) {
        override fun getResources(): Resources = timerResources
    }

    private class Chain(
        private val item: TimerItem,
        private val progress: Int = 37,
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
            return 17.5f
        }
        override fun proceed(args: Array<Any?>): Any? = error("arguments must remain unchanged")
        override fun proceedWith(receiver: Any): Any? = error("not used")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("not used")
    }

    private companion object {
        const val WIDTH_ID = 0x7f0702fa
        const val FOUR_STREAM_ID = 0x7f0702fb
        const val MARGIN_ID = 0x7f0702f9
    }
}
