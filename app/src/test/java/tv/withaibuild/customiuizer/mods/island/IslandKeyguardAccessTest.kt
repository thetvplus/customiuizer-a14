package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandKeyguardAccessTest {
    private val access = IslandKeyguardAccess(Owner::class.java, Callback::class.java)

    @Test fun unknownShowingOccludedAndIncompleteUnlocksAllStayNative() {
        val owner = Owner()
        assertFalse(access.isUnlocked(null))
        assertTrue(access.isUnlocked(owner))
        for (field in listOf("mShowing", "mKeyguardGoingAway", "mKeyguardFadingAway",
            "mPrimaryBouncerShowing", "mLaunchTransitionFadingAway")) {
            val flag = Owner::class.java.getField(field)
            flag.setBoolean(owner, true)
            assertFalse(field, access.isUnlocked(owner))
            flag.setBoolean(owner, false)
        }
        owner.mShowing = true; owner.mOccluded = true
        assertFalse("Occlusion does not mean unlocked", access.isUnlocked(owner))
        owner.mShowing = false; owner.mOccluded = false
        assertTrue(access.isUnlocked(owner))
    }

    @Test fun nativeEventsAreOwnedAndRemovedWithoutSubscribingToPerFrameDismissAmount() {
        val owner = Owner()
        var changes = 0
        val listener = access.listen(owner) { changes++ }
        assertEquals(listOf(listener), owner.callbacks)
        val callback = listener as Callback
        callback.onKeyguardShowingChanged()
        callback.onKeyguardGoingAwayChanged()
        callback.onKeyguardFadingAwayChanged()
        callback.onPrimaryBouncerShowingChanged()
        callback.onLaunchTransitionFadingAwayChanged()
        callback.onUnlockedChanged()
        callback.onKeyguardDismissAmountChanged()
        assertEquals(6, changes)
        access.unlisten(owner, listener)
        assertTrue(owner.callbacks.isEmpty())
        assertTrue(listener == listener)
        assertFalse(listener == Any())
    }

    @Test fun partialNativeRegistrationIsUndoneWhenAddFails() {
        val owner = Owner().apply { reject = true }
        try { access.listen(owner) { fail("must not dispatch") }; fail("expected registration failure") }
        catch (_: java.lang.reflect.InvocationTargetException) { }
        assertTrue(owner.callbacks.isEmpty())
    }

    interface Callback {
        fun onKeyguardShowingChanged()
        fun onKeyguardGoingAwayChanged()
        fun onKeyguardFadingAwayChanged()
        fun onPrimaryBouncerShowingChanged()
        fun onLaunchTransitionFadingAwayChanged()
        fun onUnlockedChanged()
        fun onKeyguardDismissAmountChanged()
    }
    class Owner {
        @JvmField var mShowing = false
        @JvmField var mOccluded = false
        @JvmField var mKeyguardGoingAway = false
        @JvmField var mKeyguardFadingAway = false
        @JvmField var mPrimaryBouncerShowing = false
        @JvmField var mLaunchTransitionFadingAway = false
        val callbacks = mutableListOf<Any>()
        var reject = false
        fun addCallback(callback: Any) { callbacks.add(callback); check(!reject) }
        fun removeCallback(callback: Any) { callbacks.remove(callback) }
    }
}
