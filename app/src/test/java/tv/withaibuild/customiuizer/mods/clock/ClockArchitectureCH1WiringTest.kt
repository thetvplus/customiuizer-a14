package tv.withaibuild.customiuizer.mods.clock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClockArchitectureCH1WiringTest {

    private val source by lazy { loadSource("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt") }

    @Test
    fun updateTimeHook_hasNoGenericReflectionOrPipelines() {
        val region = extractUpdateTimeHookRegion()

        val forbidden = listOf(
            "getObjectField",
            "callMethod",
            "setObjectField",
            "findClass",
            "findField",
            "getDeclaredField",
            "getDeclaredMethod",
            "declaredMethods",
            "ClockResolver.",
            "synchronized",
            ".filter",
            ".map",
            ".mapNotNull",
            ".toList",
            ".toTypedArray",
            ".asSequence",
            "Sequence",
            "MainModule.mPrefs",
        )

        for (token in forbidden) {
            assertFalse(
                "updateTimeHook region must not contain '$token'",
                region.contains(token),
            )
        }
    }

    @Test
    fun updateTimeHook_usesArchitectureCEffect() {
        val region = extractUpdateTimeHookRegion()

        assertTrue("must call currentEffect", region.contains("currentEffect("))
        assertTrue("must call resolveForClock", region.contains("resolveForClock("))
        assertTrue("must call readController", region.contains("readController("))
        assertTrue("must call readCalendar", region.contains("readCalendar("))
        assertTrue("must call effect.format", region.contains("effect.format("))
    }

    @Test
    fun updateTimeHook_buildClockTextBeforeEffectAccess() {
        val region = extractUpdateTimeHookRegion()

        val buildClockTextIndex = region.indexOf("buildClockText(")
        val resolveForClockIndex = region.indexOf("resolveForClock(")

        assertTrue("buildClockText must be present", buildClockTextIndex != -1)
        assertTrue("resolveForClock must be present", resolveForClockIndex != -1)
        assertTrue(
            "buildClockText must appear before resolveForClock",
            buildClockTextIndex < resolveForClockIndex,
        )
    }

    @Test
    fun updateTimeHook_currentEffectBeforeResolveForClock() {
        val region = extractUpdateTimeHookRegion()

        val currentEffectIndex = region.indexOf("currentEffect(")
        val resolveForClockIndex = region.indexOf("resolveForClock(")

        assertTrue("currentEffect must be present", currentEffectIndex != -1)
        assertTrue("resolveForClock must be present", resolveForClockIndex != -1)
        assertTrue(
            "currentEffect must appear before resolveForClock",
            currentEffectIndex < resolveForClockIndex,
        )
    }

    @Test
    fun updateTimeHook_runtimeContextClassOnlyOnSlowPath() {
        val region = extractUpdateTimeHookRegion()

        val resolveForClockIndex = region.indexOf("resolveForClock(")
        val contextClassIndex = region.indexOf("clock.context.javaClass")

        assertTrue("resolveForClock must be present", resolveForClockIndex != -1)
        assertTrue("clock.context.javaClass must be present", contextClassIndex != -1)
        assertTrue(
            "clock.context.javaClass must appear after resolveForClock",
            resolveForClockIndex < contextClassIndex,
        )
    }

    @Test
    fun updateTimeHook_preservesViewInfoMetadata() {
        val region = extractUpdateTimeHookRegion()

        assertTrue(
            "clockName metadata path must be retained",
            region.contains("ModuleHelper.getViewInfo(clock, \"clockName\")"),
        )
    }

    @Test
    fun updateTimeHook_proceedsAtMostOnce() {
        val region = extractUpdateTimeHookRegion()

        var count = 0
        var fromIndex = 0
        while (true) {
            val index = region.indexOf("chain.proceed()", fromIndex)
            if (index == -1) break
            count++
            fromIndex = index + 1
        }

        assertTrue("chain.proceed() may appear at most once in updateTimeHook", count <= 1)
    }

    private fun extractUpdateTimeHookRegion(): String {
        val start = source.indexOf("val updateTimeHook = object : MethodHook")
        val endMarker = "ModuleHelper.findAndHookMethod(\"com.android.systemui.statusbar.views.MiuiClock\", lpparam.classLoader, \"updateTime\", updateTimeHook)"
        val end = source.indexOf(endMarker, start)

        if (start == -1 || end == -1) {
            throw AssertionError("Could not locate updateTimeHook region in SystemClockHooks.kt")
        }

        return source.substring(start, end)
    }

    private fun loadSource(relativePath: String): String {
        var dir = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
                ?: throw AssertionError("Repository root not found while locating $relativePath")
        }
    }
}
