package tv.withaibuild.customiuizer.mods

import android.content.ComponentName
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Modifier

/** Cold metadata only; observes the ROM's existing task observer without retaining its owner. */
internal class IslandForegroundAccess(val observerClass: Class<*>) {
    constructor(loader: ClassLoader) : this(XposedHelpers.findClass(
        "com.miui.systemui.functions.MiuiTopActivityObserver", loader))

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
}
