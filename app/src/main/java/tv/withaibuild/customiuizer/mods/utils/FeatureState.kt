package tv.withaibuild.customiuizer.mods.utils

/**
 * Lifecycle state of a feature within one process.
 *
 * The state is kept under a registry-level lock, so a single feature is installed at most once
 * per process even if `installAll` is called concurrently.
 */
enum class FeatureState {
    NOT_INSTALLED,
    INSTALLING,
    INSTALLED,
    FAILED_TRANSIENT,
    FAILED_PERMANENT,
}
