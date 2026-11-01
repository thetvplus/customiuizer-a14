package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.widget.Toast
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference

/** Extra native preferences belong to one current information fragment and its exact package/user. */
internal object AppInformationHooks {
    private const val OWNER = "customiuizer_app_information_owner"
    private const val SCREEN = "customiuizer_app_information_screen"
    internal data class Target(val packageName: String, val uid: Int)
    private data class Binding(val fragment: WeakReference<Any>, val screen: WeakReference<Any>, val target: Target)
    private val keys = setOf("apk_versioncode", "apk_filename", "data_path", "app_uid", "target_sdk", "open_in_store", "launch_app")

    internal fun target(info: PackageInfo?, arguments: Bundle?): Target? {
        val app = info?.applicationInfo ?: return null
        // Parcelled/incomplete PackageInfo can lack a name despite the SDK annotation.
        val pkg = info.packageName ?: return null
        val uid = arguments?.getInt("am_app_uid", -1) ?: return null
        if (uid < 0 || uid != app.uid || pkg != app.packageName || pkg != arguments.getString("am_app_pkgname")) return null
        return Target(pkg, uid)
    }

    internal class Access(val fragmentType: Class<*>, private val activityType: Class<*>) {
        val create = fragmentType.getDeclaredMethod("onCreatePreferences", Bundle::class.java, String::class.java)
        val click = fragmentType.declaredMethods.single { it.name == "onPreferenceTreeClick" && it.parameterCount == 1 }
        private val info = XposedHelpers.findFirstFieldByExactType(fragmentType, PackageInfo::class.java).apply { isAccessible = true }
        private val add = XposedHelpers.findMethodsByExactParameters(fragmentType, Void.TYPE,
            String::class.java, String::class.java, String::class.java).single().apply { isAccessible = true }
        private val getActivity = fragmentType.getMethod("getActivity")
        private val getArguments = fragmentType.getMethod("getArguments")
        private val getScreen = fragmentType.getMethod("getPreferenceScreen")
        private val find = fragmentType.getMethod("findPreference", CharSequence::class.java)
        private val getKey = click.parameterTypes[0].getMethod("getKey")
        private val getTitle = click.parameterTypes[0].getMethod("getTitle")

        private fun activity(fragment: Any): Activity? = (getActivity.invoke(fragment) as? Activity)?.takeIf {
            activityType.isInstance(it) && !it.isFinishing && !it.isDestroyed
        }
        private fun info(fragment: Any) = info.get(fragment) as? PackageInfo
        private fun target(fragment: Any) = target(info(fragment), getArguments.invoke(fragment) as? Bundle)

        fun decorate(fragment: Any) {
            val act = activity(fragment) ?: return
            val info = info(fragment) ?: return
            val target = target(fragment) ?: return
            val screen = getScreen.invoke(fragment) ?: return
            if ((XposedHelpers.getAdditionalInstanceField(fragment, SCREEN) as? WeakReference<*>)?.get() === screen) return
            val res = ModuleHelper.getModuleRes(act)
            val app = info.applicationInfo ?: return
            val rows = listOf(
                Triple("apk_versioncode", R.string.appdetails_apk_version_code, info.longVersionCode.toString()),
                Triple("apk_filename", R.string.appdetails_apk_file, app.sourceDir.orEmpty()),
                Triple("data_path", R.string.appdetails_data_path, app.dataDir.orEmpty()),
                Triple("app_uid", R.string.appdetails_app_uid, app.uid.toString()),
                Triple("target_sdk", R.string.appdetails_sdk, app.targetSdkVersion.toString()),
                Triple("open_in_store", R.string.appdetails_playstore, ""),
                Triple("launch_app", R.string.appdetails_launch, ""))
            for ((key, title, value) in rows) {
                if (find.invoke(fragment, key) == null) add.invoke(fragment, key, res.getString(title), value)
                val preference = find.invoke(fragment, key) ?: continue
                XposedHelpers.setAdditionalInstanceField(preference, OWNER, Binding(WeakReference(fragment), WeakReference(screen), target))
            }
            XposedHelpers.setAdditionalInstanceField(fragment, SCREEN, WeakReference(screen))
        }

        /** Returns false for native preferences, detached pages, old rows and a changed package/user. */
        fun click(fragment: Any, preference: Any): Boolean {
            val binding = XposedHelpers.getAdditionalInstanceField(preference, OWNER) as? Binding ?: return false
            if (binding.fragment.get() !== fragment || binding.screen.get() !== getScreen.invoke(fragment) ||
                target(fragment) != binding.target) return false
            val key = getKey.invoke(preference) as? String ?: return false
            if (key !in keys || find.invoke(fragment, key) !== preference) return false
            val act = activity(fragment) ?: return false
            val info = info(fragment) ?: return false
            val app = info.applicationInfo ?: return false
            when (key) {
                "apk_filename", "data_path" -> {
                    val title = getTitle.invoke(preference)?.toString().orEmpty()
                    act.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newPlainText(title, if (key == "apk_filename") app.sourceDir else app.dataDir))
                    val message = act.resources.getIdentifier("app_manager_copy_pkg_to_clip", "string", act.packageName)
                    if (message != 0) Toast.makeText(act, message, Toast.LENGTH_SHORT).show()
                }
                "open_in_store" -> {
                    try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${info.packageName}"))) }
                    catch (_: android.content.ActivityNotFoundException) {
                        act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${info.packageName}")))
                    }
                }
                "launch_app" -> {
                    val intent = act.packageManager.getLaunchIntentForPackage(info.packageName)
                    if (intent == null) Toast.makeText(act, ModuleHelper.getModuleRes(act).getString(R.string.appdetails_nolaunch), Toast.LENGTH_SHORT).show()
                    else {
                        val user = Various.resolveAppInfoLaunchUserId(binding.target.uid) { it / 100000 } ?: return true
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        if (user == 0) act.startActivity(intent)
                        else XposedHelpers.callMethod(act, "startActivityAsUser", intent, XposedHelpers.newInstance(UserHandle::class.java, user))
                    }
                }
                else -> return false
            }
            return true
        }
    }

    fun install(loader: ClassLoader) {
        val fragment = XposedHelpers.findClass("com.miui.appmanager.fragment.AMAppInformationFragment", loader)
        val access = Access(fragment, XposedHelpers.findClass("com.miui.appmanager.AMAppInfomationActivity", loader))
        ModuleHelper.hookMethod(access.create, object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                if (callback.getThrowable() == null) callback.getThisObject()?.let(access::decorate)
            }
        })
        ModuleHelper.hookMethod(access.click, object : MethodHook() {
            override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                val fragment = chain.thisObject ?: return chain.proceed()
                val preference = chain.getArg(0) ?: return chain.proceed()
                val owned = ModuleHelper.guarded(false) { access.click(fragment, preference) }
                return if (owned) true else chain.proceed()
            }
        })
    }
}
