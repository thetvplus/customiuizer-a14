package com.android.server.wm

import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Minimal test double for `com.android.server.wm.WindowState`.
 *
 * The real `WindowState` class lives in `services.jar` and is not on the unit-test
 * classpath. This double exposes only the fields and methods that the
 * `SystemStatusBarInsetsHooks` window-frame logic touches via reflection.
 */
class WindowState {

    @JvmField
    var mAttrs: WindowManager.LayoutParams = WindowManager.LayoutParams()

    @JvmField
    var mDisplayContent: Any = FakeDisplayContent()

    @JvmField
    var mWmService: FakeWindowManagerService = FakeWindowManagerService()

    @JvmField
    var mFrame: Rect = Rect()

    fun getDisplayId(): Int = (mDisplayContent as FakeDisplayContent).displayId

    fun getDisplayMetrics(): DisplayMetrics = (mDisplayContent as FakeDisplayContent).getDisplayMetrics()

    fun getFrame(): Rect = mFrame

    override fun toString(): String = "WindowState{$mAttrs}"

    class FakeDisplayContent(
        val displayId: Int = 0,
        private val metrics: DisplayMetrics = DisplayMetrics().apply {
            densityDpi = 469
            density = 2.93125f
        },
    ) {
        var onGetDisplayMetrics: (() -> Unit)? = null

        private val policy: FakeDisplayPolicy = FakeDisplayPolicy()

        fun getDisplayMetrics(): DisplayMetrics {
            onGetDisplayMetrics?.invoke()
            return metrics
        }

        fun getDisplayPolicy(): FakeDisplayPolicy = policy
    }

    class FakeDisplayPolicy {
        @JvmField
        var mDecorInsets: FakeDecorInsets = FakeDecorInsets()
    }

    class FakeDecorInsets {
        var onInvalidate: (() -> Unit)? = null
        var invalidateCount = 0
            private set

        fun invalidate() {
            onInvalidate?.invoke()
            invalidateCount++
        }
    }

    class FakeWindowManagerService {
        @JvmField
        var mWindowPlacerLocked: Any = FakeWindowSurfacePlacer()

        @JvmField
        var mAnimationHandler: android.os.Handler? = null

        @JvmField
        var mGlobalLock: Any = Any()
    }

    class FakeWindowSurfacePlacer {
        var requestTraversalCount = 0
            private set
        var performSurfacePlacementCount = 0
            private set
        var onRequestTraversal: (() -> Unit)? = null

        fun requestTraversal() {
            onRequestTraversal?.invoke()
            requestTraversalCount++
        }

        fun performSurfacePlacement() {
            performSurfacePlacementCount++
        }
    }
}
