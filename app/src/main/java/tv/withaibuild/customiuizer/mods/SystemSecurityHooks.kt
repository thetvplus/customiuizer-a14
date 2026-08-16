package tv.withaibuild.customiuizer.mods

import android.content.pm.ApplicationInfo
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * Signature, integrity and secure-flag hooks.
 * Relaxes platform checks that block sideloaded or modified packages, and removes
 * the FLAG_SECURE restriction on capture.
 */
object SystemSecurityHooks {

    @JvmStatic
    fun NoAccessDeviceLogsRequest(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.android.server.logcat.LogcatManagerService", lpparam.classLoader, "onLogAccessRequested", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    XposedHelpers.callMethod(thisObject, "declineRequest", chain.getArg(0))
                    skipped = true; result = null; throwable = null

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun NoVersionCheckHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.android.server.pm.PackageManagerServiceUtils", lpparam.classLoader, "checkDowngrade", HookerClassHelper.DO_NOTHING)
    }

    @JvmStatic
    fun DisableSystemIntegrityHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("android.util.apk.ApkSignatureVerifier", lpparam.classLoader, "getMinimumSignatureSchemeVersionForTargetSdk", Int::class.javaPrimitiveType!!, HookerClassHelper.returnConstant(1))
    }

    @JvmStatic
    fun NoSignatureVerifyServiceHook(lpparam: SystemServerStartingParam) {
        val SignDetails = XposedHelpers.findClassIfExists("android.content.pm.SigningDetails", lpparam.classLoader) ?: return
        val signUnknown = XposedHelpers.getStaticObjectField(SignDetails, "UNKNOWN")
        ModuleHelper.hookAllMethods(SignDetails, "checkCapability", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                val thisObject = chain.thisObject
                try {

                    if (thisObject == signUnknown || args[0] == signUnknown) {
                        return XposedHelpers.throwOrReturn(null, false)
                    }
                    val flags = args[1] as Int
                    if (flags != 4) { skipped = true; result = true; throwable = null }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllConstructors("android.util.jar.StrictJarVerifier", lpparam.classLoader, object : MethodHook() {
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

                    XposedHelpers.setObjectField(thisObject, "signatureSchemeRollbackProtectionsEnforced", false)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.hookAllMethods("android.util.jar.StrictJarVerifier", lpparam.classLoader, "verifyMessageDigest", HookerClassHelper.returnConstant(true))
        ModuleHelper.hookAllMethods("android.util.jar.StrictJarVerifier", lpparam.classLoader, "verify", HookerClassHelper.returnConstant(true))
        ModuleHelper.hookAllMethods("com.android.server.pm.PackageManagerServiceUtils", lpparam.classLoader, "verifySignatures", HookerClassHelper.returnConstant(false))
        ModuleHelper.hookAllMethods("com.android.server.pm.InstallPackageHelper", lpparam.classLoader, "doesSignatureMatchForPermissions", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                try {

                    val packageName = XposedHelpers.callMethod(args[1], "getPackageName") as String
                    val sourcePackageName = args[0] as String
                    if (sourcePackageName == packageName) {
                        skipped = true; result = true; throwable = null
                    }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.hookAllMethods("com.android.server.pm.InstallPackageHelper", lpparam.classLoader, "cannotInstallWithBadPermissionGroups", HookerClassHelper.returnConstant(false))
        ModuleHelper.hookAllMethods("com.android.server.pm.permission.PermissionManagerServiceImpl", lpparam.classLoader, "shouldGrantPermissionBySignature", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val isSystem = XposedHelpers.callMethod(chain.getArg(0), "isSystem") as Boolean
                    if (isSystem) {
                        skipped = true; result = true; throwable = null
                    }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("android.content.pm.ApplicationInfo", lpparam.classLoader, "isSignedWithPlatformKey", object : MethodHook() {
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

                    val isSystemSign = result as Boolean
                    if (!isSystemSign) {
                        val flags = XposedHelpers.getIntField(thisObject, "flags")
                        result = (flags and 1) != 0 || (flags and 128) != 0
                        throwable = null
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun RemoveSecureHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.wm.WindowState", lpparam.classLoader, "isSecureLocked", HookerClassHelper.returnConstant(false))
        ModuleHelper.findAndHookMethod("com.android.server.wm.WindowSurfaceController", lpparam.classLoader, "setSecure", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    args[0] = false

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.hookAllConstructors("com.android.server.wm.WindowSurfaceController", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    var flags = args[2] as Int
                    val secureFlag = 128
                    flags = flags and secureFlag.inv()
                    args[2] = flags

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.hookAllMethods("com.android.server.wm.WindowManagerServiceImpl", lpparam.classLoader, "notAllowCaptureDisplay", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    skipped = true; result = false; throwable = null

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun RemoveActStartConfirmHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.miui.server.SecurityManagerService\$LocalService", lpparam.classLoader, "checkAllowStartActivity", HookerClassHelper.returnConstant(true))
    }

}
