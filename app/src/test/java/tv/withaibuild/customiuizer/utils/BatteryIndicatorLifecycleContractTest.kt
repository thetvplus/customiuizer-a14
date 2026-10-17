package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.ref.WeakReference

class BatteryIndicatorLifecycleContractTest {

    @Test
    fun fullChargeDuplicatesDoNotRedraw() {
        val source = source()
        assertTrue(source.contains("val charging = isCharging && !isCharged"))
        assertTrue(source.contains("mIsBeingCharged == charging"))
        assertFalse(source.contains("mIsBeingCharged == isCharging && !isCharged"))
    }

    @Test
    fun layoutUpdatesAreCoalescedWithoutCoroutineJobs() {
        val source = source()
        assertTrue(source.contains("if (updatePosted) return"))
        assertTrue(source.contains("if (!post(updateRunnable)) updatePosted = false"))
        assertFalse(source.contains("viewScope.launch { update() }"))
        assertTrue(source.contains("removeCallbacks(updateRunnable)"))
    }

    @Test
    fun receiverAndHostReferencesFollowViewLifetime() {
        val source = source()
        assertTrue(source.contains("ModuleHelper.registerOwnedReceiver("))
        assertTrue(source.contains("ModuleHelper.unregisterOwnedReceiver(this, RECEIVER_KEY, broadcastReceiver)"))
        assertTrue(source.contains("mStatusBar = null"))
        assertFalse(source.contains("context.registerReceiver(broadcastReceiver"))
    }

    @Test
    fun preferenceObserverDoesNotStronglyRetainViewOwner() {
        val ownerClassName = "tv.withaibuild.customiuizer.utils.BatteryIndicator"
        val observerClass = Class.forName(
            "$ownerClassName\$BatteryIndicatorPreferenceObserver"
        )
        val ownerFields = observerClass.declaredFields.filter { field ->
            field.type.name == ownerClassName
        }
        val weakOwnerFields = observerClass.declaredFields.filter { field ->
            field.type == WeakReference::class.java
        }

        assertTrue("observer must not contain a strong BatteryIndicator field", ownerFields.isEmpty())
        assertEquals("observer must contain exactly one weak owner field", 1, weakOwnerFields.size)
    }

    @Test
    fun asynchronousScopeAndThrowableBoundariesPreserveOom() {
        val source = source()
        assertTrue(source.contains("Dispatchers.Main + ModuleHelper.coroutineFailureHandler"))
        assertTrue(source.contains("catch (oom: OutOfMemoryError)"))
    }

    private fun source(): String {
        val relativePath = "app/src/main/java/tv/withaibuild/customiuizer/utils/BatteryIndicator.kt"
        var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
