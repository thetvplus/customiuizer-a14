package tv.withaibuild.customiuizer.mods.utils

/**
 * Historical screen-edge preference for StrongToast Dynamic Island.
 *
 * r14.20.0 is TOP-only. Persisted Bottom values migrate to [TOP] so old backups cannot re-enter
 * a removed Bottom geometry path.
 */
enum class StrongToastPosition(val preferenceValue: Int) {
    TOP(0),
    /**
     * Source-compatibility sentinel while obsolete StrongToast shell code is removed.
     * [fromPreference] never returns it and r14.20.0 exposes no Bottom configuration.
     */
    @Deprecated("Dynamic Island is TOP-only")
    BOTTOM(1);

    companion object {
        /** Legacy preference value that meant Bottom before TOP-only. */
        const val LEGACY_BOTTOM_VALUE = 1

        @JvmStatic
        fun fromPreference(value: Int): StrongToastPosition {
            // Any non-top value, including legacy Bottom (1), migrates to TOP.
            return TOP
        }
    }
}
