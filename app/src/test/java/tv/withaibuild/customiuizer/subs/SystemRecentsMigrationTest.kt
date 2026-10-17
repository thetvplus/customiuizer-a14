package tv.withaibuild.customiuizer.subs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemRecentsMigrationTest {

    @Test
    fun legacyRecentsCardStyleToBoolean_convertsAllRawTypes() {
        assertEquals(true, System.legacyRecentsCardStyleToBoolean("1"))
        assertEquals(false, System.legacyRecentsCardStyleToBoolean("0"))
        assertEquals(false, System.legacyRecentsCardStyleToBoolean("2"))
        assertEquals(false, System.legacyRecentsCardStyleToBoolean("invalid"))

        assertEquals(true, System.legacyRecentsCardStyleToBoolean(1))
        assertEquals(false, System.legacyRecentsCardStyleToBoolean(0))
        assertEquals(false, System.legacyRecentsCardStyleToBoolean(2))

        assertNull(System.legacyRecentsCardStyleToBoolean(true))
        assertNull(System.legacyRecentsCardStyleToBoolean(false))

        assertNull(System.legacyRecentsCardStyleToBoolean(null))

        assertEquals(false, System.legacyRecentsCardStyleToBoolean(2.5f))
    }

    @Test
    fun migrationRunsBeforeXmlInflation() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/subs/System.kt").readText()
        val body = source.substringAfter("override fun onCreatePreferences(")
            .substringBefore("override fun onCreate(")

        val migrationIndex = body.indexOf("migrateLegacyRecentsCardStylePreference()")
        val superIndex = body.indexOf("super.onCreatePreferences(")

        assertTrue("migration must be called", migrationIndex != -1)
        assertTrue("super.onCreatePreferences must exist", superIndex != -1)
        assertTrue("migration must run before super", migrationIndex < superIndex)
    }

    private fun source(path: String): File {
        var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }
}
