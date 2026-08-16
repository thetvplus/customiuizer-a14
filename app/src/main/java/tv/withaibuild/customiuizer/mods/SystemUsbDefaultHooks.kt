package tv.withaibuild.customiuizer.mods

import android.os.Message
import io.github.libxposed.api.XposedModuleInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Non-destructive default USB function override for HyperOS 1 / Android 14.
 *
 * The ROM owns the persisted default ([UsbHandler.mScreenUnlockedFunctions]), ADB composition,
 * manual current-function sessions, policy paths and USB listeners.  This hook rewrites the
 * effective function argument while the ROM itself is applying a default-function path, and
 * supplements the two ROM branches that skip [setEnabledFunctions]: screen-unlock with a native
 * charging default, and cable plug-in while already unlocked.
 *
 * Mode mapping (read from the ListPreference as a String and parsed by PrefMap.getStringAsInt):
 *   0 -> follow system default
 *   1 -> charge only   (UsbManager.FUNCTION_NONE)
 *   2 -> file transfer  (UsbManager.FUNCTION_MTP)
 *   3 -> photo transfer (UsbManager.FUNCTION_PTP)
 *
 * The feature is installed even when the startup mode is "follow system" so a later preference
 * change can take effect on the next default-application event without reinstalling hooks.
 */
object SystemUsbDefaultHooks {

    internal const val PREF_KEY = "system_usb_default_function"

    private const val USB_DEVICE_MANAGER_CLASS = "com.android.server.usb.UsbDeviceManager"
    private const val USB_HANDLER_CLASS = "com.android.server.usb.UsbDeviceManager\$UsbHandler"
    private const val USB_HANDLER_HAL_CLASS = "com.android.server.usb.UsbDeviceManager\$UsbHandlerHal"
    private const val USB_HANDLER_LEGACY_CLASS = "com.android.server.usb.UsbDeviceManager\$UsbHandlerLegacy"

    // Preference modes (must match the ListPreference entryValues in prefs_system.xml).
    internal const val MODE_FOLLOW_SYSTEM = 0
    internal const val MODE_CHARGING = 1
    internal const val MODE_MTP = 2
    internal const val MODE_PTP = 3

    // USB function bitmasks.  Use literal AOSP values because the public SDK stubs hide these fields.
    internal const val FUNCTION_NONE = 0L
    internal const val FUNCTION_MTP = 4L // 1 << 2
    internal const val FUNCTION_PTP = 16L // 1 << 4

    /**
     * Parsed (functions, forceRestart, operationId) triplet for the exact
     * `setEnabledFunctions(JZI)V` boundary.
     */
    internal data class SetEnabledCall(
        val functions: Long,
        val forceRestart: Boolean,
        val operationId: Int,
    )

    /**
     * Parses the exact `setEnabledFunctions(JZI)V` argument array.
     *
     * Any arity, type or null mismatch returns `null` so the hook falls open.
     */
    internal fun parseSetEnabledCall(args: Array<Any?>): SetEnabledCall? {
        if (args.size != 3) return null
        val functions = args[0] as? Long ?: return null
        val forceRestart = args[1] as? Boolean ?: return null
        val operationId = args[2] as? Int ?: return null
        return SetEnabledCall(functions, forceRestart, operationId)
    }

    internal data class ResolvedTargets(
        val handlerClass: Class<*>,
        val handlerHalClass: Class<*>,
        val handlerLegacyClass: Class<*>?,
        val msgUpdateState: Int,
        val msgSetScreenUnlockedFunctions: Int,
        val msgUpdateScreenLock: Int,
        val msgBootCompleted: Int,
        val msgSystemReady: Int,
        val fieldScreenUnlockedFunctions: Field,
        val fieldScreenLocked: Field,
        val fieldCurrentFunctions: Field,
        val fieldBootCompleted: Field,
        val methodSetScreenUnlockedFunctions: Method,
        val methodFinishBoot: Method,
        val methodHandleMessage: Method,
        val methodSetEnabledFunctionsHal: Method,
        val methodSetEnabledFunctionsLegacy: Method?,
        val methodIsUsbTransferAllowed: Method,
        val operationCounter: AtomicInteger,
    )

    internal data class ContextFrame(val reason: ContextReason, val messageWhat: Int = -1)

