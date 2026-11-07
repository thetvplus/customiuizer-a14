package tv.withaibuild.customiuizer.mods.recents

import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.ArrayDeque

/** Cold scene scope; no frame callback, scheduler, velocity rewrite or second
 * spring engine. Native Surface updates, cancellation and finish remain native. */
internal class DeckMotion(private val api: DeckNativeApi) {
    companion object {
        const val KEY="system_recents_ios18_deck_motion"
        private const val PROFILE="a14.recents.deck.spring.profile"
        private const val SAVED="a14.recents.deck.spring.saved"
        fun clear(spring: Any,owner: DeckOwner,ticket: Long) {
            val profile=XposedHelpers.getAdditionalInstanceField(spring,PROFILE) as? Profile ?: return
            if(profile.owner===owner && profile.ticket==ticket) {
                restore(spring); XposedHelpers.removeAdditionalInstanceField(spring,PROFILE)
            }
        }
        private fun restore(spring: Any) {
            val saved=XposedHelpers.removeAdditionalInstanceField(spring,SAVED) as? Saved ?: return
            saved.parameters.restore(spring,saved.snapshot)
        }
    }
    private class Profile(val owner: DeckOwner?,val ticket: Long,val scene: DeckSpringScene,val expected: Any?)
    private class Saved(val parameters: DeckSpringParameters,val snapshot: DeckSpringParameters.Snapshot)
    private val contexts=ThreadLocal<ArrayDeque<Profile>>()
    val initialize=api.exact(api.spring,"initAllAnimations",Void.TYPE)
    val configure=api.exact(api.spring,"setAnimParamByType",Void.TYPE,
        XposedHelpers.findClass("com.miui.home.recents.util.RectFSpringAnim\$AnimType",api.spring.classLoader))
    private val parameters=DeckSpringParameters(api.spring)
    private val durationClass=XposedHelpers.findClass("com.miui.home.recents.TransitionAnimDurationHelper",api.spring.classLoader)
    private val durationInstance=api.staticExact(durationClass,"getInstance",durationClass)
    private val durationRatio=api.exact(durationClass,"getAnimDurationRatio",Float::class.javaPrimitiveType!!)

    fun enter(owner: DeckOwner?,scene: DeckSpringScene,expected: Any?=null) {
        val stack=contexts.get() ?: ArrayDeque<Profile>().also(contexts::set)
        stack.push(Profile(owner,owner?.transition ?: 0L,scene,expected))
    }
    fun leave() { contexts.get()?.let { it.poll(); if(it.isEmpty()) contexts.remove() } }
    fun mark(spring: Any?,owner: DeckOwner,scene: DeckSpringScene) {
        if(spring!=null && owner.allowed && owner.transition!=0L)
            XposedHelpers.setAdditionalInstanceField(spring,PROFILE,Profile(owner,owner.transition,scene,spring))
    }
    fun beforeInit(spring: Any) {
        val context=contexts.get()?.peek()
        val tagged=XposedHelpers.getAdditionalInstanceField(spring,PROFILE) as? Profile
        val profile=if(valid(context) && context!!.expected===spring) context
            else if(valid(tagged) && tagged!!.expected===spring) tagged else null
        restore(spring)
        if(profile==null) { XposedHelpers.removeAdditionalInstanceField(spring,PROFILE); return }
        val ratio=(durationRatio.invoke(durationInstance.invoke(null)) as Number).toFloat()
        if(!ratio.isFinite() || ratio<=0f) return
        val saved=parameters.apply(spring,profile.scene)
        XposedHelpers.setAdditionalInstanceField(spring,SAVED,Saved(parameters,saved))
        XposedHelpers.setAdditionalInstanceField(spring,PROFILE,profile)
    }
    fun failedInit(spring: Any) {
        restore(spring); XposedHelpers.removeAdditionalInstanceField(spring,PROFILE)
    }
    fun beforeConfigure(spring: Any) = restore(spring)
    fun afterConfigure(spring: Any,nativeType: String?,failed: Boolean) {
        val context=contexts.get()?.peek()
        val tagged=XposedHelpers.getAdditionalInstanceField(spring,PROFILE) as? Profile
        val profile=if(valid(context) && (context!!.expected==null || context.expected===spring)) context else tagged
        val expected=if(profile?.scene==DeckSpringScene.OPEN) "OPEN_FROM_RECENTS" else "CLOSE_TO_RECENTS"
        if(failed || !valid(profile) || nativeType!=expected) {
            XposedHelpers.removeAdditionalInstanceField(spring,PROFILE)
        } else {
            // The actual native setter identifies the button-created spring,
            // including older hosts without getLastAminType().
            XposedHelpers.setAdditionalInstanceField(spring,PROFILE,
                Profile(profile!!.owner,profile.ticket,profile.scene,spring))
        }
    }
    private fun valid(profile: Profile?): Boolean {
        val owner=profile?.owner ?: return false
        return profile.ticket!=0L && profile.ticket==owner.transition && owner.allowed
    }
}
