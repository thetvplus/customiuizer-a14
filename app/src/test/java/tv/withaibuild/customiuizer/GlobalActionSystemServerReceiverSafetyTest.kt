package tv.withaibuild.customiuizer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract tests for the system_server Global Action Receiver registered by
 * [tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks.setupGlobalActions].
 *
 * These are static source checks: the receiver runs inside system_server, so it
 * cannot be exercised in a plain JVM unit test. We verify the structural safety
 * boundaries instead.
 */
class GlobalActionSystemServerReceiverSafetyTest {

    private val sourceFile = "app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt"

    @Test
    fun phoneWindowManagerActionReceiver_onReceive_usesGuardedLambdaReturns() {
        val receiver = phoneWindowManagerActionReceiverSource()

        assertTrue(
            "onReceive must wrap the full business body in ModuleHelper.guarded",
            receiver.contains("ModuleHelper.guarded {")
        )

        assertTrue(
            "action == null must return from the guarded lambda, not onReceive",
            receiver.contains("intent.action ?: return@guarded")
        )

        assertTrue(
            "isTrustedBroadcast rejection must return from the guarded lambda",
            receiver.contains("return@guarded")
        )

        assertFalse(
            "onReceive must not use a bare return that exits the function early",
            regexBareReturn(receiver)
        )
    }

    @Test
    fun phoneWindowManagerActionReceiver_onReceive_unifiedResultCodeHandling() {
        val receiver = phoneWindowManagerActionReceiverSource()

        assertTrue(
            "failure path must set ACTION_FAILED for ordered broadcasts",
            receiver.contains("GlobalActions.ACTION_FAILED")
        )

        assertTrue(
            "success path must set ACTION_HANDLED for ordered broadcasts",
            receiver.contains("GlobalActions.ACTION_HANDLED")
        )

        val tail = receiver.section("if (isOrderedBroadcast)", "}, intentfilter, Context.RECEIVER_EXPORTED)")
        assertTrue(
            "ordered-broadcast result must be set in a unified tail guarded block",
            tail.contains("ModuleHelper.guarded {") && tail.contains("setResultCode(")
        )

        assertTrue(
            "setResultCode must choose result based on completed flag",
            tail.contains("if (completed)") || tail.contains("if (completed)")
        )
    }

    @Test
    fun phoneWindowManagerActionReceiver_setResultCode_isGuarded() {
        val receiver = phoneWindowManagerActionReceiverSource()

        val tail = receiver.section("if (isOrderedBroadcast)", "}, intentfilter, Context.RECEIVER_EXPORTED)")
        assertTrue(
            "final setResultCode must be wrapped in ModuleHelper.guarded",
            tail.contains("ModuleHelper.guarded {") && tail.contains("setResultCode(")
        )
    }

    @Test
    fun phoneWindowManagerActionReceiver_completedOnlyInsideBusinessBody() {
        val receiver = phoneWindowManagerActionReceiverSource()

        assertTrue(
            "completed = true must be present inside the when branches",
            receiver.contains("completed = true")
        )

        val whenBody = whenBody(receiver)
        assertTrue(
            "completed must be set to true inside the when branches",
            whenBody.contains("completed = true")
        )

        // completed = true must not appear after the when block; it is set per action branch.
        val afterWhen = receiver.substring(receiver.indexOf(whenBody) + whenBody.length)
        assertFalse(
            "completed = true must not be set unconditionally after the when block",
            afterWhen.contains("completed = true")
        )
    }

    @Test
    fun phoneWindowManagerActionReceiver_nullAction_entersFailureResult() {
        val receiver = phoneWindowManagerActionReceiverSource()

        assertTrue(
            "null action must short-circuit out of guarded business body",
            receiver.contains("intent.action ?: return@guarded")
        )

        val tail = receiver.section("if (isOrderedBroadcast)", "}, intentfilter, Context.RECEIVER_EXPORTED)")
        assertTrue(
            "null action must still reach the unified failure result",
            tail.contains("if (completed)") && tail.contains("GlobalActions.ACTION_FAILED")
        )
    }

    @Test
    fun phoneWindowManagerActionReceiver_trustRejection_entersFailureResult() {
        val receiver = phoneWindowManagerActionReceiverSource()

        assertTrue(
            "isTrustedBroadcast must still gate custom actions",
            receiver.contains("ModuleHelper.isTrustedBroadcast(")
        )

        assertTrue(
            "untrusted sender must still be rejected with ACTION_FAILED",
            receiver.contains("rejectionResultCode = GlobalActions.ACTION_FAILED")
        )

        val businessBody = receiver.section(
            "ModuleHelper.guarded {",
            "if (isOrderedBroadcast)"
        )
        assertTrue(
            "trust rejection must use return@guarded",
            businessBody.contains("return@guarded")
        )
    }

    @Test
    fun phoneWindowManagerActionReceiver_doesNotRethrow() {
        val receiver = phoneWindowManagerActionReceiverSource()

        assertFalse(
            "onReceive must not rethrow a caught Throwable",
            receiver.contains("throw t") || receiver.contains("throw e") ||
                receiver.contains("throw ex")
        )
    }

    @Test
    fun otherSystemUiReceivers_areNotModified() {
        val source = source()

        // status bar and freeform receivers are unrelated and must keep
        // their original registration shape. We do not wrap them in a completed flag.
        assertFalse(
            "statusBarActionReceiver must not carry a completed flag",
            source.sectionAfter("setupStatusBar").contains("completed")
        )

        assertFalse(
            "freeformModeReceiver must not carry a completed flag",
            source.sectionAfter("freeformModeReceiver").contains("completed")
        )
    }

    private fun regexBareReturn(source: String): Boolean {
        // Detect a bare 'return' inside onReceive that is not a labelled return.
        // This is a coarse heuristic: any 'return' followed by whitespace/newline
        // but not '@' is a non-local return.
        val regex = "\\breturn(?!@)\\b".toRegex()
        return regex.containsMatchIn(source)
    }

    private fun whenBody(source: String): String {
        val start = "when (action) {"
        val startIndex = source.indexOf(start)
        check(startIndex >= 0) { "Could not find 'when (action) {'" }

        var depth = 0
        var inString = false
        var escape = false

        for (i in startIndex until source.length) {
            val c = source[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (c == '\\') {
                    escape = true
                } else if (c == '"') {
                    inString = false
                }
                continue
            }

            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(startIndex, i + 1)
                    }
                }
            }
        }

        error("Could not find matching closing brace for 'when (action) {'")
    }

    private fun phoneWindowManagerActionReceiverSource(): String {
        val source = source()
        return source.section(
            "phoneWindowManagerActionReceiver",
            "}, intentfilter, Context.RECEIVER_EXPORTED)"
        )
    }

    private fun source(): String = source(sourceFile)

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
        check(startIndex >= 0) { "Could not find start marker '$start'" }
        val endIndex = indexOf(end, startIndex + start.length)
        check(endIndex > startIndex) { "Could not find end marker '$end' after '$start'" }
        return substring(startIndex, endIndex + end.length)
    }

    private fun String.sectionAfter(marker: String): String {
        val index = indexOf(marker)
        check(index >= 0) { "Could not find marker '$marker'" }
        return substring(index)
    }
}
