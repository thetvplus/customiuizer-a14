package tv.withaibuild.customiuizer.installers

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.PermissionControllerHooks
import tv.withaibuild.customiuizer.utils.PrefMap

object PermissionControllerInstaller {
    private const val BLOCK_NOTIFICATIONS = "various_block_notification_permission_prompts"
    private const val BLOCK_LOCATION = "various_block_location_permission_prompts"

    @JvmStatic
    fun install(lpparam: PackageReadyParam, prefs: PrefMap) {
        val blockNotifications = prefs.getBoolean(BLOCK_NOTIFICATIONS)
        val blockLocation = prefs.getBoolean(BLOCK_LOCATION)
        if (!blockNotifications && !blockLocation) return
        PermissionControllerHooks.install(lpparam, blockNotifications, blockLocation)
    }
}
