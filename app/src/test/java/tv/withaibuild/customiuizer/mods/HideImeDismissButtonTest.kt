package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideImeDismissButtonTest {

    @Test
    fun shouldHideImeDismissButton_imeAltAndGestural_true() {
        assertTrue(Controls.shouldHideImeDismissButton(0x1, 2))
    }

    @Test
    fun shouldHideImeDismissButton_noImeAlt_false() {
        assertFalse(Controls.shouldHideImeDismissButton(0, 2))
    }

    @Test
    fun shouldHideImeDismissButton_threeButtonMode_false() {
        assertFalse(Controls.shouldHideImeDismissButton(0x1, 0))
    }

    @Test
    fun shouldHideImeDismissButton_twoButtonMode_false() {
        assertFalse(Controls.shouldHideImeDismissButton(0x1, 1))
    }

    @Test
    fun shouldHideImeDismissButton_nonBackAltHintBit_false() {
        assertFalse(Controls.shouldHideImeDismissButton(0x2, 2))
    }

    @Test
    fun shouldHideImeDismissButton_otherBitsDoNotAffectBackAlt_true() {
        assertTrue(Controls.shouldHideImeDismissButton(0x3, 2))
    }
}
