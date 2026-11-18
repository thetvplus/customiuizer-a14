package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import java.lang.reflect.Method

/** One prepared feature, one attempt. Failed wrappers remain passive forever.
 * Handles live only during installation; native callbacks own the commit flag.
 * A failed cleanup is permanent so a later attempt cannot pile up live wrappers. */
internal class PreparedHookBatch(
    private val installHook: (Method, MethodHook) -> CustomMethodUnhooker? = ModuleHelper::hookMethod,
) {
    @Volatile var isActive = false
        private set
    private var attempted = false

    fun install(entries: List<Pair<Method, MethodHook>>): FeatureInstallResult {
        synchronized(this) {
            if (attempted) return if (isActive) FeatureInstallResult.ALREADY_INSTALLED else FeatureInstallResult.FAILED_PERMANENT
            attempted = true
        }
        if (entries.isEmpty()) return FeatureInstallResult.FAILED_PERMANENT
        val handles = ArrayList<CustomMethodUnhooker>(entries.size)
        var failure: Throwable? = null
        try {
            for ((method, callback) in entries) {
                val gated = object : MethodHook(callback.mPriority) {
                    override fun intercept(chain: XposedInterface.Chain): Any? = when {
                        !isActive -> chain.proceed()
                        callback.mIsReturnConstant -> callback.mReturnConstantValue
                        else -> callback.intercept(chain)
                    }
                }
                val handle = installHook(method, gated) ?: break
                handles.add(handle)
            }
            if (handles.size == entries.size) {
                isActive = true
                return FeatureInstallResult.INSTALLED
            }
        } catch (t: Throwable) {
            failure = t
        }
        val cleanupErrors = ArrayList<Throwable>()
        for (handle in handles.asReversed()) try { handle.unhook() } catch (t: Throwable) { cleanupErrors.add(t) }
        failure?.let { rethrowFatal(it, cleanupErrors) }
        for (error in cleanupErrors) rethrowFatal(error, listOfNotNull(failure) + cleanupErrors)
        failure?.let { XposedHelpers.log(it) }
        cleanupErrors.forEach { XposedHelpers.log(it) }
        return if (cleanupErrors.isEmpty()) FeatureInstallResult.FAILED_TRANSIENT else FeatureInstallResult.FAILED_PERMANENT
    }

    private fun rethrowFatal(error: Throwable, others: List<Throwable>) {
        try { FatalErrors.unwrapAndRethrowIfFatal(error) } catch (fatal: Throwable) {
            for (other in others) if (other !== fatal && other !== error) fatal.addSuppressed(other)
            throw fatal
        }
    }
}
