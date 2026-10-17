package tv.withaibuild.customiuizer.mods

import android.graphics.Typeface
import org.junit.Assert.assertEquals
import org.junit.Test

class NetSpeedTypefaceStyleTest {

    @Test
    fun resolveNetSpeedTypefaceStyle_turnsBoldOnPreservingOtherBits() {
        assertEquals(Typeface.BOLD, resolveNetSpeedTypefaceStyle(Typeface.NORMAL, true))
        assertEquals(Typeface.BOLD_ITALIC, resolveNetSpeedTypefaceStyle(Typeface.ITALIC, true))
    }

    @Test
    fun resolveNetSpeedTypefaceStyle_turnsBoldOffPreservingOtherBits() {
        assertEquals(Typeface.NORMAL, resolveNetSpeedTypefaceStyle(Typeface.BOLD, false))
        assertEquals(Typeface.ITALIC, resolveNetSpeedTypefaceStyle(Typeface.BOLD_ITALIC, false))
    }

    @Test
    fun resolveNetSpeedTypefaceStyle_noStyleChangeIsIdempotent() {
        assertEquals(Typeface.NORMAL, resolveNetSpeedTypefaceStyle(Typeface.NORMAL, false))
        assertEquals(Typeface.BOLD, resolveNetSpeedTypefaceStyle(Typeface.BOLD, true))
    }
}
