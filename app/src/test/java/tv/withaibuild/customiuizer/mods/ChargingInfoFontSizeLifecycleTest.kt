package tv.withaibuild.customiuizer.mods

import android.util.TypedValue
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference

/**
 * Lifecycle and style-application tests for the lock-screen charging info font size fix.
 *
 * These tests verify the reversible state path: the View's original text size and single-line
 * state are captured on first apply, then restored when the feature is disabled or when a default
 * value is selected. They also verify that non-default values are applied and that the observer
 * re-apply path does not depend on the initial snapshot being dispatched (that is a separate
 * PreferenceBootstrap contract).
 */
class ChargingInfoFontSizeLifecycleTest {

    /**
     * Minimal [TextView] double that records the styling calls we care about.
     *
     * The real [TextView] lives in the framework; this records only what
     * [applyChargingInfoStyle] touches so the tests can verify idempotency,
     * fallback and reversibility without a device.
     */
    private class RecordingTextView : TextView(null) {
        var originalTextSize = 15f
        var currentTextSize = originalTextSize
        var originalSingleLine = true
        var currentSingleLine = originalSingleLine

        val setTextSizeCalls = mutableListOf<Pair<Int, Float>>()
        val setSingleLineCalls = mutableListOf<Boolean>()
        val postCalls = mutableListOf<Runnable?>()

        override fun getTextSize(): Float = currentTextSize

        override fun isSingleLine(): Boolean = currentSingleLine

        override fun setTextSize(unit: Int, size: Float) {
            setTextSizeCalls.add(unit to size)
            currentTextSize = size
        }

        override fun setSingleLine(singleLine: Boolean) {
            setSingleLineCalls.add(singleLine)
            currentSingleLine = singleLine
        }

        override fun post(action: Runnable?): Boolean {
            postCalls.add(action)
            action?.run()
            return true
        }
    }

