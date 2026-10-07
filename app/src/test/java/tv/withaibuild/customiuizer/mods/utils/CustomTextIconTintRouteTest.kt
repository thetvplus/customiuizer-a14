package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.Context
import android.view.View
import com.android.systemui.statusbar.phone.MiuiLightDarkIconManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CustomTextIconTintRouteTest {

    private val context = object : Application() {}
    private val classLoader = FakeView::class.java.classLoader!!

    @Before
    fun setUp() {
        CustomTextIconTintRoute.releaseAll()
    }

    @After
    fun tearDown() {
        CustomTextIconTintRoute.releaseAll()
    }

    @Test
    fun registerAddsViewToDispatcherAndAppliesInitialTint() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        val handle = CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)

        assertNotNull(handle)
        assertTrue("View should be added to dispatcher", view in dispatcher.registered)
        assertEquals(1, dispatcher.addCount)
        assertEquals(1, view.onDarkChangedCount)
        assertEquals(1, view.listenerAddCount)
        assertTrue("View should receive initial tint", view.lastDarkTint != null)
    }

    @Test
    fun controlCenterOwnerTintIsNotOverwrittenByWallpaperCallbacks() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)
        // MiuiLightDarkIconManager.onIconAdded paints the Control Center view white.
        // setLight then skips identical values, even if wallpaper tint is reapplied.
        view.onDarkChanged(null, 0f, 0xFFFFFFFF.toInt())
        val handle = CustomTextIconTintRoute.registerForIconManager(
            view, MiuiLightDarkIconManager(), classLoader, "test", dispatcher)

        repeat(10) { dispatcher.dispatchTint(0xFF000000.toInt()) }

        assertEquals("wallpaper callbacks must not overwrite the owner's white tint",
            0xFFFFFFFF.toInt(), view.lastDarkTint)
        assertNull(handle)
        assertEquals(0, dispatcher.addCount)
        assertEquals(0, view.listenerAddCount)
        assertTrue(CustomTextIconTintRoute.registrations.isEmpty())
    }

    @Test
    fun ordinaryIconManagerStillTracksWallpaperTintAndReleases() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)
        val handle = CustomTextIconTintRoute.registerForIconManager(
            view, Any(), classLoader, "test", dispatcher)
        assertNotNull(handle)

        dispatcher.dispatchTint(0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), view.lastDarkTint)
        dispatcher.dispatchTint(0xFFFFFFFF.toInt())
        assertEquals(0xFFFFFFFF.toInt(), view.lastDarkTint)
        handle!!.release("fixture")
        assertEquals(1, dispatcher.removeCount)
        assertEquals(1, view.listenerRemoveCount)
        assertTrue(CustomTextIconTintRoute.registrations.isEmpty())
    }

    @Test
    fun detachRemovesReceiverButKeepsListenerForReattach() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)
        view.simulateDetach()
        view.dispatchOnAttachStateChangeListenerDetaches()

        assertFalse("View should be removed from dispatcher", view in dispatcher.registered)
        assertEquals(1, dispatcher.removeCount)
        // Normal detach must NOT remove the listener so re-attach can re-register.
        assertEquals(0, view.listenerRemoveCount)
        assertEquals(1, CustomTextIconTintRoute.registrations.size)
    }

    @Test
    fun reattachReregistersAndAppliesInitialTintAgain() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)
        view.simulateDetach()
        view.dispatchOnAttachStateChangeListenerDetaches()

        assertEquals(1, dispatcher.addCount)
        assertEquals(1, view.onDarkChangedCount)

        view.attached = true
        view.dispatchOnAttachStateChangeListenerAttached()

        assertTrue(view in dispatcher.registered)
        assertEquals(2, dispatcher.addCount)
        assertEquals(2, view.onDarkChangedCount)
    }

    @Test
    fun terminalDisposeRemovesListenerAndBlocksReattach() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        val handle = CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)
        handle.release("terminal-test")

        assertFalse(view in dispatcher.registered)
        assertEquals(1, dispatcher.removeCount)
        assertEquals(1, view.listenerRemoveCount)
        assertEquals(0, CustomTextIconTintRoute.registrations.size)

        view.attached = true
        view.dispatchOnAttachStateChangeListenerAttached()

        assertFalse(view in dispatcher.registered)
        assertEquals(1, dispatcher.addCount)
    }

    @Test
    fun releaseAllDisposesAllRegistrations() {
        val dispatcher = FakeDarkIconDispatcher()
        val view1 = FakeView(context, attached = true)
        val view2 = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view1, classLoader, "left", dispatcher)
        CustomTextIconTintRoute.register(view2, classLoader, "right", dispatcher)

        assertEquals(2, dispatcher.addCount)

        CustomTextIconTintRoute.releaseAll()

        assertTrue(dispatcher.registered.isEmpty())
        assertEquals(2, dispatcher.removeCount)
        assertEquals(2, view1.listenerRemoveCount + view2.listenerRemoveCount)
        assertEquals(0, CustomTextIconTintRoute.registrations.size)
    }

    @Test
    fun dispatcherNullIsReleasedOnReleaseAll() {
        val view = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view, classLoader, "test", null)
        assertEquals(1, CustomTextIconTintRoute.registrations.size)

        view.simulateDetach()
        view.dispatchOnAttachStateChangeListenerDetaches()

        CustomTextIconTintRoute.releaseAll()

        assertEquals(0, CustomTextIconTintRoute.registrations.size)
        assertEquals(1, view.listenerRemoveCount)
    }

    @Test
    fun addDarkReceiverExceptionIsReleasedOnReleaseAll() {
        val dispatcher = FakeDarkIconDispatcher(failAdd = true)
        val view = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)
        assertEquals(1, CustomTextIconTintRoute.registrations.size)

        view.simulateDetach()
        view.dispatchOnAttachStateChangeListenerDetaches()

        CustomTextIconTintRoute.releaseAll()

        assertEquals(0, CustomTextIconTintRoute.registrations.size)
        assertEquals(1, view.listenerRemoveCount)
    }

    @Test
    fun failedRegisterThenRegisterAgainDoesNotDuplicateListener() {
        val dispatcher = FakeDarkIconDispatcher(failAdd = true)
        val view = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)
        assertEquals(1, view.listenerAddCount)

        val dispatcher2 = FakeDarkIconDispatcher()
        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher2)

        assertEquals(2, view.listenerAddCount)
        assertEquals(1, view.listenerRemoveCount)
        assertTrue(view in dispatcher2.registered)
        assertEquals(1, CustomTextIconTintRoute.registrations.size)
    }

    @Test
    fun ownerReleaseBeforeDetachDisposesAndRemovesListener() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        val handle = CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)
        handle.release("owner-replaced")

        view.simulateDetach()
        view.dispatchOnAttachStateChangeListenerDetaches()

        assertEquals(1, dispatcher.removeCount)
        assertEquals(1, view.listenerRemoveCount)
    }

    @Test
    fun detachBeforeOwnerReleaseStillIdempotent() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        val handle = CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)
        view.simulateDetach()
        view.dispatchOnAttachStateChangeListenerDetaches()

        assertEquals(1, dispatcher.removeCount)

        handle.release("generation-replaced")

        assertEquals(1, dispatcher.removeCount)
        assertEquals(1, view.listenerRemoveCount)
    }

    @Test
    fun generationReplacementDisposesOldReceiver() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        val handle = CustomTextIconTintRoute.register(view, classLoader, "right", dispatcher)
        handle.release("generation-replaced")

        assertFalse(view in dispatcher.registered)
        assertEquals(1, dispatcher.removeCount)
        assertEquals(1, view.listenerRemoveCount)
    }

    @Test
    fun registerIsIdempotentForActiveView() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)
        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)

        assertEquals(1, dispatcher.addCount)
        assertEquals(1, view.listenerAddCount)
        assertEquals(1, view.onDarkChangedCount)
    }

    @Test
    fun detachThenReattachDoesNotAddDuplicateListener() {
        val dispatcher = FakeDarkIconDispatcher()
        val view = FakeView(context, attached = true)

        CustomTextIconTintRoute.register(view, classLoader, "test", dispatcher)
        view.simulateDetach()
        view.dispatchOnAttachStateChangeListenerDetaches()

        view.attached = true
        view.dispatchOnAttachStateChangeListenerAttached()

        assertEquals(1, view.listenerAddCount)
        assertEquals(2, dispatcher.addCount)
    }

    private class FakeDarkIconDispatcher(val failAdd: Boolean = false) {
        val registered = mutableListOf<View>()
        var addCount = 0
        var removeCount = 0
        var initialTintCount = 0

        @Suppress("unused")
        fun addDarkReceiver(view: View) {
            if (failAdd) throw RuntimeException("addDarkReceiver failed")
            registered.add(view)
            addCount++
            if (view is FakeView) {
                view.onDarkChanged(null, 0.5f, 0xFFFFFFFF.toInt())
                initialTintCount++
            }
        }

        @Suppress("unused")
        fun removeDarkReceiver(view: View) {
            registered.remove(view)
            removeCount++
        }

        fun dispatchTint(tint: Int) {
            for (view in registered) {
                (view as? FakeView)?.onDarkChanged(null, 0f, tint)
            }
        }
    }

    private class FakeView(context: Context, var attached: Boolean) : View(context) {
        val listeners = mutableListOf<View.OnAttachStateChangeListener>()
        var listenerAddCount = 0
        var listenerRemoveCount = 0
        var onDarkChangedCount = 0
        var lastDarkTint: Int? = null

        override fun isAttachedToWindow() = attached

        override fun addOnAttachStateChangeListener(listener: View.OnAttachStateChangeListener) {
            listeners.add(listener)
            listenerAddCount++
        }

        override fun removeOnAttachStateChangeListener(listener: View.OnAttachStateChangeListener) {
            if (listeners.remove(listener)) {
                listenerRemoveCount++
            }
        }

        fun simulateDetach() {
            attached = false
        }

        fun dispatchOnAttachStateChangeListenerAttached() {
            listeners.forEach { it.onViewAttachedToWindow(this) }
        }

        fun dispatchOnAttachStateChangeListenerDetaches() {
            val copy = listeners.toList()
            copy.forEach { it.onViewDetachedFromWindow(this) }
        }

        @Suppress("unused")
        fun onDarkChanged(areas: List<*>?, darkIntensity: Float, tint: Int) {
            onDarkChangedCount++
            lastDarkTint = tint
        }
    }
}
