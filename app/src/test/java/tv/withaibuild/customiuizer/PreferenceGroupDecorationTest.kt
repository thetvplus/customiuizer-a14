package tv.withaibuild.customiuizer

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.prefs.PreferenceCategoryEx
import tv.withaibuild.customiuizer.utils.Helpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PreferenceGroupDecorationTest {

    private val context: Context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)
    private val manager = PreferenceManager(context)
    private var screen = manager.createPreferenceScreen(context).apply {
        repeat(2) { group ->
            val category = PreferenceCategory(context).apply { key = "category$group" }
            addPreference(category)
            repeat(20) { category.addPreference(Preference(context)) }
        }
    }
    private val adapter = CountingAdapter(context)
    private val list = RecyclerView(context).apply { adapter = this@PreferenceGroupDecorationTest.adapter }
    private val decoration = PreferenceGroupDecoration { screen }
    private val canvas = Canvas()
    private val state = RecyclerView.State()

    @Test
    fun aHiddenPageContainerPromotesOnlyItsImmediateHeadings() {
        val page = PreferenceCategoryEx(context, null)
        screen.addPreference(page)
        page.hide()
        val section = PreferenceCategoryEx(context, null)
        val inner = PreferenceCategoryEx(context, null)
        page.addPreference(section)
        section.addPreference(inner)
        assertFalse(preferenceGroupInputRow(page).titleVisible)
        assertTrue(preferenceGroupInputRow(section).isTopLevel)
        assertFalse(preferenceGroupInputRow(inner).isTopLevel)

        val holder = PreferenceViewHolder.createInstanceForTests(
            LayoutInflater.from(context).inflate(R.layout.preference_category, android.widget.FrameLayout(context), false),
        )
        section.onBindViewHolder(holder)
        assertEquals(context.resources.getDimensionPixelSize(R.dimen.preference_group_header_padding), holder.itemView.paddingStart)
        inner.onBindViewHolder(holder)
        assertEquals(context.resources.getDimensionPixelSize(R.dimen.preference_item_child_padding), holder.itemView.paddingStart)
    }

    @Test
    fun rebindingStatusKeepsTitlesCleanAndExplainsUnavailableOptions() {
        val title = TextView(context)
        repeat(2) { Helpers.bindPreferenceTitle(title, "Feature", false, true) }
        assertEquals("Feature", title.text.toString())
        assertEquals(context.getString(R.string.preference_applies_immediately), title.stateDescription)
        repeat(2) { Helpers.bindPreferenceTitle(title, "Feature", true, false) }
        assertEquals("Feature · ${context.getString(R.string.preference_unavailable)}", title.text.toString())
        Helpers.bindPreferenceTitle(title, "Feature", false, false)
        assertEquals("Feature", title.text.toString())
        assertEquals(null, title.stateDescription)
    }

    @Test
    fun repeatedFramesReuseOneDisplayedSnapshot() {
        repeat(25) {
            decoration.onDraw(canvas, list, state)
            decoration.onDrawOver(canvas, list, state)
        }
        assertEquals("Two category lookups for the whole unchanged page", 2, adapter.lookups)
    }

    @Test
    fun everyAdapterChangeInvalidatesTheSnapshotEvenWhenCountIsUnchanged() {
        decoration.onDraw(canvas, list, state)
        val changes = listOf<() -> Unit>(
            { adapter.notifyDataSetChanged() },
            { adapter.notifyItemChanged(0) },
            { adapter.notifyItemChanged(0, "title") },
            { adapter.notifyItemInserted(1) },
            { adapter.notifyItemRemoved(1) },
            { adapter.notifyItemMoved(1, 2) },
        )
        for ((index, change) in changes.withIndex()) {
            change()
            decoration.onDraw(canvas, list, state)
            decoration.onDrawOver(canvas, list, state)
            assertEquals(2 * (index + 2), adapter.lookups)
        }
    }

    @Test
    fun replacingSameSizeScreenRebuildsTheSnapshot() {
        decoration.onDraw(canvas, list, state)
        screen = manager.createPreferenceScreen(context).apply {
            addPreference(PreferenceCategory(context))
        }
        decoration.onDraw(canvas, list, state)
        assertEquals(3, adapter.lookups)
    }

    @Test
    fun replacingAdapterReleasesOldObserver() {
        decoration.onDraw(canvas, list, state)
        val replacement = CountingAdapter(context)
        list.adapter = replacement
        decoration.onDraw(canvas, list, state)
        assertFalse("Old adapter must not retain the decoration", adapter.hasObservers())
        assertEquals(2, replacement.lookups)
    }

    @Test
    fun disposalReleasesObserverAndCachedScreen() {
        decoration.onDraw(canvas, list, state)
        decoration.dispose()
        list.adapter = null
        assertFalse("Destroyed page must leave no adapter observer", adapter.hasObservers())
        list.adapter = adapter
        decoration.onDraw(canvas, list, state)
        assertEquals(4, adapter.lookups)
    }

    private class CountingAdapter(private val context: Context) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>(), PreferenceGroup.PreferencePositionCallback {
        var lookups = 0
        override fun getItemCount() = 42
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(View(context)) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        override fun getPreferenceAdapterPosition(key: String) = RecyclerView.NO_POSITION
        override fun getPreferenceAdapterPosition(preference: Preference): Int {
            lookups++
            return if (preference.key == "category0") 0 else 21
        }
    }
}
