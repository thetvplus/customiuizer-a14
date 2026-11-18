package tv.withaibuild.customiuizer.mods

import android.app.Application
import miui.systemui.controlcenter.panel.main.MainPanelContentDistributor
import miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ControlCenterColumnOwnershipTest {
    @Test fun eachDistributorBindsItsActualLeftAdapterWithoutChangingTheRight() {
        val lookups = mutableListOf<Class<*>>()
        repeat(3) {
            val owner = MainPanelContentDistributor(emptyList())
            SystemUIControlCenterHooks.configureLeftPanelColumns(owner, 5) { lookups.add(it) }
            assertEquals(5, owner.leftAdapter.layoutManager.columns)
            assertEquals(true, XposedHelpers.getAdditionalInstanceField(owner.leftAdapter, "leftAdapter"))
            assertEquals(2, owner.rightAdapter.layoutManager.columns)
            assertNull(XposedHelpers.getAdditionalInstanceField(owner.rightAdapter, "leftAdapter"))
        }
        assertEquals(List(3) { MainPanelAdapter.Lookup::class.java }, lookups)
    }
}
