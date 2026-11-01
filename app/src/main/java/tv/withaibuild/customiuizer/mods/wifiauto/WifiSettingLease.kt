package tv.withaibuild.customiuizer.mods.wifiauto

/** One property's ownership, including a sticky manual veto until the matched-network session ends. */
internal class WifiSettingLease {
    var original: Int? = null
        private set
    var last: Int? = null
        private set
    var scene: String? = null
        private set
    var owned = false
        private set
    var overridden = false
        private set
    val tracked get() = last != null

    fun observe(current: Int?) {
        if (last != null && current != null && current != last) abandon(true)
    }

    fun prepare(current: Int?, sceneId: String?) {
        if (overridden || current == null) return
        if (last == null) { original = current; last = current }
        scene = sceneId
    }

    fun apply(target: Int?, current: Int?, sceneId: String?, write: (Int) -> Int?): Boolean {
        observe(current)
        if (overridden || current == null) return false
        if (target == null) {
            val restore = original
            val changed = owned && restore != null && current != restore
            if (changed) {
                val result = write(restore)
                if (result == restore) abandon(false)
                else observe(result) // Unknown/unchanged: retain the one bounded restoration anchor.
            } else abandon(false)
            return changed
        }
        prepare(current, sceneId)
        if (target == current) return false
        val observed = write(target)
        if (observed == target) { last = target; owned = true }
        else if (owned) observe(observed)
        else abandon(true)
        return true
    }

    /** Native DND can change internal ringer mode. Its confirmed echo is not a user's new command. */
    fun nativeSideEffect(current: Int?) {
        if (last != null && current != null && !overridden) { last = current; owned = owned || current != original }
    }
    fun abandon(manual: Boolean) { original = null; last = null; scene = null; owned = false; overridden = overridden || manual }
    fun end() { abandon(false); overridden = false }
    fun finishSession() { if (!owned) end() }
}
