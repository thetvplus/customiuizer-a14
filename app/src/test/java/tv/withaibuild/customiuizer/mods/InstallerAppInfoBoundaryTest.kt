package tv.withaibuild.customiuizer.mods

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class InstallerAppInfoBoundaryTest {
    open class Base<T> { open fun bind(holder: T) = Unit }
    class Holder {
        val description = TextView(RuntimeEnvironment.getApplication()).apply { text = "native description" }
        val size = TextView(description.context).apply { text = "Size: 12 MB" }
        val extra = TextView(description.context).apply { text = "native source and verification" }
        val parent = LinearLayout(description.context).apply { addView(description); addView(size); addView(extra) }
        fun getTvDes() = description
        fun getAppSize() = size
    }
    class Apk {
        var previous: ApplicationInfo? = null
        var failure: Throwable? = null
        val pkg = PackageInfo().apply {
            versionName = "2.0"; longVersionCode = 200
            applicationInfo = ApplicationInfo().apply { minSdkVersion = 26; targetSdkVersion = 34 }
        }
        fun getInstalledPackageInfo() = previous
        fun getPackageInfo(): PackageInfo { failure?.let { throw it }; return pkg }
        fun getInstalledVersionName(): String? = "1.0"
        fun getInstalledVersionCode(): Int = 100
    }
    class Owner(@JvmField val apk: Apk) : Base<Holder>() {
        var nativeBinds = 0
        override fun bind(holder: Holder) { nativeBinds++; holder.description.text = "native description"; holder.size.text = "Size: 12 MB" }
        fun iconOnly(holder: Holder) = Unit
    }
    private fun access() = requireNotNull(InstallerAppInfo.prepare(Owner::class.java, Holder::class.java, Apk::class.java) {
        InstallerAppInfo.Labels("Name", "Code", "SDK")
    })
    @Test fun completeBindKeepsNativeChildrenAndAddsCurrentMetadataOnce() {
        val owner = Owner(Apk()); val holder = Holder(); val access = access()
        assertTrue(access.bind.isBridge)
        val chain = Chain(owner, holder)
        assertSame(chain.result, access.hook().intercept(chain))
        assertEquals(1, owner.nativeBinds); assertEquals(1, chain.calls)
        assertEquals(3, holder.parent.childCount)
        assertSame(holder.extra, holder.parent.getChildAt(2)); assertSame(holder.parent, holder.size.parent)
        assertEquals("native source and verification", holder.extra.text.toString())
        assertEquals(View.GONE, holder.size.visibility)
        assertEquals("Name: 2.0\nSize: 12 MB\nCode: 200\nSDK: 26-34", holder.description.text.toString())
        owner.apk.previous = ApplicationInfo().apply { minSdkVersion = 23; targetSdkVersion = 33 }
        access.hook().intercept(chain)
        assertEquals("Name: 1.0 ➟ 2.0\nSize: 12 MB\nCode: 100 ➟ 200\nSDK: 23-33 ➟ 26-34", holder.description.text.toString())
        assertEquals(3, holder.parent.childCount)
    }
    @Test fun missingMetadataAndModuleFailuresPreserveTheCompletedNativeBind() {
        val access = access()
        for (failure in listOf<Throwable?>(null, IllegalStateException("metadata unavailable"))) {
            val owner = Owner(Apk().apply { if (failure == null) pkg.applicationInfo = null else this.failure = failure })
            val holder = Holder(); access.hook().intercept(Chain(owner, holder))
            assertEquals("native description", holder.description.text.toString())
            assertEquals(View.VISIBLE, holder.size.visibility); assertEquals(3, holder.parent.childCount)
        }
    }
    @Test fun nativeFailureDoesNotReadTheHolderAndFatalMetadataFailurePropagates() {
        val owner = Owner(Apk()); val holder = Holder(); val access = access()
        val error = SecurityException("native bind rejected")
        val chain = Chain(owner, holder, error)
        assertSame(error, assertThrows(SecurityException::class.java) { access.hook().intercept(chain) })
        assertEquals(0, chain.argReads); assertEquals(0, owner.nativeBinds)
        val fatal = OutOfMemoryError("metadata fatal"); owner.apk.failure = fatal
        assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { access.hook().intercept(Chain(owner, holder)) })
    }
    private class Chain(private val owner: Owner, private val holder: Holder, private val failure: Throwable? = null) : XposedInterface.Chain {
        val result = Any(); var calls = 0; var argReads = 0
        override fun getExecutable(): Executable = error("metadata resolved before bind")
        override fun getThisObject(): Any = owner
        override fun getArgs(): List<Any?> = error("must not copy arguments")
        override fun getArg(index: Int): Any? { argReads++; return holder }
        override fun proceed(): Any? { calls++; failure?.let { throw it }; owner.bind(holder); return result }
        override fun proceed(args: Array<Any?>): Any? = error("unused")
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
