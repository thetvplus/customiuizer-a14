package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.withaibuild.customiuizer.mods.statusbarheight.InsetsSourceAbi
import tv.withaibuild.customiuizer.mods.statusbarheight.InsetsTypeEncoding
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightResolver

class StatusBarInsetsResolverTest {

    @Test
    fun modernAbiSelectsModernPublic() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = true,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = 1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.MODERN_PUBLIC, info.encoding)
        assertEquals(1, info.statusBarType)
        assertEquals(2, info.navigationType)
        assertEquals(128, info.displayCutoutType)
    }

    @Test
    fun modernAbiWithLegacyFieldsStillSelectsModernPublic() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = 1,
            publicStatusType = 1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.MODERN_PUBLIC, info.encoding)
        assertEquals(1, info.statusBarType)
        assertEquals(2, info.navigationType)
        assertEquals(128, info.displayCutoutType)
    }

    @Test
    fun legacyAbiSelectsLegacyInternal() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = 1,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.LEGACY_INTERNAL, info.encoding)
        assertEquals(0, info.statusBarType)
        assertEquals(1, info.navigationType)
        assertEquals(-1, info.displayCutoutType)
    }

    @Test
    fun legacyFieldsWithModernConstructorSelectsModernPublic() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = 1,
            publicStatusType = 1,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.MODERN_PUBLIC, info.encoding)
        assertEquals(1, info.statusBarType)
        assertEquals(-1, info.navigationType)
        assertEquals(-1, info.displayCutoutType)
    }

    @Test
    fun onlyStatusBarLegacyConstantIsUnsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = null,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun bothConstructorsWithoutGetIdIsUnsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = true,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = 1,
            publicStatusType = 1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun noAvailableTypesIsUnsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = false,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun modernAbiMissingGetTypeIsUnsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = false,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = 1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun modernAbiMissingPublicStatusTypeFallsBackToUnsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = true,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = null,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun legacyAbiMissingGetTypeIsUnsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = false,
            legacyStatusType = 0,
            legacyNavigationType = 1,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun publicAndLegacyValuesAreNotMixed() {
        val legacyOnly = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = 1,
            publicStatusType = 1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(legacyOnly)

        assertEquals(InsetsTypeEncoding.LEGACY_INTERNAL, info.encoding)
        assertEquals(0, info.statusBarType)
        assertEquals(1, info.navigationType)
        assertEquals(-1, info.displayCutoutType)
    }

    @Test
    fun modernAbiWithPublicStatusTypeMinusOneIsUnsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = true,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = -1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun legacyAbiWithLegacyStatusTypeMinusOneIsUnsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = -1,
            legacyNavigationType = 1,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun legacyAbiWithLegacyNavigationTypeMinusOneIsUnsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = -1,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun modernAbiWithNullPublicStatusTypeIsUnsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = true,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = null,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun legacyAbiWithNullStatusAndNavIsUnsupported() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
    }

    @Test
    fun modernStatusOneNavTwoCutoutOneTwentyEightIsModernPublic() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = 1,
            publicStatusType = 1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.MODERN_PUBLIC, info.encoding)
        assertEquals(1, info.statusBarType)
        assertEquals(2, info.navigationType)
        assertEquals(128, info.displayCutoutType)
    }

    @Test
    fun legacyStatusZeroNavOneIsLegacyInternal() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = 1,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.LEGACY_INTERNAL, info.encoding)
        assertEquals(0, info.statusBarType)
        assertEquals(1, info.navigationType)
        assertEquals(-1, info.displayCutoutType)
    }
}
