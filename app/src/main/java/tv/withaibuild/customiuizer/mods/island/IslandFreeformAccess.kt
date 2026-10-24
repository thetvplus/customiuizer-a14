package tv.withaibuild.customiuizer.mods.island

import android.os.UserHandle
import android.view.Display
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Modifier

internal fun interface IslandFreeformPort {
    /** Worker-only, once on attachment and after native mode events. Null means unavailable. */
    fun hasWindow(user: UserHandle): Boolean?
}

/** Reads the native current stack inventory instead of guessing from undocumented event numbers. */
internal class IslandFreeformAccess(manager: Class<*>, callback: Class<*>, stack: Class<*>) : IslandFreeformPort {
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("miui.app.MiuiFreeFormManager", loader),
        XposedHelpers.findClass("com.miui.systemui.functions.MiuiTopActivityObserver\$2", loader),
        XposedHelpers.findClass("miui.app.MiuiFreeFormManager\$MiuiFreeFormStackInfo", loader))

    private val read = manager.getDeclaredMethod("getAllFreeFormStackInfosOnDisplay", Int::class.javaPrimitiveType).apply {
        require(Modifier.isStatic(modifiers) && List::class.java.isAssignableFrom(returnType)); isAccessible = true
    }
    val changed = callback.getDeclaredMethod("dispatchFreeFormStackModeChanged", Int::class.javaPrimitiveType, stack).apply {
        require(!Modifier.isStatic(modifiers) && returnType == Void.TYPE); isAccessible = true
    }
    private val display = stack.getDeclaredField("displayId").apply {
        require(type == Int::class.javaPrimitiveType && !Modifier.isStatic(modifiers)); isAccessible = true
    }
    private val userId = stack.getDeclaredField("userId").apply {
        require(type == Int::class.javaPrimitiveType && !Modifier.isStatic(modifiers)); isAccessible = true
    }

    override fun hasWindow(user: UserHandle): Boolean? {
        val stacks = read.invoke(null, Display.DEFAULT_DISPLAY) as? List<*> ?: return null
        return stacks.any { entry ->
            entry != null && display.getInt(entry) == Display.DEFAULT_DISPLAY && userId.getInt(entry).let { id ->
                id >= 0 && UserHandle.getUserHandleForUid(Math.multiplyExact(id, 100000)) == user
            }
        }
    }
}
