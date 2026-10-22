package tv.withaibuild.customiuizer.prefs

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import androidx.preference.PreferenceScreen
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.utils.Helpers

class PreferenceCategoryEx(context: Context, attrs: AttributeSet?) : PreferenceCategory(context, attrs) {

    private val dynamic: Boolean
    private var state = 0 // 0-正常 1-纯区块 2-顶层隐藏
    private var unsupported = false

    init {
        val xmlAttrs: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.PreferenceCategoryEx)
        dynamic = xmlAttrs.getBoolean(R.styleable.PreferenceCategoryEx_dynamic, false)
        state = xmlAttrs.getInt(R.styleable.PreferenceCategoryEx_state, 0)
        xmlAttrs.recycle()
        layoutResource = R.layout.preference_category
    }

    override fun onPrepareAddPreference(preference: Preference): Boolean {
        preference.onParentChanged(this, shouldDisableDependents())
        return true
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val title = holder.findViewById(android.R.id.title) as? TextView
        Helpers.bindPreferenceTitle(title, this.title, unsupported, dynamic)
        title?.visibility = if (state == 2 || state == 1) View.GONE else View.VISIBLE
        val finalView = holder.itemView
        val childPadding = context.resources.getDimensionPixelSize(R.dimen.preference_item_child_padding)
        val section = parent is PreferenceScreen || (parent as? PreferenceCategoryEx)?.isPageContainer() == true
        val padding = if (section) context.resources.getDimensionPixelSize(R.dimen.preference_group_header_padding) else childPadding
        val vertical = if (isTitleVisible() && !section) childPadding / 2 else 0
        finalView.setPadding(padding, vertical, padding, vertical)
        title?.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(
            if (section) R.dimen.preference_category_text_size else R.dimen.secondary_text_size,
        ))
    }

    fun setUnsupported(value: Boolean) {
        unsupported = value
        isEnabled = !value
    }

    fun isDynamic(): Boolean = dynamic

    fun isTitleVisible(): Boolean = state == 0

    fun isPageContainer(): Boolean = state == 2 && parent is PreferenceScreen

    fun hide() {
        state = 2
        notifyChanged()
    }
}
