package tv.withaibuild.customiuizer.mods.volume

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Region
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.FrameLayout
import android.view.Gravity
import android.view.ViewOutlineProvider
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.utils.G4RoundRectGeometry
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.CleanupActions
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import kotlin.math.*

/** Cosmetic adapter for the A14 plugin. Native listeners, stream/routing identity,
 * relative touch mapping, accessibility, safety warnings and dismissal stay native. */
internal object IosVolumeStyle {
    const val KEY = "system_volume_ios"
    const val COMPACT_KEY = "system_volume_ios_compact"
    const val STYLE_KEY = "system_volume_ios_style"
    const val CLASSIC_OFFSET_KEY = "system_volume_ios_classic_offset"
    private const val OWNER = "a14.iosVolume.owner"
    private const val BINDING = "a14.iosVolume.binding"

    /** The existing controller callback precedes native show delivery on Main. */
    internal class NativeInput {
        @Volatile private var keyAt=Long.MIN_VALUE
        @Volatile var sequence=0L
            private set
        @Volatile var session=WeakReference<Session>(null)
        @Synchronized fun record(flags: Int) {
            sequence++
            val key=flags and 0x1000!=0
            val pressed=keyAt!=Long.MIN_VALUE
            keyAt=if(key && flags and 1!=0) android.os.SystemClock.uptimeMillis() else Long.MIN_VALUE
            // Normal KEY_UP has FROM_KEY without SHOW_UI. AudioService removes
            // PLAY_SOUND for media, so that flag cannot qualify this boundary.
            // Only release a press whose native show we actually received.
            if(key && flags and 1==0 && pressed) session.get()?.releaseHardwareAsync(this,sequence)
        }
        fun isKey(): Boolean {
            val at=keyAt
            return at!=Long.MIN_VALUE && android.os.SystemClock.uptimeMillis()-at in 0L..300L
        }
    }

