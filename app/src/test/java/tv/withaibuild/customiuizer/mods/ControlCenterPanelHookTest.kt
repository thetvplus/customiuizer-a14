package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import miui.systemui.controlcenter.panel.main.MainPanelAnimController
import miui.systemui.controlcenter.panel.main.MainPanelContent
import miui.systemui.controlcenter.panel.main.MainPanelContentDistributor
import miui.systemui.controlcenter.panel.main.MainPanelController
import miui.systemui.controlcenter.panel.main.footer.FooterSpaceController
import miui.systemui.controlcenter.panel.main.header.StatusHeaderController
import miui.systemui.controlcenter.panel.main.qs.EditButtonController
import miui.systemui.controlcenter.panel.main.qs.QSListController
import miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter
import miui.systemui.controlcenter.panel.main.recyclerview.MainPanelListItem
import miui.systemui.controlcenter.panel.main.security.SecurityFooterController
import miui.systemui.controlcenter.qs.tileview.QSTileItemView
import miui.systemui.util.CommonUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ControlCenterPanelHookTest {
    @Test fun carrierTextUsesOneNativeWriteAndOnlyRewritesChangedInput() {
        for (hideOperator in listOf(false, true)) {
            withControlCenterHooks(install = {
                SystemUIControlCenterHooks.CCHeaderHook(it, hideOperator, hideDelimiter = true, showStep = false)
            }) { installed ->
                val type = StatusHeaderController::class.java
                val method = type.getDeclaredMethod("onCreate\$lambda-2\$lambda-0", type, String::class.java)
                val owner = StatusHeaderController(RuntimeEnvironment.getApplication())
                fun dispatch(text: String, expected: String) {
                    val rewrite: ((Array<Any?>) -> Any?)? = if (text == expected) null else { args ->
                        assertSame(owner, args[0])
                        StatusHeaderController.`onCreate$lambda-2$lambda-0`(owner, args[1] as String)
                    }
                    installed.invoke(method, null, owner, text, rewrite = rewrite) {
                        StatusHeaderController.`onCreate$lambda-2$lambda-0`(owner, text)
                    }
                    assertEquals(expected, owner.carrier.text.toString())
                }

                dispatch("", "")
                dispatch("A | B | C", if (hideOperator) "" else "ABC")
                dispatch("Carrier", if (hideOperator) "" else "Carrier")
                assertEquals(3, owner.carrierWrites)
                assertEquals(setOf(method), installed.keys)
            }
        }
    }

    @Test fun stepViewFollowsTheNativeCarrierGeometryAndPrivacyState() = withControlCenterHooks(install = {
        SystemUIControlCenterHooks.CCHeaderHook(it, hideOperator = false, hideDelimiter = false, showStep = true)
    }) { installed ->
        val context = RuntimeEnvironment.getApplication().createConfigurationContext(Configuration().apply { densityDpi = 240 })
        val owner = StatusHeaderController(context)
        val type = StatusHeaderController::class.java
        fun create() = installed.invoke(type.getDeclaredMethod("createStatusBarViews"), owner, proceed = owner::createStatusBarViews)
        fun update() = installed.invoke(type.getDeclaredMethod("updateConstraint"), owner, proceed = owner::updateConstraint)
        fun adjust() = installed.invoke(type.getDeclaredMethod("adjustCarrierOrPrompt"), owner, proceed = owner::adjustCarrierOrPrompt)
        val header = owner.getView()
        create()
        create()
        assertEquals(3, header.childCount)
        val step = header.getChildAt(2) as TextView
        val params = step.layoutParams as ConstraintLayout.LayoutParams
        assertEquals(View.GONE, step.visibility)

        update()
        adjust()
        assertEquals(View.VISIBLE, step.visibility)
        assertEquals(owner.carrier.id, params.endToStart)
        assertEquals(owner.icons.id, params.bottomToTop)
        assertEquals(owner.verticalBottomMargin, params.bottomMargin)
        assertEquals(6, params.marginEnd)
        val spec = MeasureSpec.makeMeasureSpec(400, MeasureSpec.EXACTLY)
        header.measure(spec, spec)
        header.layout(0, 0, 400, 400)
        update()
        assertFalse(step.isLayoutRequested)

        owner.privacyPrompt = true
        adjust()
        assertEquals(View.GONE, step.visibility)
        owner.privacyPrompt = false
        owner.vertical = false
        update()
        adjust()
        assertEquals(View.VISIBLE, owner.carrier.visibility)
        assertEquals(View.GONE, step.visibility)
        owner.vertical = true
        owner.verticalBottomMargin = 36
        update()
        adjust()
        assertEquals(36, params.bottomMargin)
        assertEquals(View.VISIBLE, step.visibility)

        val expand = type.getDeclaredMethod("onExpandChange", Float::class.javaPrimitiveType)
        installed.invoke(expand, owner, 14f) { owner.onExpandChange(14f) }
        assertEquals(owner.carrier.translationY, step.translationY, 0f)
    }

    @Test fun verticalStackKeepsTheLeftPanelAttachedAndHorizontalUsesNativeMargins() = withControlCenterHooks { installed ->
        val target = MainPanelController::class.java.getDeclaredMethod("setUseSeparatedPanels", Boolean::class.javaObjectType)
        val owner = MainPanelController(RuntimeEnvironment.getApplication())
        val stack = owner.horizontalMainPanel
        val left = owner.leftMainPanel
        var nativeCalls = 0
        fun setSeparated(value: Boolean?) = installed.invoke(target, owner, value) {
            nativeCalls++
            owner.setUseSeparatedPanels(value)
        }

        setSeparated(null)
        assertNull(left.parent)
        setSeparated(false)
        setSeparated(false)
        assertEquals(false, owner.useSeparatedPanels)
        assertSame(stack, left.parent)
        assertEquals(LinearLayout.VERTICAL, stack.orientation)
        assertEquals(1, stack.indexOfChild(left))

        setSeparated(true)
        assertEquals(LinearLayout.HORIZONTAL, stack.orientation)
        assertEquals(0, stack.indexOfChild(left))
        assertEquals(24, (left.layoutParams as LinearLayout.LayoutParams).marginEnd)
        val removals = stack.removals

        setSeparated(false)
        assertSame(stack, left.parent)
        assertEquals(1, stack.indexOfChild(left))
        assertEquals(LinearLayout.VERTICAL, stack.orientation)
        assertEquals(0, (left.layoutParams as LinearLayout.LayoutParams).marginEnd)
        assertEquals(removals, stack.removals)

        owner.configuredMargin = 36
        setSeparated(true)
        assertEquals(36, owner.panelMargin)
        assertEquals(36, (left.layoutParams as LinearLayout.LayoutParams).marginEnd)
        assertEquals(4, nativeCalls)
    }

    @Test fun onlyChangedStackedDimensionsRequestLayout() = withControlCenterHooks { installed ->
        val owner = MainPanelController(RuntimeEnvironment.getApplication())
        val target = MainPanelController::class.java.getDeclaredMethod("setUseSeparatedPanels", Boolean::class.javaObjectType)
        val resize = MainPanelController::class.java.getDeclaredMethod("updatePanelSize")
        val stack = owner.horizontalMainPanel
        val panels = listOf(owner.leftMainPanel, owner.rightMainPanel)
        val spec = MeasureSpec.makeMeasureSpec(400, MeasureSpec.EXACTLY)
        fun layout() {
            stack.measure(spec, spec)
            stack.layout(0, 0, 400, 400)
        }
        fun setSeparated(value: Boolean) = installed.invoke(target, owner, value) { owner.setUseSeparatedPanels(value) }
        var nativeCalls = 0
        fun updateSize() = installed.invoke(resize, owner) { nativeCalls++; owner.updatePanelSize() }

        setSeparated(false)
        layout()
        owner.panelWidth = 240
        updateSize()
        for (panel in panels) {
            assertEquals(240, panel.layoutParams.width)
            assertEquals(WRAP_CONTENT, panel.layoutParams.height)
            assertTrue(panel.isLayoutRequested)
        }
        layout()
        updateSize()
        assertTrue(panels.none { it.isLayoutRequested })
        assertEquals(0, nativeCalls)

        setSeparated(true)
        updateSize()
        assertEquals(1, nativeCalls)
        assertTrue(panels.all { it.layoutParams.height == MATCH_PARENT })
    }

    @Test fun nativeTileInflateAndResizeUseTheSameCustomGeometry() = withControlCenterHooks { installed ->
        val type = QSTileItemView::class.java
        val inflate = type.getDeclaredMethod("onFinishInflate")
        val updateHeight = type.getDeclaredMethod("updateContainerHeight").apply { isAccessible = true }
        val updateSize = type.getDeclaredMethod("updateSize").apply { isAccessible = true }
        val context = RuntimeEnvironment.getApplication()

        for ((density, iconSize, height) in listOf(Triple(240, 81, 103), Triple(320, 108, 138))) {
            val tile = QSTileItemView(context.createConfigurationContext(Configuration().apply { densityDpi = density }))
            fun updateNativeHeight() = installed.invoke(updateHeight, tile) { updateHeight.invoke(tile) }
            installed.invoke(inflate, tile) {
                tile.onFinishInflate()
                updateNativeHeight()
            }
            assertEquals(1, tile.expansions)
            assertEquals(height, tile.appliedHeight)
            assertEquals(iconSize, tile.iconFrame.layoutParams.width)
            installed.invoke(updateSize, tile) {
                updateNativeHeight()
                updateSize.invoke(tile)
            }
            assertEquals(2, tile.expansions)
            assertEquals(22, tile.labelHeight)
            assertEquals(height, tile.appliedHeight)
            assertEquals(iconSize, tile.iconFrame.layoutParams.width)
            assertEquals(iconSize, tile.iconFrame.layoutParams.height)
        }
    }

    @Test fun panelAnimationMirrorsOnlyChangedAlphaInNativeVerticalMode() = withControlCenterHooks { installed ->
        val config = Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        val context = RuntimeEnvironment.getApplication().createConfigurationContext(config)
        val owner = MainPanelAnimController(context)
        val animUpdate = MainPanelAnimController::class.java.getDeclaredMethod("onAnimUpdate")
        val configChanged = MainPanelAnimController::class.java.getDeclaredMethod("onConfigurationChanged", Int::class.javaPrimitiveType)
        fun animate() = installed.invoke(animUpdate, owner, proceed = owner::onAnimUpdate)
        try {
            owner.panelAlpha = 0.4f
            animate()
            assertEquals(1f, owner.leftMainPanel.alpha, 0f)

            CommonUtils.forceVertical = true
            animate()
            assertEquals(0.4f, owner.leftMainPanel.alpha, 0f)

            CommonUtils.modeReads = 0
            animate()
            assertEquals(1, CommonUtils.modeReads)

            CommonUtils.forceVertical = false
            installed.invoke(configChanged, owner, ActivityInfo.CONFIG_ORIENTATION) {
                owner.onConfigurationChanged(ActivityInfo.CONFIG_ORIENTATION)
            }
            assertEquals(1f, owner.leftMainPanel.alpha, 0f)
        } finally {
            CommonUtils.forceVertical = false
            CommonUtils.modeReads = 0
        }
    }

    @Test fun verticalRebuildMovesAvailableControllersInNativeOrder() = withControlCenterHooks(
        install = { SystemUIControlCenterHooks.hookPanelContent(it, 5, hideEdit = false, hideSecurity = false) },
    ) { installed ->
        val qs = QSListController()
        val edit = EditButtonController()
        val security = SecurityFooterController()
        val spacer = FooterSpaceController()
        val first = object : MainPanelContent {
            override val priority = 50
            override val onRight = false
        }
        val last = object : MainPanelContent { override val priority = 230 }
        val owner = MainPanelContentDistributor(listOf(last, security, spacer, edit, qs, first))
        val left = owner.leftPanelContent
        val right = owner.rightPanelContent

        installed.distribute(owner, horizontal = false)
        assertTrue(left.isEmpty() && right.isEmpty())
        owner.inited = true
        installed.distribute(owner, horizontal = true)
        assertEquals(listOf(first), left)
        assertEquals(listOf(qs, edit, security, spacer, last), right)
        installed.distribute(owner, horizontal = false)
        assertEquals(listOf(qs, edit, security), left)
        assertEquals(listOf(first, last), right)

        security.available = false
        installed.distribute(owner, horizontal = false)
        assertEquals(listOf(qs, edit), left)
        assertEquals(listOf(first, last), right)
    }

    @Test fun combinedOptionsKeepTheNativeSourceListAndBindOnlyTheLeftGrid() = withControlCenterHooks(
        install = { SystemUIControlCenterHooks.hookPanelContent(it, 5, hideEdit = true, hideSecurity = true) },
    ) { installed ->
        val qs = QSListController()
        val spacer = FooterSpaceController()
        val owner = MainPanelContentDistributor(listOf(EditButtonController(), SecurityFooterController(), qs, spacer))
        val source = owner.childControllers
        installed.invoke(owner.javaClass.declaredConstructors.single(), owner) {}
        assertSame(source, owner.childControllers)
        assertEquals(listOf(qs, spacer), source)
        assertEquals(5, owner.leftAdapter.layoutManager.columns)
        assertEquals(2, owner.rightAdapter.layoutManager.columns)

        owner.inited = true
        installed.distribute(owner, horizontal = true)
        assertEquals(listOf(qs, spacer), owner.rightPanelContent)
        installed.distribute(owner, horizontal = false)
        assertEquals(listOf(qs), owner.leftPanelContent)
        assertTrue(owner.rightPanelContent.isEmpty())
    }

    @Test fun customSpansReadEachLeftAdaptersCurrentContentAndKeepRightSpansNative() = withControlCenterHooks(
        install = { SystemUIControlCenterHooks.hookPanelContent(it, 5, hideEdit = false, hideSecurity = false) },
    ) { installed ->
        val first = MainPanelContentDistributor(emptyList())
        val second = MainPanelContentDistributor(emptyList())
        val constructor = MainPanelContentDistributor::class.java.declaredConstructors.single()
        for (owner in listOf(first, second)) {
            installed.invoke(constructor, owner) {}
            assertEquals(5, owner.leftAdapter.layoutManager.columns)
            assertEquals(2, owner.rightAdapter.layoutManager.columns)
        }
        first.leftAdapter.contentMap[0] = listOf(MainPanelListItem { 2 }, MainPanelListItem { 7 })
        first.leftAdapter.contentMap[1] = listOf(MainPanelListItem { 0 })
        second.leftAdapter.contentMap[0] = listOf(MainPanelListItem { 3 })
        first.rightAdapter.contentMap[0] = listOf(MainPanelListItem { 7 })
        var nativeCalls = 0
        fun span(adapter: MainPanelAdapter, position: Int): Any? {
            val lookup = adapter.layoutManager.spanSizeLookup
            val target = lookup.javaClass.getDeclaredMethod("getSpanSize", Int::class.javaPrimitiveType)
            return installed.invoke(target, lookup, position) {
                nativeCalls++
                lookup.getSpanSize(position)
            }
        }

        assertEquals(2, span(first.leftAdapter, 0))
        assertEquals(5, span(first.leftAdapter, 1))
        assertEquals(1, span(first.leftAdapter, 2))
        assertEquals(5, span(first.leftAdapter, 3))
        assertEquals(5, span(first.leftAdapter, -1))
        first.leftAdapter.contentMap = linkedMapOf(0 to listOf(MainPanelListItem { 5 }))
        assertEquals(5, span(first.leftAdapter, 0))
        assertEquals(3, span(second.leftAdapter, 0))
        assertEquals(0, nativeCalls)
        assertEquals(4, span(first.rightAdapter, 0))
        assertEquals(1, nativeCalls)
    }

    @Test fun failedSpanHookDoesNotApplyCustomColumnsOrDisableHiddenItems() = withControlCenterHooks(
        install = { SystemUIControlCenterHooks.hookPanelContent(it, 5, hideEdit = true, hideSecurity = false) },
        reject = { it.name == "getSpanSize" },
    ) { installed ->
        val tile = QSListController()
        val owner = MainPanelContentDistributor(listOf(tile, EditButtonController()))
        installed.invoke(owner.javaClass.declaredConstructors.single(), owner) {}
        assertEquals(listOf(tile), owner.childControllers)
        assertEquals(2, owner.leftAdapter.layoutManager.columns)
        assertTrue(installed.keys.none { it.name == "distributePanels" })
    }

    private fun Map<Executable, XposedInterface.Hooker>.distribute(owner: MainPanelContentDistributor, horizontal: Boolean) {
        val method = owner.javaClass.getDeclaredMethod("distributePanels", Boolean::class.javaPrimitiveType)
        invoke(method, owner, horizontal) { owner.distributePanels(horizontal) }
    }

    private fun withControlCenterHooks(
        install: (ClassLoader) -> Unit = { SystemUIControlCenterHooks.SystemCCGridHookLoader(it, 5) },
        reject: (Executable) -> Boolean = { false },
        test: (Map<Executable, XposedInterface.Hooker>) -> Unit,
    ) {
        val previous = XposedHelpers.moduleInst
        val installed = LinkedHashMap<Executable, XposedInterface.Hooker>()
        try {
            XposedHelpers.moduleInst = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface::class.java)) { _, call, args ->
                if (call.name != "hook") return@newProxyInstance null
                val target = args!![0] as Executable
                object : XposedInterface.HookBuilder {
                    override fun setPriority(priority: Int): XposedInterface.HookBuilder = this
                    override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = this
                    override fun setId(id: String?): XposedInterface.HookBuilder = error("No new ID")
                    override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                        check(!reject(target))
                        assertNull(installed.put(target, hooker))
                        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.HookHandle::class.java)) { _, _, _ -> null } as XposedInterface.HookHandle
                    }
                }
            } as XposedInterface
            install(javaClass.classLoader!!)
            test(installed)
        } finally {
            XposedHelpers.moduleInst = previous
        }
    }

    private fun Map<Executable, XposedInterface.Hooker>.invoke(
        member: Executable,
        owner: Any?,
        vararg args: Any?,
        rewrite: ((Array<Any?>) -> Any?)? = null,
        proceed: () -> Any?,
    ): Any? = getValue(member).intercept(object : XposedInterface.Chain {
        override fun getExecutable() = member
        override fun getThisObject() = owner
        override fun getArgs(): List<Any?> = if (rewrite != null) args.toList() else error("Read only the arguments used by the hook")
        override fun getArg(index: Int): Any? = args[index]
        override fun proceed(): Any? = proceed.invoke()
        override fun proceed(args: Array<Any?>): Any? = requireNotNull(rewrite)(args)
        override fun proceedWith(thisObject: Any): Any? = error("The hook must preserve the native owner")
        override fun proceedWith(thisObject: Any, args: Array<Any?>): Any? = error("The hook must preserve the native call")
    })
}