    internal enum class ContextReason {
        HANDLE_MESSAGE,
        SET_SCREEN_UNLOCKED_FUNCTIONS,
        FINISH_BOOT,
    }

    internal object UsbDefaultContext {
        // Lazy per-thread deque: get() without a prior set() returns null.
        private val threadLocal = ThreadLocal<ArrayDeque<ContextFrame>?>()

        fun push(reason: ContextReason, messageWhat: Int = -1) {
            val deque = threadLocal.get() ?: ArrayDeque<ContextFrame>()
            deque.push(ContextFrame(reason, messageWhat))
            threadLocal.set(deque)
        }

        fun pop() {
            val deque = threadLocal.get() ?: return
            deque.pollFirst()
            if (deque.isEmpty()) {
                threadLocal.remove()
            }
        }

        fun peek(): ContextFrame? = threadLocal.get()?.peekFirst()

        fun clear() {
            threadLocal.get()?.clear()
            threadLocal.remove()
        }
    }

    /**
     * Installs the USB default-function hooks.  Called once at system_server startup.
     *
     * Any failure is fail-open: USB continues to behave natively and no host process is taken down.
     * Fatal errors ([OutOfMemoryError], [ThreadDeath], [VirtualMachineError]) are propagated.
     */
    @JvmStatic
    fun hook(lpparam: XposedModuleInterface.SystemServerStartingParam): FeatureInstallResult {
        return try {
            val targets = resolveTargets(lpparam.classLoader) ?: return FeatureInstallResult.FAILED_TRANSIENT
            if (!installAllHooks(targets)) {
                return FeatureInstallResult.FAILED_TRANSIENT
            }
            FeatureInstallResult.INSTALLED
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
            FeatureInstallResult.FAILED_TRANSIENT
        }
    }

    /**
     * Resolves ROM classes, message ids, fields and the exact methods we hook at cold install time.
     * No DexKit, no disk I/O.
     */
    private fun resolveTargets(classLoader: ClassLoader): ResolvedTargets? {
        val usbDeviceManagerClass = XposedHelpers.findClassIfExists(USB_DEVICE_MANAGER_CLASS, classLoader) ?: return null
        val handlerClass = XposedHelpers.findClassIfExists(USB_HANDLER_CLASS, classLoader) ?: return null
        val handlerHalClass = XposedHelpers.findClassIfExists(USB_HANDLER_HAL_CLASS, classLoader) ?: return null
        val handlerLegacyClass = XposedHelpers.findClassIfExists(USB_HANDLER_LEGACY_CLASS, classLoader)

        val msgUpdateState = getStaticIntQuiet(usbDeviceManagerClass, "MSG_UPDATE_STATE") ?: return null
        val msgSetScreenUnlockedFunctions = getStaticIntQuiet(usbDeviceManagerClass, "MSG_SET_SCREEN_UNLOCKED_FUNCTIONS") ?: return null
        val msgUpdateScreenLock = getStaticIntQuiet(usbDeviceManagerClass, "MSG_UPDATE_SCREEN_LOCK") ?: return null
        val msgBootCompleted = getStaticIntQuiet(usbDeviceManagerClass, "MSG_BOOT_COMPLETED") ?: return null
        val msgSystemReady = getStaticIntQuiet(usbDeviceManagerClass, "MSG_SYSTEM_READY") ?: return null

        val fieldScreenUnlockedFunctions = findFieldQuiet(handlerClass, "mScreenUnlockedFunctions") ?: return null
        val fieldScreenLocked = findFieldQuiet(handlerClass, "mScreenLocked") ?: return null
        val fieldCurrentFunctions = findFieldQuiet(handlerClass, "mCurrentFunctions") ?: return null
        val fieldBootCompleted = findFieldQuiet(handlerClass, "mBootCompleted") ?: return null

        val methodSetScreenUnlockedFunctions = findMethodQuiet(handlerClass, "setScreenUnlockedFunctions", Int::class.javaPrimitiveType!!) ?: return null
        val methodFinishBoot = findMethodQuiet(handlerClass, "finishBoot", Int::class.javaPrimitiveType!!) ?: return null
        val methodHandleMessage = findMethodQuiet(handlerClass, "handleMessage", Message::class.java) ?: return null

        val methodSetEnabledFunctionsHal = findMethodQuiet(
            handlerHalClass,
            "setEnabledFunctions",
            Long::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
        ) ?: return null

        val methodSetEnabledFunctionsLegacy = handlerLegacyClass?.let {
            findMethodQuiet(
                it,
                "setEnabledFunctions",
                Long::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )
        }

        val methodIsUsbTransferAllowed = findMethodQuiet(handlerClass, "isUsbTransferAllowed") ?: return null

        val operationCounter = try {
            XposedHelpers.getStaticObjectField(usbDeviceManagerClass, "sUsbOperationCount") as? AtomicInteger
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            null
        } ?: return null

        return ResolvedTargets(
            handlerClass = handlerClass,
            handlerHalClass = handlerHalClass,
            handlerLegacyClass = handlerLegacyClass,
            msgUpdateState = msgUpdateState,
            msgSetScreenUnlockedFunctions = msgSetScreenUnlockedFunctions,
            msgUpdateScreenLock = msgUpdateScreenLock,
            msgBootCompleted = msgBootCompleted,
            msgSystemReady = msgSystemReady,
            fieldScreenUnlockedFunctions = fieldScreenUnlockedFunctions,
            fieldScreenLocked = fieldScreenLocked,
            fieldCurrentFunctions = fieldCurrentFunctions,
            fieldBootCompleted = fieldBootCompleted,
            methodSetScreenUnlockedFunctions = methodSetScreenUnlockedFunctions,
            methodFinishBoot = methodFinishBoot,
            methodHandleMessage = methodHandleMessage,
            methodSetEnabledFunctionsHal = methodSetEnabledFunctionsHal,
            methodSetEnabledFunctionsLegacy = methodSetEnabledFunctionsLegacy,
            methodIsUsbTransferAllowed = methodIsUsbTransferAllowed,
            operationCounter = operationCounter,
        )
    }

