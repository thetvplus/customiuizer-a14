package tv.withaibuild.customiuizer.mods

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class VolumeCursorKeysTest {
    class OverridingIme : android.inputmethodservice.InputMethodService() {
        var nativeDowns = 0
        override fun onKeyDown(code: Int, event: KeyEvent): Boolean { nativeDowns++; return false }
    }

    @Test fun frameworkSessionHandlesVolumeBeforeAnImeOverrideAndCompletesEachKeyOnce() {
        val service = org.robolectric.Robolectric.buildService(OverridingIme::class.java).create()
        val ime = service.get()
        val session = ime.InputMethodSessionImpl().apply { isEnabled = true }
        val owner = android.inputmethodservice.AbstractInputMethodService.AbstractInputMethodSessionImpl::class.java
            .getDeclaredField("this\$0").apply { isAccessible = true }
        val hook = Controls.volumeCursorSessionHook(owner)
        val editor = Editor()
        tv.withaibuild.customiuizer.mods.utils.XposedHelpers.setAdditionalInstanceField(ime,
            "customiuizer_volume_cursor_keys", VolumeCursorKeys(editor))
        val previous = org.robolectric.util.ReflectionHelpers.getStaticField<Controls.ControlsSnapshot>(Controls::class.java, "controlsConfig")
        org.robolectric.util.ReflectionHelpers.setStaticField(Controls::class.java, "controlsConfig", enabled)
        var completions = 0
        val callback = android.view.inputmethod.InputMethodSession.EventCallback { _, handled -> assertTrue(handled); completions++ }
        class Chain(private val key: KeyEvent, private val callbackValue: Any? = callback) : io.github.libxposed.api.XposedInterface.Chain {
            var proceeds = 0
            override fun getExecutable() = session.javaClass.getMethod("dispatchKeyEvent", Int::class.javaPrimitiveType,
                KeyEvent::class.java, android.view.inputmethod.InputMethodSession.EventCallback::class.java)
            override fun getThisObject(): Any = session
            override fun getArgs(): List<Any?> = error("Read individual arguments")
            override fun getArg(index: Int): Any? = when (index) { 0 -> 11; 1 -> key; 2 -> callbackValue; else -> error("index") }
            override fun proceed(): Any? { proceeds++; return null }
            override fun proceed(args: Array<Any?>): Any? = error("Original arguments must be retained")
            override fun proceedWith(receiver: Any): Any? = error("Original receiver must be retained")
            override fun proceedWith(receiver: Any, args: Array<Any?>): Any? = error("Original receiver must be retained")
        }
        try {
            val down = Chain(event()); hook.intercept(down)
            assertEquals(0, down.proceeds); assertEquals(1, completions); assertEquals(1, editor.moves.size)
            val up = Chain(event(action = KeyEvent.ACTION_UP)); hook.intercept(up)
            assertEquals(0, up.proceeds); assertEquals(2, completions); assertEquals(0, ime.nativeDowns)
            val ordinary = Chain(event(code = KeyEvent.KEYCODE_A)); hook.intercept(ordinary)
            assertEquals(1, ordinary.proceeds); assertEquals(2, completions)
            val absentCallback = Chain(event(), null); hook.intercept(absentCallback)
            assertEquals(1, absentCallback.proceeds); assertEquals(1, editor.moves.size)
            session.isEnabled = false
            val disabled = Chain(event()); hook.intercept(disabled)
            assertEquals(1, disabled.proceeds); assertEquals(2, completions)
        } finally {
            org.robolectric.util.ReflectionHelpers.setStaticField(Controls::class.java, "controlsConfig", previous)
            service.destroy()
        }
    }
    private val enabled = Controls.ControlsSnapshot(volumeCursorEnabled = true)
    private class Editor : VolumeCursorKeys.Editor {
        var current: VolumeCursorKeys.Target? = VolumeCursorKeys.Target(Any(), "editor.app")
        var reads = 0
        var result = VolumeCursorKeys.Move.SENT
        var failure: Throwable? = null
        val moves = mutableListOf<Pair<Any, Int>>()
        override fun target(): VolumeCursorKeys.Target? { reads++; return current }
        override fun move(connection: Any, direction: Int): VolumeCursorKeys.Move {
            failure?.let { throw it }; moves.add(connection to direction); return result
        }
    }
    private fun event(code: Int = KeyEvent.KEYCODE_VOLUME_UP, action: Int = KeyEvent.ACTION_DOWN,
        repeat: Int = 0, time: Long = 100, device: Int = 3, flags: Int = 0, modifiers: Int = 0) =
        KeyEvent(time, time + repeat * 10 + action, action, code, repeat, modifiers, device, 0, flags)

    @Test fun bothDirectionsSendOneCursorMoveAndOwnTheirMatchingRelease() {
        val editor = Editor(); val keys = VolumeCursorKeys(editor)
        for ((code, direction) in listOf(KeyEvent.KEYCODE_VOLUME_UP to KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_VOLUME_DOWN to KeyEvent.KEYCODE_DPAD_RIGHT)) {
            assertTrue(keys.onDown(code, event(code), enabled))
            val reads = editor.reads
            assertTrue(keys.onUp(code, event(code, KeyEvent.ACTION_UP)))
            assertEquals("Release must not re-query the editor or package", reads, editor.reads)
            assertEquals(direction, editor.moves.last().second)
        }
    }
    @Test fun exclusionUsesTheActualEditorAndDoesNotCaptureEitherHalf() {
        val editor = Editor(); val keys = VolumeCursorKeys(editor)
        val config = enabled.copy(volumeCursorApps = setOf("editor.app"))
        assertFalse(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), config))
        assertFalse(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP)))
        assertTrue(editor.moves.isEmpty())
    }
    @Test fun hiddenOrMissingEditorNeverSwallowsKeysAndOrphanUpDoesNotQueryIt() {
        val editor = Editor().apply { current = null }; val keys = VolumeCursorKeys(editor)
        assertFalse(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP)))
        assertEquals(0, editor.reads)
        assertFalse(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled))
        assertTrue(editor.moves.isEmpty())
    }
    @Test fun acceptedDownStillOwnsUpAfterKeyboardHidesOrConfigurationChanges() {
        val editor = Editor(); val keys = VolumeCursorKeys(editor)
        assertTrue(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled))
        editor.current = null
        assertTrue(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP)))
        assertFalse(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP)))
        assertEquals(1, editor.moves.size)
    }
    @Test fun reverseIsFrozenForOnePressAndRepeatsAreNotInventedOrDuplicated() {
        val editor = Editor(); val keys = VolumeCursorKeys(editor)
        assertTrue(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled.copy(volumeCursorReverse = true)))
        assertTrue(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled))
        assertTrue(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(repeat = 3), enabled))
        assertTrue(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(repeat = 3), enabled))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT), editor.moves.map { it.second })
    }
    @Test fun repeatWithoutAnOwnedInitialDownRemainsNative() {
        val editor = Editor(); val keys = VolumeCursorKeys(editor)
        assertFalse(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(repeat = 1), enabled))
        assertTrue(editor.moves.isEmpty())
        assertEquals(0, editor.reads)
    }
    @Test fun connectionReplacementCannotMoveTheNewApplicationsCursorDuringAHeldKey() {
        val editor = Editor(); val keys = VolumeCursorKeys(editor)
        keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled)
        editor.current = VolumeCursorKeys.Target(Any(), "other.app")
        assertTrue(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(repeat = 1), enabled))
        assertTrue(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP)))
        assertEquals(1, editor.moves.size)
    }
    @Test fun addingAnExclusionOrDisablingStopsRepeatsButKeepsTheOwnedRelease() {
        for (config in listOf(enabled.copy(volumeCursorApps = setOf("editor.app")), enabled.copy(volumeCursorEnabled = false))) {
            val editor = Editor(); val keys = VolumeCursorKeys(editor)
            keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled)
            assertTrue(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(repeat = 1), config))
            assertTrue(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP)))
            assertEquals(1, editor.moves.size)
        }
    }
    @Test fun foreignDeviceAndOldReleaseCannotClearTheCurrentPress() {
        val editor = Editor(); val keys = VolumeCursorKeys(editor)
        keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled)
        assertFalse(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP, device = 9)))
        assertFalse(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP, time = 99)))
        assertTrue(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP)))
    }
    @Test fun twoVolumeKeysHaveIndependentLeasesAndCancelledUpOnlyReleasesItsOwnKey() {
        val editor = Editor(); val keys = VolumeCursorKeys(editor)
        keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled)
        keys.onDown(KeyEvent.KEYCODE_VOLUME_DOWN, event(KeyEvent.KEYCODE_VOLUME_DOWN), enabled)
        assertTrue(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_CANCELED)))
        assertTrue(keys.onUp(KeyEvent.KEYCODE_VOLUME_DOWN, event(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP)))
        assertEquals(2, editor.moves.size)
    }
    @Test fun refusedCursorDownDoesNotAcquireARelease() {
        val editor = Editor().apply { result = VolumeCursorKeys.Move.DECLINED }; val keys = VolumeCursorKeys(editor)
        assertFalse(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled))
        assertFalse(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP)))
    }
    @Test fun failedCursorUpStopsFurtherRepeatsWithoutReplayingTheVolumeKey() {
        val editor = Editor().apply { result = VolumeCursorKeys.Move.STOP_REPEAT }; val keys = VolumeCursorKeys(editor)
        assertTrue(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled))
        assertTrue(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(repeat = 1), enabled))
        assertTrue(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP)))
        assertEquals(1, editor.moves.size)
    }
    @Test fun repeatFailureKeepsItsOriginalLeaseAndFatalStillPropagates() {
        val editor = Editor(); val keys = VolumeCursorKeys(editor)
        keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled)
        editor.failure = IllegalStateException("dead editor")
        assertTrue(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(repeat = 1), enabled))
        assertTrue(keys.onUp(KeyEvent.KEYCODE_VOLUME_UP, event(action = KeyEvent.ACTION_UP)))
        editor.failure = null
        keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(time = 200), enabled)
        val fatal = OutOfMemoryError("fatal")
        editor.failure = fatal
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) {
            keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(repeat = 1, time = 200), enabled)
        })
    }
    @Test fun unrelatedModifiedVirtualAndDisabledKeysNeverQueryAnEditor() {
        val editor = Editor(); val keys = VolumeCursorKeys(editor)
        assertFalse(keys.onDown(KeyEvent.KEYCODE_ENTER, event(KeyEvent.KEYCODE_ENTER), enabled))
        assertFalse(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(modifiers = KeyEvent.META_CTRL_ON), enabled))
        assertFalse(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(flags = KeyEvent.FLAG_VIRTUAL_HARD_KEY), enabled))
        assertFalse(keys.onDown(KeyEvent.KEYCODE_VOLUME_UP, event(), enabled.copy(volumeCursorEnabled = false)))
        assertEquals(0, editor.reads)
    }
    @Test fun nativeSenderUsesTheCapturedConnectionAndAlwaysCompletesAnAcceptedDpadPair() {
        val events = mutableListOf<Triple<Int, Int, Int>>()
        val connection = connection { event -> events.add(Triple(event.action, event.keyCode, event.flags)); true }
        assertEquals(VolumeCursorKeys.Move.SENT, VolumeCursorKeys.send(connection, KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), events.map { it.first })
        assertTrue(events.all { it.second == KeyEvent.KEYCODE_DPAD_LEFT &&
            it.third and KeyEvent.FLAG_SOFT_KEYBOARD != 0 && it.third and KeyEvent.FLAG_KEEP_TOUCH_MODE != 0 })
    }
    @Test fun nativeSenderDistinguishesRefusedDownFromFailedUp() {
        var calls = 0
        assertEquals(VolumeCursorKeys.Move.DECLINED, VolumeCursorKeys.send(connection { calls++; false }, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(1, calls)
        assertEquals(VolumeCursorKeys.Move.STOP_REPEAT, VolumeCursorKeys.send(connection { it.action == KeyEvent.ACTION_DOWN }, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(VolumeCursorKeys.Move.STOP_REPEAT, VolumeCursorKeys.send(connection {
            if (it.action == KeyEvent.ACTION_UP) error("connection closed") else true
        }, KeyEvent.KEYCODE_DPAD_RIGHT))
    }
    private fun connection(send: (KeyEvent) -> Boolean): InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader, arrayOf(InputConnection::class.java)) { _, method, args ->
        if (method.name == "sendKeyEvent") send(args!![0] as KeyEvent) else error("Unexpected input read: ${method.name}")
    } as InputConnection
}
