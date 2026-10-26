package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.view.View
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Installer metadata only. No holder, Context, APK or installed-package result survives a bind. */
internal class InstallerAppInfo private constructor(
    val bind: Method,
    private val apkField: Field,
    private val description: Method,
    private val size: Method,
    private val installed: Method,
    private val incoming: Method,
    private val installedName: Method,
    private val installedCode: Method,
    private val labels: (Context) -> Labels?,
) {
    internal data class Labels(val versionName: String, val versionCode: String, val sdk: String)

    fun hook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            try {
                val holder = chain.getArg(0) ?: return result
                val versionView = description.invoke(holder) as? TextView ?: return result
                val sizeView = size.invoke(holder) as? TextView ?: return result
                if (versionView === sizeView) return result
                val apk = apkField.get(chain.thisObject) ?: return result
                val packageInfo = incoming.invoke(apk) as? PackageInfo ?: return result
                val app = packageInfo.applicationInfo ?: return result
                val previous = installed.invoke(apk) as? ApplicationInfo
                val strings = labels(versionView.context) ?: return result
                val text = buildString {
                    append(strings.versionName).append(": ")
                    if (previous != null) append(installedName.invoke(apk) as? String ?: "—").append(" ➟ ")
                    append(packageInfo.versionName ?: "—").append('\n')
                    append(sizeView.text).append('\n')
                    append(strings.versionCode).append(": ")
                    if (previous != null) append(installedCode.invoke(apk)).append(" ➟ ")
                    append(packageInfo.longVersionCode).append('\n')
                    append(strings.sdk).append(": ")
                    if (previous != null) append(previous.minSdkVersion).append('-').append(previous.targetSdkVersion).append(" ➟ ")
                    append(app.minSdkVersion).append('-').append(app.targetSdkVersion)
                }
                // Keep native children, size text and parent geometry, including accessibility data.
                versionView.text = text
                versionView.setSingleLine(false)
                versionView.maxLines = 10
                sizeView.visibility = View.GONE
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log(t)
            }
            return result
        }
    }

    companion object {
        fun prepare(owner: Class<*>, holder: Class<*>, apk: Class<*>, labels: (Context) -> Labels? = { context ->
            ModuleHelper.getModuleRes(context)?.let {
                Labels(it.getString(R.string.various_installappinfo_vername), it.getString(R.string.various_installappinfo_vercode), it.getString(R.string.various_installappinfo_sdk))
            }
        }): InstallerAppInfo? {
            return try {
                // Hook the erased binder after its complete native bind, rather than choosing the
                // first void(holder) method (the current ROM also has an icon-only helper).
                val bind = owner.declaredMethods.singleOrNull {
                    it.isBridge && it.returnType == Void.TYPE && it.parameterCount == 1 &&
                        it.parameterTypes[0] != holder && it.parameterTypes[0].isAssignableFrom(holder)
                } ?: return null
                val field = owner.declaredFields.singleOrNull { apk.isAssignableFrom(it.type) } ?: return null
                fun getter(type: Class<*>, name: String, result: Class<*>): Method =
                    type.getDeclaredMethod(name).also {
                        require(result.isAssignableFrom(it.returnType))
                        it.isAccessible = true
                    }
                bind.isAccessible = true
                field.isAccessible = true
                InstallerAppInfo(bind, field,
                    getter(holder, "getTvDes", TextView::class.java), getter(holder, "getAppSize", TextView::class.java),
                    getter(apk, "getInstalledPackageInfo", ApplicationInfo::class.java), getter(apk, "getPackageInfo", PackageInfo::class.java),
                    getter(apk, "getInstalledVersionName", String::class.java), getter(apk, "getInstalledVersionCode", Int::class.javaPrimitiveType!!), labels)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log(t)
                null
            }
        }
    }
}
