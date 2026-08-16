package tv.withaibuild.customiuizer.mods.statusbarheight

import android.graphics.Rect
import android.view.WindowInsets
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Cold resolver for the status bar height feature.
 *
 * All reflection and `ClassLoader` discovery happens here during install time.  The output is an
 * immutable [StatusBarHeightAbi] capability object.  Hot paths must not perform any further
 * discovery.
 */
internal object StatusBarHeightResolver {

    private const val INSETS_SOURCE_CLASS = "android.view.InsetsSource"
    private const val INSETS_STATE_CLASS = "android.view.InsetsState"
    private const val WINDOW_STATE_CLASS = "com.android.server.wm.WindowState"
    private const val DISPLAY_POLICY_CLASS = "com.android.server.wm.DisplayPolicy"
    private const val DECOR_INSETS_INFO_CLASS = "com.android.server.wm.DisplayPolicy\$DecorInsets\$Info"
    private const val WINDOW_MANAGER_SERVICE_CLASS = "com.android.server.wm.WindowManagerService"

    private const val SET_FRAME_METHOD = "setFrame"
    private const val GET_TYPE_METHOD = "getType"
    private const val GET_FRAME_METHOD = "getFrame"
    private const val GET_ID_METHOD = "getId"
    private const val SET_FRAMES_METHOD = "setFrames"
    private const val DECOR_INSETS_UPDATE_METHOD = "update"

    /**
     * Resolve the cold core ABI.
     */
    fun resolveCore(classLoader: ClassLoader): StatusBarHeightAbi {
        val insets = resolveInsetsSourceCapability(classLoader)
        val windowManager = resolveWindowManagerCapability(classLoader)
        val decorInsets = resolveDecorInsetsCapability(classLoader)
        val refresh = resolveRefreshCapability(windowManager, decorInsets, classLoader)
        return StatusBarHeightAbi(insets, windowManager, decorInsets, refresh)
    }

    private fun resolveInsetsSourceCapability(classLoader: ClassLoader): InsetsSourceCapability {
        val sourceClass = XposedHelpers.findClassIfExists(INSETS_SOURCE_CLASS, classLoader)
        return resolveInsetsSourceClass(sourceClass, classLoader)
    }

    /**
     * Resolve InsetsSource capability from an already-loaded class.  Used by tests and by [resolveCore].
     */
    fun resolveInsetsSourceClass(sourceClass: Class<*>?, classLoader: ClassLoader): InsetsSourceCapability {
        val setFrameMethods = if (sourceClass != null) {
            try {
                sourceClass.declaredMethods.filter { it.name == SET_FRAME_METHOD }
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                emptyList()
            }
        } else {
            emptyList()
        }

        val setFrameOneArg = setFrameMethods.any { it.parameterTypes.contentEquals(arrayOf(Rect::class.java)) }
        val setFrameFourArg = setFrameMethods.any {
            it.parameterTypes.contentEquals(arrayOf(Int::class.java, Int::class.java, Int::class.java, Int::class.java))
        }

        val sourceAbi = if (sourceClass != null) resolveInsetsSourceAbi(sourceClass, classLoader) else InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = false,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val typeInfo = selectTypeEncoding(sourceAbi)

        val typeField = if (sourceClass != null) resolveIntField(sourceClass, "mType") else null
        val getTypeMethod = if (sourceClass != null && typeField == null) resolveNoArgMethod(sourceClass, GET_TYPE_METHOD) else null
        val getIdMethod = if (sourceClass != null) resolveNoArgMethod(sourceClass, GET_ID_METHOD) else null
        val getFrameMethod = if (sourceClass != null) resolveNoArgMethod(sourceClass, GET_FRAME_METHOD) else null

        return InsetsSourceCapability(
            sourceClass = sourceClass,
            setFrameOneArg = setFrameOneArg,
            setFrameFourArg = setFrameFourArg,
            typeInfo = typeInfo,
            typeField = typeField,
            getTypeMethod = getTypeMethod,
            getIdMethod = getIdMethod,
            getFrameMethod = getFrameMethod,
        )
    }

