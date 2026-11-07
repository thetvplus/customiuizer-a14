package tv.withaibuild.customiuizer.mods.island

import android.app.Activity
import android.graphics.RenderEffect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers.getField

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandBlurCacheTest {
    @Test fun firstUseCreatesOnlyTheVisitedEffectAndRepeatedHandoffsReuseIt() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            val shell=IslandActivityView(host)
            host.setContentView(shell)
            val effects=getField<Array<RenderEffect?>>(shell,"slotBlur")
            assertTrue(effects.all { it==null })
            val resolve=IslandActivityView::class.java.getDeclaredMethod("slotBlur",Int::class.javaPrimitiveType).apply { isAccessible=true }
            val first=resolve.invoke(shell,42)
            assertEquals(1,effects.count { it!=null })
            repeat(20) {
                assertSame(first,resolve.invoke(shell,42))
                for(level in effects.indices) assertSame(resolve.invoke(shell,level),resolve.invoke(shell,level))
            }
            assertEquals(IslandMotion.CONTENT_BLUR_STEPS,effects.count { it!=null })
            val clear=IslandActivityView::class.java.getDeclaredMethod("clearContentEffects").apply { isAccessible=true }
            clear.invoke(shell)
            assertEquals(-1,getField<Int>(shell,"blurLevel"))
            assertSame(first,resolve.invoke(shell,42))
        } finally { host.finish() }
    }
}
