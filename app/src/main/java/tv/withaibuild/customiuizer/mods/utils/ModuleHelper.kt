package tv.withaibuild.customiuizer.mods.utils

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Application
import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.MiuiMultiWindowUtils
import android.view.View
import io.github.libxposed.api.XposedModuleInterface
import kotlinx.coroutines.CoroutineExceptionHandler
import miui.app.MiuiFreeFormManager
import miui.process.ForegroundInfo
import miui.process.ProcessManager
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.utils.Helpers
import java.io.RandomAccessFile
import java.lang.reflect.Method

class ModuleHelper private constructor() {

    interface PreferenceObserver : PreferenceObserverRegistry.PreferenceObserver

    companion object {
        const val NOT_EXIST_SYMBOL = "ObjectFieldNotExist"
        const val prefsName = "customiuizer_prefs"

        @JvmField
        var currentPackageName: String? = null

        @SuppressLint("StaticFieldLeak")
        @JvmField
        var mModuleContext: Context? = null

        @SuppressLint("StaticFieldLeak")
        @JvmField
        var mCachedContext: Context? = null

        @JvmField
        var cachedModuleRes: Resources? = null

        @JvmField
        var cachedModuleConfig: Configuration? = null

        private val viewInfoTag = ResourceHooks.getFakeResId("view_info_tag")

        @JvmField
        internal var ActivityThreadClass: Class<*>? = null

        private var thermalId = -1

        private var thermalIdScanned = false


        private fun Method.descriptor(): String =
            parameterTypes.joinToString(",") { it.name ?: it.toString() }

        @JvmStatic
        fun hookMethod(method: Method, callback: MethodHook): CustomMethodUnhooker? {
            return try {
                val unhooker = XposedHelpers.doHookMethod(method, callback)
                if (unhooker != null) {
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.METHOD,
                        method.declaringClass?.name ?: "?",
                        method.name,
                        method.descriptor(),
                        HookDiagnostics.Status.INSTALLED,
                    )
                } else {
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.METHOD,
                        method.declaringClass?.name ?: "?",
                        method.name,
                        method.descriptor(),
                        HookDiagnostics.Status.INSTALL_FAILED,
                        "unhooker-null",
                    )
                }
                unhooker
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log("Failed to hook " + method.name + " method: " + toReport.javaClass.simpleName)
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.METHOD,
                    method.declaringClass?.name ?: "?",
                    method.name,
                    method.descriptor(),
                    HookDiagnostics.Status.INSTALL_FAILED,
                    toReport.javaClass.simpleName,
                )
                null
            }
        }

        @JvmStatic
        fun findAndHookMethod(className: String, classLoader: ClassLoader?, methodName: String, vararg parameterTypesAndCallback: Any?): CustomMethodUnhooker? =
            HookInstallerFacade.findAndHookMethod(className, classLoader, methodName, *parameterTypesAndCallback)

        @JvmStatic
        fun callMethodSilently(obj: Any?, methodName: String, vararg args: Any?): Any? {
            return try {
                XposedHelpers.callMethod(obj, methodName, *args)
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (e: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(e)
                XposedHelpers.log("callMethodSilently failed: $methodName: ${toReport.javaClass.simpleName}")
                NOT_EXIST_SYMBOL
            }
        }

        @JvmStatic
        fun findAndHookMethod(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any?): CustomMethodUnhooker? =
            HookInstallerFacade.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)

        @JvmStatic
        @Suppress("UNUSED_RETURN_VALUE")
        fun findAndHookMethodSilently(className: String, classLoader: ClassLoader?, methodName: String, vararg parameterTypesAndCallback: Any?): Boolean =
            HookInstallerFacade.findAndHookMethodSilently(className, classLoader, methodName, *parameterTypesAndCallback)

        @JvmStatic
        @Suppress("UNUSED_RETURN_VALUE")
        fun findAndHookMethodSilently(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any?): Boolean =
            HookInstallerFacade.findAndHookMethodSilently(clazz, methodName, *parameterTypesAndCallback)

        @JvmStatic
        fun findAndHookConstructor(className: String, classLoader: ClassLoader?, vararg parameterTypesAndCallback: Any?): CustomMethodUnhooker? {
            val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
            if (hookClass == null) {
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.CONSTRUCTOR,
                    className,
                    "<init>",
                    HookInstallerFacade.argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.TARGET_CLASS_MISSING,
                )
                XposedHelpers.log("Failed to hook constructor in " + className + " (class not found)")
                return null
            }
            return try {
                val unhooker = XposedHelpers.findAndHookConstructor(hookClass, *parameterTypesAndCallback)
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.CONSTRUCTOR,
                    className,
                    "<init>",
                    HookInstallerFacade.argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.INSTALLED,
                )
                unhooker
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log("Failed to hook constructor in " + className + ": " + toReport.javaClass.simpleName)
                val status = when {
                    HookDiagnostics.isMemberMissingException(toReport) -> HookDiagnostics.Status.TARGET_MEMBER_MISSING
                    else -> HookDiagnostics.Status.INSTALL_FAILED
                }
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.CONSTRUCTOR,
                    className,
                    "<init>",
                    HookInstallerFacade.argList(*parameterTypesAndCallback),
                    status,
                    toReport.javaClass.simpleName,
                )
                null
            }
        }

        @JvmStatic
        fun hookAllConstructors(className: String, classLoader: ClassLoader?, callback: MethodHook) {
            try {
                val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
                if (hookClass == null) {
                    XposedHelpers.log("Failed to hook " + className + " constructor (class not found)")
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                        className,
                        "<all>",
                        "",
                        HookDiagnostics.Status.TARGET_CLASS_MISSING,
                    )
                    return
                }
                val unhookers = XposedHelpers.hookAllConstructors(hookClass, callback)
                if (unhookers.isEmpty()) {
                    XposedHelpers.log("Failed to hook " + className + " constructor (no constructors found)")
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                        className,
                        "<all>",
                        "",
                        HookDiagnostics.Status.TARGET_MEMBER_MISSING,
                    )
                } else {
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                        className,
                        "<all>",
                        "",
                        HookDiagnostics.Status.INSTALLED,
                    )
                }
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log(toReport)
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                    className,
                    "<all>",
                    "",
                    HookDiagnostics.Status.INSTALL_FAILED,
                    toReport.javaClass.simpleName,
                )
            }
        }

        @JvmStatic
        fun hookAllConstructors(hookClass: Class<*>?, callback: MethodHook) {
            val className = hookClass?.canonicalName ?: "?"
            if (hookClass == null) {
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                    className,
                    "<all>",
                    "",
                    HookDiagnostics.Status.TARGET_CLASS_MISSING,
                )
                return
            }
            try {
                val unhookers = XposedHelpers.hookAllConstructors(hookClass, callback)
                if (unhookers.isEmpty()) {
                    XposedHelpers.log("Failed to hook " + className + " constructor (no constructors found)")
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                        className,
                        "<all>",
                        "",
                        HookDiagnostics.Status.TARGET_MEMBER_MISSING,
                    )
                } else {
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                        className,
                        "<all>",
                        "",
                        HookDiagnostics.Status.INSTALLED,
                    )
                }
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log(toReport)
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                    className,
                    "<all>",
                    "",
                    HookDiagnostics.Status.INSTALL_FAILED,
                    toReport.javaClass.simpleName,
                )
            }
        }

        @JvmStatic
        fun hookAllMethods(className: String, classLoader: ClassLoader?, methodName: String, callback: MethodHook) {
            try {
                val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
                if (hookClass == null) {
                    XposedHelpers.log("Failed to hook " + methodName + " method in " + className + " (class not found)")
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.TARGET_CLASS_MISSING,
                    )
                    return
                }
                val unhookers = XposedHelpers.hookAllMethods(hookClass, methodName, callback)
                if (unhookers.isEmpty()) {
                    XposedHelpers.log("Failed to hook " + methodName + " method in " + className + " (no methods found)")
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.TARGET_MEMBER_MISSING,
                    )
                } else {
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.INSTALLED,
                    )
                }
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log(toReport)
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    HookDiagnostics.Status.INSTALL_FAILED,
                    toReport.javaClass.simpleName,
                )
            }
        }

        @JvmStatic
        fun hookAllMethods(hookClass: Class<*>?, methodName: String, callback: MethodHook) {
            val className = hookClass?.canonicalName ?: "?"
            if (hookClass == null) {
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    HookDiagnostics.Status.TARGET_CLASS_MISSING,
                )
                return
            }
            try {
                val unhookers = XposedHelpers.hookAllMethods(hookClass, methodName, callback)
                if (unhookers.isEmpty()) {
                    XposedHelpers.log("Failed to hook " + methodName + " method in " + className + " (no methods found)")
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.TARGET_MEMBER_MISSING,
                    )
                } else {
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.INSTALLED,
                    )
                }
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log(toReport)
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    HookDiagnostics.Status.INSTALL_FAILED,
                    toReport.javaClass.simpleName,
                )
            }
        }

        @JvmStatic
        fun proxySystemProperties(method: String, prop: String, value: String, classLoader: ClassLoader?): Any? {
            val sysPropClass = XposedHelpers.findClassIfExists("android.os.SystemProperties", classLoader) ?: return null
            return XposedHelpers.callStaticMethod(sysPropClass, method, prop, value)
        }

        @JvmStatic
        fun proxySystemProperties(method: String, prop: String, value: Int, classLoader: ClassLoader?): Any? {
            val sysPropClass = XposedHelpers.findClassIfExists("android.os.SystemProperties", classLoader) ?: return null
            return XposedHelpers.callStaticMethod(sysPropClass, method, prop, value)
        }

        @JvmStatic
        fun hookAllMethodsSilently(className: String, classLoader: ClassLoader?, methodName: String, callback: MethodHook): Boolean {
            return try {
                val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
                if (hookClass == null) {
                    HookDiagnostics.record(
                        PreferenceObserverRegistry.processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.SILENTLY_SKIPPED,
                        "class-not-found",
                    )
                    return false
                }
                val ok = XposedHelpers.hookAllMethods(hookClass, methodName, callback).isNotEmpty()
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    if (ok) HookDiagnostics.Status.INSTALLED else HookDiagnostics.Status.SILENTLY_SKIPPED,
                    if (ok) "" else "no-methods-found",
                )
                ok
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                val status = if (HookDiagnostics.isMemberMissingException(toReport)) {
                    HookDiagnostics.Status.TARGET_MEMBER_MISSING
                } else {
                    HookDiagnostics.Status.INSTALL_FAILED
                }
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    status,
                    toReport.javaClass.simpleName,
                )
                false
            }
        }

        @JvmStatic
        fun hookAllMethodsSilently(hookClass: Class<*>?, methodName: String, callback: MethodHook): Boolean {
            val className = hookClass?.canonicalName ?: "?"
            if (hookClass == null) {
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    HookDiagnostics.Status.SILENTLY_SKIPPED,
                    "class-null",
                )
                return false
            }
            return try {
                val ok = XposedHelpers.hookAllMethods(hookClass, methodName, callback).isNotEmpty()
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    if (ok) HookDiagnostics.Status.INSTALLED else HookDiagnostics.Status.SILENTLY_SKIPPED,
                    if (ok) "" else "no-methods-found",
                )
                ok
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                val status = if (HookDiagnostics.isMemberMissingException(toReport)) {
                    HookDiagnostics.Status.TARGET_MEMBER_MISSING
                } else {
                    HookDiagnostics.Status.INSTALL_FAILED
                }
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    status,
                    toReport.javaClass.simpleName,
                )
                false
            }
        }

        @JvmStatic
        fun getStaticObjectFieldSilently(clazz: Class<*>, fieldName: String): Any? {
            return try {
                XposedHelpers.getStaticObjectField(clazz, fieldName)
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log("getStaticObjectFieldSilently failed: ${clazz.canonicalName}.$fieldName: ${toReport.javaClass.simpleName}")
                NOT_EXIST_SYMBOL
            }
        }

        @JvmStatic
        fun getObjectFieldSilently(obj: Any?, fieldName: String): Any? {
            return try {
                XposedHelpers.getObjectField(obj, fieldName)
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log("getObjectFieldSilently failed: $fieldName: ${toReport.javaClass.simpleName}")
                NOT_EXIST_SYMBOL
            }
        }

        @JvmStatic
        fun getUserId(): Int {
            return Process.myUid() / 100000
        }

        @JvmStatic
        fun findContext(): Context? {
            if (mCachedContext != null) return mCachedContext
            var context: Context? = null
            try {
                val atClass = ActivityThreadClass
                    ?: XposedHelpers.findClass("android.app.ActivityThread", null).also { ActivityThreadClass = it }
                context = XposedHelpers.callStaticMethod(atClass, "currentApplication") as? Application
                if (context == null) {
                    val currentActivityThread = XposedHelpers.callStaticMethod(atClass, "currentActivityThread")
                    if (currentActivityThread != null) {
                        context = XposedHelpers.callMethod(currentActivityThread, "getSystemContext") as? Context
                    }
                }
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
            }
            if (context != null) mCachedContext = context
            return context
        }

        @JvmStatic
        fun findContext(lpparam: XposedModuleInterface.PackageReadyParam?): Context? =
            ContextResolver.findContext(lpparam)

        @JvmStatic
        fun getNextMIUIAlarmTime(context: Context): Long {
            var nextTime = 0L
            try {
                nextTime = Settings.Global.getLong(context.contentResolver, "next_alarm_clock_long")
            } catch (e: Settings.SettingNotFoundException) {
            }
            return nextTime
        }

        @JvmStatic
        fun openAppInfo(context: Context, pkg: String, user: Int) {
            try {
                val intent = Intent("miui.intent.action.APP_MANAGER_APPLICATION_DETAIL")
                intent.setPackage("com.miui.securitycenter")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                intent.putExtra("package_name", pkg)
                if (user != 0) intent.putExtra("miui.intent.extra.USER_ID", user)
                context.startActivity(intent)
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    intent.data = Uri.parse("package:$pkg")
                    if (user != 0) {
                        val userHandle = XposedHelpers.newInstance(UserHandle::class.java, user) as UserHandle
                        XposedHelpers.callMethod(context, "startActivityAsUser", intent, userHandle)
                    } else {
                        context.startActivity(intent)
                    }
                } catch (oom: OutOfMemoryError) {
                    throw oom
                } catch (t2: Throwable) {
                    val toReport = FatalErrors.unwrapAndRethrowIfFatal(t2)
                    XposedHelpers.log("openAppInfo fallback failed: ${toReport.javaClass.simpleName}")
                }
            }
        }

        @JvmStatic
        fun observePreferenceChange(prefObserver: PreferenceObserver?) =
            PreferenceObserverRegistry.observePreferenceChange(prefObserver)

        @JvmStatic
        fun observePreferenceChange(prefObserver: PreferenceObserver?, owner: Any?) =
            PreferenceObserverRegistry.observePreferenceChange(prefObserver, owner)

        @JvmStatic
        fun unregisterPreferenceObserver(owner: Any?) =
            PreferenceObserverRegistry.unregisterPreferenceObserver(owner)

        @JvmStatic
        fun handlePreferenceChanged(key: String?) =
            PreferenceObserverRegistry.handlePreferenceChanged(key)

        /**
         * Delegates to [ReceiverRegistry].
         */
        @JvmStatic
        @JvmOverloads
        fun registerModuleReceiver(
            context: Context,
            key: String,
            receiver: BroadcastReceiver,
            filter: IntentFilter,
            flags: Int,
            permission: String? = null
        ): Boolean = ReceiverRegistry.registerModuleReceiver(context, key, receiver, filter, flags, permission)

        @JvmStatic
        @JvmOverloads
        fun unregisterModuleReceiver(key: String, expectedReceiver: BroadcastReceiver? = null) =
            ReceiverRegistry.unregisterModuleReceiver(key, expectedReceiver)

        @JvmStatic
        @JvmOverloads
        fun registerOwnedReceiver(
            context: Context,
            owner: Any,
            key: String,
            filter: IntentFilter,
            flags: Int,
            permission: String? = null,
            callback: ReceiverRegistry.OwnedReceiverCallback
        ): BroadcastReceiver = ReceiverRegistry.registerOwnedReceiver(context, owner, key, filter, flags, permission, callback)

        @JvmStatic
        @JvmOverloads
        fun unregisterOwnedReceiver(
            owner: Any,
            key: String,
            expectedReceiver: BroadcastReceiver? = null
        ) = ReceiverRegistry.unregisterOwnedReceiver(owner, key, expectedReceiver)

        @JvmStatic
        fun replaceModuleRegistration(key: String, cleanup: Runnable): Boolean =
            ReceiverRegistry.replaceModuleRegistration(key, cleanup)


        /**
         * Exception handler for every coroutine scope the module runs inside a host process.
         *
         * A `SupervisorJob` stops one child's failure from cancelling its siblings; it does not
         * stop the failure itself. Without a handler an uncaught exception in `launch` reaches
         * the thread's default handler, which in SystemUI or Launcher means the process dies.
         * Attach this to the scope rather than wrapping each `launch` body, so a coroutine added
         * later cannot forget it.
         */
        @JvmField
        val coroutineFailureHandler: CoroutineExceptionHandler =
            CoroutineExceptionHandler { _, throwable ->
                if (throwable is OutOfMemoryError) throw throwable
                XposedHelpers.log(throwable)
            }

        /**
         * Runs [block], logging instead of propagating any failure.
         *
         * Framework-invoked callbacks — `Handler.handleMessage`, `BroadcastReceiver.onReceive`,
         * `ContentObserver.onChange`, `Runnable.run` — execute outside the [MethodHook] try/catch.
         * A throw there kills system_server, SystemUI or Launcher, so every such body is wrapped.
         * The function is inline: no object is allocated and no frame is added on the hot path.
         */
        @JvmStatic
        inline fun guarded(block: () -> Unit) = CallbackGuard.guarded(block)

        /**
         * [guarded] for a callback that has to return a value, such as `OnLongClickListener`.
         *
         * [fallback] is what the framework sees when the body fails, so it must be the answer
         * that leaves the host's own behavior intact — usually "not consumed".
         */
        @JvmStatic
        inline fun <T> guarded(fallback: T, block: () -> T): T = CallbackGuard.guarded(fallback, block)

        /**
         * Broadcast identity helpers.
         *
         * Android 14 lets a sender share its package/UID with the receiver via
         * [BroadcastOptions.setShareIdentityEnabled]. Receivers that handle high-privilege
         * commands use [isTrustedBroadcast] to verify the sender package against an
         * action-specific whitelist. These functions are cold-path; one BroadcastOptions
         * instance is allocated per user-triggered broadcast.
         */

        @JvmStatic
        fun sendBroadcastWithIdentity(context: Context, intent: Intent, permission: String? = null) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            context.sendBroadcast(intent, permission, options)
        }

        @JvmStatic
        fun sendOrderedBroadcastWithIdentity(
            context: Context,
            intent: Intent,
            permission: String? = null,
            resultReceiver: BroadcastReceiver? = null,
            scheduler: android.os.Handler? = null,
            initialCode: Int = 0,
            initialData: String? = null,
            initialExtras: Bundle? = null
        ) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            context.sendOrderedBroadcast(intent, permission, options, resultReceiver, scheduler, initialCode, initialData, initialExtras)
        }

        @JvmStatic
        fun isTrustedBroadcast(
            receiver: BroadcastReceiver,
            vararg allowedPackages: String,
            allowNull: Boolean = false,
            rejectionResultCode: Int? = null
        ): Boolean {
            val fromPackage = receiver.getSentFromPackage()
            if (fromPackage == null) return allowNull
            if (fromPackage !in allowedPackages) {
                if (rejectionResultCode != null && receiver.isOrderedBroadcast) {
                    receiver.setResultCode(rejectionResultCode)
                }
                return false
            }
            return true
        }

        @JvmStatic
        @Synchronized
        @JvmOverloads
        fun getModuleContext(context: Context, config: Configuration? = null): Context {
            if (mModuleContext == null) {
                mModuleContext = context.createPackageContext(Helpers.modulePkg, Context.CONTEXT_IGNORE_SECURITY)
            }
            return if (config == null) mModuleContext!! else mModuleContext!!.createConfigurationContext(config)
        }

        @JvmStatic
        @Synchronized
        fun getModuleRes(context: Context): Resources {
            val newConfig = context.resources.configuration
            if (cachedModuleRes != null && cachedModuleConfig == newConfig) {
                return cachedModuleRes!!
            }
            val config = Configuration(newConfig)
            val moduleContext = getModuleContext(context, config)
            cachedModuleRes = moduleContext.resources
            cachedModuleConfig = config
            return cachedModuleRes!!
        }

        @JvmStatic
        fun getDepInstance(classLoader: ClassLoader?, className: String): Any? {
            return ReflectionCache.getDepInstance(classLoader, className)
        }

        @JvmStatic
        fun getViewInfo(view: View?, key: String): Any? {
            if (view == null) return null
            val info = view.getTag(viewInfoTag)
            if (info == null) return null
            @Suppress("UNCHECKED_CAST")
            val viewInfo = info as HashMap<String, Any?>
            return viewInfo[key]
        }

        @JvmStatic
        fun setViewInfo(view: View?, key: String, value: Any?) {
            if (view == null) return
            val info = view.getTag(viewInfoTag)
            val viewInfo: HashMap<String, Any?> = if (info == null) {
                val newInfo = HashMap<String, Any?>()
                view.setTag(viewInfoTag, newInfo)
                newInfo
            } else {
                @Suppress("UNCHECKED_CAST")
                info as HashMap<String, Any?>
            }
            viewInfo[key] = value
        }

        @JvmStatic
        @Throws(PendingIntent.CanceledException::class)
        fun getFreeformOptions(mContext: Context, pkgName: String, pendingIntent: PendingIntent, ignoreCheck: Boolean): Bundle? {
            if (!ignoreCheck) {
                val foregroundInfo: ForegroundInfo? = ProcessManager.getForegroundInfo()
                if (foregroundInfo != null) {
                    val topPackage = foregroundInfo.mForegroundPackageName
                    if (pkgName == topPackage) return null
                }
                val freeFormStackInfoList = MiuiFreeFormManager.getAllFreeFormStackInfosOnDisplay(
                    mContext.display?.displayId ?: 0
                )
                val freeFormCount = freeFormStackInfoList?.size ?: 0
                if (freeFormCount == 2) return null
                freeFormStackInfoList?.forEach { rootTaskInfo ->
                    if (pkgName == rootTaskInfo.packageName) return null
                }
            }
            if (!pendingIntent.isActivity) {
                val bIntent = Intent(tv.withaibuild.customiuizer.mods.GlobalActions.ACTION_PREFIX + "SetFreeFormPackage")
                bIntent.putExtra("package", pkgName)
                bIntent.setPackage("android")
                mContext.sendBroadcast(bIntent)
            }
            val options: ActivityOptions? = MiuiMultiWindowUtils.getActivityOptions(mContext, pkgName, true, false)
            if (options != null) {
                XposedHelpers.callMethod(options, "setFreeformAnimation", false)
            }
            return options?.toBundle()
        }

        @JvmStatic
        fun getFreeformIntent(pkgName: String): Intent {
            val intent = Intent()
            if (pkgName != "com.tencent.tim") {
                XposedHelpers.callMethod(intent, "addFlags", 134217728)
                XposedHelpers.callMethod(intent, "addFlags", 268435456)
                XposedHelpers.callMethod(intent, "addMiuiFlags", 256)
            }
            return intent
        }

        /**
         * Resolves the first CPU thermal zone once per process.
         *
         * The zone list never changes at runtime, so the sysfs scan is memoized even when nothing
         * matches; otherwise the SystemUI monitor tick would reopen 19 sysfs files every 2 s.
         * Called from the NetworkSpeedController background handler thread only.
         *
         * The [thermalIdScanned] and [thermalId] fields are only written after a complete,
         * non-fatal scan. Fatal JVM errors are rethrown before any memoized state is committed
         * so the next call does not return a stale `-1`.
         */
        @JvmStatic
        fun getCPUThermalId(): Int {
            if (thermalIdScanned) return thermalId
            val found = scanForCpuThermalId { readThermalType(it) }
            thermalId = found
            thermalIdScanned = true
            return found
        }

        /**
         * Scans thermal zones for a CPU sensor. [readType] is injected so unit tests can simulate
         * I/O results and fatal errors without touching the filesystem.
         */
        internal fun scanForCpuThermalId(readType: (Int) -> String?): Int {
            var found = -1
            for (i in 2 until 40 step 2) {
                val sensorType = try {
                    readType(i)
                } catch (oom: OutOfMemoryError) {
                    throw oom
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    null
                }
                if (sensorType != null && (sensorType.startsWith("cpu-") || sensorType.startsWith("cpu_big"))) {
                    found = i
                    break
                }
            }
            return found
        }

        private fun readThermalType(index: Int): String? =
            RandomAccessFile("/sys/devices/virtual/thermal/thermal_zone$index/type", "r").use { it.readLine() }

        @JvmStatic
        fun replacePkgAndFrameworkValue(pkg: String, type: String, name: String, resValue: Any?) {
            if (pkg != "android") {
                MainModule.resHooks.setThemeValueReplacement("android", type, name, resValue)
            }
            MainModule.resHooks.setThemeValueReplacement(pkg, type, name, resValue)
        }

        @JvmStatic
        fun getObjectFieldByPath(target: Any?, path: String): Any? {
            if (target == null) return null
            var obj: Any? = target
            for (field in path.split('.')) {
                obj = getObjectFieldSilently(obj, field)
                if (obj == NOT_EXIST_SYMBOL) {
                    return NOT_EXIST_SYMBOL
                }
            }
            return obj
        }
    }
}
