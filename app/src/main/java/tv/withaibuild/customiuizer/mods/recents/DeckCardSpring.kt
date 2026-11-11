package tv.withaibuild.customiuizer.mods.recents

import android.view.View

/** Cold-bound native root springs. A coordinate change updates their existing
 * position and velocity; it neither cancels them nor starts another clock. */
internal class DeckCardSpring(task: Class<*>) {
    private val owner=task.getDeclaredMethod("getSpringAnimationImpl").apply { isAccessible=true }
    private val impl=owner.returnType
    private val x=impl.getDeclaredMethod("getTranslationXSpringAnim").apply { isAccessible=true }
    private val y=impl.getDeclaredMethod("getTranslationYSpringAnim").apply { isAccessible=true }
    private val sx=impl.getDeclaredMethod("getScaleXSpringAnim").apply { isAccessible=true }
    private val sy=impl.getDeclaredMethod("getScaleYSpringAnim").apply { isAccessible=true }
    private val dynamic=x.returnType.superclass!!
    private val all=impl.getDeclaredMethod("getAllSpringAnim").apply { isAccessible=true;require(List::class.java.isAssignableFrom(returnType)) }
    private val cancel=dynamic.getDeclaredMethod("cancel").apply { isAccessible=true }
    private val running=dynamic.getDeclaredField("mRunning").apply { isAccessible=true;require(type==Boolean::class.javaPrimitiveType) }
    private val value=dynamic.getDeclaredField("mValue").apply { isAccessible=true;require(type==Float::class.javaPrimitiveType) }
    private val velocity=dynamic.getDeclaredField("mVelocity").apply { isAccessible=true;require(type==Float::class.javaPrimitiveType) }
    init { require(y.returnType==x.returnType && sx.returnType==x.returnType && sy.returnType==x.returnType) }

    fun cancel(view: View) {
        val springs=all.invoke(owner.invoke(view)) as List<*>
        for(i in springs.indices) springs[i]?.let { if(running.getBoolean(it)) cancel.invoke(it) }
    }

    fun rebase(view: View,scaleX: Float,scaleY: Float,translation: Float,crossX: Float,crossY: Float) {
        val springs=owner.invoke(view)
        val scaleSpringX=sx.invoke(springs)!!;val scaleSpringY=sy.invoke(springs)!!
        val velocityX=if(running.getBoolean(scaleSpringX)) velocity.getFloat(scaleSpringX) else 0f
        val velocityY=if(running.getBoolean(scaleSpringY)) velocity.getFloat(scaleSpringY) else 0f
        update(x.invoke(springs)!!,view.translationX,translation,crossX*velocityX)
        update(y.invoke(springs)!!,view.translationY,translation,crossY*velocityY)
        update(scaleSpringX,view.scaleX,scaleX,0f)
        update(scaleSpringY,view.scaleY,scaleY,0f)
    }
    private fun update(spring: Any,position: Float,factor: Float,coupledVelocity: Float) {
        if(!running.getBoolean(spring)) return // Native start will read the View itself.
        value.setFloat(spring,position)
        velocity.setFloat(spring,velocity.getFloat(spring)*factor+coupledVelocity)
    }
}
