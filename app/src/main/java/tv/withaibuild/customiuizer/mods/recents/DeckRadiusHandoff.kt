package tv.withaibuild.customiuizer.mods.recents

import android.graphics.RectF
import java.util.ArrayDeque
import kotlin.math.abs

/** Legacy Surface radii are in source-window units. Capture the exact endpoint
 * scale in the same native closing call, before its spring reset/start. */
internal class DeckRadiusHandoff {
    private class Scope(val owner: Any?,val ticket: Long,val expected: Any?) {
        var width=0f; var height=0f; var scale=1f
    }
    private val scopes=ThreadLocal<ArrayDeque<Scope>>()
    fun enter(owner: Any?,ticket: Long,expected: Any?=null) {
        val stack=scopes.get() ?: ArrayDeque<Scope>().also(scopes::set)
        stack.push(Scope(owner,ticket,expected))
    }
    fun leave() { scopes.get()?.let { it.poll(); if(it.isEmpty()) scopes.remove() } }
    fun capture(owner: Any,ticket: Long,rect: RectF,scale: Float) {
        val current=scopes.get()?.peek() ?: return
        if(current.owner!==owner || current.ticket!=ticket || ticket==0L) return
        current.width=rect.width(); current.height=rect.height(); current.scale=scale
    }
    fun corrected(owner: Any,ticket: Long,spring: Any,target: RectF,radius: Float): Float {
        val current=scopes.get()?.peek() ?: return radius
        if(current.owner!==owner || current.ticket!=ticket || ticket==0L ||
            current.expected!=null && current.expected!==spring || radius<=0f || !radius.isFinite() ||
            !current.scale.isFinite() || current.scale<=0f || current.width<=0f || current.height<=0f ||
            !current.width.isFinite() || !current.height.isFinite() || !target.width().isFinite() || !target.height().isFinite()) return radius
        val aligned=abs(target.width()-current.width)<=2f && abs(target.height()-current.height)<=2f
        val rotated=abs(target.width()-current.height)<=2f && abs(target.height()-current.width)<=2f
        if(!aligned && !rotated) return radius
        return radius*current.scale
    }
}
