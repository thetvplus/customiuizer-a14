package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.os.PowerManager
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Keep MIUI's alert dispatch and light bit; mute only sound/vibration for this native job. */
internal object NotificationAlertHook {
    internal fun alertFlags(flags: Int, interactive: Boolean): Int = if (interactive) flags and 3.inv() else flags

    private data class Alert(val manager: Any, val key: String)

    fun install(loader: ClassLoader) {
        val callback = XposedHelpers.findClass("com.android.systemui.statusbar.notification.policy.MiuiAlertManager\$\$ExternalSyntheticLambda0", loader)
        val owner = XposedHelpers.findField(callback, "f\$0")
        val entry = XposedHelpers.findField(callback, "f\$1")
        val key = XposedHelpers.findField(entry.type, "mKey")
        val manager = XposedHelpers.findField(owner.type, "mNm")
        val context = XposedHelpers.findField(owner.type, "mContext")
        val proxy = XposedHelpers.findClass("android.app.INotificationManager\$Stub\$Proxy", loader)
        val scope = NotificationLaunchScope<Alert>()
        val state = object { @Volatile var enabled = MainModule.mPrefs.getBoolean("system_mutevisiblenotif") }
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key == "system_mutevisiblenotif") state.enabled = MainModule.mPrefs.getBoolean("system_mutevisiblenotif")
            }
        })
        val send = XposedHelpers.findMethodExact(proxy, "buzzBeepBlinkForNotification", String::class.java, Int::class.javaPrimitiveType!!)
        val run = XposedHelpers.findMethodExact(callback, "run", *emptyArray<Any>())
        ModuleHelper.hookMethod(send, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                scope.consumeIf { it.manager === chain.thisObject && it.key == chain.getArg(0) } ?: return chain.proceed()
                val args = XposedHelpers.getArgsArray(chain)
                args[1] = alertFlags(args[1] as Int, true)
                return chain.proceed(args)
            }
        })
        ModuleHelper.hookMethod(run, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val alert = ModuleHelper.guarded<Alert?>(null) {
                    if (!state.enabled) return@guarded null
                    val nativeOwner = owner.get(chain.thisObject)
                    val power = (context.get(nativeOwner) as Context).getSystemService(PowerManager::class.java)
                    if (power?.isInteractive != true) null
                    else Alert(requireNotNull(manager.get(nativeOwner)), key.get(entry.get(chain.thisObject)) as String)
                }
                return scope.within(alert) { chain.proceed() }
            }
        })
    }
}
