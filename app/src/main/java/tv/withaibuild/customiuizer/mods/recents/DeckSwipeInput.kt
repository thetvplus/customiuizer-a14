package tv.withaibuild.customiuizer.mods.recents

/** Main-thread native onTouchEvent scope. Unconsumed, failed and nested calls
 * cannot lend a calculation to another helper or a later MOVE. No event allocation. */
internal class DeckSwipeInput {
    private var depth=0
    private var helper: Any?=null
    private var vertical: Any?=null
    private var delta=Float.NaN
    fun begin(helper: Any,vertical: Any?) {
        depth++
        delta=Float.NaN
        if(depth==1) { this.helper=helper; this.vertical=vertical }
    }
    fun calculate(vertical: Any,delta: Float) {
        if(depth==1 && this.vertical===vertical) this.delta=delta
    }
    fun consume(helper: Any): Float {
        val result=if(depth==1 && this.helper===helper) delta else Float.NaN
        delta=Float.NaN
        return result
    }
    fun end() {
        delta=Float.NaN
        if(--depth==0) { helper=null; vertical=null }
    }
}
