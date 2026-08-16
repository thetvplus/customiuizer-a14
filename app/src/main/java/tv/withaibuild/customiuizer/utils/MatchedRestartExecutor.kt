package tv.withaibuild.customiuizer.utils

import android.util.Log

/**
 * Executes a matched-restart request given a 3-bit [mask].
 *
 * One root check, fixed order, attempt all selected targets, no soft/system reboot.
 * Failures are isolated and logged with bounded diagnostics.
 */
internal class MatchedRestartExecutor(
    private val commandRunner: (String) -> Pair<Int, String> = { cmd ->
        val result = AppHelper.executeRootCommand(cmd)
        Pair(result.first, result.second)
    }
) {

    data class Result(
        val rootGranted: Boolean,
        val attemptedMask: Int,
        val succeededMask: Int,
        val failedMask: Int
    ) {
        val attempted: Int get() = Integer.bitCount(attemptedMask)
        val succeeded: Int get() = Integer.bitCount(succeededMask)
        val failed: Int get() = Integer.bitCount(failedMask)
    }

    private companion object {
        private const val TAG = "miuizer"
        private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
        private const val LAUNCHER_PACKAGE = "com.miui.home"
        private const val SYSTEMUI_PROCESS = "com.android.systemui"

        // Fixed execution order: SECURITY_CENTER -> LAUNCHER -> SYSTEMUI
        private val ORDER = intArrayOf(
            RestartMask.SECURITY_CENTER,
            RestartMask.LAUNCHER,
            RestartMask.SYSTEMUI
        )
    }

    fun execute(mask: Int): Result {
        val idResult = commandRunner("id")
        val rootGranted = idResult.first == 0 && idResult.second.contains("uid=0")
        if (!rootGranted) {
            return Result(rootGranted = false, attemptedMask = 0, succeededMask = 0, failedMask = 0)
        }

        var attempted = 0
        var succeeded = 0
        var failed = 0

        for (bit in ORDER) {
            if (mask and bit == 0) continue

            attempted = attempted or bit

            val ok = when (bit) {
                RestartMask.SECURITY_CENTER -> forceStopPackage(SECURITY_CENTER_PACKAGE)
                RestartMask.LAUNCHER -> forceStopPackage(LAUNCHER_PACKAGE)
                RestartMask.SYSTEMUI -> killProcess(SYSTEMUI_PROCESS)
                else -> false
            }

            if (ok) {
                succeeded = succeeded or bit
            } else {
                failed = failed or bit
            }
        }

        return Result(rootGranted = true, attemptedMask = attempted, succeededMask = succeeded, failedMask = failed)
    }

    private fun forceStopPackage(packageName: String): Boolean {
        val cmd = "am force-stop $packageName"
        val result = commandRunner(cmd)
        if (result.first == 0) return true
        logShellFailure("force-stop", packageName, cmd, result.first, result.second)
        return false
    }

    private fun killProcess(processName: String): Boolean {
        val pidCmd = "pidof $processName"
        val pidResult = commandRunner(pidCmd)
        if (pidResult.first != 0 || pidResult.second.isBlank()) {
            logShellFailure("pidof", processName, pidCmd, pidResult.first, pidResult.second)
            return false
        }

        val pids = pidResult.second
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (pids.isBlank()) {
            logShellFailure("pidof", processName, pidCmd, pidResult.first, pidResult.second)
            return false
        }

        val killCmd = "kill -9 $pids"
        val killResult = commandRunner(killCmd)
        if (killResult.first == 0) return true
        logShellFailure("kill", processName, killCmd, killResult.first, killResult.second)
        return false
    }

    private fun logShellFailure(
        operation: String,
        target: String,
        command: String,
        exitCode: Int,
        output: String
    ) {
        val truncated = if (output.length > 240) output.take(240) + "…" else output
        Log.e(
            TAG,
            "Matched restart failed: target=$target, operation=$operation, " +
                "command=\"$command\", exit=$exitCode, output=\"$truncated\""
        )
    }

}
