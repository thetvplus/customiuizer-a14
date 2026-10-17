package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Registry of all features the module can install.
 *
 * Each feature is identified by its [FeatureId] and is installed at most once per process.  The
 * registry matches by [FeatureTarget] and [InstallPhase], checks [FeatureSpec.isEnabled]
 * once, creates the [FeatureDefinition] only when enabled, and records the result. A failing
 * feature does not stop installation of the remaining features.
 *
 * This registry is intentionally a plain (non-concurrent) structure because it is only used from
 * the single LSPosed init thread during one installation call.  The process-scoped state is held
 * in [FeatureInstallState].
 */
class FeatureInstallRegistry {

    private val orderedFeatures = ArrayList<FeatureSpec>()
    private val definitions = HashMap<FeatureId, FeatureSpec>()

    /**
     * Register a feature spec.  Safe to call multiple times with the same spec.
     * A different spec under the same [FeatureId] is rejected and recorded.
     */
    @Synchronized
    fun register(feature: FeatureSpec) {
        val existing = definitions.putIfAbsent(feature.id, feature)
        if (existing != null && existing !== feature) {
            val message = "Duplicate feature id ${feature.id.name}: ${existing::class.java.name} vs ${feature::class.java.name}"
            HookDiagnostics.record(
                process = HookDiagnostics.currentProcessName ?: android.os.Process.myPid().toString(),
                kind = HookDiagnostics.Kind.FEATURE,
                targetClass = FeatureInstallRegistry::class.java.name,
                targetMember = feature.id.name,
                status = HookDiagnostics.Status.DUPLICATE_FEATURE,
                exceptionType = message,
            )
            throw IllegalArgumentException(message)
        }
        if (existing == null) {
            orderedFeatures.add(feature)
            FeatureInstallState.initialize(feature.id)
        }
    }

    /**
     * Install all features matching [target] and [phase].
     *
     * The default release path does **not** allocate a result list.  Pass [collectResults] = `true`
     * when tests or diagnostics need the ordered result list.
     */
    @Synchronized
    @JvmOverloads
    fun installAll(
        target: FeatureTarget,
        phase: InstallPhase,
        prefs: PrefMap,
        collectResults: Boolean = false,
    ): List<FeatureInstallResult> {
        val results = if (collectResults) ArrayList<FeatureInstallResult>() else null
        for (spec in orderedFeatures) {
            if (spec.target != target && spec.target != FeatureTarget.ANY) continue
            if (spec.phase != phase) continue
            val result = installOne(spec, prefs)
            if (results != null) results.add(result)
        }
        return results ?: emptyList()
    }

    private fun installOne(spec: FeatureSpec, prefs: PrefMap): FeatureInstallResult {
        if (!spec.isEnabled(prefs)) {
            return FeatureInstallResult.SKIPPED
        }

        val id = spec.id
        val state = FeatureInstallState.beginInstall(id)

        return when (state) {
            FeatureState.INSTALLED, FeatureState.INSTALLING -> FeatureInstallResult.ALREADY_INSTALLED
            FeatureState.FAILED_PERMANENT -> FeatureInstallResult.FAILED_PERMANENT
            FeatureState.FAILED_TRANSIENT, FeatureState.NOT_INSTALLED -> {
                val result = try {
                    spec.create().install()
                } catch (t: Throwable) {
                    FeatureInstallState.set(id, FeatureState.FAILED_TRANSIENT)
                    val reportable = FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(reportable)
                    recordInstallFailure(spec, reportable)
                    FeatureInstallResult.FAILED_TRANSIENT
                }
                FeatureInstallState.set(id, toState(result))
                result
            }
        }
    }

    private fun recordInstallFailure(spec: FeatureSpec, t: Throwable) {
        if (t is OutOfMemoryError) throw t
        HookDiagnostics.record(
            process = HookDiagnostics.currentProcessName ?: android.os.Process.myPid().toString(),
            kind = HookDiagnostics.Kind.FEATURE,
            targetClass = spec::class.java.name,
            targetMember = spec.id.name,
            descriptor = spec.name,
            status = HookDiagnostics.Status.INSTALL_FAILED,
            exceptionType = t.javaClass.name,
        )
    }

    private fun toState(result: FeatureInstallResult): FeatureState = when (result) {
        FeatureInstallResult.INSTALLED, FeatureInstallResult.ALREADY_INSTALLED -> FeatureState.INSTALLED
        FeatureInstallResult.FAILED_PERMANENT -> FeatureState.FAILED_PERMANENT
        FeatureInstallResult.FAILED_TRANSIENT -> FeatureState.FAILED_TRANSIENT
        FeatureInstallResult.SKIPPED -> FeatureState.NOT_INSTALLED
    }
}
