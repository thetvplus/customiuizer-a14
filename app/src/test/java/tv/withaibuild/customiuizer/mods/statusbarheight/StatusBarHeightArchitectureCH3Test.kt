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

class StatusBarHeightArchitectureCH3Test {

    @Test
    fun isClientWindowFrames_knownClass() {
        val effect = effectForH3()
        val frames = ClientWindowFrames()
        assertTrue(effect.isClientWindowFrames(frames))
    }

    @Test
    fun isClientWindowFrames_foreignClass_rejected() {
        val effect = effectForH3()
        assertFalse(effect.isClientWindowFrames("not-frames"))
    }

    @Test
    fun readClientWindowFrame_frozenField() {
        val effect = effectForH3()
        val frames = ClientWindowFrames()
        val expected = Rect().apply { set(0, 0, 1080, 80) }
        frames.frame = expected

        val actual = effect.readClientWindowFrame(frames)

        assertSame(expected, actual)
    }

    @Test
    fun readClientWindowFrame_missingFrameField_returnsNull() {
        val effect = effectWithoutClientFrame()
        val frames = ClientWindowFrames()

        assertNull(effect.readClientWindowFrame(frames))
    }

    @Test
    fun resolveClientWindowFrames_singleSetFramesOverload_resolves() {
        val wm = StatusBarHeightResolver.resolveWindowManagerClass(
            WindowStateWithOneClientFrames::class.java,
            FakeLayoutParams::class.java,
        )

        assertNotNull(wm.clientWindowFramesClass)
        assertNotNull(wm.clientWindowFramesFrameField)
        assertTrue(wm.clientWindowFramesClass!!.isInstance(ClientWindowFrames()))
    }

    @Test
    fun resolveClientWindowFrames_multipleOverloadsSameClass_resolves() {
        val wm = StatusBarHeightResolver.resolveWindowManagerClass(
            WindowStateWithClientFramesOverloads::class.java,
            FakeLayoutParams::class.java,
        )

        assertNotNull(wm.clientWindowFramesClass)
        assertNotNull(wm.clientWindowFramesFrameField)
    }

    private fun effectForH3(): StatusBarHeightEffect {
        val wm = StatusBarHeightResolver.resolveWindowManagerClass(
            FakeWindowState::class.java,
            FakeLayoutParams::class.java,
        ).copy(
            clientWindowFramesClass = ClientWindowFrames::class.java,
            clientWindowFramesFrameField = ClientWindowFrames::class.java.getDeclaredField("frame").also { it.isAccessible = true },
        )
        val decor = DecorInsetsCapability(
            infoClass = null,
            updateMethod = null,
            displayContentClass = FakeDisplayContent::class.java,
            displayContentGetDisplayMetricsMethod = FakeDisplayContent::class.java.getDeclaredMethod("getDisplayMetrics").also { it.isAccessible = true },
            nonDecorInsetsField = null,
            nonDecorFrameField = null,
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
        return StatusBarHeightEffect(StatusBarHeightAbi(insets, wm, decor, StatusBarHeightRefreshCapability(null, null, null, null, null)))
    }

    private fun effectWithoutClientFrame(): StatusBarHeightEffect {
        val wm = StatusBarHeightResolver.resolveWindowManagerClass(
            FakeWindowState::class.java,
            FakeLayoutParams::class.java,
        ).copy(
            clientWindowFramesClass = ClientWindowFrames::class.java,
            clientWindowFramesFrameField = null,
        )
        val decor = DecorInsetsCapability(
            infoClass = null,
            updateMethod = null,
            displayContentClass = FakeDisplayContent::class.java,
            displayContentGetDisplayMetricsMethod = FakeDisplayContent::class.java.getDeclaredMethod("getDisplayMetrics").also { it.isAccessible = true },
            nonDecorInsetsField = null,
            nonDecorFrameField = null,
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
        return StatusBarHeightEffect(StatusBarHeightAbi(insets, wm, decor, StatusBarHeightRefreshCapability(null, null, null, null, null)))
    }

    class ClientWindowFrames {
        @JvmField
        var frame: Rect? = null
    }

    class WindowStateWithOneClientFrames {
        fun setFrames(frames: ClientWindowFrames) {}
        fun setFrames(left: Int, top: Int, right: Int, bottom: Int) {}
    }

    class WindowStateWithClientFramesOverloads {
        fun setFrames(frames: ClientWindowFrames) {}
        fun setFrames(frames: ClientWindowFrames, extra: Int) {}
    }

    class FakeWindowState {
        val mAttrs: FakeLayoutParams = FakeLayoutParams(
            type = 0,
            packageName = "",
            height = 0,
        )
        var mDisplayId = 0
        var mDisplayMetrics = DisplayMetrics().apply {
            densityDpi = 160
            density = 1.0f
        }
        var mDisplayContent = FakeDisplayContent(mDisplayMetrics)

        fun getDisplayId(): Int = mDisplayId
        fun getDisplayMetrics(): DisplayMetrics = mDisplayMetrics
    }

    class FakeDisplayContent(private val metrics: DisplayMetrics) {
        fun getDisplayMetrics(): DisplayMetrics = metrics
    }

    class FakeLayoutParams(var type: Int = 0, var packageName: String = "", var height: Int = 0)
}
