package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GestureConfigPublisherTest {

    private val defaultConfig = GestureConfig(
        singleAction = 2,
        brightnessSensitivityFactor = 0.618f,
        volumeSensitivityFactor = 1.0f,
    )

    private val updatedConfig = GestureConfig(
        singleAction = 3,
        brightnessSensitivityFactor = 1.0f,
        volumeSensitivityFactor = 1.66f,
    )

    @Test
    fun getBeforePublish_usesFallback() {
        val publisher = GestureConfigPublisher({ defaultConfig }, fallback = defaultConfig)
        assertSame(defaultConfig, publisher.get())
    }

    @Test
    fun publishThenGet_returnsPublishedConfig() {
        val publisher = GestureConfigPublisher({ defaultConfig }, fallback = defaultConfig)
        publisher.publish()
        assertSame(defaultConfig, publisher.get())
    }

    @Test
    fun republish_replacesConfig() {
        var callCount = 0
        val publisher = GestureConfigPublisher({
            callCount++
            if (callCount == 1) defaultConfig else updatedConfig
        }, fallback = defaultConfig)

        publisher.publish()
        assertSame(defaultConfig, publisher.get())

        publisher.publish()
        assertSame(updatedConfig, publisher.get())
    }

    @Test
    fun publishFailure_retainsLastValid() {
        var shouldFail = false
        val publisher = GestureConfigPublisher({
            if (shouldFail) throw IllegalStateException("resolve failed") else updatedConfig
        }, fallback = defaultConfig)

        publisher.publish()
        assertSame(updatedConfig, publisher.get())

        shouldFail = true
        publisher.publish()
        assertSame(updatedConfig, publisher.get())
    }

    @Test
    fun firstPublishFailure_fallsBack() {
        val publisher = GestureConfigPublisher({ throw IllegalStateException("resolve failed") }, fallback = defaultConfig)
        publisher.publish()
        assertSame(defaultConfig, publisher.get())
    }

    @Test
    fun get_doesNotCallResolveOncePublished() {
        var callCount = 0
        val publisher = GestureConfigPublisher({
            callCount++
            defaultConfig
        })
        publisher.publish()
        assertEquals(1, callCount)
        publisher.get()
        publisher.get()
        assertEquals(1, callCount)
    }
}
