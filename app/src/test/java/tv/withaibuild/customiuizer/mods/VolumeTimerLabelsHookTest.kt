package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.VolumeTimerValues
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import java.util.concurrent.ExecutionException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VolumeTimerLabelsHookTest {
    private val hook = SystemUIControlCenterHooks.VolumeTimerLabelsHook()

    @Test
    fun resourceSecondsAndLabelsShareTheElevenOrderedChoices() {
        val expected = intArrayOf(0, 1800, 3600, 7200, 10800, 14400, 18000, 21600, 28800, 36000, 43200)
        assertArrayEquals(expected, VolumeTimerValues.segments())
        val callerOwned = VolumeTimerValues.segments()
        callerOwned[10] = 1
        assertArrayEquals(expected, VolumeTimerValues.segments())
        assertEquals(expected.size, VolumeTimerValues.labels(Locale.US, "Off").size)
        assertEquals("12 hours", VolumeTimerValues.labels(Locale.US, "Off")[10])
    }

    @Test
    fun englishUsesPluralHoursForAllExtendedChoices() {
        val helper = helper(Locale.US)
        val chain = Chain(helper)
        hook.intercept(chain)
        assertArrayEquals(arrayOf("Off", "30 minutes", "1 hour", "2 hours", "3 hours", "4 hours",
            "5 hours", "6 hours", "8 hours", "10 hours", "12 hours"), helper.mTimeSegmentTitle)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun russianUsesOneFewAndManyHourForms() {
        val helper = helper(Locale.forLanguageTag("ru"), "Откл")
        hook.intercept(Chain(helper))
        assertArrayEquals(arrayOf("Откл", "30 минут", "1 час", "2 часа", "3 часа", "4 часа",
            "5 часов", "6 часов", "8 часов", "10 часов", "12 часов"), helper.mTimeSegmentTitle)
    }

    @Test
    fun czechUsesDifferentFormsForTwoAndFiveHours() {
        val helper = helper(Locale.forLanguageTag("cs"), "Vyp.")
        hook.intercept(Chain(helper))
        assertArrayEquals(arrayOf("Vyp.", "30 minut", "1 hodina", "2 hodiny", "3 hodiny", "4 hodiny",
            "5 hodin", "6 hodin", "8 hodin", "10 hodin", "12 hodin"), helper.mTimeSegmentTitle)
    }

    @Test
    fun simplifiedChineseKeepsTheExpectedUnitsAndOrder() {
        val helper = helper(Locale.SIMPLIFIED_CHINESE, "关闭")
        hook.intercept(Chain(helper))
        assertArrayEquals(arrayOf("关闭", "30分钟", "1小时", "2小时", "3小时", "4小时", "5小时",
            "6小时", "8小时", "10小时", "12小时"), helper.mTimeSegmentTitle)
    }

    @Test
    fun traditionalChineseKeepsTheExpectedUnitsAndOrder() {
        for (locale in listOf(Locale.TRADITIONAL_CHINESE, Locale.forLanguageTag("zh-Hant-TW"))) {
            val helper = helper(locale, "關閉")
            assertEquals(locale, helper.mContext.resources.configuration.locales[0])
            hook.intercept(Chain(helper))
            assertArrayEquals(arrayOf("關閉", "30 分鐘", "1 小時", "2 小時", "3 小時", "4 小時", "5 小時",
                "6 小時", "8 小時", "10 小時", "12 小時"), helper.mTimeSegmentTitle)
        }
    }

    @Test
    fun theNativeOffLabelDoesNotDependOnAnotherResourceLookup() {
        val helper = helper(Locale.US, "Native off translation")
        hook.intercept(Chain(helper))
        assertEquals("Native off translation", helper.mTimeSegmentTitle!![0])
        assertEquals(0, (helper.mContext.resources as TimerResources).identifierReads)
    }

    @Test
    fun missingPluginStringNamesDoNotPreventAnElevenLabelArray() {
        val resources = TimerResources(Locale.US).apply { missingNames = true }
        val helper = TimerHelper(TimerContext(resources), arrayOf("Off", "30 minutes", "1 hour", "2 hours", "8 hours"))
        hook.intercept(Chain(helper))
        assertEquals(11, helper.mTimeSegmentTitle!!.size)
        assertEquals("12 hours", helper.mTimeSegmentTitle!![10])
    }

    @Test
    fun aConfigurationFailurePublishesAllElevenChoicesWithoutMutatingTheNativeArray() {
        val resources = TimerResources(Locale.US).apply {
            configurationFailure = Resources.NotFoundException("configuration unavailable")
        }
        val helper = TimerHelper(TimerContext(resources), arrayOf("Off", "30 minutes", "1 hour", "2 hours", "8 hours"))
        val original = helper.mTimeSegmentTitle
        val result = Any()
        val chain = Chain(helper, result)
        assertSame(result, hook.intercept(chain))
        assertArrayEquals(arrayOf("Off", "30 minutes", "1 hour", "2 hours", "8 hours"), original)
        assertEquals(11, helper.mTimeSegmentTitle!!.size)
        assertEquals("12 h", helper.mTimeSegmentTitle!![10])
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun anOrdinaryNativeFailureIsNotFollowedByLabelReplacement() {
        val helper = helper(Locale.US)
        val original = helper.mTimeSegmentTitle
        val failure = IllegalStateException("native initialization failed")
        val chain = Chain(helper) { throw failure }
        assertSame(failure, assertThrows(Throwable::class.java) { hook.intercept(chain) })
        assertSame(original, helper.mTimeSegmentTitle)
        assertEquals(0, (helper.mContext as TimerContext).resourceReads)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun directAndWrappedFatalNativeFailuresEscapeBeforeAdaptation() {
        for (fatal in listOf(OutOfMemoryError("native"), StackOverflowError("native"), ThreadDeath())) {
            for (failure in listOf(fatal, InvocationTargetException(ExecutionException(fatal)))) {
                val helper = helper(Locale.US)
                val original = helper.mTimeSegmentTitle
                val chain = Chain(helper) { throw failure }
                assertSame(fatal, assertThrows(Throwable::class.java) { hook.intercept(chain) })
                assertSame(original, helper.mTimeSegmentTitle)
                assertEquals(0, (helper.mContext as TimerContext).resourceReads)
                assertEquals(1, chain.proceedCount)
            }
        }
    }

    @Test
    fun fatalResourceFailuresAreNotSwallowedOrPartiallyPublished() {
        for (fatal in listOf(OutOfMemoryError("resources"), StackOverflowError("resources"), ThreadDeath())) {
            val context = TimerContext(TimerResources(Locale.US)).apply {
                failure = InvocationTargetException(ExecutionException(fatal))
            }
            val helper = TimerHelper(context, arrayOf("Off"))
            val original = helper.mTimeSegmentTitle
            val chain = Chain(helper)
            assertSame(fatal, assertThrows(Throwable::class.java) { hook.intercept(chain) })
            assertSame(original, helper.mTimeSegmentTitle)
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test
    fun anOrdinaryResourceFailureKeepsTheNativeResultAndAllTimerChoices() {
        val context = TimerContext(TimerResources(Locale.US)).apply {
            failure = Resources.NotFoundException("resources unavailable")
        }
        val helper = TimerHelper(context, arrayOf("Off"))
        val original = helper.mTimeSegmentTitle
        val result = Any()
        val chain = Chain(helper, result)
        assertSame(result, hook.intercept(chain))
        assertArrayEquals(arrayOf("Off"), original)
        assertEquals(11, helper.mTimeSegmentTitle!!.size)
        assertEquals("12 h", helper.mTimeSegmentTitle!![10])
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun aResourceFailureMustNotClampTheTwelveHourLabelToEightHours() {
        val context = TimerContext(TimerResources(Locale.US)).apply {
            failure = Resources.NotFoundException("resources unavailable")
        }
        val helper = TimerHelper(context, arrayOf("Off", "30 minutes", "1 hour", "2 hours", "8 hours"))
        hook.intercept(Chain(helper))
        val titles = helper.mTimeSegmentTitle!!
        // The ROM constrains the determined segment to the title array's last index.
        assertEquals("12 h", titles[10.coerceIn(0, titles.lastIndex)])
        assertArrayEquals(arrayOf("Off", "30 min", "1 h", "2 h", "3 h", "4 h", "5 h",
            "6 h", "8 h", "10 h", "12 h"), titles)
    }

    @Test
    fun missingOrEmptyNativeTitlesKeepNativeBehavior() {
        for (titles in listOf(null, emptyArray<String>())) {
            val helper = TimerHelper(TimerContext(TimerResources(Locale.US)), titles)
            val chain = Chain(helper)
            hook.intercept(chain)
            assertSame(titles, helper.mTimeSegmentTitle)
            assertEquals(0, (helper.mContext as TimerContext).resourceReads)
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test
    fun eachInitializationUsesItsCurrentResourceLocale() {
        val oldDefault = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val resources = TimerResources(Locale.forLanguageTag("ru"))
            val helper = TimerHelper(TimerContext(resources), arrayOf("Откл"))
            hook.intercept(Chain(helper))
            assertEquals("5 часов", helper.mTimeSegmentTitle!![6])
            resources.locale = Locale.US
            helper.mTimeSegmentTitle = arrayOf("Off")
            hook.intercept(Chain(helper))
            assertEquals("5 hours", helper.mTimeSegmentTitle!![6])
        } finally {
            Locale.setDefault(oldDefault)
        }
    }

    private fun helper(locale: Locale, off: String = "Off") = TimerHelper(
        TimerContext(TimerResources(locale)), arrayOf(off, "30 minutes", "1 hour", "2 hours", "8 hours"),
    )

    private class TimerHelper(
        @JvmField val mContext: Context,
        @JvmField var mTimeSegmentTitle: Array<String>?,
    )

    @Suppress("DEPRECATION")
    private class TimerResources(var locale: Locale) :
        Resources(RuntimeEnvironment.getApplication().assets, DisplayMetrics(), Configuration()) {
        var identifierReads = 0
        var missingNames = false
        var configurationFailure: Throwable? = null

        override fun getConfiguration(): Configuration {
            configurationFailure?.let { throw it }
            return Configuration().apply { setLocale(this@TimerResources.locale) }
        }

        override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
            identifierReads++
            if (missingNames) return 0
            return when (name) {
                "timer_off" -> 1
                "timer_30_minutes" -> 2
                "timer_1_hour" -> 3
                else -> 0
            }
        }

        override fun getString(id: Int): String = when (id) {
            1 -> when (locale.language) {
                "ru" -> "Откл"
                "cs" -> "Vyp."
                "zh" -> if (locale.country == "TW") "關閉" else "关闭"
                else -> "Off"
            }
            2 -> when (locale.language) {
                "ru" -> "%d минут"
                "cs" -> "%d minut"
                "zh" -> if (locale.country == "TW") "%d分鐘" else "%d分钟"
                else -> "%d minutes"
            }
            3 -> when (locale.language) {
                "ru" -> "%d час"
                "cs" -> "%d hodina"
                "zh" -> if (locale.country == "TW") "%d小時" else "%d小时"
                else -> "%d hour"
            }
            else -> throw NotFoundException("missing timer resource")
        }

        override fun getString(id: Int, vararg formatArgs: Any?): String =
            String.format(locale, getString(id), *formatArgs)
    }

    private class TimerContext(private val timerResources: Resources) : ContextWrapper(null) {
        var resourceReads = 0
        var failure: Throwable? = null
        override fun getResources(): Resources {
            resourceReads++
            failure?.let { throw it }
            return timerResources
        }
    }

    private class Chain(
        private val helper: Any,
        private val result: Any? = null,
        private val initialize: () -> Unit = {},
    ) : XposedInterface.Chain {
        var proceedCount = 0
        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any = helper
        override fun getArgs(): List<Any?> = emptyList()
        override fun getArg(index: Int): Any? = error("not used")
        override fun proceed(): Any? {
            proceedCount++
            initialize()
            return result
        }
        override fun proceed(args: Array<Any?>): Any? = error("not used")
        override fun proceedWith(receiver: Any): Any? = error("not used")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("not used")
    }
}
