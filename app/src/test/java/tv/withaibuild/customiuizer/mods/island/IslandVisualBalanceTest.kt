package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.SharedMemory
import android.view.View
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers.getField
import java.io.File
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Native Skia review evidence; deliberately not a ROM, device-video or frame-time claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandVisualBalanceTest {
    @Test fun theReceivingBodyHasItsOwnReadableTempoAcrossNativePageSpeedsAndRefreshRates() {
        val peaks = mutableListOf<Long>()
        val review = JSONArray()
        for (hz in listOf(60, 90, 120)) for (pageMs in listOf(650, 420, 900)) for (existing in listOf(false, true)) {
            val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
            val memory = SharedMemory.create("island-visual-balance", 4)
            val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
            val shell = IslandActivityView(host).apply { absorption = true; entranceStyle = IslandEntranceStyle.CENTER }
            host.setContentView(shell, FrameLayout.LayoutParams(160, 40))
            val samples = JSONArray()
            val export = hz == 60 && pageMs == 650
            val strip = if (export && directory != null) Bitmap.createBitmap(2560, 120, Bitmap.Config.ARGB_8888) else null
            val canvas = strip?.let(::Canvas)?.apply { drawColor(0xffdce3eb.toInt()) }
            ShadowChoreographer.setPaused(true)
            try {
                shell.bind("Summer evening", null, ColorDrawable(Color.CYAN), true, true, true, 0, false)
                shell.geometry(160, 40, 40, IslandSizing.elasticMaximum(160, 120, 24, 376, 1f))
                shell.cameraAnchor(20f, 16f); layout(shell, 160)
                if (existing) { shell.prepareEntry(); shell.transition(160, 40, 160, 40, true); clock(shell)!!.end() }
                shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
                if (existing) shell.prepareReturnFromCurrent() else shell.prepareEntry(mediaReturn = true)
                shell.transition(160, 40, 160, 40, !existing)
                val animation = clock(shell)!!
                var peak = 0f; var peakAt = 0L; var column = 0
                val captures = longArrayOf(0, 80, 160, 260, 380, 500, 650, 800)
                for (i in 0..(hz * 11 / 10)) {
                    val time = i * 1000L / hz
                    writer.putInt(0, (time.toFloat() / pageMs * 1_000_000).roundToInt().coerceAtMost(1_000_000))
                    if (clock(shell) === animation) animation.currentPlayTime = time
                    val width = 160 * shell.scaleX
                    if (width > peak) { peak = width; peakAt = time }
                    assertTrue("Actual safe width is authoritative", width in 39.999f..179.201f)
                    assertEquals(0f, shell.translationX, 0f)
                    assertEquals(0f, shell.translationY, 0f)
                    samples.put(JSONObject().put("timeMs", time).put("width", width).put("height", 40 * shell.scaleY))
                    if (canvas != null && column < captures.size && time >= captures[column]) {
                        paint.color = Color.DKGRAY; paint.textSize = 16f
                        canvas.drawText("${if (existing) "Reopen" else "First"} ${time}ms", column * 320f + 12, 22f, paint)
                        draw(canvas, shell, column * 320f + 160, 68f, 1.5f)
                        column++
                    }
                }
                if (export && directory != null) {
                    val name = if (existing) "absorption-reopen" else "absorption-first"
                    File(directory, "$name.json").writeText(samples.toString(2))
                    strip?.let { image -> File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                }
                assertTrue("A full receiving form needs a visible, bounded crest: $peak", peak >= 170.4f)
                assertTrue("A readable receiving gesture must not finish its crest in a blink: $peakAt", peakAt in 280L..620L)
                peaks += peakAt
                review.put(JSONObject().put("hz", hz).put("nativePageMs", pageMs).put("existing", existing)
                    .put("peakWidth", peak).put("peakAtMs", peakAt))
                assertNull("Both clocks must settle and release their channel", clock(shell))
                assertNull(getField<Any?>(shell, "returnProgress"))
                assertEquals(1f, shell.scaleX, 0f); assertEquals(1f, shell.scaleY, 0f)
            } finally {
                clock(shell)?.cancel(); shell.cancelMediaReturn()
                SharedMemory.unmap(writer); memory.close(); strip?.recycle(); host.finish()
                ShadowChoreographer.setPaused(false)
            }
        }
        // Page speed must not squeeze the island's mass into the page's first few frames.
        assertTrue("Consistent receiving tempo across 420/650/900ms native returns", peaks.max() - peaks.min() <= 90L)
        directory?.let { File(it, "balance-peaks.json").writeText(review.toString(2)) }
    }

    @Test fun nativeContentReviewIncludesLightDarkWallpapersRtlAndLargeType() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().get()
        val cases = listOf(
            Triple("Music", "Summer evening", null), Triple("Loading", "Loading…", null),
            Triple("Call", "01:28", "Connected"), Triple("Alarm", "07:30", "Alarm"),
            Triple("Timer", "03:12", "Timer"), Triple("Stopwatch", "12:34", "Stopwatch"),
            Triple("Recording", "00:28", "Recording"), Triple("Privacy", "Camera", null),
            Triple("Message", "Alice", "Photos saved successfully"), Triple("Chinese", "新消息", "照片已经保存至相册"),
            Triple("RTL", "رسالة جديدة", "تم حفظ الصور بنجاح"), Triple("Large type", "New message", "Photos saved"),
            Triple("Idle", "", null), Triple("Landscape", "Alice", "Photos saved successfully"))
        val image = if (directory != null) Bitmap.createBitmap(1000, cases.size * 96, Bitmap.Config.ARGB_8888) else null
        val canvas = image?.let(::Canvas)
        try {
            for ((row, item) in cases.withIndex()) for (dark in listOf(false, true)) {
                val config = Configuration(host.resources.configuration).apply { fontScale = if (item.first == "Large type") 1.6f else 1f }
                val shell = IslandActivityView(host.createConfigurationContext(config)).apply { simplified = true }
                host.setContentView(shell, FrameLayout.LayoutParams(280, 40))
                shell.layoutDirection = if (item.first == "RTL") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                val media = item.first in listOf("Music", "Loading")
                val expanded = item.first in listOf("Message", "Chinese", "RTL", "Large type", "Landscape")
                val width = when (item.first) { "Idle" -> 100; "Landscape" -> 240; else -> if (expanded) 280 else 160 }
                shell.idle = item.first == "Idle"
                shell.privacy = if (item.first == "Privacy") 2 else 0
                shell.contentHeight(40); shell.geometry(width, 40, if (item.first == "Landscape") 0 else 40, width)
                shell.bind(item.second, item.third, if (shell.idle) null else ColorDrawable(Color.CYAN), true, media, media,
                    0, false, status = !expanded && !media && !shell.idle, expanded = expanded,
                    loading = item.first == "Loading", recording = item.first == "Recording")
                layout(shell, width); shell.cameraAnchor(20f, 16f); shell.prepareEntry()
                if (canvas != null) {
                    val x = if (dark) 500f else 0f
                    paint.color = if (dark) 0xff202125.toInt() else 0xffe4e8ed.toInt()
                    canvas.drawRect(x, row * 96f, x + 500, (row + 1) * 96f, paint)
                    paint.color = if (dark) Color.LTGRAY else Color.DKGRAY; paint.textSize = 14f
                    canvas.drawText(item.first, x + 12, row * 96f + 20, paint)
                    draw(canvas, shell, x + 250, row * 96f + 55, 1.4f)
                }
                if (!shell.idle) assertTrue(shell.contentDescription.isNotEmpty())
                if (item.first == "Chinese") {
                    val title = getField<android.widget.Chronometer>(shell, "title")
                    assertEquals(2, title.lineCount)
                    for (line in 0 until title.lineCount) assertTrue("A short CJK caption must not leave a one-character last line",
                        title.layout.getLineEnd(line) - title.layout.getLineStart(line) >= 3)
                }
                host.setContentView(FrameLayout(host))
            }
            image?.let { bitmap -> File(directory, "content-matrix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        } finally { image?.recycle(); host.finish() }
    }

    private val directory = System.getenv("ISLAND_PREVIEW_DIR")?.let(::File)?.also { it.mkdirs() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun clock(shell: IslandActivityView) = getField<ValueAnimator?>(shell, "motion")
    private fun layout(shell: IslandActivityView, width: Int) {
        shell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
        shell.layout(0, 0, width, 40)
    }
    private fun draw(canvas: Canvas, shell: IslandActivityView, x: Float, y: Float, scale: Float) {
        val save = canvas.save()
        canvas.translate(x - shell.width * scale * .5f, y - 20 * scale); canvas.scale(scale, scale)
        canvas.concat(shell.matrix); canvas.clipPath(getField<IslandShape>(shell, "shape").path)
        canvas.saveLayerAlpha(null, (shell.alpha * 255).roundToInt().coerceIn(0, 255))
        shell.draw(canvas); canvas.restoreToCount(save)
    }
}
