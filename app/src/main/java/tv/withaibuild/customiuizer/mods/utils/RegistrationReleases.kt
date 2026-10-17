package tv.withaibuild.customiuizer.mods.utils

/**
 * Cold-path helper for releasing a SystemUI registration.
 *
 * Called from cleanup callbacks to remove a dark receiver or an icon group. Failures are
 * isolated and recorded, but fatal JVM errors always propagate. The diagnostics hold only
 * strings and exception types, never the View, Context or Throwable.
 */
fun releaseRegistrationSilently(
    target: Any?,
    methodName: String,
    argument: Any?,
    tag: String,
) {
    if (target == null) {
        HookDiagnostics.record(
            PreferenceObserverRegistry.processName(),
            HookDiagnostics.Kind.RECEIVER,
            "?",
            methodName,
            tag,
            HookDiagnostics.Status.SILENTLY_SKIPPED,
            "target-null",
        )
        return
    }

    val targetClass = try {
        target.javaClass.name
    } catch (_: Throwable) {
        "?"
    }

    try {
        XposedHelpers.callMethod(target, methodName, argument)
    } catch (t: Throwable) {
        val toReport = FatalErrors.unwrapAndRethrowIfFatal(t)

        val status = if (HookDiagnostics.isMemberMissingException(toReport)) {
            HookDiagnostics.Status.TARGET_MEMBER_MISSING
        } else {
            HookDiagnostics.Status.RECEIVER_UNREGISTER_FAILED
        }

        HookDiagnostics.record(
            PreferenceObserverRegistry.processName(),
            HookDiagnostics.Kind.RECEIVER,
            targetClass,
            methodName,
            argument?.javaClass?.simpleName ?: "",
            status,
            toReport.javaClass.simpleName,
        )
        XposedHelpers.log("releaseRegistrationSilently failed: $methodName on $tag: ${toReport.javaClass.simpleName}")
    }
}
