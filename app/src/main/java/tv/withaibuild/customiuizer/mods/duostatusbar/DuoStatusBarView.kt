package tv.withaibuild.customiuizer.mods.duostatusbar

import android.content.Context
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import tv.withaibuild.customiuizer.mods.utils.FatalErrors

/** Fixed iOS templates; short event-driven transitions reuse drawing state and paths. */
internal class DuoStatusBarView(context: Context, private val config: DuoConfig,
    private val onFailure: (Throwable) -> Unit,
    private val onGeometryChanged: () -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * 120f / 47f
        if (config.showPercent) typeface = DuoIosFont.get(context)
    }
    private val cellularText = if (config.networkFallback && config.cellularStyle == 2) Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 35f
        isFakeBoldText = config.bold
    } else null
    private var cellularFont: Typeface? = null
    private var cellularBaseline = 0f
    private val iosGlyphs = DuoIosGlyphs(config)
    // Resolve once on attachment. Resource reads recur only on configuration changes.
    private val nativeHeightId = resources.getIdentifier("status_bar_icon_height", "dimen", "com.android.systemui")
    var iconSizePx = 0
        private set
    private var drawingSizePx = 0f
    val layoutWidthPx: Int get() = iconSizePx + paddingLeft + paddingRight
    var binding: DuoBinding? = null
    private var level = -1
    private var charging = false
    private var saver = false
    private var foreground = -1
    private var network = 0
    private val transition = DuoTransition()
    private val secondaryTransition = DuoTransition()
    private val cellularMotion = DuoCellularMotion()
    private var offsetPx = 0f
    private var sharedHeightPx = 0
    private var label = ""
    private var failed = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        updateSize()
        syncTypeface()
    }

    val usesCellularText: Boolean get() = cellularText != null

    /** Binding/configuration only; the fixed subset is shared across all bindings. */
    fun syncTypeface() {
        val paint = cellularText ?: return
        val localFont = DuoIosFont.get(context)
        if (cellularFont === localFont) return
        cellularFont = localFont
        paint.typeface = localFont
        val bounds = Rect()
        paint.getTextBounds("5G", 0, 2, bounds)
        cellularBaseline = 61f - (bounds.top + bounds.bottom) / 2f
        invalidate()
    }

    private fun updateSize() {
        val nativeHeight = if (nativeHeightId == 0) 0 else resources.getDimensionPixelSize(nativeHeightId)
        drawingSizePx = DuoSizing.drawingPixels(config, nativeHeight, resources.displayMetrics.density)
        iconSizePx = kotlin.math.ceil(drawingSizePx).toInt().coerceAtLeast(1)
        offsetPx = config.offsetDp * resources.displayMetrics.density
        val horizontal = (config.horizontalPaddingDp * resources.displayMetrics.density + 0.5f).toInt()
        setPadding(horizontal, paddingTop, horizontal, paddingBottom)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (failed) return
        try {
            updateSize()
            binding?.syncTypeface()
            layoutParams?.let { it.width = layoutWidthPx }
            onGeometryChanged()
            requestLayout()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            failed = true
            onFailure(t)
        }
    }

    fun render(level: Int, charging: Boolean, saver: Boolean, foreground: Int, network: Int, audio: Int) {
        val key = DuoCenter.key(network, audio, config)
        if (this.level == level && this.charging == charging && this.saver == saver &&
            this.foreground == foreground && this.network == network && transition.current == key) return
        if (transition.previous >= 0) iosGlyphs.prepare(transition.previous,foreground,network)
        if (transition.current >= 0) iosGlyphs.prepare(transition.current,foreground,network)
        iosGlyphs.prepare(key,foreground,network)
        val primary=DuoCompactLayout.primary(network)
        var secondary=DuoCompactLayout.secondary(primary,network and DuoNetworkState.AIRPLANE!=0,false,false)
        // Keep existing Android text/audio options informative when they replace
        // the primary cellular bars. They are explicit product extensions.
        val audioHint=DuoCenter.glyph(key) in DuoAudioState.WIRED_GLYPH..DuoAudioState.BLUETOOTH_GLYPH
        val cellularText=DuoCenter.glyph(key)==DuoAudioState.CELLULAR_GLYPH && this.cellularText!=null && DuoCenter.value(key) in 2..5
        if(primary==DuoCompactLayout.CELLULAR && (!config.networkFallback || audioHint || cellularText)) secondary=DuoCompactLayout.CELLULAR
        if(secondary==DuoCompactLayout.AIRPLANE || secondaryTransition.previous==DuoCompactLayout.AIRPLANE ||
            secondaryTransition.current==DuoCompactLayout.AIRPLANE) iosGlyphs.preparePlane(foreground)
        val now=SystemClock.uptimeMillis()
        val mayAnimate=this.level>=0 && !saver && isShown && windowVisibility==VISIBLE && ValueAnimator.areAnimatorsEnabled()
        val timeScale=ValueAnimator.getDurationScale()
        // Wi-Fi strength changes are not wrapped in the native withAnimation.
        // Replacing their primitive value must not restart an entering silhouette.
        if(transition.current>=0 && DuoCenter.glyph(transition.current)==DuoCenter.glyph(key) &&
            (transition.current and DuoCenter.WARNING)==(key and DuoCenter.WARNING)) transition.replaceCurrent(key)
        else transition.change(key, now, mayAnimate,timeScale)
        secondaryTransition.change(secondary,now,mayAnimate,timeScale)
        val cellularVisible=primary==DuoCompactLayout.CELLULAR || secondary==DuoCompactLayout.CELLULAR ||
            secondaryTransition.previous==DuoCompactLayout.CELLULAR || DuoCenter.glyph(transition.previous)==DuoAudioState.CELLULAR_GLYPH
        cellularMotion.change(network,now,mayAnimate && cellularVisible,timeScale)
        if (saver) stopTransition()
        if (this.level != level && config.showPercent) label = level.toString()
        iosGlyphs.prepareMarker(label,DuoGeometry.showBolt(level,charging,config.showChargingBolt,config.showPercent),
            DuoGeometry.batteryColor(foreground,level,charging,saver),text)
        iosGlyphs.prepareRing(level)
        this.level = level
        this.charging = charging
        this.saver = saver
        this.foreground = foreground
        this.network = network
        invalidate()
    }

    fun stopTransition() { transition.finish(); secondaryTransition.finish(); cellularMotion.finish() }

    val rowHeightPx: Int
        get() = (height - paddingTop - paddingBottom).coerceAtLeast(0)

    val sharedRowHeightPx: Int get() = sharedHeightPx

    fun requiredHeight(sourceHeight: Int): Int = DuoSizing.requiredHeight(iconSizePx, sourceHeight, offsetPx)

    fun refreshGeometry() = onGeometryChanged()

    fun shareHeight(height: Int) {
        if (sharedHeightPx == height) return
        sharedHeightPx = height
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!failed) onGeometryChanged()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) stopTransition()
        else if (!failed) onGeometryChanged()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) stopTransition()
    }

    override fun onDetachedFromWindow() {
        stopTransition()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // A wrap-content battery host must retain a height after its original children are hidden.
        setMeasuredDimension(resolveSize(layoutWidthPx, widthMeasureSpec),
            resolveSize(iconSizePx + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (failed || level < 0 || width <= 0 || height <= 0) return
        val saved = canvas.save()
        try {
            val contentWidth = width - paddingLeft - paddingRight
            val contentHeight = height - paddingTop - paddingBottom
            val rowHeight = DuoSizing.sharedHeight(contentHeight, sharedHeightPx)
            val offset = DuoSizing.offset(rowHeight, offsetPx)
            val size = minOf(drawingSizePx,DuoSizing.drawingSize(contentWidth, rowHeight, offset))
            if (size <= 0f) return
            val scale = size / 120f
            canvas.translate(paddingLeft + (contentWidth - size) / 2f,
                paddingTop + (contentHeight - size) / 2f + offset)
            canvas.scale(scale, scale)
            drawIcon(canvas)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            failed = true
            onFailure(t)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun drawIcon(canvas: Canvas) {
        val showBolt = DuoGeometry.showBolt(level,charging,config.showChargingBolt,config.showPercent)
        val track = DuoGeometry.dim(foreground)
        val tint = DuoGeometry.batteryColor(foreground, level, charging, saver)
        fill.color=track
        canvas.drawPath(iosGlyphs.track,fill)
        fill.color=tint
        canvas.drawPath(iosGlyphs.progress,fill)
        if(showBolt) iosGlyphs.drawBolt(canvas,tint)
        if(config.showPercent) {
            text.color=tint
            canvas.drawText(label,iosGlyphs.percentX,iosGlyphs.percentBaseline,text)
        }

        val now=SystemClock.uptimeMillis()
        if (!ValueAnimator.areAnimatorsEnabled()) stopTransition()
        transition.fraction(now); secondaryTransition.fraction(now); cellularMotion.sample(now)
        if(transition.running) drawCenter(canvas,transition.previous,transition.previousAlpha)
        drawCenter(canvas, transition.current, transition.currentAlpha)
        if(secondaryTransition.running) drawSecondary(canvas,secondaryTransition.previous,secondaryTransition.previousAlpha,transition.previous)
        drawSecondary(canvas,secondaryTransition.current,secondaryTransition.currentAlpha,transition.current)
        if ((transition.running || secondaryTransition.running || cellularMotion.running) && isShown && windowVisibility == VISIBLE) postInvalidateOnAnimation()
    }

    private fun drawSecondary(canvas: Canvas,kind: Int,alpha: Int,primaryKey: Int) {
        if(alpha<=0) return
        val color=DuoGeometry.withAlpha(foreground,(foreground ushr 24)*alpha/255)
        if(kind==DuoCompactLayout.AIRPLANE) {
            iosGlyphs.drawPlane(canvas,color,DuoCenter.glyph(primaryKey)!=DuoAudioState.PLANE_GLYPH,primaryKey,network)
            return
        }
        if(kind!=DuoCompactLayout.CELLULAR) return
        fill.color = cellularMotion.color(color, 0)
        canvas.drawCircle(34.535364f, 101.289640f, 5.106383f, fill)
        fill.color = cellularMotion.color(color, 1)
        canvas.drawCircle(51.114283f, 107.689895f, 5.106383f, fill)
        fill.color = cellularMotion.color(color, 2)
        canvas.drawCircle(68.885717f, 107.689895f, 5.106383f, fill)
        fill.color = cellularMotion.color(color, 3)
        canvas.drawCircle(85.464636f, 101.289640f, 5.106383f, fill)
    }

    private fun drawCenter(canvas: Canvas, key: Int, alpha: Int) {
        if (key < 0 || alpha <= 0) return
        val foreground = DuoGeometry.withAlpha(this.foreground, (this.foreground ushr 24) * alpha / 255)
        // 24B91020 Wi-Fi transitions combine center-anchored scale
        // (minimum .00001) with opacity. Reuse this finite handoff's fraction;
        // strength-only updates and the battery ring remain static.
        val saved=if(alpha<255 && DuoCenter.glyph(key)==DuoAudioState.WIFI_GLYPH) canvas.save() else -1
        if(saved>=0) {
            val scale=.00001f+.99999f*(alpha/255f)
            canvas.scale(scale,scale,60f,60f)
        }
        drawCenterGlyph(canvas, key, foreground)
        if(saved>=0) canvas.restoreToCount(saved)
    }

    private fun drawCenterGlyph(canvas: Canvas,key: Int,foreground: Int) {
        if(DuoCenter.glyph(key)==DuoAudioState.PLANE_GLYPH) return // The native airplane is the secondary item.
        val value=DuoCenter.value(key)
        if(DuoCenter.glyph(key)==DuoAudioState.CELLULAR_GLYPH && cellularText!=null && value in 2..5) {
            cellularText.color=foreground
            canvas.drawText(DuoCellularType.label(value),59.5f,cellularBaseline,cellularText)
        } else iosGlyphs.draw(canvas,key,foreground,network,cellularMotion)
    }
}
