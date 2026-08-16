package tv.withaibuild.customiuizer.mods.utils.feature

import android.view.View
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * Adapts HyperOS StrongToast control / view boundaries into a [DynamicIslandEvent].
 *
 * Type comes only from the hook entry that observed the event (charging battery path vs
 * custom StrongToast show) or from an explicit ROM category field when one exists.
 * Text, drawable names and View class names are never used as the primary classifier.
 */
internal object DynamicIslandEventAdapter {

    /** Charging path observed at [MIUIStrongToastControl$6.onRefreshBatteryInfo]. */
    const val SOURCE_CHARGING_BATTERY = "charging_battery"

    /** Generic show path observed at [MIUIStrongToastControl.showCustomStrongToast]. */
    const val SOURCE_CUSTOM_SHOW = "custom_show"

    /**
     * Builds an event from a StrongToast View plus the current immutable config snapshot.
     *
     * @param sourceHint Hook-entry identity. Prefer this over reflecting into the View.
     */
    @JvmStatic
    fun fromStrongToast(
        strongToast: Any,
        config: StrongToastRuntimeSnapshot,
        sourceHint: String? = null,
        durationMs: Long = DynamicIslandEvent.DEFAULT_DURATION_MS,
    ): DynamicIslandEvent {
        val type = resolveType(strongToast, sourceHint)
        return DynamicIslandEvent(
            type = type,
            sourceToken = strongToast,
            config = config,
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    @JvmStatic
    fun resolveType(strongToast: Any?, sourceHint: String?): DynamicIslandEventType {
        when (sourceHint) {
            SOURCE_CHARGING_BATTERY -> return DynamicIslandEventType.CHARGING
            SOURCE_CUSTOM_SHOW -> {
                val fromRom = readRomCategory(strongToast)
                if (fromRom != null) return fromRom
                return DynamicIslandEventType.OTHER
            }
        }
        val fromRom = readRomCategory(strongToast)
        return fromRom ?: DynamicIslandEventType.OTHER
    }

    /**
     * Best-effort ROM category read. Fail-open to null so the shared renderer still runs.
     * Known HyperOS fields are checked by name only; no string-content inspection.
     */
    private fun readRomCategory(strongToast: Any?): DynamicIslandEventType? {
        if (strongToast == null) return null
        return try {
            val category = readCategoryValue(strongToast) ?: return null
            mapCategoryValue(category)
        } catch (t: Exception) {
            null
        }
    }

    private fun readCategoryValue(strongToast: Any): Any? {
        val fieldNames = arrayOf(
            "mStrongToastCategory",
            "mToastCategory",
            "mCategory",
            "mStrongToastType",
            "mType",
        )
        for (name in fieldNames) {
            val value = try {
                XposedHelpers.getObjectField(strongToast, name)
            } catch (_: Exception) {
                null
            }
            if (value != null) return value
        }
        val methodNames = arrayOf("getStrongToastCategory", "getToastCategory", "getCategory", "getType")
        for (name in methodNames) {
            val value = try {
                XposedHelpers.callMethod(strongToast, name)
            } catch (_: Exception) {
                null
            }
            if (value != null) return value
        }
        return null
    }

    private fun mapCategoryValue(value: Any): DynamicIslandEventType? {
        when (value) {
            is Number -> return when (value.toInt()) {
                // HyperOS StrongToast category ordinals observed on A14 ROM builds.
                // Unknown ordinals stay OTHER so the shared path still renders.
                1 -> DynamicIslandEventType.MUTE
                2 -> DynamicIslandEventType.DND
                3 -> DynamicIslandEventType.CHARGING
                else -> DynamicIslandEventType.OTHER
            }
            is Enum<*> -> {
                val name = value.name.uppercase()
                return when {
                    name.contains("MUTE") || name.contains("SILENT") || name.contains("RINGER") ->
                        DynamicIslandEventType.MUTE
                    name.contains("DND") || name.contains("ZEN") || name.contains("DISTURB") ->
                        DynamicIslandEventType.DND
                    name.contains("CHARGE") || name.contains("BATTERY") ->
                        DynamicIslandEventType.CHARGING
                    else -> DynamicIslandEventType.OTHER
                }
            }
            else -> return null
        }
    }

    /** Proves that every supported type resolves to the same shared renderer token. */
    @JvmStatic
    fun sharedRendererTokenFor(type: DynamicIslandEventType): String {
        // Intentionally ignore type: one host for all StrongToast island events.
        return DynamicIslandEvent.SHARED_RENDERER_TOKEN
    }
}
