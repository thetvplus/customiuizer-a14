package tv.withaibuild.customiuizer.prefs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.TypedArray
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers
import java.lang.ref.WeakReference
import java.util.IllegalFormatException
import java.util.Locale

class SeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr), PreferenceState {

    private var dynamic = false
    private var newmod = false
    private var highlight = false
    private var unsupported = false

    private var defaultValue = 0
    private var minValue = 0
    private var maxValue = 10
    private var stepValue = 1
    private var negativeShift = 0
    private val indentLevel: Int

    private var displayDividerValue = 1
    private var useDisplayDividerValue = false
    private var showPlus = false

    private var format: String? = null
    private var note: String? = null
    private var offText: String? = null

    private var values: SeekBarValues
    private var currentValue: Int? = null
    private var trackingStart: Int? = null
    private var binding = false
    private var valueViewRef = WeakReference<TextView>(null)
    private var seekBarRef = WeakReference<SeekBar>(null)
    private val valueView get() = valueViewRef.get()
    private val seekBar get() = seekBarRef.get()
    private var editor: AlertDialog? = null

    private var listener: SeekBar.OnSeekBarChangeListener? = null

    init {
        if (attrs != null) {
            val xmlAttrs: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.SeekBarPreference)
            indentLevel = xmlAttrs.getInt(R.styleable.SeekBarPreference_indentLevel, 0)
            dynamic = xmlAttrs.getBoolean(R.styleable.SeekBarPreference_dynamic, false)
            minValue = xmlAttrs.getInt(R.styleable.SeekBarPreference_minValue, 0)
            maxValue = xmlAttrs.getInt(R.styleable.SeekBarPreference_maxValue, 10)
            stepValue = xmlAttrs.getInt(R.styleable.SeekBarPreference_stepValue, 1)
            defaultValue = xmlAttrs.getInt(R.styleable.SeekBarPreference_android_defaultValue, 0)
            negativeShift = xmlAttrs.getInt(R.styleable.SeekBarPreference_negativeShift, 0)
            showPlus = xmlAttrs.getBoolean(R.styleable.SeekBarPreference_showplus, false)

            if (xmlAttrs.hasValue(R.styleable.SeekBarPreference_displayDividerValue)) {
                useDisplayDividerValue = true
                displayDividerValue = xmlAttrs.getInt(R.styleable.SeekBarPreference_displayDividerValue, 1)
            }

            if (minValue < 0) minValue = 0
            if (maxValue <= minValue) maxValue = minValue + 1

            if (defaultValue < minValue) defaultValue = minValue
            else if (defaultValue > maxValue) defaultValue = maxValue

            if (stepValue <= 0) stepValue = 1
            displayDividerValue = displayDividerValue.coerceAtLeast(1)
            negativeShift = negativeShift.coerceAtLeast(0)

            format = xmlAttrs.getString(R.styleable.SeekBarPreference_format)
            note = xmlAttrs.getString(R.styleable.SeekBarPreference_note)
            offText = xmlAttrs.getString(R.styleable.SeekBarPreference_offtext)

            xmlAttrs.recycle()
        } else {
            indentLevel = 0
        }

