package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.SharedMemory
import android.os.UserHandle
import android.view.View
import android.widget.FrameLayout
import java.io.File
import java.nio.ByteOrder
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers.getField

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandReturnFusionTest {
    @Test fun strongerContactHasAVisibleMeniscusInsideTheOriginalWindow() {
        val shape = IslandShape().apply { resize(240, 40, 1f, 1f, 1f) }
        val region = Region().apply { setPath(shape.path, Region(0, 0, 240, 40)) }
        assertTrue("The centre remains inside the original bottom edge", region.contains(120, 39))
        assertFalse("Contact must deepen beyond the earlier three-pixel cue", region.contains(80, 36))
        assertTrue("The body remains substantial", region.contains(80, 34))
        val bounds = RectF().also { shape.path.computeBounds(it, true) }
        assertEquals(RectF(0f, 0f, 240f, 40f), bounds)
        exportShape(shape.path, "stronger-contact")
    }

    @Test fun aFullyMergedPageSkipsClipAndMeshDrawAndCanBecomeVisibleAgain() {
        withSession(true) { session ->
            val mesh = getField<Any>(session, "drawing")
            val vertices = getField<FloatArray>(mesh, "vertices")
            val rect = getField<RectF>(mesh, "rect")
            val paint = getField<Paint>(mesh, "paint")
            val texture = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888)
            session.javaClass.getDeclaredField("texture").apply { isAccessible = true }.set(session, texture)
            val canvas = MeshCanvas(texture)
            val from = RectF(20f, 100f, 280f, 600f)
            val destination = RectF(100f, 10f, 200f, 50f)
            val shell = RectF(80f, 0f, 220f, 80f)
            IslandAbsorptionMesh.fill(vertices, from, destination, .4f, rect)
            val visible = vertices.copyOf()
            IslandAbsorptionMesh.fill(vertices, from, destination, 1f, rect, occlusion = shell)
            assertArrayEquals("Fully occluded rows need no new deformation", visible, vertices, 0f)
            paint.alpha = (255f * IslandAbsorptionMesh.mergeOpacity(rect, shell)).toInt()
            assertEquals(0, paint.alpha)
            (session as android.graphics.drawable.Drawable).draw(canvas)
            assertEquals("An occluded page must not submit an invisible mesh", 0, canvas.meshCalls)
            assertEquals("No invisible clip work", 0, canvas.clipCalls)
            assertFalse("Keep the bounded texture until its owner releases it", texture.isRecycled)

            IslandAbsorptionMesh.fill(vertices, from, destination, .4f, rect)
            paint.alpha = (255f * IslandAbsorptionMesh.mergeOpacity(rect, shell)).toInt()
            mesh.javaClass.getDeclaredField("clipReady").apply { isAccessible = true }.setBoolean(mesh, false)
            session.draw(canvas)
            assertEquals("A visible tail must still be drawn", 1, canvas.meshCalls)
            paint.alpha = 0
            session.draw(canvas)
            assertEquals("Further hidden frames stay idle", 1, canvas.meshCalls)
        }
    }

    private class MeshCanvas(bitmap: Bitmap) : Canvas(bitmap) {
        var meshCalls = 0
        var clipCalls = 0
        override fun isHardwareAccelerated() = true
        override fun clipPath(path: Path): Boolean { clipCalls++; return super.clipPath(path) }
        override fun drawBitmapMesh(bitmap: Bitmap, meshWidth: Int, meshHeight: Int, verts: FloatArray,
            vertOffset: Int, colors: IntArray?, colorOffset: Int, paint: Paint?) { meshCalls++ }
    }

    @Test fun occlusionNeverCullsAVisibleOffCentreMesh() {
        val destination = RectF(300f, 10f, 500f, 50f)
        val shell = RectF(240f, 0f, 560f, 80f)
        var skipped = 0
        var visible = 0
        for (left in listOf(-400f, 0f, 600f)) for (step in 960..1000) {
            val source = RectF(left, 100f, left + 800f, 1800f)
            val reference = FloatArray(IslandAbsorptionMesh.FLOATS)
            val expected = RectF()
            IslandAbsorptionMesh.fill(reference, source, destination, step / 1000f, expected)
            val actual = FloatArray(IslandAbsorptionMesh.FLOATS) { -5000f }
            val bounds = RectF()
            IslandAbsorptionMesh.fill(actual, source, destination, step / 1000f, bounds, occlusion = shell)
            if (actual[0] == -5000f) {
                skipped++
                assertEquals("Only the already invisible merge can skip rows", 0f,
                    IslandAbsorptionMesh.mergeOpacity(expected, shell), 0f)
                assertTrue("The skipped envelope must include every row", bounds.contains(expected))
            } else {
                visible++
                assertArrayEquals(reference, actual, 0f)
                assertEquals(expected, bounds)
            }
        }
        assertTrue("Exercise genuinely occluded rows", skipped > 0)
        assertTrue("Exercise the still-visible trailing body", visible > 0)
    }

    @Test fun defaultReturnDoesNotAllocateAbsorptionDrawingResources() {
        withSession(false) { session ->
            assertTrue("Default return must only own its progress/lifetime state: ${drawingResources(session)}",
                drawingResources(session).isEmpty())
        }
    }

    @Test fun absorptionRetainsExactlyOneBoundedMeshAllocation() {
        withSession(true) { session ->
            val first = drawingResources(session)
            assertEquals(1, first.values.count { it is FloatArray && it.size == IslandAbsorptionMesh.FLOATS })
            assertEquals(1, first.values.count { it is IntArray && it.size == (IslandAbsorptionMesh.ROWS + 1) * 2 })
            val again = drawingResources(session)
            for ((key, value) in first) assertSame(value, again[key])
        }
    }

    @Test fun absorptionHasTheSameLocalContactWithoutAnExtraClock() =
        exerciseNativeContact()

    @Test fun powerSaveMotionNativeReturnsKeepTheUnmodifiedCapsule() =
        exerciseNativeContact(powerSaveMotion = true)

    @Test fun liquidContactPreservesTheExistingCameraMaskReservation() {
        exerciseNativeContact(tightCameraMask = true)
    }

    @Test fun repeatedDrawsReuseTheClipUntilGeometryOrPresentationChanges() {
        withSession(true) { session ->
            val mesh = getField<Any>(session, "drawing")
            val rect = getField<RectF>(mesh, "rect")
            val vertices = getField<FloatArray>(mesh, "vertices")
            val destination = RectF(100f, 10f, 200f, 50f)
            IslandAbsorptionMesh.fill(vertices, RectF(20f, 100f, 280f, 600f), destination, .3f, rect)
            val clip = mesh.javaClass.getDeclaredMethod("clip", Boolean::class.javaPrimitiveType,
                Float::class.javaPrimitiveType).apply { isAccessible = true }
            val path = clip.invoke(mesh, false, 8f) as Path
            val first = path.generationId
            assertSame(path, clip.invoke(mesh, false, 8f))
            assertEquals("An extra draw must not rebuild an unchanged path", first, path.generationId)
            clip.invoke(mesh, true, 8f)
            assertNotEquals("The accepted hide changes the clip exactly once", first, path.generationId)
            val accepted = path.generationId
            clip.invoke(mesh, true, 8f)
            assertEquals(accepted, path.generationId)
            mesh.javaClass.getDeclaredField("clipReady").apply { isAccessible = true }.setBoolean(mesh, false)
            clip.invoke(mesh, true, 8f)
            assertNotEquals("A native geometry update invalidates the cached path", accepted, path.generationId)
        }
    }

    @Test fun preparedContactColorsKeepTheBodyOpaqueAndStandaloneCallsOverwriteOldInputs() {
        val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
        val destination = RectF(100f, 10f, 200f, 50f)
        IslandAbsorptionMesh.fill(vertices, RectF(20f, 100f, 280f, 600f), destination, .4f)
        val colors = IntArray((IslandAbsorptionMesh.ROWS + 1) * 2)
        colors[colors.lastIndex] = -1
        IslandAbsorptionMesh.contactColors(colors, vertices, destination, .4f)
        assertTrue(colors.drop(16).all { it == -1 })
        for (step in 0..100) {
            IslandAbsorptionMesh.contactColors(colors, vertices, destination, step / 100f, opaqueBodyReady = true)
            assertTrue(colors.drop(16).all { it == -1 })
            assertTrue(colors.take(16).all { Color.alpha(it) in 239..255 })
        }
    }

    @Test fun warpApproximationStaysWithinThreeQuartersOfAPixelEvenOnTheMaximumAdmittedScreen() {
        val source = RectF(60f, 1000f, 9000f, 20000f)
        val target = RectF(3500f, 25f, 5500f, 225f)
        val vertices = FloatArray(IslandAbsorptionMesh.FLOATS)
        for (step in 0..1000) {
            val t = step / 1000f
            IslandAbsorptionMesh.fill(vertices, source, target, t)
            val top = vertices[1]
            val bottom = vertices.last()
            val bend = 1.0 + 2.6 * t * (1.0 - t)
            var previous = -Float.MAX_VALUE
            for (row in 0..IslandAbsorptionMesh.ROWS) {
                val expected = top + (bottom - top) * (row.toDouble() / IslandAbsorptionMesh.ROWS).pow(bend)
                val actual = vertices[row * 4 + 1]
                assertEquals("row=$row progress=$t", expected, actual.toDouble(), .75)
                assertTrue("The page must not fold over itself", actual > previous)
                previous = actual
            }
        }
    }

    private fun exerciseNativeContact(powerSaveMotion: Boolean = false, tightCameraMask: Boolean = false) {
        for (hz in listOf(60, 90, 120)) {
            val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
            val memory = SharedMemory.create("return-fusion", 4)
            val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
            val shell = IslandActivityView(host)
            host.setContentView(shell, FrameLayout.LayoutParams(240, 40))
            ShadowChoreographer.setPaused(true)
            try {
                shell.bind("Music", null, ColorDrawable(Color.RED), true, false, false, 0, false)
                shell.geometry(240, 40, 40, 260)
                shell.cameraAnchor(20f, if (tightCameraMask) 40f else 16f)
                shell.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
                shell.layout(0, 0, 240, 40)
                shell.prepareEntry()
                shell.transition(240, 40, 240, 40, entering = true)
                getField<ValueAnimator>(shell, "motion").end()
                shell.powerSaveMotion = powerSaveMotion
                shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
                shell.prepareReturnFromCurrent()
                shell.transition(240, 40, 240, 40, entering = false)
                val clock = getField<ValueAnimator?>(shell, "motion")
                if (!powerSaveMotion) {
                    assertNotNull(clock)
                    for (step in 0..(hz / 5)) {
                        val time = step * 1000L / hz
                        writer.putInt(0, (time / 400f * 1_000_000).toInt().coerceAtMost(500_000))
                        clock!!.currentPlayTime = time
                    }
                    writer.putInt(0, 500_000)
                    clock!!.currentPlayTime = 200L
                    assertSame("Contact must use the existing finite clock", clock, getField(shell, "motion"))
                }
                if (tightCameraMask) {
                    // A geometry refresh may reach the exact mask reservation
                    // during the return, without completing or replacing its clock.
                    shell.scaleY = 1f
                    shell.geometry(240, 40, 40, 260)
                }
                val shape = getField<IslandShape>(shell, "shape")
                assertFalse("Exercise a laid-out native outline", shape.path.isEmpty)
                val beforeOutline = Region().apply { setPath(shape.path, Region(0, 0, 240, 40)) }
                shape.getOutline(shell, android.graphics.Outline())
                val afterOutline = Region().apply { setPath(shape.path, Region(0, 0, 240, 40)) }
                beforeOutline.op(afterOutline, Region.Op.XOR)
                assertTrue("Native outline queries must not erase the current contact", beforeOutline.isEmpty)
                val normal = IslandShape().apply { resize(240, 40, shell.scaleX, shell.scaleY) }
                val clip = Region(0, 0, 240, 40)
                val difference = Region().apply { setPath(shape.path, clip) }
                difference.op(Region().apply { setPath(normal.path, clip) }, Region.Op.XOR)
                assertEquals("powerSaveMotion=$powerSaveMotion cameraReserved=$tightCameraMask hz=$hz",
                    powerSaveMotion || tightCameraMask, difference.isEmpty)
                val bounds = RectF().also { shape.path.computeBounds(it, true) }
                assertTrue("Contact stays inside the existing window: $bounds", bounds.left >= -.001f &&
                    bounds.top >= -.001f && bounds.right <= 240.001f && bounds.bottom <= 40.001f)
                if (hz == 60) exportShape(shape.path, "absorption-${if (tightCameraMask) "camera-reserved" else if (powerSaveMotion) "powerSaveMotion" else "contact"}")

                shell.cancelMediaReturn()
                val restored = Region().apply { setPath(shape.path, clip) }
                restored.op(Region().apply { setPath(normal.path, clip) }, Region.Op.XOR)
                assertTrue("Cancellation must not leave a liquid dent", restored.isEmpty)
            } finally {
                getField<ValueAnimator?>(shell, "motion")?.cancel()
                shell.cancelMediaReturn()
                SharedMemory.unmap(writer); memory.close(); host.finish()
                ShadowChoreographer.setPaused(false)
            }
        }
    }

    private fun drawingResources(owner: Any): Map<String, Any> {
        val result = linkedMapOf<String, Any>()
        fun collect(target: Any, prefix: String) {
            for (field in target.javaClass.declaredFields) {
                if (field.name == "this$0") continue
                field.isAccessible = true
                val value = field.get(target) ?: continue
                if (value is Paint || value is Path || value is FloatArray || value is IntArray && value.size > 2)
                    result[prefix + field.name] = value
                else if (value.javaClass.simpleName == "Mesh") collect(value, prefix + field.name + ".")
            }
        }
        collect(owner, "")
        return result
    }

    private fun withSession(mesh: Boolean, block: (Any) -> Unit) {
        val ctor = LauncherWindowAbsorption::class.java.declaredConstructors.single { it.parameterCount == 2 }
            .apply { isAccessible = true }
        // Only inspect a session's allocations; none of the ROM access methods are invoked.
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
            .apply { isAccessible = true }.get(null)
        val inertAccess = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, LauncherWindowAbsorption.Access::class.java)
        val owner = ctor.newInstance(inertAccess, Handler(Looper.getMainLooper())) as LauncherWindowAbsorption
        val type = LauncherWindowAbsorption::class.java.declaredClasses.single { it.simpleName == "Session" }
        val sessionCtor = type.declaredConstructors.single().apply { isAccessible = true }
        val root = View(RuntimeEnvironment.getApplication())
        val frame = IslandMusicReturnSignal.Frame("chosen.music", UserHandle.getUserHandleForUid(1000),
            300f, 20f, 700f, 60f, 1080, 2400)
        val session = sessionCtor.newInstance(owner, Any(), Any(), root, null,
            frame.landingRectangle(), frame.rectangle(), mesh, false)
        val slot = LauncherWindowAbsorption::class.java.getDeclaredField("session").apply { isAccessible = true }
        slot.set(owner, session)
        try { block(session) } finally { owner.cancel() }
    }

    private fun exportShape(path: Path, name: String) {
        java.lang.System.getenv("CUSTOMIUIZER_RETURN_FUSION_PREVIEW_DIR")?.let { directory ->
            val image = Bitmap.createBitmap(720, 120, Bitmap.Config.ARGB_8888)
            try {
                Canvas(image).apply {
                    drawColor(Color.WHITE)
                    scale(3f, 3f)
                    drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
                }
                File(directory).mkdirs()
                File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { image.recycle() }
        }
    }
}
