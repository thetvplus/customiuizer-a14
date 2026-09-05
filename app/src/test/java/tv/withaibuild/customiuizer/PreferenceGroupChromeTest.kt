package tv.withaibuild.customiuizer

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceGroupChromeTest {

    @Test
    fun topLevelCategoriesBecomeHeadersOutsideCards() {
        val chrome = PreferenceGroupChrome.layout(
            listOf(
                header(),
                item(),
                item(),
                header(),
                item(),
            )
        )
        assertEquals(5, chrome.size)
        assertEquals(PreferenceGroupChrome.Kind.SECTION_HEADER, chrome[0].kind)
        assertFalse(chrome[0].inCard)
        assertTrue(chrome[1].inCard && chrome[1].cardStart)
        assertTrue(chrome[2].inCard && chrome[2].cardEnd)
        assertTrue(chrome[1].dividerBelow)
        assertFalse(chrome[2].dividerBelow)
        assertEquals(chrome[1].groupId, chrome[2].groupId)
        assertTrue(chrome[0].groupId != chrome[3].groupId)
        assertEquals(PreferenceGroupChrome.Kind.SECTION_HEADER, chrome[3].kind)
        assertTrue(chrome[4].inCard && chrome[4].cardStart && chrome[4].cardEnd)
    }

    @Test
    fun nestedCategoriesStayInsideTheCard() {
        val chrome = PreferenceGroupChrome.layout(
            listOf(
                header(),
                item(),
                innerHeader(),
                item(),
            )
        )
        assertEquals(PreferenceGroupChrome.Kind.INNER_HEADER, chrome[2].kind)
        assertTrue(chrome[2].inCard)
        assertEquals(chrome[1].groupId, chrome[2].groupId)
        assertEquals(chrome[2].groupId, chrome[3].groupId)
        assertTrue(chrome[1].cardStart)
        assertTrue(chrome[3].cardEnd)
    }

    @Test
    fun hiddenTopLevelTitleDoesNotSitInTheCard() {
        val chrome = PreferenceGroupChrome.layout(
            listOf(
                hiddenHeader(),
                item(),
                item(),
            )
        )
        assertEquals(PreferenceGroupChrome.Kind.HIDDEN_HEADER, chrome[0].kind)
        assertFalse(chrome[0].inCard)
        assertTrue(chrome[1].cardStart)
        assertTrue(chrome[2].cardEnd)
        assertEquals(chrome[0].groupId, chrome[1].groupId)
    }

    @Test
    fun headerOnlyGroupHasNoCard() {
        val chrome = PreferenceGroupChrome.layout(listOf(header(), header(), item()))
        assertEquals(PreferenceGroupChrome.Kind.SECTION_HEADER, chrome[0].kind)
        assertFalse(chrome[0].inCard)
        assertEquals(PreferenceGroupChrome.Kind.SECTION_HEADER, chrome[1].kind)
        assertTrue(chrome[2].inCard && chrome[2].cardStart && chrome[2].cardEnd)
    }

    @Test
    fun ungroupedPrefixBecomesItsOwnCard() {
        val chrome = PreferenceGroupChrome.layout(
            listOf(
                item(),
                item(),
                header(),
                item(),
            )
        )
        assertTrue(chrome[0].inCard && chrome[0].cardStart)
        assertTrue(chrome[1].inCard && chrome[1].cardEnd)
        assertEquals(PreferenceGroupChrome.Kind.SECTION_HEADER, chrome[2].kind)
        assertTrue(chrome[0].groupId != chrome[2].groupId)
    }

    @Test
    fun emptyInputIsEmpty() {
        assertTrue(PreferenceGroupChrome.layout(emptyList()).isEmpty())
    }

    private fun header() = PreferenceGroupChrome.InputRow(
        isCategory = true,
        isTopLevel = true,
        titleVisible = true,
    )

    private fun hiddenHeader() = PreferenceGroupChrome.InputRow(
        isCategory = true,
        isTopLevel = true,
        titleVisible = false,
    )

    private fun innerHeader() = PreferenceGroupChrome.InputRow(
        isCategory = true,
        isTopLevel = false,
        titleVisible = true,
    )

    private fun item() = PreferenceGroupChrome.InputRow(
        isCategory = false,
        isTopLevel = false,
        titleVisible = true,
    )
}

class PreferenceGroupChromeWiringTest {

    private val preferenceFragmentBase = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt")
    )
    private val categoryEx = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/prefs/PreferenceCategoryEx.kt")
    )
    private val styles = source("app/src/main/res/values/styles.xml")
    private val aboutLayout = source("app/src/main/res/layout/fragment_about.xml")
    private val activityMain = source("app/src/main/res/layout/activity_main.xml")

    @Test
    fun preferencePagesDisableStockDividersAndAttachGroupedDecoration() {
        val onViewCreated = section(
            preferenceFragmentBase,
            "override fun onViewCreated(view: View, savedInstanceState: Bundle?)",
            "open fun openSubFragment(",
        )
        assertTrue(onViewCreated.contains("attachGroupedPreferenceChrome()"))
        val attach = section(
            preferenceFragmentBase,
            "private fun attachGroupedPreferenceChrome()",
            "open fun openSubFragment(",
        )
        assertTrue(attach.contains("setDivider(null)"))
        assertTrue(attach.contains("setDividerHeight(0)"))
        assertTrue(attach.contains("PreferenceGroupDecoration"))
        assertTrue(attach.contains("preferenceScreen"))
        assertFalse(attach.contains("getListView() as RecyclerView"))
    }

    @Test
    fun categoryTitleVisibilityIsExplicit() {
        assertTrue(categoryEx.contains("fun isTitleVisible(): Boolean = state == 0"))
    }

    @Test
    fun categoryStyleIsNotAllCaps() {
        val category = section(
            styles,
            "<style name=\"Widget.InputField.Category\">",
            "<style name=\"Widget.InputField.Category.First\">",
        )
        assertFalse(category.contains("textAllCaps"))
        assertFalse(category.contains("android:textAllCaps"))
    }

    @Test
    fun aboutPageUsesGroupedCardsAndKeepsDonatePaypalUndivided() {
        assertTrue(aboutLayout.contains("@drawable/pref_group_card"))
        assertFalse(
            aboutLayout.substringAfter("about_donate_row")
                .substringBefore("about_paypal_row")
                .contains("about_divider"),
        )
        val afterContact = aboutLayout.substringAfter("about_contact_row")
            .substringBefore("about_notes_category")
        assertFalse(afterContact.contains("android:background=\"@color/about_divider\""))
    }

    @Test
    fun toolbarHasNoHairlineAboveTheWindow() {
        val between = activityMain.substringAfter("mainActionBar")
            .substringBefore("fragment_container")
        assertFalse(between.contains("about_divider"))
        assertFalse(between.contains("android:layout_height=\"1dp\""))
    }

    private fun section(source: String, start: String, end: String): String {
        val startIndex = source.indexOf(start)
        val endIndex = source.indexOf(end, startIndex + start.length)
        check(startIndex >= 0 && endIndex > startIndex) {
            "Could not extract source section between '$start' and '$end'"
        }
        return source.substring(startIndex, endIndex)
    }

    private fun source(path: String): String {
        var directory = java.io.File(System.getProperty("user.dir")!!).absoluteFile
        while (true) {
            val candidate = java.io.File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found")
        }
    }
}
