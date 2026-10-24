package tv.withaibuild.customiuizer.mods.statusbarheight

import android.graphics.Rect
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.SystemStatusBarInsetsHooks
import tv.withaibuild.customiuizer.mods.statusbarheight.StatusBarHeightArchitectureCH1Test.FakeChain
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.StatusBarInsetsTestAccess
import tv.withaibuild.customiuizer.utils.PrefMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
class StatusBarInsetsFrameLifecycleTest {
    @After fun cleanup() {
        StatusBarInsetsTestAccess.resetDiagnostics()
        StatusBarInsetsTestAccess.resetConfig()
    }

    private fun callback(raw: Int = 40): SystemStatusBarInsetsHooks.SetFrameCallback {
        StatusBarHeightConfig.configure(PrefMap().apply { put("system_statusbarheight", raw) },
            RuntimeEnvironment.getApplication().resources)
        val type = Source::class.java.getDeclaredField("mType").also { it.isAccessible = true }
        return SystemStatusBarInsetsHooks.SetFrameCallback(InsetsSourceCapability(null, true, true,
            InsetsTypeInfo(InsetsTypeEncoding.MODERN_PUBLIC, 1, 2, 128), type, null, null, null))
    }

    @Test fun rectOverloadPreservesEveryDegenerateShapeAndItsIdentity() {
        val hook = callback()
        for (input in listOf(Rect(), Rect(0, 0, 1080, 0), Rect(2, 0, 2, 104),
                Rect(4, 0, 2, 104), Rect(0, 20, 1080, 10))) {
            val before = Rect(input)
            val chain = FakeChain(Source(), arrayOf(input))
            hook.intercept(chain)
            assertFalse("must keep original arguments for $before", chain.calledWithArgs)
            assertSame(input, chain.args[0])
            assertEquals(before, input)
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test fun fourIntOverloadPreservesEveryDegenerateShape() {
        val hook = callback()
        for (input in listOf(arrayOf<Any?>(0, 0, 0, 0), arrayOf<Any?>(0, 0, 1080, 0),
                arrayOf<Any?>(2, 0, 2, 104), arrayOf<Any?>(4, 0, 2, 104),
                arrayOf<Any?>(0, 20, 1080, 10))) {
            val chain = FakeChain(Source(), input)
            hook.intercept(chain)
            assertFalse("must keep original arguments", chain.calledWithArgs)
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test fun clientHiddenButValidGeometryStillUsesTheChosenHeight() {
        val hook = callback()
        for (rect in listOf(true, false)) {
            val source = Source(visible = false)
            val original = Rect(0, 0, 1080, 104)
            val chain = FakeChain(source, if (rect) arrayOf(original) else arrayOf(0, 0, 1080, 104))
            hook.intercept(chain)
            assertTrue(chain.calledWithArgs)
            if (rect) {
                assertEquals(Rect(0, 0, 1080, 40), chain.lastArgs!![0])
                assertEquals(104, original.bottom)
            } else assertEquals(40, chain.lastArgs!![3])
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test fun validGeometryHandlesExplicit27SmallerAndLargerHeights() {
        for (raw in listOf(27, 40, 12, 110)) {
            val hook = callback(raw)
            for (rect in listOf(true, false)) {
                val chain = FakeChain(Source(), if (rect) arrayOf(Rect(0, 20, 1080, 124))
                    else arrayOf(0, 20, 1080, 124))
                hook.intercept(chain)
                assertTrue(chain.calledWithArgs)
                val bottom = if (rect) (chain.lastArgs!![0] as Rect).bottom else chain.lastArgs!![3]
                assertEquals(20 + raw, bottom)
                assertEquals(1, chain.proceedCount)
            }
        }
    }

    @Test fun defaultHeightAndUnrelatedSourcesDoNotRewriteEitherOverload() {
        for (raw in listOf(11, 40)) {
            val hook = callback(raw)
            for (type in listOf(1, 2, 8, 128)) {
                if (raw == 40 && type == 1) continue
                for (rect in listOf(true, false)) {
                    val chain = FakeChain(Source(type), if (rect) arrayOf(Rect(0, 0, 1080, 104))
                        else arrayOf(0, 0, 1080, 104))
                    hook.intercept(chain)
                    assertFalse(chain.calledWithArgs)
                    assertEquals(1, chain.proceedCount)
                }
            }
        }
    }

    class Source(@JvmField val mType: Int = 1, @JvmField val visible: Boolean = true)
}
