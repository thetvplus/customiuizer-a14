package tv.withaibuild.customiuizer.mods

import android.os.Handler
import android.util.ArrayMap
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Follows the ROM's fresh timestamp -> mask-observer scheduling event under its own user lock. */
internal class AppLockTimeoutAccess private constructor(
    val schedule: Method,
    val postDelayed: Method,
    private val service: Field,
    private val userLock: Field,
    private val userState: Method,
    private val lastCheck: Field,
    private val handler: Field,
    private val minutes: () -> Int,
) {
    private class Scope(val handler: Any, val token: String, val timeout: Long, val map: ArrayMap<*, *>, val pkg: String)
    private class Pending(val map: ArrayMap<String, Long>, val pkg: String, val original: Long, val adjusted: Long, val scope: Scope)
    private val active = ThreadLocal<Scope>()

    @Suppress("UNCHECKED_CAST")
    private fun prepareCall(chain: XposedInterface.Chain): Pending? {
        val timeout = minutes().coerceIn(1, 60) * 60000L
        if (timeout == 60000L) return null
        val pkg = chain.getArg(0) as? String ?: return null
        val user = chain.getArg(1) as? Int ?: return null
        if (pkg.isEmpty() || user < 0) return null
        val owner = chain.thisObject
        val host = service.get(owner) ?: return null
        val lock = userLock.get(host) ?: return null
        // Do not introduce a lock or touch timestamps from any unverified caller.
        if (!Thread.holdsLock(lock)) return null
        val state = userState.invoke(host, user) ?: return null
        val map = lastCheck.get(state) as? ArrayMap<String, Long> ?: return null
        val previous = active.get()
        if (previous?.map === map && previous.pkg == pkg) return null
        val original = map[pkg] ?: return null
        val offset = timeout - 60000L
        if (original < 0L || original > Long.MAX_VALUE - offset) return null
        val targetHandler = handler.get(owner) as? Handler ?: return null
        return Pending(map, pkg, original, original + offset, Scope(targetHandler, "${pkg}_$user", timeout, map, pkg))
    }

    fun scheduleHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val pending = try { prepareCall(chain) } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log(t)
                null
            } ?: return chain.proceed()
            val previous = active.get()
            pending.map[pending.pkg] = pending.adjusted
            active.set(pending.scope)
            try {
                return chain.proceed()
            } catch (t: Throwable) {
                if (pending.map[pending.pkg] == pending.adjusted) pending.map[pending.pkg] = pending.original
                throw t
            } finally {
                if (previous == null) active.remove() else active.set(previous)
            }
        }
    }

    fun handlerDelayHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val scope = active.get() ?: return chain.proceed()
            val args = try {
                if (chain.thisObject !== scope.handler || chain.getArg(1) != scope.token || chain.getArg(2) != 60000L) null
                else {
                    val argsRewritten = XposedHelpers.getArgsArray(chain)
                    argsRewritten[2] = scope.timeout
                    argsRewritten
                }
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log(t)
                null
            }
            return if (args == null) chain.proceed() else chain.proceed(args)
        }
    }

    companion object {
        fun prepare(implementation: Class<*>, host: Class<*>, state: Class<*>, minutes: () -> Int): AppLockTimeoutAccess? {
            return try {
                fun field(type: Class<*>, name: String, expected: Class<*>) = XposedHelpers.findField(type, name).also {
                    require(expected.isAssignableFrom(it.type)); it.isAccessible = true
                }
                val schedule = implementation.getDeclaredMethod("scheduleForMaskObserver", String::class.java, Int::class.javaPrimitiveType!!)
                val userState = host.getDeclaredMethod("getUserStateLocked", Int::class.javaPrimitiveType!!)
                require(schedule.returnType == Void.TYPE && userState.returnType == state)
                schedule.isAccessible = true; userState.isAccessible = true
                AppLockTimeoutAccess(schedule, Handler::class.java.getDeclaredMethod("postDelayed", Runnable::class.java, Any::class.java, Long::class.javaPrimitiveType!!),
                    field(implementation, "mService", host), field(host, "mUserStateLock", Any::class.java), userState,
                    field(state, "mAccessControlLastCheck", ArrayMap::class.java), field(implementation, "mHandler", Handler::class.java), minutes)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log(t)
                null
            }
        }
    }
}
