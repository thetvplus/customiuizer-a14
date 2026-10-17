package tv.withaibuild.customiuizer.mods.statusbariconvisibility

import android.telephony.SubscriptionManager
import tv.withaibuild.customiuizer.mods.StatusBarIconVisibilitySnapshot
import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * Hot-path effect for the HideIconsSignal Architecture C hook.
 *
 * The effect holds only an immutable [StatusBarIconVisibilityAbi] and a snapshot
 * provider. It never performs runtime member discovery, [ClassLoader] lookups,
 * string-based field lookups, or generic reflection. It does not retain runtime
 * [View], [Context], [Activity], [StatusBarMobileView], or [MobileIconState]
 * instances beyond the hot method invocation.
 *
 * Each callback selects exactly one execution mode at the start and keeps it for
 * the entire invocation: FAST uses the frozen [java.lang.reflect.Field] handles;
 * LEGACY uses the existing XposedHelpers get/set helpers. Mixed access within a
 * single callback is forbidden.
 */
internal class StatusBarIconVisibilityEffect(
    val abi: StatusBarIconVisibilityAbi?,
    val snapshotProvider: () -> StatusBarIconVisibilitySnapshot,
) {

    /**
     * Production entry point from the [MethodHook.before] callback.
     *
     * The mode is chosen before any field operation begins and never changes
     * mid-callback.
     */
    fun before(param: BeforeHookCallback) {
        // Frozen accessor order from A0: getArg(0) first, then member.name, then thisObject.
        // This must not change because REAL_METHOD_OVERLOAD_SET and zero-arg behavior are
        // NOT_PROVEN; any failure/access before getArg(0) would infer unsupported semantics.
        val mobileIconState = param.getArg(0)
        val methodName = param.getMember().name
        val thisObject = param.getThisObject()
        process(thisObject, mobileIconState, methodName)
    }

    /**
     * Testable entry point. [methodName] is the [java.lang.reflect.Member.getName].
     */
    internal fun process(thisObject: Any?, mobileIconState: Any?, methodName: String) {
        val useFast = isFastEligible(thisObject, mobileIconState)
        if (useFast) {
            processFast(thisObject!!, mobileIconState!!, methodName)
        } else {
            processLegacy(thisObject, mobileIconState, methodName)
        }
    }

    private fun isFastEligible(thisObject: Any?, mobileIconState: Any?): Boolean {
        val a = abi ?: return false
        return thisObject != null &&
            mobileIconState != null &&
            thisObject.javaClass === a.statusBarMobileViewResolutionRootClass &&
            mobileIconState.javaClass === a.mobileIconStateResolutionRootClass
    }

    /**
     * FAST path: all operations use the frozen [java.lang.reflect.Field] handles.
     *
     * `IllegalAccessException` is mapped to `IllegalAccessError` to preserve the
     * legacy XposedHelpers contract. All other field/cast failures propagate and
     * are handled by the outer `MethodHook.beforeHook` boundary. No legacy retry
     * is attempted after the first fast operation has begun.
     */
    private fun processFast(thisObject: Any, mobileIconState: Any, methodName: String) {
        val a = abi ?: error("processFast called without ABI")

        var shouldUpdate = methodName == "updateState"
        if (!shouldUpdate) {
            val mState = withLegacyIllegalAccessError { a.mStateField.get(thisObject) }
            shouldUpdate = mState == null
        }
        if (!shouldUpdate) return

        val snapshot = snapshotProvider()
        if (!SystemUIStatusBarHooks.hasMobileSignalHidingWork(snapshot)) return

        val wifiAvailable = if (snapshot.hideSignal) {
            withLegacyIllegalAccessError { a.wifiAvailableField.getBoolean(mobileIconState) }
        } else {
            false
        }
        val subId: Int
        val dataSubId: Int
        val slotId: Int
        if (SystemUIStatusBarHooks.needsSubscriptionLookup(snapshot)) {
            subId = withLegacyIllegalAccessError { a.subIdField.get(mobileIconState) } as Int
            dataSubId = SubscriptionManager.getActiveDataSubscriptionId()
            slotId = SubscriptionManager.getSlotIndex(subId)
        } else {
            subId = 0
            dataSubId = 0
            slotId = 0
        }
        val result = SystemUIStatusBarHooks.computeSignalIconHiding(
            wifiAvailable,
            subId,
            dataSubId,
            slotId,
            snapshot,
        )

        if (result.visible == false) {
            withLegacyIllegalAccessError { a.visibleField.set(mobileIconState, false) }
            return
        }
        if (result.roaming != null) {
            withLegacyIllegalAccessError { a.roamingField.set(mobileIconState, result.roaming) }
        }
        if (result.volte != null) {
            withLegacyIllegalAccessError { a.volteField.set(mobileIconState, result.volte) }
            withLegacyIllegalAccessError { a.speechHdField.set(mobileIconState, result.speechHd) }
        }
    }

    /**
     * LEGACY path: preserves the original dynamic reflection behavior.
     *
     * This is used for strict subclasses, superclasses, unrelated runtime classes,
     * missing ABI, resolver misses, or any pre-fast ABI incompatibility.
     */
    private fun processLegacy(thisObject: Any?, mobileIconState: Any?, methodName: String) {
        var shouldUpdate = methodName == "updateState"
        if (!shouldUpdate) {
            val mState = XposedHelpers.getObjectField(thisObject, "mState")
            shouldUpdate = mState == null
        }
        if (!shouldUpdate) return

        val snapshot = snapshotProvider()
        if (!SystemUIStatusBarHooks.hasMobileSignalHidingWork(snapshot)) return

        val wifiAvailable = if (snapshot.hideSignal) {
            XposedHelpers.getBooleanField(mobileIconState, "wifiAvailable")
        } else {
            false
        }
        val subId: Int
        val dataSubId: Int
        val slotId: Int
        if (SystemUIStatusBarHooks.needsSubscriptionLookup(snapshot)) {
            subId = XposedHelpers.getObjectField(mobileIconState, "subId") as Int
            dataSubId = SubscriptionManager.getActiveDataSubscriptionId()
            slotId = SubscriptionManager.getSlotIndex(subId)
        } else {
            subId = 0
            dataSubId = 0
            slotId = 0
        }
        val result = SystemUIStatusBarHooks.computeSignalIconHiding(
            wifiAvailable,
            subId,
            dataSubId,
            slotId,
            snapshot,
        )

        if (result.visible == false) {
            XposedHelpers.setObjectField(mobileIconState, "visible", false)
            return
        }
        if (result.roaming != null) {
            XposedHelpers.setObjectField(mobileIconState, "roaming", result.roaming)
        }
        if (result.volte != null) {
            XposedHelpers.setObjectField(mobileIconState, "volte", result.volte)
            XposedHelpers.setObjectField(mobileIconState, "speechHd", result.speechHd)
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
