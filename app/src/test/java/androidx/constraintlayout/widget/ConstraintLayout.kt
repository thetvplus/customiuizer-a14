package androidx.constraintlayout.widget

import android.content.Context
import android.widget.FrameLayout

class ConstraintLayout(context: Context) : FrameLayout(context) {
    class LayoutParams(width: Int, height: Int) : FrameLayout.LayoutParams(width, height) {
        @JvmField var bottomToTop = -1
        @JvmField var endToStart = -1
    }
}
