package miui.systemui.util

import android.content.Context
import android.content.res.Configuration

object CommonUtils {
    var forceVertical = false
    var modeReads = 0

    fun getInVerticalMode(context: Context): Boolean {
        modeReads++
        return forceVertical || context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }
}
