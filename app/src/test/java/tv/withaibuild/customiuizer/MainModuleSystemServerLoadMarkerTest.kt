package tv.withaibuild.customiuizer

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainModuleSystemServerLoadMarkerTest {

    @Test
    fun systemServerLoadMarkerIsLoggedBeforeAnyHookInstallation() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java").replace("\r\n", "\n")
        val method = main.section(
            "public void onSystemServerStarting(final SystemServerStartingParam lpparam) {",
            "    public void onPackageReady"
        )

        val marker = "XposedHelpers.log(\"CustoMIUIzer \" + BuildConfig.VERSION_NAME + \" (\" + BuildConfig.VERSION_CODE\n" +
            "                    + \") [\" + BuildConfig.BUILD_REVISION + \"] loaded in \" + processName);"
        assertTrue("Missing system_server load marker", method.contains(marker))

        val markerIndex = method.indexOf(marker)
        val firstHookIndex = method.indexOf("SystemServerInstaller.install(lpparam,")
        assertTrue(
            "Load marker must appear before the first hook installation",
            markerIndex in 0 until firstHookIndex
        )

        val guard = "if (!mSystemServerLoadMarkerLogged)"
        assertTrue("Missing once-per-lifecycle guard", method.contains(guard))
    }

    @Test
    fun everyProcessLoadMarkerIncludesBuildRevision() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        val revisionMarker = "[\" + BuildConfig.BUILD_REVISION + \"] loaded in \" + processName"

        assertTrue(
            "Both module load paths must log the exact build revision",
            main.windowed(revisionMarker.length).count { it == revisionMarker } == 2
        )

        val gradle = source("app/build.gradle.kts")
        assertTrue(
            "BuildConfig.BUILD_REVISION must be generated from the resolved Git revision",
            gradle.contains("buildConfigField(\"String\", \"BUILD_REVISION\", \"\\\"\$buildRevision\\\"\")")
        )
    }

    @Test
    fun onSystemServerStartingHookSummaryStageIsPreserved() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        assertTrue(
            "HookSummary stage name must not be renamed",
            main.contains("HookDiagnostics.printSummaryForStage(\"onSystemServerStarting\");")
        )
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        val endIndex = indexOf(end, startIndex + start.length)
        check(startIndex >= 0 && endIndex > startIndex) {
            "Could not extract source section between '$start' and '$end'"
        }
        return substring(startIndex, endIndex)
    }
}
