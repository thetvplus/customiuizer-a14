package tv.withaibuild.customiuizer.mods

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import java.lang.ref.WeakReference
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Two physical key leases owned by one IME service. No editor text, timers or foreground queries. */
internal class VolumeCursorKeys(private val editor: Editor) {
    data class Target(val connection: Any, val packageName: String)
    enum class Move { DECLINED, SENT, STOP_REPEAT }
    interface Editor {
        fun target(): Target?
        fun move(connection: Any, direction: Int): Move
    }
    private class Press(val device: Int, val downTime: Long, val direction: Int, target: Target,
        var repeat: Int, var repeatable: Boolean) {
        val connection = WeakReference(target.connection)
        val packageName = target.packageName
    }
    private var up: Press? = null
    private var down: Press? = null
    private fun press(code: Int) = if (code == KeyEvent.KEYCODE_VOLUME_UP) up else down
    private fun set(code: Int, value: Press?) { if (code == KeyEvent.KEYCODE_VOLUME_UP) up = value else down = value }
    private fun volume(code: Int) = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
    private fun matches(press: Press, event: KeyEvent) = press.device == event.deviceId && press.downTime == event.downTime

    fun onDown(code: Int, event: KeyEvent, config: Controls.ControlsSnapshot): Boolean {
        if (!volume(code) || event.keyCode != code || event.action != KeyEvent.ACTION_DOWN) return false
        val current = press(code)
        if (current != null && matches(current, event)) {
            if (event.isCanceled) current.repeatable = false
            if (!current.repeatable || event.repeatCount <= current.repeat) return true
            current.repeat = event.repeatCount
            try {
                val target = editor.target()
                if (!config.volumeCursorEnabled || target == null || target.packageName != current.packageName ||
                    target.connection !== current.connection.get() || target.packageName in config.volumeCursorApps) {
                    current.repeatable = false
                    return true
                }
                if (editor.move(target.connection, current.direction) != Move.SENT) current.repeatable = false
            } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                current.repeatable = false
                XposedHelpers.log(error)
            }
            return true
        }
        set(code, null)
        if (!config.volumeCursorEnabled || event.repeatCount != 0 || event.isCanceled || !event.hasNoModifiers() ||
            event.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY != 0) return false
        val target = editor.target() ?: return false
        if (target.packageName in config.volumeCursorApps) return false
        val direction = if ((code == KeyEvent.KEYCODE_VOLUME_UP) != config.volumeCursorReverse)
            KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val result = editor.move(target.connection, direction)
        if (result == Move.DECLINED) return false
        set(code, Press(event.deviceId, event.downTime, direction, target, event.repeatCount, result == Move.SENT))
        return true
    }

    fun onUp(code: Int, event: KeyEvent): Boolean {
        if (!volume(code) || event.keyCode != code || event.action != KeyEvent.ACTION_UP) return false
        val current = press(code) ?: return false
        if (!matches(current, event)) return false
        set(code, null)
        return true
    }

    /** Weak service reference avoids a value-to-key ownership cycle in additional instance fields. */
    class Native(service: InputMethodService) : Editor {
        private val owner = WeakReference(service)
        override fun target(): Target? {
            val service = owner.get() ?: return null
            if (!service.isInputViewShown) return null
            val info = service.currentInputEditorInfo ?: return null
            if (info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL || info.packageName.isNullOrBlank()) return null
            val connection = service.currentInputConnection ?: return null
            return Target(connection, info.packageName)
        }
        override fun move(connection: Any, direction: Int): Move = send(connection as InputConnection, direction)
    }

    companion object {
        internal fun send(connection: InputConnection, direction: Int): Move {
            val now = SystemClock.uptimeMillis()
            val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            val accepted = connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, direction, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
            if (!accepted) return Move.DECLINED
            // After an accepted DPAD down, keep ownership even if the connection dies on UP.
            // Never replay the physical volume key after a cursor movement may have occurred.
            return try {
                if (connection.sendKeyEvent(KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, direction, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))) Move.SENT else Move.STOP_REPEAT
            } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                XposedHelpers.log(error)
                Move.STOP_REPEAT
            }
        }
    }
}
