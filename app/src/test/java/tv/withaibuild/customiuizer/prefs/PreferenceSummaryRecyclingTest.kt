package tv.withaibuild.customiuizer.prefs

import android.app.Application
import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.utils.AppHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PreferenceSummaryRecyclingTest {
    private val previousPrefs = AppHelper.appPrefs
    private val context = RuntimeEnvironment.getApplication().apply { setTheme(R.style.AppTheme) }
    private lateinit var holder: PreferenceViewHolder
    private val summary get() = holder.itemView.findViewById<TextView>(android.R.id.summary)
    private val hint get() = holder.itemView.findViewById<TextView>(android.R.id.hint)

    @Before fun setup() {
        AppHelper.appPrefs = context.getSharedPreferences("summary-recycling", Context.MODE_PRIVATE)
        AppHelper.appPrefs!!.edit().clear().commit()
        holder = PreferenceViewHolder.createInstanceForTests(
            LayoutInflater.from(context).inflate(R.layout.pref_item, null))
    }

    @After fun cleanup() { AppHelper.appPrefs = previousPrefs }

    private fun preference(text: CharSequence? = null, count: Boolean = false) = PreferenceEx(context,
        if (count) org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(R.attr.countAsSummary, "true").build() else null).apply {
        key = "pref_key_summary_recycling"
        title = "Feedback"
        summary = text
    }

    @Test fun aHeaderCannotInheritTheIntroFromARecycledRow() {
        preference("Intro").onBindViewHolder(holder)
        assertEquals(View.VISIBLE, summary.visibility)
        preference().onBindViewHolder(holder)
        assertEquals(View.GONE, summary.visibility)
        assertTrue(summary.text.isNullOrEmpty())
        preference("Another summary").onBindViewHolder(holder)
        assertEquals("Another summary", summary.text.toString())
        assertEquals(View.VISIBLE, summary.visibility)
    }

    @Test fun aLiveSummaryProviderIsReadInsteadOfTheOldViewText() {
        var text: CharSequence? = "First"
        val pref = preference().apply {
            summaryProvider = androidx.preference.Preference.SummaryProvider<PreferenceEx> { text }
        }
        pref.onBindViewHolder(holder)
        text = ""
        pref.onBindViewHolder(holder)
        assertEquals(View.GONE, summary.visibility)
        text = "Updated"
        pref.onBindViewHolder(holder)
        assertEquals("Updated", summary.text.toString())
        assertEquals(View.VISIBLE, summary.visibility)
    }

    @Test fun currentSummarySpansAndCustomSummaryTransitionsSurviveRebinding() {
        val text = SpannableString("Styled").apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val pref = preference(text)
        pref.setCustomSummary("Value")
        pref.onBindViewHolder(holder)
        assertEquals(View.VISIBLE, summary.visibility)
        assertEquals("Styled", summary.text.toString())
        assertEquals("Value", hint.text.toString())
        pref.setCustomSummary(null)
        pref.onBindViewHolder(holder)
        assertEquals(View.VISIBLE, summary.visibility)
        assertEquals(1, (summary.text as Spanned).getSpans(0, text.length, StyleSpan::class.java).size)
        assertEquals(View.GONE, hint.visibility)
        assertTrue(hint.text.isNullOrEmpty())
    }

    @Test fun countRowsAndOrdinaryRowsDoNotLeakEitherSummary() {
        AppHelper.appPrefs!!.edit().putStringSet("pref_key_summary_recycling", setOf("one", "two")).commit()
        preference("Selection rule", count = true).onBindViewHolder(holder)
        assertEquals(View.VISIBLE, summary.visibility)
        assertEquals("Selection rule", summary.text.toString())
        assertEquals(View.VISIBLE, hint.visibility)
        assertEquals("2", hint.text.toString())
        preference().onBindViewHolder(holder)
        assertEquals(View.GONE, summary.visibility)
        assertEquals(View.GONE, hint.visibility)
        assertTrue(hint.text.isNullOrEmpty())
    }

    @Test fun aListValueAndItsExplanationStayVisibleTogetherAndBothRebindCleanly() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(R.attr.valueAsSummary, "true").build()
        val pref = ListPreferenceEx(context, attrs).apply {
            title = "Waveform"; entries = arrayOf("Off", "Real"); entryValues = arrayOf("0", "2")
            value = "2"; summary = "Used by media and recording"
        }
        pref.onBindViewHolder(holder)
        assertEquals(View.VISIBLE, summary.visibility)
        assertEquals("Used by media and recording", summary.text.toString())
        assertEquals("Real", hint.text.toString())
        pref.summary = null; pref.value = "0"; pref.onBindViewHolder(holder)
        assertEquals(View.GONE, summary.visibility)
        assertTrue(summary.text.isNullOrEmpty())
        assertEquals("Off", hint.text.toString())
    }

    @Test fun plainNavigationDoesNotCreateAHiddenValueWidget() {
        preference("Description").onBindViewHolder(holder)
        assertNull(hint)
        val pref = preference().apply { setCustomSummary("Value") }
        pref.onBindViewHolder(holder)
        assertEquals("Value", hint.text.toString())
        preference().onBindViewHolder(holder)
        assertEquals(View.GONE, hint.visibility)
        assertTrue(hint.text.isNullOrEmpty())
    }

    @Test fun warningColorCannotLeakIntoAnOrdinaryOrDisabledRecycledRow() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet().addAttribute(R.attr.warning, "true").build()
        PreferenceEx(context, attrs).apply { title = "Warning" }.onBindViewHolder(holder)
        val title = holder.itemView.findViewById<TextView>(android.R.id.title)
        assertEquals(tv.withaibuild.customiuizer.utils.Helpers.markColor, title.currentTextColor)
        val ordinary = preference("Description")
        ordinary.onBindViewHolder(holder)
        val normal = context.getColorStateList(R.color.preference_primary_text_color)
        assertEquals(normal.defaultColor, title.currentTextColor)
        ordinary.isEnabled = false; ordinary.onBindViewHolder(holder)
        assertEquals(normal.getColorForState(intArrayOf(-android.R.attr.state_enabled), normal.defaultColor), title.currentTextColor)
    }
}
