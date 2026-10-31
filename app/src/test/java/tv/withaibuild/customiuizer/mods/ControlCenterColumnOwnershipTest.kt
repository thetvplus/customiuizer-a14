package tv.withaibuild.customiuizer.mods

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ControlCenterColumnOwnershipTest {
    class Lookup
    class Layout {
        @JvmField val spanSizeLookup = Lookup()
        @JvmField var columns = 2
        fun getSpanSizeLookup() = spanSizeLookup
        fun setSpanCount(count: Int) { columns = count }
    }
    class Adapter { @JvmField val layoutManager = Layout() }
    class Distributor {
        @JvmField val leftAdapter = Adapter()
        @JvmField val rightAdapter = Adapter()
    }

    @Test fun eachDistributorBindsItsActualLeftAdapterWithoutChangingTheRight() {
        val lookups = mutableListOf<Class<*>>()
        repeat(3) {
            val owner = Distributor()
            SystemUIControlCenterHooks.configureLeftPanelColumns(owner, 5) { lookups.add(it) }
            assertEquals(5, owner.leftAdapter.layoutManager.columns)
            assertEquals(true, XposedHelpers.getAdditionalInstanceField(owner.leftAdapter, "leftAdapter"))
            assertEquals(2, owner.rightAdapter.layoutManager.columns)
            assertNull(XposedHelpers.getAdditionalInstanceField(owner.rightAdapter, "leftAdapter"))
        }
        assertEquals(listOf(Lookup::class.java, Lookup::class.java, Lookup::class.java), lookups)
    }
}