    private fun installAllHooks(targets: ResolvedTargets): Boolean {
        val unhookers = mutableListOf<HookerClassHelper.CustomMethodUnhooker>()

        fun install(method: Method?, callback: HookerClassHelper.MethodHook): Boolean {
            if (method == null) return true
            val unhooker = try {
                ModuleHelper.hookMethod(method, callback)
            } catch (t: Throwable) {
                FatalErrors.rethrowIfFatal(t)
                null
            }
            if (unhooker == null) {
                unhookers.forEach { it.unhook() }
                unhookers.clear()
                return false
            }
            unhookers.add(unhooker)
            return true
        }

        if (!install(targets.methodSetScreenUnlockedFunctions, SetScreenUnlockedFunctionsHook())) return false
        if (!install(targets.methodFinishBoot, FinishBootHook())) return false
        if (!install(targets.methodHandleMessage, HandleMessageHook(targets))) return false
        if (!install(targets.methodSetEnabledFunctionsHal, SetEnabledFunctionsHook(targets))) return false
        if (!install(targets.methodSetEnabledFunctionsLegacy, SetEnabledFunctionsHook(targets))) return false

        return true
    }

    /**
     * Fast-path hook installed on [UsbHandlerHal]/[UsbHandlerLegacy].setEnabledFunctions(JZI).
     *
     * Only rewrites the argument when the call originates from a recognized default-application
     * context.  Manual current-function, policy, user-switch, accessory, tethering and MIDI paths
     * are never in that context, so they are left untouched.
     */
    internal class SetEnabledFunctionsHook(private val targets: ResolvedTargets) : HookerClassHelper.MethodHook() {

        public override fun before(callback: HookerClassHelper.BeforeHookCallback) {
            if (UsbDefaultContext.peek() == null) return

            val mode = getMode()
            if (mode == MODE_FOLLOW_SYSTEM) return

            val args = callback.getArgs()
            val handler = callback.getThisObject() ?: return

            val nativeDefault = readLong(handler, targets.fieldScreenUnlockedFunctions)
            val screenLocked = readBoolean(handler, targets.fieldScreenLocked)
            val transferAllowed = if (mode == MODE_MTP || mode == MODE_PTP) isUsbTransferAllowed(handler, targets) else true

            applySetEnabledFunctionsOverride(
                args = args,
                mode = mode,
                nativeDefault = nativeDefault,
                screenLocked = screenLocked,
                transferAllowed = transferAllowed,
            )
        }
    }

