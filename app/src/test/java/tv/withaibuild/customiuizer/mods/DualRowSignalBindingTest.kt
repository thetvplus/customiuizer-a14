package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.util.DisplayMetrics
import android.widget.ImageView
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DualRowSignalBindingTest {
    @Test
    fun pairCaptureKeepsTheOriginalArgumentListAndDefersResourceReadsForSingleSim() {
        val resources = SignalResources()
        val controller = controller(resources)
        val binding = binding()
        val primary = IconState(subId = 7, strengthId = 4)
        val secondary = IconState(subId = 9, strengthId = 2).apply {
            showName = "5G"
            activityIn = true
            dataConnected = true
        }
        val single = arrayListOf(primary)
        val singleChain = Chain(controller, single) { assertSame(primary, single.single()) }
        binding.capturePair.intercept(singleChain)
        assertEquals(0, resources.nameLookups)
        assertEquals(1, singleChain.nativeCalls)

        val pair = arrayListOf(primary, secondary)
        val pairChain = Chain(controller, pair) {
            assertFalse(secondary.visible)
            assertEquals("5G", primary.showName)
            assertEquals(true, primary.activityIn)
            assertEquals(true, primary.dataConnected)
            pair.reverse() // The native controller consumes and reverses the original list.
        }
        binding.capturePair.intercept(pairChain)
        assertSame(secondary, pair[0])
        assertEquals(1, pairChain.nativeCalls)
        assertEquals(0, pairChain.argumentCopies)
        assertEquals(0, pairChain.argumentReplacements)
    }

    @Test
    fun nativeDarknessIsFollowedByOneWritePerRowUsingItsCurrentTint() {
        val binding = binding()
        val primary = publishPair(binding)
        val view = MobileView(primary).apply { mUseTint = true }
        val nativeTint = ColorStateList.valueOf(Color.GREEN)
        val chain = Chain(view) { view.mMobileRoaming.imageTintList = nativeTint }

        binding.drawAfterDarkness.intercept(chain)

        assertEquals(1, chain.nativeCalls)
        assertEquals(listOf(132), view.mMobile.resourceWrites)
        assertEquals(listOf(212), view.mSmallRoaming.resourceWrites)
        assertSame(nativeTint, view.mMobile.imageTintList)
        assertSame(nativeTint, view.mSmallRoaming.imageTintList)
    }

    @Test
    fun themedAssetsUseNativeLightModeAndZeroLevelForNoSignal() {
        val binding = binding(hasTintAssets = false)
        val view = MobileView(publishPair(binding, secondarySignalId = 0)).apply {
            mUseTint = true
            mLight = false
        }
        binding.drawAfterDarkness.intercept(Chain(view))
        assertEquals(listOf(131), view.mMobile.resourceWrites)
        assertEquals(listOf(201), view.mSmallRoaming.resourceWrites)
        assertEquals(Color.BLACK, view.mSmallRoaming.imageTintList!!.defaultColor)
    }

    @Test
    fun inactiveStatesAndFailedNativeDarknessDoNotDrawOverTheRom() {
        val binding = binding()
        val primary = publishPair(binding)
        for (state in listOf(null, IconState(9, 2), IconState(7, 2).apply { airplane = true },
            IconState(7, 2).apply { visible = false })) {
            val view = MobileView(state)
            val chain = Chain(view)
            binding.drawAfterDarkness.intercept(chain)
            assertEquals(1, chain.nativeCalls)
            assertEquals(emptyList<Int>(), view.mMobile.resourceWrites)
            assertEquals(emptyList<Int>(), view.mSmallRoaming.resourceWrites)
        }

        val view = MobileView(primary)
        val nativeFailure = IllegalStateException("native darkness")
        assertSame(nativeFailure, assertThrows(IllegalStateException::class.java) {
            binding.drawAfterDarkness.intercept(Chain(view) { throw nativeFailure })
        })
        assertEquals(emptyList<Int>(), view.mMobile.resourceWrites)
        assertEquals(emptyList<Int>(), view.mSmallRoaming.resourceWrites)
    }

    private fun binding(hasTintAssets: Boolean = true) = SystemUIStatusBarHooks.DualRowSignalBinding(
        IconController::class.java, MobileView::class.java, IconState::class.java,
        Array(2) { slot -> Array(6) { level -> IntArray(3) { mode -> (slot + 1) * 100 + level * 10 + mode } } },
        hasTintAssets,
    )

    private fun publishPair(binding: SystemUIStatusBarHooks.DualRowSignalBinding, secondarySignalId: Int = 2): IconState {
        val primary = IconState(7, 4)
        binding.capturePair.intercept(Chain(controller(SignalResources()), listOf(primary, IconState(9, secondarySignalId))))
        return primary
    }

    private fun controller(resources: Resources) = IconController(object : ContextWrapper(null) {
        override fun getResources(): Resources = resources
    })

    private class IconController(@JvmField val mContext: Context)

    private class IconState(@JvmField val subId: Int, @JvmField val strengthId: Int) {
        @JvmField var visible = true
        @JvmField var airplane = false
        @JvmField var dataConnected = false
        @JvmField var showName: String? = null
        @JvmField var activityIn = false
        @JvmField var activityOut = false
    }

    private class MobileView(@JvmField val mState: IconState?) {
        @JvmField val mMobile = RecordingImage()
        @JvmField val mSmallRoaming = RecordingImage()
        @JvmField val mSmallHd = ImageView(RuntimeEnvironment.getApplication())
        @JvmField val mMobileRoaming = ImageView(RuntimeEnvironment.getApplication())
        @JvmField var mLight = true
        @JvmField var mUseTint = false
    }

    private class RecordingImage : ImageView(RuntimeEnvironment.getApplication()) {
        val resourceWrites = mutableListOf<Int>()
        override fun setImageResource(resId: Int) { resourceWrites.add(resId) }
    }

    @Suppress("DEPRECATION")
    private class SignalResources : Resources(RuntimeEnvironment.getApplication().assets, DisplayMetrics(), Configuration()) {
        var nameLookups = 0

        override fun getIdentifier(name: String, defType: String, defPackage: String): Int {
            nameLookups++
            return name.substringAfterLast('_').toIntOrNull()?.plus(1) ?: 7
        }
    }

    private class Chain(private val receiver: Any, private val states: List<IconState>? = null,
        private val native: () -> Unit = {}) : XposedInterface.Chain {
        var nativeCalls = 0
        var argumentCopies = 0
        var argumentReplacements = 0

        override fun getExecutable(): Executable = error("signature already resolved")
        override fun getThisObject(): Any = receiver
        override fun getArgs(): List<Any?> {
            argumentCopies++
            return listOf("mobile", states)
        }
        override fun getArg(index: Int): Any = checkNotNull(states).also { check(index == 1) }
        override fun proceed(): Any? {
            nativeCalls++
            native()
            return null
        }
        override fun proceed(args: Array<Any?>): Any? {
            argumentReplacements++
            return proceed()
        }
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
