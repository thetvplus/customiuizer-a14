package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.view.View
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DynamicIslandStatusBarFadeTest {
    private val islandA = Any()
    private val islandB = Any()
    private val targets = mutableListOf<Float>()
    private var pendingEnd: (() -> Unit)? = null

    @Before
    fun setUp() {
        DynamicIslandStatusBarFade.contentsRef = null
        DynamicIslandStatusBarFade.ownerRef = null
        DynamicIslandStatusBarFade.originalAlpha = null
        targets.clear()
        pendingEnd = null
        DynamicIslandStatusBarFade.alphaRunner = DynamicIslandStatusBarFade.AlphaRunner { view, target, onEnd ->
            pendingEnd = onEnd
            targets += target
            view.alpha = target
        }
    }

    @After
    fun tearDown() {
        DynamicIslandStatusBarFade.contentsRef = null
        DynamicIslandStatusBarFade.ownerRef = null
        DynamicIslandStatusBarFade.originalAlpha = null
        DynamicIslandStatusBarFade.alphaRunner = DynamicIslandStatusBarFade.AlphaRunner { view, target, onEnd ->
            view.animate().cancel()
            view.animate().alpha(target).withEndAction(onEnd).start()
        }
    }

    @Test
    fun acquireFadesFromSavedOriginalAlpha() {
        val contents = FakeContents(alpha = 0.8f)
        DynamicIslandStatusBarFade.bind(contents)
        DynamicIslandStatusBarFade.acquire(islandA)

        assertEquals(listOf(0f), targets)
        assertEquals(0f, contents.alpha)
        assertEquals(0.8f, DynamicIslandStatusBarFade.originalAlpha)
        assertSame(islandA, DynamicIslandStatusBarFade.ownerRef?.get())
    }

    @Test
    fun staleDetachDoesNotRestoreWhileSuccessorOwnsTheIsland() {
        val contents = FakeContents(alpha = 1f)
        DynamicIslandStatusBarFade.bind(contents)
        DynamicIslandStatusBarFade.acquire(islandA)
        DynamicIslandStatusBarFade.acquire(islandB)
        DynamicIslandStatusBarFade.release(islandA)

        assertEquals(listOf(0f, 0f), targets)
        assertEquals(0f, contents.alpha)
        assertSame(islandB, DynamicIslandStatusBarFade.ownerRef?.get())
        assertEquals(1f, DynamicIslandStatusBarFade.originalAlpha)
    }

    @Test
    fun currentOwnerDetachRestoresOriginalAlpha() {
        val contents = FakeContents(alpha = 0.6f)
        DynamicIslandStatusBarFade.bind(contents)
        DynamicIslandStatusBarFade.acquire(islandA)
        DynamicIslandStatusBarFade.release(islandA)

        assertEquals(listOf(0f, 0.6f), targets)
        pendingEnd?.invoke()
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        assertNull(DynamicIslandStatusBarFade.originalAlpha)
        assertEquals(0.6f, contents.alpha)
    }

    @Test
    fun successorAcquireKeepsOriginalWhenRestoreIsInterrupted() {
        val contents = FakeContents(alpha = 0.75f)
        DynamicIslandStatusBarFade.bind(contents)
        DynamicIslandStatusBarFade.acquire(islandA)
        DynamicIslandStatusBarFade.release(islandA)
        DynamicIslandStatusBarFade.acquire(islandB)

        assertEquals(listOf(0f, 0.75f, 0f), targets)
        pendingEnd?.invoke()
        assertEquals(0.75f, DynamicIslandStatusBarFade.originalAlpha)
        assertSame(islandB, DynamicIslandStatusBarFade.ownerRef?.get())
    }

    @Test
    fun missingContentsDoesNotPreventIslandOwnership() {
        DynamicIslandStatusBarFade.acquire(islandA)
        DynamicIslandStatusBarFade.release(islandA)

        assertTrue(targets.isEmpty())
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
    }

    @Test
    fun newStatusBarContentsFadesIfAnIslandIsAlreadyActive() {
        val first = FakeContents(alpha = 1f)
        DynamicIslandStatusBarFade.bind(first)
        DynamicIslandStatusBarFade.acquire(islandA)

        val second = FakeContents(alpha = 1f)
        DynamicIslandStatusBarFade.bind(second)

        assertEquals(listOf(0f, 0f), targets)
        assertEquals(0f, second.alpha)
        assertEquals(1f, DynamicIslandStatusBarFade.originalAlpha)
    }

    private class FakeContents(alpha: Float = 1f) : View(null as Context?) {
        private var storedAlpha = alpha
        override fun isAttachedToWindow(): Boolean = true
        override fun getAlpha(): Float = storedAlpha
        override fun setAlpha(alpha: Float) {
            storedAlpha = alpha
        }
    }
}
