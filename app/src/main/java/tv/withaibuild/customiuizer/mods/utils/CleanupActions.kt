package tv.withaibuild.customiuizer.mods.utils

/** Finish independent releases before propagating a failure, including wrapped fatal errors. */
internal class CleanupActions(primary: Throwable? = null) {
    private val failures = ArrayList<Throwable>().apply { primary?.let(::add) }
    fun run(action: () -> Unit) {
        try { action() } catch (error: Throwable) { failures.add(error) }
    }
    fun finish() {
        for (error in failures) {
            try { FatalErrors.unwrapAndRethrowIfFatal(error) } catch (fatal: Throwable) {
                for (other in failures) if (other !== error && other !== fatal) fatal.addSuppressed(other)
                throw fatal
            }
        }
        val first = failures.firstOrNull() ?: return
        for (other in failures.drop(1)) if (other !== first) first.addSuppressed(other)
        throw first
    }
}
