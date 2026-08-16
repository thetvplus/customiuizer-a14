package tv.withaibuild.customiuizer.mods.utils

/**
 * Resolves a field value from [obj] by trying [candidates] in order.
 *
 * For each candidate field:
 * - fatal errors are re-thrown immediately;
 * - non-fatal reflection errors (e.g. [NoSuchFieldError]) continue to the next candidate;
 * - the first value satisfying [predicate] is returned.
 *
 * Returns null when no candidate matches or no candidate exists.
 */
internal object FieldCandidateResolver {

    inline fun <reified T : Any> resolve(obj: Any, candidates: List<String>, crossinline predicate: (Any?) -> Boolean): T? {
        for (fieldName in candidates) {
            val value = try {
                XposedHelpers.getObjectField(obj, fieldName)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                continue
            }
            if (predicate(value)) {
                return value as? T
            }
        }
        return null
    }

    /**
     * Overload that uses [T] as the predicate (`it is T`).
     */
    inline fun <reified T : Any> resolve(obj: Any, candidates: List<String>): T? {
        return resolve(obj, candidates) { it is T }
    }
}
