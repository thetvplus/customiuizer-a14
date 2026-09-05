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
    private val mainFragment = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/MainFragment.kt")
    )
    private val decoration = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/PreferenceGroupChrome.kt")
    )
    private val searchAdapter = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/utils/ModSearchAdapter.kt")
    )
    private val styles = source("app/src/main/res/values/styles.xml")
    private val aboutLayout = source("app/src/main/res/layout/fragment_about.xml")
    private val activityMain = source("app/src/main/res/layout/activity_main.xml")
    private val lightColors = source("app/src/main/res/values/colors.xml")
    private val nightColors = source("app/src/main/res/values-night/colors.xml")
    private val prefsMain = source("app/src/main/res/xml/prefs_main.xml")

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

    @Test
    fun groupedCardsUseSurfaceContainerAndDynamicOutline() {
        val card = source("app/src/main/res/drawable/pref_group_card.xml")
        assertTrue(card.contains("@color/color_surface_container"))
        assertTrue(decoration.contains("R.color.color_surface_container"))
        assertTrue(decoration.contains("R.color.color_outline_variant"))
        assertFalse(decoration.contains("R.color.color_surface_variant"))
        assertFalse(decoration.contains("R.color.about_divider"))
    }

    @Test
    fun surfaceTokensUseSystemDynamicPalette() {
        for (source in listOf(lightColors, nightColors)) {
            assertTrue(source.contains("@android:color/system_neutral1_"))
            assertTrue(source.contains("color_surface_container"))
            assertTrue(source.contains("color_outline_variant"))
        }
        assertTrue(lightColors.contains("color_window_background\">@android:color/system_neutral1_50"))
        assertTrue(lightColors.contains("color_surface_container\">@android:color/system_neutral1_10"))
        assertTrue(nightColors.contains("color_window_background\">@android:color/system_neutral1_900"))
        assertTrue(nightColors.contains("color_surface_container\">@android:color/system_neutral1_800"))
    }

    @Test
    fun sectionHeadersAlignWithInsetCardTitles() {
        assertTrue(categoryEx.contains("preference_group_header_padding"))
        val dimens = source("app/src/main/res/values/dimens.xml")
        assertTrue(dimens.contains("preference_group_header_padding\">32dp"))
        assertTrue(dimens.contains("normal_text_size\">17sp"))
        assertTrue(dimens.contains("secondary_text_size\">13sp"))
        assertTrue(dimens.contains("preference_group_radius\">12dp"))
    }

    @Test
    fun homePageDoesNotPinCategoryIcons() {
        assertFalse(mainFragment.contains("applyMainPageIcons"))
        assertFalse(mainFragment.contains("pref_icon_system"))
        assertFalse(prefsMain.contains("android:icon"))
    }

    @Test
    fun searchResultsAndOverflowMenuUseGroupedCardSurfaces() {
        assertTrue(searchAdapter.contains("pref_search_row_single"))
        assertTrue(searchAdapter.contains("pref_search_row_top"))
        assertTrue(searchAdapter.contains("pref_search_row_middle"))
        assertTrue(searchAdapter.contains("pref_search_row_bottom"))
        val searchList = source("app/src/main/res/layout/prefs_main12.xml")
        assertTrue(searchList.contains("@dimen/preference_group_inset"))
        val menu = source("app/src/main/res/drawable/popmenu_background.xml")
        assertTrue(menu.contains("@color/color_surface_container"))
        assertTrue(menu.contains("@dimen/preference_group_radius"))
        assertTrue(styles.contains("AppTextAppearance.PopupMenu"))
        assertTrue(styles.contains("actionOverflowMenuStyle"))
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
