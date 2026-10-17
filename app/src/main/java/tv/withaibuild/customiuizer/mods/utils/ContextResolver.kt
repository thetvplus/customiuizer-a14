package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.Context
import io.github.libxposed.api.XposedModuleInterface

/**
 * Resolves the Android [Context] for the current process.
 *
 * The lookup uses `android.app.ActivityThread` from libxposed, first asking for the
 * current [Application] and then falling back to the system context. The implementation
 * stays simple and delegates the detailed reflective calls to [XposedHelpers].
 */
object ContextResolver {

    @JvmStatic
    fun findContext(lpparam: XposedModuleInterface.PackageReadyParam?): Context? {
        var context: Context? = null
        try {
            val classLoader = lpparam?.classLoader
            context = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", classLoader),
                "currentApplication"
            ) as? Application
            if (context == null) {
                val currentActivityThread = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentActivityThread"
                )
                if (currentActivityThread != null) {
                    context = XposedHelpers.callMethod(currentActivityThread, "getSystemContext") as? Context
                }
            }
        } catch (ignore: Throwable) {
        }
        return context
    }
}
