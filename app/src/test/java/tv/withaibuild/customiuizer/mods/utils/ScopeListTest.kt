package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Contract tests for the Xposed scope list and module metadata.
 *
 * The scope list defines which processes the module is allowed to load into.
 * With staticScope=true, the Xposed manager only shows these targets to the user.
 *
 * These tests enforce:
 * - Structural validity (no blanks, no duplicates, valid format)
 * - staticScope=true in module.prop
 * - All known production hook targets are present
 * - ProcessRouter targets are a subset of scope.list
 */
class ScopeListTest {

    private val scopeFile = locateScopeFile()
    private val modulePropFile = locateModulePropFile()

    @Test
    fun scopeList_isNotEmpty() {
        val lines = scopeFile.readLines()
        assertTrue("scope.list must not be empty", lines.isNotEmpty())
    }

    @Test
    fun scopeList_hasNoDuplicates() {
        val lines = scopeFile.readLines().filter { it.isNotBlank() }
        val unique = lines.toSet()
        assertEquals("scope.list must not contain duplicate package names", unique.size, lines.size)
    }

    @Test
    fun scopeList_hasNoBlankLines() {
        for ((index, line) in scopeFile.readLines().withIndex()) {
            assertFalse("scope.list contains a blank line at ${index + 1}", line.isBlank())
        }
    }

    @Test
    fun scopeList_entriesAreTrimmedAndLowercase() {
        for ((index, line) in scopeFile.readLines().withIndex()) {
            assertEquals(
                "scope.list line ${index + 1} has leading/trailing whitespace",
                line.trim(), line,
            )
            assertEquals(
                "scope.list line ${index + 1} must be lowercase",
                line.lowercase(), line,
            )
        }
    }

    @Test
    fun scopeList_containsCoreTargets() {
        val lines = scopeFile.readLines().toSet()
        val required = CORE_PRODUCTION_TARGETS
        for (pkg in required) {
            assertTrue("scope.list must contain production target: $pkg", pkg in lines)
        }
    }

    @Test
    fun scopeList_containsInputMethodTargets() {
        val lines = scopeFile.readLines().toSet()
        for (pkg in INPUT_METHOD_TARGETS) {
            assertTrue("scope.list must contain input method target: $pkg", pkg in lines)
        }
    }

    @Test
    fun moduleProp_staticScopeIsTrue() {
        val props = Properties()
        modulePropFile.reader().use { props.load(it) }
        assertEquals(
            "module.prop must declare staticScope=true",
            "true", props.getProperty("staticScope"),
        )
    }

    @Test
    fun moduleProp_hasRequiredApiVersions() {
        val props = Properties()
        modulePropFile.reader().use { props.load(it) }
        val minApi = props.getProperty("minApiVersion")?.toIntOrNull()
        val targetApi = props.getProperty("targetApiVersion")?.toIntOrNull()
        assertTrue("module.prop must declare minApiVersion >= 101", minApi != null && minApi >= 101)
        assertTrue("module.prop must declare targetApiVersion >= 102", targetApi != null && targetApi >= 102)
    }

    @Test
    fun processRouter_allInstallableTargetsInScope() {
        val scopeLines = scopeFile.readLines().toSet()
        val routerTargets = PROCESS_ROUTER_PACKAGE_TARGETS
        for (pkg in routerTargets) {
            assertTrue(
                "ProcessRouter installable target '$pkg' is missing from scope.list",
                pkg in scopeLines,
            )
        }
    }

    companion object {
        /**
         * Core production targets that MUST be in scope.list.
         * Derived from ProcessRouter explicit package dispatch + system_server scope.
         */
        val CORE_PRODUCTION_TARGETS = listOf(
            "system",
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.miui.home",
            "com.miui.securitycenter",
            "com.miui.powerkeeper",
            "com.miui.guardprovider",
            "com.miui.miwallpaper",
            "com.miui.screenshot",
            "com.miui.gallery",
            "com.android.incallui",
            "com.miui.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )

        /**
         * Input method targets explicitly listed in ProcessRouter.
         * These are the known IME packages on HyperOS / Chinese market devices.
         */
        val INPUT_METHOD_TARGETS = listOf(
            "com.baidu.input",
            "com.baidu.input_mi",
            "com.google.android.inputmethod.latin",
            "com.iflytek.inputmethod",
            "com.iflytek.inputmethod.miui",
            "com.sohu.inputmethod.sogou",
            "com.sohu.inputmethod.sogou.xiaomi",
            "com.tencent.wetype",
            "com.touchtype.swiftkey",
        )

        /**
         * All packages that ProcessRouter maps to an installable ProcessScope.
         * This is the authoritative list derived from ProcessRouter.resolve().
         */
        val PROCESS_ROUTER_PACKAGE_TARGETS = CORE_PRODUCTION_TARGETS + INPUT_METHOD_TARGETS
    }

    private fun locateScopeFile(): File {
        val start = System.getProperty("user.dir") ?: "."
        var dir = File(start).absoluteFile
        while (true) {
            val candidate = File(dir, "app/src/main/resources/META-INF/xposed/scope.list")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: error("Repository root not found")
        }
    }

    private fun locateModulePropFile(): File {
        val start = System.getProperty("user.dir") ?: "."
        var dir = File(start).absoluteFile
        while (true) {
            val candidate = File(dir, "app/src/main/resources/META-INF/xposed/module.prop")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: error("Repository root not found")
        }
    }
}
