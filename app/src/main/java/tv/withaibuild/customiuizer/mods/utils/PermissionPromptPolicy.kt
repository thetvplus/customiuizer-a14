package tv.withaibuild.customiuizer.mods.utils

internal object PermissionPromptPolicy {
    private const val NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    private val locationPermissions = setOf(
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
    )

    fun shouldSuppress(
        permissions: Collection<String>,
        blockNotifications: Boolean,
        blockLocation: Boolean,
    ): Boolean {
        if (permissions.isEmpty()) return false
        for (permission in permissions) {
            val allowed = when {
                permission == NOTIFICATIONS -> blockNotifications
                permission in locationPermissions -> blockLocation
                else -> false
            }
            if (!allowed) return false
        }
        return true
    }
}
