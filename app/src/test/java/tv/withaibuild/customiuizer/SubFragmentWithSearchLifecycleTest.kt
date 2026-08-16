package tv.withaibuild.customiuizer

import android.content.Context
import android.view.View
import android.widget.ListView
import android.widget.TextView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Regression test for the P6-B1 SubFragmentWithSearch lifecycle fix.
 *
 * Verifies that [onDestroyView] releases the Fragment-owned View references
 * and resets the search-focus state.
 */
class SubFragmentWithSearchLifecycleTest {

    @Test
    fun onDestroyView_releases_view_references_and_resets_focus() {
        val fragment = SubFragmentWithSearch()

        // The Android unit-test classpath uses stubs; a null Context is sufficient
        // for these objects because the test only checks the Fragment field values.
        val listView = ListView(null as Context?)
        val searchView = View(null as Context?)
        val textInput = TextView(null as Context?)

        fragment.listView = listView
        setPrivateField(fragment, "searchView", searchView)
        setPrivateField(fragment, "textInput", textInput)
        setPrivateField(fragment, "isSearchFocused", true)

        fragment.onDestroyView()

        assertNull(fragment.listView)
        assertNull(getPrivateField<View>(fragment, "searchView"))
        assertNull(getPrivateField<TextView>(fragment, "textInput"))
        assertFalse(getPrivateField<Boolean>(fragment, "isSearchFocused") ?: true)
    }

    @Test
    fun sub_fragment_with_search_extends_sub_fragment() {
        assertSame(SubFragment::class.java, SubFragmentWithSearch::class.java.superclass)
    }

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(target: Any, name: String): T? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T?
    }
}
