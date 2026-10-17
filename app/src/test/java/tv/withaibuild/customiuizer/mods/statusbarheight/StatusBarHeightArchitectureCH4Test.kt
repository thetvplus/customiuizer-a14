package tv.withaibuild.customiuizer.mods.statusbarheight

import android.graphics.Rect
import android.util.DisplayMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarHeightArchitectureCH4Test {

    @Test
    fun isDecorInsetsInfo_knownClass() {
        val effect = effectForH4()
        assertTrue(effect.isDecorInsetsInfo(FakeDecorInsetsInfo()))
    }

    @Test
    fun isDecorInsetsInfo_foreignClass_rejected() {
        val effect = effectForH4()
        assertFalse(effect.isDecorInsetsInfo("not-info"))
    }

    @Test
    fun readNonDecorInsets_frozenField() {
        val effect = effectForH4()
        val info = FakeDecorInsetsInfo()
        val expected = Rect().apply { set(0, 0, 1080, 80) }
        info.mNonDecorInsets = expected

        assertSame(expected, effect.readNonDecorInsets(info))
    }

    @Test
    fun readNonDecorFrame_frozenField() {
        val effect = effectForH4()
        val info = FakeDecorInsetsInfo()
        val expected = Rect().apply { set(0, 0, 1080, 2400) }
        info.mNonDecorFrame = expected

        assertSame(expected, effect.readNonDecorFrame(info))
    }

    @Test
    fun readDisplayContentMetrics_frozenMethod() {
        val effect = effectForH4()
        val display = FakeDisplayContent()
        val expected = DisplayMetrics().apply {
            densityDpi = 160
            density = 1.0f
        }
        display.metrics = expected

        assertSame(expected, effect.readDisplayContentMetrics(display))
    }

    @Test
    fun readDisplayContentMetrics_missingMethod_returnsNull() {
        val effect = effectWithoutMetrics()
        assertNull(effect.readDisplayContentMetrics(FakeDisplayContent()))
    }

    @Test
    fun readDisplayContentMetrics_runtimeException_failClosed() {
        val effect = effectForH4()
        val display = FakeDisplayContent().apply {
            onGetDisplayMetrics = { throw RuntimeException("boom") }
        }

        assertNull(effect.readDisplayContentMetrics(display))
    }

    @Test
    fun readDisplayContentMetrics_oom_propagatesSameIdentity() {
        val effect = effectForH4()
        val oom = OutOfMemoryError("oom")
        val display = FakeDisplayContent().apply {
            onGetDisplayMetrics = { throw oom }
        }

        val thrown = try {
            effect.readDisplayContentMetrics(display)
            null
        } catch (t: Throwable) {
            t
        }

        assertSame(oom, thrown)
    }

    private fun effectForH4(): StatusBarHeightEffect {
        return effectWithMetrics(metricsMethod = FakeDisplayContent::class.java.getDeclaredMethod("getDisplayMetrics").also { it.isAccessible = true })
    }

    private fun effectWithoutMetrics(): StatusBarHeightEffect {
        return effectWithMetrics(metricsMethod = null)
    }

    private fun effectWithMetrics(metricsMethod: java.lang.reflect.Method?): StatusBarHeightEffect {
        val infoClass = FakeDecorInsetsInfo::class.java
        val decor = DecorInsetsCapability(
            infoClass = infoClass,
            updateMethod = infoClass.getDeclaredMethod(
                "update",
                FakeDisplayContent::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).also { it.isAccessible = true },
            displayContentClass = FakeDisplayContent::class.java,
            displayContentGetDisplayMetricsMethod = metricsMethod,
            nonDecorInsetsField = infoClass.getDeclaredField("mNonDecorInsets").also { it.isAccessible = true },
            nonDecorFrameField = infoClass.getDeclaredField("mNonDecorFrame").also { it.isAccessible = true },
        )
        val wm = WindowManagerCapability(
            windowStateClass = null,
            displayPolicyClass = null,
            windowStateAttrsField = null,
            windowStateDisplayContentField = null,
            windowStateWindowManagerServiceField = null,
            windowStateGetFrameMethod = null,
            windowStateGetDisplayMetricsMethod = null,
            windowStateGetDisplayIdMethod = null,
            windowStateWindowFramesField = null,
            windowFramesFrameField = null,
            clientWindowFramesClass = null,
            clientWindowFramesFrameField = null,
            layoutParamsClass = null,
            layoutParamsTypeField = null,
            layoutParamsHeightField = null,
            layoutParamsPackageNameField = null,
        )
        val insets = InsetsSourceCapability(
            sourceClass = null,
            setFrameOneArg = true,
            setFrameFourArg = true,
            typeInfo = InsetsTypeInfo(InsetsTypeEncoding.MODERN_PUBLIC, 1, 2, 128),
            typeField = null,
            getTypeMethod = null,
            getIdMethod = null,
            getFrameMethod = null,
        )
        val refresh = StatusBarHeightRefreshCapability(
            windowManagerServicePlacerField = null,
            windowSurfacePlacerRequestTraversalMethod = null,
            displayContentGetDisplayPolicyMethod = null,
            displayPolicyDecorInsetsField = null,
            decorInsetsInvalidateMethod = null,
        )
        return StatusBarHeightEffect(StatusBarHeightAbi(insets, wm, decor, refresh))
    }

    class FakeDecorInsetsInfo {
        @JvmField
        var mNonDecorInsets: Rect = Rect()

        @JvmField
        var mNonDecorFrame: Rect = Rect()

        fun update(displayContent: FakeDisplayContent, rotation: Int, w: Int, h: Int) {}
    }

    class FakeDisplayContent {
        @JvmField
        var metrics: DisplayMetrics = DisplayMetrics().apply {
            densityDpi = 160
            density = 1.0f
        }

        var onGetDisplayMetrics: (() -> Unit)? = null

        fun getDisplayMetrics(): DisplayMetrics {
            onGetDisplayMetrics?.invoke()
            return metrics
        }
    }
}
