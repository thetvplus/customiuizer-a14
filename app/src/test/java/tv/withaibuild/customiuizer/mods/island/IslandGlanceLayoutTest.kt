package tv.withaibuild.customiuizer.mods.island

import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "mdpi", shadows = [IslandTextLayoutShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandGlanceLayoutTest {
    @Test fun oneWidthSearchPreparesOnePaintAndCachedWidthDoesNoNewLayoutWork() {
        val shell = IslandActivityView(RuntimeEnvironment.getApplication())
        IslandTextLayoutShadow.reset()
        val width = shell.toastWidth("已删除", 96, 400, 40, 40, expanded = false)
        assertTrue(IslandTextLayoutShadow.probes in 2..14)
        assertEquals("One width search must share its prepared paint across real layout probes",
            1, IslandTextLayoutShadow.paints.size)
        val probes = IslandTextLayoutShadow.probes
        val firstPaint = IslandTextLayoutShadow.paints.single()
        assertEquals(width, shell.toastWidth("已删除", 96, 400, 40, 40, expanded = false))
        assertEquals(probes, IslandTextLayoutShadow.probes)
        assertSame(firstPaint, IslandTextLayoutShadow.paints.single())
        shell.contentHeight(64)
        shell.toastWidth("已删除", 96, 400, 40, 64, expanded = true)
        assertTrue(IslandTextLayoutShadow.probes > probes)
        assertEquals("A new font/shape query owns a fresh local paint", 2, IslandTextLayoutShadow.paints.size)
    }

    @Test fun nativeFitBoundariesRemainMinimalAcrossLocalesFontScalesAndFeedbackForms() {
        val app = RuntimeEnvironment.getApplication()
        for (locale in listOf("zh-CN", "en", "ar")) for (scale in listOf(1f, 2f)) {
            val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)); fontScale = scale }
            val shell = IslandActivityView(app.createConfigurationContext(config))
            for (expanded in listOf(false, true)) {
                val height = if (expanded) 64 else 40
                shell.contentHeight(height)
                for (text in listOf("已删除", "Saved to gallery", "تم الحفظ", "👨‍👩‍👧‍👦 saved", "This feedback needs two lines to fit")) {
                    val width = shell.toastWidth(text, 96, 360, 40, height, expanded)
                    val fits = shell.fitsToast(text, width, 40, height, expanded)
                    if (fits && width > 96) assertFalse("$locale $scale $expanded $text $width",
                        shell.fitsToast(text, width - 1, 40, height, expanded))
                    if (!fits) assertEquals(360, width)
                }
            }
        }
        IslandTextLayoutShadow.reset()
        val shell = IslandActivityView(app)
        assertEquals(8, shell.toastWidth("Saved", 1, 8, 40, 40, expanded = false))
        assertEquals(0, IslandTextLayoutShadow.probes)
    }

    @Test fun repeatedGeometryKeepsTextLayoutSettledAndRealRowChangesStillApply() {
        val shell = IslandActivityView(RuntimeEnvironment.getApplication()).apply { simplified = true }
        shell.bind("Notice", "Public message", null, false, false, false, 0, false, expanded = true)
        shell.geometry(280, 40, 40)
        val title = (shell.getChildAt(1) as LinearLayout).getChildAt(0) as TextView
        fun layout(height: Int) {
            shell.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            shell.layout(0, 0, 280, height)
        }
        layout(40)
        assertEquals(2, title.maxLines)
        assertFalse(title.isLayoutRequested)
        var redundantLayouts = 0
        repeat(120) {
            shell.geometry(280, 40, 40)
            if (title.isLayoutRequested) redundantLayouts++
            layout(40)
        }
        assertEquals("Unchanged geometry must not request another text layout", 0, redundantLayouts)

        shell.geometry(280, 32, 40)
        assertEquals(1, title.maxLines)
        assertTrue(title.isLayoutRequested)
        layout(32)
        shell.geometry(280, 40, 40)
        assertEquals(2, title.maxLines)
        assertTrue(title.isLayoutRequested)
        layout(40)
        shell.bind("Notice", "Public message", null, false, false, false, 0, false, expanded = false)
        assertEquals(1, title.maxLines)
        assertTrue(title.isLayoutRequested)
    }

    @Test fun shortFeedbackUsesMeasuredGlyphsAndLargeMultilingualTextRespectsTheCeiling() {
        val app = RuntimeEnvironment.getApplication()
        for (locale in listOf("zh-CN", "zh-TW", "en", "de", "ar", "ja")) for (scale in listOf(1f, 1.3f, 2f)) {
            val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)); fontScale = scale }
            val shell = IslandActivityView(app.createConfigurationContext(config))
            shell.contentHeight(40)
            for (text in listOf("已删除", "Deleted", "已刪除", "Gelöscht", "削除済み", "تم الحذف", "👨‍👩‍👧‍👦", "A very long notification without room to grow")) {
                val width = shell.toastWidth(text, 120, 218, 40, 40, expanded = false)
                assertTrue("$locale $scale $text $width", width in 120..218)
                if (locale == "zh-CN" && scale == 1f && text == "已删除") assertTrue(width < 160)
            }
        }
    }

    @Test fun oneThousandConfirmedDefaultReturnsAllocateNoAnimatorAndAlwaysFinishAtRest() {
        val shell = IslandActivityView(RuntimeEnvironment.getApplication())
        repeat(1000) { index ->
            shell.geometry(160 + index % 40, 40, 40)
            shell.prepareEntry(mediaReturn = true)
            shell.transition(100, 40, 160 + index % 40, 40, entering = true)
            assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(shell, "motion"))
            assertEquals(1f, shell.alpha, 0f)
            assertEquals(1f, shell.scaleX, 0f)
            assertEquals(0f, shell.translationY, 0f)
        }
    }
}
