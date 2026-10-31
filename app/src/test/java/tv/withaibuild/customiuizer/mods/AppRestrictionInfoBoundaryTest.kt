package tv.withaibuild.customiuizer.mods

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.app.Activity
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AppRestrictionInfoBoundaryTest {
    class VendorInfo : ApplicationInfo()
    @Test fun incompletePackageWithApplicationInfoIsRejected() {
        val info = PackageInfo().apply { applicationInfo = ApplicationInfo().apply { uid = 10001 } }
        assertNull(Various.resolveAppStateTarget(info, 0))
    }
    @Test fun targetValidationUsesTheActualActivityContextUser() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val info = PackageInfo().apply {
                packageName = "target.app"
                applicationInfo = ApplicationInfo().apply { packageName = "target.app"; uid = 10001 }
            }
            assertEquals(Various.AppStateTarget("target.app", 10001), Various.appStateTarget(controller.get(), info))
            info.applicationInfo!!.uid = 1010001
            assertNull(Various.appStateTarget(controller.get(), info))
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun appStateTargetsRequireMatchingPackageUidAndTheActualContextUser() {
        fun info(uid: Int) = PackageInfo().apply {
            packageName = "target.app"
            applicationInfo = ApplicationInfo().apply { packageName = "target.app"; this.uid = uid }
        }
        assertEquals(Various.AppStateTarget("target.app", 10001), Various.resolveAppStateTarget(info(10001), 0))
        assertEquals(Various.AppStateTarget("target.app", 1010001), Various.resolveAppStateTarget(info(1010001), 10))
        assertNull(Various.resolveAppStateTarget(info(1010001), 0))
        assertNull(Various.resolveAppStateTarget(info(10001), 10))
        assertNull(Various.resolveAppStateTarget(info(-1), 0))
        assertNull(Various.resolveAppStateTarget(info(10001), -1))
        assertNull(Various.resolveAppStateTarget(info(10001).apply { applicationInfo!!.packageName = "other.app" }, 0))
        assertNull(Various.resolveAppStateTarget(PackageInfo(), 0))
    }
    @Test fun restrictionUiReceivesACopyWithoutChangingSharedPackageMetadata() {
        val original = ApplicationInfo().apply {
            packageName = "system.app"; uid = 10001
            flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_DEBUGGABLE
        }
        val chain = Chain(original)
        val result = Various.appRestrictionInfoHook().intercept(chain) as ApplicationInfo
        assertNotSame(original, result)
        assertEquals(ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_DEBUGGABLE, original.flags)
        assertEquals(ApplicationInfo.FLAG_DEBUGGABLE, result.flags)
        assertEquals(original.packageName, result.packageName); assertEquals(original.uid, result.uid)
        assertEquals(1, chain.nativeCalls)
    }
    @Test fun otherQueriesUsersAndAlreadyOrdinaryAppsKeepTheirExactNativeObject() {
        val system = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM }
        for ((flags, user) in listOf(0 to 0, 128 to 10)) {
            val chain = Chain(system, flags, user)
            assertSame(system, Various.appRestrictionInfoHook().intercept(chain))
            assertEquals(1, chain.nativeCalls)
        }
        val ordinary = ApplicationInfo()
        assertSame(ordinary, Various.appRestrictionInfoHook().intercept(Chain(ordinary)))
        val vendor = VendorInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM }
        assertSame("Unknown vendor subclasses must not lose their extra metadata", vendor,
            Various.appRestrictionInfoHook().intercept(Chain(vendor)))
        assertEquals(ApplicationInfo.FLAG_SYSTEM, vendor.flags)
        assertNull(Various.appRestrictionInfoHook().intercept(Chain(null)))
    }
    @Test fun nativeFailureCannotTriggerMetadataMutationOrArgumentReads() {
        val original = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM }
        val error = IllegalStateException("native package query")
        val chain = Chain(original, failure = error)
        assertSame(error, assertThrows(IllegalStateException::class.java) { Various.appRestrictionInfoHook().intercept(chain) })
        assertEquals(1, chain.nativeCalls); assertEquals(0, chain.argumentReads)
        assertEquals(ApplicationInfo.FLAG_SYSTEM, original.flags)
    }
    private class Chain(private val result: Any?, private val flags: Int = 128, private val user: Int = 0,
        private val failure: Throwable? = null) : XposedInterface.Chain {
        var nativeCalls = 0; var argumentReads = 0
        override fun getExecutable(): Executable = error("unused")
        override fun getThisObject(): Any = error("no owner needed")
        override fun getArgs(): List<Any?> = error("must not copy arguments")
        override fun getArg(index: Int): Any? { argumentReads++; return if (index == 3) flags else user }
        override fun proceed(): Any? { nativeCalls++; failure?.let { throw it }; return result }
        override fun proceed(args: Array<Any?>): Any? = error("unused")
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
