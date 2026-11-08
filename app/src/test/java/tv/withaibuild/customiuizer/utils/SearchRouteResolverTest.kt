package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRouteResolverTest {

    @Test
    fun nullSubNormalizesToNull() {
        assertNull(SearchRouteResolver.normalizeSub(null))
    }

    @Test
    fun emptySubNormalizesToNull() {
        assertNull(SearchRouteResolver.normalizeSub(""))
    }

    @Test
    fun blankSubNormalizesToNull() {
        assertNull(SearchRouteResolver.normalizeSub("   "))
    }

    @Test
    fun whitespaceAroundValidSubIsTrimmedByBlankCheck() {
        assertEquals("pref_key_system_cat_screen", SearchRouteResolver.normalizeSub("pref_key_system_cat_screen"))
    }

    @Test
    fun validSubWithWhitespaceNormalizesToNull() {
        assertNull(SearchRouteResolver.normalizeSub("   \t  "))
    }

    @Test
    fun allFourKnownCategoriesReturnRoute() {
        assertEquals("pref_key_system", SearchRouteResolver.resolve("pref_key_system", "sub", "key")?.category)
        assertEquals("pref_key_launcher", SearchRouteResolver.resolve("pref_key_launcher", "sub", "key")?.category)
        assertEquals("pref_key_controls", SearchRouteResolver.resolve("pref_key_controls", "sub", "key")?.category)
        assertEquals("pref_key_various", SearchRouteResolver.resolve("pref_key_various", null, "key")?.category)
    }

    @Test
    fun unknownCategoryReturnsNull() {
        assertNull(SearchRouteResolver.resolve("pref_key_missing", null, "key"))
    }

    @Test
    fun systemWithNullSubIsCategorySelector() {
        val route = SearchRouteResolver.resolve("pref_key_system", null, "pref_key_system")!!
        assertTrue(route.isCategorySelector())
        assertNull(route.sub)
    }

    @Test
    fun launcherWithNullSubIsCategorySelector() {
        val route = SearchRouteResolver.resolve("pref_key_launcher", null, "pref_key_launcher")!!
        assertTrue(route.isCategorySelector())
    }

    @Test
    fun controlsWithNullSubIsCategorySelector() {
        val route = SearchRouteResolver.resolve("pref_key_controls", null, "pref_key_controls")!!
        assertTrue(route.isCategorySelector())
    }

    @Test
    fun variousWithNullSubIsCategorySelector() {
        val route = SearchRouteResolver.resolve("pref_key_various", null, "pref_key_various_hiddenfeatures_cat")!!
        assertTrue(route.isCategorySelector())
        assertNull(route.sub)
    }

    @Test
    fun variousWithBlankSubNormalizesAndIsCategorySelector() {
        val route = SearchRouteResolver.resolve("pref_key_various", "", "pref_key_various_hiddenfeatures_cat")!!
        assertTrue(route.isCategorySelector())
        assertNull(route.sub)
    }

    @Test
    fun routeKeyIsPreserved() {
        assertEquals("pref_key_system_orientationlock", SearchRouteResolver.resolve("pref_key_system", "pref_key_system_cat_screen", "pref_key_system_orientationlock")?.key)
    }
}
