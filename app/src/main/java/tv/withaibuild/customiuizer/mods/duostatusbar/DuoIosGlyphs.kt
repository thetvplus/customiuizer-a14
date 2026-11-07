package tv.withaibuild.customiuizer.mods.duostatusbar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Path
import android.graphics.PathMeasure

internal class DuoIosGlyphs(private val config: DuoConfig) {
    private class Symbol(val glyph: DuoIosSymbols.Glyph,val rect: RectF) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        var tint=0
    }
    private val symbols=arrayOfNulls<Symbol>(7)
    private var cells: Array<Path>?=null
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG)
    val cutout=Path()
    val track=Path()
    val progress=Path()
    private val arc=Path()
    private val ringMeasure by lazy(LazyThreadSafetyMode.NONE) {
        val r=60f-4.66f*120f/47f
        val c=r*.5522847498f
        val circle=Path().apply {
            moveTo(60f+r,60f)
            cubicTo(60f+r,60f+c,60f+c,60f+r,60f,60f+r)
            cubicTo(60f-c,60f+r,60f-r,60f+c,60f-r,60f)
            cubicTo(60f-r,60f-c,60f-c,60f-r,60f,60f-r)
            cubicTo(60f+c,60f-r,60f+r,60f-c,60f+r,60f)
            close()
            // Rotation preserves curve length. Each native peer rotates +pi/2
            // once; Android PathMeasure supplies the bounded trim adapter.
            transform(android.graphics.Matrix().apply { setRotate(90f,60f,60f) })
        }
        PathMeasure(circle,false)
    }
    private val ringStroke=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND
        strokeWidth=3.33f*120f/47f*if(config.bold) 1.176f else 1f
        strokeMiter=10f
    }
    private var ringLevel=-1
    private var ringDirty=true
    private val marker=Path()
    private val bounds=RectF()
    private val textInk=Rect()
    private val transform=android.graphics.Matrix()
    private val mask=Paint().apply {
        style=Paint.Style.FILL_AND_STROKE; strokeWidth=4.5f*120f/47f
        strokeJoin=Paint.Join.ROUND; strokeCap=Paint.Cap.ROUND
    }
    var percentX=60f
        private set
    var percentBaseline=0f
        private set
    private var markerLabel: String?=null
    private var markerBolt=false
    private fun slot(key: Int,small: Boolean=false): Int = when(DuoCenter.glyph(key)) {
        DuoAudioState.PLANE_GLYPH -> 0
        DuoAudioState.WIRED_GLYPH -> 1
        DuoAudioState.BLUETOOTH_GLYPH -> 2
        DuoAudioState.CELLULAR_GLYPH -> -2
        DuoAudioState.OFFLINE_GLYPH -> 5
        else -> if(key and DuoCenter.WARNING!=0) { if(small) 4 else 3 } else -1
    }
    private fun prepareSymbol(slot: Int,color: Int) {
        val image=symbols[slot] ?: run {
            val glyph=when(slot) {
                0 -> DuoIosSymbols.airplane
                1 -> if(config.headphoneStyle==3) DuoIosSymbols.airpods else DuoIosSymbols.headphones
                2 -> if(config.headphoneStyle==2) DuoIosSymbols.headphones else DuoIosSymbols.airpods
                3,4 -> DuoIosSymbols.wifi_warning
                5 -> DuoIosSymbols.offline
                else -> DuoIosSymbols.bolt
            }
            val width: Float
            val height: Float
            if(slot==6) { height=11f*120f/47f; width=height*glyph.width/glyph.height }
            else {
                val size=120f*(if(slot==4) 15f else 16f)/47f*if(slot in 1..2) config.headphoneScale else 1f
                val ratio=glyph.width/glyph.height
                width=if(ratio>=1) size else size*ratio
                height=if(ratio>=1) size/ratio else size
            }
            val x=60f-width*glyph.centerX
            val top=if(slot==4) DuoIosWifi.top(true) else 60f-height*glyph.centerY
            Symbol(glyph,RectF(x,top,x+width,top+height)).also { symbols[slot]=it }
        }
        val tint=color or 0xff000000.toInt()
        if(image.tint!=tint) { image.tint=tint; image.paint.color=tint }
    }
    fun prepare(key: Int,color: Int,network: Int=0) {
        val small=network and DuoNetworkState.AIRPLANE!=0
        val slot=slot(key,small)
        if(slot>=0) prepareSymbol(slot,if(slot==2) DuoGeometry.bluetoothColor(color,DuoCenter.value(key)) else color)
        else if(slot==-2) cells=DuoIosCellular.paths
        else DuoIosWifi.paths(config.bold,small)
    }
    fun preparePlane(color: Int) = prepareSymbol(0,color)
    fun drawPlane(canvas: Canvas,color: Int,stacked: Boolean,primaryKey: Int,network: Int=0) {
        val image=symbols[0] ?: return
        // Native secondary airplane: symbolSize 12; top-center anchor at either
        // the measured primary bottom +1 or parent top +30. Symbol frame sizes
        // remain an Android approximation of SwiftUI metrics.
        val small=network and DuoNetworkState.AIRPLANE!=0
        val primarySlot=slot(primaryKey,small)
        val primaryBottom=if(primarySlot>=1) symbols[primarySlot]?.rect?.bottom ?: DuoIosWifi.layoutBottom(config.bold,small)
            else DuoIosWifi.layoutBottom(config.bold,small)
        val top=if(stacked) primaryBottom+120f/47f else 30f*120f/47f
        val saved=canvas.save()
        canvas.translate(60f,top); canvas.scale(.75f,.75f); canvas.translate(-60f,-image.rect.top)
        drawSymbol(canvas,0,color ushr 24)
        canvas.restoreToCount(saved)
    }
    fun prepareMarker(label: String,showBolt: Boolean,color: Int,text: Paint) {
        if(!config.showPercent && !showBolt) {
            if(markerBolt) { markerBolt=false; markerLabel=null; cutout.rewind(); ringDirty=true }
            return
        }
        if(showBolt) prepareSymbol(6,color)
        if(markerLabel==label && markerBolt==showBolt) return
        markerLabel=label; markerBolt=showBolt; ringDirty=true
        val center=if(showBolt && !config.showPercent) 14.042553f else 16.595745f
        val bolt=if(showBolt) symbols[6] else null
        val textWidth=if(config.showPercent) text.measureText(label) else 0f
        val spacing=if(config.showPercent && showBolt) -1.5f*120f/47f else 0f
        val width=textWidth+(bolt?.rect?.width() ?: 0f)+spacing
        val left=60f-width/2
        percentX=left+textWidth/2
        marker.rewind()
        if(config.showPercent) {
            text.getTextBounds(label,0,label.length,textInk)
            percentBaseline=center-(textInk.top+textInk.bottom)*.5f
            text.getTextPath(label,0,label.length,percentX,percentBaseline,marker)
        }
        if(bolt!=null) {
            bolt.rect.offsetTo(left+textWidth+spacing,center-bolt.rect.height()/2)
            val contour=bolt.glyph.paths.single()
            bounds.set(0f,0f,bolt.glyph.width,bolt.glyph.height)
            transform.setRectToRect(bounds,bolt.rect,android.graphics.Matrix.ScaleToFit.FILL)
            marker.addPath(contour,transform)
        }
        cutout.rewind(); mask.getFillPath(marker,cutout)
    }
    /** Boolean geometry is prepared on battery/marker changes. Canvas clipping
     * aliases mask edges in device coordinates; filled paths retain antialiasing
     * and avoid a clip operation on each redraw. */
    fun prepareRing(level: Int) {
        if(ringLevel==level && !ringDirty) return
        fun outline(fraction: Float,out: Path) {
            val start=ringMeasure.length/6f
            arc.rewind(); ringMeasure.getSegment(start,start+ringMeasure.length*fraction,arc,true)
            out.rewind(); ringStroke.getFillPath(arc,out)
            if(!cutout.isEmpty) out.op(cutout,Path.Op.DIFFERENCE)
        }
        if(ringDirty) outline(2f/3f,track)
        outline(DuoGeometry.arcFillFraction(level),progress)
        ringLevel=level; ringDirty=false
    }
    fun draw(canvas: Canvas,key: Int,color: Int,network: Int=0,cellular: DuoCellularMotion?=null) {
        val small=network and DuoNetworkState.AIRPLANE!=0
        val slot=slot(key,small)
        if(slot>=0) drawSymbol(canvas,slot,color ushr 24)
        else if(slot==-2) cells?.let { bars ->
            for(i in bars.indices) { fill.color=cellular?.color(color,i) ?: DuoGeometry.signalColor(color,network,i); canvas.drawPath(bars[i],fill) }
        }
        else {
            val paths=DuoIosWifi.paths(config.bold,small)
            for(i in 0..2) {
                fill.color=DuoGeometry.wifiColor(color,DuoCenter.value(key),i)
                canvas.drawPath(paths[i],fill)
            }
        }
    }
    fun drawBolt(canvas: Canvas,color: Int) = drawSymbol(canvas,6,color ushr 24)
    private fun drawSymbol(canvas: Canvas,slot: Int,alpha: Int) {
        val image=symbols[slot] ?: return
        image.paint.alpha=alpha
        val saved=canvas.save()
        canvas.translate(image.rect.left,image.rect.top)
        canvas.scale(image.rect.width()/image.glyph.width,image.rect.height()/image.glyph.height)
        // Preserve native peer layers. Union changes antialias coverage where
        // warning-symbol pieces meet, even when its binary silhouette matches.
        for(path in image.glyph.paths) canvas.drawPath(path,image.paint)
        canvas.restoreToCount(saved)
    }
}
