package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext
import java.lang.reflect.InvocationTargetException

class CoroutineFatalBoundaryTest {
    @Test fun asynchronousFatalErrorsIncludingWrappedOnesCannotBeLoggedAsOrdinaryFailures() {
        for (failure in listOf(OutOfMemoryError("injected"), ThreadDeath(), InternalError("injected"))) {
            val thrown = assertThrows(failure.javaClass) {
                ModuleHelper.coroutineFailureHandler.handleException(EmptyCoroutineContext, failure)
            }
            assertSame(failure, thrown)
            assertSame(failure, assertThrows(failure.javaClass) {
                ModuleHelper.coroutineFailureHandler.handleException(EmptyCoroutineContext, InvocationTargetException(failure))
            })
        }
    }
}
