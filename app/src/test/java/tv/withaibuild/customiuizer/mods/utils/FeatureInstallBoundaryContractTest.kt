package tv.withaibuild.customiuizer.mods.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureInstallBoundaryContractTest {

    @Test
    fun featureDefinitionsDoNotSwallowInstallerThrowables() {
        val featureDirectory = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature")
        val files = featureDirectory.listFiles { file -> file.extension == "kt" }.orEmpty()

        files.forEach { file ->
            assertFalse(
                "${file.name} must delegate install failure isolation to FeatureInstallRegistry",
                file.readText().contains("catch (t: Throwable)")
            )
        }

        val systemServerInstaller = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemServerInstaller.kt"
        ).readText()
        assertFalse(systemServerInstaller.contains("catch (t: Throwable)"))
    }

    @Test
    fun registryKeepsFatalAndOrdinaryFailurePathsSeparate() {
        val registry = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt"
        ).readText()
        val throwableCatch = registry.indexOf("catch (t: Throwable)")

        assertTrue("Throwable catch must exist and delegate fatal propagation", throwableCatch >= 0)

        val afterCatch = registry.substring(throwableCatch)
        assertTrue(
            "state must roll back to FAILED_TRANSIENT before fatal/non-fatal dispatch",
            afterCatch.contains("FeatureInstallState.set(id, FeatureState.FAILED_TRANSIENT)")
        )
        assertTrue(
            "fatal errors must be unwrapped and rethrown before logging/recording",
            afterCatch.contains("FatalErrors.unwrapAndRethrowIfFatal(t)")
        )
        assertTrue(
            "non-fatal failures must still be recorded as FAILED_TRANSIENT",
            afterCatch.contains("recordInstallFailure(spec, reportable)")
        )
    }

    @Test
    fun dexKitCloseDoesNotSwallowOutOfMemoryError() {
        val helpers = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java"
        ).readText()
        val closeBridge = helpers.substring(helpers.indexOf("public static void closeBridge()"))

        assertTrue(closeBridge.contains("catch (OutOfMemoryError oom)"))
        assertTrue(closeBridge.contains("throw oom;"))
    }

    private fun source(relativePath: String): File {
        var directory = File(requireNotNull(java.lang.System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.exists()) return candidate
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
