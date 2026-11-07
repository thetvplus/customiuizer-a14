package tv.withaibuild.customiuizer.mods.recents

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.util.SparseIntArray
import android.view.accessibility.AccessibilityManager
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.CleanupActions
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import java.util.IdentityHashMap
import java.lang.ref.WeakReference
import android.os.Handler
import android.os.Looper

internal class DeckFrame(val owner: DeckOwner,val rect: RectF,var index: Int,var scale: Float,var visible: Boolean,var z: Float,var depthScale: Float) {
    companion object {
        private val retired=Any()
        fun copy(source: Any,out: Any) {
            when(val value=XposedHelpers.getAdditionalInstanceField(source,DeckOwner.FRAME)) {
                is DeckFrame -> value.copyTo(out)
                retired -> XposedHelpers.setAdditionalInstanceField(out,DeckOwner.FRAME,retired)
                else -> XposedHelpers.removeAdditionalInstanceField(out,DeckOwner.FRAME)
            }
        }
        fun isRetired(transform: Any): Boolean {
            val value=XposedHelpers.getAdditionalInstanceField(transform,DeckOwner.FRAME)
            return value===retired || value is DeckFrame && value.isRetired
        }
    }
    var generation=owner.geometryGeneration
    private val isRetired get()=owner.closed || generation!=owner.geometryGeneration
    fun copyTo(out: Any): Boolean {
        if(isRetired) {
            // Keep a marker without retaining the old owner: dropping metadata
            // alone lets native apply the stale LTRB with the new card matrix.
            XposedHelpers.setAdditionalInstanceField(out,DeckOwner.FRAME,retired)
            return false
        }
        if(!owner.allowed) { XposedHelpers.removeAdditionalInstanceField(out,DeckOwner.FRAME); return false }
        owner.putFrame(out,index,scale,visible,z,depthScale)
        return true
    }
}

