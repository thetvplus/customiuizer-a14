package tv.withaibuild.customiuizer.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.TransitionDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.applyGroupedListRow
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale

@SuppressLint("WrongConstant")
class PrivacyAppAdapter(
    context: Context,
    arr: ArrayList<AppData>,
    private val privacyAppsMap: HashMap<Int, ArrayList<String>>
) : BaseAdapter(), Filterable {

    private val ctx: Context = context
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val filter = ItemFilter()
    private val originalAppList = ArrayList(arr).apply { for (app in this) app.prepareForList() }
    // The live privacy map and published rows are only read or changed on the main thread.
    private var filteredAppList = ArrayList(originalAppList)

    init {
        sortList()
    }

    private fun sortList() {
        filteredAppList = selectedAppsFirst(filteredAppList) { app ->
            isPrivacyApp(app.pkgName, app.user)
        }
    }

    override fun getCount(): Int = filteredAppList.size

    override fun getItem(position: Int): AppData = filteredAppList[position]

    override fun getItemId(position: Int): Long = position.toLong()

    private fun isPrivacyApp(pkgName: String, user: Int): Boolean {
        return privacyAppsMap[user]?.contains(pkgName) ?: false
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder = (convertView?.tag as? ViewHolder) ?: run {
            val row = inflater.inflate(R.layout.applist_item11, parent, false)
            ViewHolder(row).also { row.tag = it }
        }

        val ad = getItem(position)
        applyGroupedListRow(holder.root, position, count)
        holder.icon.tag = position
        holder.title.text = ad.label
        holder.disableIcon.visibility = if (ad.enabled) View.GONE else View.VISIBLE
        holder.dualIcon.visibility = if (ad.user != 0) View.VISIBLE else View.GONE

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

        try {
            holder.checked.visibility = View.VISIBLE
            holder.checked.isChecked = isPrivacyApp(ad.pkgName, ad.user)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            holder.checked.visibility = View.GONE
        }

        return holder.root
    }

    private class ViewHolder(val root: View) {
        val disableIcon: ImageView = root.findViewById(R.id.icon_disable)
        val dualIcon: ImageView = root.findViewById(R.id.icon_dual)
        val checked: CheckBox = root.findViewById(android.R.id.checkbox)
        val title: TextView = root.findViewById(android.R.id.title)
        val icon: ImageView = root.findViewById(android.R.id.icon)
    }

    private inner class ItemFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filterString = constraint?.toString()?.lowercase(Locale.ROOT) ?: ""
            val results = FilterResults()
            val nlist = ArrayList<AppData>()

            for (app in originalAppList) {
                if (app.labelLower.contains(filterString)) {
                    nlist.add(app)
                }
            }

            results.values = nlist
            results.count = nlist.size
            return results
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            filteredAppList = results?.values as? ArrayList<AppData> ?: ArrayList()
            sortList()
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter = filter
}
