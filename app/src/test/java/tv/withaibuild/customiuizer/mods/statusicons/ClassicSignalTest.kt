package tv.withaibuild.customiuizer.mods.statusicons

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.android.systemui.statusbar.StatusBarMobileView
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.feature.ClassicSignalFeature
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClassicSignalTest {
    private val savedModule = XposedHelpers.moduleInst
    private val hooks = LinkedHashMap<String, XposedInterface.Hooker>()
    private val preferences = MainModule.mPrefs.getAll()
    private var attempts = 0
    private var failAt = 0
    private var unhooks = 0
    @Before fun setup() {
        MainModule.mPrefs.clear(); MainModule.mPrefs.put("system_statusbar_signal_dots", true)
        XposedHelpers.moduleInst = proxy(XposedInterface::class.java) { name, args ->
            assertEquals("hook", name); builder(args!![0] as Method)
        }
    }
    @After fun cleanup() { XposedHelpers.moduleInst = savedModule; MainModule.mPrefs.replaceSnapshot(preferences) }
    private fun builder(method: Method): XposedInterface.HookBuilder = proxy(XposedInterface.HookBuilder::class.java) { name, args ->
        when (name) {
            "setPriority", "setExceptionMode" -> builder(method)
            "intercept" -> {
                hooks[method.name] = args!![0] as XposedInterface.Hooker
                if (++attempts == failAt) throw IllegalStateException("install")
                proxy(XposedInterface.HookHandle::class.java) { member, _ -> when (member) {
                    "getExecutable" -> method
                    "unhook" -> { unhooks++; null }
                    else -> throw AssertionError(member)
                } }
            }
            else -> throw AssertionError(name)
        }
    }
    private fun install(loader: ClassLoader = StatusBarMobileView::class.java.classLoader!!) = ClassicSignalHooks.install(
        proxy(PackageReadyParam::class.java) { name, _ -> when (name) {
            "getClassLoader" -> loader
            "getPackageName" -> "com.android.systemui"
            else -> throw AssertionError(name)
        } })
    private fun host() = StatusBarMobileView().apply {
        mMobile = ImageView(ApplicationProvider.getApplicationContext()).apply {
            layoutParams = FrameLayout.LayoutParams(12, 20); setImageDrawable(ColorDrawable(Color.BLUE))
        }
        mState = StatusBarSignalPolicy.MobileIconState().apply { strengthId = 103 }
    }
    private inner class Chain(private val host: StatusBarMobileView, private val name: String = "applyMobileState", private val native: () -> Unit = {}) : XposedInterface.Chain {
        var calls = 0
        val result = Any()
        fun invoke() = hooks.getValue(name).intercept(this)
        override fun getExecutable() = if (name == "applyMobileState") host.javaClass.getDeclaredMethod(name, StatusBarSignalPolicy.MobileIconState::class.java) else host.javaClass.getDeclaredMethod(name)
        override fun getThisObject(): Any = host
        override fun getArg(index: Int): Any? = host.mState
        override fun getArgs(): List<Any?> = throw AssertionError("argument array")
        override fun proceed(): Any { calls++; native(); return result }
        override fun proceed(args: Array<Any?>): Any? = throw AssertionError("replacement arguments")
        override fun proceedWith(receiver: Any): Any? = throw AssertionError("receiver")
        override fun proceedWith(receiver: Any, args: Array<Any?>): Any? = throw AssertionError("receiver")
    }
    @Test fun onlyActualTableMembershipMapsLevelsIncludingZero() {
        val ids = intArrayOf(900, 12, 41, 7, 82, 5)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), ids.map { ClassicSignalHooks.dotCount(ids, it) })
        assertNull(ClassicSignalHooks.dotCount(ids, 901))
        assertEquals(listOf(0, 1, 3, 4, 5), (1..5).map { ClassicSignalHooks.dotCount(intArrayOf(1, 2, 3, 4, 5), it) })
    }
    @Test fun enableIsIndependentAndDuoHasOnePresentationOwner() {
        val prefs = PrefMap()
        assertFalse(ClassicSignalFeature.evaluateEnabled(prefs))
        prefs.put("system_statusbar_signal_dots", true); assertTrue(ClassicSignalFeature.evaluateEnabled(prefs))
        prefs.put("system_statusbar_duo", true); assertFalse(ClassicSignalFeature.evaluateEnabled(prefs))
    }
    @Test fun nativeUpdatesTintFallbackAndSeparateSimViewsStayIndependent() {
        assertEquals(FeatureInstallResult.INSTALLED, install())
        val first = host(); val second = host().apply { mState!!.strengthId = 100 }
        val native = first.mMobile!!.drawable
        val chain = Chain(first); assertSame(chain.result, chain.invoke()); assertEquals(1, chain.calls)
        Chain(second).invoke()
        val ink = first.mMobile!!.drawable as ClassicSignalHooks.Dots
        assertEquals(3, ink.level); assertEquals(0, second.mMobile!!.drawable.level)
        assertEquals(34, first.mMobile!!.layoutParams.width)
        first.mMobile!!.imageTintList = ColorStateList.valueOf(Color.RED)
        Chain(first, "applyDarknessInternal").invoke(); assertSame(ink, first.mMobile!!.drawable)
        first.mState!!.strengthId = 999
        Chain(first).invoke(); assertSame(native, first.mMobile!!.drawable); assertEquals(12, first.mMobile!!.layoutParams.width)
        first.mState!!.strengthId = 105; Chain(first).invoke(); assertEquals(5, first.mMobile!!.drawable.level)
        MainModule.mPrefs.put("system_statusbar_duo", true); Chain(first).invoke(); assertSame(native, first.mMobile!!.drawable)
        MainModule.mPrefs.put("system_statusbar_duo", false); Chain(first).invoke()
        first.mState!!.airplane = true; Chain(first).invoke(); assertSame(native, first.mMobile!!.drawable)
        assertEquals(0, second.mMobile!!.drawable.level)
    }
    @Test fun failedInstallAndNativeExceptionsCannotChangeTheImage() {
        failAt = 2
        assertEquals(FeatureInstallResult.FAILED_TRANSIENT, install()); assertEquals(1, unhooks)
        val host = host(); val native = host.mMobile!!.drawable
        val chain = Chain(host); chain.invoke(); assertEquals(1, chain.calls); assertSame(native, host.mMobile!!.drawable)
        failAt = 0; assertEquals(FeatureInstallResult.INSTALLED, install())
        Chain(host).invoke(); assertTrue(host.mMobile!!.drawable is ClassicSignalHooks.Dots)
        val error = IllegalStateException("native state")
        val failing = Chain(host) { throw error }
        assertSame(error, runCatching { failing.invoke() }.exceptionOrNull()); assertEquals(1, failing.calls)
        assertSame(native, host.mMobile!!.drawable)
    }
    @Test fun nativeImageReplacementAndNewerGeometryAreNotOverwrittenOnRelease() {
        assertEquals(FeatureInstallResult.INSTALLED, install())
        val host = host(); val first = host.mMobile!!; val native = first.drawable
        Chain(host).invoke()
        host.mMobile = ImageView(first.context).apply { layoutParams = FrameLayout.LayoutParams(17, 20); setImageDrawable(ColorDrawable(Color.GREEN)) }
        Chain(host).invoke(); assertSame(native, first.drawable); assertEquals(12, first.layoutParams.width)
        val current = host.mMobile!!
        current.layoutParams.width = 22 // native density/theme update
        Chain(host).invoke(); assertEquals(34, current.layoutParams.width)
        MainModule.mPrefs.put("system_statusbar_signal_dots", false)
        Chain(host).invoke(); assertEquals(22, current.layoutParams.width)
        assertTrue(current.drawable is ColorDrawable)
    }
    @Test fun missingLoaderDoesNotPoisonACompatibleLoader() {
        val parent = StatusBarMobileView::class.java.classLoader!!
        val missing = object : ClassLoader(parent) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name.endsWith("TelephonyIcons")) throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        assertTrue(runCatching { install(missing) }.exceptionOrNull() is ClassNotFoundException)
        assertEquals(0, attempts); assertEquals(FeatureInstallResult.INSTALLED, install())
    }
    @Test fun emptyDotsAreOutlinedAndNativeTintAndAlphaReachTheActualPixels() {
        val ink = ClassicSignalHooks.Dots(3f).apply { setBounds(0, 0, 102, 60); level = 2; setTintList(ColorStateList.valueOf(Color.RED)) }
        val bitmap = Bitmap.createBitmap(102, 60, Bitmap.Config.ARGB_8888)
        try {
            ink.draw(Canvas(bitmap))
            assertEquals(Color.RED, bitmap.getPixel(10, 30)); assertEquals(Color.RED, bitmap.getPixel(31, 30))
            assertEquals(0, Color.alpha(bitmap.getPixel(52, 30)))
            assertTrue(Color.alpha(bitmap.getPixel(44, 30)) > 0)
            bitmap.eraseColor(Color.TRANSPARENT); ink.alpha = 100; ink.draw(Canvas(bitmap))
            assertEquals(100, Color.alpha(bitmap.getPixel(10, 30)))
        } finally { bitmap.recycle() }
    }
    @Test fun normalAndLockscreenInkUseTheMeasuredTemplateBounds() {
        for (lock in listOf(false, true)) {
            val width = if (lock) 127 else 102; val height = if (lock) 72 else 60
            val ink = ClassicSignalHooks.Dots(3f).apply { this.lock = lock; setBounds(0, 0, width, height); level = 5 }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                ink.draw(Canvas(bitmap))
                val occupied = android.graphics.Rect(width, height, 0, 0)
                for (y in 0 until height) for (x in 0 until width) if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                    occupied.left = minOf(occupied.left, x); occupied.top = minOf(occupied.top, y)
                    occupied.right = maxOf(occupied.right, x + 1); occupied.bottom = maxOf(occupied.bottom, y + 1)
                }
                assertEquals(if (lock) android.graphics.Rect(2, 26, 127, 47) else android.graphics.Rect(2, 22, 102, 38), occupied)
            } finally { bitmap.recycle() }
        }
    }
    private fun <T> proxy(type: Class<T>, body: (String, Array<out Any?>?) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> body(method.name, args) })!!
}
