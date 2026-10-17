package tv.withaibuild.customiuizer.mods.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureInstallMetricsWiringTest {

    @Test
    fun targetProcessCatalogsHaveDevelopOnlyColdPathMeasurements() {
        val metrics = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FeatureInstallMetrics.kt"
        )
        assertTrue(metrics.contains("BuildConfig.BUILD_TYPE != \"develop\""))
        assertTrue(metrics.contains("art.gc.bytes-allocated"))
        assertTrue(metrics.contains("CustoMIUIzer FeaturePerf"))
        assertFalse(metrics.contains("catch (t: Throwable)"))

        val systemUi = source(
            "app/src/main/java/tv/withaibuild/customiuizer/installers/SystemUiInstaller.kt"
        )
        assertTrue(systemUi.contains("label = \"systemui/package-ready\""))
        assertTrue(systemUi.contains("FeatureInstallMetrics.recordCatalog("))

        val launcher = source(
            "app/src/main/java/tv/withaibuild/customiuizer/installers/LauncherInstaller.kt"
        )
        assertTrue(launcher.contains("label = \"launcher/package-ready\""))
        assertTrue(launcher.contains("label = \"launcher/post-attach\""))
        assertTrue(launcher.contains("FeatureInstallMetrics.recordCatalog("))

        val systemServer = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt"
        )
        assertTrue(systemServer.contains("label = \"system-server/starting\""))
        assertTrue(systemServer.contains("FeatureInstallMetrics.recordCatalog("))
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
