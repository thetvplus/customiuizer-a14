package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GestureDependenciesResolverTest {

    private val readyDeps = GestureDependencies(
        ownerId = 1,
        classLoaderIdentity = "cl-1",
        displayManager = Any(),
        displayId = 0,
        minimumBacklight = 0.0f,
        maximumBacklight = 1.0f,
        audioManager = Any(),
        statusBarHeight = 80,
        screenWidth = 1080,
        density = 3.0f,
    )

    private val dummyContext = Any()

    @Test
    fun allReady() {
        val resolver = FakeGestureDependenciesResolver()
        resolver.set(1, "cl-1", GestureDependenciesResult.Ready(readyDeps))
        val result = resolver.prepare(1, "cl-1", dummyContext)
        assertNotNull((result as GestureDependenciesResult.Ready).dependencies)
    }

    @Test
    fun notReady() {
        val resolver = FakeGestureDependenciesResolver()
        assertEquals(GestureDependenciesResult.NotReady, resolver.prepare(1, "cl-1", dummyContext))
    }

    @Test
    fun failedTransient() {
        val resolver = FakeGestureDependenciesResolver()
        resolver.set(1, "cl-1", GestureDependenciesResult.FailedTransient("missing field"))
        assertEquals(GestureDependenciesResult.FailedTransient("missing field"), resolver.prepare(1, "cl-1", dummyContext))
    }

    @Test
    fun ownerChange_resetsResult() {
        val resolver = FakeGestureDependenciesResolver()
        resolver.set(1, "cl-1", GestureDependenciesResult.Ready(readyDeps))
        assertEquals(GestureDependenciesResult.NotReady, resolver.prepare(2, "cl-1", dummyContext))
    }

    @Test
    fun classLoaderChange_resetsResult() {
        val resolver = FakeGestureDependenciesResolver()
        resolver.set(1, "cl-1", GestureDependenciesResult.Ready(readyDeps))
        assertEquals(GestureDependenciesResult.NotReady, resolver.prepare(1, "cl-2", dummyContext))
    }

    @Test
    fun repeatedPrepare_returnsSame() {
        val resolver = FakeGestureDependenciesResolver()
        resolver.set(1, "cl-1", GestureDependenciesResult.Ready(readyDeps))
        val first = resolver.prepare(1, "cl-1", dummyContext)
        val second = resolver.prepare(1, "cl-1", dummyContext)
        assertEquals(first, second)
    }

    @Test
    fun noHalfPublishedObject() {
        val incomplete = readyDeps.copy(displayManager = Any(), audioManager = null)
        val resolver = FakeGestureDependenciesResolver()
        resolver.set(1, "cl-1", GestureDependenciesResult.Ready(incomplete))
        val result = resolver.prepare(1, "cl-1", dummyContext) as GestureDependenciesResult.Ready
        assertNull(result.dependencies.audioManager)
    }
}
