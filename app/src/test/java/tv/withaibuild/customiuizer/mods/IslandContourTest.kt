package tv.withaibuild.customiuizer.mods

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandContourTest {
    @Test fun lengthMorphKeepsExactBoundsAndTheSameCapsuleAlgorithmAtBothEndpoints() {
        val shape=IslandShape()
        for (width in 184..280) {
            shape.resize(width,40)
            val bounds=RectF().also { shape.path.computeBounds(it,true) }
            assertEquals(width.toFloat(),bounds.width(),.001f)
            assertEquals(40f,bounds.height(),.001f)
            assertTrue(shape.contains(width*.5f,20f))
            val same=shape.path.generationId
            shape.resize(width,40)
            assertEquals(same,shape.path.generationId)
        }
    }

    @Test fun physicalEndCapsStayIdenticalWhileOnlyTheStraightMiddleChangesLength() {
        val reference = Path().also { IslandCapsuleGeometry.build(it, 180f, 40f) }
        val expected = Region().apply { setPath(reference, Region(0, 0, 180, 40)) }
        val shape = IslandShape()
        for (sx in listOf(.5f, .75f, 1f, 1.025f)) {
            shape.resize(360, 40, sx, 1f)
            val visible = Path(shape.path).apply { transform(Matrix().apply { setScale(sx, 1f) }) }
            val width = (360 * sx).toInt()
            val actual = Region().apply { setPath(visible, Region(0, 0, width, 40)) }
            for (y in 0 until 40) for (x in 0 until 45) {
                assertEquals("left $sx $x,$y", expected.contains(x, y), actual.contains(x, y))
                assertEquals("right $sx $x,$y", expected.contains(179 - x, y), actual.contains(width - 1 - x, y))
            }
        }
    }

    @Test fun nonUniformScaleKeepsPhysicalCapsuleCurvatureAndExactBounds() {
        val shape = IslandShape()
        shape.resize(328, 112, .6f, .5f)
        val visible = Path(shape.path).apply { transform(Matrix().apply { setScale(.6f, .5f) }) }
        val expected = Path().also { IslandCapsuleGeometry.build(it, 196.8f, 56f) }
        val actualBounds = RectF().also { visible.computeBounds(it, true) }
        assertEquals(196.8f, actualBounds.width(), .001f)
        assertEquals(56f, actualBounds.height(), .001f)
        val actual = Region().apply { setPath(visible, Region(0, 0, 197, 56)) }
        val reference = Region().apply { setPath(expected, Region(0, 0, 197, 56)) }
        for (y in 0 until 56) for (x in 0 until 197)
            assertEquals("$x,$y", reference.contains(x, y), actual.contains(x, y))
    }
}
