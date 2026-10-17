package tv.withaibuild.customiuizer.mods.battery

import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URLDecoder
import java.lang.reflect.Modifier
import tv.withaibuild.customiuizer.mods.SystemUIBatteryHooks
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.battery.testfixtures.BaseBatteryView
import tv.withaibuild.customiuizer.mods.battery.testfixtures.FakeTextView
import tv.withaibuild.customiuizer.mods.battery.testfixtures.ShadowedBatteryView
import tv.withaibuild.customiuizer.mods.battery.testfixtures.SubBatteryView
import java.lang.reflect.Field

/**
 * Runtime tests for the Battery Style Architecture C migration:
 * Resolver, frozen ABI, FAST/legacy mode selection, field shadowing, and fatal boundaries.
 */
class BatteryArchitectureCTest {


    // -------------------------------------------------------------------------
    // A. Exact runtime target class -> FAST selected and returns legacy value.
    // -------------------------------------------------------------------------
    @Test
    fun exactTargetClass_selectsFastPath_andReturnsSameValueAsLegacy() {
        val view = BaseBatteryView()
        view.setupChildrenInOemOrder()

        val abi = resolveAbiFor(BaseBatteryView::class.java)
        val effect = BatteryStyleEffect(abi)

        assertTrue("exact owner class must use FAST path", effect.useFastPath(view))

        val fastPercent = effect.readPercentView(view, true)
        val legacyPercent = XposedHelpers.getObjectField(view, "mBatteryPercentView") as? TextView
        assertSame("FAST path must return the same object as legacy lookup", legacyPercent, fastPercent)

        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view, effect)
        assertNotNull("FAST baseline capture must succeed", baseline)
    }

    // -------------------------------------------------------------------------
    // B. Runtime subclass with target fields present -> legacy fallback selected.
    // -------------------------------------------------------------------------
    @Test
    fun runtimeSubclass_doesNotSelectFastPath() {
        val baseAbi = resolveAbiFor(BaseBatteryView::class.java)
        val effect = BatteryStyleEffect(baseAbi)

        val subView = SubBatteryView()
        subView.setupChildrenInOemOrder()

        assertFalse("subclass owner must not use FAST path", effect.useFastPath(subView))

        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(subView, effect)
        assertNotNull("legacy fallback on subclass must still capture baseline", baseline)
    }

    // -------------------------------------------------------------------------
    // C. Subclass shadows exact same-name field -> fallback reads subclass field.
    // -------------------------------------------------------------------------
    @Test
    fun runtimeSubclassShadowing_fallbackPreservesRuntimeOwnerPrecedence() {
        val baseAbi = resolveAbiFor(BaseBatteryView::class.java)
        val effect = BatteryStyleEffect(baseAbi)

        val view = ShadowedBatteryView()
        view.setupChildrenInOemOrder()

        // The shadow field on the subclass is intentionally a distinct object.
        val shadowPercent = FakeTextView()
        view.mBatteryPercentView = shadowPercent

        assertFalse("shadowed subclass must use legacy fallback", effect.useFastPath(view))

        val fallbackPercent = effect.readPercentView(view, false)
        assertSame("fallback must read the subclass-shadowed field", shadowPercent, fallbackPercent)

        // Sanity: fast path must read the base-class value, which is a distinct object
        // from the shadow field.
        val fastPercent = effect.readPercentView(view, true)
        assertNotNull("fast path must return the base-class value", fastPercent)
        val basePercent = BaseBatteryView::class.java.getDeclaredField("mBatteryPercentView").get(view) as? TextView
        assertSame("fast path must return the base-class value", basePercent, fastPercent)
    }

    // -------------------------------------------------------------------------
    // D. Subclass declares a field only on the subclass -> legacy fallback succeeds.
    // -------------------------------------------------------------------------
    @Test
    fun subclassOnlyField_legacyFallbackSucceeds() {
        val subclassOnlyView = SubclassOnlyBatteryView()
        subclassOnlyView.mBatteryTextDigitView = FakeTextView()
        subclassOnlyView.mBatteryPercentView = FakeTextView()
        subclassOnlyView.mBatteryPercentMarkView = FakeTextView()

        // No ABI: the resolver would have returned null because the target class does not
        // have the fields. The fallback-only effect must still work.
        val effect = BatteryStyleEffect(null)

        assertFalse("null ABI must select legacy fallback", effect.useFastPath(subclassOnlyView))

        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(subclassOnlyView, effect)
        assertNotNull("legacy fallback must capture baseline for subclass-only fields", baseline)
    }

    // -------------------------------------------------------------------------
    // E. Declared field type View/Object with runtime TextView -> supported.
    // -------------------------------------------------------------------------
    @Test
    fun fieldDeclaredAsSupertype_runtimeTextView_supported() {
        val view = BaseBatteryViewAsView()
        view.mBatteryTextDigitView = FakeTextView()
        view.mBatteryPercentView = FakeTextView()
        view.mBatteryPercentMarkView = FakeTextView()

        val abi = resolveAbiFor(BaseBatteryViewAsView::class.java)
        val effect = BatteryStyleEffect(abi)

        assertTrue("exact owner class must use FAST path", effect.useFastPath(view))

        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view, effect)
        assertNotNull("FAST path must work with fields declared as View/Object", baseline)
    }

    // -------------------------------------------------------------------------
    // F. Runtime field value non-TextView -> safe-cast no-op.
    // -------------------------------------------------------------------------
    @Test
    fun nonTextViewFieldValue_safeCastNoOp() {
        val view = BaseBatteryViewAsView()
        view.mBatteryTextDigitView = FakeTextView()
        view.mBatteryPercentView = NonTextView()
        view.mBatteryPercentMarkView = FakeTextView()

        val abi = resolveAbiFor(BaseBatteryViewAsView::class.java)
        val effect = BatteryStyleEffect(abi)

        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view, effect)
        assertNull("non-TextView field must short-circuit capture", baseline)
    }

    // -------------------------------------------------------------------------
    // G. Runtime field value null -> safe-cast no-op.
    // -------------------------------------------------------------------------
    @Test
    fun nullFieldValue_safeCastNoOp() {
        val view = BaseBatteryView()
        view.setupChildrenInOemOrder()
        view.mBatteryPercentView = null

        val abi = resolveAbiFor(BaseBatteryView::class.java)
        val effect = BatteryStyleEffect(abi)

        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view, effect)
        assertNull("null field must short-circuit capture", baseline)
    }

    // -------------------------------------------------------------------------
    // H. Frozen target ABI resolve success.
    // -------------------------------------------------------------------------
    @Test
    fun resolver_returnsFrozenAbiForTargetClassWithAllFields() {
        val abi = BatteryStyleResolver.resolve(
            BaseBatteryView::class.java.classLoader,
            BaseBatteryView::class.java.name,
        )

        assertNotNull("resolver must return ABI when all three fields exist", abi)
        assertEquals(BaseBatteryView::class.java, abi!!.resolutionRootClass)
        assertEquals("mBatteryTextDigitView", abi.digitField.name)
        assertEquals("mBatteryPercentView", abi.percentField.name)
        assertEquals("mBatteryPercentMarkView", abi.markField.name)
    }

    // -------------------------------------------------------------------------
    // I. Target-class field miss -> ABI unavailable, fallback selected.
    // -------------------------------------------------------------------------
    @Test
    fun resolver_returnsNullWhenTargetClassMissesField() {
        val abi = BatteryStyleResolver.resolve(
            BaseBatteryViewMissingMark::class.java.classLoader,
            BaseBatteryViewMissingMark::class.java.name,
        )

        assertNull("resolver must return null when one field is missing", abi)
    }

    // -------------------------------------------------------------------------
    // J. BatteryStyle identity refresh still forces apply.
    // -------------------------------------------------------------------------
    @Test
    fun batteryStyleIdentityRefresh_forcesReApply() {
        val view = BaseBatteryView()
        view.setupChildrenInOemOrder()

        val effect = BatteryStyleEffect(null)
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view, effect)!!

        val state = SystemUIBatteryHooks.BatteryViewState()
        state.baseline = baseline

        val firstStyle = SystemUIBatteryHooks.BatteryStyle(
            swap = true,
            fontSizeDp = 12.0f,
            markFontSizeDp = 10.0f,
            bold = true,
            leftMarginDp = 6f,
            rightMarginDp = 4f,
            verticalOffset = 12,
            markVerticalOffset = 20,
            battery4 = false,
        )

        SystemUIBatteryHooks.reconcileBatteryView(view, firstStyle, state)
        state.appliedStyle = firstStyle

        val secondStyle = SystemUIBatteryHooks.BatteryStyle(
            swap = firstStyle.swap,
            fontSizeDp = firstStyle.fontSizeDp,
            markFontSizeDp = firstStyle.markFontSizeDp,
            bold = firstStyle.bold,
            leftMarginDp = firstStyle.leftMarginDp,
            rightMarginDp = firstStyle.rightMarginDp,
            verticalOffset = firstStyle.verticalOffset,
            markVerticalOffset = firstStyle.markVerticalOffset,
            battery4 = firstStyle.battery4,
        )
        assertTrue("identical values but new instance must be treated as different", firstStyle !== secondStyle)

        view.resetMutationCount()
        SystemUIBatteryHooks.reconcileBatteryView(view, secondStyle, state)
        assertTrue("new identity must force re-apply", state.appliedStyle === secondStyle)
    }

    // -------------------------------------------------------------------------
    // K. Child replacement recaptures baseline.
    // -------------------------------------------------------------------------
    @Test
    fun childReplacement_recapturesBaseline() {
        val view = BaseBatteryView()
        view.setupChildrenInOemOrder()

        val effect = BatteryStyleEffect(null)
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view, effect)!!

        val newMark = FakeTextView()
        newMark.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseline.markTextSize)
        view.removeView(view.mBatteryPercentMarkView)
        view.mBatteryPercentMarkView = newMark
        view.addView(newMark)

        assertTrue("child identity change must be detected", SystemUIBatteryHooks.childIdentitiesChanged(view, baseline.childIds))

        val newBaseline = SystemUIBatteryHooks.captureBatteryBaseline(view, effect)!!
        assertEquals(view.indexOfChild(newMark), newBaseline.markIndex)
    }

    // -------------------------------------------------------------------------
    // L. Swap idempotence.
    // -------------------------------------------------------------------------
    @Test
    fun swapIsIdempotent() {
        val view = BaseBatteryView()
        view.setupChildrenInOemOrder()

        val effect = BatteryStyleEffect(null)
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view, effect)!!

        val swapStyle = SystemUIBatteryHooks.BatteryStyle(
            swap = true,
            fontSizeDp = 7.5f,
            markFontSizeDp = 7.5f,
            bold = false,
            leftMarginDp = 0f,
            rightMarginDp = 0f,
            verticalOffset = 8,
            markVerticalOffset = 17,
            battery4 = false,
        )

        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, swapStyle, effect)
        assertEquals(0, view.indexOfChild(view.mBatteryPercentView))
        assertEquals(1, view.indexOfChild(view.mBatteryPercentMarkView))

        view.resetMutationCount()
        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, swapStyle, effect)
        assertEquals("second swap must not add/remove views", 0, view.mutationCount)
    }

    // -------------------------------------------------------------------------
    // M. battery4 margin routing.
    // -------------------------------------------------------------------------
    @Test
    fun battery4RightMarginRoutedToPercent() {
        val view = BaseBatteryView()
        view.setupChildrenInOemOrder()

        val effect = BatteryStyleEffect(null)
        val baseline = SystemUIBatteryHooks.captureBatteryBaseline(view, effect)!!

        val battery4Style = SystemUIBatteryHooks.BatteryStyle(
            swap = false,
            fontSizeDp = 7.5f,
            markFontSizeDp = 7.5f,
            bold = false,
            leftMarginDp = 0f,
            rightMarginDp = 6f,
            verticalOffset = 8,
            markVerticalOffset = 17,
            battery4 = true,
        )

        SystemUIBatteryHooks.applyBatteryStyle(view, baseline, battery4Style, effect)

        val percentPad = paddingOf(view.mBatteryPercentView)
        val markPad = paddingOf(view.mBatteryPercentMarkView)

        assertTrue("percent view should carry the right margin", percentPad.end > 0)
        assertEquals("mark view should not carry the right margin", 0, markPad.end)
    }

    // -------------------------------------------------------------------------
    // N. FAST Field.get IllegalAccess handling.
    //
    // Note: this path is preserved in BatteryStyleEffect for defensive parity, but it is
    // not feasible to trigger IllegalAccessException in unit tests because XposedHelpers.findField
    // calls Field.setAccessible(true) and the fields used in the fixtures are public. The
    // IllegalAccessException -> IllegalAccessError mapping is covered by source inspection.
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // C (revised). Resolver missing target class -> null/fallback.
    // -------------------------------------------------------------------------
    @Test
    fun resolverMissingClass_returnsNullForFallback() {
        val abi = BatteryStyleResolver.resolve(
            BaseBatteryView::class.java.classLoader,
            "this.class.does.not.Exist",
        )
        assertNull("missing class must select fallback", abi)
    }

    // -------------------------------------------------------------------------
    // D (revised). Resolver missing each exact field -> null/fallback.
    // -------------------------------------------------------------------------
    @Test
    fun resolverMissingDigitField_returnsNullForFallback() {
        val abi = BatteryStyleResolver.resolve(
            BaseBatteryViewMissingDigit::class.java.classLoader,
            BaseBatteryViewMissingDigit::class.java.name,
        )
        assertNull("missing digit field must select fallback", abi)
    }

    @Test
    fun resolverMissingPercentField_returnsNullForFallback() {
        val abi = BatteryStyleResolver.resolve(
            BaseBatteryViewMissingPercent::class.java.classLoader,
            BaseBatteryViewMissingPercent::class.java.name,
        )
        assertNull("missing percent field must select fallback", abi)
    }

    @Test
    fun resolverMissingMarkField_returnsNullForFallback() {
        val abi = BatteryStyleResolver.resolve(
            BaseBatteryViewMissingMark::class.java.classLoader,
            BaseBatteryViewMissingMark::class.java.name,
        )
        assertNull("missing mark field must select fallback", abi)
    }

    // -------------------------------------------------------------------------
    // E (revised). Resolver fatal boundary -> does not swallow fatal.
    // -------------------------------------------------------------------------
    @Test(expected = OutOfMemoryError::class)
    fun resolverFatal_propaatesImmediately() {
        val throwingLoader = object : ClassLoader(BaseBatteryView::class.java.classLoader) {
            override fun loadClass(name: String?): Class<*> {
                throw OutOfMemoryError("fatal class load failure")
            }
        }
        BatteryStyleResolver.resolve(throwingLoader, BaseBatteryView::class.java.name)
    }

    // -------------------------------------------------------------------------
    // A (revised). Production callback wiring uses captured effect, not mutable global state.
    // -------------------------------------------------------------------------
    @Test
    fun noMutableGlobalBatteryStyleEffect() {
        val mutableEffectFields = SystemUIBatteryHooks::class.java.declaredFields.filter {
            BatteryStyleEffect::class.java.isAssignableFrom(it.type) && !Modifier.isFinal(it.modifiers)
        }
        assertTrue(
            "SystemUIBatteryHooks must not expose a mutable BatteryStyleEffect field; use a hook-local capture",
            mutableEffectFields.isEmpty(),
        )
    }

    // -------------------------------------------------------------------------
    // B (revised). reconcileBatteryView receives an explicit Effect and passes it to helpers.
    // -------------------------------------------------------------------------
    @Test
    fun reconcileBatteryView_usesSuppliedEffect() {
        val view = BaseBatteryView()
        view.setupChildrenInOemOrder()

        val abi = resolveAbiFor(BaseBatteryView::class.java)
        val effect = BatteryStyleEffect(abi)
        assertTrue("test fixture must be eligible for FAST path", effect.useFastPath(view))

        val state = SystemUIBatteryHooks.BatteryViewState()
        val style = SystemUIBatteryHooks.BatteryStyle(
            swap = false,
            fontSizeDp = 12.0f,
            markFontSizeDp = 10.0f,
            bold = false,
            leftMarginDp = 6f,
            rightMarginDp = 4f,
            verticalOffset = 12,
            markVerticalOffset = 20,
            battery4 = false,
        )

        SystemUIBatteryHooks.reconcileBatteryView(view, style, state, effect)

        assertTrue("reconcile must apply the supplied style", state.appliedStyle === style)
        assertEquals(12.0f * 2.0f, view.mBatteryTextDigitView.textSize, 0.001f)
        assertEquals(12.0f * 2.0f, view.mBatteryPercentView.textSize, 0.001f)
    }

    // -------------------------------------------------------------------------
    // S. Structural source invariants.
    // -------------------------------------------------------------------------
    @Test
    fun fastPathSourceDoesNotContainXposedGetObjectField() {
        val source = sourceOf(BatteryStyleEffect::class.java)
        assumeTrue("source file must be available in the workspace", source != null)
        val body = source!!.substringAfter("private fun readFast(").substringBefore("private fun readLegacy(")
        assertFalse(
            "FAST path readFast must not call XposedHelpers.getObjectField",
            body.contains("XposedHelpers.getObjectField"),
        )
    }

    @Test
    fun hookCallbackCapturesLocalEffectSource() {
        val source = sourceOf(SystemUIBatteryHooks::class.java)
        assumeTrue("source file must be available in the workspace", source != null)
        val body = source!!.substringAfter("fun StatusBarStyleBatteryIconHook(").substringBefore("private fun isBatteryStyleDefault")
        assertTrue(
            "StatusBarStyleBatteryIconHook must create a local effect and not use a mutable global",
            body.contains("val effect = BatteryStyleEffect(") &&
                body.contains("reconcileBatteryView(batteryView, style, state, effect)"),
        )
        assertFalse(
            "StatusBarStyleBatteryIconHook must not contain the old mutable batteryStyleEffect field",
            body.contains("batteryStyleEffect"),
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun resolveAbiFor(clazz: Class<*>): BatteryStyleAbi {
        return BatteryStyleResolver.resolve(clazz.classLoader, clazz.name)!!
    }

    private fun paddingOf(view: TextView): SystemUIBatteryHooks.Padding {
        return SystemUIBatteryHooks.Padding(
            view.paddingStart,
            view.paddingTop,
            view.paddingEnd,
            view.paddingBottom,
        )
    }

    /**
     * A minimal subclass-only view for test D.
     */
    @Suppress("Unused")
    private class SubclassOnlyBatteryView : LinearLayout(null as android.content.Context?) {
        lateinit var mBatteryTextDigitView: TextView
        lateinit var mBatteryPercentView: TextView
        lateinit var mBatteryPercentMarkView: TextView
    }

    /**
     * View that declares the three fields as {@link View} but holds {@link TextView} values.
     */
    @Suppress("Unused")
    private class BaseBatteryViewAsView : LinearLayout(null as android.content.Context?) {
        lateinit var mBatteryTextDigitView: View
        lateinit var mBatteryPercentView: View
        lateinit var mBatteryPercentMarkView: View
    }

    /**
     * View that is missing the digit field for resolver field-miss tests.
     */
    @Suppress("Unused")
    private class BaseBatteryViewMissingDigit : LinearLayout(null as android.content.Context?) {
        lateinit var mBatteryPercentView: TextView
        lateinit var mBatteryPercentMarkView: TextView
    }

    /**
     * View that is missing the percent field for resolver field-miss tests.
     */
    @Suppress("Unused")
    private class BaseBatteryViewMissingPercent : LinearLayout(null as android.content.Context?) {
        lateinit var mBatteryTextDigitView: TextView
        lateinit var mBatteryPercentMarkView: TextView
    }

    /**
     * View that is missing the mark field for resolver field-miss tests.
     */
    @Suppress("Unused")
    private class BaseBatteryViewMissingMark : LinearLayout(null as android.content.Context?) {
        lateinit var mBatteryTextDigitView: TextView
        lateinit var mBatteryPercentView: TextView
    }

    private fun sourceOf(clazz: Class<*>): String? {
        val resourcePath = clazz.name.replace('.', '/') + ".class"
        val loader = javaClass.classLoader ?: return null
        val url = loader.getResource(resourcePath) ?: return null
        // Decode the file path from the URL, handling spaces and percent-escapes.
        val classFile = File(URLDecoder.decode(url.file, "UTF-8"))
        var dir: File? = classFile.parentFile
        var safety = 0
        while (dir != null && safety < 20) {
            val srcMain = File(dir, "src/main/java")
            if (srcMain.isDirectory) {
                val relative = clazz.name.replace('.', '/') + ".kt"
                val kt = File(srcMain, relative)
                if (kt.exists()) return kt.readText()
            }
            dir = dir.parentFile
            safety++
        }
        return null
    }

    private class NonTextView : View(null as android.content.Context?)
}
