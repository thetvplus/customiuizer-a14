package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.view.View
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.ReceiverRegistry
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Ownership contract for the Security Center sidebar expand receiver.
 *
 * The process-scoped registry must not keep a strong View after detach, and
 * [Various.releaseSideBarExpandReceiver] must unregister the exact instance.
 */
class SideBarExpandReceiverOwnershipTest {

    private class TrackableContext : Application() {
        val registeredReceivers = java.util.concurrent.CopyOnWriteArrayList<BroadcastReceiver>()
        val unregisteredReceivers = java.util.concurrent.CopyOnWriteArrayList<BroadcastReceiver>()

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent {
            if (receiver != null) registeredReceivers.add(receiver)
            return Intent("stub")
        }

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter?,
            broadcastPermission: String?,
            scheduler: Handler?,
            flags: Int
        ): Intent {
            if (receiver != null) registeredReceivers.add(receiver)
            return Intent("stub")
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            if (receiver != null) {
                unregisteredReceivers.add(receiver)
                registeredReceivers.remove(receiver)
            }
        }
    }

    @After
    fun tearDown() {
        getModuleReceiversMap().clear()
    }

    @Test
    fun createSideBarExpandReceiver_holdsViewOnlyWeakly() {
        val view = View(null as Context?)
        val receiver = Various.createSideBarExpandReceiver(view, 0)
        val viewRef = findCapturedWeakView(receiver)
        assertSame(view, viewRef.get())
        viewRef.clear()
        assertTrue(viewRef.get() == null)
    }

    @Test
    fun releaseSideBarExpandReceiver_unregistersExactInstance() {
        val context = TrackableContext()
        val view = View(null as Context?)
        val receiver = Various.createSideBarExpandReceiver(view, 0)
        val filter = IntentFilter("tv.withaibuild.customiuizer.action.ShowSideBar")

        assertTrue(
            ReceiverRegistry.registerModuleReceiver(
                context,
                Various.SIDE_BAR_EXPAND_RECEIVER_KEY,
                receiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        )
        assertTrue(context.registeredReceivers.contains(receiver))

        Various.releaseSideBarExpandReceiver(receiver)

        assertTrue(context.unregisteredReceivers.contains(receiver))
        assertFalse(context.registeredReceivers.contains(receiver))
        assertFalse(getModuleReceiversMap().containsKey(Various.SIDE_BAR_EXPAND_RECEIVER_KEY))
    }

    @Test
    fun releaseSideBarExpandReceiver_ignoresNullAndWrongInstance() {
        val context = TrackableContext()
        val live = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val other = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {}
        }
        val filter = IntentFilter("tv.withaibuild.customiuizer.action.ShowSideBar")
        assertTrue(
            ReceiverRegistry.registerModuleReceiver(
                context,
                Various.SIDE_BAR_EXPAND_RECEIVER_KEY,
                live,
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        )

        Various.releaseSideBarExpandReceiver(null)
        Various.releaseSideBarExpandReceiver(other)

        assertTrue(context.registeredReceivers.contains(live))
        assertTrue(context.unregisteredReceivers.isEmpty())
        assertTrue(getModuleReceiversMap().containsKey(Various.SIDE_BAR_EXPAND_RECEIVER_KEY))

        Various.releaseSideBarExpandReceiver(live)
        assertTrue(context.unregisteredReceivers.contains(live))
    }

    @Test
    fun ownerDetach_unregistersCurrentReceiver() {
        val context = TrackableContext()
        val owner = View(null as Context?)
        val receiver = Various.createSideBarExpandReceiver(owner, 0)
        registerSidebarReceiver(context, receiver)

        val registeredReceiver = arrayOf<BroadcastReceiver?>(receiver)
        val registeredOwnerView = arrayOf<WeakReference<View>?>(WeakReference(owner))
        val isHooked = booleanArrayOf(true, false)

        val toRelease = Various.takeSideBarExpandReceiverIfOwner(
            registeredReceiver,
            registeredOwnerView,
            isHooked,
            owner,
        )
        assertSame(receiver, toRelease)
        assertNull(registeredReceiver[0])
        assertNull(registeredOwnerView[0])
        assertFalse(isHooked[0])

        Various.releaseSideBarExpandReceiver(toRelease)
        assertTrue(context.unregisteredReceivers.contains(receiver))
        assertFalse(context.registeredReceivers.contains(receiver))
        assertFalse(getModuleReceiversMap().containsKey(Various.SIDE_BAR_EXPAND_RECEIVER_KEY))
    }

    @Test
    fun nonOwnerDetach_doesNotUnregisterCurrentReceiver() {
        val context = TrackableContext()
        val owner = View(null as Context?)
        val other = View(null as Context?)
        val receiver = Various.createSideBarExpandReceiver(owner, 0)
        registerSidebarReceiver(context, receiver)

        val registeredReceiver = arrayOf<BroadcastReceiver?>(receiver)
        val registeredOwnerView = arrayOf<WeakReference<View>?>(WeakReference(owner))
        val isHooked = booleanArrayOf(true, false)

        val toRelease = Various.takeSideBarExpandReceiverIfOwner(
            registeredReceiver,
            registeredOwnerView,
            isHooked,
            other,
        )
        assertNull(toRelease)
        assertSame(receiver, registeredReceiver[0])
        assertSame(owner, registeredOwnerView[0]?.get())
        assertTrue(isHooked[0])
        assertTrue(context.registeredReceivers.contains(receiver))
        assertTrue(context.unregisteredReceivers.isEmpty())
        assertTrue(getModuleReceiversMap().containsKey(Various.SIDE_BAR_EXPAND_RECEIVER_KEY))
    }

    @Test
    fun newerOwner_notReleasedByOldOwnerDetach() {
        val context = TrackableContext()
        val ownerA = View(null as Context?)
        val ownerB = View(null as Context?)
        val receiverA = Various.createSideBarExpandReceiver(ownerA, 0)
        val receiverB = Various.createSideBarExpandReceiver(ownerB, 1)
        registerSidebarReceiver(context, receiverA)
        registerSidebarReceiver(context, receiverB)

        val registeredReceiver = arrayOf<BroadcastReceiver?>(receiverB)
        val registeredOwnerView = arrayOf<WeakReference<View>?>(WeakReference(ownerB))
        val isHooked = booleanArrayOf(true, false)

        val toRelease = Various.takeSideBarExpandReceiverIfOwner(
            registeredReceiver,
            registeredOwnerView,
            isHooked,
            ownerA,
        )
        assertNull(toRelease)
        assertSame(receiverB, registeredReceiver[0])
        assertSame(ownerB, registeredOwnerView[0]?.get())
        assertTrue(isHooked[0])
        assertTrue(context.registeredReceivers.contains(receiverB))
        assertFalse(context.unregisteredReceivers.contains(receiverB))
    }

    @Test
    fun collectedOwner_otherDetachDoesNotUnregister() {
        val context = TrackableContext()
        val owner = View(null as Context?)
        val other = View(null as Context?)
        val receiver = Various.createSideBarExpandReceiver(owner, 0)
        registerSidebarReceiver(context, receiver)

        val ownerRef = WeakReference(owner)
        ownerRef.clear()
        val registeredReceiver = arrayOf<BroadcastReceiver?>(receiver)
        val registeredOwnerView = arrayOf<WeakReference<View>?>(ownerRef)
        val isHooked = booleanArrayOf(true, false)

        val toRelease = Various.takeSideBarExpandReceiverIfOwner(
            registeredReceiver,
            registeredOwnerView,
            isHooked,
            other,
        )
        assertNull(toRelease)
        assertSame(receiver, registeredReceiver[0])
        assertTrue(isHooked[0])
        assertTrue(context.registeredReceivers.contains(receiver))
        assertTrue(context.unregisteredReceivers.isEmpty())
    }

    @Test
    fun addSideBarExpandReceiverHook_detachUnregistersAndUsesWeakView() {
        val source = File("src/main/java/tv/withaibuild/customiuizer/mods/Various.kt").readText()
        val start = source.indexOf("fun AddSideBarExpandReceiverHook")
        assertTrue(start >= 0)
        val next = source.indexOf("\n    @JvmStatic\n    fun InterceptPermHook", start)
        val body = if (next >= 0) source.substring(start, next) else source.substring(start)
        assertTrue(body.contains("createSideBarExpandReceiver"))
        assertTrue(body.contains("releaseSideBarExpandReceiver"))
        assertTrue(body.contains("takeSideBarExpandReceiverIfOwner"))
        assertTrue(body.contains("registeredOwnerView"))
        assertTrue(body.contains("onViewDetachedFromWindow"))
        assertTrue(body.contains("FatalErrors.rethrowIfFatal"))
        assertTrue(source.contains("val viewRef = WeakReference(view)"))
    }

    private fun registerSidebarReceiver(context: TrackableContext, receiver: BroadcastReceiver) {
        assertTrue(
            ReceiverRegistry.registerModuleReceiver(
                context,
                Various.SIDE_BAR_EXPAND_RECEIVER_KEY,
                receiver,
                IntentFilter("tv.withaibuild.customiuizer.action.ShowSideBar"),
                Context.RECEIVER_NOT_EXPORTED,
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun getModuleReceiversMap(): ConcurrentHashMap<String, Any> {
        val field = ReceiverRegistry::class.java.getDeclaredField("moduleReceivers")
            .apply { isAccessible = true }
        return field.get(null) as ConcurrentHashMap<String, Any>
    }

    private fun findCapturedWeakView(receiver: BroadcastReceiver): WeakReference<View> {
        var current: Class<*>? = receiver.javaClass
        while (current != null) {
            for (field in current.declaredFields) {
                field.isAccessible = true
                val value = field.get(receiver)
                if (value is WeakReference<*>) {
                    val target = value.get()
                    if (target is View || target == null) {
                        @Suppress("UNCHECKED_CAST")
                        return value as WeakReference<View>
                    }
                }
            }
            current = current.superclass
        }
        error("createSideBarExpandReceiver must capture the View through a WeakReference")
    }
}
