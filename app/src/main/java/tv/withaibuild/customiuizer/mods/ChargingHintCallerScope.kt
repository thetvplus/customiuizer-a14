package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

private const val KEYGUARD_INDICATION_INJECTOR =
    "com.android.keyguard.injector.KeyguardIndicationInjector"
private const val MIUI_CHARGE_CONTROLLER = "com.miui.charge.MiuiChargeController"
private const val MAX_KEYGUARD_ANONYMOUS_CLASS_ORDINAL = 16

internal enum class ChargingHintCaller {
    UNKNOWN,
    KEYGUARD,
    MIUI_CHARGE
}

internal class ChargingHintCallerScopes {
    private val keyguardDepth = ThreadLocal<Int>()
    private val miuiChargeDepth = ThreadLocal<Int>()

    fun currentCaller(): ChargingHintCaller = when {
        (keyguardDepth.get() ?: 0) > 0 -> ChargingHintCaller.KEYGUARD
        (miuiChargeDepth.get() ?: 0) > 0 -> ChargingHintCaller.MIUI_CHARGE
        else -> ChargingHintCaller.UNKNOWN
    }

    fun isKeyguardCaller(): Boolean = currentCaller() == ChargingHintCaller.KEYGUARD

    fun enterKeyguard() {
        keyguardDepth.set((keyguardDepth.get() ?: 0) + 1)
    }

    fun exitKeyguard() {
        exit(keyguardDepth)
    }

    fun enterMiuiCharge() {
        miuiChargeDepth.set((miuiChargeDepth.get() ?: 0) + 1)
    }

    fun exitMiuiCharge() {
        exit(miuiChargeDepth)
    }

    private fun exit(depth: ThreadLocal<Int>) {
        val current = depth.get() ?: return
        if (current <= 1) {
            depth.remove()
        } else {
            depth.set(current - 1)
        }
    }
}

internal val chargingHintCallerScopes = ChargingHintCallerScopes()

internal fun resolveKeyguardChargingHintCallerMethod(
    candidates: Iterable<Class<*>>,
    outerClassName: String
): Method {
    val matches = candidates.mapNotNull { candidate ->
        if (candidate.superclass?.name != "android.os.AsyncTask") return@mapNotNull null
        val fields = candidate.declaredFields
        if (fields.none { it.name == "this\$0" && it.type.name == outerClassName }) {
            return@mapNotNull null
        }
        if (fields.none { it.name == "val\$batteryLevel" && it.type == Integer.TYPE }) {
            return@mapNotNull null
        }
        if (fields.none { it.name == "val\$powerPluggedIn" && it.type == java.lang.Boolean.TYPE }) {
            return@mapNotNull null
        }
        candidate.declaredMethods.singleOrNull { method ->
            method.name == "doInBackground" &&
                method.returnType == Any::class.java &&
                method.parameterTypes.contentEquals(arrayOf(Array<Any>::class.java))
        }
    }
    check(matches.size == 1) {
        "Expected exactly one verified Keyguard charging hint caller, found ${matches.size}"
    }
    return matches.single()
}

internal fun resolveMiuiChargeChargingHintCallerMethod(miuiChargeClass: Class<*>): Method {
    val matches = miuiChargeClass.declaredMethods.filter { method ->
        method.name == "onContentChanged" &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
    }
    check(matches.size == 1) {
        "Expected exactly one verified MiuiCharge charging hint caller, found ${matches.size}"
    }
    return matches.single()
}

internal fun installChargingHintCallerScopes(
    classLoader: ClassLoader?
): List<CustomMethodUnhooker>? {
    val methods = try {
        val keyguardCandidates = ArrayList<Class<*>>(MAX_KEYGUARD_ANONYMOUS_CLASS_ORDINAL)
        for (ordinal in 1..MAX_KEYGUARD_ANONYMOUS_CLASS_ORDINAL) {
            XposedHelpers.findClassIfExists("$KEYGUARD_INDICATION_INJECTOR\$$ordinal", classLoader)
                ?.let(keyguardCandidates::add)
        }
        val keyguardMethod = resolveKeyguardChargingHintCallerMethod(
            keyguardCandidates,
            KEYGUARD_INDICATION_INJECTOR
        )
        val miuiChargeClass = XposedHelpers.findClassIfExists(MIUI_CHARGE_CONTROLLER, classLoader)
            ?: error("Missing $MIUI_CHARGE_CONTROLLER")
        listOf(keyguardMethod, resolveMiuiChargeChargingHintCallerMethod(miuiChargeClass))
    } catch (t: Throwable) {
        XposedHelpers.log(FatalErrors.unwrapAndRethrowIfFatal(t))
        return null
    }

    val installed = ArrayList<CustomMethodUnhooker>(methods.size)
    for ((index, method) in methods.withIndex()) {
        val scopeHook = if (index == 0) keyguardScopeHook else miuiChargeScopeHook
        val unhooker = ModuleHelper.hookMethod(method, scopeHook)
        if (unhooker != null) {
            installed.add(unhooker)
        } else {
            unhookChargingHintCallerScopes(installed)
            return null
        }
    }
    return installed
}

internal fun unhookChargingHintCallerScopes(unhookers: List<CustomMethodUnhooker>) {
    for (unhooker in unhookers.asReversed()) {
        try {
            unhooker.unhook()
        } catch (t: Throwable) {
            XposedHelpers.log(FatalErrors.unwrapAndRethrowIfFatal(t))
        }
    }
}

private val keyguardScopeHook = object : MethodHook() {
    override fun intercept(chain: XposedInterface.Chain): Any? {
        chargingHintCallerScopes.enterKeyguard()
        return try {
            chain.proceed()
        } finally {
            chargingHintCallerScopes.exitKeyguard()
        }
    }
}

private val miuiChargeScopeHook = object : MethodHook() {
    override fun intercept(chain: XposedInterface.Chain): Any? {
        chargingHintCallerScopes.enterMiuiCharge()
        return try {
            chain.proceed()
        } finally {
            chargingHintCallerScopes.exitMiuiCharge()
        }
    }
}
