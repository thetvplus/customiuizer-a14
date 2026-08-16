package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.BadParcelableException
import android.util.Pair
import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.Helpers.MimeType
import java.util.List
import tv.withaibuild.customiuizer.utils.HookUtils

/**
 * Share sheet and open-with chooser hooks.
 * Filters entries out of the system share sheet and the open-with dialog, in both
 * the activity and the resolver-service variants.
 */
object SystemShareMenuHooks {

    @JvmStatic
    fun CleanShareMenuHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("miui.securityspace.XSpaceResolverActivityHelper.ResolverActivityRunner", lpparam.classLoader, "run", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mOriginalIntent = XposedHelpers.getObjectField(thisObject, "mOriginalIntent") as Intent?
                    if (mOriginalIntent == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val action = mOriginalIntent.action
                    if (action == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SENDTO && action != Intent.ACTION_SEND_MULTIPLE) { return XposedHelpers.throwOrReturn(throwable, result) }
                    if (mOriginalIntent.dataString != null && mOriginalIntent.dataString!!.contains(":")) { return XposedHelpers.throwOrReturn(throwable, result) }

                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val mAimPackageName = XposedHelpers.getObjectField(thisObject, "mAimPackageName") as String?
                    if (mAimPackageName == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val selectedApps = MainModule.mPrefs.getStringSet("system_cleanshare_apps") ?: emptySet<String>()
                    val mRootView = XposedHelpers.getObjectField(thisObject, "mRootView") as View
                    val appResId1 = HookUtils.getResId(mContext.resources, "app1", "id", "android.miui")
                    val appResId2 = HookUtils.getResId(mContext.resources, "app2", "id", "android.miui")
                    val removeOriginal = selectedApps.contains(mAimPackageName) || selectedApps.contains(mAimPackageName + "|0")
                    val removeDual = selectedApps.contains(mAimPackageName + "|999")
                    val originalApp = mRootView.findViewById<View>(appResId1)
                    val dualApp = mRootView.findViewById<View>(appResId2)
                    if (removeOriginal) dualApp?.performClick()
                    else if (removeDual) originalApp?.performClick()

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun CleanShareMenuServiceHook(lpparam: SystemServerStartingParam) {
        val hook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject
                    val args = chain.args

                    try {
                        if (args[0] == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                        if (args.size < 6) { return XposedHelpers.throwOrReturn(throwable, result) }
                        val origIntent = args[0] as Intent
                        val intent = origIntent.clone() as Intent
                        val action = intent.action
                        if (action == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SENDTO && action != Intent.ACTION_SEND_MULTIPLE) { return XposedHelpers.throwOrReturn(throwable, result) }
                        if (intent.dataString != null && intent.dataString!!.contains(":")) { return XposedHelpers.throwOrReturn(throwable, result) }
                        if (intent.hasExtra("CustoMIUIzer") && intent.getBooleanExtra("CustoMIUIzer", false)) { return XposedHelpers.throwOrReturn(throwable, result) }
                        val selectedApps = MainModule.mPrefs.getStringSet("system_cleanshare_apps") ?: emptySet<String>()
                        val resolved = result as? List<ResolveInfo> ?: return XposedHelpers.throwOrReturn(throwable, result)
                        val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                        val pm = mContext.packageManager
                        val itr = resolved.iterator()
                        while (itr.hasNext()) {
                            val resolveInfo = itr.next()
                            val removeOriginal = selectedApps.contains(resolveInfo.activityInfo.packageName) || selectedApps.contains(resolveInfo.activityInfo.packageName + "|0")
                            val removeDual = selectedApps.contains(resolveInfo.activityInfo.packageName + "|999")
                            var hasDual = false
                            try {
                                hasDual = XposedHelpers.callMethod(pm, "getPackageInfoAsUser", resolveInfo.activityInfo.packageName, 0, 999) != null
                            } catch (ignore: Throwable) {}
                            if ((removeOriginal && !hasDual) || (removeOriginal && hasDual && removeDual)) itr.remove()
                        }
                        result = resolved; throwable = null
                    } catch (t: Throwable) {
                        if (t !is BadParcelableException) XposedHelpers.log(t)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }

        val ActQueryService = "com.android.server.pm.ComputerEngine"
        ModuleHelper.hookAllMethods(ActQueryService, lpparam.classLoader, "queryIntentActivitiesInternal", hook)
    }

    private fun hideMimeType(mimeFlags: Int, mimeType: String?): Boolean {
        var dataType = MimeType.OTHERS
        if (mimeType != null) {
            if (mimeType.startsWith("image/")) dataType = MimeType.IMAGE
            else if (mimeType.startsWith("audio/")) dataType = MimeType.AUDIO
            else if (mimeType.startsWith("video/")) dataType = MimeType.VIDEO
            else if (mimeType.startsWith("text/") ||
                mimeType.startsWith("application/pdf") ||
                mimeType.startsWith("application/msword") ||
                mimeType.startsWith("application/vnd.ms-") ||
                mimeType.startsWith("application/vnd.openxmlformats-")) dataType = MimeType.DOCUMENT
            else if (mimeType.startsWith("application/vnd.android.package-archive") ||
                mimeType.startsWith("application/zip") ||
                mimeType.startsWith("application/x-zip") ||
                mimeType.startsWith("application/octet-stream") ||
                mimeType.startsWith("application/rar") ||
                mimeType.startsWith("application/x-rar") ||
                mimeType.startsWith("application/x-tar") ||
                mimeType.startsWith("application/x-bzip") ||
                mimeType.startsWith("application/gzip") ||
                mimeType.startsWith("application/x-lz") ||
                mimeType.startsWith("application/x-compress") ||
                mimeType.startsWith("application/x-7z") ||
                mimeType.startsWith("application/java-archive")) dataType = MimeType.ARCHIVE
            else if (mimeType.startsWith("link/")) dataType = MimeType.LINK
        }
        return (mimeFlags and dataType) == dataType
    }

    private fun getContentType(context: Context, intent: Intent): String? {
        val scheme = intent.scheme
        val linkSchemes = scheme == "http" || scheme == "https" || scheme == "vnd.youtube"
        var mimeType = intent.type
        if (mimeType == null && linkSchemes) mimeType = "link/*"
        if (mimeType == null && intent.data != null) try {
            mimeType = context.contentResolver.getType(intent.data!!)
        } catch (ignore: Throwable) {}
        return mimeType
    }

    private fun isRemoveApp(isDynamic: Boolean, context: Context, pkgName: String, selectedApps: Set<String>, mimeType: String?): Pair<Boolean, Boolean> {
        val key = "system_cleanopenwith_apps"
        val mimeFlags0: Int
        val mimeFlags999: Int
        if (isDynamic) {
            mimeFlags0 = MainModule.mPrefs.getInt("${key}_${pkgName}|0", MimeType.ALL)
            mimeFlags999 = MainModule.mPrefs.getInt("${key}_${pkgName}|999", MimeType.ALL)
        } else {
            mimeFlags0 = MainModule.mPrefs.getInt("${key}_${pkgName}|0", MimeType.ALL)
            mimeFlags999 = MainModule.mPrefs.getInt("${key}_${pkgName}|999", MimeType.ALL)
        }
        val removeOriginal = (selectedApps.contains(pkgName) || selectedApps.contains(pkgName + "|0")) && hideMimeType(mimeFlags0, mimeType)
        val removeDual = selectedApps.contains(pkgName + "|999") && hideMimeType(mimeFlags999, mimeType)
        return Pair(removeOriginal, removeDual)
    }

    @JvmStatic
    fun CleanOpenWithMenuHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("miui.securityspace.XSpaceResolverActivityHelper.ResolverActivityRunner", lpparam.classLoader, "run", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mOriginalIntent = XposedHelpers.getObjectField(thisObject, "mOriginalIntent") as Intent?
                    if (mOriginalIntent == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val action = mOriginalIntent.action
                    if (action != Intent.ACTION_VIEW) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val mAimPackageName = XposedHelpers.getObjectField(thisObject, "mAimPackageName") as String?
                    if (mAimPackageName == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val selectedApps = MainModule.mPrefs.getStringSet("system_cleanopenwith_apps") ?: emptySet<String>()
                    val mimeType = getContentType(mContext, mOriginalIntent)
                    val isRemove = isRemoveApp(true, mContext, mAimPackageName, selectedApps, mimeType)

                    val mRootView = XposedHelpers.getObjectField(thisObject, "mRootView") as View
                    val appResId1 = HookUtils.getResId(mContext.resources, "app1", "id", "android.miui")
                    val appResId2 = HookUtils.getResId(mContext.resources, "app2", "id", "android.miui")
                    val originalApp = mRootView.findViewById<View>(appResId1)
                    val dualApp = mRootView.findViewById<View>(appResId2)
                    if (isRemove.first) dualApp?.performClick()
                    else if (isRemove.second) originalApp?.performClick()

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun CleanOpenWithMenuServiceHook(lpparam: SystemServerStartingParam) {
        val hook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject
                    val args = chain.args

                    try {
                        if (args[0] == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                        if (args.size < 6) { return XposedHelpers.throwOrReturn(throwable, result) }
                        val origIntent = args[0] as Intent
                        val intent = origIntent.clone() as Intent
                        val action = intent.action
                        if (action != Intent.ACTION_VIEW) { return XposedHelpers.throwOrReturn(throwable, result) }
                        if (intent.hasExtra("CustoMIUIzer") && intent.getBooleanExtra("CustoMIUIzer", false)) { return XposedHelpers.throwOrReturn(throwable, result) }
                        val scheme = intent.scheme
                        val validSchemes = scheme == "http" || scheme == "https" || scheme == "vnd.youtube"
                        if (intent.type == null && !validSchemes) { return XposedHelpers.throwOrReturn(throwable, result) }

                        val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                        val mimeType = getContentType(mContext, intent)

                        val key = "system_cleanopenwith_apps"
                        val selectedApps = MainModule.mPrefs.getStringSet(key) ?: emptySet<String>()
                        val resolved = result as? List<ResolveInfo> ?: return XposedHelpers.throwOrReturn(throwable, result)
                        val pm = mContext.packageManager
                        val itr = resolved.iterator()
                        while (itr.hasNext()) {
                            val resolveInfo = itr.next()
                            val isRemove = isRemoveApp(false, mContext, resolveInfo.activityInfo.packageName, selectedApps, mimeType)
                            var hasDual = false
                            try {
                                hasDual = XposedHelpers.callMethod(pm, "getPackageInfoAsUser", resolveInfo.activityInfo.packageName, 0, 999) != null
                            } catch (ignore: Throwable) {}
                            if ((isRemove.first && !hasDual) || (isRemove.first && hasDual && isRemove.second)) itr.remove()
                        }

                        result = resolved; throwable = null
                    } catch (t: Throwable) {
                        if (t !is BadParcelableException) XposedHelpers.log(t)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }

        val ActQueryService = "com.android.server.pm.ComputerEngine"
        ModuleHelper.hookAllMethods(ActQueryService, lpparam.classLoader, "queryIntentActivitiesInternal", hook)
    }

}