    fun install(loader: ClassLoader): FeatureInstallResult {
        val entries = ArrayList<Pair<java.lang.reflect.Method, MethodHook>>()
        fun hookMethod(method: java.lang.reflect.Method, callback: MethodHook) { entries.add(method to callback) }
        fun hookAllMethods(type: Class<*>, name: String, callback: MethodHook) {
            val column="com.android.systemui.miui.volume.MiuiVolumeDialogImpl\$VolumeColumn"
            val parameters=when(name) {
                "updateColumnH","updateColumnSliderBlendColor" -> listOf(column)
                "showH","showVolumeDialogH" -> listOf("int")
                // (ZZ)V delegates directly here on both admitted plugin artifacts.
                "updateExpandedH" -> listOf("boolean","boolean","boolean")
                "onProgressChanged" -> listOf("android.widget.SeekBar","int","boolean")
                "onStartTrackingTouch","onStopTrackingTouch" -> listOf("android.widget.SeekBar")
                "updateVolumeColumnH" -> listOf(column,"boolean")
                "destroy","createShowAnimator" -> emptyList()
                else -> error("Unspecified volume ABI: $name")
            }
            val result=if(name=="createShowAnimator") "android.animation.ValueAnimator" else "void"
            val method=type.declaredMethods.single { it.name==name && it.returnType.name==result &&
                it.parameterTypes.map(Class<*>::getName)==parameters }
            hookMethod(method,callback)
        }
        val startCompact = MainModule.mPrefs.getBoolean(COMPACT_KEY, false)
        val classic = MainModule.mPrefs.getString(STYLE_KEY, "1") == "2"
        val classicOffset = MainModule.mPrefs.getInt(CLASSIC_OFFSET_KEY, 0).coerceIn(-100, 100)
        val input=NativeInput()
        if (!classic) {
            val controller=XposedHelpers.findClass("com.android.systemui.volume.VolumeDialogControllerImpl",loader)
            val volumeChanged=controller.getDeclaredMethod("onVolumeChangedW",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
                .apply { require(returnType==Boolean::class.javaPrimitiveType);isAccessible=true }
            hookMethod(volumeChanged,object: MethodHook() {
                override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded { input.record(callback.getArg(1) as Int) }
            })
        }
        val dialog = XposedHelpers.findClassIfExists("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", loader) ?: return FeatureInstallResult.FAILED_PERMANENT
        val listener = XposedHelpers.findClassIfExists("${dialog.name}\$VolumeSeekBarChangeListener", loader) ?: return FeatureInstallResult.FAILED_PERMANENT
        val expanded = dialog.getDeclaredField("mExpanded").apply { isAccessible = true }
        val showing = dialog.getDeclaredField("mShowing").apply { isAccessible = true }
        val dialogView = dialog.getDeclaredField("mDialogView").apply { isAccessible = true }
        val dialogContent = dialog.getDeclaredField("mDialogContentView").apply { isAccessible = true }
        val shadowView = dialog.getDeclaredField("mShadowView").apply { isAccessible = true }
        val expandButton = dialogView.type.getDeclaredField("mExpandButton").apply { isAccessible = true }
        val columns = dialog.getDeclaredField("mColumns").apply { isAccessible = true }
        val temporary = dialog.getDeclaredField("mTempColumn").apply { isAccessible = true }
        val columnType = temporary.type
        val view = columnType.getDeclaredField("view").apply { isAccessible = true }
        val slider = columnType.getDeclaredField("slider").apply { isAccessible = true }
        val progress = columnType.getDeclaredField("progressView").apply { isAccessible = true }
        val background = columnType.getDeclaredField("progressViewBg").apply { isAccessible = true }
        val icon = columnType.getDeclaredField("icon").apply { isAccessible = true }
        // The field is declared as framework SeekBar, whose touch handling is
        // inherited. Observe the plugin override before its relative-axis transform.
        val nativeTouch = XposedHelpers.findClass("com.android.systemui.miui.volume.MiuiVolumeSeekBar", loader)
            .getDeclaredMethod("onTouchEvent", MotionEvent::class.java)
        val listenerColumn = listener.getDeclaredField("mColumn").apply { isAccessible = true }
        val streamState = columnType.getDeclaredField("ss").apply { isAccessible = true }
        val cachedIcon = XposedHelpers.findFieldIfExists(columnType, "cachedIconRes")
        val classicIconUpdate = if (classic && cachedIcon != null)
            XposedHelpers.findMethodExactIfExists(dialog, "updateIconRes", columnType) else null
        val headset = XposedHelpers.findFieldIfExists(dialog, "mIsHeadset")
        val bluetooth = XposedHelpers.findFieldIfExists(streamState.type, "routedToBluetooth")
        val dynamic = XposedHelpers.findFieldIfExists(streamState.type, "dynamic")
        fun speakerRoute(owner: Any, column: Any): Boolean {
            val state = streamState.get(column) ?: return false
            return headset?.getBoolean(owner) == false && bluetooth?.getBoolean(state) == false &&
                dynamic?.getBoolean(state) == false
        }
        val streamLevel = streamState.type.getDeclaredField("level").apply { isAccessible = true }
        val streamMin = streamState.type.getDeclaredField("levelMin").apply { isAccessible = true }
        val streamMax = streamState.type.getDeclaredField("levelMax").apply { isAccessible = true }
        val fillHeight = progress.type.getDeclaredField("mHeight").apply { isAccessible = true }
        val fillProgress = progress.type.getDeclaredField("mProgress").apply { isAccessible = true }
        val fillSetter = progress.type.getDeclaredMethod("setProgress", Int::class.javaPrimitiveType)
        val blurOpened = XposedHelpers.findClass("miui.systemui.util.MiBlurCompat", loader)
            .getDeclaredMethod("getBackgroundBlurOpened", android.content.Context::class.java)
        // Verified A14 column container; resolve once, never from a frame callback.
        val material = XposedHelpers.findClassIfExists("c.f.b.a.a.g", loader) ?: return FeatureInstallResult.FAILED_PERMANENT
        val hasBlur = material.getDeclaredMethod("isBlurEnabledAndSupported")
        val setBlur = material.getDeclaredMethod("setBlurEnabled", Boolean::class.javaPrimitiveType)
        val getViewMode = View::class.java.getDeclaredMethod("getMiViewBlurMode")
        val setViewMode = View::class.java.getDeclaredMethod("setMiViewBlurMode", Int::class.javaPrimitiveType)
        val nativeMotion = XposedHelpers.findClassIfExists("com.android.systemui.miui.volume.MiuiVolumeDialogMotion", loader) ?: return FeatureInstallResult.FAILED_PERMANENT
        val motionRoot = nativeMotion.getDeclaredField("mDialogView").apply { isAccessible = true }
        val expandSource = nativeMotion.getDeclaredField("mIsExpandButton").apply { isAccessible = true }
        val motionCallback = nativeMotion.getDeclaredField("mCallback").apply { isAccessible = true }
        val expandClick = motionCallback.type.getDeclaredMethod("onExpandClicked").apply { isAccessible = true }
        val viewMotion = dialogView.type.getDeclaredField("mMotion").apply { isAccessible = true }
        val listenerInfo = View::class.java.getDeclaredField("mListenerInfo").apply { isAccessible = true }
        val clickListener = listenerInfo.type.getDeclaredField("mOnClickListener").apply { isAccessible = true }
        val computeInsets = dialogView.type.declaredMethods.single { it.name == "onComputeInternalInsets" && it.parameterCount == 1 }
        val touchRegion = computeInsets.parameterTypes[0].getDeclaredField("touchableRegion").apply { isAccessible = true }
        fun clear(owner: Any) {
            (XposedHelpers.getAdditionalInstanceField(owner, OWNER) as? Session)?.close()
            XposedHelpers.setAdditionalInstanceField(owner, OWNER, null)
        }
        fun apply(owner: Any) {
            if (expanded.getBoolean(owner) || !showing.getBoolean(owner)) return
            val items = (columns.get(owner) as? List<*>) ?: return
            val bindings = ArrayList<Binding>()
            try {
                for (item in items.take(16) + listOfNotNull(temporary.get(owner))) {
                    item ?: continue
                    val root = view.get(item) as? View ?: continue
                    if (!visibleColumn(root)) continue
                    if (root.resources.configuration.orientation == Configuration.ORIENTATION_UNDEFINED) continue
                    val seek = slider.get(item) as? SeekBar ?: continue
                    val fill = progress.get(item) as? View ?: continue
                    val track = background.get(item) as? View ?: continue
                    // The admitted native track contains only its now-hidden fill.
                    // Unknown child layouts retain their native presentation.
                    if (track !is ViewGroup || track.childCount != 1 || track.getChildAt(0) !== fill) continue
                    val image = icon.get(item) as? ImageView ?: continue
                    if (XposedHelpers.getAdditionalInstanceField(root, BINDING) != null) continue
                    val backdrop = material.isInstance(root) && hasBlur.invoke(root) == true
                    val nativeViews = arrayOf(root, seek, track)
                    val modes = IntArray(nativeViews.size) { (getViewMode.invoke(nativeViews[it]) as Number).toInt() }
                    val blur = { enabled: Boolean ->
                        val cleanup = CleanupActions()
                        if (backdrop) cleanup.run { setBlur.invoke(root, enabled) }
                        for (i in nativeViews.indices) cleanup.run {
                            val current=(getViewMode.invoke(nativeViews[i]) as Number).toInt()
                            val target=if(enabled) modes[i] else 0
                            if(current!=target && (!enabled || current==0)) setViewMode.invoke(nativeViews[i],target)
                        }
                        cleanup.finish()
                    }
                    val nativeFill = blurOpened.invoke(null, root.context) == true
                    val binding = Binding(root, seek, fill, track, image, nativeFill, startCompact, classic, blur)
                    bindings.add(binding)
                    if (classicIconUpdate != null) binding.nativeGlyph(cachedIcon!!.getInt(item), speakerRoute(owner, item))
                    if (nativeFill) binding.nativeProgress(fillProgress.getInt(fill), fillHeight.getInt(fill))
                    streamState.get(item)?.let { binding.nativeVolume(streamLevel.getInt(it), streamMin.getInt(it), streamMax.getInt(it),initial=true) }
                    XposedHelpers.setAdditionalInstanceField(root, BINDING, binding)
                }
                if (bindings.size != 1) {
                    val cleanup = CleanupActions()
                    bindings.forEach { binding -> cleanup.run { binding.close() } }; cleanup.finish(); return
                }
                val root = dialogView.get(owner) as? View
                val content = dialogContent.get(owner) as? View
                if (root == null || content == null) {
                    val cleanup = CleanupActions()
                    bindings.forEach { binding -> cleanup.run { binding.close() } }; cleanup.finish()
                }
                else {
                    val more = expandButton.get(root) as? View
                    val originalClick = more?.let { listenerInfo.get(it) }?.let { clickListener.get(it) as? View.OnClickListener }
                    val adapter = Session(root, content, shadowView.get(owner) as? View, bindings,
                        { (getViewMode.invoke(content) as Number).toInt() }, { setViewMode.invoke(content, it) },
                        { closing ->
                            if(input.session.get()===closing) input.session.clear()
                            if(XposedHelpers.getAdditionalInstanceField(owner,OWNER)===closing)
                                XposedHelpers.removeAdditionalInstanceField(owner,OWNER)
                        }, more, originalClick, classicOffset = classicOffset) {
                        viewMotion.get(root)?.let { motion ->
                            // The original dots pass through to the overlapping slider.
                            // A separate footer invokes that same guarded native action.
                            expandSource.setBoolean(motion, false)
                            motionCallback.get(motion)?.let { expandClick.invoke(it) }
                        }
                    }
                    XposedHelpers.setAdditionalInstanceField(owner, OWNER, adapter)
                    input.session=WeakReference(adapter)
                }
            } catch (error: Throwable) {
                val cleanup = CleanupActions(error)
                bindings.forEach { binding -> cleanup.run { binding.close() } }
                cleanup.finish()
            }
        }
        if (classicIconUpdate != null && cachedIcon != null) {
            // Initial replacement and independent mute/route updates share one
            // capability gate. Missing callbacks keep every glyph native.
            hookMethod(classicIconUpdate, object : MethodHook() {
                    override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                        if (callback.getThrowable() != null) return@guarded
                        val owner = callback.getThisObject() ?: return@guarded
                        val column = callback.getArg(0) ?: return@guarded
                        val binding = XposedHelpers.getAdditionalInstanceField(view.get(column), BINDING) as? Binding ?: return@guarded
                        binding.nativeGlyph(cachedIcon.getInt(column), speakerRoute(owner, column))
                    }
            })
        }
        hookAllMethods(dialog, "updateColumnH", object : MethodHook() {
            override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded { callback.getThisObject()?.let(::clear) }
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                if (callback.getThrowable() == null) callback.getThisObject()?.let(::apply)
            }
        })
        hookAllMethods(dialog, "showVolumeDialogH", object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                if (callback.getThrowable() == null) callback.getThisObject()?.let { clear(it); apply(it) }
            }
        })
        hookAllMethods(dialog, "updateExpandedH", object : MethodHook() {
            override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded { callback.getThisObject()?.let(::clear) }
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                if (callback.getThrowable() == null) callback.getThisObject()?.let(::apply)
            }
        })
        hookAllMethods(dialog, "destroy", object : MethodHook() {
            override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded { callback.getThisObject()?.let(::clear) }
        })
        hookAllMethods(dialog, "updateColumnSliderBlendColor", object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                if (callback.getThrowable() != null) return@guarded
                val item = callback.getArg(0) ?: return@guarded
                val root = view.get(item) as? View ?: return@guarded
                val session = XposedHelpers.getAdditionalInstanceField(callback.getThisObject(), OWNER) as? Session
                if (session != null) session.refreshMaterial()
                else (XposedHelpers.getAdditionalInstanceField(root, BINDING) as? Binding)?.refreshMaterial()
            }
        })
        // Repeated hardware input retains the current shape/velocity and re-arms
        // only the cosmetic collapse; native showH still owns dismissal timing.
        if (!classic) hookAllMethods(dialog, "showH", object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                if (callback.getThrowable() == null) (XposedHelpers.getAdditionalInstanceField(callback.getThisObject(), OWNER) as? Session)?.noteInput(input.isKey())
            }
        })
        for (method in if (classic) listOf("onProgressChanged") else listOf("onProgressChanged", "onStartTrackingTouch", "onStopTrackingTouch")) {
            hookAllMethods(listener, method, object : MethodHook() {
                override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                    if (callback.getThrowable() != null) return@guarded
                    val item = listenerColumn.get(callback.getThisObject()) ?: return@guarded
                    val root = view.get(item) as? View ?: return@guarded
                    val binding = XposedHelpers.getAdditionalInstanceField(root, BINDING) as? Binding ?: return@guarded
                    when (method) {
                        "onStartTrackingTouch" -> binding.interact(true)
                        "onStopTrackingTouch" -> binding.interact(false)
                        else -> binding.refresh()
                    }
                }
            })
        }
        hookMethod(fillSetter, object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                if (callback.getThrowable() != null) return@guarded
                val source = callback.getThisObject() ?: return@guarded
                val binding = XposedHelpers.getAdditionalInstanceField(source, BINDING) as? Binding ?: return@guarded
                binding.nativeProgress((callback.getArg(0) as Number).toInt(), fillHeight.getInt(source))
            }
        })
        if (!classic) hookAllMethods(dialog, "updateVolumeColumnH", object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                if (callback.getThrowable() != null) return@guarded
                val column = callback.getArg(0) ?: return@guarded
                val binding = XposedHelpers.getAdditionalInstanceField(view.get(column), BINDING) as? Binding ?: return@guarded
                streamState.get(column)?.let { binding.nativeVolume(streamLevel.getInt(it), streamMin.getInt(it), streamMax.getInt(it)) }
            }
        })
        if (!classic) hookMethod(nativeTouch, object : MethodHook() {
            override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded {
                val binding = XposedHelpers.getAdditionalInstanceField(callback.getThisObject(), BINDING) as? Binding ?: return@guarded
                (callback.getArg(0) as? MotionEvent)?.let(binding::observeTouch)
            }
        })
        hookMethod(background.type.getDeclaredMethod("dispatchDraw", Canvas::class.java), object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                if (XposedHelpers.getAdditionalInstanceField(callback.getThisObject(), BINDING) is Binding)
                    // Native dispatchDraw leaves a rounded clip on the canvas for
                    // the following foreground. Its sole child is already hidden.
                    callback.returnAndSkip(null)
            }
        })
        hookMethod(computeInsets, object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                if (callback.getThrowable() != null) return@guarded
                val session = XposedHelpers.getAdditionalInstanceField(callback.getThisObject(), OWNER) as? Session ?: return@guarded
                session.includeTouchableRegion(touchRegion.get(callback.getArg(0)) as Region)
            }
        })
        hookAllMethods(nativeMotion, "createShowAnimator", object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                if (callback.getThrowable() != null) return@guarded
                val root = motionRoot.get(callback.getThisObject()) as? View ?: return@guarded
                // The native factory re-enables its large material shadow after
                // attachment. Suppress that once without touching its animation.
                (XposedHelpers.getAdditionalInstanceField(root, OWNER) as? Session)?.refreshMaterial()
            }
        })
        return PreparedHookBatch().install(entries)
    }

    internal fun classicGlyphResource(name: String, speakerRoute: Boolean): Int = when (name) {
        "ic_miui_volume_media", "ic_miui_volume_media_collpased" -> if (speakerRoute) R.drawable.ios12_volume_speaker else 0
        "ic_miui_volume_media_mute", "ic_miui_volume_media_mute_collpased" -> if (speakerRoute) R.drawable.ios12_volume_mute else 0
        "ic_miui_volume_speaker" -> R.drawable.ios12_volume_speaker
        "ic_miui_volume_speaker_mute" -> R.drawable.ios12_volume_mute
        "ic_miui_volume_ringer_on_seekbar", "ic_miui_volume_ringer_on_seekbar_collpased" -> R.drawable.ios12_volume_ringer
        "ic_miui_volume_ringer_mute_on_seekbar", "ic_miui_volume_ringer_mute_on_seekbar_collpased" -> R.drawable.ios12_volume_ringer_mute
        else -> 0
    }
    private fun loadClassicGlyph(icon: ImageView, resource: Int, speakerRoute: Boolean): Drawable? {
        if (resource == 0) return null
        return try {
            val selected = classicGlyphResource(icon.resources.getResourceEntryName(resource), speakerRoute)
            if (selected == 0) null else ModuleHelper.getModuleRes(icon.context).getDrawable(selected, null)?.mutate()
        } catch (_: android.content.res.Resources.NotFoundException) { null }
    }
    internal fun visibleColumn(root: View): Boolean {
        var current: View? = root
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            current = current.parent as? View
        }
        return true
    }

    internal class Session(private val root: View, private val content: View, private val shadow: View?, private val bindings: List<Binding>,
        private val readMode: () -> Int, private val writeMode: (Int) -> Unit, private val ended: (Session) -> Unit,
        private val more: View? = null, private val moreClick: View.OnClickListener? = null,
        private val classicOffset: Int = 0,
        private val moreAction: (() -> Unit)? = null) : View.OnAttachStateChangeListener {
        @Volatile private var closed = false
        private val shadowVisibility = shadow?.visibility
        private var nativeBackground = content.background
        private var nativeMode = readMode()
        private val moreLayout = more?.layoutParams
        private val moreClickable = more?.isClickable
        private val moreTouchFocusable = more?.isFocusableInTouchMode
        private val moreImage = more as? ImageView
        private val moreDrawable = moreImage?.drawable
        private val moreScale = moreImage?.scaleType
        private val moreZ = more?.translationZ
        private var appliedMoreZ = Float.NaN
        private var appliedMoreLayout: android.view.ViewGroup.LayoutParams? = null
        private var parentSuppressed = false
        private var ownedMoreClick: View.OnClickListener? = null
        private val touchLocation = IntArray(2)
        private val touchRect = Rect()
        private val moreGlyph = moreImage?.let { MoreGlyph(root.resources.displayMetrics.density) }
        private var hardwareReleased: Runnable?=null
        private val placement = bindings.singleOrNull()?.takeIf { it.classic }?.let { ClassicVolumePlacement(root, it.trackView, classicOffset, more) }
        init {
            try {
                root.addOnAttachStateChangeListener(this); content.addOnAttachStateChangeListener(this)
                XposedHelpers.setAdditionalInstanceField(root, OWNER, this)
                shadow?.visibility = View.INVISIBLE
                if (moreLayout is FrameLayout.LayoutParams) more?.layoutParams = FrameLayout.LayoutParams(moreLayout).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    width = (58 * root.resources.displayMetrics.density).roundToInt()
                    height = (28 * root.resources.displayMetrics.density).roundToInt()
                }.also { appliedMoreLayout = it }
                if (moreGlyph != null && moreImage != null) {
                    moreImage.scaleType = ImageView.ScaleType.FIT_XY; moreImage.setImageDrawable(moreGlyph)
                    bindings.singleOrNull()?.attachMore(moreGlyph, moreImage)
                }
                if (moreAction != null && more != null) {
                    // Native columns cover the original dots' hit area. Keep the
                    // independent footer above those siblings for normal dispatch.
                    var highest = more.z
                    (more.parent as? ViewGroup)?.let { parent ->
                        for (i in 0 until parent.childCount) highest = max(highest, parent.getChildAt(i).z)
                    }
                    appliedMoreZ = more.translationZ + highest - more.z + root.resources.displayMetrics.density
                    more.translationZ = appliedMoreZ
                    // The native accessibility target takes focus on the first tap.
                    // Keep keyboard focusability, but let this footer click on that UP.
                    more.isFocusableInTouchMode = false
                    ownedMoreClick = View.OnClickListener { if (!closed) ModuleHelper.guarded { moreAction.invoke() } }
                    more.setOnClickListener(ownedMoreClick)
                }
                if (content.isAttachedToWindow) suppressParent()
            } catch (error: Throwable) {
                val cleanup = CleanupActions(error)
                cleanup.run { close() }
                cleanup.finish()
            }
        }
        private fun suppressParent() {
            // Motion's earlier attach listener creates this drawable after showH.
            // Capture that actual native result, then give this material one owner.
            if (!parentSuppressed || content.background != null) nativeBackground = content.background
            val mode = readMode()
            if (!parentSuppressed || mode != 0) nativeMode = mode
            parentSuppressed = true
            if(content.background!=null) content.background=null
            if(mode!=0) writeMode(0)
        }
        @Synchronized fun releaseHardwareAsync(input: NativeInput,sequence: Long) {
            if(closed) return
            hardwareReleased?.let(root::removeCallbacks)
            val release=Runnable {
                if(!closed && input.sequence==sequence && input.session.get()===this)
                    ModuleHelper.guarded { bindings.forEach { it.releaseHardware() } }
            }
            hardwareReleased=release
            root.post(release)
            // close can run on Main between the worker's check and enqueue.
            if(closed) root.removeCallbacks(release)
        }
        fun noteInput(hardware: Boolean=true) { if (!closed) bindings.forEach { it.noteInput(hardware) } }
        fun refreshMaterial() {
            if (closed) return
            // Palette/show factories can replace material after attach. Preserve the
            // latest native objects for expansion, then reassert the one owner.
            suppressParent()
            shadow?.visibility = View.INVISIBLE
            bindings.forEach { it.refreshMaterial() }
        }
        fun includeTouchableRegion(region: Region) {
            if (closed || !root.isShown) return
            placement?.update()
            // The verified native callback uses getLeft/getTop and omits the
            // dialog's final translation. The compact strip and dot reach that
            // translated edge; retain native regions and union actual bounds.
            root.getLocationInWindow(touchLocation)
            touchRect.set(touchLocation[0], touchLocation[1], touchLocation[0] + root.width, touchLocation[1] + root.height)
            // The verified collapsed callback sets only this root's rectangle.
            // Central mode replaces its stale untranslated edge region, otherwise
            // invisible side coordinates would continue to intercept input.
            if (placement != null) {
                // Native parent width may still use its narrow-column resource.
                // Include the real slider and footer, even outside that parent.
                val track = bindings.single().trackView
                track.getLocationInWindow(touchLocation)
                touchRect.union(touchLocation[0], touchLocation[1], touchLocation[0] + track.width, touchLocation[1] + track.height)
                if (more?.isShown == true) {
                    more.getLocationInWindow(touchLocation)
                    touchRect.union(touchLocation[0], touchLocation[1], touchLocation[0] + more.width, touchLocation[1] + more.height)
                }
                region.set(touchRect)
            } else region.op(touchRect, Region.Op.UNION)
        }
        fun close() {
            if (closed) return
            closed = true
            val cleanup = CleanupActions()
            cleanup.run { placement?.close() }
            cleanup.run { hardwareReleased?.let(root::removeCallbacks);hardwareReleased=null }
            cleanup.run { root.removeOnAttachStateChangeListener(this) }
            cleanup.run { content.removeOnAttachStateChangeListener(this) }
            cleanup.run {
                if (XposedHelpers.getAdditionalInstanceField(root, OWNER) === this)
                    XposedHelpers.removeAdditionalInstanceField(root, OWNER)
            }
            cleanup.run { if (shadow?.visibility == View.INVISIBLE) shadowVisibility?.let { shadow.visibility = it } }
            cleanup.run {
                val current = more?.layoutParams
                if (current === appliedMoreLayout && current is FrameLayout.LayoutParams && moreLayout is FrameLayout.LayoutParams) {
                    val untouched = current.width == (58 * root.resources.displayMetrics.density).roundToInt() &&
                        current.height == (28 * root.resources.displayMetrics.density).roundToInt() &&
                        current.gravity == (Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL) &&
                        current.leftMargin == moreLayout.leftMargin && current.topMargin == moreLayout.topMargin &&
                        current.rightMargin == moreLayout.rightMargin && current.bottomMargin == moreLayout.bottomMargin
                    if (current.width == (58 * root.resources.displayMetrics.density).roundToInt()) current.width = moreLayout.width
                    if (current.height == (28 * root.resources.displayMetrics.density).roundToInt()) current.height = moreLayout.height
                    if (current.gravity == Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL) current.gravity = moreLayout.gravity
                    more.layoutParams = if (untouched) moreLayout else current
                }
            }
            cleanup.run {
                if (moreImage?.drawable === moreGlyph) {
                    moreImage?.setImageDrawable(moreDrawable)
                    if (moreImage?.scaleType == ImageView.ScaleType.FIT_XY) moreScale?.let { moreImage.scaleType = it }
                }
            }
            cleanup.run { if (more != null && moreZ != null && more.translationZ == appliedMoreZ) more.translationZ = moreZ }
            cleanup.run {
                if (moreAction != null && more != null && ownedMoreClick != null) {
                    val info = View::class.java.getDeclaredField("mListenerInfo").apply { isAccessible = true }.get(more)
                    val current = info?.javaClass?.getDeclaredField("mOnClickListener")?.apply { isAccessible = true }?.get(info)
                    if (current === ownedMoreClick) {
                        more.setOnClickListener(moreClick); moreClickable?.let { more.isClickable = it }
                    }
                    if (!more.isFocusableInTouchMode) moreTouchFocusable?.let { more.isFocusableInTouchMode = it }
                }
            }
            cleanup.run { if (parentSuppressed && content.background == null) content.background = nativeBackground }
            cleanup.run { if (parentSuppressed && readMode() == 0) writeMode(nativeMode) }
            // Binding owns the moving footer until its final pose is restored.
            bindings.forEach { binding -> cleanup.run { binding.close() } }
            cleanup.run { ended(this) }
            cleanup.finish()
        }
        override fun onViewAttachedToWindow(v: View) = ModuleHelper.guarded {
            if (!closed) { shadow?.visibility = View.INVISIBLE; if (v === content) suppressParent() }
        }
        override fun onViewDetachedFromWindow(v: View) = ModuleHelper.guarded { if (v === root) close() }
    }

    internal class Binding(private val root: View, private val slider: SeekBar, private val fill: View,
        private val track: View, private val icon: ImageView,
        private val nativeFill: Boolean = false,
        private val startCompact: Boolean = false,
        val classic: Boolean = false,
        private val blur: ((Boolean) -> Unit)? = null) : View.OnAttachStateChangeListener {
        val trackView get() = track
        private val panelWidth get() = if (classic) 155 else 58
        private val panelHeight get() = if (classic) 155 else 170
        private var rootBackground = root.background
        private var sliderBackground = slider.background
        private var progress = slider.progressDrawable
        private var thumb = slider.thumb
        private var foreground = track.foreground
        private var trackBackground = track.background
        private val trackClip = track.clipToOutline
        private val nativeOutline = track.outlineProvider
        private val nativeElevation = track.elevation
        private val nativeAmbient = track.outlineAmbientShadowColor
        private val nativeSpot = track.outlineSpotShadowColor
        private var fillAlpha = fill.alpha
        private val iconAlpha = icon.alpha
        private var iconTint = icon.imageTintList
        private val iconX = icon.translationX; private val iconY = icon.translationY
        private val iconScaleX = icon.scaleX; private val iconScaleY = icon.scaleY
        private val iconZ = icon.translationZ
        private var nativeIcon = icon.drawable
        private var nativeIconResource = 0
        private var nativeSpeakerRoute = false
        private var classicGlyph: Drawable? = null
        private val iconScaleType = icon.scaleType
        private val iconMatrix = if (classic) Matrix(icon.imageMatrix) else null
        private val classicMatrix = if (classic) Matrix() else null
        private val paddingBottom = root.paddingBottom
        private val dimensions = arrayOf(slider, track).map { it.layoutParams.width to it.layoutParams.height }
        private var closed = false
        private val density = root.resources.displayMetrics.density
        // 7 Plus uses a 414-point short edge. This is an Android window mapping,
        // not an assertion that UIKit points equal dp on the target display.
        private val panelDensity = if (classic) root.resources.displayMetrics.let {
            minOf(it.widthPixels, it.heightPixels).coerceAtLeast(1) / 414f
        } else density
        private var moreView: ImageView? = null
        private var moreLocations: IntArray? = null
        private var moreTrackDelta = 0f
        private var morePositioned = false
        private var moreOriginalY = 0f
        private var moreAppliedY = Float.NaN
        private var moreOriginalX = 0f
        private var moreAppliedX = Float.NaN
        private var appliedIconAlpha = Float.NaN
        private var appliedIconX = Float.NaN; private var appliedIconY = Float.NaN
        private var appliedIconScaleX = Float.NaN; private var appliedIconScaleY = Float.NaN
        private var appliedIconZ = Float.NaN; private var appliedElevation = Float.NaN
        private var visibleLevel = 0
        private var glyphFillThreshold = .16f
        private val darkIconTint = ColorStateList.valueOf(0xff242426.toInt())
        private val lightIconTint = ColorStateList.valueOf(0xfffafafa.toInt())
        private val moreLayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionMore() }
        private val classicLayout = if (classic) View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> paint.refreshGeometry() } else null
        private val paint: Track = Track(panelDensity, startCompact, classic) { w, h, glyph, offsetY ->
            icon.alpha = iconAlpha * glyph.coerceIn(0f, 1f)
            val f = regularFraction(paint.materialWidth / density)
            val glyphY = if (classic) h * .48f else (h - w) * (1f - f) + (h - w * .5f) * f
            glyphFillThreshold = (1f - glyphY / h).coerceIn(0f, 1f)
            updateIconTint()
            icon.translationX = track.left + paint.center(track.width.toFloat()) - icon.left - icon.width * .5f
            icon.translationY = track.top + (track.height - h) * .5f + glyphY + offsetY - icon.top - icon.height * .5f
            if (classic) {
                updateClassicGlyph()
                icon.scaleX = 1f; icon.scaleY = 1f
            } else {
                icon.scaleX = iconScaleX * glyph * .768f; icon.scaleY = iconScaleY * glyph * .768f
            }
            if (morePositioned) moreView?.let {
                moreAppliedY = moreTrackDelta + (track.height + h) * .5f + offsetY
                it.translationY = moreAppliedY
            }
            track.elevation = if (classic) 0f else paintShadowElevation(paint.materialWidth)
            icon.translationZ = track.z + density - icon.elevation
            appliedIconAlpha = icon.alpha; appliedIconX = icon.translationX; appliedIconY = icon.translationY
            appliedIconScaleX = icon.scaleX; appliedIconScaleY = icon.scaleY; appliedIconZ = icon.translationZ
            appliedElevation = track.elevation
            track.invalidateOutline()
        }
        private fun updateClassicGlyph() {
            val matrix = classicMatrix ?: return
            if (icon.width <= 0 || icon.height <= 0) return
            val drawable = icon.drawable
            val width = drawable?.intrinsicWidth?.takeIf { it > 0 } ?: icon.width
            val height = drawable?.intrinsicHeight?.takeIf { it > 0 } ?: icon.height
            val scale = if (classicGlyph != null) panelDensity / density
                else 52f * panelDensity / maxOf(width, height)
            // ImageView's canvas matrix lets VectorDrawable rasterize at the final
            // resolution. Scaling its RenderNode magnifies the small native cache.
            // Keep the native Drawable, including muted/route/animation identity.
            matrix.setScale(scale, scale)
            matrix.postTranslate((icon.width - icon.paddingLeft - icon.paddingRight - width * scale) * .5f,
                (icon.height - icon.paddingTop - icon.paddingBottom - height * scale) * .5f)
            if (icon.scaleType != ImageView.ScaleType.MATRIX) icon.scaleType = ImageView.ScaleType.MATRIX
            if (icon.imageMatrix != matrix) icon.imageMatrix = matrix
        }
        private fun paintShadowElevation(width: Float): Float {
            val compact=((47f*density-width)/(40f*density)).coerceIn(0f,1f)
            return (20f-15f*compact)*density
        }
        private val ownedOutline=object: ViewOutlineProvider() {
            override fun getOutline(view: View,outline: Outline) { paint.getOutline(outline) }
        }
        private var tracking = false
        private var compactBeforeTracking = false
        private var receivedInput = false
        private var nativeLevel = slider.progress
        private var nativeMin = 0
        private var nativeMax = slider.max
        private var hardwarePending = false
        private var levelChangedForInput = false
        private val hardwareEndpoint = Runnable { ModuleHelper.guarded { consumeHardwareEndpoint() } }
        // A bounded fallback covers interrupted/missing native release delivery.
        private val hardwareRelease = Runnable { ModuleHelper.guarded { releaseHardware() } }
        private var pointer = -1
        private var edge = 0
        private var edgeY = 0f
        private var lastY = 0f
        private var lastTouch = 0L
        private data class Clip(val view: ViewGroup, val children: Boolean, val padding: Boolean)
        private var clipping: List<Clip>? = null
        private val compact = Runnable { ModuleHelper.guarded { if (!closed && !tracking && root.isShown) paint.form(7f, 134f, .408, 1.0) } }
        init {
            try {
                blur?.invoke(false)
                root.background = null; slider.background = null; slider.progressDrawable = null; slider.thumb = null
                track.background = null; track.foreground = paint; track.clipToOutline = false; fill.alpha = 0f
                // Keep shadow compositing on Android's RenderNode. iOS radius/
                // opacity states guide this native-elevation mapping; no blur bitmap
                // or second material is drawn underneath the antialiased perimeter.
                track.outlineProvider = ownedOutline
                track.outlineAmbientShadowColor = 0xff000000.toInt(); track.outlineSpotShadowColor = 0xff000000.toInt()
                allowStretch()
                root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, paddingBottom + (28 * density).roundToInt())
                for (v in arrayOf(slider, track)) v.layoutParams = v.layoutParams.apply {
                    width = (panelWidth * panelDensity).roundToInt(); height = (panelHeight * panelDensity).roundToInt()
                }
                classicLayout?.let { track.addOnLayoutChangeListener(it); icon.addOnLayoutChangeListener(it) }
                slider.addOnAttachStateChangeListener(this)
                XposedHelpers.setAdditionalInstanceField(slider, BINDING, this)
                XposedHelpers.setAdditionalInstanceField(track, BINDING, this)
                if (nativeFill) XposedHelpers.setAdditionalInstanceField(fill, BINDING, this)
                refresh()
                if (classic || startCompact) paint.refreshGeometry()
                else {
                    paint.form(47f, 148f, .8, .9)
                    root.postDelayed(compact, 1000L)
                }
            } catch (error: Throwable) {
                val cleanup = CleanupActions(error)
                cleanup.run { close() }
                cleanup.finish()
            }
        }
        fun refresh() {
            if (!nativeFill) {
                visibleLevel = if (slider.max > 0) (slider.progress.toLong() * 10000 / slider.max).toInt() else 0
                paint.level = visibleLevel; updateIconTint()
            }
        }
        fun nativeProgress(progress: Int, height: Int) {
            if (!closed && nativeFill && height > 0) {
                visibleLevel = (progress.toLong() * 10000 / height).toInt().coerceIn(0, 10000)
                paint.level = visibleLevel; updateIconTint()
            }
        }
        fun nativeGlyph(resource: Int, speakerRoute: Boolean) {
            if (!classic || closed) return
            if (nativeIconResource == resource && nativeSpeakerRoute == speakerRoute &&
                (classicGlyph == null || icon.drawable === classicGlyph)) return
            if (icon.drawable !== classicGlyph) nativeIcon = icon.drawable
            nativeIconResource = resource; nativeSpeakerRoute = speakerRoute
            classicGlyph = loadClassicGlyph(icon, resource, speakerRoute)
            val target = classicGlyph ?: nativeIcon
            if (icon.drawable !== target) icon.setImageDrawable(target)
            paint.refreshGeometry()
        }
        private fun updateIconTint() {
            val tint = if (classic || visibleLevel >= glyphFillThreshold * 10000f) darkIconTint else lightIconTint
            if (icon.imageTintList !== tint) icon.imageTintList = tint
        }
        fun nativeVolume(level: Int, minimum: Int, maximum: Int,initial: Boolean=false) {
            val changed = level != nativeLevel
            if(changed || minimum!=nativeMin || maximum!=nativeMax) paint.releaseEndpoint()
            if(initial) levelChangedForInput=false else if(changed) levelChangedForInput=true
            nativeLevel = level; nativeMin = minimum; nativeMax = maximum
            if (changed && hardwarePending) consumeHardwareEndpoint()
        }
        private fun consumeHardwareEndpoint() {
            if (!hardwarePending) return
            hardwarePending = false; root.removeCallbacks(hardwareEndpoint)
            val advance = !levelChangedForInput
            levelChangedForInput = false
            if (closed || tracking || !root.isShown || nativeMax <= nativeMin) return
            when (nativeLevel) {
                nativeMax -> { allowStretch(); paint.endpointKick(1f,advance) }
                nativeMin -> { allowStretch(); paint.endpointKick(-1f,advance) }
            }
            root.removeCallbacks(hardwareRelease)
            root.postDelayed(hardwareRelease,600L)
        }
        fun attachMore(glyph: MoreGlyph, more: ImageView) {
            moreView = more; moreOriginalY = more.translationY; moreOriginalX = more.translationX; moreLocations = IntArray(2)
            more.addOnLayoutChangeListener(moreLayout); track.addOnLayoutChangeListener(moreLayout)
            paint.attachMore(glyph); positionMore()
        }
        private fun positionMore() {
            val more = moreView ?: return
            val locations = moreLocations ?: return
            if (track.height <= 0 || more.height <= 0) return
            // Resolve different native parents only on layout, never on a frame.
            track.getLocationInWindow(locations)
            val trackY = locations[1]
            val trackCenterX = locations[0] + track.width * .5f
            more.getLocationInWindow(locations)
            if (classic) {
                moreAppliedX = more.translationX + trackCenterX - locations[0] - more.width * .5f
                more.translationX = moreAppliedX
            }
            moreTrackDelta = trackY - locations[1] + more.translationY
            morePositioned = true
            paint.refreshGeometry()
        }
        fun refreshMaterial() {
            if (closed) return
            root.background?.let { rootBackground = it }
            slider.background?.let { sliderBackground = it }
            slider.progressDrawable?.let { progress = it }
            slider.thumb?.let { thumb = it }
            track.background?.let { trackBackground = it }
            if (track.foreground !== paint) foreground = track.foreground
            if (fill.alpha != 0f) fillAlpha = fill.alpha
            if (icon.imageTintList !== darkIconTint && icon.imageTintList !== lightIconTint) iconTint = icon.imageTintList
            root.background = null; slider.background = null
            slider.progressDrawable = null; slider.thumb = null
            track.background = null; track.foreground = paint; fill.alpha = 0f
            blur?.invoke(false)
            updateIconTint()
            if (classic) paint.refreshGeometry()
        }
        fun releaseHardware() {
            hardwarePending=false;levelChangedForInput=false
            root.removeCallbacks(hardwareEndpoint);root.removeCallbacks(hardwareRelease)
            if(!closed) paint.releaseEndpoint()
        }
        fun noteInput(hardware: Boolean=true) {
            if (closed || tracking || classic) return
            root.removeCallbacks(compact)
            // iOS contextual value changes select Compact after the initial
            // appearance. Hardware updates keep the thin strip usable; only
            // native touch tracking enlarges it into Interacting.
            if (receivedInput && !paint.isCompact) paint.form(7f, 134f, .408, 1.0)
            receivedInput = true
            if (!paint.isCompact) root.postDelayed(compact, 1000L)
            if(!hardware) { releaseHardware();return }
            root.removeCallbacks(hardwareEndpoint); hardwarePending = true
            // Coalesce native state delivery with showH. Attempts beyond an
            // endpoint have no value-change callback, so retain one bounded check.
            root.postDelayed(hardwareEndpoint, 32L)
        }
        fun interact(active: Boolean) {
            if (classic) return
            if (active) releaseHardware()
            if (active) allowStretch()
            if (active && !tracking) compactBeforeTracking = paint.isCompact
            tracking = active
            root.removeCallbacks(compact)
            val returnCompact = !active && compactBeforeTracking
            paint.form(if (active) 58f else if (returnCompact) 7f else 47f,
                if (active) 170f else if (returnCompact) 134f else 148f, .408, 1.0)
            if (!active && !returnCompact) root.postDelayed(compact, 1000L)
        }
        private fun allowStretch() {
            if (clipping == null) {
                val saved = ArrayList<Clip>(6)
                clipping = saved
                var parent = track.parent as? ViewGroup
                while (parent != null && saved.size < 6) {
                    saved.add(Clip(parent, parent.clipChildren, parent.clipToPadding))
                    parent.clipChildren = false; parent.clipToPadding = false
                    parent = parent.parent as? ViewGroup
                }
            }
        }
        fun observeTouch(event: MotionEvent) {
            if (classic) return
            if (closed) return
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pointer = event.getPointerId(0); lastY = event.rawY; edgeY = lastY; edge = 0
                    lastTouch = event.eventTime; paint.beginTouch()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pointer < 0 || event.findPointerIndex(pointer) != 0 || !tracking) return
                    val y = event.rawY
                    // Native voice streams can clamp at a nonzero minimum. The
                    // plugin maps levels linearly into SeekBar progress units.
                    val minimum = if (nativeMax > 0 && nativeMin > 0)
                        (nativeMin.toLong() * slider.max / nativeMax).toInt().coerceIn(slider.min, slider.max) else slider.min
                    val next = when { slider.progress >= slider.max && y < lastY -> -1
                        slider.progress <= minimum && y > lastY -> 1; else -> edge }
                    if (next != edge) { edge = next; edgeY = lastY }
                    if (slider.progress > minimum && slider.progress < slider.max) { edge = 0; edgeY = y }
                    val delta = if (edge < 0) minOf(0f, y - edgeY) else if (edge > 0) maxOf(0f, y - edgeY) else 0f
                    // Android raw motion supplies a bounded display overflow, not a
                    // replacement audio value or the unrecovered UIKit pan driver.
                    val overflow = -paint.panOverflow(delta)
                    paint.overdrag(overflow, event.eventTime - lastTouch)
                    lastY = y; lastTouch = event.eventTime
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    pointer = -1; edge = 0; paint.dragging = false
                    if (event.actionMasked != MotionEvent.ACTION_UP) paint.overdrag(0f, 0L)
                }
            }
        }
        fun close() {
            if (closed) return
            closed = true
            val cleanup = CleanupActions()
            cleanup.run { root.removeCallbacks(compact) }
            cleanup.run { root.removeCallbacks(hardwareEndpoint) }
            cleanup.run { root.removeCallbacks(hardwareRelease) }
            hardwarePending = false
            cleanup.run { paint.stop() }
            cleanup.run { slider.removeOnAttachStateChangeListener(this) }
            clipping?.forEach { clip ->
                cleanup.run { if (!clip.view.clipChildren) clip.view.clipChildren = clip.children }
                cleanup.run { if (!clip.view.clipToPadding) clip.view.clipToPadding = clip.padding }
            }
            clipping = null
            cleanup.run { moreView?.removeOnLayoutChangeListener(moreLayout) }
            cleanup.run { track.removeOnLayoutChangeListener(moreLayout) }
            cleanup.run { classicLayout?.let { track.removeOnLayoutChangeListener(it); icon.removeOnLayoutChangeListener(it) } }
            cleanup.run { moreView?.let {
                if (it.translationY == moreAppliedY) it.translationY = moreOriginalY
                if (it.translationX == moreAppliedX) it.translationX = moreOriginalX
            } }
            moreView = null; moreLocations = null; morePositioned = false
            cleanup.run { if (root.background == null) root.background = rootBackground }
            cleanup.run { if (slider.background == null) slider.background = sliderBackground }
            cleanup.run { if (slider.progressDrawable == null) slider.progressDrawable = progress }
            cleanup.run { if (slider.thumb == null) slider.thumb = thumb }
            cleanup.run { if (track.foreground === paint) track.foreground = foreground }
            cleanup.run { if (track.background == null) track.background = trackBackground }
            cleanup.run { if (!track.clipToOutline) track.clipToOutline = trackClip }
            cleanup.run { if (fill.alpha == 0f) fill.alpha = fillAlpha }
            cleanup.run { if (track.outlineProvider === ownedOutline) track.outlineProvider = nativeOutline }
            cleanup.run { if (track.elevation == appliedElevation) track.elevation = nativeElevation }
            cleanup.run { if (track.outlineAmbientShadowColor == 0xff000000.toInt()) track.outlineAmbientShadowColor = nativeAmbient }
            cleanup.run { if (track.outlineSpotShadowColor == 0xff000000.toInt()) track.outlineSpotShadowColor = nativeSpot }
            cleanup.run { blur?.invoke(true) }
            cleanup.run { if (classicGlyph != null && icon.drawable === classicGlyph) icon.setImageDrawable(nativeIcon) }
            classicGlyph = null
            cleanup.run { if (icon.alpha == appliedIconAlpha) icon.alpha = iconAlpha }
            cleanup.run { if (icon.imageTintList === darkIconTint || icon.imageTintList === lightIconTint) icon.imageTintList = iconTint }
            cleanup.run { if (icon.translationX == appliedIconX) icon.translationX = iconX }
            cleanup.run { if (icon.translationY == appliedIconY) icon.translationY = iconY }
            cleanup.run {
                if (classicMatrix != null && icon.scaleType == ImageView.ScaleType.MATRIX && icon.imageMatrix == classicMatrix) {
                    icon.scaleType = iconScaleType
                    icon.imageMatrix = iconMatrix
                }
            }
            cleanup.run { if (icon.scaleX == appliedIconScaleX) icon.scaleX = iconScaleX }
            cleanup.run { if (icon.scaleY == appliedIconScaleY) icon.scaleY = iconScaleY }
            cleanup.run { if (icon.translationZ == appliedIconZ) icon.translationZ = iconZ }
            cleanup.run {
                if (root.paddingBottom == paddingBottom + (28 * density).roundToInt())
                    root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, paddingBottom)
            }
            for ((i, v) in arrayOf(slider, track).withIndex()) cleanup.run {
                val params = v.layoutParams
                var changed = false
                if (params.width == (panelWidth * panelDensity).roundToInt()) { params.width = dimensions[i].first; changed = true }
                if (params.height == (panelHeight * panelDensity).roundToInt()) { params.height = dimensions[i].second; changed = true }
                if (changed) v.layoutParams = params
            }
            for (v in arrayOf(root, slider, track, fill)) cleanup.run {
                if (XposedHelpers.getAdditionalInstanceField(v, BINDING) === this)
                    XposedHelpers.removeAdditionalInstanceField(v, BINDING)
            }
            cleanup.finish()
        }
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = ModuleHelper.guarded { close() }
    }

    /** Selected iOS 18 dimensions; material/placement and Android spring mapping
     * are approximations. Value animation remains the native progress animation. */
    private fun regularFraction(width: Float) = (4f * (width - 7f) / 40f).coerceIn(0f, 1f)
    internal fun cornerRadius(width: Float): Float {
        val f = regularFraction(width)
        return width * ((1f - f) * .5f + f * .3191f)
    }
    internal fun trackCenter(width: Float, room: Float, density: Float): Float {
        val compact = ((47f * density - width) / (40f * density)).coerceIn(0f, 1f)
        return room * .5f + (room - width).coerceAtLeast(0f) * .5f * compact
    }
    internal fun interface GlyphFrame { fun update(width: Float, height: Float, scale: Float, offsetY: Float) }
    internal class MoreGlyph(private val density: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var width = 47f * density
        private var opacity = 255
        fun pose(width: Float) { if (this.width != width) { this.width = width; invalidateSelf() } }
        override fun draw(canvas: Canvas) {
            val compact = ((47f * density - width) / (40f * density)).coerceIn(0f, 1f)
            val x = bounds.left + trackCenter(width, bounds.width().toFloat(), density)
            // Keep the native 28dp action target below the bar while bringing
            // its ink closer; compact weight follows the existing shape clock.
            val y = bounds.top + minOf(bounds.height() * .5f, 11f * density)
            val radius = minOf((1.8f + 1.7f * compact) * density, width * .5f)
            // This Android-only action has its own neutral ink. Native volume
            // contrast palettes switch near maximum and must not recolor it white.
            paint.color = 0xff8e8e93.toInt()
            paint.alpha = opacity
            canvas.drawCircle(x, y, radius, paint)
            paint.alpha = (opacity * (1f - compact) * (1f - compact)).roundToInt()
            val gap = 6f * density * (1f - compact)
            if (paint.alpha > 0) {
                canvas.drawCircle(x - gap, y, radius, paint)
                canvas.drawCircle(x + gap, y, radius, paint)
            }
        }
        override fun getIntrinsicWidth() = (58 * density).roundToInt()
        override fun getIntrinsicHeight() = (28 * density).roundToInt()
        override fun setTintList(tint: ColorStateList?) = Unit
        override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Platform API") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
    internal class Track(private val density: Float, startCompact: Boolean = false, private val classic: Boolean = false, private val changed: GlyphFrame) : Drawable() {
        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var width = (if (classic) 155f else if (startCompact) 7f else 14.1f) * density
        private var height = (if (classic) 155f else if (startCompact) 134f else 44.4f) * density
        private var velocityW = 0.0
        private var velocityH = 0.0
        private var glyph = if (classic) 1f else if (startCompact) 0f else .3f
        private var velocityGlyph = 0.0
        private var stretch = 0f
        private var velocityStretch = 0.0
        private var targetWidth = if (classic) 155f else if (startCompact) 7f else 47f
        private var targetHeight = if (classic) 155f else if (startCompact) 134f else 148f
        private var initialStretch = 18f * density
        private var more: MoreGlyph? = null
        fun attachMore(more: MoreGlyph) { this.more = more; more.pose(unscaledWidth()) }
        val materialWidth: Float get() = width
        fun center(room: Float) = if (classic) room * .5f else trackCenter(unscaledWidth(),room,density)
        fun refreshGeometry() = notifyFrame()
        var dragging = false
        // 22G100 captures the stretch range at pan begin; changing to the
        // interacting form must not replace that captured input resistance.
        fun beginTouch() { dragging = true; initialStretch = stretchAmount() }
        fun panOverflow(distance: Float): Float =
            sign(distance) * initialStretch * (1f - 1f / (1f + .55f * abs(distance) / initialStretch))
        private fun stretchAmount() = floor(height * if(targetWidth==58f) .105f else .182f).coerceAtLeast(density)
        private var endpointDirection=0f
        private var buttonRaw=0.0
        private var buttonValue=0f
        private var buttonVelocity=0.0
        private var buttonTarget=0f
        private var buttonFrom=0f
        private var buttonFromVelocity=0.0
        private var buttonAt=0L
        private var buttonOmega=2*PI/.3
        private var pressScale=1f
        private var pressVelocity=0.0
        private var pressTarget=1f
        private var pressFrom=1f
        private var pressFromVelocity=0.0
        private var pressAt=0L
        fun releaseEndpoint() {
            if (classic) return
            endpointDirection=0f;buttonRaw=0.0
            targetPress(1f);targetButton(0f,.6)
        }
        private fun targetButton(target: Float,response: Double) {
            if(buttonTarget==target) return
            buttonFrom=buttonValue;buttonFromVelocity=buttonVelocity;buttonTarget=target
            buttonAt=clock?.currentPlayTime ?: 0L;buttonOmega=2*PI/response
            if(!ValueAnimator.areAnimatorsEnabled()) {
                buttonValue=target;buttonVelocity=0.0;notifyFrame();invalidateSelf();return
            }
            val current=clock
            if(current==null) animateForm(targetWidth,targetHeight,.408,1.0)
            else current.duration=maxOf(current.duration,buttonAt+900L)
        }
        private fun targetPress(target: Float) {
            if(pressTarget==target) return
            pressFrom=pressScale; pressFromVelocity=pressVelocity; pressTarget=target
            pressAt=clock?.currentPlayTime ?: 0L
            if(!ValueAnimator.areAnimatorsEnabled()) {
                pressScale=target; pressVelocity=0.0; notifyFrame(); invalidateSelf(); return
            }
            val current=clock
            if(current==null) animateForm(targetWidth,targetHeight,.408,1.0)
            else current.duration=maxOf(current.duration,pressAt+1200L)
        }
        fun endpointKick(direction: Float,advance: Boolean=true) {
            if (classic) return
            if (dragging || !ValueAnimator.areAnimatorsEnabled() || direction==0f || !direction.isFinite()) return
            if(endpointDirection!=direction) { endpointDirection=direction;buttonRaw=0.0 }
            // Same-version default stepCount=16. The virtual excess is cosmetic;
            // native stream levels, their range and repeat delivery stay native.
            if(advance) buttonRaw+=direction/16.0
            val range=stretchAmount()/height.coerceAtLeast(density)
            // Bound error in every visible axis to 1/8 physical pixel, including
            // the steeper width contraction of the expanded track. Accumulate
            // subpixel repeats, but do not keep retargeting an invisible change.
            val narrow=minOf(width*.92f,width-4f*density).coerceAtLeast(density)
            val slope=maxOf(.77f,.73f,(width-narrow)/stretchAmount())*1.06f
            val quantum=.125f/slope
            val remaining=range/(1.0+.55*abs(buttonRaw)/range)
            val value=sign(buttonRaw)*if(remaining*height<=quantum) range.toDouble() else range-remaining
            targetPress(if(direction>0f) 1.06f else .92f)
            if(abs(value-buttonTarget)*height*slope>=.125 || abs(value)>=range)
                targetButton(value.toFloat(),.3)
        }
        fun overdrag(value: Float, elapsed: Long) {
            if (value == stretch && velocityStretch == 0.0) return
            if (!ValueAnimator.areAnimatorsEnabled()) { stretch = 0f; velocityStretch = 0.0; notifyFrame(); invalidateSelf(); return }
            velocityStretch = if (elapsed > 0) ((value - stretch) * 1000.0 / elapsed).coerceIn(-1000.0 * density, 1000.0 * density) else 0.0
            stretch = value; notifyFrame(); invalidateSelf()
        }
        // A retiring button response remains on this clock while touch follows
        // the native slider immediately. Handoff cannot drop the old displacement.
        private fun totalStretch() = stretch+height*buttonValue
        private fun unscaledWidth(): Float = width + (minOf(width * .92f, width - 4f * density).coerceAtLeast(density) - width) *
            (abs(totalStretch()) / stretchAmount()).coerceAtMost(1.5f)
        private fun unscaledHeight() = height + .73f * abs(totalStretch())
        private fun elasticWidth() = unscaledWidth()*pressScale
        private fun elasticHeight() = unscaledHeight()*pressScale
        private fun elasticOffset() = -.77f*totalStretch()*pressScale
        private fun notifyFrame() {
            val w = elasticWidth()
            changed.update(w, elasticHeight(), glyph, elasticOffset())
            more?.pose(unscaledWidth())
        }
        private var clock: ValueAnimator? = null
        private var cachedW = -1f
        private var cachedH = -1f
        private var opacity = 255
        var isCompact = startCompact && !classic
            private set
        fun form(w: Float, h: Float, response: Double, damping: Double) {
            if (classic) return
            if(targetWidth==w && targetHeight==h) {
                if(clock!=null) return
                // A repeated native state delivery must not start an idle clock.
                // Keep initial entrance, interrupted motion and stretch release.
                val targetGlyph=if(w==7f) 0f else w/47f
                if(width==w*density && height==h*density && glyph==targetGlyph && stretch==0f &&
                    velocityW==0.0 && velocityH==0.0 && velocityGlyph==0.0 && velocityStretch==0.0 &&
                    pressScale==pressTarget && pressVelocity==0.0 &&
                    buttonValue==buttonTarget && buttonVelocity==0.0) return
            }
            animateForm(w,h,response,damping)
        }
        private fun animateForm(w: Float,h: Float,response: Double,damping: Double) {
            clock?.cancel()
            pressFrom=pressScale; pressFromVelocity=pressVelocity; pressAt=0L
            buttonFrom=buttonValue;buttonFromVelocity=buttonVelocity;buttonAt=0L
            targetWidth = w; targetHeight = h
            isCompact = w == 7f
            val targetW = w * density; val targetH = h * density
            val targetGlyph = if (w == 7f) 0f else w / 47f
            if (!ValueAnimator.areAnimatorsEnabled()) {
                width = targetW; height = targetH; glyph = targetGlyph; stretch = 0f
                velocityW = 0.0; velocityH = 0.0; velocityGlyph = 0.0; velocityStretch = 0.0
                pressScale = pressTarget; pressVelocity = 0.0
                buttonValue=buttonTarget;buttonVelocity=0.0
                notifyFrame(); invalidateSelf(); return
            }
            val fromW = width; val fromH = height; val vw = velocityW; val vh = velocityH
            val fromGlyph = glyph; val vg = velocityGlyph
            val fromStretch = stretch; val vs = velocityStretch
            val omega = 2 * PI / response
            val decay = damping * omega
            // Critical/underdamped analytic response; finite and velocity preserving.
            val duration = maxOf((response * 1500).toLong().coerceIn(400, 1400),
                if (pressScale != pressTarget || pressVelocity != 0.0) 1200L else 0L,
                if (buttonValue != buttonTarget || buttonVelocity != 0.0) 900L else 0L)
            clock = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: Animator) { cancelled = true }
                    override fun onAnimationEnd(animation: Animator) = ModuleHelper.guarded {
                        if (!cancelled) {
                            width = targetW; height = targetH; velocityW = 0.0; velocityH = 0.0
                            glyph = targetGlyph; velocityGlyph = 0.0
                            pressScale = pressTarget; pressVelocity = 0.0
                            buttonValue=buttonTarget;buttonVelocity=0.0
                            if (!dragging) { stretch = 0f; velocityStretch = 0.0 }
                            notifyFrame(); invalidateSelf()
                        }
                        if (clock === animation) clock = null
                    }
                })
                addUpdateListener { ModuleHelper.guarded {
                    val t = it.currentPlayTime / 1000.0
                    fun sample(from: Float, target: Float, v: Double, axis: Int): Float {
                        val y = from - target
                        // UIKit's pressScale uses response .8 / damping .9;
                        // it applies CGAffineTransformMakeScale(s, s) to the track.
                        val axisTime=when(axis) {
                            4 -> (it.currentPlayTime-pressAt).coerceAtLeast(0L)/1000.0
                            5 -> (it.currentPlayTime-buttonAt).coerceAtLeast(0L)/1000.0
                            else -> t
                        }
                        val axisOmega=when(axis) { 4 -> 2*PI/.8;5 -> buttonOmega;else -> omega }
                        val axisDamping=when(axis) { 4 -> .9;5 -> 1.0;else -> damping }
                        val axisDecay=if(axis in 4..5) axisDamping*axisOmega else decay
                        val offset: Double; val speed: Double
                        if(y==0f && v==0.0) { offset=0.0;speed=0.0 }
                        else if (axisDamping == 1.0) {
                            val b = v + axisOmega * y; val e = exp(-axisOmega * axisTime)
                            offset = (y + b * axisTime) * e; speed = (v - axisOmega * b * axisTime) * e
                        } else {
                            val f = axisOmega * sqrt(1 - axisDamping * axisDamping); val b = (v + axisDecay * y) / f
                            val c = cos(f * axisTime); val s = sin(f * axisTime); val o = y * c + b * s; val e = exp(-axisDecay * axisTime)
                            offset = o * e; speed = e * (-axisDecay * o + f * (b * c - y * s))
                        }
                        when (axis) { 0 -> velocityW = speed; 1 -> velocityH = speed; 2 -> velocityGlyph = speed; 3 -> velocityStretch = speed;4 -> pressVelocity = speed;else -> buttonVelocity=speed }
                        return (target + offset).toFloat()
                    }
                    width = sample(fromW, targetW, vw, 0); height = sample(fromH, targetH, vh, 1)
                    glyph = sample(fromGlyph, targetGlyph, vg, 2)
                    if (pressScale != pressTarget || pressVelocity != 0.0)
                        pressScale = sample(pressFrom,pressTarget,pressFromVelocity,4)
                    if(buttonValue!=buttonTarget || buttonVelocity!=0.0)
                        buttonValue=sample(buttonFrom,buttonTarget,buttonFromVelocity,5)
                    if (!dragging) {
                        val sampled = sample(fromStretch, 0f, vs, 3)
                        val limit=maxOf(initialStretch,stretchAmount(),18f*density)
                        stretch = sampled.coerceIn(-limit, limit)
                        if (stretch != sampled) velocityStretch = 0.0
                    }
                    if (it.currentPlayTime >= it.duration) { width = targetW; height = targetH; velocityW = 0.0; velocityH = 0.0; clock = null }
                    notifyFrame(); invalidateSelf()
                } }
                start()
            }
        }
        fun stop() {
            val previous = clock; clock = null; more = null
            previous?.cancel()
            endpointDirection=0f; pressScale=1f; pressTarget=1f; pressVelocity=0.0
            buttonRaw=0.0;buttonValue=0f;buttonTarget=0f;buttonVelocity=0.0
        }
        override fun onLevelChange(level: Int): Boolean { invalidateSelf(); return true }
        override fun getOutline(outline: Outline) {
            if (classic) {
                outline.setRoundRect(bounds, 9f * density); outline.alpha = 0f; return
            }
            val w=elasticWidth().coerceIn(1f,bounds.width().coerceAtLeast(1).toFloat())
            val h=elasticHeight().coerceAtLeast(1f)
            val x=bounds.left+center(bounds.width().toFloat())-w*.5f
            val y=bounds.exactCenterY()-h*.5f+elasticOffset()
            outline.setRoundRect(x.roundToInt(),y.roundToInt(),(x+w).roundToInt(),(y+h).roundToInt(),cornerRadius(w/(density*pressScale))*density*pressScale)
            val compact=((47f*density-width)/(40f*density)).coerceIn(0f,1f)
            outline.alpha=(.18f+.12f*compact)*opacity/255f
        }
        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            if (classic) {
                val x = bounds.exactCenterX() - 77.5f * density
                val y = bounds.exactCenterY() - 77.5f * density
                paint.color = 0xffd4d4d8.toInt(); paint.alpha = opacity * 235 / 255
                canvas.drawRoundRect(x, y, x + 155f * density, y + 155f * density, 9f * density, 9f * density, paint)
                val filled = level.coerceIn(0, 10000) * 16 / 10000
                for (i in 0..15) {
                    paint.color = if (i < filled) 0xff242426.toInt() else 0xffa0a0a5.toInt(); paint.alpha = opacity
                    val left = x + (14f + i * 8f) * density
                    canvas.drawRect(left, y + 126f * density, left + 7f * density, y + 131f * density, paint)
                }
                return
            }
            val w = unscaledWidth().coerceIn(1f/pressScale, bounds.width()/pressScale)
            val h = unscaledHeight().coerceAtLeast(1f/pressScale)
            if (w != cachedW || h != cachedH) {
                path.rewind()
                // At the compact limit the two corners meet as a semicircle.
                // A clamped G4 shoulder alone leaves a flat crown on a 7 dp cap.
                G4RoundRectGeometry.build(path, w, h, cornerRadius(w / density) * density,
                    1f - regularFraction(w / density))
                cachedW = w; cachedH = h
            }
            // Keep Android's window/entry/input geometry. Only the painted strip
            // moves toward its native trailing edge as it becomes compact.
            canvas.save(); canvas.translate(bounds.left + center(bounds.width().toFloat()) - w*pressScale/2,
                bounds.exactCenterY() - h*pressScale/2 + elasticOffset())
            canvas.scale(pressScale,pressScale)
            val fillTop = h * (1f - level.coerceIn(0, 10000) / 10000f)
            // Each antialiased perimeter pixel has one material owner. Drawing
            // the white region over a complete dark path leaves a dark fringe.
            canvas.save(); canvas.clipRect(0f, 0f, w, fillTop)
            paint.color = 0xff58585e.toInt(); paint.alpha = opacity * 232 / 255; canvas.drawPath(path, paint); canvas.restore()
            canvas.save(); canvas.clipRect(0f, fillTop, w, h)
            paint.color = 0xfffafafa.toInt(); paint.alpha = opacity; canvas.drawPath(path, paint); canvas.restore()
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Platform API") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
