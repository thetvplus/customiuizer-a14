package tv.withaibuild.customiuizer.mods

import android.view.View
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** One SystemUI NavigationBarView contract, resolved before installing its single hook.
 * Retains metadata from this ClassLoader only, never a navigation view or button. */
internal class ImeDismissButtonAccess private constructor(
    val update: Method, private val ownerType: Class<*>, private val buttonType: Class<*>,
    private val hints: Field, private val mode: Field, private val back: Method,
    private val visibility: Method, private val setVisibility: Method,
) {
    fun dispatch(chain: XposedInterface.Chain): Any? {
        // A failed native update owns its exception and partial state. Do not inspect or
        // alter that state; there is no second native call or module restoration pass.
        val result = chain.proceed()
        try {
            val owner = chain.thisObject
            if (ownerType.isInstance(owner) && Controls.shouldHideImeDismissButton(hints.getInt(owner), mode.getInt(owner))) {
                val button = back.invoke(owner)
                if (buttonType.isInstance(button) && visibility.invoke(button) == View.VISIBLE)
                    setVisibility.invoke(button, View.INVISIBLE)
            }
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log(error)
        }
        return result
    }

    companion object {
        fun resolve(loader: ClassLoader): ImeDismissButtonAccess? {
            val owner = XposedHelpers.findClassIfExists("com.android.systemui.navigationbar.NavigationBarView", loader) ?: return null
            val button = XposedHelpers.findClassIfExists("com.android.systemui.navigationbar.buttons.ButtonDispatcher", loader) ?: return null
            return resolve(owner, button)
        }

        internal fun resolve(owner: Class<*>, button: Class<*>): ImeDismissButtonAccess? {
            val hints = XposedHelpers.findFieldIfExists(owner, "mNavigationIconHints") ?: return null
            val mode = XposedHelpers.findFieldIfExists(owner, "mNavBarMode") ?: return null
            val update = method(owner, "updateNavButtonIcons") ?: return null
            val back = method(owner, "getBackButton") ?: return null
            val visibility = method(button, "getVisibility") ?: return null
            val setVisibility = method(button, "setVisibility", Int::class.javaPrimitiveType!!) ?: return null
            if (hints.type != Int::class.javaPrimitiveType || mode.type != Int::class.javaPrimitiveType ||
                Modifier.isStatic(hints.modifiers) || Modifier.isStatic(mode.modifiers) ||
                update.returnType != Void.TYPE || !button.isAssignableFrom(back.returnType) ||
                visibility.returnType != Int::class.javaPrimitiveType || setVisibility.returnType != Void.TYPE ||
                listOf(update, back, visibility, setVisibility).any { Modifier.isStatic(it.modifiers) || Modifier.isAbstract(it.modifiers) }) return null
            hints.isAccessible = true; mode.isAccessible = true
            for (method in listOf(update, back, visibility, setVisibility)) method.isAccessible = true
            return ImeDismissButtonAccess(update, owner, button, hints, mode, back, visibility, setVisibility)
        }

        /** A ROM may move the same instance contract to a base class. Search only that
         * owner's hierarchy, once; never fall back to global View visibility hooks. */
        private fun method(owner: Class<*>, name: String, vararg parameters: Class<*>): Method? {
            var type: Class<*>? = owner
            var depth = 0
            while (type != null && type != Any::class.java && depth++ < 8) {
                try { return type.getDeclaredMethod(name, *parameters) }
                catch (_: NoSuchMethodException) { type = type.superclass }
            }
            return null
        }
    }
}
