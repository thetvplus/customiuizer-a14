package tv.withaibuild.customiuizer.mods.utils.feature

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicIslandEventAdapterTest {
    @Test
    fun muteDndAndChargingUseTheSameSharedRendererToken() {
        val expected = DynamicIslandEvent.SHARED_RENDERER_TOKEN

        assertEquals(expected, DynamicIslandEventAdapter.sharedRendererTokenFor(DynamicIslandEventType.MUTE))
        assertEquals(expected, DynamicIslandEventAdapter.sharedRendererTokenFor(DynamicIslandEventType.DND))
        assertEquals(expected, DynamicIslandEventAdapter.sharedRendererTokenFor(DynamicIslandEventType.CHARGING))
        assertEquals(expected, DynamicIslandHost.rendererToken())
    }
}
