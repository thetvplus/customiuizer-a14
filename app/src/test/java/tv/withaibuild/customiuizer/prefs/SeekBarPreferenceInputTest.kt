package tv.withaibuild.customiuizer.prefs

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceViewHolder
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.utils.AppHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SeekBarPreferenceInputTest {
    @Test fun replacingLinkedBoundsPreservesTheValueUntilTheCompleteNewRangeIsApplied() {
        val pref = preference(min = 60, max = 100, shift = 80, default = 90)
        pref.setValue(90)
        pref.setValueRange(80, 160)
        assertEquals(90, pref.getValue())
        pref.setValueRange(0, 80)
        assertEquals(80, pref.getValue())
        pref.setValueRange(66, 94)
        assertEquals(80, pref.getValue())
    }
    private val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
    private lateinit var activity: AppCompatActivity
    private val previousPrefs = AppHelper.appPrefs
    private lateinit var parent: FrameLayout

    @Before fun setup() {
        activity = controller.get().apply { setTheme(R.style.AppTheme) }
        controller.setup().visible()
        AppHelper.appPrefs = activity.getSharedPreferences("seekbar-input-test", Context.MODE_PRIVATE)
        AppHelper.appPrefs!!.edit().clear().commit()
        parent = FrameLayout(activity)
        activity.setContentView(parent)
    }

    @After fun close() {
        controller.pause().stop().destroy()
        AppHelper.appPrefs = previousPrefs
    }

    private fun preference(min: Int = 0, max: Int = 800, shift: Int = 400, divider: Int = 1,
        default: Int = 400, off: String? = null, step: Int = 1, key: String = "pref_key_test",
    ): SeekBarPreference {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.defaultValue, default.toString())
            .addAttribute(R.attr.minValue, min.toString()).addAttribute(R.attr.maxValue, max.toString())
            .addAttribute(R.attr.negativeShift, shift.toString()).addAttribute(R.attr.stepValue, step.toString())
            .addAttribute(R.attr.displayDividerValue, divider.toString())
            .addAttribute(R.attr.format, "%s dp").addAttribute(R.attr.showplus, "true")
        if (off != null) attrs.addAttribute(R.attr.offtext, off)
        return SeekBarPreference(activity, attrs.build()).also { it.key = key; it.title = "Offset" }
    }

    private fun bind(pref: SeekBarPreference): PreferenceViewHolder {
        val view = LayoutInflater.from(activity).inflate(R.layout.preference_seekbar12, parent, false)
        parent.addView(view)
        return PreferenceViewHolder.createInstanceForTests(view).also(pref::onBindViewHolder)
    }

    private fun edit(holder: PreferenceViewHolder): Pair<AlertDialog, EditText> {
        (holder.findViewById(R.id.seekbar_value) as TextView).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        return dialog to checkNotNull(dialog.findViewById(android.R.id.edit))
    }

    private fun confirm(dialog: AlertDialog, input: EditText, value: String) {
        input.setText(value)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun numericEditorFitsTheVisibleKeyboardAndSystemBarsWithAnUpwardBias() {
        val (dialog, input) = edit(bind(preference()))
        val params = dialog.window!!.attributes
        val required = WindowInsets.Type.ime() or WindowInsets.Type.systemBars()
        assertEquals(required, params.fitInsetsTypes and required)
        assertFalse(params.isFitInsetsIgnoringVisibility)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            params.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
            params.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE)
        assertEquals(Gravity.CENTER, params.gravity)
        assertEquals(-(24 * activity.resources.displayMetrics.density).toInt(), params.y)
        assertTrue(input.hasFocus())
        confirm(dialog, input, "12")
        assertFalse(dialog.isShowing)
    }

    @Test fun toastCenterInputSavesOnceAndRunsTheExistingListenerSequence() {
        val pref = preference()
        val holder = bind(pref)
        val events = mutableListOf<String>()
        pref.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) { events += "start" }
            override fun onProgressChanged(bar: SeekBar, value: Int, user: Boolean) { events += "$value:$user" }
            override fun onStopTrackingTouch(bar: SeekBar) { events += "stop:${bar.progress}" }
        })
        var writes = 0
        val change = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> writes++ }
        AppHelper.appPrefs!!.registerOnSharedPreferenceChangeListener(change)
        val (dialog, input) = edit(holder)
        assertEquals("0", input.text.toString())
        assertEquals(0, input.selectionStart)
        assertEquals(1, input.selectionEnd)
        confirm(dialog, input, "+25")
        assertEquals(425, pref.getValue())
        assertEquals(425, AppHelper.appPrefs!!.getInt(pref.key, -1))
        assertEquals(425, (holder.findViewById(R.id.seekbar) as SeekBar).progress)
        assertTrue((holder.findViewById(R.id.seekbar_value) as TextView).text.startsWith("+25"))
        assertEquals(listOf("start", "425:true", "stop:425"), events)
        assertEquals(1, writes)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(1, writes)
        AppHelper.appPrefs!!.unregisterOnSharedPreferenceChangeListener(change)
    }

    @Test fun cancelInvalidStepAndOverflowLeaveValueAndStorageUntouched() {
        val pref = preference(max = 30000, shift = 0, default = 0, step = 500)
        val holder = bind(pref)
        var edit = edit(holder)
        for (text in listOf("125", "30001", "2147483648", "")) {
            confirm(edit.first, edit.second, text)
            assertTrue(edit.first.isShowing)
            assertNotNull(edit.second.error)
            assertEquals(0, pref.getValue())
            assertFalse(AppHelper.appPrefs!!.contains(pref.key))
        }
        edit.first.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        edit = edit(holder)
        confirm(edit.first, edit.second, "29500")
        assertEquals(29500, pref.getValue())
    }

    @Test fun dividedInputAndDefaultSentinelHaveOneStorageMeaning() {
        val pref = preference(max = 40, shift = 20, divider = 2, default = 20, off = "Default")
        val holder = bind(pref)
        val (dialog, input) = edit(holder)
        assertEquals("", input.text.toString())
        assertEquals("Default", input.hint)
        confirm(dialog, input, "1.5")
        assertEquals(23, pref.getValue())
        val reset = edit(holder).first
        reset.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(20, AppHelper.appPrefs!!.getInt(pref.key, -1))
        assertEquals("Default", (holder.findViewById(R.id.seekbar_value) as TextView).text)
        val alias = edit(holder)
        confirm(alias.first, alias.second, "0")
        assertEquals("Default", (holder.findViewById(R.id.seekbar_value) as TextView).text)
    }

    @Test fun aNamedNonzeroDefaultIsNotInventedAsNumericZero() {
        val pref = preference(min = 45, max = 200, shift = 0, divider = 100, default = 45,
            off = "Default", step = 5)
        val holder = bind(pref)
        val edit = edit(holder)
        confirm(edit.first, edit.second, "0")
        assertTrue(edit.first.isShowing)
        assertEquals(45, pref.getValue())
        confirm(edit.first, edit.second, "1.25")
        assertEquals(125, pref.getValue())
        edit(holder).first.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(45, AppHelper.appPrefs!!.getInt(pref.key, -1))
    }

    @Test fun dynamicRangeAndLinkedValuesUseTheNewValueBeforeSaving() {
        val min = preference(max = 99, shift = 0, default = 0, key = "pref_key_min")
        val max = preference(max = 100, shift = 0, default = 50, key = "pref_key_max")
        val holder = bind(min)
        val maxHolder = bind(max)
        min.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
            override fun onProgressChanged(bar: SeekBar, value: Int, user: Boolean) {
                assertTrue(user)
                if (max.getValue() <= value) max.setValue(value + 1, true)
                max.setMinValue(value + 1)
            }
        })
        val edit = edit(holder)
        confirm(edit.first, edit.second, "80")
        assertEquals(81, max.getValue())
        assertEquals(81, AppHelper.appPrefs!!.getInt(max.key, -1))
        val constrained = edit(maxHolder)
        confirm(constrained.first, constrained.second, "50")
        assertTrue(constrained.first.isShowing)
        confirm(constrained.first, constrained.second, "90")
        assertEquals(90, max.getValue())
    }

    @Test fun holderReuseAndDetachDismissTheEditorAndCannotEditThePreviousOwner() {
        val first = preference()
        val holder = bind(first)
        val oldDialog = edit(holder).first
        val second = preference(shift = 0, default = 7, key = "pref_key_second")
        second.onBindViewHolder(holder)
        assertFalse(oldDialog.isShowing)
        first.setValue(600)
        assertEquals(7, (holder.findViewById(R.id.seekbar) as SeekBar).progress)
        val dialog = edit(holder).first
        second.onDetached()
        assertFalse(dialog.isShowing)
        assertFalse(AppHelper.appPrefs!!.contains(second.key))
    }

    @Test fun disabledOrUnsupportedPreferencesCannotCommitAnOpenEditor() {
        val pref = preference()
        val holder = bind(pref)
        val edit = edit(holder)
        pref.setUnsupported(true)
        confirm(edit.first, edit.second, "30")
        assertFalse(edit.first.isShowing)
        assertEquals(400, pref.getValue())
        assertFalse(AppHelper.appPrefs!!.contains(pref.key))
    }

    @Test fun toastTopBottomAndDurationKeepTheirRangesAndDistinctDefaultEncoding() {
        for ((name, default, input) in listOf(Triple("top", 24, "799"), Triple("bottom", 0, "777"))) {
            val pref = preference(max = 800, shift = 0, default = default, key = "pref_key_$name")
            val holder = bind(pref)
            val edit = edit(holder)
            confirm(edit.first, edit.second, input)
            assertEquals(input.toInt(), AppHelper.appPrefs!!.getInt(pref.key, -1))
            edit(holder).first.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(default, pref.getValue())
        }
        val duration = preference(max = 19, shift = 4, default = 0, off = "Default", key = "pref_key_duration")
        val holder = bind(duration)
        var edit = edit(holder)
        confirm(edit.first, edit.second, "15")
        assertEquals(19, duration.getValue())
        edit = edit(holder)
        confirm(edit.first, edit.second, "16")
        assertTrue(edit.first.isShowing)
        confirm(edit.first, edit.second, "0")
        assertEquals("numeric zero is a valid override, distinct from the default sentinel", 4, duration.getValue())
        edit(holder).first.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, AppHelper.appPrefs!!.getInt(duration.key, -1))
    }

    @Test fun nonzeroMinAndFractionalScaleDeliverTheRawProgressExpectedByStopListeners() {
        for (case in listOf(
            listOf(1, 6, 1, 1, 4, 3), listOf(0, 100, 10, 0, 15, 15),
        )) {
            val (min, max, divider, default, input) = case
            val expectedProgress = case[5]
            val pref = preference(min = min, max = max, shift = 0, divider = divider, default = default)
            val holder = bind(pref)
            var stopped = -1
            var progressValue = -1
            pref.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) { stopped = bar.progress }
                override fun onProgressChanged(bar: SeekBar, value: Int, user: Boolean) {
                    assertTrue(user)
                    progressValue = value
                }
            })
            val edit = edit(holder)
            confirm(edit.first, edit.second, if (divider == 10) "1.5" else input.toString())
            assertEquals(expectedProgress, stopped)
            assertEquals(input, progressValue)
            pref.onDetached()
            AppHelper.appPrefs!!.edit().clear().commit()
        }
    }

    @Test fun accessibilityProgressChangesShareTheUserCommitPath() {
        val pref = preference()
        val holder = bind(pref)
        val bar = holder.findViewById(R.id.seekbar) as SeekBar
        val args = android.os.Bundle().apply {
            putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 450f)
        }
        assertTrue(bar.performAccessibilityAction(
            android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args))
        assertEquals(450, pref.getValue())
        assertEquals(450, AppHelper.appPrefs!!.getInt(pref.key, -1))
    }

    @Test fun pastedNegativeSignsDecimalsAndIllegalCharactersAreValidatedWithoutSilentFiltering() {
        val pref = preference(max = 800, shift = 0, default = 24)
        val holder = bind(pref)
        val edit = edit(holder)
        for (text in listOf("-1", "1.5", "1e3", "NaN", "Infinity", "50 dp", "1,2,3")) {
            confirm(edit.first, edit.second, text)
            assertEquals("preserve input for validation", text, edit.second.text.toString())
            assertTrue(edit.first.isShowing)
            assertNotNull(edit.second.error)
            assertEquals(24, pref.getValue())
            assertFalse(AppHelper.appPrefs!!.contains(pref.key))
        }
        confirm(edit.first, edit.second, "800")
        assertEquals(800, pref.getValue())
    }
}