    /**
     * Production argument rewrite boundary.  Parses the exact JZI triplet, computes the
     * effective function and writes it back to `args[0]` when appropriate.
     *
     * Returns the effective value that was written, or `null` when the original arguments
     * must be preserved.
     */
    internal fun applySetEnabledFunctionsOverride(
        args: Array<Any?>,
        mode: Int,
        nativeDefault: Long,
        screenLocked: Boolean,
        transferAllowed: Boolean,
    ): Long? {
        val call = parseSetEnabledCall(args) ?: return null
        val effective = computeEffectiveUsbFunctions(
            mode = mode,
            nativeDefault = nativeDefault,
            currentFunctions = call.functions,
            screenLocked = screenLocked,
            forceRestart = call.forceRestart,
            transferAllowed = transferAllowed,
        )
        if (effective != null) args[0] = effective
        return effective
    }

    private class SetScreenUnlockedFunctionsHook : HookerClassHelper.MethodHook() {
        override fun before(callback: HookerClassHelper.BeforeHookCallback) {
            UsbDefaultContext.push(ContextReason.SET_SCREEN_UNLOCKED_FUNCTIONS)
        }

        override fun after(callback: HookerClassHelper.AfterHookCallback) {
            UsbDefaultContext.pop()
        }
    }

    private class FinishBootHook : HookerClassHelper.MethodHook() {
        override fun before(callback: HookerClassHelper.BeforeHookCallback) {
            UsbDefaultContext.push(ContextReason.FINISH_BOOT)
        }

        override fun after(callback: HookerClassHelper.AfterHookCallback) {
            UsbDefaultContext.pop()
        }
    }

    internal class HandleMessageHook(private val targets: ResolvedTargets) : HookerClassHelper.MethodHook() {
        public override fun before(callback: HookerClassHelper.BeforeHookCallback) {
            val msg = callback.getArg(0) as? Message ?: return
            if (isDefaultMessage(targets, msg.what)) {
                UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, msg.what)
            }
        }

