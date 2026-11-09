package tv.withaibuild.customiuizer.mods.media

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView

/** Appearance only. Native views retain data, tint, actions, focus and seek ownership. */
internal class MediaCardAppearance(root: ViewGroup, ids: Map<String, Int>) {
    private val art=root.findViewById<ImageView>(ids.getValue("album_art"))
    private val artScale=art.scaleType
    private val artForeground=art.foreground
    private val padding=intArrayOf(art.paddingLeft,art.paddingTop,art.paddingRight,art.paddingBottom)
    private val text=listOf("header_title","header_artist","media_elapsed_time","media_total_time",
        "media_scrubbing_elapsed_time","media_scrubbing_total_time").mapNotNull { name ->
        ids[name]?.let { root.findViewById<TextView>(it) }?.let {
            TextStyle(it,when(name) { "header_title"->16f; "header_artist"->14f; else->11f },
                name=="header_title",name.contains("time"),name.endsWith("total_time"))
        }
    }
    private val seek=ids["media_progress_bar"]?.let { root.findViewById<SeekBar>(it) }?.let(::ProgressStyle)
    private val buttons=listOf("actionPlayPause","actionNext","actionPrev","action0","action1","action2","action3","action4")
        .mapNotNull { name -> ids[name]?.let { root.findViewById<ImageButton>(it) }?.let(::ButtonStyle) }
    fun apply() {
        if(art.scaleType!=ImageView.ScaleType.CENTER_CROP) art.scaleType=ImageView.ScaleType.CENTER_CROP
        if(art.paddingLeft!=0 || art.paddingTop!=0 || art.paddingRight!=0 || art.paddingBottom!=0) art.setPadding(0,0,0,0)
        if(art.foreground===artForeground && artForeground!=null) art.foreground=null
        for(style in text) style.apply()
        seek?.apply()
        for(style in buttons) style.apply()
    }
    fun bind(button: ImageButton,action: Long,active: Boolean = true) {
        for(style in buttons) if(style.view===button) { style.bind(action,active);return }
    }
    fun restore() {
        if(art.scaleType==ImageView.ScaleType.CENTER_CROP) art.scaleType=artScale
        if(art.paddingLeft==0 && art.paddingTop==0 && art.paddingRight==0 && art.paddingBottom==0)
            art.setPadding(padding[0],padding[1],padding[2],padding[3])
        if(art.foreground==null) art.foreground=artForeground
        for(style in text) style.restore()
        seek?.restore()
        for(style in buttons) style.restore()
    }
    private class TextStyle(val view: TextView,val sp: Float,bold: Boolean,val time: Boolean,trailing: Boolean) {
        private var size=view.textSize
        private var face=view.typeface
        private var pad=view.includeFontPadding
        private var gravity=view.gravity
        private var features=view.fontFeatureSettings
        private val desiredFace=Typeface.create(if(bold) "sans-serif-medium" else "sans-serif",Typeface.NORMAL)
        private val desiredGravity=(if(trailing) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        private var appliedSize=Float.NaN
        fun apply() {
            if(view.textSize!=appliedSize) size=view.textSize
            if(view.typeface!==desiredFace) face=view.typeface
            if(view.includeFontPadding) pad=true
            if(view.gravity!=desiredGravity) gravity=view.gravity
            if(time && view.fontFeatureSettings!="tnum") features=view.fontFeatureSettings
            val target=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,sp,view.resources.displayMetrics)
            if(view.textSize!=target) view.setTextSize(TypedValue.COMPLEX_UNIT_PX,target)
            appliedSize=target
            if(view.typeface!==desiredFace) view.typeface=desiredFace
            if(view.includeFontPadding) view.includeFontPadding=false
            if(view.gravity!=desiredGravity) view.gravity=desiredGravity
            if(time && view.fontFeatureSettings!="tnum") view.fontFeatureSettings="tnum"
        }
        fun restore() {
            if(view.textSize==appliedSize) view.setTextSize(TypedValue.COMPLEX_UNIT_PX,size)
            if(view.typeface===desiredFace) view.typeface=face
            if(!view.includeFontPadding) view.includeFontPadding=pad
            if(view.gravity==desiredGravity) view.gravity=gravity
            if(time && view.fontFeatureSettings=="tnum") view.fontFeatureSettings=features
            appliedSize=Float.NaN
        }
    }
    private class ProgressStyle(val view: SeekBar) {
        private var nativeTrack=view.progressDrawable
        private var nativeThumb=view.thumb
        private val density=view.resources.displayMetrics.density
        private val track=LayerDrawable(arrayOf(Track(density,false),Track(density,true),Track(density,true))).apply {
            setId(0,android.R.id.background);setId(1,android.R.id.secondaryProgress);setId(2,android.R.id.progress)
        }
        private val thumb=Thumb(density)
        fun apply() {
            if(view.progressDrawable!==track) { nativeTrack=view.progressDrawable;view.progressDrawable=track }
            if(view.thumb!==thumb) { nativeThumb=view.thumb;thumb.alpha=nativeThumb?.alpha ?: 255;view.thumb=thumb }
        }
        fun restore() {
            if(view.progressDrawable===track) view.progressDrawable=nativeTrack
            if(view.thumb===thumb) { nativeThumb?.alpha=thumb.alpha;view.thumb=nativeThumb }
        }
    }
    private class ButtonStyle(val view: ImageButton) {
        private var native: Drawable?=view.drawable
        private var scale=view.scaleType
        private val glyph=Transport(view.resources.displayMetrics.density)
        private var action=0L
        fun bind(action: Long,active: Boolean) {
            // bindButtonCommon already installed the latest native icon/listener.
            // Even offscreen, remember its semantics without taking presentation.
            if(view.drawable!==glyph) { native=view.drawable;scale=view.scaleType }
            this.action=action
            if(active) apply() else restore()
        }
        fun apply() {
            if(view.drawable!==glyph) {
                if(view.drawable!==native) {
                    // An unattributed async replacement cannot inherit an older
                    // play/pause identity. Keep that newer native owner intact.
                    native=view.drawable;scale=view.scaleType;action=0L
                    return
                }
                scale=view.scaleType
            }
            if(action==2L || action==4L || action==16L || action==32L) {
                if(native==null) return
                glyph.action=action
                if(view.drawable!==glyph) view.setImageDrawable(glyph)
                if(view.scaleType!=ImageView.ScaleType.CENTER) view.scaleType=ImageView.ScaleType.CENTER
            } else restore()
        }
        fun restore() {
            if(view.drawable===glyph) { view.setImageDrawable(native);if(view.scaleType==ImageView.ScaleType.CENTER) view.scaleType=scale }
        }
    }
    private abstract class Tinted: Drawable() {
        protected val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private var colors: ColorStateList?=null
        private var drawableAlpha=255
        private var color=Color.WHITE
        init { paint.color=color }
        override fun setAlpha(alpha: Int) { if(drawableAlpha!=alpha) { drawableAlpha=alpha;invalidateSelf() } }
        override fun getAlpha()=drawableAlpha
        override fun setColorFilter(filter: ColorFilter?) { if(paint.colorFilter!==filter) { paint.colorFilter=filter;invalidateSelf() } }
        override fun setTintList(tint: ColorStateList?) { colors=tint;onStateChange(state) }
        override fun isStateful()=colors?.isStateful==true
        override fun onStateChange(state: IntArray): Boolean {
            val next=colors?.getColorForState(state,colors!!.defaultColor) ?: Color.WHITE
            if(color==next) return false
            color=next;invalidateSelf();return true
        }
        protected fun preparePaint() { paint.color=color;paint.alpha=Color.alpha(color)*drawableAlpha/255 }
        @Deprecated("Platform API") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
    private class Track(val density: Float,val clipped: Boolean): Tinted() {
        override fun onLevelChange(level: Int): Boolean { invalidateSelf();return true }
        override fun getIntrinsicHeight()=(4f*density+.5f).toInt()
        override fun draw(canvas: Canvas) {
            val b=bounds;val center=b.exactCenterY();val radius=2f*density
            val width=if(clipped) b.width()*level/10000f else b.width().toFloat()
            if(width<=0f) return
            val rtl=layoutDirection==android.view.View.LAYOUT_DIRECTION_RTL
            val left=if(rtl) b.right-width else b.left.toFloat()
            preparePaint();canvas.drawRoundRect(left,center-radius,left+width,center+radius,radius,radius,paint)
        }
    }
    private class Thumb(val density: Float): Tinted() {
        override fun getIntrinsicWidth()=(12f*density+.5f).toInt()
        override fun getIntrinsicHeight()=intrinsicWidth
        override fun isStateful()=true
        override fun onStateChange(state: IntArray): Boolean { super.onStateChange(state);invalidateSelf();return true }
        override fun draw(canvas: Canvas) {
            val pressed=state.any { it==android.R.attr.state_pressed || it==android.R.attr.state_focused }
            preparePaint();canvas.drawCircle(bounds.exactCenterX(),bounds.exactCenterY(),(if(pressed) 5f else 2.5f)*density,paint)
        }
    }
    private class Transport(val density: Float): Tinted() {
        private val path=Path()
        var action=0L
            set(value) {
                if(field==value) return
                field=value;path.rewind()
                if(value==2L) {
                    path.addRoundRect(6f,4f,10f,20f,1f,1f,Path.Direction.CW)
                    path.addRoundRect(14f,4f,18f,20f,1f,1f,Path.Direction.CW)
                } else if(value==4L) {
                    path.moveTo(6f,3f);path.lineTo(21f,12f);path.lineTo(6f,21f);path.close()
                } else {
                    for(index in 0..1) {
                        val offset=1f+11f*index
                        path.moveTo(offset,5f);path.lineTo(offset+11f,12f);path.lineTo(offset,19f);path.close()
                    }
                }
                invalidateSelf()
            }
        override fun getIntrinsicWidth()=(28f*density+.5f).toInt()
        override fun getIntrinsicHeight()=intrinsicWidth
        override fun draw(canvas: Canvas) {
            val b=bounds;val size=minOf(b.width(),b.height()).toFloat()
            val saved=canvas.save();canvas.translate(b.exactCenterX()-size*.5f,b.exactCenterY()-size*.5f)
            canvas.scale(size/24f,size/24f)
            if(action==16L) { canvas.translate(24f,0f);canvas.scale(-1f,1f) }
            preparePaint();canvas.drawPath(path,paint);canvas.restoreToCount(saved)
        }
    }
}
