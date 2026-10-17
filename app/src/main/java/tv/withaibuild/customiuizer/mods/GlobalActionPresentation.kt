package tv.withaibuild.customiuizer.mods

import tv.withaibuild.customiuizer.R

/**
 * Display titles for GlobalAction IDs.
 *
 * Parameterized actions 8 / 9 / 10 / 20 keep titleResId 0 so
 * [tv.withaibuild.customiuizer.utils.AppHelper.getActionNameLocal] can attach the
 * selected app, shortcut, toggle, or activity subtitle.
 */
object GlobalActionPresentation {

    private val titles = mapOf(
        0 to R.string.notselected,
        1 to R.string.notselected,
        2 to R.string.array_global_actions_notif,
        3 to R.string.array_global_actions_eqs,
        4 to R.string.array_global_actions_lock,
        5 to R.string.array_global_actions_sleep,
        6 to R.string.array_global_actions_screenshot,
        7 to R.string.array_global_actions_recents,
        11 to R.string.array_global_actions_back,
        12 to R.string.array_global_actions_powermenu_short,
        13 to R.string.array_global_actions_clearmemory,
        14 to R.string.array_global_actions_invertcolors,
        15 to R.string.array_global_actions_goback,
        16 to R.string.array_global_actions_menu,
        17 to R.string.array_global_actions_volume,
        18 to R.string.array_global_actions_volume_up,
        19 to R.string.array_global_actions_volume_down,
        22 to R.string.array_global_actions_onehanded_left,
        23 to R.string.array_global_actions_clear_notifs,
        24 to R.string.array_global_actions_forceclose,
        25 to R.string.array_global_actions_scrolltotop,
        26 to R.string.array_global_actions_expandsidebar,
        27 to R.string.array_global_actions_floatingwindow,
        28 to R.string.array_global_actions_pinningwindow,
        29 to R.string.array_global_actions_splitscreen,
        85 to R.string.array_media_playpause,
        87 to R.string.array_media_next,
        88 to R.string.array_media_prev,
    )

    @JvmStatic
    fun titleResId(action: Int): Int = titles[action] ?: 0

    @JvmStatic
    fun isParameterized(action: Int): Boolean = action == 8 || action == 9 || action == 10 || action == 20
}
