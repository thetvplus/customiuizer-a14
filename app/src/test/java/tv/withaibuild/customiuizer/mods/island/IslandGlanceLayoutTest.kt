package tv.withaibuild.customiuizer.mods.island

import android.content.res.Configuration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandGlanceLayoutTest {
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
