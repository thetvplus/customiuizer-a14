package tv.withaibuild.customiuizer.mods.volumedialogautohide

import android.view.accessibility.AccessibilityManager
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.concurrent.atomic.AtomicReference

/**
 * Hot-path effect for the VolumeDialogAutohideDelay hook.
 *
 * The effect holds an immutable ABI, a snapshot reference and an install-resolved
 * accessibility recommender. It never discovers members or looks up ClassLoaders
 * in callbacks, and does not retain dialog or manager instances between calls.
 *
 * Each callback selects exactly one execution mode at the start and keeps it for
 * the entire invocation: FAST uses the frozen [java.lang.reflect.Field] handles;
 * LEGACY uses the existing XposedHelpers get helpers. Mixed access within a single
 * callback is forbidden.
 */
internal class VolumeDialogAutohideDelayEffect(
    val abi: VolumeDialogAutohideDelayAbi?,
    val snapshotRef: AtomicReference<VolumeDialogAutohideDelaySnapshot?>,
    private val recommendTimeout: (Any?, Int, Int) -> Int?,
) {

    /**
     * Production entry point from the [MethodHook.before] callback.
     */
    fun before(param: BeforeHookCallback) {
        val thisObject = param.getThisObject()
        process(thisObject, param)
    }

    /**
     * Testable entry point.
     *
     * Captures the snapshot exactly once for mode selection and, if FAST is
     * selected, passes the same captured immutable snapshot through the entire
     * FAST invocation.
     */
    internal fun process(thisObject: Any?, param: BeforeHookCallback) {
        val a = abi
        val snapshot = snapshotRef.get()

        if (
            a != null &&
            thisObject != null &&
            thisObject.javaClass === a.resolutionRootClass &&
            snapshot != null
        ) {
            processFast(thisObject, a, snapshot, param)
        } else {
            processLegacy(thisObject, param)
        }
    }

    /**
     * FAST path: all field access uses frozen [Field] handles. No preference read
     * is performed inside this path.
     */
    private fun processFast(
        thisObject: Any,
        a: VolumeDialogAutohideDelayAbi,
        snapshot: VolumeDialogAutohideDelaySnapshot,
        param: BeforeHookCallback,
    ) {
        val mHovering = withLegacyIllegalAccessError { a.mHoveringField.getBoolean(thisObject) }
        if (mHovering) {
            returnTimeout(thisObject, 16000, false, param)
            return
        }

        val mSafetyWarning = readSafetyWarning(thisObject)
        if (mSafetyWarning) {
            val opt = snapshot.expanded
            returnTimeout(thisObject, if (opt > 0) opt else 5000, true, param)
            return
        }

        val mExpanded = withLegacyIllegalAccessError { a.mExpandedField.getBoolean(thisObject) }
        val opt = if (mExpanded) snapshot.expanded else snapshot.collapsed
        if (opt > 0) {
            returnTimeout(thisObject, opt, false, param)
        }
    }

    /**
     * LEGACY path: preserves the original dynamic reflection behavior.
     */
    private fun processLegacy(thisObject: Any?, param: BeforeHookCallback) {
        val mHovering = XposedHelpers.getBooleanField(thisObject, "mHovering")
        if (mHovering) {
            returnTimeout(thisObject, 16000, false, param)
            return
        }

        val mSafetyWarning = try {
            XposedHelpers.getObjectField(thisObject, "mIsSafetyShowing") as Boolean
        } catch (e: Throwable) {
            FatalErrors.rethrowIfFatal(e)
            XposedHelpers.getObjectField(thisObject, "mSafetyWarning") as Boolean
        }

        if (mSafetyWarning) {
            val opt = MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0)
            returnTimeout(thisObject, if (opt > 0) opt else 5000, true, param)
            return
        }

        val mExpanded = XposedHelpers.getBooleanField(thisObject, "mExpanded")
        val opt = MainModule.mPrefs.getInt(
            if (mExpanded) "system_volumedialogdelay_expanded" else "system_volumedialogdelay_collapsed",
            0,
        )
        if (opt > 0) {
            returnTimeout(thisObject, opt, false, param)
        }
    }

    private fun returnTimeout(thisObject: Any?, base: Int, hasText: Boolean, param: BeforeHookCallback) {
        val flags = AccessibilityManager.FLAG_CONTENT_CONTROLS or
            (if (hasText) AccessibilityManager.FLAG_CONTENT_TEXT else 0)
        val timeout = recommendTimeout(thisObject, base, flags) ?: return
        if (timeout > 0) param.returnAndSkip(timeout)
    }

    /**
     * Strategy A: keep the exact legacy safety-alias block. Do not cache the
     * fields or resolve them at install time.
     */
    private fun readSafetyWarning(thisObject: Any): Boolean {
        return try {
            XposedHelpers.getObjectField(thisObject, "mIsSafetyShowing") as Boolean
        } catch (e: Throwable) {
            FatalErrors.rethrowIfFatal(e)
            XposedHelpers.getObjectField(thisObject, "mSafetyWarning") as Boolean
        }
    }

    /**
     * Wraps a fast field operation to map [IllegalAccessException] to
     * [IllegalAccessError], matching the legacy XposedHelpers contract.
     */
    private inline fun <T> withLegacyIllegalAccessError(action: () -> T): T {
        return try {
            action()
        } catch (e: IllegalAccessException) {
            XposedHelpers.log(e)
            throw IllegalAccessError(e.message)
        }
    }
}
