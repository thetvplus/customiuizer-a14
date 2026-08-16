package tv.withaibuild.customiuizer

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static contracts for the low-allocation preference-page transition path. */
class PreferenceNavigationContractTest {

    private val source = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt")
    )

    @Test
    fun navigationFinishesVisiblePressFeedbackBeforeStartingTheTransaction() {
        val stringOverload = source.substring(
            source.indexOf("open fun openSubFragment(\n        fragment: Fragment", source.indexOf("open fun openSubFragment(") + 1),
            source.indexOf("override fun onCreateAnimator")
        )
        val finishFeedback = stringOverload.indexOf("finishNavigationFeedback()")
        val beginTransaction = stringOverload.indexOf("beginTransaction()")

        assertTrue(finishFeedback >= 0)
        assertTrue(beginTransaction > finishFeedback)
        assertTrue(source.contains("jumpDrawablesToCurrentState()"))
    }

    @Test
    fun userNavigationIsNotCommittedAfterFragmentStateWasSaved() {
        val stringOverload = source.substring(
            source.indexOf("open fun openSubFragment(\n        fragment: Fragment", source.indexOf("open fun openSubFragment(") + 1),
            source.indexOf("override fun onCreateAnimator")
        )

        assertTrue(stringOverload.contains("isStateSaved"))
        assertTrue(stringOverload.contains(".commit()"))
        assertFalse(stringOverload.contains("commitAllowingStateLoss"))
    }
}
