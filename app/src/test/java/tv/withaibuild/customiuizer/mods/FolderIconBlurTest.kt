package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import android.graphics.drawable.Drawable
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderIconBlurTest {
    class Native { fun prepare() {}; fun draw() {}; fun supported() = false }
    class Modern {
        companion object { @JvmStatic fun `onWallpaperColorChanged$lambda-3`(folder: Modern, drawable: Drawable) {} }
    }
    class Legacy { fun accept(drawable: Drawable) {} }
    class InvalidConsumer { fun accept(drawable: Drawable) = true }
    private val prepare = Native::class.java.getMethod("prepare")
    private val draw = Native::class.java.getMethod("draw")
    private val gate = Native::class.java.getMethod("supported")
    private val active = linkedMapOf<Method, XposedInterface.Hooker>()
    private val attempts = ArrayList<Method>()
    private val retired = ArrayList<Method>()
    private var previous: XposedInterface? = null
    private var rejected: Method? = null
    private var failure: Throwable = IllegalStateException("installation rejected")
    private var rollbackFailure: Throwable? = null
    private var allowed = true

    @Before fun framework() {
        previous = XposedHelpers.moduleInst
        XposedHelpers.moduleInst = proxy(XposedInterface::class.java) { _, name, args ->
            check(name == "hook")
            val target = args!![0] as Method
            attempts.add(target)
            proxy(XposedInterface.HookBuilder::class.java) { builder, operation, values ->
                when (operation) {
                    "setPriority", "setExceptionMode" -> builder
                    "intercept" -> {
                        if (target == rejected) throw failure
                        active[target] = values!![0] as XposedInterface.Hooker
                        proxy(XposedInterface.HookHandle::class.java) { _, action, _ ->
                            when (action) {
                                "getExecutable" -> target
                                "unhook" -> {
                                    retired.add(target); active.remove(target)
                                    rollbackFailure?.let { throw it }
                                    null
                                }
                                else -> error(action)
                            }
                        }
                    }
                    else -> error(operation)
                }
            }
        }
    }
    @After fun restore() { XposedHelpers.moduleInst = previous }

    private fun install() = installFolderIconBlurHooks(gate, listOf(prepare, draw)) { allowed }
    private fun call(method: Method, native: () -> Any?): Any? = active.getValue(method).intercept(
        proxy(XposedInterface.Chain::class.java) { _, name, _ -> check(name == "proceed"); native() })
    private fun supported() = call(gate) { false }

    @Test fun bothVerifiedWallpaperCallbacksResolveBeforeAnyHookIsInstalled() {
        val modern = folderIconWallpaperBlurEntry(Modern::class.java, null)
        assertEquals(Modern::class.java, modern.declaringClass)
        assertArrayEquals(arrayOf(Modern::class.java, Drawable::class.java), modern.parameterTypes)
        val legacy = folderIconWallpaperBlurEntry(Native::class.java, Legacy::class.java)
        assertEquals(Legacy::class.java, legacy.declaringClass)
        assertThrows(IllegalArgumentException::class.java) { folderIconWallpaperBlurEntry(Native::class.java, null) }
        assertThrows(IllegalArgumentException::class.java) { folderIconWallpaperBlurEntry(Native::class.java, InvalidConsumer::class.java) }
        assertTrue("Preflight cannot install a partial native blur", attempts.isEmpty())
    }

    @Test fun onlyFolderPreparationAndDrawingCanUseTheNativeBlurCapability() {
        install()
        assertEquals(listOf(prepare, draw, gate), attempts)
        assertEquals(false, supported())
        assertEquals(true, call(prepare) { call(draw) { supported() } })
        assertEquals(false, supported())
        allowed = false
        assertEquals(false, call(draw) { supported() })
        assertEquals("Keep an already supported ROM path", true, call(gate) { true })
    }

    @Test fun anOrdinaryOrFatalNativeFailureLeavesNoScopeBehind() {
        install()
        for (error in listOf(IllegalArgumentException("native"), OutOfMemoryError("native"))) {
            assertSame(error, assertThrows(Throwable::class.java) {
                call(prepare) { call(draw) { throw error } }
            })
            assertEquals(false, supported())
        }
    }

    @Test fun anotherThreadNeverInheritsAConcurrentFolderScope() {
        install()
        val other = AtomicReference<Any?>()
        call(prepare) {
            val thread = Thread { other.set(supported()) }
            thread.start(); thread.join(2000)
            assertFalse(thread.isAlive)
            assertEquals(true, supported())
        }
        assertEquals(false, other.get())
    }

    @Test fun aPartialScopeInstallationNeverActivatesTheGate() {
        rejected = draw
        assertThrows(IllegalStateException::class.java) { install() }
        assertEquals(listOf(prepare, draw), attempts)
        assertEquals(listOf(prepare), retired)
        assertTrue(active.isEmpty())
    }

    @Test fun aRejectedGateRetiresAllNewScopesInReverseOrder() {
        rejected = gate
        assertThrows(IllegalStateException::class.java) { install() }
        assertEquals(listOf(draw, prepare), retired)
        assertTrue(active.isEmpty())
    }

    @Test fun aFatalRollbackIsPropagatedAndCannotReplaceAnOriginalFatalFailure() {
        rejected = gate
        val cleanup = OutOfMemoryError("rollback")
        rollbackFailure = cleanup
        assertSame(cleanup, assertThrows(Throwable::class.java) { install() })
        val primary = ThreadDeath()
        failure = primary
        assertSame(primary, assertThrows(Throwable::class.java) { install() })
        assertArrayEquals(arrayOf(cleanup), primary.suppressed)
    }

    private fun <T> proxy(type: Class<T>, body: (Any, String, Array<out Any?>?) -> Any?): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, args -> body(self, method.name, args) })
}
