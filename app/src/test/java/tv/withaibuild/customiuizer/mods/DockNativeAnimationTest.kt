package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.graphics.Matrix
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockNativeAnimationTest {
    @Test fun nativePrepareFramesAndResetOwnThePanelInBothModes() {
        for (full in listOf(false, true)) {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
            val root = FrameLayout(activity.get())
            activity.get().setContentView(root)
            val host = FrameLayout(activity.get())
            root.addView(host, FrameLayout.LayoutParams(320, 80).apply { leftMargin = 20; topMargin = 600 })
            val icon = View(activity.get())
            host.addView(icon, FrameLayout.LayoutParams(48, 48))
            fun layout() {
                val decor = host.rootView
                decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                decor.layout(0, 0, 360, 800)
            }
            layout()
            LauncherDockBackground.attach(host, DockBackgroundConfig(fullWidth = full), null)
            val panel = if (full) {
                val decor = host.rootView as FrameLayout
                val layer = (0 until decor.childCount).map { decor.getChildAt(it) }.filterIsInstance<DockFullWidthContainer>().single()
                layer.getChildAt(0) as DockBackgroundView
            } else host.getChildAt(0) as DockBackgroundView
            repeat(3) { panel.onPreDraw(); layout() }
            val original = listOf(icon)
            val participants = LauncherDockBackground.userPresentChildren(original, panel)
            assertEquals(listOf(icon, panel), participants)
            assertEquals(listOf(icon), original)
            assertSame(participants, LauncherDockBackground.userPresentChildren(participants, panel))
            // ROM V12 prepares these View properties and updates them from its own springs.
            for (progress in listOf(0f, .2f, .65f, .4f, 1f, 0f, 1f)) {
                for (view in participants.filterIsInstance<View>()) {
                    view.alpha = progress
                    view.scaleX = .6f + .4f * progress
                    view.scaleY = view.scaleX
                    view.translationY = 60f * (1 - progress)
                }
                panel.scheduleGeometry()
                panel.onPreDraw()
                layout()
                assertEquals(icon.alpha, panel.alpha, .0001f)
                assertEquals(icon.scaleX, panel.scaleX, .0001f)
                assertEquals(icon.scaleY, panel.scaleY, .0001f)
                assertEquals(icon.translationY, panel.translationY, .0001f)
            }
            assertEquals(0f, host.translationY, 0f)
            activity.pause().stop().destroy()
            assertSame(original, LauncherDockBackground.userPresentChildren(original, panel))
        }
    }

    @Test fun fullWidthMatrixMatchesHostAcrossAncestorsAndInterruptedLegacyFrames() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val root = FrameLayout(activity.get())
        activity.get().setContentView(root)
        val ancestor = FrameLayout(activity.get())
        root.addView(ancestor, FrameLayout.LayoutParams(340, 760).apply { leftMargin = 10; topMargin = 20 })
        val host = FrameLayout(activity.get())
        ancestor.addView(host, FrameLayout.LayoutParams(300, 80).apply { leftMargin = 15; topMargin = 580 })
        val layer = DockFullWidthContainer(activity.get())
        root.addView(layer, FrameLayout.LayoutParams(360, 800))
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 360, 800)
        root.scaleX = .95f; root.scaleY = .95f; root.translationY = 3f; root.alpha = .8f
        ancestor.scaleX = .9f; ancestor.scaleY = .9f; ancestor.translationX = -4f; ancestor.alpha = .6f
        val legacy = Matrix()
        for (progress in listOf(0f, .3f, .8f, .2f, 1f)) {
            host.pivotX = 110f; host.pivotY = 20f
            host.scaleX = .7f + progress * .3f; host.scaleY = .8f + progress * .2f
            host.translationX = 12f * (1 - progress); host.translationY = 60f * (1 - progress)
            host.alpha = .2f + .8f * progress
            layer.sync(host, legacy, .7f)
            val hostMatrix = Matrix().also { host.transformMatrixToGlobal(it) }
            val layerMatrix = Matrix().also { layer.transformMatrixToGlobal(it) }
            val expected = floatArrayOf(0f, 0f, 300f, 80f)
            val actual = floatArrayOf(25f, 600f, 325f, 680f)
            hostMatrix.mapPoints(expected)
            layerMatrix.mapPoints(actual)
            assertArrayEquals(expected, actual, .001f)
            assertEquals(host.alpha * ancestor.alpha * .7f, layer.alpha, .0001f)
        }
        host.scaleX = 1f; host.scaleY = 1f; host.translationX = 0f; host.translationY = 0f
        ancestor.scaleX = 1f; ancestor.scaleY = 1f; ancestor.translationX = 0f
        legacy.setScale(.8f, .8f, 150f, 40f); legacy.postTranslate(8f, 10f)
        layer.sync(host, legacy, .4f)
        val expected = floatArrayOf(0f, 0f, 300f, 80f)
        legacy.mapPoints(expected)
        val hostMatrix = Matrix().also { host.transformMatrixToGlobal(it) }
        hostMatrix.mapPoints(expected)
        val actual = floatArrayOf(25f, 600f, 325f, 680f)
        Matrix().also { layer.transformMatrixToGlobal(it) }.mapPoints(actual)
        assertArrayEquals(expected, actual, .001f)
        legacy.reset()
        layer.sync(host, legacy, 1f)
        assertTrue(layer.animationMatrix == null || layer.animationMatrix!!.isIdentity)
        host.visibility = View.INVISIBLE
        layer.sync(host, legacy, 1f)
        assertEquals(0f, layer.alpha, 0f)
        layer.sync(host, legacy, .4f, drawingAnimation = true)
        assertEquals(host.alpha * ancestor.alpha * .4f, layer.alpha, .0001f)
        activity.pause().stop().destroy()
    }

    @Test fun fullWidthGoogleBlurMultipliesNativeAndInheritedOpacityAndRecoversAfterInterruption() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val root = FrameLayout(activity.get())
        activity.get().setContentView(root)
        val host = FrameLayout(activity.get())
        root.addView(host, FrameLayout.LayoutParams(320, 80).apply { topMargin = 600 })
        val materialType = DockBackgroundTest.NativeMaterial::class.java
        DockBackgroundTest.NativeMaterial.googleSupported = true
        try {
            LauncherDockBackground.attach(host, DockBackgroundConfig(fullWidth = true, material = 2),
                materialType.getConstructor(android.content.Context::class.java))
            val decor = host.rootView as FrameLayout
            val layer = (0 until decor.childCount).map { decor.getChildAt(it) }.filterIsInstance<DockFullWidthContainer>().single()
            val panel = layer.getChildAt(0) as DockBackgroundView
            repeat(3) {
                decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                decor.layout(0, 0, 360, 800)
                panel.onPreDraw()
            }
            val material = panel.getChildAt(0) as DockBackgroundTest.NativeMaterial
            host.alpha = .4f; panel.alpha = .5f
            panel.onPreDraw()
            assertEquals(46, material.background.alpha)
            val blurRegion = material.background
            val animation = android.view.animation.AnimationSet(false)
            host.startAnimation(animation)
            panel.bindEditingAnimation(animation, showing = true)
            host.visibility = View.INVISIBLE
            panel.onEditingFrame(animation, .5f, true, Matrix().apply { setTranslate(0f, -30f) })
            assertEquals(23, material.background.alpha)
            assertEquals(.5f, panel.alpha, 0f)
            host.clearAnimation()
            panel.onPreDraw()
            assertFalse(material.appliedEnabled)
            assertNull(material.background)
            assertEquals(0, blurRegion.alpha)
            host.visibility = View.VISIBLE
            panel.onPreDraw()
            assertTrue(material.appliedEnabled)
            assertSame(blurRegion, material.background)
            assertEquals(46, material.background.alpha)
        } finally {
            activity.pause().stop().destroy()
            DockBackgroundTest.NativeMaterial.googleSupported = false
        }
    }
}
