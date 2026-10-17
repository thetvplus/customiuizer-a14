package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedInterface

/**
 * API 102 installation policy shared by method and constructor hooks.
 * IDs identify only the existing infrastructure hooks; ownership and one-time
 * installation remain with their callers. No handles or target objects are retained here.
 */
internal object HookInstallation {
    /** Installation-only path; the callback and its invocation behavior are unchanged. */
    @JvmStatic
    fun intercept(
        builder: XposedInterface.HookBuilder,
        hook: HookerClassHelper.MethodHook,
        id: String?,
    ): XposedInterface.HookHandle {
        val configured = builder.setPriority(hook.mPriority)
            .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
        // The framework must implement API 102. A failed ID assignment is an
        // installation failure, not permission to silently install a different hook.
        val identified = if (id != null) configured.setId(id) else configured
        // Never retry intercept: an installation failure may have framework-side effects.
        val handle = identified.intercept(hook)
        if (id != null) confirmStableHookId(handle, id)
        return handle
    }

    /** Readback is diagnostic only; never retry or remove an already installed hook. */
    private fun confirmStableHookId(handle: XposedInterface.HookHandle, id: String) {
        val actualId = try {
            handle.id
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("API 102 hook ID readback unavailable for $id: ${t.javaClass.simpleName}")
            return
        }
        if (actualId == id) {
            XposedHelpers.log("API 102 stable hook ID installed: $id (confirmed)")
        } else {
            XposedHelpers.log("API 102 hook ID not confirmed: $id")
        }
    }

    /** Match the legacy declared-method selection; IDs are scoped per executable. */
    @JvmStatic
    fun interceptAllWithStableId(hookClass: Class<*>, methodName: String, hook: HookerClassHelper.MethodHook, id: String): Boolean {
        var installed = false
        for (method in hookClass.declaredMethods) {
            if (method.name == methodName) {
                intercept(XposedHelpers.moduleInst.hook(method), hook, id)
                installed = true
            }
        }
        return installed
    }

    /** Short, fixed stable hook IDs for infrastructure hooks.  Never dynamically joined. */
    const val STABLE_ID_SYSTEMUI_INIT = "systemui.init"
    const val STABLE_ID_PLUGIN_CREATE = "systemui.plugin.create"
    const val STABLE_ID_APPLICATION_ATTACH = "application.attach"
}
