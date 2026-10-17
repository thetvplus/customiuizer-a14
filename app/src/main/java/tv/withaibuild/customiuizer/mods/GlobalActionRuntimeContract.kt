package tv.withaibuild.customiuizer.mods

/**
 * Static ownership table for advertised GlobalAction IDs and trigger keys.
 *
 * This is metadata for contract tests and audits. Execution still goes through
 * [GlobalActions.handleResolvedAction]; this table must not grow into a dispatcher.
 */
enum class GlobalActionOwner {
    NONE,
    LOCAL,
    SYSTEMUI,
    SYSTEM_SERVER,
    SECURITY_CENTER,
    LAUNCHER,
}

enum class TriggerInstallMode {
    NA,
    ALWAYS,
    PREF_GATED_AT_START,
}

enum class ReceiverInstallMode {
    NA,
    ALWAYS,
    PREF_GATED,
    LOCAL,
}

data class GlobalActionRuntimeSpec(
    val id: Int,
    val name: String,
    val owner: GlobalActionOwner,
    val transport: String?,
    val installPoint: String,
    val installGate: String,
    val runtimeChangeSupported: Boolean,
    val restartRequirement: String,
) {
    val executionOwner: GlobalActionOwner get() = owner
    val receiverInstallMode: String get() = installGate
}

data class GlobalActionTriggerSpec(
    val preferenceKey: String,
    val triggerOwner: GlobalActionOwner,
    val triggerInstallMode: TriggerInstallMode,
    val executionOwner: String,
    val receiverInstallMode: ReceiverInstallMode,
    val restartRequirement: String,
    val triggerHook: String,
)

object GlobalActionRuntimeContract {

    val specs: List<GlobalActionRuntimeSpec> = listOf(
        spec(1, "none", GlobalActionOwner.NONE, null, "n/a", "n/a", true, "NONE"),
        systemUi(2, "ExpandNotifications"),
        systemUi(3, "ExpandSettings"),
        systemUi(4, "LockDevice"),
        systemUi(5, "GoToSleep"),
        systemUi(6, "TakeScreenshot"),
        systemUi(7, "OpenRecents"),
        systemUi(8, "LaunchIntent", "Launch App"),
        systemUi(9, "LaunchIntent", "Launch Shortcut"),
        systemUi(10, "Toggle*", "Toggle"),
        pwm(11, "SwitchToPrevApp"),
        systemUi(12, "OpenPowerMenu"),
        systemUi(13, "ClearMemory"),
        pwm(14, "ToggleColorInversion"),
        systemUi(15, "GoBack"),
        pwm(16, "SimulateMenu"),
        systemUi(17, "OpenVolumeDialog"),
        systemUi(18, "VolumeUp"),
        systemUi(19, "VolumeDown"),
        systemUi(20, "LaunchIntent", "Launch Activity"),
        systemUi(22, "SwitchOneHanded"),
        systemUi(23, "ClearNotifications"),
        pwm(24, "ForceClose"),
        systemUi(25, "ScrollToTop"),
        spec(
            26, "ShowSideBar", GlobalActionOwner.SECURITY_CENTER, "ShowSideBar",
            "Various.AddSideBarExpandReceiverHook",
            "various_enable_expand_sidebar",
            false,
            "SECURITY_CENTER",
        ),
        systemUi(27, "FloatingWindow"),
        spec(
            28, "PinningWindow", GlobalActionOwner.SYSTEMUI, "PinningWindow",
            "setupStatusBar/freeformModeReceiver",
            "hasConfiguredActionCode(28)",
            true,
            "SYSTEMUI",
        ),
        spec(
            29, "SplitScreen", GlobalActionOwner.SYSTEMUI, "SplitScreen",
            "setupStatusBar/soScSplitScreenReceiver",
            "hasConfiguredActionCode(29)",
            true,
            "SYSTEMUI",
        ),
        spec(85, "PlayPause", GlobalActionOwner.LOCAL, null, "handleResolvedAction/sendDownUpKeyEvent", "n/a", true, "NONE"),
        spec(87, "Next", GlobalActionOwner.LOCAL, null, "handleResolvedAction/sendDownUpKeyEvent", "n/a", true, "NONE"),
        spec(88, "Previous", GlobalActionOwner.LOCAL, null, "handleResolvedAction/sendDownUpKeyEvent", "n/a", true, "NONE"),
    )

