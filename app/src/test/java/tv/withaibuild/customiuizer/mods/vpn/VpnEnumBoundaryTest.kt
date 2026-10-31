package tv.withaibuild.customiuizer.mods.vpn

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URLClassLoader
import javax.tools.ToolProvider

class VpnEnumBoundaryTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun fixture(state: String, block: (ClassLoader) -> Unit) {
        val directory = temporary.newFolder()
        val files = listOf(
            "ActivityRecord" to "package com.android.server.wm; public class ActivityRecord { $state }",
            "Task" to "package com.android.server.wm; public class Task {}",
            "Vpn" to "package com.android.server.connectivity; public class Vpn {}",
        ).map { (name, body) -> directory.resolve("$name.java").apply { writeText(body) } }
        assertEquals(0, checkNotNull(ToolProvider.getSystemJavaCompiler()).run(null, null, null,
            "-proc:none", "-d", directory.path, *files.map { it.path }.toTypedArray()))
        object : URLClassLoader(arrayOf(directory.toURI().toURL()), javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith("com.android.server.wm.") && name != "com.android.server.connectivity.Vpn")
                    return super.loadClass(name, resolve)
                synchronized(this) {
                    val type = findLoadedClass(name) ?: findClass(name)
                    if (resolve) resolveClass(type)
                    return type
                }
            }
        }.use(block)
    }

    private fun rejects(state: String, message: String) = fixture(state) { loader ->
        val status = VpnAutoSwitchHooks.status
        val failure = assertThrows(IllegalStateException::class.java) { VpnAutoSwitchHooks.install(loader) }
        assertEquals(message, failure.message)
        assertEquals(status, VpnAutoSwitchHooks.status)
    }

    @Test fun nonEnumIsRejectedBeforeResolvingOrInstallingHooks() =
        rejects("public static class State {}", "ActivityRecord.State is not an enum")

    @Test fun emptyEnumIsRejectedBeforeResolvingOrInstallingHooks() =
        rejects("public enum State {}", "ActivityRecord.State is missing RESUMED")

    @Test fun missingResumedIsRejectedBeforeResolvingOrInstallingHooks() =
        rejects("public enum State { PAUSED }", "ActivityRecord.State is missing RESUMED")

    @Test fun enumNameIsIndependentOfDisplayText() = fixture(
        "public enum State { RESUMED; public String toString(){return \"display label\";} }"
    ) { loader ->
        // Reaching the next ABI field proves RESUMED was found by name, without installing hooks.
        assertEquals("packageName", assertThrows(NoSuchFieldException::class.java) {
            VpnAutoSwitchHooks.install(loader)
        }.message)
    }
}
