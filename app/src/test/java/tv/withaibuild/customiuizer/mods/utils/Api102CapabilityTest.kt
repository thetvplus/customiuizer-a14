package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Api102CapabilityTest {

    @Before
    fun resetCapabilities() {
        // Reset to a clean state before each test so order does not leak.
        XposedApiCapabilities.initialize(101)
    }

    @Test
    fun api101_flagsAreZero() {
        XposedApiCapabilities.initialize(101)

        assertFalse("API 101 must not report stable hook IDs", XposedApiCapabilities.supportsStableHookId())
        assertFalse("API 101 must not report replaceHook", XposedApiCapabilities.supportsReplaceHook())
    }

    @Test
    fun api102_flagsAreSet() {
        XposedApiCapabilities.initialize(102)

        assertTrue("API 102 must report stable hook IDs", XposedApiCapabilities.supportsStableHookId())
        assertTrue("API 102 must report replaceHook", XposedApiCapabilities.supportsReplaceHook())
    }

    @Test
    fun initialize_isIdempotent() {
        XposedApiCapabilities.initialize(101)
        XposedApiCapabilities.initialize(102)
        XposedApiCapabilities.initialize(101)

        assertFalse("last initialize(101) must win", XposedApiCapabilities.supportsStableHookId())
    }

    @Test
    fun bridge_setStableHookId_appliesToBuilder() {
        XposedApiCapabilities.initialize(102)

        val builder = object : XposedInterface.HookBuilder {
            var appliedId: String? = null
            override fun setPriority(priority: Int) = this
            override fun setExceptionMode(mode: XposedInterface.ExceptionMode) = this
            override fun intercept(hooker: XposedInterface.Hooker) = throw UnsupportedOperationException()
            override fun setId(id: String?): XposedInterface.HookBuilder {
                appliedId = id
                return this
            }
        }

        val returned = Api102HookBridge.setStableHookId(builder, Api102HookBridge.STABLE_ID_RES_TEXT)

        assertSame("bridge returns the same builder", builder, returned)
        assertEquals(Api102HookBridge.STABLE_ID_RES_TEXT, builder.appliedId)
    }

    @Test
    fun stableHookIds_areFixedShortStrings() {
        assertTrue("res.text id", Api102HookBridge.STABLE_ID_RES_TEXT.isNotEmpty())
        assertTrue("res.string id", Api102HookBridge.STABLE_ID_RES_STRING.isNotEmpty())
        assertTrue("res.layout id", Api102HookBridge.STABLE_ID_RES_LAYOUT.isNotEmpty())
        assertTrue("res.drawable_density id", Api102HookBridge.STABLE_ID_RES_DRAWABLE_DENSITY.isNotEmpty())
        assertTrue("res.theme_merge id", Api102HookBridge.STABLE_ID_RES_THEME_MERGE.isNotEmpty())
        assertTrue("systemui.init id", Api102HookBridge.STABLE_ID_SYSTEMUI_INIT.isNotEmpty())
        assertTrue("launcher.init id", Api102HookBridge.STABLE_ID_LAUNCHER_INIT.isNotEmpty())
    }
}
