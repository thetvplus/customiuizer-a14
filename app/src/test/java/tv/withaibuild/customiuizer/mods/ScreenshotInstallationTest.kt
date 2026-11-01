package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.os.Environment
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScreenshotInstallationTest {
    private val old = System.systemMiscConfig
    private val loader = javaClass.classLoader!!
    class Saver { fun save(a: Any?, b: Any?, c: Any?, d: Any?, format: Bitmap.CompressFormat, f: Any?, g: Boolean, h: Boolean) = true }
    private val save = Saver::class.java.getDeclaredMethod("save", Any::class.java, Any::class.java, Any::class.java, Any::class.java, Bitmap.CompressFormat::class.java, Any::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
    @After fun restore() { System.systemMiscConfig = old }
    private fun config(format: Int = 3) { System.systemMiscConfig = old.copy(screenshotFormat = format, screenshotPath = 1) }
    private fun handle(onClose: () -> Unit = {}) = object : CustomMethodUnhooker { override fun unhook() = onClose() }
    @Test fun noUniqueSaverCannotReportInstalledOrRegisterPartialHooks() {
        config(); var installs = 0
        assertEquals(FeatureInstallResult.FAILED_PERMANENT, System.installScreenshotHooks(loader, { null }) { _, _ -> installs++; handle() })
        assertEquals(0, installs)
    }
    @Test fun resolutionCompletesBeforeAnyFrameworkHookIsRegistered() {
        config(); var installs = 0; var installsAtResolution = -1
        assertEquals(FeatureInstallResult.INSTALLED, System.installScreenshotHooks(loader, { installsAtResolution = installs; save }) { _, _ -> installs++; handle() })
        assertEquals(0, installsAtResolution); assertTrue(installs > 0)
    }
    @Test fun queryFailureIsTransientWithoutAnyPartialHook() {
        config(); var installs = 0
        assertEquals(FeatureInstallResult.FAILED_TRANSIENT, System.installScreenshotHooks(loader, { throw IllegalStateException("query unavailable") }) { _, _ -> installs++; handle() })
        assertEquals(0, installs)
    }
    @Test fun fatalQueryEscapesWithoutRegisteringHooks() {
        config(); var installs = 0; val failure = OutOfMemoryError("query fatal")
        assertSame(failure, assertThrows(OutOfMemoryError::class.java) { System.installScreenshotHooks(loader, { throw failure }) { _, _ -> installs++; handle() } })
        assertEquals(0, installs)
    }
    @Test fun failedInstallationRollsBackEarlierHooks() {
        config(); var attempts = 0; var closes = 0
        assertEquals(FeatureInstallResult.FAILED_TRANSIENT, System.installScreenshotHooks(loader, { save }) { _, _ -> attempts++; if (attempts == 2) null else handle { closes++ } })
        assertEquals(2, attempts); assertEquals(1, closes)
    }
    @Test fun fatalInstallationPreservesItsIdentityAndReleasesEarlierHooks() {
        config(); var attempts = 0; var closes = 0; val failure = OutOfMemoryError("install fatal")
        assertSame(failure, assertThrows(OutOfMemoryError::class.java) { System.installScreenshotHooks(loader, { save }) { _, _ -> attempts++; if (attempts == 2) throw failure else handle { closes++ } } })
        assertEquals(2, attempts); assertEquals(1, closes)
    }
    @Test fun frameworkCallbacksStayPassiveUntilTheWholeBatchCommits() {
        config(); val registered = ArrayList<Pair<Method, MethodHook>>(); var nameDuringInstall: String? = null
        assertEquals(FeatureInstallResult.INSTALLED, System.installScreenshotHooks(loader, { save }) { method, hook ->
            registered.add(method to hook)
            if (method.name == "update" && method.parameterCount == 4) {
                val values = ContentValues().apply { put("_display_name", "Screenshot_original.jpg") }
                val chain = Chain(listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values, null, null))
                hook.intercept(chain); nameDuringInstall = values.getAsString("_display_name"); assertEquals(1, chain.calls)
            }
            handle()
        })
        assertEquals("Screenshot_original.jpg", nameDuringInstall)
        val hook = registered.single { it.first.name == "update" && it.first.parameterCount == 4 }.second
        val values = ContentValues().apply { put("_display_name", "Screenshot_original.jpg") }; val chain = Chain(listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values, null, null))
        assertSame(chain.result, hook.intercept(chain)); assertEquals("Screenshot_original.png", values.getAsString("_display_name")); assertEquals(1, chain.calls)
    }
    @Test fun jpegAlsoPreparesTheSaverForLaterDynamicFormatChanges() {
        config(2); var resolutions = 0; var saveInstalls = 0
        assertEquals(FeatureInstallResult.INSTALLED, System.installScreenshotHooks(loader, { resolutions++; save }) { method, _ -> if (method == save) saveInstalls++; handle() })
        assertEquals(1, resolutions); assertEquals(1, saveInstalls)
    }
    @Test fun saverReadsTheCurrentFormatAndPreservesEveryOtherArgument() {
        config(3); val hook = System.screenshotSaveFormatHook(); config(4)
        val args = listOf(Any(), Any(), Uri.EMPTY, Any(), Bitmap.CompressFormat.PNG, Any(), false, true); val chain = Chain(args)
        assertSame(chain.result, hook.intercept(chain)); assertEquals(1, chain.calls); assertEquals(Bitmap.CompressFormat.WEBP, chain.received!![4])
        for (i in args.indices.filter { it != 4 }) assertSame(args[i], chain.received!![i])
    }
    @Test fun saverRetainsNativeFailureWithoutRetry() {
        config(); val failure = IllegalStateException("native save failed")
        val chain = Chain(listOf(null, null, null, null, Bitmap.CompressFormat.JPEG, null, false, false), failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { System.screenshotSaveFormatHook().intercept(chain) }); assertEquals(1, chain.calls)
    }
    @Test fun thumbnailCompressionRetainsTheNativePolicy() {
        config(4); val chain = Chain(listOf(Bitmap.CompressFormat.JPEG, 100, java.io.ByteArrayOutputStream()))
        assertSame(chain.result, System.screenshotCompressHook().intercept(chain)); assertEquals(chain.arguments, chain.received); assertEquals(1, chain.calls)
    }
    @Test fun pngCompressionKeepsLosslessQualityAndNativeCallCount() {
        config(3); System.systemMiscConfig = System.systemMiscConfig.copy(screenshotQuality = 42)
        val chain = Chain(listOf(Bitmap.CompressFormat.JPEG, 100, object : OutputStream() { override fun write(b: Int) = Unit }))
        assertSame(chain.result, System.screenshotCompressHook().intercept(chain)); assertEquals(Bitmap.CompressFormat.PNG, chain.received!![0]); assertEquals(100, chain.received!![1]); assertEquals(1, chain.calls)
    }
    @Test fun customInsertionUsesRelativePathAndKeepsDataAbsentWhenNativeRequestUsesScopedStorage() {
        config(3)
        val folder = java.io.File(Environment.getExternalStorageDirectory(), "Pictures/SyntheticScreenshots").path
        System.systemMiscConfig = System.systemMiscConfig.copy(screenshotPath = 4, screenshotMyPath = folder)
        val original = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "Screenshot_test.jpg") }
        val chain = Chain(listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, original), owner = RuntimeEnvironment.getApplication().contentResolver)
        assertSame(chain.result, System.screenshotInsertHook().intercept(chain))
        val result = chain.received!![1] as ContentValues
        assertEquals("Pictures/SyntheticScreenshots/", result.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals("Screenshot_test.png", result.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertEquals("image/png", result.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertFalse(result.containsKey(MediaStore.MediaColumns.DATA))
        assertFalse(original.containsKey(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals(1, chain.calls)
    }
    @Test fun providerRejectedTopLevelDirectoryKeepsTheNativeDestination() {
        config(3)
        val folder = java.io.File(Environment.getExternalStorageDirectory(), "Documents/SyntheticScreenshots").path
        System.systemMiscConfig = System.systemMiscConfig.copy(screenshotPath = 4, screenshotMyPath = folder)
        val original = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Screenshot_test.png")
            put(MediaStore.MediaColumns.DATA, "/native/Screenshot_test.png")
        }
        val chain = Chain(listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, original))
        System.screenshotInsertHook().intercept(chain)
        val result = chain.received!![1] as ContentValues
        assertEquals("/native/Screenshot_test.png", result.getAsString(MediaStore.MediaColumns.DATA))
        assertFalse(result.containsKey(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals(1, chain.calls)
    }
    @Test fun invalidCustomFolderKeepsNativeLocationAndNativeFailureIsNeverRetried() {
        config(4)
        System.systemMiscConfig = System.systemMiscConfig.copy(screenshotPath = 4, screenshotMyPath = "/not-a-storage-volume/Pictures/Test")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Screenshot_test.png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Screenshots/")
        }
        val failure = IllegalArgumentException("Synthetic provider rejection")
        val chain = Chain(listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values), failure, RuntimeEnvironment.getApplication().contentResolver)
        assertSame(failure, assertThrows(IllegalArgumentException::class.java) { System.screenshotInsertHook().intercept(chain) })
        assertEquals("DCIM/Screenshots/", (chain.received!![1] as ContentValues).getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals(1, chain.calls)
    }
    private class Chain(val arguments: List<Any?>, val failure: Throwable? = null, val owner: Any? = null) : XposedInterface.Chain {
        val result = Any(); var calls = 0; var received: List<Any?>? = null
        override fun getExecutable(): Executable = error("No reflective native method access")
        override fun getThisObject(): Any = owner ?: error("No owner access")
        override fun getArgs(): List<Any?> = arguments
        override fun getArg(index: Int): Any? = arguments[index]
        override fun proceed(): Any? { calls++; received = arguments; failure?.let { throw it }; return result }
        override fun proceed(args: Array<Any?>): Any? { calls++; received = args.toList(); failure?.let { throw it }; return result }
        override fun proceedWith(receiver: Any): Any? = error("No receiver rewriting")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("No receiver rewriting")
    }
}