/** Native stack lifetime, algorithms, visible cards and finite transition ticket. */
internal class DeckOwner(val stack: ViewGroup,val api: DeckNativeApi,private val headerConfig: DeckHeaderConfig,
    private val radiusHandoff: DeckRadiusHandoff?=null) : View.OnLayoutChangeListener,ViewTreeObserver.OnPreDrawListener {
    companion object {
        const val OWNER="a14.recents.deck.owner"
        const val SCOPE="a14.recents.deck.scope"
        const val FRAME="a14.recents.deck.frame"
        const val CARD="a14.recents.deck.card"
    }
    class Card(val owner: DeckOwner,val geometry: DeckCardGeometry,val task: Any,val taskId: Int,var index: Int,val header: DeckHeaderOwner,var depthScale: Float,val shadow: DeckCardShadow) {
        val view get()=geometry.view
        val depth=owner.depthOrder.add(view,owner.depthIndex(taskId,index),shadow.height)
        init {
            geometry.onScaleChanged={ value -> header.pose(value,index,(value*depthScale-1f)/.12f,owner.current.landscape) }
            // Native entry can defer the first scale sample until after layout.
            geometry.onScaleChanged?.invoke(geometry.scale)
        }
        var animation: ValueAnimator?=null
        val windowInput=RectF()
        // Native completion listeners can outlive the invocation carrying them.
        // Keep that card's native spring path until the card is returned/rebound.
        var nativePoseCompletion=false
        fun release() {
            val cleanup=CleanupActions()
            val previous=animation; animation=null
            cleanup.run { previous?.cancel() }
            cleanup.run { header.close() }
            cleanup.run { geometry.release() }
            cleanup.run { shadow.close() }
            cleanup.run { owner.depthOrder.remove(depth) }
            cleanup.finish()
        }
    }
    inner class Scope(val algorithm: Any) {
        val owner get()=this@DeckOwner
        var landscape=api.visualLandscape.invoke(algorithm) as Boolean
            private set
        fun refreshOrientation() {
            val next=api.visualLandscape.invoke(algorithm) as Boolean
            if(landscape==next) return
            landscape=next
            metrics=null
            retireGeometry()
        }
        private var metrics: IosDeckGeometry?=null
        private var screenWidth=0f
        private var cardWidth=0f
        private var safeHeight=0f
        private var cardHeight=0f
        private var layoutLeft=0f
        private var layoutTop=0f
        private var layoutGap=1f
        private var layoutCount=0
        var contentHeader=0f
            private set
        var fullWidth=0f
            private set
        val base get()=api.base.get(algorithm) as RectF
        val count get()=api.count.getInt(algorithm)
        val gap: Float get()=1f+api.gap.getFloat(algorithm)/base.width().coerceAtLeast(1f)
        fun geometryValid(): Boolean {
            val window=api.window.get(algorithm) as Rect
            val insets=api.visualInsets.get(algorithm) as Rect
            return !base.isEmpty && !window.isEmpty && window.width()>insets.left+insets.right &&
                window.height()>insets.top+insets.bottom && landscape==(window.width()>window.height())
        }
        fun write(index: Int,q: Float,out: Any): Boolean {
            val taskCount=count
            if(!allowed || index !in 0 until taskCount || !q.isFinite()) {
                XposedHelpers.removeAdditionalInstanceField(out,FRAME)
                if(!allowed && !suspended) restoreCards()
                return false
            }
            val bounds=base
            val window=api.window.get(algorithm) as Rect
            val insets=api.visualInsets.get(algorithm) as Rect
            val usableWidth=(window.width()-insets.left-insets.right).toFloat()
            val usableHeight=(window.height()-insets.top-insets.bottom).toFloat()
            // Launcher may keep its physical window portrait and rotate the
            // RecentsContainer. All rectangles/insets here use native visual axes.
            if(bounds.isEmpty || window.isEmpty || usableWidth<=0f || usableHeight<=0f ||
                landscape!=(window.width()>window.height())) {
                geometryReady=false
                XposedHelpers.removeAdditionalInstanceField(out,FRAME)
                val cleanup=CleanupActions()
                cleanup.run { if(!suspended) restoreCards() }
                cleanup.run { current.updateMinimum() }
                cleanup.run { stable.updateMinimum() }
                cleanup.finish()
                return false
            }
            if(metrics==null || screenWidth!=usableWidth || cardWidth!=bounds.width() || safeHeight!=usableHeight || cardHeight!=bounds.height()) {
                screenWidth=usableWidth; cardWidth=bounds.width(); safeHeight=usableHeight; cardHeight=bounds.height()
                metrics=IosDeckGeometry(screenWidth,cardWidth,stack.resources.displayMetrics.density,
                    DeckViewport.pageScale(screenWidth,safeHeight,cardWidth,cardHeight,headerConfig.cardPercent))
                contentHeader=api.headerHeight.getInt(algorithm).toFloat()
            }
            val model=metrics!!
            layoutGap=gap;layoutCount=taskCount
            val requested=-q/layoutGap
            val candidate=model.candidate(index,taskCount,requested)
            val presentation=model.presentationCursor(index,requested)
            val rect=api.rect.get(out) as RectF
            val nativeTop=(api.padding.get(algorithm) as Rect).top+window.top.toFloat()
            val top=DeckViewport.top(nativeTop,(window.top+insets.top).toFloat(),safeHeight,cardHeight,landscape)
            val left=model.left(index,presentation,(window.left+insets.left).toFloat())
            layoutLeft=(window.left+insets.left).toFloat(); layoutTop=top;fullWidth=window.width().toFloat()
            rect.set(left,top,left+bounds.width(),top+bounds.height())
            val scale=model.scale(index,presentation)
            val visible=candidate && left+(bounds.width()*(1f+scale))*.5f>window.left-screenWidth*.25f &&
                left+(bounds.width()*(1f-scale))*.5f<window.right+screenWidth*.25f
            putFrame(out,index,scale,visible,1f/(index+1),model.baseWidth/(model.pageScale*model.width))
            return true
        }
        fun contentAt(index: Int,q: Float,out: RectF): Boolean {
            val model=metrics ?: return false
            if(index !in 0 until layoutCount || !q.isFinite()) return false
            val cursor=-q/layoutGap
            val left=model.left(index,model.presentationCursor(index,cursor),layoutLeft)
            out.set(left,layoutTop,left+cardWidth,layoutTop+cardHeight)
            model.content(out,model.scale(index,cursor),contentHeader)
            return true
        }
        fun content(index: Int,q: Float,rect: RectF) {
            if(!allowed) return
            frozen[index]?.takeIf { transition!=0L && it.scroll==q }?.let {
                rect.set(it.rect); radiusHandoff?.capture(this@DeckOwner,transition,rect,it.scale); return
            }
            val model=metrics ?: return
            // Native getter already removed its header from the raw frame.
            val header=api.headerHeight.getInt(algorithm).toFloat()
            rect.top-=header
            // NavStubView queries its future q before changing the current scroll.
            // The raw rectangle and its depth scale must describe that same q.
            val scale=model.scale(index,-q/gap)
            model.content(rect,scale,header)
            if(transition!=0L) {
                frozen[index]=FrozenContent(RectF(rect),scale,q)
                radiusHandoff?.capture(this@DeckOwner,transition,rect,scale)
            }
        }
        fun updateMinimum() {
            if(base.width()>0f) api.minimum.setFloat(algorithm,api.minimumScroll.invoke(algorithm) as Float)
        }
    }
    val current=Scope(requireNotNull(api.current.get(stack)))
    private val stable=Scope(requireNotNull(api.stable.get(stack)))
    val handler=api.touch.get(stack)
    private val scroller=api.scroller.get(stack)
    var flingVelocity=0
    val scroll get()=api.scroll.getFloat(scroller)
    private val cards=LinkedHashMap<View,Card>()
    private val drawCards=ArrayList<Card>(8)
    private var dragging: Card?=null
    fun trackSwipe(card: Card,raw: Float,delta: Float) {
        if(dragging!==card) { dragging?.geometry?.finishSwipe(); dragging=card }
        card.geometry.nativeAlphaWritten(card.view.alpha)
        if(!card.geometry.trackSwipe(raw,delta)) suspend(true)
    }
    fun finishSwipe(card: Card) {
        card.geometry.finishSwipe()
        if(dragging===card) dragging=null
    }
    private val depthOrder=DeckDepthOrder(stack)
    private val depthIndices=SparseIntArray()
    private var depthObserver: ViewTreeObserver?=null
    private val accessibility=stack.context.getSystemService(AccessibilityManager::class.java)
    private val accessibilityChanged=AccessibilityManager.TouchExplorationStateChangeListener { ModuleHelper.guarded { refresh() } }
    private var baseAllowed=false
    private var tasksAllowed=true
    private var geometryReady=true
    private var physicalRotation=stack.display?.rotation
    private var suspended=false
    var closed=false
        private set
    var transition=0L
        private set
    private var sequence=0L
    var geometryGeneration=0L
        private set
    private val ticketTimeout=Runnable { ModuleHelper.guarded { endTransition(transition) } }
    private class LinkedSpring(spring: Any,val ticket: Long) {
        val spring=WeakReference(spring)
        lateinit var listener: DeckSpringEndListener
    }
    private val linkedSprings=ArrayList<LinkedSpring>(2)
    private val callbacks=Handler(Looper.getMainLooper())
    private class FrozenContent(val rect: RectF,val scale: Float,val scroll: Float)
    private val frozen=HashMap<Int,FrozenContent>(2)
    private var opening: DeckOpenAlignment?=null
    private var openingIndex=-1
    private val occludingBounds=RectF()
    private val gestureReference=RectF()
    private val gestureTarget=RectF()
    val openingTaskId: Int get()=opening?.selectedId ?: -1
    private val pairs=IdentityHashMap<Any,Pair<DeckFrame,DeckFrame>>()
    private var interpolation: Float?=null
    private val restoreTransform=api.transform.getDeclaredConstructor().newInstance()
    val router=DeckTouchRouter { api.nativeIntercept.invoke(handler,it) as Boolean }
    val allowed: Boolean get() = !closed && !suspended && baseAllowed && tasksAllowed && geometryReady &&
        ValueAnimator.areAnimatorsEnabled() && (transition!=0L ||
            (stack.parent?.let { api.recents.isInstance(it) && api.overview.getBoolean(it) && !api.split.getBoolean(it) }==true))

    init {
        try {
            XposedHelpers.setAdditionalInstanceField(stack,OWNER,this)
            for(scope in listOf(current,stable)) XposedHelpers.setAdditionalInstanceField(scope.algorithm,SCOPE,scope)
            XposedHelpers.setAdditionalInstanceField(handler,OWNER,this)
            XposedHelpers.setAdditionalInstanceField(scroller,OWNER,this)
            stack.addOnLayoutChangeListener(this)
            accessibility?.addTouchExplorationStateChangeListener(accessibilityChanged)
            refresh()
        } catch(error: Throwable) {
            val cleanup=CleanupActions(error); cleanup.run { close() }; cleanup.finish()
        }
    }
    fun refresh(requestLayout: Boolean=true) {
        if(closed) return
        val config=stack.resources.configuration
        baseAllowed=stack.isHardwareAccelerated && orientationReady(config) && stack.display?.displayId==0 &&
            config.smallestScreenWidthDp<600 && stack.layoutDirection==View.LAYOUT_DIRECTION_LTR &&
            accessibility?.isTouchExplorationEnabled!=true
        refreshGeometry()
        if(!allowed) restoreCards()
        else observeDepth()
        current.updateMinimum(); stable.updateMinimum()
        if(requestLayout) stack.requestLayout()
    }
    private fun orientationReady(config: Configuration)=when(stack.display?.rotation) {
        Surface.ROTATION_0 -> config.orientation==Configuration.ORIENTATION_PORTRAIT
        Surface.ROTATION_90,Surface.ROTATION_270 -> config.orientation==Configuration.ORIENTATION_LANDSCAPE
        else -> false
    }
    private fun retireGeometry() {
        geometryGeneration++
        geometryReady=false
        // A physical 90 -> 270 turn keeps the landscape boolean and dimensions,
        // but its old frames, window receipts and springs still own different axes.
        val cleanup=CleanupActions()
        cleanup.run { invalidateLayout() }
        cleanup.run { endTransition(transition) }
        cleanup.run { restoreCards() }
        cleanup.run { current.updateMinimum() }
        cleanup.run { stable.updateMinimum() }
        cleanup.finish()
    }
    fun rotationChanged(rotation: Int?) {
        if(physicalRotation!=rotation) {
            physicalRotation=rotation
            retireGeometry()
        }
    }
    fun refreshGeometry() {
        rotationChanged(stack.display?.rotation)
        current.refreshOrientation(); stable.refreshOrientation()
        geometryReady=current.geometryValid() && stable.geometryValid()
    }
    fun invalidateLayout() { frozen.clear(); pairs.clear(); interpolation=null; clearOpening() }
    fun tasksChanged() {
        val tasks=api.getTasks.invoke(api.getStack.invoke(stack)) as? List<*> ?: return
        tasksAllowed=true; depthIndices.clear()
        for(i in tasks.indices) {
            val task=tasks[i]
            if(task==null) { tasksAllowed=false; continue }
            val key=api.key.get(task)
            depthIndices.put(api.id.getInt(key),i)
            if(api.multiple.invoke(task) as Boolean || api.windowMode.getInt(key) !in 0..1) tasksAllowed=false
        }
        // Index belongs to the current layout, not to a task identity. Native
        // deletion/reordering invalidates every previously frozen endpoint.
        invalidateLayout()
        // The real task list includes the pending dismissal. The layout map
        // temporarily ignores it, which would otherwise give two cards rank 0.
        // Stable/interpolation frames and that simulated removal do not rank Z.
        for(card in cards.values) card.depth.index=depthIndex(card.taskId,card.depth.index)
        if(!allowed) restoreCards()
        current.updateMinimum(); stable.updateMinimum()
    }
    fun putFrame(out: Any,index: Int,scale: Float,visible: Boolean,z: Float,depthScale: Float) {
        val old=XposedHelpers.getAdditionalInstanceField(out,FRAME) as? DeckFrame
        if(old?.owner===this) { old.index=index; old.scale=scale; old.visible=visible; old.z=z; old.depthScale=depthScale; old.generation=geometryGeneration }
        else XposedHelpers.setAdditionalInstanceField(out,FRAME,DeckFrame(this,api.rect.get(out) as RectF,index,scale,visible,z,depthScale))
        api.scale.setFloat(out,scale); api.visible.setBoolean(out,visible); api.z.setFloat(out,z)
    }
    fun card(view: View,frame: DeckFrame): Card? {
        if(!allowed || frame.generation!=geometryGeneration || !frame.visible || view.parent!==stack) return null
        val task=api.getTask.invoke(view) ?: return null
        cards[view]?.takeIf { it.task===task }?.let {
            if(it.index!=frame.index || it.depthScale!=frame.depthScale)
                it.header.pose(it.geometry.scale,frame.index,(it.geometry.scale*frame.depthScale-1f)/.12f,current.landscape)
            it.index=frame.index; it.depthScale=frame.depthScale; return it
        }
        remove(view)
        if(cards.size>=24 || view.animation!=null) return null
        val geometry=DeckCardGeometry(view,stack)
        if(!geometry.owned) { suspend(true); return null }
        var header: DeckHeaderOwner?=null
        var shadow: DeckCardShadow?=null
        try {
            val headerView=api.getHeader.invoke(view)
            header=DeckHeaderOwner(headerView as View,api.headerIcon.get(headerView) as View,
                api.headerTitle.get(headerView) as android.widget.TextView,api.headerBadge.get(headerView) as View,headerConfig)
            val taskId=api.id.getInt(api.key.get(task))
            val thumbnail=api.getThumbnail.invoke(view) as View
            shadow=DeckCardShadow(view,headerView,api.thumbnailRadius.getInt(thumbnail).toFloat(),thumbnail)
            return Card(this,geometry,task,taskId,frame.index,header,frame.depthScale,shadow).also {
                cards[view]=it; XposedHelpers.setAdditionalInstanceField(view,CARD,it)
                drawCards.add(it)
                observeDepth()
            }
        } catch(error: Throwable) {
            val cleanup=CleanupActions(error)
            if(cards.containsKey(view)) cleanup.run { remove(view) }
            else {
                cleanup.run { shadow?.close() }; cleanup.run { header?.close() }; cleanup.run { geometry.release() }
            }
            cleanup.finish(); return null
        }
    }
    fun hit(x: Float,y: Float): Card? {
        if(!allowed) return null
        var best: Card?=null
        for(card in cards.values) if(card.view.visibility==View.VISIBLE && card.view.alpha>.01f &&
            card.view.hasOnClickListeners() && !(api.ignored.invoke(stack,card.task) as Boolean) && card.geometry.contains(x,y)) {
            if(best==null || card.view.z>best.view.z) best=card
        }
        return best
    }
    fun remove(view: View) {
        val card=cards.remove(view) ?: return
        drawCards.remove(card)
        if(dragging===card) dragging=null
        val cleanup=CleanupActions()
        cleanup.run { clearOpening() }
        cleanup.run { router.cancel() }
        cleanup.run { card.release() }
        cleanup.run { XposedHelpers.removeAdditionalInstanceField(view,CARD) }
        cleanup.run { if(cards.isEmpty()) stopObservingDepth() }
        cleanup.finish()
    }
    private fun depthIndex(taskId: Int,fallback: Int) =
        depthIndices.get(taskId,fallback)
    fun reapplyDepth() = depthOrder.apply()
    fun snap(scroll: Float): Float? = if(allowed) IosDeckSnap.target(scroll,current.gap,current.count,flingVelocity) else null
    fun appGestureInput(card: Card,window: RectF,reference: Int,gap: Float,bend: Float): RectF? {
        val q=scroll
        if(!allowed || !current.contentAt(reference,q,gestureReference) ||
            !current.contentAt(card.index,q,gestureTarget)) return null
        return card.windowInput.takeIf { card.geometry.windowInput(window,gestureReference,gestureTarget,
            reference-card.index,gap,current.fullWidth,bend,current.contentHeader,it) }
    }
    fun alignClosing(selected: Card,window: RectF,reference: RectF,alpha: Float) {
        val header=current.contentHeader
        val q=scroll
        for(i in 0 until drawCards.size) {
            val card=drawCards[i]
            if(card===selected || !current.contentAt(card.index,q,gestureTarget)) continue
            val slots=selected.index-card.index
            if(card.geometry.windowInput(window,reference,gestureTarget,slots,25f,current.fullWidth,0f,header,card.windowInput)) {
                card.geometry.alignClosing(card.windowInput,slots,25f,header)
                (api.getHeader.invoke(card.view) as View).alpha=alpha
            }
        }
    }
    private fun observeDepth() {
        if(depthObserver!=null || cards.isEmpty() || !allowed) return
        depthObserver=stack.viewTreeObserver.also { it.addOnPreDrawListener(this) }
    }
    private fun stopObservingDepth() {
        depthObserver?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        depthObserver=null
    }
    override fun onPreDraw(): Boolean {
        ModuleHelper.guarded {
            if(allowed) {
                // Native entry/header animators can write alpha after the first
                // layout and bypass TaskView's setters. Reconcile on an already
                // scheduled draw; never request another frame or resolve members.
                for(i in 0 until drawCards.size) {
                    val card=drawCards[i]
                    card.shadow.syncContentAlpha()
                    card.header.refreshOpacity()
                }
                if(dragging?.geometry?.updateSwipe()==false) suspend(true)
                if(allowed) reapplyDepth()
            } else restoreCards()
        }
        return true
    }
    fun interpolate(fraction: Float?) {
        interpolation=fraction
        if(fraction==null || pairs.isNotEmpty()) return
        val tasks=api.currentTasks.get(handler) as? List<*> ?: return
        val start=api.startTransforms.get(handler) as? List<*> ?: return
        val end=api.endTransforms.get(handler) as? List<*> ?: return
        for(i in 0 until minOf(tasks.size,start.size,end.size)) {
            val task=tasks[i] ?: continue
            val a=XposedHelpers.getAdditionalInstanceField(start[i] ?: continue,FRAME) as? DeckFrame ?: continue
            val b=XposedHelpers.getAdditionalInstanceField(end[i] ?: continue,FRAME) as? DeckFrame ?: continue
            if(a.owner===this && b.owner===this && a.generation==geometryGeneration && b.generation==geometryGeneration) pairs[task]=a to b
        }
    }
    fun applyInterpolation(view: View,out: Any) {
        if(!allowed) return
        val fraction=interpolation ?: return
        val pair=pairs[api.getTask.invoke(view)] ?: return
        val (a,b)=pair
        // Native LTRB already interpolates the two real endpoints. The bounded
        // Deck tail preserves an offscreen card's exit/entry side, so newly
        // visible neighbors must follow that same progress rather than jump in.
        putFrame(out,b.index,a.scale+(b.scale-a.scale)*fraction,a.visible||b.visible,a.z+(b.z-a.z)*fraction,b.depthScale)
    }
    fun resetInterpolation() { pairs.clear() }
    fun cardAt(index: Int): Card? = cards.values.firstOrNull { it.index==index }
    fun endpointScale(index: Int): Float? = frozen[index]?.scale
    fun alignOpening(selected: Card,start: RectF,current: RectF,progress: Float,headerAlpha: Float) {
        if(!allowed || transition==0L || start.isEmpty) return
        var alignment=opening
        if(alignment==null || alignment.selectedId!=selected.taskId) {
            clearOpening()
            alignment=DeckOpenAlignment(selected.taskId,RectF(start))
            val bounds=RectF()
            for(card in cards.values) if(card!==selected && card.geometry.owned) {
                card.geometry.screenContent(bounds,(api.getHeader.invoke(card.view) as View).height.toFloat())
                alignment.add(card.view,api.getHeader.invoke(card.view) as View,bounds,selected.depth.index-card.depth.index)
            }
            opening=alignment
            openingIndex=selected.depth.index
        }
        alignment.apply(current,progress,headerAlpha)
    }
    fun openingBoundary(surface: RectF): Float? {
        if(opening==null) return null
        var edge=Float.POSITIVE_INFINITY
        for(card in cards.values) if(card.depth.index<openingIndex && card.geometry.owned &&
            card.view.visibility==View.VISIBLE && card.view.alpha>.01f) {
            card.geometry.screenContent(occludingBounds,0f)
            if(RectF.intersects(surface,occludingBounds)) edge=minOf(edge,occludingBounds.left)
        }
        return edge.takeIf { it.isFinite() }
    }
    private fun clearOpening() {
        val previous=opening; opening=null; openingIndex=-1; previous?.release()
    }
    fun beginTransition(force: Boolean=false): Long {
        if(closed || !baseAllowed || !tasksAllowed || suspended || !ValueAnimator.areAnimatorsEnabled()) return 0L
        // Display rotation can arrive before this View's configuration/layout.
        // Do not hand a cached viewport to a new native window request.
        if(!geometryReady || !orientationReady(stack.resources.configuration)) {
            if(geometryReady || transition!=0L) retireGeometry()
            return 0L
        }
        if(!force && transition!=0L) return transition
        val cleanup=CleanupActions()
        for(link in linkedSprings.toList()) cleanup.run { releaseSpring(link) }
        linkedSprings.clear()
        cleanup.run { clearOpening() }; cleanup.run { router.cancel() }; cleanup.finish()
        transition=++sequence; frozen.clear()
        observeDepth()
        stack.removeCallbacks(ticketTimeout); stack.postDelayed(ticketTimeout,transitionTimeout(2000L))
        return transition
    }
    fun linkSpring(spring: Any?,ticket: Long) {
        if(ticket==0L || ticket!=transition) return
        if(spring==null) { endTransition(ticket); return }
        if(linkedSprings.any { it.spring.get()===spring && it.ticket==ticket }) return
        val iterator=linkedSprings.iterator()
        while(iterator.hasNext()) {
            val old=iterator.next()
            if(old.spring.get()==null || old.spring.get()===spring) { releaseSpring(old); iterator.remove() }
        }
        val link=LinkedSpring(spring,ticket)
        // Open springs are returned before start. Keep the finite pending ticket
        // until the native start callback, then let native end/cancel release it.
        link.listener=DeckSpringEndListener(
            { onUi { springStarted(ticket) } },
            { onUi { if(!closed && linkedSprings.remove(link)) {
                val cleanup=CleanupActions(); cleanup.run { releaseSpring(link) }; cleanup.run { endTransition(ticket) }; cleanup.finish() } } })
        linkedSprings.add(link)
        api.listen.invoke(spring,link.listener)
        if(api.running.invoke(spring) as Boolean) springStarted(ticket)
    }
    private fun onUi(block: ()->Unit) {
        if(Looper.myLooper()==callbacks.looper) ModuleHelper.guarded(block)
        else callbacks.post { ModuleHelper.guarded(block) }
    }
    private fun releaseSpring(link: LinkedSpring) {
        link.spring.get()?.let { spring ->
            try { api.unlisten.invoke(spring,link.listener) }
            finally {
                val cleanup=CleanupActions()
                cleanup.run { DeckMotion.clear(spring,this,link.ticket) }
                cleanup.run { DeckModernMotion.clear(spring,this,link.ticket) }
                cleanup.finish()
            }
        }
    }
    private fun transitionTimeout(base: Long): Long =
        (base * ValueAnimator.getDurationScale().coerceAtLeast(1f).toDouble()).toLong()
    private fun springStarted(ticket: Long) {
        if(ticket==transition && ticket!=0L) {
            stack.removeCallbacks(ticketTimeout)
            // Native end/cancel normally owns completion. A missing callback
            // still cannot retain the presentation owner indefinitely.
            stack.postDelayed(ticketTimeout,transitionTimeout(10000L))
        }
    }
    fun endTransition(ticket: Long) {
        if(ticket==0L || ticket!=transition) return
        transition=0; frozen.clear(); stack.removeCallbacks(ticketTimeout)
        val cleanup=CleanupActions()
        val iterator=linkedSprings.iterator()
        while(iterator.hasNext()) {
            val link=iterator.next()
            if(link.ticket==ticket) { iterator.remove(); cleanup.run { releaseSpring(link) } }
        }
        cleanup.run { clearOpening() }
        cleanup.run { if(!allowed) restoreCards() }
        cleanup.finish()
    }
    fun suspend(value: Boolean) {
        suspended=value
        val cleanup=CleanupActions()
        if(value) {
            transition=0; frozen.clear(); stack.removeCallbacks(ticketTimeout)
            for(link in linkedSprings.toList()) cleanup.run { releaseSpring(link) }
            linkedSprings.clear()
            cleanup.run { restoreCards() }
        }
        cleanup.run { refresh() }
        cleanup.finish()
    }
    private fun restoreCards() {
        dragging=null
        drawCards.clear()
        if(cards.isEmpty() && opening==null && depthObserver==null) { router.cancel(); return }
        val cleanup=CleanupActions()
        cleanup.run { clearOpening() }
        cleanup.run { stopObservingDepth() }
        cleanup.run { router.cancel() }
        val previous=suspended; suspended=true
        try {
            val iterator=cards.values.iterator()
            while(iterator.hasNext()) {
                val card=iterator.next(); iterator.remove()
                cleanup.run { card.release() }
                cleanup.run { XposedHelpers.removeAdditionalInstanceField(card.view,CARD) }
                cleanup.run {
                    // A pooled view may already represent a different task.
                    if(api.getTask.invoke(card.view)===card.task) {
                        api.taskTransform.invoke(current.algorithm,card.task,scroll,restoreTransform)
                        api.updateView.invoke(stack,card.view,restoreTransform,api.immediate)
                    }
                }
            }
        } finally { suspended=previous }
        cleanup.finish()
    }
    fun close() {
        if(closed) return
        closed=true; transition=0; flingVelocity=0; pairs.clear(); frozen.clear()
        val cleanup=CleanupActions()
        cleanup.run { stack.removeCallbacks(ticketTimeout) }
        for(link in linkedSprings.toList()) cleanup.run { releaseSpring(link) }
        linkedSprings.clear()
        cleanup.run { restoreCards() }
        cleanup.run { stack.removeOnLayoutChangeListener(this) }
        cleanup.run { accessibility?.removeTouchExplorationStateChangeListener(accessibilityChanged) }
        for(value in listOf(stack,handler,scroller)) cleanup.run {
            if(XposedHelpers.getAdditionalInstanceField(value,OWNER)===this)
                XposedHelpers.removeAdditionalInstanceField(value,OWNER)
        }
        for(scope in listOf(current,stable)) cleanup.run {
            if(XposedHelpers.getAdditionalInstanceField(scope.algorithm,SCOPE)===scope)
                XposedHelpers.removeAdditionalInstanceField(scope.algorithm,SCOPE)
        }
        cleanup.finish()
    }
    override fun onLayoutChange(v: View,l: Int,t: Int,r: Int,b: Int,ol: Int,ot: Int,or: Int,ob: Int) = ModuleHelper.guarded {
        if(r-l!=or-ol || b-t!=ob-ot || !baseAllowed || !geometryReady) refresh(false)
    }
}