        public override fun after(callback: HookerClassHelper.AfterHookCallback) {
            val frame = UsbDefaultContext.peek()
            if (frame?.reason != ContextReason.HANDLE_MESSAGE) return

            try {
                if (callback.getThrowable() != null) return

                val msg = callback.getArgs().getOrNull(0) as? Message ?: return
                if (!shouldSupplementDefaultApplication(targets, msg)) return

                val handler = callback.getThisObject() ?: return
                maybeApplyConfiguredDefault(handler, targets)
            } finally {
                UsbDefaultContext.pop()
            }
        }
    }

    /**
     * ROM default-application branches that do not always call [setEnabledFunctions] themselves:
     *
     * - `MSG_UPDATE_SCREEN_LOCK` unlock with native [FUNCTION_NONE]
     * - `MSG_UPDATE_STATE` connect while already unlocked (typical HyperOS charging default)
     *
     * Cable plug-in is the user-visible path: Xiaomi's native default is charging, so connect
     * never rewrites functions unless we apply the configured override here.
     */
    internal fun shouldSupplementDefaultApplication(targets: ResolvedTargets, msg: Message): Boolean {
        return when (msg.what) {
            targets.msgUpdateScreenLock -> msg.arg1 == 0 // 0 == screen unlocked
            targets.msgUpdateState -> msg.arg1 == 1 // 1 == connected
            else -> false
        }
    }

    /**
     * True when [currentFunctions] is still the ROM default rather than a manual USB session
     * (MIDI / RNDIS / accessory / notification-selected function).
     */
    internal fun isOverridableCurrentFunction(currentFunctions: Long, nativeDefault: Long): Boolean {
        return currentFunctions == FUNCTION_NONE || currentFunctions == nativeDefault
    }

    /**
     * Applies the configured USB default when the ROM's own default-application branch did not
     * call [setEnabledFunctions].  Manual current-function sessions are left untouched.
     */
    private fun maybeApplyConfiguredDefault(handler: Any, targets: ResolvedTargets) {
        if (!readBoolean(handler, targets.fieldBootCompleted)) return
        if (readBoolean(handler, targets.fieldScreenLocked)) return

        val mode = getMode()
        if (mode == MODE_FOLLOW_SYSTEM) return

        val nativeDefault = readLong(handler, targets.fieldScreenUnlockedFunctions)
        val currentFunctions = readLong(handler, targets.fieldCurrentFunctions)
        if (!isOverridableCurrentFunction(currentFunctions, nativeDefault)) return

        val transferAllowed = if (mode == MODE_MTP || mode == MODE_PTP) {
            isUsbTransferAllowed(handler, targets)
        } else {
            true
        }
        val effective = computeEffectiveUsbFunctions(
            mode = mode,
            nativeDefault = nativeDefault,
            currentFunctions = currentFunctions,
            screenLocked = false,
            forceRestart = false,
            transferAllowed = transferAllowed,
        ) ?: return

        val method = pickSetEnabledFunctionsMethod(handler, targets) ?: return
        val operationId = targets.operationCounter.incrementAndGet()
        try {
            method.invoke(handler, effective, false, operationId)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
        }
    }

    private fun pickSetEnabledFunctionsMethod(handler: Any, targets: ResolvedTargets): Method? {
        return when {
            targets.handlerHalClass.isAssignableFrom(handler.javaClass) -> targets.methodSetEnabledFunctionsHal
            targets.handlerLegacyClass?.isAssignableFrom(handler.javaClass) == true -> targets.methodSetEnabledFunctionsLegacy
            else -> null
        }
    }

    private fun isDefaultMessage(targets: ResolvedTargets, what: Int): Boolean {
        return what == targets.msgUpdateState ||
            what == targets.msgSetScreenUnlockedFunctions ||
            what == targets.msgUpdateScreenLock
    }

    /**
     * Pure preference-to-function mapping.  Used by the hook and by tests.
     */
    internal fun resolveEffective(mode: Int, nativeDefault: Long): Long {
        return when (mode) {
            MODE_FOLLOW_SYSTEM -> nativeDefault
            MODE_CHARGING -> FUNCTION_NONE
            MODE_MTP -> FUNCTION_MTP
            MODE_PTP -> FUNCTION_PTP
            else -> nativeDefault
        }
    }

    /**
     * Applies the policy/transfer guards and returns the function value to write, or `null` when
     * the original argument must be preserved.
     */
    internal fun computeEffectiveUsbFunctions(
        mode: Int,
        nativeDefault: Long,
        currentFunctions: Long,
        screenLocked: Boolean,
        forceRestart: Boolean,
        transferAllowed: Boolean,
    ): Long? {
        if (mode == MODE_FOLLOW_SYSTEM) return null
        if (screenLocked || forceRestart) return null
        val effective = resolveEffective(mode, nativeDefault)
        if (effective == currentFunctions) return null
        if ((mode == MODE_MTP || mode == MODE_PTP) && !transferAllowed) {
            return if (currentFunctions == FUNCTION_NONE) null else FUNCTION_NONE
        }
        return effective
    }

    internal fun getMode(): Int {
        return when (val value = MainModule.mPrefs.getStringAsInt(PREF_KEY, MODE_FOLLOW_SYSTEM)) {
            MODE_FOLLOW_SYSTEM, MODE_CHARGING, MODE_MTP, MODE_PTP -> value
            else -> MODE_FOLLOW_SYSTEM
        }
    }

    private fun isUsbTransferAllowed(handler: Any, targets: ResolvedTargets): Boolean {
        return try {
            targets.methodIsUsbTransferAllowed.invoke(handler) as? Boolean ?: false
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    // --- Reflection helpers with fatal propagation ---

    private fun readLong(obj: Any, field: Field): Long {
        return field.getLong(obj)
    }

    private fun readBoolean(obj: Any, field: Field): Boolean {
        return field.getBoolean(obj)
    }

    private fun getStaticIntQuiet(clazz: Class<*>, name: String): Int? {
        return try {
            XposedHelpers.getStaticIntField(clazz, name)
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            null
        }
    }

    private fun findFieldQuiet(clazz: Class<*>, name: String): Field? {
        return try {
            clazz.getDeclaredField(name).also { it.isAccessible = true }
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            null
        }
    }

    private fun findMethodQuiet(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        return try {
            if (parameterTypes.isEmpty()) {
                XposedHelpers.findMethodBestMatch(clazz, name)
            } else {
                XposedHelpers.findMethodExactIfExists(clazz, name, *parameterTypes)
            }
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            null
        }
    }
}
