package tv.withaibuild.customiuizer.mods.clock

import android.content.Context
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Cold resolver for the Architecture C SystemClock core.
 *
 * All reflection and `ClassLoader` discovery happens once during install (or test setup).  The
 * output is an immutable [ClockAbi].  No `Context`, `View` or controller instance is retained.
 */
internal object ClockResolver {

    private const val CONTROLLER_CLASS = "com.android.systemui.statusbar.policy.MiuiStatusBarClockController"
    private const val MIUI_CLOCK_CLASS = "com.android.systemui.statusbar.views.MiuiClock"
    private const val MIUI_STATUS_BAR_CLOCK_CLASS = "com.android.systemui.statusbar.views.MiuiStatusBarClock"

    private const val FIELD_MCALENDAR = "mCalendar"
    private const val FIELD_MCLOCKLISTENERS = "mClockListeners"
    private const val FIELD_MIS24 = "mIs24"
    private const val FIELD_MCONTROLLER = "mMiuiStatusBarClockController"
    private const val METHOD_UPDATE_TIME = "updateTime"
    private const val METHOD_SET_TIME_IN_MILLIS = "setTimeInMillis"
    private const val METHOD_FORMAT = "format"

    /**
     * Resolve the full cold ABI from the Xposed `ClassLoader`.
     *
     * Returns `null` only when the controller class cannot be resolved at all.  A missing cold
     * calendar capability does **not** cause a null return; it is represented by
     * `ClockAbi.calendarCold == null` so that a one-time runtime calibration can still be attempted.
     */
    fun resolveCore(classLoader: ClassLoader): ClockAbi? {
        val controllerClass = XposedHelpers.findClassIfExists(CONTROLLER_CLASS, classLoader) ?: return null
        val controller = resolveControllerClass(controllerClass) ?: return null

        val targetClasses = listOf(
            XposedHelpers.findClassIfExists(MIUI_CLOCK_CLASS, classLoader),
            XposedHelpers.findClassIfExists(MIUI_STATUS_BAR_CLOCK_CLASS, classLoader),
        )
        val targets = targetClasses
            .mapNotNull { it?.let(::resolveClockTargetClass) }
            .toTypedArray()

        val calendarCold = resolveCalendarFromDeclaredType(controller.calendarField.type, Context::class.java)

        return ClockAbi(controller, targets, calendarCold)
    }

    /**
     * Resolve the controller capability deterministically.
     *
     * The `mIs24` field must be a primitive `boolean`; otherwise the capability is rejected.
     */
    fun resolveControllerClass(controllerClass: Class<*>?): ControllerCapability? {
        if (controllerClass == null) return null

        val calendarField = resolveField(controllerClass, FIELD_MCALENDAR) ?: return null
        val clockListenersField = resolveField(controllerClass, FIELD_MCLOCKLISTENERS) ?: return null
        val is24Field = resolveField(controllerClass, FIELD_MIS24) ?: return null

        // mIs24 is a 24-hour flag; only the primitive boolean semantic is accepted.
        if (is24Field.type != Boolean::class.javaPrimitiveType) return null

        return ControllerCapability(controllerClass, calendarField, clockListenersField, is24Field)
    }

    /**
     * Resolve a single clock hook target capability.
     *
     * Each target is resolved independently.  A resolved inherited member is acceptable.
     */
    fun resolveClockTargetClass(targetClass: Class<*>?): ClockTargetCapability? {
        if (targetClass == null) return null

        val controllerField = resolveField(targetClass, FIELD_MCONTROLLER) ?: return null
        val updateTimeMethod = resolveNoArgMethod(targetClass, METHOD_UPDATE_TIME) ?: return null

        return ClockTargetCapability(targetClass, controllerField, updateTimeMethod)
    }

    /**
     * Resolve the calendar capability from the cold declared type of `mCalendar`.
     *
     * @param calendarDeclaredClass the type read from `mCalendar` at install time.
     * @param contextClass the lower bound for the first `format` parameter.  Cold resolution uses
     *   [Context]; runtime calibration may use the actual context class.
     */
    fun resolveCalendarFromDeclaredType(
        calendarDeclaredClass: Class<*>,
        contextClass: Class<*> = Context::class.java,
    ): CalendarCapability? {
        val setTimeInMillisMethod = resolveSetTimeInMillis(calendarDeclaredClass) ?: return null
        val formatMethod = resolveFormat(calendarDeclaredClass, contextClass) ?: return null

        return CalendarCapability(calendarDeclaredClass, setTimeInMillisMethod, formatMethod)
    }

    /**
     * Resolve the calendar capability from a real calendar object and actual context class.
     *
     * This is a one-time, narrow runtime calibration.  Neither the calendar object nor the context
     * is retained; only the resolved [Class] and [Method] references are returned.
     */
    fun resolveCalendarFromRuntime(calendarObject: Any, actualContextClass: Class<*>): CalendarCapability? {
        return resolveCalendarFromDeclaredType(calendarObject.javaClass, actualContextClass)
    }

