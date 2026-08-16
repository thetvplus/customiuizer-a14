package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLocaleEntryTest {

    @Test
    fun entriesAndValuesAreParallel() {
        val (entries, values) = AppLocaleController.buildLocaleDisplayData("System")

        assertEquals("entries and values must have the same length", entries.size, values.size)
        for (i in values.indices) {
            assertNotNull(values[i])
            assertNotNull(entries[i])
            assertTrue("entry must not be empty", entries[i].isNotEmpty())
        }
    }

    @Test
    fun autoIsFirstAndUsesProvidedLabel() {
        val (entries, values) = AppLocaleController.buildLocaleDisplayData("System default")

        assertEquals("auto", values[0])
        assertEquals("System default", entries[0].toString())
    }

    @Test
    fun zhTWHasFixedTraditionalLabel() {
        val (entries, values) = AppLocaleController.buildLocaleDisplayData("System")

        val index = values.indexOf("zh-TW")
        assertTrue("zh-TW must be present", index >= 0)
        assertEquals("繁體中文（台灣）", entries[index].toString())
    }

    @Test
    fun ptBRHasBrazilSuffix() {
        val (entries, values) = AppLocaleController.buildLocaleDisplayData("System")

        val index = values.indexOf("pt-BR")
        assertTrue("pt-BR must be present", index >= 0)
        assertTrue(entries[index].toString().contains("(Brasil)"))
    }

    @Test
    fun allSupportedTagsArePresent() {
        val (_, values) = AppLocaleController.buildLocaleDisplayData("System")

        assertTrue(values.contains("auto"))
        assertTrue(values.contains("en"))
        assertTrue(values.contains("zh-CN"))
        assertTrue(values.contains("zh-TW"))
        assertTrue(values.contains("ru-RU"))
        assertTrue(values.contains("ja-JP"))
        assertTrue(values.contains("vi-VN"))
        assertTrue(values.contains("cs-CZ"))
        assertTrue(values.contains("pt-BR"))
        assertTrue(values.contains("tr-TR"))
        assertTrue(values.contains("es-ES"))

        // Values must be unique.
        assertEquals(values.size, values.toSet().size)
    }
}