    @Test
    fun applyChargingInfoStyle_defaultFontSize_restoresOriginalTextSize() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 16)
            put("system_charginginfo_view", 1)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        // First call captures the original size and then, because the value is 16 (default),
        // restores it.
        val last = textView.setTextSizeCalls.last()
        assertEquals(TypedValue.COMPLEX_UNIT_PX, last.first)
        assertEquals(textView.originalTextSize, last.second, 0.001f)
    }

    @Test
    fun applyChargingInfoStyle_nonDefaultFontSize_appliesSpSize() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 2)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        assertEquals(1, textView.setTextSizeCalls.size)
        val (unit, size) = textView.setTextSizeCalls[0]
        assertEquals(TypedValue.COMPLEX_UNIT_SP, unit)
        assertEquals(10.0f, size, 0.001f)
    }

    @Test
    fun applyChargingInfoStyle_fontSizeRoundTrip_restoresOriginal() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_view", 2)
        }

        // Apply custom size.
        prefs.put("system_charginginfo_fontsize", 20)
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        assertEquals(10.0f, textView.currentTextSize, 0.001f)

        // Return to default; original must be restored.
        prefs.put("system_charginginfo_fontsize", 16)
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        val last = textView.setTextSizeCalls.last()
        assertEquals(TypedValue.COMPLEX_UNIT_PX, last.first)
        assertEquals(textView.originalTextSize, last.second, 0.001f)
        assertEquals(textView.originalTextSize, textView.currentTextSize, 0.001f)
    }

    @Test
    fun applyChargingInfoStyle_singleLineRoundTrip_restoresOriginal() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
        }

        // Opt 1 forces single-line false.
        prefs.put("system_charginginfo_view", 1)
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        assertEquals(false, textView.currentSingleLine)

        // Opt 2 restores the original single-line state.
        prefs.put("system_charginginfo_view", 2)
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        assertEquals(textView.originalSingleLine, textView.currentSingleLine)
        assertEquals(listOf(false, textView.originalSingleLine), textView.setSingleLineCalls)
    }

    @Test
    fun applyChargingInfoStyle_featureDisabledRoundTrip_restoresOriginalState() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }

        // First enable with custom values.
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        assertEquals(10.0f, textView.currentTextSize, 0.001f)
        assertEquals(false, textView.currentSingleLine)

        // Then disable; everything must return to the original captured values.
        prefs.put("system_charginginfo", false)
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        assertEquals(textView.originalTextSize, textView.currentTextSize, 0.001f)
        assertEquals(textView.originalSingleLine, textView.currentSingleLine)
        val lastSize = textView.setTextSizeCalls.last()
        assertEquals(TypedValue.COMPLEX_UNIT_PX, lastSize.first)
        assertEquals(textView.originalTextSize, lastSize.second, 0.001f)
    }

    @Test
    fun applyChargingInfoStyle_reEnableAfterDisable_usesCurrentPrefs() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", false)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }

        // Disabled: original state.
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        assertEquals(textView.originalTextSize, textView.currentTextSize, 0.001f)

        // Re-enable with custom values.
        prefs.put("system_charginginfo", true)
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        assertEquals(10.0f, textView.currentTextSize, 0.001f)
        assertEquals(false, textView.currentSingleLine)
    }

    @Test
    fun applyChargingInfoStyle_repeatedCallsAreIdempotent() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 24)
            put("system_charginginfo_view", 2)
        }
        repeat(3) {
            SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        }

        assertEquals("repeated apply must not produce cumulative scaling", 3, textView.setTextSizeCalls.size)
        for ((_, size) in textView.setTextSizeCalls) {
            assertEquals(12.0f, size, 0.001f)
        }
    }

    @Test
    fun applyChargingInfoStyle_configChangeUsesLatestValue() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 18)
            put("system_charginginfo_view", 2)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        assertEquals(9.0f, textView.setTextSizeCalls.last().second, 0.001f)

        prefs.put("system_charginginfo_fontsize", 22)
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        assertEquals(11.0f, textView.setTextSizeCalls.last().second, 0.001f)
    }

    @Test
    fun applyChargingInfoStyle_invalidFontSize_restoresOriginalTextSize() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 41)
            put("system_charginginfo_view", 2)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        // Out-of-range values must not leave the custom size; original must be restored.
        val last = textView.setTextSizeCalls.last()
        assertEquals(TypedValue.COMPLEX_UNIT_PX, last.first)
        assertEquals(textView.originalTextSize, last.second, 0.001f)
    }

    @Test
    fun applyChargingInfoStyle_capturesOriginalStyleExactlyOnce() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }

        // Capture the original state.
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        // Change the view's reported original state to a sentinel value.
        textView.originalTextSize = 99f
        textView.originalSingleLine = false

        // A second call must still use the values captured on the first call, not the new ones.
        prefs.put("system_charginginfo_fontsize", 16)
        prefs.put("system_charginginfo_view", 2)
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        val lastSize = textView.setTextSizeCalls.last()
        assertEquals(15.0f, lastSize.second, 0.001f)
        assertEquals(true, textView.currentSingleLine)
    }

    @Test
    fun applyChargingInfoStyle_originalFieldsStoredInAdditionalInstanceField() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 2)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        assertNotNull(tv.withaibuild.customiuizer.mods.utils.XposedHelpers.getAdditionalInstanceField(
            textView, "charging_info_original_text_size"
        ))
        assertNotNull(tv.withaibuild.customiuizer.mods.utils.XposedHelpers.getAdditionalInstanceField(
            textView, "charging_info_original_single_line"
        ))
    }

    @Test
    fun observerCallback_usesProductionFactory() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }

        val observer = SystemLockScreenHooks.createChargingInfoPreferenceObserver(textView, prefs)

        observer.onChange("system_charginginfo")
        assertEquals(10.0f, textView.currentTextSize, 0.001f)
        assertEquals(false, textView.currentSingleLine)
    }

    @Test
    fun productionObserver_masterToggleTrueFalseTrueThroughObserver() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }

        val observer = SystemLockScreenHooks.createChargingInfoPreferenceObserver(textView, prefs)

        // Feature enabled: custom style applied.
        observer.onChange("system_charginginfo")
        assertEquals(10.0f, textView.currentTextSize, 0.001f)
        assertEquals(false, textView.currentSingleLine)

        // Master switch off: original style restored.
        prefs.put("system_charginginfo", false)
        observer.onChange("system_charginginfo")
        assertEquals(textView.originalTextSize, textView.currentTextSize, 0.001f)
        assertEquals(textView.originalSingleLine, textView.currentSingleLine)

        // Master switch on again: current prefs re-applied.
        prefs.put("system_charginginfo", true)
        observer.onChange("system_charginginfo")
        assertEquals(10.0f, textView.currentTextSize, 0.001f)
        assertEquals(false, textView.currentSingleLine)
    }

    @Test
    fun productionObserver_fontSizeCustomThenDefault_restoresOriginal() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_view", 2)
        }

        val observer = SystemLockScreenHooks.createChargingInfoPreferenceObserver(textView, prefs)

        prefs.put("system_charginginfo_fontsize", 20)
        observer.onChange("system_charginginfo_fontsize")
        assertEquals(10.0f, textView.currentTextSize, 0.001f)

        prefs.put("system_charginginfo_fontsize", 16)
        observer.onChange("system_charginginfo_fontsize")
        assertEquals(textView.originalTextSize, textView.currentTextSize, 0.001f)
    }

    @Test
    fun productionObserver_opt1ThenOpt2_restoresSingleLine() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }

        val observer = SystemLockScreenHooks.createChargingInfoPreferenceObserver(textView, prefs)

        observer.onChange("system_charginginfo_view")
        assertEquals(false, textView.currentSingleLine)

        prefs.put("system_charginginfo_view", 2)
        observer.onChange("system_charginginfo_view")
        assertEquals(textView.originalSingleLine, textView.currentSingleLine)
    }

    @Test
    fun productionObserver_originalSingleLineFalse_opt1KeepsFalse_opt2RestoresFalse() {
        val textView = RecordingTextView()
        textView.originalSingleLine = false
        textView.currentSingleLine = false

        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }

        val observer = SystemLockScreenHooks.createChargingInfoPreferenceObserver(textView, prefs)

        observer.onChange("system_charginginfo_fontsize")
        assertEquals(false, textView.currentSingleLine)

        prefs.put("system_charginginfo_view", 2)
        observer.onChange("system_charginginfo_view")
        assertEquals(false, textView.currentSingleLine)
        assertTrue(textView.setSingleLineCalls.all { it == false })
    }

    @Test
    fun productionObserver_unrelatedKey_doesNotModifyView() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }

        val observer = SystemLockScreenHooks.createChargingInfoPreferenceObserver(textView, prefs)

        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        textView.setTextSizeCalls.clear()
        textView.setSingleLineCalls.clear()

        observer.onChange("system_statusbarheight")

        assertTrue("unrelated key must not modify text size", textView.setTextSizeCalls.isEmpty())
        assertTrue("unrelated key must not modify single-line", textView.setSingleLineCalls.isEmpty())
    }

    @Test
    fun productionObserver_viewWeakReferenceCleared_returnsSafely() {
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }

        val observer = SystemLockScreenHooks.createChargingInfoPreferenceObserver(RecordingTextView(), prefs)
        val viewRefField = observer.javaClass.declaredFields.first { it.type == WeakReference::class.java }
            .apply { isAccessible = true }
        (viewRefField.get(observer) as WeakReference<*>).clear()

        observer.onChange("system_charginginfo")
        // No exception and no action is the passing condition.
    }

    @Test
    fun productionObserver_repeatedSameKey_doesNotAccumulateSize() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 2)
        }

        val observer = SystemLockScreenHooks.createChargingInfoPreferenceObserver(textView, prefs)

        repeat(3) { observer.onChange("system_charginginfo_fontsize") }

        assertEquals(3, textView.setTextSizeCalls.size)
        for ((_, size) in textView.setTextSizeCalls) {
            assertEquals(10.0f, size, 0.001f)
        }
        assertEquals(10.0f, textView.currentTextSize, 0.001f)
    }

    @Test
    fun productionObserver_postsExactlyOncePerRelevantKey() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }

        val observer = SystemLockScreenHooks.createChargingInfoPreferenceObserver(textView, prefs)

        observer.onChange("system_charginginfo")
        assertEquals(1, textView.postCalls.size)

        observer.onChange("system_statusbarheight")
        assertEquals(1, textView.postCalls.size)
    }

    @Test
    fun productionObserver_doesNotHoldTextViewStrongly() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", true)
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }

        val observer = SystemLockScreenHooks.createChargingInfoPreferenceObserver(textView, prefs)

        for (field in observer.javaClass.declaredFields) {
            field.isAccessible = true
            assertFalse(
                "observer must not hold a TextView strong reference",
                TextView::class.java.isAssignableFrom(field.type)
            )
        }
    }
}
