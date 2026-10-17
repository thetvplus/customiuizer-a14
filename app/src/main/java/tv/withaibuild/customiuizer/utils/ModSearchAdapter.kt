package tv.withaibuild.customiuizer.utils

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import tv.withaibuild.customiuizer.R
import java.util.Locale

class ModSearchAdapter(context: Context) : BaseAdapter(), Filterable {

    private val mInflater: LayoutInflater = LayoutInflater.from(context)
    private val mFilter = ItemFilter()

    /**
     * Mutated only in [ItemFilter.publishResults] and read only in [getView], both on the
     * main thread, so a plain list is correct. It used to be a CopyOnWriteArrayList, which
     * copied the whole array on clear, on addAll and again on sort — three copies per
     * keystroke — for concurrency that never happens.
     */
    private val modsList = ArrayList<ModData>()

    /** The query the currently published results were produced from. Main thread only. */
    private var filterString = ""

    override fun getCount(): Int = modsList.size

    override fun getItem(position: Int): ModData = modsList[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = convertView ?: mInflater.inflate(R.layout.pref_item, parent, false)

        val itemTitle: TextView = row.findViewById(android.R.id.title)
        val itemSummary: TextView = row.findViewById(android.R.id.summary)

        val ad = getItem(position)

        val start = ad.titleLower.indexOf(filterString)
        if (start >= 0) {
            val spannable = SpannableString(ad.title)
            spannable.setSpan(
                ForegroundColorSpan(Helpers.markColorVibrant),
                start,
                start + filterString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            itemTitle.setText(spannable, TextView.BufferType.SPANNABLE)
        } else {
            itemTitle.text = ad.title
        }
        itemSummary.text = ad.breadcrumbs

        return row
    }

    private inner class ItemFilter : Filter() {

        /**
         * Runs on the filter's worker thread, so it reads the index and touches no adapter
         * state. Both the query comparison and the lowered query are loop-invariant and are
         * computed once; the per-mod lowered title comes from the index.
         *
         * The results keep the index order, which [Helpers.getAllMods] already sorted, so
         * there is nothing left to sort here.
         */
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString().orEmpty()
            val loweredQuery = query.lowercase(Locale.ROOT)
            val newModsOnly = query == Helpers.NEW_MODS_SEARCH_QUERY
            val source = Helpers.allModsList

            val matches = ArrayList<ModData>(if (loweredQuery.isEmpty()) source.size else 16)
            for (mod in source) {
                val matched =
                    if (newModsOnly) Helpers.newMods.contains(mod.key)
                    else mod.titleLower.contains(loweredQuery)
                if (matched) matches.add(mod)
            }

            return FilterResults().apply {
                values = matches
                count = matches.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            // Adopt the query these results came from rather than the latest one typed:
            // performFiltering runs on a worker thread, so a newer query can already be in
            // flight, and the highlight span must match the rows actually being shown.
            filterString = constraint?.toString()?.lowercase(Locale.ROOT).orEmpty()
            modsList.clear()
            (results?.values as? ArrayList<ModData>)?.let { modsList.addAll(it) }
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter = mFilter
}
