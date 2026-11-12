package tv.withaibuild.customiuizer.mods

import android.view.View
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import tv.withaibuild.customiuizer.mods.utils.feature.GenericAppFeatures
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PocoFolderBlurTest {
    class Native {
        companion object {
            @JvmStatic fun isFolderBlurSupported(small: Boolean) = false
            @JvmStatic fun isBlurSupported() = false
            @JvmStatic fun publish() {}
            @JvmStatic fun render() {}
            @JvmStatic fun draw() {}
        }
    }
    class WrongAbi { fun isFolderBlurSupported(small: Boolean) = false }
    class ModernPublication { fun onCreateBeforeSetCreatedFlag() = true }
    class JoinPoint
    class LegacyPublication {
        companion object { @JvmStatic fun onCreate_aroundBody0(launcher: LegacyPublication, bundle: Bundle, point: JoinPoint) {} }
    }
    class Folder : FrameLayout(RuntimeEnvironment.getApplication())
    private val gate = Native::class.java.getDeclaredMethod("isFolderBlurSupported", Boolean::class.javaPrimitiveType)
    private val publish = Native::class.java.getDeclaredMethod("publish")
    private val render = Native::class.java.getDeclaredMethod("render")
    private val draw = Native::class.java.getDeclaredMethod("draw")
    private val active = linkedMapOf<Method, XposedInterface.Hooker>()
    private val retired = ArrayList<Method>()
    private val operations = ArrayList<Executable>()
    private val logs = ArrayList<String>()
    private var previous: XposedInterface? = null
    private var rejected: Method? = null
    private var deoptAllowed = true
    private var nativeState = FolderBlurCapability(true, true)
    private var stateFailure: Throwable? = null
    private var stateReads = 0
    private var nativeSmall = true
    private var nativeLarge = true
    private var folderImage = true
    private var defaultFailure: Throwable? = null

    @Before fun framework() {
        previous = XposedHelpers.moduleInst
        XposedHelpers.moduleInst = proxy(XposedInterface::class.java) { _, name, args ->
            when (name) {
                "deoptimize" -> { operations.add(args!![0] as Executable); deoptAllowed }
                "log" -> { (args?.get(2) as? String)?.let(logs::add); null }
                "hook" -> {
                    val method = args!![0] as Method
                    operations.add(method)
                    proxy(XposedInterface.HookBuilder::class.java) { builder, operation, values ->
                        when (operation) {
                            "setPriority", "setExceptionMode" -> builder
                            "intercept" -> {
                                check(method != rejected) { "Rejected" }
                                active[method] = values!![0] as XposedInterface.Hooker
                                proxy(XposedInterface.HookHandle::class.java) { _, action, _ ->
                                    when (action) {
                                        "getExecutable" -> method
                                        "unhook" -> { retired.add(method); active.remove(method); null }
                                        else -> error(action)
                                    }
                                }
                            }
                            else -> error(operation)
                        }
                    }
                }
                else -> error(name)
            }
        }
    }
    @After fun restore() { XposedHelpers.moduleInst = previous }

    private fun install() = installPocoFolderBlurHooks(gate, publish, render, listOf(draw),
        readCapability = { stateReads++; stateFailure?.let { throw it }; nativeState },
        defaultFolder = { defaultFailure?.let { throw it }; if (it) nativeSmall else nativeLarge },
        isFolderBackground = { folderImage })

    private fun call(method: Method, args: List<Any?> = emptyList(), native: (List<Any?>) -> Any? = { false }): Any? =
        active.getValue(method).intercept(proxy(XposedInterface.Chain::class.java) { _, name, values ->
            when (name) {
                "getArg" -> args[values!![0] as Int]
                "getArgs" -> args
                "proceed" -> native(if (values.isNullOrEmpty()) args else (values[0] as Array<*>).toList())
                else -> error(name)
            }
        })
    private fun allowed(small: Boolean) = call(gate, listOf(small))

    @Test fun onlyTheBlurredWorkspaceIconUsesTheLongerNativeFade() {
        val context = RuntimeEnvironment.getApplication()
        val workspaceIcon = Folder()
        val preview = View(context)
        val nativeCurve = LinearInterpolator()
        var blurred = true
        active[draw] = folderBlurFadeHook(Folder::class.java) { it === workspaceIcon && blurred }

        fun fade(view: View, from: Float, to: Float, duration: Long): List<Any?> {
            var calls = 0
            val forwarded = call(draw, listOf(view, from, to, duration, 0L, nativeCurve)) {
                calls++
                it
            }
            assertEquals(1, calls)
            @Suppress("UNCHECKED_CAST")
            return forwarded as List<Any?>
        }

        val opening = fade(workspaceIcon, 1f, 0f, 25)
        assertEquals(listOf(workspaceIcon, 1f, 0f, 96L, 0L), opening.take(5))
        val closing = fade(workspaceIcon, .2f, 1f, 80)
        assertEquals(listOf(workspaceIcon, .2f, 1f, 144L, 0L), closing.take(5))
        assertEquals(.5f, (closing[5] as Interpolator).getInterpolation(.5f), .001f)
        assertSame(nativeCurve, fade(preview, 0f, 1f, 80)[5])
        assertSame(nativeCurve, fade(workspaceIcon, 0f, 1f, 0)[5])
        assertSame(nativeCurve, fade(workspaceIcon, 0f, 1f, 180)[5])
        blurred = false
        assertEquals(80L, fade(workspaceIcon, 0f, 1f, 80)[3])
    }

    @Test fun modernGateInstallsWithoutTheRemovedWallpaperCallbackOrGeneralBlurHook() {
        assertTrue(hasPocoFolderBlurGate(Native::class.java))
        assertThrows(NoSuchMethodException::class.java) { folderIconWallpaperBlurEntry(Native::class.java, Native::class.java) }
        install()
        assertEquals(listOf(draw, publish, render, gate), operations)
        assertEquals(setOf(publish, render, gate), active.keys)
        assertEquals(true, allowed(true))
        assertEquals(true, allowed(false))
        assertFalse(active.containsKey(Native::class.java.getDeclaredMethod("isBlurSupported")))
        assertThrows(IllegalArgumentException::class.java) { hasPocoFolderBlurGate(WrongAbi::class.java) }
    }

    @Test fun nativePublicationAndSeparateThemePoliciesControlBothFolderSizes() {
        nativeState = FolderBlurCapability(true, false)
        install()
        assertEquals(false, allowed(true))
        nativeState = FolderBlurCapability(true, true)
        call(publish) { null }
        nativeSmall = false
        assertEquals(false, allowed(true))
        assertEquals(true, allowed(false))
        nativeSmall = true; nativeLarge = false
        assertEquals(true, allowed(true))
        assertEquals(false, allowed(false))
        repeat(1000) { allowed(true); allowed(false) }
        assertEquals("Draws must not reflectively reload native flags", 2, stateReads)
        assertTrue("Per-result diagnostics are bounded", logs.size <= 5)
        nativeState = FolderBlurCapability(false, true)
        call(publish) { null }
        assertEquals(false, allowed(true))
        assertEquals(false, allowed(false))
    }

    @Test fun nativeBlackBlendKeepsBothAlphasAndAllGeometryTonalAndAnimationArguments() {
        install()
        val view = View(RuntimeEnvironment.getApplication()).apply { layout(0, 0, 100, 100) }
        val args = listOf(view, 20, 0xb3767676.toInt(), 100, 0x66b4b4b4, 100,
            0xff149400.toInt(), 106, 0xff2ef200.toInt(), 106, 10)
        var observed: List<Any?>? = null
        var calls = 0
        call(render, args) { observed = it; calls++; null }
        val expected = args.toMutableList().apply { this[2] = 0xb3000000.toInt(); this[4] = 0x66000000 }
        assertEquals(expected, observed)
        assertEquals(1, calls)
        assertEquals("Do not mutate the caller's list", 0xb3767676.toInt(), args[2])
        folderImage = false
        call(render, args) { observed = it; null }
        assertSame("Open-folder/shortcut overlays keep their material", args, observed)
        folderImage = true
        nativeState = FolderBlurCapability(true, false)
        call(publish) { null }
        call(render, args) { observed = it; null }
        assertSame(args, observed)
    }

    @Test fun rejectedDeoptimizationNeverInstallsAnIneffectiveGate() {
        deoptAllowed = false
        assertThrows(IllegalStateException::class.java) { install() }
        assertEquals(listOf(draw), operations)
        assertTrue(active.isEmpty())
    }

    @Test fun bothNativePublicationSitesResolveAndUnknownAbisFailBeforeInstallation() {
        val modern = pocoFolderBlurPublicationCaller(ModernPublication::class.java) { error("No AspectJ lookup needed") }
        assertEquals("onCreateBeforeSetCreatedFlag", modern.name)
        val legacy = pocoFolderBlurPublicationCaller(LegacyPublication::class.java) { JoinPoint::class.java }
        assertArrayEquals(arrayOf(LegacyPublication::class.java, Bundle::class.java, JoinPoint::class.java), legacy.parameterTypes)
        assertThrows(NoSuchMethodException::class.java) {
            pocoFolderBlurPublicationCaller(Native::class.java) { JoinPoint::class.java }
        }
        assertTrue(operations.isEmpty())
    }

    @Test fun selectedFeatureUsesTheRealAttachedPackageVersionBeforeResolvingNativeClasses() {
        val poco = "com.mi.android.globallauncher"
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application.packageManager).installPackage(PackageInfo().apply {
            packageName = poco
            versionName = "RELEASE-5.39.40.99999-01010101"
            applicationInfo = ApplicationInfo().apply { packageName = poco }
        })
        val context = object : ContextWrapper(application) { override fun getPackageName() = poco }
        val param = proxy(PackageReadyParam::class.java) { _, name, _ ->
            when (name) {
                "getPackageName" -> poco
                "getClassLoader" -> javaClass.classLoader
                else -> error(name)
            }
        }
        val prefs = PrefMap().apply { put("launcher_folder_icon_blur", true) }
        val spec = GenericAppFeatures.selected(param, prefs, false, false, false, false, true, context).single()
        assertThrows(Throwable::class.java) { spec.create().install() }
        assertTrue(logs.any { it.contains("POCO version=RELEASE-5.39.40.99999-01010101") })
        assertTrue(operations.isEmpty())
    }

    @Test fun tintOwnerIsTheFolderImageRatherThanTheOpenFolderOrAnUnrelatedOverlay() {
        val image = View(RuntimeEnvironment.getApplication())
        val container = FrameLayout(RuntimeEnvironment.getApplication()).apply { addView(image) }
        val folder = Folder().apply { addView(container) }
        assertTrue(isFolderIconBackground(image, Folder::class.java))
        assertFalse(isFolderIconBackground(folder, Folder::class.java))
        folder.removeView(container)
        FrameLayout(RuntimeEnvironment.getApplication()).addView(container)
        assertFalse(isFolderIconBackground(image, Folder::class.java))
        assertFalse(isFolderIconBackground(View(RuntimeEnvironment.getApplication()), Folder::class.java))
    }

    @Test fun failedInstallationRollsBackBeforeAnyFolderCanLoseItsNormalBackground() {
        rejected = gate
        assertThrows(IllegalStateException::class.java) { install() }
        assertEquals(listOf(render, publish), retired)
        assertTrue(active.isEmpty())
    }

    @Test fun failedPublicationAndThemeEvaluationFailClosedWhileFatalAndNativeFailuresPropagate() {
        install()
        stateFailure = IllegalStateException("unavailable")
        call(publish) { null }
        assertEquals(false, allowed(true))
        stateFailure = null
        call(publish) { null }
        defaultFailure = IllegalStateException("theme")
        assertEquals(false, allowed(false))
        val fatal = OutOfMemoryError("theme")
        defaultFailure = fatal
        assertSame(fatal, assertThrows(Throwable::class.java) { allowed(true) })
        val native = IllegalArgumentException("native publisher")
        assertSame(native, assertThrows(Throwable::class.java) { call(publish) { throw native } })
    }

    private fun <T> proxy(type: Class<T>, body: (Any, String, Array<out Any?>?) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, args -> body(self, method.name, args) })
}
