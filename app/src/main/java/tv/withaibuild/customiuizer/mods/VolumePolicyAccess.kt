package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.os.Handler
import android.os.PowerManager
import android.view.KeyEvent
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Preflight the policy's complete instance contract before installing a system_server hook. */
internal class VolumePolicyAccess private constructor(val owner: Class<*>, val intercept: Method,
    val context: Field, val power: Field, val handler: Field, val wakeLock: Field) {
    companion object {
        fun resolve(owner: Class<*>): VolumePolicyAccess? {
            val method = XposedHelpers.findMethodExactIfExists(owner, "interceptKeyBeforeQueueing",
                KeyEvent::class.java, Int::class.javaPrimitiveType) ?: return null
            if (method.returnType != Int::class.javaPrimitiveType || Modifier.isStatic(method.modifiers) || Modifier.isAbstract(method.modifiers)) return null
            fun field(name: String, type: Class<*>): Field? {
                val value = XposedHelpers.findFieldIfExists(owner, name) ?: return null
                if (!type.isAssignableFrom(value.type) || Modifier.isStatic(value.modifiers)) return null
                return value.apply { isAccessible = true }
            }
            return VolumePolicyAccess(owner, method,
                field("mContext", Context::class.java) ?: return null,
                field("mPowerManager", PowerManager::class.java) ?: return null,
                field("mHandler", Handler::class.java) ?: return null,
                field("mBroadcastWakeLock", PowerManager.WakeLock::class.java) ?: return null)
        }
    }
}
