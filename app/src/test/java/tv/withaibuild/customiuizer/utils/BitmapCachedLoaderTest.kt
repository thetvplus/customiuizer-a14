package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapCachedLoaderTest {

    @Test
    fun clampLoaderThreadCountNeverDropsBelowTwo() {
        assertEquals(2, BitmapCachedLoader.clampLoaderThreadCount(1))
        assertEquals(2, BitmapCachedLoader.clampLoaderThreadCount(2))
        assertEquals(2, BitmapCachedLoader.clampLoaderThreadCount(4))
    }

    @Test
    fun clampLoaderThreadCountUsesHalfOfAvailableCores() {
        assertEquals(3, BitmapCachedLoader.clampLoaderThreadCount(6))
        assertEquals(4, BitmapCachedLoader.clampLoaderThreadCount(8))
    }

    @Test
    fun clampLoaderThreadCountNeverExceedsFour() {
        assertEquals(4, BitmapCachedLoader.clampLoaderThreadCount(10))
        assertEquals(4, BitmapCachedLoader.clampLoaderThreadCount(16))
    }
}
