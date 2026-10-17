package tv.withaibuild.customiuizer.mods.statusbarheight

import android.graphics.Rect
import android.view.WindowManager
import com.android.server.wm.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarHeightArchitectureCRefreshTest {

    private fun wmWithPolicy(windowStateClass: Class<*>): WindowManagerCapability {
        return StatusBarHeightResolver.resolveWindowManagerClass(
            windowStateClass,
            WindowManager.LayoutParams::class.java,
        ).copy(displayPolicyClass = WindowState.FakeDisplayPolicy::class.java)
    }

    @Test
    fun resolveRefreshCapability_fullFakes_resolvesAllMembers() {
        val wm = wmWithPolicy(WindowStateWithTypedPlacer::class.java)
        val decor = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            WindowStateFakeDecorInsetsInfo::class.java,
            WindowState.FakeDisplayContent::class.java,
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
    fun resolveRefreshCapability_windowManagerServiceMissing_traversalUnavailableOthersIntact() {
        val wm = wmWithPolicy(WindowStateNoWmService::class.java)
        val decor = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            WindowStateFakeDecorInsetsInfo::class.java,
            WindowState.FakeDisplayContent::class.java,
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
    fun resolveRefreshCapability_displayContentMissing_invalidationUnavailableOthersIntact() {
        val wm = wmWithPolicy(WindowStateNoDisplayContent::class.java)
        val decor = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            WindowStateFakeDecorInsetsInfo::class.java,
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
    fun resolveRefreshCapability_mDecorInsetsFieldType_isInvalidateClassAuthority() {
        val wm = wmWithPolicy(WindowStateWithTypedPlacer::class.java)
        val decor = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            WindowStateFakeDecorInsetsInfo::class.java,
            WindowState.FakeDisplayContent::class.java,
        )

        val refresh = StatusBarHeightResolver.resolveRefreshCapability(
            wm,
            decor,
            javaClass.classLoader!!,
        )

        val decorInsetsField = refresh.displayPolicyDecorInsetsField
        assertNotNull(decorInsetsField)
        assertSame(WindowState.FakeDecorInsets::class.java, decorInsetsField?.type)
    }

    @Test
    fun resolveRefreshCapability_mWindowPlacerLockedMissing_traversalUnavailable() {
        val wm = wmWithPolicy(WindowStateNoPlacer::class.java)
        val decor = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            WindowStateFakeDecorInsetsInfo::class.java,
            WindowState.FakeDisplayContent::class.java,
        )

        val refresh = StatusBarHeightResolver.resolveRefreshCapability(
            wm,
            decor,
            javaClass.classLoader!!,
        )

        assertNull(refresh.windowManagerServicePlacerField)
        assertNull(refresh.windowSurfacePlacerRequestTraversalMethod)
        assertTrue(refresh.canInvalidateDecorInsets)
    }

    @Test
    fun resolveRefreshCapability_mDecorInsetsMissing_invalidationUnavailable() {
        val wm = wmWithPolicy(WindowStateWithTypedPlacer::class.java).copy(displayPolicyClass = DisplayPolicyNoDecorInsets::class.java)
        val decor = StatusBarHeightResolver.resolveDecorInsetsInfoClass(
            WindowStateFakeDecorInsetsInfo::class.java,
            WindowState.FakeDisplayContent::class.java,
        )

        val refresh = StatusBarHeightResolver.resolveRefreshCapability(
            wm,
            decor,
            javaClass.classLoader!!,
        )

        assertNull(refresh.displayPolicyDecorInsetsField)
        assertNull(refresh.decorInsetsInvalidateMethod)
        assertFalse(refresh.canInvalidateDecorInsets)
    }

    @Test
    fun effect_readWindowDisplayContent_returnsDisplayContent() {
        val effect = fullEffect()
        val win = WindowState()

        val displayContent = effect.readWindowDisplayContent(win)
        assertSame(win.mDisplayContent, displayContent)
    }

    @Test
    fun effect_readWindowManagerService_returnsWmService() {
        val effect = fullEffect()
        val win = WindowState()

        val wmService = effect.readWindowManagerService(win)
        assertSame(win.mWmService, wmService)
    }

    @Test
    fun effect_readDisplayPolicy_returnsPolicyFromDisplayContent() {
        val effect = fullEffect()
        val win = WindowState()

        val displayPolicy = effect.readDisplayPolicy(win.mDisplayContent)
        assertNotNull(displayPolicy)
    }

    @Test
    fun effect_readDecorInsets_returnsDecorInsets() {
        val effect = fullEffect()
        val win = WindowState()

        val displayPolicy = effect.readDisplayPolicy(win.mDisplayContent)!!
        val decorInsets = effect.readDecorInsets(displayPolicy)
        assertNotNull(decorInsets)
    }

    @Test
    fun effect_invalidateDecorInsets_callsInvalidateAndReturnsTrue() {
        val effect = fullEffect()
        val win = WindowState()

        val displayPolicy = effect.readDisplayPolicy(win.mDisplayContent)!!
        val decorInsets = effect.readDecorInsets(displayPolicy)!!

        assertTrue(effect.invalidateDecorInsets(decorInsets))
        assertEquals(1, (decorInsets as WindowState.FakeDecorInsets).invalidateCount)
    }

    @Test
    fun effect_requestTraversal_callsRequestTraversalAndReturnsTrue() {
        val effect = fullEffect()
        val win = WindowState()

        val wmService = effect.readWindowManagerService(win)!!
        val placer = effect.readWindowPlacer(wmService) as WindowState.FakeWindowSurfacePlacer

        assertTrue(effect.requestTraversal(placer))
        assertEquals(1, placer.requestTraversalCount)
    }

    @Test
    fun effect_requestTraversal_methodMissing_returnsFalse() {
        val effect = fullEffect().refreshCap(
            windowSurfacePlacerRequestTraversalMethod = null,
        )
        val placer = WindowState.FakeWindowSurfacePlacer()

        assertFalse(effect.requestTraversal(placer))
        assertEquals(0, placer.requestTraversalCount)
    }

    @Test
    fun effect_readDisplayPolicy_methodRuntimeException_failClosed() {
        val displayContent = ThrowingRuntimeDisplayContent()
        val effect = fullEffect().refreshCap(
            displayContentGetDisplayPolicyMethod = ThrowingRuntimeDisplayContent::class.java.getDeclaredMethod("getDisplayPolicy").also { it.isAccessible = true },
        )

        assertNull(effect.readDisplayPolicy(displayContent))
    }

    @Test
    fun effect_requestTraversal_methodOom_propagatesSameIdentity() {
        val oom = OutOfMemoryError("oom")
        val placer = ThrowingOomWindowSurfacePlacer(oom)
        val effect = fullEffect().refreshCap(
            windowSurfacePlacerRequestTraversalMethod = ThrowingOomWindowSurfacePlacer::class.java.getDeclaredMethod("requestTraversal").also { it.isAccessible = true },
        )

        val thrown = try {
            effect.requestTraversal(placer)
            null
        } catch (t: Throwable) {
            t
        }

        assertSame(oom, thrown)
    }

    @Test
    fun effect_invalidateDecorInsets_methodOom_propagatesSameIdentity() {
        val oom = OutOfMemoryError("oom")
        val decorInsets = ThrowingOomDecorInsets(oom)
        val effect = fullEffect().refreshCap(
            decorInsetsInvalidateMethod = ThrowingOomDecorInsets::class.java.getDeclaredMethod("invalidate").also { it.isAccessible = true },
        )

        val thrown = try {
            effect.invalidateDecorInsets(decorInsets)
            null
        } catch (t: Throwable) {
            t
        }

        assertSame(oom, thrown)
    }

    private fun fullEffect(): StatusBarHeightEffect {
        val wm = wmWithPolicy(WindowState::class.java).copy(
            windowStateWindowManagerServiceField = WindowState::class.java.getDeclaredField("mWmService").also { it.isAccessible = true },
            windowStateDisplayContentField = WindowState::class.java.getDeclaredField("mDisplayContent").also { it.isAccessible = true },
        )
        val insets = InsetsSourceCapability(
            sourceClass = null,
            setFrameOneArg = true,
            setFrameFourArg = true,
            typeInfo = InsetsTypeInfo(InsetsTypeEncoding.MODERN_PUBLIC, 1, 2, 128),
            typeField = null,
            getTypeMethod = null,
            getIdMethod = null,
            getFrameMethod = null,
        )
        val decor = DecorInsetsCapability(
            infoClass = null,
            updateMethod = null,
            displayContentClass = WindowState.FakeDisplayContent::class.java,
            displayContentGetDisplayMetricsMethod = null,
            nonDecorInsetsField = null,
            nonDecorFrameField = null,
        )
        val resolvedRefresh = StatusBarHeightResolver.resolveRefreshCapability(
            wm,
            decor,
            javaClass.classLoader!!,
        )
        // The test double declares mWindowPlacerLocked as Any, so the placer method must be
        // resolved against the concrete placer class rather than the field's declared type.
        val refresh = resolvedRefresh.copy(
            windowManagerServicePlacerField = WindowState.FakeWindowManagerService::class.java.getDeclaredField("mWindowPlacerLocked").also { it.isAccessible = true },
            windowSurfacePlacerRequestTraversalMethod = WindowState.FakeWindowSurfacePlacer::class.java.getDeclaredMethod("requestTraversal").also { it.isAccessible = true },
        )
        return StatusBarHeightEffect(StatusBarHeightAbi(insets, wm, decor, refresh))
    }

    private fun StatusBarHeightEffect.refreshCap(
        windowManagerServicePlacerField: java.lang.reflect.Field? = abi.refresh.windowManagerServicePlacerField,
        windowSurfacePlacerRequestTraversalMethod: java.lang.reflect.Method? = abi.refresh.windowSurfacePlacerRequestTraversalMethod,
        displayContentGetDisplayPolicyMethod: java.lang.reflect.Method? = abi.refresh.displayContentGetDisplayPolicyMethod,
        displayPolicyDecorInsetsField: java.lang.reflect.Field? = abi.refresh.displayPolicyDecorInsetsField,
        decorInsetsInvalidateMethod: java.lang.reflect.Method? = abi.refresh.decorInsetsInvalidateMethod,
    ): StatusBarHeightEffect {
        val newRefresh = StatusBarHeightRefreshCapability(
            windowManagerServicePlacerField = windowManagerServicePlacerField,
            windowSurfacePlacerRequestTraversalMethod = windowSurfacePlacerRequestTraversalMethod,
            displayContentGetDisplayPolicyMethod = displayContentGetDisplayPolicyMethod,
            displayPolicyDecorInsetsField = displayPolicyDecorInsetsField,
            decorInsetsInvalidateMethod = decorInsetsInvalidateMethod,
        )
        return StatusBarHeightEffect(abi.copy(refresh = newRefresh))
    }

    // ------------------------------------------------------------------------
    // Fake framework classes for refresh resolver / effect unit tests.
    // ------------------------------------------------------------------------

    class WindowStateNoWmService {
        @JvmField
        var mAttrs: WindowManager.LayoutParams = WindowManager.LayoutParams()

        @JvmField
        var mDisplayContent: WindowState.FakeDisplayContent = WindowState.FakeDisplayContent()

        fun getFrame(): Rect = Rect()
        fun getDisplayMetrics(): Any = Any()
        fun getDisplayId(): Int = 0
    }

    class WindowStateWithTypedPlacer {
        @JvmField
        var mAttrs: WindowManager.LayoutParams = WindowManager.LayoutParams()

        @JvmField
        var mDisplayContent: WindowState.FakeDisplayContent = WindowState.FakeDisplayContent()

        @JvmField
        var mWmService: TypedPlacerService = TypedPlacerService()

        fun getFrame(): Rect = Rect()
        fun getDisplayMetrics(): Any = Any()
        fun getDisplayId(): Int = 0
    }

    class TypedPlacerService {
        @JvmField
        var mWindowPlacerLocked: WindowState.FakeWindowSurfacePlacer = WindowState.FakeWindowSurfacePlacer()
    }

    class WindowStateNoDisplayContent {
        @JvmField
        var mAttrs: WindowManager.LayoutParams = WindowManager.LayoutParams()

        @JvmField
        var mWmService: TypedPlacerService = TypedPlacerService()

        fun getFrame(): Rect = Rect()
        fun getDisplayMetrics(): Any = Any()
        fun getDisplayId(): Int = 0
    }

    class WindowStateNoPlacer {
        @JvmField
        var mAttrs: WindowManager.LayoutParams = WindowManager.LayoutParams()

        @JvmField
        var mDisplayContent: WindowState.FakeDisplayContent = WindowState.FakeDisplayContent()

        @JvmField
        var mWmService: NoPlacerService = NoPlacerService()

        fun getFrame(): Rect = Rect()
        fun getDisplayMetrics(): Any = Any()
        fun getDisplayId(): Int = 0
    }

    class NoPlacerService

    class DisplayPolicyNoDecorInsets {
        @JvmField
        var unrelated: Any = Any()
    }

    class WindowStateFakeDecorInsetsInfo {
        @JvmField
        var mNonDecorInsets: Rect = Rect()

        @JvmField
        var mNonDecorFrame: Rect = Rect()

        fun update(content: WindowState.FakeDisplayContent, rotation: Int, displayW: Int, displayH: Int) {}
    }

    class ThrowingRuntimeDisplayContent {
        fun getDisplayMetrics(): Any = Any()
        fun getDisplayPolicy(): WindowState.FakeDisplayPolicy {
            throw RuntimeException("boom")
        }
    }

    class ThrowingOomWindowSurfacePlacer(private val oom: OutOfMemoryError) {
        fun requestTraversal() {
            throw oom
        }
    }

    class ThrowingOomDecorInsets(private val oom: OutOfMemoryError) {
        var invalidateCount = 0
            private set

        fun invalidate() {
            invalidateCount++
            throw oom
        }
    }
}