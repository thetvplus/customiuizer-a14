package tv.withaibuild.customiuizer.mods

import android.view.View
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.*
import org.junit.Test

class ImeDismissButtonBoundaryTest {
    open class Button(@JvmField var value: Int = View.VISIBLE) {
        val writes = mutableListOf<Int>()
        fun getVisibility(): Int = value
        fun setVisibility(next: Int) { writes.add(next); value = next }
    }
    open class Nav {
        @JvmField var mNavigationIconHints = 1
        @JvmField var mNavBarMode = 2
        var button: Button? = Button()
        var backReads = 0
        var lookupFailure: Throwable? = null
        var nativeVisibility = View.VISIBLE
        val home = Button()
        val recents = Button()
        fun updateNavButtonIcons() { button?.value = nativeVisibility }
        fun getBackButton(): Button? { backReads++; lookupFailure?.let { throw it }; return button }
    }
    class DerivedNav : Nav()
    class WrongHints {
        @JvmField var mNavigationIconHints = 1L
        @JvmField var mNavBarMode = 2
        fun updateNavButtonIcons() {}
        fun getBackButton(): Button = Button()
    }
    class WrongUpdate {
        @JvmField var mNavigationIconHints = 1
        @JvmField var mNavBarMode = 2
        fun updateNavButtonIcons(): Int = 1
        fun getBackButton(): Button = Button()
    }
    class StaticUpdate {
        @JvmField var mNavigationIconHints = 1
        @JvmField var mNavBarMode = 2
        fun getBackButton(): Button = Button()
        companion object { @JvmStatic fun updateNavButtonIcons() {} }
    }
    class WrongBack {
        @JvmField var mNavigationIconHints = 1
        @JvmField var mNavBarMode = 2
        fun updateNavButtonIcons() {}
        fun getBackButton(): Any = Button()
    }
    private fun access(type: Class<*> = Nav::class.java) =
        requireNotNull(ImeDismissButtonAccess.resolve(type, Button::class.java))

