package tv.withaibuild.customiuizer.mods.recents

/** Keep native pose springs only while their completion work is outstanding.
 * Native listeners may finish after the updateTaskView invocation has returned. */
internal class DeckPoseCompletion {
    private var pending=0
    val active: Boolean get()=pending!=0

    fun track(callback: Runnable): Completion {
        pending++
        return Completion(callback)
    }

    inner class Completion(private val callback: Runnable): Runnable {
        private var outstanding=true
        override fun run() {
            try { callback.run() }
            finally { finish() }
        }
        // A failed native invocation never installed its completion listener.
        fun finish() {
            if(outstanding) { outstanding=false; pending-- }
        }
    }
}
