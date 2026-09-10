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

    @Test
    fun groupedListRowsPickTopMiddleBottomSingle() {
        assertEquals(R.drawable.pref_search_row_single, groupedListRowBackground(0, 1))
        assertEquals(R.drawable.pref_search_row_top, groupedListRowBackground(0, 4))
        assertEquals(R.drawable.pref_search_row_middle, groupedListRowBackground(1, 4))
        assertEquals(R.drawable.pref_search_row_middle, groupedListRowBackground(2, 4))
        assertEquals(R.drawable.pref_search_row_bottom, groupedListRowBackground(3, 4))
        assertEquals(R.drawable.pref_search_row_top, groupedListRowBackground(0, 2))
        assertEquals(R.drawable.pref_search_row_bottom, groupedListRowBackground(1, 2))
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
    private val appDataAdapter = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/utils/AppDataAdapter.kt")
    )
    private val preferenceAdapter = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/utils/PreferenceAdapter.kt")
    )
    private val helpers = Files.readString(
        Path.of("src/main/java/tv/withaibuild/customiuizer/utils/Helpers.kt")
    )
    private val styles = source("app/src/main/res/values/styles.xml")
    private val aboutLayout = source("app/src/main/res/layout/fragment_about.xml")
    private val aboutHead = source("app/src/main/res/layout/fragment_about_head.xml")
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
        // Decoration must follow the displayed adapter snapshot, including while
        // visibility changes are waiting for the adapter's posted update.
        assertTrue(decoration.contains("adapter as? PreferenceGroup.PreferencePositionCallback"))
        assertTrue(decoration.contains("positions.getPreferenceAdapterPosition(pref)"))
        assertTrue(decoration.contains("MutableList(adapter.itemCount)"))
        assertFalse(decoration.contains("flattenVisiblePreferences"))
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
        assertFalse(decoration.contains("dividerPaint.alpha"))
        assertFalse(decoration.contains("R.color.color_surface_variant"))
        assertFalse(decoration.contains("R.color.about_divider"))
    }

    @Test
    fun surfaceTokensUseTelegramPalette() {
        for (source in listOf(lightColors, nightColors)) {
            assertFalse(source.contains("@android:color/system_neutral"))
            assertFalse(source.contains("@android:color/system_accent"))
            assertTrue(source.contains("color_surface_container"))
            assertTrue(source.contains("color_outline_variant"))
        }
        assertTrue(lightColors.contains("color_window_background\">#EFEFF4"))
        assertTrue(lightColors.contains("color_surface_container\">#FFFFFF"))
        assertTrue(nightColors.contains("color_window_background\">#000000"))
        assertTrue(nightColors.contains("color_surface_container\">#1C1C1D"))
        assertTrue(lightColors.contains("color_outline_variant\">#D9D9D9"))
        assertTrue(nightColors.contains("color_outline_variant\">#14FFFFFF"))
        assertTrue(lightColors.contains("highlight_normal_light\">#3390EC"))
        assertTrue(nightColors.contains("highlight_normal_light\">#6CB7F9"))
        assertTrue(lightColors.contains("list_item_bg_color_pressed\">#0F000000"))
        assertTrue(nightColors.contains("list_item_bg_color_pressed\">#14FFFFFF"))
        assertTrue(lightColors.contains("list_item_bg_color_longpress\">#1A000000"))
        assertTrue(nightColors.contains("list_item_bg_color_longpress\">#24FFFFFF"))
    }

    @Test
    fun pressHighlightIsClippedToGroupedCorners() {
        assertTrue(decoration.contains("clipToOutline"))
        assertTrue(decoration.contains("GroupedRowOutline"))
        assertTrue(decoration.contains("outline.setPath"))
        val highlight = source("app/src/main/res/drawable/list_item_bg.xml")
        assertTrue(highlight.contains("<selector"))
        assertTrue(highlight.contains("state_pressed"))
        assertTrue(highlight.contains("state_selected"))
        assertTrue(highlight.contains("state_activated"))
        assertTrue(highlight.contains("@drawable/list_item_bg_pressed"))
        assertTrue(highlight.contains("@drawable/list_item_bg_selected"))
        assertFalse(highlight.contains("<ripple"))
        assertFalse(highlight.contains("23.33dp"))
        val selected = source("app/src/main/res/drawable/list_item_bg_selected.xml")
        assertTrue(selected.contains("@color/list_item_bg_color_longpress"))
        val searchSingle = source("app/src/main/res/drawable/pref_search_row_single.xml")
        val searchTop = source("app/src/main/res/drawable/pref_search_row_top.xml")
        val searchMiddle = source("app/src/main/res/drawable/pref_search_row_middle.xml")
        val searchBottom = source("app/src/main/res/drawable/pref_search_row_bottom.xml")
        for (row in listOf(searchSingle, searchTop, searchMiddle, searchBottom)) {
            assertTrue(row.contains("<layer-list"))
            assertTrue(row.contains("@drawable/list_item_bg"))
            assertFalse(row.contains("android:alpha"))
            assertFalse(row.contains("<ripple"))
        }
        for (row in listOf(searchSingle, searchTop, searchBottom)) {
            assertTrue(row.contains("@dimen/preference_group_radius"))
        }
        assertTrue(decoration.contains("applyGroupedListRow"))
        assertTrue(searchAdapter.contains("applyGroupedListRow"))
        assertTrue(styles.contains("GroupedListView"))
        assertTrue(styles.contains("@android:color/transparent"))
        val about = source("app/src/main/res/layout/fragment_about.xml")
        assertTrue(about.contains("@drawable/list_item_bg"))
        assertFalse(about.contains("selectableItemBackground"))
    }

    @Test
    fun overflowMenuClipsSelectorToGroupedRadius() {
        val menu = source("app/src/main/res/drawable/popmenu_background.xml")
        assertTrue(menu.contains("@color/color_surface_container"))
        assertTrue(menu.contains("@dimen/preference_group_radius"))
        assertTrue(styles.contains("OverflowListView"))
        assertTrue(styles.contains("android:clipToOutline"))
        assertTrue(styles.contains("@drawable/list_item_bg"))
        assertTrue(styles.contains("AppTextAppearance.PopupMenu"))
        assertTrue(styles.contains("actionOverflowMenuStyle"))
    }

    @Test
    fun sectionHeadersAlignWithInsetCardTitles() {
        assertTrue(categoryEx.contains("preference_group_header_padding"))
        val dimens = source("app/src/main/res/values/dimens.xml")
        assertTrue(dimens.contains("preference_group_header_padding\">32dp"))
        assertTrue(dimens.contains("preference_group_header_bottom\">6dp"))
        assertTrue(dimens.contains("preference_group_gap\">10dp"))
        assertTrue(dimens.contains("normal_text_size\">16sp"))
        assertTrue(dimens.contains("preference_category_text_size\">14sp"))
        assertTrue(dimens.contains("secondary_text_size\">13sp"))
        assertTrue(dimens.contains("preference_group_radius\">10dp"))
        assertTrue(dimens.contains("preference_item_padding_top\">11dp"))
        assertTrue(categoryEx.contains("setPadding(headerPadding, 0, headerPadding, 0)"))
    }

    @Test
    fun homePageDoesNotPinCategoryIcons() {
        assertFalse(mainFragment.contains("applyMainPageIcons"))
        assertFalse(mainFragment.contains("pref_icon_system"))
        assertFalse(prefsMain.contains("android:icon"))
    }

    @Test
    fun searchResultsAndOverflowMenuUseGroupedCardSurfaces() {
        assertTrue(decoration.contains("pref_search_row_single"))
        assertTrue(decoration.contains("pref_search_row_top"))
        assertTrue(decoration.contains("pref_search_row_middle"))
        assertTrue(decoration.contains("pref_search_row_bottom"))
        assertTrue(searchAdapter.contains("applyGroupedListRow"))
        assertTrue(appDataAdapter.contains("applyGroupedListRow"))
        assertTrue(preferenceAdapter.contains("applyGroupedListRow"))
        assertFalse(preferenceAdapter.contains("setMiuiPrefItem"))
        assertFalse(helpers.contains("\"miui\""))
        val searchList = source("app/src/main/res/layout/prefs_main12.xml")
        assertTrue(searchList.contains("GroupedListView"))
        assertTrue(styles.contains("@dimen/preference_group_inset"))
        val menu = source("app/src/main/res/drawable/popmenu_background.xml")
        assertTrue(menu.contains("@color/color_surface_container"))
        assertTrue(menu.contains("@dimen/preference_group_radius"))
        val corners = source("app/src/main/res/drawable/rounded_corners.xml")
        assertTrue(corners.contains("@dimen/preference_group_radius"))
        assertFalse(corners.contains("13dp"))
        assertFalse(aboutHead.contains("about_divider"))
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
