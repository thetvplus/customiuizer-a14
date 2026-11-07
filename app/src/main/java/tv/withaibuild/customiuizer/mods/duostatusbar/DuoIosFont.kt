package tv.withaibuild.customiuizer.mods.duostatusbar

import android.content.Context
import android.graphics.Typeface
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper

/** Nunito 900 subset under OFL-1.1. Shared cold resource owner;
 * rounded Android approximation, without Apple font data. */
internal object DuoIosFont {
    private var font: Typeface?=null
    @Synchronized fun get(context: Context): Typeface = font ?: requireNotNull(
        ModuleHelper.getModuleRes(context).getFont(R.font.duo_rounded_heavy)).also { font=it }
}
