package tv.withaibuild.customiuizer.mods

import java.util.concurrent.atomic.AtomicReference

/** The legacy tile request has one bounded slot, an explicit user and no timer or retained host. */
internal class FreeformRequest(private val now: () -> Long) {
    private data class Request(val pkg: String, val user: Int, val component: String, val expires: Long)
    private val pending = AtomicReference<Request?>()

    fun replace(pkg: String?, user: Int, component: String?) {
        pending.set(pkg?.takeIf { it.isNotBlank() && user >= 0 && component?.startsWith("$it/") == true }
            ?.let { Request(it, user, component!!, now() + 5000) })
    }

    fun consume(pkg: String, user: Int, component: String?, caller: String?): Boolean {
        while (true) {
            val request = pending.get() ?: return false
            val expired = now() >= request.expires
            if (!expired && (request.pkg != pkg || request.user != user || request.component != component || caller != "com.android.systemui")) return false
            if (pending.compareAndSet(request, null)) return !expired
        }
    }
}
