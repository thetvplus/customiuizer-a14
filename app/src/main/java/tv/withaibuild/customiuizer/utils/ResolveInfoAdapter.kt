package tv.withaibuild.customiuizer.utils

import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.drawable.TransitionDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.applyGroupedListRow
import java.util.ArrayList
import java.util.Locale

class ResolveInfoAdapter(context: Context, arr: ArrayList<ResolveInfo>) : BaseAdapter(), Filterable {

    private val ctx: Context = context
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val filter = ItemFilter()
    private class Entry(val resolveInfo: ResolveInfo, val app: AppData)

    // Labels and loader inputs belong to this adapter. Rebinding must not reload package
    // resources or leave the loader with only a weak reference to a temporary AppData.
    private val originalAppList = ArrayList<Entry>(arr.size).apply {
        val pm = context.packageManager
        for (ri in arr) {
            val app = AppData().apply {
                pkgName = ri.activityInfo.applicationInfo.packageName
                actName = ri.activityInfo.name
                enabled = ri.activityInfo.enabled
                label = ri.loadLabel(pm).toString()
                prepareForList()
            }
            add(Entry(ri, app))
        }
    }
    // Worker-created results are handed to the main thread without further mutation.
    private var filteredAppList: List<Entry> = originalAppList

    override fun getCount(): Int = filteredAppList.size

    override fun getItem(position: Int): ResolveInfo = filteredAppList[position].resolveInfo

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder = (convertView?.tag as? ViewHolder) ?: run {
            val row = inflater.inflate(R.layout.applist_item11, parent, false)
            ViewHolder(row).also { row.tag = it }
        }

        val ad = filteredAppList[position].app
        applyGroupedListRow(holder.root, position, count)
        holder.icon.tag = position

        holder.title.text = ad.label
        holder.disableIcon.visibility = if (ad.enabled) View.INVISIBLE else View.VISIBLE
        val icon = Helpers.memoryCache[ad.iconKey]

        if (icon == null) {
            val dualIcon = arrayOf(ctx.resources.getDrawable(R.drawable.card_icon_default, ctx.theme))
            val crossfader = TransitionDrawable(dualIcon)
            crossfader.setCrossFadeEnabled(true)
            holder.icon.setImageDrawable(crossfader)
            BitmapCachedLoader(holder.icon, ad, ctx).execute()
        } else {
            holder.icon.setImageBitmap(icon)
        }

        return holder.root
    }

    private class ViewHolder(val root: View) {
        val disableIcon: ImageView = root.findViewById(R.id.icon_disable)
        val title: TextView = root.findViewById(android.R.id.title)
        val icon: ImageView = root.findViewById(android.R.id.icon)
    }

    private inner class ItemFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filterString = constraint?.toString()?.lowercase(Locale.ROOT) ?: ""
            val results = FilterResults()
            val nlist = ArrayList<Entry>()

            for (entry in originalAppList) {
                if (entry.app.labelLower.contains(filterString)) {
                    nlist.add(entry)
                }
            }

            results.values = nlist
            results.count = nlist.size
            return results
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            filteredAppList = results?.values as? List<Entry> ?: emptyList()
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter = filter
}
