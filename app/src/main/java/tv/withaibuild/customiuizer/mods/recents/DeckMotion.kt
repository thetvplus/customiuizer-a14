package tv.withaibuild.customiuizer.mods.recents

import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.ArrayDeque

/** Confirmed 22G100 window scenes. Geometry and tracking use the sampled pose. */
internal enum class DeckSpringScene(val response: Float,val damping: Float,val nativeType: String) {
    OPEN(.336f,1f,"OPEN_FROM_RECENTS"),
    TOGGLE(.336f,1f,"CLOSE_TO_RECENTS"),
    GESTURE(.531f,.94f,"CLOSE_TO_RECENTS")
}

/** One iOS scene/lifetime owner across orientations and both Launcher families.
 * Native engines keep their velocities, Surface work, cancellation and finish. */
internal class DeckMotion private constructor(
    private val parameters: DeckSpringTuning,
    val configure: Method,
    val initialize: Method?,
    private val durationRatio: (() -> Float)?
) {
    companion object {
        const val KEY="system_recents_ios18_deck_motion"
        private const val PROFILE="a14.recents.deck.spring.profile"
        fun create(api: DeckNativeApi): DeckMotion {
            if(api.profile==DeckLauncherProfile.WINDOW_ELEMENT) return modern(api.spring)
            val duration=XposedHelpers.findClass("com.miui.home.recents.TransitionAnimDurationHelper",api.spring.classLoader)
            val instance=api.staticExact(duration,"getInstance",duration)
            val ratio=api.exact(duration,"getAnimDurationRatio",Float::class.javaPrimitiveType!!)
            return legacy(api.spring) { (ratio.invoke(instance.invoke(null)) as Number).toFloat() }
        }
        private fun configure(type: Class<*>)=type.getDeclaredMethod("setAnimParamByType",
            type.declaredClasses.single { it.simpleName=="AnimType" && it.isEnum }).apply {
            require(returnType==Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible=true
        }
        fun modern(type: Class<*>)=DeckMotion(DeckModernSpringParameters(type),configure(type),null,null)
        fun legacy(type: Class<*>,durationRatio: () -> Float)=DeckMotion(DeckSpringParameters(type),configure(type),
            type.getDeclaredMethod("initAllAnimations").apply {
                require(returnType==Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible=true
            },durationRatio)
        private fun profile(spring: Any)=XposedHelpers.getAdditionalInstanceField(spring,PROFILE) as? Profile
        fun clear(spring: Any,owner: DeckOwner,ticket: Long) {
            val profile=profile(spring) ?: return
            if(profile.owner===owner && profile.ticket==ticket) {
                try { profile.motion.restore(spring,profile) }
                finally { XposedHelpers.removeAdditionalInstanceField(spring,PROFILE) }
            }
        }
    }
    private class Profile(val motion: DeckMotion,val owner: DeckOwner?,val ticket: Long,
        val scene: DeckSpringScene,val expected: Any?) {
        var snapshot: DeckSpringTuning.Snapshot?=null
    }
    private val contexts=ThreadLocal<ArrayDeque<Profile>>()

    fun enter(owner: DeckOwner?,scene: DeckSpringScene,expected: Any?=null) {
        val stack=contexts.get() ?: ArrayDeque<Profile>().also(contexts::set)
        stack.push(Profile(this,owner,owner?.transition ?: 0L,scene,expected))
    }
    fun leave() { contexts.get()?.let { it.poll(); if(it.isEmpty()) contexts.remove() } }
    fun closingScene(owner: DeckOwner?): DeckSpringScene {
        val context=contexts.get()?.peek()
        // The synchronous closing provider may create a new window ticket.
        // Preserve its originating gesture only for that same live owner.
        return if(owner!=null && owner.allowed && context?.owner===owner && context.scene==DeckSpringScene.GESTURE)
            DeckSpringScene.GESTURE else DeckSpringScene.TOGGLE
    }
    fun mark(spring: Any?,owner: DeckOwner,scene: DeckSpringScene,ticket: Long=owner.transition) {
        if(spring==null || !owner.allowed || ticket==0L || ticket!=owner.transition) return
        val previous=profile(spring)
        if(previous?.motion===this && previous.owner===owner && previous.ticket==ticket && previous.scene==scene) return
        if(previous!=null) previous.motion.restore(spring,previous)
        XposedHelpers.setAdditionalInstanceField(spring,PROFILE,Profile(this,owner,ticket,scene,spring))
    }
    fun beforeInit(spring: Any) {
        val context=contexts.get()?.peek()
        val tagged=profile(spring)
        val active=if(valid(context) && context!!.expected===spring) context
            else if(valid(tagged) && tagged!!.expected===spring) tagged else null
        if(tagged!=null) tagged.motion.restore(spring,tagged)
        if(active==null) { XposedHelpers.removeAdditionalInstanceField(spring,PROFILE); return }
        val ratio=durationRatio?.invoke() ?: return
        if(!ratio.isFinite() || ratio<=0f) return
        apply(spring,active)
        XposedHelpers.setAdditionalInstanceField(spring,PROFILE,active)
    }
    fun failedInit(spring: Any) {
        profile(spring)?.let { it.motion.restore(spring,it) }
        XposedHelpers.removeAdditionalInstanceField(spring,PROFILE)
    }
    fun beforeConfigure(spring: Any) { profile(spring)?.let { it.motion.restore(spring,it) } }
    fun afterConfigure(spring: Any,nativeType: String?,failed: Boolean) {
        val context=contexts.get()?.peek()
        val tagged=profile(spring)
        val active=when {
            valid(context) && context!!.expected===spring -> context
            valid(tagged) && tagged!!.expected===spring -> tagged
            valid(context) && context!!.expected==null -> context
            else -> null
        }
        if(failed || !valid(active) || nativeType!=active?.scene?.nativeType) {
            XposedHelpers.removeAdditionalInstanceField(spring,PROFILE); return
        }
        val selected=if(active!!.expected===spring) active else Profile(this,active.owner,active.ticket,active.scene,spring)
        // Legacy applies before native initAllAnimations creates its forces;
        // WindowElement's setter configures the live SpringBundles directly.
        if(initialize==null) apply(spring,selected)
        XposedHelpers.setAdditionalInstanceField(spring,PROFILE,selected)
    }
    private fun apply(spring: Any,profile: Profile) {
        try { profile.snapshot=parameters.apply(spring,profile.scene) }
        catch(error: Throwable) {
            XposedHelpers.removeAdditionalInstanceField(spring,PROFILE)
            throw error
        }
    }
    private fun restore(spring: Any,profile: Profile) {
        val snapshot=profile.snapshot ?: return
        parameters.restore(spring,snapshot)
        profile.snapshot=null
    }
    private fun valid(profile: Profile?): Boolean {
        val owner=profile?.owner ?: return false
        return profile.motion===this && profile.ticket!=0L && profile.ticket==owner.transition && owner.allowed
    }
}
