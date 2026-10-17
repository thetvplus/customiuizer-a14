package tv.withaibuild.customiuizer

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationFeedbackTest {

    @Test
    fun preferencePageWithRecyclerView_cleansRootAndList() {
        val plan = NavigationFeedback.plan(hasFragmentView = true, hasPreferenceList = true)
        assertTrue(plan.clearFragmentRoot)
        assertTrue(plan.cleanPreferenceList)
    }

    @Test
    fun editPageWithoutPreferenceList_cleansRootAndDoesNotTouchList() {
        val plan = NavigationFeedback.plan(hasFragmentView = true, hasPreferenceList = false)
        assertTrue(plan.clearFragmentRoot)
        assertFalse(plan.cleanPreferenceList)
    }

    @Test
    fun missingFragmentView_skipsAllCleanup() {
        val plan = NavigationFeedback.plan(hasFragmentView = false, hasPreferenceList = false)
        assertFalse(plan.clearFragmentRoot)
        assertFalse(plan.cleanPreferenceList)
    }

    @Test
    fun absentListIsALegalStateNotAnException() {
        val plan = NavigationFeedback.plan(hasFragmentView = true, hasPreferenceList = false)
        assertEquals(
            NavigationFeedback.Plan(clearFragmentRoot = true, cleanPreferenceList = false),
            plan,
        )
    }
}

class NavigationFeedbackWiringContractTest {

    private val preferenceFragmentBase = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt")
    )
    private val subFragment = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/SubFragment.kt")
    )

    @Test
    fun finishNavigationFeedbackUsesPlanAndDoesNotDereferenceAMissingList() {
        val finish = section(
            preferenceFragmentBase,
            "private fun finishNavigationFeedback()",
            "override fun onCreateAnimator",
        )
        assertTrue(finish.contains("NavigationFeedback.plan("))
        assertTrue(finish.contains("getListView() as RecyclerView?"))
        assertTrue(finish.contains("if (!plan.cleanPreferenceList) return"))
        assertFalse(
            Regex("""val preferenceList = getListView\(\)\s+preferenceList\.stopScroll\(\)""")
                .containsMatchIn(finish),
        )
        assertTrue(finish.contains("list.stopScroll()"))
    }

    @Test
    fun openSubFragmentStillGuardsSavedStateAndUsesOrdinaryCommit() {
        val open = section(
            preferenceFragmentBase,
            "open fun openSubFragment(\n        fragment: Fragment,\n        args: Bundle?,\n        settingsType: AppHelper.SettingsType,\n        abType: AppHelper.ActionBarType,\n        title: String?,",
            "private fun finishNavigationFeedback()",
        )
        assertTrue(open.contains("isStateSaved"))
        assertTrue(open.contains(".commit()"))
        assertTrue(open.contains("finishNavigationFeedback()"))
        assertFalse(open.contains("commitAllowingStateLoss"))
        assertFalse(open.contains("executePendingTransactions"))
    }

    @Test
    fun editPagesDoNotCreateThePreferenceRecyclerView() {
        val onCreateView = section(
            subFragment,
            "override fun onCreateView",
            "override fun onViewCreated",
        )
        assertTrue(onCreateView.contains("settingsType == AppHelper.SettingsType.Preference"))
        assertTrue(onCreateView.contains("super.onCreateView("))
        assertTrue(onCreateView.contains("R.layout.prefs_common"))
        assertFalse(onCreateView.contains("super.onCreateView(crtInflator, container, savedInstanceState)\n        } else {\n            super.onCreateView"))
    }

    @Test
    fun editToChildSelectorsShareTheCommonOpenSubFragmentHelper() {
        val files = listOf(
            "src/main/java/tv/withaibuild/customiuizer/subs/MultiAction.kt",
            "src/main/java/tv/withaibuild/customiuizer/subs/AppSelector.kt",
            "src/main/java/tv/withaibuild/customiuizer/subs/SortableList.kt",
            "src/main/java/tv/withaibuild/customiuizer/SubFragment.kt",
            "src/main/java/tv/withaibuild/customiuizer/subs/System_NoScreenLock.kt",
        )
        for (file in files) {
            val source = Files.readString(Path.of(file))
            assertTrue("$file must open children through openSubFragment", source.contains("openSubFragment("))
            assertFalse(
                "$file must not implement a private navigation helper",
                source.contains("fun finishNavigationFeedback"),
            )
        }
        assertTrue(
            Files.readString(Path.of("src/main/java/tv/withaibuild/customiuizer/subs/MultiAction.kt"))
                .contains("SettingsType.Edit"),
        )
        assertTrue(
            Files.readString(Path.of("src/main/java/tv/withaibuild/customiuizer/subs/AppSelector.kt"))
                .contains("ActivitySelector()"),
        )
    }

    private fun section(source: String, start: String, end: String): String {
        val startIndex = source.indexOf(start)
        val endIndex = source.indexOf(end, startIndex + start.length)
        check(startIndex >= 0 && endIndex > startIndex) {
            "Could not extract source section between '$start' and '$end'"
        }
        return source.substring(startIndex, endIndex)
    }
}
