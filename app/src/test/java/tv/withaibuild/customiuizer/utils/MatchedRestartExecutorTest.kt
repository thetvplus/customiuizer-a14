package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchedRestartExecutorTest {

    @Test
    fun root_check_is_done_once_and_no_targets_run_without_root() {
        val commands = mutableListOf<String>()
        val executor = MatchedRestartExecutor { cmd ->
            commands += cmd
            if (cmd == "id") Pair(1, "uid=1000") else Pair(0, "")
        }

        val result = executor.execute(RestartMask.LAUNCHER or RestartMask.SYSTEMUI)

        assertFalse(result.rootGranted)
        assertEquals(1, commands.size)
        assertEquals("id", commands[0])
        assertEquals(0, result.attempted)
        assertEquals(0, result.succeeded)
        assertEquals(0, result.failed)
    }

    @Test
    fun order_is_security_center_then_launcher_then_systemui() {
        val commands = mutableListOf<String>()
        val executor = MatchedRestartExecutor { cmd ->
            commands += cmd
            when {
                cmd == "id" -> Pair(0, "uid=0")
                cmd.startsWith("am force-stop com.miui.securitycenter") -> Pair(0, "")
                cmd.startsWith("am force-stop com.miui.home") -> Pair(0, "")
                cmd == "pidof com.android.systemui" -> Pair(0, "1234")
                cmd == "kill -9 1234" -> Pair(0, "")
                else -> Pair(1, "")
            }
        }

        val result = executor.execute(
            RestartMask.SYSTEMUI or RestartMask.LAUNCHER or RestartMask.SECURITY_CENTER
        )

        assertTrue(result.rootGranted)
        assertEquals(3, result.attempted)
        assertEquals(3, result.succeeded)
        assertEquals(0, result.failed)

        // SECURITY_CENTER -> LAUNCHER -> SYSTEMUI
        val order = commands.filter { it != "id" }
        assertEquals(
            listOf(
                "am force-stop com.miui.securitycenter",
                "am force-stop com.miui.home",
                "pidof com.android.systemui",
                "kill -9 1234"
            ),
            order
        )
    }

    @Test
    fun attempt_all_even_after_failure() {
        val executor = MatchedRestartExecutor { cmd ->
            when {
                cmd == "id" -> Pair(0, "uid=0")
                cmd.startsWith("am force-stop com.miui.securitycenter") -> Pair(1, "fail")
                cmd.startsWith("am force-stop com.miui.home") -> Pair(0, "")
                cmd == "pidof com.android.systemui" -> Pair(0, "5678")
                cmd == "kill -9 5678" -> Pair(0, "")
                else -> Pair(1, "")
            }
        }

        val result = executor.execute(
            RestartMask.SECURITY_CENTER or RestartMask.LAUNCHER or RestartMask.SYSTEMUI
        )

        assertTrue(result.rootGranted)
        assertEquals(3, result.attempted)
        assertEquals(2, result.succeeded)
        assertEquals(1, result.failed)
        assertEquals(RestartMask.SECURITY_CENTER, result.failedMask)
    }

    @Test
    fun no_system_reboot_or_soft_reboot_commands() {
        val commands = mutableListOf<String>()
        val executor = MatchedRestartExecutor { cmd ->
            commands += cmd
            if (cmd == "id") Pair(0, "uid=0") else Pair(0, "")
        }

        executor.execute(RestartMask.LAUNCHER)

        assertTrue(commands.none { it.contains("reboot") })
        assertTrue(commands.none { it.contains("setprop") && it.contains("sys") })
    }

    @Test
    fun single_target_mask() {
        val commands = mutableListOf<String>()
        val executor = MatchedRestartExecutor { cmd ->
            commands += cmd
            if (cmd == "id") Pair(0, "uid=0") else Pair(0, "")
        }

        val result = executor.execute(RestartMask.LAUNCHER)

        assertEquals(1, result.attempted)
        assertEquals(1, result.succeeded)
        assertEquals(0, result.failed)
        assertEquals(listOf("id", "am force-stop com.miui.home"), commands)
    }
}
