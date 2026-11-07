package tv.withaibuild.customiuizer.mods.recents

import android.animation.ObjectAnimator
import android.view.View
import android.graphics.RectF

/** Replace only the visual animator returned before SwipeHelper attaches its
 * progress/finish listeners. Gesture qualification, deletion and cancellation
 * remain native; retain the real release scale and alpha without a pose jump. */
internal object DeckDismissMotion {
    // Native upward selection negates its zero target. Compare primitive
    // floats: boxed Any equality distinguishes -0.0f from +0.0f.
    fun admitted(direction: Int,target: Float)=direction==1 && target==0f

    fun upward(geometry: DeckCardGeometry, native: ObjectAnimator): ObjectAnimator {
        val view = geometry.view
        val bounds = RectF()
        geometry.screenContent(bounds, 0f)
        val distance = (bounds.bottom + view.z * 2f).coerceAtLeast(1f)
        return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY - distance).apply {
            duration = native.duration
            interpolator = native.interpolator
        }
    }
}
