package tv.withaibuild.customiuizer.mods.volumedialogautohide

import android.view.accessibility.AccessibilityManager
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Field

/** One timeout policy for the resolved ROM class and the runtime reflection fallback. */
internal class VolumeDialogAutohideDelayEffect(
    private val abi: VolumeDialogAutohideDelayAbi?,
    private val prefs: PrefMap,
    private val recommendTimeout: (Any?, Int, Int) -> Int?,
) {
    fun before(param: BeforeHookCallback) {
        val dialog = param.getThisObject()
        val resolved = abi?.takeIf { dialog?.javaClass === it.resolutionRootClass }
        if (readBoolean(dialog, resolved?.mHoveringField, "mHovering")) {
            returnTimeout(dialog, 16000, false, param)
            return
        }

        val safetyWarning = readSafetyWarning(dialog)
        val expanded = safetyWarning || readBoolean(dialog, resolved?.mExpandedField, "mExpanded")
        val configured = prefs.getInt(
            if (expanded) "system_volumedialogdelay_expanded" else "system_volumedialogdelay_collapsed",
            0,
        )
        if (configured > 0 || safetyWarning) {
            returnTimeout(dialog, if (configured > 0) configured else 5000, safetyWarning, param)
        }
    }

    private fun readBoolean(dialog: Any?, field: Field?, name: String): Boolean {
        if (field == null) return XposedHelpers.getBooleanField(dialog, name)
        return try {
            field.getBoolean(dialog)
        } catch (e: IllegalAccessException) {
            // Match XposedHelpers for a handle that becomes inaccessible.
            XposedHelpers.log(e)
            throw IllegalAccessError(e.message)
        }
    }

    private fun returnTimeout(thisObject: Any?, base: Int, hasText: Boolean, param: BeforeHookCallback) {
        val flags = AccessibilityManager.FLAG_CONTENT_CONTROLS or
            (if (hasText) AccessibilityManager.FLAG_CONTENT_TEXT else 0)
        val timeout = recommendTimeout(thisObject, base, flags) ?: return
        if (timeout > 0) param.returnAndSkip(timeout)
    }

    // ROMs expose either alias. A present but unreadable primary value also uses the fallback.
    private fun readSafetyWarning(dialog: Any?): Boolean {
        return try {
            XposedHelpers.getObjectField(dialog, "mIsSafetyShowing") as Boolean
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.getObjectField(dialog, "mSafetyWarning") as Boolean
        }
    }
}
