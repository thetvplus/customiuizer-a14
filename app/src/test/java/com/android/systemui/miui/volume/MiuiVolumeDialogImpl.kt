package com.android.systemui.miui.volume

import android.animation.ObjectAnimator
import android.content.Context
import android.widget.FrameLayout
import android.widget.SeekBar

/** Native ABI fixture; unchanged values intentionally emit no progress callback on show. */
class MiuiVolumeDialogImpl(context: Context) {
    @JvmField val mDialogView = MiuiVolumeDialogView(context)
    val window = FrameLayout(context).apply { addView(mDialogView) }
    @JvmField val mColumns = arrayListOf(VolumeColumn(context, 3), VolumeColumn(context, 0))
    @JvmField var mActiveStream = 3
    @JvmField var mShowing = false
    fun showVolumeDialogH(reason: Int) { mShowing = true }
    fun dismissH(reason: Int) { mShowing = false }
    fun updateColumnH(column: VolumeColumn) { column.slider.progress = column.ss.level * 1000 }
    fun destroy() { mShowing = false }
    class StreamState { @JvmField var level = 5; @JvmField var levelMin = 0 }
    class VolumeColumn(context: Context, @JvmField val stream: Int) {
        @JvmField val ss = StreamState()
        @JvmField val slider = SeekBar(context).apply { max = 15000; progress = 5000 }
        @JvmField var anim: ObjectAnimator? = null
        @JvmField var animTargetProgress = 0
    }
    inner class VolumeSeekBarChangeListener(@JvmField val mColumn: VolumeColumn) {
        fun onProgressChanged(slider: SeekBar, progress: Int, fromUser: Boolean) { check(mShowing) }
        fun onStartTrackingTouch(slider: SeekBar) { check(mShowing) }
        fun onStopTrackingTouch(slider: SeekBar) = Unit
    }
}