        values = makeValues()
        layoutResource = R.layout.preference_seekbar12
    }

    fun getView(finalView: View) {
        val mTitle = finalView.findViewById<TextView>(android.R.id.title)
        Helpers.bindPreferenceTitle(mTitle, title, unsupported, dynamic)
        seekBar?.alpha = if (isEnabled) 1.0f else 0.75f
        if (newmod) mTitle?.let { Helpers.applyNewMod(it) }
        if (highlight) {
            // One-shot: the row rebinds whenever its own state changes, and a flash
            // restarted on every bind never lets the row settle.
            highlight = false
            Helpers.applySearchItemHighlight(finalView)
        }

        val childPadding = context.resources.getDimensionPixelSize(R.dimen.preference_item_child_padding)
        val hrzPadding = (indentLevel + 1) * childPadding
        finalView.setPadding(hrzPadding, 0, childPadding, 0)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val mSummaryView = holder.findViewById(android.R.id.summary) as? TextView
        if (!summary.isNullOrEmpty()) {
            mSummaryView?.visibility = View.VISIBLE
            mSummaryView?.text = summary
        } else {
            mSummaryView?.visibility = View.GONE
        }

        val mNoteView = holder.findViewById(android.R.id.text1) as? TextView
        if (note.isNullOrEmpty()) {
            mNoteView?.visibility = View.GONE
        } else {
            mNoteView?.visibility = View.VISIBLE
            mNoteView?.text = note
        }

        releaseBinding()
        val bar = holder.findViewById(R.id.seekbar) as? SeekBar
        @Suppress("UNCHECKED_CAST")
        val previous = bar?.getTag(R.id.seekbar) as? WeakReference<SeekBarPreference>
        previous?.get()?.releaseBinding()
        valueViewRef = WeakReference(holder.findViewById(R.id.seekbar_value) as? TextView)
        seekBarRef = WeakReference(bar)
        bar?.setTag(R.id.seekbar, WeakReference(this))
        syncView()
        bar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) { finishUserChange(seekBar) }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                trackingStart = getValue()
                listener?.onStartTrackingTouch(seekBar)
            }
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (binding) return
                val discrete = fromUser && trackingStart == null
                if (discrete) {
                    trackingStart = getValue()
                    listener?.onStartTrackingTouch(seekBar)
                }
                currentValue = values.storage(progress)
                updateDisplay()
                listener?.onProgressChanged(seekBar, getValue(), fromUser)
                if (discrete) finishUserChange(seekBar)
            }
        })
        valueView?.apply {
            setOnClickListener { showValueEditor() }
            isClickable = canEdit()
            isFocusable = isClickable
        }
        getView(holder.itemView)
        holder.setDividerAllowedAbove(false)
    }

    fun setOnSeekBarChangeListener(listener: SeekBar.OnSeekBarChangeListener?) {
        this.listener = listener
    }

    fun getMinValue(): Int = minValue

    fun setMinValue(value: Int) {
        minValue = value.coerceIn(0, Int.MAX_VALUE - 1)
        updateAllValues()
    }

    fun getMaxValue(): Int = maxValue

    /** Atomically replace a linked range so an intermediate bound cannot persist a
     * different value while the other bound still belongs to the previous layout. */
    fun setValueRange(minimum: Int, maximum: Int) {
        require(minimum >= 0 && maximum > minimum)
        if (minValue == minimum && maxValue == maximum) return
        minValue = minimum
        maxValue = maximum
        updateAllValues()
    }

    fun setMaxValue(value: Int) {
        maxValue = value.coerceAtLeast(1)
        updateAllValues()
    }

    fun getStepValue(): Int = stepValue

    fun setStepValue(value: Int) {
        stepValue = value.coerceAtLeast(1)
        updateAllValues()
    }

    fun getFormat(): String? = format

    private fun setFormat(format: String?) {
        this.format = format
        updateDisplay()
    }

    fun setFormat(formatResId: Int) {
        setFormat(context.resources.getString(formatResId))
    }

    fun getValue(): Int = currentValue ?: values.bounded(AppHelper.getIntOfAppPrefs(key, defaultValue))
        .also { currentValue = it }

    fun setValue(value: Int) = setValue(value, false)

    fun setValue(value: Int, save: Boolean) {
        currentValue = values.bounded(value)
        syncView()
        if (save) saveValue()
    }

    fun setDefaultValue(value: Int) {
        editor?.dismiss()
        defaultValue = value
        updateDisplay()
    }

    private fun makeValues() = SeekBarValues(minValue, maxValue, stepValue, negativeShift,
        if (useDisplayDividerValue) displayDividerValue else 1)

    private fun updateAllValues() {
        editor?.dismiss()
        val oldValue = getValue()
        if (maxValue <= minValue) maxValue = minValue + 1
        values = makeValues()
        setValue(oldValue)
    }

    private fun syncView() {
        binding = true
        try {
            seekBar?.let {
                it.max = values.progressMax
                it.progress = values.progress(getValue())
            }
            updateDisplay()
        } finally {
            binding = false
        }
    }

    private fun updateDisplay() {
        val view = valueView ?: return
        val fmt = format
        if (fmt.isNullOrEmpty()) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        val value = getValue()
        val realValue = value.toLong() - negativeShift
        view.text = if (value == defaultValue && offText != null) offText else {
            val text = try {
                if (useDisplayDividerValue) String.format(Locale.US, fmt, realValue.toDouble() / displayDividerValue)
                else String.format(Locale.US, fmt, realValue)
            } catch (_: IllegalFormatException) {
                values.number(value)
            }
            if (showPlus && realValue > 0) "+$text" else text
        }
        view.contentDescription = context.getString(R.string.seekbar_edit_value, title, view.text)
    }

    private fun saveValue() {
        val k = key ?: return
        if (!values.contains(getValue())) return
        AppHelper.appPrefs?.edit()?.putInt(k, getValue())?.apply()
    }

    /** Dragging and direct input finish through the same business callback and single write. */
    private fun finishUserChange(bar: SeekBar) {
        val start = trackingStart ?: return
        trackingStart = null
        if (getValue() == start) return
        if (!callChangeListener(getValue())) {
            setValue(start)
            return
        }
        listener?.onStopTrackingTouch(bar)
        saveValue()
    }

    private fun canEdit(): Boolean = isEnabled && isSelectable && isVisible && !unsupported &&
        !format.isNullOrEmpty() && values.isValid && seekBar != null

    private fun showValueEditor() {
        if (!canEdit() || editor != null) return
        val bar = seekBar ?: return
        val activity = activityContext() ?: return
        if (activity.isFinishing || activity.isDestroyed || !bar.isAttachedToWindow) return
        val input = AppCompatEditText(activity).apply {
            id = android.R.id.edit
            inputType = InputType.TYPE_CLASS_NUMBER or
                (if (values.minimum < negativeShift) InputType.TYPE_NUMBER_FLAG_SIGNED else 0) or
                (if (values.divider > 1) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
            filters = arrayOf(InputFilter.LengthFilter(64))
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            // A sentinel has a name, not a numeric value. Its numeric alias is explained below.
            if (getValue() == defaultValue && offText != null) hint = offText
            else setText(values.number(getValue()))
            setSelectAllOnFocus(true)
        }
        val padding = (24 * activity.resources.displayMetrics.density).toInt()
        val container = FrameLayout(activity).apply {
            setPadding(padding, 0, padding, 0)
            addView(input, FrameLayout.LayoutParams(-1, -2))
        }
        val unit = FORMAT_VALUE.replace(format.orEmpty(), "").replace("%%", "%").trim()
        var guidance = context.getString(R.string.seekbar_value_range, values.number(values.minimum),
            values.number(values.maximum), values.stepNumber(), unit)
        if (offText != null && values.contains(defaultValue)) {
            guidance += "\n" + context.getString(R.string.seekbar_value_special, values.number(defaultValue), offText)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(guidance)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(offText ?: context.getString(R.string.array_default), null)
            .create()
        editor = dialog
        fun submit(value: Int?) {
            if (editor !== dialog || !dialog.isShowing) return
            if (!canEdit() || seekBar !== bar) { dialog.dismiss(); return }
            if (value == null || !values.contains(value)) {
                input.error = context.getString(R.string.seekbar_value_error)
                return
            }
            if (value != getValue()) {
                trackingStart = getValue()
                listener?.onStartTrackingTouch(bar)
                setValue(value)
                listener?.onProgressChanged(bar, getValue(), true)
                finishUserChange(bar)
            }
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            if (editor !== dialog || !dialog.isShowing) return@setOnShowListener
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit(values.parse(input.text.toString())) }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
                isEnabled = values.contains(defaultValue)
                setOnClickListener { submit(defaultValue) }
            }
            input.setOnEditorActionListener { _, action, _ ->
                if (action != EditorInfo.IME_ACTION_DONE) false else {
                    submit(values.parse(input.text.toString()))
                    true
                }
            }
            input.requestFocus()
            input.selectAll()
            dialog.window?.let { window ->
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                window.attributes = window.attributes.apply {
                    // Let WindowManager fit the floating dialog above the actual IME, including
                    // keyboard-height changes. A small upward bias leaves the editor more room.
                    setFitInsetsTypes(fitInsetsTypes or WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                    setFitInsetsIgnoringVisibility(false)
                    gravity = Gravity.CENTER
                    y = -(24 * activity.resources.displayMetrics.density).toInt()
                }
            }
        }
        dialog.setOnDismissListener {
            input.setOnEditorActionListener(null)
            dialog.setOnShowListener(null)
            dialog.setOnDismissListener(null)
            if (editor === dialog) editor = null
        }
        try {
            dialog.show()
        } catch (_: WindowManager.BadTokenException) {
            dialog.setOnShowListener(null)
            dialog.setOnDismissListener(null)
            editor = null
        }
    }

    private fun activityContext(): Activity? {
        var candidate = context
        while (candidate is ContextWrapper) {
            if (candidate is Activity) return candidate
            val base = candidate.baseContext
            if (base === candidate) break
            candidate = base
        }
        return null
    }

    private fun releaseBinding() {
        editor?.dismiss()
        trackingStart?.let { currentValue = it }
        seekBar?.let {
            it.setOnSeekBarChangeListener(null)
            it.setTag(R.id.seekbar, null)
        }
        valueView?.setOnClickListener(null)
        valueViewRef.clear()
        seekBarRef.clear()
        trackingStart = null
    }

    override fun onDetached() {
        releaseBinding()
        super.onDetached()
    }

    companion object {
        private val FORMAT_VALUE = Regex("%(?:[0-9]+\\$)?[-#+ 0,(]*[0-9]*(?:\\.[0-9]+)?[a-zA-Z]")
    }

    fun setUnsupported(value: Boolean) {
        unsupported = value
        isEnabled = !value
    }

    override fun markAsNew() {
        newmod = true
    }

    override fun applyHighlight() {
        highlight = true
    }
}
