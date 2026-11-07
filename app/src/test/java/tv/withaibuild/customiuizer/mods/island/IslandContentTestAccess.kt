package tv.withaibuild.customiuizer.mods.island

import android.view.View
import android.view.ViewGroup

/** Inspect the real live lanes beneath their shared content container. */
internal fun IslandActivityView.contentAt(index: Int): View =
    (getChildAt(0) as ViewGroup).getChildAt(index)
