package tv.withaibuild.customiuizer.mods.island

import android.view.View
import android.view.ViewGroup

/** Inspect the real live lanes beneath their single blur owner. */
internal fun IslandActivityView.contentAt(index: Int): View =
    (getChildAt(0) as ViewGroup).getChildAt(index)
