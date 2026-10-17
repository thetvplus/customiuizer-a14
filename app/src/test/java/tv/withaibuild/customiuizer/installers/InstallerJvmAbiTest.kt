package tv.withaibuild.customiuizer.installers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Exact JVM ABI contract for every installer entry point.
 *
 * Each public static method must match the declared [MethodAbi] exactly:
 * - public static
 * - exact parameter types
 * - [Void.TYPE] return type
 * - [kotlin.jvm.JvmStatic] annotation
 */
@RunWith(Parameterized::class)
class InstallerJvmAbiTest(
    private val expectedAbi: MethodAbi,
) {

    data class MethodAbi(
        val className: String,
        val methodName: String,
        val parameterTypes: List<Class<*>>,
        val returnType: Class<*>,
    ) {
        val simpleClassName: String get() = className.substringAfterLast('.')
    }

    companion object {
        const val EXPECTED_INSTALLER_FILE_COUNT = 13

        private val packageReadyParam: Class<*>
            get() = Class.forName("io.github.libxposed.api.XposedModuleInterface\$PackageReadyParam")
        private val prefMap: Class<*>
            get() = Class.forName("tv.withaibuild.customiuizer.utils.PrefMap")

        private fun bool() = java.lang.Boolean.TYPE

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): List<Array<Any>> = EXPECTED_ABIS.map { arrayOf(it) }

        @JvmStatic
        val EXPECTED_ABIS: List<MethodAbi> = listOf(
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.AndroidPackageInstaller",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.GenericAppInstaller",
                "installPostAttach",
                listOf(packageReadyParam, prefMap, bool(), bool(), bool(), bool()),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.GuardProviderInstaller",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.InputMethodInstaller",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.LauncherInstaller",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.LauncherInstaller",
                "handleLoadLauncher",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.MediaInstaller",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.PackageInstallerRouter",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.PermissionControllerInstaller",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.PhoneInstaller",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.PowerKeeperInstaller",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.SecurityCenterInstaller",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.SettingsInstaller",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
            MethodAbi(
                "tv.withaibuild.customiuizer.installers.SystemUiInstaller",
                "install",
                listOf(packageReadyParam, prefMap),
                Void.TYPE,
            ),
        )

        @JvmStatic
        val EXPECTED_INSTALLER_FILES: Set<String> = EXPECTED_ABIS.map { it.simpleClassName }.toSortedSet()
    }

    @Test
    fun installerFileCountMatchesExpected() {
        val installersDir = File(projectRoot(), "app/src/main/java/tv/withaibuild/customiuizer/installers")
        val files = installersDir.listFiles { f -> f.isFile && f.name.endsWith(".kt") } ?: emptyArray()
        val names = files.map { it.nameWithoutExtension }.toSortedSet()

        assertEquals(
            "installer .kt file count changed; verify JAVA_BOUNDARY_ALLOWLIST and InstallerJvmAbiTest",
            EXPECTED_INSTALLER_FILE_COUNT,
            names.size,
        )
        assertEquals("installer .kt file names diverge from ABI table", EXPECTED_INSTALLER_FILES, names)
    }

    @Test
    fun jvmStaticInstallMethodMatchesAbi() {
        val cls = Class.forName(expectedAbi.className)
        assertTrue(
            "${expectedAbi.className} must be public",
            java.lang.reflect.Modifier.isPublic(cls.modifiers),
        )

        val method = cls.getMethod(
            expectedAbi.methodName,
            *expectedAbi.parameterTypes.toTypedArray(),
        )

        assertNotNull("${expectedAbi.className}.${expectedAbi.methodName} missing", method)
        assertTrue(
            "${expectedAbi.className}.${expectedAbi.methodName} must be public",
            java.lang.reflect.Modifier.isPublic(method.modifiers),
        )
        assertTrue(
            "${expectedAbi.className}.${expectedAbi.methodName} must be static",
            java.lang.reflect.Modifier.isStatic(method.modifiers),
        )
        assertEquals(
            "${expectedAbi.className}.${expectedAbi.methodName} return type mismatch",
            expectedAbi.returnType,
            method.returnType,
        )
        assertTrue(
            "${expectedAbi.className}.${expectedAbi.methodName} must be annotated with @JvmStatic",
            method.isAnnotationPresent(kotlin.jvm.JvmStatic::class.java),
        )
    }

    @Test
    fun allowlistMatchesInstallerFiles() {
        val allowlist = File(projectRoot(), "docs/JAVA_BOUNDARY_ALLOWLIST.md").readText()
        val allowlistFiles = Regex("installers/([A-Za-z0-9_]+)\\.kt")
            .findAll(allowlist)
            .map { it.groupValues[1] }
            .toSortedSet()

        val installersDir = File(projectRoot(), "app/src/main/java/tv/withaibuild/customiuizer/installers")
        val files = installersDir.listFiles { f -> f.isFile && f.name.endsWith(".kt") } ?: emptyArray()
        val fileNames = files.map { it.nameWithoutExtension }.toSortedSet()

        assertEquals(
            "JAVA_BOUNDARY_ALLOWLIST installer list diverges from app/src installers",
            fileNames,
            allowlistFiles,
        )
        assertEquals(
            "JAVA_BOUNDARY_ALLOWLIST must list exactly $EXPECTED_INSTALLER_FILE_COUNT installer files",
            EXPECTED_INSTALLER_FILE_COUNT,
            allowlistFiles.size,
        )
    }

    private fun projectRoot(): File {
        val dir = File(System.getProperty("user.dir"))
        return if (File(dir, "app/src").isDirectory) dir else dir.parentFile
    }
}
