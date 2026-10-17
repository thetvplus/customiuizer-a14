package tv.withaibuild.customiuizer.mods.statusbarheight

import java.lang.reflect.Field
import java.lang.reflect.Method

/** Which type encoding the ROM uses for `android.view.InsetsSource.getType()`. */
internal enum class InsetsTypeEncoding {
    MODERN_PUBLIC,
    LEGACY_INTERNAL,
    UNSUPPORTED,
}

/**
 * Resolved type encoding constants.
 */
internal data class InsetsTypeInfo(
    val encoding: InsetsTypeEncoding,
    val statusBarType: Int,
    val navigationType: Int,
    val displayCutoutType: Int,
) {
    val isSupported: Boolean get() = encoding != InsetsTypeEncoding.UNSUPPORTED

    companion object {
        const val TYPE_UNRESOLVED = Int.MIN_VALUE
    }
}

/**
 * Cold-path description of `android.view.InsetsSource` constructors/methods.
 * Used to build [InsetsTypeInfo] and not retained at runtime.
 */
internal data class InsetsSourceAbi(
    val hasOneIntConstructor: Boolean,
    val hasIdTypeConstructor: Boolean,
    val hasGetId: Boolean,
    val hasGetType: Boolean,
    val legacyStatusType: Int?,
    val legacyNavigationType: Int?,
    val publicStatusType: Int?,
    val publicNavigationType: Int?,
    val publicDisplayCutoutType: Int?,
)

/** Raw public/legacy type values before normalization. */
internal data class RawTypeInfo(
    val statusBarType: Int?,
    val navigationType: Int?,
    val displayCutoutType: Int?,
)

/**
 * Immutable capability for the `android.view.InsetsSource` hot path.
 *
 * @property sourceClass the InsetsSource class; null if not found.
 * @property setFrameOneArg true if `setFrame(Rect)` exists.
 * @property setFrameFourArg true if `setFrame(int,int,int,int)` exists.
 * @property typeInfo resolved type encoding.
 * @property typeField preferred `mType` field; null if unavailable.
 * @property getTypeMethod fallback `getType()` method; null if unavailable.
 * @property getIdMethod resolved `getId()` method; null if unavailable.
 * @property getFrameMethod resolved `getFrame()` method; null if unavailable.
 */
internal data class InsetsSourceCapability(
    val sourceClass: Class<*>?,
    val setFrameOneArg: Boolean,
    val setFrameFourArg: Boolean,
    val typeInfo: InsetsTypeInfo,
    val typeField: Field?,
    val getTypeMethod: Method?,
    val getIdMethod: Method?,
    val getFrameMethod: Method?,
) {
    val canReadType: Boolean get() = typeField != null || getTypeMethod != null

    val coreSupported: Boolean
        get() = sourceClass != null &&
            typeInfo.isSupported &&
            (setFrameOneArg || setFrameFourArg) &&
            canReadType
}

/**
 * Immutable capability for the WindowManager / WindowState path.
 */
internal data class WindowManagerCapability(
    val windowStateClass: Class<*>?,
    val displayPolicyClass: Class<*>?,
    val windowStateAttrsField: Field?,
    val windowStateDisplayContentField: Field?,
    val windowStateWindowManagerServiceField: Field?,
    val windowStateGetFrameMethod: Method?,
    val windowStateGetDisplayMetricsMethod: Method?,
    val windowStateGetDisplayIdMethod: Method?,
    val windowStateWindowFramesField: Field?,
    val windowFramesFrameField: Field?,
    val clientWindowFramesClass: Class<*>?,
    val clientWindowFramesFrameField: Field?,
    val layoutParamsClass: Class<*>?,
    val layoutParamsTypeField: Field?,
    val layoutParamsHeightField: Field?,
    val layoutParamsPackageNameField: Field?,
)

/**
 * Immutable capability for DisplayPolicy.DecorInsets.Info.
 */
internal data class DecorInsetsCapability(
    val infoClass: Class<*>?,
    val updateMethod: Method?,
    val displayContentClass: Class<*>?,
    val displayContentGetDisplayMetricsMethod: Method?,
    val nonDecorInsetsField: Field?,
    val nonDecorFrameField: Field?,
)

/**
 * Immutable capability for the preference-change refresh / traversal path.
 *
 * Each member is nullable.  `null` means resolved-unavailable or not provided by this ROM.
 * Partial capability is intentional: traversal and decor-invalidation are independent.
 */
internal data class StatusBarHeightRefreshCapability(
    val windowManagerServicePlacerField: Field?,
    val windowSurfacePlacerRequestTraversalMethod: Method?,
    val displayContentGetDisplayPolicyMethod: Method?,
    val displayPolicyDecorInsetsField: Field?,
    val decorInsetsInvalidateMethod: Method?,
) {
    val canRequestTraversal: Boolean
        get() = windowManagerServicePlacerField != null &&
            windowSurfacePlacerRequestTraversalMethod != null

    val canInvalidateDecorInsets: Boolean
        get() = displayContentGetDisplayPolicyMethod != null &&
            displayPolicyDecorInsetsField != null &&
            decorInsetsInvalidateMethod != null
}

/**
 * Aggregate cold-resolved ABI for the status bar height feature.
 */
internal data class StatusBarHeightAbi(
    val insets: InsetsSourceCapability,
    val windowManager: WindowManagerCapability,
    val decorInsets: DecorInsetsCapability,
    val refresh: StatusBarHeightRefreshCapability,
)