    @Test fun onlyTheVisibleImeBackButtonOfTheCurrentOwnerIsHiddenAfterNativeUpdate() {
        val owner = Nav()
        owner.button!!.value = View.GONE
        val chain = Chain(owner)
        assertSame(chain.result, access().dispatch(chain))
        assertEquals(1, chain.proceeded)
        assertEquals(listOf(View.INVISIBLE), owner.button!!.writes)
        assertEquals(View.VISIBLE, owner.home.value)
        assertEquals(View.VISIBLE, owner.recents.value)
    }
    @Test fun absentImeOrNonGesturalNavigationNeverReadsOrWritesTheButton() {
        for ((hints, mode) in listOf(0 to 2, 2 to 2, 1 to 0, 1 to 1, 1 to 3)) {
            val owner = Nav().apply { mNavigationIconHints = hints; mNavBarMode = mode }
            val chain = Chain(owner)
            assertSame(chain.result, access().dispatch(chain))
            assertEquals(1, chain.proceeded)
            assertEquals(0, owner.backReads)
            assertTrue(owner.button!!.writes.isEmpty())
        }
    }
    @Test fun nativeFailureIsPropagatedWithoutAnyModuleButtonAccess() {
        val owner = Nav()
        val failure = IllegalStateException("native update failed")
        val chain = Chain(owner, nativeFailure = failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { access().dispatch(chain) })
        assertEquals(1, chain.proceeded)
        assertEquals(0, owner.backReads)
        assertTrue(owner.button!!.writes.isEmpty())
    }
    @Test fun nativeInvisibleAndGoneStatesRemainNativeWithoutRedundantWrites() {
        for (visibility in listOf(View.INVISIBLE, View.GONE)) {
            val owner = Nav().apply { nativeVisibility = visibility }
            access().dispatch(Chain(owner))
            assertEquals(visibility, owner.button!!.value)
            assertTrue(owner.button!!.writes.isEmpty())
        }
    }
    @Test fun keyboardAndNavigationModeChangesLeaveRestorationToTheNativeOwner() {
        val owner = Nav()
        val access = access()
        access.dispatch(Chain(owner))
        assertEquals(View.INVISIBLE, owner.button!!.value)
        owner.mNavBarMode = 0
        access.dispatch(Chain(owner))
        assertEquals(View.VISIBLE, owner.button!!.value)
        owner.mNavBarMode = 2; owner.mNavigationIconHints = 0
        access.dispatch(Chain(owner))
        assertEquals(View.VISIBLE, owner.button!!.value)
        assertEquals(1, owner.backReads)
    }
    @Test fun bindingIsNotRetainedAcrossCurrentButtonOrNavigationViewReplacement() {
        val owner = Nav()
        val access = access()
        val previous = owner.button!!
        access.dispatch(Chain(owner))
        val replacement = Button()
        owner.button = replacement
        access.dispatch(Chain(owner))
        assertEquals(listOf(View.INVISIBLE), previous.writes)
        assertEquals(listOf(View.INVISIBLE), replacement.writes)
        val newOwner = DerivedNav()
        access.dispatch(Chain(newOwner))
        assertEquals(listOf(View.INVISIBLE), newOwner.button!!.writes)
    }
    @Test fun missingButtonAndUnrelatedOwnerPreserveTheNativeResult() {
        val owner = Nav().apply { button = null }
        val absent = Chain(owner)
        assertSame(absent.result, access().dispatch(absent))
        val unrelated = Chain(Any())
        assertSame(unrelated.result, access().dispatch(unrelated))
        assertEquals(1, unrelated.proceeded)
    }
    @Test fun ordinaryModuleLookupFailureDoesNotRepeatNativeUpdateOrReplaceItsResult() {
        val owner = Nav().apply { lookupFailure = IllegalArgumentException("incompatible button") }
        val chain = Chain(owner)
        assertSame(chain.result, access().dispatch(chain))
        assertEquals(1, chain.proceeded)
        assertTrue(owner.button!!.writes.isEmpty())
    }
    @Test fun nativeAndWrappedModuleFatalFailuresStillPropagate() {
        val fatal = OutOfMemoryError("fatal")
        val native = Chain(Nav(), nativeFailure = fatal)
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { access().dispatch(native) })
        val owner = Nav().apply { lookupFailure = fatal }
        val module = Chain(owner)
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { access().dispatch(module) })
        assertEquals(1, native.proceeded); assertEquals(1, module.proceeded)
    }
    @Test fun completeInstanceSignaturesAreRequiredBeforeInstallingAnyHook() {
        assertNotNull(ImeDismissButtonAccess.resolve(DerivedNav::class.java, Button::class.java))
        assertNull(ImeDismissButtonAccess.resolve(WrongHints::class.java, Button::class.java))
        assertNull(ImeDismissButtonAccess.resolve(WrongUpdate::class.java, Button::class.java))
        assertNull(ImeDismissButtonAccess.resolve(StaticUpdate::class.java, Button::class.java))
        assertNull(ImeDismissButtonAccess.resolve(WrongBack::class.java, Button::class.java))
        assertNull(ImeDismissButtonAccess.resolve(Nav::class.java, String::class.java))
        assertNull(ImeDismissButtonAccess.resolve(Any::class.java, Button::class.java))
    }
    private class Chain(private val owner: Any, private val nativeFailure: Throwable? = null) : XposedInterface.Chain {
        val result = Any()
        var proceeded = 0
        override fun getExecutable(): Executable = error("unused")
        override fun getThisObject(): Any = owner
        override fun getArgs(): List<Any?> = error("must not copy arguments")
        override fun getArg(index: Int): Any? = error("no arguments")
        override fun proceed(): Any? {
            proceeded++
            nativeFailure?.let { throw it }
            (owner as? Nav)?.updateNavButtonIcons()
            return result
        }
        override fun proceed(args: Array<Any?>): Any? = error("unused")
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
