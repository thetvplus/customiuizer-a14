package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap
import java.io.File
import java.lang.reflect.Proxy

class RemainingFeaturesWiringTest {

    @Test
    fun installersNoLongerContainDirectPreferenceChecks() {
        val installers = listOf(
            "app/src/main/java/tv/withaibuild/customiuizer/installers/LauncherInstaller.java",
            "app/src/main/java/tv/withaibuild/customiuizer/installers/InputMethodInstaller.java",
            "app/src/main/java/tv/withaibuild/customiuizer/installers/SettingsInstaller.java",
            "app/src/main/java/tv/withaibuild/customiuizer/installers/SecurityCenterInstaller.java",
            "app/src/main/java/tv/withaibuild/customiuizer/installers/PhoneInstaller.java",
            "app/src/main/java/tv/withaibuild/customiuizer/installers/PowerKeeperInstaller.java",
            "app/src/main/java/tv/withaibuild/customiuizer/installers/GuardProviderInstaller.java",
            "app/src/main/java/tv/withaibuild/customiuizer/installers/PackageInstallerRouter.java",
            "app/src/main/java/tv/withaibuild/customiuizer/installers/MediaInstaller.java",
            "app/src/main/java/tv/withaibuild/customiuizer/installers/AndroidPackageInstaller.java",
            "app/src/main/java/tv/withaibuild/customiuizer/installers/GenericAppInstaller.java",
        )
        for (path in installers) {
            val src = source(path)
            assertFalse("$path should not directly read mPrefs in install/handleLoadLauncher", src.contains("mPrefs.get"))
        }
    }

    @Test
    fun remainingFeatureIdsAreUnique() {
        val all = LauncherPackageReadyFeatures.all(fakePackageReadyParam(), PrefMap()) +
            LauncherPostAttachFeatures.all(fakePackageReadyParam(), PrefMap()) +
            InputMethodFeatures.all(fakePackageReadyParam(), PrefMap()) +
            SettingsFeatures.all(fakePackageReadyParam(), PrefMap()) +
            SecurityCenterFeatures.all(fakePackageReadyParam(), PrefMap()) +
            PhoneFeatures.all(fakePackageReadyParam(), PrefMap()) +
            PowerKeeperFeatures.all(fakePackageReadyParam(), PrefMap()) +
            GuardProviderFeatures.all(fakePackageReadyParam(), PrefMap()) +
            PackageInstallerFeatures.all(fakePackageReadyParam(), PrefMap()) +
            MediaFeatures.all(fakePackageReadyParam(), PrefMap()) +
            AndroidPackageFeatures.all(fakePackageReadyParam(), PrefMap()) +
            GenericAppFeatures.all(fakePackageReadyParam(), PrefMap())
        val ids = all.map { it.id }
        assertEquals("All remaining features should have unique FeatureIds", ids.size, ids.toSet().size)
    }

    @Test
    fun mainModuleStillRoutesToInstallers() {
        val main = source("app/src/main/java/tv/withaibuild/customiuizer/MainModule.java")
        assertTrue("MainModule must call InputMethodInstaller.install", main.contains("InputMethodInstaller.install(lpparam, mPrefs);"))
        assertTrue("MainModule must call AndroidPackageInstaller.install", main.contains("AndroidPackageInstaller.install(lpparam, mPrefs);"))
        assertTrue("MainModule must call MediaInstaller.install", main.contains("MediaInstaller.install(lpparam, mPrefs);"))
        assertTrue("MainModule must call LauncherInstaller.install", main.contains("LauncherInstaller.install(lpparam, mPrefs);"))
        assertTrue("MainModule must call PackageInstallerRouter.install", main.contains("PackageInstallerRouter.install(lpparam, mPrefs);"))
        assertTrue("MainModule must call SecurityCenterInstaller.install", main.contains("SecurityCenterInstaller.install(lpparam, mPrefs);"))
        assertTrue("MainModule must call SettingsInstaller.install", main.contains("SettingsInstaller.install(lpparam, mPrefs);"))
        assertTrue("MainModule must call PhoneInstaller.install", main.contains("PhoneInstaller.install(lpparam, mPrefs);"))
        assertTrue("MainModule must call PowerKeeperInstaller.install", main.contains("PowerKeeperInstaller.install(lpparam, mPrefs);"))
        assertTrue("MainModule must call GuardProviderInstaller.install", main.contains("GuardProviderInstaller.install(lpparam, mPrefs);"))
        assertTrue("MainModule must delegate post-attach hooks to GenericAppInstaller", main.contains("GenericAppInstaller.installPostAttach(lpparam, mPrefs"))
        assertTrue("MainModule must call onPackageReady diagnostic summary", main.contains("HookDiagnostics.printSummaryForStage(\"onPackageReady\");"))
    }

