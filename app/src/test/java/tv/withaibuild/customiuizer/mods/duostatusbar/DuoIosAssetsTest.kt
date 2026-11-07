package tv.withaibuild.customiuizer.mods.duostatusbar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DuoIosAssetsTest {
    private val context get()=RuntimeEnvironment.getApplication()
    @Test fun standaloneWifiKeepsItsVisibleCentreOnTheBatteryCentre() {
        for (bold in listOf(false,true)) {
            val all=android.graphics.Path()
            for (path in DuoIosWifi.paths(bold)) all.addPath(path)
            val ink=RectF(); all.computeBounds(ink,true)
            assertEquals("Standalone Wi-Fi must share the ring's vertical centre",60f,ink.centerY(),.05f)
            assertEquals("Signal strength cannot move its horizontal anchor",60f,ink.centerX(),.05f)
        }
    }
    @Test fun audioHintsAndOfflineExtensionKeepTheirOwnOpticalCentres() {
        val config=DuoConfig(false,24)
        val wifi=DuoNetworkState.WIFI_READY or DuoNetworkState.WIFI_CONNECTED or (4 shl 4)
        val cases=listOf(wifi to DuoAudioState.WIRED,wifi to DuoAudioState.BLUETOOTH,
            DuoNetworkState.MOBILE_READY to 0)
        val image=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888)
        try {
            for((network,audio) in cases) {
                val key=DuoCenter.key(network,audio,config)
                val glyphs=DuoIosGlyphs(config); glyphs.prepare(key,-1)
                image.eraseColor(0); glyphs.draw(Canvas(image),key,-1,network)
                var total=0.0; var sx=0.0; var sy=0.0
                for(y in 0 until 120) for(x in 0 until 120) {
                    val a=image.getPixel(x,y) ushr 24
                    total+=a; sx+=(x+.5)*a; sy+=(y+.5)*a
                }
                assertTrue(total>0)
                assertEquals(60.0,sx/total,.35)
                assertEquals(60.0,sy/total,.35)
            }
        } finally { image.recycle() }
    }
    @Test fun wifiKeepsNativeSixteenPointSizeWithinTheExistingFrame() {
        val image=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888)
        try {
            val config=DuoConfig(false,24)
            val network=DuoNetworkState.WIFI_CONNECTED or (4 shl 4)
            val key=DuoCenter.key(network,0,config)
            val glyphs=DuoIosGlyphs(config); glyphs.prepare(key,-1)
            glyphs.draw(Canvas(image),key,-1,network)
            val ink=android.graphics.Rect(120,120,0,0)
            for(y in 0 until 120) for(x in 0 until 120) if(image.getPixel(x,y) ushr 24>=128) ink.union(x,y,x+1,y+1)
            assertEquals(46.315575f,ink.width().toFloat(),1f)
            assertEquals(33.968563f,ink.height().toFloat(),1f)
            assertEquals(60f,ink.exactCenterX(),1f)
            assertEquals(60f,ink.exactCenterY(),.5f)
        } finally { image.recycle() }
    }
    @Test fun nativeBoldMediumLowestWifiPartRetainsItsRecoveredShape() {
        val bounds=RectF()
        DuoIosWifi.paths(false)[0].computeBounds(bounds,true)
        assertEquals(53.09757f,bounds.left,.002f)
        assertEquals(66.90125f,bounds.top,.002f)
        assertEquals(66.90008f,bounds.right,.002f)
        assertEquals(76.85395f,bounds.bottom,.002f)
    }
    @Test fun airplaneUsesTheSmallerNativeWifiBranchAndStacksBelowItsMeasuredBottom() {
        val config=DuoConfig(false,24)
        val glyphs=DuoIosGlyphs(config)
        val image=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888)
        val base=DuoNetworkState.WIFI_CONNECTED or (4 shl 4)
        val key=DuoCenter.key(base,0,config)
        fun ink():android.graphics.Rect {
            val bounds=android.graphics.Rect(120,120,0,0)
            for(y in 0 until 120) for(x in 0 until 120)
                if(image.getPixel(x,y) ushr 24>=128) bounds.union(x,y,x+1,y+1)
            return bounds
        }
        try {
            glyphs.prepare(key,-1,base);glyphs.draw(Canvas(image),key,-1,base)
            val ordinary=ink()
            val plane=base or DuoNetworkState.AIRPLANE
            image.eraseColor(0);glyphs.prepare(key,-1,plane);glyphs.draw(Canvas(image),key,-1,plane)
            val small=ink()
            assertEquals(15f/16f,small.width().toFloat()/ordinary.width(),.025f)
            assertEquals(13f*120/47,small.top.toFloat(),1f)
            assertEquals(60f,ordinary.exactCenterY(),.5f)
            image.eraseColor(0);glyphs.preparePlane(-1);glyphs.drawPlane(Canvas(image),-1,true,key,plane)
            val secondary=ink()
            assertEquals(120f/47,secondary.top-small.bottom.toFloat(),1f)
            assertTrue(secondary.bottom<100)
            assertSame(DuoIosWifi.paths(false,true),DuoIosWifi.paths(false,true))
        } finally { image.recycle() }
    }
    @Test fun bezierRingLeavesTheBottomGapAndCrossesTheTopOnceAtHalfCharge() {
        val glyphs=DuoIosGlyphs(DuoConfig(false,24))
        val image=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=-1 }
        try {
            glyphs.prepareRing(1);Canvas(image).drawPath(glyphs.progress,paint)
            assertEquals(0,image.getPixel(60,12) ushr 24)
            glyphs.prepareRing(50);image.eraseColor(0);Canvas(image).drawPath(glyphs.progress,paint)
            assertTrue(image.getPixel(60,12) ushr 24>128)
            assertEquals(0,image.getPixel(60,108) ushr 24)
            glyphs.prepareRing(100)
            val difference=android.graphics.Path(glyphs.track)
            assertTrue(difference.op(glyphs.progress,android.graphics.Path.Op.XOR))
            assertTrue(difference.isEmpty)
        } finally { image.recycle() }
    }
    @Test fun insideRingCellularBarsRetainEveryAndroidSignalStep() {
        val state=DuoNetworkState().apply { subscriptions(10,-1,true) }
        val config=DuoConfig(false,24)
        val glyphs=DuoIosGlyphs(config)
        val bitmap=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888)
        try {
            var previous=-1L
            for(level in 0..5) {
                state.mobile(0,10,level,true,5)
                val key=DuoCenter.key(state.snapshot,0,config)
                glyphs.prepare(key,-1); bitmap.eraseColor(0)
                glyphs.draw(Canvas(bitmap),key,-1,state.snapshot)
                var alpha=0L
                for(y in 0 until 120) for(x in 0 until 120) alpha+=bitmap.getPixel(x,y) ushr 24
                assertTrue("Each native signal step must remain distinct",alpha>previous)
                previous=alpha
            }
        } finally { bitmap.recycle() }
    }
    @Test fun secondaryPlaneUsesFixedTopOrTheMeasuredPrimaryBottomIncludingAudioHints() {
        val config=DuoConfig(false,24,headphoneScale=115)
        val glyphs=DuoIosGlyphs(config)
        val bitmap=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888)
        val wifi=DuoCenter.key(DuoNetworkState.WIFI_CONNECTED,0,config)
        val audio=DuoCenter.key(DuoNetworkState.WIFI_CONNECTED,DuoAudioState.WIRED,config)
        glyphs.prepare(wifi,-1); glyphs.prepare(audio,-1); glyphs.preparePlane(-1)
        fun top(stacked: Boolean,key: Int): Int {
            bitmap.eraseColor(0); glyphs.drawPlane(Canvas(bitmap),-1,stacked,key)
            return (0 until 120).first { y -> (0 until 120).any { x -> bitmap.getPixel(x,y) ushr 24>128 } }
        }
        try {
            val fixed=top(false,wifi)
            assertTrue(kotlin.math.abs((30f*120f/47f).toInt()-fixed)<=1)
            val stacked=top(true,wifi)
            assertEquals((DuoIosWifi.layoutBottom(false)+120f/47f).toInt().toFloat(),stacked.toFloat(),1f)
            assertTrue("A larger Android audio hint must also move its secondary item",top(true,audio)>stacked)
        } finally { bitmap.recycle() }
    }
    @Test fun percentAndBoltShareTheNativeRowAndCutoutFollowsActualGlyphs() {
        val config=DuoConfig(true,24)
        val glyphs=DuoIosGlyphs(config)
        val text=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface=DuoIosFont.get(context); textSize=11f*120/47; textAlign=Paint.Align.CENTER
        }
        assertSame(text.typeface,DuoIosFont.get(context))
        glyphs.prepareMarker("50",false,-1,text)
        val single=RectF(); glyphs.cutout.computeBounds(single,true)
        assertEquals(60f,glyphs.percentX,0f)
        assertTrue(single.width()>text.measureText("50"))
        glyphs.prepareMarker("50",true,-1,text)
        val combined=RectF(); glyphs.cutout.computeBounds(combined,true)
        assertTrue(glyphs.percentX<60f)
        assertTrue(combined.right>single.right)
        glyphs.prepareMarker("100",false,-1,text)
        val hundred=RectF(); glyphs.cutout.computeBounds(hundred,true)
        assertTrue(hundred.width()>single.width())
        val bitmap=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap); glyphs.prepareMarker("100",true,-1,text)
            glyphs.drawBolt(canvas,-1)
            assertTrue((0 until 120).any { y -> (0 until 120).any { x -> bitmap.getPixel(x,y) ushr 24>0 } })
        } finally { bitmap.recycle() }
    }
    @Test fun wifiMastersAreFixedAndEveryUserHeadphoneScaleIsRetained() {
        assertSame(DuoIosWifi.paths(false),DuoIosWifi.paths(false))
        for(bold in listOf(false,true)) for(path in DuoIosWifi.paths(bold)) {
            val box=RectF(); path.computeBounds(box,true)
            assertTrue(box.left>35f && box.right<85f)
            assertTrue(box.top>42f && box.bottom<78.5f)
        }
        val audio=DuoAudioState().apply { wired(true,1,true) }
        var last=0
        for(scale in intArrayOf(85,100,115)) {
            val config=DuoConfig(false,24,headphoneScale=scale)
            val key=DuoCenter.key(0,audio.snapshot,config)
            val glyphs=DuoIosGlyphs(config); glyphs.prepare(key,-1)
            val bitmap=Bitmap.createBitmap(120,120,Bitmap.Config.ARGB_8888)
            try {
                glyphs.draw(Canvas(bitmap),key,-1)
                var pixels=0
                for(y in 0 until 120) for(x in 0 until 120) if(bitmap.getPixel(x,y) ushr 24>0) pixels++
                assertTrue(pixels>last); last=pixels
            } finally { bitmap.recycle() }
        }
    }
}
