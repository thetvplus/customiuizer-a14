package tv.withaibuild.customiuizer.mods.duostatusbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Matrix
import androidx.core.graphics.PathParser
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.imageio.ImageIO

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DuoNativeSymbolsTest {
    @Test fun originalNativeVectorLayersAndTransformsSurviveDirectCodePreparation() {
        val fixtures=JSONArray(javaClass.getResourceAsStream("/duo-native/vector-masters.json")!!.bufferedReader().use { it.readText() })
        val glyphs=mapOf("airplane" to DuoIosSymbols.airplane,"headphones" to DuoIosSymbols.headphones,
            "airpods" to DuoIosSymbols.airpods,"wifi_warning" to DuoIosSymbols.wifi_warning,"bolt" to DuoIosSymbols.bolt)
        for(i in 0 until fixtures.length()) {
            val source=fixtures.getJSONObject(i);val name=source.getString("name");val glyph=glyphs.getValue(name)
            val w=kotlin.math.ceil(glyph.width*32).toInt();val h=kotlin.math.ceil(glyph.height*32).toInt()
            val expected=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            val actual=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            try {
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=-1 }
                val reference=Canvas(expected);reference.scale(32f,32f)
                val layers=source.getJSONArray("layers")
                for(j in 0 until layers.length()) {
                    val layer=layers.getJSONObject(j);val t=layer.getJSONArray("transform")
                    val matrix=Matrix().apply { setValues(floatArrayOf(t.getDouble(0).toFloat(),t.getDouble(2).toFloat(),t.getDouble(4).toFloat(),
                        t.getDouble(1).toFloat(),t.getDouble(3).toFloat(),t.getDouble(5).toFloat(),0f,0f,1f)) }
                    val save=reference.save();reference.concat(matrix)
                    paint.alpha=(255*layer.getDouble("opacity")).toInt()
                    reference.drawPath(PathParser.createPathFromPathData(layer.getString("d")),paint)
                    reference.restoreToCount(save)
                }
                paint.alpha=255
                Canvas(actual).apply { scale(32f,32f);for(path in glyph.paths) drawPath(path,paint) }
                var union=0;var intersection=0
                for(y in 0 until h) for(x in 0 until w) {
                    val a=expected.getPixel(x,y) ushr 24>=128;val b=actual.getPixel(x,y) ushr 24>=128
                    if(a || b) union++;if(a && b) intersection++
                }
                val overlap=intersection.toDouble()/union
                if(overlap<=.998) {
                    val folder=java.io.File("build/reports/duo-native").apply { mkdirs() }
                    java.io.FileOutputStream(java.io.File(folder,"$name-expected.png")).use { expected.compress(Bitmap.CompressFormat.PNG,100,it) }
                    java.io.FileOutputStream(java.io.File(folder,"$name-actual.png")).use { actual.compress(Bitmap.CompressFormat.PNG,100,it) }
                }
                assertTrue("$name native curves/opacity/transform changed: $overlap",overlap>.998)
            } finally { expected.recycle();actual.recycle() }
        }
    }
    @Test fun vectorContoursRetainNativeMaskTopologyAndOpticalCentre() {
        val glyphs=listOf("offline" to DuoIosSymbols.offline)
        for((name,glyph) in glyphs) {
            val native=ImageIO.read(javaClass.getResourceAsStream("/duo-native/duo_ios_$name.png"))
            val result=Bitmap.createBitmap(native.width,native.height,Bitmap.Config.ARGB_8888)
            try {
                Canvas(result).drawPath(glyph.paths.single(),Paint(Paint.ANTI_ALIAS_FLAG).apply { color=-1 })
                var union=0; var intersection=0; var originalX=0.0; var actualX=0.0
                var originalY=0.0; var actualY=0.0; var originalCount=0; var actualCount=0
                for(y in 0 until native.height) for(x in 0 until native.width) {
                    val expected=native.getRGB(x,y) ushr 24>=128
                    val actual=result.getPixel(x,y) ushr 24>=128
                    if(expected || actual) union++
                    if(expected && actual) intersection++
                    if(expected) { originalCount++; originalX+=x; originalY+=y }
                    if(actual) { actualCount++; actualX+=x; actualY+=y }
                }
                assertTrue("$name contour changed native ink",intersection.toDouble()/union>.965)
                assertEquals("$name horizontal centre",originalX/originalCount,actualX/actualCount,.25)
                assertEquals("$name vertical centre",originalY/originalCount,actualY/actualCount,.25)
            } finally { result.recycle() }
        }
    }
}
