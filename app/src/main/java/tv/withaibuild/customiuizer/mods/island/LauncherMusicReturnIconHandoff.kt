package tv.withaibuild.customiuizer.mods.island

/** Only the synchronous native icon setup borrows the original params. No View/owner survives animTo. */
internal class LauncherMusicReturnIconHandoff {
    private data class Entry(val owner: Any, val copy: Any, val original: Any)
    private val current = ThreadLocal<Entry>()

    fun nativeParams(owner: Any?, submitted: Any): Any {
        val entry = current.get()
        return if (entry != null && entry.owner === owner && entry.copy === submitted) entry.original else submitted
    }

    fun <T> during(owner: Any, copy: Any, original: Any, block: () -> T): T {
        val previous = current.get()
        current.set(Entry(owner, copy, original))
        try {
            return block()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }
}
