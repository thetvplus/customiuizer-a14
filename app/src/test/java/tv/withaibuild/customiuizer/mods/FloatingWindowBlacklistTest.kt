package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class FloatingWindowBlacklistTest {
    private fun run(value: Any? = null, failure: Throwable? = null): Any? {
        var calls = 0
        val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            check(method.name == "proceed") { "Unexpected chain access: ${method.name}" }
            calls++
            failure?.let { throw it }
            value
        } as XposedInterface.Chain
        return try { SystemWindowHooks.floatingWindowBlacklistHook().intercept(chain) }
        finally { assertEquals(1, calls) }
    }

    @Test fun nativeFailurePreservesIdentity() {
        val failure = IllegalStateException("Synthetic native failure")
        assertSame(failure, assertThrows(IllegalStateException::class.java) { run(failure = failure) })
    }

    @Test fun nativeFatalFailurePreservesIdentity() {
        val failure = OutOfMemoryError("Synthetic native fatal failure")
        assertSame(failure, assertThrows(OutOfMemoryError::class.java) { run(failure = failure) })
    }

    @Test fun successfulListKeepsIdentityAndRequiredCameraEntry() {
        val list = arrayListOf("synthetic.example")
        assertSame(list, run(list))
        assertEquals(listOf("com.android.camera"), list)
    }

    @Test fun nativeNullRemainsNull() { assertNull(run()) }
}