    /**
     * Deterministic field resolution: walk from [startClass] to superclasses and return the first
     * declared field named [fieldName].  [NoSuchFieldException] is treated as a normal miss; all
     * other reflection errors are unwrapped for fatal propagation.
     */
    internal fun resolveField(startClass: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = startClass
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return null
            }
        }
        return null
    }

    /**
     * Deterministic no-arg method resolution.
     *
     * Walks the class hierarchy from [startClass] upward.  At each level, declared methods matching
     * [methodName] with zero parameters are collected.  Zero candidates continues to the superclass;
     * exactly one candidate is resolved; more than one candidate at the same level is fail-closed.
     * Return type is not used for selection.
     */
    internal fun resolveNoArgMethod(startClass: Class<*>, methodName: String): Method? {
        var current: Class<*>? = startClass
        while (current != null) {
            val candidates = try {
                current.declaredMethods.filter { it.name == methodName && it.parameterCount == 0 }
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return null
            }

            if (candidates.isNotEmpty()) {
                val chosen = selectSingleNoArgCandidate(candidates) ?: return null
                return try {
                    chosen.isAccessible = true
                    chosen
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    null
                }
            }
            current = current.superclass
        }
        return null
    }

    /**
     * Resolve `setTimeInMillis(long)` deterministically.
     *
     * Only the primitive `long` overload is accepted.  Wrapper `Long`, `Object` or any other single
     * parameter type is rejected.  Return type is not an oracle.
     */
    private fun resolveSetTimeInMillis(startClass: Class<*>): Method? {
        val longPrimitive = Long::class.javaPrimitiveType ?: return null
        var current: Class<*>? = startClass

        while (current != null) {
            val candidates = try {
                current.declaredMethods.filter {
                    it.name == METHOD_SET_TIME_IN_MILLIS &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0] == longPrimitive
                }
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return null
            }

            when (candidates.size) {
                0 -> { /* continue to superclass */ }
                1 -> return try {
                    candidates[0].isAccessible = true
                    candidates[0]
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    null
                }
                else -> return null // multiple primitive long overloads at the same level
            }
            current = current.superclass
        }
        return null
    }

    /**
     * Resolve `format` with three parameters compatible with [contextClass] and [StringBuilder].
     *
     * Walks the full usable hierarchy, collecting compatible candidates from every level.  Methods
     * with the same effective parameter signature at multiple levels are normalized: the nearest
     * declaration wins.  Synthetic/bridge artifacts at the same level are only used when no
     * non-synthetic method with the same signature exists.  Superclass private methods are not
     * treated as inherited candidates.  Return type is not an oracle.
     *
     * After normalization, the single most-specific candidate is selected using parameter-specific
     * dominance (A dominates B when every parameter of A is assignable to the corresponding
     * parameter of B and at least one parameter is strictly narrower).  Incomparable candidates are
     * fail-closed.
     */
    private fun resolveFormat(startClass: Class<*>, contextClass: Class<*>): Method? {
        val stringBuilderClass = StringBuilder::class.java
        val collected = ArrayList<Method>()

        var current: Class<*>? = startClass
        while (current != null) {
            val declared = try {
                current.declaredMethods
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return null
            }

            // Two passes: non-synthetic first, then synthetic/bridge.  This prefers the real method
            // over a bridge at the same level without depending on reflection iteration order.
            for (syntheticPass in 0..1) {
                for (method in declared) {
                    val isSynthetic = method.isSynthetic || method.isBridge
                    if (syntheticPass == 0 && isSynthetic) continue
                    if (syntheticPass == 1 && !isSynthetic) continue
                    if (method.name != METHOD_FORMAT || method.parameterCount != 3) continue

                    val pt = method.parameterTypes
                    val compatible = pt[0].isAssignableFrom(contextClass) &&
                        pt[1].isAssignableFrom(stringBuilderClass) &&
                        pt[2].isAssignableFrom(stringBuilderClass)
                    if (!compatible) continue

                    if (current !== startClass && java.lang.reflect.Modifier.isPrivate(method.modifiers)) continue

                    collected.add(method)
                }
            }

            current = current.superclass
        }

        if (collected.isEmpty()) return null

        // Normalize same parameter signatures: nearest (already in collected order) wins.
        val distinct = ArrayList<Method>()
        val seenSignatures = HashSet<List<Class<*>>>()
        for (method in collected) {
            val signature = method.parameterTypes.toList()
            if (seenSignatures.add(signature)) {
                distinct.add(method)
            }
        }

        return selectMostSpecificFormat(distinct)
    }

    private fun selectSingleNoArgCandidate(candidates: List<Method>): Method? {
        val nonSynthetic = ArrayList<Method>()
        for (method in candidates) {
            if (!method.isSynthetic && !method.isBridge) nonSynthetic.add(method)
        }
        return when {
            nonSynthetic.size == 1 -> nonSynthetic[0]
            nonSynthetic.isEmpty() && candidates.size == 1 -> candidates[0]
            else -> null
        }
    }

    private fun selectMostSpecificFormat(candidates: List<Method>): Method? {
        if (candidates.size == 1) return setAccessible(candidates[0])

        // A winner must dominate every other distinct parameter signature.
        // Same-signature normalization already ran, so dominance is strict here.
        var winner: Method? = null

        for (candidate in candidates) {
            var dominatesAll = true

            for (other in candidates) {
                if (candidate === other) continue
                if (!candidate.dominates(other)) {
                    dominatesAll = false
                    break
                }
            }

            if (dominatesAll) {
                if (winner != null) {
                    // two unrelated candidates each dominate all others; malformed
                    return null
                }
                winner = candidate
            }
        }

        return setAccessible(winner)
    }

    private fun Method.dominates(other: Method): Boolean {
        val a = this.parameterTypes
        val b = other.parameterTypes
        var strictlyNarrower = false
        for (i in a.indices) {
            if (!b[i].isAssignableFrom(a[i])) return false
            if (a[i] != b[i]) strictlyNarrower = true
        }
        return strictlyNarrower
    }

    private fun setAccessible(method: Method?): Method? {
        if (method == null) return null
        return try {
            method.isAccessible = true
            method
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }
}