    private fun resolveInsetsSourceAbi(sourceClass: Class<*>, classLoader: ClassLoader): InsetsSourceAbi {
        val constructors = try {
            sourceClass.declaredConstructors
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            emptyArray()
        }

        val hasOneIntConstructor = constructors.any { it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) }
        val hasIdTypeConstructor = constructors.any {
            it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        }

        val hasGetType = resolveNoArgMethod(sourceClass, GET_TYPE_METHOD) != null
        val hasGetId = resolveNoArgMethod(sourceClass, GET_ID_METHOD) != null

        val publicTypes = if (hasIdTypeConstructor) resolvePublicTypes() else RawTypeInfo(null, null, null)
        val legacyTypes = if (hasOneIntConstructor && !hasIdTypeConstructor) resolveLegacyTypes(classLoader) else RawTypeInfo(null, null, null)

        return InsetsSourceAbi(
            hasOneIntConstructor = hasOneIntConstructor,
            hasIdTypeConstructor = hasIdTypeConstructor,
            hasGetId = hasGetId,
            hasGetType = hasGetType,
            legacyStatusType = legacyTypes.statusBarType,
            legacyNavigationType = legacyTypes.navigationType,
            publicStatusType = publicTypes.statusBarType,
            publicNavigationType = publicTypes.navigationType,
            publicDisplayCutoutType = publicTypes.displayCutoutType,
        )
    }

    /**
     * Select the type encoding.  Parity-locked to the existing oracle until B2 integration.
     */
    fun selectTypeEncoding(abi: InsetsSourceAbi): InsetsTypeInfo {
        if (abi.hasIdTypeConstructor && abi.hasGetId && abi.hasGetType) {
            val status = abi.publicStatusType
            if (status != null && status >= 0) {
                return InsetsTypeInfo(
                    encoding = InsetsTypeEncoding.MODERN_PUBLIC,
                    statusBarType = status,
                    navigationType = abi.publicNavigationType?.takeIf { it >= 0 } ?: -1,
                    displayCutoutType = abi.publicDisplayCutoutType?.takeIf { it >= 0 } ?: -1,
                )
            }
        }

        if (abi.hasOneIntConstructor && !abi.hasIdTypeConstructor && abi.hasGetType) {
            val status = abi.legacyStatusType
            val nav = abi.legacyNavigationType
            if (status != null && status >= 0 && nav != null && nav >= 0) {
                return InsetsTypeInfo(
                    encoding = InsetsTypeEncoding.LEGACY_INTERNAL,
                    statusBarType = status,
                    navigationType = nav,
                    displayCutoutType = -1,
                )
            }
        }

        return InsetsTypeInfo(
            encoding = InsetsTypeEncoding.UNSUPPORTED,
            statusBarType = InsetsTypeInfo.TYPE_UNRESOLVED,
            navigationType = -1,
            displayCutoutType = -1,
        )
    }

    private fun resolvePublicTypes(): RawTypeInfo {
        return RawTypeInfo(
            statusBarType = safePublicType { WindowInsets.Type.statusBars() },
            navigationType = safePublicType { WindowInsets.Type.navigationBars() },
            displayCutoutType = safePublicType { WindowInsets.Type.displayCutout() },
        )
    }

    private fun resolveLegacyTypes(classLoader: ClassLoader): RawTypeInfo {
        val stateClass = XposedHelpers.findClassIfExists(INSETS_STATE_CLASS, classLoader)
            ?: return RawTypeInfo(null, null, null)
        return RawTypeInfo(
            statusBarType = getStaticInt(stateClass, "ITYPE_STATUS_BAR"),
            navigationType = getStaticInt(stateClass, "ITYPE_NAVIGATION_BAR"),
            displayCutoutType = getStaticInt(stateClass, "ITYPE_DISPLAY_CUTOUT"),
        )
    }

    private fun resolveWindowManagerCapability(classLoader: ClassLoader): WindowManagerCapability {
        val windowStateClass = XposedHelpers.findClassIfExists(WINDOW_STATE_CLASS, classLoader)
        val displayPolicyClass = XposedHelpers.findClassIfExists(DISPLAY_POLICY_CLASS, classLoader)
        val lpClass = findLayoutParamsClass(classLoader)

        val clientFrames = if (windowStateClass != null) resolveClientWindowFrames(windowStateClass) else null

        val windowFramesClass = if (windowStateClass != null) {
            resolveDeclaredField(windowStateClass, "mWindowFrames")?.type
        } else null
        val windowFramesFrameField = if (windowFramesClass != null) resolveDeclaredField(windowFramesClass, "mFrame") else null

        return WindowManagerCapability(
            windowStateClass = windowStateClass,
            displayPolicyClass = displayPolicyClass,
            windowStateAttrsField = if (windowStateClass != null) resolveDeclaredField(windowStateClass, "mAttrs") else null,
            windowStateDisplayContentField = if (windowStateClass != null) resolveDeclaredField(windowStateClass, "mDisplayContent") else null,
            windowStateWindowManagerServiceField = if (windowStateClass != null) resolveDeclaredField(windowStateClass, "mWmService") else null,
            windowStateGetFrameMethod = if (windowStateClass != null) resolveNoArgMethod(windowStateClass, GET_FRAME_METHOD) else null,
            windowStateGetDisplayMetricsMethod = if (windowStateClass != null) resolveNoArgMethod(windowStateClass, "getDisplayMetrics") else null,
            windowStateGetDisplayIdMethod = if (windowStateClass != null) resolveNoArgMethod(windowStateClass, "getDisplayId") else null,
            windowStateWindowFramesField = if (windowStateClass != null) resolveDeclaredField(windowStateClass, "mWindowFrames") else null,
            windowFramesFrameField = windowFramesFrameField,
            clientWindowFramesClass = clientFrames?.first,
            clientWindowFramesFrameField = clientFrames?.second,
            layoutParamsClass = lpClass,
            layoutParamsTypeField = if (lpClass != null) resolveDeclaredField(lpClass, "type") else null,
            layoutParamsHeightField = if (lpClass != null) resolveDeclaredField(lpClass, "height") else null,
            layoutParamsPackageNameField = if (lpClass != null) resolveDeclaredField(lpClass, "packageName") else null,
        )
    }

    /**
     * Resolve WindowManager capability from already-loaded classes.  Used by tests and by [resolveCore].
     */
    fun resolveWindowManagerClass(windowStateClass: Class<*>?, lpClass: Class<*>?): WindowManagerCapability {
        val displayPolicyClass: Class<*>? = null
        val clientFrames = if (windowStateClass != null) resolveClientWindowFrames(windowStateClass) else null
        val windowFramesClass = if (windowStateClass != null) {
            resolveDeclaredField(windowStateClass, "mWindowFrames")?.type
        } else null
        val windowFramesFrameField = if (windowFramesClass != null) resolveDeclaredField(windowFramesClass, "mFrame") else null

        return WindowManagerCapability(
            windowStateClass = windowStateClass,
            displayPolicyClass = displayPolicyClass,
            windowStateAttrsField = if (windowStateClass != null) resolveDeclaredField(windowStateClass, "mAttrs") else null,
            windowStateDisplayContentField = if (windowStateClass != null) resolveDeclaredField(windowStateClass, "mDisplayContent") else null,
            windowStateWindowManagerServiceField = if (windowStateClass != null) resolveDeclaredField(windowStateClass, "mWmService") else null,
            windowStateGetFrameMethod = if (windowStateClass != null) resolveNoArgMethod(windowStateClass, GET_FRAME_METHOD) else null,
            windowStateGetDisplayMetricsMethod = if (windowStateClass != null) resolveNoArgMethod(windowStateClass, "getDisplayMetrics") else null,
            windowStateGetDisplayIdMethod = if (windowStateClass != null) resolveNoArgMethod(windowStateClass, "getDisplayId") else null,
            windowStateWindowFramesField = if (windowStateClass != null) resolveDeclaredField(windowStateClass, "mWindowFrames") else null,
            windowFramesFrameField = windowFramesFrameField,
            clientWindowFramesClass = clientFrames?.first,
            clientWindowFramesFrameField = clientFrames?.second,
            layoutParamsClass = lpClass,
            layoutParamsTypeField = if (lpClass != null) resolveDeclaredField(lpClass, "type") else null,
            layoutParamsHeightField = if (lpClass != null) resolveDeclaredField(lpClass, "height") else null,
            layoutParamsPackageNameField = if (lpClass != null) resolveDeclaredField(lpClass, "packageName") else null,
        )
    }

    private fun resolveDecorInsetsCapability(classLoader: ClassLoader): DecorInsetsCapability {
        val infoClass = XposedHelpers.findClassIfExists(DECOR_INSETS_INFO_CLASS, classLoader)
        val displayContentClass = XposedHelpers.findClassIfExists("com.android.server.wm.DisplayContent", classLoader)
        return resolveDecorInsetsInfoClass(infoClass, displayContentClass)
    }

    /**
     * Resolve DecorInsets.Info capability from already-loaded classes.  Used by tests and by [resolveCore].
     *
     * @param infoClass the DecorInsets.Info class, or null if not found.
     * @param displayContentClass the DisplayContent class to use for exact update method matching,
     *                            or null if not found.
     */
    fun resolveDecorInsetsInfoClass(infoClass: Class<*>?, displayContentClass: Class<*>?): DecorInsetsCapability {
        val updateMethod = if (infoClass != null && displayContentClass != null) {
            resolveDecorUpdateMethod(infoClass, displayContentClass)
        } else null

        val displayContentGetDisplayMetricsMethod = if (displayContentClass != null) {
            resolveNoArgMethod(displayContentClass, "getDisplayMetrics")
        } else null

        return DecorInsetsCapability(
            infoClass = infoClass,
            updateMethod = updateMethod,
            displayContentClass = displayContentClass,
            displayContentGetDisplayMetricsMethod = displayContentGetDisplayMetricsMethod,
            nonDecorInsetsField = if (infoClass != null) resolveDeclaredField(infoClass, "mNonDecorInsets") else null,
            nonDecorFrameField = if (infoClass != null) resolveDeclaredField(infoClass, "mNonDecorFrame") else null,
        )
    }

    private fun resolveDecorUpdateMethod(infoClass: Class<*>, displayContentClass: Class<*>): Method? {
        val candidates = try {
            infoClass.declaredMethods.filter { it.name == DECOR_INSETS_UPDATE_METHOD }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            emptyList()
        }

        val exact = candidates.filter { method ->
            val pt = method.parameterTypes
            pt.size == 4 &&
                pt[0] == displayContentClass &&
                pt[1] == Int::class.javaPrimitiveType &&
                pt[2] == Int::class.javaPrimitiveType &&
                pt[3] == Int::class.javaPrimitiveType
        }

        // Deterministic: require exactly one matching overload.
        if (exact.size != 1) return null

        return try {
            val chosen = exact[0]
            chosen.isAccessible = true
            chosen
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    private fun resolveClientWindowFrames(windowStateClass: Class<*>): Pair<Class<*>?, Field?>? {
        val setFramesMethods = try {
            windowStateClass.declaredMethods.filter { it.name == SET_FRAMES_METHOD }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            emptyList()
        }

        // Collect all overloads whose first parameter is named ClientWindowFrames,
        // then use the single distinct class. Fail closed on zero or multiple types.
        val candidateClasses = setFramesMethods
            .mapNotNull { method ->
                if (method.parameterTypes.isEmpty()) null
                else if (method.parameterTypes[0].simpleName != "ClientWindowFrames") null
                else method.parameterTypes[0]
            }
            .distinct()

        if (candidateClasses.size != 1) return null

        val clazz = candidateClasses[0]
        val frameField = resolveDeclaredField(clazz, "frame")
        return clazz to frameField
    }

    private fun findLayoutParamsClass(classLoader: ClassLoader): Class<*>? {
        return try {
            XposedHelpers.findClassIfExists("android.view.WindowManager\$LayoutParams", classLoader)
                ?: Class.forName("android.view.WindowManager\$LayoutParams")
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    // ----------------------------------------------------------------------------
    // Refresh capability.
    // ----------------------------------------------------------------------------

    /**
     * Resolve the refresh/traversal cold ABI.
     *
     * Sources are best-effort: existing resolved classes, then string-based class lookup,
     * then field types.  Each member is resolved independently so partial refresh paths are
     * available even when the other half is missing.
     */
    fun resolveRefreshCapability(
        windowManager: WindowManagerCapability,
        decorInsets: DecorInsetsCapability,
        classLoader: ClassLoader,
    ): StatusBarHeightRefreshCapability {
        val displayContentClass = decorInsets.displayContentClass
            ?: windowManager.windowStateDisplayContentField?.type

        val displayPolicyClass = windowManager.displayPolicyClass

        val windowManagerServiceClass = XposedHelpers.findClassIfExists(WINDOW_MANAGER_SERVICE_CLASS, classLoader)
            ?: windowManager.windowStateWindowManagerServiceField?.type

        val decorInsetsField = if (displayPolicyClass != null) {
            resolveDeclaredField(displayPolicyClass, "mDecorInsets")
        } else null

        val decorInsetsClass = decorInsetsField?.type

        val placerField = if (windowManagerServiceClass != null) {
            resolveDeclaredField(windowManagerServiceClass, "mWindowPlacerLocked")
        } else null

        val placerClass = placerField?.type

        return StatusBarHeightRefreshCapability(
            windowManagerServicePlacerField = placerField,
            windowSurfacePlacerRequestTraversalMethod = if (placerClass != null) resolveNoArgMethod(placerClass, "requestTraversal") else null,
            displayContentGetDisplayPolicyMethod = if (displayContentClass != null) resolveNoArgMethod(displayContentClass, "getDisplayPolicy") else null,
            displayPolicyDecorInsetsField = decorInsetsField,
            decorInsetsInvalidateMethod = if (decorInsetsClass != null) resolveNoArgMethod(decorInsetsClass, "invalidate") else null,
        )
    }

    // ----------------------------------------------------------------------------
    // Low-level reflection helpers (cold only).
    // ----------------------------------------------------------------------------

    /**
     * Resolve a public no-arg method on [clazz] or its superclasses.
     * Deterministic and fail-closed: ambiguous candidates return null.
     */
    fun resolveNoArgMethod(clazz: Class<*>, methodName: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            val candidates = try {
                current.declaredMethods.filter { it.name == methodName && it.parameterTypes.isEmpty() }
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return null
            }

            if (candidates.isNotEmpty()) {
                val chosen = selectDeterministicNoArgMethod(candidates) ?: return null
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

    private fun selectDeterministicNoArgMethod(candidates: List<Method>): Method? {
        val nonSynthetic = candidates.filter { !it.isSynthetic && !it.isBridge }
        return when {
            nonSynthetic.size == 1 -> nonSynthetic[0]
            nonSynthetic.isEmpty() && candidates.size == 1 -> candidates[0]
            else -> null
        }
    }

    fun resolveDeclaredField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val field = current.getDeclaredField(name)
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

    fun resolveIntField(clazz: Class<*>?, name: String): Field? {
        if (clazz == null) return null
        val field = resolveDeclaredField(clazz, name) ?: return null
        return if (field.type == Int::class.javaPrimitiveType || field.type == Int::class.java) field else null
    }

    private fun getStaticInt(clazz: Class<*>, fieldName: String): Int? {
        return try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            val value = field.get(null)
            if (value is Int) value else null
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    private fun safePublicType(block: () -> Int): Int? {
        return try {
            block()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }
}