    @Test
    fun genericAppInstallerCreatesOnlyRoutedSpecsInsideAttachCallback() {
        val installer = source("app/src/main/java/tv/withaibuild/customiuizer/installers/GenericAppInstaller.java")
        val callback = installer.indexOf("after(")
        val registry = installer.indexOf("FeatureInstallRegistry()")
        assertTrue("generic registry must be short-lived inside Application.attach", callback >= 0 && registry > callback)
        assertTrue("generic installer must construct only routed specs", installer.contains("GenericAppFeatures.selected("))

        val param = fakePackageReadyParam()
        val prefs = PrefMap()
        assertTrue(GenericAppFeatures.selected(param, prefs, false, false, false, false).isEmpty())

        val statusBar = GenericAppFeatures.selected(param, prefs, false, true, false, false)
        assertEquals(1, statusBar.size)
        assertEquals("system_statusbarcolor", statusBar.single().preferenceKey)

        val noOverscroll = GenericAppFeatures.selected(param, prefs, false, false, true, false)
        assertEquals(1, noOverscroll.size)
        assertEquals("system_nooverscroll", noOverscroll.single().preferenceKey)

        val media = GenericAppFeatures.selected(param, prefs, false, false, false, true)
        assertEquals(1, media.size)
        assertEquals("controls_volumemedia_up", media.single().preferenceKey)
    }

    @Test
    fun representativeFeaturesHaveCorrectTargetPhaseAndKey() {
        val launcherReady = LauncherPackageReadyFeatures.all(fakePackageReadyParam(), PrefMap()).first()
        assertEquals(FeatureTarget.LAUNCHER, launcherReady.target)
        assertEquals(InstallPhase.PACKAGE_READY, launcherReady.phase)

        val launcherAttach = LauncherPostAttachFeatures.all(fakePackageReadyParam(), PrefMap()).first()
        assertEquals(FeatureTarget.LAUNCHER, launcherAttach.target)
        assertEquals(InstallPhase.APPLICATION_ATTACHED, launcherAttach.phase)

        val input = InputMethodFeatures.all(fakePackageReadyParam(), PrefMap()).first()
        assertEquals(FeatureTarget.ANY, input.target)
        assertEquals(InstallPhase.PACKAGE_READY, input.phase)

        val settings = SettingsFeatures.all(fakePackageReadyParam(), PrefMap()).first { it.preferenceKey == "miuizer_settingsiconpos" }
        assertEquals(FeatureTarget.SYSTEM_PACKAGE, settings.target)
        assertEquals(InstallPhase.PACKAGE_READY, settings.phase)
        assertEquals("miuizer_settingsiconpos", settings.preferenceKey)

        val security = SecurityCenterFeatures.all(fakePackageReadyParam(), PrefMap()).first { it.preferenceKey == "various_appdetails" }
        assertEquals(FeatureTarget.SYSTEM_PACKAGE, security.target)
        assertEquals(InstallPhase.PACKAGE_READY, security.phase)

        val phone = PhoneFeatures.all(fakePackageReadyParam(), PrefMap()).first()
        assertEquals(FeatureTarget.SYSTEM_PACKAGE, phone.target)
        assertEquals(InstallPhase.PACKAGE_READY, phone.phase)

        val power = PowerKeeperFeatures.all(fakePackageReadyParam(), PrefMap()).first()
        assertEquals(FeatureTarget.SYSTEM_PACKAGE, power.target)
        assertEquals(InstallPhase.PACKAGE_READY, power.phase)

        val guard = GuardProviderFeatures.all(fakePackageReadyParam(), PrefMap()).first()
        assertEquals(FeatureTarget.SYSTEM_PACKAGE, guard.target)
        assertEquals(InstallPhase.PACKAGE_READY, guard.phase)

        val pkgInstaller = PackageInstallerFeatures.all(fakePackageReadyParam(), PrefMap()).first()
        assertEquals(FeatureTarget.SYSTEM_PACKAGE, pkgInstaller.target)
        assertEquals(InstallPhase.PACKAGE_READY, pkgInstaller.phase)

        val media = MediaFeatures.all(fakePackageReadyParam(), PrefMap()).first()
        assertEquals(FeatureTarget.SYSTEM_PACKAGE, media.target)
        assertEquals(InstallPhase.PACKAGE_READY, media.phase)

        val android = AndroidPackageFeatures.all(fakePackageReadyParam(), PrefMap()).first()
        assertEquals(FeatureTarget.SYSTEM_PACKAGE, android.target)
        assertEquals(InstallPhase.PACKAGE_READY, android.phase)

        val generic = GenericAppFeatures.all(fakePackageReadyParam(), PrefMap()).first { it.preferenceKey == "system_statusbarcolor" }
        assertEquals(FeatureTarget.ANY, generic.target)
        assertEquals(InstallPhase.APPLICATION_ATTACHED, generic.phase)
        assertEquals("system_statusbarcolor", generic.preferenceKey)
    }

    private fun fakePackageReadyParam(): XposedModuleInterface.PackageReadyParam {
        return Proxy.newProxyInstance(
            XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.example.test"
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "toString" -> "FakePackageReadyParam"
                "equals" -> false
                "hashCode" -> 0
                else -> null
            }
        } as XposedModuleInterface.PackageReadyParam
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        val candidates = when {
            relativePath.endsWith(".java") -> {
                listOf(
                    relativePath.replace(".java", ".kt"),
                    relativePath
                )
            }
            relativePath.endsWith(".kt") -> {
                listOf(
                    relativePath,
                    relativePath.replace(".kt", ".java")
                )
            }
            else -> listOf(relativePath)
        }
        while (true) {
            for (path in candidates) {
                val candidate = File(directory, path)
                if (candidate.isFile) return candidate.readText()
            }
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
