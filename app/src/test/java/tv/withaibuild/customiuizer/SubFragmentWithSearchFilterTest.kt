package tv.withaibuild.customiuizer

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Filterable
import android.widget.HeaderViewListAdapter
import android.widget.ListView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SubFragmentWithSearchFilterTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun dispatch(fragment: SubFragmentWithSearch, query: String) {
        ReflectionHelpers.callInstanceMethod<Any?>(fragment, "applyFilter", ClassParameter.from(String::class.java, query))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun platformAdapterFiltersAndClearsThroughTheSameSearchEntry() {
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, arrayListOf("Alpha", "Beta", "Betelgeuse"))
        val list = ListView(context).apply { this.adapter = adapter }
        val fragment = SubFragmentWithSearch().apply { listView = list }
        dispatch(fragment, "be")
        assertEquals(2, adapter.count)
        assertEquals("Beta", adapter.getItem(0))
        assertEquals("Betelgeuse", adapter.getItem(1))
        dispatch(fragment, "")
        assertEquals(3, adapter.count)
        assertSame(adapter, fragment.listView!!.adapter)
    }

    @Test fun nativeHeaderWrapperDelegatesFilteringWithoutReplacingTheListOrHeader() {
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, arrayListOf("Alpha", "Beta", "Betelgeuse"))
        val list = ListView(context).apply { addHeaderView(View(context)); this.adapter = adapter }
        val wrapper = list.adapter
        assertTrue(wrapper is HeaderViewListAdapter)
        val fragment = SubFragmentWithSearch().apply { listView = list }
        dispatch(fragment, "be")
        assertEquals(2, adapter.count)
        assertEquals(3, list.count)
        assertSame(wrapper, list.adapter)
        assertSame(list, fragment.listView)
    }

    @Test fun nonFilterableContentAndAnUnboundViewRemainNoOps() {
        val adapter = object : BaseAdapter() {
            override fun getCount() = 1
            override fun getItem(position: Int) = "Fixed"
            override fun getItemId(position: Int) = 0L
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?) = convertView ?: View(context)
        }
        val list = ListView(context).apply { addHeaderView(View(context)); this.adapter = adapter }
        val wrapper = list.adapter
        assertNull((wrapper as Filterable).filter)
        val fragment = SubFragmentWithSearch().apply { listView = list }
        dispatch(fragment, "anything")
        assertEquals(2, list.count)
        assertSame(wrapper, list.adapter)
        fragment.listView = null
        dispatch(fragment, "anything")
    }
}
