package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Message
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks.NetSpeedTickAccess

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NativeNetworkSpeedHookTest {
    @Test
    fun defaultAndInvalidIntervalsLeaveTheNativeSuccessorInPlace() {
        val owner = Controller()
        val handler = owner.mBgHandler
        val tick = checkNotNull(NetSpeedTickAccess.resolve(Controller::class.java))
        for (delay in listOf(4000L, 0L, -1000L)) {
            val before = handler.enqueues
            val hook = SystemUIStatusBarHooks.createNetSpeedIntervalHook(tick) { delay }
            val result = Any()
            val chain = Chain(handler, Message.obtain(handler, TICK)) {
                handler.sendEmptyMessageDelayed(TICK, 4000L)
                result
            }

            assertSame(result, hook.intercept(chain))
            assertEquals(1, chain.nativeCalls)
            assertEquals(before + 1, handler.enqueues)
            shadowOf(handler.looper).idleFor(Duration.ofMillis(3999))
            assertEquals(0, handler.delivered)
            shadowOf(handler.looper).idleFor(Duration.ofMillis(1))
            assertEquals(1, handler.delivered)
            handler.delivered = 0
        }
    }

    @Test
    fun customIntervalReplacesOnlyTheSuccessfulOwnedBackgroundTick() {
        val owner = Controller()
        val tick = checkNotNull(NetSpeedTickAccess.resolve(Controller::class.java))
        val hook = SystemUIStatusBarHooks.createNetSpeedIntervalHook(tick) { 1000L }
        val failure = IllegalStateException("native tick")
        val background = owner.mBgHandler
        val other = TickHandler(owner)
        val unrelated = Chain(other, Message.obtain(other, TICK)) { Unit }
        val failed = Chain(background, Message.obtain(background, TICK)) { throw failure }

        hook.intercept(unrelated)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { hook.intercept(failed) })
        assertFalse(other.hasMessages(TICK))
        assertFalse(background.hasMessages(TICK))

        val chain = Chain(background, Message.obtain(background, TICK)) {
            background.sendEmptyMessageDelayed(TICK, 4000L)
        }
        hook.intercept(chain)
        assertEquals(1, chain.nativeCalls)
        assertEquals(2, background.enqueues)
        shadowOf(background.looper).idleFor(Duration.ofSeconds(1))
        assertEquals(1, background.delivered)
        assertFalse(background.hasMessages(TICK))
    }

    @Test
    fun clearAndHiddenTextKeepTheirNativeArgumentsWithoutFormatting() {
        val owner = Controller()
        val custom = arrayOf<String?>("custom", "")
        var formats = 0
        val hook = SystemUIStatusBarHooks.createDetailedNetSpeedTextHook(
            field(Controller::class.java, "EMPTY"),
            field(Controller::class.java, "mNetworkSpeedState"),
            field(NativeState::class.java, "visible"),
        ) {
            formats++
            custom
        }
        val shownInput = arrayOf("1", "KB/s")
        val clear = Chain(owner, owner.EMPTY) { assertSame(owner.EMPTY, it) }
        val hidden = Chain(owner, shownInput) { assertSame(shownInput, it) }

        hook.intercept(clear)
        owner.mNetworkSpeedState.visible = false
        hook.intercept(hidden)
        assertEquals(0, formats)
        assertEquals(0, clear.argumentCopies)
        assertEquals(0, hidden.argumentCopies)

        owner.mNetworkSpeedState.visible = true
        hook.intercept(Chain(owner, shownInput) { assertSame(custom, it) })
        assertEquals(1, formats)
    }

    private fun field(owner: Class<*>, name: String): Field =
        owner.getDeclaredField(name).apply { isAccessible = true }

    private class NativeState(@JvmField var visible: Boolean = true)

    private class Controller {
        @JvmField val mBgHandler = TickHandler(this)
        @JvmField val mNetworkSpeedState = NativeState()
        @JvmField val EMPTY = arrayOf("", "")
    }

    private class TickHandler(@JvmField val `this$0`: Controller) : Handler(Looper.getMainLooper()) {
        var enqueues = 0
        var delivered = 0
        override fun sendMessageAtTime(msg: Message, uptimeMillis: Long): Boolean {
            enqueues++
            return super.sendMessageAtTime(msg, uptimeMillis)
        }
        override fun handleMessage(msg: Message) { delivered++ }
    }

    private class Chain(
        val owner: Any,
        val argument: Any,
        val native: (Any?) -> Any?,
    ) : XposedInterface.Chain {
        var nativeCalls = 0
        var argumentCopies = 0
        override fun getExecutable(): Executable = error("unused")
        override fun getThisObject() = owner
        override fun getArg(index: Int): Any { assertEquals(0, index); return argument }
        override fun getArgs(): List<Any?> { argumentCopies++; return listOf(argument) }
        override fun proceed(): Any? { nativeCalls++; return native(argument) }
        override fun proceed(args: Array<Any?>): Any? { nativeCalls++; return native(args.single()) }
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }

    private companion object {
        const val TICK = 200001
    }
}
