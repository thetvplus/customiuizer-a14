package tv.withaibuild.customiuizer.mods.utils

/**
 * Process-level snapshot of the libxposed capabilities available on this host.
 *
 * The snapshot is computed once from the real [XposedInterface.getApiVersion] and then
 * stored as fixed bits.  There are no Maps, no reflection, no threads and no Context
 * held here.  Callers on hot paths simply read the pre-computed bits.
 */
internal object XposedApiCapabilities {

    private const val STABLE_HOOK_ID = 1
    private const val REPLACE_HOOK = 1 shl 1

    @Volatile
    private var flags = 0

    /**
     * Records the capabilities for the current process.
     *
     * Called once from the module entry cold path (e.g. [MainModule.onModuleLoaded]).
     * Repeated calls are idempotent.
     */
    @JvmStatic
    fun initialize(apiVersion: Int) {
        flags = if (apiVersion >= 102) {
            STABLE_HOOK_ID or REPLACE_HOOK
        } else {
            0
        }
    }

    /** True if the host supports [XposedInterface.HookBuilder.setId]. */
    @JvmStatic
    fun supportsStableHookId(): Boolean = flags and STABLE_HOOK_ID != 0

    /** True if the host supports [XposedInterface.HookHandle.replaceHook]. */
    @JvmStatic
    fun supportsReplaceHook(): Boolean = flags and REPLACE_HOOK != 0
}
