package tv.withaibuild.customiuizer.mods.statusbariconvisibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks
import java.io.File
import java.lang.reflect.Modifier

/**
 * B2 consolidation structural invariants for the HideIconsSignal Architecture C migration.
 *
 * These tests are source/reflection invariants, not runtime ROM callbacks.
 *
 * Evidence classification: STRUCTURAL.
 */
class C4HideIconsSignalB2StructuralTest {

    // -------------------------------------------------------------------------
    // A. production hook wiring source invariant
    // -------------------------------------------------------------------------
    @Test
    fun hideIconsSignalHook_wiringSourceInvariants() {
        val body = extractHideIconsSignalHookBody(readMainSource("SystemUIStatusBarHooks.kt"))

        // Resolver called exactly once with the install classloader inside this hook.
        assertEquals(
            "HideIconsSignalHook must resolve ABI exactly once",
            1,
            body.occurrenceCount("val abi = StatusBarIconVisibilityResolver.resolve(lpparam.classLoader)"),
        )

        // Effect created exactly once as a local, immutable val and captured by the hook.
        assertEquals(
            "HideIconsSignalHook must create the Effect as a local val exactly once",
            1,
            body.occurrenceCount("val effect = StatusBarIconVisibilityEffect(abi) { currentOrBuildStatusBarIconVisibilitySnapshot() }"),
        )

        // Callback in this hook delegates to the captured Effect.
        assertEquals(
            "MethodHook.before in HideIconsSignalHook must delegate to effect.before(param)",
            1,
            body.occurrenceCount("effect.before(param)"),
        )

        // Both methods are installed via hookAllMethods on the StatusBarMobileView hook surface.
        assertEquals(
            "applyMobileState must be installed exactly once in this hook",
            1,
            body.occurrenceCount(
                "ModuleHelper.hookAllMethods(\"com.android.systemui.statusbar.StatusBarMobileView\", lpparam.classLoader, \"applyMobileState\", stateHook)",
            ),
        )
        assertEquals(
            "updateState must be installed exactly once in this hook",
            1,
            body.occurrenceCount(
                "ModuleHelper.hookAllMethods(\"com.android.systemui.statusbar.StatusBarMobileView\", lpparam.classLoader, \"updateState\", stateHook)",
            ),
        )
    }

    // -------------------------------------------------------------------------
    // B. FAST source invariant
    // -------------------------------------------------------------------------
    @Test
    fun processFast_containsNoXposedHelpersFieldAccess() {
        val source = readMainSource("StatusBarIconVisibilityEffect.kt")

        // Extract the processFast body up to the start of processLegacy.
        val fastStart = source.indexOf("private fun processFast")
        val legacyStart = source.indexOf("private fun processLegacy")
        assertTrue("processFast must exist", fastStart >= 0)
        assertTrue("processLegacy must exist", legacyStart >= 0)
        val fastBody = source.substring(fastStart, legacyStart)

        // FAST path must not use runtime XposedHelpers field accessors or field lookup.
        assertFalse(
            "processFast must not call XposedHelpers.getObjectField",
            fastBody.contains("XposedHelpers.getObjectField"),
        )
        assertFalse(
            "processFast must not call XposedHelpers.getBooleanField",
            fastBody.contains("XposedHelpers.getBooleanField"),
        )
        assertFalse(
            "processFast must not call XposedHelpers.setObjectField",
            fastBody.contains("XposedHelpers.setObjectField"),
        )
        assertFalse(
            "processFast must not perform findField lookup",
            fastBody.contains("findField(") || fastBody.contains("findFieldIfExists"),
        )
    }

    // -------------------------------------------------------------------------
    // C. no mutable process-global Effect
    // -------------------------------------------------------------------------
    @Test
    fun noMutableProcessGlobalEffectField() {
        val clazz = SystemUIStatusBarHooks::class.java
        for (field in clazz.declaredFields) {
            assertFalse(
                "SystemUIStatusBarHooks must not hold a StatusBarIconVisibilityEffect field (process-global): ${field.name}",
                StatusBarIconVisibilityEffect::class.java.isAssignableFrom(field.type),
            )
            if (StatusBarIconVisibilityEffect::class.java.isAssignableFrom(field.type)) {
                assertTrue(
                    "Any StatusBarIconVisibilityEffect field must be final/immutable: ${field.name}",
                    Modifier.isFinal(field.modifiers),
                )
            }
        }
    }

    private fun readMainSource(fileName: String): String {
        val path = File("src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/$fileName")
        val altPath = File("src/main/java/tv/withaibuild/customiuizer/mods/$fileName")
        val file = if (path.exists()) path else altPath
        return file.readText(Charsets.UTF_8)
    }

    private fun extractHideIconsSignalHookBody(source: String): String {
        val startMarker = "fun HideIconsSignalHook"
        val start = source.indexOf(startMarker)
        assertTrue("HideIconsSignalHook must exist in source", start >= 0)

        // The enclosing object is indented with 4 spaces; the next top-level function
        // inside that object starts with 4 spaces + "fun ". Nested functions such as
        // `override fun before` are indented further, so they do not end the extraction.
        val nextTopLevel = source.indexOf("\n    fun ", start + startMarker.length)
        assertTrue("HideIconsSignalHook body must end before next top-level function", nextTopLevel >= 0)
        return source.substring(start, nextTopLevel)
    }

    private fun String.occurrenceCount(target: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val idx = indexOf(target, from)
            if (idx < 0) return count
            count++
            from = idx + target.length
        }
    }
}
