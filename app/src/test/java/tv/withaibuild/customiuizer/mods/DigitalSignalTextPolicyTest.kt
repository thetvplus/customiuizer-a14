package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigitalSignalTextPolicyTest {
    @Test
    fun zeroKeepsSystemTextAppearanceSize() {
        assertNull(resolveDigitalSignalCustomTextSizeDp(0))
    }

    @Test
    fun customSizeRemainsBounded() {
        assertEquals(7f, resolveDigitalSignalCustomTextSizeDp(14)!!, 0.001f)
        assertEquals(13f, resolveDigitalSignalCustomTextSizeDp(26)!!, 0.001f)
        assertEquals(20f, resolveDigitalSignalCustomTextSizeDp(99)!!, 0.001f)
    }

    @Test
    fun twoLineSpacingMatchesExistingCompactPolicy() {
        assertEquals(0.9f, resolveDigitalSignalLineSpacing(8f), 0.001f)
        assertEquals(0.85f, resolveDigitalSignalLineSpacing(13f), 0.001f)
    }
}
