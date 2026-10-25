package tv.withaibuild.customiuizer.mods.island

import android.os.UserHandle
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandFreeformAccessTest {
    private val access = IslandFreeformAccess(Manager::class.java, Callback::class.java, Stack::class.java)
    @After fun clear() { Manager.stacks = emptyList() }
    @Test fun inventoryUsesTheCurrentUserAndDefaultDisplayAndDistinguishesUnknownFromEmpty() {
        val current = UserHandle.getUserHandleForUid(10000)
        Manager.stacks = null
        assertNull(access.hasWindow(current))
        Manager.stacks = emptyList()
        assertEquals(false, access.hasWindow(current))
        Manager.stacks = listOf(Stack(1, 0), Stack(0, 10))
        assertEquals(false, access.hasWindow(current))
        Manager.stacks = listOf(Stack(0, 0), Stack(0, 10))
        assertEquals(true, access.hasWindow(current))
        assertEquals(0, Manager.requestedDisplay)
        assertEquals("dispatchFreeFormStackModeChanged", access.changed.name)
    }
    @Test fun sourceExclusionReadsOnlyCurrentUserPackagesAndDoesNotGuessMissingOwners() {
        val current = UserHandle.getUserHandleForUid(10000)
        Manager.stacks = listOf(Stack(0, 0, "video.app"), Stack(0, 10, "music.app"))
        assertEquals(setOf("video.app"), access.snapshot(current).packages)
        Manager.stacks = listOf(Stack(0, 0))
        assertNull(access.snapshot(current).packages)
    }
    class Stack(@JvmField val displayId: Int, @JvmField val userId: Int, @JvmField val packageName: String? = null)
    class Callback { fun dispatchFreeFormStackModeChanged(action: Int, info: Stack) = Unit }
    class Manager {
        companion object {
            var stacks: List<Stack>? = emptyList()
            var requestedDisplay = -1
            @JvmStatic fun getAllFreeFormStackInfosOnDisplay(display: Int): List<Stack>? { requestedDisplay = display; return stacks }
        }
    }
}
