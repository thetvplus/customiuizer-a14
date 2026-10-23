package tv.withaibuild.customiuizer.mods

/** One native launch call, including nested calls. Nothing survives return or failure. */
internal class NotificationLaunchScope<T : Any> {
    private val value = ThreadLocal<T?>()

    fun <R> within(request: T?, action: () -> R): R {
        val previous = value.get()
        value.set(request) // A normal nested launch must mask an outer floating launch too.
        return try { action() } finally {
            if (previous == null) value.remove() else value.set(previous)
        }
    }

    fun consumeIf(matches: (T) -> Boolean): T? {
        val request = value.get() ?: return null
        if (!matches(request)) return null
        value.remove() // Consume before computing options; reentrant work cannot inherit them.
        return request
    }
}
