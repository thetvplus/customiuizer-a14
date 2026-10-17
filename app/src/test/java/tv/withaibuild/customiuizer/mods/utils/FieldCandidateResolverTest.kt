package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldCandidateResolverTest {

    @Suppress("unused")
    private class FirstMissingTarget(
        val iconGroup: String = "third",
        val mIcons: String = "second"
    ) {
        // mGroup intentionally absent.
    }

    @Suppress("unused")
    private class FirstMatchTarget(
        val mGroup: String = "first",
        val mIcons: String = "second"
    )

    @Suppress("unused")
    private class NoMatchTarget(
        val mGroup: Int = 1,
        val mIcons: Int = 2,
        val iconGroup: Int = 3
    )

    @Test
    fun triesSecondCandidateWhenFirstMissing() {
        val target = FirstMissingTarget()
        val result = FieldCandidateResolver.resolve<String>(
            target,
            listOf("mGroup", "mIcons", "iconGroup")
        )
        assertEquals("second", result)
    }

    @Test
    fun returnsFirstMatchingCandidate() {
        val target = FirstMatchTarget()
        val result = FieldCandidateResolver.resolve<String>(
            target,
            listOf("mGroup", "mIcons", "iconGroup")
        )
        assertEquals("first", result)
    }

    @Test
    fun returnsNullWhenNoCandidateMatchesType() {
        val target = NoMatchTarget()
        val result = FieldCandidateResolver.resolve<String>(
            target,
            listOf("mGroup", "mIcons", "iconGroup")
        )
        assertNull(result)
    }

    @Test
    fun customPredicateCanChooseLaterCandidate() {
        val target = FirstMatchTarget()
        val result = FieldCandidateResolver.resolve<String>(
            target,
            listOf("mGroup", "mIcons", "iconGroup")
        ) { it == "second" }
        assertEquals("second", result)
    }
}
