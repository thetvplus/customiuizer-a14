package tv.withaibuild.customiuizer.mods.volumedialogautohide

import android.view.accessibility.AccessibilityManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [VolumeTimeoutRecommendationTest.RecommendationShadow::class])
class VolumeTimeoutRecommendationTest {
    @Test
    fun missingOrWronglyTypedManagersLeaveTheNativeImplementationAvailable() {
        assertNull(VolumeDialogAutohideDelayHook.resolveTimeoutRecommendation(Any::class.java))
        assertNull(VolumeDialogAutohideDelayHook.resolveTimeoutRecommendation(WrongManager::class.java))
    }

    @Test
    fun aMissingRuntimeManagerDoesNotInventATimeout() {
        val recommend = VolumeDialogAutohideDelayHook.resolveTimeoutRecommendation(Dialog::class.java)!!
        assertNull(recommend(Dialog(null), 2500, 4))
    }

    @Test
    fun theResolvedFieldUsesTheNativeManagersCurrentRecommendation() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(AccessibilityManager::class.java)!!
        val shadow = Shadow.extract<RecommendationShadow>(manager)
        val recommend = VolumeDialogAutohideDelayHook.resolveTimeoutRecommendation(Dialog::class.java)!!
        val dialog = Dialog(manager)
        shadow.recommended = 45000
        assertEquals(45000, recommend(dialog, 2500, 6))
        assertEquals(listOf(2500, 6), shadow.lastRequest)
        shadow.recommended = 60000
        assertEquals(60000, recommend(dialog, 16000, 4))
        assertEquals(listOf(16000, 4), shadow.lastRequest)
        dialog.mAccessibilityMgr = null
        assertNull(recommend(dialog, 2500, 4))
    }

    class Dialog(@JvmField var mAccessibilityMgr: AccessibilityManager?)
    class WrongManager { @JvmField val mAccessibilityMgr = "unsupported" }

    @Implements(AccessibilityManager::class)
    class RecommendationShadow : org.robolectric.shadows.ShadowAccessibilityManager() {
        var recommended = 0
        var lastRequest = emptyList<Int>()

        @Implementation
        fun getRecommendedTimeoutMillis(originalTimeout: Int, flags: Int): Int {
            lastRequest = listOf(originalTimeout, flags)
            return recommended
        }
    }
}
