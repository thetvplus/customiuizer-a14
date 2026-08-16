package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPromptPolicyTest {
    @Test
    fun notificationOnlyRequestCanBeSuppressed() {
        assertTrue(
            PermissionPromptPolicy.shouldSuppress(
                listOf("android.permission.POST_NOTIFICATIONS"),
                blockNotifications = true,
                blockLocation = false,
            ),
        )
    }

    @Test
    fun foregroundAndBackgroundLocationCanBeSuppressed() {
        assertTrue(
            PermissionPromptPolicy.shouldSuppress(
                listOf(
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_BACKGROUND_LOCATION",
                ),
                blockNotifications = false,
                blockLocation = true,
            ),
        )
    }

    @Test
    fun emptyDisabledAndMixedRequestsArePreserved() {
        assertFalse(PermissionPromptPolicy.shouldSuppress(emptyList(), true, true))
        assertFalse(
            PermissionPromptPolicy.shouldSuppress(
                listOf("android.permission.POST_NOTIFICATIONS"),
                blockNotifications = false,
                blockLocation = true,
            ),
        )
        assertFalse(
            PermissionPromptPolicy.shouldSuppress(
                listOf(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.CAMERA",
                ),
                blockNotifications = true,
                blockLocation = true,
            ),
        )
    }
}
