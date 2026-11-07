package tv.withaibuild.customiuizer.mods.recents

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/** Opt-in coherent Deck slice. Native task identity, pool, gesture business,
 * physical scroller, window springs, Surface updates and finish callbacks stay native. */
internal object IosRecentsDeckHooks {
    const val KEY="system_recents_ios18_deck"
    private const val WINDOW_RECEIPT="a14.recents.deck.window.receipt"
    private const val WINDOW_PENDING="a14.recents.deck.window.pending"
    internal class WindowReceipt(owner: DeckOwner,val ticket: Long) {
        val owner=WeakReference(owner)
        private val generation=owner.geometryGeneration
        fun matches(active: DeckOwner)=owner.get()===active && active.allowed &&
            active.transition==ticket && ticket!=0L && active.geometryGeneration==generation
    }

    fun install(param: PackageReadyParam,prefs: PrefMap): FeatureInstallResult {
        val entries=ArrayList<Pair<Method,MethodHook>>()
        // Select the native consumer family, then require its entire cold ABI.
        // A version number neither admits an incompatible ABI nor rejects a match.
        val profile=DeckLauncherProfile.resolve(param.classLoader)
        val api=DeckNativeApi(param.classLoader,profile)
        val headerConfig=DeckHeaderConfig.read(prefs)
        val motion=if(profile==DeckLauncherProfile.LEGACY && prefs.getBoolean(DeckMotion.KEY,true))
            optionalMotion { DeckMotion(api) } else null
        val modernMotion=if(profile==DeckLauncherProfile.WINDOW_ELEMENT && prefs.getBoolean(DeckMotion.KEY,true))
            optionalMotion { DeckModernMotion(api.spring) } else null
        val radius=if(profile==DeckLauncherProfile.LEGACY) DeckRadiusHandoff() else null
        // Only a native WindowElement update can admit a Surface correction.
        // Reuse a bounded stack on its UI thread, including nested native calls;
        // afterAlways drops every owner reference on success and failure.
        val surfaces=ThreadLocal<ArrayList<DeckOwner?>>()
        var latest=WeakReference<DeckOwner>(null)
        fun owner(value: Any?)=value?.let { XposedHelpers.getAdditionalInstanceField(it,DeckOwner.OWNER) as? DeckOwner }
        fun frame(value: Any?)=value?.let { XposedHelpers.getAdditionalInstanceField(it,DeckOwner.FRAME) as? DeckFrame }
            ?.takeIf { !it.owner.closed && it.generation==it.owner.geometryGeneration }
        fun card(value: Any?)=value?.let { XposedHelpers.getAdditionalInstanceField(it,DeckOwner.CARD) as? DeckOwner.Card }
        fun bind(view: ViewGroup) {
            if(owner(view)!=null || !api.horizontal.isInstance(api.current.get(view))) return
            latest=WeakReference(DeckOwner(view,api,headerConfig,radius))
        }
        fun after(method: Method,block: (AfterHookCallback)->Unit) = entries.add(method to object:MethodHook() {
            override fun after(callback: AfterHookCallback)=ModuleHelper.guarded {
                if(callback.getThrowable()==null) block(callback)
            }
        })
        fun before(method: Method,block: (BeforeHookCallback)->Unit) = entries.add(method to object:MethodHook() {
            override fun before(callback: BeforeHookCallback)=ModuleHelper.guarded { block(callback) }
        })
        fun afterAlways(method: Method,block: (AfterHookCallback)->Unit) = entries.add(method to object:MethodHook() {
            override fun after(callback: AfterHookCallback)=ModuleHelper.guarded { block(callback) }
        })
        // Use the native window-rotation path consistently: RotationHelper must
        // not lock the Activity while RecentsContainer rotates only its content.
        // Keep this in the same atomic batch, before any Launcher is created.
        entries.add(api.keepRecentsPortrait to HookerClassHelper.returnConstant(false))
        if(motion!=null) {
            before(motion.configure) { motion.beforeConfigure(it.getThisObject()!!) }
            afterAlways(motion.configure) { motion.afterConfigure(it.getThisObject()!!,
                (it.getArg(0) as? Enum<*>)?.name,it.getThrowable()!=null) }
            before(motion.initialize) { motion.beforeInit(it.getThisObject()!!) }
            afterAlways(motion.initialize) { if(it.getThrowable()!=null) motion.failedInit(it.getThisObject()!!) }
        }
        if(modernMotion!=null) {
            before(modernMotion.configure) { modernMotion.beforeConfigure(it.getThisObject()!!) }
            afterAlways(modernMotion.configure) { modernMotion.afterConfigure(it.getThisObject()!!,
                (it.getArg(0) as? Enum<*>)?.name,it.getThrowable()!=null) }
        }
        api.springReset?.let { reset -> before(reset) {
            val active=latest.get() ?: return@before
            if(!active.allowed || radius==null) return@before
            val original=it.getArg(3) as Float
            val corrected=radius.corrected(active,active.transition,it.getThisObject()!!,it.getArg(1) as RectF,original)
            if(corrected!=original) it.getArgs()[3]=corrected
        } }
        before(api.layout) { owner(it.getThisObject())?.close() }
        after(api.layout) { bind(it.getThisObject() as ViewGroup) }
        after(api.attached) { bind(it.getThisObject() as ViewGroup); owner(it.getThisObject())?.refresh() }
        before(api.detached) { owner(it.getThisObject())?.close() }
        before(api.configuration) { owner(it.getThisObject())?.close() }
        after(api.configuration) { bind(it.getThisObject() as ViewGroup) }
        before(api.displayRotation) {
            val active=latest.get() ?: return@before
            if(active.stack.rootView===(it.getThisObject() as View).rootView)
                active.rotationChanged(it.getArg(0) as Int)
        }
        before(api.update) {
            (XposedHelpers.getAdditionalInstanceField(it.getThisObject(),DeckOwner.SCOPE) as? DeckOwner.Scope)?.owner?.invalidateLayout()
        }
        after(api.update) {
            (XposedHelpers.getAdditionalInstanceField(it.getThisObject(),DeckOwner.SCOPE) as? DeckOwner.Scope)?.let { scope ->
                scope.owner.refreshGeometry()
                scope.owner.tasksChanged()
            }
        }
        before(api.fling) { owner(it.getThisObject())?.let { active ->
            active.flingVelocity=if(active.allowed) it.getArg(5) as Int else 0
        } }
        afterAlways(api.fling) { owner(it.getThisObject())?.flingVelocity=0 }
        before(api.nearest) { owner(it.getThisObject())?.snap(it.getArg(0) as Float)?.let(it::returnAndSkip) }
        before(api.bounds) {
            val scope=XposedHelpers.getAdditionalInstanceField(it.getThisObject(),DeckOwner.SCOPE) as? DeckOwner.Scope
            if(scope?.write(it.getArg(0) as Int,it.getArg(1) as Float,it.getArg(2)!!)==true) it.returnAndSkip(null)
        }
        before(api.minimumScroll) {
            val scope=XposedHelpers.getAdditionalInstanceField(it.getThisObject(),DeckOwner.SCOPE) as? DeckOwner.Scope
            if(scope?.owner?.allowed==true) it.returnAndSkip(-((scope.count-1).coerceAtLeast(0))*scope.gap)
        }
        after(api.content) {
            val scope=XposedHelpers.getAdditionalInstanceField(it.getThisObject(),DeckOwner.SCOPE) as? DeckOwner.Scope
            val result=it.getResult() as? RectF
            if(scope!=null && result!=null) scope.content(it.getArg(0) as Int,it.getArg(1) as Float,result)
        }
        after(api.copy) {
            DeckFrame.copy(it.getArg(0)!!,it.getThisObject()!!)
        }
        after(api.reset) { XposedHelpers.removeAdditionalInstanceField(it.getThisObject(),DeckOwner.FRAME) }
        after(api.fill) {
            val card=card(it.getArg(0)) ?: return@after
            card.owner.putFrame(it.getThisObject()!!,card.index,card.geometry.scale,true,1f/(card.index+1),card.depthScale)
        }
        before(api.updateView) {
            owner(it.getThisObject())?.applyInterpolation(it.getArg(0) as View,it.getArg(1)!!)
        }
        before(api.apply) {
            if(DeckFrame.isRetired(it.getThisObject()!!)) { it.returnAndSkip(null); return@before }
            val frame=frame(it.getThisObject()) ?: return@before
            frame.owner.card(it.getArg(0) as View,frame) ?: return@before
            // Native ObjectAnimator Z would keep overwriting the owned order
            // after applyToTaskView returns. LTRB/timing/business remain native.
            it.getArgs()[3]=false
        }
        afterAlways(api.apply) {
            if(it.getThrowable()!=null) {
                card(it.getArg(0))?.owner?.remove(it.getArg(0) as View)
                return@afterAlways
            }
            val frame=frame(it.getThisObject()) ?: return@afterAlways
            val view=it.getArg(0) as View
            val card=frame.owner.card(view,frame) ?: return@afterAlways
            card.depth.apply()
            card.animation?.cancel(); card.animation=null
            val timing=it.getArg(2)!!
            if(api.isImmediate.invoke(timing) as Boolean || card.geometry.scale==frame.scale) {
                if(!card.geometry.apply(frame.scale)) frame.owner.suspend(true)
            } else {
                val target=frame.scale
                val animation=ValueAnimator.ofFloat(card.geometry.scale,target).apply {
                    addUpdateListener { value -> ModuleHelper.guarded {
                        if(card.animation===value && !frame.owner.closed && card.geometry.owned && !card.geometry.apply(value.animatedValue as Float))
                            frame.owner.suspend(true)
                    } }
                }
                card.animation=animation
                @Suppress("UNCHECKED_CAST")
                (it.getArg(1) as MutableList<Animator>).add(api.applyTiming.invoke(timing,6,animation) as Animator)
            }
        }
        before(api.poolReturn) { owner(it.getThisObject())?.remove(it.getArg(0) as View) }
        before(api.poolPickup) { owner(it.getThisObject())?.remove(it.getArg(0) as View) }
        before(api.dragEnd) {
            card(it.getArg(0))?.geometry?.releaseSwipe()
            owner(it.getThisObject())?.resetInterpolation()
        }
        before(api.interpolate) { owner(it.getThisObject())?.interpolate(it.getArg(0) as Float) }
        afterAlways(api.interpolate) { owner(it.getThisObject())?.interpolate(null) }
        before(api.prepareSnap) { card(it.getArg(0))?.geometry?.releaseSwipe() }
        before(api.snapChild) { card(it.getArg(0))?.geometry?.snapSwipe() }
        after(api.childSnapped) {
            card(it.getArg(0))?.let { selected -> selected.owner.finishSwipe(selected) }
            owner(it.getThisObject())?.reapplyDepth()
        }
        // calculate -> set native pose -> onMoveUpdate is synchronous on Main.
        // Consume the native raw delta without another touch listener or field query.
        val swipeInput=DeckSwipeInput()
        before(api.swipeTouch) {
            val helper=it.getThisObject()!!
            swipeInput.begin(helper,api.verticalSwipe.get(helper))
        }
        afterAlways(api.swipeTouch) { swipeInput.end() }
        before(api.verticalCalculate) { swipeInput.calculate(it.getThisObject()!!,it.getArg(0) as Float) }
        after(api.swipeMove) {
            val raw=swipeInput.consume(it.getThisObject()!!)
            val active=card(it.getArg(0)) ?: return@after
            if(active.owner.allowed && raw.isFinite()) active.owner.trackSwipe(active,raw,it.getArg(2) as Float)
        }
        after(api.dismissAnimator) {
            val view=it.getArg(0) as View
            val selected=card(view) ?: return@after
            val active=selected.owner
            if(active.allowed && DeckDismissMotion.admitted(api.swipeDirection.getInt(it.getThisObject()),it.getArg(1) as Float)) {
                selected.geometry.commitSwipe()
                val native=it.getResult() as android.animation.ObjectAnimator
                it.setResult(DeckDismissMotion.upward(selected.geometry,native))
            }
        }
        after(api.navVisible) {
            for(value in it.getArg(0) as List<*>) frame(value)?.let { f ->
                if(f.owner.allowed) api.visible.setBoolean(value,f.visible || f.owner.transition!=0L && api.visible.getBoolean(value))
            }
        }
        after(api.intercept) {
            val active=owner(it.getThisObject()) ?: return@after
            val event=it.getArg(0) as MotionEvent
            if(active.allowed && event.actionMasked==MotionEvent.ACTION_DOWN) {
                active.router.begin(event,it.getResult()==true,active.hit(event.x,event.y)?.geometry)
                it.setResult(true)
            }
        }
        before(api.onTouch) {
            owner(it.getThisObject())?.router?.touch(it.getArg(0) as MotionEvent)?.let(it::returnAndSkip)
        }
        before(api.find) {
            val active=owner(it.getThisObject()) ?: return@before
            if(active.allowed) it.returnAndSkip(active.hit(it.getArg(0) as Float,it.getArg(1) as Float)?.view)
        }
        before(api.hit) {
            val card=card(it.getArg(2)) ?: return@before
            if(card.owner.allowed) it.returnAndSkip(!(api.ignored.invoke(card.owner.stack,card.task) as Boolean) &&
                card.geometry.contains(it.getArg(0) as Float,it.getArg(1) as Float))
        }
        before(api.click) {
            if(it.getArg(0)===it.getThisObject()) card(it.getThisObject())?.owner?.takeIf { value -> value.allowed }?.beginTransition(true)
        }
        after(api.childrenAlpha) { card(it.getThisObject())?.let { selected ->
            selected.shadow.syncContentAlpha()
            selected.header.refreshOpacity(true)
        } }
        after(api.headerTransform) { card(it.getThisObject())?.header?.refreshOpacity(true) }
        after(api.setThumbnailRadius) {
            val thumbnail=it.getThisObject() as View
            card(thumbnail.parent)?.shadow?.radius((it.getArg(0) as Int).toFloat())
        }
        before(api.openStart) { card(it.getArg(2))?.owner?.beginTransition(true) }
        after(api.openStart) {
            val card=card(it.getArg(2)) ?: return@after
            val result=it.getResult() as? RectF ?: return@after
            if(card.owner.allowed) card.geometry.screenContent(result,(api.getHeader.invoke(card.view) as View).height.toFloat())
        }
        before(api.createSpring) {
            val card=card(it.getArg(1)) ?: return@before
            if(card.owner.allowed) it.getArgs()[7]=(it.getArg(7) as Float)*card.geometry.scale*card.view.scaleX
        }
        after(api.createSpring) {
            val active=card(it.getArg(1))?.owner ?: return@after
            motion?.mark(it.getResult(),active,DeckSpringScene.OPEN)
            active.linkSpring(it.getResult(),active.transition)
        }
        before(api.alignOpen) {
            val selected=card(it.getArg(0)) ?: return@before
            if(selected.owner.allowed) {
                if(surfaces.get()?.lastOrNull()!==selected.owner)
                    selected.owner.alignOpening(selected,it.getArg(1) as RectF,it.getArg(2) as RectF,it.getArg(3) as Float,it.getArg(4) as Float)
                it.returnAndSkip(null)
            }
        }
        before(api.alignClose) {
            val selected=card(it.getArg(0)) ?: return@before
            if(selected.owner.allowed) {
                selected.owner.alignClosing(selected,it.getArg(1) as RectF,it.getArg(2) as RectF,
                    it.getArg(4) as Float)
                it.returnAndSkip(null)
            }
        }
        api.closing?.let { closing ->
        before(closing) {
            val active=latest.get()
            try { active?.beginTransition(true) }
            finally { radius?.enter(active,active?.transition ?: 0L); motion?.enter(active,DeckSpringScene.TOGGLE) }
        }
        afterAlways(closing) {
            try { if(it.getThrowable()==null) latest.get()?.let { active ->
                motion?.mark(it.getResult(),active,DeckSpringScene.TOGGLE)
                active.linkSpring(it.getResult(),active.transition)
            } } finally { motion?.leave(); radius?.leave() }
        }
        }
        api.windowElement?.let { window ->
            before(window.update) {
                val scope=surfaces.get() ?: ArrayList<DeckOwner?>(2).also(surfaces::set)
                scope.add(null)
                val element=it.getThisObject()!!
                // startInMainThread can synchronously draw before animTo stores
                // currentRectFParams. That first frame belongs to the new request.
                val params=XposedHelpers.getAdditionalInstanceField(element,WINDOW_PENDING)
                    ?: window.current(element) ?: return@before
                if(window.scene(params)!=true) return@before
                val selected=card(window.view(params)) ?: return@before
                val active=selected.owner
                val receipt=XposedHelpers.getAdditionalInstanceField(params,WINDOW_RECEIPT) as? WindowReceipt ?: return@before
                if(!receipt.matches(active)) return@before
                val progress=it.getArg(3) as Float
                active.alignOpening(selected,window.start(it.getThisObject()!!),it.getArg(6) as RectF,progress,
                    ((.4f-progress)/.4f).coerceIn(0f,1f))
                scope[scope.lastIndex]=active
            }
            afterAlways(window.update) { surfaces.get()?.let { scope -> if(scope.isNotEmpty()) scope.removeAt(scope.lastIndex) } }
            api.surfaceOcclusion?.let { surface -> after(surface.calculate) {
                val active=surfaces.get()?.lastOrNull() ?: return@after
                val result=it.getResult() ?: return@after
                val clipped=surface.apply(active,it.getThisObject()!!,it.getArg(0)!!,it.getArg(4) as Int,result)
                if(clipped!==result) it.setResult(clipped)
            } }
            before(window.closing) { latest.get()?.beginTransition(true) }
            afterAlways(window.closing) {
                if(it.getThrowable()!=null || it.getResult()==null) latest.get()?.let { active -> active.endTransition(active.transition) }
            }
            before(window.animate) {
                val original=it.getArg(0) ?: return@before
                XposedHelpers.setAdditionalInstanceField(it.getThisObject(),WINDOW_PENDING,original)
                val opening=window.scene(original) ?: return@before
                val target=window.view(original)
                val selected=(if(target!=null) card(target) else latest.get()?.cardAt(window.index(original))) ?: return@before
                val active=selected.owner
                if(!active.allowed || !selected.geometry.owned) return@before
                val ticket=active.beginTransition()
                if(ticket==0L) return@before
                val scale=if(opening) selected.geometry.scale else active.endpointScale(window.index(original)) ?: selected.geometry.scale
                val copy=window.scaled(original,scale,opening,selected.view.scaleX)
                XposedHelpers.setAdditionalInstanceField(copy,WINDOW_RECEIPT,WindowReceipt(active,ticket))
                if(modernMotion!=null) window.spring(it.getThisObject()!!)?.let { spring -> modernMotion.mark(spring,active,ticket,
                    if(opening) DeckSpringScene.OPEN else modernMotion.closingScene()) }
                XposedHelpers.setAdditionalInstanceField(it.getThisObject(),WINDOW_PENDING,copy)
                it.getArgs()[0]=copy
            }
            afterAlways(window.animate) {
                XposedHelpers.removeAdditionalInstanceField(it.getThisObject(),WINDOW_PENDING)
                val params=it.getArg(0) ?: return@afterAlways
                // Keep the weak receipt with native currentRectFParams: later
                // frames must still belong to this owner/ticket/orientation.
                val receipt=XposedHelpers.getAdditionalInstanceField(params,WINDOW_RECEIPT) as? WindowReceipt ?: return@afterAlways
                val active=receipt.owner.get() ?: return@afterAlways
                if(it.getThrowable()==null && receipt.matches(active) && window.accepted(params,it.getThisObject()!!))
                    active.linkSpring(window.spring(it.getThisObject()!!),receipt.ticket)
                else {
                    XposedHelpers.removeAdditionalInstanceField(params,WINDOW_RECEIPT)
                    window.spring(it.getThisObject()!!)?.let { spring -> DeckModernMotion.clear(spring,active,receipt.ticket) }
                    active.endTransition(receipt.ticket)
                }
            }
        }
        api.appGesturePose?.let { pose -> before(pose) {
            if(it.getArg(13)==true) return@before
            val selected=card(it.getArg(0)) ?: return@before
            val bend=it.getArg(15) as Float
            selected.owner.appGestureInput(selected,it.getArg(2) as RectF,it.getArg(3) as Int,
                it.getArg(5) as Float,300f*bend*bend*bend)?.let { input ->
                val args=it.getArgs()
                args[2]=input
                // This is already the native window's sampled pose. A second
                // per-card spring would chase it with another phase lag. Keep
                // calls carrying native completion work on their original path.
                if(args[10]!=null) selected.nativePoseCompletion=true
                if(!selected.nativePoseCompletion) args[7]=false
            }
        } }
        before(api.navToRecents) {
            modernMotion?.enterGesture()
            val active=latest.get()
            try { active?.beginTransition(true) }
            finally {
                val spring=api.navSpring?.get(it.getThisObject())
                radius?.enter(active,active?.transition ?: 0L,spring)
                motion?.enter(active,DeckSpringScene.GESTURE,spring)
            }
        }
        afterAlways(api.navToRecents) {
            try { if(it.getThrowable()==null && api.navSpring!=null) latest.get()?.let { active ->
                val spring=api.navSpring.get(it.getThisObject())
                motion?.mark(spring,active,DeckSpringScene.GESTURE)
                active.linkSpring(spring,active.transition)
            } } finally { modernMotion?.leaveGesture(); motion?.leave(); radius?.leave() }
        }
        // Native entry already reloads the stack and requests its layout in the
        // same call. Refresh admission before that traversal without requesting
        // another layout on repeated state notifications or on exit.
        after(api.setOverview) { owner(api.recentsStack.get(it.getThisObject()))?.refresh(false) }
        before(api.splitStart) { latest.get()?.suspend(true) }
        after(api.splitEnd) { latest.get()?.suspend(false) }
        return PreparedHookBatch().install(entries)
    }

    internal fun <T> optionalMotion(resolve: () -> T): T? = try { resolve() }
    catch (error: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(error)
        XposedHelpers.log("Deck: native window timing retained; optional motion ABI unavailable", error)
        null
    }
}
