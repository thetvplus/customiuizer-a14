package tv.withaibuild.customiuizer.mods

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

class SystemUIControlCenterHooksExtractPluginLoaderTest {

    class ClassLoaderFactory(val loader: ClassLoader) {
        fun get(): ClassLoader = loader
    }

    class ValidFactory(
        val mAppInfo: ApplicationInfo,
        val mClassLoaderFactory: ClassLoaderFactory
    )

    class LoaderByName(
        val info: ApplicationInfo,
        val mPluginClassLoader: ClassLoaderFactory
    )

    private fun appInfo(packageName: String): ApplicationInfo {
        return ApplicationInfo().apply { this.packageName = packageName }
    }

    private val testLoader: ClassLoader
        get() = this::class.java.classLoader!!

    @Test
    fun extractsPluginLoaderFromValidFactory() {
        val factory = ValidFactory(
            mAppInfo = appInfo("miui.systemui.plugin"),
            mClassLoaderFactory = ClassLoaderFactory(testLoader)
        )
        val result = SystemUIControlCenterHooks.extractPluginLoader(factory)
        assertSame(testLoader, result)
    }

    @Test
    fun returnsNullForNonPluginPackage() {
        val factory = ValidFactory(
            mAppInfo = appInfo("com.example"),
            mClassLoaderFactory = ClassLoaderFactory(testLoader)
        )
        assertNull(SystemUIControlCenterHooks.extractPluginLoader(factory))
    }

    @Test
    fun fallsBackToApplicationInfoFieldByType() {
        val expectedLoader = testLoader
        val info = appInfo("miui.systemui.plugin")
        val factory = LoaderByName(info, ClassLoaderFactory(expectedLoader))
        val result = SystemUIControlCenterHooks.extractPluginLoader(factory)
        assertEquals(expectedLoader, result)
    }

    @Test
    fun fallsBackToClassLoaderFieldByName() {
        val expectedLoader = testLoader
        val info = appInfo("miui.systemui.plugin")
        val factory = LoaderByName(info, ClassLoaderFactory(expectedLoader))
        val result = SystemUIControlCenterHooks.extractPluginLoader(factory)
        assertEquals(expectedLoader, result)
    }

    @Test(expected = OutOfMemoryError::class)
    fun rethrowsOutOfMemoryErrorInAppInfoRead() {
        SystemUIControlCenterHooks.extractPluginLoader(
            factory = Any(),
            getObjectField = { _, _ -> throw OutOfMemoryError("oom") }
        )
    }

    @Test(expected = ThreadDeath::class)
    fun rethrowsThreadDeathInAppInfoRead() {
        SystemUIControlCenterHooks.extractPluginLoader(
            factory = Any(),
            getObjectField = { _, _ -> throw ThreadDeath() }
        )
    }

    @Test(expected = InternalError::class)
    fun rethrowsVirtualMachineErrorInAppInfoRead() {
        SystemUIControlCenterHooks.extractPluginLoader(
            factory = Any(),
            getObjectField = { _, _ -> throw InternalError("vm error") }
        )
    }

    @Test(expected = StackOverflowError::class)
    fun rethrowsStackOverflowErrorInAppInfoRead() {
        SystemUIControlCenterHooks.extractPluginLoader(
            factory = Any(),
            getObjectField = { _, _ -> throw StackOverflowError("soe") }
        )
    }

    @Test
    fun ordinaryReflectionFailureFallsBackAndContinues() {
        val expectedLoader = testLoader
        val info = appInfo("miui.systemui.plugin")
        val factory = LoaderByName(info, ClassLoaderFactory(expectedLoader))
        val result = SystemUIControlCenterHooks.extractPluginLoader(
            factory = factory,
            getObjectField = { obj, name ->
                if (name == "mAppInfo") {
                    throw NoSuchFieldError("mAppInfo missing")
                }
                XposedHelpers.getObjectField(obj, name)
            }
        )
        assertEquals(expectedLoader, result)
    }

    @Test(expected = OutOfMemoryError::class)
    fun rethrowsOutOfMemoryErrorInCallMethod() {
        val factory = ValidFactory(
            mAppInfo = appInfo("miui.systemui.plugin"),
            mClassLoaderFactory = ClassLoaderFactory(testLoader)
        )
        SystemUIControlCenterHooks.extractPluginLoader(
            factory = factory,
            callInstanceMethod = { _, _ -> throw OutOfMemoryError("oom in callMethod") }
        )
    }
}
