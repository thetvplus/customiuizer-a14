package tv.withaibuild.customiuizer.prefs

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Pair
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.accessibility.AccessibilityEvent
import tv.withaibuild.customiuizer.utils.AppHelper

class SpinnerExFake(context: Context, attrs: AttributeSet?) : SpinnerEx(context, attrs) {
    var selectAction: (() -> Unit)? = null
    private val taps by lazy { GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true
        override fun onSingleTapUp(event: MotionEvent) = performClick()
    }).apply { setIsLongpressEnabled(false) } }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (selectAction == null) return super.onTouchEvent(event)
        if (!isEnabled) return false
        taps.onTouchEvent(event)
        isPressed = event.actionMasked == MotionEvent.ACTION_DOWN
        return true
    }

    override fun performClick(): Boolean {
        val action = selectAction ?: return super.performClick()
        if (!isEnabled) return false
        playSoundEffect(SoundEffectConstants.CLICK)
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
        action()
        return true
    }

    var value: String? = null
    private val others = ArrayList<Pair<String, String>>()

    fun addValue(key: String, newValue: String?) {
        val v = newValue ?: AppHelper.getStringOfAppPrefs(key, null)
        if (v != null) others.add(Pair(key, v))
    }

    fun addValue(key: String, newValue: Intent?) {
        val sVal = if (newValue == null) {
            AppHelper.getStringOfAppPrefs(key, null)
        } else {
            newValue.toUri(0)
        }
        if (sVal != null) others.add(Pair(key, sVal))
    }

    fun applyOthers() {
        if (others.isEmpty()) return
        val editor = AppHelper.appPrefs!!.edit()
        for (pref in others) {
            editor.putString(pref.first, pref.second)
        }
        editor.apply()
    }
}
