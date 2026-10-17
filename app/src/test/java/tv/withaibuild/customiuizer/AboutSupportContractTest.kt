package tv.withaibuild.customiuizer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutSupportContractTest {
    @Test
    fun supportEntries_areColdPathAndUseCanonicalLinks() {
        val fragment = source("app/src/main/java/tv/withaibuild/customiuizer/AboutFragment.kt")
        val layout = source("app/src/main/res/layout/fragment_about.xml")

        assertTrue(layout.contains("about_donate_title"))
        assertTrue(layout.contains("about_paypal_title"))
        assertTrue(layout.contains("about_paypal_summary"))
        assertFalse(
            "No divider between donate_row and paypal_row (same group)",
            layout.substringAfter("about_donate_row")
                .substringBefore("about_paypal_row")
                .contains("about_divider"),
        )
        assertTrue(layout.contains("about_repository_title"))
        assertTrue(layout.contains("about_contact_title"))
        assertTrue(layout.contains("about_dynamic"))
        assertTrue(layout.contains("about_unsupported"))
        assertFalse(fragment.contains("fragment_about_tail"))
        assertTrue(fragment.contains("inSampleSize = DONATION_IMAGE_SAMPLE_SIZE"))
        assertTrue(fragment.contains("image.setImageDrawable(null)"))
        assertTrue(fragment.contains("bitmap.recycle()"))
        assertTrue(fragment.contains("https://github.com/tomthenpc/customiuizer-a14"))
        assertTrue(fragment.contains("https://t.me/Jinji_Kiko"))
        assertTrue(fragment.contains("PAYPAL_DONATION_URL"))
        assertTrue(
            "PayPal URL must be paypal.me/Jinjitv",
            fragment.contains("https://paypal.me/Jinjitv"),
        )
        assertFalse(
            "Old PayPal donations URL must be removed",
            fragment.contains("cgi-bin/webscr"),
        )
        assertTrue(fragment.contains("Intent.ACTION_VIEW"))
    }

    @Test
    fun donationTitlesAreSymmetricPaymentMethods() {
        val en = source("app/src/main/res/values/strings.xml")
        val zh = source("app/src/main/res/values-zh-rCN/strings.xml")
        assertTrue(en.contains("name=\"about_donate_title\">WeChat donation code</string>"))
        assertTrue(en.contains("name=\"about_paypal_title\">PayPal donation</string>"))
        assertTrue(zh.contains("name=\"about_donate_title\">微信赞赏码</string>"))
        assertTrue(zh.contains("name=\"about_paypal_title\">PayPal 赞赏</string>"))
        assertFalse(en.contains("name=\"about_donate_title\">Support development</string>"))
        assertFalse(zh.contains("name=\"about_donate_title\">赞赏开发</string>"))
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir") ?: "").absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }
}
