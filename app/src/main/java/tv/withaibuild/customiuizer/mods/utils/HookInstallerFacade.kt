package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker

/**
 * Diagnostics-aware hook installation helpers.
 *
 * This object wraps the low-level [XposedHelpers] installation calls and records
 * every attempt via [HookDiagnostics].  Callers continue to use
 * [ModuleHelper.findAndHookMethod] so Java/Kotlin callers and the invariant
 * checks do not need to change.
 */
object HookInstallerFacade {

    internal fun argList(vararg args: Any?): String {
        if (args.isEmpty()) return ""
        val lastIndex = args.size - 1
        val sb = StringBuilder()
        for (i in 0 until lastIndex) {
            if (i > 0) sb.append(',')
            sb.append(args[i]?.toString() ?: "")
        }
        return sb.toString()
    }

    @JvmStatic
    fun findAndHookMethod(className: String, classLoader: ClassLoader?, methodName: String, vararg parameterTypesAndCallback: Any?): CustomMethodUnhooker? {
        val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
        if (hookClass == null) {
            HookDiagnostics.record(
                PreferenceObserverRegistry.processName(),
                HookDiagnostics.Kind.METHOD,
                className,
                methodName,
                argList(*parameterTypesAndCallback),
                HookDiagnostics.Status.TARGET_CLASS_MISSING,
            )
            XposedHelpers.log("Failed to hook " + methodName + " method in " + className + " (class not found)")
            return null
        }
        return try {
            val unhooker = XposedHelpers.findAndHookMethod(hookClass, methodName, *parameterTypesAndCallback)
            if (unhooker != null) {
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.INSTALLED,
                )
            } else {
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.INSTALL_FAILED,
                    "unhooker-null",
                )
                XposedHelpers.log("Failed to hook " + methodName + " method in " + className + " (unhooker is null)")
            }
            unhooker
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("Failed to hook " + methodName + " method in " + className)
            val status = when {
                HookDiagnostics.isMemberMissingException(t) -> HookDiagnostics.Status.TARGET_MEMBER_MISSING
                else -> HookDiagnostics.Status.INSTALL_FAILED
            }
            HookDiagnostics.record(
                PreferenceObserverRegistry.processName(),
                HookDiagnostics.Kind.METHOD,
                className,
                methodName,
                argList(*parameterTypesAndCallback),
                status,
                t.javaClass.simpleName,
            )
            null
        }
    }

    @JvmStatic
    fun findAndHookMethod(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any?): CustomMethodUnhooker? {
        val className = clazz.canonicalName ?: clazz.name
        return try {
            val unhooker = XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
            if (unhooker != null) {
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.INSTALLED,
                )
            } else {
                HookDiagnostics.record(
                    PreferenceObserverRegistry.processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.INSTALL_FAILED,
                    "unhooker-null",
                )
                XposedHelpers.log("Failed to hook " + methodName + " method in " + className + " (unhooker is null)")
            }
            unhooker
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("Failed to hook " + methodName + " method in " + className)
            val status = when {
                HookDiagnostics.isMemberMissingException(t) -> HookDiagnostics.Status.TARGET_MEMBER_MISSING
                else -> HookDiagnostics.Status.INSTALL_FAILED
            }
            HookDiagnostics.record(
                PreferenceObserverRegistry.processName(),
                HookDiagnostics.Kind.METHOD,
                className,
                methodName,
                argList(*parameterTypesAndCallback),
                status,
                t.javaClass.simpleName,
            )
            null
        }
    }

    @JvmStatic
    @Suppress("UNUSED_RETURN_VALUE")
    fun findAndHookMethodSilently(className: String, classLoader: ClassLoader?, methodName: String, vararg parameterTypesAndCallback: Any?): Boolean {
        val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
        if (hookClass == null) {
            HookDiagnostics.record(
                PreferenceObserverRegistry.processName(),
                HookDiagnostics.Kind.METHOD,
                className,
                methodName,
                argList(*parameterTypesAndCallback),
                HookDiagnostics.Status.SILENTLY_SKIPPED,
                "class-not-found",
            )
            return false
        }
        return try {
            val unhooker = XposedHelpers.findAndHookMethod(hookClass, methodName, *parameterTypesAndCallback)
            val ok = unhooker != null
            HookDiagnostics.record(
                PreferenceObserverRegistry.processName(),
                HookDiagnostics.Kind.METHOD,
                className,
                methodName,
                argList(*parameterTypesAndCallback),
                if (ok) HookDiagnostics.Status.INSTALLED else HookDiagnostics.Status.SILENTLY_SKIPPED,
                if (ok) "" else "unhooker-null",
            )
            ok
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            HookDiagnostics.record(
                PreferenceObserverRegistry.processName(),
                HookDiagnostics.Kind.METHOD,
                className,
                methodName,
                argList(*parameterTypesAndCallback),
                HookDiagnostics.Status.SILENTLY_SKIPPED,
                t.javaClass.simpleName,
            )
            false
        }
    }

    @JvmStatic
    @Suppress("UNUSED_RETURN_VALUE")
    fun findAndHookMethodSilently(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any?): Boolean {
        val className = clazz.canonicalName ?: clazz.name
        return try {
            val unhooker = XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
            val ok = unhooker != null
            HookDiagnostics.record(
                PreferenceObserverRegistry.processName(),
                HookDiagnostics.Kind.METHOD,
                className,
                methodName,
                argList(*parameterTypesAndCallback),
                if (ok) HookDiagnostics.Status.INSTALLED else HookDiagnostics.Status.SILENTLY_SKIPPED,
                if (ok) "" else "unhooker-null",
            )
            ok
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            HookDiagnostics.record(
                PreferenceObserverRegistry.processName(),
                HookDiagnostics.Kind.METHOD,
                className,
                methodName,
                argList(*parameterTypesAndCallback),
                HookDiagnostics.Status.SILENTLY_SKIPPED,
                t.javaClass.simpleName,
            )
            false
        }
    }
}
