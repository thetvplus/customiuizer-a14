package tv.withaibuild.customiuizer.prefs

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.utils.Helpers

class CheckBoxPreferenceEx(context: Context, attrs: AttributeSet?) : SwitchPreference(context, attrs), PreferenceState {

    private val indentLevel: Int
    private val dynamic: Boolean
    private var newmod = false
    private var highlight = false
    private var unsupported = false

    init {
        val xmlAttrs: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.CheckBoxPreferenceEx)
        dynamic = xmlAttrs.getBoolean(R.styleable.CheckBoxPreferenceEx_dynamic, false)
        indentLevel = xmlAttrs.getInt(R.styleable.CheckBoxPreferenceEx_indentLevel, 0)
        xmlAttrs.recycle()
        isIconSpaceReserved = false
    }

    fun getView(finalView: View) {
        val title = finalView.findViewById<TextView>(android.R.id.title)
        title?.text = Helpers.appendStatusMarker(title?.text, unsupported, dynamic)
        if (newmod) title?.let { Helpers.applyNewMod(it) }
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
        val title = holder.findViewById(android.R.id.title) as? TextView
        title?.maxLines = 2
        // The row owns the click. Propagate its pressed state to the non-clickable Switch so
        // the thumb/track responds on ACTION_DOWN, before persistence and adapter rebind.
        // Re-apply checked after that: parent selected/pressed during search highlight must
        // not leave the widget visually behind Preference.isChecked.
        val switchWidget = holder.findViewById(android.R.id.switch_widget)
        switchWidget?.isDuplicateParentStateEnabled = true
        val switchButton = switchWidget as? CompoundButton
        if (switchButton != null) {
            switchButton.isChecked = isChecked
            switchButton.jumpDrawablesToCurrentState()
        }
        getView(holder.itemView)
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
