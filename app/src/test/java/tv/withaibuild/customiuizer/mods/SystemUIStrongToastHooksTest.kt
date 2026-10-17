package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeSnapshot
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeState
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

class SystemUIStrongToastHooksTest {

    class FakeKeyguard {
        @JvmField
        var mShowing: Boolean = true
    }

    class FakeControl {
        @JvmField
        var mKeyguardStateController: FakeKeyguard = FakeKeyguard()
    }

    @After
    fun tearDown() {
        StrongToastRuntimeState.instance?.let { state ->
            tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry.observers.remove(state.preferenceObserver)
        }
        StrongToastRuntimeState.instance = null
        StrongToastRuntimeState.installed = false
        SystemUIStrongToastHooks.snapshotRef = null
        SystemUIStrongToastHooks.installed = false
    }

    private fun fakePackageReadyParam(): XposedModuleInterface.PackageReadyParam {
        return Proxy.newProxyInstance(
            XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.android.systemui"
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "getApplicationInfo" -> null
                else -> null
            }
        } as XposedModuleInterface.PackageReadyParam
    }

    private fun fakeChain(thisObject: Any, onProceed: () -> Any? = { null }): XposedInterface.Chain {
        return Proxy.newProxyInstance(
            XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getThisObject" -> thisObject
                "proceed" -> onProceed()
                "getExecutable" -> null
                "getArgs" -> emptyList<Any>()
                "getArg" -> null
                "proceedWith" -> null
                "equals" -> false
                "hashCode" -> java.lang.System.identityHashCode(this)
                "toString" -> "FakeChain"
                else -> null
            }
        } as XposedInterface.Chain
    }

    private fun setDynamicIslandSnapshot() {
        SystemUIStrongToastHooks.snapshotRef =
            AtomicReference(StrongToastRuntimeSnapshot(StrongToastPresentationMode.DYNAMIC_ISLAND, StrongToastPosition.TOP, 0))
    }

    private fun setHideSnapshot() {
        SystemUIStrongToastHooks.snapshotRef =
            AtomicReference(StrongToastRuntimeSnapshot(StrongToastPresentationMode.HIDE, StrongToastPosition.TOP, 0))
    }

    private fun setSystemDefaultSnapshot() {
        SystemUIStrongToastHooks.snapshotRef =
            AtomicReference(StrongToastRuntimeSnapshot(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPosition.TOP, 0))
    }

    @Test
    fun install_setsInstalledFlag_andReusesExistingInstallation() {
        val lpparam = fakePackageReadyParam()
        val snapshot = AtomicReference(StrongToastRuntimeSnapshot(StrongToastPresentationMode.DYNAMIC_ISLAND, StrongToastPosition.TOP, 0))

        SystemUIStrongToastHooks.install(lpparam, snapshot)

        assertTrue(SystemUIStrongToastHooks.installed)
        assertSame(snapshot, SystemUIStrongToastHooks.snapshotRef)

        val anotherSnapshot = AtomicReference(StrongToastRuntimeSnapshot(StrongToastPresentationMode.HIDE, StrongToastPosition.TOP, 0))
        SystemUIStrongToastHooks.install(lpparam, anotherSnapshot)

        // A second call does not try to install the hooks again.
        assertTrue(SystemUIStrongToastHooks.installed)
    }

    @Test
    fun controlHook_systemDefault_proceedsOnce() {
        setSystemDefaultSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        var proceedCount = 0
        val chain = fakeChain(control) { proceedCount++; null }

        assertNull(hook.intercept(chain))
        assertEquals(1, proceedCount)
        assertTrue(control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_hideWithAllowHide_skipsAndDoesNotProceed() {
        setHideSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        var proceedCount = 0
        val chain = fakeChain(control) { proceedCount++; null }

        assertNull(hook.intercept(chain))
        assertEquals(0, proceedCount)
        assertTrue(control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_hideWithoutAllowHide_proceedsOnce() {
        setHideSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            false
        )

        var proceedCount = 0
        val chain = fakeChain(control) { proceedCount++; null }

        assertNull(hook.intercept(chain))
        assertEquals(1, proceedCount)
        assertTrue(control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_dynamicIsland_opensAndClosesGate() {
        setDynamicIslandSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        var proceedCount = 0
        val chain = fakeChain(control) { proceedCount++; null }

        assertNull(hook.intercept(chain))
        assertEquals(1, proceedCount)
        assertTrue(control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_nestedDynamicIsland_usesLifoGateOwnership() {
        setDynamicIslandSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val outerHook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )
        val innerHook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        var outerProceed = 0
        var innerProceed = 0

        val innerChain = fakeChain(control) {
            innerProceed++
            assertFalse("inner should see gate already open", control.mKeyguardStateController.mShowing)
            null
        }

        val outerChain = fakeChain(control) {
            outerProceed++
            assertFalse("outer should have opened the gate", control.mKeyguardStateController.mShowing)
            innerHook.intercept(innerChain)
            // Inner has finished; the outer still owns the gate.
            assertFalse("outer gate must still be open after inner exits", control.mKeyguardStateController.mShowing)
            null
        }

        assertNull(outerHook.intercept(outerChain))
        assertEquals(1, outerProceed)
        assertEquals(1, innerProceed)
        assertTrue("outer must restore the gate after the whole chain", control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_ordinaryFailure_restoresGateAndRethrows() {
        setDynamicIslandSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        val failure = RuntimeException("chain failed")
        val chain = fakeChain(control) { throw failure }

        val thrown = assertThrows(RuntimeException::class.java) { hook.intercept(chain) }
        assertSame(failure, thrown)
        assertTrue(control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_fatalError_restoresGateAndPropagates() {
        setDynamicIslandSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        val error = InternalError("fatal")
        val chain = fakeChain(control) { throw error }

        val thrown = assertThrows(InternalError::class.java) { hook.intercept(chain) }
        assertSame(error, thrown)
        assertTrue(control.mKeyguardStateController.mShowing)
    }

    // -------------------------------------------------------------------------
    // MATCH_STATUS_BAR_HEIGHT exact baseline capture / restore tests
    // -------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private class FakeResources(dimensionMap: Map<String, Int> = emptyMap()) : Resources(null, DisplayMetrics(), Configuration()) {
        private val metrics = DisplayMetrics().apply { density = 2.0f }
        private val nameToId = mutableMapOf<String, Int>()
        private val dimensions = mutableMapOf<Int, Int>()

        init {
            register("cl_strong_toast_msg", MSG_ID)
            register("strong_toast_bottom_view", BOTTOM_ID)
            for ((name, value) in dimensionMap) {
                val id = NEXT_DIMEN_ID++
                register(name, id)
                setDimension(id, value)
            }
        }

        fun register(name: String, id: Int) {
            nameToId[name] = id
        }

        fun setDimension(id: Int, px: Int) {
            dimensions[id] = px
        }

        override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int =
            nameToId[name] ?: 0

        override fun getDisplayMetrics(): DisplayMetrics = metrics

        override fun getDimensionPixelSize(id: Int): Int = dimensions[id] ?: 0
    }

    /**
     * Test double for MATCH root. It stores padding and gravity itself because the
     * android.jar View/LinearLayout stub does not hold View state in the JVM unit-test
     * runtime. The stub findViewById is a no-op, so we test the helpers directly instead of
     * the full production find / apply / reset pipeline.
     */
    private open class FakeRoot(res: FakeResources) : LinearLayout(null as Context?) {
        private val fakeRes = res
        private var storedPaddingLeft = 0
        private var storedPaddingTop = 0
        private var storedPaddingRight = 0
        private var storedPaddingBottom = 0
        private var storedGravity = 0
        private var storedLp: ViewGroup.LayoutParams? = null
        var throwOnSetLayoutParams = false

        override fun getResources(): Resources = fakeRes

        override fun getPaddingLeft(): Int = storedPaddingLeft
        override fun getPaddingTop(): Int = storedPaddingTop
        override fun getPaddingRight(): Int = storedPaddingRight
        override fun getPaddingBottom(): Int = storedPaddingBottom

        override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
            storedPaddingLeft = left
            storedPaddingTop = top
            storedPaddingRight = right
            storedPaddingBottom = bottom
        }

        override fun getGravity(): Int = storedGravity
        override fun setGravity(gravity: Int) {
            storedGravity = gravity
        }

        override fun getLayoutParams(): ViewGroup.LayoutParams? = storedLp
        override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
            if (throwOnSetLayoutParams) throw RuntimeException("FakeRoot layoutParams failure")
            storedLp = params
        }
    }

    private class FakeCapsule(res: FakeResources) : LinearLayout(null as Context?) {
        private val fakeRes = res
        private var storedLp: ViewGroup.LayoutParams? = null
        private var storedGravity = 0
        var throwOnSetLayoutParams = false

        override fun getResources(): Resources = fakeRes

        override fun getLayoutParams(): ViewGroup.LayoutParams? = storedLp
        override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
            if (throwOnSetLayoutParams) throw RuntimeException("FakeCapsule layoutParams failure")
            storedLp = params
        }

        override fun getGravity(): Int = storedGravity
        override fun setGravity(gravity: Int) {
            storedGravity = gravity
        }
    }

    private class FakeBottomView(res: FakeResources) : View(null as Context?) {
        private val fakeRes = res
        private var storedVisibility = View.VISIBLE

        override fun getResources(): Resources = fakeRes

        override fun getVisibility(): Int = storedVisibility
        override fun setVisibility(visibility: Int) {
            storedVisibility = visibility
        }
    }

    private fun matchFixture(): Triple<FakeRoot, FakeCapsule, FakeBottomView> {
        val res = FakeResources(mapOf("strong_toast_width" to 91))
        val root = FakeRoot(res)
        root.setPadding(3, 5, 7, 9)
        root.gravity = GRAVITY_PARENT_BASELINE
        root.layoutParams = LinearLayout.LayoutParams(500, 200).apply {
            width = 500
            height = 200
            topMargin = 19
            bottomMargin = 23
            gravity = GRAVITY_PARENT_LAYOUT_BASELINE
        }

        val capsule = FakeCapsule(res)
        val lp = LinearLayout.LayoutParams(300, 141).apply {
            width = 300
            height = 141
            topMargin = 7
            bottomMargin = 11
            gravity = GRAVITY_LAYOUT_BASELINE
        }
        capsule.layoutParams = lp
        capsule.gravity = GRAVITY_CAPSULE_BASELINE

        val bottomView = FakeBottomView(res).apply {
            visibility = View.INVISIBLE
        }

        return Triple(root, capsule, bottomView)
    }

    @Test
    fun captureMatchModeBaseline_readsExactCurrentValues() {
        val (root, capsule, bottomView) = matchFixture()

        val baseline = SystemUIStrongToastHooks.captureMatchModeBaseline(capsule, root, bottomView)
        assertNotNull(baseline)

        assertEquals(300, baseline!!.width)
        assertEquals(141, baseline.height)
        assertEquals(7, baseline.topMargin)
        assertEquals(11, baseline.bottomMargin)
        assertEquals(GRAVITY_LAYOUT_BASELINE, baseline.layoutGravity)
        assertEquals(GRAVITY_CAPSULE_BASELINE, baseline.capsuleGravity)
        assertEquals(3, baseline.parentPaddingLeft)
        assertEquals(5, baseline.parentPaddingTop)
        assertEquals(7, baseline.parentPaddingRight)
        assertEquals(9, baseline.parentPaddingBottom)
        assertEquals(GRAVITY_PARENT_BASELINE, baseline.parentGravity)
        assertEquals(500, baseline.parentWidth)
        assertEquals(200, baseline.parentHeight)
        assertEquals(19, baseline.parentTopMargin)
        assertEquals(23, baseline.parentBottomMargin)
        assertEquals(GRAVITY_PARENT_LAYOUT_BASELINE, baseline.parentLayoutGravity)
        assertEquals(View.INVISIBLE, baseline.bottomViewVisibility)
    }

    @Test
    fun captureMatchModeBaseline_returnsNullWhenLayoutParamsMissing() {
        val res = FakeResources()
        val root = FakeRoot(res)
        val capsule = FakeCapsule(res)

        assertNull(SystemUIStrongToastHooks.captureMatchModeBaseline(capsule, root, null))
    }

    @Test
    fun applyMatchModeBaselineToViews_appliesAndRestoresExactBaseline() {
        val (root, capsule, bottomView) = matchFixture()

        val baseline = SystemUIStrongToastHooks.captureMatchModeBaseline(capsule, root, bottomView)
        assertNotNull(baseline)

        val applied = SystemUIStrongToastHooks.applyMatchModeBaselineToViews(
            root,
            capsule,
            root,
            bottomView,
            82,
            false
        )
        assertTrue("apply must succeed when baseline exists", applied)

        // MATCH resizes the message row and keeps the ROM chin when it can share the height.
        assertEquals(500, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.width)
        assertEquals(200, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.height)
        assertEquals(0, root.paddingLeft)
        assertEquals(0, root.paddingTop)
        assertEquals(0, root.paddingRight)
        assertEquals(0, root.paddingBottom)
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, root.gravity)

        assertEquals(300, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.width)
        assertEquals(82, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.height)
        assertEquals(7, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin)
        assertEquals(11, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin)
        assertEquals(GRAVITY_LAYOUT_BASELINE, (capsule.layoutParams as? LinearLayout.LayoutParams)?.gravity)
        assertEquals(GRAVITY_CAPSULE_BASELINE, capsule.gravity)
        assertEquals(View.VISIBLE, bottomView.visibility)

        SystemUIStrongToastHooks.restoreMatchModeBaseline(
            root,
            capsule,
            root,
            bottomView,
            baseline!!
        )

        // Both parent and child are restored to the captured baseline.
        assertEquals(500, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.width)
        assertEquals(200, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.height)
        assertEquals(19, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin)
        assertEquals(23, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin)
        assertEquals(GRAVITY_PARENT_LAYOUT_BASELINE, (root.layoutParams as? LinearLayout.LayoutParams)?.gravity)
        assertEquals(3, root.paddingLeft)
        assertEquals(5, root.paddingTop)
        assertEquals(7, root.paddingRight)
        assertEquals(9, root.paddingBottom)
        assertEquals(GRAVITY_PARENT_BASELINE, root.gravity)
        assertEquals(300, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.width)
        assertEquals(141, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.height)
        assertEquals(7, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin)
        assertEquals(11, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin)
        assertEquals(GRAVITY_LAYOUT_BASELINE, (capsule.layoutParams as? LinearLayout.LayoutParams)?.gravity)
        assertEquals(GRAVITY_CAPSULE_BASELINE, capsule.gravity)
        assertEquals(View.INVISIBLE, bottomView.visibility)
    }

    @Test
    fun applyMatchModeBaselineToViews_doubleApply_doesNotOverwriteBaseline() {
        val (root, capsule, bottomView) = matchFixture()

        assertTrue(SystemUIStrongToastHooks.applyMatchModeBaselineToViews(root, capsule, root, bottomView, 82, false))
        assertTrue(SystemUIStrongToastHooks.applyMatchModeBaselineToViews(root, capsule, root, bottomView, 150, false))

        val baseline = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD) as SystemUIStrongToastHooks.MatchModeBaseline
        assertEquals(141, baseline.height)
        assertEquals(300, baseline.width)
        assertEquals(7, baseline.topMargin)
        assertEquals(11, baseline.bottomMargin)
        assertEquals(GRAVITY_LAYOUT_BASELINE, baseline.layoutGravity)
        assertEquals(GRAVITY_CAPSULE_BASELINE, baseline.capsuleGravity)
        assertEquals(3, baseline.parentPaddingLeft)
        assertEquals(5, baseline.parentPaddingTop)
        assertEquals(7, baseline.parentPaddingRight)
        assertEquals(9, baseline.parentPaddingBottom)
        assertEquals(GRAVITY_PARENT_BASELINE, baseline.parentGravity)
        assertEquals(500, baseline.parentWidth)
        assertEquals(200, baseline.parentHeight)
        assertEquals(19, baseline.parentTopMargin)
        assertEquals(23, baseline.parentBottomMargin)
        assertEquals(GRAVITY_PARENT_LAYOUT_BASELINE, baseline.parentLayoutGravity)
        assertEquals(View.INVISIBLE, baseline.bottomViewVisibility)

        SystemUIStrongToastHooks.restoreMatchModeBaseline(root, capsule, root, bottomView, baseline)

        assertEquals(141, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.height)
        assertEquals(300, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.width)
        assertEquals(200, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.height)
        assertEquals(500, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.width)
    }

    @Test
    fun applyMatchModeBaselineToViews_nextEvent_capturesFreshBaseline() {
        val (root, capsule, bottomView) = matchFixture()

        assertTrue(SystemUIStrongToastHooks.applyMatchModeBaselineToViews(root, capsule, root, bottomView, 82, false))
        val firstBaseline = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD) as SystemUIStrongToastHooks.MatchModeBaseline
        SystemUIStrongToastHooks.restoreMatchModeBaseline(root, capsule, root, bottomView, firstBaseline)
        XposedHelpers.removeAdditionalInstanceField(root, MATCH_BASELINE_FIELD)

        (capsule.layoutParams as? LinearLayout.LayoutParams)?.apply {
            width = 400
            height = 222
            topMargin = 13
            bottomMargin = 17
            gravity = GRAVITY_LAYOUT_NEXT
        }
        capsule.gravity = GRAVITY_CAPSULE_NEXT
        root.setPadding(2, 4, 6, 8)
        root.gravity = GRAVITY_PARENT_NEXT
        root.layoutParams = LinearLayout.LayoutParams(600, 333).apply {
            width = 600
            height = 333
            topMargin = 29
            bottomMargin = 31
            gravity = GRAVITY_PARENT_LAYOUT_NEXT
        }
        bottomView.visibility = View.GONE

        assertTrue(SystemUIStrongToastHooks.applyMatchModeBaselineToViews(root, capsule, root, bottomView, 104, false))
        val secondBaseline = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD) as SystemUIStrongToastHooks.MatchModeBaseline

        assertEquals(400, secondBaseline.width)
        assertEquals(222, secondBaseline.height)
        assertEquals(13, secondBaseline.topMargin)
        assertEquals(17, secondBaseline.bottomMargin)
        assertEquals(GRAVITY_LAYOUT_NEXT, secondBaseline.layoutGravity)
        assertEquals(GRAVITY_CAPSULE_NEXT, secondBaseline.capsuleGravity)
        assertEquals(2, secondBaseline.parentPaddingLeft)
        assertEquals(4, secondBaseline.parentPaddingTop)
        assertEquals(6, secondBaseline.parentPaddingRight)
        assertEquals(8, secondBaseline.parentPaddingBottom)
        assertEquals(GRAVITY_PARENT_NEXT, secondBaseline.parentGravity)
        assertEquals(600, secondBaseline.parentWidth)
        assertEquals(333, secondBaseline.parentHeight)
        assertEquals(29, secondBaseline.parentTopMargin)
        assertEquals(31, secondBaseline.parentBottomMargin)
        assertEquals(GRAVITY_PARENT_LAYOUT_NEXT, secondBaseline.parentLayoutGravity)
        assertEquals(View.GONE, secondBaseline.bottomViewVisibility)

        SystemUIStrongToastHooks.restoreMatchModeBaseline(root, capsule, root, bottomView, secondBaseline)

        assertEquals(400, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.width)
        assertEquals(222, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.height)
        assertEquals(13, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin)
        assertEquals(17, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin)
        assertEquals(GRAVITY_LAYOUT_NEXT, (capsule.layoutParams as? LinearLayout.LayoutParams)?.gravity)
        assertEquals(GRAVITY_CAPSULE_NEXT, capsule.gravity)
        assertEquals(2, root.paddingLeft)
        assertEquals(4, root.paddingTop)
        assertEquals(6, root.paddingRight)
        assertEquals(8, root.paddingBottom)
        assertEquals(GRAVITY_PARENT_NEXT, root.gravity)
        assertEquals(600, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.width)
        assertEquals(333, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.height)
        assertEquals(29, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin)
        assertEquals(31, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin)
        assertEquals(GRAVITY_PARENT_LAYOUT_NEXT, (root.layoutParams as? LinearLayout.LayoutParams)?.gravity)
        assertEquals(View.GONE, bottomView.visibility)
    }

    @Test
    fun applyMatchModeBaselineToViews_returnsFalseWhenBaselineCaptureFails() {
        val res = FakeResources()
        val root = FakeRoot(res)
        val capsule = FakeCapsule(res)

        val applied = SystemUIStrongToastHooks.applyMatchModeBaselineToViews(root, capsule, root, null, 82, false)
        assertFalse("apply must fail when baseline cannot be captured", applied)
        assertNull(
            "no baseline must be stored when capture fails",
            XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
        )
    }

    @Test
    fun applyMatchStatusBarHeight_returnsFalseAndDoesNotMutateWhenCapsuleMissing() {
        val res = FakeResources(mapOf("strong_toast_width" to 91))
        val root = FakeRoot(res)

        val applied = SystemUIStrongToastHooks.applyMatchStatusBarHeight(root, 82, false)
        assertFalse("apply must fail when no cl_strong_toast_msg child exists", applied)
        assertNull(
            "no baseline must be stored when apply fails",
            XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
        )
    }

    @Test
    fun resetMatchModeCapsule_isNoOpWhenBaselineMissing() {
        val (root, capsule, bottomView) = matchFixture()

        val originalWidth = (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.width
        val originalVisibility = bottomView.visibility

        SystemUIStrongToastHooks.resetMatchModeCapsule(root)

        assertEquals(originalWidth, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.width)
        assertEquals(originalVisibility, bottomView.visibility)
    }

    @Test
    fun resetMatchModeBaselineToViews_restoresAndClearsBaseline() {
        val (root, capsule, bottomView) = matchFixture()

        assertTrue(SystemUIStrongToastHooks.applyMatchModeBaselineToViews(root, capsule, root, bottomView, 82, false))
        val baseline = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
            as SystemUIStrongToastHooks.MatchModeBaseline

        SystemUIStrongToastHooks.resetMatchModeBaselineToViews(root, capsule, root, bottomView, baseline)

        assertEquals(300, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.width)
        assertEquals(141, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.height)
        assertEquals(7, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin)
        assertEquals(11, (capsule.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin)
        assertEquals(GRAVITY_LAYOUT_BASELINE, (capsule.layoutParams as? LinearLayout.LayoutParams)?.gravity)
        assertEquals(GRAVITY_CAPSULE_BASELINE, capsule.gravity)
        assertEquals(3, root.paddingLeft)
        assertEquals(5, root.paddingTop)
        assertEquals(7, root.paddingRight)
        assertEquals(9, root.paddingBottom)
        assertEquals(GRAVITY_PARENT_BASELINE, root.gravity)
        assertEquals(500, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.width)
        assertEquals(200, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.height)
        assertEquals(19, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin)
        assertEquals(23, (root.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin)
        assertEquals(GRAVITY_PARENT_LAYOUT_BASELINE, (root.layoutParams as? LinearLayout.LayoutParams)?.gravity)
        assertEquals(View.INVISIBLE, bottomView.visibility)

        assertNull(
            "MATCH_BASELINE_FIELD must be removed after normal restore",
            XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
        )
    }

    @Test
    fun resetMatchModeBaselineToViews_clearsBaselineWhenCapsuleMissing() {
        val (root, capsule, bottomView) = matchFixture()

        assertTrue(SystemUIStrongToastHooks.applyMatchModeBaselineToViews(root, capsule, root, bottomView, 82, false))
        val baseline = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
            as SystemUIStrongToastHooks.MatchModeBaseline

        // Simulate the production path where the capsule could not be resolved,
        // but the field still has to be cleared.
        SystemUIStrongToastHooks.resetMatchModeBaselineToViews(root, null, root, null, baseline)

        assertNull(
            "MATCH_BASELINE_FIELD must be removed when capsule cannot be resolved",
            XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
        )
    }

    @Test
    fun resetMatchModeBaselineToViews_clearsBaselineWhenRestoreThrows() {
        val (root, capsule, bottomView) = matchFixture()

        assertTrue(SystemUIStrongToastHooks.applyMatchModeBaselineToViews(root, capsule, root, bottomView, 82, false))
        val baseline = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
            as SystemUIStrongToastHooks.MatchModeBaseline

        // Cause restore to fail at LayoutParams assignment with an ordinary RuntimeException.
        capsule.throwOnSetLayoutParams = true

        var thrown: RuntimeException? = null
        try {
            SystemUIStrongToastHooks.resetMatchModeBaselineToViews(root, capsule, root, bottomView, baseline)
        } catch (e: RuntimeException) {
            thrown = e
        }

        assertNotNull("restore must propagate the ordinary exception", thrown)
        assertNull(
            "MATCH_BASELINE_FIELD must be removed even when restore throws",
            XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
        )
    }

    @Test
    fun resetMatchModeBaselineToViews_nextEventAfterRestoreFailureCapturesFreshBaseline() {
        val (root, capsule, bottomView) = matchFixture()

        // Event A: apply MATCH, capture baseline A, then force restore to throw.
        assertTrue(SystemUIStrongToastHooks.applyMatchModeBaselineToViews(root, capsule, root, bottomView, 82, false))
        val baselineA = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
            as SystemUIStrongToastHooks.MatchModeBaseline
        assertEquals(300, baselineA.width)
        assertEquals(141, baselineA.height)

        capsule.throwOnSetLayoutParams = true
        try {
            SystemUIStrongToastHooks.resetMatchModeBaselineToViews(root, capsule, root, bottomView, baselineA)
        } catch (_: RuntimeException) {
        }

        // Fix the capsule and reconfigure with event B values. The field must be absent.
        capsule.throwOnSetLayoutParams = false
        (capsule.layoutParams as? LinearLayout.LayoutParams)?.apply {
            width = 400
            height = 222
            topMargin = 13
            bottomMargin = 17
            gravity = GRAVITY_LAYOUT_NEXT
        }
        capsule.gravity = GRAVITY_CAPSULE_NEXT
        root.setPadding(2, 4, 6, 8)
        root.gravity = GRAVITY_PARENT_NEXT
        root.layoutParams = LinearLayout.LayoutParams(600, 333).apply {
            width = 600
            height = 333
            topMargin = 29
            bottomMargin = 31
            gravity = GRAVITY_PARENT_LAYOUT_NEXT
        }
        bottomView.visibility = View.GONE

        assertNull(
            "MATCH_BASELINE_FIELD must be absent before the next event",
            XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
        )

        // Event B: fresh apply must capture the new baseline, not stale baseline A.
        assertTrue(SystemUIStrongToastHooks.applyMatchModeBaselineToViews(root, capsule, root, bottomView, 104, false))
        val baselineB = XposedHelpers.getAdditionalInstanceField(root, MATCH_BASELINE_FIELD)
            as SystemUIStrongToastHooks.MatchModeBaseline

        assertEquals(400, baselineB.width)
        assertEquals(222, baselineB.height)
        assertEquals(13, baselineB.topMargin)
        assertEquals(17, baselineB.bottomMargin)
        assertEquals(GRAVITY_LAYOUT_NEXT, baselineB.layoutGravity)
        assertEquals(GRAVITY_CAPSULE_NEXT, baselineB.capsuleGravity)
        assertEquals(2, baselineB.parentPaddingLeft)
        assertEquals(4, baselineB.parentPaddingTop)
        assertEquals(6, baselineB.parentPaddingRight)
        assertEquals(8, baselineB.parentPaddingBottom)
        assertEquals(GRAVITY_PARENT_NEXT, baselineB.parentGravity)
        assertEquals(600, baselineB.parentWidth)
        assertEquals(333, baselineB.parentHeight)
        assertEquals(29, baselineB.parentTopMargin)
        assertEquals(31, baselineB.parentBottomMargin)
        assertEquals(GRAVITY_PARENT_LAYOUT_NEXT, baselineB.parentLayoutGravity)
        assertEquals(View.GONE, baselineB.bottomViewVisibility)
    }

    companion object {
        private const val MATCH_BASELINE_FIELD = "customiuizer_match_mode_baseline"
        private const val MSG_ID = 1001
        private const val BOTTOM_ID = 1002
        private var NEXT_DIMEN_ID = 2001

        // Deliberately non-default, non-zero, non-CENTER fixture values so that tests cannot
        // pass with a hard-coded CENTER / VISIBLE implementation.
        private const val GRAVITY_LAYOUT_BASELINE = Gravity.FILL_VERTICAL
        private const val GRAVITY_CAPSULE_BASELINE = Gravity.BOTTOM or Gravity.RIGHT
        private const val GRAVITY_PARENT_BASELINE = Gravity.BOTTOM or Gravity.END
        private const val GRAVITY_PARENT_LAYOUT_BASELINE = Gravity.BOTTOM or Gravity.FILL_HORIZONTAL
        private const val GRAVITY_LAYOUT_NEXT = Gravity.FILL_HORIZONTAL
        private const val GRAVITY_CAPSULE_NEXT = Gravity.TOP or Gravity.LEFT
        private const val GRAVITY_PARENT_LAYOUT_NEXT = Gravity.TOP or Gravity.FILL_HORIZONTAL
        private const val GRAVITY_PARENT_NEXT = Gravity.TOP or Gravity.START
    }
}
