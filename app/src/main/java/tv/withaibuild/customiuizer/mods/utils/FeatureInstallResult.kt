package tv.withaibuild.customiuizer.mods.utils

/**
 * Outcome of installing a single feature.
 *
 * Feature installation must be explicit about what happened.  A feature is a unit of
 * functionality guarded by a preference and a process/phase target.  The result tells the
 * installer whether the feature is now active, was skipped because it is disabled or not
 * applicable, failed in a way that should be retried, or failed permanently.
 *
 * This is an enum so the common results are singletons.  Detailed reasons (exception type,
 * skip cause) are recorded in bounded diagnostics rather than in the result object.
 */
enum class FeatureInstallResult {

    /** The feature was installed for the first time in this process. */
    INSTALLED,

    /** The feature was already installed; the request was idempotent. */
    ALREADY_INSTALLED,

    /**
     * The feature was skipped because it is disabled, not applicable to the current process/phase,
     * or a precondition was not met.
     */
    SKIPPED,

    /**
     * Installation failed but may succeed later (for example, the target class is not yet loaded).
     * The installer may retry the feature on a later phase or package-ready callback.
     */
    FAILED_TRANSIENT,

    /**
     * Installation failed and should not be retried automatically (for example, a class or method
     * is missing in this ROM and the feature cannot work).  The failure is recorded once.
     */
    FAILED_PERMANENT;

    /** Whether this result means the feature is active in the target process. */
    val isActive: Boolean
        get() = this == INSTALLED || this == ALREADY_INSTALLED
}
