package tv.withaibuild.customiuizer.mods.recents

import android.view.View
import android.view.ViewGroup

/** One depth owner for admitted cards. Native navigation can write a temporary
 * running-task Z and clear it later; neither write may reorder a Deck frame. */
internal class DeckDepthOrder(private val parent: ViewGroup) {
    inner class Entry(val view: View,var index: Int,private val projection: Float) {
        private var nativeZ=view.translationZ
        private var appliedZ=Float.NaN
        fun apply() {
            val current=view.translationZ
            if(current!=appliedZ) nativeZ=current
            if(view.parent!==parent) return
            // Z controls both sibling sorting and hardware shadow projection.
            // A common positive plane preserves rank without cancelling shadows.
            val target=projection+1f/(index+1)-view.elevation
            if(current!=target) view.translationZ=target
            appliedZ=target
        }
        fun release() {
            if(view.translationZ==appliedZ) view.translationZ=nativeZ
        }
    }
    private val entries=ArrayList<Entry>(8)
    fun add(view: View,index: Int,projection: Float=0f): Entry=Entry(view,index,projection).also { entries.add(it) }
    fun remove(entry: Entry) { entries.remove(entry); entry.release() }
    fun apply() {
        // Called only by the active stack's existing pre-draw, without requesting
        // a recurring frame, allocating an iterator or resolving a native member.
        for(i in 0 until entries.size) entries[i].apply()
    }
}
