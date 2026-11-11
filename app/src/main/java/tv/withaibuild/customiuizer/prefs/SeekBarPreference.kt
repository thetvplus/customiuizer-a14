package tv.withaibuild.customiuizer.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.utils.AppHelper
import java.util.IllegalFormatException
import java.util.Locale

/** Integer storage lattice shared by Compose dragging, exact input and linked ranges. */
class SeekBarPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet?,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle) : Preference(context, attrs, defStyleAttr), PreferenceState {
    private var defaultValue = 0
    private var minValue = 0
    private var maxValue = 10
    private var stepValue = 1
    private var negativeShift = 0
    private var displayDividerValue = 1
    private var useDisplayDividerValue = false
    private var showPlus = false
    private var format: String? = null
    private var note: String? = null
    private var offText: String? = null
    private var currentValue: Int? = null
    private var trackingStart: Int? = null
    private var values = makeValues()
    private var valuePreview: ((Int) -> Unit)? = null
    private var valueCommit: ((Int) -> Unit)? = null
    /** Typed settings definitions configure the same storage lattice without XML inflation. */
    internal fun configureComposeValues(minimum: Int, maximum: Int, step: Int, initial: Int,
        shift: Int, divider: Int, useDivider: Boolean, displayFormat: String?, disabledText: String?, valueNote: String?, plus: Boolean,
    ) {
        minValue = minimum.coerceAtMost(Int.MAX_VALUE - 1)
        maxValue = maximum.coerceAtLeast(minValue + 1)
        stepValue = step.coerceAtLeast(1)
        defaultValue = initial.coerceIn(minValue, maxValue)
        negativeShift = shift.coerceAtLeast(0)
        displayDividerValue = divider.coerceAtLeast(1)
        useDisplayDividerValue = useDivider
        format = displayFormat
        offText = disabledText
        note = valueNote
        showPlus = plus
        values = makeValues()
        currentValue = null
    }

    fun setOnValueChangeListener(preview: ((Int) -> Unit)? = null, commit: ((Int) -> Unit)? = null) {
        valuePreview = preview
        valueCommit = commit
    }

    internal val composeMaximum: Int get() = values.progressMax
    internal val composeSignedInput: Boolean get() = values.minimum.toLong() < negativeShift
    internal val composeProgress: Int get() = values.progress(getValue())
    internal val composeInput: String get() = values.number(getValue())
    internal val composeNote: String? get() = note
    internal val composeDefaultInput: String? get() = defaultValue.takeIf(values::contains)?.let(values::number)
    internal val composeDefaultLabel: String get() = offText ?: context.getString(R.string.array_default)
    internal val composeGuidance: String get() = context.getString(R.string.seekbar_value_range,
        values.number(values.minimum), values.number(values.maximum), values.stepNumber(),
        FORMAT_VALUE.replace(format.orEmpty(), "").replace("%%", "%").trim())

    internal fun composeDisplay(): String {
        if (getValue() == defaultValue && offText != null) return offText!!
        val real = getValue().toLong() - negativeShift
        val formatted = try {
            if (useDisplayDividerValue) String.format(Locale.US, format ?: "%s", real.toDouble() / displayDividerValue)
            else String.format(Locale.US, format ?: "%s", real)
        } catch (_: IllegalFormatException) { values.number(getValue()) }
        return if (showPlus && real > 0) "+$formatted" else formatted
    }

    private fun canEdit() = isEnabled && isSelectable && isVisible && !extras.getBoolean("ui.unsupported")

    internal fun previewComposeValue(progress: Int) {
        if (!canEdit()) return
        if (trackingStart == null) trackingStart = getValue()
        currentValue = values.storage(progress)
        valuePreview?.invoke(getValue())
        notifyChanged()
    }

    internal fun finishComposeChange(): Boolean {
        val start = trackingStart ?: return true
        trackingStart = null
        if (getValue() == start) return true
        if (!canEdit() || !callChangeListener(getValue())) {
            setValue(start)
            valuePreview?.invoke(start)
            return false
        }
        valueCommit?.invoke(getValue())
        saveValue()
        return true
    }

    internal fun cancelComposeChange() {
        val start = trackingStart ?: return
        trackingStart = null
        setValue(start)
        valuePreview?.invoke(start)
    }

    internal fun submitComposeInput(input: String): Boolean {
        if (!canEdit()) return false
        val parsed = values.parse(input) ?: return false
        previewComposeValue(values.progress(parsed))
        return finishComposeChange()
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
        notifyChanged()
    }

    fun setFormat(formatResId: Int) {
        setFormat(context.resources.getString(formatResId))
    }

    fun getValue(): Int = currentValue ?: values.bounded(AppHelper.getIntOfAppPrefs(key, defaultValue))
        .also { currentValue = it }

    fun setValue(value: Int) = setValue(value, false)

    fun setValue(value: Int, save: Boolean) {
        val bounded = values.bounded(value)
        val changed = currentValue != bounded
        currentValue = bounded
        if (changed) notifyChanged()
        if (save) saveValue()
    }

    fun setDefaultValue(value: Int) {
        defaultValue = value
        notifyChanged()
    }

    private fun makeValues() = SeekBarValues(minValue, maxValue, stepValue, negativeShift,
        if (useDisplayDividerValue) displayDividerValue else 1)

    private fun updateAllValues() {
        val oldValue = getValue()
        if (maxValue <= minValue) maxValue = minValue + 1
        values = makeValues()
        setValue(oldValue)
    }

    private fun saveValue() {
        val k = key ?: return
        if (values.contains(getValue())) AppHelper.appPrefs?.edit()?.putInt(k, getValue())?.apply()
    }
    override fun onDetached() { cancelComposeChange(); super.onDetached() }
    companion object {
        private val FORMAT_VALUE = Regex("%(?:[0-9]+\\$)?[-#+ 0,(]*[0-9]*(?:\\.[0-9]+)?[a-zA-Z]")
    }
    override fun markAsNew() { extras.putBoolean("ui.new", true); notifyChanged() }
    override fun applyHighlight() { extras.putBoolean("ui.highlight", true); notifyChanged() }
    fun setUnsupported(value: Boolean) { extras.putBoolean("ui.unsupported", value); isEnabled = !value }
}
