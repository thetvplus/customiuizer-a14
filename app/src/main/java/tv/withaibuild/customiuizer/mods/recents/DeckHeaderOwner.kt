package tv.withaibuild.customiuizer.mods.recents

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.graphics.RectF
import tv.withaibuild.customiuizer.mods.utils.CleanupActions
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.PrefMap

internal class DeckHeaderConfig(val iconPercent: Int, val titlePercent: Int, val cardPercent: Int = 0) {
    companion object {
        fun read(prefs: PrefMap) = DeckHeaderConfig(
            prefs.getStringAsInt("system_recents_ios18_deck_header_icon",0),
            prefs.getStringAsInt("system_recents_ios18_deck_header_title",0),
            if (prefs.getBoolean("system_recents_ios18_deck_size_auto",true)) 0
            else prefs.getInt("system_recents_ios18_deck_size",75).coerceIn(55,85))
    }
    fun ratio(percent: Int) = percent.coerceIn(50,125)/100f
}

/** Header ink has its own scale; Deck depth must not magnify native app identity.
 * Native icon/text objects, font scaling, actions and root gesture transform stay
 * native. All layout metrics are read on layout, then reused by finite frames. */
internal class DeckHeaderOwner(val view: View,private val icon: View, private val title: TextView,private val badge: View,
    private val config: DeckHeaderConfig) : View.OnLayoutChangeListener {
    private class Opacity(val view: View,private val hides: Boolean=false) {
        private var native=view.alpha
        private var applied=Float.NaN
        private var visibility=view.visibility
        private var hidden=false
        fun pose(factor: Float,nativeWritten: Boolean=false) {
            if(nativeWritten || view.alpha!=applied) native=view.alpha
            applied=native*factor
            if(view.alpha!=applied) view.alpha=applied
            if(!hides) return
            if(!hidden || view.visibility!=View.INVISIBLE) visibility=view.visibility
            if(factor==0f && visibility==View.VISIBLE) { hidden=true; view.visibility=View.INVISIBLE }
            else if(hidden) { if(view.visibility==View.INVISIBLE) view.visibility=visibility; hidden=false }
        }
        fun close() {
            if(view.alpha==applied) view.alpha=native
            if(hidden && view.visibility==View.INVISIBLE) view.visibility=visibility
        }
    }
    private class Original(val view: View) {
        val scaleX=view.scaleX; val scaleY=view.scaleY
        val pivotX=view.pivotX; val pivotY=view.pivotY
        val pivotWasSet=view.isPivotSet
        val translationX=view.translationX; val translationY=view.translationY
        var appliedX=Float.NaN; var appliedY=Float.NaN; var appliedTranslation=Float.NaN
        var appliedVertical=Float.NaN
        fun pose(scale: Float,translation: Float=translationX,vertical: Float=translationY) {
            if(view.pivotX!=0f) view.pivotX=0f
            if(view.pivotY!=view.height*.5f) view.pivotY=view.height*.5f
            appliedX=scaleX*scale; appliedY=scaleY*scale; appliedTranslation=translation
            appliedVertical=vertical
            if(view.scaleX!=appliedX) view.scaleX=appliedX
            if(view.scaleY!=appliedY) view.scaleY=appliedY
            if(view.translationX!=translation) view.translationX=translation
            if(view.translationY!=vertical) view.translationY=vertical
        }
        fun release() {
            if(view.scaleX==appliedX) view.scaleX=scaleX
            if(view.scaleY==appliedY) view.scaleY=scaleY
            if(view.translationX==appliedTranslation) view.translationX=translationX
            if(view.translationY==appliedVertical) view.translationY=translationY
            if(pivotWasSet) { view.pivotX=pivotX; view.pivotY=pivotY } else view.resetPivot()
        }
    }
    private val image=Original(icon)
    private val label=Original(title)
    private val lock=Original(badge)
    private val headerOpacity=Opacity(view,true)
    private val titleOpacity=Opacity(title)
    private val iconInk=RectF()
    private val density=view.resources.displayMetrics.density
    private val autoTitleSize=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
        15f,view.resources.displayMetrics)
    private var scale=1f
    private var closed=false
    private var badgeGap=0f
    private var depth=-.2f
    private var index=0
    private var landscape=false
    init {
        try { for(child in arrayOf(view,icon,title,badge)) child.addOnLayoutChangeListener(this); metrics() }
        catch(error: Throwable) {
            val cleanup=CleanupActions(error); cleanup.run { close() }; cleanup.finish()
        }
    }
    private fun metrics() {
        badgeGap=(badge.left-title.right).toFloat()
        val imageView=icon as? ImageView
        val drawable=imageView?.drawable
        if(imageView!=null && drawable!=null && !drawable.bounds.isEmpty) {
            iconInk.set(drawable.bounds); imageView.imageMatrix.mapRect(iconInk)
            iconInk.offset(icon.paddingLeft.toFloat(),icon.paddingTop.toFloat())
        } else iconInk.set(icon.paddingLeft.toFloat(),icon.paddingTop.toFloat(),
            (icon.width-icon.paddingRight).toFloat(),(icon.height-icon.paddingBottom).toFloat())
    }
    fun pose(deckScale: Float,index: Int=this.index,depth: Float=this.depth,landscape: Boolean=this.landscape) {
        if(closed || deckScale<=0f || !deckScale.isFinite()) return
        scale=deckScale; this.index=index; this.depth=depth; this.landscape=landscape
        // Depth is valid before the native children receive their first layout.
        refreshOpacity()
        if(icon.width==0 || title.height==0) return
        // 22G100's standard 16 Pro branch: 26pt icon, 8pt gaps, title baseline
        // 16pt above the snapshot. Subheadline's default maps to Android 15sp;
        // retain the native font and Android font scaling instead of fixing px.
        val inkSize=maxOf(iconInk.width()*image.scaleX,iconInk.height()*image.scaleY).coerceAtLeast(1f)
        val iconRatio=if(config.iconPercent==0) 26f*density/inkSize else config.ratio(config.iconPercent)
        val titleRatio=if(config.titlePercent==0) autoTitleSize/(title.textSize*label.scaleY) else config.ratio(config.titlePercent)
        val iconScale=iconRatio/deckScale
        val titleScale=titleRatio/deckScale
        val iconBottom=view.height-8f*density/deckScale
        val iconCenter=iconBottom-iconInk.height()*image.scaleY*iconScale*.5f
        image.pose(iconScale,vertical=iconBottom-icon.top-icon.height*.5f-(iconInk.bottom-icon.height*.5f)*image.scaleY*iconScale)
        val titleX=icon.left+image.translationX+iconInk.right*image.scaleX*iconScale+8f*density/deckScale-title.left
        val baseline=view.height-16f*density/deckScale
        val titleY=baseline-title.top-title.height*.5f-(title.baseline-title.height*.5f)*label.scaleY*titleScale
        label.pose(titleScale,titleX,titleY)
        lock.pose(1f/deckScale,title.left+titleX+title.width*label.scaleX*titleScale+badgeGap/deckScale-badge.left,
            iconCenter-badge.top-badge.height*.5f)
    }
    fun refreshOpacity(nativeWritten: Boolean=false) {
        if(closed) return
        val h=1f-((-depth-.285f)*4.5f).coerceIn(0f,1f)
        val f=if(index==0) 1f else 1f-((-depth-.1415f)*8.12f).coerceIn(0f,1f)
        headerOpacity.pose(h,nativeWritten)
        titleOpacity.pose(f*((-depth-(if(landscape) .0074f else .023f))*(if(landscape) 75f else 40f)).coerceIn(0f,1f))
    }
    fun close() {
        if(closed) return
        closed=true
        val cleanup=CleanupActions()
        for(child in arrayOf(view,icon,title,badge)) cleanup.run { child.removeOnLayoutChangeListener(this) }
        cleanup.run { image.release() }; cleanup.run { label.release() }; cleanup.run { lock.release() }
        cleanup.run { headerOpacity.close() }; cleanup.run { titleOpacity.close() }; cleanup.finish()
    }
    override fun onLayoutChange(v: View,l: Int,t: Int,r: Int,b: Int,ol: Int,ot: Int,or: Int,ob: Int) = ModuleHelper.guarded {
        metrics(); pose(scale)
    }
}
