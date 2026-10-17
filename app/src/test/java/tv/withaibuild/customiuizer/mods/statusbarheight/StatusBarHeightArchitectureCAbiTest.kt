package tv.withaibuild.customiuizer.mods.statusbarheight

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarHeightArchitectureCAbiTest {

    @Test
    fun typeEncoding_modernBothConstructorsGetIdGetTypeWithPublicStatus_selectsModernPublic() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = 1,
            publicStatusType = 1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.MODERN_PUBLIC, info.encoding)
        assertEquals(1, info.statusBarType)
        assertEquals(2, info.navigationType)
        assertEquals(128, info.displayCutoutType)
    }

    @Test
    fun typeEncoding_modernNoGetId_unsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = true,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = 1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun typeEncoding_modernNoGetType_unsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = false,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = 1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun typeEncoding_modernPublicStatusNegative_unsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = true,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = -1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun typeEncoding_legacyValid_selectsLegacyInternal() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = 1,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.LEGACY_INTERNAL, info.encoding)
        assertEquals(0, info.statusBarType)
        assertEquals(1, info.navigationType)
        assertEquals(-1, info.displayCutoutType)
    }

    @Test
    fun typeEncoding_legacyMissingNav_unsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = null,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun typeEncoding_legacyNegativeStatus_unsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = -1,
            legacyNavigationType = 1,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun resolveNoArgMethod_traversesSuperclass() {
        val method = StatusBarHeightResolver.resolveNoArgMethod(FakeInsetsSourceChild::class.java, "getType")

        assertNotNull(method)
        assertEquals("getType", method!!.name)
    }

    @Test
    fun resolveNoArgMethod_deterministicNonSynthetic() {
        val method = StatusBarHeightResolver.resolveNoArgMethod(FakeInsetsSource::class.java, "getType")

        assertNotNull(method)
        assertFalse(method!!.isSynthetic)
    }

    @Test
    fun insetsCapability_typeFieldPreferred_overGetTypeMethod() {
        val cap = StatusBarHeightResolver.resolveInsetsSourceClass(FakeInsetsSource::class.java, javaClass.classLoader!!)

        assertNotNull(cap.typeField)
        assertNull(cap.getTypeMethod)
        assertTrue(cap.canReadType)
        assertFalse(cap.coreSupported) // type encoding unsupported for fake class
    }

    @Test
    fun insetsCapability_mTypeMissing_fallsBackToGetTypeMethod() {
        val cap = StatusBarHeightResolver.resolveInsetsSourceClass(FakeInsetsSourceNoField::class.java, javaClass.classLoader!!)

        assertNull(cap.typeField)
        assertNotNull(cap.getTypeMethod)
        assertTrue(cap.canReadType)
    }

    @Test
    fun insetsCapability_noMTypeAndNoGetType_cannotReadType() {
        val cap = StatusBarHeightResolver.resolveInsetsSourceClass(FakeInsetsSourceNoAccess::class.java, javaClass.classLoader!!)

        assertNull(cap.typeField)
        assertNull(cap.getTypeMethod)
        assertFalse(cap.canReadType)
        assertFalse(cap.coreSupported)
    }

    @Test
    fun insetsCapability_getIdAndGetFrameFrozenAsMethods() {
        val cap = StatusBarHeightResolver.resolveInsetsSourceClass(FakeInsetsSourceNoField::class.java, javaClass.classLoader!!)

        assertNotNull(cap.getIdMethod)
        assertNotNull(cap.getFrameMethod)
    }

    @Test
    fun resolveCore_onSystemClassLoader_returnsFailClosedCapabilities() {
        val abi = StatusBarHeightResolver.resolveCore(javaClass.classLoader!!)

        assertFalse(abi.insets.coreSupported)
        assertEquals(InsetsTypeEncoding.UNSUPPORTED, abi.insets.typeInfo.encoding)
        assertNull(abi.decorInsets.infoClass)
    }

    @Test
    fun windowManagerCapability_coreFieldsResolved() {
        val cap = StatusBarHeightResolver.resolveWindowManagerClass(FakeWmsWithClientFrames::class.java, FakeLayoutParams::class.java)

        assertNotNull(cap.windowStateClass)
        assertNotNull(cap.windowStateAttrsField)
        assertNotNull(cap.windowStateDisplayContentField)
        assertNotNull(cap.windowStateWindowManagerServiceField)
        assertNotNull(cap.windowStateGetFrameMethod)
        assertNotNull(cap.windowStateGetDisplayMetricsMethod)
        assertNotNull(cap.windowStateGetDisplayIdMethod)
        assertNotNull(cap.windowStateWindowFramesField)
        assertNotNull(cap.windowFramesFrameField)
        assertNotNull(cap.clientWindowFramesClass)
        assertNotNull(cap.clientWindowFramesFrameField)
        assertNotNull(cap.layoutParamsTypeField)
        assertNotNull(cap.layoutParamsHeightField)
        assertNotNull(cap.layoutParamsPackageNameField)
    }

    @Test
    fun windowManagerCapability_missingClientFramesClass_gracefullyNull() {
        val cap = StatusBarHeightResolver.resolveWindowManagerClass(FakeWindowStateNoClientFrames::class.java, FakeLayoutParams::class.java)

        assertNotNull(cap.windowStateClass)
        assertNull(cap.clientWindowFramesClass)
        assertNull(cap.clientWindowFramesFrameField)
    }

    @Test
    fun windowManagerCapability_windowFrames_mFrame_resolvedFromFieldType() {
        val cap = StatusBarHeightResolver.resolveWindowManagerClass(FakeWmsWithClientFrames::class.java, FakeLayoutParams::class.java)

        assertNotNull(cap.windowStateWindowFramesField)
        assertNotNull(cap.windowFramesFrameField)
        assertEquals(Rect::class.java, cap.windowFramesFrameField?.type)
    }

    @Test
    fun decorInsetsCapability_exactUpdateMethod_accepted() {
        val cap = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            FakeDecorInsetsInfo::class.java,
            FakeDisplayContent::class.java,
        )

        assertNotNull(cap.infoClass)
        assertNotNull(cap.updateMethod)
        assertNotNull(cap.displayContentClass)
        assertNotNull(cap.displayContentGetDisplayMetricsMethod)
        assertNotNull(cap.nonDecorInsetsField)
        assertNotNull(cap.nonDecorFrameField)
    }

    @Test
    fun decorInsetsCapability_wrongArity_updateMethodNull() {
        val cap = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            FakeDecorInsetsInfoWrongArity::class.java,
            FakeDisplayContent::class.java,
        )

        assertNull(cap.updateMethod)
    }

    @Test
    fun decorInsetsCapability_wrongFirstParameter_updateMethodNull() {
        val cap = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            FakeDecorInsetsInfoWrongFirstParam::class.java,
            FakeDisplayContent::class.java,
        )

        assertNull(cap.updateMethod)
    }

    @Test
    fun decorInsetsCapability_multipleOverloads_exactSelected() {
        val cap = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            FakeDecorInsetsInfoWithOverloads::class.java,
            FakeDisplayContent::class.java,
        )

        assertNotNull(cap.updateMethod)
        assertEquals(4, cap.updateMethod?.parameterTypes?.size)
        assertEquals(FakeDisplayContent::class.java, cap.updateMethod?.parameterTypes?.get(0))
    }

    @Test
    fun decorInsetsCapability_multipleWrongOverloads_updateMethodNull() {
        val cap = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            FakeDecorInsetsInfoWithWrongOverloads::class.java,
            FakeDisplayContent::class.java,
        )

        assertNull(cap.updateMethod)
    }

    private fun wmWithPolicy(windowStateClass: Class<*>): WindowManagerCapability {
        return StatusBarHeightResolver.resolveWindowManagerClass(
            windowStateClass,
            FakeLayoutParams::class.java,
        ).copy(displayPolicyClass = FakeDisplayPolicy::class.java)
    }

    @Test
    fun refreshCapability_fullFakes_resolvesAllMembers() {
        val wm = wmWithPolicy(FakeWindowStateBase::class.java)
        val decor = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            FakeDecorInsetsInfo::class.java,
            FakeDisplayContent::class.java,
        )

        val refresh = StatusBarHeightResolver.resolveRefreshCapability(
            wm,
            decor,
            javaClass.classLoader!!,
        )

        assertNotNull(refresh.windowManagerServicePlacerField)
        assertNotNull(refresh.windowSurfacePlacerRequestTraversalMethod)
        assertNotNull(refresh.displayContentGetDisplayPolicyMethod)
        assertNotNull(refresh.displayPolicyDecorInsetsField)
        assertNotNull(refresh.decorInsetsInvalidateMethod)
    }

    @Test
    fun refreshCapability_windowManagerServiceMissing_traversalUnavailableOthersIntact() {
        val wm = wmWithPolicy(FakeWindowStateNoWmService::class.java)
        val decor = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            FakeDecorInsetsInfo::class.java,
            FakeDisplayContent::class.java,
        )

        val refresh = StatusBarHeightResolver.resolveRefreshCapability(
            wm,
            decor,
            javaClass.classLoader!!,
        )

        assertNull(refresh.windowManagerServicePlacerField)
        assertNull(refresh.windowSurfacePlacerRequestTraversalMethod)
        assertNotNull(refresh.displayContentGetDisplayPolicyMethod)
        assertNotNull(refresh.displayPolicyDecorInsetsField)
        assertNotNull(refresh.decorInsetsInvalidateMethod)
    }

    @Test
    fun refreshCapability_displayContentMissing_invalidationUnavailableOthersIntact() {
        val wm = wmWithPolicy(FakeWindowStateNoDisplayContent::class.java)
        val decor = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            FakeDecorInsetsInfo::class.java,
            null,
        )

        val refresh = StatusBarHeightResolver.resolveRefreshCapability(
            wm,
            decor,
            javaClass.classLoader!!,
        )

        assertNotNull(refresh.windowManagerServicePlacerField)
        assertNotNull(refresh.windowSurfacePlacerRequestTraversalMethod)
        assertNull(refresh.displayContentGetDisplayPolicyMethod)
        assertFalse(refresh.canInvalidateDecorInsets)
    }

    @Test
    fun refreshCapability_mDecorInsetsFieldType_isInvalidateClassAuthority() {
        val wm = wmWithPolicy(FakeWindowStateBase::class.java)
        val decor = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            FakeDecorInsetsInfo::class.java,
            FakeDisplayContent::class.java,
        )

        val refresh = StatusBarHeightResolver.resolveRefreshCapability(
            wm,
            decor,
            javaClass.classLoader!!,
        )

        val decorInsetsField = refresh.displayPolicyDecorInsetsField
        assertNotNull(decorInsetsField)
        assertSame(FakeDecorInsets::class.java, decorInsetsField?.type)
    }

    // ------------------------------------------------------------------------
    // Fake framework classes for cold resolver unit tests.
    // ------------------------------------------------------------------------

    open class FakeInsetsSourceBase {
        var mType: Int = 0
        fun getType(): Int = mType
    }

    class FakeInsetsSourceChild : FakeInsetsSourceBase()

    class FakeInsetsSource : FakeInsetsSourceBase() {
        fun getId(): Int = 1
        fun getFrame(): Rect = Rect()
        fun setFrame(rect: Rect) {}
        fun setFrame(left: Int, top: Int, right: Int, bottom: Int) {}
    }

    class FakeInsetsSourceNoField {
        var mName: String = "name"
        fun getId(): Int = 1
        fun getType(): Int = 1
        fun getFrame(): Rect = Rect()
        fun setFrame(rect: Rect) {}
    }

    class FakeInsetsSourceNoAccess {
        fun setFrame(rect: Rect) {}
    }

    class ClientWindowFrames {
        @JvmField
        val frame: Rect = Rect()
    }

    class WindowFrames {
        @JvmField
        val mFrame: Rect = Rect()
    }

    open class FakeWindowStateBase {
        var mAttrs: FakeLayoutParams = FakeLayoutParams()
        var mDisplayContent: FakeDisplayContent = FakeDisplayContent()
        var mWmService: FakeWindowManagerService = FakeWindowManagerService()
        var mWindowFrames: WindowFrames = WindowFrames()
        fun getFrame(): Rect = Rect()
        fun getDisplayMetrics(): Any = Any()
        fun getDisplayId(): Int = 0
    }

    class FakeWmsWithClientFrames : FakeWindowStateBase() {
        fun setFrames(frames: ClientWindowFrames, something: Any?, something2: Any?) {}
    }

    class FakeWindowStateNoClientFrames : FakeWindowStateBase()

    class FakeWindowStateNoWmService {
        var mAttrs: FakeLayoutParams = FakeLayoutParams()
        var mDisplayContent: FakeDisplayContent = FakeDisplayContent()
        var mWindowFrames: WindowFrames = WindowFrames()
        fun getFrame(): Rect = Rect()
        fun getDisplayMetrics(): Any = Any()
        fun getDisplayId(): Int = 0
    }

    class FakeWindowStateNoDisplayContent {
        var mAttrs: FakeLayoutParams = FakeLayoutParams()
        var mWmService: FakeWindowManagerService = FakeWindowManagerService()
        var mWindowFrames: WindowFrames = WindowFrames()
        fun getFrame(): Rect = Rect()
        fun getDisplayMetrics(): Any = Any()
        fun getDisplayId(): Int = 0
    }

    class FakeLayoutParams {
        @JvmField
        var type: Int = 0

        @JvmField
        var height: Int = 0

        @JvmField
        var packageName: String? = null
    }

    class FakeDisplayContent {
        private val policy: FakeDisplayPolicy = FakeDisplayPolicy()

        fun getDisplayMetrics(): Any = Any()

        fun getDisplayPolicy(): FakeDisplayPolicy = policy
    }

    class FakeDecorInsetsInfo {
        @JvmField
        var mNonDecorInsets: Rect = Rect()

        @JvmField
        var mNonDecorFrame: Rect = Rect()

        fun update(content: FakeDisplayContent, rotation: Int, displayW: Int, displayH: Int) {}
    }

    class FakeDecorInsetsInfoWrongArity {
        @JvmField
        var mNonDecorInsets: Rect = Rect()

        @JvmField
        var mNonDecorFrame: Rect = Rect()

        fun update(content: FakeDisplayContent) {}
    }

    class FakeDecorInsetsInfoWrongFirstParam {
        @JvmField
        var mNonDecorInsets: Rect = Rect()

        @JvmField
        var mNonDecorFrame: Rect = Rect()

        fun update(content: String, rotation: Int, displayW: Int, displayH: Int) {}
    }

    class FakeDecorInsetsInfoWithOverloads {
        @JvmField
        var mNonDecorInsets: Rect = Rect()

        @JvmField
        var mNonDecorFrame: Rect = Rect()

        fun update() {}
        fun update(content: FakeDisplayContent, rotation: Int, displayW: Int, displayH: Int) {}
    }

    class FakeDecorInsetsInfoWithWrongOverloads {
        @JvmField
        var mNonDecorInsets: Rect = Rect()

        @JvmField
        var mNonDecorFrame: Rect = Rect()

        fun update() {}
        fun update(content: String, rotation: Int, displayW: Int, displayH: Int) {}
    }

    class FakeWindowManagerService {
        @JvmField
        var mWindowPlacerLocked: FakeWindowSurfacePlacer = FakeWindowSurfacePlacer()
    }

    class FakeWindowSurfacePlacer {
        fun requestTraversal() {}
    }

    class FakeDisplayPolicy {
        @JvmField
        var mDecorInsets: FakeDecorInsets = FakeDecorInsets()
    }

    class FakeDecorInsets {
        fun invalidate() {}
    }
}
