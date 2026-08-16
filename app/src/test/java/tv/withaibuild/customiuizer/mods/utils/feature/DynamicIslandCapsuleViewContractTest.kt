package tv.withaibuild.customiuizer.mods.utils.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source contract for the single-shape Dynamic Island capsule.
 *
 * The unit-test android.jar has no functional Canvas, Path or layout pass, so the capsule's
 * defining property - the painted pill and the child clip come from one cached [android.graphics.Path]
 * that is rebuilt only on size changes - is asserted against the production body.
 */
class DynamicIslandCapsuleViewContractTest {

    private val source: String by lazy {
        source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/" +
                "DynamicIslandCapsuleView.kt"
        )
    }

    @Test
    fun capsuleCachesItsShapeInsteadOfAllocatingPerFrame() {
        assertTrue(
            "capsule must cache the shape rect",
            source.contains("private val shapeRect = RectF()")
        )
        assertTrue(
            "capsule must cache the shape path",
            source.contains("private val shapePath = Path()")
        )
        assertTrue(
            "capsule must cache the fill paint",
            source.contains("private val fillPaint = Paint(")
        )

        for (function in listOf("onDraw", "dispatchDraw")) {
            val body = functionBody(source, function)
            // drawPath / clipPath are allowed; only Path(...) construction is banned.
            assertFalse(
                "$function must not allocate a Path per frame",
                Regex("""\bPath\s*\(""").containsMatchIn(body),
            )
            assertFalse(
                "$function must not allocate a RectF per frame",
                Regex("""\bRectF\s*\(""").containsMatchIn(body),
            )
            assertFalse(
                "$function must not allocate a Paint per frame",
                Regex("""\bPaint\s*\(""").containsMatchIn(body),
            )
        }
    }

    @Test
    fun shapeIsBuiltOnlyInOnSizeChanged() {
        val body = functionBody(source, "onSizeChanged")

        assertTrue("size change must reset the cached path", body.contains("shapePath.reset()"))
        assertTrue(
            "size change must rebuild the rounded rect from the new bounds",
            body.contains("shapePath.addRoundRect(shapeRect,")
        )
        assertTrue(
            "the radius must be a pure function of the capsule height",
            body.contains("val radius = h / 2f")
        )
        assertTrue(
            "the shape rect must span the whole View",
            body.contains("shapeRect.set(0f, 0f, w.toFloat(), h.toFloat())")
        )

        // Nothing outside onSizeChanged may mutate the cached geometry.
        assertEquals(
            "addRoundRect must appear exactly once",
            1,
            source.split("addRoundRect").size - 1
        )
        assertEquals(
            "shapePath.reset must appear exactly once",
            1,
            source.split("shapePath.reset()").size - 1
        )
    }

    @Test
    fun backgroundAndChildClipUseTheSamePath() {
        val drawBody = functionBody(source, "onDraw")
        assertTrue(
            "the pill must be painted from the cached path",
            drawBody.contains("canvas.drawPath(shapePath, fillPaint)")
        )

        val dispatchBody = functionBody(source, "dispatchDraw")
        assertTrue(
            "children must be clipped to the very same path",
            dispatchBody.contains("canvas.clipPath(shapePath)")
        )
        assertTrue(
            "the clip must be balanced with a save/restore pair",
            dispatchBody.contains("val save = canvas.save()") &&
                dispatchBody.contains("canvas.restoreToCount(save)")
        )
        assertTrue(
            "children must still be drawn inside the clip",
            dispatchBody.contains("super.dispatchDraw(canvas)")
        )
    }

    @Test
    fun outlineClippingIsNotASecondShapeOwner() {
        val code = stripComments(source)
        assertFalse(
            "the capsule must not install an outline provider",
            code.contains("ViewOutlineProvider") || code.contains("outlineProvider")
        )
        assertTrue(
            "outline clipping must stay off so the path clip is the only rounded clip",
            code.contains("clipToOutline = false")
        )
        assertFalse(
            "the capsule must not enable outline clipping",
            code.contains("clipToOutline = true")
        )
        assertFalse(
            "the capsule must not delegate the pill to a background drawable",
            code.contains("GradientDrawable")
        )
        assertTrue(
            "the capsule must not inherit a background",
            code.contains("background = null")
        )
    }

    @Test
    fun capsuleDoesNotDisableChildClipping() {
        // The old capsule declared "never clips" and turned clipChildren off. The pill is the
        // clip now, so nothing may opt children out of it.
        assertFalse(
            "the capsule must not disable clipChildren",
            source.contains("clipChildren = false")
        )
        assertFalse(
            "the capsule must not disable clipToPadding",
            source.contains("clipToPadding = false")
        )
    }

    private fun source(path: String): String {
        var directory = File(System.getProperty("user.dir")!!).absoluteFile
        while (true) {
            val candidate = File(directory, path)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: error("Repository root not found for $path")
        }
    }

    private fun stripComments(source: String): String =
        source
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""(?m)^\s*//.*$"""), "")

    private fun functionBody(source: String, functionName: String): String {
        val start = source.indexOf("fun $functionName(")
        require(start >= 0) { "$functionName must exist" }
        var depth = 0
        var seenBrace = false
        for (i in start until source.length) {
            when (source[i]) {
                '{' -> {
                    seenBrace = true
                    depth++
                }
                '}' -> {
                    depth--
                    if (seenBrace && depth == 0) return source.substring(start, i + 1)
                }
            }
        }
        error("$functionName body is not well-formed")
    }
}
