package tv.withaibuild.customiuizer.mods.island

import android.content.ComponentName
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import java.lang.reflect.Modifier
import java.lang.reflect.Method

/** Cold metadata only; observes the ROM's existing task observer without retaining its owner. */
internal class IslandForegroundAccess(val observerClass: Class<*>, private val ownerGetter: Method? = null) {
    constructor(loader: ClassLoader) : this(XposedHelpers.findClass(
        "com.miui.systemui.functions.MiuiTopActivityObserver", loader),
        optionalOwnerGetter(loader))

    companion object {
        private fun optionalOwnerGetter(loader: ClassLoader): Method? = try {
            XposedHelpers.findClass("com.miui.systemui.MiuiDependency", loader)
                .getDeclaredMethod("get", Class::class.java).apply {
                    require(Modifier.isStatic(modifiers) && !returnType.isPrimitive)
                    isAccessible = true
                }
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            // Initial catch-up is optional; native constructor/update callbacks
            // remain useful when this dependency accessor differs across ROMs.
            null
        }
    }

    private val topActivity = observerClass.getDeclaredField("mTopActivity").apply {
        require(type == ComponentName::class.java && !Modifier.isStatic(modifiers))
        isAccessible = true
    }
    // Task moves write mTopActivity directly and then notify; stack changes call update first.
    val updates = arrayOf("updateTopActivity", "notifyListeners").map { name ->
        observerClass.getDeclaredMethod(name).apply {
            require(returnType == Void.TYPE && !Modifier.isStatic(modifiers))
            isAccessible = true
        }
    }

    fun packageName(owner: Any?): String? = if (owner == null) null else
        (topActivity.get(owner) as? ComponentName)?.packageName?.takeUnless { it.isBlank() }

    /** One catch-up at status-bar bind; subsequent changes use the existing native hooks. */
    fun currentPackage(fallback: String? = null): String? {
        val owner = ownerGetter?.invoke(null, observerClass) ?: return fallback
        return packageName(owner)
    }
}
