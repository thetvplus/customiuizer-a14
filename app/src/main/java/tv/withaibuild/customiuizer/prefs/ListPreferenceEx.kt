package tv.withaibuild.customiuizer.prefs

import android.content.Context
import android.content.res.TypedArray
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.utils.Helpers

class ListPreferenceEx(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs), PreferenceState {

    private val indentLevel: Int
    private val dynamic: Boolean
    private var newmod = false
    private var highlight = false
    private var unsupported = false
    private val valueAsSummary: Boolean
    private var listDefaultValue: String? = null

    init {
        val xmlAttrs: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.ListPreferenceEx)
        indentLevel = xmlAttrs.getInt(R.styleable.ListPreferenceEx_indentLevel, 0)
        dynamic = xmlAttrs.getBoolean(R.styleable.ListPreferenceEx_dynamic, false)
        valueAsSummary = xmlAttrs.getBoolean(R.styleable.ListPreferenceEx_valueAsSummary, false)
        xmlAttrs.recycle()
        isIconSpaceReserved = false
    }

    override fun notifyChanged() {
        super.notifyChanged()
        notifyDependencyChange(shouldDisableDependents())
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        val value = a.getString(index)
        listDefaultValue = value
        return value
    }

    override fun shouldDisableDependents(): Boolean {
        return TextUtils.equals(listDefaultValue, value) || super.shouldDisableDependents()
    }

    fun setUnsupported(value: Boolean) {
        unsupported = value
        isEnabled = !value
    }

    fun getView(finalView: View) {
        val title = finalView.findViewById<TextView>(android.R.id.title)
        val summary = finalView.findViewById<TextView>(android.R.id.summary)
        val valSummary = finalView.findViewById<TextView>(android.R.id.hint)
        val res = context.resources

        summary?.visibility = if (valueAsSummary || summary.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        valSummary?.visibility = if (valueAsSummary) View.VISIBLE else View.GONE
        valSummary?.text = if (valueAsSummary) {
            val resolved = entry
            if (resolved.isNullOrEmpty()) value ?: "" else resolved
        } else ""
        if (valueAsSummary) {
            val disableColor = res.getColor(R.color.preference_primary_text_disable, context.theme)
            val secondary = res.getColor(R.color.preference_secondary_text, context.theme)
            valSummary?.setTextColor(if (isEnabled) secondary else disableColor)
        }
        title?.text = Helpers.appendStatusMarker(title?.text, unsupported, dynamic)
        if (newmod) title?.let { Helpers.applyNewMod(it) }
        if (highlight) {
            // One-shot: the row rebinds whenever its own state changes, and a flash
            // restarted on every bind never lets the row settle.
            highlight = false
            Helpers.applySearchItemHighlight(finalView)
        }

        val childPadding = res.getDimensionPixelSize(R.dimen.preference_item_child_padding)
        val hrzPadding = (indentLevel + 1) * childPadding
        finalView.setPadding(hrzPadding, 0, childPadding, 0)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val title = holder.findViewById(android.R.id.title) as? TextView
        title?.maxLines = 2

        val summary = holder.findViewById(android.R.id.summary) as? TextView

        var valSummary = holder.itemView.findViewById<TextView>(android.R.id.hint)
        if (valSummary == null) {
            valSummary = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, summary?.textSize ?: textSize)
                setPadding(summary?.paddingLeft ?: 0, summary?.paddingTop ?: 0, context.resources.getDimensionPixelSize(R.dimen.preference_summary_padding_right), summary?.paddingBottom ?: 0)
                id = android.R.id.hint
            }
            (holder.itemView as? ViewGroup)?.addView(valSummary, 2)
        }

        // Binding is read-only with respect to preference state.
        //
        // This used to "repair" a value that was missing from entryValues by calling
        // setValue here. That is a write during onBindViewHolder, and it has three
        // consequences, all of which were observed on the language preference:
        //
        //  - setValue -> notifyChanged -> notifyItemChanged while the RecyclerView is
        //    laying out, which throws IllegalStateException and kills the screen;
        //  - the repair persists the placeholder value from the XML, so the stored
        //    language silently reverts;
        //  - the row then renders the placeholder entry instead of a language.
        //
        // A mismatch is a setup bug, so log it and render what we have. The owner of
        // the preference repairs the stored value at setup time, before the first bind
        // (see AppLocaleController.setupLocalePreference).
        val currentEntries = entries
        val currentValues = entryValues
        if (currentEntries != null && currentValues != null && currentEntries.size != currentValues.size) {
            android.util.Log.e("ListPreferenceEx", "entries/entryValues size mismatch: ${currentEntries.size} vs ${currentValues.size}")
        }
        if (currentValues != null && value != null && !currentValues.contains(value)) {
            android.util.Log.e("ListPreferenceEx", "value '$value' is not in entryValues; showing it verbatim")
        }

        getView(holder.itemView)
    }

    override fun markAsNew() {
        newmod = true
    }

    override fun applyHighlight() {
        highlight = true
    }
}
