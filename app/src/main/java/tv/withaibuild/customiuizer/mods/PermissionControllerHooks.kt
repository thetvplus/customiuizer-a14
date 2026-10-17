package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.PermissionPromptPolicy
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

object PermissionControllerHooks {
    private const val ACTIVITY =
        "com.android.permissioncontroller.permission.ui.GrantPermissionsActivity"

    @JvmStatic
    fun install(
        lpparam: PackageReadyParam,
        blockNotifications: Boolean,
        blockLocation: Boolean,
    ) {
        if (!blockNotifications && !blockLocation) return

        ModuleHelper.hookAllMethods(
            ACTIVITY,
            lpparam.classLoader,
            "onRequestInfoLoad",
            object : MethodHook() {
                override fun before(callback: BeforeHookCallback) {
                    try {
                        val activity = callback.getThisObject() ?: return
                        val requested = XposedHelpers.getObjectField(
                            activity,
                            "mRequestedPermissions",
                        ) as? Collection<*> ?: return
                        val permissions = requested.filterIsInstance<String>()
                        if (permissions.size != requested.size) return
                        if (!PermissionPromptPolicy.shouldSuppress(
                                permissions,
                                blockNotifications,
                                blockLocation,
                            )
                        ) return

                        XposedHelpers.callMethod(activity, "setResultAndFinish")
                        callback.returnAndSkip(null)
                    } catch (oom: OutOfMemoryError) {
                        throw oom
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        XposedHelpers.log("Permission prompt suppression failed: ${t.javaClass.simpleName}")
                    }
                }
            },
        )
    }
}
