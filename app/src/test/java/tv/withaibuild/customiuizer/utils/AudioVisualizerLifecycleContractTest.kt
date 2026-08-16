package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.ref.WeakReference

class AudioVisualizerLifecycleContractTest {

    @Test
    fun preferenceObserverDoesNotStronglyRetainViewOwner() {
        val ownerClassName = "tv.withaibuild.customiuizer.utils.AudioVisualizer"
        val observerClass = Class.forName(
            "$ownerClassName\$AudioVisualizerPreferenceObserver"
        )
        val ownerFields = observerClass.declaredFields.filter { field ->
            field.type.name == ownerClassName
        }
        val weakOwnerFields = observerClass.declaredFields.filter { field ->
            field.type == WeakReference::class.java
        }

        assertTrue("observer must not contain a strong AudioVisualizer field", ownerFields.isEmpty())
        assertEquals("observer must contain exactly one weak owner field", 1, weakOwnerFields.size)
    }

    @Test
    fun observerRegistrationAndDisposalRemainOwnerBound() {
        val source = source()
        assertTrue(source.contains("AudioVisualizerPreferenceObserver(this)"))
        assertTrue(source.contains("ModuleHelper.observePreferenceChange(preferenceObserver, this)"))
        assertTrue(source.contains("ModuleHelper.unregisterPreferenceObserver(this)"))
    }

    @Test
    fun disposeReleasesVisualizerBeforeCancellingOwnerScope() {
        val source = source()
        val disposeBlock = source.section("internal fun dispose() {", "callback?.invoke(this)")

        val mVisualizerNulled = disposeBlock.indexOf("mVisualizer = null")
        val releaseLaunch = disposeBlock.indexOf("viewScope.launch {")
        val releaseCall = disposeBlock.indexOf("releaseVisualizer(visualizer)")
        val cancelCall = disposeBlock.indexOf("viewScope.cancel()")

        assertTrue("mVisualizer owner reference must be cleared before release launch", mVisualizerNulled < releaseLaunch)
        assertTrue("releaseVisualizer must be launched before the scope is cancelled", releaseCall < cancelCall)
        assertTrue("viewScope.cancel() must live inside the release launch block", releaseLaunch < cancelCall)
    }

    @Test
    fun releaseVisualizerRunsOutsideCancellableOwnerScope() {
        val source = source()

        val releaseFun = source.block(
            "private suspend fun releaseVisualizer(visualizer: Visualizer?) = withContext("
        )

        assertTrue(
            "releaseVisualizer must use NonCancellable to survive owner-scope cancellation",
            releaseFun.contains("NonCancellable")
        )
    }

    @Test
    fun linkVisualizerPublicationIsGuardedAgainstOwnerScopeCancellation() {
        val source = source()

        val linkFun = source.block(
            "private suspend fun linkVisualizer(generation: Long) = withContext(Dispatchers.IO) {"
        )

        assertTrue(
            "linkVisualizer must guard candidate publication/release with NonCancellable",
            linkFun.contains("withContext(NonCancellable)")
        )
    }

    @Test
    fun linkVisualizerStateValidationAndPublicationAreUnderVisualizerLock() {
        val source = source()

        val linkFun = source.block(
            "private suspend fun linkVisualizer(generation: Long) = withContext(Dispatchers.IO) {"
        )

        val syncBlock = linkFun.block("synchronized(visualizerLock) {")

        val stateCheck = "if (detached || !mDisplaying || generation != visualizerGeneration)"
        val publication = "mVisualizer = candidate"

        assertTrue(
            "state validity check must live inside synchronized(visualizerLock)",
            syncBlock.contains(stateCheck)
        )
        assertTrue(
            "mVisualizer publication must live inside synchronized(visualizerLock)",
            syncBlock.contains(publication)
        )

        val stateCheckIndex = syncBlock.indexOf(stateCheck)
        val publicationIndex = syncBlock.indexOf(publication)
        assertTrue(
            "state validity check must precede mVisualizer = candidate",
            stateCheckIndex < publicationIndex
        )
    }

    @Test
    fun displayOnPublishesNewGenerationBeforeDisplaying() {
        val source = source()

        val checkStateChanged = source.block("private fun checkStateChanged() {")
        val displayOnBlock = checkStateChanged.block("if (!mDisplaying) {")

        val generationIndex = displayOnBlock.indexOf("++visualizerGeneration")
        val displayOnIndex = displayOnBlock.indexOf("mDisplaying = true")

        assertTrue(
            "++visualizerGeneration must precede mDisplaying = true in display-on",
            generationIndex < displayOnIndex
        )
    }

    private fun source(): String {
        val relativePath = "app/src/main/java/tv/withaibuild/customiuizer/utils/AudioVisualizer.kt"
        var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        check(startIndex >= 0) { "Could not find section start '$start'" }
        val endIndex = indexOf(end, startIndex + start.length)
        check(endIndex > startIndex) { "Could not find section end '$end' after '$start'" }
        return substring(startIndex, endIndex + end.length)
    }

    private fun String.block(start: String): String {
        val startIndex = indexOf(start)
        check(startIndex >= 0) { "Could not find block start '$start'" }
        val braceIndex = indexOf("{", startIndex)
        check(braceIndex >= 0) { "No opening brace for block '$start'" }
        var depth = 1
        var i = braceIndex + 1
        while (depth > 0 && i < length) {
            when (this[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        check(depth == 0) { "Could not find matching brace for block '$start'" }
        return substring(startIndex, i)
    }
}