    val triggerSpecs: List<GlobalActionTriggerSpec> = listOf(
        sysServerAlways("controls_backlong", "Controls.NavBarActionsHook"),
        sysServerAlways("controls_homelong", "Controls.NavBarActionsHook"),
        sysServerAlways("controls_menulong", "Controls.NavBarActionsHook"),
        sysServerAlways("controls_powerdt", "Controls.PowerDoubleTapActionHook"),
        launcherGated("controls_fsg_assist_left", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.FSGesturesHook"),
        launcherGated("controls_fsg_assist_right", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.FSGesturesHook"),
        launcherGated("controls_fsg_swipeandstop", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.FSGesturesHook"),
        systemUiGated("controls_navbarleft", "LAUNCHER|SYSTEMUI", "Controls.NavBarButtonsHook"),
        systemUiGated("controls_navbarleftlong", "LAUNCHER|SYSTEMUI", "Controls.NavBarButtonsHook"),
        systemUiGated("controls_navbarright", "LAUNCHER|SYSTEMUI", "Controls.NavBarButtonsHook"),
        systemUiGated("controls_navbarrightlong", "LAUNCHER|SYSTEMUI", "Controls.NavBarButtonsHook"),
        launcherGated("launcher_swipedown", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.HomescreenSwipesHook"),
        launcherGated("launcher_swipeup", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.HomescreenSwipesHook"),
        launcherGated("launcher_swipedown2", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.HomescreenSwipesHook"),
        launcherGated("launcher_swipeup2", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.HomescreenSwipesHook"),
        launcherGated("launcher_swipeleft", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.HotSeatSwipesHook"),
        launcherGated("launcher_swiperight", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.HotSeatSwipesHook"),
        launcherGated("launcher_doubletap", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.LauncherDoubleTapHook"),
        launcherGated("launcher_pinch", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.LauncherPinchHook"),
        launcherGated("launcher_shake", "LAUNCHER|SYSTEMUI", "LauncherGestureHooks.ShakeHook"),
        launcherGated("launcher_spread", "LAUNCHER|SYSTEMUI", "LauncherFolderHooks.PrivacyFolderHook"),
        systemUiGated("system_statusbarcontrols", "SYSTEMUI", "SystemUIControlCenterHooks.StatusBarGesturesHook"),
        systemUiGated("system_statusbarcontrols_longpress", "SYSTEMUI", "SystemUIControlCenterHooks.StatusBarGesturesHook"),
        systemUiGated("system_lockscreenshortcuts_left", "SYSTEMUI", "SystemUILockScreenHooks.LockScreenShortcutHook"),
        systemUiGated("system_lockscreenshortcuts_right", "SYSTEMUI", "SystemUILockScreenHooks.LockScreenShortcutHook"),
    )

    fun spec(id: Int): GlobalActionRuntimeSpec? = specs.firstOrNull { it.id == id }

    fun trigger(preferenceKey: String): GlobalActionTriggerSpec? =
        triggerSpecs.firstOrNull { it.preferenceKey == preferenceKey }

    fun ids(): Set<Int> = specs.map { it.id }.toSet()

    fun triggerKeys(): Set<String> = triggerSpecs.map { it.preferenceKey }.toSet()

    private fun spec(
        id: Int,
        name: String,
        owner: GlobalActionOwner,
        transport: String?,
        installPoint: String,
        installGate: String,
        runtimeChangeSupported: Boolean,
        restartRequirement: String,
    ) = GlobalActionRuntimeSpec(
        id, name, owner, transport, installPoint, installGate, runtimeChangeSupported, restartRequirement,
    )

    private fun systemUi(id: Int, transport: String, name: String = transport) = spec(
        id, name, GlobalActionOwner.SYSTEMUI, transport,
        "setupStatusBar/mSBReceiver",
        "ALWAYS",
        true,
        "SYSTEMUI",
    )

    private fun pwm(id: Int, transport: String) = spec(
        id, transport, GlobalActionOwner.SYSTEM_SERVER, transport,
        "setupGlobalActions/phoneWindowManagerActionReceiver",
        "ALWAYS",
        true,
        "NONE",
    )

    private fun sysServerAlways(key: String, hook: String) = GlobalActionTriggerSpec(
        preferenceKey = key,
        triggerOwner = GlobalActionOwner.SYSTEM_SERVER,
        triggerInstallMode = TriggerInstallMode.ALWAYS,
        executionOwner = "according_to_action",
        receiverInstallMode = ReceiverInstallMode.ALWAYS,
        restartRequirement = "NONE",
        triggerHook = hook,
    )

    private fun launcherGated(key: String, restart: String, hook: String) = GlobalActionTriggerSpec(
        preferenceKey = key,
        triggerOwner = GlobalActionOwner.LAUNCHER,
        triggerInstallMode = TriggerInstallMode.PREF_GATED_AT_START,
        executionOwner = "according_to_action",
        receiverInstallMode = ReceiverInstallMode.PREF_GATED,
        restartRequirement = restart,
        triggerHook = hook,
    )

    private fun systemUiGated(key: String, restart: String, hook: String) = GlobalActionTriggerSpec(
        preferenceKey = key,
        triggerOwner = GlobalActionOwner.SYSTEMUI,
        triggerInstallMode = TriggerInstallMode.PREF_GATED_AT_START,
        executionOwner = "according_to_action",
        receiverInstallMode = ReceiverInstallMode.PREF_GATED,
        restartRequirement = restart,
        triggerHook = hook,
    )
}
