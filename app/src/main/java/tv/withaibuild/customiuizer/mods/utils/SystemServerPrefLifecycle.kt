package tv.withaibuild.customiuizer.mods.utils

/**
 * Pure gates for the system_server preference-install lifecycle.
 *
 * Business features are preference-gated at [FeatureInstallRegistry.installAll]. When the
 * remote snapshot is not ready the installer feeds an empty map so those features stay
 * [FeatureState.NOT_INSTALLED]. The first transition to [PreferenceBootstrap.State.LOADED]
 * is allowed to run `installAll` again; already-installed features stay installed.
 *
 * Later preference toggles must not install new hooks — that remains a reboot/apply path.
 */
internal object SystemServerPrefLifecycle {

    fun shouldMarkCatchUpComplete(
        prefReady: Boolean,
        state: PreferenceBootstrap.State?,
    ): Boolean = prefReady && state == PreferenceBootstrap.State.LOADED

    fun shouldRunCatchUp(
        catchUpDone: Boolean,
        state: PreferenceBootstrap.State?,
    ): Boolean = !catchUpDone && state == PreferenceBootstrap.State.LOADED
}
