package tv.withaibuild.customiuizer.mods.utils

import android.annotation.SuppressLint
import io.github.libxposed.api.XposedInterface

/**
 * Isolated bridge for libxposed API 102 hook installation features.
 *
 * This is the **only** file in the module that may reference
 * [XposedInterface.HookBuilder.setId].  Keeping the symbol here means the rest of
 * the code base does not import it and the API 101 path can remain untouched.
 *
 * The bridge does not hold [HookHandle], [Context], or any long-lived state.  It
 * has no Maps, no Handlers, no threads and no Receivers.
 */
internal object Api102HookBridge {

    /**
     * Assigns a stable [id] to [builder] when the host supports it.
     *
     * The caller must ensure [XposedApiCapabilities.supportsStableHookId] is true.
     * The returned [HookBuilder] is the same builder, so the call can be chained.
     */
    @JvmStatic
    @SuppressLint("XposedNewApi")
    fun setStableHookId(builder: XposedInterface.HookBuilder, id: String): XposedInterface.HookBuilder {
        if (!XposedApiCapabilities.supportsStableHookId()) return builder
        return builder.setId(id)
    }

    /** Short, fixed stable hook IDs for infrastructure hooks.  Never dynamically joined. */
    @JvmStatic
    val STABLE_ID_RES_TEXT = "res.text"
    @JvmStatic
    val STABLE_ID_RES_STRING = "res.string"
    @JvmStatic
    val STABLE_ID_RES_LAYOUT = "res.layout"
    @JvmStatic
    val STABLE_ID_RES_DRAWABLE_DENSITY = "res.drawable_density"
    @JvmStatic
    val STABLE_ID_RES_THEME_MERGE = "res.theme_merge"
    @JvmStatic
    val STABLE_ID_SYSTEMUI_INIT = "systemui.init"
    @JvmStatic
    val STABLE_ID_LAUNCHER_INIT = "launcher.init"
}
