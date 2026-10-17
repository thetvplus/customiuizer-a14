package tv.withaibuild.customiuizer.utils

import android.os.Looper
import android.os.DeadObjectException
import io.github.libxposed.service.XposedService
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Exercises the real service SDK across its Binder boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class NativeScopeRequestTest {
    private var previous: XposedService? = null
    private var scopeReads = 0
    private var requests = 0
    private var requested: List<String>? = null
    private var approval: List<String>? = null
    private var failure: String? = null
    private var scopeFailure: Throwable? = null
    private var requestFailure: Throwable? = null

    @Before fun save() { previous = XposedServiceManager.service }
    @After fun restore() { XposedServiceManager.service = previous }

    @Test fun largeSelectionReadsScopeOnceAndRequestsOnlyMissingPackages() {
        val granted = (0 until 500).map { "app.$it" }
        val all = (0 until 1000).map { "app.$it" }
        approval = all.drop(500)
        bind(granted)
        var result: Boolean? = null
        assertTrue(XposedServiceManager.requestScope(all) { ok, _ -> result = ok })
        assertNull("Binder callback must post to main", result)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(true, result)
        assertEquals(1, scopeReads)
        assertEquals(1, requests)
        assertEquals(all.drop(500), requested)
    }

    @Test fun grantedSelectionDoesNotCreateAnApprovalRequest() {
        bind(listOf("already.granted"))
        var result: Boolean? = null
        assertTrue(XposedServiceManager.requestScope(listOf("already.granted")) { ok, _ -> result = ok })
        assertEquals(true, result)
        assertEquals(1, scopeReads)
        assertEquals(0, requests)
    }

    @Test fun partialApprovalAndFailureStayUnsuccessful() {
        approval = listOf("app.one")
        bind(emptyList())
        var result: Boolean? = null
        var message: String? = null
        XposedServiceManager.requestScope(listOf("app.one", "app.two")) { ok, text -> result = ok; message = text }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, result)
        assertNull(message)
        failure = "denied"
        XposedServiceManager.requestScope(listOf("app.two")) { ok, text -> result = ok; message = text }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, result)
        assertEquals("denied", message)
    }

    @Test fun disconnectedServiceDoesNotInvokeTheCallback() {
        XposedServiceManager.service = null
        assertFalse(XposedServiceManager.requestScope(listOf("app.one")) { _, _ -> fail("no service") })
    }

    @Test fun deadServiceAndAuthorizationFailureDoNotCrashOrClaimApproval() {
        bind(emptyList())
        for (error in listOf(DeadObjectException(), SecurityException("not authorized"))) {
            scopeFailure = error
            assertFalse(XposedServiceManager.requestScope(listOf("app.one")) { _, _ -> fail("no approval") })
        }
        assertEquals(2, scopeReads)
        assertEquals(0, requests)
    }

    @Test fun failedRequestDoesNotRetryOrClaimApproval() {
        bind(emptyList())
        for (error in listOf(DeadObjectException(), SecurityException("not authorized"))) {
            requestFailure = error
            assertFalse(XposedServiceManager.requestScope(listOf("app.one")) { _, _ -> fail("no approval") })
        }
        assertEquals(2, scopeReads)
        assertEquals(2, requests)
    }

    @Test fun fatalNativeScopeFailuresStillEscape() {
        bind(emptyList())
        for (error in listOf(OutOfMemoryError("fixture"), ThreadDeath(), InternalError("fixture"))) {
            scopeFailure = error
            assertSame(error, assertThrows(error.javaClass) {
                XposedServiceManager.requestScope(listOf("app.one")) { _, _ -> fail("no approval") }
            })
            scopeFailure = null
            requestFailure = error
            assertSame(error, assertThrows(error.javaClass) {
                XposedServiceManager.requestScope(listOf("app.one")) { _, _ -> fail("no approval") }
            })
        }
    }

    @Test fun callerCallbackFailureIsNotMistakenForABinderFailure() {
        bind(listOf("already.granted"))
        val error = IllegalStateException("caller failure")
        assertSame(error, assertThrows(IllegalStateException::class.java) {
            XposedServiceManager.requestScope(listOf("already.granted")) { _, _ -> throw error }
        })
    }

    private fun bind(granted: List<String>) {
        val type = Class.forName("io.github.libxposed.service.IXposedService")
        val binder = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            when (method.name) {
                "getScope" -> { scopeReads++; scopeFailure?.let { throw it }; granted }
                "requestScope" -> {
                    requests++
                    requestFailure?.let { throw it }
                    @Suppress("UNCHECKED_CAST")
                    requested = args!![0] as List<String>
                    val callback = args[1]!!
                    val callbackType = Class.forName("io.github.libxposed.service.IXposedScopeCallback")
                    if (failure != null) callbackType.getMethod("onScopeRequestFailed", String::class.java).invoke(callback, failure)
                    else callbackType.getMethod("onScopeRequestApproved", List::class.java).invoke(callback, approval)
                    null
                }
                else -> error("Unexpected service operation: ${method.name}")
            }
        }
        val constructor = XposedService::class.java.getDeclaredConstructor(type).apply { isAccessible = true }
        XposedServiceManager.service = constructor.newInstance(binder)
    }
}
