package miui.systemui.controlcenter.qs.tileview

import android.content.Context
import android.widget.FrameLayout
import miui.systemui.controlcenter.R

class QSTileItemView(context: Context) : FrameLayout(context) {
    @JvmField var containerHeight = 0
    var labelHeight = 0
        private set
    var appliedHeight = 0
        private set
    var expansions = 0
        private set
    val iconFrame = FrameLayout(context).apply { id = R.id.icon_frame }

    init { addView(iconFrame, LayoutParams(68, 68)) }

    fun `_$_findCachedViewById`(id: Int) = iconFrame.takeIf { it.id == id }

    private fun updateContainerHeight() {
        containerHeight = 85
        labelHeight = 22
    }

    private fun changeExpand() {
        expansions++
        appliedHeight = containerHeight
    }

    private fun updateSize() {
        changeExpand()
        iconFrame.layoutParams.width = 68
        iconFrame.layoutParams.height = 68
    }

    public override fun onFinishInflate() = super.onFinishInflate()
}
