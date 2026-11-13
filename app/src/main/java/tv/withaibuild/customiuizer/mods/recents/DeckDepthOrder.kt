package tv.withaibuild.customiuizer.mods.recents

import android.view.View
import android.view.ViewGroup

/** One depth owner for admitted cards. Native navigation can write a temporary
 * running-task Z and clear it later; neither write may reorder a Deck frame. */
internal class DeckDepthOrder(private val parent: ViewGroup) {
    // Use the same projection plane for all cards. Rank offsets separate
    // each card's shadow from its neighbor's content.
    private val projection = 7f * parent.resources.displayMetrics.density

    inner class Entry(val view: View,index: Int) {
        var index=index
            set(value) {
                if(field==value) return
                field=value
                orderDirty=true
            }
        var rank=0
        private var nativeZ=view.translationZ
        private var appliedZ=Float.NaN
        fun apply() {
            if(orderDirty) {
                this@DeckDepthOrder.apply()
                return
            }
            val current=view.translationZ
            if(current!=appliedZ) nativeZ=current
            if(view.parent!==parent) return
            val target=projection+1f-rank*.125f-view.elevation
            if(current!=target) view.translationZ=target
            appliedZ=target
        }
        fun release() {
            if(view.translationZ==appliedZ) view.translationZ=nativeZ
        }
    }
    private val entries=ArrayList<Entry>(8)
    private var orderDirty=false
    fun add(view: View,index: Int): Entry=Entry(view,index).also {
        entries.add(it)
        orderDirty=true
    }
    fun remove(entry: Entry) {
        if(entries.remove(entry)) orderDirty=true
        entry.release()
    }
    fun apply() {
        if(orderDirty) {
            entries.sortBy { it.index }
            // Android 14 batches shadows with a Z difference below 0.1 px.
            // Use attached ranks so even distant task indices can cast shadows
            // onto the next card without moving the whole stack in Z.
            for(i in entries.indices) entries[i].rank=i
            orderDirty=false
        }
        for(i in entries.indices) entries[i].apply()
    }
}
